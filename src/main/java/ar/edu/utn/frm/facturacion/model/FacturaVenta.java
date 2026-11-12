package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Raíz del agregado Factura: los renglones solo existen dentro de ella. La cascada va únicamente
 * hacia los detalles; cliente, punto de venta, condición de IVA y moneda son referencias sin cascada.
 * Los importes son datos en esta etapa: no se calcula nada.
 */
@Entity
@Table(name = "factura_venta", uniqueConstraints = @UniqueConstraint(
        name = "uk_factura_venta_punto_venta_numero", columnNames = {"punto_venta_id", "numero"}))
@Getter
@Setter
@NoArgsConstructor
@ToString
public class FacturaVenta extends AuditoriaApp {

    /** Nulo mientras es BORRADOR. */
    private Long numero;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_moneda_id", nullable = false)
    private TipoMoneda tipoMoneda;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;

    @Column(name = "importe_cobrado")
    private double importeCobrado;

    @Column(name = "importe_saldo")
    private double importeSaldo;

    @Column(name = "importe_total", nullable = false)
    private double importeTotal;

    @Column(length = 14)
    private String cae;

    @Column(name = "cae_fecha_vencimiento")
    private LocalDate caeFechaVencimiento;

    @Column(name = "resultado_afip")
    private String resultadoAfip;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFactura estado;

    @Column(name = "fecha_anulacion")
    private LocalDate fechaAnulacion;

    @Column(length = 500)
    private String observaciones;

    @ToString.Exclude
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FacturaVentaDetalle> detalles = new ArrayList<>();

    /** Fija ambos extremos de la relación. */
    public void agregarDetalle(FacturaVentaDetalle detalle) {
        detalles.add(detalle);
        detalle.setFactura(this);
    }

    public void quitarDetalle(FacturaVentaDetalle detalle) {
        detalles.remove(detalle);
        detalle.setFactura(null);
    }

    /** Reemplaza los renglones sobre la MISMA colección (con orphanRemoval no se puede asignar otra lista). */
    public void reemplazarDetalles(List<FacturaVentaDetalle> nuevos) {
        List<FacturaVentaDetalle> aAgregar = new ArrayList<>(nuevos);
        detalles.forEach(d -> d.setFactura(null));
        detalles.clear();
        aAgregar.forEach(this::agregarDetalle);
    }
}
