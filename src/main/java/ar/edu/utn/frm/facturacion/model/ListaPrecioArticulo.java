package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Precio de un artículo en una lista. Su id es el que viaja en el renglón de la factura. */
@Entity
@Table(name = "lista_precio_articulo", uniqueConstraints = @UniqueConstraint(
        name = "uk_lista_precio_articulo", columnNames = {"lista_precio_id", "articulo_id"}))
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ListaPrecioArticulo extends AuditoriaApp {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_id", nullable = false)
    private ListaPrecio listaPrecio;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;

    @Column(name = "precio_venta", nullable = false)
    private double precioVenta;
}
