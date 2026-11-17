package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Total facturado por código de moneda: suma solo las facturas EMITIDA, nunca mezcla monedas y usa
 * aritmética decimal ({@link BigDecimal}) con redondeo HALF_UP a dos decimales.
 */
final class TotalesPorMoneda {

    private static final int ESCALA = 2;

    private TotalesPorMoneda() {
    }

    /**
     * Devuelve un total por cada código de moneda presente en la lista (en cualquier estado), ordenado por código.
     * Una moneda sin facturas emitidas queda en 0.00.
     */
    static SortedMap<String, BigDecimal> calcular(List<FacturaReporteDTO> filas) {
        SortedMap<String, BigDecimal> totales = new TreeMap<>();
        for (FacturaReporteDTO fila : filas) {
            BigDecimal acumulado = totales.getOrDefault(fila.getCodigoMoneda(), BigDecimal.ZERO);
            if (EstadoFactura.EMITIDA.name().equals(fila.getEstado())) {
                acumulado = acumulado.add(BigDecimal.valueOf(fila.getImporteTotal()));
            }
            totales.put(fila.getCodigoMoneda(), acumulado);
        }
        totales.replaceAll((moneda, total) -> total.setScale(ESCALA, RoundingMode.HALF_UP));
        return totales;
    }
}
