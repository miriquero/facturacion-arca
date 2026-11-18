# frontend-consulta-facturas Specification

## Purpose
Define la pantalla de consulta de facturas del frontend: carga de catálogos, filtros, tabla de resultados con cantidad y mensaje sin datos, presentación de importes y fechas, enlaces de descarga sincronizados con la consulta y descarte de búsquedas obsoletas.

## Requirements

### Requirement: Carga inicial de catálogos y listado
Al abrir la página, la interfaz SHALL pedir los cinco catálogos y el listado sin filtros, completar los selectores con las etiquetas de cada opción y mostrar la tabla con el resultado.

#### Scenario: Selectores completos
- **WHEN** la página arranca y la API devuelve un punto de venta `0001 - Casa Central` y una moneda `Pesos` con código `PES`
- **THEN** el selector de punto de venta ofrece `0001 - Casa Central` y el de moneda ofrece `Pesos`, cada opción con el id de su catálogo como valor

#### Scenario: Cliente opcional
- **WHEN** se completan los selectores de cliente
- **THEN** el selector de cliente del formulario tiene además una primera opción `Consumidor Final` con valor vacío

#### Scenario: Catálogo que falla
- **WHEN** la API responde `500` a un catálogo
- **THEN** la región de estado muestra un error y el listado se presenta igual

### Requirement: Filtros de la consulta
La interfaz SHALL ofrecer filtros por fecha desde, fecha hasta, estado (`BORRADOR`, `EMITIDA`, `ANULADA` o todos) y monto mínimo, y al buscar MUST pedir el listado solo con los filtros informados.

#### Scenario: Búsqueda por estado y fecha
- **WHEN** se elige estado `EMITIDA`, fecha desde 2026-11-01 y se busca
- **THEN** se pide el listado con `estado=EMITIDA` y `fechaDesde=2026-11-01` y la tabla muestra la respuesta

#### Scenario: Limpiar filtros
- **WHEN** se limpian los filtros
- **THEN** los campos quedan vacíos y se pide el listado sin filtros

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
La interfaz MUST mostrar la cantidad de facturas del último resultado aplicado y, si es cero, un mensaje de que no hay facturas para los filtros elegidos en lugar de filas.

#### Scenario: Tres coincidencias
- **WHEN** el listado devuelve tres facturas
- **THEN** se muestra `3 facturas` como cantidad de coincidencias

#### Scenario: Sin coincidencias
- **WHEN** el listado devuelve `[]`
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
La interfaz MUST ofrecer enlaces de descarga PDF y Excel cuyo destino es la URL de reporte con los filtros del resultado que muestra la tabla, y MUST NOT ofrecer un enlace de TXT (ese formato queda disponible por la URL del cliente HTTP).

#### Scenario: Enlaces tras una búsqueda
- **WHEN** se busca con `estado` `ANULADA` y llega la respuesta
- **THEN** el enlace PDF apunta a la URL de reporte con `formato=pdf` y `estado=ANULADA`, y el de Excel a la misma con `formato=xlsx`

### Requirement: Búsquedas obsoletas descartadas
Solo la respuesta de la búsqueda más reciente MUST aplicarse; una respuesta o un error de una búsqueda anterior que llega después MUST descartarse sin modificar la tabla, la cantidad, los enlaces ni la región de estado.

#### Scenario: Respuestas en orden inverso
- **WHEN** se busca `BORRADOR` (búsqueda 1), luego `EMITIDA` (búsqueda 2), y la respuesta de la 2 llega antes que la de la 1
- **THEN** la tabla, la cantidad y los enlaces quedan con el resultado y los filtros de `EMITIDA` aunque la respuesta de `BORRADOR` llegue después

#### Scenario: Error tardío de una búsqueda vieja
- **WHEN** la búsqueda 1 falla después de que la búsqueda 2 ya se aplicó
- **THEN** no se muestra ningún error y la tabla conserva el resultado de la búsqueda 2

### Requirement: Tabla, cantidad y enlaces actualizados juntos
La tabla, la cantidad de coincidencias y los enlaces de descarga MUST actualizarse en el mismo paso con una misma respuesta; si una búsqueda vigente falla, MUST conservar el último resultado aplicado y mostrar el error.

#### Scenario: Búsqueda rechazada por la API
- **WHEN** la búsqueda vigente recibe `400` por fechas invertidas
- **THEN** la región de estado muestra el detalle del error y la tabla, la cantidad y los enlaces conservan el resultado anterior
