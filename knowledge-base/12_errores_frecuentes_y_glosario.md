# Errores Frecuentes y Glosario

Archivo extra (no canónico). Mapea el Anexo A de la consigna a reglas y a la etapa donde conviene escribir la prueba.

## Errores frecuentes (Anexo A)

| Síntoma | Revisión | Regla | Etapa |
|---------|----------|-------|-------|
| El detalle no queda asociado a la factura | Actualizar ambos extremos; revisar lado propietario y `mappedBy` | RN-PE-07, 08 | 1 |
| Se elimina un catálogo al borrar un borrador | Revisar cascadas: catálogos compartidos | RN-PE-09 | 1 |
| Recursión al imprimir/comparar entidades | Excluir asociaciones bidireccionales de `toString/equals/hashCode`; sin `@Data` indiscriminado | RN-GE-03 | 2 |
| `LazyInitializationException` tras responder | Armar el DTO dentro de la transacción; sin OSIV | RN-CC-04 | 3/4 |
| Totales con diferencia de centavos | Orden de cálculo y HALF_UP a 2 decimales; no `Math.round` | RN-IM-04..07, 09 | 4 |
| Valor extremo se guarda con pérdida de precisión | Finitud y round-trip BigDecimal/double; rechazar toda la operación | RN-IM-09 | 4 |
| El total del reporte mezcla importes | Solo EMITIDA y por código de moneda | RN-RE-07 | 2 |
| Dos emisiones con el mismo número | Bloqueo por punto de venta + unicidad en BD | RN-NU-02 | 4 |
| Una edición modifica una factura recién emitida | Bloquear antes de verificar estado, misma transacción | RN-CC-01 | 4 |
| El frontend falla después de DELETE | No leer JSON de 204 | RN-FE-04 | 5 |
| Una búsqueda vieja reemplaza la actual | Descartar respuestas obsoletas; sincronizar tabla y enlaces | RN-FE-13 | 5 |
| Una denominación se interpreta como HTML | `textContent` y nodos | RN-FE-14 | 5 |
| TXT con filas/columnas inesperadas | Escapar `\`, TAB, CR, LF antes de unir las 9 columnas | RN-RE-09 | 2 |
| PDF muestra caracteres ausentes | Fuente incorporada y detección de glifos; error explícito | RN-RE-05 | 2 |
| Se pierde información al reiniciar | H2 en memoria es temporal; usar perfil PostgreSQL | RN-CF-02 | 0 |

Pistas adicionales (etapa 1): tabla intermedia inesperada → `mappedBy`; guardar cabecera no inserta detalles → cascada y asociación bidireccional; restricción de auditoría incumplida → usuario del contexto (`UsuarioActual`); contar sentencias SQL no reemplaza comprobar los datos persistidos.

## Glosario

| Término | Significado |
|---------|-------------|
| Borrador | Factura editable y eliminable, sin número ni CAE |
| Emitida | Con número y CAE simulado; se consulta o anula; no se modifica ni elimina |
| Anulada | Conservada para consulta, con fecha de anulación y baja lógica |
| CAE simulado | Cadena de 14 dígitos generada localmente, sin validez fiscal |
| Consumidor Final | Factura con cliente nulo |
| Bonificación | Descuento porcentual del renglón antes del IVA |
| Neto / IVA / Subtotal | Calculados por el backend; subtotal = neto + IVA redondeados; total = suma de subtotales |
| Moneda | Unidad del comprobante; los reportes separan totales por código, sin convertir |
| Agregado / raíz | Conjunto de entidades con ciclo de vida común gestionado por la raíz (`FacturaVenta`, `Cliente`) |
| Lado propietario | Entidad que contiene la clave foránea (el detalle) |
| Proyección DTO | `SELECT new` JPQL que construye DTOs directamente |
| OSIV | Open Session in View; aquí deshabilitado (`open-in-view=false`) |
| ProblemDetail | Formato de error HTTP (RFC 7807) de Spring |
| Bloqueo pesimista | `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) |
| PES / DOL | Códigos de moneda de ejemplo (se muestran como ARS / USD) |
| Kit de nivelación | Material opcional de la cátedra para continuar una etapa |
