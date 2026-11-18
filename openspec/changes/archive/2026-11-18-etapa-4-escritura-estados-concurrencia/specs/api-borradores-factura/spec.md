# Spec Delta

## Purpose

Define la creación, el reemplazo y la eliminación por HTTP de facturas en estado BORRADOR: qué se envía, qué se responde y qué queda persistido en el agregado factura–renglones.

## ADDED Requirements

### Requirement: Alta de borrador
`POST /api/facturas` con un cuerpo válido SHALL crear una factura en estado BORRADOR, sin número, sin CAE, sin vencimiento ni resultado de autorización, con cobrado 0 y saldo igual al total, y MUST responder `201 Created` con un encabezado `Location` que termina en `/api/facturas/{id}` y un cuerpo con la fila de reporte de la factura creada.

#### Scenario: Borrador con cliente y dos renglones
- **WHEN** se envía `POST /api/facturas` con cliente, condición de IVA, punto de venta, moneda y dos renglones válidos
- **THEN** la respuesta es `201`, `Location` termina en `/api/facturas/{id}` con el id generado, el cuerpo tiene ese `id`, `estado` `BORRADOR`, `numeroFactura` nulo, `cantidadItems` 2 y el `importeTotal` calculado, y `GET` de esa `Location` devuelve la factura con sus dos renglones

#### Scenario: Respuesta igual a la fila del listado
- **WHEN** se crea un borrador y luego se pide `GET /api/facturas`
- **THEN** la fila del listado con ese `id` tiene los mismos diez campos que el cuerpo de la respuesta del alta

### Requirement: Cliente opcional en el alta
Un alta sin cliente MUST crear la factura sin cliente y presentarla como «Consumidor Final».

#### Scenario: Alta sin cliente
- **WHEN** se envía `POST /api/facturas` sin `clienteId`
- **THEN** la respuesta es `201` con `clienteDenominacion` `Consumidor Final` y la factura guardada no tiene cliente

### Requirement: Fecha de emisión por defecto
Si el request no informa la fecha de emisión, la factura MUST tomar la fecha actual del reloj de la aplicación; si la informa, MUST conservarla.

#### Scenario: Fecha omitida
- **WHEN** con el reloj fijado en 2026-11-17 se crea un borrador sin `fechaEmision`
- **THEN** la factura tiene fecha de emisión 2026-11-17

#### Scenario: Fecha informada
- **WHEN** se crea un borrador con `fechaEmision` 2026-11-01
- **THEN** la factura tiene fecha de emisión 2026-11-01

### Requirement: Precio y descripción tomados del catálogo
Cada renglón MUST tomar el precio unitario de la lista–artículo referenciada, sin aceptar precios ni importes del request, y si la descripción falta o está en blanco MUST usar la denominación del artículo. La bonificación omitida MUST guardarse como 0.

#### Scenario: Precio de la lista
- **WHEN** se crea un borrador con un renglón de la lista–artículo del martillo (precio 3000.00) y el cuerpo incluye además un campo `precioUnitario` 1
- **THEN** el renglón guardado tiene precio unitario 3000.00 y el campo extra no influye en ningún importe

#### Scenario: Descripción por defecto
- **WHEN** se crea un renglón sin `descripcion` y otro con `descripcion` en blanco, ambos del artículo `Martillo`
- **THEN** los dos renglones tienen descripción `Martillo` y bonificación 0

### Requirement: Reemplazo de un borrador
`PUT /api/facturas/{id}` sobre un BORRADOR con un cuerpo válido MUST reemplazar todos los datos de cabecera y todos los renglones por los del request, recalcular los importes y responder `200 OK` con la fila de reporte actualizada; los renglones anteriores MUST dejar de existir.

#### Scenario: Reemplazo de dos renglones por uno
- **WHEN** un borrador con dos renglones recibe `PUT` con otra moneda, sin cliente y un único renglón distinto
- **THEN** la respuesta es `200`, la factura releída tiene la moneda nueva, ningún cliente, exactamente el renglón nuevo, el total recalculado, y los dos renglones anteriores ya no existen en la base

#### Scenario: Reenvío de los mismos datos
- **WHEN** se envía `PUT` con exactamente el mismo contenido que el borrador ya tiene
- **THEN** la respuesta es `200` y los importes no cambian

### Requirement: Edición solo en BORRADOR
`PUT` sobre una factura EMITIDA o ANULADA MUST responder `409 Conflict` sin modificar nada, y sobre un id inexistente MUST responder `404`.

#### Scenario: Editar una emitida
- **WHEN** se envía un `PUT` válido a una factura EMITIDA
- **THEN** la respuesta es `409` y la factura conserva su cabecera, renglones, número y CAE

#### Scenario: Editar una inexistente
- **WHEN** se envía un `PUT` válido a `/api/facturas/999999`
- **THEN** la respuesta es `404`

### Requirement: Eliminación física de un borrador
`DELETE /api/facturas/{id}` sobre un BORRADOR MUST borrar la factura y sus renglones y responder `204 No Content` sin cuerpo; el cliente, sus datos dependientes y los catálogos MUST permanecer.

#### Scenario: Borrar un borrador con cliente
- **WHEN** se envía `DELETE` a un borrador con cliente y dos renglones
- **THEN** la respuesta es `204` con cuerpo vacío, `GET` de esa factura devuelve `404`, sus renglones no existen y el cliente, el punto de venta, la moneda, la condición de IVA y las listas–artículo siguen existiendo

### Requirement: Eliminación solo en BORRADOR
`DELETE` sobre una factura EMITIDA o ANULADA MUST responder `409` sin borrar nada, y sobre un id inexistente MUST responder `404`.

#### Scenario: Borrar una anulada
- **WHEN** se envía `DELETE` a una factura ANULADA
- **THEN** la respuesta es `409` y la factura sigue existiendo

#### Scenario: Borrar una inexistente
- **WHEN** se envía `DELETE /api/facturas/999999`
- **THEN** la respuesta es `404`
