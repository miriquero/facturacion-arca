package ar.edu.utn.frm.facturacion.dto;

import java.time.LocalDate;

/** Filtros tal como llegan por HTTP, sin normalizar ni validar. */
public record FacturaFiltrosDTO(LocalDate fechaDesde, LocalDate fechaHasta, String estado, Double montoMinimo) {
}
