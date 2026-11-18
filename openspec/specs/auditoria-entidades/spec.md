# auditoria-entidades Specification

## Purpose
Define el identificador común de todas las entidades y los datos de auditoría (alta, modificación y baja) de las entidades auditables, completados a partir del usuario declarado en el contexto de auditoría y protegidos contra cambios posteriores del alta.

## Requirements

### Requirement: Identificador generado por la base
Toda entidad MUST tener un identificador numérico entero largo generado por la base de datos al insertarse (identidad), y los datos comunes de identificación y auditoría MUST mapearse en las tablas de cada entidad, sin tablas propias para las superclases.

#### Scenario: Id asignado al persistir
- **WHEN** se persiste una entidad nueva sin identificador
- **THEN** después de sincronizar con la base la entidad tiene un identificador no nulo asignado por la base

#### Scenario: Sin tablas para las superclases
- **WHEN** se inspecciona el esquema generado
- **THEN** no existen tablas que correspondan a las superclases de identificación ni de auditoría, y las columnas de auditoría aparecen en las tablas de las entidades auditables

### Requirement: Alta completada desde el contexto de auditoría
Al persistir una entidad auditable, el sistema MUST registrar fecha y hora de alta y de modificación (fecha con hora) y MUST tomar el usuario de carga y de modificación del contexto de auditoría del hilo actual.

#### Scenario: Auditoría completa al persistir
- **WHEN** el contexto de auditoría contiene el usuario `sistema` y se persiste un catálogo nuevo
- **THEN** al releerlo desde la base tiene fecha de alta y de modificación no nulas, y `sistema` como usuario de carga y de modificación

#### Scenario: Usuario de auditoría distinto
- **WHEN** el contexto de auditoría contiene el usuario `acortez` y se persiste un catálogo nuevo
- **THEN** al releerlo desde la base `acortez` figura como usuario de carga y de modificación

### Requirement: Persistencia rechazada sin usuario en el contexto
Si el contexto de auditoría está vacío al persistir una entidad auditable, la base MUST rechazar la inserción por violar la obligatoriedad del usuario de carga.

#### Scenario: Contexto vacío
- **WHEN** el contexto de auditoría está vacío y se persiste un catálogo nuevo
- **THEN** la operación falla con una violación de integridad de datos y la entidad no queda guardada

### Requirement: Alta inmutable después de persistir
La fecha de alta y el usuario de carga MUST NOT modificarse en la base una vez insertada la entidad, aunque se cambien en el objeto en memoria.

#### Scenario: Cambio del alta ignorado
- **WHEN** se persiste un catálogo, se cambian en memoria su fecha de alta y su usuario de carga, se sincroniza, se limpia el contexto de persistencia y se relee
- **THEN** la fecha de alta y el usuario de carga releídos son los originales

### Requirement: Referencias de auditoría diferidas y opcionales en la baja
Las referencias de auditoría a usuarios MUST cargarse de forma diferida, y la fecha y el usuario de baja MUST admitir valores nulos mientras la entidad no fue dada de baja.

#### Scenario: Usuario de carga no inicializado
- **WHEN** se relee un catálogo en un contexto de persistencia limpio
- **THEN** su usuario de carga no está inicializado hasta que se accede a sus datos

#### Scenario: Baja nula al crear
- **WHEN** se persiste un catálogo nuevo
- **THEN** su fecha de baja y su usuario de baja quedan nulos

### Requirement: Contexto de auditoría por hilo
El contexto de auditoría MUST guardar un usuario por hilo de ejecución, devolverlo mientras esté fijado y quedar vacío tras limpiarlo; un hilo MUST NOT ver el usuario fijado por otro.

#### Scenario: Fijar y limpiar
- **WHEN** se fija el usuario `sistema` en el contexto y luego se limpia
- **THEN** el contexto devuelve `sistema` antes de limpiar y ningún usuario después

#### Scenario: Aislamiento entre hilos
- **WHEN** un hilo fija el usuario `sistema` y otro hilo consulta el contexto
- **THEN** el otro hilo no obtiene ningún usuario

### Requirement: Modificación completada desde el contexto de auditoría
Al actualizar una entidad auditable, el sistema MUST registrar la fecha y hora de modificación y MUST tomar el usuario de modificación del contexto de auditoría del hilo actual, sin alterar fecha de alta ni usuario de carga.

#### Scenario: Modificación con el contexto vacío
- **WHEN** una entidad auditable guardada se modifica y el contexto de auditoría del hilo está vacío
- **THEN** la modificación falla por la restricción `NOT NULL` del usuario de modificación (igual que un alta con el contexto vacío) y la base no cambia; el sistema MUST NOT conservar el usuario anterior ni atribuir la modificación a otro usuario

#### Scenario: Cambio de estado por otro usuario
- **WHEN** una factura creada con el usuario `sistema` se emite con `acortez` en el contexto de auditoría
- **THEN** al releerla su usuario de modificación es `acortez`, su fecha de modificación no es anterior a la de alta y su usuario de carga sigue siendo `sistema`

### Requirement: Modificación explícita en cada edición aceptada
Cada `PUT` aceptado MUST registrar la modificación de la cabecera de la factura aunque solo cambien los renglones o se reenvíe el mismo contenido.

#### Scenario: Solo cambian los renglones
- **WHEN** un borrador creado por `sistema` recibe con `X-Usuario: acortez` un `PUT` que mantiene la cabecera y cambia solo la cantidad de un renglón
- **THEN** la cabecera releída tiene `acortez` como usuario de modificación

#### Scenario: Mismo contenido
- **WHEN** un borrador creado por `sistema` recibe con `X-Usuario: mriquero` un `PUT` idéntico a su contenido actual
- **THEN** la cabecera releída tiene `mriquero` como usuario de modificación

### Requirement: Baja lógica al anular
Anular una factura MUST registrar la fecha y hora de baja y el usuario de baja tomado del contexto de auditoría, sin eliminar la fila; mientras la factura no está anulada, ambos datos MUST permanecer nulos.

#### Scenario: Anulación por un usuario declarado
- **WHEN** una factura EMITIDA se anula con `X-Usuario: acortez`
- **THEN** al releerla tiene fecha de baja no nula, `acortez` como usuario de baja, y la fila sigue existiendo

#### Scenario: Emitida sin baja
- **WHEN** se emite un borrador
- **THEN** la factura emitida tiene fecha y usuario de baja nulos
