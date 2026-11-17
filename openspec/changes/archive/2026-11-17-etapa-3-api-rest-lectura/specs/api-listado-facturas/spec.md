# Spec Delta

## Purpose

Expone por HTTP el listado de facturas como filas de reporte, con filtros opcionales normalizados y validados, para que el frontend y cualquier cliente consulten facturas sin recibir entidades.

## ADDED Requirements

### Requirement: Listado de facturas por HTTP
`GET /api/facturas` SHALL responder `200 OK` con un arreglo JSON de filas de reporte, cada una con exactamente los campos `id`, `numeroFactura`, `fechaEmision` (`yyyy-MM-dd`), `clienteDenominacion`, `condicionIva`, `puntoVentaDescripcion`, `importeTotal`, `cantidadItems`, `estado` y `codigoMoneda`, en el orden del reporte (fecha de emisión descendente y, ante igual fecha, id descendente).

#### Scenario: Sin filtros
- **WHEN** se pide `GET /api/facturas` con tres facturas guardadas del 2026-11-01, 2026-11-05 y 2026-11-10
- **THEN** la respuesta es `200` con tres filas en el orden 2026-11-10, 2026-11-05, 2026-11-01 y cada fila tiene solo los diez campos del reporte

#### Scenario: Cliente opcional en el listado
- **WHEN** se pide `GET /api/facturas` con una factura sin cliente
- **THEN** su fila tiene `clienteDenominacion` igual a `Consumidor Final`

#### Scenario: Borrador sin número
- **WHEN** se pide `GET /api/facturas` con una factura BORRADOR
- **THEN** su fila tiene `numeroFactura` nulo y `estado` igual a `BORRADOR`

### Requirement: Consulta sin resultados
Una consulta del listado sin coincidencias MUST responder `200 OK` con un arreglo vacío y MUST NOT responder `404`.

#### Scenario: Base sin facturas
- **WHEN** se pide `GET /api/facturas` sin facturas guardadas
- **THEN** la respuesta es `200` con el cuerpo `[]`

#### Scenario: Filtros sin coincidencias
- **WHEN** se pide `GET /api/facturas?montoMinimo=999999999` y ninguna factura llega a ese total
- **THEN** la respuesta es `200` con el cuerpo `[]`

### Requirement: Filtros por fecha inclusivos
Los parámetros opcionales `fechaDesde` y `fechaHasta`, con formato `yyyy-MM-dd`, MUST incluir las facturas emitidas exactamente en cada límite.

#### Scenario: Rango con límites exactos
- **WHEN** con facturas del 2026-11-01, 2026-11-05 y 2026-11-10 se pide `GET /api/facturas?fechaDesde=2026-11-05&fechaHasta=2026-11-10`
- **THEN** la respuesta contiene las facturas del 2026-11-10 y del 2026-11-05 y no la del 2026-11-01

#### Scenario: Mismo día como desde y hasta
- **WHEN** se pide `GET /api/facturas?fechaDesde=2026-11-05&fechaHasta=2026-11-05`
- **THEN** la respuesta contiene solo las facturas del 2026-11-05

#### Scenario: Solo fecha desde
- **WHEN** se pide `GET /api/facturas?fechaDesde=2026-11-05`
- **THEN** la respuesta contiene las facturas del 2026-11-05 en adelante

### Requirement: Fechas invertidas rechazadas
Si `fechaDesde` es posterior a `fechaHasta`, la consulta MUST responder `400 Bad Request` con un detalle que nombre ambos parámetros.

#### Scenario: Rango invertido
- **WHEN** se pide `GET /api/facturas?fechaDesde=2026-11-10&fechaHasta=2026-11-01`
- **THEN** la respuesta es `400` y el detalle menciona `fechaDesde` y `fechaHasta`

### Requirement: Estado normalizado
El parámetro opcional `estado` MUST normalizarse quitando espacios exteriores y convirtiéndolo a mayúsculas antes de filtrar; un valor vacío o solo con espacios MUST tratarse como filtro ausente.

#### Scenario: Estado con espacios y minúsculas
- **WHEN** con facturas BORRADOR, EMITIDA y ANULADA se pide `GET /api/facturas?estado=%20emitida%20`
- **THEN** la respuesta contiene solo la factura EMITIDA

#### Scenario: Estado vacío
- **WHEN** se pide `GET /api/facturas?estado=%20%20`
- **THEN** la respuesta contiene las facturas de los tres estados

### Requirement: Estado desconocido rechazado
Un `estado` que, normalizado, no sea `BORRADOR`, `EMITIDA` ni `ANULADA` MUST responder `400 Bad Request` con un detalle que indique el valor recibido y los valores admitidos.

#### Scenario: Estado inexistente
- **WHEN** se pide `GET /api/facturas?estado=PAGADA`
- **THEN** la respuesta es `400` y el detalle menciona `PAGADA`, `BORRADOR`, `EMITIDA` y `ANULADA`

### Requirement: Importe mínimo inclusivo
El parámetro opcional `montoMinimo` MUST incluir las facturas cuyo total es exactamente igual al mínimo y MUST NOT imponer un mínimo cero: un valor negativo es válido.

#### Scenario: Total igual al mínimo
- **WHEN** con facturas de total 100.10, 100.00 y 250.00 se pide `GET /api/facturas?montoMinimo=100.10`
- **THEN** la respuesta contiene las de 100.10 y 250.00 y no la de 100.00

#### Scenario: Mínimo negativo
- **WHEN** se pide `GET /api/facturas?montoMinimo=-50`
- **THEN** la respuesta es `200` con todas las facturas

### Requirement: Importe mínimo no finito rechazado
Un `montoMinimo` que no sea un número finito MUST responder `400 Bad Request` con un detalle que nombre el parámetro.

#### Scenario: NaN e infinitos
- **WHEN** se pide `GET /api/facturas` con `montoMinimo` igual a `NaN`, `Infinity`, `-Infinity` o `1e400`
- **THEN** cada respuesta es `400` y el detalle menciona `montoMinimo`

### Requirement: Parámetros mal tipados rechazados
Una fecha que no cumpla `yyyy-MM-dd` o un `montoMinimo` no numérico MUST responder `400 Bad Request` con un detalle que nombre el parámetro, sin exponer mensajes internos del framework.

#### Scenario: Fecha con otro formato
- **WHEN** se pide `GET /api/facturas?fechaDesde=05/11/2026`
- **THEN** la respuesta es `400` y el detalle menciona `fechaDesde`

#### Scenario: Monto con texto
- **WHEN** se pide `GET /api/facturas?montoMinimo=cien`
- **THEN** la respuesta es `400` y el detalle menciona `montoMinimo`

### Requirement: Filtros combinados
Los filtros presentes MUST aplicarse en conjunto: una factura aparece solo si cumple todos.

#### Scenario: Estado, fechas y monto
- **WHEN** con una EMITIDA del 2026-11-05 por 500.00, una EMITIDA del 2026-11-05 por 50.00, una BORRADOR del 2026-11-05 por 500.00 y una EMITIDA del 2026-10-01 por 500.00 se pide `GET /api/facturas?estado=EMITIDA&fechaDesde=2026-11-01&fechaHasta=2026-11-30&montoMinimo=100`
- **THEN** la respuesta contiene solo la EMITIDA del 2026-11-05 por 500.00
