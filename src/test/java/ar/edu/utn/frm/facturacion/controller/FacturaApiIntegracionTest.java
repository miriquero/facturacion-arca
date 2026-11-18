package ar.edu.utn.frm.facturacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Contrato HTTP de punta a punta (H2, sin transacción de prueba, con {@code open-in-view: false}). */
class FacturaApiIntegracionTest extends IntegracionApiBase {

    private static final LocalDate DEL_01 = LocalDate.of(2026, 11, 1);
    private static final LocalDate DEL_05 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEL_10 = LocalDate.of(2026, 11, 10);

    private FacturaVenta[] tresFechas() {
        return new FacturaVenta[] {
            guardarFactura(1L, DEL_01, 10.00, EstadoFactura.EMITIDA, juan),
            guardarFactura(2L, DEL_05, 10.00, EstadoFactura.EMITIDA, juan),
            guardarFactura(3L, DEL_10, 10.00, EstadoFactura.EMITIDA, juan)};
    }

    /** Parámetros decodificados (un {@code %20} de la URL llega al controlador como un espacio). */
    private ResultActions listar(String... parametros) throws Exception {
        MockHttpServletRequestBuilder peticion = get("/api/facturas");
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(peticion);
    }

    @Test
    void sinFiltrosDevuelveLasTresEnOrdenDescendente() throws Exception {
        FacturaVenta[] f = tresFechas();

        listar().andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(f[2].getId()))
                .andExpect(jsonPath("$.items[0].fechaEmision").value("2026-11-10"))
                .andExpect(jsonPath("$.items[1].fechaEmision").value("2026-11-05"))
                .andExpect(jsonPath("$.items[2].fechaEmision").value("2026-11-01"))
                .andExpect(jsonPath("$.items[0].clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$.items[0].condicionIva").value("Responsable Inscripto"))
                .andExpect(jsonPath("$.items[0].puntoVentaDescripcion").value("Casa Central"))
                .andExpect(jsonPath("$.items[0].codigoMoneda").value("PES"));
    }

    @Test
    void rangoConLimitesExactos() throws Exception {
        tresFechas();

        listar("fechaDesde", "2026-11-05", "fechaHasta", "2026-11-10").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].fechaEmision").value("2026-11-10"))
                .andExpect(jsonPath("$.items[1].fechaEmision").value("2026-11-05"));
    }

    @Test
    void mismoDiaComoDesdeYHasta() throws Exception {
        tresFechas();

        listar("fechaDesde", "2026-11-05", "fechaHasta", "2026-11-05").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].fechaEmision").value("2026-11-05"));
    }

    @Test
    void soloFechaDesdeIncluyeDesdeEseDiaEnAdelante() throws Exception {
        tresFechas();

        listar("fechaDesde", "2026-11-05")
                .andExpect(jsonPath("$.items[*].fechaEmision").value(contains("2026-11-10", "2026-11-05")));
    }

    private void tresEstados() {
        guardarFactura(null, DEL_01, 10.00, EstadoFactura.BORRADOR, juan);
        guardarFactura(1L, DEL_05, 20.00, EstadoFactura.EMITIDA, juan);
        guardarFactura(2L, DEL_10, 30.00, EstadoFactura.ANULADA, juan);
    }

    @Test
    void estadoConEspaciosYMinusculasSeNormaliza() throws Exception {
        tresEstados();

        listar("estado", " emitida ").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].estado").value("EMITIDA"));
    }

    @Test
    void estadoVacioEquivaleASinFiltro() throws Exception {
        tresEstados();

        listar("estado", "  ").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3));
        listar("estado", "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void estadoInexistenteEs400ConValoresAdmitidos() throws Exception {
        listar("estado", "PAGADA").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(containsString("PAGADA")))
                .andExpect(jsonPath("$.detail").value(containsString("BORRADOR")))
                .andExpect(jsonPath("$.detail").value(containsString("EMITIDA")))
                .andExpect(jsonPath("$.detail").value(containsString("ANULADA")));
    }

    @Test
    void rangoInvertidoEs400() throws Exception {
        listar("fechaDesde", "2026-11-10", "fechaHasta", "2026-11-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("fechaDesde")))
                .andExpect(jsonPath("$.detail").value(containsString("fechaHasta")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e400"})
    void montoMinimoNoFinitoEs400(String valor) throws Exception {
        listar("montoMinimo", valor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("montoMinimo")));
    }

    @Test
    void minimoNegativoDevuelveTodas() throws Exception {
        tresFechas();

        listar("montoMinimo", "-50").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void totalIgualAlMinimoSeIncluye() throws Exception {
        guardarFactura(1L, DEL_01, 100.10, EstadoFactura.EMITIDA, juan);
        guardarFactura(2L, DEL_05, 100.00, EstadoFactura.EMITIDA, juan);
        guardarFactura(3L, DEL_10, 250.00, EstadoFactura.EMITIDA, juan);

        listar("montoMinimo", "100.10").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].importeTotal").value(contains(250.00, 100.10)));
    }

    @Test
    void filtrosCombinados() throws Exception {
        FacturaVenta buscada = guardarFactura(1L, DEL_05, 500.00, EstadoFactura.EMITIDA, juan);
        guardarFactura(2L, DEL_05, 50.00, EstadoFactura.EMITIDA, juan);
        guardarFactura(null, DEL_05, 500.00, EstadoFactura.BORRADOR, juan);
        guardarFactura(3L, LocalDate.of(2026, 10, 1), 500.00, EstadoFactura.EMITIDA, juan);

        listar("estado", "EMITIDA", "fechaDesde", "2026-11-01", "fechaHasta", "2026-11-30", "montoMinimo", "100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(buscada.getId()))
                .andExpect(jsonPath("$.items[0].importeTotal").value(500.00));
    }

    @Test
    void filtrosSinCoincidenciasDevuelven200ConArregloVacio() throws Exception {
        tresFechas();

        listar("montoMinimo", "999999999").andExpect(status().isOk()).andExpect(content().json("{\"items\":[],\"pagina\":0,\"tamanio\":10,\"totalElementos\":0,\"totalPaginas\":0}", true));
    }

    @Test
    void baseSinFacturasDevuelve200ConArregloVacio() throws Exception {
        listar().andExpect(status().isOk()).andExpect(content().json("{\"items\":[],\"pagina\":0,\"tamanio\":10,\"totalElementos\":0,\"totalPaginas\":0}", true));
    }

    @Test
    void clienteOpcionalApareceComoConsumidorFinal() throws Exception {
        guardarFactura(1L, DEL_05, 10.00, EstadoFactura.EMITIDA, null);

        listar().andExpect(jsonPath("$.items[0].clienteDenominacion").value("Consumidor Final"));
    }

    @Test
    void borradorSinNumeroTieneNumeroNulo() throws Exception {
        guardarFactura(null, DEL_05, 10.00, EstadoFactura.BORRADOR, juan);

        listar().andExpect(jsonPath("$.items[0].numeroFactura").isEmpty())
                .andExpect(jsonPath("$.items[0].estado").value("BORRADOR"));
    }

    // --- Detalle ---

    @Test
    void detalleDeFacturaEmitidaConCliente() throws Exception {
        FacturaVenta factura = armarFactura(1L, DEL_05, 7260.00, EstadoFactura.EMITIDA, juan);
        factura.setCae("70000000000001");
        factura.setCaeFechaVencimiento(LocalDate.of(2026, 11, 15));
        factura.setResultadoAfip("A");
        factura.setImporteCobrado(0.0);
        factura.setImporteSaldo(7260.00);
        factura.setObservaciones("Entrega en mostrador");
        FacturaVenta guardada = facturaRepositorio.save(factura);

        mvc.perform(get("/api/facturas/{id}", guardada.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(guardada.getId()))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.fechaEmision").value("2026-11-05"))
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.clienteId").value(juan.getId()))
                .andExpect(jsonPath("$.clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$.clienteCuitCuil").value("20-12345678-9"))
                .andExpect(jsonPath("$.condicionIvaId").value(responsableInscripto.getId()))
                .andExpect(jsonPath("$.condicionIva").value("Responsable Inscripto"))
                .andExpect(jsonPath("$.puntoVentaId").value(casaCentral.getId()))
                .andExpect(jsonPath("$.puntoVentaNumero").value(1))
                .andExpect(jsonPath("$.puntoVentaDescripcion").value("Casa Central"))
                .andExpect(jsonPath("$.tipoMonedaId").value(pesos.getId()))
                .andExpect(jsonPath("$.codigoMoneda").value("PES"))
                .andExpect(jsonPath("$.importeTotal").value(7260.00))
                .andExpect(jsonPath("$.importeCobrado").value(0.0))
                .andExpect(jsonPath("$.importeSaldo").value(7260.00))
                .andExpect(jsonPath("$.cae").value("70000000000001"))
                .andExpect(jsonPath("$.caeFechaVencimiento").value("2026-11-15"))
                .andExpect(jsonPath("$.resultadoAfip").value("A"))
                .andExpect(jsonPath("$.fechaAnulacion").isEmpty())
                .andExpect(jsonPath("$.observaciones").value("Entrega en mostrador"));
    }

    @Test
    void detalleDeBorradorNoTieneNumeroNiCaeNiAnulacion() throws Exception {
        FacturaVenta borrador = guardarFactura(null, DEL_05, 100.00, EstadoFactura.BORRADOR, juan);

        mvc.perform(get("/api/facturas/{id}", borrador.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.numero").isEmpty())
                .andExpect(jsonPath("$.cae").isEmpty())
                .andExpect(jsonPath("$.caeFechaVencimiento").isEmpty())
                .andExpect(jsonPath("$.fechaAnulacion").isEmpty());
    }

    @Test
    void detalleDeFacturaSinClienteEsConsumidorFinal() throws Exception {
        FacturaVenta sinCliente = guardarFactura(1L, DEL_05, 100.00, EstadoFactura.EMITIDA, null);

        mvc.perform(get("/api/facturas/{id}", sinCliente.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").isEmpty())
                .andExpect(jsonPath("$.clienteDenominacion").value("Consumidor Final"))
                .andExpect(jsonPath("$.clienteCuitCuil").isEmpty());
    }

    @Test
    void detalleConDosRenglonesEnOrdenDeId() throws Exception {
        var general = guardarLista("GENERAL", "Lista general");
        var precioMartillo = guardarPrecio(general, guardarArticulo("ART-001", "Martillo"), 3000.00);
        var precioCable = guardarPrecio(general, guardarArticulo("ART-003", "Cable HDMI"), 8000.00);
        FacturaVenta factura = guardarFactura(1L, DEL_05, 19630.00, EstadoFactura.EMITIDA, juan,
                renglon(precioMartillo, "Martillo", 1, 3000.00, 3000.00, 630.00, 3630.00),
                renglon(precioCable, "Cable HDMI", 2, 8000.00, 16000.00, 0.0, 16000.00));

        mvc.perform(get("/api/facturas/{id}", factura.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles.length()").value(2))
                .andExpect(jsonPath("$.detalles[0].listaPrecioArticuloId").value(precioMartillo.getId()))
                .andExpect(jsonPath("$.detalles[0].articuloCodigo").value("ART-001"))
                .andExpect(jsonPath("$.detalles[0].descripcion").value("Martillo"))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(1.0))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(3000.00))
                .andExpect(jsonPath("$.detalles[0].porcentajeBonificacion").value(0.0))
                .andExpect(jsonPath("$.detalles[0].importeNeto").value(3000.00))
                .andExpect(jsonPath("$.detalles[0].importeIva").value(630.00))
                .andExpect(jsonPath("$.detalles[0].importeSubtotal").value(3630.00))
                .andExpect(jsonPath("$.detalles[1].listaPrecioArticuloId").value(precioCable.getId()))
                .andExpect(jsonPath("$.detalles[1].articuloCodigo").value("ART-003"))
                .andExpect(jsonPath("$.detalles[1].cantidad").value(2.0))
                .andExpect(jsonPath("$.detalles[1].importeSubtotal").value(16000.00));
    }

    @Test
    void cabeceraSinRenglonesDevuelveArregloVacio() throws Exception {
        FacturaVenta factura = guardarFactura(1L, DEL_05, 100.00, EstadoFactura.EMITIDA, juan);

        mvc.perform(get("/api/facturas/{id}", factura.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles").isArray())
                .andExpect(jsonPath("$.detalles.length()").value(0));
    }

    @Test
    void idInexistenteEs404ConElIdEnElDetalle() throws Exception {
        mvc.perform(get("/api/facturas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("999999")));
    }

    // --- Reporte ---

    private static final String ENCABEZADO_TXT = "Número\tFecha\tCliente\tCondición IVA\tPunto de venta\tÍtems\tTotal\tEstado\tMoneda";
    private static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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

    @Test
    void reportePdfEmpiezaConMagicNumberYSeDescargaComoFacturasPdf() throws Exception {
        guardarFactura(1L, DEL_05, 10.00, EstadoFactura.EMITIDA, juan);

        MockHttpServletResponse r = reporte("formato", "pdf");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentType()).isEqualTo("application/pdf");
        assertThat(r.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"facturas.pdf\"");
        assertThat(new String(r.getContentAsByteArray(), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void reporteExcelSeAbreComoLibroConLaHojaFacturas() throws Exception {
        guardarFactura(1L, DEL_05, 10.00, EstadoFactura.EMITIDA, juan);

        MockHttpServletResponse r = reporte("formato", "xlsx");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentType()).isEqualTo(TIPO_XLSX);
        assertThat(r.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"facturas.xlsx\"");
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(r.getContentAsByteArray()))) {
            assertThat(libro.getSheet("Facturas")).isNotNull();
            assertThat(libro.getSheet("Facturas").getRow(1).getCell(2).getStringCellValue()).isEqualTo("Juan Servicios");
        }
    }

    @Test
    void reporteTxtTieneLosNueveEncabezadosSeparadosPorTabulacion() throws Exception {
        guardarFactura(1L, DEL_05, 10.00, EstadoFactura.EMITIDA, juan);

        MockHttpServletResponse r = reporte("formato", "txt");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(r.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"facturas.txt\"");
        assertThat(texto(r).split("\n")[0]).isEqualTo(ENCABEZADO_TXT);
        assertThat(texto(r).split("\n")).hasSize(2);
    }

    @Test
    void sinFormatoElReporteEsUnPdf() throws Exception {
        MockHttpServletResponse r = reporte();

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentType()).isEqualTo("application/pdf");
        assertThat(r.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"facturas.pdf\"");
    }

    @Test
    void formatoVacioTambienEsPdf() throws Exception {
        assertThat(reporte("formato", "").getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void aliasExcelYTsvDanLosMismosTiposQueXlsxYTxt() throws Exception {
        assertThat(reporte("formato", "excel").getContentType()).isEqualTo(TIPO_XLSX);
        assertThat(reporte("formato", "tsv").getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(reporte("formato", "tsv").getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"facturas.txt\"");
    }

    @Test
    void formatoEnMayusculasYConEspaciosEsExcel() throws Exception {
        assertThat(reporte("formato", " XLSX ").getContentType()).isEqualTo(TIPO_XLSX);
    }

    @Test
    void formatoDocEs400SinDescarga() throws Exception {
        MockHttpServletResponse r = reporte("formato", "doc");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(r.getHeader("Content-Disposition")).isNull();
        assertThat(r.getContentType()).startsWith("application/problem+json");
        assertThat(texto(r)).contains("doc").contains("pdf").contains("xlsx").contains("txt");
    }

    @Test
    void filtroInvalidoEnElReporteEs400IgualQueEnElListado() throws Exception {
        MockHttpServletResponse r = reporte("formato", "pdf", "estado", "PAGADA");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(r.getHeader("Content-Disposition")).isNull();
        assertThat(texto(r)).contains("PAGADA").contains("BORRADOR");
    }

    @Test
    void reporteFiltradoSinCoincidenciasTieneSoloElEncabezado() throws Exception {
        guardarFactura(1L, DEL_05, 10.00, EstadoFactura.EMITIDA, juan);

        MockHttpServletResponse r = reporte("formato", "txt", "montoMinimo", "999999999");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(texto(r)).isEqualTo(ENCABEZADO_TXT + "\n");
    }

    @Test
    void elArchivoFiltradoCorrespondeFilaPorFilaAlListado() throws Exception {
        guardarFactura(null, DEL_01, 10.00, EstadoFactura.BORRADOR, juan);
        guardarFactura(1L, DEL_05, 1210.50, EstadoFactura.EMITIDA, juan);
        guardarFactura(2L, DEL_10, 30.00, EstadoFactura.ANULADA, juan);
        guardarFactura(3L, DEL_10, 250.00, EstadoFactura.EMITIDA, null);

        listar("estado", "EMITIDA").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].numeroFactura").value(3))
                .andExpect(jsonPath("$.items[0].fechaEmision").value("2026-11-10"))
                .andExpect(jsonPath("$.items[0].clienteDenominacion").value("Consumidor Final"))
                .andExpect(jsonPath("$.items[0].importeTotal").value(250.00))
                .andExpect(jsonPath("$.items[1].numeroFactura").value(1))
                .andExpect(jsonPath("$.items[1].fechaEmision").value("2026-11-05"))
                .andExpect(jsonPath("$.items[1].clienteDenominacion").value("Juan Servicios"))
                .andExpect(jsonPath("$.items[1].importeTotal").value(1210.50));

        String[] lineas = texto(reporte("formato", "txt", "estado", "EMITIDA")).split("\n");
        assertThat(lineas).hasSize(3);
        assertThat(lineas[1].split("\t")).startsWith("3", "2026-11-10", "Consumidor Final").contains("250.00");
        assertThat(lineas[2].split("\t")).startsWith("1", "2026-11-05", "Juan Servicios").contains("1210.50");
        assertThat(String.join("\n", lineas)).doesNotContain("BORRADOR").doesNotContain("ANULADA");
    }

    @Test
    void clienteConCaracterDeUsoPrivadoImposibilitaElPdfPeroNoLasAlternativas() throws Exception {
        var raro = clienteRepositorio.save(DatosDePrueba.cliente("Cliente  Raro", "20-99999999-9",
                DatosDePrueba.contacto("raro@ejemplo.com"), DatosDePrueba.domicilio("Calle", "1")));
        guardarFactura(1L, DEL_05, 10.00, EstadoFactura.EMITIDA, raro);

        MockHttpServletResponse pdf = reporte("formato", "pdf");
        assertThat(pdf.getStatus()).isEqualTo(400);
        assertThat(pdf.getHeader("Content-Disposition")).isNull();
        assertThat(texto(pdf)).contains("U+E000");

        MockHttpServletResponse txt = reporte("formato", "txt");
        assertThat(txt.getStatus()).isEqualTo(200);
        assertThat(texto(txt)).contains("");
        MockHttpServletResponse xlsx = reporte("formato", "xlsx");
        assertThat(xlsx.getStatus()).isEqualTo(200);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getContentAsByteArray()))) {
            assertThat(libro.getSheet("Facturas").getRow(1).getCell(2).getStringCellValue()).contains("");
        }
    }

    // --- Errores transversales ---

    @Test
    void listadoVacioEs200ConSobreVacioYDetalleInexistenteEs404() throws Exception {
        mvc.perform(get("/api/facturas")).andExpect(status().isOk()).andExpect(content().json("{\"items\":[],\"pagina\":0,\"tamanio\":10,\"totalElementos\":0,\"totalPaginas\":0}", true));
        mvc.perform(get("/api/facturas/1")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/facturas?estado=PAGADA, 400",
            "GET, /api/facturas?fechaHasta=ayer, 400",
            "GET, /api/facturas?montoMinimo=NaN, 400",
            "GET, /api/facturas/999999, 404",
            "GET, /api/facturas/abc, 400",
            "GET, /api/facturas/reporte?formato=doc, 400",
            "GET, /api/facturas/reporte?estado=PAGADA, 400",
            "PUT, /api/facturas, 405",
            "POST, /api/catalogos/clientes, 405"})
    void todoErrorTieneProblemJsonConLosCuatroCampos(String metodo, String ruta, int estado) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.valueOf(metodo), ruta))
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(estado))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")));
    }
}
