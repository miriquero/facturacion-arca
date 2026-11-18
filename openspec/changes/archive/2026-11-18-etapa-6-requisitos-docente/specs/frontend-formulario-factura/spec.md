## MODIFIED Requirements

### Requirement: Cabecera del borrador
El formulario SHALL pedir cliente (opcional, `Consumidor Final` por defecto, elegido con el explorador de clientes y con un botón para volver a `Consumidor Final`), condición de IVA, punto de venta y moneda (obligatorios, elegidos de los catálogos), fecha de emisión opcional y observaciones opcionales. El cliente elegido MUST mostrarse con la etiqueta de su opción.

#### Scenario: Obligatorios sin elegir
- **WHEN** se intenta guardar sin punto de venta ni moneda
- **THEN** ambos campos quedan marcados como inválidos con su mensaje y no se envía ninguna petición

#### Scenario: Consumidor Final
- **WHEN** se guarda sin elegir cliente
- **THEN** el request no incluye `clienteId`

#### Scenario: Cliente elegido con el explorador
- **WHEN** se abre el explorador de clientes del formulario, se elige `Juan Servicios (20-12345678-9)` de id 2 y se guarda un formulario válido
- **THEN** el formulario muestra `Juan Servicios (20-12345678-9)` como cliente y el request incluye `clienteId` 2

#### Scenario: Volver a Consumidor Final
- **WHEN** con el cliente 2 elegido se activa el botón para quitarlo y se guarda
- **THEN** el formulario muestra `Consumidor Final` y el request no incluye `clienteId`

### Requirement: Renglones que se agregan y quitan
El formulario SHALL permitir agregar renglones y quitar cualquiera de ellos; cada renglón MUST pedir la opción lista–artículo (elegida con el explorador de artículos por lista de precios de ese renglón), la cantidad, la bonificación opcional y la descripción opcional, y el guardado MUST exigir al menos un renglón y una opción elegida en cada renglón.

#### Scenario: Agregar y quitar
- **WHEN** se agregan tres renglones y se quita el segundo
- **THEN** quedan dos renglones con los valores del primero y del tercero, en ese orden

#### Scenario: Sin renglones
- **WHEN** se intenta guardar sin renglones
- **THEN** se muestra un error que pide al menos un renglón y no se envía ninguna petición

#### Scenario: Opción elegida con el explorador
- **WHEN** en el renglón 1 se abre el explorador de artículos y se elige `ART-001 - Martillo (GENERAL)` de id 7
- **THEN** el renglón 1 muestra esa etiqueta y el request envía `listaPrecioArticuloId` 7 para ese renglón

#### Scenario: Renglón sin opción
- **WHEN** se intenta guardar con un renglón sin opción elegida
- **THEN** el control de elección de ese renglón queda marcado como inválido con su mensaje y no se envía ninguna petición

### Requirement: Precio de catálogo y total neto estimado
Cada renglón MUST mostrar el precio de catálogo de la opción elegida y el formulario MUST mostrar un total neto estimado sin IVA (suma de cantidad × precio × (1 − bonificación/100)), identificado como estimación; ninguno de esos valores MUST enviarse en el request.

#### Scenario: Estimación con bonificación
- **WHEN** un renglón tiene elegida la opción de precio 3000.00, cantidad 2 y bonificación 10, y otro la de precio 8000.00 con cantidad 1
- **THEN** los precios mostrados son 3000,00 y 8000,00 y el total neto estimado es 13.400,00, rotulado como estimación sin IVA

#### Scenario: Renglón incompleto
- **WHEN** un renglón no tiene opción elegida o su cantidad es inválida
- **THEN** ese renglón no suma a la estimación y no muestra precio

### Requirement: Carga de un borrador para editar
Elegir editar un BORRADOR MUST pedir su detalle y completar cabecera y renglones con los ids y valores recibidos, mostrando la etiqueta del cliente y de la opción de cada renglón tomadas de los catálogos cargados (o, si no figuran, la denominación del cliente y el código y la descripción del renglón del detalle), indicando qué borrador se edita; si el detalle recibido no está en BORRADOR, MUST mostrar un error y no entrar en edición.

#### Scenario: Borrador con dos renglones
- **WHEN** se edita el borrador 5, cuyo detalle trae cliente 2, moneda 1 y dos renglones
- **THEN** el formulario muestra la etiqueta del cliente 2, moneda 1 y los dos renglones con la etiqueta de su opción, cantidad, bonificación y descripción, y el título indica que se edita el borrador 5

#### Scenario: Ya fue emitido
- **WHEN** se pide editar una fila que figuraba como BORRADOR pero el detalle llega con estado `EMITIDA`
- **THEN** la región de estado muestra un error y el formulario no entra en edición
