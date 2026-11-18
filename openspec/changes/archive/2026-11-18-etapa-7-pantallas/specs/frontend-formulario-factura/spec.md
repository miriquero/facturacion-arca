## ADDED Requirements

### Requirement: Pantalla propia del formulario
El formulario de borrador MUST mostrarse en su propia pantalla: `#/nueva` MUST mostrarlo vacío en modo alta (un renglón vacío, cliente `Consumidor Final`, título `Nuevo borrador`) cada vez que se entra, y `#/factura/{id}/editar` MUST mostrarlo en modo edición del borrador `{id}`. Un guardado que termina cuando el usuario ya salió de la pantalla MUST anunciar su resultado en la región de estado sin navegar.

#### Scenario: Entrar a la alta
- **WHEN** se navega a `#/nueva` después de haber escrito valores en una visita anterior a la alta
- **THEN** el formulario está vacío, en modo alta, con un renglón vacío y el título `Nuevo borrador`

#### Scenario: Guardado que termina fuera de la pantalla
- **WHEN** se guarda un borrador nuevo, se navega al listado antes de la respuesta y la API responde con el id 15
- **THEN** el listado sigue visible y la región de estado anuncia que se creó el borrador 15

## MODIFIED Requirements

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

### Requirement: Bloqueo durante el guardado
Mientras el alta o la edición están en curso, el formulario MUST deshabilitar el guardado, la cancelación y la edición de campos y renglones, y MUST liberarlos al terminar tanto con éxito como con error; un segundo intento de guardado en curso MUST NOT enviar otra petición.

#### Scenario: Doble envío
- **WHEN** se guarda y, antes de la respuesta, se vuelve a activar el guardado
- **THEN** se envió una sola petición de alta

#### Scenario: Liberación tras error
- **WHEN** el guardado falla con `400`
- **THEN** los controles vuelven a estar habilitados y el guardado puede reintentarse
