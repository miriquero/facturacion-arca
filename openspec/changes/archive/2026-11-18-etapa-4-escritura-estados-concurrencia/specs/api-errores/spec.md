# Spec Delta

## MODIFIED Requirements

### Requirement: Errores de entrada como 400
Un filtro inválido, un formato de reporte no admitido, un parámetro con tipo incompatible, un PDF imposible por un glifo no soportado, un cuerpo de escritura que no supera la validación, un cuerpo JSON ilegible y un estado desconocido en el cambio de estado MUST responder `400 Bad Request` con un detalle comprensible en español que nombre el parámetro, el campo o el carácter en cuestión.

#### Scenario: Tipo incompatible
- **WHEN** se pide `GET /api/facturas?fechaHasta=ayer`
- **THEN** la respuesta es `400` y el detalle menciona `fechaHasta` y el formato `yyyy-MM-dd`

#### Scenario: Estado de destino desconocido
- **WHEN** se envía `PATCH /api/facturas/1/estado` con `{"estado":"PAGADA"}`
- **THEN** la respuesta es `400` con el formato de error uniforme y el detalle menciona `PAGADA`

### Requirement: Recurso inexistente como 404
Una factura pedida, editada, cambiada de estado o eliminada por un id inexistente, y una referencia a cliente, condición de IVA, punto de venta, moneda o lista–artículo inexistente en un cuerpo de escritura, MUST responder `404 Not Found`.

#### Scenario: Factura borrada o nunca creada
- **WHEN** se pide el detalle de un id que no existe
- **THEN** la respuesta es `404` con el formato de error uniforme

#### Scenario: Referencia inexistente en el cuerpo
- **WHEN** se envía `POST /api/facturas` con `condicionIvaId` 999999
- **THEN** la respuesta es `404` con el formato de error uniforme y el detalle menciona `999999`

### Requirement: Métodos no admitidos como 405
Un método HTTP que la API no ofrece en una ruta existente MUST responder `405 Method Not Allowed` con el formato de error uniforme.

#### Scenario: Escritura todavía no disponible
- **WHEN** se envía `POST /api/catalogos/clientes` (los catálogos siguen siendo de solo lectura; `DELETE /api/facturas/{id}` ya existe desde esta etapa)
- **THEN** la respuesta es `405` con `status` 405 y `timestamp`

#### Scenario: Método no ofrecido sobre la colección de facturas
- **WHEN** se envía `PUT /api/facturas` o `DELETE /api/facturas` sin id
- **THEN** la respuesta es `405` con el formato de error uniforme

## ADDED Requirements

### Requirement: Errores de validación por campo
Un cuerpo de escritura que no supera la validación MUST responder `400` con el formato de error uniforme más una propiedad `errores` que asocia la ruta de cada campo inválido (por ejemplo `detalles[0].descripcion`) con un mensaje en español.

#### Scenario: Dos campos inválidos
- **WHEN** se envía `POST /api/facturas` sin `tipoMonedaId` y con un renglón de cantidad 0
- **THEN** la respuesta es `400` con `status`, `title`, `detail`, `timestamp` y `errores` con exactamente las claves `tipoMonedaId` y `detalles[0].cantidad`, cada una con un mensaje no vacío

### Requirement: JSON ilegible sin detalles del analizador
Un cuerpo JSON malformado, con un tipo incompatible con el campo o ausente cuando es obligatorio MUST responder `400` con un detalle legible en español, y MUST NOT exponer mensajes, nombres de clases ni posiciones internas del analizador JSON.

#### Scenario: JSON malformado
- **WHEN** se envía `POST /api/facturas` con el cuerpo `{"puntoVentaId": 1,`
- **THEN** la respuesta es `400` con el formato de error uniforme y el cuerpo no contiene `JSON parse error`, `com.fasterxml`, `line:` ni `column:`

#### Scenario: Tipo incompatible en un campo
- **WHEN** se envía `POST /api/facturas` con `"cantidad": "mucho"` en un renglón
- **THEN** la respuesta es `400`, el detalle nombra el campo `cantidad` y no contiene texto del analizador

#### Scenario: Cuerpo ausente
- **WHEN** se envía `PUT /api/facturas/1` sin cuerpo
- **THEN** la respuesta es `400` con el formato de error uniforme

### Requirement: Conflicto de negocio como 409
Una operación válida en su forma pero incompatible con el estado actual de la factura, o que produce un importe no representable, MUST responder `409 Conflict` con el formato de error uniforme y un detalle que explique el conflicto.

#### Scenario: Transición inválida
- **WHEN** se envía `PATCH` con `{"estado":"ANULADA"}` a un borrador
- **THEN** la respuesta es `409` con `status` 409, `timestamp` y un detalle que menciona `BORRADOR` y `ANULADA`

#### Scenario: Importe no representable
- **WHEN** un alta produce un importe que no puede guardarse sin pérdida
- **THEN** la respuesta es `409` con el formato de error uniforme
