package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Superclase de todas las entidades: identificador generado por la base (IDENTITY). No genera tabla propia. */
@Getter
@ToString
@Setter
@NoArgsConstructor
@MappedSuperclass
public abstract class EntityId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;
}
