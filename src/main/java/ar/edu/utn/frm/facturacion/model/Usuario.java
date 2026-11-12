package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Usuario usado solo para auditoría (no hay login): ver {@link UsuarioActual}. */
@Entity
@Table(name = "usuario", uniqueConstraints = @UniqueConstraint(name = "uk_usuario_usuario", columnNames = "usuario"))
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Usuario extends EntityId {

    @Column(nullable = false, length = 50)
    private String usuario;

    @ToString.Exclude
    @Column(nullable = false)
    private String clave;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;
}
