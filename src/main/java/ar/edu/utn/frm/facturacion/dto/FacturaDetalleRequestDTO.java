package ar.edu.utn.frm.facturacion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Renglón de una factura en el cuerpo de {@code POST} y {@code PUT}. No lleva precio ni importes: el servidor
 * los toma de la lista de precios y los calcula. {@code listaPrecioArticuloId} es el de la asociación
 * lista-artículo, no el del artículo. Cantidad y bonificación son {@link BigDecimal}: el número JSON llega sin
 * pasar por binario y siempre es finito.
 */
public record FacturaDetalleRequestDTO(
        @NotNull(message = "El renglón debe indicar la lista de precios del artículo") Long listaPrecioArticuloId,
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor que cero") BigDecimal cantidad,
        @DecimalMin(value = "0", message = "La bonificación no puede ser negativa")
        @DecimalMax(value = "100", message = "La bonificación no puede superar 100") BigDecimal porcentajeBonificacion,
        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres") String descripcion) {
}
