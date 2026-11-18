# frontend-acciones-factura Specification

## Purpose
Define las acciones por fila que el frontend ofrece según el estado de cada factura (editar, emitir, anular y eliminar), sus confirmaciones y el comportamiento ante la cancelación, el éxito y el error.

## Requirements

### Requirement: Acciones según el estado
Las acciones sobre una factura MUST ofrecerse en su pantalla de detalle y solo las válidas para su estado: BORRADOR → Editar (enlace a `#/factura/{id}/editar`), Emitir y Eliminar; EMITIDA → Anular e Imprimir PDF; ANULADA → Imprimir PDF. Una factura informada a ARCA (EMITIDA o ANULADA) MUST NOT ofrecer editar ni eliminar. Cada fila del listado MUST ofrecer solo navegación: un enlace «Ver» al detalle (`#/factura/{id}`) y, si la factura está informada, el enlace «Imprimir PDF»; la fila MUST NOT ofrecer editar, emitir, anular ni eliminar. El nombre accesible de cada acción y de cada enlace de fila MUST identificar la factura sobre la que actúa. El detalle MUST ofrecer siempre el enlace «Volver al listado».

#### Scenario: Tres estados
- **WHEN** se muestra el detalle del BORRADOR 6, luego el de la EMITIDA 1 y luego el de la ANULADA 3
- **THEN** el detalle 6 ofrece Editar, Emitir y Eliminar; el 1 Anular e Imprimir PDF; el 3 solo Imprimir PDF; los tres ofrecen «Volver al listado»

#### Scenario: Fila con solo navegación
- **WHEN** el listado trae un BORRADOR de id 6, una EMITIDA de id 1 y una ANULADA de id 3
- **THEN** cada fila tiene un enlace «Ver» a `#/factura/{id}`, las filas 1 y 3 tienen además «Imprimir PDF», y ninguna fila tiene botones de editar, emitir, anular ni eliminar

#### Scenario: Informadas sin edición ni eliminación
- **WHEN** se muestra el detalle de una EMITIDA y el de una ANULADA
- **THEN** ninguno contiene un control de editar ni de eliminar

#### Scenario: Nombre accesible
- **WHEN** se inspeccionan el enlace «Ver» de la fila 6, el botón Emitir del detalle del borrador 6 y el enlace de imprimir de la factura 1
- **THEN** sus nombres accesibles incluyen la acción y el identificador 6, 6 y 1 respectivamente

### Requirement: Emisión de un borrador
Emitir desde el detalle MUST enviar el cambio de estado a `EMITIDA` del borrador mostrado y, ante el éxito, anunciar el número asignado y volver a pedir el detalle de esa factura para mostrarla emitida, con el anuncio todavía visible.

#### Scenario: Emisión exitosa
- **WHEN** en el detalle del borrador 6 se activa Emitir y la API responde `200` con `numeroFactura` 4
- **THEN** la región de estado anuncia la emisión con el número 4, se pide de nuevo el detalle de la factura 6 y el anuncio sigue visible después de esa recarga

#### Scenario: Emisión rechazada
- **WHEN** la API responde `409` porque el borrador no tiene renglones
- **THEN** la región de estado muestra el detalle del conflicto, el detalle no cambia y sus acciones vuelven a estar habilitadas

### Requirement: Eliminación con confirmación
Eliminar desde el detalle de un borrador MUST pedir confirmación nombrando el borrador; solo si se confirma MUST enviarse la eliminación, y ante la respuesta `204` MUST navegarse al listado anunciando la eliminación. Si se rechaza la confirmación, MUST NOT enviarse ninguna petición y el detalle MUST seguir visible.

#### Scenario: Confirmada
- **WHEN** en el detalle del borrador 6 se elige Eliminar y se confirma
- **THEN** se envía `DELETE /facturas/6`, la respuesta `204` se procesa sin error, se muestra el listado y la región de estado anuncia que se eliminó el borrador 6

#### Scenario: Rechazada
- **WHEN** en el detalle del borrador 6 se elige Eliminar y se cancela la confirmación
- **THEN** no se envía ninguna petición y sigue visible el detalle del borrador 6

### Requirement: Anulación con motivo opcional
Anular desde el detalle de una factura EMITIDA MUST pedir un motivo opcional en un diálogo que se puede cancelar; aceptar con un motivo no vacío MUST enviar `ANULADA` con ese motivo como observaciones, y aceptar con el motivo vacío MUST enviar `ANULADA` sin observaciones. Ante el éxito MUST anunciarse la anulación y volver a pedirse el detalle.

#### Scenario: Con motivo
- **WHEN** en el detalle de la factura 1 se anula con el motivo `Error en el cliente`
- **THEN** se envía `PATCH /facturas/1/estado` con `estado` `ANULADA` y `observaciones` `Error en el cliente`, y ante el `200` se anuncia la anulación y se pide de nuevo el detalle de la factura 1

#### Scenario: Sin motivo
- **WHEN** se anula la factura 1 aceptando el diálogo con el motivo vacío o solo con espacios
- **THEN** se envía `PATCH` con `estado` `ANULADA` y sin `observaciones`

#### Scenario: Motivo demasiado largo
- **WHEN** se acepta el diálogo con un motivo de 501 caracteres
- **THEN** se muestra un error que menciona 500 y no se envía ninguna petición

### Requirement: Cancelar la anulación no envía nada
Cancelar el diálogo de anulación MUST cancelar la operación sin enviar ningún `PATCH` y sin cambiar el detalle mostrado.

#### Scenario: Diálogo cancelado
- **WHEN** en el detalle de la factura 1 se elige Anular y se cancela el diálogo
- **THEN** no se envía ninguna petición y el detalle sigue mostrando la factura como EMITIDA

### Requirement: Acción sobre una factura sin repetición
Mientras una acción del detalle (emitir, anular o eliminar) está en curso, la interfaz MUST deshabilitar las demás acciones del detalle, MUST impedir que se dispare de nuevo y MUST liberarlas al terminar con éxito o con error.

#### Scenario: Doble clic en emitir
- **WHEN** en el detalle del borrador 6 se activa Emitir dos veces antes de la respuesta
- **THEN** se envía un solo `PATCH` y, mientras tanto, Emitir y Eliminar están deshabilitados

#### Scenario: Liberación tras error
- **WHEN** la emisión falla con `409`
- **THEN** Emitir y Eliminar vuelven a estar habilitados

### Requirement: Errores de acción visibles
Un error de emisión, anulación o eliminación MUST mostrarse en la región de estado con el detalle recibido de la API y MUST permanecer visible hasta la siguiente acción.

#### Scenario: Factura inexistente
- **WHEN** la API responde `404` a la eliminación del borrador 6 porque otro usuario lo borró
- **THEN** la región de estado muestra el detalle del `404` hasta la siguiente acción

### Requirement: Impresión del PDF de una factura informada
La acción imprimir PDF, en la fila del listado y en el detalle, MUST ser un enlace nativo a la URL del PDF de esa factura construida por el cliente HTTP, que el navegador descarga sin pasar por la región de estado ni por el enrutador; MUST NOT ofrecerse para un BORRADOR.

#### Scenario: Enlace de la factura 1
- **WHEN** con la base `http://localhost:8080/api` el listado trae la EMITIDA de id 1 y luego se muestra su detalle
- **THEN** la fila y el detalle tienen un enlace con destino `http://localhost:8080/api/facturas/1/pdf`

#### Scenario: Borrador sin impresión
- **WHEN** se muestran la fila y el detalle del BORRADOR de id 6
- **THEN** ninguno tiene enlace a una URL de PDF

### Requirement: Recarga conserva la página vigente
Al volver al listado desde otra pantalla (por el enlace «Facturas», «Volver al listado», Atrás o después de eliminar), la interfaz MUST volver a pedir el listado con los filtros y la página vigentes antes de salir; si la página quedó fuera de rango por una eliminación, MUST pedir la última página existente.

#### Scenario: Emisión en la página 2
- **WHEN** en la página 2 (índice 1) se abre el detalle del borrador 6, se lo emite y se vuelve al listado
- **THEN** se pide de nuevo el listado con los mismos filtros y `pagina=1`

#### Scenario: Se elimina el único elemento de la última página
- **WHEN** desde la página de índice 2, que tiene una sola fila, se abre el detalle de ese borrador, se lo elimina y la nueva respuesta para `pagina=2` trae `items` vacío con `totalPaginas` 2
- **THEN** se pide el listado con `pagina=1` y la tabla muestra esa página
