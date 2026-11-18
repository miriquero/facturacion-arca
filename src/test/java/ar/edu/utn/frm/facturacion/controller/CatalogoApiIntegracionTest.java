package ar.edu.utn.frm.facturacion.controller;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.cliente;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.contacto;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.domicilio;
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

    // --- Búsqueda con q (etapa 6) ---

    private void agregarFerreteria() {
        clienteRepositorio.save(cliente("Ferretería Ñandú", "30-71234567-1", contacto("f@ejemplo.com"),
                domicilio("Belgrano", "200")));
    }

    @Test
    void clientesPorParteDeLaDenominacionConEspaciosExteriores() throws Exception {
        agregarFerreteria();

        mvc.perform(get("/api/catalogos/clientes").param("q", " juan "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].etiqueta").value("Juan Servicios (20-12345678-9)"));
    }

    @Test
    void clientesPorParteDelCuit() throws Exception {
        agregarFerreteria();

        mvc.perform(get("/api/catalogos/clientes").param("q", "7123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].etiqueta").value("Ferretería Ñandú (30-71234567-1)"));
    }

    @Test
    void clientesSinCoincidenciasYComodinLiteralDanArregloVacio() throws Exception {
        agregarFerreteria();

        mvc.perform(get("/api/catalogos/clientes").param("q", "zzz")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[]", true));
        mvc.perform(get("/api/catalogos/clientes").param("q", "%")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[]", true));
    }

    @Test
    void sinQOConQEnBlancoElCatalogoSigueCompleto() throws Exception {
        agregarFerreteria();

        mvc.perform(get("/api/catalogos/clientes")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/catalogos/clientes").param("q", "   ")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void qDeMasDeCienCaracteresEs400QueMencionaQYElMaximo() throws Exception {
        mvc.perform(get("/api/catalogos/clientes").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("q")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("100")));
        mvc.perform(get("/api/catalogos/lista-precio-articulos").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ofertasPorDenominacionDelArticuloYPorCodigoDeLista() throws Exception {
        var general = guardarLista("GENERAL", "Lista general");
        var mayorista = guardarLista("MAYORISTA", "Lista mayorista");
        var martillo = guardarArticulo("ART-001", "Martillo");
        var cable = guardarArticulo("ART-003", "Cable HDMI");
        guardarPrecio(general, martillo, 3000.00);
        var enMayorista = guardarPrecio(mayorista, martillo, 2500.00);
        guardarPrecio(general, cable, 8000.00);

        mvc.perform(get("/api/catalogos/lista-precio-articulos").param("q", "martillo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].valor").value(3000.00))
                .andExpect(jsonPath("$[1].valor").value(2500.00));
        mvc.perform(get("/api/catalogos/lista-precio-articulos").param("q", "mayor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(enMayorista.getId()))
                .andExpect(jsonPath("$[0].etiqueta").value("ART-001 - Martillo (MAYORISTA)"));
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
