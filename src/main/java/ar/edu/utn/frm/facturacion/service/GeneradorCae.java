package ar.edu.utn.frm.facturacion.service;

/** Fuente del CAE ficticio de una factura emitida. Es una interfaz para poder fijarlo en las pruebas. */
public interface GeneradorCae {

    /** Un CAE de exactamente 14 dígitos decimales. */
    String generar();
}
