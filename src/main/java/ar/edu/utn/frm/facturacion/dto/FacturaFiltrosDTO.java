package ar.edu.utn.frm.facturacion.dto;

import java.time.LocalDate;

/**
 * Filtros tal como llegan por HTTP, sin normalizar ni validar. {@code informada} llega como texto crudo (igual
 * criterio que {@code estado}): el conversor de Spring aceptaría «yes», «on» o «1» y el contrato solo admite
 * {@code true} y {@code false}.
 */
public record FacturaFiltrosDTO(LocalDate fechaDesde, LocalDate fechaHasta, String estado, Double montoMinimo,
                                Long numero, Long clienteId, String informada) {

    /** Constructor de las etapas 2 y 3 (sin los filtros nuevos). */
    public FacturaFiltrosDTO(LocalDate fechaDesde, LocalDate fechaHasta, String estado, Double montoMinimo) {
        this(fechaDesde, fechaHasta, estado, montoMinimo, null, null, null);
    }
}
