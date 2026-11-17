# Etapa 2 - Lombok, DTO, JPQL y reportes

## Verificaciones y entorno

Motor: **H2 en memoria** (`@DataJpaTest` para la consulta; los exportadores se prueban sin Spring). **PostgreSQL no se probó**: sigue postergado a antes de la etapa 4 (ver `docs/etapa-1.md`); la consulta agrupa todas las columnas no agregadas justamente para que corra igual en PostgreSQL. JDK 17.0.20.1, Gradle 8.10.2.

| Verificación | Resultado |
|---|---|
| Baseline al empezar (`./gradlew clean test`) | 105 pruebas en verde |
| `./gradlew clean test bootJar` al cerrar | **179 pruebas, 0 fallas** (74 nuevas); `build/libs/` solo tiene `facturacion-arca-1.0.0.jar` |
| `jar tf build/libs/facturacion-arca-1.0.0.jar \| grep fonts/` | `fonts/NotoSansCJKsc-Regular.otf` y `fonts/OFL.txt` dentro del JAR |
| `cd frontend && npm test` | 7 pruebas en verde |
| Búsqueda en `report/` de `Math.round`, `double` acumulado y `@Data` | sin resultados; `report/` solo importa `EstadoFactura` de `model/` |

**Pruebas de caracterización** (pasaron al escribirse porque el comportamiento ya existía): `RepresentacionTextualTest` (detalle enlazado), `IgualdadEntidadesTest`, las de `FacturaReporteRepositoryTest` sobre cliente opcional, número nulo, estados, monedas, orden, lista vacía y proyección sin entidades, y varias de totales, TXT, Excel y PDF que la implementación ya cubría al escribirlas. Cada una se validó con una **mutación temporal** que la hace fallar y se restauró (p. ej. `JOIN` interno al cliente, sin `COALESCE`, `f.id ASC`, `@EqualsAndHashCode` con recursión, sin `setScale`, filtro de estado, `UTF_16`, negrita, formato de fecha, filas de total en lista vacía, guion del borrador, `setHeaderRows(0)`, locale de importes, tratamiento del salto de línea en el verificador de glifos). Las demás nacieron en rojo por la razón esperada (aserción).

## Lombok y recursión

Si ambos lados de una relación bidireccional entran en `toString`, `equals` o `hashCode`, la factura imprime sus detalles, cada detalle imprime su factura, y así sin fin: `StackOverflowError` (se comprobó con una mutación temporal en prueba, no queda en la entrega). Además, imprimir o comparar una asociación `LAZY` fuera de la sesión dispara la carga o una `LazyInitializationException`. Por eso la cabecera excluye `detalles` y el detalle excluye `factura` con `@ToString.Exclude`, y no se usa `@Data`. Tampoco se redefine la igualdad: con `IDENTITY` el id es nulo hasta persistir, así que un `equals`/`hashCode` por id cambiaría de valor al guardar y rompería los `HashSet`; se deja la identidad de `Object` y una prueba reflexiva lo fija en las 14 entidades.

Incoherencia encontrada: `AuditoriaApp` declaraba `@ToString.Exclude` en sus tres usuarios, pero ni `EntityId` ni `AuditoriaApp` tenían `@ToString` y las subclases no usaban `callSuper`: las exclusiones eran anotaciones muertas y el `id` no aparecía en ningún `toString` (la prueba de usuarios excluidos pasaba por accidente). Ahora `EntityId` y `AuditoriaApp` generan `toString` y las 14 entidades usan `@ToString(callSuper = true)`.

## DTO y constructor de proyección

`FacturaReporteDTO` es inmutable (campos `final`, `@Getter`, igualdad por valor segura porque no tiene asociaciones) con un único constructor `(Long, Long, LocalDate, String, String, String, Double, Long, EstadoFactura, String)`. Hibernate resuelve `SELECT new` **por posición y tipo**: `COUNT` devuelve `Long`, no `int`; el importe llega como `Double`; el estado llega como `EstadoFactura` y el DTO lo expone como texto (`estado.name()`). Un orden o un tipo equivocado falla al crear la consulta («no matching constructor»). Proyectar directo a DTO evita cargar entidades: la prueba comprueba que, tras la consulta, `Session.getStatistics().getEntityCount()` es 0, mientras que `findAll()` sí deja entidades en el contexto (cliente, renglones, catálogos y auditoría que el reporte no usa).

## Joins de la consulta

```
FROM FacturaVenta f
  LEFT JOIN f.cliente c       -- cliente es opcional (consumidor final): un JOIN interno perdería la factura
  LEFT JOIN f.detalles d      -- una cabecera sin renglones debe aparecer con COUNT(d) = 0
  JOIN f.condicionIva ci      -- FK NOT NULL: el join interno no pierde filas
  JOIN f.puntoVenta pv        -- ídem
  JOIN f.tipoMoneda m         -- ídem; el código es m.codigoAfip (PES, DOL)
```

`COALESCE(c.denominacion, 'Consumidor Final')` completa el cliente ausente; `COUNT(d)` ignora los nulos del lado opcional. El `GROUP BY` lista **todas** las columnas no agregadas (no solo `f.id`): PostgreSQL acepta la dependencia funcional de la clave primaria solo para columnas de la misma tabla, no de las unidas. Orden: `f.fechaEmision DESC, f.id DESC`. La consulta vive en dos constantes (selección y joins / agrupación y orden) para que la etapa 3 inserte un `WHERE` con parámetros nombrados sin reescribirla.

## Criterio de totales por estado y moneda

`TotalesPorMoneda` suma **solo facturas EMITIDA**, separadas por código de moneda y sin convertir; una moneda presente sin emitidas queda en 0.00; lista vacía, sin totales. Usa `BigDecimal.valueOf(double)` y `setScale(2, HALF_UP)`, nunca aritmética `double` ni `Math.round` (`0.1 + 0.2` en `double` da `0.30000000000000004`). En Excel el total es una fórmula `SUMIFS($G$2:$G$n,$H$2:$H$n,"EMITIDA",$I$2:$I$n,I<fila>)` por moneda (sigue siendo correcta si alguien edita una celda); se evalúa con `XSSFFormulaEvaluator` para que el archivo traiga el resultado y se pide `setForceFormulaRecalculation(true)`. El TXT no lleva totales. La fecha de generación del PDF usa un `Clock` inyectado (`RelojConfig`) y se imprime en `America/Argentina/Buenos_Aires` aunque la JVM esté en otra zona (con `java -jar` la zona no está forzada).

## Estrategia de preservación de texto

- **TXT**: UTF-8 sin BOM, `\t` entre campos, `\n` fijo, una línea física por factura, encabezado en la primera línea (decisión del estudiante, 2026-11-12). Escape en este orden: `\` → `\\`, TAB → `\t`, CR → `\r`, LF → `\n`; la barra va **primero** porque si no se re-escaparían las barras recién agregadas. Las comillas quedan literales (no es CSV). Se recupera el original con un decodificador de **un solo recorrido** (ve una barra y mira el siguiente carácter); encadenar `replace` al revés rompería `C:\nuevo`, caso fijado por una prueba. El decodificador vive solo en las pruebas.
- **Excel**: las celdas de texto guardan el texto tal cual (sin escapes); número, ítems y total son numéricos y la fecha una celda de fecha.
- **PDF**: fuente Noto Sans CJK SC incrustada con `Identity-H` (OpenPDF subsetea los glifos usados) y, **antes** de abrir el documento, `VerificadorGlifos` recorre cada texto por code point (no por `char`, para no partir pares sustitutos) con `BaseFont.charExists`. OpenPDF omite en silencio los glifos ausentes, y después ya no se sabe cuál faltó. El primero sin glifo lanza `GlifoNoSoportadoException` con `U+XXXX`, el campo y la fila; no se devuelve ningún byte y **no se sustituyen caracteres**. TAB y CR se rechazan (el PDF no puede representarlos); LF se admite como corte de línea. Excel y TXT siguen disponibles con la misma lista. El código HTTP queda para la etapa 3.

## Fuente del PDF

`NotoSansCJKsc-Regular.otf`, descargada del repositorio oficial **`notofonts/noto-cjk`** (GitHub, `Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf`); licencia en `Sans/LICENSE`, copiada a `OFL.txt` (**SIL Open Font License 1.1**, que exige distribuirla con la fuente). 16 437 364 bytes, SHA-256 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`. Ambos archivos están en `src/main/resources/fonts/` y viajan dentro del JAR (verificado). Se versiona por **commit directo, sin Git LFS** (decisión del estudiante): con LFS, quien clone sin `git-lfs` obtendría un puntero y el PDF fallaría; `.gitattributes` marca `*.otf` como binario. La fuente se lee del classpath de forma perezosa (Excel, TXT y el arranque no dependen de ella); si falta, `IllegalStateException` que nombra el archivo. «Noto Sans SC» de Google Fonts es otro archivo y no cumple el nombre de la consigna.

## Muestras y revisión visual

La prueba `MuestrasReportesTest` escribe en `build/muestras-reportes/` (ignorado por Git, borrado por `clean`; los exportadores no escriben nada): `facturas.pdf`, `facturas-varias-paginas.pdf` (80 filas, 3 páginas), `facturas.xlsx` y `facturas.txt`, con borrador, factura sin cliente, `PES` y `DOL`, acentos, ñ, comillas, `«»` y texto chino; TXT y Excel además llevan barra invertida, tabulación y salto de línea. Hallazgo de entorno: `PageSize.A4.rotate()` deja el rectángulo en 842 × 595 con `/Rotate 90`, así que el tamaño visible se lee con `getPageSizeWithRotation` (con `getPageSize` da 595 × 842). Revisión automática: extracción de texto con `PdfTextExtractor` (que no distingue el aspecto) y un vistazo a las dos páginas en el visor de PDF del navegador (apaisado, encabezado, ñ, acentos y caracteres chinos legibles). **Revisión visual del estudiante (2026-11-17)**: abrí las cuatro muestras (los dos PDF, el Excel y el TXT) y confirmé que se ven bien: orientación apaisada, encabezados, guion en los borradores, acentos, ñ y caracteres chinos legibles, y totales por moneda.

## Semilla reutilizada (DD-13) y referencia del docente

Origen: mi proyecto previo `api-facturas` (`dto/FacturaReporteDTO`, la consulta `SELECT new` en `FacturaService`, `exportToPdf` y `exportToExcel` del controlador) y los TPs de JPQL. Cada pieza entró solo detrás de una prueba en rojo.

| Se trajo | Se adaptó | Es nuevo |
|---|---|---|
| Nombres de los 9 campos del DTO y `@Getter`; `COALESCE(..., 'Consumidor Final')`, `LEFT JOIN` de cliente, joins a condición IVA y punto de venta, `GROUP BY` con `f.id`, orden por fecha e id descendentes; secuencia `Document` → `PdfWriter` → `PdfPTable` → `close` | `Date` a `LocalDate`; DTO inmutable (sin setters ni `@AllArgsConstructor`) con constructor `(Long, …, EstadoFactura, …)`; **`JOIN f.detalles` interno a `LEFT JOIN`** (la semilla perdía las cabeceras sin renglones: la prueba «cabecera sin renglones» lo expuso en rojo); `m.codigoAfip` al `SELECT` y al `GROUP BY` (DD-13: la semilla no tenía moneda); consulta del servicio a un fragmento de repositorio; PDF a `byte[]` en vez de `HttpServletResponse`, vertical a A4 apaisado, 7 a 9 columnas, `null` impreso a `-`, fuente Helvetica a Noto incrustada; el `exportToExcel` de la semilla era **texto tabulado** con extensión `.xls`: es el antecedente del TXT, corregido (escapes reversibles, UTF-8, `\n` fijo, fechas ISO, importes con dos decimales, vacío en vez de `null`) | `ReporteFacturasService`, totales por moneda, Excel real con Apache POI, `EscapeTxt`, `VerificadorGlifos`, `GlifoNoSoportadoException`, `RelojConfig`, filtros diferidos a la etapa 3, todas las pruebas |

Los filtros dinámicos de la semilla no se trajeron (son de la etapa 3). Referencia del docente (`arca-spirng`): consultada **solo para nombres y estructura** (`FacturaReporteRepository` + `Impl` con `buscarReporte`, `ReporteFacturasService`, carpeta `fonts/`); no se leyó ni copió implementación. Me aparto en la firma: los métodos devuelven `byte[]` como pide la consigna y el tipo de contenido es asunto de la etapa 3.

## Decisiones

Los cambios de `toString` y la igualdad por identidad (D1); DTO inmutable de un constructor (D2); fragmento de repositorio con `EntityManager` (D3) y consulta en dos piezas (D4); un servicio con auxiliares puros (D5); totales con `BigDecimal` (D6); TXT con escape reversible (D7); Excel con `SUMIFS` evaluado (D8); PDF con fuente perezosa (D9) y verificación de glifos previa (D10); fuente por commit directo (D11); `Clock` en hora de Argentina (D12); muestras solo en `build/` (D13); pruebas por capa (D14). Se separó `ReportePdf` del servicio para que este no crezca de más. Para que `FacturaReporteRepositoryTest` (paquete `repository`) reutilice `DatosDePrueba`, `PersistenciaBase` y `FacturaBase`, esas clases de prueba pasaron a ser públicas.

## Uso de IA

- Herramientas: Claude Code (Anthropic) con sub-agentes de OpenSpec (propuesta, diseño, tareas y ejecución de la etapa).
- Qué se pidió: la propuesta y el diseño de la etapa, las pruebas en rojo, el código mínimo para pasarlas, las mutaciones de validación de las pruebas de caracterización, la ejecución de las verificaciones, las muestras y este informe.
- Qué se revisó y qué se corrigió: revisé la propuesta y tomé tres decisiones: descargar la fuente NotoSansCJKsc-Regular.otf del repositorio oficial notofonts/noto-cjk, versionarla con commit directo en lugar de Git LFS, y que el TXT lleve línea de encabezado. Abrí las cuatro muestras de los reportes y confirmé que se ven bien. Con una guía de revisión que me armó la IA leí y verifiqué la consulta `SELECT new` (`LEFT JOIN` de cliente y renglones, `COALESCE`, `COUNT` con `GROUP BY`), el constructor de `FacturaReporteDTO`, los totales con `BigDecimal` solo de facturas emitidas y por moneda, las fórmulas `SUMIFS` del Excel, el escape del TXT, la fuente incrustada con Identity-H y la verificación de glifos, y el `Clock` en hora argentina. La etapa corrigió un error de la etapa 1: las exclusiones de `@ToString` de la superclase de auditoría no tenían efecto.
- Los commits propuestos llevan la línea `Co-Authored-By`.
