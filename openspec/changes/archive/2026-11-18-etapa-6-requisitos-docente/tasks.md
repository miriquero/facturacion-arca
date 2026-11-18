# Tasks

> Reglas para todo el apply:
> - **Backend** siempre con `JAVA_HOME=C:\Users\zbook\.jdks\ms-17.0.20.1` (nunca el JDK 26 por defecto). **Frontend** con `npm test` dentro de `frontend/` (compila y corre `node --test` sin argumentos). Sin dependencias, versiones ni archivos de build nuevos: `build.gradle`, `package.json`, `package-lock.json` y `tsconfig.json` no cambian (`strict` siempre activo).
> - TDD estricto (skills `springboot-tdd`, `java-junit`, `tdd`): cada **GREEN** va precedida de su **RED**, que se cierra solo al ver la falla **por la razón esperada** (aserción, no compilación: si no compila, crear primero el esqueleto mínimo). Un comportamiento por ciclo. Las pruebas que pasan al escribirse son **caracterización**: se validan con una mutación temporal que se revierte y se anotan en `docs/etapa-6.md`. Esperados literales tomados de `specs/`, nunca recalculados como el código. Sin aserciones triviales.
> - Reglas duras: DTO y no entidades en HTTP; `BigDecimal` con `HALF_UP` y sin aritmética `double` nueva; JPQL solo con fragmentos constantes y parámetros con nombre; nunca `innerHTML`/`outerHTML`/`insertAdjacentHTML`; texto del servidor con `textContent`; sin framework ni bundler.
> - No tocar `CHANGES.md`, `CLAUDE.md`, `AGENTS.md`, `docs/consigna/` ni changes archivados. Sin comandos Git que cambien estado: al cerrar se entregan los comandos al estudiante.
> - Gobernanza MEDIA: al cerrar cada grupo, correr la suite afectada completa y reportar. En las tareas **CHECKPOINT** el apply se detiene, muestra el resultado al estudiante y espera su visto bueno.
> - Tamaño estimado: grande (≈ 80 tareas; 3 a 4 sesiones de apply). Backend ≈ 45 % y frontend ≈ 55 %.

## 1. Red de seguridad e informe

- [x] 1.1 Red de seguridad backend: `./gradlew clean test` con JDK 17; anotar el baseline (se esperan 503 pruebas en verde) en el borrador de `docs/etapa-6.md`; si algo falla, detenerse y reportarlo como falla preexistente
- [x] 1.2 Red de seguridad frontend: en `frontend/`, `npm ci` y `npm test`; anotar el baseline (se esperan 151 pruebas en verde); si algo falla, detenerse
- [x] 1.3 Crear `docs/etapa-6.md` con el esqueleto de secciones (origen del pedido y supuestos, cambio de contrato, filtros, paginación, búsqueda de catálogos, PDF por factura, facturas informadas, explorador modal, paginación en la UI, accesibilidad, pruebas existentes que cambiaron, pruebas y costuras, recorrido manual, semilla reutilizada, referencia del docente, preguntas abiertas, desviaciones, verificaciones y entorno, uso de IA); verificar que existe con esas secciones

## 2. Filtros nuevos en el backend (specs `api-listado-facturas`, `proyeccion-reporte-facturas`)

- [x] 2.1 RED: `FiltroFacturasTest` — `informada` `" FALSE "` → `false`, `"true"` → `true`, `"  "` → ausente, `"si"` → `ParametroInvalidoException` que menciona `informada`, `true` y `false`; esqueleto de los campos nuevos en `FacturaFiltrosDTO` y `FiltroFacturas` (con constructor secundario de cuatro argumentos, D1) para que falle por aserción
- [x] 2.2 GREEN: normalización de `informada` en `FiltroFacturas.desde`; 2.1 en verde y pruebas de etapas 2–3 sin cambios en verde
- [x] 2.3 RED + TRIANGULATE + GREEN: `numero` — `0` y `-3` → 400 que menciona `numero`; `1` y `987654` válidos; nulo ausente
- [x] 2.4 RED + GREEN: `jpql(filtro)` — con número 987654 y cliente 4321 el texto no contiene esos valores sino `:numero` y `:clienteId`; con informada verdadera contiene `f.cae IS NOT NULL` y con falsa `f.cae IS NULL`, sin parámetro (escenarios de «Filtros como parámetros de la consulta»)
- [x] 2.5 RED + GREEN (`@DataJpaTest`): filtro por número exacto (dos facturas número 1 de puntos distintos y una número 2)
- [x] 2.6 RED + GREEN (`@DataJpaTest`): filtro por cliente (cliente A, cliente B, sin cliente)
- [x] 2.7 RED + GREEN (`@DataJpaTest`): filtro por informada (una factura en cada estado, verdadera y falsa)
- [x] 2.8 RED + GREEN (`@DataJpaTest`): combinación cliente + informada + fecha desde y combinación contradictoria `BORRADOR` + informada → vacío
- [x] 2.9 REFACTOR: extraer el armado de condiciones del `WHERE` a un método compartido (lo usará el conteo, D2); suite de repositorio en verde; documentar filtros y fragmentos en `docs/etapa-6.md`

## 3. Paginación en el backend (specs `api-listado-facturas`, `proyeccion-reporte-facturas`)

- [x] 3.1 RED + TRIANGULATE + GREEN: `SolicitudPagina.de(pagina, tamanio)` — nulos → (0, 10); `-1` → 400 con `pagina`; `0` y `101` → 400 con `tamanio` y 100; `1` y `100` válidos; desplazamiento en `long`
- [x] 3.2 RED + TRIANGULATE + GREEN: `PaginaDTO.de(items, solicitud, total)` — total 23 / tamaño 10 → 3 páginas; total 0 → 0 páginas; total 20 / tamaño 10 → 2
- [x] 3.3 RED + GREEN (`@DataJpaTest`): `contar(filtro)` — una factura de tres renglones, una de uno y una sin renglones → 3 (no 4 ni 5); con estado EMITIDA sobre dos EMITIDA y un BORRADOR → 2
- [x] 3.4 RED + GREEN (`@DataJpaTest`): `buscarPagina(filtro, desplazamiento, cantidad)` — 12 facturas de fechas distintas: página 1 tamaño 5 = 6.ª a 10.ª; página 2 = las dos más antiguas; cantidad de ítems correcta en cada fila
- [x] 3.5 RED + GREEN (`@DataJpaTest`): 6 facturas de la misma fecha, páginas 0..2 de tamaño 2 → unión sin duplicados ni faltantes, en id descendente; unión de todas las páginas igual fila por fila a `buscarReporte` sin página
- [x] 3.6 RED + GREEN: desplazamiento mayor que `Integer.MAX_VALUE` → `items` vacío sin consultar (doble del `EntityManager` o espía); página posterior a la última → vacío con total real
- [x] 3.7 RED + GREEN (`FacturaServiceTest`): `listar(filtros, pagina)` valida filtros y página antes de consultar y arma el `PaginaDTO`; actualizar las pruebas de `listar` existentes (anotar en el informe cuáles y por qué)

## 4. Contrato HTTP del listado y del reporte (specs `api-listado-facturas`, `api-descarga-reporte`, `api-errores`)

- [x] 4.1 RED (`@WebMvcTest`): `GET /api/facturas` responde el sobre con `items`, `pagina`, `tamanio`, `totalElementos`, `totalPaginas` y pasa `pagina`/`tamanio` y los filtros nuevos al servicio; `tamanio=diez` y `numero=abc` → 400 que nombra el parámetro
- [x] 4.2 GREEN: controlador con `pagina`, `tamanio`, `numero`, `clienteId`, `informada`; actualizar `FacturaRestControllerTest` existente (listar devuelve `PaginaDTO`)
- [x] 4.3 RED + GREEN (integración): escenarios «Sin filtros», «Segunda página», «Última página incompleta», «Página posterior a la última», «Conteo con renglones múltiples», «Base sin facturas» (`items` `[]`) y «Listado vacío frente a detalle inexistente»; migrar `FacturaApiIntegracionTest` y demás pruebas que leen `$[i]` a `$.items[i]` (lista exacta en el informe)
- [x] 4.4 RED + GREEN (integración): filtros nuevos por HTTP — número existente y en dos puntos de venta, cliente e inexistente, informada `true`/`%20FALSE%20`/`si`, combinación y contradicción
- [x] 4.5 RED + GREEN (integración): reporte con `clienteId=1&informada=true` (TXT solo con EMITIDA y ANULADA del cliente 1), `pagina=1&tamanio=5` ignorados (TXT con las 12) e `informada=si` → 400 sin archivo
- [x] 4.6 Documentar en `docs/etapa-6.md` el cambio **BREAKING** del listado (antes/después con ejemplo JSON), parámetros, máximos y por qué el conteo es una consulta aparte (D2); `./gradlew test` en verde
- [x] 4.7 **CHECKPOINT 1 — contrato paginado y filtros**: mostrar al estudiante el JSON de `GET /api/facturas?pagina=0&tamanio=2&informada=true`, los 400 de paginación e `informada`, la JPQL de página y de conteo y la lista de pruebas existentes modificadas; esperar visto bueno antes de seguir (aprobado por el estudiante 2026-11-18)

## 5. Búsqueda en catálogos (spec `api-catalogos`)

- [x] 5.1 RED + TRIANGULATE + GREEN: función pura del patrón de búsqueda — `" juan "` → `%juan%`; `"%"` → `%\%%`; `"a_b"` → `%a\_b%`; en blanco → ausente; 101 caracteres → 400 con `q` y 100
- [x] 5.2 RED + GREEN (`@DataJpaTest`): búsqueda de clientes por denominación y por parte del CUIT, sin distinguir mayúsculas, orden por id, comodín literal
- [x] 5.3 RED + GREEN (`@DataJpaTest`): búsqueda de ofertas por denominación de artículo, código de artículo y código de lista, con `join fetch`
- [x] 5.4 RED + GREEN (integración): `GET /api/catalogos/clientes?q=%20juan%20`, `?q=7123`, `?q=zzz` (`[]`), `?q=%25` y `GET /api/catalogos/lista-precio-articulos?q=martillo`/`?q=mayor`; sin `q` sigue devolviendo el catálogo completo (pruebas de etapa 3 en verde); `POST` sigue en 405

## 6. Detalle con CUIT y facturas informadas bloqueadas (specs `api-detalle-factura`, `api-borradores-factura`)

- [x] 6.1 RED + GREEN: el detalle incluye `clienteCuitCuil` (`20-12345678-9`) y nulo para Consumidor Final; actualizar la construcción de `FacturaDetalladaDTO` en `FacturaRestControllerTest`
- [x] 6.2 Caracterización (integración): `PUT` válido sobre una ANULADA → 409 que menciona ANULADA y la factura conserva cabecera, renglones, número, CAE y fecha de anulación; mutación temporal (quitar `exigirBorrador` en `actualizar`) → falla; revertir
- [x] 6.3 Caracterización (integración): `DELETE` sobre una EMITIDA → 409 que menciona EMITIDA y la factura sigue con número y CAE; mutación temporal en `eliminar` → falla; revertir; anotar ambas en el informe

## 7. PDF de una factura (specs `api-pdf-factura`, `comprobante-pdf`)

- [x] 7.1 REFACTOR con red verde: extraer de `ReportePdf` la carga de la fuente (`FuentePdf`) y el formato de importes es-AR; pruebas de `reporte-pdf` sin cambios y en verde
- [x] 7.2 RED: `ComprobantePdfTest` — A4 vertical (595 × 842) y texto extraído con `PdfTextExtractor` que contiene `Factura`, `0001-00000004`, `05/11/2026`, `Casa Central` y `18/11/2026 15:30` con `Clock` fijo en 2026-11-18T18:30:00Z; esqueleto que devuelve un PDF vacío para que falle por aserción
- [x] 7.3 GREEN: identificación del comprobante
- [x] 7.4 RED + GREEN: cliente con CUIT y condición/moneda; Consumidor Final sin etiqueta de CUIT
- [x] 7.5 RED + GREEN: tabla de renglones (ocho encabezados, `ART-001`/`Martillo`/`3.000,00` antes que `ART-003`/`Cable HDMI`/`8.000,00`) y 60 renglones → más de una página con encabezados en cada una
- [x] 7.6 RED + TRIANGULATE + GREEN: totales `19.000,00`, `3.990,00`, `22.990,00` desde valores guardados; importe 0.125 → `0,13`; grep de la clase sin operaciones aritméticas con `double`
- [x] 7.7 RED + GREEN: CAE `70000000000001`, vencimiento `15/11/2026` y resultado `A`; ANULADA → `ANULADA` y `12/11/2026` conservando CAE; EMITIDA sin `ANULADA`; leyenda con `sin validez fiscal` y `CAE ficticio`; observaciones solo si existen
- [x] 7.8 RED + GREEN: fuente `Identity-H` incrustada, textos «Ñandú & Cía. — 测试» y «Cable «HDMI» 2 m» extraídos idénticos; U+E000 en una descripción → `GlifoNoSoportadoException` que menciona `U+E000` y la descripción, sin sugerir Excel/TXT
- [x] 7.9 RED + GREEN (`FacturaServiceTest`): `generarComprobante` — BORRADOR → `ReglaNegocioException` con BORRADOR y «no fue informada a ARCA»; inexistente → 404; EMITIDA → `ArchivoReporte` `factura-0001-00000004.pdf` `application/pdf`
- [x] 7.10 RED + GREEN (`@WebMvcTest` + integración con `open-in-view=false`): `GET /api/facturas/{id}/pdf` → 200, `Content-Disposition` `attachment; filename="factura-0001-00000004.pdf"`, cuerpo `%PDF`; ANULADA → 200; BORRADOR → 409 sin `Content-Disposition`; `999999` → 404; `abc` → 400 con `id`; U+E000 en el cliente → 400 con `U+E000`; la factura releída no cambia
- [x] 7.11 RED + GREEN (CORS): PDF con `Origin: http://localhost:5173` → 200 y `Access-Control-Expose-Headers` con `Content-Disposition`
- [x] 7.12 Generar un PDF de ejemplo de una EMITIDA y de una ANULADA del seed para el informe (sin versionarlos si pesan); documentar D6 en `docs/etapa-6.md`; `./gradlew test` en verde
- [x] 7.13 **CHECKPOINT 2 — PDF por factura**: mostrar al estudiante los dos PDF de ejemplo, el texto extraído (número, CAE, vencimiento, ANULADA, leyenda) y la respuesta 409 del borrador; esperar visto bueno (aprobado por el estudiante 2026-11-18)

## 8. `requests.http` y cierre del backend

- [x] 8.1 Actualizar `requests.http`: listado paginado (página 0 y 1, `tamanio` 2), filtros `numero`, `clienteId`, `informada`, paginación inválida, búsqueda `q` en ambos catálogos, PDF de una EMITIDA, de una ANULADA y de un BORRADOR (409); verificar cada request a mano contra el jar con JDK 17 y anotar las respuestas en el informe
- [x] 8.2 `./gradlew clean test bootJar` con JDK 17 en verde; anotar la cantidad de pruebas

## 9. Frontend: DTO y cliente HTTP (spec `frontend-cliente-http`)

- [x] 9.1 Tipos en `models/factura.dto.ts` (`PaginaDTO<T>`, `SolicitudPagina`, filtros nuevos, `clienteCuitCuil`) y ampliación de `ApiFacturas`; `npm run build` sin errores
- [x] 9.2 RED + GREEN: listado paginado — URL sin filtros `/facturas?pagina=0&tamanio=10`; filtros combinados con `numero=4`, `clienteId=2`, `informada=true`; devuelve la página sin transformarla; actualizar las pruebas existentes de `cliente-http.test.mjs` que esperaban `/facturas` (anotar)
- [x] 9.3 RED + GREEN: `urlReporte` con `informada=true` sin `pagina` ni `tamanio`; `txt` sin filtros ni paginación
- [x] 9.4 RED + GREEN: `buscarClientes('Ñandú & Cía')` con `q` codificado; `buscarListaPrecioArticulos('   ')` sin `?`
- [x] 9.5 RED + GREEN: `urlPdfFactura(7)` → `http://localhost:8080/api/facturas/7/pdf`
- [x] 9.6 Actualizar `soporte-pruebas/soporte.mjs` (página por defecto en `servicioFalso`, ayudante `pagina(items, …)`, doble de `VentanaModal`); `npm test` en verde con las pruebas existentes adaptadas (lista en el informe)

## 10. Frontend: filtros nuevos (spec `frontend-consulta-facturas`)

- [x] 10.1 RED + TRIANGULATE + GREEN (`validacion.test.mjs`): número `0` y `2.5` inválidos, `12` válido, vacío ausente; informada `''`/`true`/`false`
- [x] 10.2 RED + GREEN: HTML del filtro (número, informada, cliente con `<output>` «Todos» y dos botones) y búsqueda por número + informada → `numero=4`, `informada=true`, página 0; número inválido marca el campo y no envía
- [x] 10.3 RED + GREEN: cantidad desde `totalElementos` («Tres coincidencias» y «Más coincidencias que filas»: 10 filas y `23 facturas`); carga inicial con `[{}, {pagina:0,tamanio:10}]` (actualizar `consulta.test.mjs`, anotar)
- [x] 10.4 RED + GREEN: limpiar filtros restablece número, informada y cliente «Todos» y pide página 0; enlaces de descarga con `informada` y sin paginación

## 11. Frontend: paginación (spec `frontend-paginacion`)

- [x] 11.1 RED + TRIANGULATE + GREEN: `textoPagina` y estado de botones — primera de 3, última de 3, sin resultados (`Página 1 de 1`, ambos deshabilitados)
- [x] 11.2 RED + GREEN: `nav` con nombre accesible, botones `type="button"`, selector de tamaño 10/20/50 con etiqueta, `aria-current="page"` en el indicador
- [x] 11.3 RED + GREEN: «Siguiente» con los filtros del resultado mostrado (no los editados sin buscar) y `pagina=1`; anuncio `Página 2 de 3 (23 facturas)`
- [x] 11.4 RED + GREEN: tamaño 20 desde la página 2 → `pagina=0&tamanio=20`; nueva búsqueda desde la página 3 → `pagina=0`
- [x] 11.5 RED + GREEN: doble clic en «Siguiente» → un pedido; respuesta vieja tras una búsqueda descartada; error al paginar conserva la página 0, muestra el error y libera botones
- [x] 11.6 RED + GREEN (`acciones.test.mjs`): recarga tras emitir en la página de índice 1 pide `pagina=1`; eliminar el único elemento de la última página pide la anterior

## 12. Frontend: explorador modal (spec `frontend-explorador-modal`)

- [x] 12.1 RED: `explorador.test.mjs` — al abrir con el doble de `VentanaModal`, el diálogo queda abierto, `aria-labelledby` apunta a `Buscar cliente`, hay campo de búsqueda etiquetado y «Cancelar», y el foco está en el campo; esqueleto mínimo de `ui/explorador.ts` y `<dialog id="explorador">` en `index.html`
- [x] 12.2 GREEN: apertura, búsqueda inicial sin texto y lista de resultados como botones con la etiqueta (`textContent`)
- [x] 12.3 RED + GREEN: buscar `juan` pide `buscar('juan')`; sin coincidencias muestra mensaje y 0; error dentro del diálogo con `role="alert"` y el diálogo sigue abierto
- [x] 12.4 RED + GREEN: respuestas en orden inverso (`ju` / `juan`) → queda `juan`
- [x] 12.5 RED + GREEN: elegir un resultado resuelve la opción, cierra y devuelve el foco al disparador; Cancelar y evento `cancel` (Escape) resuelven `null`, cierran y devuelven el foco
- [x] 12.6 RED + GREEN: etiqueta `<img src=x onerror=alert(1)>` mostrada literal, sin `img` en el diálogo; artículos muestran el precio formateado en el nombre accesible
- [x] 12.7 Implementación de producción de `VentanaModal` con `showModal()`/`close()` en `app.ts` (no testeable en jsdom: se verifica en el recorrido manual 15.x); estilos del diálogo y `::backdrop` con contraste AA en `styles.css`
- [x] 12.8 **CHECKPOINT 3 — explorador modal**: mostrar al estudiante el explorador funcionando en el navegador (apertura, búsqueda, elección, Escape, foco de ida y vuelta) y el diseño de la abstracción `VentanaModal`; esperar visto bueno antes de reemplazar los `<select>` — aprobado por el estudiante 2026-11-18

## 13. Frontend: usos del explorador (specs `frontend-formulario-factura`, `frontend-consulta-facturas`)

- [x] 13.1 RED + GREEN: cliente del formulario — `<output>` `Consumidor Final` + «Elegir cliente» + «Usar Consumidor Final»; elegir el id 2 → request con `clienteId` 2; quitar → sin `clienteId` (actualizar `formulario.test.mjs`, anotar)
- [x] 13.2 RED + GREEN: cliente del filtro — elegir el id 2 no busca hasta «Buscar»; luego `clienteId=2`; quitar → «Todos» y sin `clienteId`
- [x] 13.3 RED + GREEN: renglón — botón «Elegir artículo (renglón N)» + `<output>`; elegir id 7 de precio 3000.00 → precio `3.000,00`, estimación recalculada y request con `listaPrecioArticuloId` 7; renglón sin opción → control inválido con mensaje y sin envío
- [x] 13.4 RED + GREEN: edición de un borrador muestra la etiqueta del cliente y de cada opción desde los catálogos (y respaldo con datos del detalle si no figuran); actualizar `edicion.test.mjs` (anotar)
- [x] 13.5 RED + GREEN: errores del servidor `detalles[i].listaPrecioArticuloId` marcados en el botón del renglón; durante el guardado los botones de explorador del formulario quedan deshabilitados y se liberan al terminar

## 14. Frontend: acción «Imprimir PDF» y facturas informadas (spec `frontend-acciones-factura`)

- [x] 14.1 RED + GREEN: EMITIDA → enlace «Imprimir PDF» a `…/facturas/1/pdf` y botón anular; ANULADA → solo el enlace; BORRADOR sin enlace; nombres accesibles con el id (actualizar `acciones.test.mjs`, anotar)
- [x] 14.2 RED + GREEN: en la segunda página, filas EMITIDA y ANULADA sin controles de editar ni eliminar

## 15. Recorrido manual de extremo a extremo

- [x] 15.1 Levantar el backend (`bootJar` con JDK 17) y el frontend (`npm run dev`); en el navegador integrado recorrer: filtrar por número, cliente (explorador) e informada; paginar y cambiar tamaño; crear un borrador eligiendo cliente y artículos con el explorador; editarlo; emitirlo; imprimir su PDF; anularlo e imprimirlo; verificar que no hay editar/eliminar en informadas; anotar resultados y capturas en `docs/etapa-6.md`
- [x] 15.2 Detener backend y frontend y verificar que los puertos 8080 y 5173 quedaron libres

## 16. Accesibilidad (spec `frontend-estado-accesibilidad`, skills `accessibility`, `accessibility-review`)

- [x] 16.1 RED + GREEN (`accesibilidad.test.mjs`): con un explorador abierto, todos los accionables son nativos, sin `tabindex` positivo y con `type`; botones de explorador con nombre (cliente del formulario, cliente del filtro, artículo del renglón 1 y 2); todo `input`/`select`/`textarea` con `label` (actualizar la prueba existente, anotar)
- [x] 16.2 Revisión WCAG 2.2 AA del diálogo y la paginación (contraste, foco visible, inercia del fondo, anuncio de página) con hallazgos por principio POUR y severidad en `docs/etapa-6.md`; dejar el checklist de teclado y lector de pantalla para que lo complete el estudiante

## 17. Documentación

- [x] 17.1 Completar `docs/etapa-6.md`: decisiones D1–D16, cambio de contrato, evidencia (pruebas, requests, PDF, recorrido), pruebas existentes modificadas y por qué, semilla (nada reutilizable, D15), referencia del docente (solo nombres, D16), preguntas abiertas, desviaciones y sección de IA con «Qué se revisó y qué se corrigió: **COMPLETAR POR EL ESTUDIANTE**»
- [x] 17.2 Actualizar `README.md`: nuevos parámetros del listado (sobre paginado), filtros, búsqueda `q`, `GET /api/facturas/{id}/pdf`, explorador y paginación en el frontend, supuesto de ARCA simulada; verificar que los enlaces relativos existen

## 18. Verificación final

- [x] 18.1 `./gradlew clean test bootJar` con `JAVA_HOME` en JDK 17: todo verde; anotar cantidad de pruebas
- [x] 18.2 `cd frontend && npm test && npm run build`: todo verde; anotar cantidad de pruebas
- [x] 18.3 Greps de reglas duras: sin `innerHTML|outerHTML|insertAdjacentHTML|document.write` en `frontend/src`; sin `@Data` en `model/`; sin `Math.round` ni aritmética `double` nueva en `report/` y `service/`; JPQL nueva sin concatenar valores; sin dependencias nuevas en `build.gradle` y `package.json`; anotar resultados
- [x] 18.4 `openspec validate etapa-6-requisitos-docente --strict` en verde; preparar para el estudiante los comandos de commit (con `Co-Authored-By`) sin ejecutarlos

## Workflow follow-up

- Confirmar con el docente: tag a evaluar, ARCA simulada, tamaño de página por defecto e inclusión de ANULADA en «informada».
- El estudiante ejecuta commit, push, PR y el tag que el docente indique.
- `/opsx:archive etapa-6-requisitos-docente` y marcar `C-07` como `[x]` en `CHANGES.md`.
