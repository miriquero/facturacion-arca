# Spec Delta

## Purpose

Expone por HTTP una factura completa por su identificador, con los datos que el frontend necesita para mostrarla y editar su cabecera y renglones, sin serializar entidades.

## ADDED Requirements

### Requirement: Detalle de factura por id
`GET /api/facturas/{id}` SHALL responder `200 OK` con la cabecera de la factura: `id`, `numero`, `fechaEmision`, `estado` (texto), `clienteId`, `clienteDenominacion`, `condicionIvaId`, `condicionIva`, `puntoVentaId`, `puntoVentaNumero`, `puntoVentaDescripcion`, `tipoMonedaId`, `codigoMoneda`, `importeTotal`, `importeCobrado`, `importeSaldo`, `cae`, `caeFechaVencimiento`, `resultadoAfip`, `fechaAnulacion`, `observaciones` y `detalles`.

#### Scenario: Factura emitida con cliente
- **WHEN** se pide el detalle de una factura EMITIDA número 1 del punto de venta 1 «Casa Central», cliente «Juan Servicios», condición «Responsable Inscripto», moneda `PES`, total 7260.00, CAE `70000000000001` y observaciones «Entrega en mostrador»
- **THEN** la respuesta es `200` con esos datos, los ids de cliente, condición, punto de venta y moneda de esa factura, y `estado` igual a `EMITIDA`

#### Scenario: Borrador sin autorización
- **WHEN** se pide el detalle de una factura BORRADOR
- **THEN** `numero`, `cae`, `caeFechaVencimiento` y `fechaAnulacion` son nulos

### Requirement: Cliente opcional en el detalle
Una factura sin cliente MUST devolver `clienteId` nulo y `clienteDenominacion` igual a `Consumidor Final`.

#### Scenario: Factura sin cliente
- **WHEN** se pide el detalle de una factura sin cliente
- **THEN** `clienteId` es nulo y `clienteDenominacion` es `Consumidor Final`

### Requirement: Renglones del detalle
`detalles` MUST contener un elemento por renglón, ordenados por id ascendente, cada uno con `id`, `listaPrecioArticuloId`, `articuloCodigo`, `descripcion`, `cantidad`, `precioUnitario`, `porcentajeBonificacion`, `importeNeto`, `importeIva` e `importeSubtotal`; una cabecera sin renglones MUST devolver un arreglo vacío.

#### Scenario: Dos renglones
- **WHEN** se pide el detalle de una factura con un renglón de 1 «Martillo» (`ART-001`) a 3000.00 y otro de 2 «Cable HDMI» (`ART-003`) a 8000.00
- **THEN** `detalles` tiene dos elementos en ese orden, con el id de la opción de lista de precios, el código de artículo, cantidad, precio e importes de cada renglón

#### Scenario: Cabecera sin renglones
- **WHEN** se pide el detalle de una factura sin renglones
- **THEN** la respuesta es `200` y `detalles` es `[]`

### Requirement: Detalle resuelto sin sesión abierta en la vista
El detalle MUST construirse por completo antes de terminar la transacción de lectura, de modo que funcione con `open-in-view` deshabilitado y la respuesta no dependa de cargas diferidas durante la serialización.

#### Scenario: Asociaciones diferidas con open-in-view deshabilitado
- **WHEN** con `spring.jpa.open-in-view=false` se pide por HTTP el detalle de una factura con cliente y dos renglones, sin transacción abierta por la prueba
- **THEN** la respuesta es `200` con el cliente y los dos renglones, sin error de carga diferida

### Requirement: Factura inexistente
Un id que no corresponde a ninguna factura MUST responder `404 Not Found` con un detalle que mencione el id.

#### Scenario: Id inexistente
- **WHEN** se pide `GET /api/facturas/999999`
- **THEN** la respuesta es `404` y el detalle menciona `999999`

#### Scenario: Id no numérico
- **WHEN** se pide `GET /api/facturas/abc`
- **THEN** la respuesta es `400` y el detalle menciona el parámetro `id`
