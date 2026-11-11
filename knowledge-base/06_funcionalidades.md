# Funcionalidades

Organizadas por **épica = etapa** de la consigna (un change de OpenSpec por etapa) y luego por historia de usuario (US-NNN). Cada épica cierra con sus **criterios de verificación** (textuales de la consigna) y su **entregable** (tag + informe). Peso de cada etapa dentro del 70 % de etapas: E1 20 %, E2 20 %, E3 15 %, E4 25 %, E5 20 %. Etapa 0 no tiene nota.

Convención TDD (Martín): cada criterio de aceptación se convierte primero en una prueba roja.

---

## Épica 0 — Puesta en marcha (Semana 1, sin nota; sin tag)

### US-001 — Arrancar el backend
**Como** estudiante **quiero** configurar y ejecutar el backend con Java 17 y el wrapper Gradle 8.10.2 **para** tener una base reproducible.
**Criterios de aceptación**:
- [ ] `java -version` verificado (17); wrapper Gradle 8.10.2 versionado.
- [ ] Proyecto `facturacion-arca`, grupo `ar.edu.utn.frm`, paquete `ar.edu.utn.frm.facturacion`, Spring Boot 3.3.5 con Web, Data JPA, Validation, Lombok, H2, PostgreSQL y dependencias de prueba; OpenPDF 2.0.3 y Apache POI 5.3.0.
- [ ] Paquetes `model, dto, repository, service, controller, exception, config, report`.
- [ ] `application.yml`: H2 `jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1`, `sa`/vacía, consola `/h2-console`, `ddl-auto: update`, `show-sql` y `format_sql`, `open-in-view: false`.
- [ ] `./gradlew bootRun` levanta en 8080 y permite conectar a la consola H2; los datos no sobreviven al reinicio.
**Reglas**: RN-CF-02, RN-CF-03.

### US-002 — Arrancar el frontend
**Como** estudiante **quiero** compilar y servir el frontend **para** verificar la conexión con la API.
- [ ] `npm ci` y `npm run dev` (Node >= 18.13); servidor en 5173; `<meta name="api-base-url">` verificado.

### US-003 — Repositorio y perfil PostgreSQL
- [ ] Versionar código, configuración y wrapper; excluir artefactos (`build/`, `.gradle/`, `node_modules/`, `dist/`, IDE) en `.gitignore`.
- [ ] README con comandos de arranque, pruebas y compilación.
- [ ] Perfil `postgres` + `docker-compose.yml` (PostgreSQL 16, db/user/pass `facturacion`, puerto 5432); declarar qué motor se probó.

**Verificación etapa 0 (consigna)**: backend inicia y permite conectar a H2 con la URL configurada; `./gradlew test bootJar` termina bien y genera `build/libs/facturacion-arca-1.0.0.jar`; el frontend compila y se sirve por HTTP; el estudiante reproduce el arranque siguiendo el README.
**Entregable**: repo preparado (sin tag ni informe obligatorio).

---

## Épica 1 — Persistencia con JPA/Hibernate (Semanas 2-3, 20 %)

### US-101 — Superclases y auditoría mapeadas
**Como** estudiante **quiero** mapear `EntityId` y `AuditoriaApp` **para** compartir id y auditoría sin tablas propias.
- [ ] `@MappedSuperclass`; id IDENTITY; fechas de auditoría `LocalDateTime`; usuarios `ManyToOne LAZY`; alta y usuario de carga `updatable=false`.
**Reglas**: RN-PE-01..04.

### US-102 — Mapear las 14 entidades
- [ ] Tablas, campos obligatorios y longitudes del modelo; unicidades (usuario, número de punto de venta, lista/artículo, punto de venta/número de factura); `numero` nulo en borrador.
- [ ] `EstadoFactura` con `EnumType.STRING`.
- [ ] Asociaciones obligatorias/opcionales según el modelo; Cliente con contacto/domicilio (cascade + orphanRemoval).
**Reglas**: RN-PE-05, 06, 10, 11, 12.

### US-103 — Agregado Factura–Detalles
- [ ] `agregarDetalle` mantiene ambos extremos; colección inicializada; reemplazo vía `clear()` sin sustituir instancia; sin cascada a catálogos.
**Reglas**: RN-PE-07, 08, 09, 13.

### US-104 — Repositorios
- [ ] Solo los necesarios; ninguno para detalles/contacto/domicilio.

### US-105 — DataLoader de ejemplo
- [ ] `CommandLineRunner` transaccional, excluido de `test`, omite si hay usuarios; usuarios `sistema` y `acortez`, catálogos y seis facturas; limpia el contexto de auditoría. Versión integrada vía `FacturaService` (se completa al llegar a etapa 4).
**Reglas**: RN-PE-14, RN-AU-08.

### US-106 — Pruebas de persistencia
- [ ] Guardar factura con 2 detalles, limpiar el contexto de persistencia, releer; verificar relación inversa, cliente opcional, catálogos conservados; eliminación de huérfanos; restricciones con datos que realmente las violan (unicidad, nulabilidad).

**Verificación etapa 1 (consigna)**: modelo y repositorios compilables, pruebas de persistencia en verde.
**Entregable**: tag `etapa-1`; README actualizado; `docs/etapa-1.md` con un ejemplo de SQL generado, explicación de relaciones, por qué la factura es raíz de agregado y qué datos permanecen al eliminar un borrador.

---

## Épica 2 — Lombok, DTO, consultas JPQL y reportes (Semanas 4-5, 20 %)

### US-201 — Lombok sin recursión
- [ ] `compileOnly` + `annotationProcessor`; getters/setters/constructores necesarios; sin `@Data` indiscriminado; cabecera excluye `detalles`, detalle excluye `factura`; exclusiones coherentes con la herencia.
- [ ] Informe: por qué incluir ambos lados en métodos generados recursiona.
**Reglas**: RN-GE-03.

### US-202 — Proyección JPQL a `FacturaReporteDTO`
- [ ] Campos: id, numeroFactura (nulo en borrador), fechaEmision, clienteDenominacion, condicionIva, puntoVentaDescripcion, importeTotal, cantidadItems, estado (texto), codigoMoneda.
- [ ] Fragmento de repositorio con `EntityManager` y `SELECT new`; constructor con `Long` para COUNT y `EstadoFactura`.
- [ ] `LEFT JOIN` cliente y detalles; `COALESCE` «Consumidor Final»; joins condición IVA, punto de venta, moneda; `GROUP BY`; orden `fechaEmision DESC, id DESC`; conserva facturas sin cliente y cabeceras sin detalles.
- [ ] Pruebas: cantidad de ítems, cliente opcional, número nulo, estado, moneda.
**Reglas**: RN-FI-05, RN-FI-08, RN-FI-09.

### US-203 — Exportar PDF
- [ ] OpenPDF, A4 apaisado, título, fecha de generación, cantidad de registros, encabezados repetidos, mensaje sin resultados, 9 columnas, guion en número de borrador, fuente Noto Sans CJK SC incrustada (Identity-H, licencia OFL), glifo no soportado → error explícito, totales solo EMITIDA por moneda.
**Reglas**: RN-RE-01..05, 07, 11.

### US-204 — Exportar Excel
- [ ] Hoja `Facturas`, encabezados destacados, fechas/números como celdas tipadas, formatos, autoajuste, totales `SUMIFS` por EMITIDA y moneda, fórmulas evaluadas y recálculo habilitado; moneda sin emitidas → 0.
**Reglas**: RN-RE-02, 03, 06, 07.

### US-205 — Exportar TXT
- [ ] UTF-8, tabuladores, una fila por factura, 9 columnas, escapes reversibles (`\\`, `\t`, `\r`, `\n`), fechas ISO, importes 2 decimales con punto, sin pie de totales.
**Reglas**: RN-RE-02, 03, 08, 09.

### US-206 — `ReporteFacturasService`
- [ ] Métodos que reciben DTOs y devuelven bytes; no dependen del DataLoader ni de carpetas locales.
- [ ] Pruebas con lista vacía, múltiples monedas, estados mezclados, caracteres especiales; PDF multipágina y carácter no soportado; Excel: tipos y resultados de fórmulas; TXT: reconstruir campos escapados y recuperar el texto original.

**Verificación etapa 2 (consigna)**: proyección JPQL y exportadores implementados, pruebas en verde y muestras verificables de los tres archivos; revisión visual del PDF completo.
**Entregable**: tag `etapa-2`; `docs/etapa-2.md` explicando joins, constructor de proyección, criterio de totales por estado y moneda, y estrategia de preservación de texto.

---

## Épica 3 — API REST de lectura (Semana 6, 15 %)

### US-301 — Listado `GET /api/facturas`
- [ ] 200 con `List<FacturaReporteDTO>`; filtros opcionales; límites inclusivos; orden fecha DESC, id DESC; sin resultados = `[]`.
- [ ] Estado normalizado (trim + mayúsculas); estado desconocido, fechas invertidas, monto no finito → 400; sin mínimo cero en backend.
**Reglas**: RN-FI-01..06.

### US-302 — Detalle `GET /api/facturas/{id}`
- [ ] `FacturaDetalladaDTO` con ids y descripciones editables, estado, importes, CAE, observaciones, renglones; 404 si no existe; asociaciones resueltas en la capa de servicio con `open-in-view=false`.

### US-303 — Descarga `GET /api/facturas/reporte`
- [ ] Mismos filtros + `formato` (default pdf; `pdf`, `xlsx|excel`, `txt|tsv`); tipo de contenido correcto y `Content-Disposition: attachment`; formato inválido → 400.
**Reglas**: RN-RE-10.

### US-304 — Catálogos de lectura
- [ ] `GET /api/catalogos/{puntos-venta, clientes, condiciones-iva, tipos-moneda, lista-precio-articulos}` → `OpcionDTO`; sin altas/modificaciones/bajas.
**Reglas**: RN-CT-01, 02.

### US-305 — CORS y errores centralizados
- [ ] CORS para localhost/127.0.0.1 en 5173 y 3000; explicar por qué no autentica. `ProblemDetail` (status, title, detail, timestamp); consulta vacía ≠ inexistente.
**Reglas**: RN-CF-01, RN-ER-01..06.

**Comprobación etapa 3 (consigna)**: filtros individuales y combinados, ausencia de resultados, cliente opcional como «Consumidor Final», detalle inexistente, formatos admitidos y formato inválido; el archivo descargado corresponde a los mismos filtros.
**Entregable**: tag `etapa-3`; código de lectura; pruebas; ejemplos reproducibles en `requests.http`; `docs/etapa-3.md` justificando DTO y manejo de errores.

---

## Épica 4 — Escritura, estados y consistencia transaccional (Semanas 7-8, 25 %)

### US-401 — Crear borrador `POST /api/facturas`
- [ ] 201 + `Location` + `FacturaReporteDTO`; sin número ni CAE; cliente opcional; fecha por defecto hoy; precio tomado de `ListaPrecioArticulo`; importes calculados en servidor.
**Reglas**: RN-VA-01, 02, 04..07, RN-IM-01..09.

### US-402 — Reemplazar borrador `PUT /api/facturas/{id}`
- [ ] 200; reemplaza cabecera y detalles de un BORRADOR (vaciando la colección administrada); recalcula; registra modificación de auditoría siempre; límites 255/256 de descripción en PUT; 409 si no es BORRADOR; 404 si no existe.
**Reglas**: RN-ES-02, RN-PE-08, RN-AU-05.

### US-403 — Emitir y anular `PATCH /api/facturas/{id}/estado`
- [ ] Emitir BORRADOR → EMITIDA: exige detalles, asigna número siguiente por punto de venta (desde 1), CAE 14 dígitos, vencimiento +10 días, resultado `A`.
- [ ] Anular EMITIDA → ANULADA: fecha de anulación, baja lógica (usuario y fecha), no elimina.
- [ ] Rechazos con 409: anular un borrador, emitir una emitida/anulada, anular una anulada, volver atrás.
- [ ] Observación no vacía reemplaza la de la factura.
**Reglas**: RN-ES-01, 03..05, 07, RN-NU-01..04, RN-CA-01..04, RN-AU-06.

### US-404 — Eliminar borrador `DELETE /api/facturas/{id}`
- [ ] 204 sin cuerpo; borra físicamente la factura y sus detalles; los catálogos y el cliente permanecen; 409 si no es BORRADOR; 404 si no existe.
**Reglas**: RN-ES-02, 06, RN-PE-09.

### US-405 — Validación y errores
- [ ] 400 con `errores` por campo; JSON malformado/incompatible/ausente → 400 con mensaje legible sin detalles del parser; referencias inexistentes → 404; importe no representable → 409 sin persistencia parcial.
**Reglas**: RN-VA-05, 06, RN-IM-09, RN-ER-02..04.

### US-406 — Concurrencia y transacciones
- [ ] Bloqueo pesimista sobre cabecera antes de editar/cambiar estado/borrar; sobre el punto de venta al emitir; unicidad (punto de venta, número); rollback completo ante fallos; emisiones concurrentes sin números duplicados; una edición no ignora una emisión concurrente.
**Reglas**: RN-CC-01..05, RN-NU-02.

### US-407 — Auditoría por `X-Usuario`
- [ ] Interceptor con fallback a `sistema`; contexto limpiado al terminar; callbacks de alta/modificación; baja al anular.
**Reglas**: RN-AU-01..07.

**Comprobación etapa 4 (consigna)**: creación y reemplazo del agregado, eliminación de huérfanos, rollback, límites 255/256 de descripción en POST y PUT, cantidad y bonificación inválidas, referencias inexistentes, redondeos y precisión, todas las transiciones permitidas y rechazadas, auditoría y emisiones concurrentes; distinguir pruebas en H2 y en PostgreSQL.
**Entregable**: tag `etapa-4`; DTO de entrada, servicios, controladores y manejo central de errores; pruebas de integración y concurrencia; ejemplos HTTP; `docs/etapa-4.md` con decisiones de cálculo, transacciones y bloqueos. (Aquí el DataLoader se integra con `FacturaService`.)

---

## Épica 5 — Frontend TypeScript e integración (Semanas 9-10, 20 %)

### US-501 — Proyecto y cliente HTTP
- [ ] TypeScript estricto, ES2020, sin framework; interfaces de DTO; `FacturaService` con consultas, catálogos, POST, PUT, PATCH estado, DELETE y construcción de URLs de reporte; base de la API desde `meta`; `X-Usuario: sistema`; 204 sin JSON; `ApiError` con estado y detalles; error no JSON → mensaje de respaldo; validaciones por campo visibles.
- [ ] Scripts documentados: `npm ci`, `npm run build`, `npm run dev`, `npm test`; servido por HTTP en 5173.
**Reglas**: RN-FE-01..05.

### US-502 — Consulta, filtros y listado
- [ ] Catálogos en selectores; filtros por fechas, estado y monto mínimo (>= 0 en el formulario); tabla; cantidad de coincidencias; mensaje sin datos; importes con formato de moneda (PES→ARS, DOL→USD, otro código visible).
**Reglas**: RN-FE-06, 16.

### US-503 — Formulario de cabecera y renglones
- [ ] Agregar/quitar renglones; selección lista–artículo; cantidad, bonificación, descripción validadas; observaciones ≤ 500; muestra precio de catálogo y total neto estimado sin IVA; no envía precio ni importes.
- [ ] Crear (POST), cargar y editar (PUT), cancelar edición; conservar valores si falla.
**Reglas**: RN-FE-07..09.

### US-504 — Emitir, anular, eliminar y descargar
- [ ] Acciones según estado; confirmar eliminación; anulación con motivo opcional, cancelar el diálogo no envía PATCH; enlaces PDF y Excel con filtros vigentes; TXT solo por API/URL.
**Reglas**: RN-FE-10, 11.

### US-505 — Consistencia, accesibilidad y seguridad de la interfaz
- [ ] Bloqueo de doble envío y liberación en éxito/error; descarte de respuestas obsoletas (búsqueda y edición); lista y enlaces actualizados juntos; `textContent`; región `role="status"` `aria-live="polite"`, `aria-busy`; éxito se limpia a los 6 s; errores persisten.
**Reglas**: RN-FE-12..15.

### US-506 — Pruebas del frontend
- [ ] Cliente HTTP y DOM: 204, errores JSON y no JSON, validaciones, texto que parece HTML, doble envío, conservación tras error, respuestas en orden inverso, cancelación de edición y del diálogo de anulación; promesas controladas para reproducir carreras; más recorrido manual completo en navegador (jsdom no sustituye).

**Verificación etapa 5 (consigna)**: frontend compilable, pruebas y recorrido completo en navegador.
**Entregable**: tag `etapa-5`; instrucciones; evidencia del circuito crear-editar-emitir-anular y de la eliminación de un borrador; descargas de ejemplo; límites de la simulación; `docs/etapa-5.md`.

---

## Entrega final y defensa (Semana 11, 30 % de la nota final)

### US-601 — Entrega completa
- [ ] Repo con las cinco etapas identificables, código, configuración de ejemplo, README (instalar, iniciar backend y frontend, elegir H2/PostgreSQL, ejecutar controles), `requests.http`, pruebas e informes.
- [ ] Evidencia final: persistencia de agregados, cálculo y límites monetarios, validaciones y rollback, estados, auditoría, concurrencia, API, reportes (vacío, varias páginas, monedas y estados diferentes; totales solo EMITIDA por moneda; TXT nueve columnas con escapes; error por glifo) e interfaz; revisar visualmente los archivos; indicar qué se comprobó y en qué entorno (sin presentar una cantidad fija de pruebas como sustituto de cobertura).

### US-602 — Defensa individual (25 min)
- [ ] 5 min de demostración: consultas, creación y edición de borrador, emisión, anulación, eliminación de otro borrador, descarga de reportes y al menos un error controlado.
- [ ] 15 min de preguntas: cascadas y lado propietario, proyección DTO, transacciones y bloqueos, redondeo, códigos HTTP, auditoría, control de respuestas asíncronas.
- [ ] 5 min de cambio breve indicado por el docente.
