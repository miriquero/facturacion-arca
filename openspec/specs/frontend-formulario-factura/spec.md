# frontend-formulario-factura Specification

## Purpose
Define el formulario del frontend para crear y editar borradores de factura con cabecera y renglones: validaciones en el navegador, estimación neta informativa, envío sin precios ni importes, conservación ante errores, bloqueo de doble envío y descarte de cargas de edición obsoletas.

## Requirements

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

### Requirement: Validación de cantidad
La cantidad de cada renglón MUST ser un número finito mayor que cero; otro valor MUST marcar ese campo como inválido con un mensaje asociado e impedir el envío.

#### Scenario: Cantidad cero o no finita
- **WHEN** un renglón tiene cantidad `0`, `-2`, vacía o `1e400`
- **THEN** su campo de cantidad queda inválido y no se envía ninguna petición

#### Scenario: Cantidad decimal
- **WHEN** un renglón tiene cantidad `0.5` y el resto es válido
- **THEN** el request se envía con `cantidad` 0.5

### Requirement: Validación de bonificación
La bonificación de cada renglón MUST ser opcional y, si se informa, un número finito entre 0 y 100 inclusive; fuera de ese rango MUST marcar el campo como inválido e impedir el envío.

#### Scenario: Bordes
- **WHEN** dos renglones tienen bonificación `0` y `100`
- **THEN** ambos son válidos

#### Scenario: Fuera de rango
- **WHEN** un renglón tiene bonificación `100.01` o `-0.01`
- **THEN** su campo de bonificación queda inválido y no se envía ninguna petición

### Requirement: Límites de texto
La descripción de un renglón MUST admitir hasta 255 caracteres y las observaciones hasta 500; un texto más largo MUST marcar el campo como inválido e impedir el envío.

#### Scenario: Descripción en el límite
- **WHEN** un renglón tiene una descripción de 255 caracteres
- **THEN** es válida y se envía completa

#### Scenario: Descripción excedida
- **WHEN** un renglón tiene una descripción de 256 caracteres
- **THEN** su campo queda inválido con un mensaje que menciona 255 y no se envía ninguna petición

#### Scenario: Observaciones excedidas
- **WHEN** las observaciones tienen 501 caracteres
- **THEN** el campo queda inválido con un mensaje que menciona 500 y no se envía ninguna petición

### Requirement: Precio de catálogo y total neto estimado
Cada renglón MUST mostrar el precio de catálogo de la opción elegida y el formulario MUST mostrar un total neto estimado sin IVA (suma de cantidad × precio × (1 − bonificación/100)), identificado como estimación; ninguno de esos valores MUST enviarse en el request.

#### Scenario: Estimación con bonificación
- **WHEN** un renglón tiene elegida la opción de precio 3000.00, cantidad 2 y bonificación 10, y otro la de precio 8000.00 con cantidad 1
- **THEN** los precios mostrados son 3000,00 y 8000,00 y el total neto estimado es 13.400,00, rotulado como estimación sin IVA

#### Scenario: Renglón incompleto
- **WHEN** un renglón no tiene opción elegida o su cantidad es inválida
- **THEN** ese renglón no suma a la estimación y no muestra precio

### Requirement: Alta de borrador
Con el formulario válido en modo alta, guardar SHALL enviar el alta, y ante el éxito MUST navegar al detalle del borrador creado anunciando la creación.

#### Scenario: Alta exitosa
- **WHEN** se guarda un formulario válido y la API responde `201` con la fila del nuevo borrador de id 15
- **THEN** se muestra el detalle de la factura 15 (se pide su detalle) y la región de estado anuncia que se creó el borrador 15

### Requirement: Carga de un borrador para editar
Mostrar la pantalla `#/factura/{id}/editar` MUST pedir el detalle de `{id}` y completar cabecera y renglones con los ids y valores recibidos, mostrando la etiqueta del cliente y de la opción de cada renglón tomadas de los catálogos cargados (o, si no figuran, la denominación del cliente y el código y la descripción del renglón del detalle), indicando qué borrador se edita. Si el detalle recibido no está en BORRADOR, MUST navegar al detalle de esa factura con un error que lo explique; si la carga falla (por ejemplo, `404`), MUST navegar al listado con el error. En ambos casos la navegación MUST reemplazar la entrada del historial, para que Atrás no vuelva a la edición imposible.

#### Scenario: Borrador con dos renglones
- **WHEN** se navega a `#/factura/5/editar` y el detalle del borrador 5 trae cliente 2, moneda 1 y dos renglones
- **THEN** el formulario muestra la etiqueta del cliente 2, moneda 1 y los dos renglones con la etiqueta de su opción, cantidad, bonificación y descripción, y el título indica que se edita el borrador 5

#### Scenario: Ya fue emitido
- **WHEN** se navega a `#/factura/5/editar` y el detalle llega con estado `EMITIDA`
- **THEN** se muestra el detalle de la factura 5 y la región de estado explica que ya no es un borrador y no se puede editar

#### Scenario: Borrador inexistente
- **WHEN** se navega a `#/factura/999/editar` y la API responde `404` con detalle `No existe la factura 999`
- **THEN** se muestra el listado y la región de estado muestra `No existe la factura 999`

### Requirement: Guardado de la edición
En edición, guardar MUST enviar el reemplazo del borrador cargado, y ante el éxito MUST navegar al detalle de ese borrador anunciando la actualización.

#### Scenario: Edición exitosa
- **WHEN** se edita el borrador 5, se cambia la cantidad del primer renglón y se guarda, y la API responde `200`
- **THEN** se envía `PUT /facturas/5`, se muestra el detalle de la factura 5 y la región de estado anuncia la actualización

### Requirement: Cancelación de la edición
El formulario MUST ofrecer «Cancelar» en ambos modos: en edición MUST volver al detalle del borrador y en alta MUST volver al listado, descartando los cambios no guardados y sin enviar ninguna petición de escritura.

#### Scenario: Cancelar sin guardar
- **WHEN** se edita el borrador 5, se cambian valores y se cancela
- **THEN** se muestra el detalle de la factura 5 y no se envió ningún `PUT`

#### Scenario: Cancelar el alta
- **WHEN** en la alta se completan valores y se cancela
- **THEN** se muestra el listado y no se envió ningún `POST`

### Requirement: Cargas de edición obsoletas descartadas
Una respuesta tardía de la carga de un borrador MUST descartarse si, antes de recibirla, se salió de la pantalla de edición o se pidió editar otro borrador.

#### Scenario: Cancelación antes de la respuesta
- **WHEN** se navega a `#/factura/5/editar`, se cancela antes de que llegue su detalle y luego el detalle llega
- **THEN** sigue visible el detalle de la factura 5 y el formulario no queda en modo edición del borrador 5

#### Scenario: Otro borrador elegido después
- **WHEN** se navega a `#/factura/5/editar`, luego a `#/factura/6/editar`, y el detalle del 6 llega antes que el del 5
- **THEN** el formulario queda con los datos del borrador 6 aunque el del 5 llegue después

### Requirement: Valores conservados ante error del guardado
Si el alta o la edición fallan, el formulario MUST conservar todos los valores ingresados y el modo (alta o edición) para permitir corregir y reintentar, y MUST mostrar el mensaje del error.

#### Scenario: Conflicto al editar
- **WHEN** la API responde `409` al guardar la edición del borrador 5
- **THEN** el formulario conserva cabecera y renglones, sigue editando el borrador 5 y la región de estado muestra el detalle del conflicto

### Requirement: Errores por campo del servidor visibles
Los errores por campo de una respuesta `400` MUST mostrarse junto al campo correspondiente cuando la ruta lo identifica (incluidos los renglones por índice) y en la región de estado cuando no; ninguno MUST ocultarse.

#### Scenario: Error en un renglón
- **WHEN** la API responde `400` con `errores` `{"detalles[1].descripcion":"admite hasta 255 caracteres"}`
- **THEN** el campo de descripción del segundo renglón queda inválido con ese mensaje

#### Scenario: Ruta sin campo visible
- **WHEN** `errores` trae una clave que no corresponde a ningún campo del formulario
- **THEN** su mensaje aparece en la región de estado junto con el detalle del error

### Requirement: Bloqueo durante el guardado
Mientras el alta o la edición están en curso, el formulario MUST deshabilitar el guardado, la cancelación y la edición de campos y renglones, y MUST liberarlos al terminar tanto con éxito como con error; un segundo intento de guardado en curso MUST NOT enviar otra petición.

#### Scenario: Doble envío
- **WHEN** se guarda y, antes de la respuesta, se vuelve a activar el guardado
- **THEN** se envió una sola petición de alta

#### Scenario: Liberación tras error
- **WHEN** el guardado falla con `400`
- **THEN** los controles vuelven a estar habilitados y el guardado puede reintentarse

### Requirement: Pantalla propia del formulario
El formulario de borrador MUST mostrarse en su propia pantalla: `#/nueva` MUST mostrarlo vacío en modo alta (un renglón vacío, cliente `Consumidor Final`, título `Nuevo borrador`) cada vez que se entra, y `#/factura/{id}/editar` MUST mostrarlo en modo edición del borrador `{id}`. Un guardado que termina cuando el usuario ya salió de la pantalla MUST anunciar su resultado en la región de estado sin navegar.

#### Scenario: Entrar a la alta
- **WHEN** se navega a `#/nueva` después de haber escrito valores en una visita anterior a la alta
- **THEN** el formulario está vacío, en modo alta, con un renglón vacío y el título `Nuevo borrador`

#### Scenario: Guardado que termina fuera de la pantalla
- **WHEN** se guarda un borrador nuevo, se navega al listado antes de la respuesta y la API responde con el id 15
- **THEN** el listado sigue visible y la región de estado anuncia que se creó el borrador 15
