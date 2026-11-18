# ciclo-de-vida-factura Specification

## Purpose
Define las únicas transiciones de estado de una factura (BORRADOR → EMITIDA → ANULADA), qué datos asigna la emisión simulada (número por punto de venta, CAE ficticio, vencimiento y resultado) y qué conserva la anulación.

## Requirements

### Requirement: Transiciones permitidas
`PATCH /api/facturas/{id}/estado` SHALL permitir solo BORRADOR → EMITIDA y EMITIDA → ANULADA, y MUST responder `200 OK` con la fila de reporte de la factura en su nuevo estado.

#### Scenario: Emitir un borrador
- **WHEN** se envía `PATCH /api/facturas/{id}/estado` con `{"estado":"EMITIDA"}` a un borrador con renglones
- **THEN** la respuesta es `200` con `estado` `EMITIDA` y `numeroFactura` asignado

#### Scenario: Anular una emitida
- **WHEN** se envía `PATCH` con `{"estado":"ANULADA"}` a una factura EMITIDA
- **THEN** la respuesta es `200` con `estado` `ANULADA` y el mismo `numeroFactura`

### Requirement: Transiciones rechazadas
Toda otra combinación de estado actual y estado pedido MUST responder `409 Conflict` sin modificar la factura: anular un borrador, emitir una emitida o anulada, anular una anulada y volver a BORRADOR desde cualquier estado.

#### Scenario: Combinaciones prohibidas
- **WHEN** se pide BORRADOR → ANULADA, BORRADOR → BORRADOR, EMITIDA → EMITIDA, EMITIDA → BORRADOR, ANULADA → ANULADA, ANULADA → EMITIDA o ANULADA → BORRADOR
- **THEN** cada pedido responde `409`, el detalle nombra el estado actual y el pedido, y la factura releída no cambió

#### Scenario: Factura inexistente
- **WHEN** se envía `PATCH /api/facturas/999999/estado` con `{"estado":"EMITIDA"}`
- **THEN** la respuesta es `404`

### Requirement: Emisión exige renglones
Emitir una factura sin renglones MUST responder `409` y dejarla en BORRADOR sin número ni CAE.

#### Scenario: Borrador vacío
- **WHEN** se pide emitir un borrador guardado sin renglones
- **THEN** la respuesta es `409` y la factura sigue en BORRADOR con número y CAE nulos

### Requirement: Numeración por punto de venta
Al emitir, la factura MUST recibir como número el máximo número existente de su punto de venta más uno, empezando en 1; cada punto de venta MUST numerar por separado y los borradores MUST NOT consumir números.

#### Scenario: Primera emisión de un punto de venta
- **WHEN** se emite el primer borrador del punto de venta 1
- **THEN** su número es 1

#### Scenario: Numeración independiente
- **WHEN** se emiten dos borradores del punto de venta 1 y luego uno del punto de venta 2
- **THEN** los del punto de venta 1 reciben 1 y 2, y el del punto de venta 2 recibe 1

#### Scenario: Anuladas conservan su número
- **WHEN** en un punto de venta se emiten las facturas 1 y 2, se anula la 2 y se emite otro borrador
- **THEN** el nuevo número es 3

### Requirement: CAE simulado
Al emitir, la factura MUST recibir un CAE de exactamente 14 dígitos decimales (con ceros a la izquierda si corresponde), vencimiento del CAE igual a la fecha actual del reloj de la aplicación más 10 días y resultado de autorización `A`. Mientras es BORRADOR, CAE, vencimiento y resultado MUST ser nulos.

#### Scenario: Datos de autorización asignados
- **WHEN** con el reloj fijado en 2026-11-17 y un generador de CAE que produce `12345678901234` se emite un borrador con fecha de emisión 2026-11-01
- **THEN** la factura tiene CAE `12345678901234`, vencimiento 2026-11-27, resultado `A` y conserva la fecha de emisión 2026-11-01

#### Scenario: Ceros a la izquierda
- **WHEN** la fuente aleatoria del generador de CAE produce el número 42
- **THEN** el CAE es `00000000000042`

#### Scenario: Formato con la fuente real
- **WHEN** se emiten facturas con el generador aleatorio por defecto
- **THEN** cada CAE coincide con el patrón de 14 dígitos

### Requirement: Anulación conserva el comprobante
Anular MUST cambiar el estado a ANULADA, fijar la fecha de anulación en la fecha actual del reloj de la aplicación y registrar la baja lógica, conservando número, CAE, importes y renglones; la factura anulada MUST seguir apareciendo en las consultas.

#### Scenario: Anulación registrada
- **WHEN** con el reloj fijado en 2026-11-17 se anula una factura EMITIDA con número 3
- **THEN** la factura releída está ANULADA con fecha de anulación 2026-11-17, número 3, el mismo CAE y los mismos renglones, y `GET /api/facturas?estado=ANULADA` la incluye

### Requirement: Observación del cambio de estado
Una observación no vacía enviada en el cambio de estado MUST reemplazar la observación de la factura; una observación ausente, vacía o en blanco MUST dejarla como estaba.

#### Scenario: Motivo de anulación
- **WHEN** una factura EMITIDA con observaciones `Entrega parcial` se anula con observaciones `Error en el cliente`
- **THEN** sus observaciones pasan a ser `Error en el cliente`

#### Scenario: Observación en blanco
- **WHEN** un borrador con observaciones `Urgente` se emite con observaciones `"   "`
- **THEN** sus observaciones siguen siendo `Urgente`

### Requirement: Emitidas y anuladas no se editan ni borran
Una vez emitida, la factura MUST conservar su cabecera, sus renglones y sus importes: solo se puede consultar o anular.

#### Scenario: Intento de edición posterior a la emisión
- **WHEN** se emite un borrador y luego se le envía un `PUT` válido y un `DELETE`
- **THEN** ambos responden `409` y la factura releída es idéntica a la emitida
