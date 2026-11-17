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
        FacturaVenta factura = DatosDePrueba.facturaBorrador(casaCentral, responsableInscripto, moneda, cliente);
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
}
