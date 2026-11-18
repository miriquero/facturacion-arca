# Design

## Context

Ver `proposal.md` (Why) y `docs/consigna/requisitos-docente-2026-11-18.md`. Estado de partida (rama `feat/etapa-6-requisitos-docente`, desde `main` con `etapa-5`): backend con 503 pruebas y frontend con 151, ambos en verde.

- **Listado y reporte** comparten `FiltroFacturas.desde(FacturaFiltrosDTO)` (única puerta de validación) y `FacturaReporteRepositoryImpl.jpql(filtro)`, que arma el `WHERE` con fragmentos constantes entre `SELECCION` y `AGRUPACION_Y_ORDEN`. La proyección usa `LEFT JOIN f.detalles d` con `COUNT(d)` y `GROUP BY`, por lo que **una fila por factura solo se garantiza después de agrupar**.
- **Catálogos**: `CatalogoService` devuelve `OpcionDTO(id, etiqueta, valor, codigoMoneda)`; clientes con `findAll(Sort.by("id"))` y ofertas con `findAllConArticuloYLista()` (join fetch).
- **PDF**: solo existe `ReportePdf` (listado A4 apaisado, fuente `NotoSansCJKsc-Regular.otf` incrustada con `Identity-H`, `VerificadorGlifos`, `Clock` inyectado). `GlifoNoSoportadoException` → `400` («PDF no disponible para estos datos», IN-03) con un mensaje que sugiere Excel/TXT.
- **Estados**: `FacturaService.exigirBorrador` ya produce `409` para `PUT`/`DELETE` sobre EMITIDA/ANULADA; la anulación conserva número, CAE y vencimiento.
- **Frontend**: `aplicacion.ts` compone `Listado`, `Formulario`, `RegionEstado`, `ControlSolicitudes` y `Dialogos` inyectables; el cliente del formulario y la opción de cada renglón son `<select>`; `ApiFacturas.obtenerFacturas` devuelve un arreglo.
- `spring.jpa.open-in-view=false`; JDK 17 por `JAVA_HOME`; sin dependencias nuevas en ningún lado.

## Goals / Non-Goals

**Goals:**
- Un único contrato de filtros (siete parámetros) validado en un solo lugar y aplicado igual al listado paginado y al reporte completo.
- Paginación estable (sin duplicados ni faltantes entre páginas) con total correcto pese al `GROUP BY`.
- Explorador modal accesible reutilizable para tres usos, testeable en jsdom.
- PDF de una factura legible, sin aritmética de punto flotante nueva, con las mismas garantías de fuente y glifos que el reporte.

**Non-Goals:**
- Integración real con ARCA/WSFE, CAI de comprobantes preimpresos, tipos de comprobante (A/B/C) o código QR fiscal.
- Paginación de catálogos o de los reportes; búsqueda insensible a acentos; ordenamiento elegible por columna.
- Cambios de esquema, de versiones o dependencias; cambios en las reglas de emisión/anulación.

## Decisions

### D1 — Filtros nuevos dentro de `FiltroFacturas`
`FacturaFiltrosDTO` agrega `numero` (`Long`), `clienteId` (`Long`) e `informada` (`String` crudo). `FiltroFacturas` agrega `numero`, `clienteId` e `informada` (`Boolean`) al final y conserva un **constructor secundario de cuatro argumentos** (nulos para los nuevos) para que las pruebas de etapas 2–3 sigan compilando sin cambios y sirvan de red de seguridad. `desde(...)` valida `numero ≥ 1` y normaliza `informada` (strip + minúsculas con `Locale.ROOT`; vacío = ausente; solo `true`/`false`; otro → `ParametroInvalidoException` que menciona `informada`, `true` y `false`).
- *Por qué `informada` como `String` y no `Boolean`*: el conversor de Spring acepta `yes`, `on`, `1`…; el spec admite solo `true`/`false`, igual criterio que `estado`.
- *Fragmentos JPQL*: `f.numero = :numero`, `c.id = :clienteId` (el `LEFT JOIN f.cliente c` ya existe; las facturas sin cliente no coinciden), `f.cae IS NOT NULL` / `f.cae IS NULL` (constantes, sin parámetro). Se elige CAE y no estado porque «informada» significa «tiene autorización» y la anulación lo conserva; si el docente excluye ANULADA, se cambia solo ese fragmento.

### D2 — Paginación: `pagina`/`tamanio`, sobre `PaginaDTO`, consulta de conteo separada
- Parámetros `pagina` (0 por defecto) y `tamanio` (10 por defecto, máximo 100) como `Integer` en el controlador; la validación de rango vive en un record puro `SolicitudPagina.de(Integer, Integer)` (→ `ParametroInvalidoException` nombrando el parámetro y el máximo). Un valor no entero cae en el manejador de tipo incompatible existente, que ya nombra el parámetro.
- Respuesta: `record PaginaDTO<T>(List<T> items, int pagina, int tamanio, long totalElementos, int totalPaginas)` con fábrica que calcula `totalPaginas = ceil(total / tamanio)` en `long` (0 si no hay elementos). **BREAKING** respecto de la etapa 3; se descartó un encabezado `X-Total-Count` con arreglo porque obligaría a exponerlo en CORS y es menos visible en `requests.http`.
- Repositorio: `buscarReporte(filtro)` queda igual (reporte completo); se agregan `buscarPagina(filtro, desplazamiento, cantidad)` (misma JPQL + `setFirstResult`/`setMaxResults`) y `contar(filtro)` con `SELECT COUNT(f) FROM FacturaVenta f LEFT JOIN f.cliente c` + el **mismo** `WHERE` (el armado de condiciones se extrae a un método compartido para que no diverjan). El conteo no une `detalles` ni agrupa: contar sobre la consulta agrupada devolvería un conteo por grupo. Los `JOIN` obligatorios (condición, punto, moneda) no filtran filas, por eso se omiten en el conteo.
- `setMaxResults` sobre una consulta con `GROUP BY` y sin `JOIN FETCH` se traduce a `LIMIT/OFFSET` en SQL (no hay paginación en memoria). El orden `fechaEmision DESC, id DESC` es total (id único), por eso las páginas no se solapan.
- Desplazamiento calculado en `long`; si supera `Integer.MAX_VALUE` se devuelve `items` vacío sin consultar (evita desbordes de `pagina * tamanio`).
- No se usa `Pageable`/`Page` de Spring Data: la consulta ya es JPQL a mano con proyección por constructor y el sobre propio mantiene el contrato en español y explicable en la defensa.

### D3 — Reporte ignora la paginación
`GET /api/facturas/reporte` agrega los tres filtros nuevos y no declara `pagina`/`tamanio` (Spring los ignora). Sigue usando `buscarReporte(filtro)`.

### D4 — Búsqueda `q` en dos catálogos, sin límite
`CatalogoService.clientes(String q)` y `listaPrecioArticulos(String q)`: `q` normalizado (strip; en blanco → catálogo completo; > 100 caracteres → 400). El patrón se arma en Java: minúsculas `Locale.ROOT`, se escapan `\`, `%` y `_` y se envuelve en `%…%`; JPQL con `lower(...) like :patron escape '\'` (explícito por portabilidad H2/PostgreSQL), orden por id, ofertas con `join fetch`. **Sin límite de resultados**: los catálogos del seed son chicos, el docente pidió paginar solo la grilla de facturas y un tope silencioso confundiría al explorador; queda como riesgo documentado. Se mantiene la carga inicial completa de catálogos porque el formulario resuelve con ella etiquetas y precios al editar un borrador y la estimación neta.

### D5 — Detalle con `clienteCuitCuil`
Se agrega `clienteCuitCuil` a `FacturaDetalladaDTO` (aditivo para el frontend) en lugar de un DTO paralelo para el PDF: un solo mapeo dentro de la transacción de lectura sirve al detalle y al comprobante.

### D6 — PDF de una factura: `GET /api/facturas/{id}/pdf`
- Ruta hija del recurso (más clara que `/reporte?id=`, que mezclaría listado y comprobante). `FacturaService.generarComprobante(id)` (transacción de solo lectura de la clase): `obtenerDetalle(id)` (404) → si `BORRADOR`, `ReglaNegocioException` (409: «La factura N está en BORRADOR: no fue informada a ARCA; emitila para imprimirla») → `ReporteFacturasService.generarComprobante(detalle)` → `ArchivoReporte("factura-%04d-%08d.pdf", application/pdf, bytes)`. El controlador arma `Content-Disposition` igual que el reporte; CORS ya expone ese encabezado para toda la API.
- Nueva clase `ComprobantePdf` (paquete `report`, package-private): A4 vertical; bloques Identificación, Cliente, tabla de renglones de 8 columnas con `setHeaderRows(1)`, Totales, Autorización (CAE, vencimiento, resultado), marca `ANULADA` + fecha de anulación si corresponde, observaciones opcionales y leyenda «Comprobante simulado con fines académicos, sin validez fiscal. CAE ficticio».
- **Fuente compartida**: la carga perezosa de la fuente se extrae de `ReportePdf` a una clase `FuentePdf` usada por ambos (refactor bajo pruebas verdes). `VerificadorGlifos` gana un método para pares (texto, nombre del dato) reutilizado por el comprobante; `GlifoNoSoportadoException` gana un mensaje para comprobante que no sugiere Excel/TXT. El manejador existente la traduce a `400` (coherente con IN-03).
- **Importes**: `BigDecimal.valueOf(double guardado)` → `setScale(2, HALF_UP)` → `DecimalFormat` es-AR (mismo helper que `ReportePdf`, extraído). Totales neto e IVA = suma de `BigDecimal` de los renglones; total = `importeTotal` guardado. Cantidad con `stripTrailingZeros` y coma decimal. Ninguna operación aritmética con `double`.
- Fecha de generación con el `Clock` inyectado (zona `America/Argentina/Buenos_Aires`).

### D7 — Facturas informadas: prueba, no código
No se toca `exigirBorrador`. Se agregan pruebas de integración para `PUT` sobre ANULADA y `DELETE` sobre EMITIDA (las faltantes), y en el frontend pruebas de que ninguna fila EMITIDA/ANULADA ofrece editar o eliminar, también en una página distinta de la primera.

### D8 — Frontend: contrato y servicio
- `models/factura.dto.ts`: `PaginaDTO<T>`, `SolicitudPagina`, `FacturaFiltrosDTO` con `numero?`, `clienteId?`, `informada?: 'true' | 'false'`; `FacturaDetalladaDTO.clienteCuitCuil`.
- `FacturaService.obtenerFacturas(filtros, pagina)` → `PaginaDTO<FacturaReporteDTO>`; `consultaFiltros` incluye los filtros nuevos y **no** la paginación (así `urlReporte` sigue reutilizándola sin `pagina`/`tamanio`); `buscarClientes(q)`, `buscarListaPrecioArticulos(q)` (omiten `q` en blanco); `urlPdfFactura(id)`.
- `ApiFacturas` se amplía con esas operaciones.

### D9 — Estado de la consulta y paginación
`aplicacion.ts` guarda `consultaAplicada = { filtros, pagina }`. Toda carga (buscar, limpiar, paginar, cambiar tamaño, recargar tras acción) pasa por una única `ejecutarConsulta(filtros, pagina, anunciar)` con el `ControlSolicitudes` existente, de modo que una respuesta obsoleta de cualquier origen se descarta. Buscar/limpiar/cambiar tamaño → `pagina 0`. Recarga tras acción → misma página; si vuelve con `items` vacío y `pagina ≥ totalPaginas > 0`, se pide `totalPaginas − 1` una sola vez. Nuevo `ui/paginacion.ts` (clase `Paginacion` + función pura `textoPagina`) renderiza en un `<nav aria-label="Paginación de facturas">` estático del HTML: botones «Anterior»/«Siguiente» (`type="button"`), `<p aria-current="page">Página X de Y</p>` (Y = máx(1, totalPaginas)), `<select>` de tamaño 10/20/50 con `label`; botones deshabilitados durante el pedido. El anuncio de página usa la región de estado (`role="status"`) existente con la limpieza de 6 s del mensaje de éxito. La cantidad muestra `totalElementos`.

### D10 — Filtros nuevos en la UI
Campo `numero` (`type="number"`, `min="1"`, `step="1"`) validado en `validarFiltros` (entero ≥ 1; vacío ausente). Cliente del filtro: `<output>` con la etiqueta elegida (o «Todos»), botón «Elegir cliente para filtrar» que abre el explorador y botón «Quitar cliente del filtro»; el id vive en un atributo `data-id` del `<output>` (sin `input hidden`, que no tendría etiqueta). Select «Informada a ARCA» con `''`/`true`/`false`. Limpiar filtros también restablece cliente e informada.

### D11 — Explorador modal (`ui/explorador.ts`)
- Un único `<dialog id="explorador" aria-labelledby="explorador-titulo">` estático en `index.html` (garantiza «uno a la vez»), sin `method="dialog"` (el cierre lo controla el código): un `<form>` de búsqueda (`label` + `input type="search"` + botón «Buscar»), contador, mensaje sin resultados, `<ul>` de resultados con un `<button type="button">` por opción (texto = etiqueta, y en artículos ` — $ precio` formateado) y botón «Cancelar».
- API: `explorador.elegir({ titulo, buscar: (q) => Promise<OpcionDTO[]>, conPrecio }, disparador): Promise<OpcionDTO | null>`. Al abrir: título, búsqueda vacía inicial, foco en el campo. Elegir → resuelve la opción; Cancelar o evento `cancel` (Escape nativo) → `null`; en ambos casos cierra y devuelve el foco al disparador.
- **Abstracción inyectable** `VentanaModal { abrir(d: HTMLDialogElement): void; cerrar(d: HTMLDialogElement): void }` en el `Entorno` (como `Dialogos`): en producción `showModal()`/`close()` (que vuelven inerte el resto y manejan Escape); en pruebas un doble que alterna el atributo `open`, porque jsdom no implementa `showModal`. Las pruebas disparan el evento `cancel` para simular Escape.
- Búsqueda al enviar el formulario de búsqueda (Enter o botón), no por tecla, para no inundar la API; `ControlSolicitudes` propio descarta respuestas viejas; errores dentro del diálogo en un `<p role="alert">`. Todo texto del servidor con `textContent`.
- Usos: cliente del formulario (reemplaza `<select id="cliente">` por `<output id="cliente">` + «Elegir cliente» + «Usar Consumidor Final»), cliente del filtro (D10) y opción de cada renglón (reemplaza el `<select>` por `<output>` + botón «Elegir artículo (renglón N)»; el id y el precio elegidos quedan en el renglón y alimentan la estimación como hoy). Al editar un borrador, las etiquetas se resuelven con los catálogos cargados (respaldo: datos del detalle). Errores de `detalles[i].listaPrecioArticuloId` se marcan en el botón del renglón (`aria-invalid` + `aria-describedby`).

### D12 — Acción «Imprimir PDF»
Enlace nativo `<a href={urlPdfFactura(id)}>Imprimir PDF<span class="visualmente-oculto"> factura N</span></a>` en filas EMITIDA y ANULADA (el `Listado` recibe la función de URL). Igual que los enlaces de reporte de la etapa 5: navegación directa, sin `fetch` ni blobs. Las filas informadas no reciben botones de editar ni eliminar (sin cambios en la lógica, nueva prueba).

### D13 — Estrategia de pruebas (skills `springboot-tdd`, `java-junit`, `jpa-patterns`, `spring-data-jpa`, `tdd`, `accessibility`)
- Backend: unitarias puras para `FiltroFacturas`, `SolicitudPagina`, `PaginaDTO` y patrón de búsqueda; `@DataJpaTest` para `jpql`, `contar`, `buscarPagina` (incluye conteo con varios renglones y páginas sin solapamiento con fechas iguales) y búsquedas de catálogo; `@WebMvcTest` para parámetros y códigos; integración `@SpringBootTest` con `open-in-view=false` para el PDF; `PdfTextExtractor` de OpenPDF para verificar número, CAE, vencimiento, `ANULADA`, leyenda y totales. AssertJ, esperados literales del spec, `Clock` fijo.
- Frontend: jsdom con `servicioFalso`, `temporizadorManual`, diferidos y el doble de `VentanaModal`; sin aserciones triviales.
- Caracterización (pruebas que pasan al escribirse, p. ej. 409 de informadas) con mutación temporal revertida y anotada en `docs/etapa-6.md`.

### D14 — Pruebas existentes que cambian (honestamente)
- `FacturaRestControllerTest`: `listar` devuelve `PaginaDTO`; construcción de `FacturaDetalladaDTO` con `clienteCuitCuil`.
- `FacturaApiIntegracionTest` (y `FacturaEscrituraIntegracionTest`, `CorsConfigTest`/`CorsConfigPropiedadTest` si leen el cuerpo del listado): `$[i]`/`$.length()` → `$.items[i]`/`$.items.length()`.
- `FacturaServiceTest`: `listar` usa `buscarPagina` + `contar`.
- `FacturaReporteRepositoryTest`: sin cambios gracias al constructor secundario (D1).
- Frontend: `soporte-pruebas/soporte.mjs` (página por defecto en `servicioFalso`, doble de `VentanaModal`), `consulta.test.mjs` (páginas, argumentos `[{}, {pagina:0,tamanio:10}]`, cliente opcional), `cliente-http.test.mjs` (URL del listado con `pagina`/`tamanio`), `acciones.test.mjs` (EMITIDA ofrece imprimir y anular; ANULADA imprimir), `formulario.test.mjs` y `edicion.test.mjs` (cliente y opción por explorador), `accesibilidad.test.mjs` (nuevos controles), `index.test.mjs` (estructura del HTML). Cada cambio se lista en `docs/etapa-6.md` con su motivo.

### D15 — Inventario de la semilla (DD-13)
`api-facturas` (solo lectura): tiene únicamente un `GET /export/pdf` del listado (7 columnas, escribe sobre `HttpServletResponse`), sin paginación, sin modales ni diálogos en su frontend y sin PDF por factura. **Nada se reutiliza**: el reporte del proyecto ya supera ese método y las tres piezas nuevas no existen en la semilla. Se declara así en el informe.

### D16 — Referencia del docente (solo consulta)
En `arca-spirng` no hay paginación, explorador modal ni PDF por factura (solo el reporte `reporte-facturas.pdf`). Se consultó únicamente para confirmar nombres de campos (`cae`, `caeFechaVencimiento`) ya alineados; no se copia código.

## Risks / Trade-offs

- [Cambio incompatible del listado] → se actualiza en el mismo change el frontend, `requests.http`, pruebas y documentación; `docs/etapa-6.md` lo destaca y el tag `etapa-3` conserva el contrato anterior.
- [Catálogos sin límite en el explorador] → aceptable con el seed; si crecen, agregar `limite` es aditivo.
- [Enlace de PDF sin manejo de error en la página] → solo se ofrece en estados imprimibles; un `400` por glifo o un `404` por borrado concurrente mostraría el Problem Details en la pestaña, igual que los enlaces de reporte de la etapa 5; documentado.
- [`<dialog>` en navegadores viejos] → los navegadores actuales lo soportan; la abstracción `VentanaModal` permite un respaldo si hiciera falta; se verifica en el recorrido manual.
- [«Informada» incluye ANULADA] → supuesto a confirmar; cambiar implica un solo fragmento JPQL y dos escenarios.
- [Conteo y página en dos consultas] → una inserción entre ambas puede desfasar el total en un elemento; aceptable para un listado de consulta.
- [Desfase entre formulario y explorador al editar con catálogo fallido] → respaldo con datos del detalle.

## Migration Plan

Sin migración de datos. Orden de despliegue local: backend (`./gradlew clean test bootJar` con JDK 17) y frontend (`npm test && npm run build`) en el mismo commit, porque el frontend de `etapa-5` no entiende el sobre paginado. Rollback: volver al tag `etapa-5`.

## Open Questions

- ¿Qué tag/versión evalúa el docente (`etapa-6` u otro)? No cambia el diseño; define el cierre.
- ¿Acepta ARCA simulada? Si exige WSFE real, el alcance cambia y se replantea el change (bloquea el cierre, no el apply).
- ¿Tamaño de página por defecto 10 le sirve? Es una constante.
- ¿«Informada» incluye ANULADA? Supuesto adoptado: sí.
