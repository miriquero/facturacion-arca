package ar.edu.utn.frm.facturacion.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.controller.FacturaRestController;
import ar.edu.utn.frm.facturacion.report.ArchivoReporte;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import ar.edu.utn.frm.facturacion.service.CatalogoService;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** CORS de las rutas {@code /api}: se prueba contra la configuración real ({@code application.yml}). */
@WebMvcTest(FacturaRestController.class)
class CorsConfigTest {

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
    private FacturaService facturaService;

    @MockBean
    private UsuarioRepository usuarios;

    @MockBean
    private CatalogoService catalogoService;

    @Test
    void origenDelFrontendEn5173RecibeSuPropioOrigenComoPermitido() throws Exception {
        when(facturaService.listar(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        mvc.perform(get("/api/facturas").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightDesde127001Puerto3000PermiteElOrigenYElMetodoGet() throws Exception {
        mvc.perform(options("/api/catalogos/clientes")
                        .header("Origin", "http://127.0.0.1:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("GET")));
    }

    @Test
    void laDescargaExponeElEncabezadoContentDisposition() throws Exception {
        when(facturaService.generarReporte(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("txt")))
                .thenReturn(new ArchivoReporte("facturas.txt", MediaType.parseMediaType("text/plain;charset=UTF-8"),
                        "x".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/facturas/reporte").param("formato", "txt").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("Content-Disposition")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173",
            "http://127.0.0.1:3000"})
    void losCuatroOrigenesLocalesSonAdmitidos(String origen) throws Exception {
        when(facturaService.listar(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        mvc.perform(get("/api/facturas").header("Origin", origen))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origen));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8081", "http://evil.example", "http://localhost.evil.example:5173",
            "https://localhost:5173"})
    void otroPuertoOtroHostOEsquemaDistintoSeRechazanConForbiddenYSinEncabezadoPermisivo(String origen)
            throws Exception {
        when(facturaService.listar(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        mvc.perform(get("/api/facturas").header("Origin", origen))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void sinEncabezadoOriginElClienteSinNavegadorRecibeLaRespuestaSinEncabezadosCors() throws Exception {
        when(facturaService.listar(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        mvc.perform(get("/api/facturas"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Expose-Headers"));
    }

    // --- CORS de escritura (spec api-cors) ---

    @ParameterizedTest(name = "preflight {0}")
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void elPreflightDeCadaMetodoDeEscrituraEsAdmitido(String metodo) throws Exception {
        mvc.perform(options("/api/facturas/1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", metodo))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString(metodo)));
    }

    @Test
    void elPreflightAdmiteContentTypeYXUsuario() throws Exception {
        mvc.perform(options("/api/facturas")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type, x-usuario"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsStringIgnoringCase("content-type"),
                                org.hamcrest.Matchers.containsStringIgnoringCase("x-usuario"))));
    }

    @Test
    void elPreflightConUnEncabezadoNoPrevistoEsRechazado() throws Exception {
        mvc.perform(options("/api/facturas")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Headers"));
    }

    @Test
    void elPreflightDesdeUnOrigenNoAdmitidoParaEscrituraEsRechazado() throws Exception {
        mvc.perform(options("/api/facturas")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void elPostConOrigenExponeLocation() throws Exception {
        when(facturaService.crear(org.mockito.ArgumentMatchers.any())).thenReturn(
                new ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO(7L, null, java.time.LocalDate.of(2026, 11, 1),
                        "Consumidor Final", "Responsable Inscripto", "Casa Central", 1.0, 1L,
                        ar.edu.utn.frm.facturacion.model.EstadoFactura.BORRADOR, "PES"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/facturas")
                        .header("Origin", "http://localhost:5173").contentType("application/json")
                        .content("{\"condicionIvaId\": 1, \"puntoVentaId\": 1, \"tipoMonedaId\": 1,"
                                + " \"detalles\": [{\"listaPrecioArticuloId\": 1, \"cantidad\": 1}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("Location"),
                                org.hamcrest.Matchers.containsString("Content-Disposition"))));
    }
}
