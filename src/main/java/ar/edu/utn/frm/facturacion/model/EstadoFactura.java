package ar.edu.utn.frm.facturacion.model;

/** Estados de una factura. Se persiste por nombre ({@code EnumType.STRING}), nunca por ordinal. */
public enum EstadoFactura {
    BORRADOR,
    EMITIDA,
    ANULADA;

    /** Tabla del ciclo de vida: solo BORRADOR a EMITIDA y EMITIDA a ANULADA (no hay vuelta atrás ni salto). */
    public boolean puedePasarA(EstadoFactura destino) {
        return switch (this) {
            case BORRADOR -> destino == EMITIDA;
            case EMITIDA -> destino == ANULADA;
            case ANULADA -> false;
        };
    }
}
