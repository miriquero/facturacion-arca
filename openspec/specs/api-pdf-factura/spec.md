# api-pdf-factura Specification

## Purpose
TBD - created by archiving change etapa-6-requisitos-docente. Update Purpose after archive.

## Requirements

### Requirement: Descarga del PDF de una factura informada
`GET /api/facturas/{id}/pdf` SHALL responder `200 OK` para una factura EMITIDA o ANULADA con el PDF de ese comprobante, `Content-Type: application/pdf` y `Content-Disposition` de tipo `attachment` con el nombre `factura-PPPP-NNNNNNNN.pdf` (punto de venta con cuatro dígitos y número con ocho).

#### Scenario: Factura emitida
- **WHEN** se pide `GET /api/facturas/{id}/pdf` de la factura EMITIDA número 4 del punto de venta 1
- **THEN** la respuesta es `200`, `Content-Type` es `application/pdf`, `Content-Disposition` es `attachment` con `filename="factura-0001-00000004.pdf"` y el cuerpo empieza con `%PDF`

#### Scenario: Factura anulada
- **WHEN** se pide el PDF de una factura ANULADA
- **THEN** la respuesta es `200` con el PDF del comprobante marcado como anulado

### Requirement: Borrador no imprimible
El PDF de una factura en BORRADOR MUST responder `409 Conflict` con un detalle que indique que la factura no fue informada a ARCA y que primero debe emitirse, sin generar archivo.

#### Scenario: Pedido sobre un borrador
- **WHEN** se pide `GET /api/facturas/{id}/pdf` de un BORRADOR
- **THEN** la respuesta es `409` con el formato de error uniforme, sin `Content-Disposition`, y el detalle menciona BORRADOR y que no está informada a ARCA

### Requirement: Factura inexistente o id inválido
Un id que no corresponde a ninguna factura MUST responder `404 Not Found` con un detalle que mencione el id, y un id no numérico MUST responder `400` nombrando el parámetro `id`.

#### Scenario: Id inexistente
- **WHEN** se pide `GET /api/facturas/999999/pdf`
- **THEN** la respuesta es `404` y el detalle menciona `999999`

#### Scenario: Id no numérico
- **WHEN** se pide `GET /api/facturas/abc/pdf`
- **THEN** la respuesta es `400` y el detalle menciona `id`

### Requirement: Glifo no soportado en el comprobante
Si el comprobante no puede representar un carácter de los datos de la factura, la respuesta MUST ser `400 Bad Request` con un detalle que identifique el carácter por su código Unicode y el dato que lo contiene, sin devolver un PDF parcial.

#### Scenario: Cliente con carácter de uso privado
- **WHEN** se pide el PDF de una factura EMITIDA cuyo cliente contiene U+E000
- **THEN** la respuesta es `400`, el detalle menciona `U+E000` y no hay `Content-Disposition`

### Requirement: Lectura completa sin sesión en la vista
El comprobante MUST armarse con todos sus datos (cliente, catálogos y renglones) dentro de una transacción de solo lectura, de modo que funcione con `open-in-view` deshabilitado y no modifique la factura.

#### Scenario: Asociaciones diferidas
- **WHEN** con `spring.jpa.open-in-view=false` se pide por HTTP el PDF de una factura EMITIDA con cliente y dos renglones, sin transacción abierta por la prueba
- **THEN** la respuesta es `200` y la factura releída conserva su estado, número, CAE e importes

### Requirement: Descarga accesible desde el frontend
La ruta del PDF MUST admitir el origen del frontend local y exponer `Content-Disposition` como el resto de las descargas.

#### Scenario: Origen del frontend
- **WHEN** se pide el PDF de una factura EMITIDA con `Origin: http://localhost:5173`
- **THEN** la respuesta es `200` y `Access-Control-Expose-Headers` incluye `Content-Disposition`
