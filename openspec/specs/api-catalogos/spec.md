# api-catalogos Specification

## Purpose
Expone por HTTP, en solo lectura, los cinco catálogos que el frontend necesita para filtrar y armar facturas, con un formato de opción común y sin operaciones de alta, modificación ni baja.

## Requirements

### Requirement: Formato de opción común
Cada catálogo SHALL responder `200 OK` con un arreglo JSON de opciones ordenadas por id ascendente, cada una con exactamente `id`, `etiqueta`, `valor` y `codigoMoneda`; `valor` y `codigoMoneda` MUST ser nulos salvo en los catálogos que los definen.

#### Scenario: Catálogo vacío
- **WHEN** se pide cualquiera de los cinco catálogos sin registros guardados
- **THEN** la respuesta es `200` con el cuerpo `[]`

### Requirement: Catálogo de puntos de venta
`GET /api/catalogos/puntos-venta` MUST devolver una opción por punto de venta con etiqueta formada por el número con cuatro dígitos, un guion y la descripción.

#### Scenario: Punto de venta 1
- **WHEN** existe el punto de venta número 1 «Casa Central»
- **THEN** su opción tiene el id del punto de venta, etiqueta `0001 - Casa Central`, `valor` nulo y `codigoMoneda` nulo

### Requirement: Catálogo de clientes
`GET /api/catalogos/clientes` MUST devolver una opción por cliente con etiqueta formada por la denominación y, entre paréntesis, el CUIT/CUIL.

#### Scenario: Cliente con CUIT
- **WHEN** existe el cliente «Juan Servicios» con CUIT `20-12345678-9`
- **THEN** su opción tiene etiqueta `Juan Servicios (20-12345678-9)`

### Requirement: Catálogo de condiciones frente al IVA
`GET /api/catalogos/condiciones-iva` MUST devolver una opción por condición con la denominación como etiqueta.

#### Scenario: Responsable Inscripto
- **WHEN** existe la condición «Responsable Inscripto»
- **THEN** su opción tiene etiqueta `Responsable Inscripto`

### Requirement: Catálogo de monedas
`GET /api/catalogos/tipos-moneda` MUST devolver una opción por moneda con la denominación como etiqueta y su código en `codigoMoneda`.

#### Scenario: Pesos y dólares
- **WHEN** existen las monedas «Pesos» con código `PES` y «Dólar estadounidense» con código `DOL`
- **THEN** sus opciones tienen `codigoMoneda` `PES` y `DOL` respectivamente y `valor` nulo

### Requirement: Catálogo de artículos por lista de precios
`GET /api/catalogos/lista-precio-articulos` MUST devolver una opción por artículo ofrecido en cada lista, cuyo `id` es el de la oferta en la lista, con etiqueta formada por código y denominación del artículo y, entre paréntesis, el código de la lista, y con el precio de venta en `valor`.

#### Scenario: Martillo en la lista general
- **WHEN** el artículo `ART-001` «Martillo» se ofrece en la lista `GENERAL` a 3000.00
- **THEN** su opción tiene el id de esa oferta, etiqueta `ART-001 - Martillo (GENERAL)`, `valor` 3000.00 y `codigoMoneda` nulo

#### Scenario: Mismo artículo en dos listas
- **WHEN** el artículo `ART-001` se ofrece en las listas `GENERAL` y `MAYORISTA` con precios distintos
- **THEN** hay dos opciones, cada una con el id de su oferta y su propio precio

### Requirement: Catálogos de solo lectura
Las rutas de catálogos MUST NOT aceptar altas, modificaciones ni bajas: `POST`, `PUT`, `PATCH` y `DELETE` MUST responder `405 Method Not Allowed` sin cambiar datos.

#### Scenario: Intento de alta
- **WHEN** se envía `POST /api/catalogos/clientes` con un cuerpo JSON
- **THEN** la respuesta es `405` y la cantidad de clientes no cambia
