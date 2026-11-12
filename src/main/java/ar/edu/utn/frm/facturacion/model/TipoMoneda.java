package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "tipo_moneda")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TipoMoneda extends AuditoriaApp {

    @Column(name = "codigo_afip", nullable = false, length = 10)
    private String codigoAfip;

    @Column(nullable = false)
    private String denominacion;

    @Column(nullable = false, length = 5)
    private String simbolo;
}
