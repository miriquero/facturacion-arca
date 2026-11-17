## MODIFIED Requirements

### Requirement: Representación textual sin recursión ni clave
La representación textual de las entidades MUST NOT recorrer asociaciones (evitando recursión infinita y carga diferida) y la del usuario MUST NOT incluir su clave. La representación MUST incluir el identificador heredado y MUST NOT incluir los usuarios de auditoría heredados. La igualdad y el hash de las entidades MUST basarse en la identidad del objeto y MUST NOT recorrer asociaciones.

#### Scenario: Factura con detalles
- **WHEN** se obtiene la representación textual de una factura que tiene dos detalles enlazados en ambos sentidos
- **THEN** se obtiene sin error y no incluye los detalles

#### Scenario: Detalle enlazado a su factura
- **WHEN** se obtiene la representación textual de un detalle cuya factura lo contiene en su colección de detalles
- **THEN** se obtiene sin error y no incluye la factura

#### Scenario: Usuario
- **WHEN** se obtiene la representación textual de un usuario con clave
- **THEN** no contiene el valor de la clave

#### Scenario: Identificador heredado visible
- **WHEN** se obtiene la representación textual de una factura persistida con identificador 42
- **THEN** contiene el identificador 42

#### Scenario: Usuarios de auditoría excluidos
- **WHEN** se obtiene la representación textual de una entidad auditable con usuario de carga y de modificación asignados
- **THEN** no contiene esos usuarios

#### Scenario: Igualdad por identidad sin recursión
- **WHEN** se comparan y se calcula el hash de dos facturas distintas con los mismos datos y detalles enlazados en ambos sentidos
- **THEN** la comparación termina sin error, las facturas no son iguales entre sí y cada una es igual a sí misma

#### Scenario: Ninguna entidad redefine la igualdad
- **WHEN** se inspeccionan las 14 entidades del modelo
- **THEN** ninguna declara su propia igualdad ni su propio hash
