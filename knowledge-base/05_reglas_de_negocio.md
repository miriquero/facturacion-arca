# Reglas de Negocio

Cada regla tiene un código único `RN-{DOMINIO}-{NN}`. Origen: la consigna salvo indicación `(ref)` = solo la solución del docente (no exigido literalmente por la consigna). Etapa = etapa en la que se implementa/prueba.

Dominios: PE persistencia · IM importes · ES estados · NU numeración · CA CAE · CC concurrencia/transacciones · AU auditoría · VA validación de DTO · FI filtros · CT catálogos · ER errores HTTP · RE reportes · CF configuración/CORS · FE frontend · GE globales.

## Dominio: Persistencia y modelo (RN-PE)

- **RN-PE-01** (E1): `EntityId` y `AuditoriaApp` son `@MappedSuperclass`; no generan tablas. Hay exactamente 14 `@Entity`.
- **RN-PE-02** (E1): Id `Long` con `GenerationType.IDENTITY`.
- **RN-PE-03** (E1): Fechas de auditoría `LocalDateTime`; fechas de negocio (emisión, anulación, vencimiento CAE) `LocalDate`.
- **RN-PE-04** (E1): Referencias de auditoría a `Usuario` son `ManyToOne` **LAZY**. `fechaAlta` y `usuarioCarga` no se actualizan tras persistir (`updatable=false`).
- **RN-PE-05** (E1): `EstadoFactura` con `EnumType.STRING`; nunca ordinal.
- **RN-PE-06** (E1): Unicidades: `Usuario.usuario`; `PuntoVenta.numero`; (`lista_precio`,`articulo`) en `ListaPrecioArticulo`; (`punto_venta`,`numero`) en `FacturaVenta`. Un borrador tiene `numero = null`.
- **RN-PE-07** (E1): `FacturaVenta.detalles`: `mappedBy="factura"`, `cascade=ALL`, `orphanRemoval=true`. El detalle es el lado propietario (contiene la FK `factura_id`). Si aparece una tabla intermedia inesperada, revisar `mappedBy`.
- **RN-PE-08** (E1): `agregarDetalle` establece ambos extremos; la colección se inicializa; al reemplazar renglones se vacía la colección gestionada **sin sustituir su instancia**.
- **RN-PE-09** (E1): No se propaga persist/remove desde la factura hacia catálogos (cliente, moneda, punto de venta, artículo, lista). Eliminar un borrador no elimina catálogos ni cliente.
- **RN-PE-10** (E1): `Cliente` posee `Contacto` y `Domicilio` (OneToOne obligatorios, cascade ALL + orphanRemoval). No hay repositorios independientes para detalles, contacto o domicilio.
- **RN-PE-11** (E1): `FacturaVenta`: cliente opcional; punto de venta, condición IVA y moneda obligatorios. `FacturaVentaDetalle`: factura y `ListaPrecioArticulo` obligatorios. `Articulo`: marca y rubro opcionales.
- **RN-PE-12** (E1): Longitudes del modelo respetadas: `observaciones` 500, `descripcion` de detalle 255, `cae` 14, etc. (ver 04).
- **RN-PE-13** (E1): Una colección `detalles` puede estar vacía **mientras se construye el objeto en memoria**; un request válido de creación/edición exige al menos un renglón.
- **RN-PE-14** (E1): `DataLoader` es `CommandLineRunner` transaccional, excluido del perfil `test`, omite la carga si ya hay usuarios; incluye usuarios `sistema` y `acortez`, catálogos y seis facturas; limpia el contexto de auditoría al terminar. En la versión integrada usa `FacturaService` para crear facturas y cambiar estados.

## Dominio: Cálculo de importes y redondeo (RN-IM)

- **RN-IM-01** (E4): El cálculo se hace **en el servicio**, con `BigDecimal`, a partir de valores finitos. El cliente nunca envía precio unitario ni importes.
- **RN-IM-02** (E4): Precio unitario = `ListaPrecioArticulo.precioVenta` (lo resuelve el servidor a partir de `listaPrecioArticuloId`).
- **RN-IM-03** (E4): Alícuota = `Articulo.alicuotaIva`; si es null → `facturacion.iva-por-defecto` (21 %).
- **RN-IM-04** (E4): Neto **sin redondear** = `cantidad × precio × (1 − bonificación/100)`. La bonificación es descuento porcentual **antes del IVA**.
- **RN-IM-05** (E4): IVA se calcula **sobre el neto anterior al redondeo** (neto sin redondear × alícuota/100) y se redondea a 2 decimales **HALF_UP**.
- **RN-IM-06** (E4): El neto se redondea **por separado**, también HALF_UP a 2 decimales.
- **RN-IM-07** (E4): `subtotal = netoRedondeado + ivaRedondeado`.
- **RN-IM-08** (E4): `importeTotal` de cabecera = suma (BigDecimal) de los subtotales; `importeSaldo = importeTotal − importeCobrado`. No hay registro de pagos (cobrado = 0).
- **RN-IM-09** (E4) **Control double round-trip**: el modelo conserva campos `double`. Antes de persistir cada importe calculado: (a) la conversión a `double` debe ser finita; (b) `BigDecimal.valueOf(double)` debe ser igual (por valor decimal) al `BigDecimal` calculado. Si falla (desborde o pérdida de centavos) → **409 Conflict** (conflicto de negocio) **sin persistencia parcial**. **Prohibido** sustituir este control por `Math.round`.
- **RN-IM-10** (E4): Ejemplo de criterio: los totales cambian por centavos si se altera el orden (redondear antes de multiplicar, etc.); respetar exactamente el orden de RN-IM-04..07.
- **RN-IM-11**: No hay conversión de monedas en ningún punto.

## Dominio: Estados y transiciones (RN-ES)

- **RN-ES-01** (E4): Únicas transiciones: BORRADOR → EMITIDA → ANULADA. No se vuelve atrás; **no se anula directamente un borrador**; no se emite una EMITIDA/ANULADA; no se anula una ANULADA.
- **RN-ES-02** (E4): Editar (PUT) y eliminar (DELETE) solo si el estado es BORRADOR.
- **RN-ES-03** (E4): Operación incompatible con el estado actual → **409 Conflict**.
- **RN-ES-04** (E4): Emitir exige detalles (no vacíos); asigna número, CAE, vencimiento y resultado `A`.
- **RN-ES-05** (E4): Anular registra `fechaAnulacion` (hoy) y baja lógica de auditoría (`usuarioBaja`, `fechaBaja`); **no elimina** el comprobante.
- **RN-ES-06** (E4): DELETE es eliminación **física** de un borrador y sus detalles (cascade); responde 204 sin cuerpo.
- **RN-ES-07** (E4): En PATCH, una observación **no vacía** reemplaza la observación de la factura; vacía/null no la modifica *(ref: `isBlank` no sustituye)*.
- **RN-ES-08**: Estas reglas son del ejercicio académico: no representan autorización real de ARCA.

## Dominio: Numeración (RN-NU)

- **RN-NU-01** (E4): La numeración es por punto de venta, comienza en **1** y se asigna al **emitir** (máximo existente + 1).
- **RN-NU-02** (E4): Antes de consultar el máximo se bloquea el **punto de venta** (bloqueo pesimista de escritura). Se mantiene la unicidad (`punto_venta`,`numero`) en la BD como red de seguridad.
- **RN-NU-03** (E4): Los borradores no tienen número (null).
- **RN-NU-04** (E4): Dos emisiones simultáneas de facturas *diferentes* del mismo punto de venta no deben duplicar número.

## Dominio: CAE simulado (RN-CA)

- **RN-CA-01** (E4): CAE = cadena de **14 dígitos** generada localmente (con ceros a la izquierda si hace falta; `String.format("%014d", ...)` *(ref)*), sin validez fiscal.
- **RN-CA-02** (E4): `caeFechaVencimiento` = fecha actual + **10 días**.
- **RN-CA-03** (E4): `resultadoAfip = "A"` *(nombre de campo ref; la consigna dice "resultado de autorización A")*.
- **RN-CA-04**: Un borrador tiene CAE, vencimiento y resultado nulos.

## Dominio: Concurrencia y transacciones (RN-CC)

- **RN-CC-01** (E4): Antes de editar, cambiar estado o eliminar se obtiene **bloqueo pesimista de escritura** (`PESSIMISTIC_WRITE`) sobre la cabecera, *antes* de verificar el estado, dentro de la misma transacción (evita editar una factura recién emitida).
- **RN-CC-02** (E4): Al emitir se bloquea además el punto de venta (RN-NU-02).
- **RN-CC-03** (E4): Las escrituras son `@Transactional`; las consultas `@Transactional(readOnly = true)`. Una falla de validación de negocio o de persistencia revierte la operación completa (rollback).
- **RN-CC-04** (E4): `open-in-view=false`; el DTO se construye **dentro** de la transacción (evita `LazyInitializationException`).
- **RN-CC-05** (E4): Los bloqueos pesimistas se comportan distinto en H2 y PostgreSQL: distinguir pruebas ejecutadas en cada motor.

## Dominio: Auditoría (RN-AU)

- **RN-AU-01** (E4): El usuario de auditoría sale del header `X-Usuario`.
- **RN-AU-02** (E4): Si falta, está vacío o no existe → se usa `sistema` (`facturacion.usuario-por-defecto`). No se rechaza el request.
- **RN-AU-03** (E4): El usuario actual se mantiene durante la petición en un `ThreadLocal` (`UsuarioActual`) y se **limpia al terminar** (`afterCompletion`).
- **RN-AU-04** (E4): Altas/modificaciones se completan con los callbacks `@PrePersist`/`@PreUpdate` de `AuditoriaApp`.
- **RN-AU-05** (E4): **Cada PUT aceptado** registra explícitamente la modificación de la cabecera aunque cambien solo los renglones o se reenvíe el mismo contenido.
- **RN-AU-06** (E4): Al anular se registran usuario y fecha de baja.
- **RN-AU-07**: `X-Usuario` identifica al actor declarado; **no autentica ni autoriza** (ver 03).
- **RN-AU-08** (E1): Una restricción de auditoría incumplida suele indicar que no hay usuario en el contexto (`UsuarioActual`); las pruebas de persistencia deben prepararlo.

## Dominio: Validación de entrada (RN-VA)

- **RN-VA-01** (E4): `FacturaRequestDTO`: `condicionIvaId`, `puntoVentaId`, `tipoMonedaId` obligatorios; `clienteId` opcional (null = «Consumidor Final»); `fechaEmision` opcional (default hoy); `observaciones` ≤ **500**; `detalles` no vacía, `@Valid`, sin elementos nulos.
- **RN-VA-02** (E4): `FacturaDetalleRequestDTO`: `listaPrecioArticuloId` obligatorio; `cantidad` > 0; `porcentajeBonificacion` opcional 0..100 (omitido = 0); `descripcion` opcional ≤ **255** (límite 255 OK / 256 → 400, tanto en POST como en PUT; si null → denominación del artículo).
- **RN-VA-03** (E4): `CambioEstadoRequestDTO`: `estado` no vacío; `observaciones` opcional ≤ 500.
- **RN-VA-04** (E4): Referencia inexistente (cliente, condición IVA, punto de venta, moneda, lista-artículo) → **404** (se resuelven los ids *antes* de completar la operación).
- **RN-VA-05** (E4): Validación fallida → **400** con mapa `errores` campo→mensaje.
- **RN-VA-06** (E4): JSON malformado, incompatible con el DTO o ausente cuando es obligatorio → **400** con mensaje legible, **sin exponer detalles internos del parser** (no filtrar mensajes de Jackson).
- **RN-VA-07** (E4): El identificador del renglón es el de la asociación lista–artículo, no el del artículo.
- **RN-VA-08** (E4): La validación definitiva corresponde al backend; el frontend solo ayuda.

## Dominio: Filtros de consulta (RN-FI)

- **RN-FI-01** (E3): Filtros opcionales: `fechaDesde`, `fechaHasta` (`yyyy-MM-dd`, **inclusivos**), `estado`, `montoMinimo` (total mínimo **inclusivo** y **finito**).
- **RN-FI-02** (E3): `estado` se **normaliza**: `trim()` + mayúsculas; desconocido → **400**.
- **RN-FI-03** (E3): `fechaDesde` > `fechaHasta` → **400**.
- **RN-FI-04** (E3): `montoMinimo` no finito (NaN/Infinity) → **400**. El backend **no impone mínimo 0** (el contrato no lo define); el formulario web sí puede restringir a no negativos.
- **RN-FI-05** (E3): Orden: `fechaEmision` DESC y luego `id` DESC. Sin paginación; sin filtro por punto de venta.
- **RN-FI-06** (E3): Sin resultados → `200` con lista vacía (≠ 404 de recurso inexistente).
- **RN-FI-07** (E2/E3): Misma consulta JPQL para listado y reporte; el archivo descargado corresponde a los mismos filtros.
- **RN-FI-08** (E2): La consulta usa `LEFT JOIN` a cliente y detalles (conserva facturas sin cliente y cabeceras sin detalles), `COALESCE(cliente.denominacion, 'Consumidor Final')`, joins a condición IVA, punto de venta y moneda, `GROUP BY` de los campos no agregados, `COUNT` de ítems; parámetros (no concatenación).
- **RN-FI-09** (E2): El constructor de la proyección `SELECT new` debe aceptar los tipos exactos de la consulta (`Long` para COUNT, `EstadoFactura`, `Double`) y respetar el **orden**; el DTO expone el estado como texto.

## Dominio: Catálogos (RN-CT)

- **RN-CT-01** (E3): Cinco catálogos de **solo lectura**; devuelven `OpcionDTO(id, etiqueta, valor, codigoMoneda)`. `valor` = precio en lista-precio-articulos; `codigoMoneda` = código en tipos-moneda; los demás pueden ser null.
- **RN-CT-02** (E3): No hay alta/modificación/eliminación de catálogos.

## Dominio: Errores HTTP (RN-ER)

- **RN-ER-01** (E3/E4): Errores centralizados en `ProblemDetail` con `status`, `title`, `detail` y marca temporal `timestamp`; la validación agrega `errores`.
- **RN-ER-02**: **400**: JSON inválido, tipos incompatibles, validación de campos, filtros inválidos (estado, fechas, monto), formato de exportación inválido, *(ref)* glifo no soportado en PDF.
- **RN-ER-03**: **404**: factura inexistente (`GET/PUT/PATCH/DELETE /{id}`) o referencia de catálogo inexistente.
- **RN-ER-04**: **409**: transición no permitida; editar/eliminar fuera de BORRADOR; importe no representable (double round-trip).
- **RN-ER-05**: Distinguir consulta vacía (200 `[]`) de recurso inexistente (404).
- **RN-ER-06**: Las entidades **nunca** se serializan en respuestas.

## Dominio: Reportes (RN-RE)

- **RN-RE-01** (E2): Tres formatos a partir de los **mismos DTOs y filtros** del listado; se generan **en memoria** y se devuelven por HTTP; no se escriben en carpetas del servidor ni dependen del `DataLoader`.
- **RN-RE-02** (E2): **Nueve columnas**, en este orden: número, fecha, cliente, condición IVA, punto de venta, ítems, total, estado, moneda. El id interno **no** es columna.
- **RN-RE-03** (E2): Borrador: PDF muestra **guion** en número; Excel/TXT dejan el campo **vacío**.
- **RN-RE-04** (E2): **PDF** con OpenPDF: A4 apaisado, título, fecha de generación, cantidad de registros, **encabezados repetidos en cada página**, mensaje cuando no hay resultados; fuente `NotoSansCJKsc-Regular.otf` incrustada con `Identity-H`, licencia OFL conservada.
- **RN-RE-05** (E2): PDF: si un texto contiene un **glifo no disponible** se rechaza la exportación con **error explícito** (no sustituir silenciosamente); TXT/Excel siguen disponibles. Status: *(ref)* 400 con el código Unicode.
- **RN-RE-06** (E2): **Excel** con Apache POI: hoja `Facturas`; encabezados destacados; fechas y números como celdas de su tipo; formatos legibles; ajuste de columnas; totales como fórmulas **`SUMIFS`** (estado = EMITIDA y código de moneda); evaluar las fórmulas y habilitar recálculo.
- **RN-RE-07** (E2): **Totales (PDF y Excel)**: solo facturas **EMITIDA**, **separados por código de moneda**; no se suman borradores ni anuladas; no se convierten monedas; una moneda presente en el reporte sin emitidas muestra **total 0**.
- **RN-RE-08** (E2): **TXT**: UTF-8, separador tabulación, **una fila física por factura**, nueve columnas, fechas ISO, importes con 2 decimales y punto decimal, **sin pie de totales**; incluye el código de moneda.
- **RN-RE-09** (E2): **Escapes TXT reversibles**: `\` → `\\`, TAB → `\t`, CR → `\r`, LF → `\n`; las comillas quedan literales; el orden de escape empieza por la barra invertida. No es CSV entrecomillado; para leerlo se separan filas y columnas y luego se decodifican escapes.
- **RN-RE-10** (E3): Endpoint de reporte: `formato` default `pdf`; admite `pdf`, `xlsx` (alias `excel`), `txt` (alias `tsv`); formato no admitido → 400; respuesta con tipo de contenido correspondiente y `Content-Disposition: attachment`.
- **RN-RE-11** (E2): Se revisa **visualmente** el PDF (una prueba de contenido no demuestra legibilidad).

## Dominio: Configuración y CORS (RN-CF)

- **RN-CF-01** (E3): CORS permite orígenes `localhost` y `127.0.0.1` en puertos 5173 y 3000 (propiedad `facturacion.cors.allowed-origins`). No es autenticación (ver 03).
- **RN-CF-02** (E0): `application.yml` con H2 por defecto y perfil `postgres`; `open-in-view: false`; consola H2 habilitada solo con H2.
- **RN-CF-03** (E0): Se debe declarar qué motor se probó: un resultado en H2 no demuestra el mismo resultado en PostgreSQL.
- **RN-CF-04**: Si cambia el puerto del backend, se actualiza el `meta` `api-base-url` del frontend; si cambia el origen del frontend, se ajusta la lista CORS.

## Dominio: Frontend (RN-FE)

- **RN-FE-01** (E5): TypeScript estricto, módulos ES2020, sin framework; interfaces para los DTO; un servicio HTTP centraliza consultas, catálogos, creación, actualización, cambio de estado, eliminación y construcción de URLs de reporte.
- **RN-FE-02** (E5): URL base de la API desde `<meta name="api-base-url">`.
- **RN-FE-03** (E5): `fetch` con manejo explícito de respuestas no exitosas, encabezados JSON cuando corresponde y `X-Usuario: sistema`.
- **RN-FE-04** (E5): Respuesta **204** se resuelve sin leer JSON (error típico: fallar tras DELETE).
- **RN-FE-05** (E5): `ApiError` conserva estado HTTP y detalles; error con cuerpo no JSON → mensaje de respaldo comprensible; **no ocultar errores de validación por campo**.
- **RN-FE-06** (E5): Importes con la moneda de cada factura: `PES` → ARS, `DOL` → USD; código no reconocido se muestra tal cual; no se convierten importes.
- **RN-FE-07** (E5): Validación en formulario: cantidad finita > 0; bonificación finita 0..100; descripción ≤ 255; observaciones ≤ 500. El request **no** envía precio unitario ni importes calculados.
- **RN-FE-08** (E5): Se muestra precio de catálogo y total estimado **neto sin IVA**; el backend es la autoridad del precio y del importe definitivo con IVA.
- **RN-FE-09** (E5): Conservar valores del formulario si falla el guardado (permite corregir y reintentar); cancelar edición descarta cambios.
- **RN-FE-10** (E5): Acciones según estado (emitir/anular/eliminar). Confirmar eliminación de borrador. Motivo de anulación opcional; **cancelar el diálogo no envía PATCH**.
- **RN-FE-11** (E5): Enlaces de descarga PDF y Excel con los **filtros de la consulta vigente**; TXT solo vía API/URL del servicio (sin tercer botón).
- **RN-FE-12** (E5): **Bloqueo de doble envío**: deshabilitar controles y acciones incompatibles durante POST/PUT; liberar al terminar (éxito **o** error).
- **RN-FE-13** (E5): **Respuestas obsoletas**: una respuesta tardía no sobrescribe una búsqueda más reciente ni restaura una edición cancelada o sustituida (ids de solicitud o equivalente); lista y enlaces de exportación se actualizan **juntos**.
- **RN-FE-14** (E5): Contenido externo se inserta con nodos DOM y **`textContent`** (nunca HTML interpretado) — denominaciones, descripciones y mensajes.
- **RN-FE-15** (E5): Región de estado persistente `role="status"` + `aria-live="polite"`; `aria-busy` durante el guardado; errores visibles hasta una acción posterior; mensajes de éxito se limpian a los **6 s** sin quitar la región del DOM.
- **RN-FE-16** (E5): Mensaje cuando no hay datos; mostrar cantidad de coincidencias.

## Dominio: Reglas globales (RN-GE)

- **RN-GE-01**: Las entidades no se devuelven por HTTP; controladores ↔ DTO, servicios ↔ reglas y transacciones, repositorios ↔ datos.
- **RN-GE-02**: Cada etapa amplía lo anterior y **exige conservar el funcionamiento anterior** (regresión).
- **RN-GE-03**: Lombok: `compileOnly` + `annotationProcessor`; sin `@Data` indiscriminado; la cabecera excluye `detalles` y el detalle excluye `factura` de `toString/equals/hashCode` (recursión). Si se demuestra la variante incorrecta, solo en prueba/rama descartable.
- **RN-GE-04**: Todo código entregado debe ser comprendido por su autor; el uso de IA se declara en cada `docs/etapa-N.md`.
- **RN-GE-05**: La versión evaluada es la del **tag** entregado (`etapa-1`..`etapa-5`).
- **RN-GE-06**: Fuera de alcance no se vuelve requisito: un cambio propuesto en la defensa es actividad de evaluación, no obligación previa.
