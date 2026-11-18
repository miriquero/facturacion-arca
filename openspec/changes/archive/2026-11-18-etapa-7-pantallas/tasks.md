# Tasks

> Reglas para todo el apply:
> - Solo **frontend**: `npm test` dentro de `frontend/` (compila con `tsc` y corre `node --test`). Sin dependencias, versiones ni archivos de build nuevos: `package.json`, `package-lock.json` y `tsconfig.json` no cambian (`strict` siempre activo). El backend no se toca.
> - TDD estricto (skills `tdd`, `accessibility`, `accessibility-review`): cada **GREEN** va precedida de su **RED**, que se cierra solo al ver la falla **por la razón esperada** (aserción, no compilación ni importación: si no existe el módulo, crear primero el esqueleto mínimo). Un comportamiento por ciclo. Las pruebas que pasan al escribirse son **caracterización**: se validan con una mutación temporal que se revierte y se anotan en `docs/etapa-7.md`. Esperados literales tomados de `specs/`. Sin aserciones triviales.
> - Reglas duras: sin framework ni bundler; nunca `innerHTML`/`outerHTML`/`insertAdjacentHTML`; texto del servidor con `textContent`; fechas sin `new Date`; importes por código de moneda; `ControlSolicitudes` por carga; liberación en `finally`; cancelar no envía nada.
> - Pruebas existentes: se migran en el grupo que cambia su comportamiento (lista en `design.md` D10) y cada cambio se anota en `docs/etapa-7.md` con el motivo; nunca se debilita una aserción más allá de lo que implica la mudanza.
> - No tocar `CHANGES.md`, `CLAUDE.md`, `AGENTS.md`, el backend ni changes archivados. Sin comandos Git que cambien estado.
> - Gobernanza MEDIA: al cerrar cada grupo, correr `npm test` completo y reportar el conteo. En la tarea **CHECKPOINT** el apply se detiene y espera el visto bueno del estudiante.

## 1. Red de seguridad e informe

- [x] 1.1 Red de seguridad: en `frontend/`, `npm ci` y `npm test`; anotar el baseline (se esperan **244** pruebas en verde) y que `tsc` compila sin errores; si algo falla, detenerse y reportarlo como falla preexistente
- [x] 1.2 Crear `docs/etapa-7.md` con el esqueleto de secciones (origen del pedido, decisiones, rutas, pantallas, accesibilidad, pruebas existentes que cambiaron, pruebas y mutaciones, recorrido manual, límites, verificaciones y entorno, uso de IA); verificar que existe con esas secciones

## 2. Rutas y enrutador (spec `frontend-navegacion-pantallas`)

- [x] 2.1 RED: `test/rutas.test.mjs` — `interpretarRuta` de ``, `#/`, `#/nueva`, `#/factura/5` y `#/factura/5/editar` (escenario «Rutas conocidas»), con esqueleto de `src/navegacion/rutas.ts` que devuelve siempre `desconocida`; ver la falla por aserción
- [x] 2.2 GREEN + TRIANGULATE: implementar `interpretarRuta`; agregar el escenario «Rutas desconocidas» (`#/facturas`, `#/factura/abc`, `0`, `-3`, `2.5`, `#/factura/5/borrar`) y un id mayor que `Number.MAX_SAFE_INTEGER`; luego RED + GREEN de `hrefDe(ruta)` para las cuatro rutas con ida y vuelta `interpretarRuta(hrefDe(r))` igual a `r`; `npm test` en verde
- [x] 2.3 Prueba de aprendizaje de jsdom (`test/enrutador.test.mjs`): asignar `location.hash`, `location.replace('#/…')`, `history.back()` y clic en `<a href="#/…">` disparan `hashchange` (asincrónico); anotar en `docs/etapa-7.md` qué dispara y qué no, y si hace falta el respaldo de `design.md` (Risks)
- [x] 2.4 RED + GREEN: `Enrutador` con la ventana de jsdom — `iniciar()` entrega la ruta actual; un cambio de fragmento llama a `alCambiar` con la ruta nueva; `ir(ruta)` agrega una entrada al historial y `ir(ruta, { reemplazar: true })` no (comprobado con `history.length`); anotar en el informe la tabla de rutas

## 3. Cáscara: pantallas, barra, foco, título y mensajes (spec `frontend-navegacion-pantallas`)

- [x] 3.1 RED: en `index.test.mjs`, barra `nav` nombrada con «Facturas» (`#/`) y «Nueva factura» (`#/nueva`); tres secciones de pantalla con `h1 tabindex="-1"`; región de estado y `<dialog id="explorador">` fuera de las secciones; reemplazar «una sola h1 y un landmark main» por «un `h1` por pantalla y un `main`» (anotar el cambio); ver la falla
- [x] 3.2 GREEN: reestructurar `index.html` (D2) conservando todos los ids existentes; `#titulo-formulario` pasa a `h1`; los tests de estructura de `index.test.mjs` (labels, ids únicos, `type` en botones, sin `tabindex` positivo) siguen en verde
- [x] 3.3 REFACTOR con red verde: mover `ApiFacturas`, `Dialogos`, `Entorno` y `mensajeDe` a `src/contexto.ts` (re-exportados por `aplicacion.ts`), agregar `ventana` al `Entorno`, `ventana: window` en `app.ts`, `montarPagina(url)` y `arrancarApp` en `soporte.mjs`; actualizar mecánicamente las llamadas a `iniciarAplicacion` de las pruebas existentes; `npm test` con las 244 (ajustadas en 3.1) en verde
- [x] 3.4 RED + GREEN: arrancar en `#/factura/5` muestra solo la sección del detalle (las otras con `hidden`) y no pide el listado; arrancar sin fragmento muestra el listado; cambiar el fragmento a `#/nueva` muestra la alta (escenarios «Entrada directa al detalle», «Entrada sin fragmento», «Cambio de fragmento», «Solo el detalle visible»)
- [x] 3.5 RED + GREEN: `aria-current="page"` en «Facturas» para el listado y en «Nueva factura» para la alta, ninguno en detalle; `document.title` `Facturas — Facturación ARCA` y `Nueva factura — Facturación ARCA` (escenarios «Listado actual», «Detalle sin enlace actual», «Títulos de las pantallas»)
- [x] 3.6 RED + GREEN: al navegar, foco en el `h1` de la pantalla nueva; en el arranque el foco no se mueve; exactamente un `h1` fuera de secciones ocultas (escenarios «Foco al abrir el detalle», «Un h1 visible»)
- [x] 3.7 RED + GREEN: mensaje pendiente de `ir(ruta, { mensaje })` escrito en la pantalla de destino; navegación del usuario limpia la región; ruta desconocida `#/facturas` muestra el listado con el error explicativo (escenarios «Error viejo al navegar», «Dirección inexistente»); `npm test` completo y conteo en el informe

## 4. Pantalla de listado (specs `frontend-consulta-facturas`, `frontend-acciones-factura`)

- [x] 4.1 RED + GREEN: fila con enlace «Ver» a `#/factura/{id}` (nombre accesible con el id) y «Imprimir PDF» solo para informadas, sin botones de acción; `Listado` pierde `onAccion` y `deshabilitarAcciones`; migrar en `acciones.test.mjs` «Tres estados» (parte de fila), «Filas informadas en la segunda página», «EMITIDA: enlace…» y «ANULADA: solo el enlace…» según el escenario «Fila con solo navegación» (anotar cada cambio)
- [x] 4.2 RED + GREEN: arrancar en `#/nueva` pide los catálogos una vez y no el listado; al ir al listado se pide la página 0 sin filtros (escenario «Entrada por otra pantalla»); las pruebas de `consulta.test.mjs` siguen en verde sin cambios de aserciones
- [x] 4.3 RED + GREEN: estado conservado al volver — filtros `EMITIDA` y cliente 2, página de índice 1, ir al detalle y volver atrás: campos intactos y pedido con `estado=EMITIDA`, `clienteId=2`, `pagina=1`; filtro editado sin buscar no viaja (escenarios «Volver con filtros y página», «Filtros editados sin buscar»)
- [x] 4.4 Caracterización + migración: «Emisión en la página 2» y «Se elimina el único elemento de la última página» y «Si la página reintentada también llega vacía…» disparadas al volver al listado desde el detalle (la lógica de la etapa 6 se mueve sin cambios); mutación temporal (quitar el reintento) → falla; revertir; anotar en el informe; `npm test` completo

## 5. Pantalla de detalle (specs `frontend-pantalla-detalle`, `frontend-acciones-factura`)

- [x] 5.1 RED + TRIANGULATE + GREEN: `formatearComprobante(1, 4)` → `0001-00000004`, `(12, 123)` → `0012-00000123`, número nulo → `—` (en `formato.test.mjs`)
- [x] 5.2 RED + GREEN: `src/ui/detalle.ts` con un `FacturaDetalladaDTO` literal — cabecera (número, fecha `01/11/2026`, estado), cliente con CUIT y Consumidor Final, condición de IVA, punto de venta y moneda (escenarios «Factura emitida», «Borrador sin número», «Cliente con CUIT», «Consumidor Final»)
- [x] 5.3 RED + GREEN: tabla de renglones con `caption`, `th scope="col"`, orden recibido, importes en `PES` y `DOL` sin conversión y descripción nula `—` (escenarios «Dos renglones en pesos», «Factura en dólares»)
- [x] 5.4 RED + GREEN: importes, CAE, vencimiento, resultado, observaciones y fecha de anulación; nulos como `—`; denominación que parece HTML queda literal sin `img` (escenarios «Factura emitida con CAE», «Borrador sin CAE», «Factura anulada», «Denominación que parece HTML»)
- [x] 5.5 RED + GREEN: `PantallaDetalle` — un solo pedido del detalle, `aria-busy` durante la carga, `h1` `Factura {id}` y luego el comprobante; `404` en la región con «Volver al listado» y sin acciones; falla de conexión; respuesta y error tardíos descartados al salir (escenarios «Carga exitosa», «Durante la carga», «Factura 999 inexistente», «Falla de conexión», «Detalle que llega tarde», «Error que llega tarde»)
- [x] 5.6 RED + GREEN: acciones por estado en el detalle (Editar enlace, Emitir, Eliminar / Anular + Imprimir PDF / solo Imprimir PDF, siempre «Volver al listado») con nombres accesibles; migrar «Tres estados», «Nombre accesible» y las pruebas de enlace PDF de `acciones.test.mjs` (escenarios «Tres estados», «Informadas sin edición ni eliminación», «Nombre accesible», «Enlace de la factura 1», «Borrador sin impresión»)
- [x] 5.7 RED + GREEN: emitir desde el detalle — anuncio con el número 4 y recarga del detalle con el anuncio todavía visible; 409 visible con acciones liberadas; doble clic → un solo `PATCH` con Emitir y Eliminar deshabilitados; migrar las pruebas de emisión y reemplazar «Una acción en curso bloquea también el guardado del formulario» (anotar por qué se elimina)
- [x] 5.8 RED + GREEN: anular desde el detalle — con motivo, sin motivo (vacío y espacios), motivo de 501 caracteres sin petición, diálogo cancelado sin `PATCH` y detalle sin cambios, recarga del detalle tras el éxito; migrar las pruebas de anulación
- [x] 5.9 RED + GREEN: eliminar desde el detalle — confirmado → `DELETE /facturas/6`, listado visible y anuncio; rechazado → ninguna petición y detalle visible; `404` visible hasta la siguiente acción; migrar las pruebas de eliminación; documentar la pantalla en el informe y `npm test` completo

## 6. Pantalla de alta y edición (spec `frontend-formulario-factura`)

- [x] 6.1 RED + GREEN: `#/nueva` muestra el formulario vacío en modo alta cada vez que se entra; «Cancelar» visible en ambos modos; cancelar el alta → listado sin `POST` (escenarios «Entrar a la alta», «Cancelar el alta»); `formulario.test.mjs` arranca en `#/nueva` (cambio mecánico)
- [x] 6.2 RED + GREEN: alta exitosa → detalle de la factura 15 con el anuncio; guardado que termina fuera de la pantalla anuncia sin navegar (escenarios «Alta exitosa», «Éxito tras guardar», «Guardado que termina fuera de la pantalla»); migrar «Alta exitosa» y quitar de «Bloqueo durante el alta» la aserción sobre botones de fila (anotar)
- [x] 6.3 RED + GREEN: edición por ruta — `#/factura/5/editar` carga el borrador; estado `EMITIDA` → detalle 5 con el error, sin entrada en el historial; `404` → listado con el error (escenarios «Borrador con dos renglones», «Ya fue emitido», «Borrador inexistente»); `editar(id)` de `edicion.test.mjs` navega por ruta
- [x] 6.4 RED + GREEN: guardar la edición → detalle 5 con el anuncio; cancelar → detalle 5 sin `PUT`; cargas obsoletas al cancelar o al pedir otro borrador (escenarios «Edición exitosa», «Cancelar sin guardar», «Cancelación antes de la respuesta», «Otro borrador elegido después»); migrar las pruebas de `edicion.test.mjs` y anotar cada cambio
- [x] 6.5 Caracterización: valores conservados ante error, errores por campo del servidor, doble envío y liberación tras `400` siguen en verde en la pantalla nueva; mutación temporal (quitar el `finally` de `bloquearGuardado`) → falla; revertir; documentar la pantalla en el informe y `npm test` completo

## 7. Accesibilidad y estilos (specs `frontend-estado-accesibilidad`, `frontend-navegacion-pantallas`)

- [x] 7.1 RED + GREEN: región de estado única, mismo nodo, sin `hidden` y fuera de las secciones al navegar listado → detalle → alta; con el detalle visible ningún campo de filtro ni del formulario queda fuera de una sección `hidden`; accionables nativos sin `tabindex` positivo en cada pantalla; dividir por pantalla las pruebas de orden de tabulación de `accesibilidad.test.mjs` (anotar)
- [x] 7.2 RED + GREEN: salir de la pantalla con el explorador abierto lo cierra y resuelve `null` (`Explorador.cerrar()`), y el foco va al `h1` de la pantalla nueva
- [x] 7.3 RED + GREEN: estilos — `[hidden] { display: none !important }`, barra de navegación con `aria-current` visible no solo por color, disposición del detalle, objetivos de al menos 44 px y solo colores de la paleta con contraste calculado (extender la prueba de estilos de `accesibilidad.test.mjs`)
- [x] 7.4 Revisión de accesibilidad por principio (POUR) de la navegación nueva (2.4.2 título, 2.4.3 orden de foco, 4.1.2 nombre/rol/valor, 4.1.3 mensajes de estado) y checklist del estudiante en `docs/etapa-7.md` con «**COMPLETAR POR EL ESTUDIANTE**»; `npm test` completo y conteo

## 8. Revisión del estudiante

- [x] 8.1 (aprobado por el estudiante 2026-11-18) **CHECKPOINT — tres pantallas en jsdom**: mostrar al estudiante la tabla de rutas, el `index.html` resultante, un resumen de `aplicacion.ts`, `navegacion/` y `pantallas/`, el conteo de `npm test` y la lista de pruebas existentes cambiadas con su motivo; esperar visto bueno antes del recorrido manual

## 9. Recorrido manual

- [x] 9.1 Recorrido en el navegador integrado (si está disponible): backend con `java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/facturacion-arca-1.0.0.jar` usando JDK 17 y `npm run dev`; recorrer listado → filtrar → página 2 → Ver → Atrás (filtros y página conservados) → Nueva factura con explorador → guardar (detalle con anuncio) → Editar → Emitir → Imprimir PDF → Anular → Eliminar un borrador → `#/factura/999` → `#/facturas` → recarga en el detalle; anotar evidencia y lo no verificado; cada defecto hallado se corrige con su RED + GREEN y se anota (si no hay hallazgos, anotarlo); detener ambos procesos y verificar 8080 y 5173 libres

## 10. Documentación y cierre

- [x] 10.1 Completar `docs/etapa-7.md`: decisiones, tabla de rutas, pruebas existentes cambiadas, mutaciones, evidencia, límites y «Uso de IA» con el mismo formato que `docs/etapa-6.md` y «Qué se revisó y qué se corrigió: **COMPLETAR POR EL ESTUDIANTE**»; verificar que no quedan secciones vacías salvo las marcadas para el estudiante
- [x] 10.2 Actualizar `README.md`: sección «Etapa 7: pantallas» con las tres pantallas y la tabla de rutas, y corregir lo desactualizado (por ejemplo, la descripción del frontend como una sola página y las acciones por fila); verificar los enlaces relativos
- [x] 10.3 Verificación frontend: `cd frontend && npm test && npm run build` en verde (conteo final contra el baseline de 244) y greps de reglas duras en `frontend/src` sin `innerHTML`, `outerHTML`, `insertAdjacentHTML` ni `new Date`, y `strict` en `tsconfig.json`; anotar en el informe y en la tabla de verificaciones del README
- [x] 10.4 Verificación backend y specs: `./gradlew clean test bootJar` con `JAVA_HOME=C:\Users\zbook\.jdks\ms-17.0.20.1` → **620** pruebas sin cambios; `git status` muestra solo cambios en `frontend/`, `docs/etapa-7.md`, `README.md` y `openspec/changes/etapa-7-pantallas/`; `openspec validate etapa-7-pantallas --strict` válido; entregar al estudiante los comandos de Git (commit con `Co-Authored-By`) sin ejecutarlos

## Workflow follow-up

- `/opsx:archive etapa-7-pantallas` y marcar `C-08` como `[x]` en `CHANGES.md` (lo hace el orquestador con el visto bueno del estudiante).
- Tag de la entrega: a definir por el estudiante.
