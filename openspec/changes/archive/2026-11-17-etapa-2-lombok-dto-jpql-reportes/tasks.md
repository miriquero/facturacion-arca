# Tasks

> Reglas para todo el apply:
> - Gradle siempre con `JAVA_HOME=C:\Users\zbook\.jdks\ms-17.0.20.1` (nunca el JDK 26). Para una clase: `./gradlew test --tests '<FQN>'`.
> - TDD estricto: cada **GREEN** va precedida de su **RED**, y la RED se cierra solo tras ver la falla **por la razón esperada** (aserción, no error de compilación ajeno: si no compila, crear primero el esqueleto mínimo). Las pruebas que pasan al escribirse se marcan como **caracterización** en `docs/etapa-2.md` (design D1). Toda pieza de la semilla (`api-facturas`) entra solo en un GREEN y se anota en el informe qué se trajo y qué se cambió.
> - Valores esperados literales, tomados de los escenarios de `specs/`; nunca recalculados como lo hace el código. AssertJ; sin aserciones triviales.
> - Dinero: nada de `double` acumulado ni `Math.round`; `BigDecimal.valueOf(double)` + `HALF_UP` a 2 decimales (design D6).
> - Sin endpoints REST, sin filtros, sin cambios en `build.gradle` ni en el `DataLoader`. Sin comandos Git que cambien estado.
> - Grupos 7 y 8 (PDF) desbloqueados el 2026-11-12 (Open Question 1 resuelta: descarga oficial, commit directo, sin Git LFS).
> - Gobernanza MEDIA: al cerrar cada grupo, correr `./gradlew test` completo y reportar al estudiante antes de seguir.

## 1. Red de seguridad e informe

- [x] 1.1 Red de seguridad: `./gradlew clean test` con JDK 17; anotar el baseline (se esperan 105 pruebas en verde) en el borrador de `docs/etapa-2.md`; si algo falla, detenerse y reportarlo como falla preexistente
- [x] 1.2 Crear `docs/etapa-2.md` con el esqueleto de secciones (Lombok y recursión, DTO y constructor de proyección, joins de la consulta, criterio de totales por estado y moneda, estrategia de preservación de texto, fuente del PDF, muestras y revisión visual, semilla reutilizada, referencia del docente, decisiones, verificaciones, uso de IA); verificar que el archivo existe con esas secciones

## 2. Lombok coherente con la herencia (spec `modelo-persistente`)

- [x] 2.1 Caracterización: en `model/RepresentacionTextualTest`, agregar «detalle enlazado a su factura» (el `toString` del detalle no contiene `factura=` ni recursiona); crear `model/IgualdadEntidadesTest` con «igualdad por identidad sin recursión» (dos facturas con los mismos datos y detalles enlazados: `equals`/`hashCode` terminan, no son iguales entre sí, cada una igual a sí misma) y «ninguna entidad redefine la igualdad» (para las 14 clases del metamodelo, `equals` y `hashCode` declarados en `Object`). Verificar que pasan y anotarlas como caracterización
- [x] 2.2 RED: «identificador heredado visible» — una factura con `id` 42 (asignado por reflexión o persistida) → `toString` contiene `id=42`; triangular con un `Usuario` (hereda de `EntityId` directamente). Falla porque ninguna superclase genera `toString`
- [x] 2.3 GREEN: `@ToString` en `EntityId`, `@ToString(callSuper = true)` en `AuditoriaApp` y en las 14 entidades, conservando todos los `@ToString.Exclude` (design D1); 2.2 en verde. Verificar que «usuarios de auditoría excluidos» sigue en verde ahora que la exclusión es efectiva y que la suite completa no cambió de número salvo las pruebas nuevas
- [x] 2.4 Documentar en `docs/etapa-2.md` por qué incluir ambos lados de una relación bidireccional en `toString`/`equals`/`hashCode` recursiona (factura → detalles → factura… `StackOverflowError`, y carga diferida fuera de sesión), por qué no se usa `@Data` ni igualdad por id con `IDENTITY`, la incoherencia encontrada en `AuditoriaApp` y su corrección; verificar `./gradlew test` en verde

## 3. DTO y proyección JPQL (spec `proyeccion-reporte-facturas`)

- [x] 3.1 RED: `repository/FacturaReporteRepositoryTest` (`@DataJpaTest`, `UsuarioActual` fijado y limpiado, datos con `DatosDePrueba`/`FacturaBase`): «factura emitida completa» (los 10 datos literales del escenario, cantidad 2). Crear el esqueleto mínimo (`FacturaReporteDTO` sin lógica, `FacturaReporteRepository` con `buscarReporte()`, `Impl` que devuelve lista vacía, `FacturaVentaRepository` extendiendo el fragmento) para que falle en la aserción
- [x] 3.2 GREEN: `dto/FacturaReporteDTO` adaptado de la semilla (design D2: inmutable, `LocalDate`, constructor `(Long, Long, LocalDate, String, String, String, Double, Long, EstadoFactura, String)`, estado a texto, `codigoMoneda` nuevo) y `FacturaReporteRepositoryImpl` con `EntityManager` por constructor, `@Transactional(readOnly = true)` y la consulta de la semilla adaptada **todavía con `JOIN f.detalles` interno** como venía; 3.1 en verde
- [x] 3.3 RED + TRIANGULATE: «cabecera sin renglones» (cantidad 0) y «factura con tres renglones» (cantidad 3, una sola fila). Falla la cabecera sin renglones por el `JOIN` interno heredado de la semilla
- [x] 3.4 GREEN: `LEFT JOIN f.detalles d` (design D4); 3.3 en verde. Anotar en el informe el defecto de la semilla que esta prueba expuso
- [x] 3.5 RED + TRIANGULATE: «factura sin cliente» (`Consumidor Final`) y «factura con cliente» («Ferretería Ñandú»); «borrador sin número»; «estados mezclados» (`BORRADOR`, `EMITIDA`, `ANULADA` como texto); «dos monedas» (`PES` y `DOL`, importes 100.00 sin convertir). Verificar cuáles fallan con la consulta actual y llevar a GREEN solo lo necesario
- [x] 3.6 RED + GREEN: «orden del reporte» (fechas 2026-11-01, 2026-11-10 y dos 2026-11-05: orden 11-10, 05 con id mayor, 05 con id menor, 11-01) y «reporte vacío» (lista vacía)
- [x] 3.7 RED + GREEN: «proyección sin cargar entidades» — tras `clear()` y `buscarReporte()`, `entityManager.getEntityManager().unwrap(Session.class).getStatistics().getEntityCount()` es 0 (triangular comparando con un `findAll()` que sí carga entidades)
- [x] 3.8 REFACTOR: separar la consulta en las dos constantes de D4 (`SELECT ... JOIN` y `GROUP BY ... ORDER BY`) y verificar que todo el grupo sigue en verde
- [x] 3.9 Documentar en `docs/etapa-2.md` cada join y por qué es `LEFT` o interno, `COALESCE`, `GROUP BY` completo (portabilidad H2/PostgreSQL), por qué el orden y los tipos del constructor importan (`Long` de `COUNT`, `Double`, `EstadoFactura`), por qué proyectar a DTO evita cargar datos que el reporte no usa (evidencia de 3.7) y la fila de semilla del DTO y de la consulta; verificar `./gradlew test` en verde

## 4. Servicio de reportes, columnas y totales (spec `exportacion-reportes`)

- [x] 4.1 RED: `report/TotalesPorMonedaTest` (unitaria): «estados mezclados en una moneda» (`PES` 100.10 + 200.20 EMITIDA, 50.00 ANULADA, 70.00 BORRADOR → 300.30); crear soporte de pruebas `report/FacturasDeEjemplo` para construir DTO legibles
- [x] 4.2 GREEN: `report/TotalesPorMoneda` con `BigDecimal.valueOf`, solo `EMITIDA`, `HALF_UP` a 2 decimales (design D6); 4.1 en verde
- [x] 4.3 RED + TRIANGULATE + GREEN: «dos monedas» (`PES` 1000.00, `DOL` 10.50, sin total combinado), «moneda sin emitidas» (`DOL` 0.00), «suma decimal exacta» (0.10 + 0.20 + 0.70 = 1.00 exacto, `compareTo` y escala 2), lista vacía → mapa vacío, orden alfabético de códigos
- [x] 4.4 RED + GREEN: `report/ColumnasReporte` con los 9 encabezados en orden (Número, Fecha, Cliente, Condición IVA, Punto de venta, Ítems, Total, Estado, Moneda); la prueba compara con la lista literal
- [x] 4.5 RED: en `FacturacionApplicationTests`, el contexto contiene `ReporteFacturasService` y un `Clock` cuya zona es `America/Argentina/Buenos_Aires`. GREEN: `config/RelojConfig` con el bean `Clock` (design D12) y `report/ReporteFacturasService` `@Service` con constructor `(Clock)` y los tres métodos públicos aún sin implementar (lanzan `UnsupportedOperationException`); verificar en verde
- [x] 4.6 Documentar en `docs/etapa-2.md` el criterio de totales (solo EMITIDA, por código de moneda, sin conversión, cero si no hay emitidas, `BigDecimal` + `HALF_UP`, por qué `0.1 + 0.2` en `double` no da 0.3) y la decisión de zona horaria con `Clock`; verificar `./gradlew test` en verde

## 5. Reporte TXT (specs `reporte-txt`, `exportacion-reportes`)

- [x] 5.1 RED: `report/ReporteTxtTest` (unitaria, `Clock.fixed`): «tres facturas» (4 líneas, 9 campos por línea, encabezado = `ColumnasReporte`) y «lista vacía» (solo encabezado)
- [x] 5.2 GREEN: `generarTxt` adaptado del `exportToExcel` de la semilla (era TSV): encabezado + una línea por factura, `\t`, `\n`, UTF-8 (design D7); 5.1 en verde
- [x] 5.3 RED + GREEN: «importe y fecha» (`2026-03-05`, `1234567.50`), número vacío en borrador, ninguna columna con el id 900 («encabezados y valores»), «sin pie de totales» (líneas = facturas + 1 con `PES` y `DOL` emitidas)
- [x] 5.4 RED: `report/EscapeTxtTest`: `A\B<TAB>C<CR><LF>"D"` → `A\\B\tC\r\n"D"`; `C:\nuevo` → `C:\\nuevo`; texto sin especiales queda igual
- [x] 5.5 GREEN: `report/EscapeTxt.escapar` con la barra invertida primero (design D7) y su uso en `generarTxt`; 5.4 en verde
- [x] 5.6 RED + GREEN: en `ReporteTxtTest`, decodificador de un solo recorrido en el código de prueba; «reconstrucción del original» para cliente, condición IVA y punto de venta con barras, tabulaciones, CR y LF (la factura ocupa una línea física y se recupera el texto exacto), «barra invertida seguida de letra» y «caracteres no ASCII» (`Ñandú & Cía. — 测试` decodificado en UTF-8, sin BOM: los 3 primeros bytes no son `EF BB BF`)
- [x] 5.7 Caracterización (`exportacion-reportes`): «contenido devuelto sin archivos» — listar el directorio de trabajo antes y después de generar TXT y Excel (y PDF cuando exista el grupo 8) y verificar que no cambió y que cada contenido no está vacío
- [x] 5.8 Documentar en `docs/etapa-2.md` la estrategia de preservación de texto del TXT (orden de escape, por qué la barra primero, por qué un solo recorrido para decodificar, no es CSV, UTF-8 sin BOM, `\n` fijo) y la adaptación de la semilla (`null` impreso, sin escapes, sin moneda); verificar `./gradlew test` en verde

## 6. Reporte Excel (specs `reporte-excel`, `exportacion-reportes`)

- [x] 6.1 RED: `report/ReporteExcelTest` (unitaria; lee los bytes con `XSSFWorkbook`): «libro generado» (hoja `Facturas`, 9 encabezados en negrita, facturas en filas 2 y 3)
- [x] 6.2 GREEN: `generarExcel` con Apache POI (nuevo, la semilla no tenía Excel real): hoja, estilo de encabezado y filas de texto (design D8); 6.1 en verde
- [x] 6.3 RED + GREEN: «tipos de una factura emitida» (fecha con formato de fecha = 2026-11-10 vía `DateUtil.isCellDateFormatted`; número 15, ítems 2 y total 1210.50 numéricos; estado y moneda texto) y número **en blanco** para borrador
- [x] 6.4 RED + GREEN: «formato del total» (`#,##0.00`) y «columna de cliente ajustada» (ancho mayor que el por defecto con un cliente de 40 caracteres)
- [x] 6.5 RED: «fórmula por moneda» (filas de total `PES` y `DOL`, `getCellFormula()` contiene `SUMIFS` y referencia las columnas G, H e I sin incluir las filas de total) y «lista vacía» (solo encabezados, sin filas de total)
- [x] 6.6 GREEN: filas de total con `SUMIFS` por código de `TotalesPorMoneda` (design D8); 6.5 en verde
- [x] 6.7 RED + GREEN: «resultado almacenado» — sin evaluar nada al leer, el resultado cacheado de `PES` es 300.30 (comparado como `BigDecimal` a 2 decimales) y `getForceFormulaRecalculation()` es `true`; triangular con «dos monedas» (1000.00 y 10.50) y «moneda sin emitidas» (`DOL` 0.00)
- [x] 6.8 Documentar en `docs/etapa-2.md` tipos de celda, formatos, por qué `SUMIFS` y no el valor de Java, por qué se evalúa y se fuerza el recálculo, y que la semilla no tenía Excel real; verificar `./gradlew test` en verde

## 7. Fuente del PDF (desbloqueado el 2026-11-12: descarga oficial, commit directo)

- [x] 7.1 (resuelta el 2026-11-12: el orquestador descargó de `notofonts/noto-cjk`; verificada: 16 437 364 bytes, SHA-256 `2c76254f…b74b`, `OFL.txt` con la licencia) Con la respuesta del estudiante a la Open Question 1 (origen y modo de versionado), obtener `NotoSansCJKsc-Regular.otf` y el texto de su licencia OFL por el medio aprobado y ubicarlos en `src/main/resources/fonts/NotoSansCJKsc-Regular.otf` y `src/main/resources/fonts/OFL.txt`; registrar origen, versión y SHA-256 en `docs/etapa-2.md`; verificar tamaño (~16 MB) y que `OFL.txt` contiene «SIL OPEN FONT LICENSE»
- [x] 7.2 RED + GREEN («licencia junto a la fuente»): prueba que lee ambos recursos del classpath (`fonts/NotoSansCJKsc-Regular.otf` no vacío y `fonts/OFL.txt` con el texto de la licencia); verificar también que el `bootJar` los contiene (`jar tf build/libs/*.jar | grep fonts/`)

## 8. Reporte PDF (spec `reporte-pdf`, `exportacion-reportes`; requiere grupo 7)

- [x] 8.1 RED: `report/ReportePdfTest` (unitaria, `Clock.fixed(2026-11-12T18:30:00Z, UTC)`, lee con `PdfReader`/`PdfTextExtractor`): «tamaño de página» (842 × 595) y «fecha de generación y cantidad» (título, `12/11/2026 15:30`, 3 registros)
- [x] 8.2 GREEN: `generarPdf` adaptado del `exportToPdf` de la semilla (`byte[]`, `PageSize.A4.rotate()`, 9 columnas de `ColumnasReporte`, título, generación, cantidad) con la fuente cargada en forma perezosa desde el classpath (design D9); 8.1 en verde
- [x] 8.3 RED + GREEN: «fuente incrustada» (fuente de la página con nombre que contiene `NotoSansCJKsc`, codificación `Identity-H` y archivo de fuente incrustado) y «caracteres especiales conservados» (`Ñandú & Cía. — 测试 «comillas»` extraído idéntico)
- [x] 8.4 RED + GREEN: «borrador en PDF» (número `-`), «encabezados y valores» (sin el id 900) y «totales por moneda» (`PES` 1.000,00 y `DOL` 0,00), «estados mezclados en una moneda» (300,30), «suma decimal exacta» (1,00)
- [x] 8.5 RED + GREEN: «reporte de varias páginas» (80 facturas → más de una página y los 9 encabezados en el texto de cada página, `setHeaderRows(1)`) y «lista vacía» (una página, 0 registros, mensaje «No hay facturas para informar.», sin totales)
- [x] 8.6 RED: «carácter de uso privado» — cliente con U+E000 → `GlifoNoSoportadoException` cuyo mensaje contiene `U+E000`, el campo y la fila; triangular con un carácter fuera del BMP sin glifo y con TAB en un texto (design D10); y «otros formatos siguen disponibles» (con la misma lista, `generarExcel` y `generarTxt` funcionan y conservan U+E000)
- [x] 8.7 GREEN: `report/VerificadorGlifos` por code point sobre textos dinámicos y fijos antes de abrir el documento, y `report/GlifoNoSoportadoException` (design D10); 8.6 en verde
- [x] 8.8 RED + GREEN: fuente ausente → `IllegalStateException` que nombra el archivo (servicio construido con un cargador de recursos que no la encuentra, sin borrar el archivo real) y Excel/TXT no la requieren
- [x] 8.9 REFACTOR: compartir una instancia del servicio por clase de prueba para no recargar la fuente; verificar el grupo en verde y el tiempo de `./gradlew test`
- [x] 8.10 Documentar en `docs/etapa-2.md` la fuente (origen, licencia, `Identity-H`, subset), la verificación previa de glifos y por qué no se sustituyen caracteres, el formato de importes del PDF y la adaptación de la semilla (`com.lowagie` se mantiene en 2.0.3, `null` en borradores, 7 columnas, sin fuente); verificar `./gradlew test` en verde

## 9. Muestras y revisión visual

- [x] 9.1 RED + GREEN: `report/MuestrasReportesTest` genera `build/muestras-reportes/facturas.txt` y `facturas.xlsx` con la lista representativa del design D13 y verifica que existen y no están vacíos; confirmar con `git status` que no aparecen archivos nuevos sin seguimiento
- [x] 9.2 (requiere grupo 8) Extender `MuestrasReportesTest` con `facturas.pdf` y `facturas-varias-paginas.pdf`; verificar que existen
- [x] 9.3 Pedir al estudiante la **revisión visual completa**: abrir `facturas.pdf` y `facturas-varias-paginas.pdf` (todas las páginas: orientación, encabezados repetidos, acentos, CJK, guiones, totales), `facturas.xlsx` en Excel/LibreOffice (tipos, formatos, fórmulas recalculadas, anchos) y `facturas.txt` en un editor UTF-8; registrar en `docs/etapa-2.md` qué se revisó, con qué programa y lo observado

## 10. Informe, README y verificación final

- [x] 10.1 Completar `docs/etapa-2.md` (~1 carilla, sin repetir el enunciado): tabla de semilla reutilizada (D-inventario), referencia del docente consultada solo por nombres (qué se miró y qué no), decisiones D1-D14 resumidas, pruebas de caracterización declaradas, verificaciones y entorno (JDK 17, H2), y sección **Uso de IA** con el mismo formato que `docs/etapa-1.md` (herramientas; qué se pidió; qué se revisó y qué se corrigió **dejado para que lo complete el estudiante**; línea `Co-Authored-By`); verificar que no queda ninguna sección vacía salvo la línea de revisión del estudiante
- [x] 10.2 Actualizar `README.md`: sección «Etapa 2: proyección y reportes» (qué hay, cómo generar las muestras con `./gradlew test` y dónde quedan, ubicación y licencia de la fuente, nota de `autoSizeColumn` y fuentes del sistema); verificar que los comandos documentados corren tal cual
- [x] 10.3 Verificación final: `./gradlew clean test bootJar` con JDK 17 en verde; anotar en `docs/etapa-2.md` el número total de pruebas (baseline 105 + nuevas) y que `build/libs/` contiene solo el JAR ejecutable con `fonts/` adentro
- [x] 10.4 Revisar que ningún `double` se acumula para importes en `report/` (búsqueda de `Math.round` y de sumas `+=` sobre `double` sin resultados) y que ninguna clase de `report/` importa entidades de `model/` salvo `EstadoFactura`; registrar el resultado

## Workflow follow-up

- Entregar al estudiante los comandos de Git (commits por grupo, PR `etapa-2` → `main`, tag `etapa-2`) con la línea `Co-Authored-By` del modelo en uso; él los ejecuta.
- `/opsx:archive etapa-2-lombok-dto-jpql-reportes` y marcar `[x]` C-03 en `CHANGES.md` después de que el estudiante revise y taguee.
