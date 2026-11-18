package ar.edu.utn.frm.facturacion.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleDTO;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComprobantePdfTest {

    /** 18:30 UTC es 15:30 en Argentina (UTC-3). */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-18T18:30:00Z"), ZoneOffset.UTC);

    /** Una sola instancia por clase: la fuente de 16 MB se lee del classpath una única vez. */
    private static final ReporteFacturasService servicio = new ReporteFacturasService(RELOJ);

    static final FacturaDetalleDTO MARTILLO = new FacturaDetalleDTO(11L, 21L, "ART-001", "Martillo", 1, 3000.00, 0,
            3000.00, 630.00, 3630.00);
    static final FacturaDetalleDTO CABLE = new FacturaDetalleDTO(12L, 22L, "ART-003", "Cable HDMI", 2, 8000.00, 0,
            16000.00, 3360.00, 19360.00);

    /** Factura EMITIDA número 4 del punto de venta 1 «Casa Central», con los dos renglones del spec. */
    static FacturaDetalladaDTO emitida() {
        return factura("EMITIDA", "Juan Servicios", "20-12345678-9", null, null, 22990.00, List.of(MARTILLO, CABLE));
    }

    static FacturaDetalladaDTO factura(String estado, String cliente, String cuit, LocalDate anulacion,
            String observaciones, double total, List<FacturaDetalleDTO> renglones) {
        return new FacturaDetalladaDTO(4L, 4L, LocalDate.of(2026, 11, 5), estado, cuit == null ? null : 3L, cliente,
                cuit, 1L, "Responsable Inscripto", 1L, 1, "Casa Central", 1L, "PES", total, 0.0, total,
                "70000000000001", LocalDate.of(2026, 11, 15), "A", anulacion, observaciones, renglones);
    }

    static PdfReader leer(byte[] pdf) {
        try {
            return new PdfReader(pdf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String texto(PdfReader lector, int pagina) {
        try {
            return new PdfTextExtractor(lector).getTextFromPage(pagina);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void todasLasPaginasSonA4Vertical() {
        PdfReader lector = leer(servicio.generarComprobante(emitida()));

        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            Rectangle tamano = lector.getPageSizeWithRotation(pagina);
            assertThat(tamano.getWidth()).as("ancho de la página %d", pagina).isEqualTo(595f);
            assertThat(tamano.getHeight()).as("alto de la página %d", pagina).isEqualTo(842f);
        }
    }

    @Test
    void identificacionConNumeroFormateadoFechasYPuntoDeVenta() {
        String pagina = texto(leer(servicio.generarComprobante(emitida())), 1);

        assertThat(pagina).contains("Factura", "0001-00000004", "05/11/2026", "Casa Central", "18/11/2026 15:30");
    }

    @Test
    void clienteConCuitMuestraDenominacionCuitCondicionYMoneda() {
        String pagina = texto(leer(servicio.generarComprobante(emitida())), 1);

        assertThat(pagina).contains("Juan Servicios", "20-12345678-9", "Responsable Inscripto", "PES");
    }

    @Test
    void consumidorFinalNoMuestraEtiquetaDeCuit() {
        FacturaDetalladaDTO sinCliente = factura("EMITIDA", "Consumidor Final", null, null, null, 3630.00,
                List.of(MARTILLO));

        String pagina = texto(leer(servicio.generarComprobante(sinCliente)), 1);

        assertThat(pagina).contains("Consumidor Final").doesNotContain("CUIT");
    }

    static final List<String> ENCABEZADOS_RENGLONES = List.of("Código", "Descripción", "Cantidad", "Precio unit.",
            "Bonif. %", "Neto", "IVA", "Subtotal");

    @Test
    void tablaDeRenglonesConLosOchoEncabezadosYLosRenglonesEnOrden() {
        String pagina = texto(leer(servicio.generarComprobante(emitida())), 1);

        assertThat(pagina).contains(ENCABEZADOS_RENGLONES);
        int martillo = pagina.indexOf("ART-001");
        int cable = pagina.indexOf("ART-003");
        assertThat(martillo).isPositive();
        assertThat(cable).isGreaterThan(martillo);
        assertThat(pagina.indexOf("Martillo", martillo)).isGreaterThan(martillo);
        assertThat(pagina.indexOf("3.000,00", martillo)).isGreaterThan(martillo).isLessThan(cable);
        assertThat(pagina.indexOf("Cable HDMI", cable)).isGreaterThan(cable);
        assertThat(pagina.indexOf("8.000,00", cable)).isGreaterThan(cable);
    }

    @Test
    void sesentaRenglonesOcupanVariasPaginasYCadaUnaRepiteLosEncabezados() {
        List<FacturaDetalleDTO> muchos = java.util.stream.IntStream.rangeClosed(1, 60)
                .mapToObj(i -> new FacturaDetalleDTO(100L + i, 21L, "ART-" + i, "Artículo número " + i, 1, 100.00, 0,
                        100.00, 21.00, 121.00))
                .toList();
        FacturaDetalladaDTO grande = factura("EMITIDA", "Juan Servicios", "20-12345678-9", null, null, 7260.00,
                muchos);

        PdfReader lector = leer(servicio.generarComprobante(grande));

        assertThat(lector.getNumberOfPages()).isGreaterThan(1);
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            assertThat(texto(lector, pagina)).as("encabezados en la página %d", pagina)
                    .contains(ENCABEZADOS_RENGLONES);
        }
    }

    @Test
    void totalesNetoIvaYTotalSalenDeLosValoresGuardados() {
        String pagina = texto(leer(servicio.generarComprobante(emitida())), 1);

        assertThat(pagina).contains("19.000,00", "3.990,00", "22.990,00");
    }

    @Test
    void unImporteGuardadoDeCeroComaCientoVeinticincoSeRedondeaHaciaArribaEnLaMitad() {
        FacturaDetalleDTO centavos = new FacturaDetalleDTO(11L, 21L, "ART-009", "Tornillo", 1, 0.125, 0, 0.125, 0,
                0.125);
        FacturaDetalladaDTO factura = factura("EMITIDA", "Juan Servicios", "20-12345678-9", null, null, 0.125,
                List.of(centavos));

        String pagina = texto(leer(servicio.generarComprobante(factura)), 1);

        assertThat(pagina).contains("0,13").doesNotContain("0,12");
    }

    @Test
    void autorizacionMuestraCaeVencimientoYResultado() {
        String pagina = texto(leer(servicio.generarComprobante(emitida())), 1);

        assertThat(pagina).contains("CAE", "70000000000001", "15/11/2026", "Resultado: A");
    }

    @Test
    void facturaAnuladaMuestraAnuladaFechaDeAnulacionYConservaElCae() {
        FacturaDetalladaDTO anulada = factura("ANULADA", "Juan Servicios", "20-12345678-9", LocalDate.of(2026, 11, 12),
                null, 22990.00, List.of(MARTILLO, CABLE));

        String pagina = texto(leer(servicio.generarComprobante(anulada)), 1);

        assertThat(pagina).contains("ANULADA", "12/11/2026", "70000000000001", "0001-00000004", "15/11/2026");
    }

    @Test
    void facturaEmitidaMuestraEmitidaYNuncaAnulada() {
        String pagina = texto(leer(servicio.generarComprobante(emitida())), 1);

        assertThat(pagina).contains("EMITIDA").doesNotContain("ANULADA");
    }

    @Test
    void leyendaDeComprobanteSimuladoSiempreVisible() {
        String pagina = texto(leer(servicio.generarComprobante(emitida())), 1);

        assertThat(pagina).contains("sin validez fiscal", "CAE ficticio");
    }

    @Test
    void observacionesSoloSeMuestranSiExisten() {
        FacturaDetalladaDTO con = factura("EMITIDA", "Juan Servicios", "20-12345678-9", null, "Entrega en mostrador",
                22990.00, List.of(MARTILLO, CABLE));

        assertThat(texto(leer(servicio.generarComprobante(con)), 1)).contains("Entrega en mostrador");
        assertThat(texto(leer(servicio.generarComprobante(emitida())), 1)).doesNotContain("Observaciones");
    }

    @Test
    void unCaracterSinGlifoEnUnaDescripcionFallaIdentificandoloYSinSugerirExcelNiTxt() {
        FacturaDetalleDTO conPrivado = new FacturaDetalleDTO(11L, 21L, "ART-001", "Martillo", 1, 3000.00, 0,
                3000.00, 630.00, 3630.00);
        FacturaDetalladaDTO factura = factura("EMITIDA", "Juan Servicios", "20-12345678-9", null, null, 3630.00,
                List.of(conPrivado));

        assertThatThrownBy(() -> servicio.generarComprobante(factura))
                .isInstanceOf(GlifoNoSoportadoException.class)
                .hasMessageContaining("U+E000")
                .hasMessageContaining("descripción")
                .hasMessageNotContainingAny("Excel", "TXT");
    }

    @Test
    void textosEspecialesSeExtraenIdenticosAlOriginal() {
        FacturaDetalleDTO especial = new FacturaDetalleDTO(11L, 21L, "ART-004", "Cable «HDMI» 2 m", 1, 8000.00, 0,
                8000.00, 0, 8000.00);
        FacturaDetalladaDTO factura = factura("EMITIDA", "Ñandú & Cía. — 测试", "30-71234567-1", null, null, 8000.00,
                List.of(especial));

        String pagina = texto(leer(servicio.generarComprobante(factura)), 1);

        assertThat(pagina).contains("Ñandú & Cía. — 测试", "Cable «HDMI» 2 m");
    }

    @Test
    void fuenteIncrustadaConIdentityH() {
        PdfReader lector = leer(servicio.generarComprobante(emitida()));

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
}
