package ar.edu.utn.frm.facturacion.model;

/**
 * Contexto de auditoría por hilo: guarda el usuario que está operando para que
 * {@link AuditoriaApp} complete los datos de alta sin que cada llamador los pase a mano.
 * En la API lo llena y limpia el {@code UsuarioActualInterceptor} (encabezado {@code X-Usuario}); lo usa también el
 * {@code DataLoader}. Siempre hay que llamar a {@link #clear()} al terminar.
 */
public final class UsuarioActual {

    private static final ThreadLocal<Usuario> ACTUAL = new ThreadLocal<>();

    private UsuarioActual() {
    }

    public static void set(Usuario usuario) {
        ACTUAL.set(usuario);
    }

    public static Usuario get() {
        return ACTUAL.get();
    }

    public static void clear() {
        ACTUAL.remove();
    }
}
