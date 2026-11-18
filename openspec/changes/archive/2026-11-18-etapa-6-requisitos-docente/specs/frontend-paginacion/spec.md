## ADDED Requirements

### Requirement: Controles de paginación bajo la grilla
Debajo de la tabla de facturas la interfaz SHALL ofrecer, dentro de una región de navegación con nombre accesible, un botón «Anterior», un botón «Siguiente», el texto `Página X de Y` (con X desde 1) y un selector de tamaño de página con las opciones 10, 20 y 50 (10 por defecto).

#### Scenario: Primera de tres páginas
- **WHEN** el listado devuelve `pagina` 0, `tamanio` 10, `totalElementos` 23 y `totalPaginas` 3
- **THEN** se muestra `Página 1 de 3`, «Anterior» está deshabilitado y «Siguiente» habilitado

#### Scenario: Última página
- **WHEN** el listado devuelve `pagina` 2 y `totalPaginas` 3
- **THEN** se muestra `Página 3 de 3`, «Siguiente» está deshabilitado y «Anterior» habilitado

#### Scenario: Sin resultados
- **WHEN** el listado devuelve `totalElementos` 0 y `totalPaginas` 0
- **THEN** se muestra `Página 1 de 1` y ambos botones están deshabilitados

#### Scenario: Región nombrada
- **WHEN** se inspeccionan los controles de paginación
- **THEN** están dentro de un `nav` con nombre accesible que menciona la paginación de facturas y los botones declaran `type="button"`

### Requirement: Cambio de página con los filtros vigentes
Activar «Siguiente» o «Anterior» MUST pedir la página contigua con los mismos filtros del resultado que muestra la tabla y el tamaño vigente; la tabla, la cantidad y los controles MUST actualizarse juntos con la respuesta.

#### Scenario: Siguiente con filtros
- **WHEN** con un resultado filtrado por `estado=EMITIDA` en la página 0 de 3 se activa «Siguiente»
- **THEN** se pide el listado con `estado=EMITIDA` y `pagina=1`, y al llegar la respuesta se muestra `Página 2 de 3`

#### Scenario: Filtros editados pero no buscados
- **WHEN** se cambia el campo de estado a `BORRADOR` sin buscar y se activa «Siguiente»
- **THEN** la página siguiente se pide con los filtros del resultado mostrado, no con `BORRADOR`

### Requirement: Cambio de tamaño y de filtros vuelve a la primera página
Cambiar el tamaño de página MUST pedir la página 0 con los filtros vigentes y el nuevo tamaño; buscar o limpiar filtros MUST pedir siempre la página 0.

#### Scenario: Tamaño 20
- **WHEN** en la página 2 se elige el tamaño 20
- **THEN** se pide el listado con `pagina=0` y `tamanio=20`

#### Scenario: Nueva búsqueda desde la página 3
- **WHEN** estando en la página 3 se busca con otro filtro
- **THEN** se pide el listado con `pagina=0`

### Requirement: Anuncio del cambio de página
Al aplicarse una página pedida con los controles de paginación, la región de estado MUST anunciar `Página X de Y` con la cantidad total de facturas, y el texto de la página actual MUST marcarse con `aria-current="page"`.

#### Scenario: Anuncio al avanzar
- **WHEN** se activa «Siguiente» y llega la página 1 de 3 con `totalElementos` 23
- **THEN** la región de estado anuncia `Página 2 de 3 (23 facturas)` y el indicador de página tiene `aria-current="page"`

### Requirement: Páginas obsoletas descartadas y sin pedidos repetidos
Solo la respuesta del pedido de listado más reciente (búsqueda, cambio de página o de tamaño) MUST aplicarse; mientras un cambio de página está en curso, los botones de paginación MUST deshabilitarse y MUST liberarse al terminar con éxito o con error.

#### Scenario: Doble clic en Siguiente
- **WHEN** se activa «Siguiente» dos veces antes de la respuesta
- **THEN** se envía un solo pedido de la página 1

#### Scenario: Respuesta vieja después de una búsqueda
- **WHEN** se activa «Siguiente» (pedido 1), luego se busca con otro filtro (pedido 2) y la respuesta del pedido 1 llega después de la del 2
- **THEN** la tabla, la cantidad, los controles y los enlaces quedan con el resultado del pedido 2

#### Scenario: Error al paginar
- **WHEN** el pedido de la página 1 falla con `500`
- **THEN** la región de estado muestra el error, la tabla sigue en la página 0 y los botones vuelven a habilitarse según esa página
