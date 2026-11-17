# Tasks

> Reglas para todo el apply:
> - Gradle siempre con `JAVA_HOME=/c/Users/zbook/.jdks/ms-17.0.20.1` (nunca el JDK 26). Para una clase: `./gradlew test --tests '<FQN>'`.
> - TDD estricto: cada **GREEN** va precedida de su **RED**, y la RED se cierra solo tras ver la falla **por la razón esperada** (aserción o status HTTP, no error de compilación ajeno: si no compila, crear primero el esqueleto mínimo). Las pruebas que pasan al escribirse se marcan como **caracterización** en `docs/etapa-3.md`. Toda pieza de la semilla (`api-facturas`) entra solo en un GREEN y se anota en el informe qué se trajo y qué se cambió (inventario del design).
> - Valores esperados literales, tomados de los escenarios de `specs/`; nunca recalculados como lo hace el código. AssertJ y `jsonPath`; sin aserciones triviales.
> - Nunca devolver entidades; DTO construidos dentro del servicio (`open-in-view: false`). Nada de aritmética con `double` ni `Math.round` en código nuevo.
> - Sin `POST`/`PUT`/`PATCH`/`DELETE`, sin paginación, sin CRUD de catálogos, sin cambios en `build.gradle` ni en el `DataLoader`. Sin comandos Git que cambien estado.
> - Las pruebas de integración con MockMvc **no** son `@Transactional` (design D12); limpian datos en `@AfterEach`.
> - Gobernanza MEDIA: al cerrar cada grupo, correr `./gradlew test` completo y reportar al estudiante antes de seguir.

## 1. Red de seguridad e informe

- [x] 1.1 Red de seguridad: `./gradlew clean test` con JDK 17; anotar el baseline (se esperan 179 pruebas en verde) en el borrador de `docs/etapa-3.md`; si algo falla, detenerse y reportarlo como falla preexistente
- [x] 1.2 Crear `docs/etapa-3.md` con el esqueleto de secciones (contratos y DTO, filtros, consulta con parámetros, detalle y `open-in-view`, reporte y formatos, catálogos, errores y códigos HTTP, CORS, semilla reutilizada, referencia del docente, decisiones, verificaciones, uso de IA); verificar que el archivo existe con esas secciones

## 2. Normalización y validación de filtros (specs `api-listado-facturas`, `api-descarga-reporte`)

- [x] 2.1 RED: `repository/FiltroFacturasTest` (unitaria): `FiltroFacturas.desde(new FacturaFiltrosDTO(null, null, " emitida ", null))` da estado `EMITIDA`; crear el esqueleto mínimo (`dto/FacturaFiltrosDTO` record, `repository/FiltroFacturas` record con `desde` que devuelve todo nulo, `exception/ParametroInvalidoException`) para que falle en la aserción
- [x] 2.2 GREEN: normalización `strip` + `toUpperCase(Locale.ROOT)` (design D3); 2.1 en verde
- [x] 2.3 RED + TRIANGULATE + GREEN: estado `"  "` y `""` → ausente; `PAGADA` → `ParametroInvalidoException` cuyo mensaje contiene `PAGADA`, `BORRADOR`, `EMITIDA` y `ANULADA`; `anulada` → `ANULADA`
- [x] 2.4 RED + GREEN: fechas — `2026-11-10`/`2026-11-01` → excepción que menciona `fechaDesde` y `fechaHasta`; iguales válido; solo una de las dos válido
- [x] 2.5 RED + GREEN: `montoMinimo` `NaN`, `POSITIVE_INFINITY`, `NEGATIVE_INFINITY` → excepción que menciona `montoMinimo` (`@ParameterizedTest`); `-50.0` válido y conservado; todo nulo → igual a `FiltroFacturas.SIN_FILTROS`
- [x] 2.6 REFACTOR con el grupo en verde; documentar en `docs/etapa-3.md` las reglas de normalización (por qué `Locale.ROOT`, estado vacío como ausente y su riesgo, sin mínimo cero, por qué `Double` para detectar no finitos); verificar `./gradlew test` en verde

## 3. Consulta JPQL con filtros (spec `proyeccion-reporte-facturas`)

- [x] 3.1 RED: en `repository/FacturaReporteRepositoryTest` (`@DataJpaTest`, helpers existentes), «límites incluidos» (desde 2026-11-05 hasta 2026-11-10 → las del 10 y del 05, en ese orden) llamando a `buscarReporte(FiltroFacturas)`; agregar la firma a `FacturaReporteRepository` con un `Impl` que ignore el filtro, para que falle en la aserción
- [x] 3.2 GREEN: `WHERE` con fragmentos constantes y `setParameter` solo para los filtros presentes, insertado entre `SELECCION` y `AGRUPACION_Y_ORDEN` (design D4); `buscarReporte()` pasa a `default` que delega con `SIN_FILTROS`; 3.1 en verde y todas las pruebas de la etapa 2 siguen verdes sin cambios
- [x] 3.3 RED + TRIANGULATE + GREEN: «solo fecha hasta», «solo anuladas», «total igual al mínimo» (100.00, 100.10, 250.00 con mínimo 100.10 → 250.00 y 100.10; design D5), «filtros sin coincidencias» (lista vacía) y «filtros vacíos» (igual fila por fila a `buscarReporte()`)
- [x] 3.4 RED + GREEN: «combinación con factura sin cliente y sin renglones» (solo la EMITIDA sin cliente ni renglones, `Consumidor Final`, ítems 0)
- [x] 3.5 RED + GREEN: «valores fuera del texto de la consulta» — prueba unitaria sobre el método package-private `jpql(FiltroFacturas)`: con desde 2026-11-05, EMITIDA y 123.45 el texto contiene `:fechaDesde`, `:estado` y `:montoMinimo` y no contiene `2026-11-05`, `EMITIDA` ni `123.45`
- [x] 3.6 Documentar en `docs/etapa-3.md` cómo se arma el `WHERE` (fragmentos fijos, parámetros nombrados, por qué no `:p IS NULL OR`, por qué no concatenar) y por qué comparar `double` con `>=` no es aritmética (borde 100.10); verificar `./gradlew test` en verde

## 4. Listado HTTP y errores de entrada (specs `api-listado-facturas`, `api-errores`)

- [x] 4.1 RED: `controller/FacturaRestControllerTest` (`@WebMvcTest(FacturaRestController.class)`, `@MockBean FacturaService`, `Clock` fijo por `@TestConfiguration`): `GET /api/facturas` → 200 con dos filas y los diez campos (`jsonPath`), y el servicio recibe un `FacturaFiltrosDTO` con los cuatro parámetros convertidos (`fechaDesde=2026-11-05` → `LocalDate`). Crear el esqueleto (`service/FacturaService` con `listar` que lanza `UnsupportedOperationException`, controlador vacío) para que falle por 404
- [x] 4.2 GREEN: `FacturaRestController` con el `@GetMapping` del listado adaptado de la semilla (`LocalDate` ISO, inyección por constructor; design D1/D2); 4.1 en verde. Anotar la fila de semilla en el informe
- [x] 4.3 RED + TRIANGULATE: «base sin facturas» → `200 []`; servicio que lanza `ParametroInvalidoException("...PAGADA...")` → 400 `application/problem+json` con `status`, `title`, `detail` y `timestamp` igual al del `Clock` fijo
- [x] 4.4 GREEN: `exception/ApiExceptionHandler extends ResponseEntityExceptionHandler` adaptado del `GlobalExceptionHandler` de la semilla (`ProblemDetail` + `timestamp` agregado en `handleExceptionInternal`; handler de `ParametroInvalidoException`; design D8); 4.3 en verde
- [x] 4.5 RED + GREEN: tipos incompatibles — `fechaDesde=05/11/2026`, `fechaHasta=ayer` y `montoMinimo=cien` → 400 con detalle en español que nombra el parámetro (y `yyyy-MM-dd` para fechas), sin texto en inglés de Spring (`Failed to convert`); sobrescribir `handleTypeMismatch`
- [x] 4.6 RED + GREEN: `service/FacturaServiceTest` (unitaria con repositorio simulado) — `listar` normaliza con `FiltroFacturas.desde` y pasa el filtro validado al repositorio; `estado=PAGADA` lanza sin consultar el repositorio. Implementar `FacturaService.listar` (`@Transactional(readOnly = true)` en la clase)
- [x] 4.7 RED + GREEN: `controller/FacturaApiIntegracionTest` (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`, sin `@Transactional`, limpieza en `@AfterEach`): «sin filtros» (orden 10, 05, 01), «rango con límites exactos», «mismo día», «estado con espacios y minúsculas» (`%20emitida%20`), «estado vacío», «estado inexistente» (400), «rango invertido» (400), «NaN e infinitos» incluyendo `1e400` (400), «mínimo negativo», «total igual al mínimo», «filtros combinados», «filtros sin coincidencias» (`[]`), «cliente opcional» y «borrador sin número»
- [x] 4.8 Documentar en `docs/etapa-3.md` el contrato del listado, por qué `FacturaReporteDTO` y no la entidad, consulta vacía 200 frente a 404 y la tabla de errores 400 de entrada; verificar `./gradlew test` en verde

## 5. Detalle de factura (spec `api-detalle-factura`)

- [x] 5.1 RED: en `FacturaRestControllerTest`, `GET /api/facturas/7` con el servicio devolviendo un `FacturaDetalladaDTO` literal → 200 con todos los campos de cabecera y dos renglones (`jsonPath`); crear `dto/FacturaDetalladaDTO` y `dto/FacturaDetalleDTO` como `record` (design D6) y `obtenerDetalle` que lanza `UnsupportedOperationException`
- [x] 5.2 GREEN: `@GetMapping("/{id}")`; 5.1 en verde
- [x] 5.3 RED + GREEN: servicio que lanza `RecursoNoEncontradoException("Factura", 999999L)` → 404 `ProblemDetail` cuyo detalle contiene `999999`; `GET /api/facturas/abc` → 400 que menciona `id`. Traer `RecursoNoEncontradoException` de la semilla y su handler 404
- [x] 5.4 RED: en `FacturaApiIntegracionTest`, «factura emitida con cliente» (datos literales del escenario, ids de las asociaciones, `EMITIDA`, CAE, observaciones), «borrador sin autorización», «factura sin cliente» (`clienteId` nulo, `Consumidor Final`), «dos renglones» (orden por id, `listaPrecioArticuloId`, `articuloCodigo` `ART-001`/`ART-003`), «cabecera sin renglones» (`[]`), «id inexistente» (404) — todo por HTTP con `open-in-view: false` y sin transacción de prueba
- [x] 5.5 GREEN: `FacturaService.obtenerDetalle` con `findById` y mapeo dentro de la transacción de solo lectura (design D6); 5.4 en verde
- [x] 5.6 Verificar que la prueba de 5.4 detecta el problema que protege: quitar temporalmente `@Transactional(readOnly = true)` del servicio, comprobar que falla por `LazyInitializationException` y restaurarlo (mutación de validación, no queda en el código); anotarlo en el informe
- [x] 5.7 Documentar en `docs/etapa-3.md` el contenido de `FacturaDetalladaDTO` y por qué se arma en el servicio (`open-in-view: false`, cargas diferidas, por qué la prueba no es transaccional), la alternativa `JOIN FETCH` y la diferencia con la referencia (`codigoMoneda`); verificar `./gradlew test` en verde

## 6. Descarga de reportes (spec `api-descarga-reporte`)

- [x] 6.1 RED: `report/FormatoReporteTest` (unitaria, `@ParameterizedTest`): `pdf`, `xlsx`, `excel`, `txt`, `tsv`, `" XLSX "` → formato, tipo de contenido y extensión esperados; `doc` → `ParametroInvalidoException` que menciona `doc` y los admitidos
- [x] 6.2 GREEN: `report/FormatoReporte` y `report/ArchivoReporte` (design D10); 6.1 en verde
- [x] 6.3 RED: en `FacturaRestControllerTest`, `GET /api/facturas/reporte?formato=txt&estado=EMITIDA` con el servicio devolviendo un `ArchivoReporte` literal → 200, `Content-Type` `text/plain;charset=UTF-8`, `Content-Disposition` `attachment; filename="facturas.txt"` y el cuerpo exacto; sin `formato` → el servicio recibe `"pdf"`; con `formato=` vacío → también `"pdf"`
- [x] 6.4 GREEN: `@GetMapping("/reporte")` con `@RequestParam(defaultValue = "pdf")` y los cuatro filtros, `ResponseEntity<byte[]>` con `ContentDisposition.attachment()`; reemplaza a `/export/pdf` y `/export/excel` de la semilla (anotar en el informe); 6.3 en verde
- [x] 6.5 RED + GREEN: `FacturaService.generarReporte(filtros, formato)` valida el formato **antes** de consultar (unitaria: con `doc` el repositorio no se invoca), aplica `FiltroFacturas.desde`, consulta y delega en `ReporteFacturasService`
- [x] 6.6 RED + GREEN: servicio que lanza `GlifoNoSoportadoException(0xE000, "cliente", 1)` → 400 `ProblemDetail` cuyo detalle contiene `U+E000` y sugiere Excel o TXT, sin `Content-Disposition` (design D9; marcar en el informe como **pendiente de confirmar con el docente**)
- [x] 6.7 RED + GREEN: en `FacturaApiIntegracionTest` — «PDF» (`%PDF`, `facturas.pdf`), «Excel» (se abre con `XSSFWorkbook`, hoja `Facturas`), «TXT» (encabezado de nueve columnas), «sin formato», «alias excel y tsv», «mayúsculas», «formato doc» (400 sin `Content-Disposition`), «filtro inválido en el reporte» (400), «reporte filtrado sin coincidencias» (solo encabezado), «archivo filtrado igual al listado» (filas del TXT con `estado=EMITIDA` comparadas con el JSON del listado con el mismo filtro: número, fecha, cliente y total en el mismo orden) y «cliente con carácter de uso privado» + «alternativas disponibles»
- [x] 6.8 Documentar en `docs/etapa-3.md` formatos, alias, tipos de contenido y nombres, el código 400 del glifo con su justificación y estado pendiente, y la adaptación de la semilla (dos endpoints `/export/*` → uno, sin escribir en `HttpServletResponse`); verificar `./gradlew test` en verde

## 7. Catálogos de solo lectura (spec `api-catalogos`)

- [x] 7.1 RED: `controller/CatalogoRestControllerTest` (`@WebMvcTest`, `@MockBean CatalogoService`): las cinco rutas devuelven 200 con las opciones del servicio y exactamente los campos `id`, `etiqueta`, `valor`, `codigoMoneda`; crear `dto/OpcionDTO` record y el esqueleto del servicio
- [x] 7.2 GREEN: `controller/CatalogoRestController` con los cinco `@GetMapping` (nuevo, no hay semilla); 7.1 en verde
- [x] 7.3 RED + GREEN: «intento de alta» — `POST`, `PUT`, `PATCH` y `DELETE` sobre una ruta de catálogo → 405 `ProblemDetail` con `timestamp` (verifica que `ResponseEntityExceptionHandler` lo cubre)
- [x] 7.4 RED: `service/CatalogoServiceIntegracionTest` (`@DataJpaTest` + `@Import(CatalogoService.class)`, o `@SpringBootTest`): «punto de venta 1» (`0001 - Casa Central`), «cliente con CUIT» (`Juan Servicios (20-12345678-9)`), «Responsable Inscripto», «pesos y dólares» (`codigoMoneda` `PES`/`DOL`, `valor` nulo), «martillo en la lista general» (id de la oferta, `ART-001 - Martillo (GENERAL)`, 3000.00), «mismo artículo en dos listas», orden por id y «catálogo vacío»
- [x] 7.5 GREEN: `CatalogoService` (readOnly) y la consulta `join fetch` en `ListaPrecioArticuloRepository` (design D7); 7.4 en verde
- [x] 7.6 RED + GREEN: en `FacturaApiIntegracionTest` (o una clase de integración de catálogos), `GET /api/catalogos/lista-precio-articulos` por HTTP con `open-in-view: false` → 200 sin error de carga diferida, y `POST /api/catalogos/clientes` no cambia la cantidad de clientes
- [x] 7.7 Documentar en `docs/etapa-3.md` el formato de `OpcionDTO`, las etiquetas, el `id` de la oferta como futuro `listaPrecioArticuloId` y por qué no hay CRUD; verificar `./gradlew test` en verde

## 8. Errores transversales (spec `api-errores`)

- [x] 8.1 RED + GREEN: en `FacturaRestControllerTest`, servicio que lanza `RuntimeException("SQL interno secreto")` → 500 `ProblemDetail` con detalle genérico; el cuerpo no contiene `SQL interno secreto` ni `java.`/`Exception`; agregar el handler de `Exception` con log SLF4J (design D8)
- [x] 8.2 RED + GREEN: «escritura todavía no disponible» — `DELETE /api/facturas/1` y `POST /api/facturas` → 405 con `status` 405 y `timestamp`
- [x] 8.3 Caracterización: «listado vacío frente a detalle inexistente» por HTTP en la integración (200 `[]` y 404) y verificación de que todo error probado en los grupos 4-7 tiene `Content-Type` `application/problem+json`; anotar como caracterización si pasan al escribirse
- [x] 8.4 Documentar en `docs/etapa-3.md` la tabla completa de códigos (400, 404, 405, 500) con su excepción de origen, por qué no se mapea `IllegalArgumentException` genérico y por qué 500 no muestra el mensaje; verificar `./gradlew test` en verde

## 9. CORS (spec `api-cors`)

- [x] 9.1 RED: `config/CorsConfigTest` (`@WebMvcTest(FacturaRestController.class)` con servicio simulado): `Origin: http://localhost:5173` → `Access-Control-Allow-Origin: http://localhost:5173`; preflight `OPTIONS /api/catalogos/clientes` desde `http://127.0.0.1:3000` con `GET` → 200; reporte con `Origin` → `Access-Control-Expose-Headers` contiene `Content-Disposition`
- [x] 9.2 GREEN: `config/CorsProperties` y `config/CorsConfig implements WebMvcConfigurer` adaptado de la semilla (cuatro orígenes exactos, solo `GET`, `exposedHeaders`; design D11) y `facturacion.cors.allowed-origins` en `application.yml`; 9.1 en verde
- [x] 9.3 RED + TRIANGULATE + GREEN: `Origin: http://localhost:8081` y `http://evil.example` → 403 sin `Access-Control-Allow-Origin`; sin `Origin` → 200 sin encabezados CORS («cliente sin navegador»); los cuatro orígenes admitidos (`@ParameterizedTest`)
- [x] 9.4 RED + GREEN: «propiedad sobrescrita» — prueba con `@TestPropertySource(properties = "facturacion.cors.allowed-origins=http://localhost:4200")`: 4200 admitido y 5173 rechazado
- [x] 9.5 Documentar en `docs/etapa-3.md` la configuración CORS y por qué **no autentica** (lo aplica el navegador, `curl` lo ignora, no identifica usuarios; ver `knowledge-base/03_actores_y_roles.md`) y la adaptación de la semilla (sin `localhost:*` ni redes privadas); verificar `./gradlew test` en verde

## 10. Ejemplos reproducibles y prueba manual

- [x] 10.1 Crear `requests.http` en la raíz (design D13) con los ejemplos de listado (sin filtros, cada filtro, combinado, estado con espacios, vacío, fechas invertidas, estado desconocido, `NaN`, fecha mal formada), detalle (existente e inexistente), reporte (`pdf`, `xlsx`, `excel`, `txt`, `tsv`, sin formato, `doc`), los cinco catálogos y una petición con `Origin`; cada bloque con un comentario del resultado esperado; verificar que cada URL coincide con una ruta probada en los grupos 4-9
- [x] 10.2 Prueba manual: arrancar la app con `./gradlew bootRun` (JDK 17, en segundo plano), esperar a que responda 8080 y ejecutar con `curl -i` al menos: listado sin filtros (6 facturas del `DataLoader`), `estado=%20emitida%20` (3), `estado=PAGADA` (400 `problem+json`), detalle de un id existente y de `999999` (404), `reporte?formato=xlsx` guardado a `build/manual/` (cabeceras correctas), `formato=doc` (400), `catalogos/lista-precio-articulos` y una petición con `Origin: http://localhost:5173`; detener la app y **verificar que el puerto 8080 quedó libre** (`netstat -ano | grep :8080` sin `LISTENING`); registrar comandos y resultados en `docs/etapa-3.md`

## 11. Informe, README y verificación final

- [x] 11.1 Completar `docs/etapa-3.md` (~1 carilla, sin repetir el enunciado): justificación de los DTO (`FacturaReporteDTO`, `FacturaDetalladaDTO`, `OpcionDTO`; por qué no entidades), manejo de errores, filtros, CORS, tabla de semilla reutilizada/adaptada/nueva/descartada, referencia del docente consultada **solo para nombres** (qué se miró y en qué nos apartamos), decisiones D1-D13 resumidas, pruebas de caracterización declaradas, glifo 400 pendiente de confirmar, verificaciones y entorno (JDK 17, H2) y sección **Uso de IA** con el mismo formato que `docs/etapa-2.md` (herramientas; qué se pidió; «Qué se revisó y qué se corrigió: **COMPLETAR POR EL ESTUDIANTE**»; línea `Co-Authored-By`); verificar que no queda ninguna sección vacía salvo la del estudiante
- [x] 11.2 Actualizar `README.md` con una sección «Etapa 3: API REST de lectura» (endpoints, filtros, formatos, catálogos, cómo usar `requests.http`, orígenes CORS y la propiedad para cambiarlos); verificar que los comandos y URLs documentados corren tal cual
- [x] 11.3 Revisión de reglas duras: búsqueda en `src/main` de `Math.round` y de aritmética con `double` en código nuevo (sin resultados), de controladores que devuelvan tipos de `model/` (ninguno) y de concatenación de valores en JPQL (ninguna); registrar el resultado en el informe
- [x] 11.4 Verificación final: `./gradlew clean test bootJar` con JDK 17 en verde; anotar en `docs/etapa-3.md` el total de pruebas (baseline 179 + nuevas) y que `build/libs/` contiene solo el JAR ejecutable

## Workflow follow-up

- Entregar al estudiante los comandos de Git (commit(s) con la línea `Co-Authored-By`, push, PR hacia `master` y tag `etapa-3`); el estudiante los ejecuta.
- Pedir al estudiante que complete «Qué se revisó y qué se corrigió» en `docs/etapa-3.md` y que consulte IN-03 (código del glifo) con el docente.
- `/opsx:archive etapa-3-api-rest-lectura` y marcar `[x]` C-04 en `CHANGES.md`.
