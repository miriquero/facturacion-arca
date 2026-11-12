package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "condicion_iva")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CondicionIva extends AuditoriaApp {

    @Column(name = "codigo_afip", nullable = false)
    private int codigoAfip;

    @Column(nullable = false)
    private String denominacion;
}
