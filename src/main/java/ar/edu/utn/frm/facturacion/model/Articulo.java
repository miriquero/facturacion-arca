package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "articulo")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Articulo extends AuditoriaApp {

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false)
    private String denominacion;

    /** Opcional: si es nulo se usa la alícuota por defecto (se resuelve en una etapa posterior). */
    @Column(name = "alicuota_iva")
    private Double alicuotaIva;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubro_id")
    private Rubro rubro;
}
