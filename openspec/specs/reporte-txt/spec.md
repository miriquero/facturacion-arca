# reporte-txt Specification

## Purpose
Define el reporte de facturas en texto tabulado UTF-8: una línea por factura, campos separados por tabulación y escapados de manera reversible, para que ningún texto rompa filas ni columnas y el original pueda recuperarse.

## Requirements

### Requirement: Codificación y separadores
El TXT MUST estar codificado en UTF-8 sin marca de orden de bytes, MUST separar los campos con tabulación y MUST terminar cada línea con un salto de línea.

#### Scenario: Caracteres no ASCII
- **WHEN** se genera el TXT con el cliente «Ñandú & Cía. — 测试»
- **THEN** al decodificar el contenido como UTF-8 se lee exactamente ese texto y el contenido no comienza con una marca de orden de bytes

### Requirement: Encabezado y una línea física por factura
El TXT MUST tener una línea de encabezado con las nueve columnas y luego exactamente una línea física por factura, con nueve campos cada una.

#### Scenario: Tres facturas
- **WHEN** se genera el TXT con tres facturas
- **THEN** el contenido tiene cuatro líneas y cada una tiene nueve campos

#### Scenario: Lista vacía
- **WHEN** se genera el TXT a partir de una lista vacía
- **THEN** el contenido tiene solo la línea de encabezado

### Requirement: Escape reversible
Cada campo de texto MUST escapar barra invertida como `\\`, tabulación como `\t`, retorno de carro como `\r` y salto de línea como `\n`, empezando por la barra invertida; las comillas MUST quedar literales.

#### Scenario: Texto con caracteres de control
- **WHEN** se genera el TXT con el cliente `A\B<TAB>C<CR><LF>"D"`
- **THEN** el campo escrito es `A\\B\tC\r\n"D"` y la factura ocupa una sola línea física

#### Scenario: Reconstrucción del original
- **WHEN** se separa el TXT en líneas y campos y se decodifican los escapes de cada campo
- **THEN** se recupera exactamente el texto original de cada campo, incluidas barras invertidas, tabulaciones, retornos de carro y saltos de línea

#### Scenario: Barra invertida seguida de letra
- **WHEN** se genera el TXT con el cliente `C:\nuevo`
- **THEN** el campo escrito es `C:\\nuevo` y al decodificarlo se recupera `C:\nuevo` sin un salto de línea

### Requirement: Fechas e importes
Las fechas MUST escribirse en formato ISO `yyyy-MM-dd` y los importes MUST escribirse con exactamente dos decimales, punto decimal y sin separador de miles.

#### Scenario: Importe y fecha
- **WHEN** se genera el TXT con una factura del 2026-03-05 por 1234567.5
- **THEN** la fecha es `2026-03-05` y el total es `1234567.50`

### Requirement: Sin pie de totales
El TXT MUST contener solo el encabezado y las filas de detalle, sin líneas de totales.

#### Scenario: Facturas emitidas en dos monedas
- **WHEN** se genera el TXT con facturas EMITIDA en `PES` y en `DOL`
- **THEN** el número de líneas es el de facturas más uno y ninguna línea es un total
