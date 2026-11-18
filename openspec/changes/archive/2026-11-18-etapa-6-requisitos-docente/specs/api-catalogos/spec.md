## ADDED Requirements

### Requirement: Búsqueda de clientes para el explorador
`GET /api/catalogos/clientes` MUST aceptar un parámetro opcional `q` y, si tiene texto, devolver solo los clientes cuya denominación o CUIT/CUIL contiene ese texto sin distinguir mayúsculas, con el mismo formato de opción y el mismo orden por id. Un `q` ausente, vacío o solo con espacios MUST devolver el catálogo completo, y los espacios exteriores MUST ignorarse.

#### Scenario: Por parte de la denominación
- **WHEN** existen «Juan Servicios» (`20-12345678-9`) y «Ferretería Ñandú» (`30-71234567-1`) y se pide `GET /api/catalogos/clientes?q=%20juan%20`
- **THEN** la respuesta es `200` con solo la opción `Juan Servicios (20-12345678-9)`

#### Scenario: Por parte del CUIT
- **WHEN** con los mismos clientes se pide `GET /api/catalogos/clientes?q=7123`
- **THEN** la respuesta tiene solo la opción de «Ferretería Ñandú»

#### Scenario: Sin coincidencias
- **WHEN** se pide `GET /api/catalogos/clientes?q=zzz`
- **THEN** la respuesta es `200` con el cuerpo `[]`

#### Scenario: Comodines como texto literal
- **WHEN** se pide `GET /api/catalogos/clientes?q=%25` (el carácter `%`) y ningún cliente contiene `%`
- **THEN** la respuesta es `200` con el cuerpo `[]`

### Requirement: Búsqueda de artículos por lista para el explorador
`GET /api/catalogos/lista-precio-articulos` MUST aceptar un parámetro opcional `q` y, si tiene texto, devolver solo las ofertas cuyo código de artículo, denominación de artículo o código de lista contiene ese texto sin distinguir mayúsculas, con el mismo formato de opción (id de la oferta, etiqueta y precio en `valor`) y el mismo orden por id. Un `q` ausente o en blanco MUST devolver el catálogo completo.

#### Scenario: Por denominación del artículo
- **WHEN** `ART-001` «Martillo» se ofrece en `GENERAL` y `MAYORISTA` y `ART-003` «Cable HDMI» en `GENERAL`, y se pide `GET /api/catalogos/lista-precio-articulos?q=martillo`
- **THEN** la respuesta tiene las dos ofertas del martillo, cada una con su precio

#### Scenario: Por código de lista
- **WHEN** con las mismas ofertas se pide `GET /api/catalogos/lista-precio-articulos?q=mayor`
- **THEN** la respuesta tiene solo la oferta `ART-001 - Martillo (MAYORISTA)`

### Requirement: Texto de búsqueda acotado
Un `q` de más de 100 caracteres (sin contar espacios exteriores) MUST responder `400 Bad Request` con un detalle que mencione `q` y el máximo.

#### Scenario: Búsqueda demasiado larga
- **WHEN** se pide `GET /api/catalogos/clientes` con un `q` de 101 caracteres
- **THEN** la respuesta es `400` y el detalle menciona `q` y 100
