package ar.edu.utn.frm.facturacion.dto;

import java.time.LocalDate;
import java.util.List;

/** Cabecera y renglones de una factura para mostrarla o editarla. Se arma dentro de la transacción del servicio. */
public record FacturaDetalladaDTO(
        Long id,
        Long numero,
        LocalDate fechaEmision,
        String estado,
        Long clienteId,
        String clienteDenominacion,
        Long condicionIvaId,
        String condicionIva,
        Long puntoVentaId,
        int puntoVentaNumero,
        String puntoVentaDescripcion,
        Long tipoMonedaId,
        String codigoMoneda,
        double importeTotal,
        double importeCobrado,
        double importeSaldo,
        String cae,
        LocalDate caeFechaVencimiento,
        String resultadoAfip,
        LocalDate fechaAnulacion,
        String observaciones,
        List<FacturaDetalleDTO> detalles) {
}
