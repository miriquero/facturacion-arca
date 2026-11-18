## MODIFIED Requirements

### Requirement: Carga inicial de catálogos y listado
Al abrir la página, la interfaz SHALL pedir los cinco catálogos y la primera página del listado sin filtros con el tamaño predeterminado, completar los selectores de condición de IVA, punto de venta y moneda con las etiquetas de cada opción y mostrar la tabla con el resultado. El cliente del formulario y del filtro MUST arrancar sin elegir (el del formulario mostrado como `Consumidor Final`).

#### Scenario: Selectores completos
- **WHEN** la página arranca y la API devuelve un punto de venta `0001 - Casa Central` y una moneda `Pesos` con código `PES`
- **THEN** el selector de punto de venta ofrece `0001 - Casa Central` y el de moneda ofrece `Pesos`, cada opción con el id de su catálogo como valor

#### Scenario: Cliente opcional
- **WHEN** la página termina de arrancar
- **THEN** el cliente del formulario se muestra como `Consumidor Final`, el filtro de cliente se muestra como «Todos» y ninguno tiene un id elegido

#### Scenario: Primera página
- **WHEN** la página arranca
- **THEN** se pide el listado sin filtros con página 0 y tamaño 10

#### Scenario: Catálogo que falla
- **WHEN** la API responde `500` a un catálogo
- **THEN** la región de estado muestra un error y el listado se presenta igual

### Requirement: Filtros de la consulta
La interfaz SHALL ofrecer filtros por número de factura, fecha desde, fecha hasta, cliente (elegido con el explorador de clientes y con un botón para quitarlo), informada a ARCA (`Todas`, `Informadas`, `No informadas`), estado (`BORRADOR`, `EMITIDA`, `ANULADA` o todos) y monto mínimo. Al buscar MUST pedir la página 0 del listado solo con los filtros informados y el tamaño de página vigente.

#### Scenario: Búsqueda por estado y fecha
- **WHEN** se elige estado `EMITIDA`, fecha desde 2026-11-01 y se busca
- **THEN** se pide el listado con `estado=EMITIDA`, `fechaDesde=2026-11-01` y página 0, y la tabla muestra la respuesta

#### Scenario: Búsqueda por número, cliente e informada
- **WHEN** se escribe el número `4`, se elige en el explorador el cliente de id 2, se elige `Informadas` y se busca
- **THEN** se pide el listado con `numero=4`, `clienteId=2`, `informada=true` y página 0

#### Scenario: No informadas
- **WHEN** se elige `No informadas` y se busca
- **THEN** se pide el listado con `informada=false`

#### Scenario: Quitar el cliente del filtro
- **WHEN** con el cliente 2 elegido en el filtro se activa el botón para quitarlo y se busca
- **THEN** el filtro de cliente vuelve a «Todos» y el listado se pide sin `clienteId`

#### Scenario: Limpiar filtros
- **WHEN** se limpian los filtros
- **THEN** los campos quedan vacíos, el cliente del filtro vuelve a «Todos», informada vuelve a `Todas` y se pide la página 0 del listado sin filtros

### Requirement: Cantidad de coincidencias y mensaje sin datos
La interfaz MUST mostrar la cantidad total de facturas que cumplen los filtros (`totalElementos` de la última página aplicada, no solo las filas visibles) y, si es cero, un mensaje de que no hay facturas para los filtros elegidos en lugar de filas.

#### Scenario: Tres coincidencias
- **WHEN** el listado devuelve una página con tres filas y `totalElementos` 3
- **THEN** se muestra `3 facturas` como cantidad de coincidencias

#### Scenario: Más coincidencias que filas
- **WHEN** el listado devuelve una página con 10 filas y `totalElementos` 23
- **THEN** la tabla tiene 10 filas y se muestra `23 facturas` como cantidad de coincidencias

#### Scenario: Sin coincidencias
- **WHEN** el listado devuelve una página con `items` vacío y `totalElementos` 0
- **THEN** se muestra `0 facturas` y un mensaje de que no hay facturas para los filtros elegidos

### Requirement: Enlaces de descarga con la consulta vigente
La interfaz MUST ofrecer enlaces de descarga PDF y Excel cuyo destino es la URL de reporte con los filtros del resultado que muestra la tabla, sin parámetros de paginación (el reporte exporta todo lo filtrado), y MUST NOT ofrecer un enlace de TXT (ese formato queda disponible por la URL del cliente HTTP).

#### Scenario: Enlaces tras una búsqueda
- **WHEN** se busca con `estado` `ANULADA` y `informada` `true` y llega la respuesta
- **THEN** el enlace PDF apunta a la URL de reporte con `formato=pdf`, `estado=ANULADA` e `informada=true`, y el de Excel a la misma con `formato=xlsx`

#### Scenario: Cambio de página sin cambio de enlaces
- **WHEN** se pasa a la página 2 de un resultado filtrado por `clienteId=2`
- **THEN** los enlaces de descarga siguen apuntando al reporte con `clienteId=2` y no contienen `pagina` ni `tamanio`

### Requirement: Tabla, cantidad y enlaces actualizados juntos
La tabla, la cantidad de coincidencias, los controles de paginación y los enlaces de descarga MUST actualizarse en el mismo paso con una misma respuesta; si una búsqueda o un cambio de página vigente falla, MUST conservar el último resultado aplicado y mostrar el error.

#### Scenario: Búsqueda rechazada por la API
- **WHEN** la búsqueda vigente recibe `400` por fechas invertidas
- **THEN** la región de estado muestra el detalle del error y la tabla, la cantidad, la paginación y los enlaces conservan el resultado anterior

## ADDED Requirements

### Requirement: Número de factura válido en el filtro
El filtro de número MUST aceptar solo enteros mayores o iguales a 1; un valor inválido MUST mostrarse como error del campo y MUST NOT enviar la búsqueda. Un número vacío MUST tratarse como filtro ausente.

#### Scenario: Número cero o decimal
- **WHEN** se ingresa `0` o `2.5` como número y se busca
- **THEN** el campo queda marcado como inválido con un mensaje asociado y no se envía ninguna petición

#### Scenario: Número válido
- **WHEN** se ingresa `12` y se busca
- **THEN** se pide el listado con `numero=12`
