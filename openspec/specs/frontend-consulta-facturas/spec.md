# frontend-consulta-facturas Specification

## Purpose
Define la pantalla de consulta de facturas del frontend: carga de catálogos, filtros, tabla de resultados con cantidad y mensaje sin datos, presentación de importes y fechas, enlaces de descarga sincronizados con la consulta y descarte de búsquedas obsoletas.

## Requirements

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

### Requirement: Monto mínimo no negativo en el formulario
El filtro de monto mínimo MUST aceptar solo números finitos mayores o iguales a cero; un valor inválido MUST mostrarse como error del campo y MUST NOT enviar la búsqueda.

#### Scenario: Monto negativo
- **WHEN** se ingresa `-1` como monto mínimo y se busca
- **THEN** el campo queda marcado como inválido con un mensaje asociado y no se envía ninguna petición

#### Scenario: Monto cero
- **WHEN** se ingresa `0` y se busca
- **THEN** se pide el listado con `montoMinimo=0`

### Requirement: Tabla de resultados
La tabla MUST mostrar una fila por factura, en el orden recibido, con número, fecha, cliente, condición de IVA, punto de venta, cantidad de ítems, total, estado y acciones; un borrador sin número MUST mostrar un guion en la columna de número.

#### Scenario: Borrador y emitida
- **WHEN** el listado trae una factura EMITIDA número 3 y un BORRADOR sin número
- **THEN** la tabla tiene dos filas, la primera con `3` y la segunda con `—` en número

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

### Requirement: Importes con la moneda de cada factura
Cada importe MUST presentarse con dos decimales en el formato de la moneda de su factura: `PES` como pesos argentinos (ARS) y `DOL` como dólares estadounidenses (USD); un código no reconocido MUST quedar visible junto al importe. Ningún importe MUST convertirse entre monedas.

#### Scenario: Pesos
- **WHEN** una factura tiene total 7260 y código `PES`
- **THEN** el total se muestra como importe en ARS con dos decimales en formato argentino

#### Scenario: Dólares
- **WHEN** una factura tiene total 9680 y código `DOL`
- **THEN** el total se muestra como importe en USD con el mismo valor 9680, sin conversión

#### Scenario: Código desconocido
- **WHEN** una factura tiene total 100 y código `EUR`
- **THEN** el total se muestra con dos decimales y el texto `EUR` visible

### Requirement: Fechas sin corrimiento de zona horaria
Las fechas `yyyy-MM-dd` recibidas MUST mostrarse como el mismo día calendario en formato `dd/mm/aaaa`, sin interpretarlas como instantes UTC.

#### Scenario: Primer día del mes
- **WHEN** una factura tiene `fechaEmision` `2026-11-01`
- **THEN** la fecha se muestra `01/11/2026`

### Requirement: Enlaces de descarga con la consulta vigente
La interfaz MUST ofrecer enlaces de descarga PDF y Excel cuyo destino es la URL de reporte con los filtros del resultado que muestra la tabla, sin parámetros de paginación (el reporte exporta todo lo filtrado), y MUST NOT ofrecer un enlace de TXT (ese formato queda disponible por la URL del cliente HTTP).

#### Scenario: Enlaces tras una búsqueda
- **WHEN** se busca con `estado` `ANULADA` y `informada` `true` y llega la respuesta
- **THEN** el enlace PDF apunta a la URL de reporte con `formato=pdf`, `estado=ANULADA` e `informada=true`, y el de Excel a la misma con `formato=xlsx`

#### Scenario: Cambio de página sin cambio de enlaces
- **WHEN** se pasa a la página 2 de un resultado filtrado por `clienteId=2`
- **THEN** los enlaces de descarga siguen apuntando al reporte con `clienteId=2` y no contienen `pagina` ni `tamanio`

### Requirement: Búsquedas obsoletas descartadas
Solo la respuesta de la búsqueda más reciente MUST aplicarse; una respuesta o un error de una búsqueda anterior que llega después MUST descartarse sin modificar la tabla, la cantidad, los enlaces ni la región de estado.

#### Scenario: Respuestas en orden inverso
- **WHEN** se busca `BORRADOR` (búsqueda 1), luego `EMITIDA` (búsqueda 2), y la respuesta de la 2 llega antes que la de la 1
- **THEN** la tabla, la cantidad y los enlaces quedan con el resultado y los filtros de `EMITIDA` aunque la respuesta de `BORRADOR` llegue después

#### Scenario: Error tardío de una búsqueda vieja
- **WHEN** la búsqueda 1 falla después de que la búsqueda 2 ya se aplicó
- **THEN** no se muestra ningún error y la tabla conserva el resultado de la búsqueda 2

### Requirement: Tabla, cantidad y enlaces actualizados juntos
La tabla, la cantidad de coincidencias, los controles de paginación y los enlaces de descarga MUST actualizarse en el mismo paso con una misma respuesta; si una búsqueda o un cambio de página vigente falla, MUST conservar el último resultado aplicado y mostrar el error.

#### Scenario: Búsqueda rechazada por la API
- **WHEN** la búsqueda vigente recibe `400` por fechas invertidas
- **THEN** la región de estado muestra el detalle del error y la tabla, la cantidad, la paginación y los enlaces conservan el resultado anterior

### Requirement: Número de factura válido en el filtro
El filtro de número MUST aceptar solo enteros mayores o iguales a 1; un valor inválido MUST mostrarse como error del campo y MUST NOT enviar la búsqueda. Un número vacío MUST tratarse como filtro ausente.

#### Scenario: Número cero o decimal
- **WHEN** se ingresa `0` o `2.5` como número y se busca
- **THEN** el campo queda marcado como inválido con un mensaje asociado y no se envía ninguna petición

#### Scenario: Número válido
- **WHEN** se ingresa `12` y se busca
- **THEN** se pide el listado con `numero=12`

### Requirement: Estado de la consulta conservado al volver
Al salir del listado y volver a él (por la barra de navegación, «Volver al listado», Atrás o tras una operación), la interfaz MUST conservar en memoria los filtros aplicados, el cliente elegido en el filtro, la página y el tamaño de página, y MUST mostrar los valores de los campos de filtro tal como estaban; MUST volver a pedir esa misma página con esos mismos filtros para reflejar cambios hechos en otras pantallas.

#### Scenario: Volver con filtros y página
- **WHEN** se busca con estado `EMITIDA` y cliente 2, se pasa a la página 2 (índice 1), se abre el detalle de una factura y se vuelve atrás
- **THEN** el filtro de estado sigue en `EMITIDA`, el cliente del filtro sigue con el id 2 y se pide el listado con `estado=EMITIDA`, `clienteId=2` y `pagina=1`

#### Scenario: Filtros editados sin buscar
- **WHEN** se cambia el estado del filtro a `ANULADA` sin buscar, se abre la alta y se vuelve al listado
- **THEN** el campo sigue mostrando `ANULADA` y el listado se pide con los filtros del último resultado mostrado, no con los editados sin buscar
