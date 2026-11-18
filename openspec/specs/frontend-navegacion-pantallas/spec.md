# frontend-navegacion-pantallas Specification

## Purpose
Define cómo el frontend se divide en tres pantallas (listado, alta y edición, detalle) con direcciones propias, historial del navegador, foco accesible al cambiar de pantalla y mensajes de estado que sobreviven a la navegación, sin framework ni bundler.

## Requirements

### Requirement: Rutas de las pantallas
La interfaz MUST resolver la pantalla a mostrar a partir del fragmento de la URL: `#/` (o fragmento vacío) → listado; `#/nueva` → alta; `#/factura/{id}` → detalle; `#/factura/{id}/editar` → edición, donde `{id}` MUST ser un entero positivo escrito solo con dígitos. Cualquier otro fragmento MUST tratarse como ruta desconocida.

#### Scenario: Rutas conocidas
- **WHEN** se interpretan los fragmentos ``, `#/`, `#/nueva`, `#/factura/5` y `#/factura/5/editar`
- **THEN** resultan listado, listado, alta, detalle de la factura 5 y edición de la factura 5

#### Scenario: Rutas desconocidas
- **WHEN** se interpretan los fragmentos `#/facturas`, `#/factura/abc`, `#/factura/0`, `#/factura/-3`, `#/factura/2.5` y `#/factura/5/borrar`
- **THEN** todos resultan ruta desconocida

### Requirement: Enlace directo y recarga
Abrir la aplicación con un fragmento MUST mostrar directamente la pantalla de esa ruta, sin pasar por el listado; recargar la página MUST conservar la pantalla.

#### Scenario: Entrada directa al detalle
- **WHEN** la aplicación arranca con la dirección `#/factura/5`
- **THEN** se muestra la pantalla de detalle, se pide el detalle de la factura 5 y no se pide el listado

#### Scenario: Entrada sin fragmento
- **WHEN** la aplicación arranca sin fragmento
- **THEN** se muestra el listado y se pide su primera página

### Requirement: Historial del navegador
Cada navegación entre pantallas MUST quedar en el historial del navegador, de modo que Atrás y Adelante cambien de pantalla; el cambio de fragmento MUST mostrar la pantalla correspondiente sin recargar el documento.

#### Scenario: Atrás desde el detalle
- **WHEN** desde el listado se abre el detalle de la factura 5 y luego se vuelve atrás en el historial
- **THEN** se muestra el listado sin recargar el documento

#### Scenario: Cambio de fragmento
- **WHEN** con el listado visible el fragmento cambia a `#/nueva`
- **THEN** se muestra la pantalla de alta

### Requirement: Ruta desconocida
Ante una ruta desconocida la interfaz MUST mostrar el listado y MUST explicar en la región de estado que la dirección no existe.

#### Scenario: Dirección inexistente
- **WHEN** la aplicación arranca con la dirección `#/facturas`
- **THEN** se muestra el listado y la región de estado indica que la dirección `#/facturas` no existe y que se muestra el listado

### Requirement: Una pantalla visible a la vez
Exactamente una pantalla MUST estar visible; las demás MUST estar ocultas con el atributo `hidden`, de modo que ningún control suyo sea alcanzable con Tab ni expuesto a los lectores de pantalla. La región de estado, la barra de navegación y el explorador modal MUST quedar fuera de las pantallas y compartirse entre ellas.

#### Scenario: Solo el detalle visible
- **WHEN** se muestra el detalle de la factura 5
- **THEN** la sección del detalle no tiene `hidden` y las del listado y del formulario sí lo tienen

#### Scenario: Región y explorador compartidos
- **WHEN** se inspecciona el documento
- **THEN** existe una sola región de estado y un solo diálogo explorador, ambos fuera de las tres secciones de pantalla

### Requirement: Encabezado, foco y título por pantalla
Cada pantalla MUST tener su propio `h1` enfocable por programa (`tabindex="-1"`) y el documento MUST tener exactamente un `h1` visible. Al navegar a otra pantalla, el foco MUST moverse al `h1` de la pantalla nueva y `document.title` MUST nombrar la pantalla seguida de `Facturación ARCA`. La carga inicial MUST NOT mover el foco.

#### Scenario: Foco al abrir el detalle
- **WHEN** desde el listado se navega al detalle de la factura 5
- **THEN** el elemento enfocado es el `h1` del detalle y el título del documento nombra la factura y termina en `Facturación ARCA`

#### Scenario: Títulos de las pantallas
- **WHEN** se navega al listado y luego a la alta
- **THEN** el título del documento es `Facturas — Facturación ARCA` y luego `Nueva factura — Facturación ARCA`

#### Scenario: Un h1 visible
- **WHEN** se muestra cualquiera de las pantallas
- **THEN** hay exactamente un `h1` fuera de las secciones ocultas

### Requirement: Barra de navegación
La página MUST ofrecer una región de navegación nombrada con un enlace «Facturas» a `#/` y otro «Nueva factura» a `#/nueva`. El enlace de la pantalla actual MUST llevar `aria-current="page"`; en el detalle y en la edición ninguno de los dos lo lleva.

#### Scenario: Listado actual
- **WHEN** se muestra el listado
- **THEN** «Facturas» tiene `aria-current="page"` y «Nueva factura» no

#### Scenario: Detalle sin enlace actual
- **WHEN** se muestra el detalle de la factura 5
- **THEN** ninguno de los dos enlaces tiene `aria-current`

### Requirement: Mensajes que sobreviven a la navegación
Cuando la propia aplicación navega después de una operación (guardar, eliminar o una edición imposible), el mensaje de esa operación MUST mostrarse en la región de estado de la pantalla de destino. Cuando la navegación la inicia el usuario (enlace, Atrás o Adelante), la región de estado MUST limpiarse para no dejar un mensaje de otra pantalla.

#### Scenario: Éxito tras guardar
- **WHEN** se guarda un borrador nuevo y la API responde con el id 15
- **THEN** se muestra el detalle de la factura 15 y la región de estado anuncia que se creó el borrador 15

#### Scenario: Error viejo al navegar
- **WHEN** el detalle muestra un error en la región de estado y el usuario activa el enlace «Facturas»
- **THEN** se muestra el listado y la región de estado queda vacía

### Requirement: Cargas obsoletas al cambiar de pantalla
Al salir de una pantalla, las respuestas pendientes de su carga MUST descartarse: una respuesta tardía MUST NOT modificar la pantalla ni la región de estado.

#### Scenario: Detalle que llega tarde
- **WHEN** se abre el detalle de la factura 5, se vuelve al listado antes de la respuesta y luego llega el detalle
- **THEN** el listado sigue visible y el contenido del detalle no se actualiza

#### Scenario: Error que llega tarde
- **WHEN** se abre el detalle de la factura 5, se vuelve al listado y luego la carga del detalle falla con `404`
- **THEN** la región de estado no muestra ese error
