# Proposal

## Why

La etapa 3 dejó una API de solo lectura: hoy no se puede crear, editar, emitir, anular ni eliminar una factura, los importes del `DataLoader` son literales escritos a mano y nada protege la numeración ni el estado frente a operaciones simultáneas. La Etapa 4 de la consigna (semanas 7 y 8, **25 %** de la nota de etapas, el change de mayor peso y riesgo) exige escritura validada, cálculo monetario en el servidor, transiciones BORRADOR → EMITIDA → ANULADA, numeración y CAE simulados, bloqueos pesimistas, auditoría por `X-Usuario` y evidencia de concurrencia distinguiendo H2 de PostgreSQL. El frontend de la etapa 5 consume exactamente estos contratos.

## What Changes

- **`POST /api/facturas`** → `201 Created` + `Location` + `FacturaReporteDTO`: crea un BORRADOR sin número ni CAE; cliente opcional («Consumidor Final»); fecha de emisión opcional (hoy por defecto, según el reloj inyectado); precio unitario tomado de la lista–artículo, nunca del request.
- **`PUT /api/facturas/{id}`** → `200` + `FacturaReporteDTO`: reemplaza cabecera y renglones de un BORRADOR vaciando la colección administrada, recalcula y registra la modificación de auditoría siempre; `409` si no es BORRADOR; `404` si no existe.
- **`PATCH /api/facturas/{id}/estado`** → `200` + `FacturaReporteDTO`: emitir (BORRADOR → EMITIDA: exige renglones, número siguiente por punto de venta desde 1, CAE de 14 dígitos, vencimiento hoy + 10 días, resultado `A`) y anular (EMITIDA → ANULADA: fecha de anulación y baja lógica, sin borrar). Toda otra transición → `409`. Una observación no vacía reemplaza la existente. Estado desconocido → `400` (IN-01, **pendiente de confirmar con el docente**, no bloqueante).
- **`DELETE /api/facturas/{id}`** → `204` sin cuerpo: borra físicamente un BORRADOR y sus renglones; catálogos y cliente permanecen; `409`/`404`.
- **Cálculo monetario** en el servicio con `BigDecimal`: neto sin redondear = cantidad × precio × (1 − bonificación/100); IVA sobre ese neto con la alícuota del artículo o el 21 % configurado; neto e IVA redondeados por separado a 2 decimales `HALF_UP`; subtotal = suma de los redondeados; total = suma de subtotales; saldo = total − cobrado (0). **Control de ida y vuelta** `BigDecimal` → `double` (finito y `BigDecimal.valueOf(d)` igual al calculado) antes de guardar; un importe no representable → `409` sin persistencia parcial. Sin `Math.round` ni aritmética `double`.
- **Validación**: DTO de entrada con Bean Validation (observaciones ≤ 500, descripción ≤ 255 —255 pasa, 256 → `400`—, cantidad > 0, bonificación opcional 0..100, al menos un renglón y ninguno nulo); `400` con mapa `errores` por campo; JSON malformado, incompatible o ausente → `400` legible sin detalles del analizador; referencias inexistentes → `404`.
- **Concurrencia**: `PESSIMISTIC_WRITE` sobre la cabecera antes de editar, cambiar estado o borrar, y sobre el punto de venta antes de calcular el próximo número; unicidad (punto de venta, número) como última defensa; rollback completo. Suite de concurrencia con hilos reales que corre sobre H2 en `./gradlew test` y, **a demanda**, contra el PostgreSQL 16.9 portátil (puerto 5433) mediante una tarea Gradle separada, nunca incluida en `test`/`check`.
- **Auditoría**: interceptor que resuelve `X-Usuario` (ausente, vacío o desconocido → `sistema`) y **siempre** limpia el contexto al terminar; modificación completada en cada actualización y explícita en cada PUT; baja lógica (usuario y fecha) al anular. `X-Usuario` declara un actor: no autentica.
- **`DataLoader` integrado**: las seis facturas (3 EMITIDA, 1 ANULADA, 2 BORRADOR) se crean y cambian de estado mediante `FacturaService`; usuarios y catálogos siguen por repositorios; se conserva `@Profile("!test")`, la omisión si hay usuarios y la limpieza del contexto en `finally` (DD-11).
- **CORS**: las rutas `/api` pasan a admitir `POST`, `PUT`, `PATCH` y `DELETE`, los encabezados `Content-Type` y `X-Usuario`, y exponen `Location` además de `Content-Disposition`.
- **`requests.http`** ampliado, **`docs/etapa-4.md`** y README; prueba manual con la app levantada.
- **Semilla (DD-13)**: `api-facturas/service/FacturaService.java` (alta, edición, baja) y los DTO de request se adaptan detrás de un test en rojo; el cálculo de la semilla (precio con IVA incluido dividido por 1,21 y suma en `double`) **se descarta** porque contradice la consigna. Inventario en `design.md`.

### Puntos de control del estudiante (governance)

El dominio es facturación, dinero y auditoría: aunque el nivel es MEDIO, el apply se detiene y muestra al estudiante el resultado en cuatro **CHECKPOINTS** obligatorios, numerados en orden de ejecución: (1) cálculo monetario y control de ida y vuelta; (2) interceptor de auditoría y callbacks (va antes que las escrituras porque toda alta necesita el usuario del contexto); (3) numeración y generación del CAE; (4) bloqueos pesimistas y pruebas de concurrencia (H2 y PostgreSQL). No avanza al grupo siguiente sin su conformidad.

## Capabilities

### New Capabilities
- `api-borradores-factura`: creación, reemplazo y eliminación HTTP de facturas en BORRADOR, con sus códigos, cabeceras y cuerpos de respuesta.
- `validacion-request-factura`: reglas de validación de los cuerpos de alta, edición y cambio de estado, y resolución de las referencias a catálogos.
- `calculo-importes-factura`: cálculo decimal de neto, IVA, subtotal, total y saldo, y el control de representabilidad antes de persistir en campos `double`.
- `ciclo-de-vida-factura`: transiciones de estado permitidas y rechazadas, emisión con numeración por punto de venta y CAE simulado, y anulación.
- `concurrencia-facturas`: bloqueos de escritura, atomicidad, emisiones simultáneas sin números duplicados y evidencia por motor de base de datos.
- `auditoria-por-request`: resolución del usuario de auditoría desde `X-Usuario` y ciclo de vida del contexto por petición.

### Modified Capabilities
- `auditoria-entidades`: se agregan la modificación completada desde el contexto en cada actualización y la baja lógica con usuario y fecha.
- `datos-de-ejemplo`: las seis facturas se crean y cambian de estado mediante el servicio, con importes calculados, numeración real, CAE generado y vencimiento desde la fecha actual.
- `api-errores`: los 400 de entrada incluyen validación por campo y JSON ilegible; el 404 cubre referencias inexistentes; aparece el 409 de conflicto de negocio; el ejemplo de 405 deja de usar rutas que ahora existen.
- `api-cors`: los orígenes admitidos pasan de solo lectura a lectura y escritura, con los encabezados que usa el frontend.

## Impact

- **Código nuevo**: `dto/FacturaRequestDTO`, `dto/FacturaDetalleRequestDTO`, `dto/CambioEstadoRequestDTO`; `service/CalculadoraImportes`, `service/ImporteDouble`, `service/GeneradorCae` (+ implementación aleatoria); `exception/ReglaNegocioException`, `exception/ImporteNoRepresentableException`; `config/UsuarioActualInterceptor`, `config/WebConfig`, `config/FacturacionProperties`; consultas con bloqueo en `FacturaVentaRepository` y `PuntoVentaRepository`, `UsuarioRepository.findByUsuario`; pruebas unitarias, `@WebMvcTest`, integración y concurrencia.
- **Código modificado**: `FacturaService` (escritura), `FacturaRestController` (POST/PUT/PATCH/DELETE), `ApiExceptionHandler` (400 por campo, JSON ilegible, 409), `EstadoFactura` (transiciones), `AuditoriaApp` (`@PreUpdate`, modificación explícita, baja), `CorsConfig`, `DataLoader`, `application.yml` (`facturacion.iva-por-defecto`, `facturacion.usuario-por-defecto`), `build.gradle` (solo la tarea `testPostgres`, sin dependencias ni versiones nuevas).
- **Pruebas existentes que cambian**: `DataLoaderTest` y `DataLoaderFalloTest` (contexto que incluye el servicio; vencimiento = hoy + 10), los casos de 405 que usaban `POST`/`DELETE /api/facturas`, y las cuatro clases `@WebMvcTest` (simulan `UsuarioRepository` por el interceptor). Detalle en `design.md` D15.
- **API**: primeras operaciones de escritura; los `GET` existentes no cambian de contrato.
- **Infraestructura**: PostgreSQL 16.9 portátil fuera del repo (`C:\Users\zbook\DesarrolloSoftware\pg16-portatil`, puerto 5433), solo para la corrida de concurrencia a demanda; sin Docker.
- **Governance**: MEDIO con 4 checkpoints obligatorios del estudiante.
- **Fuera de alcance**: login o autorización, cobros (cobrado siempre 0), paginación, CRUD de catálogos, conversión de monedas, integración fiscal real, frontend (C-06) y cualquier comando Git que cambie estado.
