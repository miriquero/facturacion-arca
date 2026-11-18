## ADDED Requirements

### Requirement: Página A4 vertical
Todas las páginas del comprobante MUST tener tamaño A4 en orientación vertical.

#### Scenario: Tamaño de página
- **WHEN** se genera el comprobante de una factura EMITIDA con dos renglones
- **THEN** cada página mide 595 × 842 puntos

### Requirement: Identificación del comprobante
El comprobante MUST mostrar el título `Factura`, el número formado por el punto de venta con cuatro dígitos, un guion y el número con ocho dígitos, la fecha de emisión en formato `dd/MM/yyyy`, la descripción del punto de venta y la fecha y hora de generación en la zona horaria `America/Argentina/Buenos_Aires`.

#### Scenario: Número formateado
- **WHEN** se genera el comprobante de la factura número 4 del punto de venta 1 «Casa Central», emitida el 2026-11-05, en el instante 2026-11-18T18:30:00Z
- **THEN** el texto extraído contiene `Factura`, `0001-00000004`, `05/11/2026`, `Casa Central` y `18/11/2026 15:30`

### Requirement: Datos del cliente
El comprobante MUST mostrar la denominación del cliente, su CUIT/CUIL, la condición frente al IVA de la factura y el código de moneda; una factura sin cliente MUST mostrar `Consumidor Final` y MUST NOT mostrar un CUIT.

#### Scenario: Cliente con CUIT
- **WHEN** se genera el comprobante de una factura del cliente «Juan Servicios» (`20-12345678-9`), condición «Responsable Inscripto», moneda `PES`
- **THEN** el texto contiene `Juan Servicios`, `20-12345678-9`, `Responsable Inscripto` y `PES`

#### Scenario: Consumidor Final
- **WHEN** se genera el comprobante de una factura sin cliente
- **THEN** el texto contiene `Consumidor Final` y no contiene la etiqueta de CUIT

### Requirement: Renglones del comprobante
El comprobante MUST mostrar una tabla con una fila por renglón, en el orden del detalle, con código de artículo, descripción, cantidad, precio unitario, bonificación, importe neto, importe de IVA y subtotal; los encabezados MUST repetirse si la tabla ocupa más de una página.

#### Scenario: Dos renglones
- **WHEN** se genera el comprobante de una factura con un renglón de 1 «Martillo» (`ART-001`) a 3000.00 y otro de 2 «Cable HDMI» (`ART-003`) a 8000.00
- **THEN** el texto contiene los ocho encabezados y, en ese orden, `ART-001`, `Martillo`, `3.000,00` y `ART-003`, `Cable HDMI`, `8.000,00`

#### Scenario: Muchos renglones
- **WHEN** se genera el comprobante de una factura con 60 renglones
- **THEN** el documento tiene más de una página y cada página contiene los ocho encabezados de la tabla

### Requirement: Importes sin aritmética de punto flotante
Todos los importes MUST tomarse de los valores guardados de la factura y sus renglones, convertirse a decimal exacto sin pasar por operaciones de punto flotante y mostrarse redondeados a dos decimales con redondeo hacia arriba en la mitad, en formato argentino (punto de miles y coma decimal). El comprobante MUST mostrar el total neto y el total de IVA como suma de los renglones y el importe total guardado de la factura.

#### Scenario: Totales
- **WHEN** se genera el comprobante de una factura con renglones de neto 3000.00 e IVA 630.00 y de neto 16000.00 e IVA 3360.00, e importe total 22990.00
- **THEN** el texto contiene `19.000,00` como total neto, `3.990,00` como total de IVA y `22.990,00` como total

#### Scenario: Redondeo en la mitad
- **WHEN** un renglón tiene un importe guardado de 0.125
- **THEN** se muestra `0,13`

### Requirement: Autorización simulada visible
El comprobante MUST mostrar el CAE, la fecha de vencimiento del CAE en formato `dd/MM/yyyy` y el resultado de la autorización.

#### Scenario: CAE y vencimiento
- **WHEN** se genera el comprobante de una factura con CAE `70000000000001`, vencimiento 2026-11-15 y resultado `A`
- **THEN** el texto contiene `CAE`, `70000000000001`, `15/11/2026` y `A`

### Requirement: Anulación marcada
El comprobante de una factura ANULADA MUST mostrar de forma destacada el texto `ANULADA` y la fecha de anulación, conservando el número, el CAE y su vencimiento; el de una factura EMITIDA MUST NOT contener `ANULADA`.

#### Scenario: Factura anulada
- **WHEN** se genera el comprobante de una factura ANULADA el 2026-11-12 con CAE `70000000000002`
- **THEN** el texto contiene `ANULADA`, `12/11/2026` y `70000000000002`

#### Scenario: Factura emitida
- **WHEN** se genera el comprobante de una factura EMITIDA
- **THEN** el texto contiene `EMITIDA` y no contiene `ANULADA`

### Requirement: Leyenda de comprobante simulado
Cada comprobante MUST incluir una leyenda que indique que es un comprobante simulado con fines académicos, sin validez fiscal, y que el CAE es ficticio.

#### Scenario: Leyenda presente
- **WHEN** se genera cualquier comprobante
- **THEN** el texto contiene `sin validez fiscal` y `CAE ficticio`

### Requirement: Observaciones opcionales
Si la factura tiene observaciones, el comprobante MUST mostrarlas; si no las tiene, MUST NOT mostrar la sección.

#### Scenario: Con observaciones
- **WHEN** la factura tiene observaciones «Entrega en mostrador»
- **THEN** el texto contiene `Entrega en mostrador`

### Requirement: Fuente incrustada y rechazo de glifos
El comprobante MUST usar la misma fuente incrustada con codificación `Identity-H` que el reporte, el texto MUST poder extraerse idéntico al original, y si un dato contiene un carácter sin glifo la generación MUST fallar identificando el carácter por su código Unicode y el dato que lo contiene, sin producir un documento con caracteres sustituidos u omitidos.

#### Scenario: Caracteres especiales conservados
- **WHEN** se genera el comprobante con el cliente «Ñandú & Cía. — 测试» y un renglón con descripción «Cable «HDMI» 2 m»
- **THEN** el texto extraído contiene exactamente esos textos

#### Scenario: Carácter sin glifo
- **WHEN** se genera el comprobante con una descripción de renglón que contiene U+E000
- **THEN** la generación falla con un error que menciona `U+E000` y la descripción, y no se devuelve contenido
