package ar.edu.utn.frm.facturacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.dto.CambioEstadoRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.frm.facturacion.dto.PaginaDTO;
import ar.edu.utn.frm.facturacion.dto.SolicitudPagina;
import ar.edu.utn.frm.facturacion.exception.ImporteNoRepresentableException;
import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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

    @MockBean
    private UsuarioRepository usuarios;

    private static FacturaReporteDTO fila(long id, Long numero, LocalDate fecha, String cliente, double total,
            long items, EstadoFactura estado) {
        return new FacturaReporteDTO(id, numero, fecha, cliente, "Responsable Inscripto", "Casa Central", total,
                items, estado, "PES");
    }

    private static PaginaDTO<FacturaReporteDTO> pagina(List<FacturaReporteDTO> items) {
        return PaginaDTO.de(items, SolicitudPagina.de(null, null), items.size());
    }

    @Test
    void listadoDevuelveElSobreConLasFilasDeLosDiezCamposYConvierteLosParametros() throws Exception {
        when(servicio.listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(PaginaDTO.de(List.of(
                fila(2, 15L, LocalDate.of(2026, 11, 10), "Juan Servicios", 1210.50, 2, EstadoFactura.EMITIDA),
                fila(1, null, LocalDate.of(2026, 11, 5), "Consumidor Final", 500.00, 0, EstadoFactura.BORRADOR)),
                SolicitudPagina.de(1, 2), 5));

        mvc.perform(get("/api/facturas")
                        .param("fechaDesde", "2026-11-05").param("fechaHasta", "2026-11-10")
                        .param("estado", " emitida ").param("montoMinimo", "100.10")
                        .param("numero", "15").param("clienteId", "3").param("informada", " TRUE ")
                        .param("pagina", "1").param("tamanio", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamanio").value(2))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].numeroFactura").value(15))
                .andExpect(jsonPath("$.items[0].fechaEmision").value("2026-11-10"))
                .andExpect(jsonPath("$.items[0].clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$.items[0].condicionIva").value("Responsable Inscripto"))
                .andExpect(jsonPath("$.items[0].puntoVentaDescripcion").value("Casa Central"))
                .andExpect(jsonPath("$.items[0].importeTotal").value(1210.50))
                .andExpect(jsonPath("$.items[0].cantidadItems").value(2))
                .andExpect(jsonPath("$.items[0].estado").value("EMITIDA"))
                .andExpect(jsonPath("$.items[0].codigoMoneda").value("PES"))
                .andExpect(jsonPath("$.items[1].numeroFactura").doesNotExist());

        String cuerpo = mvc.perform(get("/api/facturas")).andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode raiz = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(cuerpo);
        assertThat(raiz.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("items", "pagina", "tamanio", "totalElementos", "totalPaginas");
        assertThat(raiz.get("items").get(0).fieldNames())
                .toIterable().containsExactlyInAnyOrder("id", "numeroFactura", "fechaEmision", "clienteDenominacion",
                        "condicionIva", "puntoVentaDescripcion", "importeTotal", "cantidadItems", "estado", "codigoMoneda");

        ArgumentCaptor<FacturaFiltrosDTO> captor = ArgumentCaptor.forClass(FacturaFiltrosDTO.class);
        ArgumentCaptor<Integer> paginaCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tamanioCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(servicio, org.mockito.Mockito.atLeastOnce()).listar(captor.capture(), paginaCaptor.capture(),
                tamanioCaptor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(new FacturaFiltrosDTO(LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 10), " emitida ", 100.10, 15L, 3L, " TRUE "));
        assertThat(paginaCaptor.getAllValues().get(0)).isEqualTo(1);
        assertThat(tamanioCaptor.getAllValues().get(0)).isEqualTo(2);
    }

    @Test
    void baseSinFacturasDevuelve200ConItemsVacio() throws Exception {
        when(servicio.listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(pagina(List.of()));

        mvc.perform(get("/api/facturas"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[],\"pagina\":0,\"tamanio\":10,\"totalElementos\":0,"
                        + "\"totalPaginas\":0}", true));
    }

    @ParameterizedTest
    @CsvSource({"tamanio, diez", "numero, abc", "pagina, uno", "clienteId, x"})
    void parametroNoEnteroSeTraduceA400QueNombraElParametroSinTocarElServicio(String parametro, String valor)
            throws Exception {
        mvc.perform(get("/api/facturas").param(parametro, valor))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("'" + parametro + "'")));
        verifyNoInteractions(servicio);
    }

    @Test
    void parametroInvalidoSeTraduceA400ProblemDetailConMarcaTemporal() throws Exception {
        when(servicio.listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenThrow(new ParametroInvalidoException(
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
                "EMITIDA", 3L, "Juan Servicios", "20-12345678-9", 4L, "Responsable Inscripto", 5L, 1, "Casa Central", 6L, "PES",
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
                .andExpect(jsonPath("$.clienteCuitCuil").value("20-12345678-9"))
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

    // --- PDF de una factura: GET /api/facturas/{id}/pdf (etapa 6) ---

    @Test
    void pdfDeUnaFacturaSeDescargaComoAdjuntoConElNombreDelComprobante() throws Exception {
        when(servicio.generarComprobante(7L)).thenReturn(new ArchivoReporte("factura-0001-00000004.pdf",
                MediaType.APPLICATION_PDF, new byte[] {'%', 'P', 'D', 'F'}));

        mvc.perform(get("/api/facturas/7/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"factura-0001-00000004.pdf\""))
                .andExpect(content().bytes(new byte[] {'%', 'P', 'D', 'F'}));
    }

    @Test
    void pdfDeUnBorradorEs409SinContentDisposition() throws Exception {
        when(servicio.generarComprobante(8L)).thenThrow(new ReglaNegocioException(
                "La factura 8 está en BORRADOR: no fue informada a ARCA; emitila para imprimirla"));

        mvc.perform(get("/api/facturas/8/pdf"))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("BORRADOR"),
                        org.hamcrest.Matchers.containsString("no fue informada a ARCA"))));
    }

    @Test
    void pdfDeUnaFacturaInexistenteEs404ConElId() throws Exception {
        when(servicio.generarComprobante(999999L)).thenThrow(new RecursoNoEncontradoException("Factura", 999999L));

        mvc.perform(get("/api/facturas/999999/pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));
    }

    @Test
    void pdfConIdNoNumericoEs400QueNombraElIdSinTocarElServicio() throws Exception {
        mvc.perform(get("/api/facturas/abc/pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("'id'")));
        verifyNoInteractions(servicio);
    }

    @Test
    void pdfConGlifoNoSoportadoEs400ConElCodigoUnicodeYSinDescarga() throws Exception {
        when(servicio.generarComprobante(7L)).thenThrow(new GlifoNoSoportadoException(0xE000, "cliente"));

        mvc.perform(get("/api/facturas/7/pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("U+E000")));
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
        when(servicio.listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
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
    @CsvSource({"PUT, /api/facturas",
            "DELETE, /api/facturas"})
    void putYDeleteSobreLaColeccionSon405ConProblemDetail(String metodo, String ruta) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.valueOf(metodo), ruta)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
        verifyNoInteractions(servicio);
    }

    // --- Interceptor de X-Usuario registrado para /api/** (spec auditoria-por-request) ---

    @Test
    void elUsuarioDeclaradoEstaEnElContextoMientrasSeAtiendeLaPeticion() throws Exception {
        Usuario acortez = DatosDePrueba.usuario("acortez");
        when(usuarios.findByUsuario("acortez")).thenReturn(Optional.of(acortez));
        AtomicReference<Usuario> visto = new AtomicReference<>();
        when(servicio.listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocacion -> {
            visto.set(UsuarioActual.get());
            return pagina(List.of());
        });

        mvc.perform(get("/api/facturas").header("X-Usuario", "acortez")).andExpect(status().isOk());

        assertThat(visto.get()).isSameAs(acortez);
        assertThat(UsuarioActual.get()).as("el contexto se limpia al terminar la petición").isNull();
    }

    @Test
    void elContextoQuedaVacioTambienCuandoLaPeticionTerminaEnError() throws Exception {
        when(usuarios.findByUsuario("acortez")).thenReturn(Optional.of(DatosDePrueba.usuario("acortez")));
        when(servicio.listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("falla interna"));

        mvc.perform(get("/api/facturas").header("X-Usuario", "acortez"))
                .andExpect(status().isInternalServerError());

        assertThat(UsuarioActual.get()).isNull();
    }

    // --- Alta: POST /api/facturas (spec api-borradores-factura, validacion-request-factura) ---

    private static final String CUERPO_VALIDO = """
            {"clienteId": 1, "condicionIvaId": 2, "puntoVentaId": 3, "tipoMonedaId": 4,
             "fechaEmision": "2026-11-01", "observaciones": "Entrega en sucursal",
             "detalles": [{"listaPrecioArticuloId": 5, "cantidad": 2, "porcentajeBonificacion": 10,
                           "descripcion": "Martillo grande"}]}""";

    @Test
    void altaValidaDevuelve201ConLocationYLosDiezCampos() throws Exception {
        when(servicio.crear(org.mockito.ArgumentMatchers.any())).thenReturn(
                fila(7, null, LocalDate.of(2026, 11, 1), "Juan Servicios", 6534.00, 1, EstadoFactura.BORRADOR));

        mvc.perform(post("/api/facturas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/facturas/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.numeroFactura").doesNotExist())
                .andExpect(jsonPath("$.fechaEmision").value("2026-11-01"))
                .andExpect(jsonPath("$.clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$.condicionIva").value("Responsable Inscripto"))
                .andExpect(jsonPath("$.puntoVentaDescripcion").value("Casa Central"))
                .andExpect(jsonPath("$.importeTotal").value(6534.00))
                .andExpect(jsonPath("$.cantidadItems").value(1))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.codigoMoneda").value("PES"));

        ArgumentCaptor<FacturaRequestDTO> captor = ArgumentCaptor.forClass(FacturaRequestDTO.class);
        verify(servicio).crear(captor.capture());
        FacturaRequestDTO recibido = captor.getValue();
        assertThat(recibido.clienteId()).isEqualTo(1L);
        assertThat(recibido.fechaEmision()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(recibido.detalles()).hasSize(1);
        assertThat(recibido.detalles().get(0).cantidad()).isEqualByComparingTo("2");
        assertThat(recibido.detalles().get(0).porcentajeBonificacion()).isEqualByComparingTo("10");
        assertThat(recibido.detalles().get(0).listaPrecioArticuloId()).isEqualTo(5L);
    }

    @Test
    void sinPuntoDeVentaDevuelve400ConErrorPorCampo() throws Exception {
        String cuerpo = """
                {"condicionIvaId": 2, "tipoMonedaId": 4,
                 "detalles": [{"listaPrecioArticuloId": 5, "cantidad": 1}]}""";

        mvc.perform(post("/api/facturas").contentType("application/json").content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"))
                .andExpect(jsonPath("$.errores.puntoVentaId").value("El punto de venta es obligatorio"));

        verifyNoInteractions(servicio);
    }

    @Test
    void variosErroresSeInformanTodosJuntosYSinTocarElServicio() throws Exception {
        String cuerpo = """
                {"condicionIvaId": 2, "puntoVentaId": 3,
                 "detalles": [{"listaPrecioArticuloId": 5, "cantidad": 0}]}""";

        mvc.perform(post("/api/facturas").contentType("application/json").content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores.tipoMonedaId").value("El tipo de moneda es obligatorio"))
                .andExpect(jsonPath("$.errores['detalles[0].cantidad']")
                        .value("La cantidad debe ser mayor que cero"));

        verifyNoInteractions(servicio);
    }

    // --- Límites de la validación (RN-VA-01, RN-VA-02) ---

    private static String cuerpoConRenglon(String renglon) {
        return "{\"condicionIvaId\": 2, \"puntoVentaId\": 3, \"tipoMonedaId\": 4, \"detalles\": [" + renglon + "]}";
    }

    private static String renglonCon(String campo, String valorJson) {
        String base = "\"listaPrecioArticuloId\": 5, \"cantidad\": 1";
        return "{" + base + ", \"" + campo + "\": " + valorJson + "}";
    }

    private void permitirAlta() {
        when(servicio.crear(org.mockito.ArgumentMatchers.any())).thenReturn(
                fila(7, null, LocalDate.of(2026, 11, 1), "Consumidor Final", 1.0, 1, EstadoFactura.BORRADOR));
    }

    private void assertAceptado(String cuerpo) throws Exception {
        permitirAlta();
        mvc.perform(post("/api/facturas").contentType("application/json").content(cuerpo))
                .andExpect(status().isCreated());
    }

    private void assertRechazadoEn(String cuerpo, String campo) throws Exception {
        mvc.perform(post("/api/facturas").contentType("application/json").content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['" + campo + "']").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.emptyOrNullString())))
                .andExpect(jsonPath("$.errores.length()").value(1));
        verifyNoInteractions(servicio);
    }

    @Test
    void descripcionDe255PasaYDe256EsRechazada() throws Exception {
        assertAceptado(cuerpoConRenglon(renglonCon("descripcion", "\"" + "a".repeat(255) + "\"")));
        org.mockito.Mockito.reset(servicio);
        assertRechazadoEn(cuerpoConRenglon(renglonCon("descripcion", "\"" + "a".repeat(256) + "\"")),
                "detalles[0].descripcion");
    }

    @Test
    void observacionesDe500PasanYDe501SonRechazadas() throws Exception {
        String base = "\"condicionIvaId\": 2, \"puntoVentaId\": 3, \"tipoMonedaId\": 4, "
                + "\"detalles\": [{\"listaPrecioArticuloId\": 5, \"cantidad\": 1}]";
        assertAceptado("{" + base + ", \"observaciones\": \"" + "o".repeat(500) + "\"}");
        org.mockito.Mockito.reset(servicio);
        assertRechazadoEn("{" + base + ", \"observaciones\": \"" + "o".repeat(501) + "\"}", "observaciones");
    }

    @ParameterizedTest(name = "detalles {0}")
    @CsvSource(delimiter = '|', value = {
            "vacío|\"detalles\": []",
            "ausente|\"x\": 1"})
    void sinRenglonesEsRechazada(String caso, String fragmento) throws Exception {
        assertRechazadoEn("{\"condicionIvaId\": 2, \"puntoVentaId\": 3, \"tipoMonedaId\": 4, " + fragmento + "}",
                "detalles");
    }

    @Test
    void unRenglonNuloEsRechazadoYNombraSuPosicion() throws Exception {
        assertRechazadoEn("{\"condicionIvaId\": 2, \"puntoVentaId\": 3, \"tipoMonedaId\": 4, \"detalles\": ["
                + "{\"listaPrecioArticuloId\": 5, \"cantidad\": 1}, null]}", "detalles[1]");
    }

    @ParameterizedTest(name = "cantidad {0}")
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void cantidadNoPositivaEsRechazada(String cantidad) throws Exception {
        assertRechazadoEn(cuerpoConRenglon("{\"listaPrecioArticuloId\": 5, \"cantidad\": " + cantidad + "}"),
                "detalles[0].cantidad");
    }

    @Test
    void cantidadAusenteEsRechazadaYFraccionariaPasa() throws Exception {
        assertRechazadoEn(cuerpoConRenglon("{\"listaPrecioArticuloId\": 5}"), "detalles[0].cantidad");
        assertAceptado(cuerpoConRenglon("{\"listaPrecioArticuloId\": 5, \"cantidad\": 0.5}"));
    }

    @ParameterizedTest(name = "bonificación {0}")
    @ValueSource(strings = {"-0.01", "100.01", "-1", "101"})
    void bonificacionFueraDeRangoEsRechazada(String bonificacion) throws Exception {
        assertRechazadoEn(cuerpoConRenglon(renglonCon("porcentajeBonificacion", bonificacion)),
                "detalles[0].porcentajeBonificacion");
    }

    @ParameterizedTest(name = "bonificación {0}")
    @ValueSource(strings = {"0", "100", "0.01", "99.99", "null"})
    void bonificacionDentroDelRangoOOmitidaPasa(String bonificacion) throws Exception {
        assertAceptado(cuerpoConRenglon(renglonCon("porcentajeBonificacion", bonificacion)));
    }

    @Test
    void renglonSinListaPrecioArticuloEsRechazado() throws Exception {
        assertRechazadoEn(cuerpoConRenglon("{\"cantidad\": 1}"), "detalles[0].listaPrecioArticuloId");
    }

    // --- JSON ilegible (RN-VA-06): mensaje legible sin detalles internos del analizador ---

    private String cuerpoDeRespuesta400(String contenido) throws Exception {
        var pedido = post("/api/facturas").contentType("application/json");
        if (contenido != null) {
            pedido = pedido.content(contenido);
        }
        return mvc.perform(pedido)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Cuerpo ilegible"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void jsonMalformadoDevuelve400SinDetallesDelAnalizador() throws Exception {
        String cuerpo = cuerpoDeRespuesta400("{\"puntoVentaId\": 1,");

        assertThat(cuerpo).doesNotContain("JSON parse error").doesNotContain("com.fasterxml")
                .doesNotContain("line:").doesNotContain("column:").doesNotContain("Exception");
        assertThat(cuerpo).contains("El cuerpo de la petición falta o no es un JSON válido");
        verifyNoInteractions(servicio);
    }

    @Test
    void tipoIncompatibleNombraElCampoSinDetallesInternos() throws Exception {
        String cuerpo = cuerpoDeRespuesta400(cuerpoConRenglon(
                "{\"listaPrecioArticuloId\": 5, \"cantidad\": \"mucho\"}"));

        assertThat(cuerpo).contains("detalles[0].cantidad").contains("valor incompatible");
        assertThat(cuerpo).doesNotContain("com.fasterxml").doesNotContain("BigDecimal").doesNotContain("mucho");
        verifyNoInteractions(servicio);
    }

    @Test
    void cuerpoAusenteDevuelve400() throws Exception {
        String cuerpo = cuerpoDeRespuesta400(null);

        assertThat(cuerpo).contains("El cuerpo de la petición falta o no es un JSON válido");
        verifyNoInteractions(servicio);
    }

    // --- 409 y 404 de negocio ---

    @Test
    void reglaDeNegocioSeTraduceA409ConDetalleYMarcaTemporal() throws Exception {
        when(servicio.crear(org.mockito.ArgumentMatchers.any())).thenThrow(new ReglaNegocioException(
                "La factura 7 está en estado ANULADA y no admite la operación pedida"));

        mvc.perform(post("/api/facturas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflicto con el estado de la factura"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANULADA")))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
    }

    @Test
    void importeNoRepresentableSeTraduceA409ConSuPropioTitulo() throws Exception {
        when(servicio.crear(org.mockito.ArgumentMatchers.any())).thenThrow(
                new ImporteNoRepresentableException("iva", new java.math.BigDecimal("259259256925925.93")));

        mvc.perform(post("/api/facturas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Importe no representable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("259259256925925.93")))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
    }

    @Test
    void referenciaInexistenteSeTraduceA404ConElIdBuscado() throws Exception {
        when(servicio.crear(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RecursoNoEncontradoException("Condición de IVA", 999999L));

        mvc.perform(post("/api/facturas").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Condición de IVA")))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
    }

    // --- Edición: PUT /api/facturas/{id} ---

    @Test
    void edicionValidaDevuelve200ConLaFilaActualizada() throws Exception {
        when(servicio.actualizar(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any())).thenReturn(
                fila(7, null, LocalDate.of(2026, 11, 1), "Consumidor Final", 9680.00, 1, EstadoFactura.BORRADOR));

        mvc.perform(put("/api/facturas/7").contentType("application/json").content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.importeTotal").value(9680.00))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    void edicionConCuerpoInvalidoEs400SinTocarElServicio() throws Exception {
        mvc.perform(put("/api/facturas/7").contentType("application/json").content("{\"detalles\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['detalles']").exists());

        verifyNoInteractions(servicio);
    }

    // --- Eliminación: DELETE /api/facturas/{id} ---

    @Test
    void eliminacionDevuelve204SinCuerpo() throws Exception {
        mvc.perform(delete("/api/facturas/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(servicio).eliminar(7L);
    }

    @Test
    void eliminarUnaFacturaInexistenteEs404() throws Exception {
        org.mockito.Mockito.doThrow(new RecursoNoEncontradoException("Factura", 999999L)).when(servicio)
                .eliminar(999999L);

        mvc.perform(delete("/api/facturas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));
    }

    // --- Cambio de estado: PATCH /api/facturas/{id}/estado ---

    @Test
    void cambioDeEstadoDevuelve200ConLaFilaYPasaElDtoAlServicio() throws Exception {
        when(servicio.cambiarEstado(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(fila(7, 3L, LocalDate.of(2026, 11, 1), "Juan Servicios", 6534.00, 1, EstadoFactura.EMITIDA));

        mvc.perform(patch("/api/facturas/7/estado").contentType("application/json")
                        .content("{\"estado\": \"EMITIDA\", \"observaciones\": \"Listo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.numeroFactura").value(3));

        ArgumentCaptor<CambioEstadoRequestDTO> captor = ArgumentCaptor.forClass(CambioEstadoRequestDTO.class);
        verify(servicio).cambiarEstado(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertThat(captor.getValue().estado()).isEqualTo("EMITIDA");
        assertThat(captor.getValue().observaciones()).isEqualTo("Listo");
    }

    @ParameterizedTest(name = "cuerpo {0}")
    @ValueSource(strings = {"{}", "{\"estado\": \"\"}", "{\"estado\": \"   \"}"})
    void estadoVacioOAusenteEs400SinTocarElServicio(String cuerpo) throws Exception {
        mvc.perform(patch("/api/facturas/7/estado").contentType("application/json").content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado").value("El estado es obligatorio"));

        verifyNoInteractions(servicio);
    }

    @Test
    void observacionesDe501EnElCambioDeEstadoSonRechazadas() throws Exception {
        mvc.perform(patch("/api/facturas/7/estado").contentType("application/json")
                        .content("{\"estado\": \"EMITIDA\", \"observaciones\": \"" + "o".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.observaciones").exists());

        verifyNoInteractions(servicio);
    }
}
