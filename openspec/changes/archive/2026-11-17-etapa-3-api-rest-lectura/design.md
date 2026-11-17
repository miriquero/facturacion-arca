# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/`.

Estado de partida (rama `feat/etapa-3-api-rest-lectura`, 179 pruebas en verde):

- `FacturaReporteRepositoryImpl` arma la proyección `SELECT new FacturaReporteDTO(...)` con dos constantes, `SELECCION` y `AGRUPACION_Y_ORDEN`, pensadas para insertar un `WHERE` entre ambas. `buscarReporte()` no recibe filtros.
- `FacturaReporteDTO` es inmutable, con `double importeTotal` y `String estado`; `FacturaVenta` guarda importes en `double` y asociaciones `LAZY`.
- `ReporteFacturasService` (`report/`) devuelve `byte[]` para PDF, Excel y TXT; el PDF lanza `GlifoNoSoportadoException` (code point, campo y fila) cuyo código HTTP quedó para esta etapa.
- `application.yml`: H2 en memoria, `open-in-view: false`. `DataLoader` (perfil `!test`) carga seis facturas con fechas relativas a hoy (`LocalDate.now().minusDays(n)`).
- Paquetes `controller/`, `service/` y `exception/` existen solo con `package-info.java`.
- Pruebas: `@DataJpaTest` con `PersistenciaBase`/`DatosDePrueba`/`FacturaBase` (públicas desde la etapa 2) y `@SpringBootTest @ActiveProfiles("test")`.

Restricciones: consigna (autoridad), reglas duras de `CLAUDE.md` (sin entidades en HTTP, sin `double` para calcular, sin paginación ni CRUD de catálogos, Java 17, Boot 3.3.5 sin cambiar versiones) y TDD estricto (semilla solo detrás de un test en rojo).

### Contraste de fuentes (qué manda)

| Tema | Consigna | KB / CHANGES | Semilla | Referencia del docente | Se adopta |
|---|---|---|---|---|---|
| Filtros | `fechaDesde`, `fechaHasta`, `estado`, `montoMinimo`; límites inclusivos en fechas **e importe** | igual (RN-FI-01..06) | mismos nombres, `Date` | mismos nombres, `LocalDate` ISO | consigna |
| Alias de formato | `pdf`, `xlsx`/`excel`, `txt`/`tsv`, default PDF | igual (RN-RE-10) | `/export/pdf`, `/export/excel` (TSV como `.xls`) | `/reporte?formato` | consigna |
| CORS | localhost y 127.0.0.1 en 5173 y 3000 | igual + propiedad `facturacion.cors.allowed-origins` | `localhost:*`, `127.0.0.1:*` y redes privadas | propiedad con los 4 orígenes | consigna + KB |
| Errores | `ProblemDetail` con estado, título, detalle y marca temporal | igual (RN-ER-01..06) | `{"mensaje": ...}`, `IllegalArgumentException`→400, `IllegalStateException`→409 | `ProblemDetail` + `timestamp` | consigna |
| Glifo no soportado | «error explícito» (sin código) | IN-03: 400 propuesto | no existe | 400 | **400, pendiente de confirmar** (D9) |
| Estado vacío | no lo define | no lo define | se ignora si es nulo | — | se trata como ausente (D3) |

### Inventario de la semilla (DD-13)

Origen: `C:\Users\zbook\Documents\Facultad\Desarrollo\StringBoot\api-facturas`. Cada pieza entra solo en un GREEN y se declara en `docs/etapa-3.md`.

| Archivo de la semilla | Se reutiliza | Se adapta | Se descarta |
|---|---|---|---|
| `controller/FacturaRestController.java` | `@RestController @RequestMapping("/api/facturas")`, `@GetMapping` del listado con los cuatro `@RequestParam(required = false)` y sus nombres; `Double montoMinimo`; `ResponseEntity.ok(lista)` | `Date` + patrón → `LocalDate` con `@DateTimeFormat(iso = DATE)`; `@Autowired` en campo → constructor; `/export/pdf` y `/export/excel` que escriben en `HttpServletResponse` → un único `GET /reporte?formato` que devuelve `ResponseEntity<byte[]>` con los exportadores de la etapa 2 | `POST`, `PUT`, `DELETE` (etapa 4); construcción del PDF/TSV dentro del controlador; `.xls` con texto tabulado |
| `config/CorsConfig.java` | `WebMvcConfigurer.addCorsMappings` sobre `/api/**` | patrones `localhost:*` y redes privadas → los 4 orígenes exactos desde propiedad; métodos `GET`, `POST`, `PUT`, `DELETE` → solo `GET` en esta etapa; se agrega `exposedHeaders("Content-Disposition")` | orígenes `192.168.*`, `10.*`, `172.*` |
| `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` con un método por excepción y un helper de respuesta | cuerpo `Map{"mensaje"}` → `ProblemDetail` + `timestamp`; se extiende `ResponseEntityExceptionHandler` para que los errores propios de Spring MVC también salgan en `ProblemDetail`; renombrado a `ApiExceptionHandler` | `IllegalArgumentException`→400 genérico (ocultaría errores de programación como errores del cliente); `IllegalStateException`→409 (la etapa 4 define su propia excepción) |
| `exception/RecursoNoEncontradoException.java` | constructor `(String recurso, Long id)` y mensaje «X con id N no encontrado» | concordancia de género («Factura con id 99 no encontrada») si un test lo pide | — |

Lo que no existe en la semilla y es nuevo: `GET /{id}` y `FacturaDetalladaDTO`, catálogos y `OpcionDTO`, validación y normalización de filtros, `FormatoReporte`, `ParametroInvalidoException`, manejo de tipos incompatibles, 405 y 500, mapeo del glifo, `CorsProperties`, `requests.http`.

### Referencia del docente consultada (`arca-spirng`, solo lectura)

Se miraron **solo nombres y forma del contrato**, sin copiar implementación: rutas de `CatalogoRestController` (coinciden con la consigna), firma del listado (`LocalDate` ISO, `Double montoMinimo`), `@RequestParam(defaultValue = "pdf") String formato`, nombres de campos de `FacturaDetalladaDTO`, `FacturaDetalleResponseDTO` y `OpcionDTO`, nombre `ApiExceptionHandler` con `timestamp`, y la propiedad `allowed-origins`. Nos apartamos en: `codigoMoneda` en vez de `tipoMoneda` en el detalle (coherente con `FacturaReporteDTO`), agregamos `puntoVentaNumero` y `resultadoAfip`, y **no** mapeamos `IllegalArgumentException` genérico a 400 (D8).

## Goals / Non-Goals

**Goals:**
- Contratos HTTP de lectura estables para el frontend de la etapa 5 y extensibles por la etapa 4 sin romperlos.
- Una sola fuente de verdad para filtros: el mismo objeto validado alimenta el listado y el reporte.
- Errores previsibles y explicables en la defensa (por qué 400, 404, 405, 500).

**Non-Goals:**
- Escritura, `X-Usuario`, auditoría por petición y concurrencia (C-05).
- Formatear importes como texto en JSON: se exponen como números tal como están guardados; no hay aritmética en esta etapa.
- Optimizar consultas más allá de evitar el N+1 evidente (seis facturas de ejemplo; sin paginación por consigna).
- Validar la respuesta en PostgreSQL: las pruebas corren en H2 (se declara, RN-CF-03).

## Decisions

### D1 — Capas y nombres

`controller/FacturaRestController` y `controller/CatalogoRestController` solo traducen HTTP ↔ DTO; `service/FacturaService` (clase anotada `@Transactional(readOnly = true)`, la etapa 4 sobrescribe en sus métodos de escritura) y `service/CatalogoService` construyen los DTO dentro de la transacción. Inyección por constructor. Alternativa descartada: un solo servicio para facturas y catálogos (mezcla responsabilidades y crece en la etapa 4).

### D2 — Filtros: `FacturaFiltrosDTO` (crudo) → `FiltroFacturas` (validado)

- `dto/FacturaFiltrosDTO` es un `record(LocalDate fechaDesde, LocalDate fechaHasta, String estado, Double montoMinimo)` con lo que llega por HTTP. El controlador lo arma con cuatro `@RequestParam(required = false)` explícitos (como la semilla) en los dos endpoints que filtran. Alternativa descartada: `@ModelAttribute` con binding de record, porque los errores de conversión llegan como `BindException`/`MethodArgumentNotValidException` con un detalle menos claro que `MethodArgumentTypeMismatchException`, que nombra el parámetro.
- `repository/FiltroFacturas` es un `record(LocalDate desde, LocalDate hasta, EstadoFactura estado, Double montoMinimo)` ya normalizado; el factory `FiltroFacturas.desde(FacturaFiltrosDTO)` es una función pura que normaliza y valida y lanza `ParametroInvalidoException` (400). Vive en `repository/` porque es el contrato del repositorio y así el repositorio no depende de `service/`. Una constante `FiltroFacturas.SIN_FILTROS` representa todos los campos nulos.
- `FacturaService.listar(FacturaFiltrosDTO)` y `FacturaService.generarReporte(FacturaFiltrosDTO, String formato)` llaman al mismo factory: es imposible que el reporte y el listado validen distinto (RN-FI-07).

### D3 — Reglas de validación y normalización

- `estado`: `strip()` + `toUpperCase(Locale.ROOT)` (no el locale por defecto: en turco `i` → `İ`). Vacío o solo espacios → ausente, porque un `<select>` «Todos» suele enviar `estado=` y rechazarlo sería hostil sin que la consigna lo pida. Desconocido → 400 con el valor recibido y los admitidos (`EstadoFactura.values()`). No se usa `EstadoFactura.valueOf` desnudo para no exponer su `IllegalArgumentException`.
- Fechas: `desde` y `hasta` independientes; `desde > hasta` → 400; iguales es válido.
- `montoMinimo`: `Double` porque Spring convierte `NaN`, `Infinity`, `-Infinity` y `1e400` (→ infinito) sin error; así el servicio puede rechazarlos con un mensaje propio (`Double.isFinite`) en lugar de un error de conversión genérico. Negativo válido (consigna: sin mínimo cero en backend). Texto no numérico → error de conversión → 400 (D8).

### D4 — `WHERE` dinámico con fragmentos fijos y parámetros nombrados

`FacturaReporteRepositoryImpl.buscarReporte(FiltroFacturas)` arma `SELECCION + where + AGRUPACION_Y_ORDEN`, donde `where` se compone solo con fragmentos **constantes** por filtro presente (`f.fechaEmision >= :fechaDesde`, `f.fechaEmision <= :fechaHasta`, `f.estado = :estado`, `f.importeTotal >= :montoMinimo`) unidos con `AND`, y luego se asignan los parámetros presentes con `setParameter`. Los valores nunca se concatenan (RN-FI-08). Un método package-private `jpql(FiltroFacturas)` devuelve el texto para que una prueba unitaria verifique que no contiene valores. El método sin argumentos `buscarReporte()` se conserva como `default` en la interfaz delegando con `SIN_FILTROS`, así las pruebas de la etapa 2 siguen describiendo la consulta sin filtros.

Alternativas descartadas: `(:p IS NULL OR campo >= :p)` en una consulta estática (Hibernate 6 y PostgreSQL fallan al inferir el tipo de un parámetro nulo y el plan es peor); Criteria API (más verboso de explicar y la consigna pide JPQL); concatenar valores (inyección).

### D5 — Importe mínimo sin aritmética de punto flotante

Los totales se guardan en `double` tras un control de ida y vuelta `BigDecimal` → `double` a dos decimales (regla dura). El filtro compara `f.importeTotal >= :montoMinimo` con un `Double`: es una **comparación**, no un cálculo, y el literal decimal del filtro (`100.10`) se convierte al mismo `double` más cercano que el importe guardado `100.10`, por lo que el límite inclusivo es exacto. No se suma, redondea ni convierte el importe. Alternativa descartada: recibir `BigDecimal` y pasarlo como parámetro contra una columna `double` (Hibernate exige el tipo del atributo; habría que convertir de vuelta a `double`, sin ganancia) y además `BigDecimal` no puede representar `NaN`/`Infinity`, que la consigna pide rechazar con 400 explícito. La prueba de borde usa 100.10 (no representable exacto en binario) para demostrarlo.

### D6 — `FacturaDetalladaDTO` construido en el servicio

`FacturaService.obtenerDetalle(Long id)` hace `findById` (o `RecursoNoEncontradoException` → 404) y mapea dentro de la transacción de solo lectura a `dto/FacturaDetalladaDTO` (campos de `specs/api-detalle-factura`) con `List<FacturaDetalleDTO>`. El mapeo accede a las asociaciones `LAZY` (cliente, condición IVA, punto de venta, moneda, detalles → lista de precio → artículo) mientras la sesión está abierta; con `open-in-view: false` fuera del servicio ya no habría sesión. Sin cliente: `clienteId` nulo y `clienteDenominacion` «Consumidor Final» (coherente con el listado). Renglones ordenados por id ascendente en el mapeo (la colección no tiene `@OrderBy` y no se toca el modelo). DTO como clases inmutables con `@Getter` (mismo estilo que `FacturaReporteDTO`) o `record`: se elige **`record`** para los DTO nuevos (Jackson 2.15+ los serializa sin anotaciones y son la forma más corta de expresar inmutabilidad); `FacturaReporteDTO` no se toca porque Hibernate lo instancia por constructor.

Alternativa considerada: una consulta con `JOIN FETCH` de todas las asociaciones. Para una sola factura y pocos renglones el costo de las cargas diferidas es despreciable, y `JOIN FETCH` de una colección más asociaciones anidadas complica la consulta; se deja como mejora si una prueba de cantidad de sentencias lo justificara (no es requisito).

### D7 — Catálogos: `OpcionDTO` y `CatalogoService`

`dto/OpcionDTO` es `record(Long id, String etiqueta, Double valor, String codigoMoneda)`. `CatalogoService` (readOnly) mapea con `findAll(Sort.by("id"))` las entidades simples; para `lista-precio-articulos` se agrega a `ListaPrecioArticuloRepository` una consulta `select lpa from ListaPrecioArticulo lpa join fetch lpa.articulo join fetch lpa.listaPrecio order by lpa.id` que evita N+1 (cada opción toca dos asociaciones). Etiquetas: `%04d - descripción` (punto de venta), `denominación (CUIT)` (cliente), denominación (condición IVA y moneda), `código - denominación (lista)` (artículo en lista). `valor` = `precioVenta` solo en artículos; `codigoMoneda` = `codigoAfip` solo en monedas. El `id` de la opción de artículo es el de `ListaPrecioArticulo`, que la etapa 4 recibirá como `listaPrecioArticuloId`. Alternativa descartada: proyección `SELECT new OpcionDTO` con `CONCAT` (el formato `%04d` no es portable en JPQL entre H2 y PostgreSQL).

### D8 — Errores: `ApiExceptionHandler extends ResponseEntityExceptionHandler`

- Extender `ResponseEntityExceptionHandler` hace que los errores propios de Spring MVC (método no soportado → 405, parámetro requerido ausente, tipo incompatible, recurso estático inexistente → 404) salgan como `ProblemDetail` sin escribir un handler por cada uno. Se sobrescribe `handleExceptionInternal` (o `createResponseEntity`) para agregar `timestamp` (`Instant` del `Clock` inyectado de `RelojConfig`, así las pruebas pueden fijarlo) a **todo** `ProblemDetail`, y `handleTypeMismatch` para un detalle en español: «El parámetro 'fechaDesde' tiene un valor inválido: '05/11/2026'. Formato esperado: yyyy-MM-dd».
- Handlers propios: `ParametroInvalidoException` → 400 «Parámetro inválido»; `RecursoNoEncontradoException` → 404 «Recurso no encontrado»; `GlifoNoSoportadoException` → 400 «PDF no disponible para estos datos» (D9); `Exception` → 500 «Error interno» con detalle fijo «Ocurrió un error inesperado. Intente nuevamente.» y log del error con SLF4J (el cuerpo nunca lleva el mensaje original).
- No se mapea `IllegalArgumentException` genérico a 400 (lo hacen la semilla y la referencia): un error de programación se reportaría como culpa del cliente. Cada 400 nace de una excepción propia y explícita.
- `@WebMvcTest` incluye `@RestControllerAdvice`, por lo que el `Clock` se provee en esas pruebas con `@Import(RelojConfig.class)` o un `@TestConfiguration` con reloj fijo.

### D9 — Glifo no soportado: 400 (pendiente de confirmar con el docente)

Se propone **400** según la resolución de IN-03: el problema está en los **datos que el cliente pidió exportar en PDF** (un carácter que la fuente exigida no tiene), la misma petición en `xlsx` o `txt` funciona y el detalle indica cómo resolverlo; no es un fallo del servidor (500 ocultaría un error reproducible de entrada y sugeriría reintentar) y 422 no aporta en una petición `GET` sin cuerpo. El detalle reutiliza el mensaje de `GlifoNoSoportadoException` (U+XXXX, campo, fila y la sugerencia de Excel/TXT). Queda registrado en `docs/etapa-3.md` y en Open Questions como **a confirmar**; cambiarlo luego es una línea en el handler y su prueba.

### D10 — Formato del reporte: `FormatoReporte` y `ArchivoReporte`

`report/FormatoReporte` es un enum `PDF`, `XLSX`, `TXT` con tipo de contenido, extensión y alias; `FormatoReporte.desde(String)` normaliza (`strip` + minúsculas `Locale.ROOT`), acepta `pdf`, `xlsx`, `excel`, `txt`, `tsv` y lanza `ParametroInvalidoException` con los admitidos. El default PDF lo da `@RequestParam(defaultValue = "pdf")`, que Spring aplica también al parámetro vacío. `report/ArchivoReporte` es `record(String nombre, MediaType tipo, byte[] contenido)`; el controlador solo copia esos datos a `ResponseEntity` con `ContentDisposition.attachment().filename(nombre)`.

- Tipos: `application/pdf`; `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; `text/plain;charset=UTF-8` para el TXT (el archivo es `.txt` y UTF-8; `text/tab-separated-values` se consideró, pero el entregable de la consigna se llama TXT y `text/plain` lo abre cualquier navegador o editor). `tsv` es solo un alias de entrada.
- Nombres: `facturas.pdf`, `facturas.xlsx`, `facturas.txt` (sin fecha en el nombre para que las pruebas y `requests.http` sean deterministas; el PDF ya lleva la fecha de generación adentro).
- El formato se valida **antes** de consultar, para no ejecutar la consulta ante un pedido que igual será rechazado.

### D11 — CORS configurable

`config/CorsProperties` (`@ConfigurationProperties("facturacion.cors")`, `record(List<String> allowedOrigins)`) y `config/CorsConfig implements WebMvcConfigurer` con `@EnableConfigurationProperties(CorsProperties.class)`: `addMapping("/api/**").allowedOrigins(...).allowedMethods("GET").exposedHeaders("Content-Disposition")`, sin `allowCredentials`. `application.yml` declara los cuatro orígenes. Solo `GET` porque la API es de lectura; la etapa 4 agrega `POST`, `PUT`, `PATCH`, `DELETE` y el encabezado `X-Usuario`. `CorsConfig` implementa `WebMvcConfigurer` directamente para que `@WebMvcTest` lo cargue (los slices incluyen beans `WebMvcConfigurer`). Alternativa descartada: `@CrossOrigin` por controlador (orígenes repetidos y no configurables). Por qué no autentica: lo aplica el navegador; `curl` o un script ignoran CORS y no hay identidad (ver `knowledge-base/03_actores_y_roles.md`).

### D12 — Pruebas por capa

- **Unitarias puras**: `FiltroFacturasTest` (normalización, vacío, desconocido, fechas, no finitos, negativo), `FormatoReporteTest` (alias, mayúsculas, inválido), `FacturaReporteRepositoryImpl.jpql` sin valores.
- **`@WebMvcTest`** de cada controlador con `@MockBean` del servicio: contrato HTTP (status, `jsonPath`, encabezados, `ProblemDetail`, tipos incompatibles, 405, 500 sin filtrar el mensaje, CORS). No prueban reglas de negocio: el servicio simulado lanza la excepción.
- **`@DataJpaTest`** (`FacturaReporteRepositoryTest` existente, ampliado): filtros contra la base, límites inclusivos, combinación con factura sin cliente y sin renglones.
- **Integración** `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`: listado y reporte con los mismos filtros (comparando filas del TXT con el JSON), detalle con `open-in-view: false`, catálogos y glifo de punta a punta. Estas pruebas **no** son `@Transactional`: una transacción de prueba mantendría abierta la sesión durante la petición de MockMvc (mismo hilo) y ocultaría una `LazyInitializationException`. Guardan datos con los repositorios (con `UsuarioActual` fijado) y limpian en `@AfterEach` (`deleteAll` en orden: facturas, clientes, catálogos).
- Valores esperados literales tomados de los escenarios; AssertJ y `jsonPath`; sin aserciones triviales.

### D13 — `requests.http` y prueba manual

`requests.http` en la raíz (formato de VS Code REST Client / IntelliJ, variable `@base = http://localhost:8080`) con: listado sin filtros; cada filtro solo; combinado; estado con espacios y minúsculas; sin resultados; fechas invertidas; estado desconocido; `montoMinimo=NaN`; fecha mal formada; detalle existente e inexistente; reporte en los cinco valores de formato y sin formato; formato inválido; cada catálogo; una petición con `Origin`. Como el `DataLoader` usa fechas relativas a hoy, los ejemplos de fechas usan rangos amplios (`2020-01-01`..`2099-12-31`) o se explican en un comentario. La comprobación manual arranca la app (`./gradlew bootRun` con JDK 17), ejecuta varios `curl` y la detiene liberando el puerto 8080.

## Risks / Trade-offs

- [400 para el glifo puede no coincidir con el criterio del docente] → aislado en un handler y una prueba; queda declarado como pendiente en el informe.
- [Tratar `estado` vacío como ausente no está en la consigna] → decisión explícita, documentada y probada; si el docente prefiere 400, es un cambio en `FiltroFacturas.desde` y su prueba.
- [Comparar `double` con `>=`] → válido porque no hay aritmética y ambos lados vienen del mismo literal decimal; el borde 100.10 lo prueba.
- [Pruebas de integración no transaccionales pueden dejar datos entre clases] → limpieza en `@AfterEach` y contexto `test` sin `DataLoader`; si aparecen interferencias, `@DirtiesContext` en esa clase.
- [Cargas diferidas en el detalle (N+1 pequeño)] → aceptado por tamaño; documentado como alternativa `JOIN FETCH`.
- [`ResponseEntityExceptionHandler` cambia el formato de errores de Spring ya existentes (p. ej. consola H2 o recursos estáticos)] → solo afecta a controladores anotados; la consola H2 es un servlet aparte y su prueba existente sigue verde.
- [`@MockBean` está deprecado a partir de Boot 3.4] → el proyecto está fijado en 3.3.5, donde es la forma vigente.
- [Resultados probados en H2, no en PostgreSQL] → declarado en el informe (RN-CF-03).

## Migration Plan

No hay datos ni contratos previos que migrar: son los primeros endpoints. El método `buscarReporte()` se conserva, por lo que las pruebas de la etapa 2 no cambian. Rollback: revertir la rama; `build.gradle` no cambia.

## Open Questions

- **IN-03 (no bloqueante)**: confirmar con el docente que un PDF imposible por glifo no soportado debe responder `400` (D9). Se implementa 400 y se documenta como pendiente.
