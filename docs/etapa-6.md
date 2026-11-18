# Etapa 6 - Requisitos verbales del docente (change `etapa-6-requisitos-docente`)

Cambios pedidos por el docente en clase el 2026-11-18 (transcriptos en `docs/consigna/requisitos-docente-2026-11-18.md`): paginación del listado de facturas, filtros por número, cliente e informada, explorador modal para elegir cliente y artículo, y PDF por factura. ARCA sigue simulada. El detalle normativo está en `openspec/changes/etapa-6-requisitos-docente/` (design, specs y tasks).

## Origen del pedido y supuestos

Pedido verbal, sin tag oficial confirmado. Supuestos adoptados (a confirmar con el docente): ARCA simulada alcanza; tamaño de página por defecto 10 (máximo 100); «informada» = tiene CAE (incluye ANULADA, que conserva su CAE).

## Cambio de contrato

**BREAKING** respecto de la etapa 3: `GET /api/facturas` ya no devuelve un arreglo sino un sobre paginado. El tag `etapa-3` conserva el contrato anterior; el frontend, `requests.http`, las pruebas y el README se actualizaron en el mismo change.

```json
{"items":[{"id":4,"numeroFactura":1,"fechaEmision":"2026-10-29","clienteDenominacion":"Juan Servicios","condicionIva":"Consumidor Final","puntoVentaDescripcion":"Sucursal Norte","importeTotal":9680.0,"cantidadItems":1,"estado":"EMITIDA","codigoMoneda":"DOL"}],"pagina":0,"tamanio":2,"totalElementos":4,"totalPaginas":2}
```

Las filas (`items`) conservan los diez campos de la etapa 3. El reporte (`/api/facturas/reporte`) mantiene su forma: suma los filtros nuevos e ignora `pagina` y `tamanio`. El detalle agrega `clienteCuitCuil` (aditivo).

## Backend

### Filtros

Siete filtros opcionales, combinados con `AND`, validados en un solo lugar (`FiltroFacturas.desde`) y compartidos por listado y reporte.

| Parámetro | Regla | Fragmento JPQL (constante) |
|-----------|-------|----------------------------|
| `fechaDesde`, `fechaHasta`, `estado`, `montoMinimo` | sin cambios (etapa 3) | los de la etapa 3 |
| `numero` | entero ≥ 1 (si no, 400 que nombra `numero`); cualquier punto de venta | `f.numero = :numero` |
| `clienteId` | uno inexistente da resultado vacío; Consumidor Final no coincide | `c.id = :clienteId` |
| `informada` | texto `true`/`false` (se recorta y pasa a minúsculas; vacío = ausente; otro valor, 400) | `f.cae IS NOT NULL` / `f.cae IS NULL` |

`informada` llega como `String` porque el conversor de Spring aceptaría `yes`, `on` o `1`. `FiltroFacturas` y `FacturaFiltrosDTO` conservan un constructor de cuatro argumentos para que las pruebas de las etapas 2 y 3 sigan compilando. El `WHERE` y la asignación de parámetros viven en `clausulaWhere` y `asignarParametros`, compartidos por reporte, página y conteo.

### Paginación

- `pagina` (base 0, por defecto 0) y `tamanio` (por defecto 10, máximo 100), validados por `SolicitudPagina.de`; una página posterior a la última devuelve `items: []` con el total real. `totalPaginas = ceil(total / tamanio)` en `long`; el desplazamiento también se calcula en `long`.
- **El conteo es una consulta aparte**: la proyección usa `LEFT JOIN f.detalles` y `GROUP BY`, así que contar sobre ella devolvería un conteo por grupo. El conteo es `SELECT COUNT(f) FROM FacturaVenta f LEFT JOIN f.cliente c [WHERE ...]`; la página usa la misma JPQL del reporte con `setFirstResult`/`setMaxResults` (`LIMIT/OFFSET` en SQL). El orden `fechaEmision DESC, id DESC` es total, por eso las páginas no se solapan.
- Se descartó `Pageable`/`Page`: la consulta ya es JPQL a mano con proyección por constructor.

### Búsqueda en catálogos

`GET /api/catalogos/clientes?q=` (denominación o CUIT/CUIL) y `.../lista-precio-articulos?q=` (código o denominación del artículo, o código de lista). Sin `q` el catálogo sigue completo. `PatronBusqueda.de(q)` recorta, pasa a minúsculas, escapa `\`, `%` y `_` y envuelve en `%…%`; más de 100 caracteres es 400 (`q` y 100). JPQL con parámetro con nombre y `escape` explícito; las ofertas usan `join fetch`. Gotcha: en un `@Query` el escape se escribe `'\\\\'` en Java para que HQL reciba `'\\'`; con `'\\'` Hibernate 6 falla («Escape character literals must have exactly a single character»). Sin tope de resultados (D4).

### PDF por factura

`GET /api/facturas/{id}/pdf`: `FacturaService.generarComprobante` (transacción de solo lectura) arma el detalle (404), rechaza BORRADOR con 409 («no fue informada a ARCA») y devuelve `factura-PPPP-NNNNNNNN.pdf`. `ComprobantePdf` es A4 vertical con identificación, cliente (CUIT solo si existe), tabla de ocho columnas con encabezado repetido, totales neto e IVA (suma de `BigDecimal`) y total guardado, CAE, vencimiento y resultado, marca `ANULADA` con fecha, observaciones opcionales y la leyenda «sin validez fiscal… CAE ficticio». Se extrajeron `FuentePdf` y `FormatoImporte`, compartidos con el reporte. Importes: `BigDecimal.valueOf` → `HALF_UP` a 2 decimales → formato es-AR, sin aritmética `double`. `VerificadorGlifos.verificarDatos` rechaza (400) un carácter sin glifo nombrando el dato y sin sugerir Excel ni TXT.

### CUIT e informadas bloqueadas

`clienteCuitCuil` se agrega a `FacturaDetalladaDTO` (un solo mapeo para detalle y comprobante). `exigirBorrador` no se tocó: se agregaron las pruebas que faltaban, `PUT` sobre ANULADA y `DELETE` sobre EMITIDA (409 y la factura queda intacta). Pasaron al escribirse (caracterización) y se validaron quitando temporalmente `exigirBorrador` (3 fallas en cada mutación).

## Frontend

- **Contrato y servicio**: `obtenerFacturas(filtros, pagina)` recibe siempre la página; `consultaFiltros` incluye `numero`, `clienteId` e `informada` y no la paginación (por eso `urlReporte` no lleva `pagina`/`tamanio`); `buscarClientes`, `buscarListaPrecioArticulos` (omiten `q` en blanco) y `urlPdfFactura`.
- **Una sola puerta del listado** (`ejecutarConsulta`): buscar, paginar, cambiar tamaño y recargar tras una acción comparten el `ControlSolicitudes` de la etapa 5; se descarta toda respuesta obsoleta y los controles se bloquean durante el pedido. La recarga conserva filtros y página; si la página quedó fuera de rango pide `totalPaginas - 1` una sola vez.
- **Paginación en la UI**: `<nav aria-label="Paginación de facturas">` estático con «Anterior»/«Siguiente», indicador `aria-current="page"` («Página X de Y») y selector de tamaño 10/20/50 con `label`; el cambio de página se anuncia en la región de estado. La cantidad sale de `totalElementos`.
- **Filtros nuevos**: número (entero ≥ 1), «Informada a ARCA» (`''`/`true`/`false`) y cliente con `<output>` + «Elegir cliente para filtrar» + «Quitar cliente del filtro» (el id va en `data-id`). Elegir un cliente no busca hasta pulsar «Buscar».
- **Explorador modal** (`ui/explorador.ts`): un único `<dialog id="explorador">` estático; abstracción inyectable `VentanaModal { abrir, cerrar }` (producción: `showModal()`/`close()`; pruebas: un doble que alterna `open`, porque jsdom no implementa `showModal`). Busca al enviar el formulario de búsqueda, descarta respuestas viejas, muestra errores en `role="alert"`, resuelve `null` con «Cancelar» o Escape y devuelve el foco al disparador. Sirve para el cliente del formulario, el cliente del filtro y el artículo de cada renglón (`<output>` + botón «Elegir artículo (renglón N)»); al editar un borrador las etiquetas salen de los catálogos o, si no figuran, del detalle.
- **Imprimir PDF**: enlace nativo `<a href>` en filas EMITIDA y ANULADA, sin `fetch`; las informadas no ofrecen editar ni eliminar.

### Pruebas existentes que cambiaron

Ninguna aserción se debilitó: solo cambia lo que impone el contrato.

| Archivo | Cambio | Motivo |
|---|---|---|
| `FacturaRestControllerTest`, `CorsConfigTest`, `CorsConfigPropiedadTest` | stubs `listar(any(), any(), any())` y página vacía; el listado verifica el sobre; el detalle lleva el CUIT | sobre paginado y `clienteCuitCuil` |
| `FacturaServiceTest` | `listar` usa `buscarPagina` + `contar`; se agregan página inválida y posterior a la última | paginación |
| `FacturaApiIntegracionTest`, `FacturaEscrituraIntegracionTest` | `$[i]` → `$.items[i]`, `$.length()` → `$.items.length()`; el listado vacío espera el sobre vacío | sobre paginado |
| `CatalogoRestControllerTest` | los stubs reciben `q` | búsqueda |
| `cliente-http`, `consulta`, `formulario` (frontend) | `obtenerFacturas` con página y `?pagina=0&tamanio=10`; recarga con `[filtros, {pagina:0,tamanio:10}]` | contrato nuevo |
| `soporte.mjs`, `formulario`, `edicion`, `accesibilidad` (frontend) | `servicioFalso` con página por defecto, `ventanaModalFalsa()`; cliente y artículo se eligen por el explorador y se leen por `data-id` | cliente y artículo ya no son `<select>` |

## Pruebas, mutaciones y costuras

- Backend: unitarias puras (`FiltroFacturasTest`, `SolicitudPaginaTest`, `PaginaDTOTest`, `PatronBusquedaTest`), `@DataJpaTest` (repositorio y catálogo), `ComprobantePdfTest` (texto con `PdfTextExtractor`, `Clock` fijo), `@WebMvcTest` y la integración con `open-in-view=false`. Costuras: constructor de cuatro argumentos, `Clock`, `FuentePdf` compartida y un espía del `EntityManager` para el desplazamiento mayor que `Integer.MAX_VALUE`.
- Frontend: jsdom con `servicioFalso`, `temporizadorManual`, diferidos y `ventanaModalFalsa()`.
- **Caracterización validada con mutación temporal (todas revertidas)**: backend — `informada` en blanco, `PaginaDTO` con total 0, desplazamiento enorme, los 13 casos de `FacturaPaginacionIntegracionTest` (desplazamiento fijo en 0, `clienteId` o `informada` sin pasar), 6.2 y 6.3, el PDF (A4 rotado, CUIT siempre visible, `HALF_UP`→`DOWN`, observaciones siempre visibles, condición de ANULADA invertida, verificador de glifos omitido, fuente `WINANSI`, chequeo de BORRADOR desactivado), CORS (mapeo `/api/x/**`). Frontend — contador y mensaje sin datos, `aria-current`, recarga siempre en la página 0, sin rama de página fuera de rango, explorador sin control de vigencia, sin `preventDefault` en `cancel`, sin `focus()` al cerrar, sin precio, sin `::backdrop`, botón de editar en informadas, PDF en BORRADOR.
- **Desvío del ciclo TDD (declarado)**: por el apuro, en el frontend los tests de cada grupo se escribieron juntos y su rojo se vio en la misma corrida (por aserción, contra un esqueleto o contra el comportamiento viejo) en lugar de un rojo/verde por tarea; el backend sí tuvo un RED por tarea. En 11.6 el código de recarga ya existía, así que esas pruebas son de caracterización.

## Recorrido manual con ambos lados reales (2026-11-18)

Backend real (`build/libs/facturacion-arca-1.0.0.jar`, JDK 17, H2 con el seed) en 8080 y frontend con `npm run dev` en 5173, en el navegador integrado (Chromium, 1280 px). Se crearon por `curl` ocho borradores extra para tener dos páginas (14 facturas). Capturas y descargas de muestra en `docs/evidencia/etapa-6/` (7 capturas, `factura-3-anulada.pdf`, `factura-15-emitida.pdf`, `reporte-filtrado.pdf|xlsx|txt`, `reporte-todos.txt`).

| Qué | Dónde se verificó |
|---|---|
| Explorador con `showModal()` real (foco inicial, inercia del fondo, Tab dentro del diálogo, Escape, «Cancelar», elegir, foco de regreso) | Navegador real |
| Filtros por cliente (explorador) + informada, número 1 y 0, fechas invertidas (400 visible) | Navegador real |
| Paginación («Página 1 de 2» → «Página 2 de 2 (14 facturas)», tamaño 20, nueva búsqueda vuelve a la página 0) | Navegador real |
| Alta, edición, emisión, anulación y eliminación con el explorador; 409 al emitir una fila vieja | Navegador real (`confirm` y `prompt` **simulados**) |
| Varias cargas de filtros y paginación | Navegador real, disparadas con `requestSubmit()`/eventos desde JavaScript, no con teclado ni ratón |
| `href` de «Imprimir PDF» y de las descargas con los filtros vigentes (sin `pagina`/`tamanio`) | Navegador real (solo el `href`) |
| PDF de la ANULADA 3 y de la EMITIDA 15 (número, totales, CAE, marca `ANULADA`, leyenda); reportes con filtros (TXT: 1 fila filtrada, 14 sin filtros) | `curl` + extracción de texto |
| Doble clic, respuestas obsoletas, errores por campo, bloqueo durante el guardado, recarga fuera de rango | Solo jsdom |

**No se observó la descarga real del archivo en el navegador** (el navegador integrado no muestra descargas): solo se comprobó el `href` y que `curl` a esa URL devuelve el archivo.

### Hallazgos del recorrido y su corrección (frontend, con TDD)

1. **Escape tardaba dos pulsaciones**: el campo era `type="search"` y Chrome lo vacía con la primera Escape. RED: prueba que exige `type="text"` y `enterkeyhint="search"`; GREEN en `index.html`. Verificado: una Escape cierra y devuelve el foco.
2. **«Sin moneda 8.000,00» en el precio del artículo**: el catálogo `lista-precio-articulos` trae `codigoMoneda` nulo. RED con `codigoMoneda: null`; GREEN: sin código se muestra el número formateado (captura 04 antes, 05 después).
3. **El total estimado (`<output>`) no tenía `<label>`**: lo detectó la prueba de accesibilidad; corregido con `<label for="total-estimado">`.

## Accesibilidad

Pruebas automatizadas (`accesibilidad.test.mjs`): nombre accesible del diálogo, `label` del campo, `role="alert"`, accionables nativos sin `tabindex` positivo, nombres de los botones que abren exploradores (cliente, filtro, artículo de los renglones 1 y 2), `nav` con nombre y `aria-current`, orden de tabulación y estilos con solo colores de la paleta y objetivos de 2,75 rem.

Contrastes **calculados** con la fórmula de WCAG sobre el CSS (no medidos en pantalla): texto sobre fondo 17,40; primario sobre blanco 6,67 (resultados, «Cancelar», «Imprimir PDF», «Buscar»); borde `#767676` 4,54 (mínimo 3); anillo de foco 6,67; error 8,14; texto apagado sobre `#e6e6e6` 6,77. Todos cumplen AA.

Por principio: **Perceptible** (1.3.1: único incumplimiento, el `<output>` sin `label`, Menor, corregido); **Operable** (2.1.2: el foco no escapa del diálogo; Mayor corregido: Escape; 2.4.7 con `:focus-visible` de 3 px; objetivos ≥ 24 px); **Comprensible** (3.3.1 con `aria-invalid`/`aria-describedby`; 3.2.2 el filtro de cliente no dispara búsquedas); **Robusto** (4.1.2 con `<dialog>` nativo; 4.1.3 con la región de estado y `aria-live`). Menores sin tocar: el anuncio de página usa el estilo verde de éxito y «Siguiente» no nombra la página destino.

**No verificado**: lector de pantalla, zoom real (1.4.4), reflujo (1.4.10) y contraste medido en pantalla.

### Checklist para el estudiante

- [ ] Solo teclado: Tab y Mayús+Tab por filtros, descargas, grilla, paginación y formulario, sin trampas y con foco visible.
- [ ] Explorador con Enter o Espacio sobre «Elegir cliente», búsqueda con Enter, Tab por los resultados, elegir con Enter o Espacio y foco de regreso al botón.
- [ ] Escape cierra el explorador con y sin texto escrito (una sola vez) y devuelve el foco.
- [ ] Lector de pantalla (NVDA): diálogo «Buscar cliente», campo «Texto a buscar», cantidad de resultados, errores, «Página 2 de 2 (14 facturas)» y botones con su nombre completo.
- [ ] Zoom al 200 % y al 400 %: sin desplazamiento horizontal y con el diálogo utilizable.
- [ ] «Imprimir PDF» de una EMITIDA y de una ANULADA descargan el archivo en el navegador.
- [ ] Cancelar el explorador con Escape mirando la pestaña Red: no sale ningún pedido nuevo.
- [ ] Contraste del diálogo y de los botones deshabilitados con una herramienta.

Resultado (2026-11-18): el estudiante cerró la etapa **sin hacer esta revisión manual**. Ninguno de los ítems de la lista fue probado por él: recorrido solo con teclado, explorador con Enter y Espacio, Escape con y sin texto, lector de pantalla, zoom al 200 % y 400 %, descarga real de «Imprimir PDF», cancelación con Escape mirando la pestaña Red y contraste con una herramienta del navegador. Lo único verificado es lo que declara este informe como automático (jsdom y contrastes calculados desde el CSS) y lo que la IA comprobó en el navegador integrado durante el recorrido: explorador con `showModal()` real, foco de ida y vuelta, Escape, Cancelar y fondo inerte. Hallazgos del estudiante: ninguno, por no haberla hecho.

## Semilla reutilizada y referencia del docente

`api-facturas` (semilla) solo tiene un `GET /export/pdf` del listado, sin paginación, sin modales ni PDF por factura: no hay nada que reutilizar y no se copió código. En `arca-spirng` (docente) no hay paginación, explorador modal ni PDF por factura; se consultó solo para confirmar nombres de campos (`cae`, `caeFechaVencimiento`). No se volvió a leer el código de ninguno durante el apply; se confía en el inventario del design (D15 y D16).

## Límites conocidos y preguntas abiertas

- **Limitaciones**: catálogos sin tope de resultados y el explorador no pagina; búsqueda sensible a acentos; página y conteo son dos consultas (el total puede desfasarse en un elemento ante una inserción concurrente); el enlace de PDF no maneja errores en la página (un 400 por glifo o un 404 se vería como Problem Details en la pestaña); tras un 409 al emitir una fila vieja la tabla no se recarga (comportamiento de la etapa 5); **el catálogo `lista-precio-articulos` trae `codigoMoneda` nulo**, por eso el explorador muestra el precio sin moneda (no se cambió el backend); no se miró el PDF en un visor, solo texto extraído, tamaño y orden.
- **Preguntas para el docente**: ¿qué tag o versión se evalúa? ¿Alcanza ARCA simulada? ¿Sirve el tamaño de página 10? ¿«Informada» incluye ANULADA? ¿Hace falta un botón TXT en pantalla? Confirmar también IN-01 (estados) e IN-03 (PDF con glifo no soportado).
- **Desviaciones**: ninguna respecto del design, salvo la del ciclo TDD del frontend declarada arriba; `GlifoNoSoportadoException` ganó un segundo constructor para el comprobante.

## Verificaciones y entorno

| Verificación | Resultado |
|---|---|
| Backend base (1.1) | 503 pruebas en verde |
| `./gradlew clean test bootJar`, JDK 17 (18.1) | ver «Cierre» más abajo |
| Frontend `npm test && npm run build` (18.2) | ver «Cierre» más abajo |
| `requests.http` contra el jar real (8.1) | OK; se corrigieron dos ejemplos (`q=2012` y la lista `MAYORISTA`, que no existe en el seed) |
| `openspec validate etapa-6-requisitos-docente --strict` (18.4) | ver «Cierre» más abajo |
| Recorrido manual (15.x) | navegador real vs jsdom/`curl`, ver tabla del recorrido |

### Cierre (2026-11-18)

- `./gradlew clean test bootJar` con JDK 17: **620 pruebas de backend en verde** (suma de los `testsuite`, 0 fallas, errores ni omitidas); jar `build/libs/facturacion-arca-1.0.0.jar`.
- `cd frontend && npm test && npm run build`: **244 pruebas en verde** (151 de base); `tsc` estricto sin errores.
- Reglas duras: sin `innerHTML|outerHTML|insertAdjacentHTML|document.write` en `frontend/src`; sin `@Data` en `src/main`; sin `Math.round` en código (solo comentarios que dicen que no se usa); `"strict": true` en `tsconfig.json`; sin aritmética `double` en `ComprobantePdf` (solo `BigDecimal`); JPQL con parámetros con nombre y fragmentos constantes; dependencias de `build.gradle` y `package.json` sin cambios de nombre ni versión.
- `openspec validate etapa-6-requisitos-docente --strict`: válido.
- Puertos 8080, 5173 y 5433 libres al cerrar.

## Uso de IA

- Herramientas: Claude Code (Anthropic) con sub-agentes de OpenSpec (propuesta, diseño, tareas y ejecución de la etapa, con un agente para el backend y otro para el frontend).
- Qué se pidió: la propuesta y el diseño de la etapa, las pruebas en rojo, el código mínimo para pasarlas, las mutaciones de validación de las pruebas de caracterización, el PDF por factura, el explorador modal, el recorrido manual con ambos lados reales, la revisión de accesibilidad automatizada, `requests.http`, el README y este informe.
- Qué se revisó y qué se corrigió: revisé la propuesta antes de implementar y trabajé la etapa con tres puntos de revisión obligatorios, en los que el avance se detenía hasta que yo lo aprobara: el contrato paginado con los filtros nuevos, el PDF por factura, aprobado con los PDF de ejemplo que generó la aplicación, y el explorador modal. Con una guía de revisión que me armó la IA leí y verifiqué la validación única de filtros compartida con los reportes, el WHERE con parámetros nombrados, la consulta de página y la de conteo separadas, el cambio de contrato del listado, el buscador con escape de comodines, el rechazo del PDF de un borrador, el explorador con su foco, Escape y control de respuestas viejas, la paginación accesible y el enlace de impresión. El recorrido en un navegador real encontró tres defectos del frontend, que se corrigieron con pruebas: Escape tardaba dos pulsaciones por el tipo de campo, el precio de los artículos mostraba una moneda inexistente y el total estimado no tenía etiqueta. La IA declaró lo que no pudo verificar: los diálogos nativos de confirmación, la descarga real del PDF, el lector de pantalla y el zoom. Quedan pendientes con el docente qué tag se evalúa, que ARCA sea simulada, el tamaño de página, y el 400 para un estado desconocido y para un glifo no soportado.
- Los commits propuestos llevan la línea `Co-Authored-By`.
