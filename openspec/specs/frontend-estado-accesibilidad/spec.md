# frontend-estado-accesibilidad Specification

## Purpose
Fija cómo el frontend comunica el estado de las operaciones y presenta los datos recibidos de forma segura y accesible: región de estado anunciada, mensajes de éxito y error, texto externo nunca interpretado como HTML, controles etiquetados y operación por teclado.

## Requirements

### Requirement: Texto externo nunca interpretado como HTML
Todo dato recibido de la API (denominaciones, etiquetas de catálogo, descripciones, observaciones y mensajes de error) MUST insertarse como texto mediante nodos del DOM, sin interpretarse como HTML.

#### Scenario: Denominación que parece HTML
- **WHEN** el listado trae un cliente `<img src=x onerror=alert(1)>` y un catálogo con la etiqueta `<b>Casa</b>`
- **THEN** la celda y la opción muestran ese texto literal y el documento no contiene ningún elemento `img` ni `b` creado a partir de esos datos

#### Scenario: Mensaje de error que parece HTML
- **WHEN** un `ApiError` trae el detalle `<script>x()</script>`
- **THEN** la región de estado muestra ese texto literal y no se agrega ningún elemento `script`

### Requirement: Región de estado persistente y anunciada
La página MUST tener desde el inicio una única región de estado con `role="status"` y `aria-live="polite"` que nunca se retira del DOM; los mensajes de éxito, error y progreso MUST escribirse en ella.

#### Scenario: Región después de mensajes
- **WHEN** se muestran y se limpian varios mensajes
- **THEN** la misma región con `role="status"` y `aria-live="polite"` sigue en el DOM

### Requirement: Guardado reflejado con aria-busy
Mientras un alta o una edición están en curso, el formulario MUST tener `aria-busy="true"`, y al terminar con éxito o con error MUST volver a `aria-busy="false"`.

#### Scenario: Durante y después del guardado
- **WHEN** se guarda un borrador y la respuesta todavía no llegó
- **THEN** el formulario tiene `aria-busy="true"`, y cuando llega la respuesta (`201` o error) vuelve a `false`

### Requirement: Éxito que se limpia a los seis segundos
Un mensaje de éxito MUST limpiarse a los 6 segundos dejando la región vacía en el DOM; si antes se escribe otro mensaje, la limpieza pendiente MUST NOT borrar el mensaje nuevo.

#### Scenario: Limpieza a los 6 s
- **WHEN** se anuncia un éxito y pasan 6 segundos
- **THEN** la región queda vacía y sigue en el DOM

#### Scenario: Mensaje nuevo antes de los 6 s
- **WHEN** se anuncia un éxito, a los 3 segundos un error, y pasan otros 4 segundos
- **THEN** el error sigue visible

### Requirement: Errores visibles hasta una acción posterior
Un mensaje de error MUST permanecer visible sin límite de tiempo hasta que el usuario inicie otra acción (buscar, guardar, cancelar, editar o una acción de fila), y MUST distinguirse del éxito por texto y no solo por color.

#### Scenario: Error persistente
- **WHEN** se muestra un error y pasan 60 segundos sin acciones
- **THEN** el error sigue visible con un prefijo de texto que lo identifica como error

#### Scenario: Acción posterior
- **WHEN** con un error visible se inicia una nueva búsqueda
- **THEN** el error se retira de la región

### Requirement: Controles etiquetados
Cada campo de filtro, de cabecera y de renglón MUST tener una etiqueta asociada que lo nombre, y los campos de renglón MUST incluir en su nombre accesible el número de renglón.

#### Scenario: Campos con nombre
- **WHEN** se inspeccionan los campos de la página con dos renglones
- **THEN** todos los `input`, `select` y `textarea` tienen un `label` asociado y los de cantidad se distinguen como renglón 1 y renglón 2

### Requirement: Errores de campo accesibles
Un campo inválido MUST tener `aria-invalid="true"` y referenciar por `aria-describedby` el texto de su error; al corregirse y volver a validar MUST perder ambas marcas.

#### Scenario: Cantidad inválida
- **WHEN** la cantidad del renglón 1 es `0` y se intenta guardar
- **THEN** ese campo tiene `aria-invalid="true"` y su `aria-describedby` apunta a un elemento con el mensaje del error

#### Scenario: Corrección
- **WHEN** luego se corrige la cantidad a `2` y se guarda
- **THEN** el campo ya no tiene `aria-invalid="true"` ni el mensaje de error

### Requirement: Operación completa por teclado
Todas las funciones de la página (filtrar, descargar, agregar y quitar renglones, guardar, cancelar y las acciones de fila) MUST realizarse con controles nativos activables por teclado, en un orden de foco que sigue el orden visual, con indicador de foco visible.

#### Scenario: Controles nativos
- **WHEN** se inspeccionan los elementos accionables de la página
- **THEN** todos son `button`, `a` con `href`, `input`, `select` o `textarea`, ninguno tiene `tabindex` positivo y todos los botones declaran `type`

#### Scenario: Recorrido con teclado
- **WHEN** en un navegador real se recorre la página solo con Tab, Mayús+Tab, Enter, Espacio y flechas
- **THEN** se puede completar el circuito crear–editar–emitir–anular–eliminar y el foco es visible en cada control
