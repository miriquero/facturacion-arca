package ar.edu.utn.frm.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cálculo de importes (spec calculo-importes-factura). Los esperados son literales de la spec. */
class CalculadoraImportesTest {

    private final CalculadoraImportes calculadora = new CalculadoraImportes();

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    private ImportesRenglon calcular(String cantidad, String precio, String bonificacion, String alicuota) {
        return calculadora.calcularRenglon(
                d(cantidad), d(precio), bonificacion == null ? null : d(bonificacion), d(alicuota));
    }

    private static void assertImportes(ImportesRenglon r, String neto, String iva, String subtotal) {
        assertThat(r.neto()).isEqualByComparingTo(neto);
        assertThat(r.iva()).isEqualByComparingTo(iva);
        assertThat(r.subtotal()).isEqualByComparingTo(subtotal);
        // escala 2 exacta: los importes salen ya redondeados a centavos
        assertThat(r.neto().scale()).isEqualTo(2);
        assertThat(r.iva().scale()).isEqualTo(2);
        assertThat(r.subtotal().scale()).isEqualTo(2);
    }

    @Test
    void conBonificacion() {
        assertImportes(calcular("2", "3000.00", "10", "21"), "5400.00", "1134.00", "6534.00");
    }

    @Test
    void elOrdenCambiaLosCentavos() {
        // neto sin redondear 100.115; IVA 21.02 (con el neto ya redondeado daría 21.03)
        assertImportes(calcular("5", "20.023", "0", "21"), "100.12", "21.02", "121.14");
    }

    @Test
    void medioCentavoHaciaArribaDistingueHalfUpDeHalfEven() {
        // neto sin redondear 100.005; HALF_UP -> 100.01 (HALF_EVEN daría 100.00)
        assertImportes(calcular("3", "33.335", "0", "21"), "100.01", "21.00", "121.01");
    }

    @Test
    void alicuotaPropiaDiezConCinco() {
        assertImportes(calcular("1", "8000.00", "0", "10.5"), "8000.00", "840.00", "8840.00");
    }

    @Test
    void alicuotaPorDefectoVeintiuno() {
        assertImportes(calcular("2", "3000.00", null, "21"), "6000.00", "1260.00", "7260.00");
    }

    @Test
    void bonificacionCienDejaTodoEnCero() {
        assertImportes(calcular("4", "125.50", "100", "21"), "0.00", "0.00", "0.00");
    }

    @Test
    void bonificacionNulaEsCero() {
        assertImportes(calcular("1", "3000.00", null, "21"), "3000.00", "630.00", "3630.00");
    }

    /** Valores límite (caracterización: pasaron al escribirse; ver mutaciones en docs/etapa-4.md). */
    @ParameterizedTest(name = "{0} x {1} bonif {2}% alicuota {3}% -> {4} / {5} / {6}")
    @CsvSource({
        "1,0.01,0,21,0.01,0.00,0.01",
        "1,0.005,0,21,0.01,0.00,0.01",
        "0.5,3000.00,0,21,1500.00,315.00,1815.00",
        "1,0.05,0,10.5,0.05,0.01,0.06",
        "1,100.00,0,0,100.00,0.00,100.00",
        "1,100.00,33.33,21,66.67,14.00,80.67"
    })
    void valoresLimite(
            String cantidad, String precio, String bonif, String alicuota, String neto, String iva, String sub) {
        assertImportes(calcular(cantidad, precio, bonif, alicuota), neto, iva, sub);
    }

    @Test
    void totalEsLaSumaDeLosSubtotales() {
        assertThat(calculadora.sumar(List.of(d("3630.00"), d("19360.00")))).isEqualByComparingTo("22990.00");
    }

    @Test
    void saldoEsTotalMenosCobrado() {
        BigDecimal total = calculadora.sumar(List.of(d("3630.00"), d("19360.00")));
        assertThat(calculadora.saldo(total, BigDecimal.ZERO)).isEqualByComparingTo("22990.00");
        assertThat(calculadora.saldo(total, d("1000.00"))).isEqualByComparingTo("21990.00");
    }

    @Test
    void listaVaciaSumaCero() {
        assertThat(calculadora.sumar(List.of())).isEqualByComparingTo("0.00");
    }
}
