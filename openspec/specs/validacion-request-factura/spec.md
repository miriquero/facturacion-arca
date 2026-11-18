# validacion-request-factura Specification

## Purpose
Fija qué cuerpos de alta, edición y cambio de estado acepta la API y cómo se rechazan los inválidos, para que ninguna regla de entrada dependa del navegador y toda referencia a catálogos se resuelva antes de completar la operación.

## Requirements

### Requirement: Cabecera obligatoria del request
El cuerpo de `POST` y `PUT` MUST informar condición de IVA, punto de venta y moneda; el cliente y la fecha de emisión MUST ser opcionales. Un campo obligatorio ausente MUST responder `400` con el nombre del campo en `errores`.

#### Scenario: Falta el punto de venta
- **WHEN** se envía `POST /api/facturas` sin `puntoVentaId`
- **THEN** la respuesta es `400` y `errores` contiene la clave `puntoVentaId`

#### Scenario: Faltan varios campos
- **WHEN** se envía `PUT` a un borrador sin `condicionIvaId` ni `tipoMonedaId`
- **THEN** la respuesta es `400`, `errores` contiene ambas claves y el borrador no cambia

### Requirement: Observaciones de hasta 500 caracteres
Las observaciones de alta, edición y cambio de estado MUST admitir hasta 500 caracteres y rechazar 501 con `400`.

#### Scenario: Límite exacto
- **WHEN** se crea un borrador con observaciones de 500 caracteres
- **THEN** la respuesta es `201` y las observaciones se guardan completas

#### Scenario: Un carácter de más
- **WHEN** se envía `PATCH /api/facturas/{id}/estado` con observaciones de 501 caracteres
- **THEN** la respuesta es `400`, `errores` contiene `observaciones` y la factura no cambia

### Requirement: Al menos un renglón y ninguno nulo
La colección de renglones MUST estar presente, no vacía y sin elementos nulos.

#### Scenario: Sin renglones
- **WHEN** se envía `POST /api/facturas` con `detalles` vacío o ausente
- **THEN** la respuesta es `400` y `errores` contiene `detalles`

#### Scenario: Renglón nulo
- **WHEN** se envía `POST /api/facturas` con `detalles` `[ {renglón válido}, null ]`
- **THEN** la respuesta es `400` y `errores` contiene `detalles[1]`

### Requirement: Descripción de renglón hasta 255 caracteres
La descripción de un renglón MUST admitir hasta 255 caracteres y rechazar 256 con `400`, tanto en `POST` como en `PUT`.

#### Scenario: 255 en el alta
- **WHEN** se crea un borrador con un renglón de descripción de 255 caracteres
- **THEN** la respuesta es `201` y la descripción se guarda completa

#### Scenario: 256 en el alta
- **WHEN** se envía `POST` con un renglón de descripción de 256 caracteres
- **THEN** la respuesta es `400`, `errores` contiene `detalles[0].descripcion` y no se crea ninguna factura

#### Scenario: 255 y 256 en la edición
- **WHEN** se envía `PUT` a un borrador primero con una descripción de 256 caracteres y luego con una de 255
- **THEN** el primero responde `400` sin modificar el borrador y el segundo `200` con la descripción guardada completa

### Requirement: Cantidad positiva
La cantidad de cada renglón MUST ser obligatoria y mayor que cero.

#### Scenario: Cantidad cero, negativa o ausente
- **WHEN** se envía `POST` con un renglón de cantidad `0`, `-1` o sin cantidad
- **THEN** la respuesta es `400` y `errores` contiene `detalles[0].cantidad`

#### Scenario: Cantidad decimal
- **WHEN** se envía `POST` con un renglón de cantidad `0.5`
- **THEN** la respuesta es `201`

### Requirement: Bonificación entre 0 y 100
La bonificación de un renglón MUST ser opcional y, si se informa, estar entre 0 y 100 inclusive.

#### Scenario: Bordes admitidos
- **WHEN** se crean renglones con bonificación `0` y `100`
- **THEN** la respuesta es `201`; el renglón con 100 tiene neto, IVA y subtotal 0.00

#### Scenario: Fuera de rango
- **WHEN** se envía `POST` con bonificación `-0.01` o `100.01`
- **THEN** la respuesta es `400` y `errores` contiene `detalles[0].porcentajeBonificacion`

### Requirement: Referencia de renglón a la lista–artículo
Cada renglón MUST identificar la combinación lista–artículo (no el artículo aislado) y ese identificador MUST ser obligatorio.

#### Scenario: Renglón sin lista–artículo
- **WHEN** se envía `POST` con un renglón sin `listaPrecioArticuloId`
- **THEN** la respuesta es `400` y `errores` contiene `detalles[0].listaPrecioArticuloId`

### Requirement: Referencias inexistentes como 404
Un cliente, condición de IVA, punto de venta, moneda o lista–artículo inexistente MUST responder `404` nombrando el recurso y su id, sin dejar ningún dato guardado ni modificado.

#### Scenario: Lista–artículo inexistente en el segundo renglón
- **WHEN** se envía `POST` con un primer renglón válido y un segundo con `listaPrecioArticuloId` 999999
- **THEN** la respuesta es `404`, el detalle menciona `999999` y la cantidad de facturas y renglones en la base no cambia

#### Scenario: Moneda inexistente en la edición
- **WHEN** se envía `PUT` a un borrador con `tipoMonedaId` 999999
- **THEN** la respuesta es `404` y el borrador conserva su moneda y sus renglones

### Requirement: Cuerpo de cambio de estado
El cuerpo de `PATCH /api/facturas/{id}/estado` MUST traer un `estado` no vacío; las observaciones MUST ser opcionales.

#### Scenario: Estado vacío
- **WHEN** se envía `PATCH` con `estado` `"  "` o sin `estado`
- **THEN** la respuesta es `400` y `errores` contiene `estado`

### Requirement: Estado desconocido en el cambio de estado
Un `estado` que, quitando espacios exteriores y sin distinguir mayúsculas, no es BORRADOR, EMITIDA ni ANULADA MUST responder `400` indicando los valores admitidos (decisión IN-01, pendiente de confirmar con el docente).

#### Scenario: Estado inventado
- **WHEN** se envía `PATCH` con `estado` `PAGADA`
- **THEN** la respuesta es `400`, el detalle menciona `PAGADA` y los estados admitidos, y la factura no cambia

#### Scenario: Estado normalizado
- **WHEN** se envía `PATCH` a un borrador con `estado` `" emitida "`
- **THEN** la factura queda EMITIDA
