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
Una consulta sin facturas, o cuyos filtros no coinciden con ninguna factura, MUST devolver una lista vacía.

#### Scenario: Sin facturas
- **WHEN** se consulta el reporte sin facturas guardadas
- **THEN** el resultado es una lista vacía

#### Scenario: Filtros sin coincidencias
- **WHEN** se consulta el reporte con facturas guardadas y un estado ANULADA que ninguna tiene
- **THEN** el resultado es una lista vacía

### Requirement: Proyección sin cargar entidades
La consulta MUST construir las filas directamente desde la base, sin cargar entidades de factura, cliente ni renglón en el contexto de persistencia.

#### Scenario: Contexto limpio tras la consulta
- **WHEN** se limpia el contexto de persistencia y se consulta el reporte con facturas guardadas
- **THEN** después de la consulta ninguna factura, cliente ni renglón queda administrado por el contexto de persistencia

### Requirement: Consulta sin filtros sin cambios
La consulta con todos los filtros ausentes MUST devolver todas las facturas con los mismos datos y el mismo orden que la consulta sin filtros de la etapa 2.

#### Scenario: Filtros vacíos
- **WHEN** se consulta el reporte con filtros vacíos y con la consulta sin filtros sobre las mismas facturas
- **THEN** ambos resultados son iguales fila por fila

### Requirement: Filtro por rango de fechas inclusivo
La consulta MUST admitir una fecha desde y una fecha hasta opcionales, independientes entre sí, que incluyen las facturas emitidas exactamente en cada límite.

#### Scenario: Límites incluidos
- **WHEN** con facturas del 2026-11-01, 2026-11-05 y 2026-11-10 se consulta desde 2026-11-05 hasta 2026-11-10
- **THEN** el resultado tiene las del 2026-11-10 y 2026-11-05, en ese orden

#### Scenario: Solo fecha hasta
- **WHEN** con las mismas facturas se consulta solo hasta 2026-11-05
- **THEN** el resultado tiene las del 2026-11-05 y 2026-11-01

### Requirement: Filtro por estado
La consulta MUST admitir un estado opcional y devolver solo las facturas en ese estado.

#### Scenario: Solo anuladas
- **WHEN** con una factura en cada estado se consulta con estado ANULADA
- **THEN** el resultado tiene solo la factura ANULADA, con estado `ANULADA`

### Requirement: Filtro por importe mínimo inclusivo
La consulta MUST admitir un importe mínimo opcional que incluye las facturas cuyo total es exactamente igual al mínimo, comparando sin aritmética de punto flotante sobre los importes.

#### Scenario: Total igual al mínimo
- **WHEN** con facturas de total 100.00, 100.10 y 250.00 se consulta con mínimo 100.10
- **THEN** el resultado tiene las de 250.00 y 100.10

### Requirement: Filtros combinables sin perder filas del reporte
Los filtros presentes MUST combinarse con conjunción y MUST conservar las facturas sin cliente y las cabeceras sin renglones que los cumplan, con su cantidad de ítems correcta.

#### Scenario: Combinación con factura sin cliente y sin renglones
- **WHEN** con una EMITIDA sin cliente ni renglones del 2026-11-05 por 500.00, una EMITIDA del 2026-11-05 por 50.00 y una BORRADOR del 2026-11-05 por 500.00 se consulta estado EMITIDA, desde 2026-11-01 y mínimo 100
- **THEN** el resultado tiene solo la primera, con `Consumidor Final` y cantidad de ítems 0

### Requirement: Filtros como parámetros de la consulta
Los valores de los filtros MUST enviarse a la base como parámetros de la consulta y MUST NOT incorporarse al texto de la consulta.

#### Scenario: Valores fuera del texto de la consulta
- **WHEN** se arma la consulta con fecha desde 2026-11-05, estado EMITIDA e importe mínimo 123.45
- **THEN** el texto de la consulta no contiene `2026-11-05`, `EMITIDA` ni `123.45`, sino marcadores de parámetro con nombre
