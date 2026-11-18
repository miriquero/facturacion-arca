package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import java.util.List;

/** Fragmento de repositorio con las consultas de listado y reporte de facturas. */
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

    /**
     * Una página del mismo resultado que {@link #buscarReporte(FiltroFacturas)}: mismas filas y mismo orden total
     * ({@code fechaEmision DESC, id DESC}), de modo que las páginas no se solapan.
     *
     * @param desplazamiento filas a saltear (en {@code long}: {@code pagina * tamanio} puede superar un int)
     * @param cantidad       filas como máximo
     */
    List<FacturaReporteDTO> buscarPagina(FiltroFacturas filtro, long desplazamiento, int cantidad);

    /** Cantidad de facturas que cumplen los filtros (una por factura, sin importar cuántos renglones tenga). */
    long contar(FiltroFacturas filtro);
}
