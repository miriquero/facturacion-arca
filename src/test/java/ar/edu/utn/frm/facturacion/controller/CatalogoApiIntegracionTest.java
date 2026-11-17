package ar.edu.utn.frm.facturacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Catálogos por HTTP real de MockMvc, sin transacción de prueba y con {@code open-in-view: false}. */
class CatalogoApiIntegracionTest extends IntegracionApiBase {

    @Test
    void listaPrecioArticulosSeResuelveSinErrorDeCargaDiferida() throws Exception {
        var general = guardarLista("GENERAL", "Lista general");
        var mayorista = guardarLista("MAYORISTA", "Lista mayorista");
        var martillo = guardarArticulo("ART-001", "Martillo");
        var enGeneral = guardarPrecio(general, martillo, 3000.00);
        var enMayorista = guardarPrecio(mayorista, martillo, 2500.00);

        mvc.perform(get("/api/catalogos/lista-precio-articulos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(enGeneral.getId()))
                .andExpect(jsonPath("$[0].etiqueta").value("ART-001 - Martillo (GENERAL)"))
                .andExpect(jsonPath("$[0].valor").value(3000.00))
                .andExpect(jsonPath("$[0].codigoMoneda").isEmpty())
                .andExpect(jsonPath("$[1].id").value(enMayorista.getId()))
                .andExpect(jsonPath("$[1].etiqueta").value("ART-001 - Martillo (MAYORISTA)"))
                .andExpect(jsonPath("$[1].valor").value(2500.00));
    }

    @Test
    void losOtrosCuatroCatalogosRespondenPorHttp() throws Exception {
        mvc.perform(get("/api/catalogos/puntos-venta")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("0001 - Casa Central"));
        mvc.perform(get("/api/catalogos/clientes")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("Juan Servicios (20-12345678-9)"));
        mvc.perform(get("/api/catalogos/condiciones-iva")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("Responsable Inscripto"));
        mvc.perform(get("/api/catalogos/tipos-moneda")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("Pesos"))
                .andExpect(jsonPath("$[0].codigoMoneda").value("PES"));
    }

    @Test
    void unaAltaPorPostEs405YNoCambiaLaCantidadDeClientes() throws Exception {
        long antes = clienteRepositorio.count();

        mvc.perform(post("/api/catalogos/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"denominacion\":\"Intruso\",\"cuitCuil\":\"20-1-1\"}"))
                .andExpect(status().isMethodNotAllowed());

        assertThat(clienteRepositorio.count()).isEqualTo(antes);
    }
}
