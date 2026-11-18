## MODIFIED Requirements

### Requirement: Mismos filtros que el listado
El reporte MUST aceptar `fechaDesde`, `fechaHasta`, `estado`, `montoMinimo`, `numero`, `clienteId` e `informada` con la misma normalización, validación y errores que `GET /api/facturas`, y MUST contener exactamente todas las facturas que el listado devuelve con esos filtros sumando todas sus páginas, en el mismo orden. Los parámetros `pagina` y `tamanio` MUST ignorarse en el reporte: siempre se exporta todo lo filtrado.

#### Scenario: Archivo filtrado igual al listado
- **WHEN** con facturas en los tres estados se pide `GET /api/facturas?estado=EMITIDA` y `GET /api/facturas/reporte?formato=txt&estado=EMITIDA`
- **THEN** las filas del TXT corresponden, en el mismo orden, a los números, fechas, clientes y totales de los `items` del listado, y no aparece ninguna BORRADOR ni ANULADA

#### Scenario: Filtros nuevos en el reporte
- **WHEN** con facturas informadas y no informadas de dos clientes se pide `GET /api/facturas/reporte?formato=txt&clienteId=1&informada=true`
- **THEN** el TXT tiene solo las facturas EMITIDA y ANULADA del cliente 1, en el orden del listado

#### Scenario: Paginación ignorada
- **WHEN** con 12 facturas se pide `GET /api/facturas/reporte?formato=txt&pagina=1&tamanio=5`
- **THEN** el TXT tiene el encabezado y las 12 facturas

#### Scenario: Reporte filtrado sin coincidencias
- **WHEN** se pide `GET /api/facturas/reporte?formato=txt&montoMinimo=999999999`
- **THEN** la respuesta es `200` con solo la línea de encabezados

#### Scenario: Filtro inválido en el reporte
- **WHEN** se pide `GET /api/facturas/reporte?formato=pdf&estado=PAGADA` y luego `GET /api/facturas/reporte?formato=pdf&informada=si`
- **THEN** ambas respuestas son `400` igual que en el listado y no se genera archivo
