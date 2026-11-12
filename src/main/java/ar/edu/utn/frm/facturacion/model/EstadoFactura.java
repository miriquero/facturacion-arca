package ar.edu.utn.frm.facturacion.model;

/** Estados de una factura. Se persiste por nombre ({@code EnumType.STRING}), nunca por ordinal. */
public enum EstadoFactura {
    BORRADOR,
    EMITIDA,
    ANULADA
}
