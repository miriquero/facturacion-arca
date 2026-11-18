## MODIFIED Requirements

### Requirement: Región de estado persistente y anunciada
La página MUST tener desde el inicio una única región de estado con `role="status"` y `aria-live="polite"`, ubicada fuera de las pantallas y compartida por todas, que nunca se retira del DOM ni queda oculta al cambiar de pantalla; los mensajes de éxito, error y progreso de cualquier pantalla MUST escribirse en ella.

#### Scenario: Región después de mensajes
- **WHEN** se muestran y se limpian varios mensajes
- **THEN** la misma región con `role="status"` y `aria-live="polite"` sigue en el DOM

#### Scenario: Región entre pantallas
- **WHEN** se navega del listado al detalle y del detalle a la alta
- **THEN** sigue existiendo un único elemento con `role="status"`, el mismo nodo, sin `hidden` y fuera de las secciones de pantalla

### Requirement: Operación completa por teclado
Todas las funciones de la aplicación (navegar entre pantallas con la barra y con «Ver» y «Volver al listado», filtrar, elegir y quitar cliente, descargar, paginar, cambiar el tamaño de página, agregar y quitar renglones, elegir artículo, guardar, cancelar, imprimir el PDF y las acciones del detalle) MUST realizarse con controles nativos activables por teclado, en un orden de foco que sigue el orden visual, con indicador de foco visible; las ventanas exploradoras MUST cerrarse con Escape. Los controles de las pantallas ocultas MUST NOT recibir el foco.

#### Scenario: Controles nativos
- **WHEN** se inspeccionan los elementos accionables de cada pantalla, con un explorador abierto
- **THEN** todos son `button`, `a` con `href`, `input`, `select` o `textarea`, ninguno tiene `tabindex` positivo y todos los botones declaran `type`

#### Scenario: Recorrido con teclado
- **WHEN** en un navegador real se recorre la aplicación solo con Tab, Mayús+Tab, Enter, Espacio, flechas y Escape
- **THEN** se puede completar el circuito filtrar por cliente–paginar–ver detalle–volver–crear con explorador–editar–emitir–imprimir–anular–eliminar y el foco es visible en cada control

#### Scenario: Pantallas ocultas fuera del recorrido
- **WHEN** se muestra el detalle de una factura
- **THEN** ningún campo de filtro ni del formulario es alcanzable con Tab, porque sus secciones tienen `hidden`
