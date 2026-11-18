# frontend-acciones-factura Specification

## Purpose
Define las acciones por fila que el frontend ofrece según el estado de cada factura (editar, emitir, anular y eliminar), sus confirmaciones y el comportamiento ante la cancelación, el éxito y el error.

## Requirements

### Requirement: Acciones según el estado
Cada fila MUST ofrecer solo las acciones válidas para su estado: BORRADOR → editar, emitir y eliminar; EMITIDA → imprimir PDF y anular; ANULADA → imprimir PDF. Una factura informada a ARCA (EMITIDA o ANULADA) MUST NOT ofrecer editar ni eliminar. El nombre accesible de cada acción MUST identificar la factura sobre la que actúa.

#### Scenario: Tres estados
- **WHEN** el listado trae un BORRADOR de id 6, una EMITIDA de id 1 y una ANULADA de id 3
- **THEN** la fila 6 ofrece editar, emitir y eliminar; la fila 1 imprimir PDF y anular; la fila 3 solo imprimir PDF

#### Scenario: Informadas sin edición ni eliminación
- **WHEN** el listado trae una EMITIDA y una ANULADA en la segunda página de un resultado
- **THEN** ninguna de sus filas contiene un control de editar ni de eliminar

#### Scenario: Nombre accesible
- **WHEN** se inspecciona el botón de emitir de la fila del borrador 6 y el enlace de imprimir de la factura 1
- **THEN** sus nombres accesibles incluyen la acción y el identificador 6 y 1 respectivamente

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

### Requirement: Impresión del PDF de una factura informada
La acción imprimir PDF MUST ser un enlace nativo a la URL del PDF de esa factura construida por el cliente HTTP, que el navegador descarga sin pasar por la región de estado; MUST NOT ofrecerse para un BORRADOR.

#### Scenario: Enlace de la factura 1
- **WHEN** el listado trae la EMITIDA de id 1 con la base `http://localhost:8080/api`
- **THEN** su fila tiene un enlace con destino `http://localhost:8080/api/facturas/1/pdf`

#### Scenario: Borrador sin impresión
- **WHEN** el listado trae el BORRADOR de id 6
- **THEN** su fila no tiene enlace a ninguna URL de PDF

### Requirement: Recarga conserva la página vigente
Después de emitir, anular o eliminar con éxito, la interfaz MUST volver a pedir el listado con los filtros y la página vigentes; si la página quedó fuera de rango por la eliminación, MUST pedir la última página existente.

#### Scenario: Emisión en la página 2
- **WHEN** en la página 2 (índice 1) se emite el borrador 6 con éxito
- **THEN** se pide de nuevo el listado con los mismos filtros y `pagina=1`

#### Scenario: Se elimina el único elemento de la última página
- **WHEN** en la página de índice 2, que tiene una sola fila, se elimina ese borrador y la nueva respuesta para `pagina=2` trae `items` vacío con `totalPaginas` 2
- **THEN** se pide el listado con `pagina=1` y la tabla muestra esa página
