package ar.edu.utn.frm.facturacion.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FacturaVentaTest {

    @Test
    void facturaNuevaTieneColeccionVaciaYNoNula() {
        assertThat(new FacturaVenta().getDetalles()).isNotNull().isEmpty();
    }

    @Test
    void agregarDetalleEnlazaAmbosExtremos() {
        FacturaVenta factura = new FacturaVenta();
        FacturaVentaDetalle uno = new FacturaVentaDetalle();
        FacturaVentaDetalle dos = new FacturaVentaDetalle();

        factura.agregarDetalle(uno);
        factura.agregarDetalle(dos);

        assertThat(factura.getDetalles()).containsExactly(uno, dos);
        assertThat(uno.getFactura()).isSameAs(factura);
        assertThat(dos.getFactura()).isSameAs(factura);
    }

    @Test
    void quitarDetalleDesenlazaAmbosExtremos() {
        FacturaVenta factura = new FacturaVenta();
        FacturaVentaDetalle uno = new FacturaVentaDetalle();
        FacturaVentaDetalle dos = new FacturaVentaDetalle();
        factura.agregarDetalle(uno);
        factura.agregarDetalle(dos);

        factura.quitarDetalle(uno);

        assertThat(factura.getDetalles()).containsExactly(dos);
        assertThat(uno.getFactura()).isNull();
        assertThat(dos.getFactura()).isSameAs(factura);
    }

    @Test
    void reemplazarDetallesConservaLaMismaInstanciaDeColeccion() {
        FacturaVenta factura = new FacturaVenta();
        FacturaVentaDetalle viejo = new FacturaVentaDetalle();
        FacturaVentaDetalle nuevo = new FacturaVentaDetalle();
        factura.agregarDetalle(viejo);
        List<FacturaVentaDetalle> coleccionOriginal = factura.getDetalles();

        factura.reemplazarDetalles(List.of(nuevo));

        assertThat(factura.getDetalles()).isSameAs(coleccionOriginal).containsExactly(nuevo);
        assertThat(nuevo.getFactura()).isSameAs(factura);
        assertThat(viejo.getFactura()).isNull();
    }

    @Test
    void reemplazarConLaPropiaColeccionNoPierdeLosRenglones() {
        FacturaVenta factura = new FacturaVenta();
        FacturaVentaDetalle uno = new FacturaVentaDetalle();
        factura.agregarDetalle(uno);

        factura.reemplazarDetalles(List.copyOf(factura.getDetalles()));

        assertThat(factura.getDetalles()).containsExactly(uno);
        assertThat(uno.getFactura()).isSameAs(factura);
    }
}
