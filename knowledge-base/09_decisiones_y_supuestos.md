# Decisiones y Supuestos

## Decisiones documentadas

### DD-01 — La consigna manda; la solución del docente es referencia
**Decisión**: ante diferencia entre `docs/consigna/tp-integrador-facturacion-arca.md` y `arca-spirng`, prevalece la consigna; lo que solo está en la referencia se marca `(ref)`.
**Contexto**: la referencia agrega detalles (campo `id` en el DTO de reporte, nombres de clases/columnas, códigos para glifos).
**Alternativas**: copiar la referencia; ignorarla. **Justificación**: la evaluación se hace contra la consigna; copiar la referencia contradice "debe poder explicar todo el código". **Trade-off**: algunas decisiones quedan abiertas (ver 10).

### DD-02 — Un change de OpenSpec por etapa, TDD estricto
**Decisión**: etapas 0 a 5 = 6 changes; cada requisito se implementa con ciclo rojo-verde-refactor.
**Justificación**: la consigna pide "pruebas que distingan un comportamiento correcto de uno defectuoso" y cada etapa conserva lo anterior (RN-GE-02). **Trade-off**: más lento al inicio.

### DD-03 — Cálculo monetario con `BigDecimal` y persistencia `double` con control round-trip
**Decisión**: calcular en BigDecimal, persistir `double` (campos del modelo) comprobando finitud y `BigDecimal.valueOf(d).compareTo(calculado) == 0`; fallar con 409.
**Alternativas**: cambiar columnas a `NUMERIC/BigDecimal` (más correcto, pero la consigna exige conservar `double`); `Math.round` (prohibido). **Justificación**: evita pérdida silenciosa de centavos. **Trade-off**: el modelo no es el ideal monetario; se explica en la defensa.

### DD-04 — Orden de cálculo: neto sin redondear → IVA sobre neto sin redondear → redondear por separado
**Justificación**: es lo que fija la consigna; cambiar el orden altera centavos (RN-IM-04..07).

### DD-05 — Bloqueo pesimista en vez de optimista
**Decisión**: `PESSIMISTIC_WRITE` sobre factura y punto de venta. **Alternativa**: `@Version` + reintentos. **Justificación**: la consigna lo exige explícitamente. **Trade-off**: menor concurrencia, comportamiento distinto en H2 y PostgreSQL (probar en ambos).

### DD-06 — Proyección directa a DTO con JPQL dinámico
**Decisión**: fragmento de repositorio + `EntityManager` + `SELECT new` en lugar de Specifications/Criteria o carga de entidades. **Justificación**: no cargar datos de más; es requisito de la etapa 2. **Trade-off**: el constructor debe coincidir en orden y tipos.

### DD-07 — Entidades nunca expuestas; DTO por caso de uso
**Justificación**: evita recursión JSON, `LazyInitializationException` y acoplamiento. Con `open-in-view=false` el DTO se arma dentro de la transacción.

### DD-08 — Reportes en memoria
**Decisión**: `byte[]` en memoria, sin escribir archivos; sin streaming. **Trade-off**: no escala a volúmenes grandes (aceptado: sin paginación en alcance).

### DD-09 — Fuente PDF incrustada y error ante glifo faltante
**Decisión**: Noto Sans CJK SC (OFL) con Identity-H; rechazar con error explícito. **Justificación**: evita pérdida silenciosa de texto; TXT/Excel preservan.

### DD-10 — Frontend sin framework; estado simple con ids de solicitud
**Justificación**: objetivo pedagógico (promesas, fetch, DOM, carreras). **Trade-off**: más código manual.

### DD-11 — `DataLoader` en dos tiempos
**Decisión**: en etapa 1 cargar datos mínimos o probar con tests controlados; en etapa 4 recargar usando `FacturaService`. **Justificación**: lo dice la consigna (no convertir la carga provisoria en regla final).

### DD-12 — Entorno: JDK 17 forzado, TZ Argentina
**Decisión**: usar siempre `C:\Users\zbook\.jdks\ms-17.0.20.1` para Gradle; para PostgreSQL pasar `-Duser.timezone=America/Argentina/Buenos_Aires`. **Justificación**: JDK 26 por defecto no es la versión del proyecto; PostgreSQL rechaza `America/Buenos_Aires`.

### DD-13 — Trabajo previo propio como semilla por etapa
**Decisión**: el código de los TPs anteriores de la materia se reutiliza como **semilla por etapa**, no como base del proyecto. La semilla principal es `C:\Users\zbook\Documents\Facultad\Desarrollo\StringBoot\api-facturas` (Spring Boot 4.1.1 + Maven, paquete `com.ejemplo.apifacturas`); secundarias son los TPs de JPA y JPQL en `Documents\Facultad\Desarrollo\JPA\` y `tp-desarrollo\`. En cada change se trae solo lo que esa etapa necesita, se adapta a la consigna (Gradle 8.10.2, Boot 3.3.5, paquete `ar.edu.utn.frm.facturacion`, `LocalDate`, `EstadoFactura` con `EnumType.STRING`, unicidades, H2 por defecto) y entra **detrás de un test que falla primero**: lo traído no se considera hecho hasta tener su test en verde. **Alternativas descartadas**: migrar `api-facturas` entero (arrastra Maven, Boot 4 y decisiones que contradicen la consigna); ignorarlo (desperdicia trabajo propio ya entendido). **Declaración**: los TPs de JPA y JPQL fueron grupales y el integrador es individual; cada `docs/etapa-N.md` declara qué se reutilizó, de dónde y qué se modificó. **Referencia del docente** (`arca-spirng`): sigue siendo solo de consulta, nunca fuente de copia.

## Supuestos inferidos

### SU-01 — Las columnas/campos no listados por la consigna siguen la referencia
**Supuesto**: nombres como `resultadoAfip`, `codigoAfip`, `tipoEmision`, campos de Contacto/Domicilio son *sugeridos*. **Origen**: `arca-spirng/model`. **Riesgo si es falso**: tests del docente con otros nombres. **Validar**: preguntar al docente si hay pruebas/fixtures propias (ver 10, PA-04).

### SU-02 — El `id` va en `FacturaReporteDTO` (confirmado por la consigna)
**Supuesto**: se incluye `id` (la consigna E2 lo lista) y además porque el frontend necesita anular/eliminar por fila. **Origen**: comentario en la referencia. **Riesgo**: bajo. **Validar**: confirmar con docente.

### SU-03 — Estado inválido en PATCH
**Supuesto**: la consigna no especifica el código para un `estado` desconocido en el PATCH; se asume **400** (coherente con RN-FI-02) aunque la referencia lanza 409. **Riesgo**: discrepancia con tests del docente. Ver IN-01.

### SU-04 — Código HTTP del glifo no soportado
**Supuesto**: 400 (como la referencia), la consigna solo dice "error explícito". Ver IN-03.

### SU-05 — Distribución de las seis facturas de ejemplo
**Supuesto**: 3 EMITIDA, 1 ANULADA, 2 BORRADOR (referencia); la consigna solo pide "seis representativas".

### SU-06 — `importeCobrado` siempre 0
**Supuesto**: sin flujo de cobros, `saldo = total` siempre.

### SU-07 — Un solo desarrollador, rama principal protegida por disciplina
**Supuesto**: los "pull requests" son auto-revisados. Ver PA-06.

### SU-08 — La base H2 es suficiente para la mayoría de las pruebas; PostgreSQL se usa para validar bloqueos
**Riesgo si es falso**: un comportamiento válido en H2 puede fallar en PostgreSQL. **Validar**: correr la suite de concurrencia también contra PostgreSQL y declararlo en `docs/etapa-4.md`.

### SU-09 — `TipoMoneda.codigoAfip` usa `PES` y `DOL`
**Origen**: el frontend debe traducir `PES`→ARS y `DOL`→USD (consigna E5). **Riesgo**: otros códigos se muestran tal cual.
