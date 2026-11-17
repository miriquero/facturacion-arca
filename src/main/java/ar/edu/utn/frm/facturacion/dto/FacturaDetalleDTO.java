package ar.edu.utn.frm.facturacion.dto;

/** Renglón del detalle de una factura. Importes tal como están guardados; no se calcula nada. */
public record FacturaDetalleDTO(
        Long id,
        Long listaPrecioArticuloId,
        String articuloCodigo,
        String descripcion,
        double cantidad,
        double precioUnitario,
        double porcentajeBonificacion,
        double importeNeto,
        double importeIva,
        double importeSubtotal) {
}
