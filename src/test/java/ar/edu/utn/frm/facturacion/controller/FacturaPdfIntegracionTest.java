package ar.edu.utn.frm.facturacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@code GET /api/facturas/{id}/pdf} de punta a punta (H2, sin transacción de prueba, {@code open-in-view: false}):
 * si el comprobante leyera una asociación diferida fuera de la transacción del servicio, fallaría acá.
 */
class FacturaPdfIntegracionTest extends IntegracionApiBase {

    private FacturaVenta guardarConDosRenglones(Long numero, EstadoFactura estado,
            ar.edu.utn.frm.facturacion.model.Cliente cliente) {
        var general = guardarLista("GENERAL", "Lista general");
        var martillo = guardarArticulo("ART-001", "Martillo");
        var cable = guardarArticulo("ART-003", "Cable HDMI");
        var precioMartillo = guardarPrecio(general, martillo, 3000.00);
        var precioCable = guardarPrecio(general, cable, 8000.00);
        FacturaVenta factura = armarFactura(numero, LocalDate.of(2026, 11, 5), 22990.00, estado, cliente,
                renglon(precioMartillo, "Martillo", 1, 3000, 3000, 630, 3630),
                renglon(precioCable, "Cable HDMI", 2, 8000, 16000, 3360, 19360));
        factura.setCae("70000000000001");
        factura.setCaeFechaVencimiento(LocalDate.of(2026, 11, 15));
        factura.setResultadoAfip("A");
        if (estado == EstadoFactura.ANULADA) {
            factura.setFechaAnulacion(LocalDate.of(2026, 11, 12));
        }
        return facturaRepositorio.save(factura);
    }

    private static String texto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        StringBuilder todo = new StringBuilder();
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            todo.append(new PdfTextExtractor(lector).getTextFromPage(pagina));
        }
        return todo.toString();
    }

    @Test
    void facturaEmitidaDescargaElPdfConClienteYRenglonesSinTransaccionDePrueba() throws Exception {
        FacturaVenta emitida = guardarConDosRenglones(4L, EstadoFactura.EMITIDA, juan);

        MockHttpServletResponse r = mvc.perform(get("/api/facturas/{id}/pdf", emitida.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"factura-0001-00000004.pdf\""))
                .andReturn().getResponse();

        byte[] cuerpo = r.getContentAsByteArray();
        assertThat(new String(cuerpo, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(texto(cuerpo)).contains("0001-00000004", "Juan Servicios", "20-12345678-9", "Martillo",
                "Cable HDMI", "70000000000001").doesNotContain("ANULADA");
    }

    @Test
    void generarElPdfNoModificaLaFactura() throws Exception {
        FacturaVenta emitida = guardarConDosRenglones(4L, EstadoFactura.EMITIDA, juan);
        String antes = mvc.perform(get("/api/facturas/{id}", emitida.getId())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        mvc.perform(get("/api/facturas/{id}/pdf", emitida.getId())).andExpect(status().isOk());

        String despues = mvc.perform(get("/api/facturas/{id}", emitida.getId())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(despues).isEqualTo(antes);
        assertThat(facturaRepositorio.findById(emitida.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoFactura.EMITIDA);
    }

    @Test
    void facturaAnuladaDescargaElPdfMarcadoComoAnulado() throws Exception {
        FacturaVenta anulada = guardarConDosRenglones(4L, EstadoFactura.ANULADA, juan);

        MockHttpServletResponse r = mvc.perform(get("/api/facturas/{id}/pdf", anulada.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(texto(r.getContentAsByteArray())).contains("ANULADA", "12/11/2026", "70000000000001");
    }

    @Test
    void borradorEs409SinArchivoYElMensajeDiceQueNoFueInformada() throws Exception {
        FacturaVenta borrador = guardarFactura(null, LocalDate.of(2026, 11, 5), 100.00, EstadoFactura.BORRADOR, juan);

        mvc.perform(get("/api/facturas/{id}/pdf", borrador.getId()))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.detail").value(containsString("BORRADOR")))
                .andExpect(jsonPath("$.detail").value(containsString("no fue informada a ARCA")));
    }

    @Test
    void idInexistenteEs404YIdNoNumericoEs400() throws Exception {
        mvc.perform(get("/api/facturas/999999/pdf")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("999999")));
        mvc.perform(get("/api/facturas/abc/pdf")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("id")));
    }

    @Test
    void clienteConCaracterDeUsoPrivadoEs400ConElCodigoUnicodeYSinArchivo() throws Exception {
        var raro = clienteRepositorio.save(DatosDePrueba.cliente("Cliente  Raro", "20-99999999-9",
                DatosDePrueba.contacto("raro@ejemplo.com"), DatosDePrueba.domicilio("Calle", "1")));
        FacturaVenta emitida = guardarConDosRenglones(4L, EstadoFactura.EMITIDA, raro);

        mvc.perform(get("/api/facturas/{id}/pdf", emitida.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.detail").value(containsString("U+E000")));
    }
}
