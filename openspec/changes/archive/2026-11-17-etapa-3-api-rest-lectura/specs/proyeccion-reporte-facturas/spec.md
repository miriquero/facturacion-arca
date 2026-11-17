# Spec Delta

## MODIFIED Requirements

### Requirement: Reporte vacío
Una consulta sin facturas, o cuyos filtros no coinciden con ninguna factura, MUST devolver una lista vacía.

#### Scenario: Sin facturas
- **WHEN** se consulta el reporte sin facturas guardadas
- **THEN** el resultado es una lista vacía

#### Scenario: Filtros sin coincidencias
- **WHEN** se consulta el reporte con facturas guardadas y un estado ANULADA que ninguna tiene
- **THEN** el resultado es una lista vacía

## ADDED Requirements

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
