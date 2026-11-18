## MODIFIED Requirements

### Requirement: Controles etiquetados
Cada campo de filtro, de cabecera, de renglón y del explorador MUST tener una etiqueta asociada que lo nombre; los campos y botones de renglón (incluido el que abre el explorador de artículos) MUST incluir en su nombre accesible el número de renglón, y los botones que abren un explorador MUST nombrar qué se elige.

#### Scenario: Campos con nombre
- **WHEN** se inspeccionan los campos de la página con dos renglones
- **THEN** todos los `input`, `select` y `textarea` tienen un `label` asociado y los de cantidad se distinguen como renglón 1 y renglón 2

#### Scenario: Botones de explorador con nombre
- **WHEN** se inspeccionan los botones que abren exploradores con dos renglones
- **THEN** el del formulario nombra la elección de cliente, el del filtro nombra el cliente del filtro y los de renglón nombran la elección de artículo del renglón 1 y del renglón 2

### Requirement: Operación completa por teclado
Todas las funciones de la página (filtrar, elegir y quitar cliente, descargar, paginar, cambiar el tamaño de página, agregar y quitar renglones, elegir artículo, guardar, cancelar, imprimir el PDF y las acciones de fila) MUST realizarse con controles nativos activables por teclado, en un orden de foco que sigue el orden visual, con indicador de foco visible; las ventanas exploradoras MUST cerrarse con Escape.

#### Scenario: Controles nativos
- **WHEN** se inspeccionan los elementos accionables de la página, con un explorador abierto
- **THEN** todos son `button`, `a` con `href`, `input`, `select` o `textarea`, ninguno tiene `tabindex` positivo y todos los botones declaran `type`

#### Scenario: Recorrido con teclado
- **WHEN** en un navegador real se recorre la página solo con Tab, Mayús+Tab, Enter, Espacio, flechas y Escape
- **THEN** se puede completar el circuito filtrar por cliente–paginar–crear con explorador–editar–emitir–imprimir–anular–eliminar y el foco es visible en cada control
