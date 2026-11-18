package ar.edu.utn.frm.facturacion.exception;

/** Una operación choca con una regla de negocio o con el estado actual. Se traduce a 409 Conflict. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
