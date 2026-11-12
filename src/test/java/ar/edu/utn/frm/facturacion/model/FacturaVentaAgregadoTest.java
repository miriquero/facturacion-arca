package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.detalle;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.facturaBorrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.puntoVenta;

import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

class FacturaVentaAgregadoTest extends FacturaBase {

    @Autowired
    private FacturaVentaRepository facturaRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private FacturaVenta guardarYReleer(FacturaVenta factura) {
        Long id = facturaRepository.saveAndFlush(factura).getId();
        em.clear();
        return facturaRepository.findById(id).orElseThrow();
    }

    @Test
    void guardarLaFacturaGuardaSusDosRenglones() {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, null);
        factura.agregarDetalle(detalle(precioMartillo, 2, 3000));
        factura.agregarDetalle(detalle(precioTelevisor, 1, 60000));

        FacturaVenta leida = guardarYReleer(factura);

        assertThat(leida.getCliente()).isNull();
        assertThat(leida.getDetalles()).hasSize(2);
        assertThat(leida.getDetalles()).allSatisfy(d -> assertThat(d.getFactura()).isSameAs(leida));
        assertThat(leida.getDetalles()).extracting(FacturaVentaDetalle::getCantidad)
                .containsExactlyInAnyOrder(2.0, 1.0);
    }

    @Test
    void facturaConUnSoloRenglon() {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, null);
        factura.agregarDetalle(detalle(precioMartillo, 5, 3000));

        FacturaVenta leida = guardarYReleer(factura);

        assertThat(leida.getDetalles()).hasSize(1);
        assertThat(leida.getDetalles().get(0).getCantidad()).isEqualTo(5.0);
    }

    private FacturaVenta guardarConDosRenglones(Cliente cliente) {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, cliente);
        factura.agregarDetalle(detalle(precioMartillo, 2, 3000));
        factura.agregarDetalle(detalle(precioTelevisor, 1, 60000));
        return guardarYReleer(factura);
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
    }

    @Test
    void quitarUnRenglonLoEliminaDeLaBase() {
        FacturaVenta factura = guardarConDosRenglones(null);
        FacturaVentaDetalle quitado = factura.getDetalles().get(0);
        Long quitadoId = quitado.getId();
        Long facturaId = factura.getId();

        factura.quitarDetalle(quitado);
        em.flush();
        em.clear();

        assertThat(facturaRepository.findById(facturaId).orElseThrow().getDetalles()).hasSize(1);
        assertThat(em.find(FacturaVentaDetalle.class, quitadoId)).isNull();
        assertThat(contar("factura_venta_detalle")).isEqualTo(1);
    }

    @Test
    void reemplazarLosRenglonesDejaSoloElNuevoSobreLaMismaColeccion() {
        FacturaVenta factura = guardarConDosRenglones(null);
        Long facturaId = factura.getId();
        var coleccion = factura.getDetalles();
        var anteriores = coleccion.stream().map(FacturaVentaDetalle::getId).toList();

        factura.reemplazarDetalles(java.util.List.of(detalle(precioMartillo, 7, 3000)));
        em.flush();
        em.clear();

        assertThat(factura.getDetalles()).isSameAs(coleccion);
        FacturaVenta releida = facturaRepository.findById(facturaId).orElseThrow();
        assertThat(releida.getDetalles()).hasSize(1);
        assertThat(releida.getDetalles().get(0).getCantidad()).isEqualTo(7.0);
        anteriores.forEach(id -> assertThat(em.find(FacturaVentaDetalle.class, id)).isNull());
        assertThat(contar("factura_venta_detalle")).isEqualTo(1);
    }

    @Test
    void eliminarUnBorradorConservaCatalogosYCliente() {
        FacturaVenta factura = guardarConDosRenglones(juan);
        Long facturaId = factura.getId();
        var renglones = factura.getDetalles().stream().map(FacturaVentaDetalle::getId).toList();

        facturaRepository.delete(factura);
        em.flush();
        em.clear();

        assertThat(em.find(FacturaVenta.class, facturaId)).isNull();
        renglones.forEach(id -> assertThat(em.find(FacturaVentaDetalle.class, id)).isNull());
        assertThat(contar("factura_venta")).isZero();
        assertThat(contar("factura_venta_detalle")).isZero();
        assertThat(contar("cliente")).isEqualTo(1);
        assertThat(contar("contacto")).isEqualTo(1);
        assertThat(contar("domicilio")).isEqualTo(1);
        assertThat(contar("punto_venta")).isEqualTo(2);
        assertThat(contar("condicion_iva")).isEqualTo(1);
        assertThat(contar("tipo_moneda")).isEqualTo(1);
        assertThat(contar("lista_precio")).isEqualTo(1);
        assertThat(contar("articulo")).isEqualTo(2);
        assertThat(contar("lista_precio_articulo")).isEqualTo(2);
    }

    @Test
    void puntoVentaNoGuardadoNoSeGuardaEnCascada() {
        PuntoVenta sinGuardar = puntoVenta(99);
        FacturaVenta factura = facturaBorrador(sinGuardar, condicion, pesos, null);
        factura.agregarDetalle(detalle(precioMartillo, 1, 3000));

        assertThatThrownBy(() -> facturaRepository.saveAndFlush(factura))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThat(contar("punto_venta")).isEqualTo(2);
        assertThat(sinGuardar.getId()).isNull();
    }
}
