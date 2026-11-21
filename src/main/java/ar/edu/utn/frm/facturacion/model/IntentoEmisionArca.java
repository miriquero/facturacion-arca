package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Reserva durable de un número fiscal, independiente de la transacción de la factura. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"cuit", "punto", "numero"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntentoEmisionArca {
    @Id
    private Long facturaId;
    private long cuit;
    private int punto;
    private long numero;

    public IntentoEmisionArca(Long facturaId, long cuit, int punto, long numero) {
        this.facturaId = facturaId;
        this.cuit = cuit;
        this.punto = punto;
        this.numero = numero;
    }
}
