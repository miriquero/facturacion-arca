# Arquitectura Propuesta

Estructura inspirada en la solución del docente (`arca-spirng`, solo lectura); la consigna fija paquetes, versiones y reglas, pero **no** los nombres de cada clase salvo los que menciona (`FacturaService`, `FacturaReporteDTO`, `ReporteFacturasService`, `DataLoader`, `EntityId`, `AuditoriaApp`, `UsuarioActual`, `ApiError`, `FacturaService.ts`, etc.).

## Patrones aplicados

| Patrón | Dónde | Por qué |
|--------|-------|---------|
| Arquitectura en capas | controller → service → repository | Separa contrato HTTP, reglas/transacciones y acceso a datos (consigna §4) |
| DTO (entrada/salida) | `dto/` | Desacopla contrato de entidades; evita exponer entidades y problemas LAZY |
| Agregado con raíz | `FacturaVenta` (+detalles), `Cliente` (+contacto, domicilio) | Ciclo de vida compartido vía cascade + orphanRemoval |
| Superclase mapeada | `EntityId`, `AuditoriaApp` | Reutilizar id y auditoría sin tablas extra |
| Proyección JPQL `SELECT new` | `FacturaReporteRepositoryImpl` (fragmento de repositorio con `EntityManager`) | Cargar solo lo que el reporte usa |
| Repository fragment (Spring Data) | `FacturaReporteRepository` + `Impl` | Consulta dinámica con filtros opcionales |
| Interceptor + ThreadLocal | `UsuarioActualInterceptor`, `UsuarioActual` | Contexto de auditoría por petición, limpiado en `afterCompletion` |
| Bloqueo pesimista | `@Lock(PESSIMISTIC_WRITE)` en repositorios de factura y punto de venta | Evitar duplicados de numeración y ediciones sobre facturas emitidas |
| Problem Details (RFC 7807) | `ApiExceptionHandler` (`@RestControllerAdvice`) | Errores uniformes 400/404/409 |
| Excepciones de dominio | `RecursoNoEncontradoException`→404, `ReglaNegocioException`→409 | Mapeo explícito a HTTP *(nombres ref)* |
| Properties tipadas | `FacturacionProperties` (`record`/`@ConfigurationProperties`) | IVA por defecto, usuario por defecto, orígenes CORS |
| Transacciones en servicio | `@Transactional` / `readOnly = true` | Atomicidad y rollback; DTO armado dentro de la transacción |
| Frontend sin framework | `app.ts` (DOM/estado) + `FacturaService.ts` (HTTP) + `models/factura.dto.ts` | Aprender fetch/DOM/promesas sin abstracciones |
| Descarte de respuestas obsoletas | contador/id de solicitud en el frontend | Evitar condiciones de carrera visuales |

## Estructura de directorios (proyecto a construir)

```
facturacion-arca/
├── README.md                        # instrucciones reproducibles (H2/PostgreSQL, tests)
├── build.gradle  settings.gradle  gradlew  gradlew.bat  gradle/wrapper/   # Gradle 8.10.2
├── docker-compose.yml               # solo PostgreSQL 16
├── requests.http                    # ejemplos HTTP reproducibles
├── .gitignore                       # build/, .gradle/, node_modules/, dist/, .idea/, tmp/
├── docs/
│   ├── consigna/tp-integrador-facturacion-arca.md   # fuente (no tocar)
│   └── etapa-1.md ... etapa-5.md    # informes de ~1 carilla
├── knowledge-base/                  # esta KB
├── openspec/                        # changes por etapa
├── src/main/java/ar/edu/utn/frm/facturacion/
│   ├── FacturacionApplication.java
│   ├── config/       # FacturacionProperties, WebConfig(CORS+interceptor), UsuarioActualInterceptor, DataLoader
│   ├── controller/   # FacturaRestController, CatalogoRestController
│   ├── dto/          # FacturaRequestDTO, FacturaDetalleRequestDTO, CambioEstadoRequestDTO, FacturaFiltrosDTO,
│   │                 # FacturaReporteDTO, FacturaDetalladaDTO, FacturaDetalleResponseDTO, OpcionDTO
│   ├── exception/    # ApiExceptionHandler, RecursoNoEncontradoException, ReglaNegocioException
│   ├── model/        # EntityId, AuditoriaApp, EstadoFactura, UsuarioActual + 14 entidades
│   ├── repository/   # JpaRepository por entidad necesaria + FacturaReporteRepository(+Impl)
│   ├── service/      # FacturaService, CatalogoService
│   └── report/       # ReporteFacturasService (PDF/XLSX/TXT)
├── src/main/resources/
│   ├── application.yml              # H2 por defecto + documento perfil postgres
│   └── fonts/                       # NotoSansCJKsc-Regular.otf + licencia OFL
├── src/test/java/ar/edu/utn/frm/facturacion/   # persistencia, servicio, HTTP (MockMvc), reportes, concurrencia
├── src/test/resources/application-test.yml     # H2 create-drop
└── frontend/
    ├── index.html                   # <meta name="api-base-url" content="http://localhost:8080/api">
    ├── styles.css  package.json  package-lock.json  tsconfig.json
    ├── src/app.ts  src/models/factura.dto.ts  src/services/FacturaService.ts
    ├── dist/                        # compilado (ignorado por Git)
    └── test/app.test.mjs            # jsdom + módulos VM
```

## Seguridad

- **Autenticación / autorización**: ninguna (fuera de alcance). `X-Usuario` solo audita (ver 03).
- **CORS**: orígenes `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173`, `http://127.0.0.1:3000`; configurable (`facturacion.cors.allowed-origins`).
- **Validación de entrada**: Bean Validation en DTOs (`@NotNull`, `@NotEmpty`, `@Valid`, `@Size`, `@Positive`, `@DecimalMin/Max`) + validaciones de negocio en el servicio; no exponer mensajes internos del parser JSON.
- **Inyección / XSS**: JPQL con parámetros (sin concatenar); frontend con `textContent` y nodos DOM.
- **Secrets**: solo credenciales de desarrollo (`facturacion/facturacion`, `sa`); no hay cargador `.env` propio (usar `SPRING_DATASOURCE_*`). No versionar nada sensible.
- **Integridad**: unicidades en BD; bloqueos pesimistas; double round-trip de importes.

## Variables de entorno / propiedades

| Variable / propiedad | Descripción | Ejemplo | Sensible |
|----------------------|-------------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | Selecciona perfil (`postgres`) | `postgres` | N |
| `SPRING_DATASOURCE_URL` | URL JDBC alternativa | `jdbc:postgresql://localhost:5432/facturacion` | N |
| `SPRING_DATASOURCE_USERNAME` | Usuario BD | `facturacion` | S (dev: N) |
| `SPRING_DATASOURCE_PASSWORD` | Password BD | `facturacion` | S |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | Driver | `org.postgresql.Driver` | N |
| `server.port` | Puerto backend | `8080` | N |
| `facturacion.iva-por-defecto` | Alícuota cuando el artículo no define | `21.0` | N |
| `facturacion.usuario-por-defecto` | Usuario de auditoría de respaldo | `sistema` | N |
| `facturacion.cors.allowed-origins` | Lista CSV de orígenes | `http://localhost:5173,...` | N |
| `JAVA_HOME` | JDK 17 para Gradle | `C:\Users\zbook\.jdks\ms-17.0.20.1` | N |
| `<meta name="api-base-url">` | Base de la API en el frontend | `http://localhost:8080/api` | N |

## Entorno de desarrollo (gotchas de esta máquina)

- **JDK**: el predeterminado de la máquina es **26**; el proyecto es Java 17. Antes de correr Gradle: `JAVA_HOME=C:\Users\zbook\.jdks\ms-17.0.20.1` (PowerShell: `$env:JAVA_HOME="C:\Users\zbook\.jdks\ms-17.0.20.1"; $env:Path="$env:JAVA_HOME\bin;$env:Path"`). Verificar con `java -version`.
- **Zona horaria con PostgreSQL en Windows**: `America/Buenos_Aires` es rechazada; ejecutar la JVM con `-Duser.timezone=America/Argentina/Buenos_Aires` (no cambia la zona del SO). Con Gradle: pasarlo vía `JAVA_TOOL_OPTIONS` o `org.gradle.jvmargs`/`bootRun.jvmArgs`.
- **Node >= 18.13** y `npm ci`; las pruebas usan módulos VM experimentales de Node.
- **Docker** opcional (solo para PostgreSQL). Comando: `docker compose up -d db`.
- **PowerShell vs Bash**: `gradlew.bat` en PowerShell; `./gradlew` en Git Bash/Linux.
- **Comandos clave**: `gradlew.bat bootRun`, `gradlew.bat test`, `gradlew.bat test bootJar`, `npm ci`, `npm run dev|build|watch|test`.

## Orden de implementación sugerido (para el roadmap)

E0 → E1 → E2 → E3 → E4 → E5, un change de OpenSpec por etapa (ver 06). Dependencias duras: E2 necesita el modelo de E1; E3 necesita DTO/proyección/reportes de E2; E4 necesita lectura de E3 (`FacturaReporteDTO`, errores `ProblemDetail`) y reabre el `DataLoader` de E1; E5 necesita la API completa de E3+E4. **Gobernanza sugerida** (ver CLAUDE.md global): E4 (concurrencia/auditoría/importes) y el control double round-trip = MEDIUM con checkpoints; persistencia (E1) = MEDIUM; DTO/catálogos = LOW; no hay dominios CRITICAL (sin auth/billing real).
