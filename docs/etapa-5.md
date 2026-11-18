# Etapa 5 - Frontend en TypeScript

Cliente web sin framework (HTML, CSS y TypeScript estricto con módulos ES2020) que consume la API de las etapas 3 y 4: consulta con filtros, alta y edición de borradores, emisión, anulación, eliminación y descargas. Este informe resume qué se decidió y qué se comprobó; el detalle normativo está en `openspec/changes/etapa-5-frontend-typescript/` (design y specs).

## Cliente HTTP y DTO

- `frontend/src/models/factura.dto.ts` espeja los records Java (mismos nombres y campos). Los requests de alta y edición **no tienen** campos de precio ni de importe: el compilador impide armarlos y, además, `FacturaService` copia solo los campos del contrato antes de serializar (`soloContrato`), de modo que un objeto armado a mano con `precioUnitario` o `importeTotal` tampoco los envía.
- `FacturaService(baseUrl, fetchInyectado)`: la URL base sale del `<meta name="api-base-url">` (sin barra final), `fetch` se inyecta (en el navegador `app.ts` pasa `(url, init) => fetch(url, init)`: llamar a `window.fetch` desligado falla en algunos navegadores). Operaciones: `obtenerFacturas`, `obtenerFactura`, `obtenerCatalogos` (cinco pedidos en paralelo), `crearFactura`, `actualizarFactura` (PUT), `cambiarEstado` (PATCH `/estado`), `eliminarFactura`, `urlReporte`.
- **Encabezados**: siempre `X-Usuario: sistema` (declara quién opera, no autentica); `Content-Type: application/json` solo si hay cuerpo; ningún otro (no se envía `Accept`). CORS de la etapa 4 admite exactamente `Content-Type` y `X-Usuario`, y `GET/POST/PUT/PATCH/DELETE` desde `localhost:5173` y `127.0.0.1:5173`; cualquier otro encabezado haría fallar el preflight.
- **`ApiError`** (`services/ApiError.ts`): una sola clase con `status`, `titulo` y `errores` (mapa campo → mensaje). Mensaje = `detail`; si no hay, `title`; si no, `El servidor respondió <status> sin un detalle legible.` El cuerpo de un error se lee con `text()` y `JSON.parse` dentro de `try` (nunca `json()` directo: un 502 de un proxy trae HTML). `errores` se descarta entero si algún valor no es texto. Una falla de red es `ApiError` de estado 0 con la URL base en el mensaje.
- **204**: se resuelve en `undefined` sin leer el cuerpo.
- `consultaFiltros` (pura) arma la query en orden fijo y omite vacíos; la usan el listado **y** `urlReporte`, así los enlaces de descarga no pueden diverger de la consulta. El TXT no tiene botón: queda disponible con `urlReporte(filtros, 'txt')`.

## Consulta y filtros

Al abrir la página se piden los cinco catálogos y el listado sin filtros en paralelo; un catálogo que falla muestra el error y el listado se presenta igual. Filtros: fecha desde/hasta, estado y monto mínimo (≥ 0, finito; `0` es válido). Las fechas invertidas **no** se validan en el cliente: las rechaza el backend con 400 (sirve como error controlado de la API). Tabla, contador (`N facturas`, `1 factura`) y los dos enlaces (PDF, Excel) se actualizan en una sola función (`aplicarResultado`) con una misma respuesta; si una búsqueda vigente falla se conserva lo anterior y se muestra el error. Las recargas posteriores a guardar, emitir, anular o eliminar usan los **filtros aplicados** (los del último resultado mostrado), no lo que esté tipeado.

## Formato de moneda y fechas

- `PES` → `Intl.NumberFormat('es-AR', ARS)`, `DOL` → USD, cualquier otro código (incluido `null`) queda visible: `EUR 100,00` / `Sin moneda 100,00`. Nunca hay conversión entre monedas (SU-09): 9680 en `DOL` se muestra `US$ 9.680,00`.
- Gotcha: `Intl` con `es-AR` separa símbolo y número con espacio duro (U+00A0) y muestra el dólar como `US$`; las pruebas lo escriben como `\u00a0` y se confirmaron los literales en Node 24 antes de fijarlos.
- Las fechas `yyyy-MM-dd` se muestran partiendo el texto (`2026-11-01` → `01/11/2026`). **Nunca** `new Date('yyyy-MM-dd')`: lo interpreta como medianoche UTC y en Argentina (UTC−3) muestra el día anterior. `grep` de `new Date` en `frontend/src` solo encuentra un comentario.

## Formulario y validaciones

Dos formularios con `novalidate`: los atributos `min`, `max`, `maxlength` quedan como ayuda semántica, pero los mensajes los produce siempre `ui/validacion.ts` (puro, recibe texto crudo y devuelve `{ datos?, errores }` con claves en la **misma notación que la API**: `puntoVentaId`, `detalles[1].cantidad`, `observaciones`). Así un único mecanismo (`Formulario.mostrarErrores`) pinta errores locales y del servidor. Reglas: cantidad finita > 0 (`1e400` es inválida), bonificación vacía u 0–100, descripción ≤ 255, observaciones ≤ 500, motivo de anulación ≤ 500 (se mide `string.length`, igual que `@Size`), cabecera obligatoria (condición de IVA, punto de venta, moneda), al menos un renglón. Cliente vacío = Consumidor Final (se omite `clienteId`); fecha vacía = se omite (el backend usa hoy).

Cada renglón es un `fieldset` con `legend` «Renglón N»; los `id` salen de un contador monotónico (no del índice) para que `label for` siga siendo único al quitar; las etiquetas dicen «Cantidad (renglón 2)» y se renumeran. Tras quitar, el foco va al renglón siguiente o, si no hay, a «Agregar renglón». Ante errores el foco va al primer campo inválido (después de re-habilitar el `fieldset`: un elemento deshabilitado no recibe foco).

**Estimación**: total neto sin IVA = Σ cantidad × precio × (1 − bonificación/100), rotulado como estimación no vinculante. Es aritmética `number` del navegador y puede diferir en centavos del neto del servidor (que redondea por renglón con `HALF_UP`); el importe definitivo es el que devuelve el backend (en el recorrido: estimado 13.400,00 neto, servidor 16.214,00 con IVA 21 %). Nada de eso se envía.

**Edición**: se carga el detalle con sus ids y valores, el título pasa a «Editar borrador N» y el foco va al título. Se reenvía la `fechaEmision` cargada (dependencia abierta 4 de la etapa 4: si el PUT la omite, el servidor pone la de hoy). Cancelar descarta cambios y vuelve al modo alta sin ninguna escritura. Un detalle que llega como EMITIDA/ANULADA muestra error y no entra en edición.

**Conservación y errores**: si el guardado falla, valores y modo se conservan y se muestra el mensaje; los errores `detalles[i].campo` se marcan en el campo del renglón i; una clave sin campo visible se lista en la región de estado junto al detalle.

## Control asíncrono

- `ControlSolicitudes` (ids): `iniciar()` incrementa y devuelve, `esVigente(id)` compara con el último, `invalidar()` incrementa sin emitir. Dos instancias: búsquedas y carga de edición. Toda continuación `await` comprueba `esVigente` **antes** de tocar el DOM o la región, en éxito y en error (un error tardío de una búsqueda vieja no se muestra). Se eligió ids y no `AbortController` solo: cancelar la red no garantiza que una promesa ya resuelta no escriba; se pueden combinar sin cambiar la regla.
- **Bloqueo durante el guardado**: `aria-busy="true"` en el formulario, `fieldset` principal deshabilitado (cabecera, renglones, agregar/quitar, guardar, cancelar) y acciones de fila deshabilitadas; se liberan en `finally` tanto con éxito como con error. Un segundo submit mientras se guarda se ignora (segunda línea de defensa, además del `fieldset` deshabilitado).
- **Acciones de fila**: mientras una está en curso se deshabilitan todas las acciones y el botón de guardar; un doble clic envía una sola petición. La recarga del listado se dispara después de liberar el bloqueo.

## Acciones por estado

| Estado | Acciones |
|---|---|
| BORRADOR | Editar, Emitir, Eliminar |
| EMITIDA | Anular |
| ANULADA | ninguna |

Cada botón es nativo (`type="button"`) con nombre accesible «acción + objeto + id» (`Emitir borrador 6`: texto visible más un `span.visualmente-oculto`). Un único listener delegado en el `tbody`. **Diálogos**: `confirm` y `prompt` nativos detrás de la interfaz `Dialogos` (modales, operables con teclado y anunciados por los lectores de pantalla, sin manejo de foco propio; jsdom no los implementa, otra razón para inyectarlos). Se descartó un `<dialog>` propio: más código de foco y de cierre con Escape, y jsdom no implementa `showModal`; queda como mejora posible. Cancelar la confirmación o el motivo (`null`) no envía **ninguna** petición; un motivo vacío o de espacios se anula **sin** `observaciones`; más de 500 caracteres muestra error y no envía.

## Región de estado y DOM seguro

`#estado` (`role="status"`, `aria-live="polite"`) está en el HTML desde la carga, nunca se retira del DOM y solo cambia su contenido. Se eligió `status`/`polite` y no `role="alert"` porque la consigna pide una región persistente y `alert` interrumpe al lector. El éxito se limpia a los 6 s (la limpieza pendiente se cancela si llega otro mensaje); el error es persistente hasta la siguiente acción del usuario, lleva el prefijo visible «Error:» (texto, no solo color) y los detalles por campo sin destino se listan en una `ul`; el progreso («Guardando borrador…») no se limpia solo. Toda acción iniciada por el usuario llama primero a `limpiar()`; las recargas automáticas no.

**Política sin `innerHTML`**: todo texto externo entra por `textContent` / `new Option` (`ui/dom.ts`); la estructura fija vive en `index.html`. `grep -rnE "innerHTML|outerHTML|insertAdjacentHTML|document\.write" frontend/src` no devuelve nada (solo se usa `insertAdjacentElement`, que no interpreta HTML). Las pruebas cubren `<img src=x onerror=alert(1)>` en una celda, `<b>Casa</b>` en una opción y `<script>x()</script>` en un error.

## Accesibilidad

Comprobado automáticamente (jsdom y cálculo sobre `styles.css`, ver `test/index.test.mjs`, `test/accesibilidad.test.mjs`, `test/consulta.test.mjs`, `test/formulario.test.mjs`):

- Todo `input`/`select`/`textarea` tiene `label` asociado; los campos de renglón incluyen «renglón N» en su nombre; ids únicos; todos los `button` con `type`; ningún `tabindex` positivo; una `h1` y un `main`; tabla con `caption` y `th scope="col"`; región `status` única y sin `hidden`.
- Errores de campo con `aria-invalid="true"` y `aria-describedby` al texto del error; al corregir y volver a validar pierden ambas marcas; foco al primer campo inválido.
- `aria-busy` verdadero durante el guardado y falso al terminar.
- Orden de tabulación = orden del DOM (filtros → descargas → tabla → formulario → guardar).
- **Contraste** calculado desde las variables CSS de `styles.css` (el cálculo vive en una prueba): texto `#1a1a1a` sobre blanco 17,40:1; texto apagado `#4d4d4d` sobre blanco 8,45:1; texto blanco sobre `#0b5cad` 6,67:1; `#0b5cad` sobre blanco 6,67:1 (botón secundario, enlaces, foco); borde de controles `#767676` sobre blanco 4,54:1 (mínimo 3:1); error `#a40000` sobre `#fdecec` 7,13:1; éxito `#0b5d1e` sobre `#e8f5e9` 7,18:1; deshabilitado `#4d4d4d` sobre `#e6e6e6` 6,77:1. Todos cumplen (texto ≥ 4,5:1; controles y foco ≥ 3:1).

Verificado por mí en un navegador real (panel de navegador integrado, Chromium) durante el recorrido: con una pulsación real de la tecla Tab el botón «Buscar» recibe foco con `:focus-visible` verdadero y contorno sólido azul (captura `03-foco-visible-tab.jpg`); la altura mínima medida de los botones es 44 px; el orden de tabulación leído del DOM coincide con el visual; ningún `tabindex` positivo.

**Límites (no se probó)**: jsdom no demuestra lectores de pantalla, foco visual ni diseño. No se probó con ningún lector de pantalla (NVDA/Narrador) ni se hizo un recorrido completo del circuito solo con teclado. El zoom al 200 % no se pudo comprobar: se emuló un viewport de 640 px y el `body`/`main` midieron 640 px sin elementos fuera de la tabla que lo excedan, pero la herramienta informó un `innerWidth` inconsistente (785 px), así que ese resultado **no** es concluyente.

### Hallazgos por principio (POUR) y severidad

| Principio | Hallazgo | Severidad | Estado |
|---|---|---|---|
| Perceptible | Estado de fila y errores con texto (`BORRADOR`/`EMITIDA`/`ANULADA`, prefijo «Error:») además de color | — | cumplido |
| Perceptible | El anillo de foco del botón principal (azul sobre azul) se distingue solo por el desfase blanco de 2 px | Menor | aceptado; el estudiante lo confirma en su recorrido |
| Operable | Tabla con `tabindex="0"` y `role="region"` para poder desplazarla con teclado | — | cumplido (comprobación de estructura; falta recorrido manual) |
| Operable | `confirm`/`prompt` nativos: Escape y Cancelar no envían nada | — | verificado con pruebas y con el navegador (respuestas simuladas, ver abajo) |
| Comprensible | Errores de campo junto al campo, con texto y asociados | — | cumplido |
| Robusto | `aria-busy`, región `status`, nombres accesibles de las acciones | — | cumplido en jsdom; falta lector de pantalla |

### Checklist para el estudiante (revisión de teclado y lector de pantalla)

Revisión del estudiante (2026-11-18), en un navegador real: recorrido completo del circuito solo con teclado, sin fallas; zoom al 200 %, sin fallas; PDF de muestra (`docs/evidencia/etapa-5/facturas-emitidas.pdf`) abierto y correcto. **No probado por el estudiante**: lector de pantalla (NVDA o Narrador); cancelar los diálogos con Escape mirando la pestaña Red (esa cancelación sí está cubierta por pruebas automáticas y por la comprobación de la IA con respuestas simuladas); el destino del foco al editar o quitar renglones por separado; y el anillo de foco del botón principal azul por separado. Hallazgos: ninguno en lo probado.

- [x] Solo con teclado (Tab, Mayús+Tab, Enter, Espacio, flechas, Escape en los diálogos): crear un borrador con dos renglones, editarlo, emitirlo, anularlo y eliminar otro borrador, sin usar el mouse.
- [x] El foco es visible en cada control (incluido el botón principal azul y los enlaces PDF/Excel) y nunca queda atrapado.
- [ ] Al entrar en edición el foco va al título «Editar borrador N»; al quitar un renglón va al siguiente o a «Agregar renglón»; con errores va al primer campo inválido.
- [ ] Con NVDA o Narrador: se anuncian el éxito («Se creó el borrador N»), el error («Error: …»), el progreso y el estado ocupado; los botones de la tabla se leen como «Emitir borrador 6»; los campos de renglón se leen con «renglón N»; los errores de campo se leen al enfocar el campo.
- [x] Zoom al 200 % real del navegador: sin pérdida de contenido ni desplazamiento horizontal fuera de la tabla; la tabla se puede desplazar con el teclado.
- [ ] Cancelar el diálogo de eliminación y el de anulación con Escape: en la pestaña Red no aparece ningún `DELETE` ni `PATCH`.
- [ ] Anotar acá los hallazgos con severidad (Crítico/Mayor/Menor).

## Pruebas y costuras

`npm test` compila y corre `node --test` sin argumentos (Node 24: nunca `node --test test/`, que ejecutaría la carpeta como archivo). El soporte (`frontend/soporte-pruebas/`: `diferido`, `temporizadorManual`, `respuesta`, `fetchFalso`, `servicioFalso`, `montarPagina`, `esperarTareas`) está fuera de `test/` y sin sufijo `.test`; se comprobó con un archivo temporal que falla que `node --test` **no** lo levanta.

Costuras declaradas: (1) `FacturaService` ↔ `fetch` simulado: atrapa URL, método, encabezados, cuerpo y traducción de respuestas; no atrapa CORS ni el backend real. (2) `iniciarAplicacion` ↔ `ApiFacturas` doble + jsdom con el `index.html` real: atrapa render, validación, bloqueo, carreras y atributos de accesibilidad; no atrapa diseño, foco real, lectores de pantalla ni contraste visual. (3) Funciones puras (`formato`, `validacion`, `estimacion`, `ControlSolicitudes`, `RegionEstado` con temporizador manual). (4) Recorrido manual en el navegador con el backend real.

Las carreras se reproducen con promesas controladas (ningún `setTimeout` real en las pruebas).

| Archivo | Pruebas |
|---|---|
| `cliente-http.test.mjs` | 25 |
| `formato.test.mjs` | 7 |
| `solicitudes.test.mjs` | 2 |
| `validacion.test.mjs` | 17 (incluye casos parametrizados) |
| `estimacion.test.mjs` | 2 |
| `region-estado.test.mjs` | 6 |
| `dom.test.mjs` | 4 |
| `index.test.mjs` / `config.test.mjs` | 11 / 4 |
| `consulta.test.mjs` | 18 |
| `formulario.test.mjs` | 18 |
| `edicion.test.mjs` | 8 |
| `acciones.test.mjs` | 14 |
| `accesibilidad.test.mjs` | 15 |
| **Total** | **151** (baseline previo: 7) |

(Los conteos por archivo cuentan los casos que Node ejecuta, no las funciones `test(...)` del fuente.)

### TDD: honestidad sobre el ciclo

- Cada ciclo se cerró viendo la RED antes de la GREEN. En varias RED la falla fue por el esqueleto vacío (p. ej. `Cannot read properties of undefined`, o `s.obtenerFactura is not a function` cuando el método todavía no existía) y no por una aserción de valor; es la falla natural de un comportamiento ausente, pero no es la «aserción» ideal que pedía la tarea. Las pruebas de `FacturaService` se escribieron por grupos de tareas (2.7–2.11 y los ciclos de formulario/edición/acciones) y se vieron todas en rojo juntas antes de implementar, no una a una.
- El esqueleto de `Formulario`, `Listado` y `aplicacion.ts` se amplió por grupo; en 6.x se implementaron `listado.ts` y `formulario.ts` mínimos junto con `aplicacion.ts` en un solo GREEN.
- **Pruebas de caracterización** (pasaron al escribirse o se escribieron después del código) y su validación por mutación temporal (siempre revertida):
  - «Sin filtros no hay `?`»: pasó al escribirse. Mutación (`texto === ''` → `false`): 3 fallas.
  - 204 sin leer el cuerpo: RED real. Mutación (quitar la rama 204): falla la prueba.
  - `ControlSolicitudes.invalidar`: pasó al escribirse. Mutación (sin incremento): 1 falla.
  - Fechas sin `new Date`: mutación tosca con `new Date(texto).toLocaleDateString('es-AR')` y `TZ=America/Argentina/Buenos_Aires`: fallan las dos pruebas de fecha (la mutación dejó variables sin definir, así que parte de la falla es `ReferenceError`; la evidencia fuerte contra `Date` es el argumento del corrimiento UTC−3, no esta mutación).
  - `index.test.mjs`: cuatro pruebas pasaron al escribirse (labels, `tabindex`, ids únicos, `h1`/`main`). Mutaciones: quitar un `label` → 1 falla; quitar `type` de un botón → 1 falla. Las de `tabindex`, ids únicos y `h1`/`main` **no** se mutaron.
  - Búsquedas obsoletas (sin `esVigente`): 2 fallas. Cargas de edición obsoletas (sin `esVigente`): 3 fallas.
  - Bloqueo (sin `finally`): fallan «Bloqueo durante el alta» y «Liberación tras error».
  - Anulación enviada aunque el diálogo devuelva `null`: 1 falla. Doble clic: solo falla al quitar **a la vez** la guarda `accionEnCurso` y el `disabled` de los botones (cada defensa por separado no basta para romper la prueba, son defensa en profundidad). Guardado bloqueado durante una acción (sin la guarda en `guardar`): 1 falla.
  - `accesibilidad.test.mjs`: escrita **después** del código. Mutación del color `--apagado` a `#aaaaaa`: 2 fallas de contraste. El resto (labels, nombres, orden de tabulación) no se mutó.

## Recorrido manual con el backend real (2026-11-18)

Entorno: Windows 11, navegador integrado de la herramienta (Chromium; versión no informada), Node v24.22.1, TypeScript 5.6.3, JDK 17.0.20.1, `java -jar build/libs/facturacion-arca-1.0.0.jar` (código de la etapa 4) con H2 y el `DataLoader` (6 facturas: 3 EMITIDA, 1 ANULADA, 2 BORRADOR), frontend con `npm run dev` en 5173. La consola del navegador, sin backend, solo mostró `ERR_CONNECTION_REFUSED` (esperado) y la región de estado «Error: No se pudo conectar con la API en http://localhost:8080/api. ¿Está corriendo el backend?»; sin errores de módulo.

**Cómo se condujo**: yo manejé el navegador con scripts que operan el DOM real de la página (rellenar campos, `requestSubmit`, `click` en los botones de la tabla) y registrar las peticiones con un envoltorio de `fetch`. `window.confirm` y `window.prompt` **se reemplazaron por funciones que devuelven lo que el caso necesita** (los diálogos nativos bloquean la automatización): lo verificado es el flujo de la aplicación y las peticiones reales contra el backend, **no** el aspecto ni la operación con teclado de los diálogos nativos reales. No se hizo ningún clic con el mouse real sobre la página, salvo la tecla Tab real para el foco.

Datos observados (H2 en memoria, se pierde al reiniciar):

| Paso | Petición | Resultado |
|---|---|---|
| Carga | `GET /facturas` + 5 catálogos | 6 facturas; fechas sin corrimiento (`17/11/2026` para 2026-11-17); `US$ 9.680,00` para la factura en `DOL` |
| Alta | `POST /facturas` → 201 | borrador **7**, cliente Juan Servicios, RI, punto 0001, dos renglones (Martillo ×2 al 10 %, descripción «Martillo grande»; Cable HDMI ×1). Estimación 13.400,00 neto (`$ 13.400,00`); el servidor devolvió total 16.214,00 (con IVA). Región: «Se creó el borrador 7.» |
| Edición | `GET /facturas/7`, `PUT /facturas/7` → 200 | título «Editar borrador 7», foco en el título, fecha 2026-11-18 reenviada; cantidad 2 → 3, estimación 16.100,00, servidor 19.481,00; vuelve a «Nuevo borrador» |
| Emisión | `PATCH /facturas/7/estado` → 200 | número **4**, CAE `76318477672593`, vencimiento 2026-11-28, resultado `A`; región: «Se emitió el borrador 7: número 4.» |
| Anulación | `PATCH /facturas/7/estado` → 200 | motivo «Error en el cliente»; estado ANULADA, fecha de anulación 2026-11-18. **Observación**: el backend guarda el motivo en `observaciones` de la factura |
| Cancelaciones | confirmación de eliminar rechazada (borrador 6); diálogo de anulación cancelado (factura 4) | **ninguna petición nueva** (comprobado con el registro de `fetch`); la fila sigue EMITIDA y los botones habilitados |
| Eliminación | `DELETE /facturas/6` → 204; luego `GET /facturas/6` → 404 | la fila desaparece, contador 6 facturas, región «Se eliminó el borrador 6.» |
| Cliente frena | descripción de 256 caracteres | campo con `aria-invalid`, mensaje «Admite hasta 255 caracteres (tiene 256).», foco en el campo, **ninguna petición**, valores conservados |
| 400 | `GET /facturas?fechaDesde=2026-11-10&fechaHasta=2026-11-01` → 400 | región «Error: fechaDesde (2026-11-10) no puede ser posterior a fechaHasta (2026-11-01).»; tabla y contador conservados (captura `02-error-400-fechas-invertidas.jpg`) |
| 409 | edición del borrador 5 + emisión del 5 por otra vía (`PATCH` directo) + guardar → `PUT /facturas/5` → 409 | región «Error: No se puede editar la factura 5: está en estado EMITIDA y solo se admite en estado BORRADOR»; formulario con los dos renglones, modo «Editar borrador 5», `aria-busy="false"` y controles habilitados |

Las capturas están en `docs/evidencia/etapa-5/`: `01-tabla-emitidas-y-formulario.jpg`, `02-error-400-fechas-invertidas.jpg`, `03-foco-visible-tab.jpg` (viewport pequeño de la herramienta).

### Descargas de ejemplo

Con el filtro `estado=EMITIDA` la tabla mostró 4 facturas y los enlaces apuntaron a `…/facturas/reporte?formato=pdf&estado=EMITIDA` y `…formato=xlsx&estado=EMITIDA`. Las muestras se bajaron con `curl` a esas mismas URL (no con un clic del navegador, que no pudo guardar archivos) y se guardaron en `docs/evidencia/etapa-5/`:

| Archivo | Contenido verificado |
|---|---|
| `facturas-emitidas.txt` | encabezado + exactamente las 4 facturas de la tabla (22990.00, 9680.00 DOL, 72600.00, 7260.00), `Content-Disposition: facturas.txt`, UTF-8 |
| `facturas-emitidas.xlsx` | hoja con 7 filas (encabezado, 4 facturas, línea en blanco y «Total facturado (EMITIDA)»), `Content-Disposition: facturas.xlsx` |
| `facturas-emitidas.pdf` | 150 070 bytes, empieza con `%PDF-1.5`, `application/pdf`. **No se revisó visualmente** (no hay `pdftoppm` en el equipo): falta que el estudiante lo abra |

## Límites de la simulación

- ARCA está simulada: el CAE (14 dígitos) es ficticio y no hay integración fiscal real.
- No hay autenticación: `X-Usuario` declara quién opera y CORS restringe orígenes de navegador; ninguno autentica.
- H2 en memoria se borra al reiniciar el backend; los datos del recorrido no persisten.
- La estimación sin IVA es informativa y no vinculante; el importe definitivo lo devuelve el servidor.
- Sin paginación, sin cobros, sin conversión de monedas.

## Semilla reutilizada y qué cambió (DD-13)

La semilla es `api-facturas/frontend` (trabajo previo individual; los TPs de JPA/JPQL fueron trabajo grupal y no se reutilizan acá). **No reabrí la semilla durante este apply**: el código se escribió de nuevo siguiendo el inventario del diseño (D15), que ya describe qué se adapta, qué es nuevo y qué se descarta; por eso no puedo afirmar línea a línea qué se parece al original. Correspondencia:

| Pieza de la semilla | Destino en esta etapa |
|---|---|
| `models/factura.dto.ts` | **Adaptado**: `numeroFactura` nulable, `codigoMoneda`, `EstadoFactura`, `listaPrecioArticuloId`; request sin `estado`, `usuarioId` ni precios; `ErrorRespuestaDTO` → `ProblemDetail`. **Nuevo**: `FacturaDetalladaDTO`, `FacturaDetalleDTO`, `OpcionDTO`, `CambioEstadoRequestDTO`, `FormatoReporte`, `Catalogos` |
| `FacturaService.ts` (`enviar<T>`, `ApiError`, 204, `URLSearchParams`) | **Adaptado**: base desde el meta, `fetch` inyectado, `X-Usuario`, `Content-Type` solo con cuerpo, cuerpo de error con `text()` y `ProblemDetail`; `PUT` de estado → `PATCH /estado`. **Nuevo**: catálogos, detalle, `actualizarFactura`, `urlReporte`, `soloContrato` |
| helpers de `app.ts` (`celda`, `boton`, `filaVacia`, delegación en `tbody`) | **Adaptado** en `ui/dom.ts`, `ui/listado.ts`, `ui/validacion.ts` |
| `app.ts` monolítico (ARS fijo, `new Date`, mensaje sin limpieza, sin control de respuestas obsoletas) | **Descartado**, reemplazado por `ui/formato.ts`, `RegionEstado`, `ControlSolicitudes` y `aplicacion.ts` |
| `index.html` | **Adaptado**: región `#estado` siempre visible, `caption` y `th scope`, renglones como `fieldset`, enlaces de descarga; se conservan el meta y `#estado-api` |

## Referencia del docente

`C:\Users\zbook\DesarrolloSoftware\arca-spirng` se consideró **solo para nombres** (carpetas `models/` y `services/`, `EstadoFactura`, `ProblemDetail`, `CambioEstadoRequestDTO`, métodos `obtenerFacturas`, `obtenerFactura`, `crearFactura`, `actualizarFactura`, `eliminarFactura`, la existencia de `ApiError`), tomados del diseño (D16). **No reabrí la referencia durante este apply** y no se copió código. Diferencias deliberadas: `node --test` en lugar de `--experimental-vm-modules`, `typescript ~5.6.3`, `jsdom ^25`, `cambiarEstado` en lugar de `actualizarEstado`.

## Dependencias de la etapa 4

El backend de la etapa 4 ya está integrado y archivado en `main` (tag `etapa-4`); el recorrido corrió contra su JAR. Se confirmó contra el backend real: `POST` 201, `PUT` 200, `PATCH /estado` 200, `DELETE` 204, 400 con mensaje, 404 por id inexistente y 409 por estado. Quedan abiertas con el docente IN-01 (estado desconocido en `PATCH`: 400 implementado, 409 en la referencia) e IN-03; **no afectan al frontend**, que solo envía `EMITIDA` o `ANULADA` y muestra cualquier error como `ApiError`. Abierto 2 (varios errores en un campo se unen con `; `): el frontend los muestra tal cual. No se verificó el preflight CORS con un navegador distinto del integrado.

## Deviaciones respecto del diseño y de los specs

- **Precios mostrados**: el spec escribe `3000,00` y `8000,00`; la página muestra `3.000,00` y `8.000,00` porque usa el mismo formato `es-AR` que el total (`13.400,00`) del mismo escenario. Las pruebas usan los literales con separador de miles.
- **Selects obligatorios**: llevan una primera opción vacía («Elegí un punto de venta», etc.) para que «sin elegir» sea posible; el cliente tiene «Consumidor Final» como opción vacía.
- **Un renglón inicial**: el formulario arranca (y se vacía) con un renglón en blanco, como pide el diseño.
- El total estimado lleva el símbolo de la moneda elegida en la cabecera; sin moneda se muestra sin símbolo.
- Se agregó `formatearDecimal` a `ui/formato.ts` (precio de catálogo sin símbolo) y una prueba de accesibilidad automatizada (`accesibilidad.test.mjs`, contraste calculado desde el CSS).
- Mientras corre una acción de fila también se deshabilita el botón «Guardar» del formulario (el diseño solo exigía bloquear la acción).
- Las capturas y descargas se versionan en `docs/evidencia/etapa-5/` (decisión del estudiante).

## Verificaciones y entorno

Entorno: Windows 11, Node v24.22.1, TypeScript 5.6.3, JDK 17.0.20.1 (`C:\Users\zbook\.jdks\ms-17.0.20.1`), fecha 2026-11-18.

- Baseline antes de empezar: `npm ci` + `npm test` → 7 pruebas en verde.
- Final: `cd frontend && npm test && npm run build` → **151 pruebas, 0 fallas**; `tsc` estricto sin errores (`"strict": true` intacto; `package.json`, `package-lock.json` y `tsconfig.json` sin cambios).
- Revisión estática: sin `innerHTML`/`outerHTML`/`insertAdjacentHTML`/`document.write` en `frontend/src`; sin `new Date` fuera de un comentario; sin `any` de tipos (las únicas coincidencias de `any` son el atributo HTML `step="any"`).
- Backend: `./gradlew clean test bootJar` con JDK 17 al cerrar: BUILD SUCCESSFUL; sumando los atributos de `build/test-results/test/*.xml` dan 503 pruebas, 0 fallas, 0 errores, 0 omitidas (el backend no se modificó en esta etapa). El JAR regenerado es `build/libs/facturacion-arca-1.0.0.jar`.
- Puertos: backend (8080) y `http-server` (5173) detenidos al terminar; `netstat -ano` no muestra ningún `LISTEN` en 8080 ni 5173.

## Uso de IA

- Herramientas: Claude Code (Anthropic) con sub-agentes de OpenSpec (propuesta, diseño, tareas y ejecución de la etapa).
- Qué se pidió: la propuesta y el diseño de la etapa, las pruebas en rojo, el código mínimo para pasarlas, las mutaciones de validación de las pruebas de caracterización, los controles asíncronos, el recorrido manual con el backend real, la revisión de accesibilidad automatizada, el README y este informe.
- Qué se revisó y qué se corrigió: revisé la propuesta antes de implementar y aprobé que las descargas de ejemplo y las capturas se guarden en `docs/evidencia/etapa-5/`. Con una guía de revisión que me armó la IA leí y verifiqué el cliente HTTP con su `ApiError` único y el manejo de la respuesta 204 sin cuerpo, el control de respuestas obsoletas con identificadores de solicitud, el bloqueo del formulario durante el guardado con su liberación en `finally`, la confirmación de borrado y el motivo de anulación, cuya cancelación no envía ninguna petición, la inserción de datos del servidor solo con `textContent`, el formato de importes por código de moneda y de fechas sin `new Date`, y la región de estado con `aria-live`. Revisé las capturas y los archivos de muestra del circuito crear, editar, emitir, anular y eliminar, que el sub-agente recorrió en un navegador real. La IA declaró lo que no pudo verificar: los diálogos nativos, el zoom al 200 %, el lector de pantalla y el PDF visual. La revisión de accesibilidad con teclado y lector de pantalla la anoto aparte en la lista de accesibilidad de este informe.
- Los commits propuestos llevan la línea `Co-Authored-By`.
