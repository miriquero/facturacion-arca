# Design

## Context

Motivación y alcance: ver `proposal.md`. Estado actual del frontend (tag `etapa-6`, 244 pruebas en verde):

- `index.html` es una sola página: `h1`, región de estado, sección de filtros, sección de resultados (tabla, descargas, paginación), sección del formulario y un único `<dialog id="explorador">`.
- `src/aplicacion.ts` (≈ 320 líneas) arma todo en `iniciarAplicacion(entorno)`: región de estado, explorador, `Formulario`, `Listado` (con botones de acción por fila y un listener delegado), `Paginacion`, la consulta con un único `ControlSolicitudes`, la edición con otro, las acciones de fila con `accionEnCurso` y el guardado con `guardando`. `app.ts` es el único módulo que toca globales (`fetch`, `confirm`, `prompt`, `showModal`, `setTimeout`).
- Las pruebas montan el `index.html` real en jsdom (`soporte-pruebas/soporte.mjs`: `montarPagina`, `servicioFalso`, `dialogosFalsos`, `temporizadorManual`, `ventanaModalFalsa`) y llaman a `iniciarAplicacion` con dobles; no hay red.
- Restricciones: sin framework ni bundler, TypeScript `strict` (con `noUnusedLocals`), nunca `innerHTML` con datos del servidor, fechas sin `new Date`, importes por código de moneda, sin cambios de backend ni de contrato, sin dependencias nuevas.

Skills aplicadas (compact rules de `.atl/skill-registry.md`): `tdd` (rojo antes de verde, una costura por ciclo, pruebas por interfaz pública, esperados literales), `accessibility` (HTML semántico primero, un `h1`, foco lógico y visible, `aria-live` para cambios dinámicos) y `accessibility-review` (2.4.3 orden de foco, 2.4.2 título de página, 4.1.2 nombre/rol/valor, 1.3.1 semántica, objetivos de 44 px).

## Goals / Non-Goals

**Goals:**
- Tres pantallas con dirección propia, historial del navegador y foco accesible, con el menor código nuevo posible.
- Un límite claro entre enrutador, controladores de pantalla y armado común, cada pieza probada por su interfaz pública.
- Conservar el comportamiento de las etapas 5 y 6 y que las pruebas existentes cambien solo lo que implica la mudanza.

**Non-Goals:**
- Rutas con History API (`pushState`) y URLs sin `#`: `http-server` no reescribe rutas y una recarga en `/factura/5` daría 404.
- Aviso de cambios sin guardar al salir del formulario, transiciones animadas, carga diferida de módulos.
- Filtros y página dentro de la URL (se conservan en memoria; ver Open Questions).
- Cualquier cambio de backend, de contrato o de `package.json`/`tsconfig.json`.

## Decisions

### D1 — Enrutador por fragmento (`#/…`) con ventana inyectable
`src/navegacion/rutas.ts` (puro): `type Ruta = { tipo: 'listado' } | { tipo: 'alta' } | { tipo: 'detalle'; id: number } | { tipo: 'edicion'; id: number } | { tipo: 'desconocida'; fragmento: string }`, `interpretarRuta(hash): Ruta` y `hrefDe(ruta): string`. El id se acepta solo con `^[1-9]\d*$` y dentro de `Number.MAX_SAFE_INTEGER`.
`src/navegacion/enrutador.ts`: `Enrutador(ventana, alCambiar)` escucha `hashchange`, expone `iniciar()` (procesa el fragmento actual y devuelve la promesa de la primera carga), `ir(ruta, { reemplazar?, mensaje? })` (asigna `location.hash` o usa `location.replace` para no dejar en el historial una edición imposible) y `rutaActual`. `Ventana` es una interfaz mínima (`location.hash`, `location.replace`, `addEventListener('hashchange')`) que satisfacen `window` y la ventana de jsdom.
*Por qué*: el fragmento no requiere configuración del servidor (`http-server` sirve siempre `index.html`), Atrás/Adelante y la recarga funcionan solos y los enlaces son `<a href="#/factura/5">` nativos (teclado, abrir en otra pestaña, lector de pantalla). *Alternativas*: History API (descartada en Non-Goals); estado sin URL (no cumple enlaces directos ni Atrás).

### D2 — Pantallas como secciones del mismo HTML, mostradas con `hidden`
`index.html` tiene `<header>` con el nombre del sistema y `<nav aria-label="Principal">`, la región de estado y tres `<section>` (`pantalla-listado`, `pantalla-formulario`, `pantalla-detalle`) dentro de `main`, cada una con su `h1 tabindex="-1"`. Solo una no tiene `hidden`. El listado y el formulario conservan sus ids actuales; el detalle tiene un contenedor que se reconstruye con nodos en cada carga. El explorador y la región de estado quedan fuera de las secciones.
*Por qué*: `hidden` (`display: none`) saca la sección del orden de Tab y del árbol de accesibilidad sin `inert` ni ARIA extra; los controladores existentes (`Listado`, `Paginacion`, `Formulario`, `Explorador`) se enlazan una sola vez al arrancar y el estado del listado (valores de los filtros, página) sobrevive naturalmente porque su DOM no se destruye. *Alternativa*: controladores que crean y destruyen el DOM de cada pantalla (más código, perder el estado de los filtros, reescribir el formulario).

### D3 — Límites de módulos
- `src/contexto.ts`: `ApiFacturas`, `Dialogos`, `Entorno` (ahora con `ventana`), `mensajeDe`; `aplicacion.ts` los re-exporta para no romper importaciones.
- `src/pantallas/pantallaListado.ts`, `pantallaDetalle.ts`, `pantallaFormulario.ts`: un controlador por pantalla con la interfaz `Pantalla { seccion; titulo; tituloDocumento(ruta); entrar(ruta): Promise<void>; salir(): void }`. Cada uno tiene su propio `ControlSolicitudes`; `salir()` lo invalida (respuestas tardías descartadas).
- `src/ui/detalle.ts`: función pura que construye los nodos del detalle a partir de `FacturaDetalladaDTO` (solo `crear`/`textContent`), probada sin enrutador.
- `src/ui/formato.ts`: `formatearComprobante(puntoVenta, numero)` → `0001-00000004` (con `padStart`), `—` si el número es nulo.
- `src/aplicacion.ts`: armado (servicio, región, explorador, pantallas, enrutador) y la «cáscara»: mostrar una sección, `aria-current`, `document.title`, foco y mensaje pendiente. Queda por debajo de ≈ 120 líneas.
`app.ts` solo agrega `ventana: window` al entorno.

### D4 — Dónde viven las acciones
Editar, Emitir, Anular y Eliminar pasan al detalle; la fila del listado ofrece solo `<a>` «Ver» (con texto oculto `factura {id}`) e «Imprimir PDF» para informadas. *Por qué*: una sola ubicación para las reglas por estado, la fila deja de tener botones (desaparecen `deshabilitarAcciones` y el listener delegado de `Listado`) y el usuario ve la factura completa antes de emitir o eliminar. Editar es un enlace a `#/factura/{id}/editar`; Emitir, Anular y Eliminar son `button type="button"`. «Imprimir PDF» sigue siendo un enlace nativo a `urlPdfFactura(id)` (no pasa por el enrutador porque su `href` no empieza con `#`).

### D5 — Foco, título y `aria-current`
En cada cambio de ruta posterior al arranque, la cáscara muestra la sección, actualiza `document.title` (`Facturas`, `Nueva factura`, `Factura {id}`, `Editar borrador {id}`, siempre con ` — Facturación ARCA`), pone `aria-current="page"` en el enlace de la barra que corresponde (ninguno en detalle y edición) y enfoca el `h1` de la pantalla. En el arranque no se mueve el foco (el lector empieza por el principio del documento). El `h1` del formulario es el actual `#titulo-formulario` (pasa de `h2` a `h1`, textos `Nuevo borrador` / `Editar borrador {id}` sin cambios). El `h1` del detalle cambia a `Factura 0001-00000004` o `Borrador 6` cuando llegan los datos, sin mover el foco otra vez.

### D6 — Región de estado única y mensaje pendiente
`ir(ruta, { mensaje })` guarda un mensaje (`{ tipo: 'exito' | 'error', texto }`) que la cáscara escribe después de mostrar la nueva pantalla. Una navegación sin mensaje (enlace, Atrás, Adelante) limpia la región. Las recargas internas (detalle después de emitir o anular, listado al volver) no limpian la región ni escriben progreso; usan `aria-busy` en la sección. La ruta desconocida escribe un error persistente («La dirección `#/facturas` no existe; se muestra el listado»). La región sigue con su temporizador de 6 s para el éxito.

### D7 — Estado del listado en memoria
`PantallaListado` conserva `filtrosAplicados`, la página y el tamaño (ya viven en el cierre y en `Paginacion`); los campos de filtro conservan sus valores porque su DOM no se toca. `entrar()` hace la carga inicial la primera vez (`busqueda` con `{}`) y después una `recarga` con los filtros aplicados y la página vigente (la lógica de «última página existente» de la etapa 6 se mueve sin cambios). Los catálogos se piden una sola vez al arrancar, cualquiera sea la ruta.

### D8 — Pantalla del formulario
`entrar({tipo:'alta'})` vacía el formulario. `entrar({tipo:'edicion', id})` invalida cargas anteriores y pide el detalle; BORRADOR → `formulario.cargar`; otro estado → `ir(detalle, { reemplazar: true, mensaje: error })`; error de carga → `ir(listado, { reemplazar: true, mensaje: error })`. El botón `#cancelar-edicion` queda visible en ambos modos con el texto «Cancelar» y navega sin enviar nada (edición → detalle, alta → listado). Guardar conserva validación, `bloquearGuardado` con `finally` y `ApiError` por campo; ante el éxito navega al detalle con el mensaje, salvo que la pantalla ya no esté activa: entonces solo anuncia (sin navegar). `salir()` cierra el explorador si estaba abierto (nuevo `Explorador.cerrar()`, que resuelve `null`).

### D9 — Pantalla de detalle
`entrar({tipo:'detalle', id})` pone `h1` `Factura {id}`, `aria-busy="true"`, pide `obtenerFactura(id)` con su `ControlSolicitudes` y reemplaza el contenido con `ui/detalle.ts`; error → `region.error(mensajeDe(e))`, contenido vacío salvo «Volver al listado». Las acciones reutilizan la lógica actual de `accionFila` (confirmación, `validarMotivo`, `accionEnCurso`, liberación en `finally`): emitir/anular → anuncio y recarga del detalle; eliminar → `ir(listado, { mensaje })`.

### D10 — Pruebas existentes que cambian (honestamente)
- **Mecánico, sin cambiar aserciones**: todas las llamadas a `iniciarAplicacion` agregan `ventana: pagina.ventana` (o usan el nuevo `arrancarApp` de `soporte.mjs`); `montarPagina(url)` acepta la dirección inicial.
- `index.test.mjs`: «una sola h1 y un landmark main» → «un `h1` por pantalla, exactamente uno visible, y un `main`» (la regla cambia por el pedido).
- `acciones.test.mjs`: las pruebas de acciones pasan de botones de fila al detalle (arrancan en `#/factura/{id}` con `obtenerFactura`); «Emisión exitosa» deja de esperar un segundo `obtenerFacturas` y espera un segundo `obtenerFactura`; «Eliminar confirmado» espera el listado y el anuncio; «Tres estados» y «Nombre accesible» se reescriben según el escenario modificado; las de recarga de página se disparan al volver al listado; «Una acción en curso bloquea también el guardado del formulario» se **elimina** (las acciones y el formulario ya no conviven en una pantalla) y la reemplaza «Acción en curso deshabilita las demás acciones del detalle»; las de PDF de la fila se conservan y se agrega el enlace en el detalle.
- `edicion.test.mjs`: `editar(id)` navega a `#/factura/{id}/editar` en lugar de hacer clic en el botón de fila; «Ya fue emitido», «Edición exitosa», «Cancelar sin guardar» y «Error al cargar el detalle» esperan la navegación de los escenarios modificados; las aserciones `cancelar-edicion hidden === true` cambian porque Cancelar existe en ambos modos.
- `formulario.test.mjs`: arranca en `#/nueva`; «Alta exitosa» espera el detalle 15 en lugar de la recarga del listado; «Bloqueo durante el alta» pierde la aserción sobre botones de fila (ya no existen).
- `accesibilidad.test.mjs`: los dos tests de orden de tabulación se dividen por pantalla; «los botones de acción nombran la acción y la factura» pasa a los enlaces «Ver» y a los botones del detalle.
- `consulta.test.mjs`, `paginacion.test.mjs`, `explorador.test.mjs`: solo el cambio mecánico (las del explorador sobre el formulario arrancan en `#/nueva`).
Ninguna aserción se debilita más allá de lo que implica la mudanza; la lista exacta se anota en `docs/etapa-7.md`.

### D11 — Estrategia de pruebas
Costuras: (1) `interpretarRuta`/`hrefDe` puras; (2) `Enrutador` con la ventana de jsdom; (3) `ui/detalle.ts` con un `FacturaDetalladaDTO` literal; (4) aplicación completa en jsdom con `servicioFalso`, entrando por URL (`montarPagina('http://localhost:5173/#/factura/5')`) y navegando con `ventana.location.hash` y clics en enlaces. Esperados literales de `specs/`. Antes de depender de ellos, una prueba de aprendizaje fija cómo jsdom dispara `hashchange` (asignación, `replace`, `history.back()`, clic en `<a href="#…">`) y que es asincrónico (se espera con `esperarTareas`). Pruebas que pasen al escribirse son caracterización y llevan mutación temporal revertida.

## Risks / Trade-offs

- [jsdom no dispara `hashchange` en `location.replace` o en `history.back()`] → la prueba de aprendizaje (tarea 2.3) lo determina; si falta, `Enrutador.ir` procesa la ruta directamente y descarta el `hashchange` duplicado de la misma ruta, y Atrás se prueba en el navegador real (tarea de recorrido).
- [En jsdom, `click()` sobre un control de una sección `hidden` funciona igual] → las pruebas operan cada pantalla después de navegar a ella y hay pruebas explícitas de `hidden`; el orden de foco real se verifica en el navegador.
- [Mensaje en la región y foco movido a la vez pueden pisarse en el lector de pantalla] → el mensaje se escribe después de mover el foco; `aria-live="polite"` espera a que termine la lectura del `h1`. Se anota como ítem del checklist manual.
- [Pérdida de lo escrito en la alta al navegar] → aceptado (Non-Goal); «Cancelar» y la barra lo hacen explícito.
- [Pruebas existentes reescritas en volumen (≈ 40)] → D10 enumera por qué cambia cada grupo; se migran en el mismo grupo que la funcionalidad que las afecta, nunca al final.
- [Recarga del listado al volver cuesta un pedido extra] → aceptado: refleja emisiones y eliminaciones hechas en el detalle.

## Migration Plan

Solo frontend. Se despliega reconstruyendo (`npm run build`) y sirviendo `frontend/` como hasta ahora; las direcciones viejas (`/` sin fragmento) abren el listado. Rollback: volver al tag `etapa-6`.

## Open Questions

- ¿Conviene, más adelante, reflejar filtros y página en la URL (`#/?estado=EMITIDA&pagina=2`) para compartir un listado filtrado? No cambia este diseño: hoy se conservan en memoria.
- ¿Qué tag llevará la entrega (`etapa-7`)? Lo decide el estudiante al cerrar; no bloquea el apply.
