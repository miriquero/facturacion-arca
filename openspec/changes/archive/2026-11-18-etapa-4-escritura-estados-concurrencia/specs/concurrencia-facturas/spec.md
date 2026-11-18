# Spec Delta

## Purpose

Garantiza que las operaciones de escritura sobre facturas sean atómicas y que operaciones simultáneas no dupliquen números ni modifiquen una factura cuyo estado cambió, y exige declarar en qué motor de base de datos se comprobó cada resultado.

## ADDED Requirements

### Requirement: Operación atómica
Cada alta, edición, cambio de estado o eliminación MUST ejecutarse en una única transacción: si falla cualquier validación de negocio o la persistencia, MUST NOT quedar ningún cambio parcial de esa operación.

#### Scenario: Falla al emitir después de numerar
- **WHEN** al emitir un borrador el generador de CAE falla después de asignado el número
- **THEN** la petición falla, la factura sigue en BORRADOR con número y CAE nulos, y la próxima emisión exitosa de ese punto de venta recibe el número que le hubiera correspondido

#### Scenario: Falla en el segundo renglón de una edición
- **WHEN** un borrador con dos renglones recibe un `PUT` cuyo segundo renglón referencia una lista–artículo inexistente
- **THEN** la respuesta es `404` y el borrador releído conserva sus dos renglones originales

### Requirement: Bloqueo de la factura antes de modificarla
Editar, cambiar de estado o eliminar una factura MUST obtener un bloqueo de escritura sobre su cabecera antes de verificar su estado, y mantenerlo hasta terminar la transacción, de modo que la verificación y la modificación vean el mismo estado.

#### Scenario: Edición que espera a una emisión concurrente
- **WHEN** una transacción bloquea un borrador y, mientras lo retiene, otro hilo envía un `PUT` válido a esa factura; luego la primera transacción la emite y confirma
- **THEN** el `PUT` no termina mientras la primera transacción retiene el bloqueo, y al liberarse responde `409` sin modificar la factura emitida

#### Scenario: Eliminación que espera a una emisión concurrente
- **WHEN** el mismo escenario se repite con un `DELETE` en lugar del `PUT`
- **THEN** el `DELETE` responde `409` y la factura emitida sigue existiendo

#### Scenario: Dos emisiones de la misma factura
- **WHEN** dos hilos piden simultáneamente emitir el mismo borrador
- **THEN** exactamente uno obtiene `200` y el otro `409`, y la factura tiene un único número y un único CAE

### Requirement: Bloqueo del punto de venta al numerar
Al emitir, el sistema MUST bloquear para escritura el punto de venta de la factura antes de consultar el máximo número y asignar el siguiente, dentro de la misma transacción.

#### Scenario: Emisiones simultáneas del mismo punto de venta
- **WHEN** ocho hilos reales emiten a la vez ocho borradores diferentes del mismo punto de venta sin facturas emitidas
- **THEN** todas las emisiones terminan bien y los números asignados son exactamente 1 a 8, sin repeticiones ni huecos

#### Scenario: Emisiones simultáneas de puntos de venta distintos
- **WHEN** varios hilos emiten a la vez borradores de dos puntos de venta distintos
- **THEN** cada punto de venta queda numerado desde 1 sin repeticiones

### Requirement: Unicidad como última defensa
La base MUST mantener la restricción única (punto de venta, número), de modo que un número duplicado sea rechazado aunque fallara el bloqueo.

#### Scenario: Duplicado forzado
- **WHEN** se intenta guardar directamente una segunda factura con el mismo punto de venta y número que una emitida
- **THEN** la base rechaza la operación con una violación de integridad

### Requirement: Esperas acotadas en las pruebas de concurrencia
Las pruebas de concurrencia MUST usar hilos reales, arrancarlos juntos y esperar cada resultado con un tiempo máximo, de modo que un bloqueo no liberado haga fallar la prueba en lugar de colgar la ejecución.

#### Scenario: Bloqueo no liberado
- **WHEN** un hilo de la prueba no termina dentro del tiempo máximo
- **THEN** la prueba falla informando qué hilo no terminó

### Requirement: Evidencia distinguida por motor
La suite de concurrencia MUST correr sobre H2 en la ejecución normal de pruebas y MUST poder ejecutarse a demanda contra PostgreSQL sin que la ejecución normal dependa de un servidor externo; cada corrida MUST verificar e informar el motor y la versión efectivamente usados.

#### Scenario: Ejecución normal
- **WHEN** se ejecuta la batería de pruebas habitual sin servidor PostgreSQL disponible
- **THEN** la suite de concurrencia corre sobre H2, informa `H2` como motor y la batería no intenta conectarse a PostgreSQL

#### Scenario: Ejecución contra PostgreSQL
- **WHEN** con el PostgreSQL 16 local en el puerto 5433 se ejecuta la corrida de concurrencia a demanda
- **THEN** las mismas pruebas corren contra PostgreSQL, informan `PostgreSQL` y su versión, y su reporte queda separado del de H2

#### Scenario: Motor equivocado
- **WHEN** se pide la corrida contra PostgreSQL pero la conexión resultante es a otro motor
- **THEN** la verificación de motor falla
