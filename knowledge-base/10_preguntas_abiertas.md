# Preguntas Abiertas

## Inconsistencias detectadas (consigna vs. referencia, y dentro de la consigna)

### IN-01 — Código HTTP para `estado` desconocido en `PATCH /estado`
**Consigna dice**: el estado desconocido en los *filtros* es 400 (etapa 3); para PATCH solo pide `estado` "no vacío" y que transiciones incompatibles sean 409. No define el caso `estado: "XYZ"`.
**Referencia hace**: `parsearEstado` lanza `ReglaNegocioException` → **409**.
**Impacto**: un test del docente (o de Martín) puede esperar 400 o 409.
**Resolución propuesta**: 400 (valor inválido = error de entrada) y documentarlo en `docs/etapa-4.md`; confirmar con el docente.

### IN-02 — `id` en `FacturaReporteDTO`
**Consigna dice**: campos `id, numeroFactura, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion, importeTotal, cantidadItems, estado, codigoMoneda` (la etapa 2 sí menciona `id` en la lista de campos); el archivo no lleva columna de id.
**Referencia hace**: comenta que "agrega `id` y `estado`" respecto del enunciado original, y tiene un constructor de 9 args (sin moneda) y otro de 10.
**Impacto**: bajo; el frontend necesita `id` para acciones por fila.
**Resolución propuesta**: incluir `id` en el DTO (no en las columnas del archivo); no conservar el constructor de 9 argumentos salvo que un test lo pida.

### IN-03 — Código HTTP al fallar el PDF por glifo no soportado
**Consigna dice**: "error explícito"; en el README de la referencia es 400 con el código Unicode.
**Impacto**: contrato del endpoint de reporte.
**Resolución propuesta**: 400 con detalle del code point; confirmar (alternativa 422/500 sería peor: 500 oculta un error de entrada).

### IN-04 — Versiones de JDK y de TypeScript
**Consigna dice**: Java 17; TypeScript 5.6. **Referencia**: "JDK 17 o 21"; TypeScript `^5.6.3` resuelto a 5.9.3 en el lockfile.
**Impacto**: el JDK 26 de esta máquina compilaría pero no es el objetivo; fijar `^5.6.3` puede traer 5.9.x.
**Resolución propuesta**: forzar JDK 17 (`toolchain` o `JAVA_HOME`) y declarar `typescript` con la versión que acepte el docente (5.6.x exacto si se quiere fidelidad).

### IN-05 — Etapa 0 sin tag, pero "cinco entregas con tags etapa-1 a etapa-5"
**Consigna dice**: etapa 0 no tiene nota ni tag; informes `docs/etapa-N.md` para cada entrega.
**Impacto**: ¿hay `docs/etapa-0.md`? No exigido.
**Resolución propuesta**: tratar la etapa 0 como change de OpenSpec sin tag ni informe, solo con commits y README.

### IN-06 — DataLoader en etapa 1 vs. integrado con `FacturaService` (etapa 4)
**Consigna dice**: la versión final usa `FacturaService`; en etapa 1 no hay servicio.
**Impacto**: la etapa 1 queda con una carga provisoria o sin carga.
**Resolución propuesta**: en E1 cargar solo usuarios/catálogos con repositorios y probar facturas con tests; completar las 6 facturas en E4.

### IN-07 — Nombre de la institución
**Consigna dice**: "Universidad Nacional Tecnologica" (en la tabla de presentación); el contexto del proyecto indica UTN FRM.
**Impacto**: solo documentación.
**Resolución propuesta**: usar "UTN FRM" en informes salvo que el docente indique otra denominación.

### IN-08 — Cálculo del IVA: "neto sin redondear" vs. persistir neto redondeado
**Consigna dice**: IVA sobre neto sin redondear; neto e IVA se redondean por separado; subtotal = suma de redondeados.
**Riesgo**: implementaciones que redondean el neto antes de calcular el IVA difieren en centavos (error frecuente, Anexo A).
**Resolución**: seguir la consigna; agregar casos de prueba de triangulación con valores que difieran (p. ej. cantidad 3 × 33,335).

### IN-09 — Generación del CAE
**Consigna dice**: 14 dígitos, sin más detalle. **Referencia**: `random.nextLong(10^14)` formateado con `%014d`.
**Impacto**: tests deterministas necesitan una fuente de aleatoriedad inyectable.
**Resolución propuesta**: inyectar un `Random`/`Supplier<String>` para poder fijar el CAE en pruebas.

### IN-10 — `[DISCOVERY]` `needs_infra`
`needs_infra` se infirió como `true` porque PostgreSQL + Docker Compose son parte de la consigna, aunque el modo por defecto (H2 en memoria) no requiere infraestructura. Confirmar si el deploy es solo local (asumido).

## Preguntas abiertas (priorizadas)

| Prioridad | Pregunta | Bloquea | Decisor |
|-----------|----------|---------|---------|
| ~~Alta~~ Resuelta 2026-11-12 | Remoto creado: `https://github.com/miriquero/facturacion-arca` (público), antes de la etapa 1. | Etapa 0/1 (tags, PR) | Martín |
| Alta | PR con un solo desarrollador: ¿se abre un PR por etapa desde una rama `etapa-N` hacia `main` y se mergea a uno mismo antes de taguear? ¿Squash o merge? | Etapa 1 | Martín / docente |
| Alta | ¿Cómo se declara el uso de IA en cada `docs/etapa-N.md` (qué se pidió, qué se revisó, qué se corrigió)? ¿Se acepta usar OpenSpec + agentes y cómo se documenta ese flujo? | Etapa 1 (primer informe) | Martín / docente |
| Alta | Código HTTP para `estado` inválido en PATCH (IN-01) y para glifo no soportado (IN-03) | Etapa 3/4 | Docente |
| Media | ¿La cátedra publicará kits de nivelación? ¿Se usarán entre etapas? (deben declararse) | Etapa 2+ | Docente |
| Media | ¿Se corre la suite de concurrencia contra PostgreSQL real (Docker) o con el script de verificación aislado? ¿En qué puerto, dado que 5432 puede estar ocupado? | Etapa 4 | Martín |
| Media | ¿Hay pruebas/fixtures del docente que fijen nombres de clases, campos o columnas (SU-01)? | Etapa 1 | Docente |
| Media | ¿Cómo se versiona la fuente `NotoSansCJKsc-Regular.otf` (tamaño grande, licencia OFL) en Git? ¿Git LFS o commit directo? | Etapa 2 | Martín |
| Media | ¿Cómo se prueba el frontend en navegador real (recorrido manual documentado, capturas) además de jsdom? | Etapa 5 | Martín |
| Baja | ¿Se fija TypeScript en 5.6.x o se acepta `^5.6.3` (IN-04)? | Etapa 5 | Docente |
| Baja | ¿Se usa `toolchain` de Gradle para forzar Java 17 en lugar de `JAVA_HOME`? | Etapa 0 | Martín |
| Baja | ¿Se tomarán `requests.http` como parte de la entrega de cada etapa (E3 en adelante) o solo del final? | Etapa 3 | Docente |
| Baja | Formato de fecha/hora de los reportes (zona horaria del servidor vs. Argentina) | Etapa 2 | Martín |
