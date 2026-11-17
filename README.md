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
- **Pruebas**: se corren con `./gradlew test` (179 pruebas en total tras esta etapa), sobre H2 en memoria. PostgreSQL sigue sin probarse (antes de la etapa 4).

## Etapa 3: API REST de lectura

Solo `GET`; no hay escritura (etapa 4), login ni paginación. Los errores salen como `application/problem+json` (`status`, `title`, `detail`, `timestamp`). Detalle de decisiones y verificaciones: [docs/etapa-3.md](docs/etapa-3.md).

| Endpoint | Qué hace |
|----------|----------|
| `GET /api/facturas` | Listado (10 campos del reporte, de la más nueva a la más vieja). Filtros opcionales: `fechaDesde`, `fechaHasta` (`yyyy-MM-dd`, inclusivas), `estado` (`BORRADOR`, `EMITIDA`, `ANULADA`; sin distinguir mayúsculas ni espacios; vacío = sin filtro), `montoMinimo` (inclusivo, puede ser negativo; `NaN` e infinitos dan 400). Sin resultados: `200 []`. |
| `GET /api/facturas/{id}` | Cabecera y renglones de una factura. Id inexistente: 404. |
| `GET /api/facturas/reporte?formato=` | Descarga con los mismos filtros que el listado. `pdf` (por defecto), `xlsx` (alias `excel`), `txt` (alias `tsv`) → `facturas.pdf`, `facturas.xlsx`, `facturas.txt`. Otro formato: 400. PDF imposible por un carácter sin glifo: 400. |
| `GET /api/catalogos/{puntos-venta, clientes, condiciones-iva, tipos-moneda, lista-precio-articulos}` | Opciones `{id, etiqueta, valor, codigoMoneda}` ordenadas por id. |

Cómo probarla: arrancar con `./gradlew bootRun` (JDK 17) y ejecutar los ejemplos de [requests.http](requests.http) con la extensión REST Client de VS Code o con el cliente HTTP de IntelliJ; también sirve `curl -i "http://localhost:8080/api/facturas?estado=%20emitida%20"`.

CORS: se admiten `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173` y `http://127.0.0.1:3000` (solo `GET`, y se expone `Content-Disposition`). Para cambiarlos, la propiedad `facturacion.cors.allowed-origins` en `application.yml` o, por ejemplo, `--facturacion.cors.allowed-origins=http://localhost:4200` al arrancar. CORS lo aplica el navegador: **no autentica** (`curl` lo ignora).

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

## Verificaciones y motor

Fecha: 2026-11-11. JDK 17.0.20.1 (Microsoft), Gradle 8.10.2, Node v24.22.1, npm 11.19.1, Docker 29.2.1 / Compose v5.1.0.

| Verificacion | Motor | Resultado |
|--------------|-------|-----------|
| `gradlew clean test bootJar` (105 pruebas de backend: 19 de la etapa 0 + 86 de la etapa 1) | H2 en memoria | OK |
| `java -jar` arranca en 8080 | H2 en memoria | OK |
| Consola H2: conexion, `SHOW TABLES;`, tabla creada y ausente tras reiniciar `bootRun` | H2 en memoria | OK |
| Perfil `postgres`: propiedades del datasource (prueba automatica, sin conectar) | ninguno (solo configuracion) | OK |
| `docker compose config` | n/a | valido |
| Arranque real contra PostgreSQL 16 y persistencia tras reinicio | PostgreSQL | **NO VERIFICADO**: Docker Desktop no arranca en este equipo (servicio WSL deshabilitado) y el puerto 5432 está ocupado por un PostgreSQL 18 local. Se verificará con PostgreSQL 16 en el puerto 5433 antes de la etapa 4 |
| Frontend: `npm ci && npm test && npm run build`, `npm run dev` en 5173 sin errores en consola | n/a | OK (7 pruebas) |

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
