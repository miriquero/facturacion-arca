package ar.edu.utn.frm.facturacion.report;

import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.emitida;
import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.factura;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TotalesPorMonedaTest {

    @Test
    void estadosMezclados_sumaSoloLasEmitidas() {
        Map<String, BigDecimal> totales = TotalesPorMoneda.calcular(List.of(
                emitida("PES", 100.10),
                emitida("PES", 200.20),
                factura(EstadoFactura.ANULADA, "PES", 50.00),
                factura(EstadoFactura.BORRADOR, "PES", 70.00)));

        assertThat(totales).containsOnlyKeys("PES");
        assertThat(totales.get("PES")).isEqualByComparingTo("300.30");
    }

    @Test
    void dosMonedas_totalSeparadoPorCodigoSinCombinado() {
        Map<String, BigDecimal> totales = TotalesPorMoneda.calcular(List.of(
                emitida("PES", 1000.00), emitida("DOL", 10.50)));

        assertThat(totales).containsOnlyKeys("DOL", "PES");
        assertThat(totales.get("PES")).isEqualByComparingTo("1000.00");
        assertThat(totales.get("DOL")).isEqualByComparingTo("10.50");
    }

    @Test
    void monedaSinEmitidas_tieneTotalCero() {
        Map<String, BigDecimal> totales = TotalesPorMoneda.calcular(List.of(
                emitida("PES", 100.00), factura(EstadoFactura.BORRADOR, "DOL", 40.00)));

        assertThat(totales.get("PES")).isEqualByComparingTo("100.00");
        assertThat(totales.get("DOL")).isEqualByComparingTo("0.00");
        assertThat(totales.get("DOL").scale()).isEqualTo(2);
    }

    @Test
    void sumaDecimalExacta_ceroDiezCeroVeinteCeroSetentaDanUnoExacto() {
        Map<String, BigDecimal> totales = TotalesPorMoneda.calcular(List.of(
                emitida("PES", 0.10), emitida("PES", 0.20), emitida("PES", 0.70)));

        assertThat(totales.get("PES")).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    void sumaDecimalExacta_diezCentavosYVeinteCentavosDanTreintaExacto() {
        Map<String, BigDecimal> totales = TotalesPorMoneda.calcular(List.of(
                emitida("PES", 0.10), emitida("PES", 0.20)));

        assertThat(totales.get("PES")).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void listaVacia_devuelveMapaVacio() {
        assertThat(TotalesPorMoneda.calcular(List.of())).isEmpty();
    }

    @Test
    void ordenaLosCodigosAlfabeticamente() {
        Map<String, BigDecimal> totales = TotalesPorMoneda.calcular(List.of(
                emitida("PES", 1.00), emitida("DOL", 2.00), emitida("EUR", 3.00)));

        assertThat(totales.keySet()).containsExactly("DOL", "EUR", "PES");
    }
}
