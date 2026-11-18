package ar.edu.utn.frm.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.exception.ImporteNoRepresentableException;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Control de ida y vuelta BigDecimal -> double -> BigDecimal (spec calculo-importes-factura). */
class ImporteDoubleTest {

    @Test
    void importeRepresentableSeGuardaIgual() {
        assertThat(ImporteDouble.aDouble(new BigDecimal("7260.00"), "total")).isEqualTo(7260.0);
    }

    @ParameterizedTest
    @CsvSource({"0.00,0.0", "100.12,100.12", "121.01,121.01", "22990.00,22990.0", "0.5,0.5"})
    void otrosImportesRepresentables(String decimal, double esperado) {
        assertThat(ImporteDouble.aDouble(new BigDecimal(decimal), "subtotal")).isEqualTo(esperado);
    }

    @Test
    void importeConMasDigitosDeLosQueCabenSeRechaza() {
        BigDecimal iva = new BigDecimal("259259256925925.93");

        assertThatThrownBy(() -> ImporteDouble.aDouble(iva, "iva"))
                .isInstanceOf(ImporteNoRepresentableException.class)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("iva")
                .hasMessageContaining("259259256925925.93")
                .satisfies(e -> {
                    ImporteNoRepresentableException ex = (ImporteNoRepresentableException) e;
                    assertThat(ex.getCampo()).isEqualTo("iva");
                    assertThat(ex.getValor()).isEqualByComparingTo("259259256925925.93");
                });
    }

    @Test
    void valorNoFinitoSeRechaza() {
        BigDecimal desbordado = new BigDecimal("1e400");

        assertThatThrownBy(() -> ImporteDouble.aDouble(desbordado, "precio"))
                .isInstanceOf(ImporteNoRepresentableException.class)
                .hasMessageContaining("precio");
    }
}
