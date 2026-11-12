package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Dependiente del {@link Cliente}: no tiene repositorio propio, se guarda y elimina con su cliente. */
@Entity
@Table(name = "contacto")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Contacto extends EntityId {

    private String email;
    private String telefono;
    private String celular;
}
