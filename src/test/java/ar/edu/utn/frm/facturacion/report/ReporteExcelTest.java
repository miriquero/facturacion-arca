package ar.edu.utn.frm.facturacion.report;

import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.emitida;
import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.factura;
import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.fila;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReporteExcelTest {

    private final ReporteFacturasService servicio = new ReporteFacturasService(
            Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"), ZoneOffset.UTC));

    private XSSFWorkbook generar(List<FacturaReporteDTO> filas) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(servicio.generarExcel(filas)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void libroGenerado_hojaFacturasConNueveEncabezadosEnNegritaYLasFacturasDebajo() {
        FacturaReporteDTO primera = fila(1L, 15L, LocalDate.of(2026, 11, 10), "Ferretería Ñandú",
                "Responsable Inscripto", "Casa Central", 1210.50, 2, EstadoFactura.EMITIDA, "PES");
        FacturaReporteDTO segunda = fila(2L, 16L, LocalDate.of(2026, 11, 9), "Juan Servicios",
                "Consumidor Final", "Sucursal", 500.00, 1, EstadoFactura.EMITIDA, "PES");

        try (XSSFWorkbook libro = generar(List.of(primera, segunda))) {
            Sheet hoja = libro.getSheet("Facturas");

            assertThat(hoja).isNotNull();
            Row encabezado = hoja.getRow(0);
            for (int columna = 0; columna < ColumnasReporte.ENCABEZADOS.size(); columna++) {
                Cell celda = encabezado.getCell(columna);
                assertThat(celda.getStringCellValue()).isEqualTo(ColumnasReporte.ENCABEZADOS.get(columna));
                assertThat(((XSSFCellStyle) celda.getCellStyle()).getFont().getBold())
                        .as("negrita en %s", celda.getStringCellValue()).isTrue();
            }
            assertThat(encabezado.getLastCellNum()).isEqualTo((short) 9);
            assertThat(hoja.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Ferretería Ñandú");
            assertThat(hoja.getRow(2).getCell(2).getStringCellValue()).isEqualTo("Juan Servicios");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void tiposDeUnaFacturaEmitida_fechaConFormatoDeFechaYNumerosNumericos() {
        FacturaReporteDTO factura = fila(900L, 15L, LocalDate.of(2026, 11, 10), "Ferretería", "Responsable Inscripto",
                "Casa Central", 1210.50, 2, EstadoFactura.EMITIDA, "PES");

        try (XSSFWorkbook libro = generar(List.of(factura))) {
            Row fila = libro.getSheet("Facturas").getRow(1);

            assertThat(fila.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(fila.getCell(1))).isTrue();
            assertThat(fila.getCell(1).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 10));
            assertThat(fila.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(fila.getCell(0).getNumericCellValue()).isEqualTo(15.0);
            assertThat(fila.getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(fila.getCell(5).getNumericCellValue()).isEqualTo(2.0);
            assertThat(fila.getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(fila.getCell(6).getNumericCellValue()).isEqualTo(1210.50);
            for (int columna : new int[] {2, 3, 4, 7, 8}) {
                assertThat(fila.getCell(columna).getCellType()).as("columna %d", columna).isEqualTo(CellType.STRING);
            }
            assertThat(fila.getCell(7).getStringCellValue()).isEqualTo("EMITIDA");
            assertThat(fila.getCell(8).getStringCellValue()).isEqualTo("PES");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void borrador_elNumeroEsUnaCeldaEnBlancoYElRestoConservaSusTipos() {
        FacturaReporteDTO borrador = fila(3L, null, LocalDate.of(2026, 11, 11), "Cliente", "Consumidor Final",
                "Casa Central", 70.00, 1, EstadoFactura.BORRADOR, "PES");

        try (XSSFWorkbook libro = generar(List.of(borrador))) {
            Row fila = libro.getSheet("Facturas").getRow(1);

            assertThat(fila.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(fila.getCell(6).getNumericCellValue()).isEqualTo(70.00);
            assertThat(fila.getCell(7).getStringCellValue()).isEqualTo("BORRADOR");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void formatoDelTotal_dosDecimalesYSeparadorDeMiles() {
        FacturaReporteDTO factura = fila(1L, 1L, LocalDate.of(2026, 11, 10), "Cliente", "RI", "Casa Central",
                1234567.5, 1, EstadoFactura.EMITIDA, "PES");

        try (XSSFWorkbook libro = generar(List.of(factura))) {
            Cell total = libro.getSheet("Facturas").getRow(1).getCell(6);

            assertThat(total.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            assertThat(total.getNumericCellValue()).isEqualTo(1234567.5);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void formatoDeLaFecha_diaMesYAnio() {
        try (XSSFWorkbook libro = generar(List.of(emitida("PES", 10.00)))) {
            Cell fecha = libro.getSheet("Facturas").getRow(1).getCell(1);

            assertThat(fecha.getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void columnaDeClienteAjustada_masAnchaQueElAnchoPorDefecto() {
        FacturaReporteDTO factura = fila(1L, 1L, LocalDate.of(2026, 11, 10), "C".repeat(40), "RI", "Casa Central",
                10.00, 1, EstadoFactura.EMITIDA, "PES");

        try (XSSFWorkbook libro = generar(List.of(factura))) {
            Sheet hoja = libro.getSheet("Facturas");

            assertThat(hoja.getColumnWidth(2)).isGreaterThan(hoja.getDefaultColumnWidth() * 256);
            assertThat(hoja.getColumnWidth(2)).isGreaterThan(hoja.getColumnWidth(7));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void formulaPorMoneda_unaFilaDeTotalPorCodigoConSumifsSobreEstadoYMoneda() {
        try (XSSFWorkbook libro = generar(List.of(emitida("PES", 1000.00), emitida("DOL", 10.50)))) {
            Sheet hoja = libro.getSheet("Facturas");

            assertThat(hoja.getLastRowNum()).isEqualTo(5);
            Row totalDolares = hoja.getRow(4);
            Row totalPesos = hoja.getRow(5);
            assertThat(totalDolares.getCell(0).getStringCellValue()).isEqualTo("Total facturado (EMITIDA)");
            assertThat(totalDolares.getCell(8).getStringCellValue()).isEqualTo("DOL");
            assertThat(totalDolares.getCell(6).getCellFormula())
                    .isEqualTo("SUMIFS($G$2:$G$3,$H$2:$H$3,\"EMITIDA\",$I$2:$I$3,I5)");
            assertThat(totalPesos.getCell(8).getStringCellValue()).isEqualTo("PES");
            assertThat(totalPesos.getCell(6).getCellFormula())
                    .isEqualTo("SUMIFS($G$2:$G$3,$H$2:$H$3,\"EMITIDA\",$I$2:$I$3,I6)");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void listaVacia_soloLosEncabezadosYNingunaFilaDeTotal() {
        try (XSSFWorkbook libro = generar(List.of())) {
            Sheet hoja = libro.getSheet("Facturas");

            assertThat(hoja.getLastRowNum()).isZero();
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Número");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resultado almacenado de la fórmula de total de la fila indicada, sin evaluar nada al leer. */
    private static BigDecimal resultadoAlmacenado(XSSFWorkbook libro, int indiceDeFila) {
        Cell formula = libro.getSheet("Facturas").getRow(indiceDeFila).getCell(6);
        assertThat(formula.getCellType()).isEqualTo(CellType.FORMULA);
        assertThat(formula.getCachedFormulaResultType()).isEqualTo(CellType.NUMERIC);
        return BigDecimal.valueOf(formula.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    void resultadoAlmacenado_estadosMezcladosEnPesosDan30030YSeForzaElRecalculo() {
        List<FacturaReporteDTO> filas = List.of(emitida("PES", 100.10), emitida("PES", 200.20),
                factura(EstadoFactura.ANULADA, "PES", 50.00));

        try (XSSFWorkbook libro = generar(filas)) {
            assertThat(resultadoAlmacenado(libro, 5)).isEqualByComparingTo("300.30");
            assertThat(libro.getForceFormulaRecalculation()).isTrue();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void resultadoAlmacenado_dosMonedasYMonedaSinEmitidas() {
        List<FacturaReporteDTO> filas = List.of(emitida("PES", 1000.00), emitida("DOL", 10.50),
                factura(EstadoFactura.BORRADOR, "EUR", 40.00));

        try (XSSFWorkbook libro = generar(filas)) {
            assertThat(libro.getSheet("Facturas").getRow(5).getCell(8).getStringCellValue()).isEqualTo("DOL");
            assertThat(resultadoAlmacenado(libro, 5)).isEqualByComparingTo("10.50");
            assertThat(libro.getSheet("Facturas").getRow(6).getCell(8).getStringCellValue()).isEqualTo("EUR");
            assertThat(resultadoAlmacenado(libro, 6)).isEqualByComparingTo("0.00");
            assertThat(libro.getSheet("Facturas").getRow(7).getCell(8).getStringCellValue()).isEqualTo("PES");
            assertThat(resultadoAlmacenado(libro, 7)).isEqualByComparingTo("1000.00");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void resultadoAlmacenado_sumaDecimalExactaDeDiezVeinteYSetentaCentavos() {
        List<FacturaReporteDTO> filas = List.of(emitida("PES", 0.10), emitida("PES", 0.20), emitida("PES", 0.70));

        try (XSSFWorkbook libro = generar(filas)) {
            assertThat(resultadoAlmacenado(libro, 5)).isEqualByComparingTo("1.00");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
