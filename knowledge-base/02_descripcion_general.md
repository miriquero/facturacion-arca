# Descripción General

## Stack tecnológico (fijado por la consigna)

| Capa | Tecnología | Versión |
|------|-----------|---------|
| Lenguaje backend | Java | 17 (fuente y destino 17) |
| Framework | Spring Boot (Web, Data JPA, Validation) | 3.3.5 |
| Build | Gradle wrapper (sin instalación global) | 8.10.2 |
| Persistencia | Jakarta Persistence / Hibernate | la de Boot 3.3.5 |
| Boilerplate | Lombok (`compileOnly` + `annotationProcessor`) | la de Boot |
| BD desarrollo | H2 en memoria | `jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1`, usuario `sa`, password vacía |
| BD alternativa | PostgreSQL vía perfil `postgres` + `docker-compose.yml` | 16 |
| PDF | OpenPDF | 2.0.3 |
| Excel | Apache POI | 5.3.0 |
| TXT | UTF-8 con tabuladores | - |
| Frontend | HTML, CSS, TypeScript estricto, módulos ES2020, **sin framework** | TypeScript 5.6 (la referencia declara `^5.6.3`, el lockfile resuelve 5.9.3) |
| Servidor frontend | `http-server` (npm) | puerto 5173 |
| Pruebas backend | Spring Boot Test / JUnit Platform / MockMvc | - |
| Pruebas frontend | Node.js + jsdom (módulos VM experimentales) | Node >= 18.13 (jsdom 26.1.0 en la referencia) |
| Coordenadas | Grupo `ar.edu.utn.frm`, paquete `ar.edu.utn.frm.facturacion`, proyecto `facturacion-arca` | JAR: `build/libs/facturacion-arca-1.0.0.jar` |

> Gotcha de entorno (esta máquina): el JDK por defecto es **26**; hay que compilar/ejecutar con **JDK 17** en `C:\Users\zbook\.jdks\ms-17.0.20.1`. Ver `08_arquitectura_propuesta.md` (sección Entorno).

## Arquitectura general

Una sola aplicación backend en capas + un frontend independiente:

```
frontend (index.html + src/app.ts)            puerto 5173 (http-server)
        |
        v
frontend/src/services/FacturaService.ts       cliente fetch, ApiError
        |  HTTP /api  (JSON + descargas)  + header X-Usuario: sistema
        v
controller --> service --> repository --> H2 | PostgreSQL        puerto 8080
                  |
                  +-- model: asociaciones y cálculo de importes
controller --> report (PDF / XLSX / TXT a partir de DTOs)
```

Reglas arquitectónicas de la consigna:
- Los **controladores** manejan contratos HTTP y DTO; los **servicios** coordinan reglas y transacciones; los **repositorios** acceden a datos.
- La consulta de reportes **proyecta directamente a `FacturaReporteDTO` mediante JPQL** (`SELECT new ...`); el servicio de reportes transforma esos DTO en archivos.
- **Las entidades nunca se devuelven como respuesta HTTP.**
- Transacciones delimitadas en los servicios; `spring.jpa.open-in-view=false`.

Paquetes (consigna, etapa 0): `model`, `dto`, `repository`, `service`, `controller`, `exception`, `config`, `report`.

## Integraciones externas

| Servicio | Propósito | Tipo |
|----------|-----------|------|
| ARCA / AFIP | **Ninguna** (CAE simulado localmente) | - |
| PostgreSQL 16 | BD alternativa a H2 (persistente entre reinicios) | JDBC, vía Docker Compose |
| Docker Compose | Levanta solo la base de datos (no backend ni frontend) | CLI |
| Maven Central / npm | Necesarios en la primera ejecución del wrapper y de `npm ci` | HTTP |

## API REST (resumen; detalle en 06 y 07)

Base local: `http://localhost:8080/api`.

| Método | Ruta | Éxito | Etapa |
|--------|------|-------|-------|
| GET | `/api/facturas` (filtros `fechaDesde`, `fechaHasta`, `estado`, `montoMinimo`) | 200 `List<FacturaReporteDTO>` | 3 |
| GET | `/api/facturas/{id}` | 200 `FacturaDetalladaDTO` / 404 | 3 |
| GET | `/api/facturas/reporte` (mismos filtros + `formato`) | 200 archivo (`Content-Disposition: attachment`) | 3 |
| GET | `/api/catalogos/{puntos-venta,clientes,condiciones-iva,tipos-moneda,lista-precio-articulos}` | 200 `List<OpcionDTO>` | 3 |
| POST | `/api/facturas` | 201 + `Location` + `FacturaReporteDTO` | 4 |
| PUT | `/api/facturas/{id}` | 200 `FacturaReporteDTO` | 4 |
| PATCH | `/api/facturas/{id}/estado` | 200 `FacturaReporteDTO` | 4 |
| DELETE | `/api/facturas/{id}` | 204 sin cuerpo | 4 |

Errores: `ProblemDetail` (`status`, `title`, `detail`, `timestamp`; validación agrega mapa `errores` campo→mensaje). Códigos 400 / 404 / 409.

## Configuración (`application.yml`; ver también 08)

Puerto 8080; H2 por defecto; `ddl-auto: update`; `show-sql` y `format_sql` activos en desarrollo; `open-in-view: false`; consola H2 en `/h2-console`; perfil `postgres` (consola H2 desactivada); propiedades `facturacion.iva-por-defecto` (21.0), `facturacion.usuario-por-defecto` (`sistema`), `facturacion.cors.allowed-origins`.
