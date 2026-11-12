package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "punto_venta", uniqueConstraints = @UniqueConstraint(name = "uk_punto_venta_numero", columnNames = "numero"))
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PuntoVenta extends AuditoriaApp {

    @Column(nullable = false)
    private int numero;

    private String descripcion;

    @Column(name = "tipo_emision")
    private String tipoEmision;

    @Column(name = "domicilio_comercial")
    private String domicilioComercial;
}
