# Flujos Principales

Cada flujo se documenta extremo a extremo y se agrupa por la etapa en que nace (E0..E5). Los flujos de etapas posteriores **reutilizan** los anteriores.

## Máquina de estados de la factura

```
            POST                    PATCH {estado: EMITIDA}            PATCH {estado: ANULADA}
  (nada) ----------> BORRADOR ------------------------------> EMITIDA ------------------------> ANULADA
                      |   ^ PUT (reemplaza cabecera+detalles)    (numero + CAE; consulta/anula; no se edita ni borra)   (conserva comprobante, baja lógica)
                      |   |
                      +---+
                      +-- DELETE (física) --> (desaparece; catálogos y cliente permanecen)
Prohibido: ANULADA/EMITIDA -> BORRADOR; BORRADOR -> ANULADA; editar/borrar fuera de BORRADOR  ==> 409
```

---

## E0 — Flujo 0.1: Arranque reproducible
**Disparador**: clonar repo / nueva máquina. **Actor**: estudiante.
1. `java -version` (debe ser 17; en esta máquina usar JDK 17 de `C:\Users\zbook\.jdks\ms-17.0.20.1`, no el 26 por defecto).
2. `gradlew.bat bootRun` (Windows) / `./gradlew bootRun` → escucha en 8080; Hibernate crea tablas (H2 en memoria).
3. Abrir `/h2-console`: Driver `org.h2.Driver`, URL `jdbc:h2:mem:facturacion`, usuario `sa`, sin password; `SHOW TABLES;`.
4. En otra terminal: `cd frontend && npm ci && npm run dev` → 5173 (compila TS una vez y sirve; no recompila solo, usar `npm run watch`).
5. `./gradlew test bootJar` → `build/libs/facturacion-arca-1.0.0.jar`.
**Casos de error**: puerto ocupado → adaptar puerto (o publicación del contenedor + URL en PostgreSQL); Gradle no encuentra Java → revisar `JAVA_HOME`; los datos se pierden al detener el proceso (H2 en memoria).

## E0 — Flujo 0.2: Perfil PostgreSQL
1. `docker compose up -d db` (PostgreSQL 16, puerto 5432, db/user/pass `facturacion`, volumen `pgdata`).
2. `gradlew.bat bootRun --args="--spring.profiles.active=postgres"` (consola H2 deshabilitada).
3. En Windows, si PostgreSQL rechaza `America/Buenos_Aires`: `-Duser.timezone=America/Argentina/Buenos_Aires` al ejecutar la JVM.
**Errores**: variables `SPRING_PROFILES_ACTIVE`/`SPRING_DATASOURCE_*` previas pisan la configuración H2; conexión rechazada → revisar servicio `db` y puerto.

---

## E1 — Flujo 1.1: Persistir una factura con detalles (prueba de persistencia)
**Disparador**: test de integración (`@DataJpaTest` o equivalente). **Actor**: estudiante (TDD).
1. Preparar contexto de auditoría (`UsuarioActual.set(usuario)`) y catálogos.
2. Crear `FacturaVenta` + `agregarDetalle` x2 (ambos extremos).
3. `repository.save(factura)` → cascada inserta detalles; `@PrePersist` completa auditoría.
4. `flush()` + `clear()` del contexto de persistencia; releer.
5. Verificar: 2 detalles, `detalle.factura` apunta a la cabecera, cliente null OK, catálogos intactos.
6. Quitar un detalle de la colección → `orphanRemoval` lo borra; eliminar la factura → detalles se van, catálogos quedan.
**Errores a provocar con datos reales**: duplicar (`punto_venta`,`numero`), duplicar usuario, nulos en campos obligatorios.

## E1 — Flujo 1.2: Carga inicial (`DataLoader`)
1. Arranque (perfil distinto de `test`) → si hay usuarios, no hace nada.
2. Si no: crea `sistema`, `acortez`, `mriquero`, catálogos y clientes; fija `UsuarioActual`; crea 6 facturas (versión final: vía `FacturaService`, y cambia estados con él); limpia el contexto en `finally`.

---

## E2 — Flujo 2.1: Consulta de reporte por proyección JPQL
1. `FacturaReporteRepositoryImpl` (con `EntityManager`) arma JPQL dinámico con parámetros opcionales.
2. `SELECT new ...FacturaReporteDTO(id, numero, fecha, COALESCE(cliente.denominacion,'Consumidor Final'), condicionIva, puntoVenta, total, COUNT(detalles), estado, moneda)` con `LEFT JOIN` cliente/detalles y joins a los demás; `GROUP BY`; `ORDER BY fecha DESC, id DESC`.
3. Hibernate invoca el constructor con tipos `Long`, `Double`, `Long`, `EstadoFactura`; el DTO convierte el estado a texto.
**Errores**: orden/tipo del constructor incorrecto → falla al armar la consulta/arranque.

## E2 — Flujo 2.2: Generar los tres reportes
**Disparador**: `ReporteFacturasService.generarX(List<FacturaReporteDTO>)`.
- **PDF**: valida que cada texto tenga glifo en la fuente → si no, **excepción explícita** (sin sustituir). Dibuja título, fecha, cantidad, tabla de 9 columnas con encabezado repetido, o mensaje "sin resultados"; totales de EMITIDA por moneda. Devuelve `byte[]`.
- **XLSX**: hoja `Facturas`, celdas tipadas, `SUMIFS` por (estado=EMITIDA, moneda), evalúa fórmulas, recálculo habilitado.
- **TXT**: escapa `\` primero, luego TAB/CR/LF; une 9 columnas con TAB; una línea por factura; UTF-8; sin totales.
**Verificación**: pruebas (vacío, multimoneda, estados mezclados, caracteres especiales, multipágina, glifo no soportado, round-trip del TXT) + **revisión visual del PDF**.

---

## E3 — Flujo 3.1: Listar con filtros
```
Cliente --GET /api/facturas?estado=%20emitida%20&montoMinimo=100--> Controller
Controller -> FacturaFiltrosDTO -> Service (valida: monto finito, fechas ordenadas, estado normalizado)
Service -> Repository (JPQL) -> List<FacturaReporteDTO> -> 200 JSON (o [] vacío)
```
Errores: estado desconocido / fechas invertidas / monto no finito / tipo de parámetro inválido → **400 ProblemDetail**.

## E3 — Flujo 3.2: Detalle de una factura
`GET /api/facturas/{id}` → servicio (`@Transactional(readOnly=true)`) carga factura con detalles y asociaciones necesarias → construye `FacturaDetalladaDTO` dentro de la transacción → 200; id inexistente → **404**.

## E3 — Flujo 3.3: Descargar reporte
`GET /api/facturas/reporte?formato=xlsx&estado=EMITIDA` → mismos filtros → consulta → `ReporteFacturasService` → `ResponseEntity<byte[]>` con `Content-Type` y `Content-Disposition: attachment; filename=...`. `formato=doc` → **400**. PDF con glifo no soportado → error explícito (400 en la referencia).

## E3 — Flujo 3.4: Catálogos
`GET /api/catalogos/lista-precio-articulos` → `[OpcionDTO{id, etiqueta, valor(precio), codigoMoneda:null}]`. El `id` devuelto es el que luego se envía como `listaPrecioArticuloId`.

---

## E4 — Flujo 4.1: Crear borrador
**Disparador**: `POST /api/facturas` (JSON) con header opcional `X-Usuario`.
1. `UsuarioActualInterceptor` resuelve usuario (fallback `sistema`) → `UsuarioActual`.
2. Controller recibe `@Valid FacturaRequestDTO` → falla → 400 con `errores`.
3. Service (`@Transactional`): resuelve ids (punto de venta, condición IVA, moneda, cliente opcional, cada `ListaPrecioArticulo`) → 404 si falta alguno.
4. Por renglón: precio de lista, alícuota (artículo o 21 %), cálculo BigDecimal (neto sin redondear → IVA HALF_UP → neto HALF_UP → subtotal); control double round-trip → 409 si no representable.
5. `agregarDetalle`; suma total; `saldo = total - cobrado`; estado BORRADOR; sin número ni CAE.
6. `save` → callbacks de auditoría → DTO → **201** + `Location: /api/facturas/{id}` + `FacturaReporteDTO`.
7. `afterCompletion` limpia el `ThreadLocal`.
**Errores**: JSON malformado → 400 legible; referencia inexistente → 404; descripción de 256 caracteres → 400; fallo en cualquier punto → rollback total.

## E4 — Flujo 4.2: Editar borrador (PUT)
1. Service: `SELECT ... FOR UPDATE` (bloqueo pesimista) sobre la cabecera → si no existe 404.
2. Si estado != BORRADOR → **409**.
3. Resuelve referencias, `detalles.clear()` (misma instancia), agrega nuevos, recalcula, `registrarModificacion()` (siempre), responde 200.
**Concurrencia**: si otra transacción emite la factura primero, el bloqueo hace que la edición vea EMITIDA y reciba 409 (no modifica lo emitido).

## E4 — Flujo 4.3: Emitir
**Disparador**: `PATCH /api/facturas/{id}/estado` `{"estado":"EMITIDA","observaciones":"..."}`.
1. Bloquea cabecera; valida transición BORRADOR→EMITIDA (si no, 409) y que haya detalles (si no, 409).
2. Bloquea punto de venta (`PESSIMISTIC_WRITE`) → `máximo(numero)+1` (1 si no hay) → asigna número.
3. Genera CAE de 14 dígitos, `caeFechaVencimiento = hoy + 10`, `resultadoAfip = "A"`, estado EMITIDA.
4. Si observación no vacía, reemplaza `observaciones`.
5. 200 `FacturaReporteDTO`.
```
Tx A (emitir F1, PV 1) ──lock PV1──> max=0 -> numero 1 -> commit
Tx B (emitir F2, PV 1) ──espera lock PV1──────────────> max=1 -> numero 2
```
La unicidad (`punto_venta`,`numero`) es la red de seguridad si faltara el bloqueo.

## E4 — Flujo 4.4: Anular
`PATCH ... {"estado":"ANULADA","observaciones":"Motivo"}`: bloquea cabecera; solo desde EMITIDA (BORRADOR→ANULADA y ANULADA→ANULADA = 409); estado ANULADA, `fechaAnulacion = hoy`, `darDeBaja()` (usuario y fecha de baja); no borra el comprobante.

## E4 — Flujo 4.5: Eliminar borrador
`DELETE /api/facturas/{id}`: bloquea cabecera; solo BORRADOR (si no, 409); elimina factura y detalles en cascada; catálogos y cliente intactos; **204 sin cuerpo**.

## E4 — Flujo 4.6: Auditoría por request
`X-Usuario: acortez` → existe → actor `acortez`. Ausente/vacío/inexistente → `sistema`. Quedan `usuarioCarga/fechaAlta` (alta), `usuarioModificacion/fechaModificacion` (alta, cada PUT), `usuarioBaja/fechaBaja` (anulación).

## E4 — Flujo 4.7: Reversión por importe no representable
Un importe cuyo `double` no es finito o cuyo `BigDecimal.valueOf(double)` difiere del decimal calculado → excepción de negocio → **409** + rollback; no queda ninguna fila parcial.

---

## E5 — Flujo 5.1: Carga inicial de la pantalla
1. `app.ts` lee `meta[name=api-base-url]`.
2. `FacturaService` pide los 5 catálogos y el listado en paralelo (con `X-Usuario: sistema`).
3. Renderiza selectores, tabla (con `textContent`), contador de coincidencias, enlaces PDF/Excel con los filtros vigentes; si no hay datos, mensaje.

## E5 — Flujo 5.2: Buscar con filtros (anti-carrera)
```
Usuario filtra (req #1) ──────────────────────────► respuesta tardía #1
Usuario filtra de nuevo (req #2) ─► respuesta #2 (rápida)
Regla: solo se aplica la respuesta cuyo id == último id emitido; #1 se descarta.
Tabla y enlaces de exportación se actualizan juntos.
```

## E5 — Flujo 5.3: Crear/editar borrador
1. Formulario: cabecera + renglones (agregar/quitar); valida cantidad > 0 finita, bonificación 0..100, descripción ≤ 255, observaciones ≤ 500; muestra precio de catálogo y total neto estimado.
2. Enviar → bloquea botones, `aria-busy=true`; request **sin** precio ni importes.
3. Éxito → mensaje en región `role="status"` (se limpia a los 6 s), recarga lista. Error → conserva valores del formulario, muestra errores de campo, libera el bloqueo.
4. Editar: cargar `GET /{id}` en el formulario; PUT; **cancelar** descarta; si una respuesta tardía llega tras cancelar o cambiar de edición, se ignora.

## E5 — Flujo 5.4: Emitir / anular / eliminar
- Emitir: PATCH EMITIDA → refresca fila.
- Anular: diálogo con motivo opcional; cancelar → no se envía PATCH; aceptar → PATCH ANULADA (+ observaciones).
- Eliminar: confirmación; DELETE → 204 resuelto sin leer JSON → quitar fila.
- Errores 409/404/400 → `ApiError` → mensaje visible hasta una acción posterior.

## E5 — Flujo 5.5: Descargas
Enlaces PDF y Excel construidos por `FacturaService.urlReporte(filtros, formato)`; TXT disponible por API/URL (sin botón).

---

## Final — Flujo F.1: Demostración de defensa (5 min)
Consultar con filtros → crear borrador → editarlo → emitir (mostrar número y CAE) → anular otro emitido → eliminar otro borrador → descargar PDF/Excel → provocar un error controlado (p. ej. editar una emitida → 409 o descripción de 256 → 400).
