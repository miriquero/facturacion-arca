## MODIFIED Requirements

### Requirement: Carga inicial de catálogos y listado
Al abrir la aplicación, la interfaz SHALL pedir los cinco catálogos una sola vez, cualquiera sea la ruta de entrada, y completar los selectores de condición de IVA, punto de venta y moneda con las etiquetas de cada opción. Si la ruta de entrada es el listado (o una ruta desconocida), MUST pedir la primera página del listado sin filtros con el tamaño predeterminado y mostrar la tabla con el resultado; si es otra pantalla, el listado MUST pedirse recién la primera vez que se muestre. El cliente del formulario y del filtro MUST arrancar sin elegir (el del formulario mostrado como `Consumidor Final`).

#### Scenario: Selectores completos
- **WHEN** la aplicación arranca y la API devuelve un punto de venta `0001 - Casa Central` y una moneda `Pesos` con código `PES`
- **THEN** el selector de punto de venta ofrece `0001 - Casa Central` y el de moneda ofrece `Pesos`, cada opción con el id de su catálogo como valor

#### Scenario: Cliente opcional
- **WHEN** la aplicación termina de arrancar
- **THEN** el cliente del formulario se muestra como `Consumidor Final`, el filtro de cliente se muestra como «Todos» y ninguno tiene un id elegido

#### Scenario: Primera página
- **WHEN** la aplicación arranca en el listado
- **THEN** se pide el listado sin filtros con página 0 y tamaño 10

#### Scenario: Catálogo que falla
- **WHEN** la API responde `500` a un catálogo
- **THEN** la región de estado muestra un error y el listado se presenta igual

#### Scenario: Entrada por otra pantalla
- **WHEN** la aplicación arranca en `#/nueva` y después se navega al listado
- **THEN** los catálogos se pidieron una sola vez, el listado no se pidió al arrancar y se pide la página 0 sin filtros al mostrarse el listado

## ADDED Requirements

### Requirement: Estado de la consulta conservado al volver
Al salir del listado y volver a él (por la barra de navegación, «Volver al listado», Atrás o tras una operación), la interfaz MUST conservar en memoria los filtros aplicados, el cliente elegido en el filtro, la página y el tamaño de página, y MUST mostrar los valores de los campos de filtro tal como estaban; MUST volver a pedir esa misma página con esos mismos filtros para reflejar cambios hechos en otras pantallas.

#### Scenario: Volver con filtros y página
- **WHEN** se busca con estado `EMITIDA` y cliente 2, se pasa a la página 2 (índice 1), se abre el detalle de una factura y se vuelve atrás
- **THEN** el filtro de estado sigue en `EMITIDA`, el cliente del filtro sigue con el id 2 y se pide el listado con `estado=EMITIDA`, `clienteId=2` y `pagina=1`

#### Scenario: Filtros editados sin buscar
- **WHEN** se cambia el estado del filtro a `ANULADA` sin buscar, se abre la alta y se vuelve al listado
- **THEN** el campo sigue mostrando `ANULADA` y el listado se pide con los filtros del último resultado mostrado, no con los editados sin buscar
