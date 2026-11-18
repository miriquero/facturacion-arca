# Spec Delta

## Purpose

Define las acciones por fila que el frontend ofrece según el estado de cada factura (editar, emitir, anular y eliminar), sus confirmaciones y el comportamiento ante la cancelación, el éxito y el error.

## ADDED Requirements

### Requirement: Acciones según el estado
Cada fila MUST ofrecer solo las acciones válidas para su estado: BORRADOR → editar, emitir y eliminar; EMITIDA → anular; ANULADA → ninguna. El nombre accesible de cada acción MUST identificar la factura sobre la que actúa.

#### Scenario: Tres estados
- **WHEN** el listado trae un BORRADOR de id 6, una EMITIDA de id 1 y una ANULADA de id 3
- **THEN** la fila 6 ofrece editar, emitir y eliminar; la fila 1 solo anular; la fila 3 ninguna acción

#### Scenario: Nombre accesible
- **WHEN** se inspecciona el botón de emitir de la fila del borrador 6
- **THEN** su nombre accesible incluye la acción y el identificador 6

### Requirement: Emisión de un borrador
Emitir MUST enviar el cambio de estado a `EMITIDA` del borrador elegido y, ante el éxito, anunciar el número asignado y volver a pedir el listado con los filtros vigentes.

#### Scenario: Emisión exitosa
- **WHEN** se emite el borrador 6 y la API responde `200` con `numeroFactura` 4
- **THEN** la región de estado anuncia la emisión con el número 4 y se pide de nuevo el listado

#### Scenario: Emisión rechazada
- **WHEN** la API responde `409` porque el borrador no tiene renglones
- **THEN** la región de estado muestra el detalle del conflicto y la tabla no cambia

### Requirement: Eliminación con confirmación
Eliminar un borrador MUST pedir confirmación nombrando el borrador; solo si se confirma MUST enviarse la eliminación, y ante la respuesta `204` MUST anunciarse y volver a pedirse el listado. Si se rechaza la confirmación, MUST NOT enviarse ninguna petición.

#### Scenario: Confirmada
- **WHEN** se elige eliminar el borrador 6 y se confirma
- **THEN** se envía `DELETE /facturas/6`, la respuesta `204` se procesa sin error, se anuncia la eliminación y se pide de nuevo el listado

#### Scenario: Rechazada
- **WHEN** se elige eliminar el borrador 6 y se cancela la confirmación
- **THEN** no se envía ninguna petición y la tabla no cambia

### Requirement: Anulación con motivo opcional
Anular una factura EMITIDA MUST pedir un motivo opcional en un diálogo que se puede cancelar; aceptar con un motivo no vacío MUST enviar `ANULADA` con ese motivo como observaciones, y aceptar con el motivo vacío MUST enviar `ANULADA` sin observaciones.

#### Scenario: Con motivo
- **WHEN** se anula la factura 1 con el motivo `Error en el cliente`
- **THEN** se envía `PATCH /facturas/1/estado` con `estado` `ANULADA` y `observaciones` `Error en el cliente`, y ante el `200` se anuncia la anulación y se pide de nuevo el listado

#### Scenario: Sin motivo
- **WHEN** se anula la factura 1 aceptando el diálogo con el motivo vacío o solo con espacios
- **THEN** se envía `PATCH` con `estado` `ANULADA` y sin `observaciones`

#### Scenario: Motivo demasiado largo
- **WHEN** se acepta el diálogo con un motivo de 501 caracteres
- **THEN** se muestra un error que menciona 500 y no se envía ninguna petición

### Requirement: Cancelar la anulación no envía nada
Cancelar el diálogo de anulación MUST cancelar la operación sin enviar ningún `PATCH` y sin cambiar la tabla.

#### Scenario: Diálogo cancelado
- **WHEN** se elige anular la factura 1 y se cancela el diálogo
- **THEN** no se envía ninguna petición y la fila sigue como EMITIDA

### Requirement: Acción de fila sin repetición
Mientras una acción de fila está en curso, la interfaz MUST impedir que se dispare de nuevo y MUST liberar las acciones al terminar con éxito o con error.

#### Scenario: Doble clic en emitir
- **WHEN** se activa emitir sobre el borrador 6 dos veces antes de la respuesta
- **THEN** se envía un solo `PATCH`

### Requirement: Errores de acción visibles
Un error de emisión, anulación o eliminación MUST mostrarse en la región de estado con el detalle recibido de la API y MUST permanecer visible hasta la siguiente acción.

#### Scenario: Factura inexistente
- **WHEN** la API responde `404` a la eliminación del borrador 6 porque otro usuario lo borró
- **THEN** la región de estado muestra el detalle del `404` hasta la siguiente acción
