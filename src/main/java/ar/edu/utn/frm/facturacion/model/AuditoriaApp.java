package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Superclase de las entidades auditables. No genera tabla: sus columnas aparecen en cada tabla hija.
 * El alta se completa en {@link #completarAlta()} con el usuario de {@link UsuarioActual}; si el
 * contexto está vacío los usuarios quedan nulos y la base rechaza el INSERT (NOT NULL).
 */
@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
public abstract class AuditoriaApp extends EntityId {

    @Column(name = "fecha_alta", nullable = false, updatable = false)
    protected LocalDateTime fechaAlta;

    @Column(name = "fecha_modificacion", nullable = false)
    protected LocalDateTime fechaModificacion;

    @Column(name = "fecha_baja")
    protected LocalDateTime fechaBaja;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_carga_id", nullable = false, updatable = false)
    protected Usuario usuarioCarga;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_modificacion_id", nullable = false)
    protected Usuario usuarioModificacion;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_baja_id")
    protected Usuario usuarioBaja;

    @PrePersist
    protected void completarAlta() {
        LocalDateTime ahora = LocalDateTime.now();
        Usuario actual = UsuarioActual.get();
        this.fechaAlta = ahora;
        this.fechaModificacion = ahora;
        this.usuarioCarga = actual;
        this.usuarioModificacion = actual;
    }
}
