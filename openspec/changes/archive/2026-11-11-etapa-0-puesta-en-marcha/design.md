# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos verificables: ver `specs/`.

Estado actual y restricciones que condicionan el enfoque:

- El repo no tiene build: solo `docs/`, `knowledge-base/`, `openspec/`, `CHANGES.md`, `CLAUDE.md`/`AGENTS.md`.
- Máquina del estudiante: JDK por defecto **26** (en `PATH`, `C:\Java\jdk-26`), JDK 17 en `C:\Users\zbook\.jdks\ms-17.0.20.1`, sin Gradle global; en la caché `~/.gradle/wrapper/dists` hay distribuciones **9.0.0** y **9.6.0**, pero no la 8.10.2. Node v24, Docker 29 disponible.
- **Gradle 8.10.2 no puede ejecutarse sobre JDK 26** (su soporte llega hasta Java 23). Por eso, aunque se use toolchain, el proceso de Gradle necesita `JAVA_HOME` apuntando a un JDK 17 (o 21). Esto condiciona D1 y D4.
- Gobernanza ALTA: datasource, `ddl-auto`, perfiles y docker-compose se escriben recién en el apply, tras la revisión del estudiante, y cada pieza entra detrás de una prueba en rojo (TDD estricto).
- Semilla DD-13 (`api-facturas`, solo lectura): Maven + Spring Boot 4.1.1. Ver D8.
- Discrepancia detectada en la documentación: `CHANGES.md` [C-01] menciona `application-postgres.yml`, mientras que `knowledge-base/08_arquitectura_propuesta.md` y la consigna («Usen application.yml») apuntan a un único archivo. Se resuelve en D3.

## Goals / Non-Goals

**Goals:**
- Que cada propiedad de configuración exigida por la consigna quede respaldada por una prueba automatizada que falla sin ella (o, si no es automatizable de forma honesta, por una verificación manual explícita en `tasks.md`).
- Que el build no dependa del JDK 26 ni de un Gradle global, y que los comandos del README funcionen igual en PowerShell y Git Bash.
- Que la configuración del perfil `postgres` se pueda probar sin una base PostgreSQL en ejecución.

**Non-Goals:**
- `application-test.yml` (perfil `test` con `create-drop`): nace en C-02 junto con el `DataLoader`, que es lo que necesita excluirse del perfil `test`.
- Testcontainers o pruebas contra PostgreSQL real en CI: la verificación PostgreSQL de esta etapa es manual (SU-08).
- Provisión automática de JDK (plugin `foojay-resolver`): evita descargas implícitas y otra dependencia que explicar.
- Recompilación automática en `npm run dev` (para eso existe `npm run watch`).

## Decisions

### D1 — Java 17: toolchain de Gradle **y** `JAVA_HOME` 17
`build.gradle` declara `java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }`, y el README exige `JAVA_HOME` = JDK 17 para ejecutar el wrapper.
- **Por qué**: el toolchain garantiza que compilación, pruebas y `bootRun` usen Java 17 aunque el daemon corra en otra versión compatible, y falla con un mensaje claro si no encuentra un JDK 17. Pero el toolchain no resuelve el arranque del propio Gradle 8.10.2, que no corre sobre JDK 26: por eso `JAVA_HOME` sigue siendo obligatorio. Con `JAVA_HOME` en 17 el JDK en uso satisface el toolchain sin auto-provisión.
- **Alternativa descartada**: depender solo de `JAVA_HOME` (o `sourceCompatibility = 17`): compila con lo que haya en `JAVA_HOME`; si alguien usa JDK 21 el bytecode sale bien pero las pruebas corren en 21, que no es la versión del proyecto. **Descartada también**: fijar `org.gradle.java.home` en un `gradle.properties` versionado, porque es una ruta propia de esta máquina.

### D2 — Groovy DSL (`build.gradle`, `settings.gradle`)
- **Por qué**: la consigna y su Anexo B nombran `build.gradle`; es el formato que el docente espera leer y el que la mayoría del material del curso usa.
- **Alternativa descartada**: Kotlin DSL (`build.gradle.kts`): tipado y mejor autocompletado, pero se aparta del nombre de archivo que cita la consigna y agrega sintaxis a defender sin aportar nada en un build de un solo módulo.

### D3 — Perfil `postgres` como segundo documento YAML dentro de `application.yml`
`application.yml` tiene un primer documento (H2, por defecto) y, separado por `---`, un segundo documento con `spring.config.activate.on-profile: postgres` que redefine `spring.datasource.*` y pone `spring.h2.console.enabled: false`. Lo común (`ddl-auto`, `show-sql`, `format_sql`, `open-in-view`) queda solo en el primer documento y se hereda.
- **Por qué**: la consigna dice «Usen application.yml» y la KB (08) documenta «H2 por defecto + documento perfil postgres»; un único archivo permite ver de un vistazo qué cambia entre motores, que es lo que se defiende oralmente.
- **Alternativa descartada**: `application-postgres.yml` (lo que menciona `CHANGES.md`): igual de válido para Spring, pero reparte la configuración en dos archivos y contradice la literalidad de la consigna. Se propone actualizar la línea de `CHANGES.md` al cerrar el change.

### D4 — Obtención del wrapper 8.10.2 sin Gradle global
Secuencia en el apply (todas con `JAVA_HOME` = JDK 17):
1. Crear primero `settings.gradle` (`rootProject.name = 'facturacion-arca'`), porque Gradle 9 no ejecuta builds sin archivo de settings.
2. Usar la distribución **9.0.0 ya presente en la caché** (`~/.gradle/wrapper/dists/gradle-9.0.0-bin/*/gradle-9.0.0/bin/gradle`) para generar el wrapper: `gradle wrapper --gradle-version 8.10.2 --distribution-type bin`.
3. Ejecutar `./gradlew wrapper --gradle-version 8.10.2 --gradle-distribution-sha256-sum <sha oficial>` para que la propia 8.10.2 regenere scripts y `gradle-wrapper.jar` (queda todo coherente con 8.10.2 y con verificación de checksum).
4. Verificar con `./gradlew --version` → «Gradle 8.10.2».
- **Por qué**: no requiere instalar nada ni descargar a mano; la única descarga es la distribución 8.10.2 que el wrapper baja de `services.gradle.org` (lo mismo que hará cualquier clon del repo). Gradle 9.0.0 corre sobre JDK 17, así que nunca se toca el JDK 26.
- **Alternativa descartada**: descargar `gradle-8.10.2-bin.zip` manualmente a una carpeta temporal y ejecutar su `bin/gradle wrapper`: funciona, pero es un paso manual más y una descarga fuera del mecanismo del wrapper. Queda como plan B si la caché 9.0.0 estuviera corrupta. **Descartada también**: copiar el wrapper de otro proyecto (p. ej. `arca-spirng`), prohibido por ser solución del docente.

### D5 — Pruebas de frontend: `node:test` + jsdom
`"test": "npm run build && node --test test/"`, con `jsdom` como única dependencia de pruebas. Las pruebas son archivos `.mjs` en `frontend/test/` que importan los módulos compilados de `dist/` y construyen documentos con `new JSDOM(...)`.
- **Por qué**: `node:test` viene con Node (estable desde 18; la consigna pide Node 18.13+, que es justamente la versión con `node --test` estable), no agrega frameworks que defender y encaja con «Node + jsdom» de la consigna. Probar el JavaScript compilado verifica además que la salida ES2020 es importable como módulo.
- **Alternativa descartada**: Vitest o Jest con entorno jsdom: más cómodos, pero suman un runner, transformaciones y configuración propia; Jest además requiere configuración extra para módulos ES. **Descartado también**: `--experimental-vm-modules` (lo que usa la referencia del docente según la KB): innecesario para importar módulos ES con `import()` estándar en esta etapa.

### D6 — Diseño de las pruebas de configuración del backend
Cada propiedad se prueba por su **efecto observable**, no releyendo el YAML:

| Prueba | Observa | Falla antes porque… |
|--------|---------|----------------------|
| `contextLoads` | el contexto arranca | no existe `@SpringBootApplication` |
| datasource H2 | `DataSource.getConnection().getMetaData()` → URL `jdbc:h2:mem:facturacion…`, usuario `SA` | sin YAML Boot genera `jdbc:h2:mem:<uuid>` |
| Hibernate | `EntityManagerFactory.getProperties()` → `hbm2ddl.auto=update`, `show_sql`, `format_sql` | con H2 embebido el default es `create-drop` y sin SQL |
| open-in-view | ausencia de bean `OpenEntityManagerInViewInterceptor` | Boot lo registra por defecto |
| consola H2 | `GET /h2-console/` → 200 con servidor en puerto aleatorio | deshabilitada por defecto (404) |
| perfil postgres | `Environment` con `@ActiveProfiles("postgres")` sobre una configuración **sin autoconfiguración** (clase `@Configuration` vacía anidada en el test) | el documento del perfil no existe |
| zona horaria | `TimeZone.getDefault().getID()` | Gradle no fija `user.timezone` |
| paquetes | recurso `…/<paquete>/package-info.class` en el classpath | los `package-info.java` no existen |

La prueba del perfil `postgres` no crea `DataSource` ni conecta: solo resuelve el `Environment`, por eso no necesita PostgreSQL. Ese mismo test triangula con el perfil por defecto (URL H2 intacta).
- **No se automatiza** «los datos no sobreviven al reinicio»: con `DB_CLOSE_DELAY=-1` la base vive mientras viva la JVM, así que dos contextos en la misma JVM de pruebas compartirían datos; una prueba así no demostraría nada sobre el reinicio del proceso. Se verifica manualmente (reiniciar `bootRun` y consultar la consola).
- **Alternativa descartada**: pruebas que leen `application.yml` y comparan cadenas: son tautológicas respecto del archivo y no detectan, por ejemplo, que una variable `SPRING_DATASOURCE_URL` pise la configuración.

### D7 — Zona horaria vía Gradle, no vía YAML
`tasks.named('bootRun')` y `tasks.named('test')` agregan `-Duser.timezone=America/Argentina/Buenos_Aires` (`jvmArgs` / `systemProperty`), con configuración perezosa (`tasks.named`) para no romper la configuration cache. El README documenta la misma bandera para `java -jar`.
- **Por qué**: el problema (DD-12) es la zona que el driver JDBC envía al conectar, que sale de la zona por defecto de la JVM; ninguna propiedad de `application.yml` la cambia antes de que arranque el pool.
- **Alternativa descartada**: `TimeZone.setDefault(...)` en `main`: es un efecto global oculto en el código, no aplica a las pruebas que no pasan por `main` y es más difícil de explicar. **Descartada también**: `JAVA_TOOL_OPTIONS` global, porque afecta a todas las JVM de la máquina.

### D8 — Semilla `api-facturas` (DD-13): qué se reutiliza y qué cambia
| Origen | Se reutiliza | Se cambia |
|--------|--------------|-----------|
| `pom.xml` | Lista de capacidades: JPA, web, H2, PostgreSQL, Lombok con procesador de anotaciones, OpenPDF | Maven → Gradle Groovy; Boot 4.1.1 → 3.3.5; módulos de Boot 4 (`spring-boot-h2console`, `starter-webmvc`, `starter-data-jpa-test`, `starter-webmvc-test`) → `starter-web` y `starter-test` de Boot 3 (la consola H2 viene en la autoconfiguración); OpenPDF 1.3.36 → 2.0.3; se **quita** `mysql-connector-j`; se **agregan** `starter-validation` y `poi-ooxml` 5.3.0; `annotationProcessorPaths` → `compileOnly` + `annotationProcessor` |
| `frontend/package.json` | Nombre, `private`, scripts `build` (`tsc`) y `watch` | `typescript` `^5.6.0` → `~5.6.3` (`^` admitiría 5.7+, fuera de la versión fijada); script `serve` en 5500 con `npx serve` → `dev` con `http-server` en 5173 declarado en `devDependencies`; se agregan `test`, `jsdom`, `"type": "module"` y `engines.node >= 18.13` |
| `frontend/tsconfig.json` | `target`/`module` ES2020, `lib` con DOM, `rootDir src`, `outDir dist`, `strict`, `noUnusedLocals`, `noImplicitReturns`, `sourceMap` | `moduleResolution` `bundler` → `node10`: el proyecto no usa bundler y el navegador exige imports con extensión `.js`, convención que se fija en el código |
| `application.properties` | **Nada** | Usa PostgreSQL por defecto, contradice la consigna (H2 por defecto) |

Como la etapa 0 no tiene informe (IN-05), la declaración de semilla e IA va en una sección breve del README; se trasladará a `docs/etapa-1.md`.

### D9 — Otras decisiones menores
- `tasks.named('jar') { enabled = false }`: así `build/libs/` contiene solo `facturacion-arca-1.0.0.jar` (sin `-plain.jar`), coincidiendo exactamente con la verificación de la consigna.
- `package-info.java` con Javadoc por paquete: Git no versiona directorios vacíos y el Javadoc documenta la responsabilidad que pide la consigna.
- `.gitattributes`: `gradlew text eol=lf`, `*.bat text eol=crlf`, `*.jar binary`, para que el wrapper funcione en ambos sistemas.
- Frontend: `src/config.ts` exporta `leerApiBaseUrl(doc: Document): string` (lanza `Error` si falta o está vacío); `src/app.ts` la usa y muestra la URL con `textContent`. Es la primera pieza real que reutilizará el cliente HTTP de C-06.
- `http-server` se invoca con `-c-1` (sin caché) para no servir módulos viejos tras recompilar.
- Versiones exactas de herramientas no fijadas por la consigna (`io.spring.dependency-management` 1.1.6, `http-server` 14.1.x, `jsdom` 25.x) se registran en el README; `package-lock.json` las congela.

## Risks / Trade-offs

- [Gradle ejecutado con el JDK 26 por defecto falla con un error poco claro] → README con los comandos de `JAVA_HOME` para PowerShell y Bash como primer paso; `java -version` como verificación previa.
- [Caché Gradle 9.0.0 incompleta o borrada] → plan B de D4 (distribución 8.10.2 temporal en el scratchpad, verificada por SHA-256).
- [`ddl-auto: update` en el perfil `postgres` deja columnas viejas al cambiar entidades en C-02..C-05] → documentado en el README: `docker compose down -v` reinicia la base; no se usa en producción.
- [Variables `SPRING_DATASOURCE_*` o `SPRING_PROFILES_ACTIVE` del entorno pisan H2 y las pruebas fallan] → las pruebas de datasource lo detectan (observan la conexión real); el README lo menciona en «Problemas frecuentes».
- [Puerto 5432 u 8080 ocupado] → README indica cambiar juntos la publicación del contenedor y la URL (o `server.port` y el meta `api-base-url`, RN-CF-04).
- [La prueba de consola H2 levanta un servidor real] → usa puerto aleatorio; es la única prueba con servidor y se mantiene en una clase separada.
- [Probar `dist/` obliga a compilar antes de testear] → `npm test` encadena `npm run build`; costo de segundos.

## Migration Plan

No hay sistema previo que migrar. Rollback: los archivos son nuevos y no hay commits del agente; el estudiante descarta los cambios del árbol de trabajo si decide no integrarlos. Los commits, el push y la decisión de rama los ejecuta el estudiante con los comandos que se entregan al final del apply.

## Open Questions

- ¿Se actualiza en este mismo change la línea de `CHANGES.md` [C-01] que menciona `application-postgres.yml` para alinearla con D3, o se deja para el archivo del change? (No altera specs ni tareas de implementación.)
