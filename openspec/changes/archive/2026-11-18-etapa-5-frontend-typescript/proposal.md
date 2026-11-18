# Proposal

## Why

Las etapas 3 y 4 dejaron la API completa (lectura, catálogos, reportes, alta, edición, cambio de estado y eliminación), pero el frontend sigue siendo el esqueleto de la etapa 0: una página que solo muestra la URL base. La Etapa 5 de la consigna (semanas 9 y 10, 20 % de la nota de etapas) exige una interfaz web TypeScript sin framework que opere el circuito completo crear–editar–emitir–anular–eliminar y que se mantenga coherente ante errores y respuestas asíncronas (respuestas obsoletas, doble envío, 204 sin cuerpo, errores no JSON). Es el último change del camino crítico y lo que se demuestra en la defensa.

## What Changes

- **Modelos del contrato HTTP** (`src/models/factura.dto.ts`): interfaces estrictas que reflejan los DTO del backend (`FacturaReporteDTO`, `FacturaDetalladaDTO` y sus renglones, `FacturaRequestDTO`, `FacturaDetalleRequestDTO`, `CambioEstadoRequestDTO`, `FacturaFiltrosDTO`, `OpcionDTO`, `ProblemDetail` con `errores`) y el tipo `EstadoFactura`.
- **Cliente HTTP único** (`src/services/FacturaService.ts` + `ApiError`): listado con filtros, detalle, cinco catálogos, `POST`, `PUT`, `PATCH /estado`, `DELETE` y construcción de URLs de reporte (`pdf`, `xlsx`, `txt`); base tomada de `<meta name="api-base-url">`; `X-Usuario: sistema` en todas las peticiones; `Content-Type: application/json` solo con cuerpo; `204` resuelto sin leer JSON; todo no-2xx convertido en `ApiError` con estado, `ProblemDetail` y errores por campo; cuerpo no JSON o falla de red → mensaje de respaldo comprensible. `fetch` inyectable para probarlo sin red.
- **Consulta**: filtros por fechas, estado y monto mínimo (≥ 0 en el formulario), tabla de resultados, cantidad de coincidencias, mensaje sin datos, importes con la moneda de cada factura (`PES` → ARS, `DOL` → USD, otro código visible tal cual, sin convertir), fechas sin corrimiento de zona horaria, enlaces de descarga PDF y Excel con los filtros de la consulta vigente (TXT solo por URL del servicio).
- **Formulario de factura**: cabecera con catálogos en selectores (cliente opcional = «Consumidor Final»), renglones que se agregan y quitan, opción lista–artículo con su precio de catálogo, total neto estimado sin IVA, validación de cantidad, bonificación, descripción ≤ 255 y observaciones ≤ 500; el request no envía precios ni importes; crear (`POST`), cargar y editar un borrador (`PUT`), cancelar la edición; conservación de los valores y errores por campo visibles cuando falla el guardado.
- **Acciones según estado**: editar, emitir y eliminar solo en BORRADOR (eliminar con confirmación); anular solo en EMITIDA con motivo opcional; cancelar cualquiera de los diálogos no envía ninguna petición.
- **Consistencia asíncrona**: bloqueo de doble envío durante `POST`/`PUT` (controles y acciones incompatibles deshabilitados, liberados en éxito y en error); descarte de respuestas obsoletas de búsqueda y de carga de edición mediante identificadores de solicitud; tabla, contador y enlaces de exportación actualizados juntos.
- **Accesibilidad y seguridad de la interfaz**: datos externos insertados solo con nodos y `textContent`; región de estado persistente `role="status"` + `aria-live="polite"`; `aria-busy` durante el guardado; éxito que se limpia a los 6 s sin retirar la región; errores visibles hasta la siguiente acción; controles con etiqueta, errores con `aria-invalid` y `aria-describedby`; operación completa por teclado; foco visible y contraste AA.
- **Pruebas** con `node --test` + jsdom (sin dependencias nuevas): cliente HTTP con `fetch` simulado (204, errores JSON y no JSON, falla de red, encabezados, URLs) e interacción del DOM con promesas controladas (texto que parece HTML, validaciones, doble envío, conservación tras error, respuestas en orden inverso, cancelación de edición y del diálogo de anulación, limpieza a los 6 s).
- **Documentación y evidencia**: `docs/etapa-5.md` (decisiones, control asíncrono, comprobaciones de accesibilidad, evidencia del circuito y de la eliminación de un borrador en navegador real, descargas de ejemplo, límites de la simulación, semilla reutilizada, referencia del docente, uso de IA) y README (instalar, compilar, probar y levantar frontend y backend juntos).
- **Semilla (DD-13)**: `api-facturas/frontend` (`app.ts`, `FacturaService.ts`, `models/factura.dto.ts`, `index.html`), adaptada detrás de pruebas en rojo; inventario en `design.md`.

## Capabilities

### New Capabilities
- `frontend-cliente-http`: cliente HTTP tipado que centraliza las llamadas a la API, sus encabezados, la construcción de URLs de reporte y la traducción de respuestas no exitosas a `ApiError`.
- `frontend-consulta-facturas`: pantalla de consulta con catálogos, filtros, tabla, contador, formato de importes y fechas, enlaces de descarga y descarte de búsquedas obsoletas.
- `frontend-formulario-factura`: alta y edición de borradores con cabecera y renglones, validaciones del navegador, estimación neta, conservación ante error, bloqueo de doble envío y descarte de cargas de edición obsoletas.
- `frontend-acciones-factura`: emisión, anulación y eliminación según el estado, con confirmación y cancelación sin petición.
- `frontend-estado-accesibilidad`: región de estado y mensajes, inserción segura de texto externo, etiquetas, errores accesibles y operación por teclado.

### Modified Capabilities
<!-- Ninguna: `frontend-esqueleto` sigue vigente tal cual (meta `api-base-url`, módulo `type="module"`, URL base visible como texto, `npm test` con jsdom). -->

## Impact

- **Código nuevo** (`frontend/src`): `models/factura.dto.ts`, `services/ApiError.ts`, `services/FacturaService.ts`, `ui/*.ts` (formato, DOM seguro, región de estado, control de solicitudes, validación, listado, formulario, acciones), `aplicacion.ts` (composición testeable); pruebas en `frontend/test/*.test.mjs` y soporte de pruebas fuera de los patrones de `node --test`.
- **Código modificado**: `frontend/index.html` (estructura completa de la pantalla, conservando el meta y `#estado-api`), `frontend/styles.css` (foco visible, contraste, estados), `frontend/src/app.ts` (arranque); `frontend/src/config.ts` sin cambios.
- **Dependencias**: ninguna nueva; `package.json`, `tsconfig.json` (`strict`, ES2020) y el lockfile no cambian.
- **API**: ninguna modificación; se consume el contrato de las etapas 3 y 4 (incluido CORS para `localhost:5173`).
- **Dependencia de change**: requiere `C-05` (`etapa-4-escritura-estados-concurrencia`), **todavía no archivado** al proponer este change; el contrato de escritura se toma de su delta (`openspec/changes/etapa-4-escritura-estados-concurrencia/specs/`). Las tareas de recorrido manual necesitan el backend de la etapa 4 corriendo.
- **Documentación**: `docs/etapa-5.md` nuevo, `README.md` actualizado.
- **Governance**: MEDIO (UI con estado y control de carreras; sin lógica monetaria en el cliente salvo la estimación neta informativa).
- **Fuera de alcance**: frameworks y bundlers, login, paginación, CRUD de catálogos, conversión de monedas, cálculo de IVA en el cliente, botón de TXT, cambios al backend y comandos Git que cambien estado.
