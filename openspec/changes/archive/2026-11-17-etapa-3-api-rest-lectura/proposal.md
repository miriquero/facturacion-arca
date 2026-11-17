# Proposal

## Why

La etapa 2 dejó la proyección `FacturaReporteDTO` y los tres exportadores en memoria, pero la aplicación todavía no expone nada por HTTP: no hay controladores, ni filtros, ni manejo de errores, y el código HTTP del glifo no soportado quedó diferido. La Etapa 3 de la consigna (semana 6, 15 % de la nota de etapas) exige una **API REST de solo lectura** con contratos claros —listado filtrado, detalle, descarga de reportes con los mismos filtros, cinco catálogos, CORS para el frontend local y errores centralizados en `ProblemDetail`— sin serializar entidades JPA. El frontend de la etapa 5 y la escritura de la etapa 4 se apoyan en estos contratos.

## What Changes

- **`GET /api/facturas`**: lista de `FacturaReporteDTO` con filtros opcionales `fechaDesde`, `fechaHasta` (`yyyy-MM-dd`, inclusivos), `estado` (normalizado con `trim` + mayúsculas) y `montoMinimo` (inclusivo, finito, sin mínimo cero). Orden fecha de emisión descendente e id descendente; sin resultados → `200 []`. Estado desconocido, fechas invertidas, monto no finito o parámetro mal tipado → `400`.
- **Filtros en la consulta JPQL existente**: el `WHERE` se inserta entre las dos constantes de la etapa 2 con fragmentos fijos y **parámetros nombrados**, nunca concatenando valores. La consulta sin filtros conserva su comportamiento actual.
- **`GET /api/facturas/{id}`**: nuevo `FacturaDetalladaDTO` (ids y descripciones para editar la cabecera, estado, importes, CAE y datos de autorización simulada, observaciones y renglones) construido dentro de una transacción de solo lectura en el servicio (`open-in-view: false`); id inexistente → `404`.
- **`GET /api/facturas/reporte`**: mismos filtros + `formato` (default `pdf`; `pdf`, `xlsx`|`excel`, `txt`|`tsv`, sin distinguir mayúsculas), bytes con el `Content-Type` correspondiente y `Content-Disposition: attachment` con nombre de archivo; formato no admitido → `400`. El PDF con un glifo no disponible responde **`400`** con el code point (propuesta de IN-03, **pendiente de confirmar con el docente**, no bloqueante).
- **Catálogos de solo lectura**: `GET /api/catalogos/{puntos-venta, clientes, condiciones-iva, tipos-moneda, lista-precio-articulos}` → `OpcionDTO(id, etiqueta, valor, codigoMoneda)`; sin altas, modificaciones ni bajas.
- **`FacturaService`** de lectura (`@Transactional(readOnly = true)`) y **`CatalogoService`**; los controladores solo traducen HTTP.
- **Errores centralizados** con `@RestControllerAdvice` que devuelve `ProblemDetail` (`status`, `title`, `detail`, `timestamp`): `400` filtros y formato inválidos, tipos incompatibles y glifo no soportado; `404` factura inexistente; `405` para métodos de escritura (todavía no existen); `500` genérico sin filtrar detalles internos. Consulta vacía (`200 []`) ≠ recurso inexistente (`404`).
- **CORS** para `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173` y `http://127.0.0.1:3000`, configurable por la propiedad `facturacion.cors.allowed-origins`; se documenta que **CORS no autentica**.
- **`requests.http`** con ejemplos reproducibles (filtros individuales y combinados, vacío, 404, formatos, formato inválido, errores 400).
- **Pruebas**: `@WebMvcTest` de los controladores (servicio simulado), unitarias de normalización y validación de filtros y del formato, e integración sobre H2 de filtros, detalle sin `LazyInitializationException`, catálogos y equivalencia entre listado y archivo descargado.
- **Documentación**: `docs/etapa-3.md` (justificación de DTO, manejo de errores, filtros, CORS, semilla reutilizada, referencia del docente consultada solo por nombres, uso de IA) y actualización del README.
- **Semilla (DD-13)**: `api-facturas/controller/FacturaRestController.java` (partes GET), `config/CorsConfig.java`, `exception/GlobalExceptionHandler.java` y `RecursoNoEncontradoException.java`; se adaptan detrás de un test en rojo (inventario en `design.md`).

## Capabilities

### New Capabilities
- `api-listado-facturas`: consulta HTTP del listado de facturas con filtros opcionales, normalización y validación de parámetros, orden y lista vacía.
- `api-detalle-factura`: consulta HTTP de una factura por id con todos los datos necesarios para editarla, sin exponer entidades, y `404` para un id inexistente.
- `api-descarga-reporte`: descarga HTTP del reporte en PDF, Excel o TXT con los mismos filtros del listado, tipo de contenido, nombre de archivo y errores de formato y de glifo.
- `api-catalogos`: cinco catálogos HTTP de solo lectura con un formato de opción común.
- `api-errores`: formato uniforme de los errores HTTP (`ProblemDetail` con marca temporal) y su correspondencia con los códigos 400, 404, 405 y 500.
- `api-cors`: orígenes del navegador admitidos para el frontend local y la aclaración de que no es un control de acceso.

### Modified Capabilities
- `proyeccion-reporte-facturas`: la consulta acepta filtros opcionales (fechas inclusivas, estado, importe mínimo inclusivo, combinables) aplicados como parámetros; el requisito «Reporte vacío» pasa a cubrir también una consulta filtrada sin coincidencias.

## Impact

- **Código nuevo**: `controller/FacturaRestController`, `controller/CatalogoRestController`; `service/FacturaService`, `service/CatalogoService`; `dto/FacturaDetalladaDTO`, `dto/FacturaDetalleDTO`, `dto/OpcionDTO`, `dto/FacturaFiltrosDTO`; `repository/FiltroFacturas`; `report/FormatoReporte` y `report/ArchivoReporte`; `exception/ApiExceptionHandler`, `exception/RecursoNoEncontradoException`, `exception/ParametroInvalidoException`; `config/CorsConfig` y `config/CorsProperties`; pruebas en `src/test/java/.../{controller,service,repository,report,config}/**`.
- **Código modificado**: `FacturaReporteRepository` y `FacturaReporteRepositoryImpl` (método con filtros; el método sin argumentos se conserva delegando con filtros vacíos); `ListaPrecioArticuloRepository` (consulta con `join fetch` para el catálogo); `application.yml` (propiedad `facturacion.cors.allowed-origins`).
- **API**: primeros endpoints HTTP del proyecto, todos `GET` bajo `/api`.
- **Dependencias**: ninguna nueva; `build.gradle` no cambia (Web y Validation ya están).
- **Archivos nuevos**: `requests.http` en la raíz, `docs/etapa-3.md`; `README.md` actualizado.
- **Governance**: MEDIO. API de solo lectura, sin datos sensibles; el apply avanza por grupos con puntos de control.
- **Fuera de alcance**: `POST`/`PUT`/`PATCH`/`DELETE` y el encabezado `X-Usuario` (C-05), login, paginación, filtro por punto de venta, CRUD de catálogos, conversión de monedas, integración fiscal real y cualquier comando Git que cambie estado.
