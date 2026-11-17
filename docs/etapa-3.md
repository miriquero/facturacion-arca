# Etapa 3 - API REST de lectura

## Contratos y DTO

La API solo lee (`GET`). Nunca sale una entidad JPA por HTTP: con `open-in-view: false` una asociación diferida serializada fuera de la transacción rompería (`LazyInitializationException`), y además se filtraría el modelo interno (auditoría, relaciones bidireccionales). Cada respuesta es un DTO construido **dentro** del servicio, en una transacción de solo lectura:

- `FacturaReporteDTO` (ya existía; es la fila del listado y del reporte, 10 campos) lo arma Hibernate con `SELECT new`.
- `FacturaDetalladaDTO` / `FacturaDetalleDTO` (records): cabecera completa y renglones ordenados por id; sin cliente, `clienteId` nulo y `Consumidor Final`. Se arman en `FacturaService.obtenerDetalle`; la prueba HTTP no es transaccional a propósito (una transacción de prueba dejaría la sesión abierta y ocultaría el error). Alternativa descartada: `JOIN FETCH` de todo (para una factura y pocos renglones no hace falta). Diferencia con la referencia: usamos `codigoMoneda` (coherente con el listado) y agregamos `puntoVentaNumero` y `resultadoAfip`.
- `OpcionDTO(id, etiqueta, valor, codigoMoneda)` para los 5 catálogos. Etiquetas: `0001 - Casa Central`, `Juan Servicios (20-12345678-9)`, denominación (IVA y moneda), `ART-001 - Martillo (GENERAL)`. El `id` de la opción de artículo es el de `ListaPrecioArticulo`: será el `listaPrecioArticuloId` que reciba la etapa 4. No hay CRUD de catálogos: `POST/PUT/PATCH/DELETE` dan 405.

## Filtros

`fechaDesde`, `fechaHasta`, `estado`, `montoMinimo` en el listado **y** en el reporte. Llegan crudos en `FacturaFiltrosDTO` y una única función pura, `FiltroFacturas.desde(...)`, los normaliza y valida; el listado y el reporte la llaman, así que no pueden validar distinto.

| Regla | Decisión |
|---|---|
| Fechas | inclusivas; `desde > hasta` → 400 (iguales es válido); cada una puede faltar |
| `estado` | `strip` + mayúsculas con `Locale.ROOT` (con el idioma por defecto, en turco `i` pasaría a `İ`); **vacío o solo espacios = ausente** (un `<select>` «Todos» manda `estado=`; la consigna no lo define, decisión del estudiante 2026-11-17); desconocido → 400 con el valor y los admitidos. No se usa `EstadoFactura.valueOf` desnudo para no exponer su excepción |
| `montoMinimo` | inclusivo; negativo válido (sin mínimo cero); `NaN`, `Infinity`, `-Infinity` y `1e400` → 400. Es `Double` justamente porque Spring los convierte sin error y así el servicio puede rechazarlos con mensaje propio |

## Consulta con parámetros

`FacturaReporteRepositoryImpl.jpql(filtro)` arma `SELECCION + WHERE + AGRUPACION_Y_ORDEN`. El `WHERE` se compone solo con **fragmentos constantes** por filtro presente (`f.fechaEmision >= :fechaDesde`, `<= :fechaHasta`, `f.estado = :estado`, `f.importeTotal >= :montoMinimo`) unidos con `AND`, y los valores se asignan con `setParameter` de parámetros nombrados; nunca se concatenan (prueba: el texto no contiene `2026-11-05`, `EMITIDA` ni `123.45`). Descartadas: `(:p IS NULL OR campo >= :p)` (Hibernate 6 y PostgreSQL fallan al inferir el tipo de un parámetro nulo y el plan es peor), Criteria (la consigna pide JPQL) y concatenar valores (inyección). `buscarReporte()` sin argumentos sigue existiendo (`default` que delega con `SIN_FILTROS`): las pruebas de la etapa 2 no cambiaron.

Comparar `importeTotal >= :montoMinimo` es una **comparación**, no aritmética: no se suma, redondea ni convierte. El borde `100.10` (no representable exacto en binario) pasa porque el literal del filtro y el importe guardado se convierten al mismo `double` más cercano.

## Reporte y formatos

`GET /api/facturas/reporte?formato=`: `pdf` (por defecto, también con `formato=` vacío), `xlsx` (alias `excel`), `txt` (alias `tsv`), sin distinguir mayúsculas ni espacios. Tipos: `application/pdf`; `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; `text/plain;charset=UTF-8` (el entregable se llama TXT). Nombres fijos `facturas.pdf|xlsx|txt` (sin fecha, para pruebas deterministas) con `Content-Disposition: attachment`. El formato se valida **antes** de consultar. Un formato desconocido → 400 con los admitidos.

**Glifo no soportado en PDF → 400** (`PDF no disponible para estos datos`, con `U+XXXX` y la sugerencia de Excel o TXT; los otros formatos funcionan con los mismos filtros). Justificación: el problema está en los datos pedidos en PDF, es reproducible y se resuelve cambiando de formato; 500 ocultaría un error de entrada y sugeriría reintentar, y 422 no aporta en un `GET`. **PENDIENTE DE CONFIRMAR CON EL DOCENTE (IN-03)**: cambiarlo es una línea en el handler y su prueba.

## Catálogos

Ver «Contratos y DTO». `lista-precio-articulos` usa una consulta con `join fetch` de artículo y lista (una sentencia, sin N+1). El formato `%04d` no es portable en JPQL entre H2 y PostgreSQL, por eso las etiquetas se arman en Java y no con una proyección `CONCAT`.

## Errores y códigos HTTP

`ApiExceptionHandler` extiende `ResponseEntityExceptionHandler`: también los errores propios de Spring MVC salen como `ProblemDetail` con `status`, `title`, `detail` y `timestamp` (del `Clock` inyectado). Hallazgo: en los errores de Spring (405) el `ProblemDetail` lo crea la superclase recién dentro de `handleExceptionInternal`, así que la marca temporal se agrega sobre el **resultado** de `super`; agregarla al cuerpo de entrada la omitía (lo expuso una prueba en rojo).

| Código | Cuándo | Origen |
|---|---|---|
| 400 | filtro inválido, formato inválido | `ParametroInvalidoException` |
| 400 | fecha mal formada, `montoMinimo=cien`, `id=abc` (detalle en español que nombra el parámetro y el formato) | `TypeMismatchException` (`handleTypeMismatch`) |
| 400 | PDF imposible por glifo (pendiente IN-03) | `GlifoNoSoportadoException` |
| 404 | factura inexistente | `RecursoNoEncontradoException` |
| 405 | `POST/PUT/PATCH/DELETE` en rutas de lectura | `HttpRequestMethodNotSupportedException` |
| 500 | cualquier falla no prevista; detalle fijo, el mensaje real solo va al log | `Exception` |

Consulta vacía (`200 []`) distinta de recurso inexistente (`404`): una colección sin elementos es una respuesta válida; solo un recurso puntual que no existe es 404. No se mapea `IllegalArgumentException` genérica a 400 (lo hace la semilla): un error de programación se reportaría como culpa del cliente; cada 400 nace de una excepción propia. El 500 nunca devuelve el mensaje original (podría traer SQL, rutas o nombres de clases).

## CORS

`CorsConfig` (`WebMvcConfigurer`) sobre `/api/**`: orígenes exactos `http://localhost:5173`, `:3000` y `http://127.0.0.1:5173`, `:3000` desde `facturacion.cors.allowed-origins` (`CorsProperties`), solo `GET`, `Content-Disposition` expuesto, sin credenciales. Otro origen: 403 sin `Access-Control-Allow-Origin`. **CORS no autentica**: lo aplica el navegador; `curl` o un script lo ignoran (sin `Origin` la API responde normal) y no identifica usuarios (ver `knowledge-base/03_actores_y_roles.md`). La etapa 4 agrega los demás métodos y `X-Usuario`.

## Semilla reutilizada (DD-13)

Origen: `api-facturas` (releída en este apply: `FacturaRestController`, `CorsConfig`, `GlobalExceptionHandler`, `RecursoNoEncontradoException`).

| Se trajo | Se adaptó | Es nuevo / se descartó |
|---|---|---|
| `@RestController @RequestMapping("/api/facturas")`, `@GetMapping` con los cuatro `@RequestParam(required = false)` y sus nombres, `Double montoMinimo`; `addCorsMappings` sobre `/api/**`; un método por excepción y helper de respuesta; `RecursoNoEncontradoException(recurso, id)` | `Date` + patrón → `LocalDate` ISO; `@Autowired` en campo → constructor; `/export/pdf` y `/export/excel` (escribían en `HttpServletResponse`, con TSV como `.xls`) → un único `/reporte?formato` con `ResponseEntity<byte[]>` y los exportadores de la etapa 2; patrones `localhost:*` y redes privadas → 4 orígenes exactos desde propiedad; métodos → solo `GET`; `{"mensaje"}` → `ProblemDetail` + `timestamp` | Nuevo: `GET /{id}`, `FacturaDetalladaDTO`, catálogos y `OpcionDTO`, `FiltroFacturas`, `FormatoReporte`, `ParametroInvalidoException`, 405/500/glifo, `CorsProperties`, `requests.http`. Descartado: `POST/PUT/DELETE` (etapa 4), `IllegalArgumentException`→400 e `IllegalStateException`→409 genéricos |

## Referencia del docente

`arca-spirng` se consultó **solo para nombres y forma del contrato** en el diseño (rutas de catálogos, `formato` con default `pdf`, `ApiExceptionHandler` con `timestamp`, propiedad `allowed-origins`); en este apply no se volvió a abrir y no se copió código. Nos apartamos en `codigoMoneda`, los campos agregados al detalle y en no mapear `IllegalArgumentException` genérica.

## Decisiones

D1 capas controlador/servicio/repositorio con inyección por constructor; D2 filtros crudos → validados; D3 reglas de validación; D4 `WHERE` con fragmentos fijos y parámetros nombrados; D5 comparación sin aritmética; D6 detalle armado en el servicio; D7 catálogos con `OpcionDTO`; D8 `ProblemDetail` y handler; D9 glifo → 400 (pendiente); D10 `FormatoReporte`/`ArchivoReporte`; D11 CORS configurable; D12 pruebas por capa; D13 `requests.http` y prueba manual. Los DTO nuevos son `record`; `FacturaReporteDTO` no se tocó porque Hibernate lo instancia por constructor.

## Verificaciones y entorno

Motor: **H2 en memoria**; PostgreSQL no se probó (sigue postergado a antes de la etapa 4, RN-CF-03). JDK 17.0.20.1, Gradle 8.10.2.

| Verificación | Resultado |
|---|---|
| Baseline al empezar | 179 pruebas en verde |
| `./gradlew clean test bootJar` al cerrar | **320 pruebas, 0 fallas, 0 omitidas** (141 nuevas); `build/libs/` solo tiene `facturacion-arca-1.0.0.jar` |
| `cd frontend && npm test` | 7 pruebas en verde |
| Revisión de reglas duras en `src/main` | sin `@Data`, `@Builder` ni `Math.round`; sin aritmética `double` nueva; ningún controlador usa tipos de `model/`; sin `POST/PUT/PATCH/DELETE`; el JPQL solo une fragmentos constantes |

**Pruebas de caracterización** (pasaron al escribirse porque el comportamiento ya existía por un paso anterior) y su mutación temporal de validación (restaurada después): fecha hasta inclusiva y mínimo negativo del repositorio (`<` en vez de `<=`; `ABS(:montoMinimo)`), filtros vacíos (consulta por defecto con otro estado), «base sin facturas» (404 si la lista es vacía), `id` no numérico (nombre del parámetro), el listado y el reporte de integración (sin `strip`, `isEmpty` en vez de `isBlank`, `!isBefore` en vez de `isAfter`, sin `isFinite`, filtros ignorados en el reporte, `generarTxt` en vez de `generarExcel`, mensaje y código del glifo), la marca temporal de los errores (se quita y fallan), la propiedad CORS (orígenes fijos en el código) y el catálogo por HTTP (quitando `join fetch` **y** `@Transactional` falla por carga diferida; con solo quitar el `join fetch` pasa, porque el servicio es transaccional: el `join fetch` evita N+1 y eso no está aserto). Las pruebas del detalle se validaron quitando `@Transactional(readOnly = true)` de `FacturaService`: falla con `LazyInitializationException: ... FacturaVenta.detalles ... no Session`. Dos casos son caracterización sin mutación posible: «sin `Origin` no hay encabezados CORS» (esos encabezados solo existen con `Origin`) y los 405 que nacen del comportamiento por defecto de Spring MVC. El resto nació en rojo por la razón esperada (aserción, estado HTTP o `UnsupportedOperationException` del esqueleto).

**Prueba manual** (`java -jar` con JDK 17, datos del `DataLoader`; se detuvo la app y el puerto 8080 quedó libre):

| Petición | Resultado |
|---|---|
| `GET /api/facturas` | 200, 6 facturas (la primera: id 6, `BORRADOR`, `numeroFactura` nulo) |
| `GET /api/facturas?estado=%20emitida%20` | 200, 3 facturas, todas `EMITIDA` |
| `GET /api/facturas?estado=PAGADA` | 400 `application/problem+json`: `Parámetro inválido`, «El estado 'PAGADA' no es válido. Valores admitidos: [BORRADOR, EMITIDA, ANULADA].», con `timestamp` |
| `GET /api/facturas/6` | 200, cabecera con `clienteDenominacion` `Consumidor Final` y un renglón `ART-002` |
| `GET /api/facturas/999999` | 404 `problem+json`: «Factura con id 999999 no encontrado» (ver nota de género abajo) |
| `GET /api/facturas/reporte?formato=xlsx` | 200, `attachment; filename="facturas.xlsx"`, 4177 bytes, empieza con `PK` |
| `GET /api/facturas/reporte` | 200 `application/pdf`, `facturas.pdf`, empieza con `%PDF` |
| `GET /api/facturas/reporte?formato=txt&estado=EMITIDA` | 200 `text/plain;charset=UTF-8`, encabezado de 9 columnas y 3 filas |
| `GET /api/facturas/reporte?formato=doc` | 400 `problem+json` con los formatos admitidos |
| `GET /api/catalogos/lista-precio-articulos` | 200, `ART-001 - Martillo (GENERAL)` 3000.0, `ART-002 - Televisor (GENERAL)` 60000.0, `ART-003 - Cable HDMI (GENERAL)` 8000.0 |
| `Origin: http://localhost:5173` | 200, `Access-Control-Allow-Origin: http://localhost:5173` y `Access-Control-Expose-Headers: Content-Disposition` |
| `Origin: http://evil.example` | 403, sin `Access-Control-Allow-Origin` |

Notas: el mensaje del 404 («no encontrado» para una factura) viene de la semilla y no concuerda en género; si se quiere, se corrige con una prueba. El archivo Excel se validó por bytes y cabeceras; no se abrió en Excel. Los ejemplos usan `requests.http` (se ejecutaron con `curl` equivalentes, no con REST Client).

**Pendiente de confirmar con el docente (IN-03)**: ¿un PDF imposible por glifo no soportado debe responder 400? (implementado así, ver «Reporte y formatos»).

## Uso de IA

- Herramientas: Claude Code (Anthropic) con sub-agentes de OpenSpec (propuesta, diseño, tareas y ejecución de la etapa).
- Qué se pidió: la propuesta y el diseño de la etapa, las pruebas en rojo, el código mínimo para pasarlas, las mutaciones de validación de las pruebas de caracterización, la prueba manual, `requests.http`, el README y este informe.
- Qué se revisó y qué se corrigió: revisé la propuesta antes de implementar y aprobé las decisiones de diseño tal como estaban: estado vacío como filtro ausente, TXT como `text/plain`, nombres de archivo sin fecha y el 400 para el glifo no soportado, que queda pendiente de confirmar con el docente. Con una guía de revisión que me armó la IA leí y verifiqué la validación única de filtros compartida por el listado y el reporte (`FiltroFacturas`), el `WHERE` con parámetros nombrados, el armado del detalle dentro de la transacción de solo lectura con `open-in-view` desactivado, el manejo de errores con `ProblemDetail` y sus códigos 400, 404, 405 y 500, el controlador de solo lectura y la configuración de CORS, que no autentica. Las pruebas de integración no son transaccionales a propósito, para que una carga perezosa fallida no quede oculta. La etapa expuso un defecto que se corrigió: el 405 salía sin marca temporal.
- Los commits propuestos llevan la línea `Co-Authored-By`.
