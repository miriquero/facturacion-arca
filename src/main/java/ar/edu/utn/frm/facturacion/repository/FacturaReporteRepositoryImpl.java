package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta del reporte como proyección directa a {@link FacturaReporteDTO}: Hibernate arma cada fila con el
 * constructor del DTO, por posición y tipo, sin cargar entidades en el contexto de persistencia.
 */
public class FacturaReporteRepositoryImpl implements FacturaReporteRepository {

    /** Selección y joins. Un futuro filtro (WHERE con parámetros nombrados) se inserta entre las dos constantes. */
    private static final String SELECCION = """
            SELECT new ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO(
                f.id, f.numero, f.fechaEmision, COALESCE(c.denominacion, 'Consumidor Final'),
                ci.denominacion, pv.descripcion, f.importeTotal, COUNT(d), f.estado, m.codigoAfip)
            FROM FacturaVenta f
                LEFT JOIN f.cliente c
                LEFT JOIN f.detalles d
                JOIN f.condicionIva ci
                JOIN f.puntoVenta pv
                JOIN f.tipoMoneda m
            """;

    /** Agrupa todas las columnas no agregadas (portable entre H2 y PostgreSQL) y fija el orden. */
    private static final String AGRUPACION_Y_ORDEN = """
            GROUP BY f.id, f.numero, f.fechaEmision, c.denominacion, ci.denominacion,
                     pv.descripcion, f.importeTotal, f.estado, m.codigoAfip
            ORDER BY f.fechaEmision DESC, f.id DESC
            """;

    private final EntityManager entityManager;

    public FacturaReporteRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarReporte() {
        return entityManager.createQuery(SELECCION + AGRUPACION_Y_ORDEN, FacturaReporteDTO.class).getResultList();
    }
}
