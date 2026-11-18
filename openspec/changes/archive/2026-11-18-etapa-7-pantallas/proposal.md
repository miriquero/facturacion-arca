# Proposal

## Why

Con las seis etapas entregadas (tags `etapa-1`..`etapa-6`; 620 pruebas de backend y 244 de frontend en verde), el frontend es **una sola página** que mezcla filtros, grilla, paginación, formulario y acciones. El estudiante pidió dividirla en **tres pantallas**: listado, alta y edición, y detalle de una factura (change `C-08` de `CHANGES.md`). Separar las tareas reduce la carga cognitiva, da a cada factura una dirección propia que se puede abrir y compartir, y permite ver una factura completa (renglones, importes, CAE y vencimiento) que hoy solo se ve en el PDF.

## What Changes

- **Navegación sin framework**: un enrutador mínimo basado en el fragmento de la URL (`#/` listado, `#/nueva` alta, `#/factura/{id}` detalle, `#/factura/{id}/editar` edición). Funcionan Atrás y Adelante del navegador y los enlaces directos (recargar conserva la pantalla). Una ruta desconocida muestra el listado con un mensaje explicativo.
- **Una pantalla visible a la vez**: las tres pantallas son secciones del mismo `index.html`; las ocultas llevan `hidden` (fuera del orden de tabulación y del árbol de accesibilidad). Cada pantalla tiene su propio `h1`; al navegar el foco va a ese `h1`, cambia `document.title` y una barra de navegación (`nav`) ofrece «Facturas» y «Nueva factura» con `aria-current="page"`. La región de estado sigue siendo **una sola**, fuera de las pantallas, y el mensaje de éxito de guardar, emitir, anular o eliminar **sobrevive** a la navegación que dispara.
- **Pantalla de listado**: filtros, grilla, paginación y descargas como hoy; cada fila ofrece «Ver» (enlace al detalle) y, si la factura está informada, «Imprimir PDF». **Las acciones de editar, emitir, anular y eliminar salen de la fila y pasan al detalle.** Al volver al listado se conservan filtros y página, y se vuelve a pedir esa página para reflejar los cambios.
- **Pantalla de alta y edición**: el formulario actual con los exploradores, la validación y el bloqueo de doble envío. La edición carga el borrador por id desde la ruta; una factura informada no se edita (vuelve al detalle con un error) y una inexistente vuelve al listado con el error. Al guardar se navega al detalle con el mensaje de éxito; cancelar vuelve sin enviar nada.
- **Pantalla de detalle (nueva)**: cabecera (número punto-número, fecha, estado), cliente (denominación y CUIT, o Consumidor Final), condición de IVA, punto de venta, moneda, tabla de renglones, importes (total, cobrado, saldo), CAE, vencimiento del CAE, resultado, observaciones y fecha de anulación; acciones según el estado (BORRADOR: Editar, Emitir, Eliminar; EMITIDA: Anular, Imprimir PDF; ANULADA: Imprimir PDF) y «Volver al listado». Una factura inexistente muestra el `404` de la API en la región de estado, no una pantalla en blanco.
- **Reorganización del código**: `aplicacion.ts` se divide en enrutador, un controlador por pantalla y un armado común (región de estado, explorador, servicio). Se conservan todas las reglas de las etapas 5 y 6: `strict`, `textContent`, `ControlSolicitudes` por carga, `ApiError`, bloqueo con `finally`, cancelar sin enviar, importes por código de moneda y fechas sin `new Date`.
- **Pruebas existentes que cambian** (honestamente, en `design.md` D10): las de acciones de fila pasan a la pantalla de detalle, las de edición se disparan por ruta, el alta exitosa navega al detalle en lugar de recargar el listado y «una sola `h1`» pasa a «una `h1` por pantalla».
- **Documentación**: `docs/etapa-7.md` (decisiones, tabla de rutas, pruebas cambiadas, evidencia, límites, uso de IA y checklist de accesibilidad para el estudiante) y `README.md` (pantallas y rutas).
- **Sin cambios** de backend, de contrato HTTP, de dependencias ni de configuración de compilación.

## Capabilities

### New Capabilities
- `frontend-navegacion-pantallas`: rutas por fragmento, historial del navegador, enlaces directos, ruta desconocida, barra de navegación, una pantalla visible a la vez, `h1` y foco por pantalla, título del documento y mensajes que sobreviven a la navegación.
- `frontend-pantalla-detalle`: contenido de la pantalla de detalle de una factura (cabecera, cliente, renglones, importes, CAE y vencimiento, anulación), factura inexistente y descarte de cargas obsoletas.

### Modified Capabilities
- `frontend-acciones-factura`: las acciones de emitir, anular, eliminar y editar viven en el detalle; la fila ofrece «Ver» e «Imprimir PDF»; después de emitir o anular se recarga el detalle y después de eliminar se vuelve al listado; la recarga del listado al volver conserva la página.
- `frontend-consulta-facturas`: carga inicial según la ruta de entrada y estado de la consulta conservado al volver al listado.
- `frontend-formulario-factura`: el alta y la edición tienen pantalla propia; guardar navega al detalle; la edición se carga por ruta; cancelar vuelve sin enviar; cargas obsoletas al salir de la pantalla.
- `frontend-estado-accesibilidad`: región de estado única compartida por las pantallas y operación por teclado que incluye la navegación.

## Impact

- **Frontend**: `index.html` (barra de navegación y tres secciones), `styles.css`, `src/aplicacion.ts` (armado), nuevos `src/navegacion/` (rutas y enrutador) y `src/pantallas/` (listado, detalle, formulario), nuevo `src/ui/detalle.ts`, `src/ui/listado.ts` (fila con «Ver»), `src/ui/formato.ts` (número punto-número), `src/app.ts` (inyecta `window`), `soporte-pruebas/soporte.mjs` y pruebas en `frontend/test/`.
- **Backend y contrato HTTP**: sin cambios (se usan `GET /api/facturas`, `GET /api/facturas/{id}`, `PATCH /api/facturas/{id}/estado`, `DELETE /api/facturas/{id}`, `GET /api/facturas/{id}/pdf` y los catálogos tal como están). Las 620 pruebas de backend deben seguir iguales.
- **Dependencias y configuración**: ninguna nueva; `package.json`, `package-lock.json` y `tsconfig.json` no cambian.
- **Documentación**: `docs/etapa-7.md` (nuevo) y `README.md`.
