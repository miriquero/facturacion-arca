# Spec Delta

## Purpose

Establece el frontend TypeScript sin framework que compila de forma estricta, se sirve por HTTP en el puerto 5173 y obtiene la URL base de la API desde el HTML, con un runner de pruebas reproducible.

## ADDED Requirements

### Requirement: Instalación y compilación reproducibles
El frontend MUST instalarse con `npm ci` a partir de un lockfile versionado, MUST requerir Node 18.13 o posterior, y `npm run build` MUST compilar TypeScript 5.6 en modo estricto a módulos ES2020 sin errores.

#### Scenario: Compilación limpia
- **WHEN** se ejecuta `npm ci` y luego `npm run build` en `frontend/`
- **THEN** la compilación termina sin errores y genera los módulos JavaScript en `frontend/dist/`

#### Scenario: Versión de TypeScript acotada
- **WHEN** se instala con `npm ci`
- **THEN** la versión instalada de TypeScript pertenece a la línea 5.6

### Requirement: Servidor de desarrollo en 5173
`npm run dev` MUST compilar el frontend y servirlo por HTTP en el puerto 5173 sin bundler ni framework.

#### Scenario: Página servida
- **WHEN** se ejecuta `npm run dev` y se solicita `http://localhost:5173/`
- **THEN** responde la página principal del frontend y el módulo compilado se carga sin errores en la consola del navegador

### Requirement: URL base de la API configurable en el HTML
La página principal MUST declarar `<meta name="api-base-url" content="http://localhost:8080/api">` y el código del frontend MUST obtener la URL base de la API exclusivamente de ese elemento.

#### Scenario: Meta presente
- **WHEN** se analiza `index.html`
- **THEN** contiene el elemento meta `api-base-url` con valor `http://localhost:8080/api` y carga el módulo principal como `type="module"`

#### Scenario: Lectura de la configuración
- **WHEN** el frontend lee la configuración de un documento cuyo meta `api-base-url` vale `http://127.0.0.1:9090/api`
- **THEN** obtiene `http://127.0.0.1:9090/api`

#### Scenario: Configuración ausente
- **WHEN** el frontend lee la configuración de un documento sin meta `api-base-url` o con contenido vacío
- **THEN** falla con un error explícito que nombra `api-base-url`

#### Scenario: Valor visible como texto
- **WHEN** la página principal se carga en el navegador
- **THEN** muestra la URL base de la API configurada insertada como texto, no como HTML

### Requirement: Pruebas del frontend con jsdom
`npm test` MUST ejecutar las pruebas del frontend sobre un DOM simulado con jsdom, sin navegador, y MUST fallar si alguna prueba falla.

#### Scenario: Pruebas en verde
- **WHEN** se ejecuta `npm test` en `frontend/` después de `npm ci`
- **THEN** las pruebas de humo se ejecutan y finalizan con código de salida 0
