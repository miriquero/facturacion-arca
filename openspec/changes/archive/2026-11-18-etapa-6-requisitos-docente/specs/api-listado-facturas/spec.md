## MODIFIED Requirements

### Requirement: Listado de facturas por HTTP
`GET /api/facturas` SHALL responder `200 OK` con un objeto JSON de página con exactamente los campos `items`, `pagina`, `tamanio`, `totalElementos` y `totalPaginas`. `items` MUST ser un arreglo de filas de reporte, cada una con exactamente los campos `id`, `numeroFactura`, `fechaEmision` (`yyyy-MM-dd`), `clienteDenominacion`, `condicionIva`, `puntoVentaDescripcion`, `importeTotal`, `cantidadItems`, `estado` y `codigoMoneda`, en el orden del reporte (fecha de emisión descendente y, ante igual fecha, id descendente). `totalElementos` MUST ser la cantidad de facturas que cumplen los filtros, sin importar la página, y `totalPaginas` MUST ser `totalElementos` dividido `tamanio` redondeado hacia arriba (0 si no hay facturas).

#### Scenario: Sin filtros
- **WHEN** se pide `GET /api/facturas` con tres facturas guardadas del 2026-11-01, 2026-11-05 y 2026-11-10
- **THEN** la respuesta es `200` con `pagina` 0, `tamanio` 10, `totalElementos` 3, `totalPaginas` 1 e `items` con tres filas en el orden 2026-11-10, 2026-11-05, 2026-11-01, cada fila con solo los diez campos del reporte

#### Scenario: Cliente opcional en el listado
- **WHEN** se pide `GET /api/facturas` con una factura sin cliente
- **THEN** su fila en `items` tiene `clienteDenominacion` igual a `Consumidor Final`

#### Scenario: Borrador sin número
- **WHEN** se pide `GET /api/facturas` con una factura BORRADOR
- **THEN** su fila en `items` tiene `numeroFactura` nulo y `estado` igual a `BORRADOR`

### Requirement: Consulta sin resultados
Una consulta del listado sin coincidencias MUST responder `200 OK` con `items` vacío, `totalElementos` 0 y `totalPaginas` 0, y MUST NOT responder `404`.

#### Scenario: Base sin facturas
- **WHEN** se pide `GET /api/facturas` sin facturas guardadas
- **THEN** la respuesta es `200` con `items` igual a `[]`, `totalElementos` 0 y `totalPaginas` 0

#### Scenario: Filtros sin coincidencias
- **WHEN** se pide `GET /api/facturas?montoMinimo=999999999` y ninguna factura llega a ese total
- **THEN** la respuesta es `200` con `items` igual a `[]` y `totalElementos` 0

## ADDED Requirements

### Requirement: Paginación en el servidor
`GET /api/facturas` MUST aceptar los parámetros opcionales `pagina` (entero desde 0, predeterminado 0) y `tamanio` (entero entre 1 y 100, predeterminado 10) y devolver en `items` solo las filas de esa página, manteniendo el orden del reporte en todas las páginas; la respuesta MUST informar en `pagina` y `tamanio` los valores aplicados.

#### Scenario: Segunda página
- **WHEN** con 12 facturas de fechas distintas se pide `GET /api/facturas?pagina=1&tamanio=5`
- **THEN** la respuesta tiene `pagina` 1, `tamanio` 5, `totalElementos` 12, `totalPaginas` 3 e `items` con la 6.ª a la 10.ª factura del orden del reporte

#### Scenario: Última página incompleta
- **WHEN** con las mismas 12 facturas se pide `GET /api/facturas?pagina=2&tamanio=5`
- **THEN** `items` tiene las dos facturas más antiguas

#### Scenario: Páginas sin solapamiento con fechas iguales
- **WHEN** con 6 facturas de la misma fecha se piden las páginas 0, 1 y 2 con `tamanio=2`
- **THEN** la unión de los `items` contiene las 6 facturas una sola vez cada una, en orden de id descendente

#### Scenario: Página posterior a la última
- **WHEN** con 3 facturas se pide `GET /api/facturas?pagina=5`
- **THEN** la respuesta es `200` con `items` igual a `[]`, `totalElementos` 3 y `totalPaginas` 1

#### Scenario: Conteo con renglones múltiples
- **WHEN** con dos facturas de tres renglones cada una y una sin renglones se pide `GET /api/facturas?tamanio=10`
- **THEN** `totalElementos` es 3 y cada factura aparece una sola vez con su cantidad de ítems

### Requirement: Paginación inválida rechazada
Un `pagina` negativo, un `tamanio` menor que 1 o mayor que 100, o cualquiera de los dos con un valor no entero MUST responder `400 Bad Request` con un detalle que nombre el parámetro.

#### Scenario: Página negativa
- **WHEN** se pide `GET /api/facturas?pagina=-1`
- **THEN** la respuesta es `400` y el detalle menciona `pagina`

#### Scenario: Tamaño fuera de rango
- **WHEN** se pide `GET /api/facturas` con `tamanio` igual a `0` y luego a `101`
- **THEN** ambas respuestas son `400` y el detalle menciona `tamanio` y el máximo 100

#### Scenario: Tamaño no numérico
- **WHEN** se pide `GET /api/facturas?tamanio=diez`
- **THEN** la respuesta es `400` y el detalle menciona `tamanio`

### Requirement: Filtro por número de factura
El parámetro opcional `numero` MUST devolver solo las facturas cuyo número es exactamente ese valor (puede haber una por punto de venta); un `numero` menor que 1 o no entero MUST responder `400 Bad Request` con un detalle que nombre el parámetro. Los borradores, que no tienen número, MUST NOT coincidir con ningún `numero`.

#### Scenario: Número existente
- **WHEN** con las facturas EMITIDA número 3 y 4 y un BORRADOR se pide `GET /api/facturas?numero=4`
- **THEN** `items` tiene solo la factura número 4

#### Scenario: Mismo número en dos puntos de venta
- **WHEN** existen la factura número 1 del punto de venta 1 y la número 1 del punto de venta 2 y se pide `GET /api/facturas?numero=1`
- **THEN** `items` tiene ambas facturas

#### Scenario: Número inválido
- **WHEN** se pide `GET /api/facturas` con `numero` igual a `0` y luego a `abc`
- **THEN** ambas respuestas son `400` y el detalle menciona `numero`

### Requirement: Filtro por cliente
El parámetro opcional `clienteId` MUST devolver solo las facturas de ese cliente; un id que no corresponde a ningún cliente MUST responder `200` con `items` vacío, y un valor no entero MUST responder `400` nombrando el parámetro.

#### Scenario: Facturas de un cliente
- **WHEN** con dos facturas del cliente 1, una del cliente 2 y una sin cliente se pide `GET /api/facturas?clienteId=1`
- **THEN** `items` tiene solo las dos facturas del cliente 1 y `totalElementos` es 2

#### Scenario: Cliente inexistente
- **WHEN** se pide `GET /api/facturas?clienteId=999999`
- **THEN** la respuesta es `200` con `items` igual a `[]`

### Requirement: Filtro por informada a ARCA
El parámetro opcional `informada` MUST compararse sin distinguir mayúsculas ni espacios exteriores: `true` MUST devolver solo las facturas con CAE (EMITIDA y ANULADA) y `false` solo las que no lo tienen (BORRADOR); un valor vacío MUST tratarse como filtro ausente y cualquier otro valor MUST responder `400 Bad Request` con un detalle que mencione `informada` y los valores admitidos.

#### Scenario: Solo informadas
- **WHEN** con una factura en cada estado se pide `GET /api/facturas?informada=true`
- **THEN** `items` tiene la EMITIDA y la ANULADA y no el BORRADOR

#### Scenario: Solo no informadas
- **WHEN** con una factura en cada estado se pide `GET /api/facturas?informada=%20FALSE%20`
- **THEN** `items` tiene solo el BORRADOR

#### Scenario: Valor no admitido
- **WHEN** se pide `GET /api/facturas?informada=si`
- **THEN** la respuesta es `400` y el detalle menciona `informada`, `true` y `false`

### Requirement: Filtros nuevos combinados con los existentes
Los filtros `numero`, `clienteId` e `informada` MUST combinarse por conjunción con `fechaDesde`, `fechaHasta`, `estado` y `montoMinimo`, y el total y la paginación MUST calcularse sobre el resultado combinado; una combinación contradictoria MUST responder `200` con `items` vacío.

#### Scenario: Cliente, informada y fechas
- **WHEN** con facturas del cliente 1 EMITIDA del 2026-11-05, BORRADOR del 2026-11-05 y EMITIDA del 2026-10-01, y una EMITIDA del cliente 2 del 2026-11-05, se pide `GET /api/facturas?clienteId=1&informada=true&fechaDesde=2026-11-01`
- **THEN** `items` tiene solo la EMITIDA del cliente 1 del 2026-11-05 y `totalElementos` es 1

#### Scenario: Combinación contradictoria
- **WHEN** se pide `GET /api/facturas?estado=BORRADOR&informada=true`
- **THEN** la respuesta es `200` con `items` igual a `[]`
