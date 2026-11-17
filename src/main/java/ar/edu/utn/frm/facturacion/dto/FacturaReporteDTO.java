package ar.edu.utn.frm.facturacion.dto;

import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Fila del reporte de facturas. Es inmutable y se construye directamente desde la consulta JPQL
 * ({@code SELECT new ...}), por lo que Hibernate resuelve el constructor por posición y tipo.
 */
@Getter
@ToString
@EqualsAndHashCode
public class FacturaReporteDTO {

    private final Long id;
    private final Long numeroFactura;
    private final LocalDate fechaEmision;
    private final String clienteDenominacion;
    private final String condicionIva;
    private final String puntoVentaDescripcion;
    private final double importeTotal;
    private final long cantidadItems;
    private final String estado;
    private final String codigoMoneda;

    public FacturaReporteDTO(Long id, Long numeroFactura, LocalDate fechaEmision, String clienteDenominacion,
            String condicionIva, String puntoVentaDescripcion, Double importeTotal, Long cantidadItems,
            EstadoFactura estado, String codigoMoneda) {
        this.id = id;
        this.numeroFactura = numeroFactura;
        this.fechaEmision = fechaEmision;
        this.clienteDenominacion = clienteDenominacion;
        this.condicionIva = condicionIva;
        this.puntoVentaDescripcion = puntoVentaDescripcion;
        this.importeTotal = importeTotal;
        this.cantidadItems = cantidadItems;
        this.estado = estado.name();
        this.codigoMoneda = codigoMoneda;
    }
}
