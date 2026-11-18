# Spec Delta

## ADDED Requirements

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
