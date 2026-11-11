# CHANGES — Secuencia de Implementación

> Índice canónico de todos los changes del proyecto **Sistema de Facturación ARCA (TP Integrador UTN FRM)**.
> Cada change corresponde a **una etapa de la consigna** (6 changes, estrictamente secuenciales, un solo desarrollador).
> Modo **Strict TDD**: toda tarea de cada change debe poder implementarse como RED → GREEN → TRIANGULATE → REFACTOR.
> **Leer este archivo antes de ejecutar cualquier `/opsx:propose`.**

---

## Cómo usar este documento

1. Identificar el change a implementar (verificar que su dependencia está en `openspec/changes/archive/`).
2. Leer los docs de la knowledge-base indicados en "Leer antes" (y la consigna en `docs/consigna/tp-integrador-facturacion-arca.md`).
3. Ejecutar `/opsx:propose <nombre-del-change>` (ej. `/opsx:propose etapa-1-persistencia-jpa`).
4. Implementar con `/opsx:apply` (TDD estricto) y archivar con `/opsx:archive <nombre-del-change>`.
5. Marcar el checkbox `[x]` en este archivo.

### Definición de "terminado" (común a todos los changes)

Un change solo se archiva cuando cumple TODO lo siguiente:

- Tests en verde sobre **H2**; si se corrió contra **PostgreSQL** (perfil `postgres`), declararlo explícitamente en el informe (SU-08).
- `docs/etapa-N.md` (~1 carilla, sin repetir el enunciado): decisiones y alternativas, dificultades y resolución, evidencia de verificaciones, **uso de IA declarado** (para qué, qué se revisó, qué se corrigió). Etapa 0 usa `docs/etapa-0.md` opcional.
- `README.md` actualizado con los comandos reproducibles de la etapa.
- Commits comprensibles, PR hacia la rama principal, revisión de los propios cambios y ejecución de comprobaciones antes de integrar.
- Git tag `etapa-N` (la versión evaluada es la del tag). La Etapa 0 no lleva nota ni tag evaluable; se sugiere un tag interno `etapa-0` por trazabilidad (ver IN-05 en `knowledge-base/10_preguntas_abiertas.md`).

---

## Árbol de dependencias

```
C-01 etapa-0-puesta-en-marcha
  └── C-02 etapa-1-persistencia-jpa
        └── C-03 etapa-2-lombok-dto-jpql-reportes
              └── C-04 etapa-3-api-rest-lectura
                    └── C-05 etapa-4-escritura-estados-concurrencia
                          └── C-06 etapa-5-frontend-typescript
                                └── (Entrega final + defensa, fuera de changes: US-601/602)
```

### Paralelismo por fase

> Cadena 100 % lineal: cada etapa consume artefactos de la anterior (entidades → DTO/reportes → API de lectura → escritura → UI). **No hay paralelismo entre changes.** Se anotan solo tareas paralelizables DENTRO de un change.

```
GATE 0: ninguna
  → C-01 etapa-0-puesta-en-marcha            [Agente A]
     (dentro del change: esqueleto backend y esqueleto frontend son independientes)

GATE 1: C-01 ✓
  → C-02 etapa-1-persistencia-jpa            [Agente A]
     (dentro: entidades de catálogo [Marca, Rubro, Articulo, ListaPrecio, ...] vs. Cliente/Contacto/Domicilio vs. Factura-Detalle)

GATE 2: C-02 ✓
  → C-03 etapa-2-lombok-dto-jpql-reportes    [Agente A]
     (dentro: los tres exportadores PDF / XLSX / TXT son independientes entre sí una vez fijado FacturaReporteDTO)

GATE 3: C-03 ✓
  → C-04 etapa-3-api-rest-lectura            [Agente A]
     (dentro: catálogos, CORS y ProblemDetail son independientes de listado/detalle/reporte)

GATE 4: C-04 ✓
  → C-05 etapa-4-escritura-estados-concurrencia [Agente A]
     (dentro: interceptor X-Usuario/auditoría y cálculo BigDecimal puro son independientes; el resto es secuencial)

GATE 5: C-05 ✓
  → C-06 etapa-5-frontend-typescript         [Agente A]
     (dentro: cliente HTTP + tests de ApiError vs. maquetado de formularios)
```

### Camino crítico (6 changes — todo el roadmap es camino crítico)

```
C-01 → C-02 → C-03 → C-04 → C-05 → C-06
```

Pesos de nota (sobre el 70 % de etapas): E1 20 % · E2 20 % · E3 15 % · E4 25 % · E5 20 %. La defensa (30 % de la nota final) requiere los 6 changes completos. C-05 es el de mayor peso y riesgo: reservar el mayor margen de tiempo (semanas 7-8).

### Plan óptimo con 3 agentes

> Proyecto individual: se usa un único hilo de trabajo. Las columnas B y C quedan libres; se sugieren como apoyo opcional (revisión/exploración), no como ejecución paralela de changes.

| Paso | Agente A (Backend/Core) | Agente B (Apoyo: tests/docs) | Agente C (Frontend) |
|------|-------------------------|------------------------------|---------------------|
| 1 | C-01 etapa-0-puesta-en-marcha | — | — |
| 2 | C-02 etapa-1-persistencia-jpa | — | — |
| 3 | C-03 etapa-2-lombok-dto-jpql-reportes | — | — |
| 4 | C-04 etapa-3-api-rest-lectura | — | — |
| 5 | C-05 etapa-4-escritura-estados-concurrencia | — | — |
| 6 | — | — | C-06 etapa-5-frontend-typescript |

---

## FASE 1 — Fundaciones (Semana 1, sin nota)

### [C-01] `etapa-0-puesta-en-marcha`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Proyecto Gradle (wrapper 8.10.2 versionado), `facturacion-arca`, grupo `ar.edu.utn.frm`, paquete base `ar.edu.utn.frm.facturacion`; Spring Boot 3.3.5, Java 17 (toolchain), starters Web, Data JPA, Validation, Lombok, H2, PostgreSQL, test; OpenPDF 2.0.3 y Apache POI 5.3.0 declarados (US-001).
  - Paquetes vacíos: `model, dto, repository, service, controller, exception, config, report`.
  - `application.yml`: H2 `jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1`, usuario `sa`, consola `/h2-console`, `ddl-auto: update`, `show-sql`/`format_sql`, `open-in-view: false`, TZ Argentina; perfil `postgres` (`application-postgres.yml`) y `docker-compose.yml` PostgreSQL 16 (db/user/pass `facturacion`, 5432) (US-003).
  - Test de humo `contextLoads` (RED: contexto no arranca sin config) y verificación de `./gradlew test bootJar` generando `build/libs/facturacion-arca-1.0.0.jar`.
  - Esqueleto frontend TypeScript 5.6 sin framework: `package.json` con scripts `npm ci`, `build`, `dev`, `test`; `tsconfig` estricto ES2020; `index.html` con `<meta name="api-base-url">`; servido por HTTP en 5173; runner de pruebas (jsdom) con un test de humo (US-002).
  - `.gitignore` (`build/`, `.gradle/`, `node_modules/`, `dist/`, IDE); `README.md` con comandos reproducibles de arranque, pruebas y compilación, y declaración del motor probado (H2/PostgreSQL).
  - Gotchas de entorno: JDK 17 forzado, TZ Argentina (DD-12).
  - Tests: contexto Spring levanta con H2; datos no sobreviven al reinicio; build del frontend compila.
- **Dependencias**: ninguna
- **Governance**: ALTO (configuración que afecta la persistencia de datos: datasource, `ddl-auto`, perfiles y docker-compose)
- **Semilla** (DD-13, adaptar detrás de un test en rojo y declarar en el informe): `api-facturas/pom.xml` (lista de dependencias a traducir a Gradle), `frontend/package.json` y `frontend/tsconfig.json`. No reutilizar `application.properties` (PostgreSQL por defecto): la consigna pide H2.
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 0 (US-001..003)
  - `knowledge-base/07_flujos_principales.md` §E0 Flujo 0.1 y 0.2
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios, §Variables de entorno / propiedades, §Entorno de desarrollo (gotchas)
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-12, SU-08
  - `knowledge-base/11_metodologia_evaluacion_y_defensa.md` §Git y entregas

---

## FASE 2 — Dominio y persistencia (Semanas 2-5, 40 % del 70 %)

### [C-02] `etapa-1-persistencia-jpa`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Superclases mapeadas: `EntityId` (id IDENTITY) y `AuditoriaApp` (fechas `LocalDateTime`, usuarios `ManyToOne LAZY`, alta/usuario de carga `updatable=false`) (US-101).
  - 14 entidades: `Usuario, FacturaVenta (tabla factura_venta), FacturaVentaDetalle, Cliente, Contacto, Domicilio, PuntoVenta, CondicionIva, TipoMoneda, Articulo, ListaPrecio, ListaPrecioArticulo, Marca, Rubro`; enum `EstadoFactura` con `EnumType.STRING`; unicidades (usuario, número de punto de venta, lista/artículo, punto de venta/número de factura); `numero` nulo en borrador (US-102).
  - Agregado Factura-Detalles: `agregarDetalle` mantiene ambos extremos, colección inicializada, reemplazo vía `clear()`, FK en el detalle (`mappedBy="factura"`), sin cascada a catálogos; Cliente con Contacto/Domicilio (cascade + orphanRemoval) (US-103).
  - Repositorios solo los necesarios (ninguno para detalles/contacto/domicilio) (US-104).
  - `DataLoader` provisional (`CommandLineRunner` transaccional, excluido del perfil `test`, omite si hay usuarios): usuarios `sistema` y `acortez`, catálogos y seis facturas; limpia contexto de auditoría (US-105; la versión integrada con `FacturaService` queda para C-05, DD-11).
  - Tests de persistencia: guardar factura con 2 detalles + `flush/clear` + releer; relación inversa; cliente opcional; catálogos conservados; eliminación de huérfanos; violaciones reales de unicidad y nulabilidad (US-106).
  - Informe `docs/etapa-1.md`: ejemplo de SQL generado, explicación de relaciones, por qué la factura es raíz de agregado, qué datos permanecen al eliminar un borrador.
  - Tests esperados: persistencia (H2); restricciones; orphanRemoval; declaración de motor.
- **Dependencias**: `C-01`
- **Governance**: ALTO (modelo de datos y constraints que todo lo demás referencia; sin lógica de negocio destructiva)
- **Semilla** (DD-13, adaptar detrás de un test en rojo y declarar en el informe): `api-facturas/model/` (14 entidades + `EntityId`/`AuditoriaApp`, ya con `@Getter` y exclusiones) y `config/CargaDatosPrueba.java`; secundaria: modelo de `JPA/TP2_JPA_corregido`. Adaptar: `java.util.Date` a `LocalDate`/`LocalDateTime`, `estado` String a `EstadoFactura` `STRING`, agregar unicidades y `nullable` faltantes.
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Superclases, §Entidades, §Constraints de unicidad, §Seed data inicial (`DataLoader`)
  - `knowledge-base/05_reglas_de_negocio.md` §Persistencia y modelo (RN-PE-01..14), §Auditoría (RN-AU-08)
  - `knowledge-base/06_funcionalidades.md` §Épica 1 (US-101..106)
  - `knowledge-base/07_flujos_principales.md` §E1 Flujo 1.1 y 1.2
  - `knowledge-base/12_errores_frecuentes_y_glosario.md` §Errores frecuentes (cascadas, lado propietario)

### [C-03] `etapa-2-lombok-dto-jpql-reportes`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Higiene Lombok: `compileOnly` + `annotationProcessor`; sin `@Data` indiscriminado; `toString/equals/hashCode` de cabecera excluyen `detalles`, detalle excluye `factura`; exclusiones coherentes con la herencia; test que demuestra ausencia de recursión (US-201, RN-GE-03).
  - `FacturaReporteDTO` (id, numeroFactura nulo en borrador, fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion, importeTotal, cantidadItems, estado texto, codigoMoneda) con constructor `Long` para COUNT y `EstadoFactura` (US-202).
  - Fragmento de repositorio con `EntityManager` y JPQL `SELECT new` dinámico: `LEFT JOIN` cliente y detalles, `COALESCE` «Consumidor Final», joins condición IVA/punto de venta/moneda, `GROUP BY`, orden `fechaEmision DESC, id DESC`; conserva facturas sin cliente y cabeceras sin detalles (RN-FI-05/08/09).
  - `ReporteFacturasService` (en `report/`): `byte[] pdf(List<FacturaReporteDTO>)`, `xlsx(...)`, `txt(...)`; sin dependencia del DataLoader ni de carpetas locales (US-206).
  - PDF (OpenPDF): A4 apaisado, título, fecha de generación, cantidad de registros, encabezados repetidos, mensaje sin resultados, 9 columnas, guion en borrador, fuente Noto Sans CJK SC incrustada (Identity-H, licencia OFL), glifo no soportado → error explícito, totales solo EMITIDA por moneda (US-203, RN-RE-01..05,07,11).
  - Excel (POI): hoja `Facturas`, encabezados destacados, celdas tipadas, formatos, autoajuste, totales `SUMIFS` EMITIDA por moneda, fórmulas evaluadas + recálculo habilitado, moneda sin emitidas → 0 (US-204).
  - TXT: UTF-8, tabuladores, 9 columnas, escapes reversibles (`\\`, `\t`, `\r`, `\n`), fechas ISO, importes 2 decimales con punto, sin pie (US-205).
  - Tests: proyección (cantidad de ítems, cliente opcional, número nulo, estado, moneda); servicio con lista vacía, múltiples monedas, estados mezclados, caracteres especiales, PDF multipágina, carácter no soportado, tipos y fórmulas Excel, round-trip del TXT escapado.
  - Muestras de los tres archivos revisadas visualmente (PDF completo) e informe `docs/etapa-2.md`: joins, constructor de proyección, criterio de totales por estado y moneda, estrategia de preservación de texto.
- **Dependencias**: `C-02`
- **Governance**: MEDIO (lógica de negocio de consulta/reportes; sin escritura de datos)
- **Semilla** (DD-13, adaptar detrás de un test en rojo y declarar en el informe): `api-facturas/dto/FacturaReporteDTO.java`, la consulta `SELECT new` y los exports PDF/Excel del controller; secundaria: `ConsultasJPQL.java` de `TP3_JPQL_facturacion_arca`. Adaptar: OpenPDF 1.3.36 (`com.lowagie`) a 2.0.3, Excel con Apache POI 5.3.0, agregar TXT y moneda.
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 2 (US-201..206)
  - `knowledge-base/05_reglas_de_negocio.md` §Reportes (RN-RE), §Filtros de consulta (RN-FI), §Reglas globales (RN-GE)
  - `knowledge-base/07_flujos_principales.md` §E2 Flujo 2.1 y 2.2
  - `knowledge-base/04_modelo_de_datos.md` §DTOs del contrato
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-06, DD-08, DD-09, SU-04

---

## FASE 3 — API REST (Semanas 6-8, 40 % del 70 %)

### [C-04] `etapa-3-api-rest-lectura`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `GET /api/facturas` → `List<FacturaReporteDTO>` con filtros opcionales (fechas desde/hasta inclusivas, estado, monto mínimo, según consigna), orden fecha DESC/id DESC, sin resultados = `[]`; estado normalizado (trim + mayúsculas); estado desconocido, fechas invertidas, monto no finito → 400 (US-301, RN-FI-01..06).
  - `GET /api/facturas/{id}` → `FacturaDetalladaDTO` (ids y descripciones editables, estado, importes, CAE, observaciones, renglones); 404 si no existe; asociaciones resueltas en servicio con `open-in-view=false` (US-302).
  - `GET /api/facturas/reporte` con los mismos filtros + `formato` (default `pdf`; `xlsx|excel`, `txt|tsv`), `Content-Type` correcto, `Content-Disposition: attachment`, formato inválido → 400 (US-303, RN-RE-10).
  - `GET /api/catalogos/{puntos-venta, clientes, condiciones-iva, tipos-moneda, lista-precio-articulos}` → `OpcionDTO`; sin altas/modificaciones/bajas (US-304, RN-CT-01/02).
  - `FacturaService` de lectura (`readOnly`), `CorsConfig` para localhost/127.0.0.1 puertos 5173 y 3000 (documentar que CORS no autentica), `@RestControllerAdvice` con `ProblemDetail` (status, title, detail, timestamp); consulta vacía ≠ inexistente (US-305, RN-CF-01, RN-ER-01..06).
  - `requests.http` con ejemplos reproducibles (filtros individuales/combinados, vacío, 404, formatos, formato inválido).
  - Tests (`@WebMvcTest` + integración H2): filtros individuales y combinados, ausencia de resultados, «Consumidor Final», detalle inexistente, formatos admitidos e inválido, el archivo descargado corresponde a los mismos filtros.
  - Informe `docs/etapa-3.md` justificando DTO y manejo de errores.
- **Dependencias**: `C-03`
- **Governance**: MEDIO (API de solo lectura; CORS es configuración sin datos sensibles)
- **Semilla** (DD-13, adaptar detrás de un test en rojo y declarar en el informe): `api-facturas/controller/FacturaRestController.java` (GET), `config/CorsConfig.java`, `exception/GlobalExceptionHandler.java` y `RecursoNoEncontradoException.java`. Adaptar a `ProblemDetail`, filtros validados, GET por id y catálogos (no existen en la semilla).
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 3 (US-301..305)
  - `knowledge-base/07_flujos_principales.md` §E3 Flujo 3.1..3.4
  - `knowledge-base/05_reglas_de_negocio.md` §Filtros (RN-FI), §Catálogos (RN-CT), §Errores HTTP (RN-ER), §Configuración y CORS (RN-CF)
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones aplicados, §Seguridad
  - `knowledge-base/10_preguntas_abiertas.md` IN-01, IN-03 (códigos HTTP dudosos)

### [C-05] `etapa-4-escritura-estados-concurrencia`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `POST /api/facturas` → 201 + `Location` + `FacturaReporteDTO`; borrador sin número ni CAE; cliente opcional; fecha por defecto hoy; precio tomado de `ListaPrecioArticulo`; importes calculados en servidor (US-401).
  - `PUT /api/facturas/{id}`: reemplaza cabecera y detalles de un BORRADOR vaciando la colección administrada; recalcula; auditoría de modificación; límites 255/256 de descripción; 409 si no es BORRADOR; 404 si no existe (US-402).
  - `PATCH /api/facturas/{id}/estado`: emitir BORRADOR→EMITIDA (exige detalles, número siguiente por punto de venta desde 1, CAE 14 dígitos, vencimiento +10 días, resultado `A`); anular EMITIDA→ANULADA (fecha y baja lógica con usuario/fecha, no elimina); 409 en transiciones inválidas; observación no vacía reemplaza la existente (US-403).
  - `DELETE /api/facturas/{id}` → 204; borra físicamente factura y detalles, catálogos y cliente permanecen; 409/404 (US-404).
  - Cálculo monetario `BigDecimal` (neto sin redondear → IVA sin redondear → redondeo por separado) con control round-trip BigDecimal↔double (sin `Math.round`); importe no representable → 409 sin persistencia parcial (DD-03/04, RN-IM-01..09).
  - Validación y errores: 400 con `errores` por campo; JSON malformado/incompatible/ausente → 400 legible sin detalles del parser; referencias inexistentes → 404 (US-405).
  - Concurrencia: bloqueo pesimista de cabecera antes de editar/cambiar estado/borrar y del punto de venta al emitir; unique (punto de venta, número); rollback completo; emisiones concurrentes sin números duplicados (US-406, RN-CC-01..05, RN-NU-02).
  - Auditoría: interceptor `X-Usuario` con fallback `sistema`, contexto limpiado al terminar, callbacks de alta/modificación, baja al anular (US-407, RN-AU-01..07).
  - `DataLoader` integrado vía `FacturaService` (reemplaza la versión provisional de C-02, DD-11).
  - Tests: creación/reemplazo del agregado, huérfanos, rollback, límites 255/256 en POST y PUT, cantidad/bonificación inválidas, referencias inexistentes, redondeos y precisión, todas las transiciones permitidas y rechazadas, auditoría, emisiones concurrentes (hilos reales; distinguir H2 vs PostgreSQL, SU-08).
  - `requests.http` ampliado e informe `docs/etapa-4.md` con decisiones de cálculo, transacciones y bloqueos.
  - Nota de checkpoints: por ser dominio de facturación/dinero/auditoría, tratar cálculo monetario, numeración/CAE y bloqueos con puntos de revisión explícitos del usuario aunque el nivel sea MEDIO.
- **Dependencias**: `C-04`
- **Governance**: MEDIO (lógica de negocio con estado y concurrencia; implementar en pasos con checkpoints y decisiones superficiadas)
- **Semilla** (DD-13, adaptar detrás de un test en rojo y declarar en el informe): `api-facturas/service/FacturaService.java` (alta, edición, baja), `dto/FacturaRequestDTO.java` y `FacturaDetalleRequestDTO.java`. No existen en la semilla y se escriben desde cero: PATCH de estado, bloqueos pesimistas, numeración, CAE, auditoría `X-Usuario`, control de ida y vuelta a `double`.
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 4 (US-401..407)
  - `knowledge-base/05_reglas_de_negocio.md` §Cálculo de importes (RN-IM), §Estados (RN-ES), §Numeración (RN-NU), §CAE (RN-CA), §Concurrencia (RN-CC), §Auditoría (RN-AU), §Validación (RN-VA)
  - `knowledge-base/07_flujos_principales.md` §Máquina de estados, §E4 Flujo 4.1..4.7
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-03, DD-04, DD-05, DD-11, SU-03, SU-06
  - `knowledge-base/10_preguntas_abiertas.md` IN-01, IN-08, IN-09

---

## FASE 4 — Interfaz (Semanas 9-10, 20 % del 70 %)

### [C-06] `etapa-5-frontend-typescript`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Cliente HTTP TypeScript estricto (ES2020, sin framework): interfaces de DTO; `FacturaService` con consultas, catálogos, POST/PUT/PATCH estado/DELETE y URLs de reporte; base de API desde `<meta name="api-base-url">`; header `X-Usuario: sistema`; 204 sin parsear JSON; `ApiError` con estado y detalles; error no JSON → mensaje de respaldo; validaciones por campo visibles (US-501, RN-FE-01..05).
  - Consulta/filtros/listado: catálogos en selectores, filtros por fechas, estado y monto mínimo (>= 0 en el formulario), tabla, cantidad de coincidencias, mensaje sin datos, formato de moneda (PES→ARS, DOL→USD, otro código visible) (US-502).
  - Formulario de cabecera y renglones: agregar/quitar, selección lista–artículo, validación de cantidad/bonificación/descripción, observaciones <= 500, precio de catálogo y total neto estimado sin IVA, no envía precio ni importes; crear (POST), cargar y editar (PUT), cancelar edición, conservar valores ante error (US-503).
  - Acciones por estado: emitir, anular (motivo opcional; cancelar diálogo no envía PATCH), eliminar con confirmación, enlaces PDF y Excel con filtros vigentes, TXT solo por API/URL (US-504).
  - Consistencia/accesibilidad: bloqueo de doble envío con liberación en éxito/error, descarte de respuestas obsoletas (búsqueda y edición), `textContent` (sin `innerHTML` con datos), región `role="status"` + `aria-live="polite"` + `aria-busy`, éxito se limpia a los 6 s, errores persisten (US-505, RN-FE-12..15).
  - Pruebas jsdom con promesas controladas: 204, errores JSON/no JSON, validaciones, texto que parece HTML, doble envío, conservación tras error, respuestas en orden inverso, cancelación de edición y de anulación (US-506).
  - Recorrido manual completo en navegador (jsdom no lo sustituye): crear-editar-emitir-anular, eliminación de un borrador, descargas de ejemplo; evidencia en `docs/etapa-5.md` junto con límites de la simulación.
- **Dependencias**: `C-05`
- **Governance**: MEDIO (UI con estado y control de carreras; sin lógica monetaria en cliente más allá de la estimación)
- **Semilla** (DD-13, adaptar detrás de un test en rojo y declarar en el informe): `api-facturas/frontend/src/` (`app.ts`, `FacturaService.ts`, `models/factura.dto.ts`) e `index.html`. Adaptar: `ApiError`, 204 sin JSON, respuestas obsoletas, bloqueo durante guardado, `textContent`, región `aria-live`.
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 5 (US-501..506), §Entrega final y defensa (US-601/602)
  - `knowledge-base/05_reglas_de_negocio.md` §Frontend (RN-FE-01..16)
  - `knowledge-base/07_flujos_principales.md` §E5 Flujo 5.1..5.5, §Final Flujo F.1
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend)
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-10 y `knowledge-base/11_metodologia_evaluacion_y_defensa.md` §Entrega final, §Defensa

---

## Resumen

| ID | Change | Etapa | Peso | Dependencias | Governance | Tag |
|----|--------|-------|------|--------------|------------|-----|
| C-01 | `etapa-0-puesta-en-marcha` | 0 | sin nota | — | ALTO | (`etapa-0` interno) |
| C-02 | `etapa-1-persistencia-jpa` | 1 | 20 % | C-01 | ALTO | `etapa-1` |
| C-03 | `etapa-2-lombok-dto-jpql-reportes` | 2 | 20 % | C-02 | MEDIO | `etapa-2` |
| C-04 | `etapa-3-api-rest-lectura` | 3 | 15 % | C-03 | MEDIO | `etapa-3` |
| C-05 | `etapa-4-escritura-estados-concurrencia` | 4 | 25 % | C-04 | MEDIO | `etapa-4` |
| C-06 | `etapa-5-frontend-typescript` | 5 | 20 % | C-05 | MEDIO | `etapa-5` |

Primer change recomendado: `C-01` (`etapa-0-puesta-en-marcha`). Arrancar con `/opsx:propose etapa-0-puesta-en-marcha`.
