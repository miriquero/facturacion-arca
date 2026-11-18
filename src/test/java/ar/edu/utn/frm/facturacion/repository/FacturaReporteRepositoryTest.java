package ar.edu.utn.frm.facturacion.repository;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.condicionIva;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.contacto;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.detalle;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.domicilio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.puntoVenta;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.tipoMoneda;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.CondicionIva;
import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaBase;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FacturaReporteRepositoryTest extends FacturaBase {

    @Autowired
    private FacturaVentaRepository repositorio;

    private PuntoVenta casaCentral;
    private CondicionIva responsableInscripto;
    private TipoMoneda dolares;
    private Cliente ferreteria;

    @BeforeEach
    void prepararDatosDelReporte() {
        casaCentral = em.persist(puntoVenta(10));
        casaCentral.setDescripcion("Casa Central");
        responsableInscripto = em.persist(condicionIva(1, "Responsable Inscripto"));
        dolares = em.persist(tipoMoneda("DOL", "Dólar estadounidense", "US$"));
        ferreteria = em.persist(DatosDePrueba.cliente("Ferretería Ñandú", "30-70000000-1",
                contacto("ferreteria@ejemplo.com"), domicilio("Belgrano", "200")));
        em.flush();
        em.clear();
    }

    /** Guarda una factura con {@code renglones} renglones y deja el contexto vacío. */
    private FacturaVenta guardar(Long numero, LocalDate fecha, double total, EstadoFactura estado, Cliente cliente,
            TipoMoneda moneda, int renglones) {
        return guardar(casaCentral, numero, fecha, total, estado, cliente, moneda, renglones, null);
    }

    /** Variante con punto de venta y CAE explícitos (filtros de la etapa 6). */
    private FacturaVenta guardar(PuntoVenta punto, Long numero, LocalDate fecha, double total, EstadoFactura estado,
            Cliente cliente, TipoMoneda moneda, int renglones, String cae) {
        FacturaVenta factura = DatosDePrueba.facturaBorrador(punto, responsableInscripto, moneda, cliente);
        factura.setCae(cae);
        factura.setNumero(numero);
        factura.setFechaEmision(fecha);
        factura.setImporteTotal(total);
        factura.setEstado(estado);
        for (int i = 0; i < renglones; i++) {
            factura.agregarDetalle(detalle(precioMartillo, 1, 3000));
        }
        em.persist(factura);
        em.flush();
        em.clear();
        return factura;
    }

    @Test
    void facturaEmitidaCompletaTraeLosDiezDatos() {
        FacturaVenta guardada = guardar(15L, LocalDate.of(2026, 11, 10), 1210.50, EstadoFactura.EMITIDA,
                ferreteria, pesos, 2);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte();

        assertThat(reporte).hasSize(1);
        FacturaReporteDTO fila = reporte.get(0);
        assertThat(fila.getId()).isEqualTo(guardada.getId());
        assertThat(fila.getNumeroFactura()).isEqualTo(15L);
        assertThat(fila.getFechaEmision()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(fila.getClienteDenominacion()).isEqualTo("Ferretería Ñandú");
        assertThat(fila.getCondicionIva()).isEqualTo("Responsable Inscripto");
        assertThat(fila.getPuntoVentaDescripcion()).isEqualTo("Casa Central");
        assertThat(fila.getImporteTotal()).isEqualTo(1210.50);
        assertThat(fila.getCantidadItems()).isEqualTo(2L);
        assertThat(fila.getEstado()).isEqualTo("EMITIDA");
        assertThat(fila.getCodigoMoneda()).isEqualTo("PES");
    }

    @Test
    void cabeceraSinRenglonesApareceConCantidadCero() {
        guardar(1L, LocalDate.of(2026, 11, 10), 500.00, EstadoFactura.EMITIDA, ferreteria, pesos, 0);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte();

        assertThat(reporte).hasSize(1);
        assertThat(reporte.get(0).getCantidadItems()).isZero();
    }

    @Test
    void facturaConTresRenglonesApareceUnaSolaVezConCantidadTres() {
        guardar(1L, LocalDate.of(2026, 11, 10), 9000.00, EstadoFactura.EMITIDA, ferreteria, pesos, 3);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte();

        assertThat(reporte).hasSize(1);
        assertThat(reporte.get(0).getCantidadItems()).isEqualTo(3L);
    }

    @Test
    void facturaSinClienteApareceComoConsumidorFinal() {
        guardar(1L, LocalDate.of(2026, 11, 10), 100.00, EstadoFactura.EMITIDA, null, pesos, 1);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte();

        assertThat(reporte).hasSize(1);
        assertThat(reporte.get(0).getClienteDenominacion()).isEqualTo("Consumidor Final");
    }

    @Test
    void facturaConClienteMuestraSuDenominacion() {
        guardar(1L, LocalDate.of(2026, 11, 10), 100.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);

        assertThat(repositorio.buscarReporte()).extracting(FacturaReporteDTO::getClienteDenominacion)
                .containsExactly("Ferretería Ñandú");
    }

    @Test
    void borradorSinNumeroTieneNumeroNuloYElRestoCompleto() {
        guardar(null, LocalDate.of(2026, 11, 10), 750.00, EstadoFactura.BORRADOR, ferreteria, pesos, 1);

        FacturaReporteDTO fila = repositorio.buscarReporte().get(0);

        assertThat(fila.getNumeroFactura()).isNull();
        assertThat(fila.getEstado()).isEqualTo("BORRADOR");
        assertThat(fila.getClienteDenominacion()).isEqualTo("Ferretería Ñandú");
        assertThat(fila.getPuntoVentaDescripcion()).isEqualTo("Casa Central");
        assertThat(fila.getImporteTotal()).isEqualTo(750.00);
    }

    @Test
    void cadaEstadoSeExponeComoTexto() {
        guardar(null, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.BORRADOR, ferreteria, pesos, 1);
        guardar(1L, LocalDate.of(2026, 11, 2), 20.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        guardar(2L, LocalDate.of(2026, 11, 3), 30.00, EstadoFactura.ANULADA, ferreteria, pesos, 1);

        assertThat(repositorio.buscarReporte()).extracting(FacturaReporteDTO::getEstado)
                .containsExactly("ANULADA", "EMITIDA", "BORRADOR");
    }

    @Test
    void cadaFilaConservaSuMonedaYSuImporteSinConvertir() {
        guardar(1L, LocalDate.of(2026, 11, 2), 100.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        guardar(2L, LocalDate.of(2026, 11, 1), 100.00, EstadoFactura.EMITIDA, ferreteria, dolares, 1);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte();

        assertThat(reporte).extracting(FacturaReporteDTO::getCodigoMoneda).containsExactly("PES", "DOL");
        assertThat(reporte).extracting(FacturaReporteDTO::getImporteTotal).containsExactly(100.00, 100.00);
    }

    @Test
    void ordenaPorFechaDescendenteYDesempataPorIdDescendente() {
        FacturaVenta del01 = guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta del05Menor = guardar(2L, LocalDate.of(2026, 11, 5), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta del10 = guardar(3L, LocalDate.of(2026, 11, 10), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta del05Mayor = guardar(4L, LocalDate.of(2026, 11, 5), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);

        assertThat(del05Mayor.getId()).isGreaterThan(del05Menor.getId());
        assertThat(repositorio.buscarReporte()).extracting(FacturaReporteDTO::getId)
                .containsExactly(del10.getId(), del05Mayor.getId(), del05Menor.getId(), del01.getId());
    }

    @Test
    void sinFacturasDevuelveListaVacia() {
        assertThat(repositorio.buscarReporte()).isEmpty();
    }

    @Test
    void laProyeccionNoCargaEntidadesAlContextoDePersistencia() {
        guardar(1L, LocalDate.of(2026, 11, 10), 100.00, EstadoFactura.EMITIDA, ferreteria, pesos, 2);
        Session sesion = em.getEntityManager().unwrap(Session.class);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte();

        assertThat(reporte).hasSize(1);
        assertThat(sesion.getStatistics().getEntityCount()).isZero();

        em.clear();
        assertThat(repositorio.findAll()).hasSize(1);
        assertThat(sesion.getStatistics().getEntityCount()).isPositive();
    }

    // --- Filtros (etapa 3) ---

    /** Tres facturas EMITIDA del 2026-11-01, 2026-11-05 y 2026-11-10; devuelve los ids en ese orden. */
    private List<Long> tresFechas() {
        FacturaVenta del01 = guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta del05 = guardar(2L, LocalDate.of(2026, 11, 5), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta del10 = guardar(3L, LocalDate.of(2026, 11, 10), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        return List.of(del01.getId(), del05.getId(), del10.getId());
    }

    @Test
    void limitesDeFechaIncluidos() {
        List<Long> ids = tresFechas();

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte(
                new FiltroFacturas(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 10), null, null));

        assertThat(reporte).extracting(FacturaReporteDTO::getId).containsExactly(ids.get(2), ids.get(1));
    }

    @Test
    void soloFechaHastaIncluyeElLimite() {
        List<Long> ids = tresFechas();

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte(
                new FiltroFacturas(null, LocalDate.of(2026, 11, 5), null, null));

        assertThat(reporte).extracting(FacturaReporteDTO::getId).containsExactly(ids.get(1), ids.get(0));
    }

    @Test
    void soloAnuladas() {
        guardar(null, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.BORRADOR, ferreteria, pesos, 1);
        guardar(1L, LocalDate.of(2026, 11, 2), 20.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta anulada = guardar(2L, LocalDate.of(2026, 11, 3), 30.00, EstadoFactura.ANULADA, ferreteria, pesos, 1);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte(
                new FiltroFacturas(null, null, EstadoFactura.ANULADA, null));

        assertThat(reporte).extracting(FacturaReporteDTO::getId).containsExactly(anulada.getId());
        assertThat(reporte.get(0).getEstado()).isEqualTo("ANULADA");
    }

    @Test
    void totalIgualAlMinimoSeIncluyeConUnImporteNoRepresentableExacto() {
        guardar(1L, LocalDate.of(2026, 11, 1), 100.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta de100con10 = guardar(2L, LocalDate.of(2026, 11, 2), 100.10, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        FacturaVenta de250 = guardar(3L, LocalDate.of(2026, 11, 3), 250.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte(new FiltroFacturas(null, null, null, 100.10));

        assertThat(reporte).extracting(FacturaReporteDTO::getId).containsExactly(de250.getId(), de100con10.getId());
        assertThat(reporte).extracting(FacturaReporteDTO::getImporteTotal).containsExactly(250.00, 100.10);
    }

    @Test
    void minimoNegativoNoExcluyeNada() {
        tresFechas();

        assertThat(repositorio.buscarReporte(new FiltroFacturas(null, null, null, -50.0))).hasSize(3);
    }

    @Test
    void filtrosSinCoincidenciasDevuelvenListaVacia() {
        guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);

        assertThat(repositorio.buscarReporte(new FiltroFacturas(null, null, EstadoFactura.ANULADA, null))).isEmpty();
        assertThat(repositorio.buscarReporte(new FiltroFacturas(null, null, null, 999999999.0))).isEmpty();
    }

    @Test
    void filtrosVaciosDanLoMismoQueLaConsultaSinFiltros() {
        guardar(null, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.BORRADOR, null, pesos, 0);
        guardar(1L, LocalDate.of(2026, 11, 5), 20.00, EstadoFactura.EMITIDA, ferreteria, dolares, 2);
        guardar(2L, LocalDate.of(2026, 11, 5), 30.00, EstadoFactura.ANULADA, ferreteria, pesos, 1);

        assertThat(repositorio.buscarReporte(FiltroFacturas.SIN_FILTROS)).hasSize(3)
                .containsExactlyElementsOf(repositorio.buscarReporte());
    }

    @Test
    void filtrosCombinadosConservanFacturaSinClienteYSinRenglones() {
        FacturaVenta sinClienteNiRenglones = guardar(1L, LocalDate.of(2026, 11, 5), 500.00, EstadoFactura.EMITIDA,
                null, pesos, 0);
        guardar(2L, LocalDate.of(2026, 11, 5), 50.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        guardar(null, LocalDate.of(2026, 11, 5), 500.00, EstadoFactura.BORRADOR, ferreteria, pesos, 1);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte(
                new FiltroFacturas(LocalDate.of(2026, 11, 1), null, EstadoFactura.EMITIDA, 100.0));

        assertThat(reporte).hasSize(1);
        assertThat(reporte.get(0).getId()).isEqualTo(sinClienteNiRenglones.getId());
        assertThat(reporte.get(0).getClienteDenominacion()).isEqualTo("Consumidor Final");
        assertThat(reporte.get(0).getCantidadItems()).isZero();
    }

    @Test
    void losValoresDeLosFiltrosNoEntranEnElTextoDeLaConsulta() {
        String texto = new FacturaReporteRepositoryImpl(null).jpql(
                new FiltroFacturas(LocalDate.of(2026, 11, 5), null, EstadoFactura.EMITIDA, 123.45));

        assertThat(texto).contains(":fechaDesde", ":estado", ":montoMinimo");
        assertThat(texto).doesNotContain("2026-11-05", "EMITIDA", "123.45");
    }

    // --- Paginación de la etapa 6: contar y buscarPagina ---

    @Test
    void contarCuentaUnaFilaPorFacturaSinImportarLosRenglones() {
        guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 3);
        guardar(2L, LocalDate.of(2026, 11, 2), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        guardar(3L, LocalDate.of(2026, 11, 3), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 0);

        assertThat(repositorio.contar(FiltroFacturas.SIN_FILTROS)).isEqualTo(3L);
    }

    @Test
    void contarAplicaElMismoFiltroQueElReporte() {
        guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 2);
        guardar(2L, LocalDate.of(2026, 11, 2), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        guardar(null, LocalDate.of(2026, 11, 3), 10.00, EstadoFactura.BORRADOR, ferreteria, pesos, 1);

        assertThat(repositorio.contar(new FiltroFacturas(null, null, EstadoFactura.EMITIDA, null))).isEqualTo(2L);
        assertThat(repositorio.contar(soloCliente(ferreteria.getId()))).isEqualTo(3L);
        assertThat(repositorio.contar(soloCliente(999999L))).isZero();
    }

    @Test
    void paginaIntermediaTraeLasFilasQueLeCorrespondenConSusRenglones() {
        // 12 facturas del 01/11 al 12/11, cada una con tantos renglones como su día (1..12 mod 3 + 1 no hace falta).
        List<Long> idsDeLaMasNuevaALaMasVieja = new java.util.ArrayList<>();
        for (int dia = 1; dia <= 12; dia++) {
            FacturaVenta f = guardar((long) dia, LocalDate.of(2026, 11, dia), 10.00, EstadoFactura.EMITIDA,
                    ferreteria, pesos, dia % 3 + 1);
            idsDeLaMasNuevaALaMasVieja.add(0, f.getId());
        }

        List<FacturaReporteDTO> pagina1 = repositorio.buscarPagina(FiltroFacturas.SIN_FILTROS, 5, 5);
        List<FacturaReporteDTO> pagina2 = repositorio.buscarPagina(FiltroFacturas.SIN_FILTROS, 10, 5);

        // La página 1 de tamaño 5 son la 6.ª a 10.ª más nuevas (días 7 a 3).
        assertThat(pagina1).extracting(FacturaReporteDTO::getId)
                .containsExactlyElementsOf(idsDeLaMasNuevaALaMasVieja.subList(5, 10));
        assertThat(pagina1).extracting(FacturaReporteDTO::getNumeroFactura).containsExactly(7L, 6L, 5L, 4L, 3L);
        assertThat(pagina1).extracting(FacturaReporteDTO::getCantidadItems).containsExactly(2L, 1L, 3L, 2L, 1L);
        // La página 2 son las dos más antiguas.
        assertThat(pagina2).extracting(FacturaReporteDTO::getNumeroFactura).containsExactly(2L, 1L);
        assertThat(pagina2).extracting(FacturaReporteDTO::getCantidadItems).containsExactly(3L, 2L);
    }

    @Test
    void conFechasIgualesLasPaginasNoDuplicanNiOmitenYCoincidenConElReporteCompleto() {
        for (long numero = 1; numero <= 6; numero++) {
            guardar(numero, LocalDate.of(2026, 11, 5), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 2);
        }

        List<FacturaReporteDTO> unidas = new java.util.ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            unidas.addAll(repositorio.buscarPagina(FiltroFacturas.SIN_FILTROS, pagina * 2L, 2));
        }

        assertThat(unidas).extracting(FacturaReporteDTO::getId).doesNotHaveDuplicates().isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        assertThat(unidas).containsExactlyElementsOf(repositorio.buscarReporte());
    }

    @Test
    void paginaPosteriorALaUltimaEstaVaciaYElTotalSigueSiendoElReal() {
        guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);
        guardar(2L, LocalDate.of(2026, 11, 2), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);

        assertThat(repositorio.buscarPagina(FiltroFacturas.SIN_FILTROS, 20, 10)).isEmpty();
        assertThat(repositorio.contar(FiltroFacturas.SIN_FILTROS)).isEqualTo(2L);
    }

    @Test
    void desplazamientoMayorQueIntegerMaxValueDevuelveVacioSinConsultar() {
        jakarta.persistence.EntityManager espia = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        FacturaReporteRepositoryImpl impl = new FacturaReporteRepositoryImpl(espia);

        List<FacturaReporteDTO> pagina = impl.buscarPagina(FiltroFacturas.SIN_FILTROS,
                (long) Integer.MAX_VALUE + 1, 10);

        assertThat(pagina).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(espia);
    }

    // --- Filtros nuevos de la etapa 6: número, cliente e informada ---

    @Test
    void numeroYClienteEntranComoParametrosYNoComoValores() {
        String texto = new FacturaReporteRepositoryImpl(null).jpql(
                new FiltroFacturas(null, null, null, null, 987654L, 4321L, null));

        assertThat(texto).contains("f.numero = :numero", "c.id = :clienteId");
        assertThat(texto).doesNotContain("987654", "4321");
    }

    @Test
    void informadaVerdaderaUsaCaeNoNuloYFalsaCaeNuloSinParametro() {
        FacturaReporteRepositoryImpl impl = new FacturaReporteRepositoryImpl(null);

        String verdadera = impl.jpql(new FiltroFacturas(null, null, null, null, null, null, true));
        String falsa = impl.jpql(new FiltroFacturas(null, null, null, null, null, null, false));

        assertThat(verdadera).contains("f.cae IS NOT NULL").doesNotContain(":informada");
        assertThat(falsa).contains("f.cae IS NULL").doesNotContain("IS NOT NULL").doesNotContain(":informada");
    }

    private Cliente otroCliente() {
        Cliente otro = em.persist(DatosDePrueba.cliente("Distribuidora Sur", "30-71111111-2",
                contacto("sur@ejemplo.com"), domicilio("Mitre", "50")));
        em.flush();
        em.clear();
        return otro;
    }

    private static FiltroFacturas soloNumero(long numero) {
        return new FiltroFacturas(null, null, null, null, numero, null, null);
    }

    private static FiltroFacturas soloCliente(long clienteId) {
        return new FiltroFacturas(null, null, null, null, null, clienteId, null);
    }

    private static FiltroFacturas soloInformada(boolean informada) {
        return new FiltroFacturas(null, null, null, null, null, null, informada);
    }

    @Test
    void filtroPorNumeroDevuelveSoloLasFacturasConEseNumeroDeCualquierPuntoDeVenta() {
        FacturaVenta numero1EnCentral = guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA,
                ferreteria, pesos, 1);
        FacturaVenta numero1EnPv1 = guardar(pv1, 1L, LocalDate.of(2026, 11, 2), 10.00, EstadoFactura.EMITIDA,
                ferreteria, pesos, 1, null);
        guardar(2L, LocalDate.of(2026, 11, 3), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte(soloNumero(1));

        assertThat(reporte).extracting(FacturaReporteDTO::getId)
                .containsExactly(numero1EnPv1.getId(), numero1EnCentral.getId());
        assertThat(repositorio.buscarReporte(soloNumero(77))).isEmpty();
    }

    @Test
    void filtroPorClienteExcluyeOtroClienteYConsumidorFinal() {
        Cliente sur = otroCliente();
        FacturaVenta deFerreteria = guardar(1L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria,
                pesos, 1);
        FacturaVenta deSur = guardar(2L, LocalDate.of(2026, 11, 2), 10.00, EstadoFactura.EMITIDA, sur, pesos, 1);
        guardar(3L, LocalDate.of(2026, 11, 3), 10.00, EstadoFactura.EMITIDA, null, pesos, 1);

        assertThat(repositorio.buscarReporte(soloCliente(ferreteria.getId())))
                .extracting(FacturaReporteDTO::getId).containsExactly(deFerreteria.getId());
        assertThat(repositorio.buscarReporte(soloCliente(sur.getId())))
                .extracting(FacturaReporteDTO::getId).containsExactly(deSur.getId());
        assertThat(repositorio.buscarReporte(soloCliente(999999L))).isEmpty();
    }

    @Test
    void filtroPorInformadaSeparaConCaeDeSinCae() {
        FacturaVenta borrador = guardar(casaCentral, null, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.BORRADOR,
                ferreteria, pesos, 1, null);
        FacturaVenta emitida = guardar(casaCentral, 1L, LocalDate.of(2026, 11, 2), 10.00, EstadoFactura.EMITIDA,
                ferreteria, pesos, 1, "70000000000001");
        FacturaVenta anulada = guardar(casaCentral, 2L, LocalDate.of(2026, 11, 3), 10.00, EstadoFactura.ANULADA,
                ferreteria, pesos, 1, "70000000000002");

        assertThat(repositorio.buscarReporte(soloInformada(true))).extracting(FacturaReporteDTO::getId)
                .containsExactly(anulada.getId(), emitida.getId());
        assertThat(repositorio.buscarReporte(soloInformada(false))).extracting(FacturaReporteDTO::getId)
                .containsExactly(borrador.getId());
    }

    @Test
    void clienteInformadaYFechaDesdeSeCombinanConY() {
        Cliente sur = otroCliente();
        FacturaVenta esperada = guardar(casaCentral, 1L, LocalDate.of(2026, 11, 10), 10.00, EstadoFactura.EMITIDA,
                ferreteria, pesos, 1, "70000000000001");
        guardar(casaCentral, 2L, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1,
                "70000000000002");
        guardar(casaCentral, 3L, LocalDate.of(2026, 11, 10), 10.00, EstadoFactura.EMITIDA, sur, pesos, 1,
                "70000000000003");
        guardar(casaCentral, null, LocalDate.of(2026, 11, 10), 10.00, EstadoFactura.BORRADOR, ferreteria, pesos, 1,
                null);

        List<FacturaReporteDTO> reporte = repositorio.buscarReporte(new FiltroFacturas(LocalDate.of(2026, 11, 5),
                null, null, null, null, ferreteria.getId(), true));

        assertThat(reporte).extracting(FacturaReporteDTO::getId).containsExactly(esperada.getId());
    }

    @Test
    void estadoBorradorContradiceInformadaVerdaderaYDaVacio() {
        guardar(casaCentral, null, LocalDate.of(2026, 11, 1), 10.00, EstadoFactura.BORRADOR, ferreteria, pesos, 1,
                null);
        guardar(casaCentral, 1L, LocalDate.of(2026, 11, 2), 10.00, EstadoFactura.EMITIDA, ferreteria, pesos, 1,
                "70000000000001");

        assertThat(repositorio.buscarReporte(new FiltroFacturas(null, null, EstadoFactura.BORRADOR, null, null, null,
                true))).isEmpty();
    }
}
