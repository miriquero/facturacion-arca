package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta del reporte como proyección directa a {@link FacturaReporteDTO}: Hibernate arma cada fila con el
 * constructor del DTO, por posición y tipo, sin cargar entidades en el contexto de persistencia.
 */
public class FacturaReporteRepositoryImpl implements FacturaReporteRepository {

    /** Selección y joins. El WHERE con parámetros nombrados se inserta entre las dos constantes. */
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

    /** Conteo de facturas: una fila por factura porque no une renglones. */
    private static final String CONTEO = """
            SELECT COUNT(f)
            FROM FacturaVenta f
                LEFT JOIN f.cliente c
            """;

    private final EntityManager entityManager;

    public FacturaReporteRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarReporte(FiltroFacturas filtro) {
        TypedQuery<FacturaReporteDTO> consulta = entityManager.createQuery(jpql(filtro), FacturaReporteDTO.class);
        asignarParametros(consulta, filtro);
        return consulta.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> buscarPagina(FiltroFacturas filtro, long desplazamiento, int cantidad) {
        if (desplazamiento > Integer.MAX_VALUE) {
            return List.of();
        }
        TypedQuery<FacturaReporteDTO> consulta = entityManager.createQuery(jpql(filtro), FacturaReporteDTO.class);
        asignarParametros(consulta, filtro);
        consulta.setFirstResult((int) desplazamiento);
        consulta.setMaxResults(cantidad);
        return consulta.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public long contar(FiltroFacturas filtro) {
        TypedQuery<Long> consulta = entityManager.createQuery(jpqlConteo(filtro), Long.class);
        asignarParametros(consulta, filtro);
        return consulta.getSingleResult();
    }

    /**
     * Conteo con el mismo {@code WHERE} que la consulta de la página. No une {@code detalles} ni agrupa: contar sobre
     * la consulta agrupada devolvería un conteo por grupo. Los {@code JOIN} obligatorios no filtran filas y se
     * omiten; el {@code LEFT JOIN} de cliente se conserva porque el filtro {@code c.id} lo usa.
     */
    String jpqlConteo(FiltroFacturas filtro) {
        return CONTEO + clausulaWhere(filtro);
    }

    /**
     * Texto de la consulta para el filtro dado: el {@code WHERE} se compone solo con fragmentos constantes (uno por
     * filtro presente) y marcadores de parámetro con nombre; los valores nunca se concatenan.
     */
    String jpql(FiltroFacturas filtro) {
        return SELECCION + clausulaWhere(filtro) + AGRUPACION_Y_ORDEN;
    }

    /**
     * {@code WHERE} compartido por todas las consultas del repositorio (reporte, página y conteo), de modo que no
     * puedan divergir: un fragmento constante por filtro presente, o cadena vacía si no hay ninguno.
     */
    private static String clausulaWhere(FiltroFacturas filtro) {
        List<String> condiciones = new ArrayList<>();
        if (filtro.desde() != null) {
            condiciones.add("f.fechaEmision >= :fechaDesde");
        }
        if (filtro.hasta() != null) {
            condiciones.add("f.fechaEmision <= :fechaHasta");
        }
        if (filtro.estado() != null) {
            condiciones.add("f.estado = :estado");
        }
        if (filtro.montoMinimo() != null) {
            condiciones.add("f.importeTotal >= :montoMinimo");
        }
        if (filtro.numero() != null) {
            condiciones.add("f.numero = :numero");
        }
        if (filtro.clienteId() != null) {
            condiciones.add("c.id = :clienteId");
        }
        if (filtro.puntoVentaId() != null) {
            condiciones.add("f.puntoVenta.id = :puntoVentaId");
        }
        if (filtro.informada() != null) {
            condiciones.add(filtro.informada() ? "f.cae IS NOT NULL" : "f.cae IS NULL");
        }
        return condiciones.isEmpty() ? "" : "WHERE " + String.join(" AND ", condiciones) + "\n";
    }

    /** Asigna solo los parámetros cuyo fragmento figura en {@link #clausulaWhere}. */
    private static void asignarParametros(TypedQuery<?> consulta, FiltroFacturas filtro) {
        if (filtro.desde() != null) {
            consulta.setParameter("fechaDesde", filtro.desde());
        }
        if (filtro.hasta() != null) {
            consulta.setParameter("fechaHasta", filtro.hasta());
        }
        if (filtro.estado() != null) {
            consulta.setParameter("estado", filtro.estado());
        }
        if (filtro.montoMinimo() != null) {
            consulta.setParameter("montoMinimo", filtro.montoMinimo());
        }
        if (filtro.numero() != null) {
            consulta.setParameter("numero", filtro.numero());
        }
        if (filtro.clienteId() != null) {
            consulta.setParameter("clienteId", filtro.clienteId());
        }
        if (filtro.puntoVentaId() != null) {
            consulta.setParameter("puntoVentaId", filtro.puntoVentaId());
        }
    }
}
