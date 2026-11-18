package ar.edu.utn.frm.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PatronBusquedaTest {

    @Test
    void textoConEspaciosExterioresSeRecortaYSeEnvuelveEnComodines() {
        assertThat(PatronBusqueda.de(" juan ")).contains("%juan%");
    }

    @Test
    void mayusculasPasanAMinusculas() {
        assertThat(PatronBusqueda.de("JuAN")).contains("%juan%");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"%|%\\%%", "a_b|%a\\_b%", "a\\b|%a\\\\b%"})
    void loComodinesYLaBarraSeEscapanComoTextoLiteral(String entrada, String esperado) {
        assertThat(PatronBusqueda.de(entrada)).contains(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void enBlancoEsAusente(String entrada) {
        assertThat(PatronBusqueda.de(entrada)).isEmpty();
    }

    @Test
    void nuloEsAusente() {
        assertThat(PatronBusqueda.de(null)).isEmpty();
    }

    @Test
    void cienCaracteresSonValidos() {
        assertThat(PatronBusqueda.de("a".repeat(100))).contains("%" + "a".repeat(100) + "%");
    }

    @Test
    void cientoUnCaracteresSeRechazanNombrandoQYElMaximo() {
        assertThatThrownBy(() -> PatronBusqueda.de("a".repeat(101)))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("q")
                .hasMessageContaining("100");
    }

    @Test
    void losEspaciosExterioresNoCuentanParaElLimite() {
        assertThat(PatronBusqueda.de("  " + "a".repeat(100) + "  ")).isPresent();
    }
}
