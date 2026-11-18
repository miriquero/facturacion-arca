package ar.edu.utn.frm.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Superclase de las entidades auditables. No genera tabla: sus columnas aparecen en cada tabla hija.
 * El alta se completa en {@link #completarAlta()} con el usuario de {@link UsuarioActual}; si el
 * contexto está vacío los usuarios quedan nulos y la base rechaza el INSERT (NOT NULL). La modificación se completa en
 * {@link #completarModificacion()} (o {@link #registrarModificacion()}) y la baja lógica en {@link #darDeBaja()}.
 */
@Getter
@ToString(callSuper = true)
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

    /**
     * Antes de cada UPDATE: fecha de modificación actual y usuario de modificación tomado del contexto. No toca
     * la fecha de alta ni el usuario de carga. Solo se dispara si Hibernate detecta cambios en la entidad; ver
     * {@link #registrarModificacion()}.
     */
    @PreUpdate
    protected void completarModificacion() {
        this.fechaModificacion = LocalDateTime.now();
        // Política estricta: con el contexto vacío el usuario queda nulo y la base rechaza el UPDATE (NOT NULL),
        // igual que un alta. Atribuir la modificación en silencio a otro usuario sería peor que fallar.
        this.usuarioModificacion = UsuarioActual.get();
    }

    /**
     * Registra una modificación explícita: actualiza fecha y usuario de modificación y, al cambiar la fecha,
     * deja la entidad sucia para que Hibernate emita el UPDATE (y dispare {@link #completarModificacion()}).
     * Hace falta cuando solo cambian entidades hijas (p. ej. los renglones de una factura): sin este llamado la
     * cabecera no tiene cambios propios y su auditoría quedaría desactualizada.
     */
    public void registrarModificacion() {
        completarModificacion();
    }

    /**
     * Baja lógica: registra la fecha y hora de baja y el usuario de baja tomado del contexto. La fila no se
     * elimina; mientras no se llame, ambos datos permanecen nulos.
     */
    public void darDeBaja() {
        this.fechaBaja = LocalDateTime.now();
        this.usuarioBaja = UsuarioActual.get();
    }
}
