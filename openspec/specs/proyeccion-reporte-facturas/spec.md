# proyeccion-reporte-facturas Specification

## Purpose
Define la consulta de facturas para reportes como una proyección directa a un DTO de reporte, sin cargar entidades, que conserva facturas sin cliente y cabeceras sin renglones y es la base del listado y de los tres formatos de reporte.

## Requirements

### Requirement: Campos del DTO de reporte
Cada fila del reporte MUST contener exactamente: identificador, número de factura, fecha de emisión, denominación del cliente, condición de IVA, descripción del punto de venta, importe total, cantidad de ítems, estado y código de moneda.

#### Scenario: Factura emitida completa
- **WHEN** se consulta el reporte con una factura EMITIDA número 15 del punto de venta «Casa Central», cliente «Ferretería Ñandú», condición «Responsable Inscripto», moneda `PES`, total 1210.50 y dos renglones
- **THEN** la fila tiene esos diez datos, con cantidad de ítems 2

### Requirement: Estado expuesto como texto
El estado de cada fila MUST exponerse como el texto del nombre del estado (`BORRADOR`, `EMITIDA` o `ANULADA`).

#### Scenario: Estados mezclados
- **WHEN** se consulta el reporte con una factura en cada estado
- **THEN** cada fila expone su estado como `BORRADOR`, `EMITIDA` o `ANULADA` respectivamente

### Requirement: Número nulo en borrador
El número de una factura en BORRADOR MUST aparecer nulo en su fila.

#### Scenario: Borrador sin número
- **WHEN** se consulta el reporte con una factura BORRADOR sin número
- **THEN** su fila tiene número nulo y el resto de los datos completos

### Requirement: Cliente opcional como Consumidor Final
Una factura sin cliente MUST aparecer en el reporte con la denominación `Consumidor Final`.

#### Scenario: Factura sin cliente
- **WHEN** se consulta el reporte con una factura sin cliente
- **THEN** la factura aparece y su denominación de cliente es `Consumidor Final`

#### Scenario: Factura con cliente
- **WHEN** se consulta el reporte con una factura del cliente «Ferretería Ñandú»
- **THEN** su denominación de cliente es «Ferretería Ñandú»

### Requirement: Cantidad de ítems por factura
La cantidad de ítems de cada fila MUST ser el número de renglones de esa factura, y una cabecera sin renglones MUST aparecer con cantidad cero.

#### Scenario: Factura con tres renglones
- **WHEN** se consulta el reporte con una factura de tres renglones
- **THEN** su fila tiene cantidad de ítems 3 y aparece una sola vez

#### Scenario: Cabecera sin renglones
- **WHEN** se consulta el reporte con una factura que no tiene renglones
- **THEN** la factura aparece con cantidad de ítems 0

### Requirement: Código de moneda por factura
Cada fila MUST llevar el código de la moneda de su factura, sin convertir importes.

#### Scenario: Dos monedas
- **WHEN** se consulta el reporte con una factura en `PES` por 100.00 y otra en `DOL` por 100.00
- **THEN** cada fila conserva su código de moneda y su importe 100.00

### Requirement: Orden del reporte
Las filas MUST ordenarse por fecha de emisión descendente y, ante igual fecha, por identificador descendente.

#### Scenario: Fechas distintas e iguales
- **WHEN** se consulta el reporte con facturas del 2026-11-01, del 2026-11-10 y dos del 2026-11-05
- **THEN** el orden es la del 2026-11-10, las dos del 2026-11-05 con el identificador mayor primero, y la del 2026-11-01

### Requirement: Reporte vacío
Una consulta sin facturas MUST devolver una lista vacía.

#### Scenario: Sin facturas
- **WHEN** se consulta el reporte sin facturas guardadas
- **THEN** el resultado es una lista vacía

### Requirement: Proyección sin cargar entidades
La consulta MUST construir las filas directamente desde la base, sin cargar entidades de factura, cliente ni renglón en el contexto de persistencia.

#### Scenario: Contexto limpio tras la consulta
- **WHEN** se limpia el contexto de persistencia y se consulta el reporte con facturas guardadas
- **THEN** después de la consulta ninguna factura, cliente ni renglón queda administrado por el contexto de persistencia
