package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Raíz del agregado Cliente: contacto y domicilio no existen sin él. La clave foránea está en la
 * tabla {@code cliente}; la cascada y el {@code orphanRemoval} hacen que se guarden, reemplacen y
 * eliminen junto con el cliente.
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Cliente extends AuditoriaApp {

    @Column(name = "cuit_cuil", nullable = false, length = 13)
    private String cuitCuil;

    @Column(nullable = false)
    private String denominacion;

    @ToString.Exclude
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @ToString.Exclude
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domicilio_id", nullable = false)
    private Domicilio domicilio;
}
