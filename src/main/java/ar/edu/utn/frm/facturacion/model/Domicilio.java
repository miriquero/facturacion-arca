package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Dependiente del {@link Cliente}: no tiene repositorio propio, se guarda y elimina con su cliente. */
@Entity
@Table(name = "domicilio")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Domicilio extends EntityId {

    @Column(name = "nombre_calle")
    private String nombreCalle;

    @Column(name = "numero_calle")
    private String numeroCalle;
}
