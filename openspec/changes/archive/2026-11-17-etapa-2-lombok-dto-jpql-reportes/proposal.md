# Proposal

## Why

La etapa 1 dejó un modelo persistente verificado, pero todavía no existe ninguna forma de **leerlo como reporte**: no hay DTO, no hay consulta JPQL y no hay exportadores. La Etapa 2 de la consigna (semanas 4-5, 20 % de la nota de etapas) exige obtener el listado de facturas mediante una **proyección JPQL directa a `FacturaReporteDTO`** y producir **PDF, Excel y TXT sin exponer entidades ni mezclar monedas**, además de garantizar que Lombok no genere recursión entre asociaciones. La etapa 3 (`GET /api/facturas` y `GET /api/facturas/reporte`) se apoya directamente en esta consulta y en estos exportadores: si el constructor de la proyección, los `LEFT JOIN` o el criterio de totales quedan mal, el error se arrastra a la API y al frontend.

## What Changes

- **Higiene Lombok (cierre de lo iniciado en la etapa 1)**: la etapa 1 ya usa `compileOnly` + `annotationProcessor`, `@Getter`/`@Setter`/`@ToString` con `@ToString.Exclude` en todas las asociaciones y **ninguna** entidad genera `equals`/`hashCode`. Lo que falta es coherencia con la herencia: las exclusiones declaradas en `AuditoriaApp` hoy no tienen efecto porque ninguna superclase genera `toString`. Se hace efectiva esa herencia (el identificador aparece en la representación textual y los usuarios de auditoría quedan excluidos de verdad) y se agregan pruebas que fijan la ausencia de recursión en `toString`, `equals` y `hashCode` del par factura–detalle.
- **`FacturaReporteDTO`** (10 campos de la consigna, número nulo en borrador, estado expuesto como texto) con un único constructor de proyección que acepta los tipos que devuelve la consulta (`Long` para `COUNT`, `EstadoFactura`).
- **Fragmento de repositorio con `EntityManager`** y JPQL `SELECT new`: `LEFT JOIN` a cliente y detalles, `COALESCE` «Consumidor Final», joins a condición de IVA, punto de venta y moneda, `GROUP BY` de los campos no agregados, orden por fecha de emisión descendente e id descendente. Se expone desde el repositorio de facturas existente. Los filtros de consulta (fechas, estado, monto mínimo) los define la consigna en la **etapa 3** y se agregan allí sobre esta misma consulta.
- **`ReporteFacturasService`** en `report/`: tres métodos que reciben `List<FacturaReporteDTO>` y devuelven `byte[]` en memoria, sin escribir archivos ni depender del `DataLoader`.
  - **TXT**: UTF-8, tabuladores, una fila física por factura, nueve columnas, escapes reversibles, fechas ISO, importes con dos decimales y punto, sin pie de totales.
  - **Excel** (Apache POI 5.3.0): hoja `Facturas`, encabezados destacados, celdas tipadas, formatos, ajuste de columnas, totales `SUMIFS` por EMITIDA y código de moneda, fórmulas evaluadas y recálculo habilitado.
  - **PDF** (OpenPDF 2.0.3): A4 apaisado, título, fecha de generación (zona `America/Argentina/Buenos_Aires`, reloj inyectable), cantidad de registros, encabezado repetido por página, mensaje sin resultados, fuente `NotoSansCJKsc-Regular.otf` incrustada con `Identity-H` y licencia OFL, error explícito ante un glifo no disponible, totales EMITIDA por moneda calculados con `BigDecimal` `HALF_UP`.
- **Fuente del PDF**: el archivo no está en el repositorio y obtenerlo requiere **descargarlo** de una fuente externa (o copiarlo de una copia local de terceros). Esa adquisición queda como tarea **bloqueada hasta que el estudiante la apruebe**; TXT, Excel y la proyección no dependen de ella.
- **Muestras** de los tres archivos generadas en `build/` (ignorado por Git) para la revisión visual del PDF completo.
- **Informe `docs/etapa-2.md`** (joins, constructor de proyección, criterio de totales, estrategia de preservación de texto, recursión de Lombok, semilla reutilizada, referencia del docente consultada solo por nombres, uso de IA) y actualización del README.
- **Semilla (DD-13)**: `api-facturas/dto/FacturaReporteDTO.java`, la consulta `SELECT new` de `FacturaService.buscarFacturasFiltradas` y los exportadores del `FacturaRestController`; secundaria `ConsultasJPQL.java` (TP3). Se adaptan detrás de un test en rojo.

## Capabilities

### New Capabilities
- `proyeccion-reporte-facturas`: el listado de facturas como proyección directa a un DTO de reporte — campos, tipos, cliente opcional, cabeceras sin renglones, cantidad de ítems, número nulo, estado como texto, código de moneda y orden.
- `exportacion-reportes`: reglas comunes a los tres formatos — generación en memoria a partir de los DTO, nueve columnas en orden fijo sin el id interno, tratamiento del número en borrador y criterio de totales (solo EMITIDA, separados por código de moneda, sin conversión, cero para monedas sin emitidas).
- `reporte-txt`: el formato de texto tabulado UTF-8 con escapes reversibles, fechas ISO e importes con punto decimal, sin pie de totales.
- `reporte-excel`: el libro Excel con hoja `Facturas`, celdas tipadas, formatos y totales como fórmulas `SUMIFS` evaluadas.
- `reporte-pdf`: el documento PDF apaisado con encabezado informativo, encabezados repetidos, mensaje sin resultados, fuente incrustada y rechazo explícito de glifos no disponibles.

### Modified Capabilities
- `modelo-persistente`: el requisito «Representación textual sin recursión ni clave» se amplía a los métodos generados de igualdad y hash (identidad de objeto, sin recorrer asociaciones) y a la coherencia con la herencia (la representación incluye el identificador y excluye los usuarios de auditoría).

## Impact

- **Código nuevo**: `dto/FacturaReporteDTO.java`; `repository/FacturaReporteRepository.java` + `FacturaReporteRepositoryImpl.java`; `report/ReporteFacturasService.java` y sus auxiliares (totales por moneda, escape TXT, verificación de glifos, excepción de glifo no soportado); `config/` con el bean `Clock` en zona Argentina; pruebas en `src/test/java/.../{model,repository,report}/**`.
- **Código modificado**: `FacturaVentaRepository` (extiende el fragmento); `EntityId`, `AuditoriaApp` y las 14 entidades (`@ToString` coherente con la herencia); `RepresentacionTextualTest`.
- **Recursos nuevos**: `src/main/resources/fonts/NotoSansCJKsc-Regular.otf` (~16 MB) y `src/main/resources/fonts/OFL.txt` — **solo tras la aprobación del estudiante**.
- **Dependencias**: ninguna nueva. `build.gradle` ya declara OpenPDF 2.0.3 y `poi-ooxml` 5.3.0 desde la etapa 0; no cambia.
- **Documentación**: `docs/etapa-2.md` nuevo; `README.md` actualizado.
- **Governance**: MEDIO. Lógica de consulta y reportes, sin escritura de datos; el apply avanza por grupos con puntos de control.
- **Fuera de alcance**: endpoints REST y filtros HTTP con su validación y códigos (C-04), código HTTP del glifo no soportado (C-04), escritura, estados, numeración y cálculo de importes de factura (C-05), login, cobros, paginación, CRUD de catálogos, conversión de monedas, integración fiscal y cualquier comando Git que cambie estado.
