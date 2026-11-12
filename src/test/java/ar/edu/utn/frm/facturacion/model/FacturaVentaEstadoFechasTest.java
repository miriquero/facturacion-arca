package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.detalle;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.facturaBorrador;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FacturaVentaEstadoFechasTest extends FacturaBase {

    @Autowired
    private FacturaVentaRepository facturaRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private FacturaVenta guardar(EstadoFactura estado, long numero) {
        FacturaVenta f = facturaBorrador(pv1, condicion, pesos, null);
        f.setEstado(estado);
        if (estado != EstadoFactura.BORRADOR) {
            f.setNumero(numero);
        }
        f.agregarDetalle(detalle(precioMartillo, 1, 3000));
        return facturaRepository.saveAndFlush(f);
    }

    @Test
    void elEstadoSeAlmacenaComoTextoConElNombreDelValor() {
        Long id = guardar(EstadoFactura.EMITIDA, 1).getId();

        String almacenado = jdbc.queryForObject("select estado from factura_venta where id = ?", String.class, id);

        assertThat(almacenado).isEqualTo("EMITIDA");
    }

    @Test
    void cadaEstadoSeAlmacenaPorSuNombre() {
        Long borrador = guardar(EstadoFactura.BORRADOR, 0).getId();
        Long anulada = guardar(EstadoFactura.ANULADA, 2).getId();

        assertThat(jdbc.queryForObject("select estado from factura_venta where id = ?", String.class, borrador))
                .isEqualTo("BORRADOR");
        assertThat(jdbc.queryForObject("select estado from factura_venta where id = ?", String.class, anulada))
                .isEqualTo("ANULADA");
    }

    @Test
    void idaYVueltaDeLosTresEstados() {
        Long borrador = guardar(EstadoFactura.BORRADOR, 0).getId();
        Long emitida = guardar(EstadoFactura.EMITIDA, 1).getId();
        Long anulada = guardar(EstadoFactura.ANULADA, 2).getId();
        em.clear();

        assertThat(facturaRepository.findById(borrador).orElseThrow().getEstado()).isEqualTo(EstadoFactura.BORRADOR);
        assertThat(facturaRepository.findById(emitida).orElseThrow().getEstado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(facturaRepository.findById(anulada).orElseThrow().getEstado()).isEqualTo(EstadoFactura.ANULADA);
    }

    @Test
    void fechaDeEmisionSeConservaExacta() {
        Long id = guardar(EstadoFactura.EMITIDA, 1).getId();
        em.clear();

        assertThat(facturaRepository.findById(id).orElseThrow().getFechaEmision()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void otraFechaDeEmisionTambienSeConserva() {
        FacturaVenta f = facturaBorrador(pv1, condicion, pesos, null);
        f.setFechaEmision(LocalDate.of(2026, 1, 31));
        Long id = facturaRepository.saveAndFlush(f).getId();
        em.clear();

        assertThat(facturaRepository.findById(id).orElseThrow().getFechaEmision()).isEqualTo(LocalDate.of(2026, 1, 31));
    }
}
