# calculo-importes-factura Specification

## Purpose
Define cómo el servidor calcula neto, IVA, subtotal, total y saldo de una factura con aritmética decimal exacta, en un orden fijo, y cómo rechaza un importe que no puede guardarse sin pérdida en los campos de punto flotante del modelo.

## Requirements

### Requirement: Neto sin redondear
El neto de un renglón antes de redondear MUST ser cantidad × precio unitario × (1 − bonificación / 100), calculado con aritmética decimal exacta.

#### Scenario: Con bonificación
- **WHEN** un renglón tiene cantidad 2, precio 3000.00, bonificación 10 y alícuota 21
- **THEN** su neto es 5400.00, su IVA 1134.00 y su subtotal 6534.00

### Requirement: IVA sobre el neto sin redondear
El IVA MUST calcularse aplicando la alícuota sobre el neto **antes** de redondearlo, y redondearse a 2 decimales con redondeo half-up.

#### Scenario: El orden cambia los centavos
- **WHEN** un renglón tiene cantidad 5, precio 20.023, sin bonificación y alícuota 21 (neto sin redondear 100.115)
- **THEN** el IVA es 21.02 (no 21.03, que resultaría de aplicar la alícuota sobre el neto ya redondeado), el neto 100.12 y el subtotal 121.14

### Requirement: Neto redondeado por separado
El neto MUST redondearse por separado a 2 decimales con redondeo half-up, y el subtotal MUST ser la suma del neto y el IVA redondeados.

#### Scenario: Medio centavo hacia arriba
- **WHEN** un renglón tiene cantidad 3, precio 33.335, sin bonificación y alícuota 21 (neto sin redondear 100.005)
- **THEN** el neto es 100.01 (no 100.00, que daría el redondeo al par), el IVA 21.00 y el subtotal 121.01

### Requirement: Alícuota del artículo o por defecto
La alícuota MUST ser la del artículo de la lista–artículo; si el artículo no tiene alícuota MUST usarse el valor configurado por defecto, 21.

#### Scenario: Alícuota propia
- **WHEN** un renglón de cantidad 1 y precio 8000.00 es de un artículo con alícuota 10.5
- **THEN** su IVA es 840.00 y su subtotal 8840.00

#### Scenario: Alícuota nula
- **WHEN** un renglón de cantidad 2 y precio 3000.00 es de un artículo sin alícuota
- **THEN** su IVA es 1260.00 y su subtotal 7260.00

### Requirement: Total y saldo de la cabecera
El total MUST ser la suma decimal de los subtotales de los renglones y el saldo MUST ser total − cobrado, con cobrado 0 porque no se registran pagos.

#### Scenario: Dos renglones
- **WHEN** una factura tiene un renglón de 1 × 3000.00 y otro de 2 × 8000.00, ambos con alícuota 21
- **THEN** los subtotales son 3630.00 y 19360.00, el total 22990.00, el cobrado 0 y el saldo 22990.00

### Requirement: Importes representables antes de guardar
Antes de persistir cada valor calculado o recibido que se guarda en un campo de punto flotante (cantidad, bonificación, precio, neto, IVA, subtotal, total y saldo), la conversión MUST ser finita y su reconversión decimal MUST ser numéricamente igual al valor decimal; si no lo es, la operación MUST responder `409 Conflict` sin persistir nada. El redondeo de enteros de punto flotante MUST NOT sustituir este control.

#### Scenario: Importe con más dígitos de los que caben
- **WHEN** se crea un borrador con un renglón de cantidad 1 de una lista–artículo con precio 1234567890123456.8 y alícuota 21 (IVA 259259256925925.93)
- **THEN** la respuesta es `409`, el detalle indica que un importe no puede representarse sin pérdida, y la cantidad de facturas y renglones en la base no cambia

#### Scenario: Importe representable
- **WHEN** el valor decimal calculado es 7260.00
- **THEN** se guarda 7260.0 y su reconversión decimal es igual a 7260.00

#### Scenario: Valor no finito
- **WHEN** la conversión de un valor decimal desbordado produce un infinito
- **THEN** se rechaza con un conflicto de negocio y no se guarda

#### Scenario: Edición con importe no representable
- **WHEN** un borrador guardado recibe un `PUT` cuyo único renglón produce un importe no representable
- **THEN** la respuesta es `409` y el borrador releído conserva sus renglones e importes anteriores
