# api-cors Specification

## Purpose
Define qué orígenes web del frontend local pueden leer las respuestas de la API desde un navegador, de forma configurable, dejando explícito que esta política no identifica ni autentica a nadie.

## Requirements

### Requirement: Orígenes locales admitidos
Las rutas bajo `/api` SHALL permitir peticiones cross-origin de lectura y escritura (`GET`, `POST`, `PUT`, `PATCH` y `DELETE`) desde `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173` y `http://127.0.0.1:3000`, devolviendo ese mismo origen en `Access-Control-Allow-Origin`.

#### Scenario: Frontend en el puerto 5173
- **WHEN** se pide `GET /api/facturas` con el encabezado `Origin: http://localhost:5173`
- **THEN** la respuesta es `200` con `Access-Control-Allow-Origin: http://localhost:5173`

#### Scenario: Preflight desde 127.0.0.1:3000
- **WHEN** se envía `OPTIONS /api/catalogos/clientes` con `Origin: http://127.0.0.1:3000` y `Access-Control-Request-Method: GET`
- **THEN** la respuesta es `200` y permite el origen y el método `GET`

#### Scenario: Preflight de cada método de escritura
- **WHEN** se envía `OPTIONS /api/facturas/1` con `Origin: http://localhost:5173` y `Access-Control-Request-Method` `POST`, `PUT`, `PATCH` o `DELETE`
- **THEN** la respuesta es `200` y `Access-Control-Allow-Methods` incluye el método pedido

### Requirement: Nombre del archivo legible desde el navegador
Las respuestas cross-origin admitidas MUST exponer el encabezado `Content-Disposition` para que el frontend pueda leer el nombre del archivo descargado.

#### Scenario: Descarga desde el frontend
- **WHEN** se pide `GET /api/facturas/reporte?formato=txt` con `Origin: http://localhost:5173`
- **THEN** `Access-Control-Expose-Headers` incluye `Content-Disposition`

### Requirement: Orígenes no previstos rechazados
Un origen distinto de los configurados MUST NOT recibir permiso cross-origin.

#### Scenario: Otro puerto u otro host
- **WHEN** se pide `GET /api/facturas` con `Origin: http://localhost:8081` o `Origin: http://evil.example`
- **THEN** la respuesta no lleva `Access-Control-Allow-Origin` y el navegador no puede leerla (Spring responde `403`)

### Requirement: Orígenes configurables
La lista de orígenes MUST leerse de la propiedad `facturacion.cors.allowed-origins`, cuyo valor por defecto son los cuatro orígenes locales, de modo que cambiar el origen del frontend no requiera modificar código.

#### Scenario: Propiedad sobrescrita
- **WHEN** la aplicación arranca con `facturacion.cors.allowed-origins=http://localhost:4200`
- **THEN** `Origin: http://localhost:4200` es admitido y `Origin: http://localhost:5173` no

### Requirement: CORS no es autenticación
La política CORS MUST NOT usarse como control de acceso: una petición sin encabezado `Origin` (por ejemplo desde `curl` o un script) MUST responder normalmente, y la API MUST NOT pedir credenciales.

#### Scenario: Cliente sin navegador
- **WHEN** se pide `GET /api/facturas` sin encabezado `Origin`
- **THEN** la respuesta es `200` con el listado y sin encabezados CORS

### Requirement: Encabezados de escritura admitidos y expuestos
Las peticiones cross-origin admitidas MUST poder enviar los encabezados `Content-Type` y `X-Usuario`, y las respuestas MUST exponer `Location` y `Content-Disposition`; un encabezado no previsto MUST NOT autorizarse en el preflight.

#### Scenario: Preflight con X-Usuario
- **WHEN** se envía `OPTIONS /api/facturas` con `Origin: http://localhost:5173`, `Access-Control-Request-Method: POST` y `Access-Control-Request-Headers: content-type, x-usuario`
- **THEN** la respuesta es `200` y `Access-Control-Allow-Headers` incluye `content-type` y `x-usuario`

#### Scenario: Location legible desde el frontend
- **WHEN** se crea un borrador con `POST /api/facturas` y `Origin: http://localhost:5173`
- **THEN** `Access-Control-Expose-Headers` incluye `Location`

#### Scenario: Encabezado no previsto
- **WHEN** se envía un preflight con `Access-Control-Request-Headers: authorization`
- **THEN** la respuesta es `403` y no autoriza ese encabezado
