## MODIFIED Requirements

### Requirement: Detalle de factura por id
`GET /api/facturas/{id}` SHALL responder `200 OK` con la cabecera de la factura: `id`, `numero`, `fechaEmision`, `estado` (texto), `clienteId`, `clienteDenominacion`, `clienteCuitCuil`, `condicionIvaId`, `condicionIva`, `puntoVentaId`, `puntoVentaNumero`, `puntoVentaDescripcion`, `tipoMonedaId`, `codigoMoneda`, `importeTotal`, `importeCobrado`, `importeSaldo`, `cae`, `caeFechaVencimiento`, `resultadoAfip`, `fechaAnulacion`, `observaciones` y `detalles`.

#### Scenario: Factura emitida con cliente
- **WHEN** se pide el detalle de una factura EMITIDA número 1 del punto de venta 1 «Casa Central», cliente «Juan Servicios» con CUIT `20-12345678-9`, condición «Responsable Inscripto», moneda `PES`, total 7260.00, CAE `70000000000001` y observaciones «Entrega en mostrador»
- **THEN** la respuesta es `200` con esos datos, `clienteCuitCuil` igual a `20-12345678-9`, los ids de cliente, condición, punto de venta y moneda de esa factura, y `estado` igual a `EMITIDA`

#### Scenario: Borrador sin autorización
- **WHEN** se pide el detalle de una factura BORRADOR
- **THEN** `numero`, `cae`, `caeFechaVencimiento` y `fechaAnulacion` son nulos

### Requirement: Cliente opcional en el detalle
Una factura sin cliente MUST devolver `clienteId` nulo, `clienteCuitCuil` nulo y `clienteDenominacion` igual a `Consumidor Final`.

#### Scenario: Factura sin cliente
- **WHEN** se pide el detalle de una factura sin cliente
- **THEN** `clienteId` y `clienteCuitCuil` son nulos y `clienteDenominacion` es `Consumidor Final`
