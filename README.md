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
| `gradlew clean test bootJar` (19 pruebas de backend) | H2 en memoria | OK |
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
