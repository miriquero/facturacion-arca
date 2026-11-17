package ar.edu.utn.frm.facturacion.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepresentacionTextualTest {

    @Test
    void facturaConDetallesEnlazadosNoRecurreNiIncluyeLosDetalles() {
        FacturaVenta factura = new FacturaVenta();
        factura.setNumero(7L);
        factura.agregarDetalle(new FacturaVentaDetalle());
        factura.agregarDetalle(new FacturaVentaDetalle());

        String texto = factura.toString();

        assertThat(texto).contains("numero=7").doesNotContain("detalles");
        assertThat(factura.getDetalles().get(0).toString()).doesNotContain("factura=");
    }

    @Test
    void facturaConClienteYCatalogosNoLosIncluye() {
        FacturaVenta factura = new FacturaVenta();
        factura.setCliente(new Cliente());
        factura.setPuntoVenta(new PuntoVenta());

        assertThat(factura.toString()).doesNotContain("cliente=", "puntoVenta=", "condicionIva=", "tipoMoneda=");
    }

    @Test
    void usuarioNoMuestraLaClave() {
        Usuario usuario = new Usuario();
        usuario.setUsuario("sistema");
        usuario.setClave("secreta-1234");

        assertThat(usuario.toString()).contains("sistema").doesNotContain("secreta-1234");
    }

    @Test
    void entidadAuditableNoMuestraLosUsuariosDeAuditoria() {
        Marca marca = new Marca();
        marca.setUsuarioCarga(new Usuario());
        marca.setUsuarioModificacion(new Usuario());

        assertThat(marca.toString()).doesNotContain("usuarioCarga", "usuarioModificacion");
    }

    @Test
    void detalleEnlazadoASuFacturaNoRecurreNiIncluyeLaFactura() {
        FacturaVenta factura = new FacturaVenta();
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setDescripcion("Martillo");
        factura.agregarDetalle(detalle);

        String texto = detalle.toString();

        assertThat(factura.getDetalles()).containsExactly(detalle);
        assertThat(detalle.getFactura()).isSameAs(factura);
        assertThat(texto).contains("descripcion=Martillo").doesNotContain("factura=");
    }

    @Test
    void identificadorHeredadoApareceEnLaRepresentacionDeUnaFactura() {
        FacturaVenta factura = new FacturaVenta();
        factura.setId(42L);

        assertThat(factura.toString()).contains("id=42");
    }

    @Test
    void identificadorHeredadoApareceEnLaRepresentacionDeUnUsuario() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setUsuario("sistema");

        assertThat(usuario.toString()).contains("id=7", "sistema");
    }
}
