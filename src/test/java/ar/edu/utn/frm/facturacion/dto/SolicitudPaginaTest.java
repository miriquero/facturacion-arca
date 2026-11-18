package ar.edu.utn.frm.facturacion.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SolicitudPaginaTest {

    @Test
    void sinParametrosUsaPaginaCeroYTamanioDiez() {
        SolicitudPagina solicitud = SolicitudPagina.de(null, null);

        assertThat(solicitud.pagina()).isZero();
        assertThat(solicitud.tamanio()).isEqualTo(10);
    }

    @Test
    void valoresValidosSeConservan() {
        SolicitudPagina solicitud = SolicitudPagina.de(3, 25);

        assertThat(solicitud.pagina()).isEqualTo(3);
        assertThat(solicitud.tamanio()).isEqualTo(25);
    }

    @Test
    void paginaNegativaSeRechazaNombrandoElParametro() {
        assertThatThrownBy(() -> SolicitudPagina.de(-1, null))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("pagina");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -5})
    void tamanioFueraDeRangoSeRechazaNombrandoElParametroYElMaximo(int tamanio) {
        assertThatThrownBy(() -> SolicitudPagina.de(null, tamanio))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("tamanio")
                .hasMessageContaining("100");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void tamanioEnLosExtremosEsValido(int tamanio) {
        assertThat(SolicitudPagina.de(0, tamanio).tamanio()).isEqualTo(tamanio);
    }

    @Test
    void desplazamientoSeCalculaEnLongSinDesbordar() {
        assertThat(SolicitudPagina.de(2, 10).desplazamiento()).isEqualTo(20L);
        assertThat(SolicitudPagina.de(Integer.MAX_VALUE, 100).desplazamiento()).isEqualTo(214748364700L);
    }
}
