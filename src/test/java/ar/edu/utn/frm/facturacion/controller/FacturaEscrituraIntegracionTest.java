package ar.edu.utn.frm.facturacion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Escritura de facturas por HTTP con el stack completo (controlador, servicio, JPA, H2), sin transacción de
 * prueba: cada petición confirma o revierte de verdad. Reloj fijo 2026-11-17 (Argentina).
 */
class FacturaEscrituraIntegracionTest extends IntegracionApiBase {

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojDePrueba() {
            return Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneOffset.UTC);
        }
    }

    /** CAE fijo (o fallando a pedido) para que las pruebas de integración sean deterministas (IN-09). */
    static class GeneradorCaeDePrueba implements ar.edu.utn.frm.facturacion.service.GeneradorCae {
        volatile boolean fallar;

        @Override
        public String generar() {
            if (fallar) {
                throw new IllegalStateException("falla simulada del generador de CAE");
            }
            return "12345678901234";
        }
    }

    @TestConfiguration
    static class CaeFijo {
        @Bean
        @Primary
        GeneradorCaeDePrueba generadorDePrueba() {
            return new GeneradorCaeDePrueba();
        }
    }

    @Autowired
    protected GeneradorCaeDePrueba generadorCae;

    protected Usuario sistema;
    protected Usuario acortez;
    protected Usuario mriquero;
    protected ListaPrecioArticulo precioMartillo;
    protected ListaPrecioArticulo precioCable;

    @BeforeEach
    void prepararCatalogosDeEscritura() {
        generadorCae.fallar = false;
        sistema = usuarioRepositorio.findByUsuario("sistema").orElseThrow();
        acortez = usuarioRepositorio.save(ar.edu.utn.frm.facturacion.model.DatosDePrueba.usuario("acortez"));
        mriquero = usuarioRepositorio.save(ar.edu.utn.frm.facturacion.model.DatosDePrueba.usuario("mriquero"));
        ListaPrecio general = guardarLista("GENERAL", "Lista general");
        Articulo martillo = guardarArticulo("ART-001", "Martillo");
        Articulo cable = guardarArticulo("ART-003", "Cable HDMI");
        precioMartillo = guardarPrecio(general, martillo, 3000);
        precioCable = guardarPrecio(general, cable, 8000);
    }

    /** La petición limpia el contexto al terminar (como en producción): se repone para usar los repositorios. */
    protected void comoSistema() {
        UsuarioActual.set(sistema);
    }

    protected static String renglonJson(Long listaPrecioArticuloId, String cantidad) {
        return "{\"listaPrecioArticuloId\": " + listaPrecioArticuloId + ", \"cantidad\": " + cantidad + "}";
    }

    protected String cuerpo(Long clienteId, String... renglones) {
        String cliente = clienteId == null ? "" : "\"clienteId\": " + clienteId + ", ";
        return "{" + cliente + "\"condicionIvaId\": " + responsableInscripto.getId()
                + ", \"puntoVentaId\": " + casaCentral.getId() + ", \"tipoMonedaId\": " + pesos.getId()
                + ", \"detalles\": [" + String.join(", ", renglones) + "]}";
    }

    @Test
    void borradorConClienteYDosRenglones() throws Exception {
        String solicitud = cuerpo(juan.getId(),
                renglonJson(precioMartillo.getId(), "1"), renglonJson(precioCable.getId(), "2"));

        mvc.perform(post("/api/facturas").contentType("application/json").content(solicitud))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/facturas/")))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.numeroFactura").doesNotExist())
                .andExpect(jsonPath("$.clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$.cantidadItems").value(2))
                .andExpect(jsonPath("$.importeTotal").value(22990.00));
    }

    // --- Auxiliares de lectura ---

    protected static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    protected com.fasterxml.jackson.databind.JsonNode postear(String solicitud, String xUsuario) throws Exception {
        var pedido = post("/api/facturas").contentType("application/json").content(solicitud);
        if (xUsuario != null) {
            pedido = pedido.header("X-Usuario", xUsuario);
        }
        String respuesta = mvc.perform(pedido).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return JSON.readTree(respuesta);
    }

    protected com.fasterxml.jackson.databind.JsonNode detalleDe(long id) throws Exception {
        String respuesta = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/facturas/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return JSON.readTree(respuesta);
    }

    protected ListaPrecioArticulo precioDeArticuloConAlicuota(String codigo, String denominacion, double alicuota,
            double precioVenta) {
        Articulo articulo = articuloRepositorio.save(ar.edu.utn.frm.facturacion.model.DatosDePrueba
                .articulo(codigo, denominacion, null, null, alicuota));
        return guardarPrecio(precioMartillo.getListaPrecio(), articulo, precioVenta);
    }

    // --- Alta: respuesta, fecha, precio, descripción y alícuota (spec api-borradores-factura) ---

    @Test
    void laRespuestaDelAltaEsIgualALaFilaDelListado() throws Exception {
        var creada = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "2")), null);

        String listado = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/facturas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var filas = JSON.readTree(listado).get("items");

        org.assertj.core.api.Assertions.assertThat(filas).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(filas.get(0)).isEqualTo(creada);
    }

    @Test
    void altaSinClienteEsAConsumidorFinal() throws Exception {
        var creada = postear(cuerpo(null, renglonJson(precioMartillo.getId(), "1")), null);

        org.assertj.core.api.Assertions.assertThat(creada.get("clienteDenominacion").asText())
                .isEqualTo("Consumidor Final");
        org.assertj.core.api.Assertions.assertThat(detalleDe(creada.get("id").asLong()).get("clienteId").isNull())
                .isTrue();
    }

    @Test
    void fechaOmitidaEsHoyYFechaInformadaSeRespeta() throws Exception {
        var omitida = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")), null);
        String conFecha = cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"))
                .replace("{\"clienteId\"", "{\"fechaEmision\": \"2026-11-01\", \"clienteId\"");
        var informada = postear(conFecha, null);

        org.assertj.core.api.Assertions.assertThat(omitida.get("fechaEmision").asText()).isEqualTo("2026-11-17");
        org.assertj.core.api.Assertions.assertThat(informada.get("fechaEmision").asText()).isEqualTo("2026-11-01");
    }

    @Test
    void elPrecioSaleDeLaListaYUnPrecioEnviadoSeIgnora() throws Exception {
        String solicitud = cuerpo(juan.getId(),
                "{\"listaPrecioArticuloId\": " + precioMartillo.getId() + ", \"cantidad\": 2, \"precioUnitario\": 1}");

        var creada = postear(solicitud, null);

        var renglon = detalleDe(creada.get("id").asLong()).get("detalles").get(0);
        org.assertj.core.api.Assertions.assertThat(renglon.get("precioUnitario").asDouble()).isEqualTo(3000.0);
        org.assertj.core.api.Assertions.assertThat(renglon.get("importeSubtotal").asDouble()).isEqualTo(7260.0);
        org.assertj.core.api.Assertions.assertThat(creada.get("importeTotal").asDouble()).isEqualTo(7260.0);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "descripción {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "\"\"", "\"   \""})
    void descripcionNulaOEnBlancoUsaLaDenominacionDelArticulo(String descripcion) throws Exception {
        String solicitud = cuerpo(juan.getId(), "{\"listaPrecioArticuloId\": " + precioMartillo.getId()
                + ", \"cantidad\": 1, \"descripcion\": " + descripcion + "}");

        var creada = postear(solicitud, null);

        var renglon = detalleDe(creada.get("id").asLong()).get("detalles").get(0);
        org.assertj.core.api.Assertions.assertThat(renglon.get("descripcion").asText()).isEqualTo("Martillo");
        org.assertj.core.api.Assertions.assertThat(renglon.get("porcentajeBonificacion").asDouble()).isEqualTo(0.0);
    }

    @Test
    void descripcionInformadaSeConserva() throws Exception {
        String solicitud = cuerpo(juan.getId(), "{\"listaPrecioArticuloId\": " + precioMartillo.getId()
                + ", \"cantidad\": 1, \"descripcion\": \"Martillo de goma\"}");

        var creada = postear(solicitud, null);

        org.assertj.core.api.Assertions.assertThat(
                detalleDe(creada.get("id").asLong()).get("detalles").get(0).get("descripcion").asText())
                .isEqualTo("Martillo de goma");
    }

    @Test
    void alicuotaNulaUsaVeintiunoYUnaPropiaSeRespeta() throws Exception {
        ListaPrecioArticulo alfajor = precioDeArticuloConAlicuota("ART-010", "Alfajor", 10.5, 8000);

        var sinAlicuota = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "2")), null);
        var conAlicuota = postear(cuerpo(juan.getId(), renglonJson(alfajor.getId(), "1")), null);

        var propio = detalleDe(conAlicuota.get("id").asLong()).get("detalles").get(0);
        var porDefecto = detalleDe(sinAlicuota.get("id").asLong()).get("detalles").get(0);
        org.assertj.core.api.Assertions.assertThat(porDefecto.get("importeIva").asDouble()).isEqualTo(1260.0);
        org.assertj.core.api.Assertions.assertThat(porDefecto.get("importeSubtotal").asDouble()).isEqualTo(7260.0);
        org.assertj.core.api.Assertions.assertThat(propio.get("importeIva").asDouble()).isEqualTo(840.0);
        org.assertj.core.api.Assertions.assertThat(propio.get("importeSubtotal").asDouble()).isEqualTo(8840.0);
    }

    // --- Referencias inexistentes: 404 sin persistencia parcial (RN-VA-04) ---

    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbc;

    protected long contarFacturas() {
        return jdbc.queryForObject("select count(*) from factura_venta", Long.class);
    }

    protected long contarRenglones() {
        return jdbc.queryForObject("select count(*) from factura_venta_detalle", Long.class);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} inexistente")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"condicionIvaId", "clienteId", "puntoVentaId",
            "tipoMonedaId"})
    void referenciaDeCabeceraInexistenteEs404YNoGuardaNada(String campo) throws Exception {
        String solicitud = cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"))
                .replaceAll("\"" + campo + "\": \\d+", "\"" + campo + "\": 999999");

        mvc.perform(post("/api/facturas").contentType("application/json").content(solicitud))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));

        org.assertj.core.api.Assertions.assertThat(contarFacturas()).isZero();
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isZero();
    }

    @Test
    void listaPrecioArticuloInexistenteEnElSegundoRenglonEs404YNoGuardaNada() throws Exception {
        String solicitud = cuerpo(juan.getId(),
                renglonJson(precioMartillo.getId(), "1"), renglonJson(999999L, "1"));

        mvc.perform(post("/api/facturas").contentType("application/json").content(solicitud))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));

        org.assertj.core.api.Assertions.assertThat(contarFacturas()).isZero();
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isZero();
    }

    // --- Importe no representable: 409 sin persistencia (spec calculo-importes-factura) ---

    @Test
    void importeConMasDigitosDeLosQueCabenEs409YLaBaseNoCambia() throws Exception {
        ListaPrecioArticulo caro = precioDeArticuloConAlicuota("ART-099", "Pieza de precio extremo", 21,
                1234567890123456.8);

        mvc.perform(post("/api/facturas").contentType("application/json")
                        .content(cuerpo(juan.getId(), renglonJson(caro.getId(), "1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Importe no representable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("importeIva")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("259259256925925.93")));

        org.assertj.core.api.Assertions.assertThat(contarFacturas()).isZero();
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isZero();
    }

    // --- Límites por HTTP real (255/256 y 500) ---

    @Test
    void descripcionDe255SeGuardaCompletaYDe256Es400SinFacturaNueva() throws Exception {
        String de255 = "d".repeat(255);
        var creada = postear(cuerpo(juan.getId(), "{\"listaPrecioArticuloId\": " + precioMartillo.getId()
                + ", \"cantidad\": 1, \"descripcion\": \"" + de255 + "\"}"), null);
        org.assertj.core.api.Assertions.assertThat(
                detalleDe(creada.get("id").asLong()).get("detalles").get(0).get("descripcion").asText())
                .isEqualTo(de255);
        long antes = contarFacturas();

        mvc.perform(post("/api/facturas").contentType("application/json")
                        .content(cuerpo(juan.getId(), "{\"listaPrecioArticuloId\": " + precioMartillo.getId()
                                + ", \"cantidad\": 1, \"descripcion\": \"" + "d".repeat(256) + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['detalles[0].descripcion']").exists());

        org.assertj.core.api.Assertions.assertThat(contarFacturas()).isEqualTo(antes);
    }

    @Test
    void observacionesDe500SeGuardanCompletas() throws Exception {
        String de500 = "o".repeat(500);
        String solicitud = cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"))
                .replace("{\"clienteId\"", "{\"observaciones\": \"" + de500 + "\", \"clienteId\"");

        var creada = postear(solicitud, null);

        org.assertj.core.api.Assertions.assertThat(
                detalleDe(creada.get("id").asLong()).get("observaciones").asText()).isEqualTo(de500);
    }

    // --- Auditoría del alta con el stack completo (spec auditoria-por-request) ---

    protected String usuarioDeCarga(long facturaId) {
        return jdbc.queryForObject("select u.usuario from factura_venta f join usuario u on u.id = f.usuario_carga_id"
                + " where f.id = ?", String.class, facturaId);
    }

    protected String usuarioDeModificacion(long facturaId) {
        return jdbc.queryForObject("select u.usuario from factura_venta f"
                + " join usuario u on u.id = f.usuario_modificacion_id where f.id = ?", String.class, facturaId);
    }

    @Test
    void elAltaConUsuarioDeclaradoQuedaAuditadaComoEse() throws Exception {
        var creada = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")), "acortez");

        long id = creada.get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(id)).isEqualTo("acortez");
        org.assertj.core.api.Assertions.assertThat(usuarioDeModificacion(id)).isEqualTo("acortez");
        org.assertj.core.api.Assertions.assertThat(UsuarioActual.get()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "X-Usuario [{0}]")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "intruso", "   "})
    void conEncabezadoVacioODesconocidoElAltaEsDelUsuarioPorDefecto(String encabezado) throws Exception {
        var creada = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")), encabezado);

        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(creada.get("id").asLong())).isEqualTo("sistema");
        org.assertj.core.api.Assertions.assertThat(UsuarioActual.get()).isNull();
    }

    @Test
    void sinEncabezadoElAltaEsDeSistemaYLosEspaciosSeIgnoran() throws Exception {
        var sin = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")), null);
        var conEspacios = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")), "  acortez  ");

        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(sin.get("id").asLong())).isEqualTo("sistema");
        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(conEspacios.get("id").asLong()))
                .isEqualTo("acortez");
    }

    @Test
    void dosPeticionesSeguidasNoSeContaminan() throws Exception {
        var primera = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")), "acortez");
        var segunda = postear(cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")), null);

        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(primera.get("id").asLong())).isEqualTo("acortez");
        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(segunda.get("id").asLong())).isEqualTo("sistema");
    }

    // --- Edición de borradores: PUT (spec api-borradores-factura) ---

    protected com.fasterxml.jackson.databind.JsonNode poner(long id, String solicitud, String xUsuario)
            throws Exception {
        var pedido = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/facturas/" + id)
                .contentType("application/json").content(solicitud);
        if (xUsuario != null) {
            pedido = pedido.header("X-Usuario", xUsuario);
        }
        String respuesta = mvc.perform(pedido).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return JSON.readTree(respuesta);
    }

    protected ar.edu.utn.frm.facturacion.model.TipoMoneda crearDolares() {
        comoSistema();
        return monedaRepositorio.save(ar.edu.utn.frm.facturacion.model.DatosDePrueba
                .tipoMoneda("DOL", "Dólar estadounidense", "US$"));
    }

    @Test
    void reemplazoDeDosRenglonesPorUno() throws Exception {
        var creada = postear(cuerpo(juan.getId(),
                renglonJson(precioMartillo.getId(), "1"), renglonJson(precioCable.getId(), "2")), null);
        long id = creada.get("id").asLong();
        long renglonesAntes = contarRenglones();
        var dolares = crearDolares();
        String nueva = cuerpo(null, renglonJson(precioCable.getId(), "1"))
                .replace("\"tipoMonedaId\": " + pesos.getId(), "\"tipoMonedaId\": " + dolares.getId());

        var editada = poner(id, nueva, null);

        org.assertj.core.api.Assertions.assertThat(renglonesAntes).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(editada.get("id").asLong()).isEqualTo(id);
        org.assertj.core.api.Assertions.assertThat(editada.get("importeTotal").asDouble()).isEqualTo(9680.0);
        org.assertj.core.api.Assertions.assertThat(editada.get("cantidadItems").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(editada.get("clienteDenominacion").asText())
                .isEqualTo("Consumidor Final");
        org.assertj.core.api.Assertions.assertThat(editada.get("codigoMoneda").asText()).isEqualTo("DOL");
        var detalle = detalleDe(id);
        org.assertj.core.api.Assertions.assertThat(detalle.get("detalles")).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(detalle.get("detalles").get(0).get("articuloCodigo").asText())
                .isEqualTo("ART-003");
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isEqualTo(1);
    }

    // --- Edición: casos de borde y de rechazo ---

    protected long borradorDeDosRenglones() throws Exception {
        return postear(cuerpo(juan.getId(),
                renglonJson(precioMartillo.getId(), "1"), renglonJson(precioCable.getId(), "2")), null)
                .get("id").asLong();
    }

    protected org.springframework.test.web.servlet.ResultActions intentarPoner(long id, String solicitud)
            throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/facturas/" + id).contentType("application/json").content(solicitud));
    }

    @Test
    void reenviarLosMismosDatosDejaLosMismosImportes() throws Exception {
        long id = borradorDeDosRenglones();
        var antes = detalleDe(id);

        poner(id, cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"), renglonJson(precioCable.getId(), "2")),
                null);

        var despues = detalleDe(id);
        org.assertj.core.api.Assertions.assertThat(despues.get("importeTotal")).isEqualTo(antes.get("importeTotal"));
        org.assertj.core.api.Assertions.assertThat(despues.get("importeSaldo")).isEqualTo(antes.get("importeSaldo"));
        org.assertj.core.api.Assertions.assertThat(despues.get("detalles")).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isEqualTo(2);
    }

    @Test
    void editarUnaFacturaEmitidaEs409YNadaCambia() throws Exception {
        comoSistema();
        var emitida = guardarFactura(1L, java.time.LocalDate.of(2026, 11, 1), 3630.0,
                ar.edu.utn.frm.facturacion.model.EstadoFactura.EMITIDA, juan,
                renglon(precioMartillo, "Martillo", 1, 3000, 3000, 630, 3630));
        var antes = detalleDe(emitida.getId());

        intentarPoner(emitida.getId(), cuerpo(null, renglonJson(precioCable.getId(), "5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("EMITIDA"),
                        org.hamcrest.Matchers.containsString("BORRADOR"))));

        org.assertj.core.api.Assertions.assertThat(detalleDe(emitida.getId())).isEqualTo(antes);
    }

    @Test
    void editarUnaFacturaAnuladaEs409ConservaNumeroCaeYFechaDeAnulacion() throws Exception {
        comoSistema();
        var factura = armarFactura(7L, java.time.LocalDate.of(2026, 11, 1), 3630.0,
                ar.edu.utn.frm.facturacion.model.EstadoFactura.ANULADA, juan,
                renglon(precioMartillo, "Martillo", 1, 3000, 3000, 630, 3630));
        factura.setCae("70000000000007");
        factura.setCaeFechaVencimiento(java.time.LocalDate.of(2026, 11, 11));
        factura.setResultadoAfip("A");
        factura.setFechaAnulacion(java.time.LocalDate.of(2026, 11, 12));
        var anulada = facturaRepositorio.save(factura);
        var antes = detalleDe(anulada.getId());

        intentarPoner(anulada.getId(), cuerpo(juan.getId(), renglonJson(precioCable.getId(), "5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("ANULADA"),
                        org.hamcrest.Matchers.containsString("BORRADOR"))));

        var despues = detalleDe(anulada.getId());
        org.assertj.core.api.Assertions.assertThat(despues).isEqualTo(antes);
        org.assertj.core.api.Assertions.assertThat(despues.get("numero").asLong()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(despues.get("cae").asText()).isEqualTo("70000000000007");
        org.assertj.core.api.Assertions.assertThat(despues.get("fechaAnulacion").asText()).isEqualTo("2026-11-12");
        org.assertj.core.api.Assertions.assertThat(despues.get("detalles")).hasSize(1);
    }

    @Test
    void editarUnaFacturaInexistenteEs404() throws Exception {
        intentarPoner(999999L, cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));
    }

    @Test
    void descripcionDe256EnLaEdicionEs400SinCambiosYDe255Es200() throws Exception {
        long id = borradorDeDosRenglones();
        var antes = detalleDe(id);
        String larga = "{\"listaPrecioArticuloId\": " + precioMartillo.getId()
                + ", \"cantidad\": 1, \"descripcion\": \"" + "d".repeat(256) + "\"}";

        intentarPoner(id, cuerpo(juan.getId(), larga)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['detalles[0].descripcion']").exists());
        org.assertj.core.api.Assertions.assertThat(detalleDe(id)).isEqualTo(antes);

        String justa = "{\"listaPrecioArticuloId\": " + precioMartillo.getId()
                + ", \"cantidad\": 1, \"descripcion\": \"" + "d".repeat(255) + "\"}";
        poner(id, cuerpo(juan.getId(), justa), null);
        org.assertj.core.api.Assertions.assertThat(
                detalleDe(id).get("detalles").get(0).get("descripcion").asText()).hasSize(255);
    }

    @Test
    void monedaInexistenteEnLaEdicionEs404YConservaMonedaYRenglones() throws Exception {
        long id = borradorDeDosRenglones();
        var antes = detalleDe(id);
        String solicitud = cuerpo(juan.getId(), renglonJson(precioCable.getId(), "1"))
                .replace("\"tipoMonedaId\": " + pesos.getId(), "\"tipoMonedaId\": 999999");

        intentarPoner(id, solicitud).andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(detalleDe(id)).isEqualTo(antes);
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isEqualTo(2);
    }

    @Test
    void fallaEnElSegundoRenglonDeLaEdicionConservaLosDosRenglones() throws Exception {
        long id = borradorDeDosRenglones();
        var antes = detalleDe(id);

        intentarPoner(id, cuerpo(juan.getId(), renglonJson(precioCable.getId(), "1"), renglonJson(999999L, "1")))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(detalleDe(id)).isEqualTo(antes);
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isEqualTo(2);
    }

    @Test
    void importeNoRepresentableEnLaEdicionEs409YConservaLosImportes() throws Exception {
        long id = borradorDeDosRenglones();
        var antes = detalleDe(id);
        comoSistema();
        ListaPrecioArticulo caro = precioDeArticuloConAlicuota("ART-099", "Pieza extrema", 21, 1234567890123456.8);

        intentarPoner(id, cuerpo(juan.getId(), renglonJson(caro.getId(), "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Importe no representable"));

        org.assertj.core.api.Assertions.assertThat(detalleDe(id)).isEqualTo(antes);
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isEqualTo(2);
    }

    @Test
    void edicionSinCuerpoEs400() throws Exception {
        long id = borradorDeDosRenglones();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/facturas/" + id)
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo ilegible"));
    }

    // --- Auditoría del PUT (spec auditoria-entidades) ---

    @Test
    void siSoloCambianLosRenglonesLaCabeceraQuedaAuditadaConElUsuarioDeclarado() throws Exception {
        long id = borradorDeDosRenglones();

        poner(id, cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "7"), renglonJson(precioCable.getId(), "2")),
                "acortez");

        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(id)).isEqualTo("sistema");
        org.assertj.core.api.Assertions.assertThat(usuarioDeModificacion(id)).isEqualTo("acortez");
    }

    @Test
    void conElMismoContenidoLaCabeceraTambienQuedaAuditadaConElUsuarioDeclarado() throws Exception {
        long id = borradorDeDosRenglones();

        poner(id, cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"), renglonJson(precioCable.getId(), "2")),
                "mriquero");

        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(id)).isEqualTo("sistema");
        org.assertj.core.api.Assertions.assertThat(usuarioDeModificacion(id)).isEqualTo("mriquero");
    }

    // --- Eliminación de borradores: DELETE (spec api-borradores-factura) ---

    protected long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
    }

    @Test
    void borrarUnBorradorConClienteQuitaSoloLaFacturaYSusRenglones() throws Exception {
        long id = borradorDeDosRenglones();
        long clientes = contar("cliente");
        long contactos = contar("contacto");
        long domicilios = contar("domicilio");
        long puntos = contar("punto_venta");
        long monedas = contar("tipo_moneda");
        long condiciones = contar("condicion_iva");
        long listas = contar("lista_precio_articulo");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/facturas/" + id))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/facturas/" + id))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(contarFacturas()).isZero();
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isZero();
        org.assertj.core.api.Assertions.assertThat(contar("cliente")).isEqualTo(clientes).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(contar("contacto")).isEqualTo(contactos).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(contar("domicilio")).isEqualTo(domicilios).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(contar("punto_venta")).isEqualTo(puntos).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(contar("tipo_moneda")).isEqualTo(monedas).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(contar("condicion_iva")).isEqualTo(condiciones).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(contar("lista_precio_articulo")).isEqualTo(listas).isEqualTo(2);
    }

    @Test
    void borrarUnaFacturaAnuladaEs409YNadaCambia() throws Exception {
        comoSistema();
        var anulada = guardarFactura(1L, java.time.LocalDate.of(2026, 11, 1), 3630.0,
                ar.edu.utn.frm.facturacion.model.EstadoFactura.ANULADA, juan,
                renglon(precioMartillo, "Martillo", 1, 3000, 3000, 630, 3630));
        var antes = detalleDe(anulada.getId());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/facturas/" + anulada.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("ANULADA"),
                        org.hamcrest.Matchers.containsString("eliminar"))));

        org.assertj.core.api.Assertions.assertThat(detalleDe(anulada.getId())).isEqualTo(antes);
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isEqualTo(1);
    }

    @Test
    void borrarUnaFacturaEmitidaEs409YSigueConNumeroYCae() throws Exception {
        comoSistema();
        var factura = armarFactura(3L, java.time.LocalDate.of(2026, 11, 1), 3630.0,
                ar.edu.utn.frm.facturacion.model.EstadoFactura.EMITIDA, juan,
                renglon(precioMartillo, "Martillo", 1, 3000, 3000, 630, 3630));
        factura.setCae("70000000000003");
        var emitida = facturaRepositorio.save(factura);
        var antes = detalleDe(emitida.getId());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/facturas/" + emitida.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("EMITIDA"),
                        org.hamcrest.Matchers.containsString("eliminar"))));

        var despues = detalleDe(emitida.getId());
        org.assertj.core.api.Assertions.assertThat(despues).isEqualTo(antes);
        org.assertj.core.api.Assertions.assertThat(despues.get("numero").asLong()).isEqualTo(3L);
        org.assertj.core.api.Assertions.assertThat(despues.get("cae").asText()).isEqualTo("70000000000003");
        org.assertj.core.api.Assertions.assertThat(contarRenglones()).isEqualTo(1);
    }

    @Test
    void borrarUnaFacturaInexistenteEs404() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/facturas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));
    }

    // --- Cambio de estado: emisión y anulación (spec ciclo-de-vida-factura) ---

    protected com.fasterxml.jackson.databind.JsonNode cambiarEstado(long id, String estado, String xUsuario)
            throws Exception {
        String cuerpoEstado = "{\"estado\": \"" + estado + "\"}";
        var pedido = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/facturas/" + id + "/estado").contentType("application/json").content(cuerpoEstado);
        if (xUsuario != null) {
            pedido = pedido.header("X-Usuario", xUsuario);
        }
        String respuesta = mvc.perform(pedido).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return JSON.readTree(respuesta);
    }

    protected org.springframework.test.web.servlet.ResultActions pedirEstado(long id, String cuerpoJson)
            throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/facturas/" + id + "/estado").contentType("application/json").content(cuerpoJson));
    }

    protected long borradorConFecha(String fecha) throws Exception {
        String solicitud = cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"))
                .replace("{\"clienteId\"", "{\"fechaEmision\": \"" + fecha + "\", \"clienteId\"");
        return postear(solicitud, null).get("id").asLong();
    }

    @Test
    void emitirUnBorradorAsignaNumeroCaeVencimientoYResultado() throws Exception {
        long id = borradorConFecha("2026-11-01");

        var emitida = cambiarEstado(id, "EMITIDA", null);

        org.assertj.core.api.Assertions.assertThat(emitida.get("estado").asText()).isEqualTo("EMITIDA");
        org.assertj.core.api.Assertions.assertThat(emitida.get("numeroFactura").asLong()).isEqualTo(1L);
        var detalle = detalleDe(id);
        org.assertj.core.api.Assertions.assertThat(detalle.get("cae").asText()).isEqualTo("12345678901234");
        org.assertj.core.api.Assertions.assertThat(detalle.get("caeFechaVencimiento").asText()).isEqualTo("2026-11-27");
        org.assertj.core.api.Assertions.assertThat(detalle.get("resultadoAfip").asText()).isEqualTo("A");
        org.assertj.core.api.Assertions.assertThat(detalle.get("fechaEmision").asText()).isEqualTo("2026-11-01");
    }

    // --- Numeración, anulación y transiciones (spec ciclo-de-vida-factura) ---

    protected long borradorEnPuntoDeVenta(Long puntoVentaId) throws Exception {
        String solicitud = cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"))
                .replace("\"puntoVentaId\": " + casaCentral.getId(), "\"puntoVentaId\": " + puntoVentaId);
        return postear(solicitud, null).get("id").asLong();
    }

    protected ar.edu.utn.frm.facturacion.model.PuntoVenta crearPuntoDeVentaDos() {
        comoSistema();
        var punto = ar.edu.utn.frm.facturacion.model.DatosDePrueba.puntoVenta(2);
        punto.setDescripcion("Sucursal Norte");
        return puntoVentaRepositorio.save(punto);
    }

    /** Crea una factura y la deja en el estado pedido por los caminos de la API (BORRADOR, EMITIDA o ANULADA). */
    protected long facturaEn(String estado) throws Exception {
        long id = borradorDeDosRenglones();
        if (!"BORRADOR".equals(estado)) {
            cambiarEstado(id, "EMITIDA", null);
        }
        if ("ANULADA".equals(estado)) {
            cambiarEstado(id, "ANULADA", null);
        }
        return id;
    }

    @Test
    void cadaPuntoDeVentaNumeraPorSeparadoYLosBorradoresNoConsumenNumeros() throws Exception {
        var pv2 = crearPuntoDeVentaDos();
        long a = borradorEnPuntoDeVenta(casaCentral.getId());
        long b = borradorEnPuntoDeVenta(casaCentral.getId());
        long c = borradorEnPuntoDeVenta(pv2.getId());
        borradorEnPuntoDeVenta(casaCentral.getId()); // un borrador sin emitir no consume número

        org.assertj.core.api.Assertions.assertThat(cambiarEstado(a, "EMITIDA", null).get("numeroFactura").asLong())
                .isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(cambiarEstado(b, "EMITIDA", null).get("numeroFactura").asLong())
                .isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(cambiarEstado(c, "EMITIDA", null).get("numeroFactura").asLong())
                .isEqualTo(1L);
    }

    @Test
    void lasAnuladasConservanSuNumeroYElSiguienteEsElTres() throws Exception {
        long uno = borradorDeDosRenglones();
        long dos = borradorDeDosRenglones();
        cambiarEstado(uno, "EMITIDA", null);
        cambiarEstado(dos, "EMITIDA", null);
        cambiarEstado(dos, "ANULADA", null);
        long nuevo = borradorDeDosRenglones();

        var emitida = cambiarEstado(nuevo, "EMITIDA", null);

        org.assertj.core.api.Assertions.assertThat(emitida.get("numeroFactura").asLong()).isEqualTo(3L);
    }

    @Test
    void emitirUnBorradorSinRenglonesEs409YSigueSinNumeroNiCae() throws Exception {
        comoSistema();
        var vacia = guardarFactura(null, java.time.LocalDate.of(2026, 11, 1), 0.0,
                ar.edu.utn.frm.facturacion.model.EstadoFactura.BORRADOR, juan);

        pedirEstado(vacia.getId(), "{\"estado\": \"EMITIDA\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("renglones")));

        var detalle = detalleDe(vacia.getId());
        org.assertj.core.api.Assertions.assertThat(detalle.get("estado").asText()).isEqualTo("BORRADOR");
        org.assertj.core.api.Assertions.assertThat(detalle.get("numero").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(detalle.get("cae").isNull()).isTrue();
    }

    @Test
    void anularUnaEmitidaFijaLaFechaYConservaNumeroCaeYRenglones() throws Exception {
        long id = facturaEn("EMITIDA");
        var antes = detalleDe(id);

        var anulada = cambiarEstado(id, "ANULADA", null);

        org.assertj.core.api.Assertions.assertThat(anulada.get("estado").asText()).isEqualTo("ANULADA");
        org.assertj.core.api.Assertions.assertThat(anulada.get("numeroFactura").asLong()).isEqualTo(1L);
        var despues = detalleDe(id);
        org.assertj.core.api.Assertions.assertThat(despues.get("fechaAnulacion").asText()).isEqualTo("2026-11-17");
        org.assertj.core.api.Assertions.assertThat(despues.get("cae")).isEqualTo(antes.get("cae"));
        org.assertj.core.api.Assertions.assertThat(despues.get("numero")).isEqualTo(antes.get("numero"));
        org.assertj.core.api.Assertions.assertThat(despues.get("detalles")).isEqualTo(antes.get("detalles"));
        org.assertj.core.api.Assertions.assertThat(despues.get("importeTotal")).isEqualTo(antes.get("importeTotal"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/facturas").param("estado", "ANULADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} -> {1} prohibida")
    @org.junit.jupiter.params.provider.CsvSource({
        "BORRADOR, ANULADA", "BORRADOR, BORRADOR", "EMITIDA, EMITIDA", "EMITIDA, BORRADOR",
        "ANULADA, ANULADA", "ANULADA, EMITIDA", "ANULADA, BORRADOR"})
    void lasCombinacionesProhibidasSon409ConAmbosEstadosYNadaCambia(String actual, String pedido) throws Exception {
        long id = facturaEn(actual);
        var antes = detalleDe(id);

        pedirEstado(id, "{\"estado\": \"" + pedido + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(actual), org.hamcrest.Matchers.containsString(pedido))));

        org.assertj.core.api.Assertions.assertThat(detalleDe(id)).isEqualTo(antes);
    }

    @Test
    void cambiarElEstadoDeUnaFacturaInexistenteEs404() throws Exception {
        pedirEstado(999999L, "{\"estado\": \"EMITIDA\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));
    }

    @Test
    void unEstadoDesconocidoEs400ConLosValoresAdmitidos() throws Exception {
        long id = facturaEn("BORRADOR");
        var antes = detalleDe(id);

        pedirEstado(id, "{\"estado\": \"PAGADA\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("PAGADA"),
                        org.hamcrest.Matchers.containsString("BORRADOR, EMITIDA, ANULADA"))));

        org.assertj.core.api.Assertions.assertThat(detalleDe(id)).isEqualTo(antes);
    }

    @Test
    void elEstadoSeNormalizaSinEspaciosNiMayusculas() throws Exception {
        long id = facturaEn("BORRADOR");

        var emitida = cambiarEstado(id, " emitida ", null);

        org.assertj.core.api.Assertions.assertThat(emitida.get("estado").asText()).isEqualTo("EMITIDA");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "cuerpo {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"estado\": \"\"}", "{\"estado\": \"  \"}"})
    void estadoEnBlancoEs400(String cuerpoJson) throws Exception {
        long id = facturaEn("BORRADOR");

        pedirEstado(id, cuerpoJson).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado").exists());
    }

    @Test
    void unaObservacionNoVaciaReemplazaLaDeLaFacturaYUnaEnBlancoLaDeja() throws Exception {
        String solicitud = cuerpo(juan.getId(), renglonJson(precioMartillo.getId(), "1"))
                .replace("{\"clienteId\"", "{\"observaciones\": \"Entrega parcial\", \"clienteId\"");
        long id = postear(solicitud, null).get("id").asLong();

        pedirEstado(id, "{\"estado\": \"EMITIDA\", \"observaciones\": \"   \"}").andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(detalleDe(id).get("observaciones").asText())
                .isEqualTo("Entrega parcial");

        pedirEstado(id, "{\"estado\": \"ANULADA\", \"observaciones\": \"Error en el cliente\"}")
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(detalleDe(id).get("observaciones").asText())
                .isEqualTo("Error en el cliente");
    }

    @Test
    void despuesDeEmitirNiElPutNiElDeleteSonPosibles() throws Exception {
        long id = facturaEn("EMITIDA");
        var emitida = detalleDe(id);

        intentarPoner(id, cuerpo(juan.getId(), renglonJson(precioCable.getId(), "9"))).andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/facturas/" + id))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(detalleDe(id)).isEqualTo(emitida);
    }

    // --- Auditoría de estados (spec auditoria-entidades, auditoria-por-request) ---

    protected String usuarioDeBaja(long facturaId) {
        var filas = jdbc.queryForList("select u.usuario from factura_venta f"
                + " join usuario u on u.id = f.usuario_baja_id where f.id = ?", String.class, facturaId);
        return filas.isEmpty() ? null : filas.get(0);
    }

    protected boolean tieneFechaDeBaja(long facturaId) {
        return jdbc.queryForObject("select count(*) from factura_venta where id = ? and fecha_baja is not null",
                Long.class, facturaId) == 1;
    }

    @Test
    void anularConUsuarioDeclaradoRegistraLaBajaAEseUsuarioYLaFilaSigueExistiendo() throws Exception {
        long id = facturaEn("EMITIDA");

        cambiarEstado(id, "ANULADA", "acortez");

        org.assertj.core.api.Assertions.assertThat(usuarioDeBaja(id)).isEqualTo("acortez");
        org.assertj.core.api.Assertions.assertThat(tieneFechaDeBaja(id)).isTrue();
        org.assertj.core.api.Assertions.assertThat(usuarioDeModificacion(id)).isEqualTo("acortez");
        org.assertj.core.api.Assertions.assertThat(contarFacturas()).isEqualTo(1);
    }

    @Test
    void unaFacturaEmitidaNoTieneBaja() throws Exception {
        long id = facturaEn("EMITIDA");

        org.assertj.core.api.Assertions.assertThat(usuarioDeBaja(id)).isNull();
        org.assertj.core.api.Assertions.assertThat(tieneFechaDeBaja(id)).isFalse();
    }

    @Test
    void cualquierUsuarioDeclaradoPuedeEmitirYAnularSinCredenciales() throws Exception {
        long id = borradorDeDosRenglones();

        cambiarEstado(id, "EMITIDA", "mriquero");
        cambiarEstado(id, "ANULADA", "mriquero");

        org.assertj.core.api.Assertions.assertThat(usuarioDeBaja(id)).isEqualTo("mriquero");
        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(id)).isEqualTo("sistema");
    }

    @Test
    void emitirConOtroUsuarioActualizaElUsuarioDeModificacionYConservaElDeCarga() throws Exception {
        long id = borradorDeDosRenglones();

        cambiarEstado(id, "EMITIDA", "acortez");

        org.assertj.core.api.Assertions.assertThat(usuarioDeCarga(id)).isEqualTo("sistema");
        org.assertj.core.api.Assertions.assertThat(usuarioDeModificacion(id)).isEqualTo("acortez");
    }

    // --- Rollback de la emisión (spec ciclo-de-vida-factura, D6: el CAE se genera después de numerar) ---

    @Test
    void siFallaElGeneradorDeCaeLaEmisionSeRevierteYElNumeroSeDevuelve() throws Exception {
        long id = borradorDeDosRenglones();
        generadorCae.fallar = true;

        pedirEstado(id, "{\"estado\": \"EMITIDA\"}").andExpect(status().isInternalServerError());

        var detalle = detalleDe(id);
        org.assertj.core.api.Assertions.assertThat(detalle.get("estado").asText()).isEqualTo("BORRADOR");
        org.assertj.core.api.Assertions.assertThat(detalle.get("numero").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(detalle.get("cae").isNull()).isTrue();

        generadorCae.fallar = false;
        var emitida = cambiarEstado(id, "EMITIDA", null);
        org.assertj.core.api.Assertions.assertThat(emitida.get("numeroFactura").asLong()).isEqualTo(1L);
    }
}
