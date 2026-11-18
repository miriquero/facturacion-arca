## MODIFIED Requirements

### Requirement: Operaciones del contrato
El cliente SHALL exponer una operación por cada ruta usada por la interfaz: listado paginado con filtros, detalle por id, los cinco catálogos, la búsqueda de clientes y de opciones lista–artículo, alta, reemplazo, cambio de estado y eliminación, con el método y la ruta del contrato de la API. El listado MUST devolver la página recibida (`items`, `pagina`, `tamanio`, `totalElementos`, `totalPaginas`) sin transformarla.

#### Scenario: Cambio de estado
- **WHEN** el cliente anula la factura 12 con el motivo `Error en el cliente`
- **THEN** envía `PATCH /facturas/12/estado` con el cuerpo `{"estado":"ANULADA","observaciones":"Error en el cliente"}`

#### Scenario: Emisión sin observaciones
- **WHEN** el cliente emite la factura 12 sin motivo
- **THEN** el cuerpo es `{"estado":"EMITIDA"}` y no incluye `observaciones`

#### Scenario: Reemplazo de un borrador
- **WHEN** el cliente actualiza el borrador 5
- **THEN** envía `PUT /facturas/5` con el request completo y devuelve la fila de reporte de la respuesta

#### Scenario: Catálogo de artículos por lista
- **WHEN** el cliente pide las opciones de lista–artículo
- **THEN** envía `GET /catalogos/lista-precio-articulos` y devuelve las opciones con `id`, `etiqueta`, `valor` y `codigoMoneda`

#### Scenario: Listado paginado
- **WHEN** el cliente pide el listado con `estado` `EMITIDA`, página 2 y tamaño 20, y la API responde una página con `totalElementos` 45
- **THEN** la URL contiene `estado=EMITIDA`, `pagina=2` y `tamanio=20`, y la operación devuelve la página con sus `items` y `totalElementos` 45

### Requirement: Filtros vacíos omitidos en la consulta
El cliente MUST incluir en la query string solo los filtros informados (`fechaDesde`, `fechaHasta`, `estado`, `montoMinimo`, `numero`, `clienteId`, `informada`), codificados como parámetros de URL, y MUST omitir los vacíos. En el listado MUST agregar además `pagina` y `tamanio`.

#### Scenario: Sin filtros
- **WHEN** se pide el listado con todos los filtros vacíos, página 0 y tamaño 10
- **THEN** la URL es `/facturas?pagina=0&tamanio=10` y no contiene ningún parámetro de filtro

#### Scenario: Filtros combinados
- **WHEN** se pide el listado con `fechaDesde` 2026-11-01, `estado` `EMITIDA`, `montoMinimo` 0, `numero` 4, `clienteId` 2 e `informada` `true`
- **THEN** la URL contiene `fechaDesde=2026-11-01`, `estado=EMITIDA`, `montoMinimo=0`, `numero=4`, `clienteId=2` e `informada=true`, y no contiene `fechaHasta`

### Requirement: URLs de reporte con los mismos filtros
El cliente MUST construir la URL de `GET /facturas/reporte` para los formatos `pdf`, `xlsx` y `txt` con el parámetro `formato` y exactamente los mismos parámetros de filtro que usaría el listado, y MUST NOT incluir `pagina` ni `tamanio`.

#### Scenario: Excel filtrado
- **WHEN** se construye la URL de reporte `xlsx` con `estado` `EMITIDA` e `informada` `true`
- **THEN** la URL es la base seguida de `/facturas/reporte` con `formato=xlsx`, `estado=EMITIDA` e `informada=true`, sin `pagina` ni `tamanio`

#### Scenario: TXT disponible por URL
- **WHEN** se construye la URL de reporte `txt` sin filtros
- **THEN** la URL contiene `formato=txt` y ningún parámetro de filtro ni de paginación

## ADDED Requirements

### Requirement: Búsqueda de catálogos para el explorador
El cliente MUST ofrecer la búsqueda de clientes y de opciones lista–artículo enviando el texto como parámetro `q` codificado, y MUST omitir `q` cuando el texto está vacío o solo tiene espacios.

#### Scenario: Búsqueda con espacios y acentos
- **WHEN** se buscan clientes con el texto `Ñandú & Cía`
- **THEN** la petición es `GET /catalogos/clientes` con `q` codificado como parámetro de URL y devuelve las opciones recibidas

#### Scenario: Texto vacío
- **WHEN** se buscan opciones lista–artículo con el texto `   `
- **THEN** la petición es `GET /catalogos/lista-precio-articulos` sin `?`

### Requirement: URL del PDF de una factura
El cliente MUST construir la URL de `GET /facturas/{id}/pdf` sobre la URL base configurada.

#### Scenario: PDF de la factura 7
- **WHEN** con la base `http://localhost:8080/api` se construye la URL del PDF de la factura 7
- **THEN** la URL es `http://localhost:8080/api/facturas/7/pdf`
