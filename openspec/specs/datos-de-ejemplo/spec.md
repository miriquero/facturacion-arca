# datos-de-ejemplo Specification

## Purpose
Define la carga inicial de datos de ejemplo (usuarios, catálogos, clientes y facturas representativas) que se ejecuta al arrancar la aplicación fuera del perfil de pruebas, de forma idempotente y dejando limpio el contexto de auditoría.

## Requirements

### Requirement: Usuarios de ejemplo
Al arrancar fuera del perfil `test` con la base sin usuarios, el sistema MUST crear los usuarios `sistema` y `acortez` que pide la consigna, y además `mriquero` (el estudiante).

#### Scenario: Usuarios creados
- **WHEN** la aplicación arranca sin el perfil `test` sobre una base sin usuarios
- **THEN** existen exactamente los usuarios `sistema`, `acortez` y `mriquero`

### Requirement: Catálogos y clientes de ejemplo
La carga MUST crear al menos una condición de IVA, las monedas `PES` y `DOL`, al menos dos puntos de venta, rubros, marcas, artículos (alguno sin marca o sin rubro), una lista de precios con sus artículos de lista y al menos dos clientes con contacto y domicilio.

#### Scenario: Catálogos presentes
- **WHEN** termina la carga sobre una base vacía
- **THEN** existen las monedas `PES` y `DOL`, al menos dos puntos de venta, al menos una condición de IVA, artículos con precio en una lista y al menos dos clientes, cada uno con contacto y domicilio

### Requirement: Seis facturas representativas
La carga MUST crear seis facturas: tres EMITIDA y una ANULADA con número asignado por punto de venta, CAE de 14 dígitos y vencimiento del CAE, y dos BORRADOR con número nulo y sin CAE; al menos una factura MUST no tener cliente y cada una MUST tener al menos un renglón. Sus importes MUST ser los que calcula el servicio a partir de los precios de lista, y el vencimiento del CAE MUST ser la fecha actual de la carga más 10 días.

#### Scenario: Distribución por estado
- **WHEN** termina la carga sobre una base vacía
- **THEN** hay seis facturas: tres EMITIDA, una ANULADA y dos BORRADOR

#### Scenario: Borradores sin numerar
- **WHEN** termina la carga sobre una base vacía
- **THEN** las facturas en BORRADOR tienen número y CAE nulos, y las EMITIDA y ANULADA tienen número y un CAE de 14 dígitos

#### Scenario: Auditoría de la carga
- **WHEN** termina la carga sobre una base vacía
- **THEN** todas las facturas y catálogos tienen usuario de carga asignado

#### Scenario: Numeración real por punto de venta
- **WHEN** termina la carga sobre una base vacía
- **THEN** las facturas numeradas del punto de venta 1 tienen los números 1, 2 y 3, la del punto de venta 2 tiene el número 1, y cada una vence el día de la carga más 10 días con resultado `A`

#### Scenario: Importes calculados
- **WHEN** termina la carga sobre una base vacía
- **THEN** el borrador con un martillo y dos cables tiene subtotales 3630.00 y 19360.00 y total 22990.00, y cada factura tiene cobrado 0 y saldo igual a su total

#### Scenario: Anulada con baja lógica
- **WHEN** termina la carga sobre una base vacía
- **THEN** la factura ANULADA tiene fecha de anulación igual al día de la carga, usuario de baja `sistema` y fecha de baja no nula

### Requirement: Carga idempotente
Si al arrancar ya existe al menos un usuario, la carga MUST NOT insertar ni modificar ningún dato.

#### Scenario: Segunda ejecución
- **WHEN** la carga se ejecuta por segunda vez sobre la base ya cargada
- **THEN** las cantidades de usuarios, catálogos y facturas no cambian

### Requirement: Carga excluida del perfil de pruebas
Con el perfil `test` activo, la carga MUST NOT ejecutarse.

#### Scenario: Perfil test
- **WHEN** la aplicación arranca con el perfil `test` sobre una base vacía
- **THEN** no existe el componente de carga y la base no tiene usuarios

### Requirement: Carga atómica con contexto de auditoría limpio
La carga MUST ejecutarse en una única transacción y MUST dejar vacío el contexto de auditoría al terminar, haya terminado bien o con error.

#### Scenario: Contexto limpio tras la carga
- **WHEN** termina la carga
- **THEN** el contexto de auditoría del hilo que la ejecutó está vacío

#### Scenario: Contexto limpio tras un error
- **WHEN** la carga falla a mitad de camino
- **THEN** el contexto de auditoría queda vacío y no quedan datos parciales de esa carga en la base

### Requirement: Facturas creadas mediante el servicio
Las facturas de ejemplo MUST crearse y cambiar de estado mediante las mismas operaciones de servicio que usa la API, de modo que cumplan sus reglas de cálculo, numeración, CAE y auditoría; usuarios, catálogos y clientes se siguen cargando directamente por sus repositorios.

#### Scenario: Carga que respeta las reglas de la API
- **WHEN** la operación de emisión del servicio rechazaría una factura (por ejemplo, por no tener renglones)
- **THEN** la carga falla completa, no deja datos parciales y el contexto de auditoría queda vacío
