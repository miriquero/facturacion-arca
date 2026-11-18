## MODIFIED Requirements

### Requirement: Consulta vacía distinta de recurso inexistente
Una consulta de colección sin elementos MUST responder `200 OK` (los catálogos con un arreglo vacío y el listado paginado de facturas con `items` vacío), y solo la búsqueda de un recurso puntual inexistente MUST responder `404`.

#### Scenario: Listado vacío frente a detalle inexistente
- **WHEN** sin facturas guardadas se pide `GET /api/facturas` y luego `GET /api/facturas/1`
- **THEN** la primera respuesta es `200` con `items` igual a `[]` y `totalElementos` 0, y la segunda `404`

#### Scenario: PDF de una factura inexistente
- **WHEN** se pide `GET /api/facturas/999999/pdf`
- **THEN** la respuesta es `404` con el formato de error uniforme
