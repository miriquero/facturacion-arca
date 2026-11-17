package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import java.util.List;

/** Fragmento de repositorio con la consulta del reporte de facturas. */
public interface FacturaReporteRepository {

    /** Todas las facturas como filas de reporte, de la más nueva a la más vieja. */
    List<FacturaReporteDTO> buscarReporte();
}
