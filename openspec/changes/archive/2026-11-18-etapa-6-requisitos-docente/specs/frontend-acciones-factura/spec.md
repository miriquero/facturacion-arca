## MODIFIED Requirements

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

## ADDED Requirements

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
