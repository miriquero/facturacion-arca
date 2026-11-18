package ar.edu.utn.frm.facturacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Listado paginado y filtros nuevos de la etapa 6 por HTTP (H2, sin transacción de prueba,
 * {@code open-in-view: false}). Los escenarios de paginación se piden con {@code tamanio=2} sobre cinco facturas.
 */
class FacturaPaginacionIntegracionTest extends IntegracionApiBase {

    private static final LocalDate DEL_01 = LocalDate.of(2026, 11, 1);
    private static final LocalDate DEL_05 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEL_10 = LocalDate.of(2026, 11, 10);

    private ResultActions listar(String... parametros) throws Exception {
        MockHttpServletRequestBuilder peticion = get("/api/facturas");
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(peticion);
    }

    private MockHttpServletResponse reporte(String... parametros) throws Exception {
        MockHttpServletRequestBuilder peticion = get("/api/facturas/reporte");
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(peticion).andReturn().getResponse();
    }

    private static String texto(MockHttpServletResponse respuesta) throws Exception {
        return respuesta.getContentAsString(StandardCharsets.UTF_8);
    }

    /** Factura con cantidad de renglones dada; los importes son literales sin sentido contable. */
    private FacturaVenta guardarConRenglones(Long numero, LocalDate fecha, int renglones) {
        var oferta = ofertaMartillo();
        var lista = new FacturaVenta[1];
        FacturaVenta factura = armarFactura(numero, fecha, 10.00, EstadoFactura.EMITIDA, juan);
        for (int i = 0; i < renglones; i++) {
            factura.agregarDetalle(renglon(oferta, "Martillo", 1, 3000, 3000, 630, 3630));
        }
        lista[0] = facturaRepositorio.save(factura);
        return lista[0];
    }

    private ListaPrecioArticulo ofertaMartillo() {
        var articulo = guardarArticulo("ART-001", "Martillo");
        var lista = guardarLista("GENERAL", "Lista general");
        return guardarPrecio(lista, articulo, 3000.00);
    }

    private FacturaVenta guardarInformada(Long numero, LocalDate fecha, EstadoFactura estado, Cliente cliente,
            String cae) {
        FacturaVenta factura = armarFactura(numero, fecha, 10.00, estado, cliente);
        factura.setCae(cae);
        return facturaRepositorio.save(factura);
    }

    // --- Paginación ---

    private FacturaVenta[] cincoFacturas() {
        // Números 1..5 con fechas 01/11, 05/11, 10/11, 10/11, 10/11 (las tres últimas empatan y se desempatan por id).
        return new FacturaVenta[] {
            guardarFactura(1L, DEL_01, 10.00, EstadoFactura.EMITIDA, juan),
            guardarFactura(2L, DEL_05, 10.00, EstadoFactura.EMITIDA, juan),
            guardarFactura(3L, DEL_10, 10.00, EstadoFactura.EMITIDA, juan),
            guardarFactura(4L, DEL_10, 10.00, EstadoFactura.EMITIDA, juan),
            guardarFactura(5L, DEL_10, 10.00, EstadoFactura.EMITIDA, juan)};
    }

    @Test
    void sinParametrosDevuelvePaginaCeroDeTamanioDiezConElTotal() throws Exception {
        cincoFacturas();

        listar().andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(10))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void segundaPaginaTraeLasFilasTerceraYCuartaDelOrdenTotal() throws Exception {
        FacturaVenta[] f = cincoFacturas();

        listar("pagina", "1", "tamanio", "2").andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamanio").value(2))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                // Orden total: 5, 4, 3 (misma fecha, id descendente), 2, 1. La página 1 son la 3.ª y la 4.ª.
                .andExpect(jsonPath("$.items[*].id").value(contains(f[2].getId().intValue(),
                        f[1].getId().intValue())));
    }

    @Test
    void ultimaPaginaIncompletaTraeUnaSolaFila() throws Exception {
        FacturaVenta[] f = cincoFacturas();

        listar("pagina", "2", "tamanio", "2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(f[0].getId()))
                .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @Test
    void paginaPosteriorALaUltimaTieneItemsVaciosYElTotalReal() throws Exception {
        cincoFacturas();

        listar("pagina", "9", "tamanio", "2").andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[],\"pagina\":9,\"tamanio\":2,\"totalElementos\":5,"
                        + "\"totalPaginas\":3}", true));
    }

    @Test
    void elTotalCuentaFacturasYNoRenglones() throws Exception {
        guardarConRenglones(1L, DEL_01, 3);
        FacturaVenta conUno = guardarConRenglones(2L, DEL_05, 1);

        listar("tamanio", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.items[0].id").value(conUno.getId()))
                .andExpect(jsonPath("$.items[0].cantidadItems").value(1));
    }

    @Test
    void paginacionInvalidaEs400QueNombraElParametro() throws Exception {
        listar("tamanio", "101").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tamanio")))
                .andExpect(jsonPath("$.detail").value(containsString("100")));
        listar("tamanio", "0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tamanio")));
        listar("pagina", "-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("pagina")));
        listar("tamanio", "diez").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tamanio")));
    }

    // --- Filtros nuevos ---

    @Test
    void filtraPorNumeroEnCualquierPuntoDeVenta() throws Exception {
        var otroPunto = puntoVentaRepositorio.save(DatosDePrueba.puntoVenta(2));
        FacturaVenta enCentral = guardarFactura(1L, DEL_01, 10.00, EstadoFactura.EMITIDA, juan);
        FacturaVenta otraNumero = armarFactura(1L, DEL_05, 10.00, EstadoFactura.EMITIDA, juan);
        otraNumero.setPuntoVenta(otroPunto);
        otraNumero = facturaRepositorio.save(otraNumero);
        guardarFactura(2L, DEL_10, 10.00, EstadoFactura.EMITIDA, juan);

        listar("numero", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.items[*].id").value(contains(otraNumero.getId().intValue(),
                        enCentral.getId().intValue())));
        listar("numero", "987654").andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[],\"pagina\":0,\"tamanio\":10,\"totalElementos\":0,"
                        + "\"totalPaginas\":0}", true));
        listar("numero", "0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("numero")));
    }

    @Test
    void filtraPorClienteExistenteEInexistente() throws Exception {
        Cliente otro = clienteRepositorio.save(DatosDePrueba.cliente("Distribuidora Sur", "30-71111111-2",
                DatosDePrueba.contacto("sur@ejemplo.com"), DatosDePrueba.domicilio("Mitre", "50")));
        FacturaVenta deJuan = guardarFactura(1L, DEL_01, 10.00, EstadoFactura.EMITIDA, juan);
        guardarFactura(2L, DEL_05, 10.00, EstadoFactura.EMITIDA, otro);
        guardarFactura(3L, DEL_10, 10.00, EstadoFactura.EMITIDA, null);

        listar("clienteId", String.valueOf(juan.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.items[0].id").value(deJuan.getId()));
        listar("clienteId", "999999").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void filtraPorInformadaConValoresNormalizadosYRechazaElResto() throws Exception {
        FacturaVenta borrador = guardarInformada(null, DEL_01, EstadoFactura.BORRADOR, juan, null);
        FacturaVenta emitida = guardarInformada(1L, DEL_05, EstadoFactura.EMITIDA, juan, "70000000000001");
        FacturaVenta anulada = guardarInformada(2L, DEL_10, EstadoFactura.ANULADA, juan, "70000000000002");

        listar("informada", "true").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(anulada.getId().intValue(),
                        emitida.getId().intValue())));
        listar("informada", " FALSE ").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(borrador.getId().intValue())));
        listar("informada", "si").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("informada")))
                .andExpect(jsonPath("$.detail").value(containsString("true")))
                .andExpect(jsonPath("$.detail").value(containsString("false")));
    }

    @Test
    void combinaClienteInformadaYFechaYDetectaLaContradiccion() throws Exception {
        Cliente otro = clienteRepositorio.save(DatosDePrueba.cliente("Distribuidora Sur", "30-71111111-2",
                DatosDePrueba.contacto("sur@ejemplo.com"), DatosDePrueba.domicilio("Mitre", "50")));
        FacturaVenta buscada = guardarInformada(1L, DEL_10, EstadoFactura.EMITIDA, juan, "70000000000001");
        guardarInformada(2L, DEL_01, EstadoFactura.EMITIDA, juan, "70000000000002");
        guardarInformada(3L, DEL_10, EstadoFactura.EMITIDA, otro, "70000000000003");
        guardarInformada(null, DEL_10, EstadoFactura.BORRADOR, juan, null);

        listar("clienteId", String.valueOf(juan.getId()), "informada", "true", "fechaDesde", "2026-11-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.items[0].id").value(buscada.getId()));
        listar("estado", "BORRADOR", "informada", "true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // --- Reporte: mismos filtros nuevos, sin paginación ---

    @Test
    void reporteAplicaClienteEInformadaYSoloTraeInformadasDelCliente() throws Exception {
        Cliente otro = clienteRepositorio.save(DatosDePrueba.cliente("Distribuidora Sur", "30-71111111-2",
                DatosDePrueba.contacto("sur@ejemplo.com"), DatosDePrueba.domicilio("Mitre", "50")));
        guardarInformada(1L, DEL_01, EstadoFactura.EMITIDA, juan, "70000000000001");
        guardarInformada(2L, DEL_05, EstadoFactura.ANULADA, juan, "70000000000002");
        guardarInformada(null, DEL_10, EstadoFactura.BORRADOR, juan, null);
        guardarInformada(3L, DEL_10, EstadoFactura.EMITIDA, otro, "70000000000003");

        MockHttpServletResponse r = reporte("formato", "txt", "clienteId", String.valueOf(juan.getId()),
                "informada", "true");

        assertThat(r.getStatus()).isEqualTo(200);
        String[] lineas = texto(r).split("\n");
        assertThat(lineas).hasSize(3);
        assertThat(lineas[1]).contains("Juan Servicios").contains("ANULADA");
        assertThat(lineas[2]).contains("Juan Servicios").contains("EMITIDA");
    }

    @Test
    void reporteIgnoraPaginaYTamanioYTraeTodasLasFilas() throws Exception {
        for (long numero = 1; numero <= 12; numero++) {
            guardarFactura(numero, DEL_05, 10.00, EstadoFactura.EMITIDA, juan);
        }

        MockHttpServletResponse r = reporte("formato", "txt", "pagina", "1", "tamanio", "5");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(texto(r).split("\n")).hasSize(13);
    }

    @Test
    void reporteConInformadaInvalidaEs400SinArchivo() throws Exception {
        MockHttpServletResponse r = reporte("formato", "txt", "informada", "si");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(r.getHeader("Content-Disposition")).isNull();
        assertThat(r.getContentType()).startsWith("application/problem+json");
        assertThat(texto(r)).contains("informada");
    }
}
