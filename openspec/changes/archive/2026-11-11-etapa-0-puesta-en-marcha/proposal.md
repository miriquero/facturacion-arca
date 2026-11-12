# Proposal

## Why

El repositorio `facturacion-arca` hoy solo contiene la consigna, la base de conocimiento y el roadmap: no hay backend ni frontend que se puedan ejecutar. La Etapa 0 de la consigna (semana 1, sin nota) exige dejar ambos lados arrancando con las versiones fijadas (Java 17, Spring Boot 3.3.5, Gradle 8.10.2, TypeScript 5.6) y el repositorio listo para las entregas con tag; todas las etapas siguientes (C-02..C-06) se construyen sobre esta base, por lo que cualquier desvío de configuración (versión, base de datos por defecto, `open-in-view`) se arrastraría a todo el trabajo evaluado.

## What Changes

- Proyecto Gradle `facturacion-arca` (grupo `ar.edu.utn.frm`, versión `1.0.0`, paquete base `ar.edu.utn.frm.facturacion`) con wrapper 8.10.2 versionado, toolchain Java 17 y Spring Boot 3.3.5.
- Dependencias: starters Web, Data JPA, Validation y Test; Lombok (`compileOnly` + `annotationProcessor`); drivers H2 y PostgreSQL (`runtimeOnly`); OpenPDF 2.0.3 y Apache POI 5.3.0 declarados (sin uso todavía).
- Clase `FacturacionApplication` y los ocho paquetes `model, dto, repository, service, controller, exception, config, report`, cada uno con un `package-info.java` que documenta su responsabilidad (los directorios vacíos no se versionan).
- `application.yml` con H2 en memoria por defecto (`jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1`, `sa`, contraseña vacía), consola `/h2-console`, `ddl-auto: update`, SQL visible y formateado, `open-in-view: false`; segundo documento YAML para el perfil `postgres`.
- `docker-compose.yml` con un único servicio `db` PostgreSQL 16 (db/usuario/contraseña `facturacion`, puerto 5432, volumen nombrado).
- Zona horaria `America/Argentina/Buenos_Aires` fijada para las JVM que lanza Gradle (`bootRun` y `test`).
- Esqueleto `frontend/`: `package.json` (scripts `build`, `watch`, `dev` en 5173, `test`), `package-lock.json`, `tsconfig.json` estricto ES2020, `index.html` con `<meta name="api-base-url" content="http://localhost:8080/api">`, un módulo que lee esa configuración y pruebas de humo con `node:test` + jsdom.
- `.gitignore`, `.gitattributes` y `README.md` con comandos reproducibles (arranque, pruebas, compilación, perfil PostgreSQL, motor probado y semilla reutilizada).
- Pruebas automatizadas que fallan antes de cada pieza de configuración (contexto, datasource H2, `open-in-view`, consola H2, propiedades Hibernate, perfil `postgres`, zona horaria, paquetes, frontend).

## Capabilities

### New Capabilities
- `backend-build`: construcción reproducible del backend — coordenadas del proyecto, versiones fijadas, wrapper, JDK 17, dependencias declaradas, estructura de paquetes y JAR `build/libs/facturacion-arca-1.0.0.jar`.
- `configuracion-base-de-datos`: selección y configuración del motor de persistencia — H2 en memoria por defecto con consola, comportamiento de Hibernate, `open-in-view` desactivado, perfil `postgres` con su contenedor y zona horaria de la JVM.
- `frontend-esqueleto`: frontend TypeScript sin framework que compila, se sirve por HTTP en 5173, expone la URL base de la API mediante `<meta name="api-base-url">` y tiene un runner de pruebas con jsdom.
- `repositorio-reproducible`: qué se versiona y qué se excluye, y el README que permite reproducir arranque, pruebas y compilación declarando el motor probado.

### Modified Capabilities
<!-- Ninguna: no existen specs previas en openspec/specs/. -->

## Impact

- **Código nuevo**: `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `src/main/java/ar/edu/utn/frm/facturacion/**`, `src/main/resources/application.yml`, `src/test/java/ar/edu/utn/frm/facturacion/**`, `docker-compose.yml`, `frontend/**`, `.gitignore`, `.gitattributes`, `README.md`.
- **Dependencias externas**: Maven Central y npm registry (descarga inicial); distribución Gradle 8.10.2 desde `services.gradle.org` la primera vez que se ejecuta el wrapper.
- **Entorno**: requiere JDK 17 (`C:\Users\zbook\.jdks\ms-17.0.20.1`) como `JAVA_HOME` para ejecutar Gradle (el JDK 26 por defecto no es compatible con Gradle 8.10.2); Node >= 18.13; Docker solo para el perfil `postgres`.
- **Governance**: ALTO (datasource, `ddl-auto`, perfiles y docker-compose afectan la persistencia). Esta propuesta no escribe código; el apply espera revisión del estudiante.
- **Fuera de alcance**: entidades, repositorios, `DataLoader`, CORS, `application-test.yml`, `requests.http`, informe `docs/etapa-0.md` (la etapa 0 no tiene tag ni informe, IN-05) y cualquier comando Git que cambie estado.
