package ar.edu.utn.frm.facturacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/facturas/{id}/estado}: el estado pedido por nombre (se normaliza con {@code strip}
 * y mayúsculas) y una observación opcional que, si no está en blanco, reemplaza la de la factura.
 */
public record CambioEstadoRequestDTO(
        @NotBlank(message = "El estado es obligatorio") String estado,
        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres") String observaciones) {
}
