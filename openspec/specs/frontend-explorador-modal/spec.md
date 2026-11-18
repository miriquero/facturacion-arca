# frontend-explorador-modal Specification

## Purpose
TBD - created by archiving change etapa-6-requisitos-docente. Update Purpose after archive.

## Requirements

### Requirement: Ventana modal exploradora
La interfaz SHALL ofrecer una ventana modal exploradora para elegir un cliente (en el formulario y en el filtro de la consulta) y para elegir una opción lista–artículo (en cada renglón). La ventana MUST ser un diálogo modal con título visible referenciado por `aria-labelledby`, un campo de búsqueda etiquetado, una lista de resultados y un botón «Cancelar»; mientras está abierta, el resto de la página MUST quedar inerte.

#### Scenario: Apertura del explorador de clientes
- **WHEN** se activa el botón para elegir cliente del formulario
- **THEN** se abre un diálogo modal cuyo `aria-labelledby` apunta a un título que dice `Buscar cliente`, con un campo de búsqueda con etiqueta y un botón «Cancelar»

#### Scenario: Apertura del explorador de artículos
- **WHEN** se activa el botón para elegir artículo del renglón 2
- **THEN** se abre un diálogo modal con el título `Buscar artículo de lista de precios` que menciona el renglón 2

### Requirement: Búsqueda en el servidor
Al abrirse, el explorador MUST pedir el catálogo correspondiente sin texto y mostrar los resultados; al buscar MUST pedir el catálogo con el texto escrito. Solo la respuesta de la búsqueda más reciente MUST mostrarse, y una búsqueda sin coincidencias MUST mostrar un mensaje en lugar de la lista.

#### Scenario: Búsqueda de clientes
- **WHEN** en el explorador de clientes se escribe `juan` y se busca
- **THEN** se pide la búsqueda de clientes con el texto `juan` y la lista muestra las opciones recibidas, con su etiqueta

#### Scenario: Sin coincidencias
- **WHEN** la búsqueda devuelve `[]`
- **THEN** el explorador muestra un mensaje de que no hay resultados y la cantidad 0

#### Scenario: Respuestas en orden inverso
- **WHEN** se busca `ju` (búsqueda 1) y luego `juan` (búsqueda 2), y la respuesta de la 2 llega antes que la de la 1
- **THEN** la lista queda con el resultado de `juan`

#### Scenario: Error de búsqueda
- **WHEN** la búsqueda falla con `500`
- **THEN** el explorador muestra el mensaje del error dentro del diálogo, anunciado, y sigue abierto

### Requirement: Resultados navegables y elegibles por teclado
Cada resultado MUST ser un botón nativo cuyo nombre accesible es la etiqueta de la opción (y, en artículos, también su precio); activarlo MUST devolver la opción elegida, cerrar el diálogo y completar el campo que lo abrió.

#### Scenario: Elegir un cliente
- **WHEN** en el explorador del formulario se activa el resultado `Juan Servicios (20-12345678-9)` de id 2
- **THEN** el diálogo se cierra y el formulario muestra ese cliente con id 2 elegido

#### Scenario: Elegir un artículo
- **WHEN** en el explorador del renglón 1 se activa `ART-001 - Martillo (GENERAL)` de id 7 y precio 3000.00
- **THEN** el diálogo se cierra, el renglón 1 queda con la opción 7 y muestra el precio 3000,00 y la estimación se recalcula

#### Scenario: Elegir el cliente del filtro
- **WHEN** en el explorador del filtro se elige el cliente de id 2
- **THEN** el filtro muestra ese cliente y no se envía ninguna búsqueda de facturas hasta que se busque

### Requirement: Cancelación sin cambios
Cerrar el explorador con «Cancelar» o con Escape MUST cerrar el diálogo sin cambiar la elección previa del campo que lo abrió.

#### Scenario: Escape
- **WHEN** con el cliente 2 elegido en el formulario se abre el explorador, se busca otro cliente y se pulsa Escape
- **THEN** el diálogo se cierra y el formulario sigue con el cliente 2

#### Scenario: Cancelar
- **WHEN** en el explorador de un renglón sin opción se activa «Cancelar»
- **THEN** el diálogo se cierra y el renglón sigue sin opción elegida

### Requirement: Manejo del foco
Al abrirse, el foco MUST pasar al campo de búsqueda del explorador; al cerrarse, por elección o cancelación, el foco MUST volver al botón que lo abrió.

#### Scenario: Ida y vuelta del foco
- **WHEN** se abre el explorador desde el botón de elegir artículo del renglón 1 y luego se cancela
- **THEN** al abrir el foco está en el campo de búsqueda y al cerrar vuelve a ese botón

### Requirement: Texto del servidor sin interpretar
Las etiquetas recibidas MUST insertarse como texto en los resultados y en el campo completado, sin interpretarse como HTML.

#### Scenario: Etiqueta que parece HTML
- **WHEN** la búsqueda devuelve un cliente con la etiqueta `<img src=x onerror=alert(1)>`
- **THEN** el resultado muestra ese texto literal y el diálogo no contiene ningún elemento `img`

### Requirement: Un solo explorador a la vez y bloqueo durante el guardado
Solo un explorador MUST estar abierto a la vez, y los botones que abren exploradores del formulario MUST deshabilitarse mientras se guarda, igual que el resto del formulario.

#### Scenario: Guardado en curso
- **WHEN** se guarda un borrador y la respuesta todavía no llegó
- **THEN** los botones para elegir cliente y artículo del formulario están deshabilitados, y vuelven a habilitarse al terminar
