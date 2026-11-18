package ar.edu.utn.frm.facturacion.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.controller.FacturaRestController;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Cambiar el origen del frontend es un cambio de configuración, no de código. */
@WebMvcTest(FacturaRestController.class)
@TestPropertySource(properties = "facturacion.cors.allowed-origins=http://localhost:4200")
class CorsConfigPropiedadTest {

    private static final ar.edu.utn.frm.facturacion.dto.PaginaDTO<ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO> PAGINA_VACIA =
            ar.edu.utn.frm.facturacion.dto.PaginaDTO.de(List.of(), ar.edu.utn.frm.facturacion.dto.SolicitudPagina.de(null, null), 0);

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

    @Test
    void conLaPropiedadSobrescritaSeAdmiteElNuevoOrigenYSeRechazaElAnterior() throws Exception {
        when(facturaService.listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(PAGINA_VACIA);

        mvc.perform(get("/api/facturas").header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(get("/api/facturas").header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
