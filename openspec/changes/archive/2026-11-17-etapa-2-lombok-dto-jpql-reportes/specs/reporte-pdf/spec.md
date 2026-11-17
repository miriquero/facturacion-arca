## Purpose

Define el reporte de facturas en PDF: un documento apaisado legible en todas sus páginas, con una fuente incrustada que preserva el texto y que rechaza explícitamente cualquier carácter que no pueda representar en lugar de sustituirlo.

## ADDED Requirements

### Requirement: Página A4 apaisada
Todas las páginas del PDF MUST tener tamaño A4 en orientación apaisada.

#### Scenario: Tamaño de página
- **WHEN** se genera el PDF con tres facturas
- **THEN** cada página mide 842 × 595 puntos

### Requirement: Encabezado informativo
La primera página MUST mostrar el título del reporte, la fecha y hora de generación en la zona horaria `America/Argentina/Buenos_Aires` y la cantidad de registros.

#### Scenario: Fecha de generación y cantidad
- **WHEN** se genera el PDF con tres facturas en el instante 2026-11-12T18:30:00Z
- **THEN** el texto incluye el título, la fecha de generación `12/11/2026 15:30` y la cantidad de registros 3

### Requirement: Encabezados repetidos en cada página
Cuando las filas no entran en una página, cada página MUST repetir la fila de encabezados de las nueve columnas.

#### Scenario: Reporte de varias páginas
- **WHEN** se genera el PDF con 80 facturas
- **THEN** el documento tiene más de una página y el texto de cada página contiene los nueve encabezados

### Requirement: Mensaje sin resultados
Un PDF generado sin filas MUST mostrar un mensaje que indique que no hay facturas para informar y MUST NOT mostrar totales.

#### Scenario: Lista vacía
- **WHEN** se genera el PDF a partir de una lista vacía
- **THEN** el documento tiene una página con el título, la cantidad de registros 0 y el mensaje de que no hay facturas, sin totales

### Requirement: Totales facturados al pie
Después de la tabla, el PDF MUST mostrar un total facturado por cada código de moneda presente, con dos decimales.

#### Scenario: Totales por moneda
- **WHEN** se genera el PDF con facturas EMITIDA en `PES` por 1000.00 y una BORRADOR en `DOL` por 40.00
- **THEN** el texto incluye un total `PES` de 1.000,00 y un total `DOL` de 0,00

### Requirement: Fuente incrustada que preserva el texto
El PDF MUST usar la fuente `NotoSansCJKsc-Regular.otf` incrustada con codificación `Identity-H`, y el texto de las celdas MUST poder extraerse idéntico al original.

#### Scenario: Fuente incrustada
- **WHEN** se genera el PDF con una factura
- **THEN** el documento declara una fuente Noto Sans CJK SC incrustada con codificación `Identity-H`

#### Scenario: Caracteres especiales conservados
- **WHEN** se genera el PDF con el cliente «Ñandú & Cía. — 测试 «comillas»»
- **THEN** el texto extraído del documento contiene exactamente ese texto

### Requirement: Licencia de la fuente conservada
El texto de la licencia SIL Open Font License de la fuente MUST distribuirse junto a la fuente en el artefacto.

#### Scenario: Licencia junto a la fuente
- **WHEN** se inspeccionan los recursos del artefacto
- **THEN** junto a la fuente existe el texto de la licencia SIL Open Font License

### Requirement: Rechazo explícito de glifos no disponibles
Si algún texto a imprimir contiene un carácter sin glifo en la fuente, la generación MUST fallar con un error que identifique el carácter por su código Unicode, y MUST NOT producir un documento con caracteres sustituidos u omitidos.

#### Scenario: Carácter de uso privado
- **WHEN** se genera el PDF con un cliente que contiene el carácter U+E000
- **THEN** la generación falla con un error que menciona `U+E000` y no se devuelve contenido

#### Scenario: Otros formatos siguen disponibles
- **WHEN** con la misma lista que hizo fallar el PDF se generan el Excel y el TXT
- **THEN** ambos se generan y conservan el carácter U+E000
