# Spec Delta

## Purpose

Centraliza en un único cliente HTTP tipado todas las llamadas del frontend a la API de facturación (consultas, catálogos, escritura, cambios de estado, eliminación y URLs de reporte) y la traducción uniforme de las respuestas no exitosas a un error de aplicación.

## ADDED Requirements

### Requirement: URL base tomada de la configuración del HTML
Todas las peticiones y URLs de reporte del cliente MUST construirse sobre la URL base leída de `<meta name="api-base-url">`, sin depender del host que sirve la página.

#### Scenario: Base configurada
- **WHEN** el meta `api-base-url` vale `http://127.0.0.1:9090/api` y se pide el listado
- **THEN** la petición se envía a `http://127.0.0.1:9090/api/facturas`

#### Scenario: Base con barra final
- **WHEN** el meta vale `http://localhost:8080/api/` y se pide el catálogo de clientes
- **THEN** la petición se envía a `http://localhost:8080/api/catalogos/clientes`, sin doble barra

### Requirement: Usuario declarado en todas las peticiones
Toda petición del cliente a la API MUST incluir el encabezado `X-Usuario: sistema`.

#### Scenario: Lectura y escritura
- **WHEN** el cliente pide el listado y luego elimina un borrador
- **THEN** ambas peticiones llevan `X-Usuario` con el valor `sistema`

### Requirement: Encabezado JSON solo con cuerpo
Las peticiones con cuerpo (`POST`, `PUT` y `PATCH`) MUST enviar el cuerpo serializado como JSON con `Content-Type: application/json`; las peticiones sin cuerpo MUST NOT declarar `Content-Type`.

#### Scenario: Alta con cuerpo
- **WHEN** el cliente crea un borrador
- **THEN** la petición es `POST /facturas` con `Content-Type: application/json` y el cuerpo es el JSON del request

#### Scenario: Consulta sin cuerpo
- **WHEN** el cliente pide el detalle de la factura 7
- **THEN** la petición es `GET /facturas/7` sin cuerpo y sin `Content-Type`

### Requirement: Operaciones del contrato
El cliente SHALL exponer una operación por cada ruta usada por la interfaz: listado con filtros, detalle por id, los cinco catálogos, alta, reemplazo, cambio de estado y eliminación, con el método y la ruta del contrato de la API.

#### Scenario: Cambio de estado
- **WHEN** el cliente anula la factura 12 con el motivo `Error en el cliente`
- **THEN** envía `PATCH /facturas/12/estado` con el cuerpo `{"estado":"ANULADA","observaciones":"Error en el cliente"}`

#### Scenario: Emisión sin observaciones
- **WHEN** el cliente emite la factura 12 sin motivo
- **THEN** el cuerpo es `{"estado":"EMITIDA"}` y no incluye `observaciones`

#### Scenario: Reemplazo de un borrador
- **WHEN** el cliente actualiza el borrador 5
- **THEN** envía `PUT /facturas/5` con el request completo y devuelve la fila de reporte de la respuesta

#### Scenario: Catálogo de artículos por lista
- **WHEN** el cliente pide las opciones de lista–artículo
- **THEN** envía `GET /catalogos/lista-precio-articulos` y devuelve las opciones con `id`, `etiqueta`, `valor` y `codigoMoneda`

### Requirement: Filtros vacíos omitidos en la consulta
El cliente MUST incluir en la query string solo los filtros informados (`fechaDesde`, `fechaHasta`, `estado`, `montoMinimo`), codificados como parámetros de URL, y MUST omitir los vacíos.

#### Scenario: Sin filtros
- **WHEN** se pide el listado con todos los filtros vacíos
- **THEN** la URL es `/facturas` sin `?`

#### Scenario: Filtros combinados
- **WHEN** se pide el listado con `fechaDesde` 2026-11-01, `estado` `EMITIDA` y `montoMinimo` 0
- **THEN** la URL contiene `fechaDesde=2026-11-01`, `estado=EMITIDA` y `montoMinimo=0`, y no contiene `fechaHasta`

### Requirement: URLs de reporte con los mismos filtros
El cliente MUST construir la URL de `GET /facturas/reporte` para los formatos `pdf`, `xlsx` y `txt` con el parámetro `formato` y exactamente los mismos parámetros de filtro que usaría el listado.

#### Scenario: Excel filtrado
- **WHEN** se construye la URL de reporte `xlsx` con `estado` `EMITIDA`
- **THEN** la URL es la base seguida de `/facturas/reporte` con `formato=xlsx` y `estado=EMITIDA`

#### Scenario: TXT disponible por URL
- **WHEN** se construye la URL de reporte `txt` sin filtros
- **THEN** la URL contiene `formato=txt` y ningún parámetro de filtro

### Requirement: Respuesta 204 sin lectura de cuerpo
Una respuesta `204 No Content` MUST resolverse sin intentar leer ni interpretar JSON.

#### Scenario: Eliminación exitosa
- **WHEN** la API responde `204` sin cuerpo a la eliminación de un borrador
- **THEN** la operación se resuelve sin error y sin leer el cuerpo de la respuesta

### Requirement: Respuestas no exitosas como ApiError
Toda respuesta con estado fuera de 2xx MUST rechazarse con un `ApiError` que conserva el estado HTTP y, si el cuerpo es un Problem Details legible, su `title`, su `detail` y su mapa `errores` por campo; el mensaje del error MUST ser el `detail` cuando exista.

#### Scenario: Conflicto de estado
- **WHEN** la API responde `409` con `{"status":409,"title":"Conflicto con el estado de la factura","detail":"La factura está EMITIDA"}`
- **THEN** el cliente rechaza con un `ApiError` de estado 409 cuyo mensaje es `La factura está EMITIDA`

#### Scenario: Validación por campo
- **WHEN** la API responde `400` con `errores` `{"tipoMonedaId":"es obligatorio","detalles[0].cantidad":"debe ser mayor que 0"}`
- **THEN** el `ApiError` tiene estado 400 y expone las dos claves con sus mensajes

### Requirement: Mensaje de respaldo ante cuerpo no JSON
Si una respuesta no exitosa no trae un Problem Details legible (cuerpo vacío, HTML, texto o JSON sin `detail`), el `ApiError` MUST conservar el estado y MUST llevar un mensaje de respaldo comprensible en español que incluya ese estado.

#### Scenario: Página HTML de error
- **WHEN** la API responde `502` con un cuerpo `<html>Bad Gateway</html>`
- **THEN** el cliente rechaza con un `ApiError` de estado 502, sin errores por campo y con un mensaje en español que menciona 502

#### Scenario: Error sin cuerpo
- **WHEN** la API responde `500` con el cuerpo vacío
- **THEN** el cliente rechaza con un `ApiError` de estado 500 y mensaje de respaldo

### Requirement: Falla de conexión comprensible
Si la petición no llega a obtener respuesta, el cliente MUST rechazar con un `ApiError` de estado 0 cuyo mensaje indique que no se pudo conectar con la API configurada.

#### Scenario: Backend apagado
- **WHEN** la petición falla por error de red
- **THEN** el cliente rechaza con un `ApiError` de estado 0 y un mensaje que menciona la URL base configurada

### Requirement: Request sin precios ni importes
Los cuerpos de alta y edición MUST contener solo cliente opcional, condición de IVA, punto de venta, moneda, fecha de emisión opcional, observaciones opcionales y renglones con `listaPrecioArticuloId`, `cantidad`, `porcentajeBonificacion` y `descripcion` opcionales; MUST NOT incluir precios unitarios ni importes calculados.

#### Scenario: Alta con dos renglones
- **WHEN** el cliente envía un alta con dos renglones
- **THEN** el JSON enviado no contiene ninguna clave `precioUnitario`, `importeNeto`, `importeIva`, `importeSubtotal` ni `importeTotal`
