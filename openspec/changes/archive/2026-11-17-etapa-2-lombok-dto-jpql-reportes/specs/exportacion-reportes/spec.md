## Purpose

Fija las reglas comunes a los reportes de facturas en PDF, Excel y TXT: se generan en memoria a partir de las filas del reporte, comparten nueve columnas en orden fijo y, cuando muestran totales, solo suman facturas emitidas separadas por moneda.

## ADDED Requirements

### Requirement: Generación en memoria a partir de las filas del reporte
Cada formato MUST generarse como contenido binario en memoria a partir de una lista de filas del reporte, sin escribir archivos en disco y sin depender de la carga de datos de ejemplo.

#### Scenario: Contenido devuelto sin archivos
- **WHEN** se genera cualquiera de los tres formatos a partir de una lista de filas
- **THEN** se obtiene un contenido no vacío y no se crea ningún archivo en el directorio de trabajo

#### Scenario: Lista vacía
- **WHEN** se genera cualquiera de los tres formatos a partir de una lista vacía
- **THEN** se obtiene un contenido válido para ese formato sin error

### Requirement: Nueve columnas en orden fijo
Cada formato MUST presentar, en este orden, las columnas número, fecha, cliente, condición IVA, punto de venta, ítems, total, estado y moneda, y MUST NOT incluir el identificador interno.

#### Scenario: Encabezados y valores
- **WHEN** se genera cualquiera de los tres formatos con una factura EMITIDA número 15 e identificador 900
- **THEN** los encabezados son las nueve columnas en ese orden y el valor 900 no aparece como dato de la fila

### Requirement: Número de borrador
Para una factura en BORRADOR, el PDF MUST mostrar un guion en la columna número y Excel y TXT MUST dejar ese campo vacío.

#### Scenario: Borrador en PDF
- **WHEN** se genera el PDF con una factura BORRADOR
- **THEN** su columna número muestra `-`

#### Scenario: Borrador en Excel y TXT
- **WHEN** se generan Excel y TXT con una factura BORRADOR
- **THEN** su campo número está vacío

### Requirement: Totales solo de facturas emitidas
Los totales facturados del PDF y del Excel MUST sumar únicamente facturas EMITIDA y MUST NOT incluir borradores ni anuladas.

#### Scenario: Estados mezclados en una moneda
- **WHEN** se generan PDF y Excel con facturas `PES` EMITIDA por 100.10 y 200.20, ANULADA por 50.00 y BORRADOR por 70.00
- **THEN** el total facturado en `PES` es 300.30

### Requirement: Totales separados por código de moneda
Los totales facturados MUST calcularse por separado para cada código de moneda presente en el reporte, sin convertir monedas, y una moneda presente sin facturas emitidas MUST mostrar total cero.

#### Scenario: Dos monedas
- **WHEN** se generan PDF y Excel con facturas EMITIDA en `PES` por 1000.00 y en `DOL` por 10.50
- **THEN** hay un total `PES` de 1000.00 y un total `DOL` de 10.50, y ningún total combinado

#### Scenario: Moneda sin emitidas
- **WHEN** se generan PDF y Excel con una factura EMITIDA en `PES` por 100.00 y una BORRADOR en `DOL` por 40.00
- **THEN** el total `PES` es 100.00 y el total `DOL` es 0.00

### Requirement: Suma decimal exacta a dos decimales
Los totales facturados MUST calcularse con aritmética decimal y redondeo half-up a dos decimales, sin acumular error de punto flotante.

#### Scenario: Importes que en punto flotante no suman exacto
- **WHEN** se generan PDF y Excel con facturas EMITIDA en `PES` por 0.10, 0.20 y 0.70
- **THEN** el total `PES` es exactamente 1.00
