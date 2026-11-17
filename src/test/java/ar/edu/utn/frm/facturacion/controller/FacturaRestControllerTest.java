package ar.edu.utn.frm.facturacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.report.ArchivoReporte;
import ar.edu.utn.frm.facturacion.report.GlifoNoSoportadoException;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FacturaRestController.class)
class FacturaRestControllerTest {

    @TestConfiguration
    static class RelojFijo {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FacturaService servicio;

    private static FacturaReporteDTO fila(long id, Long numero, LocalDate fecha, String cliente, double total,
            long items, EstadoFactura estado) {
        return new FacturaReporteDTO(id, numero, fecha, cliente, "Responsable Inscripto", "Casa Central", total,
                items, estado, "PES");
    }

    @Test
    void listadoDevuelveLasFilasConLosDiezCamposYConvierteLosParametros() throws Exception {
        when(servicio.listar(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                fila(2, 15L, LocalDate.of(2026, 11, 10), "Juan Servicios", 1210.50, 2, EstadoFactura.EMITIDA),
                fila(1, null, LocalDate.of(2026, 11, 5), "Consumidor Final", 500.00, 0, EstadoFactura.BORRADOR)));

        mvc.perform(get("/api/facturas")
                        .param("fechaDesde", "2026-11-05").param("fechaHasta", "2026-11-10")
                        .param("estado", " emitida ").param("montoMinimo", "100.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].numeroFactura").value(15))
                .andExpect(jsonPath("$[0].fechaEmision").value("2026-11-10"))
                .andExpect(jsonPath("$[0].clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$[0].condicionIva").value("Responsable Inscripto"))
                .andExpect(jsonPath("$[0].puntoVentaDescripcion").value("Casa Central"))
                .andExpect(jsonPath("$[0].importeTotal").value(1210.50))
                .andExpect(jsonPath("$[0].cantidadItems").value(2))
                .andExpect(jsonPath("$[0].estado").value("EMITIDA"))
                .andExpect(jsonPath("$[0].codigoMoneda").value("PES"))
                .andExpect(jsonPath("$[1].numeroFactura").doesNotExist());

        String cuerpo = mvc.perform(get("/api/facturas")).andReturn().getResponse().getContentAsString();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(cuerpo).get(0).fieldNames())
                .toIterable().containsExactlyInAnyOrder("id", "numeroFactura", "fechaEmision", "clienteDenominacion",
                        "condicionIva", "puntoVentaDescripcion", "importeTotal", "cantidadItems", "estado", "codigoMoneda");

        ArgumentCaptor<FacturaFiltrosDTO> captor = ArgumentCaptor.forClass(FacturaFiltrosDTO.class);
        verify(servicio, org.mockito.Mockito.atLeastOnce()).listar(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(
                new FacturaFiltrosDTO(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 10), " emitida ", 100.10));
    }

    @Test
    void baseSinFacturasDevuelve200ConArregloVacio() throws Exception {
        when(servicio.listar(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        mvc.perform(get("/api/facturas"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    @Test
    void parametroInvalidoSeTraduceA400ProblemDetailConMarcaTemporal() throws Exception {
        when(servicio.listar(org.mockito.ArgumentMatchers.any())).thenThrow(new ParametroInvalidoException(
                "El estado 'PAGADA' no es válido. Valores admitidos: [BORRADOR, EMITIDA, ANULADA]."));

        mvc.perform(get("/api/facturas").param("estado", "PAGADA"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("PAGADA")))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({
            "fechaDesde, 05/11/2026, yyyy-MM-dd",
            "fechaHasta, ayer, yyyy-MM-dd",
            "montoMinimo, cien, número"})
    void tipoIncompatibleSeTraduceA400ConDetalleEnEspanolQueNombraElParametro(String parametro, String valor,
            String esperado) throws Exception {
        mvc.perform(get("/api/facturas").param(parametro, valor))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("'" + parametro + "'")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("'" + valor + "'")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(esperado)))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Failed to convert"))))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
        verifyNoInteractions(servicio);
    }

    @Test
    void detalleDevuelveCabeceraYRenglones() throws Exception {
        when(servicio.obtenerDetalle(7L)).thenReturn(new FacturaDetalladaDTO(7L, 1L, LocalDate.of(2026, 11, 5),
                "EMITIDA", 3L, "Juan Servicios", 4L, "Responsable Inscripto", 5L, 1, "Casa Central", 6L, "PES",
                7260.00, 0.0, 7260.00, "70000000000001", LocalDate.of(2026, 11, 15), "A", null,
                "Entrega en mostrador", List.of(
                        new FacturaDetalleDTO(11L, 21L, "ART-001", "Martillo", 1, 3000.00, 0, 3000.00, 630.00, 3630.00),
                        new FacturaDetalleDTO(12L, 22L, "ART-003", "Cable HDMI", 2, 8000.00, 0, 16000.00, 0, 16000.00))));

        mvc.perform(get("/api/facturas/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.fechaEmision").value("2026-11-05"))
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.clienteId").value(3))
                .andExpect(jsonPath("$.clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$.condicionIvaId").value(4))
                .andExpect(jsonPath("$.condicionIva").value("Responsable Inscripto"))
                .andExpect(jsonPath("$.puntoVentaId").value(5))
                .andExpect(jsonPath("$.puntoVentaNumero").value(1))
                .andExpect(jsonPath("$.puntoVentaDescripcion").value("Casa Central"))
                .andExpect(jsonPath("$.tipoMonedaId").value(6))
                .andExpect(jsonPath("$.codigoMoneda").value("PES"))
                .andExpect(jsonPath("$.importeTotal").value(7260.00))
                .andExpect(jsonPath("$.importeCobrado").value(0.0))
                .andExpect(jsonPath("$.importeSaldo").value(7260.00))
                .andExpect(jsonPath("$.cae").value("70000000000001"))
                .andExpect(jsonPath("$.caeFechaVencimiento").value("2026-11-15"))
                .andExpect(jsonPath("$.resultadoAfip").value("A"))
                .andExpect(jsonPath("$.fechaAnulacion").isEmpty())
                .andExpect(jsonPath("$.observaciones").value("Entrega en mostrador"))
                .andExpect(jsonPath("$.detalles.length()").value(2))
                .andExpect(jsonPath("$.detalles[0].id").value(11))
                .andExpect(jsonPath("$.detalles[0].listaPrecioArticuloId").value(21))
                .andExpect(jsonPath("$.detalles[0].articuloCodigo").value("ART-001"))
                .andExpect(jsonPath("$.detalles[0].descripcion").value("Martillo"))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(1.0))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(3000.00))
                .andExpect(jsonPath("$.detalles[1].articuloCodigo").value("ART-003"))
                .andExpect(jsonPath("$.detalles[1].importeSubtotal").value(16000.00));
    }

    @Test
    void facturaInexistenteSeTraduceA404ConElIdEnElDetalle() throws Exception {
        when(servicio.obtenerDetalle(999999L)).thenThrow(new RecursoNoEncontradoException("Factura", 999999L));

        mvc.perform(get("/api/facturas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
    }

    @Test
    void idNoNumericoSeTraduceA400QueNombraElParametroId() throws Exception {
        mvc.perform(get("/api/facturas/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("'id'")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("'abc'")));
        verifyNoInteractions(servicio);
    }

    private static ArchivoReporte txt(String contenido) {
        return new ArchivoReporte("facturas.txt", MediaType.parseMediaType("text/plain;charset=UTF-8"),
                contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void reporteTxtSeDescargaConTipoNombreYCuerpoExactos() throws Exception {
        when(servicio.generarReporte(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("txt")))
                .thenReturn(txt("Número\tFecha\n15\t2026-11-10\n"));

        mvc.perform(get("/api/facturas/reporte").param("formato", "txt").param("estado", "EMITIDA"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"facturas.txt\""))
                .andExpect(content().string("Número\tFecha\n15\t2026-11-10\n"));

        ArgumentCaptor<FacturaFiltrosDTO> captor = ArgumentCaptor.forClass(FacturaFiltrosDTO.class);
        verify(servicio).generarReporte(captor.capture(), org.mockito.ArgumentMatchers.eq("txt"));
        assertThat(captor.getValue()).isEqualTo(new FacturaFiltrosDTO(null, null, "EMITIDA", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "SIN_PARAMETRO"})
    void sinFormatoOConFormatoVacioElServicioRecibePdf(String formato) throws Exception {
        when(servicio.generarReporte(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("pdf")))
                .thenReturn(new ArchivoReporte("facturas.pdf", MediaType.APPLICATION_PDF, new byte[] {'%', 'P', 'D', 'F'}));
        var peticion = get("/api/facturas/reporte");
        if (!formato.equals("SIN_PARAMETRO")) {
            peticion.param("formato", formato);
        }

        mvc.perform(peticion)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"facturas.pdf\""));

        verify(servicio).generarReporte(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("pdf"));
    }

    @Test
    void glifoNoSoportadoEnElPdfSeTraduceA400SinDescarga() throws Exception {
        when(servicio.generarReporte(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("pdf")))
                .thenThrow(new GlifoNoSoportadoException(0xE000, "cliente", 1));

        mvc.perform(get("/api/facturas/reporte").param("formato", "pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("PDF no disponible para estos datos"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("U+E000")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Excel o TXT")))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
    }

    @Test
    void formatoInvalidoSeTraduceA400SinDescarga() throws Exception {
        when(servicio.generarReporte(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("doc")))
                .thenThrow(new ParametroInvalidoException("El formato 'doc' no es válido. Formatos admitidos: pdf, xlsx, excel, txt, tsv."));

        mvc.perform(get("/api/facturas/reporte").param("formato", "doc"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("doc")));
    }

    @Test
    void errorInesperadoEs500ConDetalleGenericoSinFiltrarElMensajeInterno() throws Exception {
        when(servicio.listar(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("SQL interno secreto"));

        String cuerpo = mvc.perform(get("/api/facturas"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Intente nuevamente."))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("SQL interno secreto").doesNotContain("java.").doesNotContain("Exception");
    }

    @ParameterizedTest
    @CsvSource({"DELETE, /api/facturas/1", "POST, /api/facturas", "PUT, /api/facturas/1", "PATCH, /api/facturas/1"})
    void escrituraTodaviaNoDisponibleEs405ConProblemDetail(String metodo, String ruta) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.valueOf(metodo), ruta)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
        verifyNoInteractions(servicio);
    }
}
