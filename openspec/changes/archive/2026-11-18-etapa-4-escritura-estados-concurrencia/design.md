# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos observables: ver `specs/`. Este documento fija **cómo** se construye.

Estado del código al empezar (etapa 3 archivada, 320 pruebas en verde con JDK 17):

- **Modelo**: `FacturaVenta` con `agregarDetalle`, `quitarDetalle` y `reemplazarDetalles` (vacía la misma colección); importes en campos `double`; restricción única `uk_factura_venta_punto_venta_numero`. `Articulo.alicuotaIva` es `Double` nullable; `ListaPrecioArticulo.precioVenta` es `double`.
- **Auditoría**: `AuditoriaApp` solo tiene `@PrePersist` (`completarAlta`, con `LocalDateTime.now()` y `UsuarioActual.get()`); `UsuarioActual` es un `ThreadLocal<Usuario>`. No hay `@PreUpdate`, ni baja, ni interceptor.
- **Servicio**: `FacturaService` de solo lectura (`@Transactional(readOnly = true)` en la clase) con `listar`, `obtenerDetalle` y `generarReporte`.
- **HTTP**: `FacturaRestController` solo `GET`; `ApiExceptionHandler extends ResponseEntityExceptionHandler` con `ProblemDetail` + `timestamp` (reloj inyectado) y handlers de 400/404/500; `CorsConfig` solo `GET` y expone `Content-Disposition`.
- **Reloj**: `RelojConfig` publica un `Clock` de Argentina; las pruebas lo reemplazan por uno fijo.
- **Datos**: `DataLoader` provisorio con importes, números y CAE literales; `@Profile("!test")`, omite si hay usuarios, limpia el contexto en `finally`.
- **Repositorios**: `UsuarioRepository` y `PuntoVentaRepository` vacíos; `FacturaVentaRepository` extiende el fragmento de reporte.
- **Entorno**: H2 2.x en memoria (`jdbc:h2:mem:facturacion`); PostgreSQL 16.9 portátil fuera del repo en el puerto 5433 (`iniciar-pg16.bat` / `detener-pg16.bat`, base/usuario/clave `facturacion`, solo 127.0.0.1). Docker no funciona en esta máquina.

Restricciones: reglas duras de `CLAUDE.md` (JDK 17, versiones fijas, sin entidades en HTTP, sin `@Data`, sin `double`/`Math.round` para importes, sin Git que cambie estado) y TDD estricto. Ante conflicto manda la consigna (DD-01).

## Goals / Non-Goals

**Goals:**
- Cálculo monetario exacto y verificable con valores literales que distinguen el orden correcto del incorrecto.
- Atomicidad y bloqueos explicables en la defensa, con pruebas de hilos reales en H2 y una corrida a demanda en PostgreSQL 16.9 cuyo resultado real queda en el informe.
- CAE y fechas deterministas en las pruebas (fuente aleatoria y reloj inyectables).
- Auditoría por petición sin fugas de `ThreadLocal`.

**Non-Goals:**
- Cambiar los campos `double` a `BigDecimal`/`NUMERIC` (la consigna pide conservarlos, DD-03).
- Bloqueo optimista con `@Version` o reintentos automáticos (la consigna exige pesimista, DD-05).
- Exponer datos de auditoría en los DTO de respuesta (no lo pide la consigna; se verifican en pruebas y, a mano, por consola H2).
- Ejecutar la suite contra PostgreSQL dentro de `./gradlew test`.

## Decisions

### D1 — DTO de entrada como `record` con Bean Validation
`FacturaRequestDTO(Long clienteId, @NotNull Long condicionIvaId, @NotNull Long puntoVentaId, @NotNull Long tipoMonedaId, LocalDate fechaEmision, @Size(max = 500) String observaciones, @NotEmpty List<@NotNull @Valid FacturaDetalleRequestDTO> detalles)`; `FacturaDetalleRequestDTO(@NotNull Long listaPrecioArticuloId, @NotNull @Positive BigDecimal cantidad, @DecimalMin("0") @DecimalMax("100") BigDecimal porcentajeBonificacion, @Size(max = 255) String descripcion)`; `CambioEstadoRequestDTO(@NotBlank String estado, @Size(max = 500) String observaciones)`. Mensajes en español explícitos en cada anotación. El mismo DTO sirve a `POST` y `PUT` (reemplazo completo).
- **Por qué `BigDecimal` para cantidad y bonificación**: Jackson convierte el número JSON sin pasar por binario (`0.1` llega como `0.1`), un `BigDecimal` siempre es finito y el cálculo empieza decimal. Con `Double`, `1e400` llegaría como `Infinity`.
- **Rangos** (consigna, Parte A, y RN-VA-02): cantidad `> 0` (sin máximo, sin exigir entero); bonificación opcional `0 ≤ b ≤ 100`, omitida = 0; descripción ≤ 255 (256 → 400); observaciones ≤ 500.
- **Descripción en blanco**: la consigna solo dice «si es nula»; se trata también la cadena en blanco como ausente para no guardar renglones con descripción vacía (decisión menor, documentada en el informe).
- Alternativa descartada: los DTO mutables con setters de la semilla (sin validación declarativa y con validación manual en el servicio).

### D2 — Cálculo en una clase pura `CalculadoraImportes`
`service/CalculadoraImportes` (sin Spring ni JPA) recibe cantidad, precio, bonificación y alícuota como `BigDecimal` y devuelve un `record ImportesRenglon(neto, iva, subtotal)` ya redondeados; un método suma subtotales. Orden exacto (DD-04, RN-IM-04..07):
1. `netoSinRedondear = cantidad × precio × (1 − bonificación.movePointLeft(2))` — `movePointLeft(2)` divide por 100 sin división inexacta.
2. `iva = netoSinRedondear × alícuota.movePointLeft(2)` → `setScale(2, HALF_UP)`.
3. `neto = netoSinRedondear.setScale(2, HALF_UP)`.
4. `subtotal = neto + iva`; `total = Σ subtotal`; `saldo = total − cobrado` (cobrado `BigDecimal.ZERO`, SU-06).
- **Casos de triangulación verificados con JDK 17 antes de proponer**: `5 × 20.023` (neto sin redondear 100.115) → IVA **21.02** con el orden correcto y 21.03 si se redondea el neto primero; `3 × 33.335` (IN-08) → neto **100.01** con `HALF_UP` (el ejemplo de IN-08 no distingue el orden: el IVA da 21.00 en ambos; sirve para distinguir `HALF_UP` de `HALF_EVEN`); `2 × 3000` con 10 % → 5400.00 / 1134.00 / 6534.00; alícuota 10.5 → 840.00. Los valores esperados de las pruebas son **literales**, nunca recalculados como el código.
- **Precio y alícuota**: `precio = BigDecimal.valueOf(precioVenta)` tras comprobar `Double.isFinite`; `alícuota = BigDecimal.valueOf(articulo.getAlicuotaIva())` o `facturacion.iva-por-defecto` (21) si es nula.
- Alternativa descartada: el cálculo de la semilla (precio con IVA incluido dividido por 1,21 y acumulación en `double`): contradice la consigna.

### D3 — Control de ida y vuelta en `ImporteDouble`
`service/ImporteDouble.aDouble(BigDecimal valor, String campo)`: `double d = valor.doubleValue()`; si `!Double.isFinite(d)` o `BigDecimal.valueOf(d).compareTo(valor) != 0` → `ImporteNoRepresentableException(campo, valor)` (subclase de `ReglaNegocioException`, → 409). `compareTo` (no `equals`) porque `7260.00` y `7260.0` difieren en escala pero no en valor. Se aplica a todo lo que va a un campo `double`: cantidad, bonificación, precio, neto, IVA, subtotal, total y saldo. Todas las conversiones se hacen **antes** de tocar la entidad administrada, así un 409 no deja la factura a medio modificar (y la transacción igual se revierte). Caso de rechazo verificado con JDK 17: precio `1234567890123456.8`, cantidad 1, alícuota 21 → IVA `259259256925925.93` no es representable (el `double` más cercano se imprime `...925.94`) → 409. `Math.round` queda prohibido (regla dura); la revisión final lo busca en `src/main`.

### D4 — `FacturaService` con lectura y escritura
Se amplía el servicio existente (la consigna nombra `FacturaService` y el `DataLoader` lo usa): clase con `@Transactional(readOnly = true)`, métodos de escritura con `@Transactional`. Métodos: `crear(FacturaRequestDTO)`, `actualizar(Long, FacturaRequestDTO)`, `cambiarEstado(Long, CambioEstadoRequestDTO)`, `eliminar(Long)` (nombres consultados en la referencia; código propio). Todos devuelven `FacturaReporteDTO` armado **dentro** de la transacción (`open-in-view: false`) con el mismo contenido que la proyección JPQL (`Consumidor Final` si no hay cliente, `cantidadItems = detalles.size()`), salvo `eliminar` que no devuelve nada. Colaboradores nuevos: repositorios de catálogos, `CalculadoraImportes`, `FacturacionProperties`, `GeneradorCae`, `Clock`.
- **Orden dentro de cada escritura**: (1) bloquear la factura (excepto `crear`), 404 si no existe; (2) verificar estado (409); (3) resolver **todas** las referencias (404, en orden cabecera → renglones) y calcular y convertir **todos** los importes (409); (4) recién entonces modificar la entidad. Así se cumple «resolver los identificadores antes de completar la operación» y el rollback es trivial.
- `crear`: estado BORRADOR, número/CAE/vencimiento/resultado nulos, `fechaEmision` del request o `LocalDate.now(reloj)`, `save` → id por `IDENTITY`.
- `actualizar`: reemplaza cliente, condición, punto de venta, moneda, fecha (o hoy si viene nula: reemplazo completo, igual que el alta), observaciones y renglones con `reemplazarDetalles` (misma colección, `orphanRemoval`), recalcula y llama a `registrarModificacion()` (D9).
- `eliminar`: bloquea, verifica BORRADOR y `delete` (cascada a renglones; catálogos sin cascada).
- Alternativa descartada: un `FacturaEscrituraService` separado; divide el caso de uso sin beneficio y la consigna nombra uno solo.

### D5 — Estados y transiciones en el enum
`EstadoFactura.puedePasarA(EstadoFactura destino)` codifica la tabla (solo BORRADOR→EMITIDA y EMITIDA→ANULADA); se prueba con `@ParameterizedTest` sobre las 9 combinaciones. `cambiarEstado` normaliza el texto (`strip` + `toUpperCase(Locale.ROOT)`, igual que el filtro de la etapa 3); un nombre desconocido → `ParametroInvalidoException` → **400** (IN-01; la referencia usa 409; la consigna no lo define: queda **pendiente de confirmar con el docente**, no bloquea). Un nombre válido con transición prohibida → `ReglaNegocioException` → **409** con estado actual y pedido en el detalle. Observación: `isBlank` → no se toca; si no, reemplaza.

### D6 — Emisión: numeración y CAE
Dentro de `cambiarEstado` → EMITIDA, con la factura ya bloqueada y en BORRADOR:
1. Sin renglones → 409.
2. `puntoVentaRepository.bloquearPorId(pv.getId())` (`PESSIMISTIC_WRITE`).
3. `facturaRepository.maximoNumero(pv)` (`select coalesce(max(f.numero), 0) ... where f.puntoVenta = :pv`) + 1. La consulta se ejecuta **después** del bloqueo y, con aislamiento READ COMMITTED (por defecto en H2 y PostgreSQL), cada sentencia ve lo confirmado por la transacción que tenía el bloqueo antes.
4. `cae = generadorCae.generar()`, `caeFechaVencimiento = LocalDate.now(reloj).plusDays(10)`, `resultadoAfip = "A"`, estado EMITIDA. La fecha de emisión **no** se cambia (la consigna no lo pide).
- `GeneradorCae` es una interfaz de un método; `GeneradorCaeAleatorio(RandomGenerator fuente)` devuelve `String.format("%014d", fuente.nextLong(100_000_000_000_000L))`; el bean usa `SecureRandom`. Las pruebas unitarias pasan una fuente fija (42 → `00000000000042`) y las de integración reemplazan el bean por uno fijo con `@Primary` (IN-09). El CAE se genera **después** de numerar, así una falla del generador demuestra que el rollback devuelve el número.
- Alternativa descartada: una tabla de secuencias por punto de venta (más concurrente, pero la consigna pide explícitamente bloquear el punto de venta y usar el máximo).

### D7 — Bloqueos pesimistas en los repositorios
`FacturaVentaRepository.buscarParaActualizar(Long id)` y `PuntoVentaRepository.bloquearPorId(Long id)`: `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query("select ... where x.id = :id")` → `Optional`. Hibernate genera `select ... for update` en H2 y `for update` / `for no key update` en PostgreSQL (el informe registra la sentencia **real** observada con `show-sql`). Orden de adquisición fijo: factura → punto de venta (solo al emitir); `PUT`/`DELETE` bloquean solo la factura, así no hay ciclos ni interbloqueos entre operaciones de la aplicación. El bloqueo dura hasta el commit o rollback.
- **H2 frente a PostgreSQL** (RN-CC-05, SU-08): H2 (MVStore) bloquea la fila con `FOR UPDATE` y aborta la espera al vencer `LOCK_TIMEOUT` (excepción de bloqueo); PostgreSQL espera indefinidamente salvo `lock_timeout` y detecta interbloqueos por su cuenta. Ninguno de los dos admite `FOR UPDATE` junto a un agregado como `MAX`: por eso se bloquea la fila del punto de venta y el máximo se consulta aparte. Un resultado en H2 no prueba PostgreSQL: por eso la corrida separada (D8).
- No se agrega `jakarta.persistence.lock.timeout`: su soporte difiere por dialecto y ocultaría la diferencia que hay que documentar; las pruebas acotan la espera por su cuenta (D8).

### D8 — Suite de concurrencia: H2 siempre, PostgreSQL a demanda
- Clases en `src/test/java/.../concurrencia/` con `@Tag("concurrencia")`, `@SpringBootTest`, `@ActiveProfiles("test")`, **sin** `@Transactional` (las transacciones tienen que confirmarse de verdad).
- `MotorDePruebas` (soporte de pruebas) con un `@DynamicPropertySource`: si la propiedad de sistema `facturacion.pruebas.motor` es `postgres`, registra `spring.datasource.url` (de `facturacion.pruebas.postgres-url`, por defecto `jdbc:postgresql://localhost:5433/facturacion`), driver, usuario y clave `facturacion`, y deshabilita la consola H2; si no, registra una H2 propia `jdbc:h2:mem:concurrencia;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000` (espera de bloqueo holgada para que la cola de ocho hilos no venza por lentitud de la máquina).
- Una prueba `motorEfectivo` lee `DatabaseMetaData` y **afirma** que el producto es el pedido (`H2` o `PostgreSQL`), imprimiendo nombre y versión: es la evidencia del motor y falla si la corrida «PostgreSQL» terminó conectada a otro motor.
- Hilos reales: `ExecutorService` de N hilos, `CountDownLatch` de largada, cada `Future.get(30, SECONDS)`; un timeout hace fallar la prueba nombrando el hilo. Para «edición que espera a una emisión», un `TransactionTemplate` toma el bloqueo de la factura y lo retiene con un `CountDownLatch`; se comprueba que el `PUT` (por servicio, en otro hilo) **no** terminó tras ~500 ms, se emite, se confirma y se espera el 409.
- Datos propios: puntos de venta con números altos (9001, 9002), usuarios buscados o creados (`sistema`), y limpieza en `@AfterEach` solo de lo creado por la prueba, porque la base PostgreSQL puede tener datos de corridas anteriores de la app.
- **Gradle**: tarea `testPostgres` (`Test`, grupo `verification`) con las clases y el classpath de `test`, `useJUnitPlatform { includeTags 'concurrencia' }`, `user.timezone` de Argentina y `systemProperty 'facturacion.pruebas.motor', 'postgres'` (URL sobrescribible con `-PpostgresUrl=...`). No se engancha a `test`, `check` ni `build`: `./gradlew clean test` nunca necesita el servidor. Reportes separados: `build/reports/tests/test` (H2) y `build/reports/tests/testPostgres` (PostgreSQL). Ninguna dependencia nueva: el driver PostgreSQL ya está como `runtimeOnly`.
- Alternativas descartadas: Testcontainers/Docker (Docker no funciona aquí); un perfil Spring activado por variable de entorno (`@ActiveProfiles` de la prueba lo pisaría y es frágil); correr toda la batería contra PostgreSQL (otras pruebas asumen H2).

### D9 — Auditoría: interceptor + callbacks
- `config/UsuarioActualInterceptor implements HandlerInterceptor` (nombre consultado en la referencia): `preHandle` lee `X-Usuario`, aplica `strip`; si no está en blanco busca `usuarioRepository.findByUsuario`; si falta o no existe usa `facturacion.usuario-por-defecto` (`sistema`); fija `UsuarioActual` y devuelve `true`. `afterCompletion` llama **siempre** a `UsuarioActual.clear()` (Spring lo invoca también cuando el handler lanzó una excepción, si `preHandle` devolvió `true`). Se registra en `config/WebConfig implements WebMvcConfigurer` para `/api/**`.
- Si tampoco existe `sistema` (base sin datos, perfil `test`), el contexto queda vacío: las lecturas funcionan y una escritura falla por la restricción `NOT NULL` del usuario de carga (falla temprana, coherente con la etapa 1). Las pruebas de escritura crean `sistema`.
- `AuditoriaApp`: `@PreUpdate completarModificacion()` (fecha y usuario de modificación desde el contexto); `registrarModificacion()` público que hace lo mismo y deja la cabecera sucia, para que cada `PUT` aceptado actualice la cabecera aunque solo cambien los renglones (RN-AU-05: sin él, Hibernate no emite `UPDATE` de la cabecera y `@PreUpdate` no se dispara); `darDeBaja()` (usuario y fecha de baja). **Política estricta (decidida por el estudiante, 2026-11-17)**: `@PreUpdate` asigna siempre el usuario del contexto; con el contexto vacío el `UPDATE` falla por `NOT NULL`, igual que un alta (una auditoría que atribuye en silencio la modificación a otro usuario es peor que una que falla). En peticiones HTTP no cambia nada: el interceptor limpia en `afterCompletion`, después de la transacción. Quien abre una transacción fuera de HTTP (el `DataLoader`) debe hacer `flush()` antes de limpiar el contexto. Las marcas de auditoría siguen con `LocalDateTime.now()` (instante real del servidor, como en la etapa 1); las fechas de **negocio** (emisión por defecto, vencimiento, anulación) usan el `Clock` inyectado.
- `X-Usuario` es identidad **declarada**, no autenticación (RN-AU-07): se documenta en el informe junto con CORS.
- Alternativa descartada: Spring Data `AuditingEntityListener` + `AuditorAware` (agrega `@EnableJpaAuditing` y no resuelve la baja ni la modificación explícita; además la etapa 1 ya fijó `@PrePersist` propio).

### D10 — Propiedades de la aplicación
`config/FacturacionProperties` (`@ConfigurationProperties("facturacion")`, `record` con `@DefaultValue("21") BigDecimal ivaPorDefecto` y `@DefaultValue("sistema") String usuarioPorDefecto`) y las claves `facturacion.iva-por-defecto` y `facturacion.usuario-por-defecto` en `application.yml`. `CorsProperties` conserva su prefijo `facturacion.cors`.

### D11 — Errores: 400 por campo, JSON ilegible y 409
En `ApiExceptionHandler`:
- `handleMethodArgumentNotValid` → 400 «Datos inválidos» con propiedad `errores` (`Map<String,String>` ordenado, ruta del campo → mensaje; si un campo tiene varios errores se unen con `; `).
- `handleHttpMessageNotReadable` → 400 «Cuerpo ilegible» con detalle propio: si la causa es un `MismatchedInputException` con ruta, «El campo 'detalles[0].cantidad' tiene un valor incompatible»; si no, «El cuerpo de la petición falta o no es un JSON válido». Nunca `ex.getMessage()`.
- `ReglaNegocioException` (y su subclase de importe) → 409 «Conflicto con el estado de la factura» / «Importe no representable».
- 404 de referencias reutiliza `RecursoNoEncontradoException` con el nombre del recurso. De paso se corrige el género del mensaje («no encontrado/a») si una prueba lo exige; si no, queda como está (nota de la etapa 3).

### D12 — Contrato HTTP
`POST` → `ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()).body(dto)`; `PUT` y `PATCH /{id}/estado` → `200`; `DELETE` → `ResponseEntity.noContent().build()`. Todos con `@Valid @RequestBody`. Los campos desconocidos del JSON se ignoran (comportamiento por defecto de Spring Boot), por eso un `precioUnitario` enviado no tiene efecto.

### D13 — CORS
`allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")`, `allowedHeaders("Content-Type", "X-Usuario")` (antes quedaba el comodín por defecto: se explicita), `exposedHeaders("Content-Disposition", "Location")`. Sigue sin credenciales y sin autenticar.

### D14 — `DataLoader` integrado (DD-11)
Se mantienen `@Profile("!test")`, `@Transactional` en `run`, la omisión si hay usuarios y `UsuarioActual.set(sistema)` / `clear()` en `finally`. Usuarios, catálogos y clientes siguen por repositorios. Las seis facturas se crean con `facturaService.crear(...)` (fechas `LocalDate.now(reloj).minusDays(n)` como hoy) y se emiten/anulan con `cambiarEstado`, en este orden para reproducir la numeración: PV1 emite F1 (2 martillos, Juan) → 1, F2 (televisor, sin cliente) → 2, F3 (3 cables, María) → 3 y se anula con un motivo; PV2 emite F4 (cable, dólares) → 1; F5 (martillo + 2 cables, Juan) y F6 (televisor, sin cliente) quedan BORRADOR. Los métodos del servicio se unen a la transacción del loader (propagación `REQUIRED`): toda la carga sigue siendo atómica. Como los precios son enteros y la alícuota es 21 %, **los importes calculados coinciden con los literales anteriores** (7260, 72600, 29040, 9680, 22990, 72600; verificado con JDK 17); cambian el CAE (aleatorio), el vencimiento (hoy + 10 en lugar de fecha de emisión + 10) y la fecha de anulación (hoy en lugar de hace 29 días), y aparece la baja lógica.

### D15 — Pruebas existentes que cambian y por qué
| Prueba | Cambio | Motivo |
|---|---|---|
| `config/DataLoaderTest` | `@DataJpaTest @Import(DataLoader)` → `@SpringBootTest @AutoConfigureTestDatabase @Transactional` (base embebida con nombre único, sin perfil `test` para que el loader corra); el vencimiento esperado pasa de `fechaEmision + 10` a `LocalDate.now(reloj) + 10`; se agregan numeración, importes calculados y baja | El loader ahora necesita `FacturaService` y sus colaboradores; la consigna fija el vencimiento desde la fecha actual |
| `config/DataLoaderFalloTest` | mismo cambio de anotaciones (perfil `test`, `NOT_SUPPORTED`); se agrega el caso de falla del generador de CAE (spec `datos-de-ejemplo`) | `createBean(DataLoader)` necesita el servicio en el contexto |
| `config/DataLoaderPerfilTest` | se espera **sin cambios** (el loader no se instancia con perfil `test`); verificar | — |
| `controller/FacturaRestControllerTest` | `@MockBean UsuarioRepository`; los casos 405 de `POST /api/facturas` y `DELETE /api/facturas/1` pasan a `PUT /api/facturas` y `DELETE /api/facturas` | El interceptor entra en el slice `@WebMvcTest`; esas rutas ahora existen (spec `api-errores` modificada) |
| `controller/FacturaApiIntegracionTest` | el caso `"DELETE, /api/facturas/1, 405"` pasa a `"PUT, /api/facturas, 405"` | ídem |
| `controller/CatalogoRestControllerTest`, `config/CorsConfigTest`, `config/CorsConfigPropiedadTest` | `@MockBean UsuarioRepository` (y en `CorsConfigTest` los casos nuevos de métodos y encabezados) | El interceptor entra en el slice |
| `service/FacturaServiceTest` | agregar `@Mock` de los colaboradores nuevos solo si `@InjectMocks` deja de construir el servicio; sus aserciones no cambian | Constructor ampliado |

Ninguna prueba de la API ni de reportes usa importes del `DataLoader` (arman sus propios datos), así que no cambian por el cálculo. Las expectativas de la prueba manual de la etapa 3 (6 facturas, 3 emitidas, orden, totales) siguen valiendo; solo cambian CAE, vencimiento y fecha de anulación.

### D16 — Inventario de la semilla (DD-13) y de la referencia
| Semilla `api-facturas` | Destino |
|---|---|
| `FacturaService.crear/actualizar/eliminar`: transacción por operación, `buscar(clase, id)` → `RecursoNoEncontradoException`, `getDetalles().clear()` + agregar, eliminación física con cascada, descripción por defecto con la denominación, consulta de máximo número | **Adaptado**: repositorios en lugar de `EntityManager` genérico, bloqueos, orden de verificación (D4), `reemplazarDetalles`, `coalesce(max)` tras bloquear el punto de venta |
| `FacturaRequestDTO`, `FacturaDetalleRequestDTO` (ids en lugar de entidades, bonificación opcional) | **Adaptado** a `record` con Bean Validation; `articuloId` → `listaPrecioArticuloId`; sin `estado` ni `usuarioId` en el request |
| Validación manual en el servicio (`validarDetalles`), `IllegalArgumentException` → 400 | **Reemplazado** por Bean Validation + 400 con `errores` |
| Cálculo con precio con IVA incluido (÷ 1,21), suma en `double`, `redondear(double)` | **Descartado**: contradice la consigna (D2, D3) |
| `PUT` parcial (solo lo informado) y cambio de estado dentro del `PUT`; alta directa como EMITIDA; usuario por `usuarioId` o «el primero» | **Descartado**: reemplazo completo, `PATCH /estado` separado, alta como BORRADOR, usuario por `X-Usuario` |
| Estados, bloqueos, numeración con bloqueo, CAE, control de ida y vuelta, interceptor, `@PreUpdate`/baja, suite de concurrencia, tarea `testPostgres` | **Nuevo** (no existe en la semilla) |

Referencia del docente (`arca-spirng`), consultada **solo para nombres y forma del contrato**, sin copiar código: `UsuarioActualInterceptor`, `WebConfig`, `CambioEstadoRequestDTO`, `ReglaNegocioException`, nombres de métodos `crear/actualizar/cambiarEstado/eliminar`, la existencia de consultas con `@Lock(PESSIMISTIC_WRITE)` en los repositorios de factura y punto de venta y un prefijo de propiedades `facturacion`. Diferencias deliberadas: estado desconocido 400 (ella 409), `BigDecimal` en el request, generador de CAE inyectable, propiedades separadas de `CorsProperties`.

## Risks / Trade-offs

- [Pruebas de concurrencia intermitentes por tiempos] → arranque con `CountDownLatch`, esperas acotadas, `LOCK_TIMEOUT=10000` en H2, aserciones sobre resultados (conjunto de números) y no sobre el orden; la espera de «no terminó tras 500 ms» solo prueba bloqueo si el hilo efectivamente quedó esperando, por eso además se verifica el 409 final.
- [`@PreUpdate` no se dispara si la cabecera no cambió] → `registrarModificacion()` explícito en cada `PUT` y una prueba que cambia solo un renglón.
- [El `ThreadLocal` se filtra entre peticiones del pool de Tomcat] → limpieza en `afterCompletion` y prueba de «sin fuga» en el mismo hilo (MockMvc corre en el hilo de la prueba).
- [La base PostgreSQL tiene datos previos] → datos propios con números de punto de venta altos y limpieza selectiva; no se vacía la base.
- [El servidor PostgreSQL queda encendido o la app ocupando 8080] → tareas con arranque/parada explícitos y verificación de puertos libres (`netstat`).
- [La decisión 400 para estado desconocido difiere de la referencia] → documentada como pendiente (IN-01); cambiarla es tocar un handler y un caso de prueba.
- [Límite de representabilidad poco intuitivo] → el informe explica con el caso concreto por qué `7260.00` pasa y `259259256925925.93` no.
- [Servicio con muchas dependencias] → el cálculo, la conversión y el CAE quedan fuera en clases puras probadas por unidad; el servicio solo orquesta.

## Migration Plan

Sin migración de datos: H2 se recrea en cada arranque y `ddl-auto: update` no necesita columnas nuevas (las de baja y CAE ya existen desde la etapa 1). En la base PostgreSQL portátil, `update` tampoco agrega columnas. Rollback del change: volver al tag `etapa-3`.

## Open Questions

- **IN-01** (no bloqueante): ¿estado desconocido en `PATCH` es 400 (implementado) o 409 (referencia)? Consultar al docente; cambia un handler y una prueba.
- **IN-03** (heredada, no bloqueante): código del PDF con glifo no soportado (400 implementado en la etapa 3).
