package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import java.util.List;

/** Fragmento de repositorio con la consulta del reporte de facturas. */
public interface FacturaReporteRepository {

    /** Todas las facturas como filas de reporte, de la más nueva a la más vieja (sin filtros). */
    default List<FacturaReporteDTO> buscarReporte() {
        return buscarReporte(FiltroFacturas.SIN_FILTROS);
    }

    /**
     * Facturas que cumplen todos los filtros presentes (límites inclusivos), de la más nueva a la más vieja.
     * El filtro llega ya validado por {@link FiltroFacturas#desde}.
     */
    List<FacturaReporteDTO> buscarReporte(FiltroFacturas filtro);
}
