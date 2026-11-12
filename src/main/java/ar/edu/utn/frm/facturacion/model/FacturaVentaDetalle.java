package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Renglón de una factura. Es el lado propietario de la relación: la FK {@code factura_id} está en esta tabla. */
@Entity
@Table(name = "factura_venta_detalle")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class FacturaVentaDetalle extends EntityId {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private FacturaVenta factura;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_articulo_id", nullable = false)
    private ListaPrecioArticulo listaPrecioArticulo;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private double cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private double precioUnitario;

    @Column(name = "porcentaje_bonificacion")
    private double porcentajeBonificacion;

    @Column(name = "importe_neto")
    private double importeNeto;

    @Column(name = "importe_iva")
    private double importeIva;

    @Column(name = "importe_subtotal", nullable = false)
    private double importeSubtotal;
}
