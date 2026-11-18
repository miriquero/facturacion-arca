# Tasks

> Reglas para todo el apply:
> - Trabajar dentro de `frontend/` con `npm test` (compila y corre `node --test` sin argumentos; Node 24: nunca `node --test test/`). Sin dependencias nuevas: `package.json`, `package-lock.json` y `tsconfig.json` no cambian (`strict` siempre activo). Nada de `npm install <paquete>`.
> - TDD estricto (skill `tdd`): cada **GREEN** va precedida de su **RED** y la RED se cierra solo tras ver la falla **por la razón esperada** (aserción, no un `import` roto: si no compila, crear primero el esqueleto mínimo exportado). Una costura y un comportamiento por ciclo; nada de escribir todas las pruebas primero. Las pruebas que pasan al escribirse se marcan como **caracterización** en `docs/etapa-5.md` y se validan con una mutación temporal que se revierte. Toda pieza de la semilla (`api-facturas/frontend`) entra solo en un GREEN y se anota en el informe (inventario D15).
> - Esperados literales tomados de los escenarios de `specs/`; nunca recalculados como el código. Sin aserciones triviales.
> - Nunca `innerHTML`/`outerHTML`/`insertAdjacentHTML`/`document.write`; todo texto externo con `textContent` (D12). El código recibe el `Document` y no usa `document` global salvo en `app.ts`.
> - **No** tocar `src/` del backend, `build.gradle`, `docs/etapa-4.md`, `CHANGES.md` ni la carpeta del change de la etapa 4. No correr Gradle ni levantar el backend mientras el apply de la etapa 4 siga en curso (tareas marcadas **[requiere backend etapa 4]**). Sin comandos Git que cambien estado.
> - Gobernanza MEDIA: al cerrar cada grupo, correr `npm test` completo, reportar al estudiante y esperar su visto bueno antes de seguir.

## 1. Red de seguridad e informe

- [x] 1.1 Red de seguridad: en `frontend/`, `npm ci` y `npm test`; anotar el baseline (se esperan 7 pruebas en verde) en el borrador de `docs/etapa-5.md`; si algo falla, detenerse y reportarlo como falla preexistente. Verificar además que un archivo en `frontend/soporte-pruebas/` **no** es levantado por `node --test` (crear uno temporal con un `test()` que falla, comprobar que `npm test` sigue verde y borrarlo)
- [x] 1.2 Crear `docs/etapa-5.md` con el esqueleto de secciones (cliente HTTP y DTO, errores y `ApiError`, consulta y filtros, formato de moneda y fechas, formulario y validaciones, estimación, control asíncrono, acciones por estado, accesibilidad, pruebas y costuras, recorrido manual, descargas de ejemplo, límites de la simulación, semilla reutilizada, referencia del docente, dependencias de la etapa 4, verificaciones y entorno, uso de IA); verificar que el archivo existe con esas secciones

## 2. Modelos y cliente HTTP (spec `frontend-cliente-http`)

- [x] 2.1 Crear `src/models/factura.dto.ts` con los tipos de D2 (solo tipos, sin lógica) y `soporte-pruebas/` con `respuesta()` y un `fetch` falso que registra llamadas; verificar `npm run build` sin errores. Anotar en el informe la fila de semilla de los DTO
- [x] 2.2 RED: `test/cliente-http.test.mjs` — «Base configurada» (`obtenerFacturas({})` con base `http://127.0.0.1:9090/api` llama a `http://127.0.0.1:9090/api/facturas`) y «Base con barra final»; esqueleto `services/FacturaService.ts` que devuelve `[]` sin llamar para que falle en la aserción
- [x] 2.3 GREEN: `FacturaService` con `fetch` inyectado y `enviar<T>` adaptado de la semilla (D3); 2.2 en verde
- [x] 2.4 RED + GREEN: encabezados — `X-Usuario: sistema` en lectura y en `DELETE`; `Content-Type: application/json` solo con cuerpo (alta) y ausente en `GET /facturas/7`; ningún otro encabezado
- [x] 2.5 RED + TRIANGULATE + GREEN: `consultaFiltros` — sin filtros (sin `?`), filtros combinados (`fechaDesde`, `estado`, `montoMinimo=0`, sin `fechaHasta`), cadena vacía omitida
- [x] 2.6 RED + GREEN: operaciones del contrato — `obtenerFactura(7)`, `obtenerCatalogos()` (cinco rutas, resultado por nombre), `crearFactura`, `actualizarFactura(5, req)` (`PUT /facturas/5`), `cambiarEstado(12,'ANULADA','Error en el cliente')` (cuerpo exacto), `cambiarEstado(12,'EMITIDA')` sin `observaciones`, `eliminarFactura(6)` (`DELETE`)
- [x] 2.7 RED + GREEN: `urlReporte` — `xlsx` con `estado=EMITIDA`; `txt` sin filtros (solo `formato=txt`); `pdf` con los mismos parámetros que `consultaFiltros` (misma función)
- [x] 2.8 RED + GREEN: 204 — respuesta `204` cuyo cuerpo lanzaría al leerse (`Response` con `json`/`text` espiados) se resuelve en `undefined` sin leer; mutación temporal `json()` en 204 → la prueba falla; revertir
- [x] 2.9 RED + TRIANGULATE + GREEN: `services/ApiError.ts` — 409 con `detail` → mensaje = `detail`; 400 con `errores` (dos claves) conservadas; 502 con HTML → mensaje de respaldo con `502` y sin errores; 500 vacío → respaldo; JSON sin `detail` con `title` → mensaje = `title`; `errores` con un valor no texto se descarta
- [x] 2.10 RED + GREEN: falla de red — `fetch` que rechaza con `TypeError` → `ApiError` estado 0 con la URL base en el mensaje
- [x] 2.11 RED + GREEN: «Request sin precios ni importes» — el JSON enviado por `crearFactura` con dos renglones no contiene `precioUnitario` ni ninguna clave `importe*` (aunque el objeto de entrada se arme desde datos que los tengan); documentar en `docs/etapa-5.md` el cliente HTTP, `ApiError`, 204, encabezados y CORS (qué encabezados se envían y por qué) y la fila de semilla de `FacturaService`; verificar `npm test` en verde

## 3. Funciones puras de presentación y control (specs `frontend-consulta-facturas`, `frontend-formulario-factura`)

- [x] 3.1 RED + GREEN: `test/formato.test.mjs` / `ui/formato.ts` — `PES` 7260 → `$ 7.260,00`; `DOL` 9680 → `US$ 9.680,00` (mismo valor, sin conversión); `EUR` 100 → contiene `EUR` y `100,00`; `null` → visible como sin moneda; confirmar los literales con Node 24 antes de fijarlos y anotar el gotcha ` `
- [x] 3.2 RED + GREEN: fechas — `2026-11-01` → `01/11/2026`, `2026-12-31` → `31/12/2026`; número `null` → `—`, `3` → `3`; mutación temporal con `new Date(...)` + `toLocaleDateString` corriendo con `TZ=America/Argentina/Buenos_Aires` → falla; revertir
- [x] 3.3 RED + GREEN: `test/solicitudes.test.mjs` / `ui/solicitudes.ts` — `iniciar` dos veces: solo el segundo id es vigente; `invalidar` deja sin vigente al último
- [x] 3.4 RED + TRIANGULATE + GREEN: `test/validacion.test.mjs` / `ui/validacion.ts` — cantidad (`0`, `-2`, `''`, `1e400` inválidas con clave `detalles[i].cantidad`; `0.5` válida), bonificación (`0` y `100` válidas, `100.01` y `-0.01` inválidas, vacía omitida), descripción (255 válida, 256 inválida con mensaje que menciona 255, vacía omitida), observaciones (500/501), obligatorios de cabecera (`puntoVentaId`, `tipoMonedaId`, `condicionIvaId`), cliente vacío omitido, sin renglones → clave `detalles`, motivo de anulación 500/501, `montoMinimo` (`-1` inválido, `0` válido, vacío ausente)
- [x] 3.5 RED + GREEN: `ui/estimacion.ts` — renglones (3000.00 × 2 con 10 %) + (8000.00 × 1) → 13400; renglón sin opción o con cantidad inválida no suma
- [x] 3.6 Documentar en `docs/etapa-5.md` formato de moneda (PES/DOL/otros, sin conversión, SU-09), fechas sin `Date` y por qué, reglas de validación y por qué las fechas invertidas se dejan al backend, y la estimación neta como informativa (diferencias de centavos posibles); verificar `npm test` en verde

## 4. Región de estado y DOM seguro (spec `frontend-estado-accesibilidad`)

- [x] 4.1 RED + GREEN: `test/region-estado.test.mjs` con `temporizadorManual` — «Limpieza a los 6 s» (vacía y sigue en el DOM con `role="status"` y `aria-live="polite"`); a los 5999 ms todavía visible
- [x] 4.2 RED + GREEN: «Mensaje nuevo antes de los 6 s» (éxito, error a los 3 s, +4 s → error visible); error persistente a los 60 s con prefijo `Error:`; `limpiar()` lo retira; detalles por campo como lista
- [x] 4.3 RED + GREEN: `ui/dom.ts` — texto `<img src=x onerror=alert(1)>` en una celda y `<b>Casa</b>` en una opción quedan literales y no crean `img` ni `b`; mensaje de error `<script>x()</script>` en la región no crea `script`. Anotar la fila de semilla de los helpers
- [x] 4.4 Documentar en `docs/etapa-5.md` la región de estado (por qué `status`/`polite` y no `alert`, 6 s, errores persistentes, texto además de color) y la política sin `innerHTML`; verificar `npm test` en verde

## 5. Estructura de la página (specs `frontend-estado-accesibilidad`, `frontend-esqueleto`)

- [x] 5.1 RED: ampliar `test/index.test.mjs` — existe una única `#estado` con `role="status"` y `aria-live="polite"` sin `hidden`; formulario de filtros y de factura con `novalidate`; tabla con `caption` y `th scope="col"`; enlaces `#descarga-pdf` y `#descarga-xlsx` y ningún enlace de TXT; todos los `input`/`select`/`textarea` con `label` asociado; ningún `tabindex` positivo; todos los `button` con `type`; se conservan el meta `api-base-url`, el `script type="module"` y `#estado-api`
- [x] 5.2 GREEN: reescribir `index.html` (secciones de estado, filtros, resultados, formulario con `fieldset` de cabecera, contenedor de renglones, total estimado, botones, pie con la URL) adaptando la semilla; 5.1 en verde y las pruebas previas de `index.test.mjs` y `config.test.mjs` siguen verdes sin cambios
- [x] 5.3 Estilos (`styles.css`, D18): foco visible, `.visualmente-oculto`, colores de estado con texto, botones ≥ 44 px, tabla con desplazamiento propio; calcular a mano los cocientes de contraste de la paleta y anotarlos en `docs/etapa-5.md`; verificar `npm run build` y `npm test` en verde

## 6. Consulta: carga, filtros, tabla y búsquedas obsoletas (spec `frontend-consulta-facturas`)

- [x] 6.1 RED: `test/consulta.test.mjs` con `montarPagina()` y `servicioFalso()` — «Selectores completos» y «Cliente opcional» tras `app.listo`; esqueleto `aplicacion.ts` con `iniciarAplicacion` que no hace nada para que falle en la aserción
- [x] 6.2 GREEN: `iniciarAplicacion` (D13) con carga de catálogos y listado; 6.1 en verde
- [x] 6.3 RED + GREEN: «Catálogo que falla» (error en la región y la tabla igual se muestra); «Tabla de resultados» (dos filas en orden, `3` y `—`, importes y fecha formateados); «Tres coincidencias» y «Sin coincidencias» (`0 facturas` + mensaje)
- [x] 6.4 RED + GREEN: filtros — «Búsqueda por estado y fecha» (el servicio recibe exactamente esos filtros), «Limpiar filtros», «Monto negativo» (campo con `aria-invalid` y `aria-describedby`, ninguna llamada), «Monto cero»
- [x] 6.5 RED + GREEN: «Enlaces tras una búsqueda» (`href` de PDF y Excel con `estado=ANULADA` y su formato, construidos con la URL del servicio)
- [x] 6.6 RED + GREEN: carreras con diferidos — «Respuestas en orden inverso» (tabla, contador y enlaces quedan con `EMITIDA`), «Error tardío de una búsqueda vieja» (sin error y tabla de la búsqueda 2), «Búsqueda rechazada por la API» (400 visible, tabla/contador/enlaces conservan lo anterior); mutación temporal sin `esVigente` → fallan; revertir
- [x] 6.7 Documentar en `docs/etapa-5.md` la carga inicial, los filtros, cantidad y mensaje vacío, los enlaces sincronizados (misma función de query) y el control de búsquedas obsoletas con ids (por qué no `AbortController` solo); anotar la fila de semilla del listado; verificar `npm test` en verde

## 7. Formulario: renglones, validación, alta y bloqueo (spec `frontend-formulario-factura`)

- [x] 7.1 RED + GREEN: `test/formulario.test.mjs` — «Agregar y quitar» (tres renglones, se quita el segundo, quedan el primero y el tercero; ids de campo únicos; foco tras quitar); los campos del renglón 2 se llaman «… renglón 2»
- [x] 7.2 RED + GREEN: «Estimación con bonificación» (precios `3000,00` y `8000,00`, total `13.400,00` rotulado sin IVA) y «Renglón incompleto»
- [x] 7.3 RED + GREEN: validaciones en el submit — obligatorios sin elegir, sin renglones, cantidad, bonificación, descripción 256 y observaciones 501: campo con `aria-invalid="true"` y `aria-describedby` a su mensaje, foco en el primer inválido, **ninguna** llamada al servicio; «Corrección» (al guardar con el valor corregido se quitan las marcas)
- [x] 7.4 RED + GREEN: «Alta exitosa» — request exacto (sin `clienteId` con Consumidor Final; cantidad `0.5`; descripción de 255 completa; sin precios), región anuncia el borrador 15, formulario vacío, se pide de nuevo el listado con los filtros aplicados
- [x] 7.5 RED + GREEN: bloqueo con diferidos — durante el alta el formulario tiene `aria-busy="true"`, el `fieldset` está deshabilitado y las acciones de fila también; «Doble envío» (un segundo `requestSubmit` no llama de nuevo); «Liberación tras error» (400 → controles habilitados y `aria-busy="false"`); mutación temporal sin `finally` → falla; revertir
- [x] 7.6 RED + GREEN: «Valores conservados ante error» en el alta (500 no JSON → mensaje de respaldo y valores intactos) y errores por campo del servidor — `detalles[1].descripcion` marca el segundo renglón; clave desconocida → listada en la región
- [x] 7.7 Documentar en `docs/etapa-5.md` el formulario (renglones como `fieldset`, ids únicos, `novalidate` y por qué), la conservación tras error, el mapeo de `errores` del servidor y el bloqueo de doble envío; anotar la fila de semilla del alta; verificar `npm test` en verde

## 8. Edición de borradores (spec `frontend-formulario-factura`)

- [x] 8.1 RED + GREEN: `test/edicion.test.mjs` — «Borrador con dos renglones» (cabecera, renglones, título «Editar borrador 5», foco en el título) y «Ya fue emitido» (error, sin modo edición)
- [x] 8.2 RED + GREEN: «Edición exitosa» (`actualizarFactura(5, …)` con la fecha cargada reenviada, anuncio, vuelta al modo alta vacío, recarga del listado) y «Conflicto al editar» (409: valores y modo edición conservados, detalle visible)
- [x] 8.3 RED + GREEN: «Cancelar sin guardar» (formulario vacío en modo alta, ningún `PUT`)
- [x] 8.4 RED + GREEN: cargas obsoletas con diferidos — «Cancelación antes de la respuesta» y «Otro borrador elegido después» (queda el 6 aunque el 5 llegue después); mutación temporal sin `esVigente` en la carga → falla; revertir
- [x] 8.5 Documentar en `docs/etapa-5.md` el modo edición, el reenvío de la fecha (dependencia abierta 4 de la etapa 4), la cancelación y el descarte de cargas obsoletas; verificar `npm test` en verde

## 9. Acciones por estado (spec `frontend-acciones-factura`)

- [x] 9.1 RED + GREEN: `test/acciones.test.mjs` — «Tres estados» (botones por estado) y «Nombre accesible» (incluye acción e id)
- [x] 9.2 RED + GREEN: emisión — «Emisión exitosa» (anuncia número 4 y recarga) y «Emisión rechazada» (409 visible, tabla igual); «Doble clic en emitir» (un solo `PATCH`, acciones liberadas al terminar)
- [x] 9.3 RED + GREEN: eliminación con `Dialogos` falso — «Confirmada» (`eliminarFactura(6)` resuelta con `undefined` de un 204, anuncio, recarga), «Rechazada» (ninguna llamada), «Factura inexistente» (404 visible hasta la siguiente acción)
- [x] 9.4 RED + GREEN: anulación — «Con motivo», «Sin motivo» (vacío y solo espacios → sin `observaciones`), «Motivo demasiado largo» (error que menciona 500, ninguna llamada) y «Diálogo cancelado» (`pedirMotivo` → `null`: ninguna llamada y fila EMITIDA); mutación temporal que envía el `PATCH` aunque el diálogo devuelva `null` → falla; revertir
- [x] 9.5 Documentar en `docs/etapa-5.md` la tabla de acciones por estado, los diálogos nativos detrás de `Dialogos` (por qué `confirm`/`prompt` y no `<dialog>`) y la cancelación sin petición; verificar `npm test` en verde

## 10. Arranque en el navegador

- [x] 10.1 Reescribir `src/app.ts` (D13): lee el meta, escribe la URL en `#estado-api`, crea `FacturaService` con `(url, init) => fetch(url, init)`, `Dialogos` con `window.confirm`/`window.prompt` y el temporizador con `setTimeout`/`clearTimeout`, y llama a `iniciarAplicacion`; un error de configuración se muestra como texto en la región. Verificar `npm test` y `npm run build` en verde y, con `npm run dev` **sin** backend, que la página carga sin errores de módulo en la consola y la región muestra el error de conexión (falla de red → `ApiError` 0); detener `http-server` y verificar que el puerto 5173 queda libre
- [x] 10.2 Revisión estática: `grep -rnE "innerHTML|outerHTML|insertAdjacentHTML|document\.write" frontend/src` sin resultados; `grep -n '"strict": true' frontend/tsconfig.json` presente; `git diff --stat -- frontend/package.json frontend/package-lock.json frontend/tsconfig.json` vacío (solo lectura); anotar el resultado en el informe

## 11. Recorrido manual con backend real [requiere backend etapa 4]

- [x] 11.1 Precondición: el apply de `etapa-4-escritura-estados-concurrencia` terminó y su `./gradlew clean test` está en verde (consultar al estudiante/orquestador; no correr Gradle si el otro apply sigue en curso). Comparar las «Dependencias del contrato de etapa 4» del design con lo implementado (`FacturaRequestDTO`, `errores`, CORS) y, si algo difiere, ajustar el frontend con su ciclo RED/GREEN antes de seguir
- [x] 11.2 [requiere backend etapa 4] Levantar backend (`JAVA_HOME=/c/Users/zbook/.jdks/ms-17.0.20.1 ./gradlew bootRun`, H2, 8080) y frontend (`npm run dev`, 5173); abrir `http://localhost:5173/` y verificar consola sin errores, catálogos cargados y las 6 facturas del `DataLoader`
- [x] 11.3 [requiere backend etapa 4] Circuito crear–editar–emitir–anular: crear un borrador con dos renglones (uno con bonificación), editarlo (cambiar un renglón), emitirlo (anotar número y CAE desde el detalle o `requests.http`), anularlo con motivo; registrar en `docs/etapa-5.md` ids, números, estados e importes observados en cada paso, y capturas en `docs/evidencia/etapa-5/`
- [x] 11.4 [requiere backend etapa 4] Eliminación de otro borrador (confirmar; verificar que desaparece y que un `GET` de su id da 404) y cancelaciones (rechazar la confirmación de eliminar y cancelar el diálogo de anulación: en la pestaña Red del navegador no hay `DELETE` ni `PATCH`); registrar la evidencia
- [x] 11.5 [requiere backend etapa 4] Errores controlados: descripción de 256 caracteres (el cliente la frena) y un 400 del servidor (fechas invertidas en filtros) y un 409 (por ejemplo, emitir desde otra pestaña el borrador que se está editando y luego guardar); verificar mensajes visibles, valores conservados y región de estado; registrar
- [x] 11.6 [requiere backend etapa 4] Descargas: con un filtro aplicado (p. ej. `EMITIDA`), descargar PDF y Excel desde los enlaces y TXT desde la URL de `urlReporte(...,'txt')` (abierta a mano); comprobar que contienen exactamente las facturas de la tabla; guardar las tres muestras en `docs/evidencia/etapa-5/` y revisarlas visualmente
- [x] 11.7 [requiere backend etapa 4] Detener ambos servidores y verificar con `netstat -ano | grep -E ":(8080|5173) "` que los puertos 8080 y 5173 quedan libres; anotar fecha, navegador y versiones en el informe

## 12. Revisión de accesibilidad de la página final (skills `accessibility`, `accessibility-review`) [requiere backend etapa 4]

- [ ] 12.1 [requiere backend etapa 4] Recorrido solo con teclado (Tab, Mayús+Tab, Enter, Espacio, flechas, Escape en los diálogos) del circuito completo: orden de foco igual al visual, foco siempre visible, sin trampas; anotar hallazgos por principio POUR con severidad (Crítico/Mayor/Menor) en `docs/etapa-5.md` y corregir los críticos y mayores con su ciclo RED/GREEN cuando sean comprobables en jsdom
- [ ] 12.2 [requiere backend etapa 4] Contraste y zoom: verificar con las herramientas del navegador los cocientes de 5.3 (texto ≥ 4,5:1, controles y foco ≥ 3:1) y la página al 200 % (sin pérdida de contenido ni desplazamiento horizontal fuera de la tabla); comprobar que los anuncios de la región `status` se oyen con un lector de pantalla si hay uno disponible (NVDA/Narrador) o declarar que no se probó; dejar en el informe la línea «Revisión de teclado y lector de pantalla del estudiante: **COMPLETAR POR EL ESTUDIANTE**»

## 13. Informe, README y verificación final

- [x] 13.1 Completar `docs/etapa-5.md`: decisiones (D1–D18 resumidas, sin repetir el enunciado), control asíncrono, comprobaciones de accesibilidad y su límite (jsdom no demuestra lectores de pantalla), evidencia del circuito y de la eliminación (grupo 11), descargas de ejemplo, límites de la simulación (CAE ficticio, sin ARCA real, sin autenticación: `X-Usuario` declara y CORS no autentica, H2 se borra al reiniciar, estimación sin IVA no vinculante), semilla reutilizada y qué cambió (D15), referencia del docente consultada solo por nombres (D16), dependencias de la etapa 4 y su estado final, pruebas de caracterización y mutaciones, y la sección «Uso de IA» con el formato de `docs/etapa-4.md` (herramientas, qué se pidió, «Qué se revisó y qué se corrigió: **COMPLETAR POR EL ESTUDIANTE**», línea `Co-Authored-By` en los commits); verificar que no quedan secciones vacías salvo las marcadas para el estudiante
- [x] 13.2 Actualizar `README.md` (sin tocar las secciones de etapas anteriores salvo para enlazar): requisitos, `npm ci`, `npm run build`, `npm test`, `npm run dev`, cómo levantar backend y frontend juntos en dos terminales (con JDK 17), cambiar el meta si cambia el puerto, cómo detenerlos y liberar 8080/5173, y la tabla de verificaciones de la etapa 5 con fecha y entorno; verificar que los comandos documentados corren tal como están escritos
- [x] 13.3 Verificación final: en `frontend/`, `npm ci`, `npm test` (anotar cantidad de pruebas) y `npm run build` en verde; **[requiere backend etapa 4]** con el apply de la etapa 4 terminado, `JAVA_HOME=/c/Users/zbook/.jdks/ms-17.0.20.1 ./gradlew clean test bootJar` en verde y `git status --short -- src build.gradle settings.gradle` sin cambios atribuibles a este change; repetir la revisión estática de 10.2
- [x] 13.4 Entregar al estudiante (sin ejecutarlos) los comandos de commit, PR y tag `etapa-5` con mensajes que terminan en `Co-Authored-By: Claude <modelo en uso> <noreply@anthropic.com>`

## Workflow follow-up

- Archivar este change después de que la etapa 4 (`C-05`) esté archivada y el estudiante haya revisado el informe y creado el tag `etapa-5`.
- Marcar `C-06` como `[x]` en `CHANGES.md` al archivar.
- Preparar la demostración de defensa (Flujo F.1 de la KB) sobre el tag `etapa-5`.
