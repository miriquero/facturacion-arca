# Etapa 7 - Pantallas con rutas (change `etapa-7-pantallas`)

Cambio solo de frontend: la aplicación pasa de una página única a tres pantallas (listado, alta y edición, detalle) con dirección propia, historial del navegador y foco accesible. El detalle normativo está en `openspec/changes/etapa-7-pantallas/` (proposal, design, specs y tasks). No cambia el backend, el contrato HTTP, `package.json` ni `tsconfig.json`.

## Origen del pedido y supuestos

Pedido del estudiante tras la etapa 6: separar la aplicación en pantallas con dirección propia (listado, alta y edición, detalle) para poder compartir enlaces, usar Atrás y Adelante y recargar sin perder el lugar. Sin tag oficial confirmado (lo define el estudiante al cerrar). Supuestos: el backend y el contrato HTTP no cambian; las acciones sobre una factura (editar, emitir, anular, eliminar) viven en el detalle y la fila del listado solo navega; al volver al listado se vuelve a pedir la misma página con los mismos filtros.

## Decisiones

- **Rutas por fragmento (`#/…`), sin History API**: `http-server` sirve siempre `index.html`, así que una recarga en `#/factura/5` funciona sin configuración y los enlaces son `<a href="#/…">` nativos (teclado, otra pestaña, lector de pantalla).
- **Pantallas como secciones del mismo HTML con `hidden`**: saca los controles del orden de Tab y del árbol de accesibilidad sin `inert` ni ARIA extra, y los controladores (listado, paginación, formulario, explorador) se enlazan una sola vez, así que los filtros conservan su valor.
- **Un controlador por pantalla** (`src/pantallas/`) con la interfaz `Pantalla { seccion, titulo, tituloDocumento, entrar, salir }`; cada uno con su `ControlSolicitudes`, y `salir()` descarta las respuestas tardías. `aplicacion.ts` queda como cáscara (111 líneas): mostrar una sección, `aria-current`, `document.title`, foco, mensaje pendiente y cierre del explorador compartido.
- **Mensaje que sobrevive a la navegación**: `ir(ruta, { mensaje })` entrega el mensaje junto con el cambio de pantalla; la navegación del usuario limpia la región de estado única.
- **Estado del listado en memoria**: filtros aplicados, cliente del filtro, página y tamaño se conservan; al volver se pide de nuevo esa página (refleja emisiones y eliminaciones hechas en el detalle).
- **Edición imposible reemplaza el historial** (`location.replace`) para que Atrás no vuelva a ella.
- Reglas de las etapas 5 y 6 sin cambios: TypeScript estricto, `textContent` únicamente, `ApiError`, control de respuestas obsoletas por carga, bloqueo con `finally`, cancelar no envía nada, importes por código de moneda, fechas sin `new Date`.
- **Desvío de orden de tareas**: se hizo 4.2 y 4.3, luego el grupo 5 (detalle), luego el 6 (formulario) y al final 4.1 y 4.4. Quitar los botones de la fila antes rompía las pruebas de emitir, anular, eliminar y editar hasta que existieran en el detalle y en la ruta de edición; así la suite estuvo verde en cada cierre. Las tareas 6.2, 6.3 y 6.4 se cerraron juntas con una sola corrida porque «Edición exitosa» cruza las tres.

## Rutas

Enrutador por fragmento (`#/…`), sin `pushState`: `http-server` sirve siempre `index.html`, así que una recarga en `#/factura/5` funciona sin configuración. `interpretarRuta(hash)` (puro, en `src/navegacion/rutas.ts`) acepta el id solo con `^[1-9]\d*$` y dentro de `Number.MAX_SAFE_INTEGER`; todo lo demás es ruta desconocida.

| Fragmento | Pantalla | `document.title` | Enlace con `aria-current` |
|-----------|----------|------------------|---------------------------|
| `` (vacío), `#/` | Listado | `Facturas — Facturación ARCA` | Facturas |
| `#/nueva` | Alta | `Nueva factura — Facturación ARCA` | Nueva factura |
| `#/factura/{id}` | Detalle | `Factura {id} — Facturación ARCA` | ninguno |
| `#/factura/{id}/editar` | Edición (borrador) | `Editar borrador {id} — Facturación ARCA` | ninguno |
| cualquier otro | Listado con error en la región de estado | el del listado | Facturas |

## Pantallas

- **Listado (`#/`)**: filtros, tabla y paginación de la etapa 6. Cada fila ofrece «Ver» (a `#/factura/{id}`) e «Imprimir PDF» si está informada; ningún botón. La primera vez pide la página 0 sin filtros; después, los filtros aplicados y la página vigente (si la página quedó fuera de rango por una eliminación, pide la última existente, una sola vez).
- **Detalle (`#/factura/{id}`)**: `h1` «Factura 0001-00000004» o «Borrador 6»; datos en pares etiqueta/valor (número, fecha `dd/mm/aaaa`, estado, cliente y CUIT o «Consumidor Final», condición de IVA, punto de venta, moneda), tabla de renglones con `caption` y `th scope="col"`, importes, CAE, vencimiento, resultado, observaciones y fecha de anulación (nulos como «—»). `aria-busy` durante la carga. Acciones por estado: BORRADOR Editar (enlace), Emitir, Eliminar; EMITIDA Anular e Imprimir PDF; ANULADA Imprimir PDF; siempre «Volver al listado». Emitir y anular anuncian y recargan el detalle con el anuncio visible; eliminar navega al listado con el anuncio; una acción en curso deshabilita las demás; el foco vuelve al botón (si sigue) o al `h1` (si se reemplazó).
- **Formulario (`#/nueva`, `#/factura/{id}/editar`)**: la alta empieza vacía en cada visita; la edición pide el borrador (estado distinto de BORRADOR: detalle con error; falla de carga: listado con error; ambos reemplazan el historial). «Cancelar» existe en ambos modos y no envía nada (edición al detalle, alta al listado). Guardar conserva la validación, el bloqueo con `finally` y los errores por campo; ante el éxito navega al detalle con el anuncio, salvo que el usuario ya haya salido: entonces solo anuncia.

## Accesibilidad

Revisión por principio (WCAG 2.1 AA, criterios de `accessibility-review`) de lo que cambió con las pantallas. Lo verificado automáticamente en jsdom está marcado con la prueba; lo demás queda en el checklist manual.

| Principio | Criterio | Qué se hizo | Verificación |
|-----------|----------|-------------|--------------|
| Perceptible | 1.3.1 Información y relaciones | `header` con `nav` nombrada «Principal», un `main`, `h1` por pantalla, detalle como listas de definición (`dt`/`dd`) y tabla con `caption` y `th scope="col"` | `index.test.mjs`, `detalle.test.mjs` |
| Perceptible | 1.4.1 / 1.4.11 Uso del color | La página actual de la barra lleva `aria-current="page"`, negrita y subrayado grueso, no solo color | `accesibilidad.test.mjs` (estilos) |
| Operable | 2.1.1 Teclado | Solo `a href`, `button`, `input`, `select`, `textarea` nativos, sin `tabindex` positivo; las pantallas ocultas llevan `hidden` y no entran en el orden de Tab | `accesibilidad.test.mjs` (por pantalla) |
| Operable | 2.4.2 Título de página | `document.title` nombra la pantalla y termina en `Facturación ARCA` | `navegacion.test.mjs` |
| Operable | 2.4.3 Orden del foco | Al navegar el foco va al `h1` de la pantalla nueva; el arranque no lo mueve; al salir con el explorador abierto se cierra | `navegacion.test.mjs`, `explorador.test.mjs`; el orden real de Tab queda en el recorrido manual |
| Operable | 2.5.5 Tamaño del objetivo | Enlaces de la barra, «Ver», «Imprimir PDF» y acciones del detalle con al menos 2,75 rem | `accesibilidad.test.mjs` (estilos) |
| Robusto | 4.1.2 Nombre, rol y valor | «Ver factura 6», «Emitir borrador 6», «Imprimir PDF factura 1» incluyen la acción y el id (texto visualmente oculto); `aria-busy` en el detalle y el formulario mientras cargan | `acciones.test.mjs`, `pantalla-detalle.test.mjs` |
| Robusto | 4.1.3 Mensajes de estado | Una sola región `role="status"` fuera de las pantallas, mismo nodo siempre; el mensaje de una operación se escribe después de mover el foco | `accesibilidad.test.mjs`, `navegacion.test.mjs` |

### Checklist para el estudiante

Resultado (2026-11-18): el estudiante cerró la etapa **sin hacer esta revisión manual**. Ninguno de los ítems de la lista fue probado por él, ni el recorrido solo con teclado ni el lector de pantalla. Lo único verificado es lo que este informe declara como automático (jsdom y contrastes calculados desde el CSS) y lo que la IA comprobó en el navegador integrado durante el recorrido: clics y teclas reales en la navegación, foco al título de cada pantalla, explorador con `showModal()` real, Atrás y Adelante reales, y el foco después de emitir, que se corrigió. Hallazgos del estudiante: ninguno, por no haberla hecho. La lista de ítems:

- [ ] Tab y Mayús+Tab recorren listado, detalle y formulario en orden visual y el foco se ve en cada control.
- [ ] Al pasar de pantalla con un enlace el lector anuncia el `h1` nuevo y después el mensaje de la región de estado sin pisarse.
- [ ] Atrás y Adelante cambian de pantalla y conservan filtros y página del listado.
- [ ] Escape cierra el explorador y el foco vuelve al botón que lo abrió.
- [ ] Con zoom al 200 % el detalle y la barra no pierden contenido.

## Pruebas existentes que cambiaron

- Mecánico: todas las llamadas a `iniciarAplicacion` agregan `ventana: pagina.ventana`; `montarPagina(url)` acepta la dirección inicial; `formulario.test.mjs` arranca en `#/nueva`.
- `index.test.mjs`: «una sola h1 y un landmark main» pasó a «un h1 por pantalla, exactamente uno visible, y un main» (el pedido cambia la regla).
- `acciones.test.mjs` (reescrito): las pruebas de emitir, anular y eliminar por botón de fila se reemplazaron por las equivalentes del detalle en `pantalla-detalle.test.mjs`; «Tres estados» y «Nombre accesible» pasaron a «Fila con solo navegación» y al enlace «Ver factura 6»; las tres de recarga de página se disparan ahora al volver al listado desde el detalle. **Eliminada**: «Una acción en curso bloquea también el guardado del formulario» (acciones y formulario ya no conviven en una pantalla). **Eliminada**: «El enlace de PDF sigue disponible mientras hay una acción en curso» (no hay acciones en la fila).
- `edicion.test.mjs`: `editar(id)` navega a `#/factura/{id}/editar`; «Ya fue emitido», «Edición exitosa», «Cancelar sin guardar», «Cancelación antes de la respuesta» y «Error al cargar el detalle» esperan la navegación de los escenarios modificados; `cancelar-edicion hidden === true` pasó a `false` (Cancelar existe en ambos modos).
- `formulario.test.mjs`: «Alta exitosa» espera el detalle 15 en lugar de la recarga del listado; «Bloqueo durante el alta» pierde las aserciones sobre botones de fila; «Consumidor Final» vuelve a `#/nueva` entre las dos altas.
- `accesibilidad.test.mjs`: el test de orden de tabulación se dividió en tres (listado, formulario, detalle); «los botones de acción nombran…» pasó a los enlaces de fila; la regla de estilos `td .botones a.enlace-pdf` pasó a `td .botones a`.
- `paginacion.test.mjs`: `primerId()` lee el id del enlace «Ver» (las filas ya no tienen botones). `consulta.test.mjs`: solo se agregaron pruebas.

## Pruebas y mutaciones

Costuras probadas por su interfaz pública: `interpretarRuta`/`hrefDe` (puras), `Enrutador` con la ventana de jsdom, `ui/detalle.ts` con un `FacturaDetalladaDTO` literal y la aplicación completa en jsdom entrando por URL (`arrancarApp`) y navegando con `location.hash` y clics en enlaces. Esperados literales tomados de `specs/`. Se pasó de 244 a **317** pruebas.

Pruebas que pasaron al escribirse (caracterización), cada una validada con una mutación temporal que se revirtió:

| Prueba | Mutación temporal | Resultado |
|--------|-------------------|-----------|
| Rutas desconocidas e id gigante | quitar `Number.isSafeInteger`; aceptar `0` como primer dígito | fallan; revertidas |
| Región y explorador fuera de las secciones | mover `#estado` dentro de una sección | falla; revertida |
| Entrada sin fragmento | no pedir el listado en el arranque | falla; revertida |
| Detalle sin enlace actual | marcar «Facturas» también en el detalle | falla; revertida |
| Un h1 visible / foco inicial | mostrar todas las secciones; mover el foco también en el arranque | fallan; revertidas |
| Mensaje pendiente del enrutador | no borrar el mensaje pendiente; quitar la rama del mismo fragmento | fallan; revertidas |
| Volver con filtros y página | pedir siempre `{}`; no usar los filtros aplicados | fallan; revertidas |
| Detalle y error tardíos | quitar la invalidación en `salir()` | fallan; revertida |
| Informadas sin edición, borrador sin PDF | agregar Eliminar o PDF donde no corresponde | fallan; revertidas |
| Texto del servidor sin interpretar | `innerHTML` en la fila del detalle | falla; revertida |
| Guardado fuera de la pantalla | no marcar la pantalla como inactiva en `salir()` | falla; revertida |
| Liberación tras error del guardado | quitar el `bloquear(false)` del `finally` | fallan 5; revertida |
| Reintento de la última página vacía | quitar el reintento / quitar la guarda de bucle | fallan; revertidas |
| Región única, pantallas ocultas, orden de Tab | `hidden = false` en todas las secciones; mover `#estado` | fallan; revertidas |
| Foco tras emitir/anular | quitar el `titulo.focus()` | fallan 2; revertida |

Declaración honesta: en 7.2 el RED de `Explorador.cerrar()` se vio por tiempo agotado de la prueba (la promesa nunca se resolvía), no por una aserción; en 2.4 una de las rojas también fue por el tiempo de espera de `hashchange`.

### Prueba de aprendizaje de jsdom

Tarea 2.3 (`test/enrutador.test.mjs`). jsdom 25 dispara `hashchange`, siempre de forma asincrónica, en los cuatro casos: asignar `location.hash`, `location.replace('#/…')`, `history.back()` y clic en `<a href="#/…">`. No hace falta el respaldo de `design.md` (Risks): `Enrutador.ir` solo procesa directamente cuando el fragmento de destino ya es el actual (el navegador no dispara `hashchange` en ese caso). Hallazgo: `esperarTareas()` (tres `setImmediate`) NO alcanza para `history.back()` ni para el clic, porque jsdom los encola con temporizadores; por eso `soporte.mjs` tiene `esperarHashchange(ventana)`, que espera el evento real (y falla si no llega en 1 s). Las cuatro pruebas pasaron al escribirse (caracterización de jsdom): su validación es que el ayudante rechaza si el evento no llega; en una primera versión con `esperarTareas()` dos de ellas fallaron, lo que mostró la diferencia.

**Hallazgo en `rutas.ts` (tarea 2.2).** La primera versión del GREEN escribió la expresión del id como `'([1-9]\d*)'` en un literal común, donde `\d` es solo `d`: pasaban las pruebas con ids de un dígito y el id gigante, pero no los de varios dígitos. Se detectó al revisar las mutaciones de triangulación; se corrigió con `String.raw` y se agregó la prueba de id de varios dígitos (`#/factura/120`). Mutaciones validadas ahora: quitar `Number.isSafeInteger` → falla la prueba del id mayor que `MAX_SAFE_INTEGER`; aceptar `0` en el primer dígito (`[0-9]`) → falla «Rutas desconocidas». Ambas revertidas.

## Recorrido manual

Fecha 2026-11-18. Navegador integrado de la herramienta (Chromium, 1024x768) contra el backend del estudiante (`java -jar build/libs/facturacion-arca-1.0.0.jar`, JAR de la etapa 6, puerto 8080) y su `http-server` (5173, sin caché), que **ya estaban en marcha y se usaron con su autorización; no se iniciaron ni se detuvieron servidores propios**. Antes se corrió `npm run build` para que el servidor entregara el JS nuevo. La base H2 ya tenía datos de pruebas manuales del estudiante: se crearon borradores propios con `curl` (ids 7 a 16) y no se dependió de ids fijos de la base. Capturas en `docs/evidencia/etapa-7/`.

| Qué | Cómo se hizo | Resultado |
|-----|--------------|-----------|
| Arranque en `#/` (sin fragmento): listado, `aria-current`, título, sin foco movido | Navegador real | OK |
| Clic en «Ver», Atrás y Adelante reales, recarga (`location.reload()` por script) en `#/factura/{id}`, `#/nueva` y `#/factura/{id}/editar` | Clics y teclas reales del navegador; la recarga por script | OK: cada dirección vuelve a su pantalla |
| Filtro por fecha y cliente (explorador real con `showModal()`), página 2, «Ver», Atrás | Clics reales; los clics de Buscar, Quitar cliente y «Siguiente» de la preparación de la página 2 se dispararon con `.click()` por script | OK: campos intactos y pedido con `clienteId` / `pagina=1` (visto en la red) |
| Explorador: foco al abrir (campo de texto), Escape, foco de vuelta al botón, elegir cliente y artículo | Navegador real (`showModal()` real, tecla Escape real) | OK |
| Atrás con el explorador abierto | Navegación real hacia atrás | OK: el diálogo se cierra y el foco va al `h1` del listado |
| `#/facturas` (ruta desconocida) y `#/factura/999` (404 real) | Navegador real | OK: listado con «La dirección #/facturas no existe…»; detalle con el 404 del backend en la región, `h1`, «Volver al listado» y sin botones |
| Alta completa (condición, punto de venta, moneda con `form_input`; cliente y artículo con el explorador real; cantidad) y «Guardar borrador» real | Mixto: selects y cantidad por la herramienta de formularios; resto, clics reales | OK: detalle del borrador 13 con «Se creó el borrador 13.» y foco en el `h1` |
| Edición por ruta, recarga en ella, «Cancelar» (sin `PUT`, visto en la red) | Clics reales | OK |
| Emitir (clic real y Enter real) | Navegador real | OK: «Se emitió el borrador 13: número 4.» con el detalle ya emitido; ver hallazgo del foco |
| 409 real: se emitió por `curl` el borrador abierto en pantalla y se pulsó Emitir | Clic real + `curl` | OK: error del backend en la región, acciones habilitadas |
| Anular con motivo y cancelar el motivo | **Simulado**: `window.prompt` reemplazado por script (los diálogos nativos bloquean la automatización); clic real en Anular | OK: sin `PATCH` al cancelar; con motivo, la factura pasa a ANULADA, muestra fecha y observaciones y ofrece solo «Imprimir PDF» |
| Eliminar con confirmación rechazada y aceptada | **Simulado**: `window.confirm` reemplazado por script; clic real en Eliminar | OK: rechazada no envía nada; aceptada lleva al listado con «Se eliminó el borrador 8.» |
| Imprimir PDF | Solo `curl` al enlace (`/api/facturas/13/pdf`: 200 `application/pdf`, 150 677 bytes); el enlace se vio en el detalle | No se abrió ni descargó en el navegador |
| Detalle de EMITIDA y de ANULADA, tabla de renglones, 3 estados de acciones | Navegador real (valores leídos por script) | OK |
| Orden de Tab con teclado real, lector de pantalla, zoom al 200 % | **No verificado** | queda en el checklist del estudiante |

### Hallazgo del recorrido y su corrección (frontend, con TDD)

Después de emitir o anular, el foco caía a `body`: el botón activado quedaba deshabilitado mientras corría la acción y luego se reemplazaba al reconstruir las acciones. Corrección con RED visto en jsdom (`Foco tras emitir` y `Foco tras anular`, fallaron con el foco en `body`), pero **el primer arreglo no alcanzó en el navegador real**: jsdom conserva el foco en un botón deshabilitado y Chromium no, así que la prueba pasaba y el defecto seguía. La segunda versión recuerda el botón de origen: lo reenfoca al liberar las acciones si sigue en el documento (409) y, si se reemplazó, enfoca el `h1`. Verificado en el navegador real con Enter sobre Emitir (foco en `titulo-detalle`). Se agregó «Foco tras un error» (el botón conserva el foco); la prueba de jsdom no distingue el segundo arreglo del primero: su evidencia es el recorrido real.

Observaciones menores, sin corregir: `document.title` del detalle usa el id (`Factura 13 — …`) aunque el `h1` muestre el comprobante; al recargar directamente en `#/factura/{id}/editar` el foco pasa al `h1` porque `Formulario.cargar` lo enfoca (comportamiento de la etapa 6).

## Límites conocidos

- Lo escrito en la alta se pierde al navegar (a propósito: «Cancelar» y la barra lo hacen explícito); no hay aviso de cambios sin guardar.
- Los filtros y la página viven en memoria: una recarga en `#/` vuelve a la página 0 sin filtros; no están en la URL.
- Los enlaces no se pueden deshabilitar: durante una acción en el detalle siguen activos «Editar» e «Imprimir PDF».
- jsdom no reproduce que un botón deshabilitado pierda el foco; ese comportamiento solo se vio en el navegador real.
- Los diálogos nativos (`confirm`, `prompt`) se simularon en el recorrido; el PDF se verificó por `curl`, no en un visor; no hubo lector de pantalla ni zoom al 200 %.
- Pregunta abierta: ¿conviene reflejar filtros y página en la URL (`#/?estado=EMITIDA&pagina=2`)? No cambia este diseño.

## Verificaciones y entorno

Fecha 2026-11-18. JDK 17.0.20.1 (Microsoft), Node v24.22.1, TypeScript 5.6.

| Verificación | Resultado |
|---|---|
| `cd frontend && npm test && npm run build` | **317 pruebas** en verde (baseline 244), 0 fallas; `tsc` estricto sin errores |
| `./gradlew test --rerun` con JDK 17 (solo `test`; sin `clean` ni `bootJar` porque el servidor del estudiante tiene bloqueado el JAR) | **620 pruebas** de backend, 0 fallas, 0 errores (suma de los `testsuite`); el backend no se modificó |
| Reglas duras en `frontend/src` | sin `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` ni `new Date` (solo un comentario); `"strict": true`; `package.json`, `package-lock.json` y `tsconfig.json` sin cambios |
| `openspec validate etapa-7-pantallas --strict` | válido |
| Recorrido manual | navegador real vs simulado vs `curl`/jsdom, ver «Recorrido manual» |
| Servidores | 8080 y 5173 son del estudiante y siguen sirviendo; no se tocaron |

## Uso de IA

- Herramientas: Claude Code (Anthropic) con sub-agentes de OpenSpec (propuesta, diseño, tareas y ejecución de la etapa).
- Qué se pidió: la propuesta y el diseño de la etapa, las pruebas en rojo, el código mínimo para pasarlas, las mutaciones de validación de las pruebas de caracterización, el enrutador y las tres pantallas, la revisión de accesibilidad por principio, el recorrido manual con ambos lados reales, el README y este informe. No se reutilizó código de la semilla ni del repositorio del docente (no había nada para esta etapa).
- Qué se revisó y qué se corrigió: pedí dividir la aplicación en tres pantallas, listado, alta y edición, y detalle, y aprobé la propuesta antes de implementar. Trabajé la etapa con un punto de revisión obligatorio, en el que el avance se detuvo hasta que lo aprobé: las tres pantallas funcionando en las pruebas. Con una guía de revisión que me armó la IA leí y verifiqué el interpretador de rutas, el enrutador con su ventana inyectable, la estructura de secciones del HTML, el foco que pasa al título de cada pantalla, el mensaje de éxito que sobrevive a la navegación y los controladores por pantalla. Las acciones de emitir, anular y eliminar pasaron del listado al detalle, y la IA declaró las pruebas que se eliminaron o reemplazaron por ese motivo. El recorrido en un navegador real encontró un defecto que jsdom no reproducía: el foco se perdía después de emitir o anular, y se corrigió con pruebas y se verificó en el navegador. La IA declaró lo que no pudo verificar: el orden de Tab con teclado real, el lector de pantalla, el zoom, el PDF en un visor y los diálogos nativos. La revisión de accesibilidad la anoto aparte en la lista de este informe.
- Declarado por la IA: un error propio en la expresión del id de `rutas.ts` (`\d` dentro de un literal común), detectado por la revisión de mutaciones y corregido con `String.raw`; un primer arreglo del foco tras emitir que pasaba en jsdom pero no en el navegador real, corregido con el recorrido; el desvío de orden de tareas descrito arriba; lo no verificado listado en «Límites conocidos».
- Los commits propuestos llevan la línea `Co-Authored-By`.
