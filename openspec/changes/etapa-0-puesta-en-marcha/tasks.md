# Tasks

> Reglas para todo el apply: toda ejecución de Gradle con `JAVA_HOME=C:\Users\zbook\.jdks\ms-17.0.20.1` (nunca el JDK 26); TDD estricto: cada tarea **GREEN** va precedida por su tarea **RED** y la RED se cierra solo después de ver la falla por la razón esperada (no por un error de compilación ajeno); sin comandos Git que cambien estado. Gobernanza ALTA: antes de empezar, el estudiante revisó y aprobó `design.md`.

## 1. Andamiaje del build (sin código de producción)

- [x] 1.1 Crear `.gitignore` (`build/`, `.gradle/`, `frontend/node_modules/`, `frontend/dist/`, `.idea/`, `*.iml`, `.vscode/`, `out/`, `bin/`, `tmp/`, `*.log`, `.DS_Store`, `Thumbs.db`) y `.gitattributes` (`gradlew` eol=lf, `*.bat` eol=crlf, `*.jar` binary); verificar con `git check-ignore -v build/x node_modules/x frontend/dist/x` (lectura, no cambia estado)
- [x] 1.2 Crear `settings.gradle` con `rootProject.name = 'facturacion-arca'` y verificar que el archivo existe antes de invocar Gradle 9 (D4 paso 1)
- [x] 1.3 Generar el wrapper con la distribución 9.0.0 cacheada (`gradle wrapper --gradle-version 8.10.2 --distribution-type bin`), regenerarlo con la propia 8.10.2 agregando `--gradle-distribution-sha256-sum` oficial (D4); verificar `./gradlew --version` → «Gradle 8.10.2» y JVM 17, y `git check-ignore gradle/wrapper/gradle-wrapper.jar` sin salida
- [x] 1.4 Escribir `build.gradle` (Groovy): plugins `java`, `org.springframework.boot` 3.3.5, `io.spring.dependency-management` 1.1.6; `group`/`version` `ar.edu.utn.frm`/`1.0.0`; toolchain 17; dependencias de D8 (web, data-jpa, validation, lombok `compileOnly`+`annotationProcessor`, h2 y postgresql `runtimeOnly`, `openpdf` 2.0.3, `poi-ooxml` 5.3.0, `starter-test`, `testRuntimeOnly junit-platform-launcher`); `useJUnitPlatform()` vía `tasks.named('test')`; `jar` deshabilitado. Verificar con `./gradlew dependencies --configuration compileClasspath` (Boot 3.3.5, OpenPDF 2.0.3, POI 5.3.0, sin drivers) y `--configuration runtimeClasspath` (con h2 y postgresql)

## 2. Arranque del contexto y estructura de paquetes

- [x] 2.1 RED: `src/test/java/ar/edu/utn/frm/facturacion/FacturacionApplicationTests.java` con `@SpringBootTest` y `contextLoads`; `./gradlew test` falla porque no hay configuración `@SpringBootConfiguration`
- [x] 2.2 GREEN: `FacturacionApplication` (`@SpringBootApplication` + `main`) en `ar.edu.utn.frm.facturacion`; `./gradlew test` en verde
- [x] 2.3 RED: `EstructuraPaquetesTest` parametrizado (`@ValueSource` con los 8 paquetes) que exige el recurso `ar/edu/utn/frm/facturacion/<paquete>/package-info.class`; falla 8/8
- [x] 2.4 GREEN: `package-info.java` con Javadoc de responsabilidad en `model, dto, repository, service, controller, exception, config, report`; prueba 8/8 en verde
- [x] 2.5 Verificar bytecode Java 17: `javap -v` sobre `build/classes/java/main/ar/edu/utn/frm/facturacion/FacturacionApplication.class` muestra `major version: 61`

## 3. Base de datos por defecto: H2 en memoria (RN-CF-02)

- [x] 3.1 RED: `ConfiguracionH2Test` (`@SpringBootTest`) que obtiene una conexión del `DataSource` y exige URL que empieza con `jdbc:h2:mem:facturacion` y usuario `SA`; falla porque Boot genera `jdbc:h2:mem:<uuid>`
- [x] 3.2 GREEN: crear `src/main/resources/application.yml` con `spring.datasource` (URL `jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1`, `org.h2.Driver`, `sa`, contraseña vacía); 3.1 en verde
- [x] 3.3 RED: en `ConfiguracionH2Test`, prueba sobre `EntityManagerFactory.getProperties()` que exige `hibernate.hbm2ddl.auto=update`, `hibernate.show_sql=true`, `hibernate.format_sql=true`; falla (default `create-drop`)
- [x] 3.4 GREEN: agregar `spring.jpa.hibernate.ddl-auto: update`, `spring.jpa.show-sql: true`, `spring.jpa.properties.hibernate.format_sql: true`; en verde
- [x] 3.5 RED: prueba que exige que el contexto no tenga beans de tipo `OpenEntityManagerInViewInterceptor`; falla con la configuración por defecto de Boot
- [x] 3.6 GREEN: agregar `spring.jpa.open-in-view: false`; en verde y sin el warning de open-in-view en el log de pruebas
- [x] 3.7 RED: `ConsolaH2Test` con `webEnvironment = RANDOM_PORT` y `TestRestTemplate`: `GET /h2-console/` debe devolver 200 con contenido de la página de login de H2; falla con 404
- [x] 3.8 GREEN: agregar `spring.h2.console.enabled: true` y `path: /h2-console`; en verde
- [x] 3.9 README (primera versión): requisitos (JDK 17, Node >= 18.13, Docker opcional), cómo fijar `JAVA_HOME` en PowerShell y Bash, `gradlew.bat bootRun` / `./gradlew bootRun`, datos de conexión de la consola H2, aviso de que H2 en memoria no conserva datos entre reinicios; verificar que cada comando documentado se ejecutó tal cual

## 4. Perfil postgres, contenedor y zona horaria (RN-CF-02, RN-CF-03, DD-12)

- [x] 4.1 RED: `PerfilPostgresTest` con `@SpringBootTest(classes = PerfilPostgresTest.SinAutoconfiguracion.class)` (configuración vacía anidada) y `@ActiveProfiles("postgres")`; exige del `Environment` URL `jdbc:postgresql://localhost:5432/facturacion`, usuario/contraseña `facturacion`, driver `org.postgresql.Driver`, `spring.h2.console.enabled=false` y `ddl-auto` heredado `update`; falla porque el perfil no existe
- [x] 4.2 GREEN: agregar a `application.yml` el segundo documento YAML con `spring.config.activate.on-profile: postgres` (D3); 4.1 en verde
- [x] 4.3 TRIANGULATE: caso en `PerfilPostgresTest` (o clase hermana sin perfil activo) que exige que la URL por defecto siga siendo la de H2; ejecutar toda la suite: 3.x siguen en verde
- [x] 4.4 RED: `ZonaHorariaTest` que exige `TimeZone.getDefault().getID()` = `America/Argentina/Buenos_Aires`; falla con la zona del sistema
- [x] 4.5 GREEN: en `build.gradle`, `tasks.named('test') { systemProperty 'user.timezone', ... }` y `tasks.named('bootRun') { jvmArgs ... }` (D7); en verde
- [x] 4.6 Crear `docker-compose.yml` (servicio único `db`, `postgres:16`, `POSTGRES_DB/USER/PASSWORD=facturacion`, `5432:5432`, volumen `pgdata`, healthcheck `pg_isready`); verificar con `docker compose config` sin errores
- [ ] 4.7 Verificación manual PostgreSQL: `docker compose up -d db`, `gradlew.bat bootRun --args="--spring.profiles.active=postgres"`, comprobar arranque sin error de zona horaria, crear una tabla de prueba con `psql`/cliente, reiniciar el backend y confirmar que persiste; `docker compose down` al terminar. Registrar resultado y versión en el README **NO EJECUTADA: Docker Desktop no estaba en ejecucion (daemon inaccesible); pendiente para el estudiante. README lo declara.**
- [x] 4.8 README: sección «Perfil PostgreSQL» (levantar contenedor, activar perfil en PowerShell y Bash, `-Duser.timezone` para `java -jar`, `docker compose down -v` para reiniciar el esquema, cambiar 5432 junto con la URL si está ocupado, variables `SPRING_*` que pisan la configuración)

## 5. Esqueleto frontend (US-002)

- [x] 5.1 Crear `frontend/package.json` (D8: `"type": "module"`, `engines.node >= 18.13`, `devDependencies` `typescript ~5.6.3`, `http-server ^14.1.1`, `jsdom ^25`; scripts `build: tsc`, `watch: tsc --watch`, `dev: npm run build && http-server . -p 5173 -c-1`, `test: npm run build && node --test test/`) y `frontend/tsconfig.json` (D8); ejecutar `npm install` una vez para generar `package-lock.json` y verificar `npx tsc -v` → 5.6.x
- [x] 5.2 RED: `frontend/test/index.test.mjs` (jsdom sobre `index.html`: meta `api-base-url` = `http://localhost:8080/api` y `<script type="module" src="./dist/app.js">`) y `frontend/test/config.test.mjs` (importa `../dist/config.js`: devuelve `http://127.0.0.1:9090/api` desde un documento jsdom con ese meta; lanza error que menciona `api-base-url` si falta o está vacío); `npm test` falla porque no existen `index.html` ni `src/`
- [x] 5.3 GREEN: `frontend/src/config.ts` (`leerApiBaseUrl(doc: Document): string`), `frontend/src/app.ts` (importa `./config.js`, escribe la URL en `#estado-api` con `textContent`), `frontend/index.html` (meta, `#estado-api`, script módulo) y `frontend/styles.css` mínimo; `npm test` en verde
- [x] 5.4 REFACTOR: revisar nombres y mensajes; `npm test` y `npm run build` siguen en verde, sin errores de `strict`/`noUnusedLocals`
- [x] 5.5 Verificación manual: `npm run dev`, abrir `http://localhost:5173/`, comprobar que se muestra la URL de la API, que `dist/app.js` carga sin errores en la consola del navegador y que no hay 404 de módulos
- [x] 5.6 README: sección frontend (`cd frontend`, `npm ci`, `npm run dev|build|watch|test`, puerto 5173, cómo cambiar `api-base-url` si cambia el puerto del backend, RN-CF-04)

## 6. Verificación integral de la etapa

- [x] 6.1 Backend completo con JDK 17: `./gradlew clean test bootJar` (y `gradlew.bat clean test bootJar` en PowerShell) termina en `BUILD SUCCESSFUL` y `build/libs/` contiene solo `facturacion-arca-1.0.0.jar`; `java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/facturacion-arca-1.0.0.jar` arranca en 8080 (nota: `gradlew.bat` no se pudo ejecutar desde el agente; verificado con `./gradlew` en Git Bash)
- [x] 6.2 Verificación manual H2: con `bootRun`, abrir `http://localhost:8080/h2-console`, conectar (`org.h2.Driver`, `jdbc:h2:mem:facturacion`, `sa`, sin contraseña), ejecutar `SHOW TABLES;`; crear una tabla, reiniciar `bootRun` y confirmar que desapareció
- [x] 6.3 Frontend desde cero: borrar `frontend/node_modules` y `frontend/dist`, ejecutar `npm ci && npm test && npm run build` en `frontend/`; todo en verde
- [x] 6.4 Árbol limpio: `git status --porcelain` (lectura) no muestra `build/`, `.gradle/`, `node_modules/` ni `dist/`, y sí muestra los archivos fuente, wrapper y lockfile nuevos
- [x] 6.5 README final: tabla «Verificaciones y motor» (qué se probó con H2 y qué con PostgreSQL, con fecha y versiones de JDK/Node), sección «Origen del código» (semilla `api-facturas` reutilizada y cambios de D8, uso de IA) y «Problemas frecuentes» (JDK 26, puertos, `SPRING_*`); seguir el README de punta a punta en una terminal nueva sin pasos no documentados
- [x] 6.6 Entregar al estudiante los comandos Git propuestos (rama, `git add` por grupo, mensajes de commit terminados en `Co-Authored-By: Claude <modelo> <noreply@anthropic.com>`), sin ejecutarlos

## Workflow follow-up

- Alinear la línea de `CHANGES.md` [C-01] que menciona `application-postgres.yml` con D3 (segundo documento en `application.yml`) y marcar C-01 como `[x]` al archivar.
- Archivar el change con `/opsx:archive etapa-0-puesta-en-marcha` una vez que el estudiante integre los commits.
