# Proposal

## Why

Con las cinco etapas entregadas (tags `etapa-1`..`etapa-5`), el docente indicó en clase, como condición para aprobar, que el CRUD de factura de venta debe incluir filtros por número, cliente e «informada a ARCA», paginación de la grilla, exploradores modales para elegir cliente y artículo de lista de precios, bloqueo de las facturas informadas y la impresión del PDF de una factura con su CAE y vencimiento (transcripción en `docs/consigna/requisitos-docente-2026-11-18.md`, change `C-07` de `CHANGES.md`). Este change cierra las brechas entre ese pedido y lo entregado, sin rehacer lo que ya cumple.

## What Changes

- **Filtros nuevos** en `GET /api/facturas` y `GET /api/facturas/reporte`: `numero` (exacto), `clienteId` e `informada` (`true`: con CAE, es decir EMITIDA o ANULADA; `false`: sin CAE, es decir BORRADOR). Se validan en la misma función `FiltroFacturas.desde`, de modo que listado y reporte no pueden divergir.
- **BREAKING — Listado paginado en el servidor**: `GET /api/facturas` acepta `pagina` (desde 0) y `tamanio` (predeterminado 10, máximo 100) y responde un sobre `{ items, pagina, tamanio, totalElementos, totalPaginas }` en lugar del arreglo de la etapa 3. Valores inválidos → `400`. El reporte ignora la paginación y exporta todo lo filtrado.
- **Búsqueda en catálogos**: parámetro opcional `q` (contiene, sin distinguir mayúsculas) en `/api/catalogos/clientes` y `/api/catalogos/lista-precio-articulos`, para los exploradores. Siguen siendo de solo lectura.
- **PDF de una factura**: `GET /api/facturas/{id}/pdf` devuelve el comprobante de una factura informada con número `0001-00000004`, cliente con CUIT o «Consumidor Final», renglones, totales, **CAE** y **fecha de vencimiento del CAE**, estado (ANULADA marcada) y la leyenda de comprobante simulado sin validez fiscal. Un BORRADOR → `409`; inexistente → `404`; glifo no soportado → `400`.
- **Detalle con CUIT del cliente**: `GET /api/facturas/{id}` agrega `clienteCuitCuil` (aditivo) para que el PDF y el formulario lo muestren sin otra consulta.
- **Facturas informadas bloqueadas**: ya implementado (`PUT`/`DELETE` sobre EMITIDA o ANULADA → `409`); se completan los escenarios faltantes (editar una ANULADA, borrar una EMITIDA) y se prueba desde el nuevo flujo del frontend.
- **Frontend**: filtros por número, cliente (elegido con el explorador, con botón para quitarlo) e «Informada a ARCA» (Todas / Informadas / No informadas); controles de paginación accesibles bajo la grilla; ventana modal exploradora (`<dialog>` nativo, accesible, sin framework) para elegir cliente en el formulario y en el filtro, y artículo de lista de precios en cada renglón (reemplaza los `<select>`); acción «Imprimir PDF» solo en filas EMITIDA y ANULADA, que no ofrecen editar ni eliminar.
- **Documentación**: `requests.http`, `docs/etapa-6.md` y `README.md` con el cambio de contrato, las decisiones, la evidencia y el uso de IA.

**Supuesto a confirmar con el docente**: la conexión con ARCA/AFIP **sigue simulada** (CAE ficticio de 14 dígitos). Una conexión real a WSFE exigiría CUIT, certificado digital y credenciales de homologación, y la consigna escrita la declara fuera de alcance. «CAI» se interpreta como el CAE que ya genera la aplicación.

## Capabilities

### New Capabilities
- `api-pdf-factura`: descarga por HTTP del PDF de una factura informada, con sus códigos de error.
- `comprobante-pdf`: contenido y formato del documento PDF de una factura (datos fiscales simulados, renglones, totales, CAE, vencimiento, leyenda).
- `frontend-paginacion`: controles de paginación de la grilla de facturas y su comportamiento accesible.
- `frontend-explorador-modal`: ventana modal exploradora con búsqueda para elegir cliente u opción lista–artículo.

### Modified Capabilities
- `api-listado-facturas`: respuesta paginada en sobre (BREAKING), parámetros `pagina`/`tamanio` y filtros `numero`, `clienteId`, `informada`.
- `proyeccion-reporte-facturas`: filtros nuevos y consulta de una página con conteo total sobre el mismo `WHERE`.
- `api-descarga-reporte`: el reporte acepta los filtros nuevos e ignora la paginación.
- `api-catalogos`: búsqueda opcional `q` en clientes y en artículos por lista de precios.
- `api-detalle-factura`: el detalle agrega `clienteCuitCuil`.
- `api-borradores-factura`: escenarios explícitos para editar una ANULADA y borrar una EMITIDA.
- `api-errores`: el listado vacío responde un sobre con `items` vacío en lugar de `[]`.
- `frontend-cliente-http`: listado paginado, filtros nuevos, búsqueda de catálogos y URL del PDF por factura.
- `frontend-consulta-facturas`: filtros nuevos, cantidad tomada del total del servidor, vuelta a la primera página y enlaces sin paginación.
- `frontend-formulario-factura`: cliente y opción lista–artículo elegidos con el explorador.
- `frontend-acciones-factura`: «Imprimir PDF» para EMITIDA y ANULADA; ninguna edición ni eliminación sobre facturas informadas.
- `frontend-estado-accesibilidad`: operación por teclado y etiquetas que incluyen el explorador y la paginación.

## Impact

- **Backend**: `FacturaRestController`, `CatalogoRestController`, `FacturaService`, `CatalogoService`, `FiltroFacturas`, `FacturaFiltrosDTO`, `FacturaReporteRepository(+Impl)`, nuevos `PaginaDTO` y PDF de comprobante en `report/`, `FacturaDetalladaDTO`, repositorios de cliente y lista–artículo (búsqueda). Sin dependencias ni versiones nuevas; sin cambios de esquema.
- **Contrato HTTP**: cambio incompatible en `GET /api/facturas` (sobre en lugar de arreglo); rutas nuevas `GET /api/facturas/{id}/pdf`; parámetros nuevos. Afecta `requests.http`, `docs/etapa-3.md` (referencia) y pruebas MockMvc del listado.
- **Frontend**: `index.html`, `styles.css`, `models/factura.dto.ts`, `services/FacturaService.ts`, `aplicacion.ts`, `ui/listado.ts`, `ui/formulario.ts`, nuevos `ui/paginacion.ts` y `ui/explorador.ts`; pruebas existentes de consulta, cliente HTTP, formulario, edición, acciones, accesibilidad e índice se actualizan.
- **Consigna**: amplía la consigna escrita (que excluía la paginación) por pedido posterior del docente; la regla dura de `CLAUDE.md` ya registra la excepción. El tag a evaluar queda a definir con el docente.
- **Gobernanza**: MEDIA, con tres puntos de control con el estudiante (contrato paginado y filtros, PDF por factura, explorador modal).
