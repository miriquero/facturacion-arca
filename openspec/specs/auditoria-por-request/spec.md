# auditoria-por-request Specification

## Purpose
Define cómo cada petición HTTP determina el usuario que queda registrado en la auditoría a partir del encabezado `X-Usuario`, con un usuario por defecto, y garantiza que ese dato no sobreviva a la petición; deja explícito que no es autenticación.

## Requirements

### Requirement: Usuario declarado en X-Usuario
Toda petición a `/api` MUST fijar como usuario de auditoría al usuario existente cuyo identificador coincide con el valor de `X-Usuario`, sin espacios exteriores.

#### Scenario: Alta con usuario declarado
- **WHEN** se crea un borrador con `X-Usuario: acortez` y el usuario `acortez` existe
- **THEN** la factura releída tiene `acortez` como usuario de carga y de modificación

#### Scenario: Espacios exteriores
- **WHEN** se crea un borrador con `X-Usuario: "  acortez  "`
- **THEN** el usuario de carga es `acortez`

### Requirement: Usuario por defecto
Si `X-Usuario` falta, está vacío o no corresponde a un usuario existente, la petición MUST continuar normalmente usando el usuario configurado por defecto, `sistema`, y MUST NOT rechazarse por ese motivo.

#### Scenario: Sin encabezado
- **WHEN** se crea un borrador sin `X-Usuario`
- **THEN** la respuesta es `201` y el usuario de carga es `sistema`

#### Scenario: Encabezado vacío
- **WHEN** se crea un borrador con `X-Usuario: ""`
- **THEN** el usuario de carga es `sistema`

#### Scenario: Usuario inexistente
- **WHEN** se crea un borrador con `X-Usuario: intruso`
- **THEN** la respuesta es `201` y el usuario de carga es `sistema`

### Requirement: Contexto limpio al terminar la petición
El contexto de auditoría del hilo que atendió una petición MUST quedar vacío al terminarla, tanto si terminó bien como si terminó con un error.

#### Scenario: Petición exitosa
- **WHEN** termina un `POST /api/facturas` exitoso con `X-Usuario: acortez`
- **THEN** el contexto de auditoría del hilo que la atendió está vacío

#### Scenario: Petición con error
- **WHEN** termina con `409` un `PUT` a una factura EMITIDA enviado con `X-Usuario: acortez`
- **THEN** el contexto de auditoría del hilo que la atendió está vacío

#### Scenario: Sin fuga entre peticiones
- **WHEN** una petición con `X-Usuario: acortez` termina y la siguiente, en el mismo hilo, no envía el encabezado y crea un borrador
- **THEN** el usuario de carga del segundo borrador es `sistema`

### Requirement: Identidad declarada, no autenticada
El encabezado `X-Usuario` MUST usarse solo para registrar quién declaró la operación: la API MUST NOT pedir credenciales ni rechazar una operación según el usuario declarado.

#### Scenario: Cualquier usuario declarado puede operar
- **WHEN** se emite y anula una factura con `X-Usuario: mriquero` sin ninguna credencial
- **THEN** ambas operaciones responden `200` y la baja queda registrada a nombre de `mriquero`
