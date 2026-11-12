# agregado-cliente Specification

## Purpose
Define al cliente como dueño de su contacto y su domicilio: ambos son obligatorios, se guardan, reemplazan y eliminan junto con el cliente y no tienen existencia ni acceso propios.

## Requirements

### Requirement: Contacto y domicilio guardados con el cliente
Guardar un cliente nuevo con su contacto y su domicilio MUST insertar también ambos, sin operaciones independientes sobre ellos.

#### Scenario: Ida y vuelta del cliente
- **WHEN** se guarda un cliente con contacto y domicilio nuevos, se sincroniza, se limpia el contexto y se relee
- **THEN** el cliente releído tiene el contacto y el domicilio con los datos originales

### Requirement: Contacto y domicilio obligatorios
La base MUST rechazar un cliente sin contacto o sin domicilio.

#### Scenario: Cliente sin domicilio
- **WHEN** se intenta guardar un cliente con contacto pero sin domicilio
- **THEN** la operación falla con una violación de integridad de datos

#### Scenario: Cliente sin contacto
- **WHEN** se intenta guardar un cliente con domicilio pero sin contacto
- **THEN** la operación falla con una violación de integridad de datos

### Requirement: Dependientes eliminados con el cliente
Eliminar un cliente MUST eliminar su contacto y su domicilio, y reemplazar el contacto o el domicilio de un cliente MUST eliminar el anterior.

#### Scenario: Eliminación del cliente
- **WHEN** se elimina un cliente sin facturas, se sincroniza y se limpia el contexto
- **THEN** ni el cliente, ni su contacto, ni su domicilio existen en la base

#### Scenario: Reemplazo del domicilio
- **WHEN** a un cliente guardado se le asigna un domicilio nuevo y se sincroniza
- **THEN** el cliente releído tiene el domicilio nuevo y el anterior ya no existe en la base
