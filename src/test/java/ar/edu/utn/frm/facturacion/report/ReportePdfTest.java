package ar.edu.utn.frm.facturacion.report;

import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.emitida;
import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.factura;
import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.fila;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReportePdfTest {

    /** Instante fijo: 18:30 UTC es 15:30 en Argentina (UTC-3). */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"), ZoneOffset.UTC);

    /** Una sola instancia por clase de prueba: así la fuente de 16 MB se lee del classpath una única vez. */
    private static final ReporteFacturasService servicio = new ReporteFacturasService(RELOJ);

    private static PdfReader leer(byte[] pdf) {
        try {
            return new PdfReader(pdf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String texto(PdfReader lector, int pagina) {
        try {
            return new PdfTextExtractor(lector).getTextFromPage(pagina);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<FacturaReporteDTO> tresFacturas() {
        return List.of(emitida("PES", 100.00), emitida("PES", 200.00), emitida("DOL", 10.00));
    }

    @Test
    void tamanoDePagina_a4ApaisadoDe842Por595Puntos() {
        PdfReader lector = leer(servicio.generarPdf(tresFacturas()));

        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            Rectangle tamano = lector.getPageSizeWithRotation(pagina);
            assertThat(tamano.getWidth()).as("ancho de la página %d", pagina).isEqualTo(842f);
            assertThat(tamano.getHeight()).as("alto de la página %d", pagina).isEqualTo(595f);
        }
    }

    @Test
    void fechaDeGeneracionEnHoraArgentinaYCantidadDeRegistros() {
        PdfReader lector = leer(servicio.generarPdf(tresFacturas()));

        String primeraPagina = texto(lector, 1);

        assertThat(primeraPagina).contains("Reporte de facturas");
        assertThat(primeraPagina).contains("12/11/2026 15:30");
        assertThat(primeraPagina).contains("Registros: 3");
    }

    @Test
    void fuenteIncrustada_notoSansCjkConIdentityHYArchivoDeFuenteEnElDocumento() {
        PdfReader lector = leer(servicio.generarPdf(List.of(emitida("PES", 100.00))));

        PdfDictionary fuentes = lector.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.FONT);
        List<PdfDictionary> compuestas = fuentes.getKeys().stream().map(fuentes::getAsDict).toList();

        assertThat(compuestas).isNotEmpty().allSatisfy(fuente -> {
            assertThat(fuente.getAsName(PdfName.BASEFONT).toString()).contains("NotoSansCJKsc");
            assertThat(fuente.getAsName(PdfName.ENCODING).toString()).isEqualTo("/Identity-H");
            PdfDictionary descriptor = fuente.getAsArray(PdfName.DESCENDANTFONTS).getAsDict(0)
                    .getAsDict(PdfName.FONTDESCRIPTOR);
            assertThat(descriptor.contains(PdfName.FONTFILE) || descriptor.contains(PdfName.FONTFILE2)
                    || descriptor.contains(PdfName.FONTFILE3)).as("archivo de fuente incrustado").isTrue();
        });
    }

    @Test
    void caracteresEspecialesConservados_enie_acentos_dash_cjk_y_comillas() {
        String cliente = "Ñandú & Cía. — 测试 «comillas»";
        FacturaReporteDTO factura = fila(900L, 15L, LocalDate.of(2026, 11, 10), cliente, "Responsable Inscripto",
                "Casa Central", 1210.50, 2, EstadoFactura.EMITIDA, "PES");

        PdfReader lector = leer(servicio.generarPdf(List.of(factura)));

        assertThat(texto(lector, 1)).contains(cliente);
    }

    /** Los nueve encabezados tal como los devuelve el extractor (las celdas contiguas salen pegadas). */
    private static final String ENCABEZADOS_EXTRAIDOS =
            "Número Fecha Cliente Condición IVAPunto de ventaÍtemsTotalEstadoMoneda";

    @Test
    void borradorEnPdf_elNumeroSeMuestraComoGuion() {
        FacturaReporteDTO borrador = fila(3L, null, LocalDate.of(2026, 11, 10), "Cliente", "Responsable Inscripto",
                "Casa Central", 70.00, 1, EstadoFactura.BORRADOR, "PES");

        String pagina = texto(leer(servicio.generarPdf(List.of(borrador))), 1);

        assertThat(pagina).contains("- 10/11/2026 Cliente");
        assertThat(pagina).doesNotContain("null");
    }

    @Test
    void encabezadosYValores_laFacturaEmitidaNumero15NoMuestraElIdentificador() {
        FacturaReporteDTO factura = fila(900L, 15L, LocalDate.of(2026, 11, 10), "Ferretería", "Responsable Inscripto",
                "Casa Central", 1210.50, 2, EstadoFactura.EMITIDA, "PES");

        String pagina = texto(leer(servicio.generarPdf(List.of(factura))), 1);

        assertThat(pagina).contains(ENCABEZADOS_EXTRAIDOS);
        assertThat(pagina).contains("15 10/11/2026 Ferretería");
        assertThat(pagina).contains("Casa Central21.210,50EMITIDAPES");
        assertThat(pagina).doesNotContain("900");
    }

    @Test
    void totalesPorMoneda_pesosEmitidosYDolaresSoloEnBorradorMuestranMilYCero() {
        List<FacturaReporteDTO> filas = List.of(emitida("PES", 1000.00),
                factura(EstadoFactura.BORRADOR, "DOL", 40.00));

        String pagina = texto(leer(servicio.generarPdf(filas)), 1);

        assertThat(pagina).contains("Total facturado (EMITIDA) PES: 1.000,00");
        assertThat(pagina).contains("Total facturado (EMITIDA) DOL: 0,00");
    }

    @Test
    void estadosMezclados_elTotalEnPesosEs300Con30() {
        List<FacturaReporteDTO> filas = List.of(emitida("PES", 100.10), emitida("PES", 200.20),
                factura(EstadoFactura.ANULADA, "PES", 50.00), factura(EstadoFactura.BORRADOR, "PES", 70.00));

        String pagina = texto(leer(servicio.generarPdf(filas)), 1);

        assertThat(pagina).contains("Total facturado (EMITIDA) PES: 300,30");
    }

    @Test
    void sumaDecimalExacta_diezVeinteYSetentaCentavosDanUnoConCeroCero() {
        List<FacturaReporteDTO> filas = List.of(emitida("PES", 0.10), emitida("PES", 0.20), emitida("PES", 0.70));

        String pagina = texto(leer(servicio.generarPdf(filas)), 1);

        assertThat(pagina).contains("Total facturado (EMITIDA) PES: 1,00");
    }

    @Test
    void reporteDeVariasPaginas_cadaPaginaRepiteLosNueveEncabezados() {
        List<FacturaReporteDTO> filas = java.util.stream.IntStream.rangeClosed(1, 80)
                .mapToObj(i -> fila(i, (long) i, LocalDate.of(2026, 11, 10), "Cliente " + i, "Responsable Inscripto",
                        "Casa Central", 10.00 * i, 1, EstadoFactura.EMITIDA, "PES"))
                .toList();

        PdfReader lector = leer(servicio.generarPdf(filas));

        assertThat(lector.getNumberOfPages()).isGreaterThan(1);
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            assertThat(texto(lector, pagina)).as("página %d", pagina).contains(ENCABEZADOS_EXTRAIDOS);
        }
        assertThat(texto(lector, 1)).contains("Registros: 80");
    }

    @Test
    void listaVacia_unaPaginaConMensajeCantidadCeroYSinTotales() {
        PdfReader lector = leer(servicio.generarPdf(List.of()));

        String pagina = texto(lector, 1);

        assertThat(lector.getNumberOfPages()).isEqualTo(1);
        assertThat(pagina).contains("Reporte de facturas").contains("Registros: 0");
        assertThat(pagina).contains("No hay facturas para informar.");
        assertThat(pagina).doesNotContain("Total facturado");
    }

    private static FacturaReporteDTO conCliente(long fila, String cliente) {
        return fila(fila, fila, LocalDate.of(2026, 11, 10), cliente, "Responsable Inscripto", "Casa Central", 10.00, 1,
                EstadoFactura.EMITIDA, "PES");
    }

    @Test
    void caracterDeUsoPrivado_laGeneracionFallaIdentificandoElCodigoElCampoYLaFila() {
        List<FacturaReporteDTO> filas = List.of(conCliente(1, "Cliente uno"), conCliente(2, "Cliente dos"),
                conCliente(3, "Cliente \uE000 tres"));

        assertThatThrownBy(() -> servicio.generarPdf(filas))
                .isInstanceOf(GlifoNoSoportadoException.class)
                .hasMessageContaining("U+E000")
                .hasMessageContaining("cliente")
                .hasMessageContaining("fila 3");
    }

    @Test
    void caracterFueraDelBmpSinGlifo_tambienSeRechazaConSuCodigoCompleto() {
        String sinGlifo = new String(Character.toChars(0x10FFFD));

        assertThatThrownBy(() -> servicio.generarPdf(List.of(conCliente(1, "A" + sinGlifo + "B"))))
                .isInstanceOf(GlifoNoSoportadoException.class)
                .hasMessageContaining("U+10FFFD");
    }

    @Test
    void tabulacionEnUnTexto_seRechazaPorqueElPdfNoPuedeRepresentarla() {
        assertThatThrownBy(() -> servicio.generarPdf(List.of(conCliente(1, "A\tB"))))
                .isInstanceOf(GlifoNoSoportadoException.class)
                .hasMessageContaining("U+0009");
    }

    @Test
    void saltoDeLineaEnUnTexto_seAdmiteComoCorteDeLinea() {
        byte[] pdf = servicio.generarPdf(List.of(conCliente(1, "Linea uno\nLinea dos")));

        assertThat(texto(leer(pdf), 1)).contains("Linea uno").contains("Linea dos");
    }

    @Test
    void otrosFormatosSiguenDisponibles_conLaMismaListaExcelYTxtConservanElCaracter() throws IOException {
        List<FacturaReporteDTO> filas = List.of(conCliente(1, "Cliente \uE000 uno"));

        String txt = new String(servicio.generarTxt(filas), java.nio.charset.StandardCharsets.UTF_8);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(servicio.generarExcel(filas)))) {
            assertThat(libro.getSheet("Facturas").getRow(1).getCell(2).getStringCellValue())
                    .isEqualTo("Cliente \uE000 uno");
        }
        assertThat(txt).contains("Cliente \uE000 uno");
    }

    @Test
    void fuenteAusente_elPdfFallaNombrandoElArchivoYExcelYTxtNoLaRequieren() {
        ReporteFacturasService sinFuente = new ReporteFacturasService(RELOJ, "fonts/no-existe.otf");
        List<FacturaReporteDTO> filas = tresFacturas();

        assertThatThrownBy(() -> sinFuente.generarPdf(filas))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fonts/no-existe.otf");
        assertThat(sinFuente.generarExcel(filas)).isNotEmpty();
        assertThat(sinFuente.generarTxt(filas)).isNotEmpty();
    }
}
