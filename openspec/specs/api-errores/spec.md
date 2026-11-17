# api-errores Specification

## Purpose
Fija un formato único para los errores HTTP de la API (Problem Details con marca temporal) y la correspondencia entre cada situación prevista y su código, distinguiendo una consulta vacía de un recurso inexistente.

## Requirements

### Requirement: Cuerpo de error uniforme
Todo error de la API SHALL responder con tipo de contenido `application/problem+json` y un cuerpo con `status` (igual al código HTTP), `title`, `detail` y `timestamp` (instante ISO-8601).

#### Scenario: Error de filtro
- **WHEN** se pide `GET /api/facturas?estado=PAGADA`
- **THEN** la respuesta es `400` con `Content-Type` `application/problem+json` y un cuerpo con `status` 400, `title` y `detail` no vacíos y `timestamp` interpretable como instante

#### Scenario: Error de recurso inexistente
- **WHEN** se pide `GET /api/facturas/999999`
- **THEN** la respuesta es `404` con el mismo formato y `status` 404

### Requirement: Errores de entrada como 400
Un filtro inválido, un formato de reporte no admitido, un parámetro con tipo incompatible y un PDF imposible por un glifo no soportado MUST responder `400 Bad Request` con un detalle comprensible en español que nombre el parámetro o el carácter en cuestión.

#### Scenario: Tipo incompatible
- **WHEN** se pide `GET /api/facturas?fechaHasta=ayer`
- **THEN** la respuesta es `400` y el detalle menciona `fechaHasta` y el formato `yyyy-MM-dd`

### Requirement: Recurso inexistente como 404
Una factura pedida por un id inexistente MUST responder `404 Not Found`.

#### Scenario: Factura borrada o nunca creada
- **WHEN** se pide el detalle de un id que no existe
- **THEN** la respuesta es `404` con el formato de error uniforme

### Requirement: Consulta vacía distinta de recurso inexistente
Una consulta de colección sin elementos MUST responder `200 OK` con un arreglo vacío, y solo la búsqueda de un recurso puntual inexistente MUST responder `404`.

#### Scenario: Listado vacío frente a detalle inexistente
- **WHEN** sin facturas guardadas se pide `GET /api/facturas` y luego `GET /api/facturas/1`
- **THEN** la primera respuesta es `200 []` y la segunda `404`

### Requirement: Métodos no admitidos como 405
Un método HTTP que la API no ofrece en una ruta existente MUST responder `405 Method Not Allowed` con el formato de error uniforme.

#### Scenario: Escritura todavía no disponible
- **WHEN** se envía `DELETE /api/facturas/1`
- **THEN** la respuesta es `405` con `status` 405 y `timestamp`

### Requirement: Error inesperado sin detalles internos
Una falla no prevista MUST responder `500 Internal Server Error` con el formato de error uniforme y un detalle genérico, y MUST NOT exponer trazas, nombres de clases ni mensajes internos.

#### Scenario: Falla de la capa de datos
- **WHEN** el servicio de facturas lanza una excepción no prevista con el mensaje `SQL interno secreto`
- **THEN** la respuesta es `500`, el detalle es genérico y el cuerpo no contiene `SQL interno secreto` ni nombres de clases Java
