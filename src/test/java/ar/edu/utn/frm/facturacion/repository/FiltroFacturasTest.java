package ar.edu.utn.frm.facturacion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FiltroFacturasTest {

    private static FacturaFiltrosDTO conEstado(String estado) {
        return new FacturaFiltrosDTO(null, null, estado, null);
    }

    private static FacturaFiltrosDTO conFechas(LocalDate desde, LocalDate hasta) {
        return new FacturaFiltrosDTO(desde, hasta, null, null);
    }

    @Test
    void estadoConEspaciosYMinusculasSeNormaliza() {
        FiltroFacturas filtro = FiltroFacturas.desde(conEstado(" emitida "));

        assertThat(filtro.estado()).isEqualTo(EstadoFactura.EMITIDA);
    }

    @Test
    void estadoEnMinusculasSeNormaliza() {
        assertThat(FiltroFacturas.desde(conEstado("anulada")).estado()).isEqualTo(EstadoFactura.ANULADA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t"})
    void estadoVacioOSoloEspaciosEsAusente(String estado) {
        assertThat(FiltroFacturas.desde(conEstado(estado)).estado()).isNull();
    }

    @Test
    void estadoDesconocidoSeRechazaNombrandoElValorYLosAdmitidos() {
        assertThatThrownBy(() -> FiltroFacturas.desde(conEstado("PAGADA")))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("PAGADA")
                .hasMessageContaining("BORRADOR")
                .hasMessageContaining("EMITIDA")
                .hasMessageContaining("ANULADA");
    }

    @Test
    void fechasInvertidasSeRechazanNombrandoAmbosParametros() {
        assertThatThrownBy(() -> FiltroFacturas.desde(conFechas(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("fechaDesde")
                .hasMessageContaining("fechaHasta");
    }

    @Test
    void fechasInvertidasPorUnSoloDiaTambienSeRechazan() {
        assertThatThrownBy(() -> FiltroFacturas.desde(conFechas(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 5))))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void fechasIgualesSonValidas() {
        FiltroFacturas filtro = FiltroFacturas.desde(conFechas(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)));

        assertThat(filtro.desde()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(filtro.hasta()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void unaSolaFechaEsValida() {
        FiltroFacturas soloDesde = FiltroFacturas.desde(conFechas(LocalDate.of(2026, 11, 5), null));
        FiltroFacturas soloHasta = FiltroFacturas.desde(conFechas(null, LocalDate.of(2026, 11, 7)));

        assertThat(soloDesde.desde()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(soloDesde.hasta()).isNull();
        assertThat(soloHasta.desde()).isNull();
        assertThat(soloHasta.hasta()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void montoMinimoNoFinitoSeRechaza(double monto) {
        assertThatThrownBy(() -> FiltroFacturas.desde(new FacturaFiltrosDTO(null, null, null, monto)))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("montoMinimo");
    }

    @Test
    void montoMinimoNegativoEsValidoYSeConserva() {
        FiltroFacturas filtro = FiltroFacturas.desde(new FacturaFiltrosDTO(null, null, null, -50.0));

        assertThat(filtro.montoMinimo()).isEqualTo(-50.0);
    }

    @Test
    void montoMinimoCeroSeConserva() {
        assertThat(FiltroFacturas.desde(new FacturaFiltrosDTO(null, null, null, 0.0)).montoMinimo()).isEqualTo(0.0);
    }

    @Test
    void todoNuloEquivaleASinFiltros() {
        assertThat(FiltroFacturas.desde(new FacturaFiltrosDTO(null, null, null, null)))
                .isEqualTo(FiltroFacturas.SIN_FILTROS);
    }
}
