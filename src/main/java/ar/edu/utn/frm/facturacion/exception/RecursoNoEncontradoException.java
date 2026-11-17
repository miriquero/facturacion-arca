package ar.edu.utn.frm.facturacion.exception;

/** Un recurso pedido por id no existe. Se traduce a 404 Not Found. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Long id) {
        super(recurso + " con id " + id + " no encontrado");
    }
}
