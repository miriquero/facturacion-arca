package ar.edu.utn.frm.facturacion.dto;

import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;

/**
 * Pagina pedida ya validada (base 0). Es la unica puerta de validacion de {@code pagina} y {@code tamanio}.
 */
public record SolicitudPagina(int pagina, int tamanio) {

    public static final int PAGINA_POR_DEFECTO = 0;
    public static final int TAMANIO_POR_DEFECTO = 10;
    public static final int TAMANIO_MAXIMO = 100;

    /**
     * Funcion pura: nulo equivale al valor por defecto.
     *
     * @throws ParametroInvalidoException si {@code pagina} es negativa o {@code tamanio} no esta entre 1 y 100
     */
    public static SolicitudPagina de(Integer pagina, Integer tamanio) {
        int paginaValida = pagina == null ? PAGINA_POR_DEFECTO : pagina;
        int tamanioValido = tamanio == null ? TAMANIO_POR_DEFECTO : tamanio;
        if (paginaValida < 0) {
            throw new ParametroInvalidoException("pagina debe ser mayor o igual que 0 (se recibió " + paginaValida
                    + ").");
        }
        if (tamanioValido < 1 || tamanioValido > TAMANIO_MAXIMO) {
            throw new ParametroInvalidoException("tamanio debe estar entre 1 y " + TAMANIO_MAXIMO + " (se recibió "
                    + tamanioValido + ").");
        }
        return new SolicitudPagina(paginaValida, tamanioValido);
    }

    /** Fila inicial de la pagina, en {@code long} para que {@code pagina * tamanio} no desborde. */
    public long desplazamiento() {
        return (long) pagina * tamanio;
    }
}
