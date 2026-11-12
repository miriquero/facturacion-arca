package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "lista_precio")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ListaPrecio extends AuditoriaApp {

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false)
    private String denominacion;
}
