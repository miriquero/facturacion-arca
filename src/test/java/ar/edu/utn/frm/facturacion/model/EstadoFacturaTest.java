package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.EstadoFactura.ANULADA;
import static ar.edu.utn.frm.facturacion.model.EstadoFactura.BORRADOR;
import static ar.edu.utn.frm.facturacion.model.EstadoFactura.EMITIDA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Máquina de estados (spec ciclo-de-vida-factura): solo BORRADOR a EMITIDA y EMITIDA a ANULADA. */
class EstadoFacturaTest {

    @ParameterizedTest(name = "{0} -> {1} permitido: {2}")
    @CsvSource({
        "BORRADOR, BORRADOR, false",
        "BORRADOR, EMITIDA, true",
        "BORRADOR, ANULADA, false",
        "EMITIDA, BORRADOR, false",
        "EMITIDA, EMITIDA, false",
        "EMITIDA, ANULADA, true",
        "ANULADA, BORRADOR, false",
        "ANULADA, EMITIDA, false",
        "ANULADA, ANULADA, false"
    })
    void soloLasTransicionesDelCicloDeVidaEstanPermitidas(EstadoFactura origen, EstadoFactura destino,
            boolean permitida) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(permitida);
    }

    @org.junit.jupiter.api.Test
    void haySoloDosTransicionesPermitidasEnTotal() {
        long permitidas = java.util.Arrays.stream(EstadoFactura.values())
                .flatMap(o -> java.util.Arrays.stream(EstadoFactura.values()).filter(o::puedePasarA))
                .count();
        assertThat(permitidas).isEqualTo(2);
        assertThat(BORRADOR.puedePasarA(EMITIDA)).isTrue();
        assertThat(EMITIDA.puedePasarA(ANULADA)).isTrue();
    }
}
