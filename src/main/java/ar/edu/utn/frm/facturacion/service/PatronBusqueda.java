package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import java.util.Locale;
import java.util.Optional;

/**
 * Patrón de búsqueda «contiene» para los catálogos, armado en Java: el texto del usuario se recorta, se pasa a
 * minúsculas, se escapan {@code \}, {@code %} y {@code _} (para que sean texto literal) y se envuelve en
 * {@code %…%}. Se usa con {@code lower(campo) like :patron escape '\'}.
 */
public final class PatronBusqueda {

    public static final int LARGO_MAXIMO = 100;

    private PatronBusqueda() {
    }

    /**
     * @return el patrón, o vacío si {@code q} es nulo o está en blanco (catálogo completo)
     * @throws ParametroInvalidoException si, sin espacios exteriores, {@code q} supera {@link #LARGO_MAXIMO}
     */
    public static Optional<String> de(String q) {
        if (q == null || q.isBlank()) {
            return Optional.empty();
        }
        String texto = q.strip();
        if (texto.length() > LARGO_MAXIMO) {
            throw new ParametroInvalidoException("q no puede superar los " + LARGO_MAXIMO
                    + " caracteres (se recibieron " + texto.length() + ").");
        }
        String escapado = texto.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return Optional.of("%" + escapado + "%");
    }
}
