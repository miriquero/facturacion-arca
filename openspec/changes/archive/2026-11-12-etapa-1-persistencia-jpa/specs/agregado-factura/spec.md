# Spec Delta

## Purpose

Define la factura de venta como raíz de agregado de sus renglones: quién posee la clave foránea, cómo se mantienen ambos extremos de la relación, qué operaciones se propagan a los renglones y qué datos externos al agregado nunca se ven afectados.

## ADDED Requirements

### Requirement: El renglón posee la clave foránea
La relación entre factura y renglones MUST representarse con una clave foránea a la factura en la tabla de renglones, sin tabla intermedia.

#### Scenario: Columna de la FK en el renglón
- **WHEN** se inspecciona el esquema generado
- **THEN** la tabla `factura_venta_detalle` tiene una columna `factura_id` no nula que referencia a `factura_venta`

### Requirement: Agregar un renglón enlaza ambos extremos
Agregar un renglón a una factura MUST incorporarlo a la colección de renglones de la factura y MUST fijar la factura en el renglón; la colección de renglones de una factura nueva MUST existir vacía.

#### Scenario: Factura nueva
- **WHEN** se crea una factura nueva
- **THEN** su colección de renglones existe y está vacía

#### Scenario: Ambos extremos en memoria
- **WHEN** se agregan dos renglones a una factura
- **THEN** la factura contiene los dos renglones y cada renglón referencia a esa factura

### Requirement: Guardar la factura guarda sus renglones
Guardar una factura MUST insertar sus renglones sin operaciones independientes sobre ellos, y al releerla en un contexto de persistencia limpio MUST recuperarse con sus renglones y la relación inversa correcta.

#### Scenario: Ida y vuelta con dos renglones
- **WHEN** se guarda una factura con dos renglones, se sincroniza, se limpia el contexto de persistencia y se relee por su id
- **THEN** la factura releída tiene dos renglones y cada uno referencia a la factura releída

#### Scenario: Factura con un solo renglón
- **WHEN** se guarda una factura con un renglón y se relee en un contexto limpio
- **THEN** la factura releída tiene exactamente un renglón

### Requirement: Renglón quitado se elimina
Un renglón quitado de la colección de su factura MUST eliminarse de la base al sincronizar.

#### Scenario: Huérfano eliminado
- **WHEN** se relee una factura con dos renglones, se quita uno, se sincroniza y se limpia el contexto
- **THEN** la factura tiene un renglón y el renglón quitado ya no existe en la base

### Requirement: Reemplazo de renglones sobre la misma colección
Reemplazar los renglones de una factura MUST vaciar la colección gestionada y cargar los nuevos renglones en esa misma instancia de colección; los renglones anteriores MUST eliminarse de la base.

#### Scenario: Reemplazo completo
- **WHEN** una factura guardada con dos renglones se reemplaza por un único renglón nuevo y se sincroniza
- **THEN** la instancia de la colección es la misma que antes del reemplazo, la factura releída tiene solo el renglón nuevo y los dos anteriores no existen en la base

### Requirement: Catálogos y cliente fuera del agregado
Guardar o eliminar una factura MUST NOT guardar ni eliminar el cliente, el punto de venta, la condición de IVA, la moneda, los artículos, las listas ni los artículos de lista que referencia.

#### Scenario: Eliminar un borrador conserva los catálogos
- **WHEN** se elimina una factura en BORRADOR con cliente y dos renglones, se sincroniza y se limpia el contexto
- **THEN** la factura y sus renglones ya no existen, y el cliente con su contacto y domicilio, el punto de venta, la condición de IVA, la moneda, los artículos de lista, los artículos y la lista siguen existiendo

#### Scenario: Catálogo no persistido no se guarda en cascada
- **WHEN** se intenta guardar una factura que referencia un punto de venta nuevo aún no guardado
- **THEN** la operación falla y el punto de venta no se inserta
