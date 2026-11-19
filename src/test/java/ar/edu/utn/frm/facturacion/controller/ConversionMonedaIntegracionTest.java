package ar.edu.utn.frm.facturacion.controller;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.tipoMoneda;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Conversión de pesos a dólares: el precio de lista está en pesos y, en una factura en dólares, el servidor lo
 * divide por la cotización (1 dólar = 1.550 pesos) y lo redondea a dos decimales HALF_UP antes de calcular.
 */
class ConversionMonedaIntegracionTest extends IntegracionApiBase {

    private TipoMoneda dolares;
    private ListaPrecioArticulo precioCable;

    @BeforeEach
    void prepararCatalogos() {
        dolares = monedaRepositorio.save(tipoMoneda("DOL", "Dólares", "US$"));
        ListaPrecio lista = guardarLista("GENERAL", "Lista general");
        Articulo cable = guardarArticulo("ART-003", "Cable HDMI");
        precioCable = guardarPrecio(lista, cable, 9000);
    }

    private String cuerpo(Long monedaId, String cantidad) {
        return "{\"condicionIvaId\": " + responsableInscripto.getId() + ", \"puntoVentaId\": " + casaCentral.getId()
                + ", \"tipoMonedaId\": " + monedaId + ", \"detalles\": [{\"listaPrecioArticuloId\": "
                + precioCable.getId() + ", \"cantidad\": " + cantidad + "}]}";
    }

    @Test
    void enPesosElPrecioDeListaNoSeConvierte() throws Exception {
        mvc.perform(post("/api/facturas").contentType("application/json").content(cuerpo(pesos.getId(), "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importeTotal").value(10890.00));
    }

    @Test
    void enDolaresElPrecioSeDivideLaCotizacionYSeRedondeaADosDecimales() throws Exception {
        // 9000 / 1550 = 5,806... -> 5,81; IVA 21 % de 5,81 = 1,2201 -> 1,22; total 7,03
        mvc.perform(post("/api/facturas").contentType("application/json").content(cuerpo(dolares.getId(), "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoMoneda").value("DOL"))
                .andExpect(jsonPath("$.importeTotal").value(7.03));
    }

    @Test
    void laCantidadMultiplicaElPrecioYaConvertido() throws Exception {
        // 3 x 5,81 = 17,43; IVA = 3,6603 -> 3,66; total 21,09 (no 3 x 9000 / 1550 = 17,42 antes de redondear)
        mvc.perform(post("/api/facturas").contentType("application/json").content(cuerpo(dolares.getId(), "3")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importeTotal").value(21.09));
    }

    @Test
    void elDetalleGuardaElPrecioUnitarioEnLaMonedaDeLaFactura() throws Exception {
        String respuesta = mvc.perform(post("/api/facturas").contentType("application/json")
                        .content(cuerpo(dolares.getId(), "1")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Integer id = JsonPath.read(respuesta, "$.id");

        mvc.perform(get("/api/facturas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(5.81))
                .andExpect(jsonPath("$.detalles[0].importeNeto").value(5.81));
    }

    @Test
    void elCatalogoDeMonedasInformaLaCotizacionEnPesos() throws Exception {
        mvc.perform(get("/api/catalogos/tipos-moneda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigoMoneda=='PES')].valor", contains(1.0)))
                .andExpect(jsonPath("$[?(@.codigoMoneda=='DOL')].valor", contains(1550.0)));
    }

    @Test
    void elPrecioDeListaSeInformaEnLaMonedaBase() throws Exception {
        mvc.perform(get("/api/catalogos/lista-precio-articulos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoMoneda").value("PES"))
                .andExpect(jsonPath("$[0].valor").value(9000.0));
    }
}
