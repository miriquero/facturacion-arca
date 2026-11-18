package ar.edu.utn.frm.facturacion.dto;

import java.util.List;

/**
 * Sobre de una pagina de resultados. {@code totalElementos} cuenta todas las filas que cumplen el filtro, no solo
 * las de la pagina.
 */
public record PaginaDTO<T>(List<T> items, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <T> PaginaDTO<T> de(List<T> items, SolicitudPagina solicitud, long total) {
        long paginas = (total + solicitud.tamanio() - 1) / solicitud.tamanio();
        return new PaginaDTO<>(items, solicitud.pagina(), solicitud.tamanio(), total, (int) paginas);
    }
}
