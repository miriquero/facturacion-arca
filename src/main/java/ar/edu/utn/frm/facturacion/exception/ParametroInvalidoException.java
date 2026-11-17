package ar.edu.utn.frm.facturacion.exception;

/** Un parámetro de entrada tiene un valor que la API no admite. Se traduce a 400 Bad Request. */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensaje) {
        super(mensaje);
    }
}
