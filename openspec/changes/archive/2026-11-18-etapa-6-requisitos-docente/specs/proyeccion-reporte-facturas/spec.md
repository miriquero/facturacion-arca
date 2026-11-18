## MODIFIED Requirements

### Requirement: Filtros como parámetros de la consulta
Los valores de los filtros MUST enviarse a la base como parámetros de la consulta y MUST NOT incorporarse al texto de la consulta. El filtro por informada, que no tiene valor que comparar, MUST expresarse con un fragmento fijo de la consulta (presencia o ausencia de CAE).

#### Scenario: Valores fuera del texto de la consulta
- **WHEN** se arma la consulta con fecha desde 2026-11-05, estado EMITIDA, importe mínimo 123.45, número 987654 y cliente 4321
- **THEN** el texto de la consulta no contiene `2026-11-05`, `EMITIDA`, `123.45`, `987654` ni `4321`, sino marcadores de parámetro con nombre

#### Scenario: Informada como condición fija
- **WHEN** se arma la consulta con informada verdadera y luego con informada falsa
- **THEN** el texto de la primera exige CAE presente y el de la segunda CAE ausente, sin ningún parámetro para ese filtro

## ADDED Requirements

### Requirement: Filtro por número exacto
La consulta MUST admitir un número de factura opcional y devolver solo las facturas con exactamente ese número.

#### Scenario: Número en dos puntos de venta
- **WHEN** con la factura número 1 del punto de venta 1, la número 1 del punto de venta 2 y la número 2 del punto de venta 1 se consulta con número 1
- **THEN** el resultado tiene las dos facturas número 1

### Requirement: Filtro por cliente
La consulta MUST admitir un id de cliente opcional y devolver solo las facturas de ese cliente; las facturas sin cliente MUST NOT coincidir con ningún id.

#### Scenario: Cliente con y sin facturas
- **WHEN** con una factura del cliente A, una del cliente B y una sin cliente se consulta con el id del cliente A
- **THEN** el resultado tiene solo la factura del cliente A

### Requirement: Filtro por informada a ARCA
La consulta MUST admitir un indicador opcional de informada: verdadero devuelve solo las facturas con CAE y falso solo las que no tienen CAE.

#### Scenario: Informadas y no informadas
- **WHEN** con una factura en cada estado se consulta con informada verdadera y luego falsa
- **THEN** el primer resultado tiene la EMITIDA y la ANULADA y el segundo solo el BORRADOR

### Requirement: Página de la consulta con total
La consulta MUST poder devolver una sola página del resultado (desplazamiento y cantidad) junto con la cantidad total de facturas que cumplen los mismos filtros; el total MUST contar facturas y no renglones, y el orden de cada página MUST ser el mismo orden del reporte.

#### Scenario: Total con facturas de varios renglones
- **WHEN** con una factura de tres renglones, una de un renglón y una sin renglones se pide la página 0 de tamaño 2
- **THEN** la página tiene las dos primeras facturas del orden del reporte con su cantidad de ítems correcta y el total es 3

#### Scenario: Total con filtros
- **WHEN** con dos EMITIDA y un BORRADOR se pide la página 0 de tamaño 1 con estado EMITIDA
- **THEN** la página tiene una factura EMITIDA y el total es 2

#### Scenario: Consulta completa sin página
- **WHEN** se consulta el reporte sin página con los mismos filtros
- **THEN** el resultado tiene todas las facturas filtradas, igual fila por fila a la unión ordenada de todas las páginas
