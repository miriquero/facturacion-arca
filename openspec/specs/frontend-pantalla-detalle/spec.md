# frontend-pantalla-detalle Specification

## Purpose
Define el contenido de la pantalla de detalle de una factura del frontend: cabecera, cliente, renglones, importes, CAE y vencimiento, datos de anulación, el manejo de una factura inexistente y la presentación segura del texto del servidor.

## Requirements

### Requirement: Carga del detalle por id
Al mostrarse la pantalla de detalle de la factura `{id}`, la interfaz MUST pedir `GET /facturas/{id}` una vez y presentar la respuesta; mientras la respuesta no llega, la sección del detalle MUST tener `aria-busy="true"` y MUST volver a `false` al terminar con éxito o con error. El `h1` MUST mostrar `Factura {id}` hasta tener los datos.

#### Scenario: Carga exitosa
- **WHEN** se navega a `#/factura/1` y la API responde el detalle de la factura 1
- **THEN** se pidió una sola vez el detalle con el id 1, la sección deja de estar ocupada y muestra los datos recibidos

#### Scenario: Durante la carga
- **WHEN** se navega a `#/factura/1` y la respuesta todavía no llegó
- **THEN** la sección del detalle tiene `aria-busy="true"` y su `h1` dice `Factura 1`

### Requirement: Cabecera de la factura
El detalle MUST mostrar el número de comprobante con el punto de venta en cuatro dígitos y el número en ocho separados por guion, la fecha de emisión como `dd/mm/aaaa` sin pasar por `new Date` y el estado como texto. Un BORRADOR sin número MUST identificarse como borrador con su id. El `h1` MUST nombrar el comprobante.

#### Scenario: Factura emitida
- **WHEN** el detalle trae `puntoVentaNumero` 1, `numero` 4, `fechaEmision` `2026-11-01` y estado `EMITIDA`
- **THEN** el `h1` dice `Factura 0001-00000004`, la fecha se muestra `01/11/2026` y el estado `EMITIDA`

#### Scenario: Borrador sin número
- **WHEN** el detalle trae el BORRADOR de id 6 con `numero` nulo
- **THEN** el `h1` dice `Borrador 6` y el número se muestra como `—`

### Requirement: Cliente y datos fiscales
El detalle MUST mostrar la denominación del cliente con su CUIT/CUIL, o `Consumidor Final` sin CUIT cuando la factura no tiene cliente, y además la condición frente al IVA, el punto de venta (descripción) y la moneda (código).

#### Scenario: Cliente con CUIT
- **WHEN** el detalle trae `clienteDenominacion` `Ana SA` y `clienteCuitCuil` `30-71234567-1`
- **THEN** se muestran `Ana SA` y `30-71234567-1`

#### Scenario: Consumidor Final
- **WHEN** el detalle trae `clienteId` nulo y `clienteCuitCuil` nulo
- **THEN** se muestra `Consumidor Final` y ningún CUIT

### Requirement: Tabla de renglones
El detalle MUST mostrar una tabla con `caption` y encabezados `th scope="col"`, con una fila por renglón en el orden recibido y las columnas código, descripción, cantidad, precio unitario, bonificación, neto, IVA y subtotal. Los importes MUST formatearse con la moneda de la factura (`PES` → pesos argentinos, `DOL` → dólares, sin conversión); una descripción nula MUST mostrarse como `—`.

#### Scenario: Dos renglones en pesos
- **WHEN** el detalle en `PES` trae los renglones `MAR` (cantidad 2, precio 3000, bonificación 10, neto 5400) y `HDMI` con descripción nula
- **THEN** la tabla tiene dos filas en ese orden, la primera muestra `MAR`, `2`, `$ 3.000,00`, `10` y `$ 5.400,00`, y la segunda muestra `—` como descripción

#### Scenario: Factura en dólares
- **WHEN** el detalle trae `codigoMoneda` `DOL` e `importeTotal` 100
- **THEN** el total se muestra en dólares con el mismo valor, sin conversión

### Requirement: Importes, CAE y anulación
El detalle MUST mostrar el total, lo cobrado y el saldo con la moneda de la factura; el CAE, la fecha de vencimiento del CAE (`dd/mm/aaaa`), el resultado de ARCA, las observaciones y la fecha de anulación. Un dato nulo MUST mostrarse como `—`, nunca como `null` ni vacío.

#### Scenario: Factura emitida con CAE
- **WHEN** el detalle trae `cae` `71234567890123`, `caeFechaVencimiento` `2026-11-11` y `resultadoAfip` `A`
- **THEN** se muestran el CAE `71234567890123`, el vencimiento `11/11/2026` y el resultado `A`

#### Scenario: Borrador sin CAE
- **WHEN** el detalle de un BORRADOR trae `cae`, `caeFechaVencimiento`, `resultadoAfip` y `fechaAnulacion` nulos
- **THEN** cada uno se muestra como `—`

#### Scenario: Factura anulada
- **WHEN** el detalle trae estado `ANULADA`, `fechaAnulacion` `2026-11-05` y `observaciones` `Error en el cliente`
- **THEN** se muestran la fecha de anulación `05/11/2026` y las observaciones `Error en el cliente`

### Requirement: Texto del servidor sin interpretar en el detalle
Todo dato del servidor mostrado en el detalle MUST insertarse como texto, nunca interpretado como HTML.

#### Scenario: Denominación que parece HTML
- **WHEN** el detalle trae `clienteDenominacion` `<img src=x onerror=alert(1)>`
- **THEN** ese texto se muestra literal y la sección del detalle no contiene ningún `img`

### Requirement: Factura inexistente o error de carga
Si la carga del detalle falla, la región de estado MUST mostrar el mensaje de la API (por ejemplo, el `detail` de un `404`) hasta la siguiente acción, y la pantalla MUST seguir mostrando su `h1` y el enlace «Volver al listado», sin acciones sobre la factura.

#### Scenario: Factura 999 inexistente
- **WHEN** se navega a `#/factura/999` y la API responde `404` con detalle `No existe la factura 999`
- **THEN** la región de estado muestra `No existe la factura 999`, el `h1` dice `Factura 999`, hay un enlace «Volver al listado» y no hay botones de acción

#### Scenario: Falla de conexión
- **WHEN** la carga del detalle falla por no poder conectar con la API
- **THEN** la región de estado muestra el mensaje de conexión y el enlace «Volver al listado» sigue disponible
