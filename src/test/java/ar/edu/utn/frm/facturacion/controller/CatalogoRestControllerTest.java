package ar.edu.utn.frm.facturacion.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.service.CatalogoService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogoRestController.class)
class CatalogoRestControllerTest {

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
    private CatalogoService servicio;

    private static final List<OpcionDTO> OPCIONES = List.of(
            new OpcionDTO(1L, "Primera", null, null),
            new OpcionDTO(2L, "Segunda", 3000.00, "PES"));

    private void simular(String ruta) {
        switch (ruta) {
            case "puntos-venta" -> when(servicio.puntosVenta()).thenReturn(OPCIONES);
            case "clientes" -> when(servicio.clientes()).thenReturn(OPCIONES);
            case "condiciones-iva" -> when(servicio.condicionesIva()).thenReturn(OPCIONES);
            case "tipos-moneda" -> when(servicio.tiposMoneda()).thenReturn(OPCIONES);
            case "lista-precio-articulos" -> when(servicio.listaPrecioArticulos()).thenReturn(OPCIONES);
            default -> throw new IllegalArgumentException(ruta);
        }
    }

    @ParameterizedTest
    @CsvSource({"puntos-venta", "clientes", "condiciones-iva", "tipos-moneda", "lista-precio-articulos"})
    void cadaCatalogoDevuelveSusOpcionesConLosCuatroCampos(String ruta) throws Exception {
        simular(ruta);

        mvc.perform(get("/api/catalogos/" + ruta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].etiqueta").value("Primera"))
                .andExpect(jsonPath("$[0].valor").isEmpty())
                .andExpect(jsonPath("$[0].codigoMoneda").isEmpty())
                .andExpect(jsonPath("$[1].valor").value(3000.00))
                .andExpect(jsonPath("$[1].codigoMoneda").value("PES"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"valor\":null")));
    }

    @ParameterizedTest
    @CsvSource({"POST", "PUT", "PATCH", "DELETE"})
    void escrituraSobreUnCatalogoEs405ConProblemDetail(String metodo) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.valueOf(metodo), "/api/catalogos/clientes")
                        .contentType("application/json").content("{\"denominacion\":\"Nuevo\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.timestamp").value("2026-11-17T12:00:00Z"));
        org.mockito.Mockito.verifyNoInteractions(servicio);
    }
}
