# Etapa 4 - Escritura, estados y concurrencia

> Informe de la etapa 4. Los números de la sección «Verificaciones y entorno» son los de la verificación final (2026-11-18).

## Contratos de entrada

`dto/FacturaRequestDTO` y `dto/FacturaDetalleRequestDTO` son `record` con Bean Validation y mensajes en español en cada anotación; `dto/CambioEstadoRequestDTO` (estado no vacío, observaciones ≤ 500) sirve al `PATCH` de estado. El mismo DTO sirve para `POST` y `PUT` (el `PUT` reemplaza todo). Llevan identificadores, nunca entidades, y no admiten precio, estado, número, CAE ni importes: los campos desconocidos del JSON se ignoran (comportamiento por defecto de Spring Boot), así que un `precioUnitario` enviado no tiene efecto.

- **Por qué `BigDecimal` para cantidad y bonificación**: Jackson convierte el número JSON sin pasar por binario (`0.1` llega como `0.1`), un `BigDecimal` siempre es finito (con `Double`, `1e400` llegaría como `Infinity`) y el cálculo empieza decimal.
- **Rangos** (consigna, Parte A, y RN-VA-01/02): `condicionIvaId`, `puntoVentaId`, `tipoMonedaId` obligatorios; `clienteId` opcional (nulo = «Consumidor Final»); `fechaEmision` opcional (hoy); `observaciones` ≤ 500; `detalles` no vacía y sin elementos nulos; renglón con `listaPrecioArticuloId` obligatorio (el de la asociación lista-artículo, no el del artículo), `cantidad` > 0 (sin máximo, admite fracciones), `porcentajeBonificacion` opcional entre 0 y 100 (omitida = 0) y `descripcion` ≤ 255 (255 pasa, 256 → 400).
- **Descripción en blanco**: la consigna solo dice «si es nula» (se toma la denominación del artículo); se trata también la cadena en blanco como ausente para no guardar renglones con descripción vacía (decisión menor mía).

## Errores y códigos HTTP

Todos salen como `application/problem+json` con `timestamp` (reloj inyectado). Los maneja `exception/ApiExceptionHandler`.

| Situación | Código | Origen |
|---|---|---|
| Validación de campos fallida | 400 «Datos inválidos» con `errores` (ruta del campo → mensaje; varios en un campo se unen con `; `) | `MethodArgumentNotValidException` (`handleMethodArgumentNotValid`) |
| JSON malformado, tipo incompatible o cuerpo ausente | 400 «Cuerpo ilegible», sin clases, línea ni columna del analizador; si es un tipo incompatible, nombra la ruta (`detalles[0].cantidad`) | `HttpMessageNotReadableException` (`handleHttpMessageNotReadable`, nunca `ex.getMessage()`) |
| Parámetro inválido (filtros, estado desconocido en `PATCH`: IN-01) | 400 | `ParametroInvalidoException` |
| Referencia o factura inexistente | 404 con el id buscado | `RecursoNoEncontradoException` |
| Operación incompatible con el estado | 409 «Conflicto con el estado de la factura» | `ReglaNegocioException` |
| Importe que no entra en un `double` | 409 «Importe no representable» | `ImporteNoRepresentableException` |
| Método no admitido, error inesperado | 405, 500 (detalle genérico) | Spring MVC y red de seguridad |

Pruebas de validación (límites 255/256, 500/501, cantidad, bonificación, renglones) **de caracterización**: pasaron al escribirse porque las anotaciones ya estaban en los `record` (D1 pide crearlas junto con el `POST`). Se validaron con 13 mutaciones temporales (quitar `@Valid` del controlador o del anidado; quitar o ensanchar `@Size`, `@NotEmpty`, `@NotNull` de elemento, `@Positive`, `@DecimalMin`, `@DecimalMax`, `@NotNull` de lista y de cantidad; cantidad entera con `@Min(1)`): cada una hizo fallar al menos una prueba. El 404 y el 409 de `ReglaNegocioException` salen del handler; para el 404 la mutación fue quitar su handler.

## Cálculo y redondeo

`service/CalculadoraImportes` es una clase pura (sin Spring ni JPA) que trabaja solo con `BigDecimal`. Orden fijo, el de la consigna (Parte B, puntos 1 a 3):

1. neto sin redondear = cantidad × precio × (1 − bonificación / 100);
2. IVA = neto sin redondear × alícuota / 100, redondeado a 2 decimales con `HALF_UP`;
3. neto = neto sin redondear, redondeado por separado a 2 decimales con `HALF_UP`;
4. subtotal = neto + IVA (ya redondeados); total = suma de subtotales; saldo = total − cobrado (cobrado 0, no hay pagos).

**Por qué `movePointLeft(2)`**: dividir por 100 desplazando la coma es exacto; `divide` obligaría a elegir escala y modo de redondeo, y un redondeo escondido ahí rompería el orden de la consigna. **Por qué `HALF_UP`**: lo exige la consigna (el «medio centavo» sube); `HALF_EVEN` daría otro neto en casos como 100.005.

| Caso (cantidad × precio, bonificación, alícuota) | Neto sin redondear | Neto | IVA | Subtotal | Qué demuestra |
|---|---|---|---|---|---|
| 2 × 3000.00, 10 %, 21 % | 5400.0000 | 5400.00 | 1134.00 | 6534.00 | caso base con bonificación |
| 5 × 20.023, 0 %, 21 % | 100.115 | 100.12 | **21.02** | 121.14 | el orden cambia los centavos: redondear el neto antes daría IVA 21.03 |
| 3 × 33.335, 0 %, 21 % | 100.005 | **100.01** | 21.00 | 121.01 | distingue `HALF_UP` (100.01) de `HALF_EVEN` (100.00); **no** distingue el orden (el IVA da 21.00 de las dos formas) |
| 1 × 8000.00, 0 %, 10.5 % | 8000.00 | 8000.00 | 840.00 | 8840.00 | alícuota propia |
| 2 × 3000.00, sin bonificación, 21 % | 6000.00 | 6000.00 | 1260.00 | 7260.00 | alícuota por defecto / bonificación nula = 0 |
| 4 × 125.50, 100 %, 21 % | 0.00 | 0.00 | 0.00 | 0.00 | bonificación total |
| 1 × 0.005, 0 %, 21 % | 0.005 | 0.01 | 0.00 | 0.01 | límite: medio centavo |
| 1 × 100.00, 33.33 %, 21 % | 66.6700 | 66.67 | 14.00 | 80.67 | bonificación decimal |

Total: subtotales 3630.00 y 19360.00 → 22990.00, saldo 22990.00 con cobrado 0; lista vacía → 0.00. Los valores esperados de las pruebas son literales de la spec, nunca recalculados con el mismo código.

## Control de ida y vuelta (`BigDecimal` ↔ `double`)

El modelo conserva campos `double` (DD-03). `service/ImporteDouble.aDouble(valor, campo)` convierte y rechaza si `Double.isFinite` falla o si `BigDecimal.valueOf(d).compareTo(valor) != 0`; el rechazo es `ImporteNoRepresentableException` (subclase de `ReglaNegocioException`, se traduce a 409). Se usa `compareTo` y no `equals` porque `7260.00` y `7260.0` difieren en escala pero no en valor: `7260.00` pasa y se guarda `7260.0`. Caso de rechazo: IVA `259259256925925.93` (precio 1234567890123456.8 con alícuota 21 %); el `double` más cercano se imprime `…925.94`, así que se perdió un centavo y la operación completa se rechaza. `Math.round` no se usa (regla dura).

## Transacciones y bloqueos

`FacturaService` es `@Transactional(readOnly = true)` a nivel de clase y cada método de escritura se anota con `@Transactional` (consigna, Parte B, punto 6): una falla de negocio o de persistencia revierte la operación completa.

**Alta (`POST`)**: `crear` hace, en este orden, (1) resolver todas las referencias (condición de IVA, cliente, punto de venta, moneda y cada lista-artículo, en ese orden: 404 con el id buscado), (2) calcular con `CalculadoraImportes` y convertir cada importe con `ImporteDouble` (409), y (3) recién entonces construir la entidad (`BORRADOR`, número/CAE/vencimiento/resultado nulos, fecha del request u hoy con el reloj inyectado) y guardarla. «Resolver todo antes de modificar» cumple la consigna («resuelvan los identificadores antes de completar la operación») y hace trivial el rollback: ante un 404 o un 409 no hay nada a medias, y la transacción igual se revierte. La respuesta (`FacturaReporteDTO`, misma proyección que una fila del listado) se arma **dentro** de la transacción porque `open-in-view` está desactivado. Las pruebas de integración no son transaccionales, para que cada petición confirme o revierta de verdad; los conteos de facturas y renglones se hacen por JDBC.

**Edición (`PUT`) y eliminación (`DELETE`)**: orden fijo (D4): (1) `FacturaVentaRepository.buscarParaActualizar(id)` bloquea la fila (`@Lock(PESSIMISTIC_WRITE)`, la sentencia real que genera Hibernate en H2 es `select ... from factura_venta fv1_0 where fv1_0.id=? for update`, copiada de la salida de `show-sql` y comprobada por `BloqueosRepositorioTest`) y da 404 si no existe; (2) se verifica que sea `BORRADOR` (409 con el estado actual en el detalle); (3) se resuelven todas las referencias y se calcula todo; (4) recién entonces se modifica la entidad. Se bloquea **antes** de verificar el estado porque, si no, otra transacción podría emitir la factura entre la verificación y la modificación. Los renglones se reemplazan con `reemplazarDetalles`, que vacía la **misma** colección administrada (con `orphanRemoval = true` no se puede asignar otra lista: Hibernate falla) y agrega los nuevos manteniendo ambos extremos; los viejos se borran como huérfanos. El `PUT` reemplaza todo (también la fecha: omitida = hoy, igual que el alta) y llama a `registrarModificacion()` para que la cabecera quede auditada aunque solo cambien los renglones (RN-AU-05).

La eliminación es **física** y solo de borradores: la cascada alcanza a los renglones y no a los catálogos compartidos (la prueba cuenta cliente, contacto, domicilio, punto de venta, moneda, condición de IVA y listas-artículo antes y después). Una factura emitida no se borra: se anula (grupo 7). Ante un 404 o 409 la transacción se revierte y la factura queda idéntica (las pruebas comparan el detalle completo antes y después).

Pruebas de **caracterización** del grupo (pasaron al escribirse, porque el orden y el rollback ya estaban en `actualizar`/`eliminar`): rechazos 404/409/400 de la edición y de la baja, `reenviar los mismos datos`, `registrarModificacion` con el mismo contenido. Mutaciones: sin `exigirBorrador` (falla la de la emitida y la de la anulada), modificar antes de calcular con `noRollbackFor = RuntimeException` (fallan moneda inexistente, falla en el segundo renglón e importe no representable), sin `reemplazarDetalles` (falla el reemplazo), sin `registrarModificacion` (falla el caso «mismo contenido»; el de «solo renglones» **no** distingue porque cambiar la cantidad ya cambia el total de la cabecera y la deja sucia), `delete` reemplazado por un cambio de dato (falla la baja). Bloqueo: sin `@Lock` falla `BloqueosRepositorioTest`; la prueba «id inexistente → vacío» no tiene mutación útil (es el comportamiento de `Optional`).

**Bloqueos (qué, cuándo, modo, orden).** Toda escritura sobre una factura existente (`PUT`, `DELETE`, `PATCH`) empieza con `buscarParaActualizar` (`@Lock(PESSIMISTIC_WRITE)`, `select ... for update`) y mantiene el bloqueo hasta el commit o rollback; la emisión además bloquea la fila del punto de venta (`bloquearPorId`) **antes** de consultar el máximo. Orden de adquisición fijo: **factura, luego punto de venta**; `PUT` y `DELETE` bloquean solo la factura. Como todas las transacciones toman los bloqueos en el mismo orden y nunca hay dos facturas bloqueadas a la vez, no hay ciclos de espera: no hay interbloqueo posible entre operaciones de la aplicación. No se agregó `jakarta.persistence.lock.timeout`: su soporte difiere por dialecto y ocultaría justamente la diferencia que hay que documentar; las pruebas acotan la espera por su cuenta.

**Suite de concurrencia** (`src/test/.../concurrencia`, `@Tag("concurrencia")`, `@SpringBootTest`, sin `@Transactional`, hilos reales con compuerta de largada y `Future.get(30 s)`; cada prueba crea sus datos —puntos de venta 9001 y 9002, catálogos con prefijo `CONC`— y los borra al empezar y al terminar, sin tocar nada ajeno):

| Prueba | Qué comprueba | Validación (mutación temporal) |
|---|---|---|
| `EmisionConcurrenteTest.ochoEmisiones...` | 8 hilos emiten 8 borradores del mismo punto de venta: números exactamente 1 a 8, sin repetidos ni huecos | `bloquearPorId` → `findById`: falla (violación de unicidad `uk_factura_venta_punto_venta_numero`, 3 de 3 corridas en H2; también en PostgreSQL) |
| `...PuntosDeVentaDistintos...` | 4 + 4 emisiones en dos puntos: cada uno 1 a 4 | máximo global en lugar de por punto: falla |
| `...dosEmisionesDeLaMismaFactura...` | dos hilos emiten la misma factura: exactamente un éxito y un `ReglaNegocioException`, un número y un CAE | `buscarParaActualizar` → `findById`: falla (3 de 3 corridas en H2; también en PostgreSQL) |
| `EdicionContraEmisionTest` (edición y eliminación) | una transacción retiene el bloqueo de la factura, otro hilo hace `PUT` o `DELETE`: tras 500 ms **sigue esperando**; al emitir y confirmar la primera, recibe 409 y la factura emitida queda intacta | `buscarParaActualizar` → `findById`: fallan las dos (la operación no espera) |
| `UnicidadDeNumeroTest` | duplicado forzado por repositorio → violación de integridad; mismo número en otro punto de venta sí se permite | restricción única reemplazada: falla el duplicado |
| `EsperaAcotadaTest` | el utilitario hace fallar la prueba nombrando el hilo que no terminó en el máximo (no espera al hilo colgado) | mensaje sin el nombre del hilo: falla |
| `MotorEfectivoTest` | `DatabaseMetaData` informa e **insiste** en el motor pedido (H2 por defecto, PostgreSQL con `testPostgres`) e imprime nombre y versión | motor pedido distinto del efectivo: fallan las dos pruebas |

Las pruebas de emisión y de edición contra emisión pasaron al escribirse (el servicio ya tenía los bloqueos) y se declaran de **caracterización** validada con las mutaciones de la tabla; `EsperaAcotadaTest` y `MotorEfectivoTest` (URL de la H2 de concurrencia) nacieron en rojo por la aserción.

**Cómo acotan las esperas las pruebas**: cada resultado se espera con `Future.get(30 s)` y un timeout lanza un `AssertionError` que nombra al hilo; la compuerta del hilo retenedor también tiene máximo; la H2 de concurrencia usa `LOCK_TIMEOUT=10000` para que la cola de ocho hilos no venza por lentitud de la máquina; y los hilos se cancelan al terminar.

## Numeración y CAE

**Emisión** (`cambiarEstado` → `EMITIDA`), con la factura ya bloqueada y en `BORRADOR`: (1) sin renglones, 409; (2) `puntoVentaRepository.bloquearPorId` (`select ... from punto_venta ... where id=? for update`, observado en H2 con `show-sql`); (3) `facturaRepository.maximoNumero(puntoVenta)` (`select coalesce(max(f.numero), 0) ... where f.puntoVenta = :puntoVenta`) más uno; (4) CAE, vencimiento (fecha actual del reloj + 10 días), resultado `A`. La fecha de emisión **no** se cambia (la consigna no lo pide).

- **Por qué el máximo se consulta después del bloqueo**: dos emisiones simultáneas del mismo punto de venta se serializan en el `for update` del punto; la segunda espera, y como el aislamiento por defecto (READ COMMITTED en H2 y PostgreSQL) hace que cada sentencia vea lo confirmado, su consulta del máximo ya ve el número que acaba de confirmar la primera. Si se consultara el máximo antes de bloquear, ambas verían el mismo valor y repetirían número. No se puede bloquear con `max(...)` en la misma sentencia (ni H2 ni PostgreSQL admiten `FOR UPDATE` con un agregado): por eso se bloquea la **fila** del punto de venta y el máximo va aparte.
- **Unicidad como red de seguridad**: la restricción `uk_factura_venta_punto_venta_numero` impide guardar dos números iguales aunque el bloqueo fallara; no reemplaza al bloqueo (la segunda emisión fallaría con una violación en lugar de esperar).
- **Los borradores no consumen números** (número nulo, `max` los ignora) y **las anuladas conservan el suyo**, así que tras anular la 2 el siguiente es el 3.
- **CAE**: `GeneradorCae` es una interfaz de un método; `GeneradorCaeAleatorio(RandomGenerator)` devuelve `String.format("%014d", fuente.nextLong(100_000_000_000_000L))` (14 dígitos, ceros a la izquierda) y el bean de producción usa `SecureRandom`. Las pruebas unitarias le pasan una fuente fija (42 → `00000000000042`) o con semilla (`SplittableRandom(7)`), y las de integración reemplazan el bean por uno fijo con `@Primary` (`12345678901234`) que además puede configurarse para fallar (IN-09: CAE determinista en las pruebas). El CAE se genera **después** de numerar: así una falla del generador demuestra que el rollback devuelve el número (prueba `siFallaElGeneradorDeCae...`).
- Mutaciones: número global en lugar de por punto de venta, sin control de renglones vacíos, `%014d` → `%d`, tope 10^13, vencimiento +11, sin `@Transactional` rollback (`noRollbackFor`), cada una hace fallar al menos una prueba.

## Estados

`EstadoFactura.puedePasarA(destino)` codifica la tabla (probada con las 9 combinaciones y con «hay exactamente dos transiciones permitidas»):

| Desde \ Hacia | BORRADOR | EMITIDA | ANULADA |
|---|---|---|---|
| BORRADOR | 409 | **permitida** | 409 |
| EMITIDA | 409 | 409 | **permitida** |
| ANULADA | 409 | 409 | 409 |

`PATCH /api/facturas/{id}/estado` (`CambioEstadoRequestDTO`: `estado` no vacío, `observaciones` ≤ 500) hace: bloquear (404) → normalizar el texto (`strip` + mayúsculas con `Locale.ROOT`, igual que el filtro de la etapa 3) → nombre desconocido: **400** `ParametroInvalidoException` con los valores admitidos (**IN-01**, pendiente de confirmar con el docente; la referencia usa 409) → transición no permitida: **409** con el estado actual y el pedido en el detalle → emitir o anular. Una observación no en blanco reemplaza la de la factura; en blanco o ausente la deja.

**Anulación**: estado `ANULADA`, fecha de anulación = fecha actual del reloj, `darDeBaja()` (fecha y usuario de baja del contexto, es baja lógica: la fila sigue y aparece en `?estado=ANULADA`); se conservan número, CAE, importes y renglones. Emitidas y anuladas no se editan ni se borran (`PUT`/`DELETE` → 409, ver «Transacciones y bloqueos»). Un `X-Usuario` cualquiera puede emitir y anular: identidad declarada, no autenticación.

Pruebas del grupo 7 (todas de integración con el stack completo, sin transacción de prueba) **de caracterización** salvo `EstadoFacturaTest`, `GeneradorCaeAleatorioTest`, `BloqueosRepositorioTest` y la emisión básica, que nacieron en rojo: pasaron al escribirse porque `cambiarEstado` ya estaba implementado para la prueba de la emisión; se validaron con las mutaciones listadas arriba y en «Auditoría» (p. ej. sin `puedePasarA` fallan las siete combinaciones prohibidas; sin `darDeBaja` falla la auditoría de la baja).

## Auditoría

**Usuario por petición.** `config/UsuarioActualInterceptor` (registrado por `config/WebConfig` solo para `/api/**`) lee `X-Usuario` en `preHandle`, le aplica `strip` y busca al usuario con `UsuarioRepository.findByUsuario`. Resultados:

| `X-Usuario` | Contexto durante la petición |
|---|---|
| `acortez` o `"  acortez  "` (existe) | `acortez` |
| ausente, `""` o solo espacios | `sistema` (`facturacion.usuario-por-defecto`) |
| `intruso` (no existe) | `sistema` |
| `sistema` tampoco existe (base vacía) | vacío, sin excepción; una escritura falla por `NOT NULL` del usuario de carga (falla temprana, como en la etapa 1) |

**Por qué fallback y no rechazo**: el encabezado declara quién opera, no autentica (RN-AU-07; no hay login, la consigna lo deja fuera). Rechazar un usuario desconocido sería simular una autenticación que no existe. **`X-Usuario` no autentica**: cualquiera puede declarar cualquier usuario existente, igual que CORS, que solo es una política del navegador.

**Por qué `afterCompletion`**: el contexto es un `ThreadLocal` y los hilos de Tomcat se reutilizan; si el usuario de una petición quedara en el hilo, la siguiente petición sin encabezado se registraría a nombre de otro. Spring llama a `afterCompletion` siempre que `preHandle` devolvió `true`, incluso si el controlador lanzó una excepción (la prueba lo cubre con `ex == null` y `ex != null`, y por HTTP simulado con un 500).

**Por qué los `@WebMvcTest` necesitan `@MockBean UsuarioRepository`**: el slice carga los beans `HandlerInterceptor` y `WebMvcConfigurer`, así que instancia el interceptor, que depende del repositorio; sin el mock el contexto no arranca (se vio: 22 pruebas de `CatalogoRestControllerTest`, `CorsConfigTest` y `CorsConfigPropiedadTest` fallaban). Las aserciones de esas clases no cambiaron.

**Callbacks de `AuditoriaApp`.**
- `@PreUpdate completarModificacion()`: fecha de modificación actual y usuario de modificación **siempre del contexto**; no toca fecha de alta ni usuario de carga. **Política estricta** (la eligió el estudiante entre dos opciones): con el contexto vacío el `UPDATE` falla por el `NOT NULL`, igual que un alta. La alternativa descartada era conservar el usuario anterior; se rechazó porque una auditoría que atribuye en silencio una modificación a otro usuario es peor que una que falla: el error aparece al desarrollar y el dato nunca miente. En pedidos HTTP no cambia nada: el interceptor limpia en `afterCompletion`, que corre después de la transacción. Fuera de HTTP, quien abre la transacción debe hacer `flush()` antes de limpiar el contexto: el `DataLoader` lo hace (sin eso 13 pruebas del `DataLoader` fallaban, porque el flush ocurría al confirmar, con el contexto ya vacío). Prueba: `modificarConElContextoVacioEsRechazadoPorLaBase`; mutación (volver a conservar el anterior) la hace fallar.
- `registrarModificacion()`: `@PreUpdate` solo se dispara si Hibernate detecta cambios en la propia entidad; si en un `PUT` cambian solo los renglones, la cabecera no se actualiza y su auditoría quedaría vieja (RN-AU-05). El método actualiza fecha y usuario, lo que deja la cabecera sucia y fuerza el `UPDATE`. La prueba `sinRegistrarModificacion…` muestra el problema y `registrarModificacion…` la solución.
- `darDeBaja()`: fecha y usuario de baja desde el contexto; la fila sigue existiendo (baja lógica); una entidad recién persistida tiene baja nula.

**Hora real y reloj.** Las marcas de auditoría usan `LocalDateTime.now()` (instante real del servidor, como en la etapa 1); las fechas de **negocio** (emisión por defecto, vencimiento, anulación) usarán el `Clock` inyectado.

**Propiedades.** `config/FacturacionProperties` (`record`, `@DefaultValue`) con `facturacion.iva-por-defecto: 21` y `facturacion.usuario-por-defecto: sistema` en `application.yml`; `CorsProperties` conserva su prefijo propio.

**Mutaciones del grupo 3** (temporales, restauradas):

| Mutación | Pruebas que fallan |
|---|---|
| sin `strip` | `losEspaciosExterioresSeIgnoran` |
| sin usuario por defecto | cuatro casos de `ausenteVacioODesconocido…` y `sinUsuarioPorDefecto…` |
| `afterCompletion` vacío | dos de `afterCompletion…` y el de limpieza tras error por HTTP simulado |
| `afterCompletion` que limpia solo sin excepción | `afterCompletionLimpiaElContextoConExcepcion` y el de error por HTTP simulado |
| patrón `/otra/**` en `WebConfig` | `elUsuarioDeclaradoEstaEnElContexto…` |
| `@PreUpdate` desactivado | `modificacionCompletadaConElUsuario…` |
| `usuarioModificacion = null` | siete de `AuditoriaAppTest` (la línea también está en el alta) |
| `@PreUpdate` que conserva el usuario anterior con contexto vacío | `modificarConElContextoVacioEsRechazadoPorLaBase` |
| `registrarModificacion` vacío | `registrarModificacionActualiza…` |
| `registrarModificacion` llamado en el helper de la prueba de control | `sinRegistrarModificacion…` (valida la prueba de control) |
| `darDeBaja` con usuario de carga / sin fecha | `darDeBajaRegistra…` |
| el alta llama a `darDeBaja` | `bajaNulaAlCrear` (caracterización previa) |

Caracterización (pasaron al escribirse, validadas con las mutaciones anteriores): `elContextoQuedaVacioTambienCuandoLaPeticionTerminaEnError`, `sinRegistrarModificacionLaCabeceraConserva…` y `bajaNulaAlCrear`.

## CORS

`config/CorsConfig` admite ahora `GET`, `POST`, `PUT`, `PATCH` y `DELETE` desde los **mismos cuatro orígenes exactos** de la etapa 3 (`http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173`, `http://127.0.0.1:3000`, propiedad `facturacion.cors.allowed-origins`), con `allowedHeaders("Content-Type", "X-Usuario")` y `exposedHeaders("Content-Disposition", "Location")`. Antes los encabezados permitidos quedaban en el comodín por defecto; se explicitan para que un encabezado no previsto (por ejemplo `authorization`) dé 403 en el preflight. `Location` se expone para que el frontend lea la URL de la factura creada; `Content-Disposition` sigue exponiéndose para las descargas.

CORS lo aplica el navegador: **no autentica** (`curl` lo ignora), igual que `X-Usuario`, que solo **declara** quién opera. Ninguno de los dos reemplaza a un login, que está fuera de alcance. Pruebas (`CorsConfigTest`, contra la configuración real): preflight de cada método de escritura, preflight con `content-type, x-usuario`, preflight con `authorization` (403), origen no admitido (403) y `Location` expuesto en el `POST`; las de la etapa 3 (orígenes, propiedad sobrescrita, cliente sin navegador) siguen verdes. La aserción del preflight de `GET` pasó de «es exactamente `GET`» a «contiene `GET`», porque el encabezado ahora lista los cinco métodos. Mutaciones: `allowedHeaders("*")` (falla el 403 de `authorization`), sin `Location` y sin `PUT, PATCH` (fallan sus pruebas).

## `DataLoader` integrado

Con `@Profile("!test")`, la omisión si ya hay usuarios y el contexto de auditoría limpiado en `finally`, el `DataLoader` crea ahora las seis facturas con `FacturaService` (DD-11) y las cambia de estado con `cambiarEstado`, en este orden para reproducir la numeración: PV 1 emite F1 (2 martillos, Juan) → 1, F2 (televisor, sin cliente) → 2 y F3 (3 cables, María) → 3, y anula la F3 con un motivo; PV 2 emite F4 (cable, en dólares) → 1; F5 (martillo y 2 cables, Juan) y F6 (televisor, sin cliente) quedan en `BORRADOR`. Los métodos del servicio se unen a la transacción del loader (propagación `REQUIRED`), así que toda la carga sigue siendo atómica. Las fechas de emisión son `hoy − n días` con el `Clock` inyectado. **Antes de limpiar el contexto el loader hace `flush()`** (política estricta de auditoría: un `UPDATE` pendiente al confirmar la transacción, con el contexto ya vacío, violaría el `NOT NULL`).

Los importes calculados **coinciden** con los literales de `docs/etapa-1.md` (7260, 72600, 29040, 9680, 22990, 72600; precios enteros y alícuota 21 %); **cambian** el CAE (ahora aleatorio de 14 dígitos), el vencimiento (hoy + 10 en lugar de fecha de emisión + 10) y la fecha de anulación (hoy en lugar de hace 29 días), y aparece la baja lógica de la anulada (usuario `sistema`).

Pruebas que cambiaron y por qué (tabla D15 del design):

| Prueba | Cambio | Motivo |
|---|---|---|
| `DataLoaderTest` | `@DataJpaTest @Import(DataLoader)` → `@SpringBootTest @AutoConfigureTestDatabase @Transactional` (base embebida con nombre único, sin perfil `test` para que el loader corra); el vencimiento esperado pasa de `fechaEmision + 10` a `hoy + 10`; se agregan numeración real, importes calculados y baja lógica | el loader necesita `FacturaService` y sus colaboradores; la consigna fija el vencimiento desde la fecha actual |
| `DataLoaderFalloTest` | mismo cambio de anotaciones (perfil `test`, `NOT_SUPPORTED`); se agrega la carga con el `GeneradorCae` fallando: no quedan usuarios, puntos de venta ni facturas y el contexto está vacío | `createBean(DataLoader)` necesita el servicio en el contexto |
| `DataLoaderPerfilTest` | **sin cambios** (con perfil `test` el loader no existe) | — |

Mutaciones: sin `flush()` (fallan todas las del loader por el `NOT NULL`), sin `@Transactional` (fallan el rollback y el proxy), sin anular la F3, una emisión extra en PV 1 (numeración y conteos) y sin `clear()` (contexto residual). Caracterización (pasaron contra el loader provisorio): numeración real e importes calculados.

## Evidencia H2 frente a PostgreSQL

Reportes **separados**: `./gradlew test` deja el reporte en `build/reports/tests/test` (H2) y `./gradlew testPostgres` en `build/reports/tests/testPostgres` (PostgreSQL). La corrida normal **nunca** necesita el servidor: se verificó con el servidor apagado (`clean test`, puerto 5433 sin escucha) y la suite de concurrencia informó `[MOTOR] producto=H2 version=2.2.224`. `testPostgres` no se engancha a `test`, `check` ni `build` (tarea `Test` con `includeTags 'concurrencia'`, `user.timezone=America/Argentina/Buenos_Aires`, `facturacion.pruebas.motor=postgres`, URL sobrescribible con `-PpostgresUrl=...`).

| | H2 (`./gradlew test`) | PostgreSQL (`./gradlew testPostgres`) |
|---|---|---|
| Motor informado | `H2` 2.2.224 | `PostgreSQL` **16.9** (`select version()`: «PostgreSQL 16.9, compiled by Visual C++ build 1943, 64-bit»), `jdbc:postgresql://localhost:5433/facturacion` |
| Pruebas de la suite de concurrencia | 9 de 9 en verde (las mismas clases; más `EsperaAcotadaTest`, que no lleva la etiqueta) | **9 de 9 en verde**, 0 fallas, 0 omitidas |
| Tiempos por clase | Edición contra emisión 1.41 s, emisión concurrente 0.20 s (8 hilos: 0.08 s), unicidad 0.04 s | Edición contra emisión 1.89 s, emisión concurrente 0.35 s (8 hilos: 0.14 s), unicidad 0.09 s; tarea completa 11 s con el arranque de Spring |
| SQL de bloqueo de la factura | `... where fv1_0.id=? for update` | `... where fv1_0.id=? for no key update` |
| SQL de bloqueo del punto de venta | `... where pv1_0.id=? for update` | `... where pv1_0.id=? for no key update` |
| Mutaciones (sin bloqueo del punto / sin bloqueo de la factura) | detectadas | detectadas: sin bloqueo del punto fallan las dos pruebas de numeración; sin bloqueo de la factura fallan las tres de factura única y edición/eliminación |
| Datos que deja la corrida | memoria propia, se descarta | nada: tras dos corridas y dos mutaciones `punto_venta` 9001/9002, catálogos `CONC` y facturas ajenas (6 de la aplicación) quedaron como antes; `usuario sistema` ya existía y no se borra |

**Diferencias de comportamiento** (RN-CC-05, SU-08):
- **Sentencia de bloqueo**: Hibernate genera `for update` en H2 y `for no key update` en PostgreSQL (el modo de fila más débil que igual serializa a los escritores de la misma fila; no bloquea las claves foráneas que apuntan a ella).
- **Espera**: H2 aborta la espera al vencer `LOCK_TIMEOUT` (10 s en la suite) con una excepción de bloqueo; PostgreSQL, con la configuración local (`lock_timeout = 0`), espera **indefinidamente** y detecta interbloqueos por su cuenta (`deadlock_timeout` 1 s). Por eso las pruebas acotan la espera por su cuenta (`Future.get`), y ningún resultado de una prueba depende del timeout del motor. En esta corrida no se observó ningún timeout ni interbloqueo en ninguno de los dos.
- **Aislamiento**: ambos corren en READ COMMITTED por defecto (`show default_transaction_isolation` en PostgreSQL); el segundo hilo que espera el bloqueo del punto de venta, al obtenerlo, ve lo que confirmó el primero: eso es lo que hace correcta la consulta del máximo después del bloqueo.
- **Agregado con `FOR UPDATE`**: ninguno de los dos motores lo admite; se bloquea la fila del punto de venta y el máximo va aparte.
- Un resultado en H2 no prueba PostgreSQL: por eso la corrida separada. Lo que **no** se verificó: ningún otro motor ni versión de PostgreSQL, ni cargas mayores que 8 hilos, ni la corrida con PostgreSQL en otra máquina.

**Cómo repetirlo** (PostgreSQL portátil del estudiante, `C:\Users\zbook\DesarrolloSoftware\pg16-portatil`, puerto 5433, base y usuario `facturacion`):
1. Iniciar: `iniciar-pg16.bat` (si se lo lanza desde Git Bash, redirigir la salida: `cmd //c "C:\\Users\\...\\iniciar-pg16.bat" > /tmp/pgstart.log 2>&1 < /dev/null`) y comprobar con `pg_isready.exe -h 127.0.0.1 -p 5433`.
2. Correr: `JAVA_HOME=<JDK 17> ./gradlew testPostgres` (la salida imprime `[MOTOR] producto=PostgreSQL version=16.9`).
3. Detener: `detener-pg16.bat` y comprobar que 5433 quedó libre (`netstat -ano | findstr :5433`). No tocar el PostgreSQL 18 del puerto 5432.


## Semilla reutilizada (DD-13)

En este apply **no se volvió a abrir** `api-facturas`: el inventario D16 del design (hecho en la propuesta) fija qué se adapta y qué se descarta, y el código de `crear` se escribió desde la consigna y el design, detrás de pruebas en rojo.

| Semilla `api-facturas` | Destino |
|---|---|
| `FacturaService.crear` (transacción por operación, búsqueda por id con 404, descripción por defecto con la denominación) | **Adaptado** (idea, no código copiado): repositorios por catálogo en lugar de `EntityManager` genérico; orden «resolver todo, calcular, recién entonces modificar»; `reemplazarDetalles` sobre la misma colección |
| `FacturaRequestDTO`, `FacturaDetalleRequestDTO` (ids en lugar de entidades, bonificación opcional) | **Adaptado** a `record` con Bean Validation; `listaPrecioArticuloId`; sin estado ni usuario en el request |
| Validación manual y `IllegalArgumentException` → 400 | **Reemplazado** por Bean Validation y 400 con `errores` |
| Cálculo con precio con IVA incluido y suma en `double` | **Descartado**: contradice la consigna |
| Estados, bloqueos, numeración, CAE, control de ida y vuelta, interceptor, `@PreUpdate`/baja, concurrencia | **Nuevo** |

## Referencia del docente

`arca-spirng` se consultó **solo para nombres y forma del contrato** durante el diseño: `UsuarioActualInterceptor`, `WebConfig`, `CambioEstadoRequestDTO`, `ReglaNegocioException`, los nombres de métodos `crear/actualizar/cambiarEstado/eliminar`, la existencia de consultas con `@Lock(PESSIMISTIC_WRITE)` en los repositorios de factura y punto de venta y un prefijo de propiedades `facturacion`. **En este apply no se volvió a abrir** y no se copió código. Nos apartamos deliberadamente en: estado desconocido en `PATCH` → 400 (la referencia usa 409; ver IN-01), `BigDecimal` en el request, `GeneradorCae` inyectable, propiedades de negocio separadas de `CorsProperties`, y la política estricta de auditoría con el contexto vacío.

## Pendientes de confirmar con el docente

- **IN-01**: estado desconocido en `PATCH /estado` responde **400** (la referencia usa 409; la consigna no lo define). Cambiarlo es tocar un handler y un caso de prueba.
- **IN-03**: PDF con glifo no soportado responde **400** (heredado de la etapa 3).

## Verificaciones y entorno

Fecha: 2026-11-18. JDK 17.0.20.1 (Microsoft), Gradle 8.10.2, Node v24, PostgreSQL 16.9 portátil para la corrida de concurrencia (puerto 5433, apagado al terminar). Motor de la suite normal: **H2 en memoria**.

| Verificación | Motor | Resultado |
|---|---|---|
| Baseline al empezar (`./gradlew clean test`) | H2 | 320 pruebas, 0 fallas |
| `./gradlew clean test bootJar` al cerrar (con PostgreSQL **apagado**) | H2 | **503 pruebas, 0 fallas, 0 omitidas** (183 nuevas); `build/libs/` solo tiene `facturacion-arca-1.0.0.jar` |
| `./gradlew testPostgres` (suite de concurrencia) | PostgreSQL 16.9, puerto 5433 | **9 pruebas, 0 fallas** (corrida real del 2026-11-18; ver «Evidencia H2 frente a PostgreSQL») |
| Suite de concurrencia dentro de `./gradlew test` | H2 | 9 pruebas, informan `H2 2.2.224` |
| `cd frontend && npm test` | n/a | 7 pruebas en verde |
| Revisión de reglas duras en `src/main` | n/a | sin `@Data` ni `Math.round` (solo aparece en comentarios que lo prohíben); sin aritmética `double` en importes (la calculadora usa `BigDecimal`; los `double` son los campos del modelo y la conversión controlada); ningún controlador usa tipos de `model/`; los DTO se arman dentro de la transacción; ningún request acepta entidades ni precios |
| `openspec validate etapa-4-escritura-estados-concurrencia --strict` | n/a | válido |

**Prueba manual** (`java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/facturacion-arca-1.0.0.jar`, H2 y `DataLoader`; se detuvo la app y el puerto 8080 quedó libre). Se usó `curl` con las mismas peticiones de `requests.http`:

| Petición | Resultado |
|---|---|
| `POST /api/facturas` con `X-Usuario: acortez`, cliente 1 y dos renglones | 201, `Location: http://localhost:8080/api/facturas/7`, `BORRADOR`, `importeTotal` 22990.0, 2 ítems |
| `PUT /api/facturas/7` con `X-Usuario: mriquero` (un cable) | 200, `importeTotal` 9680.0, 1 ítem |
| `PATCH /api/facturas/7/estado` `{"estado":"EMITIDA"}` | 200, `numeroFactura` 4 (el loader ya había emitido 1 a 3 en el PV 1); el detalle tiene CAE de 14 dígitos, vencimiento hoy + 10 y resultado `A` |
| `PATCH /api/facturas/1/estado` `{"estado":"ANULADA","observaciones":"Error en el cliente"}` | 200, `ANULADA`, mismo número 1 |
| `DELETE /api/facturas/6` y luego `GET /api/facturas/6` | 204 sin cuerpo y 404 `problem+json` («Factura con id 6 no encontrado») |
| `POST` con descripción de 256 caracteres | 400 «Datos inválidos» con `errores["detalles[0].descripcion"]` |
| `PUT /api/facturas/2` (EMITIDA) | 409 «No se puede editar la factura 2: está en estado EMITIDA y solo se admite en estado BORRADOR» |
| `PATCH /api/facturas/5/estado` `{"estado":"ANULADA"}` (borrador) | 409 «No se puede pasar la factura 5 de BORRADOR a ANULADA…» |
| `PATCH` con `{"estado":"PAGADA"}` | 400 «Parámetro inválido» con los valores admitidos (IN-01) |
| `POST` con `tipoMonedaId` 999999 | 404 «Tipo de moneda con id 999999 no encontrado» |
| `POST` con JSON malformado | 400 «Cuerpo ilegible» sin detalles del analizador |
| `OPTIONS /api/facturas/1/estado` desde `http://localhost:5173` con `PATCH` y `content-type, x-usuario` | 200: `Allow-Methods: GET,POST,PUT,PATCH,DELETE`, `Allow-Headers: content-type, x-usuario`, `Expose-Headers: Content-Disposition, Location` |
| Preflight con `Access-Control-Request-Headers: authorization` | 403 |

No se repitió la prueba manual con el perfil `postgres` (la corrida real contra PostgreSQL de esta etapa es la suite de concurrencia).


**Pruebas de caracterización y mutaciones del grupo 2** (cada mutación se aplicó temporalmente y se restauró):

| Mutación | Pruebas que fallan |
|---|---|
| IVA sobre el neto ya redondeado | `elOrdenCambiaLosCentavos` |
| `HALF_UP` → `HALF_EVEN` | `medioCentavoHaciaArriba…`, `valoresLimite` (0.005) |
| bonificación nula sin tratar | `bonificacionNulaEsCero`, `alicuotaPorDefecto…` |
| bonificación sin `movePointLeft(2)` | `conBonificacion`, `bonificacionCien…`, `valoresLimite` (33.33) |
| alícuota con `movePointLeft(1)` | seis pruebas de cálculo |
| neto a 3 decimales | siete pruebas de cálculo |
| saldo = total + cobrado | `saldoEsTotalMenosCobrado` |
| suma que arranca en 1 | totales y lista vacía |
| `compareTo` → `equals` en `ImporteDouble` | `importeRepresentableSeGuardaIgual`, `otrosImportesRepresentables` (7260.00 y similares) |
| sin `Double.isFinite` | `valorNoFinitoSeRechaza` |
| sin la reconversión decimal | `importeConMasDigitos…` |

Caracterización (pasaron al escribirse): `valoresLimite` (validada con las mutaciones `HALF_EVEN` y bonificación sin `movePointLeft(2)`). Las demás nacieron en rojo por la aserción (el esqueleto devolvía ceros). Nota: la RED del grupo 2 escribió juntas las pruebas de 2.1, 2.3 y 2.4 y luego se implementó el cálculo completo, no un «Fake It» intermedio; la triangulación ya estaba escrita.

## Uso de IA

- Herramientas: Claude Code (Anthropic) con sub-agentes de OpenSpec (propuesta, diseño, tareas y ejecución de la etapa).
- Qué se pidió: la propuesta y el diseño de la etapa, las pruebas en rojo, el código mínimo para pasarlas, las mutaciones de validación de las pruebas de caracterización, la suite de concurrencia y la tarea `testPostgres`, la corrida contra el PostgreSQL 16.9 portátil, la prueba manual, `requests.http`, el README y este informe.
- Qué se revisó y qué se corrigió: revisé la propuesta antes de implementar y trabajé la etapa con cuatro puntos de revisión obligatorios, en los que el avance se detenía hasta que yo lo aprobara: el cálculo monetario con el control de ida y vuelta a `double`, la auditoría por request con `X-Usuario`, los estados con la numeración y el CAE, y los bloqueos pesimistas con la concurrencia. En cada punto leí el código y los casos de prueba, entre ellos el de `5 × 20,023`, que demuestra el orden del redondeo. En el punto de la auditoría corregí una decisión de la IA: había hecho que una modificación sin usuario en el contexto conservara el usuario anterior, y elegí la política estricta, en la que esa modificación falla como un alta, para que la auditoría nunca atribuya un cambio a quien no lo hizo. Para probar los bloqueos también contra PostgreSQL, aprobé descargar los binarios oficiales de PostgreSQL 16.9 y verifiqué que las 9 pruebas de concurrencia pasan en ese motor y en H2. Con una guía de revisión que me armó la IA leí además la validación de entrada, el reemplazo de borradores, el borrado, el `DataLoader` integrado con el servicio y la configuración de CORS. Quedan pendientes con el docente el 400 para un estado desconocido en el `PATCH` y para un glifo no soportado en el PDF.
- Los commits propuestos llevan la línea `Co-Authored-By`.
