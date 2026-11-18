package ar.edu.utn.frm.facturacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Cuerpo de {@code POST /api/facturas} y {@code PUT /api/facturas/{id}} (el {@code PUT} reemplaza todo). Lleva
 * identificadores, nunca entidades: el servidor resuelve las referencias (404 si no existen). Sin cliente la
 * factura es a «Consumidor Final». No hay estado, número, CAE ni importes: los fija el servidor.
 */
public record FacturaRequestDTO(
        Long clienteId,
        @NotNull(message = "La condición de IVA es obligatoria") Long condicionIvaId,
        @NotNull(message = "El punto de venta es obligatorio") Long puntoVentaId,
        @NotNull(message = "El tipo de moneda es obligatorio") Long tipoMonedaId,
        LocalDate fechaEmision,
        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres") String observaciones,
        @NotEmpty(message = "La factura debe tener al menos un renglón")
        List<@NotNull(message = "Un renglón no puede ser nulo") @Valid FacturaDetalleRequestDTO> detalles) {
}
