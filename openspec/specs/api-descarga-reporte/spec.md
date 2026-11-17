# api-descarga-reporte Specification

## Purpose
Permite descargar por HTTP el reporte de facturas en PDF, Excel o TXT aplicando exactamente los mismos filtros que el listado, con el tipo de contenido y el nombre de archivo de cada formato.

## Requirements

### Requirement: Descarga del reporte con formato
`GET /api/facturas/reporte` SHALL responder `200 OK` con los bytes del reporte en el formato pedido por el parámetro `formato`, con el encabezado `Content-Disposition` de tipo `attachment` y un nombre de archivo con la extensión del formato.

#### Scenario: PDF
- **WHEN** se pide `GET /api/facturas/reporte?formato=pdf`
- **THEN** la respuesta es `200`, `Content-Type` es `application/pdf`, `Content-Disposition` es `attachment` con `filename="facturas.pdf"` y el cuerpo empieza con `%PDF`

#### Scenario: Excel
- **WHEN** se pide `GET /api/facturas/reporte?formato=xlsx`
- **THEN** `Content-Type` es `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, el archivo es `facturas.xlsx` y el cuerpo se abre como libro con la hoja `Facturas`

#### Scenario: TXT
- **WHEN** se pide `GET /api/facturas/reporte?formato=txt`
- **THEN** `Content-Type` es `text/plain` con `charset=UTF-8`, el archivo es `facturas.txt` y la primera línea del cuerpo son los nueve encabezados separados por tabulación

### Requirement: Formato predeterminado y alias
Sin `formato`, o con `formato` vacío, el reporte MUST generarse en PDF. `excel` MUST equivaler a `xlsx` y `tsv` a `txt`, y el valor MUST compararse sin distinguir mayúsculas ni espacios exteriores.

#### Scenario: Sin formato
- **WHEN** se pide `GET /api/facturas/reporte`
- **THEN** la respuesta es un PDF descargable `facturas.pdf`

#### Scenario: Alias excel y tsv
- **WHEN** se pide el reporte con `formato=excel` y luego con `formato=tsv`
- **THEN** el primero es el mismo tipo de archivo que `xlsx` y el segundo el mismo que `txt`

#### Scenario: Mayúsculas
- **WHEN** se pide el reporte con `formato=%20XLSX%20`
- **THEN** la respuesta es el archivo Excel

### Requirement: Formato no admitido
Un `formato` distinto de `pdf`, `xlsx`, `excel`, `txt` o `tsv` MUST responder `400 Bad Request` con un detalle que indique el valor recibido y los admitidos, sin generar archivo.

#### Scenario: Formato doc
- **WHEN** se pide `GET /api/facturas/reporte?formato=doc`
- **THEN** la respuesta es `400`, no tiene `Content-Disposition` y el detalle menciona `doc` y los formatos admitidos

### Requirement: Mismos filtros que el listado
El reporte MUST aceptar `fechaDesde`, `fechaHasta`, `estado` y `montoMinimo` con la misma normalización, validación y errores que `GET /api/facturas`, y MUST contener exactamente las facturas que el listado devuelve con esos filtros, en el mismo orden.

#### Scenario: Archivo filtrado igual al listado
- **WHEN** con facturas en los tres estados se pide `GET /api/facturas?estado=EMITIDA` y `GET /api/facturas/reporte?formato=txt&estado=EMITIDA`
- **THEN** las filas del TXT corresponden, en el mismo orden, a los números, fechas, clientes y totales de las facturas del listado, y no aparece ninguna BORRADOR ni ANULADA

#### Scenario: Reporte filtrado sin coincidencias
- **WHEN** se pide `GET /api/facturas/reporte?formato=txt&montoMinimo=999999999`
- **THEN** la respuesta es `200` con solo la línea de encabezados

#### Scenario: Filtro inválido en el reporte
- **WHEN** se pide `GET /api/facturas/reporte?formato=pdf&estado=PAGADA`
- **THEN** la respuesta es `400` igual que en el listado y no se genera archivo

### Requirement: Glifo no soportado en PDF
Si el PDF no puede representar un carácter de los datos filtrados, el reporte MUST responder `400 Bad Request` con un detalle que identifique el carácter por su código Unicode y sugiera Excel o TXT, sin devolver un PDF parcial; los otros formatos MUST seguir disponibles con los mismos filtros.

#### Scenario: Cliente con carácter de uso privado
- **WHEN** con una factura cuyo cliente contiene U+E000 se pide `GET /api/facturas/reporte?formato=pdf`
- **THEN** la respuesta es `400`, el detalle menciona `U+E000` y no hay `Content-Disposition`

#### Scenario: Alternativas disponibles
- **WHEN** con la misma factura se pide el reporte con `formato=xlsx` y con `formato=txt`
- **THEN** ambas respuestas son `200` y contienen el carácter U+E000
