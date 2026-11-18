# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos observables: ver `specs/`. Este documento fija **cómo** se construye.

Estado al proponer (2026-11-18):

- **Frontend** (etapa 0, `frontend-esqueleto`): `package.json` con `build` (`tsc`), `watch`, `dev` (`npm run build && http-server . -p 5173 -c-1`) y `test` (`npm run build && node --test`); devDependencies `typescript ~5.6.3`, `jsdom ^25.0.0`, `http-server ^14.1.1`; `tsconfig` estricto, `target`/`module` ES2020, `moduleResolution: node10`, `rootDir: src`, `outDir: dist`, `noUnusedLocals`, `noImplicitReturns`. `src/config.ts` (`leerApiBaseUrl(doc)`) y `src/app.ts` (escribe la URL en `#estado-api`). Pruebas `test/config.test.mjs` y `test/index.test.mjs` (7 en verde) importan desde `dist/` y usan jsdom. Node v24.22.1: `node --test` sin argumentos descubre `**/*.test.mjs` y **todo** archivo bajo un directorio `test/` (por eso no se usa `node --test test/`).
- **Backend**: contrato de lectura de la etapa 3 (archivado: `api-listado-facturas`, `api-detalle-factura`, `api-descarga-reporte`, `api-catalogos`, `api-errores`, `api-cors`) y contrato de escritura de la etapa 4 tomado de su delta **no archivado** (`openspec/changes/etapa-4-escritura-estados-concurrencia/specs/`: `api-borradores-factura`, `ciclo-de-vida-factura`, `validacion-request-factura`, `api-errores`, `api-cors`), con sus DTO ya presentes en `src/main/java/.../dto/` (leídos, no tocados). CORS admite `http://localhost:5173` y `http://127.0.0.1:5173`, métodos `GET/POST/PUT/PATCH/DELETE`, encabezados `Content-Type` y `X-Usuario`.
- **Semilla** (`api-facturas/frontend`): cliente y pantalla funcionales para otro contrato (ver D15).

Restricciones: reglas duras de `CLAUDE.md` (sin framework ni bundler, `strict` activo, DTO tipados, nunca `innerHTML` con datos del servidor, sin Git que cambie estado), TDD estricto, versiones fijas (TypeScript 5.6). Ante conflicto manda la consigna, luego la KB, luego la semilla; la referencia del docente nunca decide (solo nombres).

## Goals / Non-Goals

**Goals:**
- Dos costuras de prueba explícitas y baratas: cliente HTTP sobre un `fetch` inyectado y aplicación sobre un puerto de servicio inyectado, ambas sin red ni navegador.
- Carreras reproducibles de forma determinista con promesas controladas (sin `setTimeout` reales en las pruebas).
- Cada regla RN-FE-01..16 trazable a un módulo pequeño y explicable en la defensa.

**Non-Goals:**
- Pruebas end-to-end automatizadas con navegador real (Playwright, Puppeteer): la consigna pide el recorrido manual y no se agregan dependencias.
- Compatibilidad con lectores de pantalla demostrada por pruebas (jsdom no lo demuestra; se revisa a mano y se declara el límite).
- Validar en el cliente las reglas que solo el backend puede decidir (existencia de referencias, transiciones concurrentes, representabilidad de importes).
- Internacionalización, persistencia local o modo sin conexión.

## Decisions

### D1 — Estructura de módulos (sin framework ni bundler)
```
frontend/src/
├── app.ts                    # arranque del navegador: lee el meta, crea dependencias reales, llama a iniciarAplicacion
├── aplicacion.ts             # composición testeable: iniciarAplicacion(entorno) conecta eventos, estado y servicios
├── config.ts                 # sin cambios (leerApiBaseUrl)
├── models/factura.dto.ts     # interfaces del contrato HTTP
├── services/ApiError.ts      # clase ApiError + lectura de ProblemDetail
├── services/FacturaService.ts
└── ui/
    ├── dom.ts                # crear elementos con textContent, opciones de select, vaciar contenedores
    ├── formato.ts            # importes por moneda, fechas dd/mm/aaaa, número de factura o guion
    ├── solicitudes.ts        # ControlSolicitudes (identificadores de solicitud)
    ├── regionEstado.ts       # RegionEstado (éxito con limpieza a 6 s, error persistente, progreso)
    ├── validacion.ts         # validaciones puras de filtros, cabecera, renglones y motivo
    ├── estimacion.ts         # total neto estimado sin IVA (puro)
    ├── listado.ts            # tabla, contador, mensaje vacío, enlaces y acciones por fila
    └── formulario.ts         # cabecera, renglones, lectura a FacturaRequestDTO, carga desde FacturaDetalladaDTO, errores de campo
```
Imports con extensión `.js` (ES2020 sin bundler, igual que `config.ts`). `index.html` sigue cargando solo `./dist/app.js`. Nombres de `models/` y `services/` según KB 08; nombres de métodos del servicio consultados en la referencia (D16). Alternativa descartada: un `app.ts` monolítico como el de la semilla (313 líneas con estado global y `document` global: imposible de probar sin el navegador).

### D2 — DTO tipados como espejo del contrato
`models/factura.dto.ts` con `export type EstadoFactura = 'BORRADOR' | 'EMITIDA' | 'ANULADA'` y las interfaces (nombres iguales a los records Java, campos iguales a los JSON):
- `FacturaReporteDTO { id: number; numeroFactura: number | null; fechaEmision: string; clienteDenominacion: string; condicionIva: string; puntoVentaDescripcion: string; importeTotal: number; cantidadItems: number; estado: EstadoFactura; codigoMoneda: string }`.
- `FacturaDetalladaDTO` (cabecera con `numero: number | null`, ids de cliente —nullable—, condición, punto de venta y moneda, `codigoMoneda`, importes, `cae`, `caeFechaVencimiento`, `resultadoAfip`, `fechaAnulacion`, `observaciones` nullable) y `FacturaDetalleDTO` (renglón con `listaPrecioArticuloId`, `articuloCodigo`, `descripcion`, `cantidad`, `precioUnitario`, `porcentajeBonificacion`, importes).
- `FacturaRequestDTO { clienteId?: number; condicionIvaId: number; puntoVentaId: number; tipoMonedaId: number; fechaEmision?: string; observaciones?: string; detalles: FacturaDetalleRequestDTO[] }` y `FacturaDetalleRequestDTO { listaPrecioArticuloId: number; cantidad: number; porcentajeBonificacion?: number; descripcion?: string }`: **sin** campos de precio ni importe, así el compilador impide enviarlos (RN-FE-07).
- `CambioEstadoRequestDTO { estado: 'EMITIDA' | 'ANULADA'; observaciones?: string }`, `FacturaFiltrosDTO { fechaDesde?, fechaHasta?, estado?: EstadoFactura, montoMinimo?: number }`, `OpcionDTO { id: number; etiqueta: string; valor: number | null; codigoMoneda: string | null }`, `ProblemDetail { status?: number; title?: string; detail?: string; timestamp?: string; errores?: Record<string, string> }`, `FormatoReporte = 'pdf' | 'xlsx' | 'txt'`.
- Las fechas viajan como `string` `yyyy-MM-dd` (Jackson serializa `LocalDate` así) y nunca se convierten a `Date` (D7).
- Las respuestas JSON se tipan con `as` en un único punto del servicio (`leerJson<T>`); no se valida el esquema completo en tiempo de ejecución (el backend es propio y está probado); **sí** se valida la forma de `ProblemDetail` (D4) porque un error puede venir de un proxy o de una página HTML.

### D3 — `FacturaService`: un único cliente HTTP con `fetch` inyectado
`new FacturaService(baseUrl: string, fetchFn: FetchLike)` con `type FetchLike = (url: string, init: RequestInit) => Promise<Response>`. En el navegador `app.ts` pasa `(url, init) => fetch(url, init)` (llamar a `window.fetch` desligado de `window` falla en algunos navegadores). Las pruebas pasan una función que registra `url` e `init` y devuelve `new Response(...)` (global en Node 24): sin dependencias ni `globalThis.fetch` parcheado.
- Métodos (nombres de la referencia, código propio): `obtenerFacturas(filtros)`, `obtenerFactura(id)`, `obtenerCatalogos()` (los cinco en paralelo con `Promise.all`, devuelve `{ puntosVenta, clientes, condicionesIva, tiposMoneda, listaPrecioArticulos }`), `crearFactura(req)`, `actualizarFactura(id, req)`, `cambiarEstado(id, estado, observaciones?)`, `eliminarFactura(id)`, `urlReporte(filtros, formato)`.
- Base normalizada sin barra final; rutas relativas a ella (`/facturas`, `/catalogos/...`): el meta ya incluye `/api`.
- Encabezados: siempre `X-Usuario: sistema` (constante `USUARIO_DECLARADO`); `Content-Type: application/json` solo si hay cuerpo; `Accept` no se envía (no hace falta y es otro encabezado que pasar por CORS). Ningún otro encabezado: el preflight de la etapa 4 rechaza los no previstos.
- `consultaFiltros(filtros)` (pura, exportada) arma `URLSearchParams` en orden fijo `fechaDesde, fechaHasta, estado, montoMinimo`, omitiendo `undefined` y cadenas vacías; la usan **tanto** el listado como `urlReporte`, así los enlaces no pueden divergir de la consulta (RN-FE-11). `urlReporte` agrega `formato` primero. `montoMinimo` se serializa con `String(n)`.
- `cambiarEstado` omite `observaciones` si es `undefined` o vacía tras `trim` (la API igual la ignora en blanco, pero no se envía ruido).
- Alternativa descartada: `URL_BASE` derivado de `window.location.hostname` (semilla): contradice RN-FE-02 y el spec `frontend-esqueleto`.

### D4 — `ApiError` y lectura de respuestas
Flujo único `enviar<T>(ruta, init)`:
1. `fetch` rechaza (red, CORS) → `ApiError(0, 'No se pudo conectar con la API en <base>. ¿Está corriendo el backend?')`.
2. `!respuesta.ok` → se lee el cuerpo con `text()` (nunca `json()` directo, que rompe con HTML o vacío), se intenta `JSON.parse` dentro de `try` y se acepta como `ProblemDetail` solo si es un objeto; `errores` se conserva solo si es un objeto cuyas claves tienen valores `string`. Mensaje = `detail` si es cadena no vacía; si no, `title`; si no, respaldo `El servidor respondió <status> sin un detalle legible.` (RN-FE-05).
3. `status === 204` → `undefined` **sin** tocar el cuerpo (RN-FE-04). Otras 2xx → `json()`.
`class ApiError extends Error { readonly status: number; readonly titulo?: string; readonly errores: Readonly<Record<string, string>> }` con `name = 'ApiError'`. Una sola clase para todos los no-2xx (la UI decide por `status` y `errores`). Se ubica en `services/ApiError.ts` para que la UI la importe sin depender del servicio. Alternativa descartada: subclases por código (más tipos sin comportamiento distinto).

### D5 — Control de respuestas obsoletas por identificador de solicitud
`ControlSolicitudes` (puro): `iniciar(): number` incrementa y devuelve un contador; `esVigente(id): boolean` compara con el último emitido; `invalidar()` incrementa sin emitir (para cancelar). Instancias separadas: `busquedas` (listado) y `cargaEdicion` (detalle a editar). Toda continuación `await` comprueba `esVigente` **antes** de tocar el DOM o la región de estado, tanto en el camino de éxito como en el de error (spec: error tardío descartado). Cancelar la edición o elegir otro borrador llama `cargaEdicion.invalidar()`/`iniciar()`. Las recargas del listado tras guardar, emitir, anular o eliminar pasan por el mismo `busquedas.iniciar()` y usan los **filtros aplicados** (los del último resultado mostrado), no lo que esté tipeado en el formulario de filtros. Alternativa descartada: `AbortController` (cancela la red pero no garantiza por sí solo que una promesa ya resuelta no escriba; además se puede combinar más adelante sin cambiar la regla). DD-10 de la KB elige ids.
- La tabla, el contador y los dos enlaces se aplican en una sola función `aplicarResultado(filas, filtros)` (RN-FE-13): no hay forma de actualizar uno sin los otros.

### D6 — Bloqueo durante el guardado y acciones de fila
- Estado `guardando: boolean` en la aplicación. Al enviar `POST`/`PUT`: si ya está `true`, se ignora el submit (segunda línea de defensa); si no, `true`, `form.setAttribute('aria-busy','true')`, `fieldset.disabled = true` sobre **todo** el contenido del formulario (cabecera, renglones, agregar/quitar, guardar, cancelar) y se deshabilitan los botones de acción de la tabla (`listado.deshabilitarAcciones(true)`), porque editar o eliminar el borrador que se está guardando es incompatible. En `finally`: `false`, `aria-busy="false"`, rehabilitar. `fieldset disabled` deshabilita los descendientes de forma nativa y accesible.
- Acciones de fila (emitir, anular, eliminar): mientras una está en curso se deshabilitan **todas** las acciones de fila y el guardado del formulario; se liberan en `finally`. Así no hay dos escrituras simultáneas desde la misma pantalla.
- La recarga del listado posterior a un éxito se dispara después de liberar el bloqueo y no lo vuelve a tomar (es una lectura con su propio id).

### D7 — Formato de importes y fechas
- `formatearImporte(valor, codigo)`: `PES` → `Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })`; `DOL` → igual con `USD`; cualquier otro (incluido `null`) → `` `${codigo ?? 'Sin moneda'} ${numero2}` `` con `Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })`. Formateadores creados una vez. Sin conversión (RN-FE-06, SU-09). Gotcha para las pruebas: `Intl` con `es-AR` separa símbolo y número con espacio duro (` `) y muestra USD como `US$`; los esperados literales de las pruebas se escriben con ` ` y se verifican primero en Node 24 (ICU completo).
- `formatearFecha('2026-11-01')` → `01/11/2026` por partición del texto. **Nunca** `new Date('yyyy-MM-dd')`: lo interpreta como medianoche UTC y en Argentina (UTC−3) muestra el día anterior; es el defecto de la semilla.
- `numeroFactura` nulo → `—`.

### D8 — Validación en el navegador (pura)
`validacion.ts` recibe valores en texto crudo (lo que hay en los `input`) y devuelve `{ datos?: T; errores: Record<string, string> }` con claves en la **misma notación que la API** (`puntoVentaId`, `detalles[1].cantidad`, `observaciones`, `montoMinimo`), así un único mecanismo (`formulario.mostrarErrores`) pinta errores locales y del servidor.
- Números con `Number(texto.trim())` y `Number.isFinite`: `''` → ausente; `1e400` → `Infinity` → inválido. Cantidad: obligatoria, finita, `> 0`. Bonificación: vacía → se omite del request (el backend la toma como 0); si no, finita en `[0, 100]`. Descripción: `trim`; vacía → se omite; `length > 255` → error (se mide `string.length`, igual que `@Size` en Java, que cuenta unidades UTF-16). Observaciones: igual con 500; además `maxlength` en el HTML como ayuda, sin confiar en él. Motivo de anulación: `trim`, ≤ 500.
- Filtro `montoMinimo`: vacío → ausente; finito `>= 0` (consigna: el formulario puede restringir lo que el backend no). Fechas invertidas **no** se validan en el cliente: el backend responde 400 y sirve para demostrar un error controlado de la API.
- Los dos formularios llevan `novalidate`: los atributos `min`, `max`, `maxlength` y `required` quedan como ayuda semántica, pero los mensajes los produce siempre esta validación (accesibles con `aria-invalid`/`aria-describedby`, iguales en navegador y jsdom, y en el mismo lugar que los del servidor). Ante errores, el foco va al primer campo inválido.
- Cabecera: los `select` obligatorios con valor `''` → error. `clienteId` `''` → se omite (Consumidor Final). `fechaEmision` vacía → se omite (el backend usa la fecha actual).

### D9 — Formulario de renglones y estimación
- Cada renglón es un `<fieldset>` con `<legend>Renglón N</legend>` (da el número al nombre accesible de sus campos), un `select` de lista–artículo (opciones del catálogo, etiqueta del servidor), `input type="number"` de cantidad (`step="any"`, `min` informativo) y bonificación, `input type="text"` de descripción, un `<output>` con el precio de catálogo y un botón «Quitar renglón N». Los `id` se generan con un contador monotónico (no con el índice) para que `label for` siga siendo único al quitar renglones; los índices de `detalles[i]` se recalculan por posición al leer y al pintar errores. Al quitar un renglón el foco pasa al renglón siguiente o, si no hay, al botón «Agregar renglón».
- `estimacion.ts`: `netoEstimado(renglones, precios)` suma `cantidad × precio × (1 − bonificación/100)` de los renglones válidos con opción elegida; se muestra en un `<output>` rotulado «Total neto estimado (sin IVA)» con la moneda elegida en la cabecera (si no hay moneda, sin símbolo). Es **informativo**: aritmética `number` del navegador, puede diferir en centavos del neto del servidor (que redondea por renglón con `HALF_UP`); el backend es la autoridad (RN-FE-08) y el informe lo aclara. La regla dura de `BigDecimal` aplica al backend; acá no se persiste nada.
- `leerRequest()` arma `FacturaRequestDTO` desde los datos validados (D8): nunca incluye precio ni importes (D2).
- `cargar(detalle: FacturaDetalladaDTO)` reconstruye cabecera y renglones; `vaciar()` vuelve al modo alta con un renglón vacío.
- Modo: `{ tipo: 'alta' } | { tipo: 'edicion'; id: number }`. En edición el título (`h2` con `tabindex="-1"`) pasa a «Editar borrador N», el botón principal a «Guardar cambios» y aparece «Cancelar edición»; al entrar en edición el foco va a ese título.
- Fecha en edición: se carga la `fechaEmision` del detalle y se reenvía; si el usuario la borra, se omite y el backend pone la de hoy (comportamiento de reemplazo completo de la etapa 4, D4 de su diseño).

### D10 — Acciones por estado y diálogos inyectables
- `listado.ts` crea, por fila y según `estado`, botones nativos con `data-accion` y `data-id`; el nombre accesible combina texto visible y un `span.visualmente-oculto` (`Emitir` + ` borrador 6`). Un único listener delegado en el `tbody`.
- Diálogos detrás de una interfaz `Dialogos { confirmar(mensaje): Promise<boolean>; pedirMotivo(mensaje): Promise<string | null> }`. En el navegador (`app.ts`): `window.confirm` y `window.prompt(mensaje, '')` envueltos en promesas: nativos, modales, operables por teclado y anunciados por los lectores de pantalla, sin manejo de foco propio. `null` (Cancelar o Escape) → no se envía nada (RN-FE-10). En las pruebas: dobles que devuelven lo que el caso necesita o promesas controladas. jsdom no implementa `confirm`/`prompt` (solo imprime «not implemented»), otra razón para inyectarlos.
- Alternativa descartada: `<dialog>` propio (más código de foco y de cierre con Escape, y jsdom no implementa `showModal`); queda como mejora posible si el docente lo pide.

### D11 — Región de estado
`RegionEstado(elemento, temporizador)` con `temporizador: { programar(fn, ms): Cancelar }` inyectado (`setTimeout`/`clearTimeout` en el navegador; uno manual en las pruebas, que avanza el reloj a voluntad, sin `mock.timers`). Métodos: `exito(texto)` (programa la limpieza a 6000 ms y cancela la anterior), `error(texto, detalles?: string[])` (cancela la limpieza pendiente; prefijo visible «Error:»; los detalles por campo sin destino se listan como `<ul>` construido con nodos), `progreso(texto)` (p. ej. «Guardando borrador…», sin limpieza automática) y `limpiar()`. El elemento `#estado` (`<div role="status" aria-live="polite">`) existe en el HTML desde la carga y solo cambia su contenido (RN-FE-15). Toda acción iniciada por el usuario llama primero a `limpiar()`; las recargas automáticas no. Clases `estado--exito` / `estado--error` para el color, siempre acompañadas del texto (no solo color). Alternativa descartada: `role="alert"` para errores (interrumpe; la consigna pide una región persistente `status`/`polite`).

### D12 — Inserción segura de texto (sin `innerHTML`)
`dom.ts` ofrece `crear(doc, etiqueta, texto?, atributos?)`, `opcion(doc, valor, etiqueta)` y `reemplazarHijos(contenedor, nodos)`; todo texto externo pasa por `textContent`/`new Option(...)`. La estructura fija vive en `index.html`; el código no usa `innerHTML`, `outerHTML`, `insertAdjacentHTML` ni `document.write`. Las pruebas cubren el texto que parece HTML (spec) y la tarea de revisión final busca esos nombres con `grep` en `frontend/src`. Para no depender de constructores globales del reino de jsdom, el código usa siempre el `Document` recibido (`doc.createElement`) y no usa `instanceof HTMLElement`.

### D13 — Composición testeable
`iniciarAplicacion({ documento, servicio, dialogos, temporizador }): Aplicacion` busca los elementos por `id` (falla con un error que nombra el `id` si falta), conecta eventos y dispara la carga inicial (catálogos y listado en paralelo; un catálogo que falla muestra el error sin impedir el listado). Devuelve un objeto con `listo: Promise<void>` (carga inicial terminada) para que las pruebas esperen sin `sleep`. `servicio` es del tipo `ApiFacturas` (la interfaz pública de `FacturaService`), así las pruebas del DOM usan un doble con promesas controladas en lugar de `fetch`. `app.ts` solo lee el meta, crea `FacturaService`, `Dialogos` y `Temporizador` reales, escribe la URL en `#estado-api` (spec `frontend-esqueleto`) y llama a `iniciarAplicacion`; un error de configuración se muestra como texto en la región de estado.

### D14 — Estrategia de pruebas (skills `tdd`, `accessibility`)
- **Costuras** (se declaran antes de escribir pruebas): (1) `FacturaService` ↔ `fetch` simulado: atrapa URL, método, encabezados, cuerpo y traducción de respuestas; no atrapa CORS ni el backend real. (2) `iniciarAplicacion` ↔ `ApiFacturas` doble + jsdom con el `index.html` real: atrapa render, validación, bloqueo, carreras, accesibilidad estática (atributos); no atrapa diseño visual, foco real, lectores de pantalla ni contraste. (3) Funciones puras (`formato`, `validacion`, `estimacion`, `ControlSolicitudes`, `RegionEstado` con temporizador manual). (4) Recorrido manual en navegador con el backend real (tareas 11 y 12).
- Archivos: `test/cliente-http.test.mjs`, `test/formato.test.mjs`, `test/validacion.test.mjs`, `test/solicitudes.test.mjs`, `test/region-estado.test.mjs`, `test/index.test.mjs` (ampliado: estructura y atributos), `test/consulta.test.mjs`, `test/formulario.test.mjs`, `test/edicion.test.mjs`, `test/acciones.test.mjs`; se conservan `config.test.mjs` e `index.test.mjs`. Importan de `../dist/` como hoy.
- Soporte de pruebas en `frontend/soporte-pruebas/` (fuera de `test/` y sin sufijo `.test`, para que `node --test` no lo ejecute como archivo de prueba): `diferido()` (promesa con `resolve`/`reject` externos), `temporizadorManual()`, `respuesta(status, cuerpo, tipo)`, `servicioFalso()` (registra llamadas y devuelve diferidos), `montarPagina()` (JSDOM con `index.html`, sin ejecutar scripts) y `esperarTareas()` (`await new Promise(r => setImmediate(r))` para vaciar microtareas tras resolver un diferido).
- Interacción: `boton.click()`, `select.value = ...` + evento `change` creado con `new documento.defaultView.Event('change', { bubbles: true })`, `form.requestSubmit()`.
- Esperados literales de los specs; nada recalculado como el código (p. ej. `13.400,00`, `01/11/2026`, `US$ 9.680,00`). Sin aserciones triviales: cada prueba de carrera afirma el contenido final de la tabla o del formulario, no solo que «no falló».
- Las pruebas que pasan al escribirse se marcan como **caracterización** en el informe (como en etapas anteriores); una mutación temporal (quitar `esVigente`, quitar el `finally` del bloqueo, usar `json()` en 204) demuestra que muerden y se revierte.

### D15 — Inventario de la semilla (DD-13)
| Semilla `api-facturas/frontend` | Destino |
|---|---|
| `models/factura.dto.ts`: `FacturaReporteDTO`, `FacturaRequestDTO`, `FacturaDetalleRequestDTO`, `FacturaFiltrosDTO` | **Adaptado**: `numeroFactura` nulable, `codigoMoneda`, `EstadoFactura`; `articuloId` → `listaPrecioArticuloId`; sin `estado` ni `usuarioId` en el request; se quita `ActualizacionFacturaDTO` (PUT parcial) y `ErrorRespuestaDTO {mensaje}` → `ProblemDetail`. **Nuevo**: `FacturaDetalladaDTO`, `FacturaDetalleDTO`, `OpcionDTO`, `CambioEstadoRequestDTO`, `FormatoReporte` |
| `FacturaService.ts`: clase con `enviar<T>`, `ApiError(status, mensaje)`, 204 → `undefined`, `try/catch` de red, `URLSearchParams` | **Adaptado**: base desde el meta, `fetch` inyectado, `X-Usuario`, `Content-Type` solo con cuerpo, cuerpo de error leído con `text()` y `ProblemDetail` con `errores`, mensaje de respaldo; `actualizarEstado` por `PUT` → `cambiarEstado` por `PATCH /estado`; catálogos, detalle, `actualizarFactura` y `urlReporte` **nuevos** |
| `app.ts`: helpers `celda`, `boton`, `filaVacia` con `textContent`, delegación de clicks en el `tbody`, `replaceChildren`, `numeroOpcional` | **Adaptado** dentro de `ui/dom.ts`, `ui/listado.ts` y `ui/validacion.ts`; ids por input numérico → selectores de catálogo |
| `app.ts`: `formatoImporte` fijo en ARS, `new Date(fechaEmision)`, `String(numeroFactura)`, mensaje global sin limpieza, botón deshabilitado sin `aria-busy`, `confirm` para anular, sin control de respuestas obsoletas | **Descartado/reemplazado** por D7, D11, D6, D10 y D5 |
| `index.html`: secciones con `aria-labelledby`, `label` envolventes, `#mensaje` con `role="status"` | **Adaptado**: región `#estado` siempre visible (no `hidden`), `caption` y `th scope` en tablas, renglones como `fieldset`, enlaces de descarga; se conservan el meta y `#estado-api` |

Cada pieza entra solo en un GREEN, detrás de su prueba en rojo, y se anota en `docs/etapa-5.md`.

### D16 — Referencia del docente (solo consulta)
Consultado en `arca-spirng/frontend` **solo** para nombres y forma: carpetas `models/` y `services/`; tipo `EstadoFactura`; interfaces `FacturaDetalladaDTO`, `FacturaDetalleResponseDTO` (acá `FacturaDetalleDTO`, como el record Java del proyecto), `OpcionDTO`, `ProblemDetail`, `CambioEstadoRequestDTO`; métodos `obtenerFacturas`, `obtenerFactura`, `crearFactura`, `actualizarFactura`, `eliminarFactura` y la existencia de `ApiError`. No se copió código ni se leyó su `app.ts`. Diferencias deliberadas: `node --test` (ella ejecuta un archivo con `--experimental-vm-modules`), `typescript ~5.6.3` y `jsdom ^25` del proyecto (ella `^5.6.3` y `26.1.0`), `cambiarEstado` en lugar de `actualizarEstado`, sin página de QA aparte.

### D17 — Precedencias resueltas
| Tema | Consigna | KB | Semilla | Referencia | Gana |
|---|---|---|---|---|---|
| Cambio de estado | `PATCH` con motivo opcional | RN-FE-10 | `PUT` con estado | `actualizarEstado` | Consigna (`PATCH`, nombre `cambiarEstado`) |
| Moneda | PES→ARS, DOL→USD, otro visible | RN-FE-06, SU-09 | siempre ARS | — | Consigna |
| URL base | meta de configuración | RN-FE-02 | `window.location` | — | Consigna |
| TXT | por URL, sin tercer botón | RN-FE-11 | — | — | Consigna (se construye `urlReporte(...,'txt')` y se prueba) |
| Monto mínimo | el formulario puede exigir ≥ 0 | US-502 | `min="0"` sin validar | — | Consigna/KB (validación propia) |
| Pruebas | `npm test` | KB 08: `test/app.test.mjs` | — | un solo archivo | Varios archivos `*.test.mjs` (organización; no contradice la consigna) |

### D18 — Estilos y contraste
`styles.css` con una paleta de pocos colores verificada a mano contra fondo blanco (texto ≥ 4,5:1, bordes de controles y foco ≥ 3:1), `:focus-visible` con contorno de 3 px, estados de fila y mensajes con texto además de color, clase `.visualmente-oculto`, objetivos de botón ≥ 44 px de alto (WCAG 2.5.5 de la skill `accessibility-review`) y diseño que se adapta a 200 % de zoom sin desplazamiento horizontal salvo la tabla (envuelta en un contenedor con desplazamiento propio y `tabindex="0"` + `aria-labelledby` para poder desplazarla con teclado). Los cocientes de contraste calculados se registran en el informe.

### Dependencias del contrato de etapa 4
El change C-05 está en curso (no archivado) al proponer este. Lo que el frontend toma de su delta y lo que queda abierto:
- **Tomado como contrato**: rutas y códigos (`POST` 201 + `Location` + `FacturaReporteDTO`; `PUT` y `PATCH /{id}/estado` 200 + `FacturaReporteDTO`; `DELETE` 204 sin cuerpo; 400 con `errores` por ruta `detalles[i].campo`; 404 por referencia; 409 por estado o importe no representable); nombres de campos del request (`listaPrecioArticuloId`, `cantidad`, `porcentajeBonificacion`, `descripcion`, `fechaEmision` opcional); `PUT` como reemplazo completo; CORS con `Content-Type` y `X-Usuario`.
- **Abierto 1 — IN-01**: estado desconocido en `PATCH` (400 implementado, 409 en la referencia). No afecta al frontend: solo envía `EMITIDA` o `ANULADA` y muestra cualquier error como `ApiError`.
- **Abierto 2 — varios errores en un campo**: la etapa 4 une los mensajes con `; ` en un solo valor (`Map<String,String>`). El frontend lo muestra tal cual; si cambiara a lista, solo cambia la lectura de `errores` en `ApiError` (una prueba).
- **Abierto 3 — `Content-Type` de los errores**: `application/problem+json` según la etapa 3; el frontend no depende del tipo (lee `text()` y prueba `JSON.parse`), así un 500 de Tomcat o un 502 de un proxy también se manejan.
- **Abierto 4 — fecha en el `PUT`**: si el request omite `fechaEmision`, la etapa 4 pone la fecha actual (reemplazo completo). El frontend reenvía la fecha cargada (D9) para no cambiarla sin querer.
- **Abierto 5 — `porcentajeBonificacion` en el detalle**: es `double` no nulo (0 si se omitió); al cargar para editar se muestra `0`. Sin impacto.
- Si alguna de estas piezas cambia al archivar la etapa 4, se ajusta en el apply de este change antes del recorrido manual; las pruebas automáticas del frontend no dependen del backend.

## Risks / Trade-offs

- [C-05 no archivado: el contrato de escritura puede moverse] → el frontend solo depende de lo listado en «Dependencias del contrato de etapa 4»; el grupo de integración manual se ejecuta recién con el apply de la etapa 4 terminado y su `./gradlew test` en verde; si algo difiere, se ajusta y se documenta.
- [Otro agente aplicando la etapa 4 en el mismo árbol] → este change no toca `src/`, `build.gradle` ni `docs/etapa-4.md`; las tareas que ejecutan Gradle o levantan el backend esperan a que ese apply termine.
- [Diferencias de `Intl` entre Node y navegadores] → las pruebas fijan `es-AR` con ICU completo de Node 24 y el recorrido manual confirma el formato en el navegador; el informe declara el entorno.
- [Estimación neta en `number`] → rotulada como estimación; el importe definitivo es el que devuelve el backend tras guardar.
- [`window.confirm`/`prompt` bloquean el hilo y no se pueden estilizar] → aceptado por simplicidad y accesibilidad nativa; aislados detrás de `Dialogos`.
- [jsdom no demuestra lectores de pantalla ni foco visual] → revisión manual de teclado, contraste y zoom (grupo 12) y declaración del límite en el informe.
- [`node --test` ejecuta todo lo que haya bajo `test/`] → soporte en `soporte-pruebas/`; la tarea 1.1 confirma el patrón con Node 24.
- [Pruebas de carreras frágiles por microtareas] → promesas controladas + `esperarTareas()` tras cada resolución; ningún `setTimeout` real.
- [Puertos 8080/5173 ocupados tras la prueba manual] → la tarea de integración detiene ambos servidores y verifica con `netstat` que quedan libres.

## Migration Plan

Sin migración de datos. `index.html`, `styles.css` y `app.ts` se reescriben dentro de `frontend/`; `config.ts`, `package.json`, `package-lock.json` y `tsconfig.json` no cambian. Rollback: volver al tag `etapa-4`.

## Open Questions

- **Evidencia binaria** (no bloquea): las descargas de ejemplo y capturas del recorrido se guardan en `docs/evidencia/etapa-5/` (versionadas, pocos KB). Si el estudiante prefiere no versionar binarios, basta con moverlas a `build/` y citarlas en el informe; no cambia ninguna tarea salvo la ruta.
- **IN-01** heredada de la etapa 4 (no bloquea este change).
