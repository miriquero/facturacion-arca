## MODIFIED Requirements

### Requirement: Edición solo en BORRADOR
`PUT` sobre una factura EMITIDA o ANULADA (informada a ARCA) MUST responder `409 Conflict` sin modificar nada, y sobre un id inexistente MUST responder `404`.

#### Scenario: Editar una emitida
- **WHEN** se envía un `PUT` válido a una factura EMITIDA
- **THEN** la respuesta es `409` y la factura conserva su cabecera, renglones, número y CAE

#### Scenario: Editar una anulada
- **WHEN** se envía un `PUT` válido a una factura ANULADA
- **THEN** la respuesta es `409`, el detalle menciona el estado ANULADA y la factura conserva su cabecera, renglones, número, CAE y fecha de anulación

#### Scenario: Editar una inexistente
- **WHEN** se envía un `PUT` válido a `/api/facturas/999999`
- **THEN** la respuesta es `404`

### Requirement: Eliminación solo en BORRADOR
`DELETE` sobre una factura EMITIDA o ANULADA (informada a ARCA) MUST responder `409` sin borrar nada, y sobre un id inexistente MUST responder `404`.

#### Scenario: Borrar una emitida
- **WHEN** se envía `DELETE` a una factura EMITIDA
- **THEN** la respuesta es `409`, el detalle menciona el estado EMITIDA y la factura sigue existiendo con su número y CAE

#### Scenario: Borrar una anulada
- **WHEN** se envía `DELETE` a una factura ANULADA
- **THEN** la respuesta es `409` y la factura sigue existiendo

#### Scenario: Borrar una inexistente
- **WHEN** se envía `DELETE /api/facturas/999999`
- **THEN** la respuesta es `404`
