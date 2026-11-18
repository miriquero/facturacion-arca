# Facturacion ARCA (TP Integrador UTN FRM)

Aplicacion academica de facturacion con ARCA **simulada**: API REST Spring Boot (backend, puerto 8080) y frontend TypeScript sin framework (puerto 5173). Este README cubre la **etapa 0: puesta en marcha** (esqueleto del backend, base de datos y frontend).

## Requisitos

| Herramienta | Version | Nota |
|-------------|---------|------|
| JDK | **17** | Gradle 8.10.2 no corre con JDK 26 (el que esta por defecto en `PATH` de esta maquina) |
| Node.js | >= 18.13 | incluye `npm` |
| Docker Desktop | opcional | solo para el perfil `postgres` |

No hace falta instalar Gradle: se usa el wrapper (`gradlew` / `gradlew.bat`, version 8.10.2).

### Fijar JAVA_HOME (obligatorio, en cada terminal nueva)

PowerShell:

```powershell
$env:JAVA_HOME = "C:\Users\zbook\.jdks\ms-17.0.20.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version   # debe decir 17
```

Git Bash:

```bash
export JAVA_HOME="/c/Users/zbook/.jdks/ms-17.0.20.1"
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # debe decir 17
```

## Backend

PowerShell: `.\gradlew.bat <tarea>` o `gradlew.bat <tarea>`. Git Bash: `./gradlew <tarea>`.

| Que | PowerShell | Bash |
|-----|------------|------|
| Arrancar (puerto 8080) | `.\gradlew.bat bootRun` | `./gradlew bootRun` |
| Pruebas | `.\gradlew.bat test` | `./gradlew test` |
| Pruebas + JAR | `.\gradlew.bat clean test bootJar` | `./gradlew clean test bootJar` |
| Ejecutar el JAR | `java -Duser.timezone=America/Argentina/Buenos_Aires -jar build\libs\facturacion-arca-1.0.0.jar` | `java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/facturacion-arca-1.0.0.jar` |

`bootJar` deja un unico archivo: `build/libs/facturacion-arca-1.0.0.jar`.

La zona horaria `America/Argentina/Buenos_Aires` la fija Gradle para `test` y `bootRun`; con `java -jar` hay que pasar la bandera `-Duser.timezone` (el driver de PostgreSQL envia la zona de la JVM al conectar y en Windows algunas zonas son rechazadas).

### Consola H2 (base por defecto)

Con el backend en marcha: <http://localhost:8080/h2-console>

| Campo | Valor |
|-------|-------|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:facturacion` |
| User Name | `sa` |
| Password | (vacia) |

> H2 es **en memoria**: al detener el backend se pierden todos los datos.

## Etapa 1: persistencia y datos de ejemplo

Informe de la etapa: [docs/etapa-1.md](docs/etapa-1.md).

- Al arrancar (`bootRun` o `java -jar`) un `DataLoader` provisorio carga 3 usuarios (`sistema`, `acortez`, `mriquero`), catálogos, 2 clientes y 6 facturas (3 EMITIDA, 1 ANULADA, 2 BORRADOR) en la H2 en memoria. Si la base ya tiene usuarios no agrega nada.
- Para verlos: con el backend en marcha, abrir la consola H2 (sección anterior) y ejecutar, por ejemplo, `select estado, count(*) from factura_venta group by estado` (devuelve 3 EMITIDA, 1 ANULADA, 2 BORRADOR) y `select usuario from usuario` (3 filas).
- Las pruebas de persistencia usan `@DataJpaTest` sobre H2 embebida (cada contexto con su propia base). Las tres pruebas `@SpringBootTest` de la etapa 0 usan el perfil `test`, que **excluye** el `DataLoader` para que no escriba en la H2 compartida.
- Las pruebas se corren con el comando de siempre: `./gradlew test`. PostgreSQL no se usó en esta etapa (ver `docs/etapa-1.md`).

## Etapa 2: proyección y reportes

Informe de la etapa: [docs/etapa-2.md](docs/etapa-2.md).

- **Qué hay**: `FacturaReporteRepository` (fragmento con `EntityManager` y una consulta JPQL `SELECT new` a `FacturaReporteDTO`, sin cargar entidades) y `ReporteFacturasService`, que recibe una lista de `FacturaReporteDTO` y devuelve los **bytes** de un PDF (A4 apaisado), un Excel (hoja `Facturas`, totales `SUMIFS` por moneda) o un TXT (UTF-8, tabulaciones, escapes reversibles). Todavía **no hay endpoints**: se exponen en la etapa 3.
- **Muestras**: `./gradlew test` (o `./gradlew test --tests '*MuestrasReportesTest'`) deja en `build/muestras-reportes/` los archivos `facturas.pdf`, `facturas-varias-paginas.pdf`, `facturas.xlsx` y `facturas.txt` para revisarlos a ojo (PDF en un visor, Excel en Excel o LibreOffice, TXT en un editor UTF-8). Esa carpeta no se versiona y `./gradlew clean` la borra.
- **Fuente del PDF**: `src/main/resources/fonts/NotoSansCJKsc-Regular.otf` (Noto Sans CJK SC, descargada de <https://github.com/notofonts/noto-cjk>, ~16 MB, versionada sin Git LFS) y su licencia `fonts/OFL.txt` (SIL Open Font License 1.1); ambas viajan en el JAR. Si el PDF falla con «No se encontró la fuente», falta ese archivo en el classpath. Un carácter sin glifo en la fuente (p. ej. U+E000) rechaza el PDF con `GlifoNoSoportadoException`; Excel y TXT sí lo conservan.
- **Nota sobre Excel**: `autoSizeColumn` mide el texto con las fuentes del sistema (AWT); en Windows funciona, en un servidor sin fuentes los anchos podrían calcularse raros.
- **Pruebas**: se corren con `./gradlew test` (179 pruebas al cerrar esta etapa; 503 tras la etapa 4), sobre H2 en memoria. PostgreSQL sigue sin probarse (antes de la etapa 4).

## Etapa 3: API REST de lectura

Solo `GET` en esta etapa (la escritura llegó en la etapa 4, ver más abajo); no hay login (y en esta etapa tampoco paginación: el listado se pagina desde la etapa 6, ver «Etapa 6»). Los errores salen como `application/problem+json` (`status`, `title`, `detail`, `timestamp`). Detalle de decisiones y verificaciones: [docs/etapa-3.md](docs/etapa-3.md).

| Endpoint | Qué hace |
|----------|----------|
| `GET /api/facturas` | Listado (10 campos del reporte, de la más nueva a la más vieja). Filtros opcionales: `fechaDesde`, `fechaHasta` (`yyyy-MM-dd`, inclusivas), `estado` (`BORRADOR`, `EMITIDA`, `ANULADA`; sin distinguir mayúsculas ni espacios; vacío = sin filtro), `montoMinimo` (inclusivo, puede ser negativo; `NaN` e infinitos dan 400). Sin resultados: `200` con arreglo vacío en la etapa 3; **desde la etapa 6 devuelve un sobre paginado** `{items, pagina, tamanio, totalElementos, totalPaginas}` (con `items: []` si no hay resultados) y admite más filtros: ver «Etapa 6». |
| `GET /api/facturas/{id}` | Cabecera y renglones de una factura. Id inexistente: 404. |
| `GET /api/facturas/reporte?formato=` | Descarga con los mismos filtros que el listado. `pdf` (por defecto), `xlsx` (alias `excel`), `txt` (alias `tsv`) → `facturas.pdf`, `facturas.xlsx`, `facturas.txt`. Otro formato: 400. PDF imposible por un carácter sin glifo: 400. |
| `GET /api/catalogos/{puntos-venta, clientes, condiciones-iva, tipos-moneda, lista-precio-articulos}` | Opciones `{id, etiqueta, valor, codigoMoneda}` ordenadas por id. |

Cómo probarla: arrancar con `./gradlew bootRun` (JDK 17) y ejecutar los ejemplos de [requests.http](requests.http) con la extensión REST Client de VS Code o con el cliente HTTP de IntelliJ; también sirve `curl -i "http://localhost:8080/api/facturas?estado=%20emitida%20"`.

CORS: se admiten `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173` y `http://127.0.0.1:3000` (en la etapa 3 solo `GET`; desde la etapa 4 también `POST`, `PUT`, `PATCH` y `DELETE`, ver abajo). Para cambiarlos, la propiedad `facturacion.cors.allowed-origins` en `application.yml` o, por ejemplo, `--facturacion.cors.allowed-origins=http://localhost:4200` al arrancar. CORS lo aplica el navegador: **no autentica** (`curl` lo ignora).

## Etapa 4: escritura, estados y concurrencia

Alta, edición, cambio de estado y eliminación de facturas, con cálculo de importes en el servidor, numeración y CAE simulados, auditoría por petición y bloqueos pesimistas. No hay login, cobros, paginación, CRUD de catálogos ni conversión de monedas. Los errores siguen saliendo como `application/problem+json`. Decisiones, mutaciones y evidencia por motor: [docs/etapa-4.md](docs/etapa-4.md).

| Endpoint | Qué hace |
|----------|----------|
| `POST /api/facturas` | Alta de un **borrador**. Cuerpo: `clienteId` (opcional; sin él es «Consumidor Final»), `condicionIvaId`, `puntoVentaId`, `tipoMonedaId`, `fechaEmision` (opcional, por defecto hoy), `observaciones` (≤ 500) y `detalles` (al menos uno: `listaPrecioArticuloId`, `cantidad` > 0, `porcentajeBonificacion` 0 a 100 opcional, `descripcion` ≤ 255 opcional). El precio **no se envía**: sale de la lista de precios. `201` con `Location` y la fila del reporte. |
| `PUT /api/facturas/{id}` | Reemplazo completo de un **borrador** (cabecera y renglones; mismo cuerpo que el alta). `200`. Solo `BORRADOR`: otro estado da `409`. |
| `PATCH /api/facturas/{id}/estado` | Cuerpo `{"estado": "EMITIDA"\|"ANULADA", "observaciones": "..."}` (la observación, si no está en blanco, reemplaza la de la factura). Solo `BORRADOR → EMITIDA` (número por punto de venta, CAE de 14 dígitos, vencimiento hoy + 10, resultado `A`) y `EMITIDA → ANULADA` (fecha de anulación y baja lógica). Otra transición: `409`; estado desconocido: `400` (IN-01, pendiente de confirmar con el docente). |
| `DELETE /api/facturas/{id}` | Eliminación física de un **borrador**: `204` sin cuerpo. Una emitida no se borra, se anula (`409`). |

Códigos: `400` (validación por campo con `errores`, JSON ilegible, estado desconocido), `404` (factura o referencia inexistente, con el id), `409` (operación incompatible con el estado, factura sin renglones al emitir, o importe que no entra en un `double`). Los importes se calculan con `BigDecimal` y `HALF_UP` a 2 decimales (IVA sobre el neto sin redondear) y se comprueba la ida y vuelta antes de guardar en los campos `double`.

**`X-Usuario`**: encabezado opcional que **declara** quién opera (`sistema`, `acortez`, `mriquero`); queda en la auditoría de la factura (usuario de carga, modificación y baja). Si falta, está vacío o no existe, se usa `sistema`. **No autentica**: cualquiera puede declarar cualquier usuario, igual que CORS no identifica a nadie.

CORS (actualiza lo de la etapa 3): se admiten `GET`, `POST`, `PUT`, `PATCH` y `DELETE` desde los mismos cuatro orígenes exactos; el cliente puede enviar `Content-Type` y `X-Usuario`, y se exponen `Location` y `Content-Disposition`.

Cómo probarla: arrancar con `./gradlew bootRun` (JDK 17; el `DataLoader` crea 6 facturas con las reglas reales: 3 emitidas, 1 anulada y 2 borradores) y ejecutar la sección «Etapa 4» de [requests.http](requests.http), o con `curl`, por ejemplo:

```bash
curl -i -X POST http://localhost:8080/api/facturas -H "Content-Type: application/json" -H "X-Usuario: acortez" \
  -d '{"clienteId":1,"condicionIvaId":2,"puntoVentaId":1,"tipoMonedaId":1,"detalles":[{"listaPrecioArticuloId":1,"cantidad":1}]}'
curl -i -X PATCH http://localhost:8080/api/facturas/7/estado -H "Content-Type: application/json" -d '{"estado":"EMITIDA"}'
```

### Pruebas de concurrencia en H2 y en PostgreSQL

La suite de concurrencia (clases con la etiqueta `concurrencia`: emisiones simultáneas con hilos reales, edición o baja que espera a una emisión, unicidad de números) corre **siempre sobre H2** dentro de `./gradlew test` y **no necesita ningún servidor**. Un resultado en H2 no prueba el comportamiento de PostgreSQL, por eso existe una corrida aparte a demanda contra el **PostgreSQL 16.9 portátil** (fuera del repositorio, puerto 5433, base y usuario `facturacion`; solo escucha en `127.0.0.1`):

1. Iniciar: `C:\Users\zbook\DesarrolloSoftware\pg16-portatil\iniciar-pg16.bat` y comprobar `pg_isready.exe -h 127.0.0.1 -p 5433` (desde Git Bash conviene redirigir la salida: `cmd //c "C:\\...\\iniciar-pg16.bat" > /tmp/pgstart.log 2>&1 < /dev/null`).
2. Correr (JDK 17): `./gradlew testPostgres` (opcional `-PpostgresUrl=jdbc:postgresql://localhost:5433/facturacion`). La salida imprime `[MOTOR] producto=PostgreSQL version=16.9`; si la conexión fuera a otro motor, la prueba falla. Reportes separados: `build/reports/tests/test` (H2) y `build/reports/tests/testPostgres` (PostgreSQL).
3. Detener: `detener-pg16.bat` y comprobar que 5433 quedó libre (`netstat -ano | findstr :5433`). No tocar el PostgreSQL 18 del puerto 5432.

Las pruebas crean y borran solo sus datos (puntos de venta 9001 y 9002, catálogos con prefijo `CONC`).

## Perfil PostgreSQL

1. Levantar el contenedor (PostgreSQL 16, base/usuario/contrasena `facturacion`, puerto 5432, volumen `pgdata`):

   ```bash
   docker compose up -d db
   ```

2. Arrancar el backend con el perfil `postgres`:

   PowerShell:

   ```powershell
   .\gradlew.bat bootRun --args="--spring.profiles.active=postgres"
   ```

   Bash:

   ```bash
   ./gradlew bootRun --args="--spring.profiles.active=postgres"
   ```

   Con `java -jar`: `java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/facturacion-arca-1.0.0.jar --spring.profiles.active=postgres`.

3. Detener: `docker compose down` (conserva los datos). `docker compose down -v` borra el volumen y reinicia el esquema (necesario porque `ddl-auto: update` no elimina columnas viejas).

- Con este perfil la consola H2 queda deshabilitada.
- Si el puerto 5432 esta ocupado, cambiar la publicacion en `docker-compose.yml` (por ejemplo `5433:5432`) **y** la URL del datasource en `application.yml`.
- Las variables de entorno `SPRING_DATASOURCE_*` y `SPRING_PROFILES_ACTIVE` pisan la configuracion de `application.yml`.

## Frontend

```bash
cd frontend
npm ci            # instala dependencias desde package-lock.json
npm run dev       # compila y sirve en http://localhost:5173 (sin cache)
npm run build     # compila TypeScript (modo estricto, ES2020) a frontend/dist
npm run watch     # recompila al guardar (en otra terminal, junto a npm run dev)
npm test          # compila y corre las pruebas con node:test + jsdom
```

Los comandos son los mismos en PowerShell y Bash. La URL base de la API se lee del `<meta name="api-base-url">` de `frontend/index.html` (por defecto `http://localhost:8080/api`); si cambia el puerto del backend (`server.port`), cambiar tambien ese meta (RN-CF-04).

## Etapa 5: frontend en TypeScript

Cliente web sin framework ni bundler (HTML, CSS y TypeScript estricto con modulos ES2020): consulta con filtros, alta y edicion de borradores, emision, anulacion, eliminacion y descargas PDF/Excel. El informe esta en [docs/etapa-5.md](docs/etapa-5.md) (decisiones, control asincrono, accesibilidad, recorrido con el backend real, limites de la simulacion y checklist de teclado y lector de pantalla para revisar a mano). Las muestras de descarga y las capturas estan en `docs/evidencia/etapa-5/`.

**Requisitos**: Node 18.13 o superior (verificado con v24.22.1) y, para el backend, el JDK 17 (ver «Fijar JAVA_HOME»). No hay dependencias nuevas: `npm ci` instala las del `package-lock.json`.

**Compilar y probar** (desde `frontend/`):

```bash
cd frontend
npm ci            # una sola vez
npm run build     # tsc estricto -> frontend/dist
npm test          # compila y corre node --test (sin argumentos: en Node 24 no usar `node --test test/`) con jsdom
```

**Correr backend y frontend juntos** (dos terminales; el frontend lee la URL de la API del meta `api-base-url`):

```bash
# Terminal 1 (raiz del repo, con JDK 17): backend en http://localhost:8080 con H2 y 6 facturas de ejemplo
export JAVA_HOME=/c/Users/zbook/.jdks/ms-17.0.20.1     # PowerShell: $env:JAVA_HOME = 'C:\Users\zbook\.jdks\ms-17.0.20.1'
./gradlew bootRun

# Terminal 2: frontend en http://localhost:5173
cd frontend
npm run dev
```

Abrir `http://localhost:5173/`. Si cambia el puerto del backend, cambiar tambien el `<meta name="api-base-url">` de `frontend/index.html`. Los origenes admitidos por CORS son `http://localhost:5173` y `http://127.0.0.1:5173`; el frontend envia `X-Usuario: sistema` (declara quien opera, no autentica).

**Detener y liberar puertos**: `Ctrl+C` en cada terminal. Para comprobar que quedaron libres: `netstat -ano | grep -E ":(8080|5173) .*LISTEN"` (Bash) o `netstat -ano | findstr "8080 5173"` (PowerShell) no debe mostrar nada en estado `LISTENING`. H2 en memoria se borra al reiniciar el backend.

## Etapa 6: requisitos verbales del docente

Cambios pedidos en clase el 2026-11-18 (ARCA sigue **simulada**: ni login, ni cobros, ni integración fiscal real). Informe: [docs/etapa-6.md](docs/etapa-6.md); evidencia (capturas y descargas de muestra) en `docs/evidencia/etapa-6/`; ejemplos ejecutables en [requests.http](requests.http).

**Listado paginado (CAMBIO de contrato)**: `GET /api/facturas` ya no devuelve un arreglo sino `{items, pagina, tamanio, totalElementos, totalPaginas}`; las filas de `items` conservan los diez campos. El tag `etapa-3` conserva el contrato anterior.

| Parámetro (`GET /api/facturas`) | Regla |
|---------------------------------|-------|
| `pagina` | base 0, por defecto 0; negativa: 400. Una página posterior a la última devuelve `items: []` con el total real. |
| `tamanio` | por defecto 10, entre 1 y 100; fuera de rango o no numérico: 400. |
| `numero` | número de comprobante exacto (≥ 1), de cualquier punto de venta; `0` o negativo: 400. |
| `clienteId` | id de cliente; uno inexistente da resultado vacío, no error. |
| `informada` | `true` = tiene CAE (EMITIDA y ANULADA), `false` = sin CAE; se normaliza (` FALSE ` vale); otro valor: 400. |
| `fechaDesde`, `fechaHasta`, `estado`, `montoMinimo` | como en la etapa 3. |

- `GET /api/facturas/reporte` acepta los mismos filtros nuevos e **ignora** `pagina` y `tamanio` (descarga todo el resultado filtrado).
- **Búsqueda en catálogos**: `GET /api/catalogos/clientes?q=` (denominación o CUIT/CUIL) y `GET /api/catalogos/lista-precio-articulos?q=` (código o denominación del artículo, o código de lista); sin distinguir mayúsculas, `%` y `_` como texto literal, máximo 100 caracteres (si no, 400). Sin `q` devuelven el catálogo completo.
- **PDF por factura**: `GET /api/facturas/{id}/pdf` devuelve el comprobante (A4 vertical, `factura-PPPP-NNNNNNNN.pdf`) de una factura EMITIDA o ANULADA; un BORRADOR da 409 («no fue informada a ARCA»), un id inexistente 404 y un carácter sin glifo 400. Es un comprobante simulado, sin validez fiscal.
- `GET /api/facturas/{id}` agrega `clienteCuitCuil`. Las facturas informadas siguen sin poder editarse ni eliminarse (409).
- **Frontend**: filtros nuevos (número, «Informada a ARCA», cliente), paginación («Anterior»/«Siguiente», «Página X de Y», tamaño 10/20/50), **explorador modal** (`<dialog>` con `showModal()`) para elegir cliente y artículo en el formulario y el cliente del filtro, y el enlace «Imprimir PDF» en las filas EMITIDA y ANULADA.

**Correr todo** (JDK 17): `./gradlew clean test bootJar` en la raíz y `java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/facturacion-arca-1.0.0.jar` (o `./gradlew bootRun`) en una terminal; `cd frontend && npm ci && npm test && npm run build && npm run dev` en otra, y abrir `http://localhost:5173/`. Como en la etapa 5, al terminar detener ambos y comprobar que 8080 y 5173 quedaron libres.

**Límites conocidos**: catálogos sin tope de resultados; el catálogo de artículos trae `codigoMoneda` nulo (el explorador muestra el precio sin moneda); «informada» incluye ANULADA (supuesto a confirmar con el docente); el checklist de teclado, lector de pantalla, zoom y descarga real lo completa el estudiante en `docs/etapa-6.md`.

## Verificaciones y motor

Fecha: 2026-11-11. JDK 17.0.20.1 (Microsoft), Gradle 8.10.2, Node v24.22.1, npm 11.19.1, Docker 29.2.1 / Compose v5.1.0.

| Verificacion | Motor | Resultado |
|--------------|-------|-----------|
| `gradlew clean test bootJar` (105 pruebas de backend: 19 de la etapa 0 + 86 de la etapa 1) | H2 en memoria | OK |
| `java -jar` arranca en 8080 | H2 en memoria | OK |
| Consola H2: conexion, `SHOW TABLES;`, tabla creada y ausente tras reiniciar `bootRun` | H2 en memoria | OK |
| Perfil `postgres`: propiedades del datasource (prueba automatica, sin conectar) | ninguno (solo configuracion) | OK |
| `docker compose config` | n/a | valido |
| Arranque real contra PostgreSQL 16 y persistencia tras reinicio | PostgreSQL 16.9 portátil, puerto 5433 | **Verificado el 2026-11-17** con el perfil `postgres` y la API de las etapas 0 a 3: Hibernate crea el esquema y las unicidades, el `DataLoader` carga 3 usuarios y 6 facturas, el listado, el detalle y el PDF responden, y al reiniciar la app el `DataLoader` omite la carga y los datos persisten. **No se usó Docker**: Docker Desktop no arranca en este equipo (servicio WSL deshabilitado) y el puerto 5432 está ocupado por un PostgreSQL 18 local, así que se usó el PostgreSQL 16.9 de los binarios oficiales de EnterpriseDB (zip, SHA-256 `304eb7da...db88`), descomprimido fuera del repositorio y ejecutado como proceso propio. El `docker-compose.yml` sigue **sin verificar** en este equipo. Las pruebas de concurrencia con bloqueos pesimistas se ejecutaron sobre este mismo motor en la etapa 4 (fila siguiente) |
| Frontend: `npm ci && npm test && npm run build`, `npm run dev` en 5173 sin errores en consola | n/a | OK (7 pruebas) |
| Etapa 4 (2026-11-18): `gradlew clean test bootJar` con PostgreSQL apagado (503 pruebas de backend: 320 previas + 183 de la etapa 4) | H2 en memoria | OK (0 fallas); `build/libs/` solo tiene `facturacion-arca-1.0.0.jar`
| Etapa 4: `gradlew testPostgres` (suite de concurrencia: emisiones simultaneas, edicion y baja contra emision, unicidad) | PostgreSQL 16.9 portatil, puerto 5433 | **Verificado el 2026-11-18**: 9 pruebas en verde, motor informado `PostgreSQL 16.9`; el bloqueo se genera como `for no key update` (en H2 es `for update`). El servidor se detuvo al terminar (5433 libre). Un resultado en H2 no prueba PostgreSQL: ver [docs/etapa-4.md](docs/etapa-4.md)
| Etapa 4: prueba manual del circuito (alta, edicion, emision, anulacion, baja, errores 400/404/409, CORS) con `java -jar` | H2 en memoria | OK; app detenida y 8080 libre |
| Etapa 5 (2026-11-18, Node v24.22.1, TypeScript 5.6.3): `cd frontend && npm test && npm run build` | n/a (jsdom, sin red) | OK: 151 pruebas, 0 fallas (baseline previo: 7); `tsc` estricto sin errores; sin `innerHTML` ni `new Date` en `frontend/src` |
| Etapa 5: recorrido manual en el navegador integrado de la herramienta (Chromium) contra `java -jar` (codigo de la etapa 4) y `npm run dev` | H2 en memoria | OK: alta, edicion, emision (numero 4, CAE ficticio), anulacion con motivo, baja de un borrador, cancelaciones sin peticion, 400 y 409 visibles, descargas PDF/Excel/TXT con el filtro EMITIDA. `confirm`/`prompt` se simularon con funciones que devuelven la respuesta (los dialogos nativos bloquean la automatizacion); **sin lector de pantalla, sin zoom real y sin recorrido completo solo con teclado** (a cargo del estudiante, ver checklist en docs/etapa-5.md) |
| Etapa 5: `gradlew clean test bootJar` con JDK 17 al cerrar | H2 en memoria | OK: 503 pruebas de backend, 0 fallas; el backend no se modifico en esta etapa. Ambos servidores detenidos y 8080 y 5173 libres |
| Etapa 6 (2026-11-18): `gradlew clean test bootJar` con JDK 17 | H2 en memoria | OK: **620 pruebas de backend** (503 de base), 0 fallas; `build/libs/facturacion-arca-1.0.0.jar` construido |
| Etapa 6: `cd frontend && npm test && npm run build` | n/a (jsdom, sin red) | OK: **244 pruebas** (151 de base), 0 fallas; `tsc` estricto sin errores |
| Etapa 6: `requests.http` (paginación, filtros nuevos, `?q=`, PDF por factura, reporte) contra `java -jar` con JDK 17 | H2 en memoria | OK, pedido por pedido con `curl` |
| Etapa 6: recorrido manual en el navegador integrado (Chromium) con backend y frontend reales | H2 en memoria | OK: explorador con `showModal()` real, filtros, paginación, alta, edición, emisión, anulación y eliminación. **Simulados**: `confirm` y `prompt`; algunas cargas se dispararon por `requestSubmit()`/eventos. **No observado**: la descarga real del archivo en el navegador (solo el `href` y `curl`). **Solo jsdom**: doble clic, respuestas obsoletas y errores por campo. **No verificado**: lector de pantalla, zoom real, reflujo y contraste medido en pantalla |
| Etapa 6: `openspec validate etapa-6-requisitos-docente --strict` | n/a | válido |

Versiones de herramientas no fijadas por la consigna: `io.spring.dependency-management` 1.1.6, `http-server` ^14.1.1, `jsdom` ^25.0.0; `package-lock.json` congela las exactas.

## Origen del codigo

- Se reutilizo como semilla (DD-13) la lista de capacidades del `pom.xml` y de `package.json`/`tsconfig.json` de mi proyecto previo `api-facturas`, **adaptada**: Maven pasa a Gradle (Groovy), Spring Boot 4.1.1 pasa a 3.3.5, OpenPDF 2.0.3, se quita MySQL, se agregan `validation` y Apache POI 5.3.0; en el frontend `typescript` pasa a `~5.6.3`, se agregan `http-server` (puerto 5173), `jsdom` y el script `test`, y `moduleResolution` pasa a `node10`. No se reutilizo su `application.properties` (usaba PostgreSQL por defecto).
- No se copio codigo de la solucion del docente.
- Se uso IA (Claude) como asistente para generar y revisar el esqueleto bajo desarrollo guiado por pruebas; el codigo fue revisado por el autor.

## Problemas frecuentes

- **Gradle falla con un error de version de Java / `Unsupported class file major version`**: el JDK por defecto es el 26. Fijar `JAVA_HOME` al JDK 17 (ver arriba) y verificar con `java -version`.
- **Puerto 8080, 5173 o 5432 ocupado**: cerrar el proceso que lo usa o cambiar el puerto (backend: `server.port` y el meta `api-base-url`; PostgreSQL: publicacion en `docker-compose.yml` y URL del datasource).
- **Las pruebas de base de datos fallan con otra URL**: hay variables `SPRING_DATASOURCE_*` o `SPRING_PROFILES_ACTIVE` definidas en el entorno; quitarlas.
- **`npm test` dice que no encuentra `dist/config.js`**: ejecutar desde `frontend/` (el script compila antes de probar).
- **Error de zona horaria al conectar a PostgreSQL con `java -jar`**: falta `-Duser.timezone=America/Argentina/Buenos_Aires`.
