## Purpose

Define el reporte de facturas en Excel: una hoja legible con celdas del tipo correcto y totales facturados expresados como fórmulas que el propio libro recalcula por estado y moneda.

## ADDED Requirements

### Requirement: Hoja Facturas con encabezados destacados
El libro MUST contener una hoja llamada `Facturas` cuya primera fila tenga las nueve columnas con un estilo destacado en negrita.

#### Scenario: Libro generado
- **WHEN** se genera el Excel con dos facturas
- **THEN** existe la hoja `Facturas`, su primera fila tiene los nueve encabezados en negrita y las filas 2 y 3 tienen las facturas

### Requirement: Celdas tipadas
La fecha MUST ser una celda numérica con formato de fecha; número, ítems y total MUST ser celdas numéricas; cliente, condición IVA, punto de venta, estado y moneda MUST ser celdas de texto.

#### Scenario: Tipos de una factura emitida
- **WHEN** se genera el Excel con una factura EMITIDA número 15 del 2026-11-10, dos ítems y total 1210.50
- **THEN** la fecha es una celda con formato de fecha que vale 2026-11-10, número, ítems y total son numéricos con valores 15, 2 y 1210.50, y estado y moneda son texto

### Requirement: Formatos legibles y columnas ajustadas
El total MUST mostrarse con dos decimales y separador de miles, la fecha con día, mes y año, y el ancho de cada columna MUST ajustarse a su contenido.

#### Scenario: Formato del total
- **WHEN** se genera el Excel con un total 1234567.5
- **THEN** la celda del total tiene un formato con dos decimales y separador de miles

#### Scenario: Columna de cliente ajustada
- **WHEN** se genera el Excel con un cliente de 40 caracteres
- **THEN** la columna de cliente es más ancha que el ancho por defecto de la hoja

### Requirement: Totales como fórmulas SUMIFS
Debajo de las filas de detalle MUST haber una fila de total por cada código de moneda presente, cuyo valor sea una fórmula `SUMIFS` sobre la columna total que seleccione estado `EMITIDA` y ese código de moneda.

#### Scenario: Fórmula por moneda
- **WHEN** se genera el Excel con facturas en `PES` y `DOL`
- **THEN** hay una fila de total para `PES` y otra para `DOL`, y cada valor es una fórmula `SUMIFS` que referencia las columnas total, estado y moneda

#### Scenario: Lista vacía
- **WHEN** se genera el Excel a partir de una lista vacía
- **THEN** la hoja tiene solo los encabezados y ninguna fila de total

### Requirement: Fórmulas evaluadas y recálculo habilitado
Las fórmulas de total MUST tener su resultado ya evaluado en el archivo y el libro MUST pedir el recálculo de fórmulas al abrirse.

#### Scenario: Resultado almacenado
- **WHEN** se lee el Excel generado con facturas EMITIDA en `PES` por 100.10 y 200.20 y ANULADA por 50.00 sin evaluar nada
- **THEN** el resultado almacenado de la fórmula `PES` es 300.30 y el libro indica recálculo forzado al abrirse
