# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos verificables: ver `specs/` (5 capacidades nuevas y 1 modificada).

Estado actual y restricciones que condicionan el enfoque:

- Etapa 1 archivada: 14 entidades, 2 superclases, 11 repositorios, `DataLoader` provisorio excluido del perfil `test`, 105 pruebas en verde. `open-in-view: false`; zona de la JVM fijada a `America/Argentina/Buenos_Aires` solo en `test` y `bootRun` (no en `java -jar`).
- `build.gradle` **ya declara** `com.github.librepdf:openpdf:2.0.3` y `org.apache.poi:poi-ooxml:5.3.0`, y Lombok como `compileOnly` + `annotationProcessor`: **no cambia en esta etapa**. Verificado en la caché de Gradle: el JAR de OpenPDF 2.0.3 conserva el paquete `com.lowagie.text` (el cambio de paquete a `org.openpdf` es de la 3.x). Lo que dice `CHANGES.md` («adaptar OpenPDF 1.3.36 `com.lowagie` a 2.0.3») se reduce a adaptar el **uso** de la API, no los imports.
- `dto/`, `report/` y `service/` solo tienen `package-info.java`. `FacturaVentaRepository` es un `JpaRepository` vacío.
- **Lombok hoy**: todas las entidades usan `@Getter`/`@Setter`/`@NoArgsConstructor`/`@ToString` con `@ToString.Exclude` en cada asociación; ninguna usa `@Data` ni `@EqualsAndHashCode`, de modo que la igualdad es la de `Object` (identidad) y no puede recursionar. `RepresentacionTextualTest` ya cubre factura con detalles y usuario sin clave. **Incoherencia real**: `AuditoriaApp` declara `@ToString.Exclude` en sus tres usuarios, pero ni `EntityId` ni `AuditoriaApp` tienen `@ToString` y ninguna subclase usa `callSuper`; esas exclusiones son anotaciones muertas, el `id` no aparece en ningún `toString` y la prueba `entidadAuditableNoMuestraLosUsuariosDeAuditoria` pasa por accidente. Es lo único que la «higiene Lombok» de la consigna («exclusiones coherentes con la herencia») deja por hacer.
- **Fuente del PDF**: `NotoSansCJKsc-Regular.otf` no está en el repositorio. La consigna la nombra textualmente: «Incrusten NotoSansCJKsc-Regular.otf con Identity-H, conservando su licencia OFL.» Obtenerla exigía una descarga externa que el estudiante aprobó el 2026-11-12 (ver D11 y Open Questions).
- Autoridad: consigna (Etapa 2, ítems 1-14 y entregable) > reglas duras de `CLAUDE.md` > KB > `CHANGES.md` > semilla. TDD estricto: ninguna línea de la semilla entra sin un test en rojo.
- Gobernanza MEDIA: se implementa por grupos con punto de control al final de cada uno.

### Inventario de la semilla (DD-13)

Semilla principal `api-facturas` (Spring Boot 4.1.1, Maven, `com.ejemplo.apifacturas`): `dto/FacturaReporteDTO.java`, `service/FacturaService.buscarFacturasFiltradas` (la consulta `SELECT new` está en el servicio, no en un repositorio) y `controller/FacturaRestController` (`exportToPdf`, `exportToExcel`). Secundaria: `JPA/TP3_JPQL_facturacion_arca/.../ConsultasJPQL.java` (idéntica en `tp-desarrollo/`).

| Semilla | Se trae | Se adapta (motivo) | Es nuevo |
|---|---|---|---|
| `FacturaReporteDTO` | nombres de los 9 campos que tenía (`id`, `numeroFactura`, `fechaEmision`, `clienteDenominacion`, `condicionIva`, `puntoVentaDescripcion`, `importeTotal`, `cantidadItems`, `estado`), `@Getter` | `Date` → `LocalDate`; se quitan `@Setter`, `@AllArgsConstructor` y `@NoArgsConstructor` (DTO inmutable); el constructor recibe `Double`, `Long` (COUNT) y `EstadoFactura` y convierte el estado a texto | campo `codigoMoneda` (la semilla no tenía moneda) |
| Consulta `SELECT new` | `COALESCE(c.denominacion, 'Consumidor Final')`, `LEFT JOIN f.cliente`, `JOIN` a condición IVA y punto de venta, `GROUP BY` con `f.id`, `ORDER BY f.fechaEmision DESC, f.id DESC` | `JOIN f.detalles` → **`LEFT JOIN`** (la semilla perdía las cabeceras sin renglones: un test en rojo lo demuestra); se agrega `JOIN f.tipoMoneda m` y `m.codigoAfip` al SELECT y al GROUP BY; se mueve del servicio a un fragmento de repositorio; FQN del paquete nuevo | — |
| Filtros dinámicos de la semilla (`fechaDesde`, `fechaHasta` con `finDelDia`, `estado`, `montoMinimo`) | nada en esta etapa | se difieren a C-04 (la consigna los define en la etapa 3); con `LocalDate` `finDelDia` deja de hacer falta | — |
| `exportToPdf` | la secuencia `Document` → `PdfWriter` → `PdfPTable` → `close` (misma API `com.lowagie.text`) | escribía en `HttpServletResponse` → `byte[]`; `new Document()` vertical → `PageSize.A4.rotate()`; 7 columnas → 9; `String.valueOf(null)` imprimía `null` en borradores → `-`; `String.valueOf(double)` → `BigDecimal` con 2 decimales; Helvetica implícita → Noto incrustada | encabezado repetido, título/fecha/cantidad, mensaje sin resultados, totales, verificación de glifos |
| `exportToExcel` | nada para Excel: era **texto tabulado** con extensión `.xls` y `application/vnd.ms-excel` | es el antecedente del **TXT**, corregido: escapes reversibles, `null` → vacío, fechas ISO, importes `0.00`, UTF-8 explícito, `\n` fijo, moneda | Excel real con Apache POI |
| `ConsultasJPQL` (TP3) | solo conceptos (`LEFT JOIN a.marca`, `GROUP BY pv.id, pv.descripcion` con `COUNT`) | — | — |
| — | — | — | fragmento de repositorio, `ReporteFacturasService`, totales por moneda, escape TXT, verificación de glifos, excepción de glifo, bean `Clock`, pruebas |

### Referencia del docente consultada (`arca-spirng`, solo lectura)

Se consultaron **solo nombres y estructura**, sin leer ni copiar implementación: existen `repository/FacturaReporteRepository` (+`Impl`) con un método `buscarReporte`, `FacturaVentaRepository` que extiende ese fragmento, `report/ReporteFacturasService` y `src/main/resources/fonts/` con `NotoSansCJKsc-Regular.otf` (16 437 364 bytes) y `OFL.txt`. Se adoptan esos nombres porque coinciden con la KB (Flujo 2.1) y con la convención `Impl` de Spring Data. Se aparta en la firma: aquí los métodos devuelven `byte[]` como pide la consigna («devuelvan los bytes de cada formato»); el tipo de contenido y el nombre del archivo son asunto del endpoint de C-04. Se declara en `docs/etapa-2.md`.

## Goals / Non-Goals

**Goals:**
- Cada regla de la Parte B y la Parte C de la consigna queda fijada por una prueba que falla sin ella, con valores esperados literales (no recalculados como lo hace el código).
- La proyección se prueba contra H2 con datos persistidos y releídos; los exportadores se prueban sin Spring, a partir de listas de DTO construidas en la prueba.
- Que el estudiante pueda explicar cada join, el orden del constructor, la elección `SUMIFS` y el escape del TXT.

**Non-Goals:**
- Filtros de consulta, normalización del estado y códigos 400 (C-04). La consulta se arma en piezas para que C-04 inserte el `WHERE` dinámico con parámetros sin reescribirla (RN-FI-07), pero en esta etapa no se escribe código de filtros sin su prueba.
- Endpoints y mapeo HTTP de `GlifoNoSoportadoException` (C-04; IN-03 propone 400).
- Demostración de la variante recursiva incorrecta de Lombok: la consigna la permite solo en prueba o rama descartable; no se incluye en la entrega. El informe explica el mecanismo.
- Streaming o paginación de reportes (DD-08: en memoria).
- Cambiar `build.gradle`, versiones o el `DataLoader`.

## Decisions

### D1 — Herencia de `@ToString`: `EntityId` y `AuditoriaApp` generan `toString` y las subclases usan `callSuper = true`
`EntityId` lleva `@ToString` (muestra `id`); `AuditoriaApp` lleva `@ToString(callSuper = true)` (fechas de alta, modificación y baja; usuarios ya excluidos); las 14 entidades pasan a `@ToString(callSuper = true)` conservando sus `@ToString.Exclude`.
- **Por qué**: hace efectivas las exclusiones declaradas en la superclase y agrega el dato más útil al depurar (el `id`), que hoy falta. Es exactamente «exclusiones coherentes con la herencia».
- **Alternativa descartada**: borrar los `@ToString.Exclude` muertos de `AuditoriaApp`. Elimina la incoherencia pero deja el `id` fuera del `toString` y pierde la documentación de qué no debe imprimirse.
- **Alternativa descartada**: `@EqualsAndHashCode(of = "id")` o igualdad por id. Con `IDENTITY` el id es nulo antes de persistir y el hash cambiaría al guardar, rompiendo `HashSet`. Se mantiene la identidad de `Object` y se fija con prueba (ninguna entidad redefine `equals`/`hashCode`).
- **Honestidad TDD**: los escenarios de «detalle enlazado», «igualdad por identidad» y «ninguna entidad redefine la igualdad» ya se cumplen hoy; sus pruebas son **de caracterización** (pasan al escribirse) y se declaran así. Solo «identificador heredado visible» y, en rigor, «usuarios de auditoría excluidos» (que hoy pasa por accidente) motivan el cambio: la primera arranca en rojo.

### D2 — `FacturaReporteDTO` como clase inmutable con un único constructor de proyección
Clase en `dto/` con campos `final`, `@Getter`, `@ToString` y `@EqualsAndHashCode` (un DTO no tiene asociaciones, así que generar igualdad por valor es seguro y útil en las pruebas). Un solo constructor explícito, en este orden y con estos tipos: `(Long id, Long numeroFactura, LocalDate fechaEmision, String clienteDenominacion, String condicionIva, String puntoVentaDescripcion, Double importeTotal, Long cantidadItems, EstadoFactura estado, String codigoMoneda)`. Guarda `double importeTotal`, `long cantidadItems` y `String estado = estado.name()`.
- **Por qué**: la consigna pide que el constructor acepte `Long` para `COUNT` y `EstadoFactura`, y que el DTO exponga texto. Hibernate resuelve `SELECT new` por **posición y tipo**: un único constructor hace visible ese contrato y un error de orden falla al crear la consulta.
- **Alternativa descartada**: `record`. Su constructor canónico tendría `String estado`, obligando a un segundo constructor con `EstadoFactura`; dos constructores de 10 parámetros que difieren en un tipo son más difíciles de defender que uno.
- **`importeTotal` como `Double`** en el constructor porque es lo que entrega Hibernate 6.5 para un atributo `double` en una expresión de selección; la primera prueba de proyección lo confirma.

### D3 — Fragmento de repositorio `FacturaReporteRepository` + `FacturaReporteRepositoryImpl`
`FacturaVentaRepository extends JpaRepository<FacturaVenta, Long>, FacturaReporteRepository`; el `Impl` recibe el `EntityManager` por constructor y su método `buscarReporte()` es `@Transactional(readOnly = true)`.
- **Por qué**: lo pide la consigna («fragmento de repositorio con EntityManager»); mantiene un único punto de acceso a facturas (la regla de etapa 1 de «acceso solo por raíces» no se altera: no aparece un repositorio nuevo de entidad).
- **Alternativa descartada**: `@Query` en la interfaz. No admite el armado dinámico que C-04 necesita; Criteria/Specifications contradicen DD-06.

### D4 — La consulta JPQL
```
SELECT new ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO(
  f.id, f.numero, f.fechaEmision, COALESCE(c.denominacion, 'Consumidor Final'),
  ci.denominacion, pv.descripcion, f.importeTotal, COUNT(d), f.estado, m.codigoAfip)
FROM FacturaVenta f
  LEFT JOIN f.cliente c
  LEFT JOIN f.detalles d
  JOIN f.condicionIva ci
  JOIN f.puntoVenta pv
  JOIN f.tipoMoneda m
GROUP BY f.id, f.numero, f.fechaEmision, c.denominacion, ci.denominacion,
         pv.descripcion, f.importeTotal, f.estado, m.codigoAfip
ORDER BY f.fechaEmision DESC, f.id DESC
```
- `LEFT JOIN` a cliente conserva la factura sin cliente; `LEFT JOIN` a detalles conserva la cabecera sin renglones y `COUNT(d)` cuenta 0 porque `COUNT` ignora los nulos del lado opcional. Con `JOIN` interno (semilla) ambas desaparecen.
- Los joins internos a condición IVA, punto de venta y moneda son seguros porque esas FK son `NOT NULL` (etapa 1).
- Se agrupan **todas** las columnas no agregadas, no solo `f.id`: PostgreSQL acepta dependencia funcional de la PK solo para columnas de la misma tabla, no de las unidas. Así la misma consulta corre en H2 y en PostgreSQL.
- `'Consumidor Final'` es un literal de la consulta, no dato del usuario; los parámetros nombrados son para los filtros de C-04 (RN-FI-08: «parámetros, no concatenación»).
- **Código de moneda = `TipoMoneda.codigoAfip`** (`PES`, `DOL`; SU-09).
- La consulta se guarda en dos constantes (`SELECT ... FROM ... JOIN ...` y `GROUP BY ... ORDER BY ...`): C-04 inserta entre ambas el `WHERE` con parámetros.

### D5 — `ReporteFacturasService` en `report/`: tres métodos públicos, auxiliares puros
`@Service` con constructor `(Clock reloj)` y métodos `byte[] generarPdf(List<FacturaReporteDTO>)`, `byte[] generarExcel(...)`, `byte[] generarTxt(...)`. Auxiliares en el mismo paquete, sin estado y probados por separado: `ColumnasReporte` (los 9 encabezados compartidos: Número, Fecha, Cliente, Condición IVA, Punto de venta, Ítems, Total, Estado, Moneda), `TotalesPorMoneda`, `EscapeTxt`, `VerificadorGlifos`, `GlifoNoSoportadoException`.
- **Por qué**: la consigna nombra la clase y pide que reciba DTO y devuelva bytes; los auxiliares permiten probar cada regla con funciones puras (totales, escape) sin abrir un PDF.
- **Alternativa descartada**: tres servicios (`ReportePdf`, `ReporteExcel`, `ReporteTxt`). Más clases sin ganancia para C-04, que solo necesita un punto de entrada.

### D6 — Totales: `TotalesPorMoneda` con `BigDecimal` y `HALF_UP`
`SortedMap<String, BigDecimal> calcular(List<FacturaReporteDTO>)`: inicializa en `0.00` **cada** código de moneda presente en la lista (cualquier estado), suma `BigDecimal.valueOf(importeTotal)` solo si `estado` es `EMITIDA` (`EstadoFactura.EMITIDA.name()`) y devuelve cada total con `setScale(2, RoundingMode.HALF_UP)`. Orden alfabético por código (`TreeMap`) para un resultado determinista.
- **Por qué**: regla dura (nada de `double` acumulado ni `Math.round`); `BigDecimal.valueOf(double)` usa la representación decimal canónica del `double` (0.1 → `0.1`), así `0.10 + 0.20 + 0.70 = 1.00` exacto.
- La inicialización con todas las monedas presentes implementa «moneda sin emitidas → total cero».
- Lista vacía → mapa vacío → el PDF no muestra totales y el Excel no agrega filas de total.

### D7 — TXT
Encabezado con los 9 nombres de `ColumnasReporte` + una línea por factura; campos unidos con `\t`, cada línea terminada en `\n` (fijo, no el separador del sistema operativo), bytes `UTF-8` sin BOM. Número vacío en borrador; fecha `LocalDate.toString()` (ISO); total `BigDecimal.valueOf(importeTotal).setScale(2, HALF_UP).toPlainString()`; ítems entero. `EscapeTxt.escapar` reemplaza en este orden `\` → `\\`, TAB → `\t`, CR → `\r`, LF → `\n` (la barra primero, si no se re-escaparían las barras recién introducidas). Las comillas quedan literales: no es CSV.
- El **decodificador** vive en el código de prueba como lector de un único recorrido de izquierda a derecha (ve `\`, mira el carácter siguiente). Encadenar `replace` en sentido inverso sería incorrecto (`C:\\nuevo` terminaría con un salto de línea); el escenario «barra invertida seguida de letra» lo fija. No se agrega a producción porque nada lo usa.
- **Encabezado incluido**: la consigna exige «una fila física por factura» y «sin pie de totales»; un encabezado no contradice ninguna de las dos y hace el archivo autodescriptivo. Ver Open Questions (no bloqueante).

### D8 — Excel
`XSSFWorkbook`, hoja `Facturas`. Fila 1: encabezados con estilo negrita y relleno gris. Filas 2..n+1: número (numérico, formato `0`; **celda en blanco** en borrador), fecha (numérica con formato `dd/mm/yyyy`), cliente, condición IVA, punto de venta (texto), ítems (numérico `0`), total (numérico `#,##0.00`), estado y moneda (texto). Tras una fila vacía, una fila por moneda de `TotalesPorMoneda.calcular(...).keySet()`: columna A `Total facturado (EMITIDA)`, columna I el código, columna G `SUMIFS($G$2:$G$n+1,$H$2:$H$n+1,"EMITIDA",$I$2:$I$n+1,$I$k)`. Luego `XSSFFormulaEvaluator.evaluateAllFormulaCells(libro)`, `libro.setForceFormulaRecalculation(true)` y `autoSizeColumn` en las 9 columnas.
- **Por qué `SUMIFS` y no el valor calculado en Java**: lo exige la consigna; además el total sigue siendo correcto si alguien edita una celda en Excel. El resultado cacheado lo calcula POI con aritmética binaria del propio Excel; las pruebas lo comparan redondeado a 2 decimales con `BigDecimal`, sin tolerancias.
- El rango de `SUMIFS` termina en la última fila de detalle, nunca incluye las filas de total.
- Las celdas del total guardan el `double` del DTO tal cual (el dato persistido ya tiene 2 decimales por la regla de ida y vuelta de etapa 4); el formato solo cambia la presentación.

### D9 — PDF
`Document(PageSize.A4.rotate(), 36, 36, 36, 36)` + `PdfWriter` sobre `ByteArrayOutputStream`. Título «Reporte de facturas» (14 pt), línea «Generado: dd/MM/yyyy HH:mm (hora de Argentina) — Registros: N» (9 pt), tabla `PdfPTable(9)` al 100 % con anchos relativos, `setHeaderRows(1)` (OpenPDF repite la fila 1 en cada página), celdas de 8 pt; lista vacía → párrafo «No hay facturas para informar.» sin tabla ni totales; después de la tabla, un párrafo por moneda «Total facturado (EMITIDA) PES: 1.000,00». Número `-` en borrador. Importes con `DecimalFormat("#,##0.00")` y símbolos `es-AR` aplicados a `BigDecimal` (sin aritmética `double`).
- **Fuente**: `BaseFont.createFont("NotoSansCJKsc-Regular.otf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null)` con los bytes leídos una vez del classpath (`fonts/NotoSansCJKsc-Regular.otf`) y guardados en un campo inicializado de forma perezosa y segura entre hilos. Perezosa para que Excel y TXT (y el arranque del contexto) no dependan de la fuente. Si el recurso falta, `IllegalStateException` con un mensaje que nombra el archivo y remite al README. La ruta del recurso es una constante que el constructor público usa por defecto; un constructor de visibilidad de paquete recibe otra ruta solo para probar el caso de fuente ausente sin borrar el archivo real. OpenPDF subsetea la fuente con `Identity-H`: el PDF incrusta solo los glifos usados.
- **Licencia**: `src/main/resources/fonts/OFL.txt` viaja en el JAR junto a la fuente (la OFL exige distribuir la licencia con la fuente).

### D10 — Glifos no disponibles: verificar antes de dibujar
Antes de abrir el `Document`, `VerificadorGlifos` recorre por **code point** (`String.codePoints()`, no `char`, para no partir pares sustitutos) cada texto que se va a imprimir: los dinámicos de cada fila (cliente, condición IVA, punto de venta, estado, moneda) y los fijos (título, encabezados, mensajes). Para cada uno consulta `BaseFont.charExists(cp)`. El primer faltante lanza `GlifoNoSoportadoException` (unchecked, en `report/`) con `codePoint`, el nombre del campo y el número de fila, y mensaje `El carácter U+E000 del campo cliente (fila 3) no está disponible en la fuente del PDF; exportá en Excel o TXT.` (`String.format("U+%04X", cp)`). No se devuelve ningún byte.
- **Por qué antes y no durante**: OpenPDF omite en silencio los glifos ausentes; verificar después ya no permite saber qué faltó. Verificar primero también evita gastar la generación entera.
- **Salto de línea (LF)**: OpenPDF lo interpreta como corte de línea dentro de la celda, no como glifo; se admite. El resto de los caracteres de control (TAB, CR) se verifica como cualquier otro y, al no tener glifo, se rechaza: el PDF no puede representarlos y TXT/Excel sí los preservan.
- El código HTTP (IN-03, propuesto 400) se decide en C-04.

### D11 — Adquisición de la fuente (RESUELTA el 2026-11-12: descarga oficial, commit directo)
- **Archivo**: `NotoSansCJKsc-Regular.otf`, Noto Sans CJK SC Regular, OpenType/CFF, ~16,4 MB (16 437 364 bytes en la copia local consultada). Licencia **SIL Open Font License 1.1**.
- **Fuente oficial**: repositorio `notofonts/noto-cjk` de GitHub, `Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf` (https://github.com/notofonts/noto-cjk/tree/main/Sans/OTF/SimplifiedChinese), o el ZIP de la última versión «Sans» en https://github.com/notofonts/noto-cjk/releases; licencia en `Sans/LICENSE` del mismo repositorio. **Google Fonts** distribuye «Noto Sans SC», que es **otro archivo** (TTF/variable, otro nombre), así que no cumple el nombre exacto que fija la consigna.
- **Destino**: `src/main/resources/fonts/NotoSansCJKsc-Regular.otf` y `src/main/resources/fonts/OFL.txt` (texto de `Sans/LICENSE`).
- **Fuente más chica**: no aplica. La consigna no pide «una fuente OFL» sino `NotoSansCJKsc-Regular.otf` por nombre; sustituirla sería apartarse de la consigna.
- **Git LFS vs commit directo**: se recomienda **commit directo**. 16 MB está lejos del límite de GitHub (aviso a 50 MB, rechazo a 100 MB); con LFS, quien clone sin `git-lfs` (por ejemplo el docente) obtiene un archivo puntero y el PDF falla con un error de fuente, y LFS agrega una herramienta más al entorno reproducible. Costo aceptado: el historial crece 16 MB una vez.
- **Alternativa sin descarga**: existe una copia local en `arca-spirng/src/main/resources/fonts/` (con su `OFL.txt`). Es un binario de terceros con licencia OFL, no código del docente, pero el origen declarado sería el repositorio del docente. Si el estudiante la elige, conviene comparar su SHA-256 con el de la fuente oficial y declararlo en el informe. **La decisión es del estudiante.**
- **Resuelto el 2026-11-12 por el estudiante**: descargada del repositorio oficial `notofonts/noto-cjk` (opción a), con licencia de `Sans/LICENSE`, y versionada por commit directo (sin Git LFS). El orquestador ya dejó los archivos en `src/main/resources/fonts/` (SHA-256 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`); los grupos 7 y 8 quedan desbloqueados.

### D12 — Fecha de generación: `Clock` inyectado, zona fija Argentina
`config/RelojConfig` expone `@Bean Clock reloj()` = `Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"))`. El servicio calcula `ZonedDateTime.ofInstant(reloj.instant(), ZONA_REPORTE)` con `ZONA_REPORTE` = esa misma zona, de modo que la hora impresa no depende ni de la zona de la JVM ni de la del `Clock`. Las pruebas usan `Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"), ZoneOffset.UTC)` y esperan `12/11/2026 15:30`.
- **Por qué Argentina**: DD-12 la fija como zona del proyecto y la zona de la JVM solo está forzada en `test` y `bootRun`, no al correr el JAR (`java -jar`); depender de la zona del sistema daría horas distintas según la máquina. Resuelve la pregunta de prioridad Baja de la KB («zona horaria del servidor vs. Argentina»).
- **Alternativa descartada**: `LocalDateTime.now()`. No es determinista en pruebas (`java-junit`: inyectar `Clock`).

### D13 — Muestras para revisión visual en `build/muestras-reportes/`
Una prueba `MuestrasReportesTest` genera `facturas.pdf`, `facturas.xlsx`, `facturas.txt` y `facturas-varias-paginas.pdf` a partir de una lista representativa (3 EMITIDA en `PES` y `DOL`, 1 ANULADA, 2 BORRADOR, una sin cliente, textos con `Ñ`, acentos, comillas, `«»` y CJK; para TXT y Excel se agrega además un cliente con barra invertida, tabulación y salto de línea, que no entra en la muestra PDF porque D10 lo rechazaría) y verifica que cada archivo existe y no está vacío. `build/` ya está en `.gitignore` y `./gradlew clean` lo borra; la ruta es relativa al directorio del proyecto (directorio de trabajo de las pruebas de Gradle).
- **Por qué una prueba y no una tarea Gradle**: no modifica `build.gradle` y se regenera con cada `./gradlew test`. Las muestras PDF dependen de la fuente (D11).
- **Alternativa**: guardar copias en `docs/muestras/` para la entrega. Ver Open Questions.

### D14 — Pruebas por capa
- Lombok: unitarias en `model/RepresentacionTextualTest` (extendida) y `model/IgualdadEntidadesTest` (reflexión sobre el metamodelo: `getMethod("equals", Object.class).getDeclaringClass() == Object.class`).
- Proyección: `repository/FacturaReporteRepositoryTest` con `@DataJpaTest` + `TestEntityManager`, reutilizando `DatosDePrueba`/`FacturaBase` y `UsuarioActual` en `@BeforeEach`/`@AfterEach`; persistir, `flush()` + `clear()`, consultar. «Sin cargar entidades»: tras `clear()` y la consulta, `Session.getStatistics().getEntityCount() == 0`.
- Exportadores: unitarias sin Spring (`new ReporteFacturasService(Clock.fixed(...))`) que **leen el resultado con la misma biblioteca**: `XSSFWorkbook` para Excel; `PdfReader` + `PdfTextExtractor` y el diccionario de fuentes de la página para PDF; `new String(bytes, UTF_8)` para TXT. Un `FacturasDeEjemplo` (soporte de pruebas en `report/`) construye DTO legibles.
- Contexto: `FacturacionApplicationTests` comprueba que el contexto tiene `ReporteFacturasService` y un `Clock` en zona Argentina.

## Risks / Trade-offs

- [`PdfTextExtractor` no reconstruye bien texto CJK/`Identity-H`] → OpenPDF escribe un `ToUnicode` para fuentes `Identity-H`, lo que permite la extracción; si en la práctica falla para algún carácter, la prueba de preservación se reduce al texto latino y el CJK se verifica en la revisión visual, declarándolo en el informe.
- [`charExists` no detecta algún glifo ausente (p. ej. variantes o combinantes)] → el escenario usa U+E000 (área de uso privado, seguro ausente); la revisión visual de la muestra completa es la verificación final que pide la consigna.
- [La fuente de 16 MB vuelve lentas las pruebas de PDF] → bytes y `BaseFont` se cargan una vez por instancia del servicio; las pruebas de PDF comparten una instancia por clase.
- [`autoSizeColumn` depende de fuentes AWT del sistema operativo] → funciona en Windows; en un servidor sin fuentes podría fallar o calcular anchos raros. Aceptado (entorno local); se documenta en el README.
- [Constructor de proyección con tipo incorrecto] → Hibernate falla al crear la consulta con un error de «no matching constructor»; la primera prueba de proyección lo expone de inmediato.
- [`GROUP BY` con todas las columnas es verboso] → es lo portable entre H2 y PostgreSQL; se explica en el informe.
- [La prueba de muestras escribe archivos] → solo bajo `build/`, ignorado por Git y limpiado por `clean`; los exportadores en sí no escriben nada (spec `exportacion-reportes`).
- [Cambiar `@ToString` en 14 entidades] → bajo riesgo: no afecta mapeo ni datos; la suite completa de etapa 1 actúa como red de seguridad.

## Migration Plan

Sin migración de datos ni de esquema: no cambia ninguna entidad persistida ni `build.gradle`. Despliegue = `./gradlew clean test bootJar` con JDK 17. Rollback = revertir el commit de la etapa; la fuente agregada en `resources/fonts/` se elimina con él.

## Open Questions

1. **[RESUELTA el 2026-11-12]** Fuente del PDF: el estudiante aprobó la opción (a), descarga desde el repositorio oficial `notofonts/noto-cjk` de GitHub (`Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf`, licencia de `Sans/LICENSE`), con commit directo y sin Git LFS. Archivos ya presentes en `src/main/resources/fonts/` (16 437 364 bytes; SHA-256 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`). Se agrega `*.otf binary` a `.gitattributes`.
2. **[RESUELTA el 2026-11-12]** Muestras: solo en `build/muestras-reportes/` (por defecto), sin copias versionadas en `docs/muestras/`.
3. **[RESUELTA el 2026-11-12]** El TXT lleva línea de encabezado (D7 confirmado).
4. (No bloqueante, **diferida a C-04**, sin cambios el 2026-11-12) Código HTTP para `GlifoNoSoportadoException` (IN-03, propuesto 400 con el código Unicode).
