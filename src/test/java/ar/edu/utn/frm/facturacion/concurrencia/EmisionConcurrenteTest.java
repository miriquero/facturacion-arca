package ar.edu.utn.frm.facturacion.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.dto.CambioEstadoRequestDTO;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/** Emisiones simultaneas con hilos reales (spec concurrencia-facturas). */
class EmisionConcurrenteTest extends ConcurrenciaBase {

    private Callable<Long> emitir(Long facturaId) {
        return comoSistema(() -> facturaService.cambiarEstado(facturaId, new CambioEstadoRequestDTO("EMITIDA", null))
                .getNumeroFactura());
    }

    @Test
    void ochoEmisionesSimultaneasDelMismoPuntoDeVentaNumeranDelUnoAlOcho() {
        List<Callable<Long>> tareas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tareas.add(emitir(crearBorrador(puntoA)));
        }

        var resultados = EsperaAcotada.correrJuntos(tareas, MAXIMO_SEGUNDOS);

        assertThat(resultados).allSatisfy(r -> assertThat(r.error()).as(r.hilo()).isNull());
        assertThat(resultados).extracting(EsperaAcotada.Resultado::valor)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(numerosEmitidos(puntoA)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    @Test
    void emisionesSimultaneasDePuntosDeVentaDistintosNumeranCadaUnoDesdeUno() {
        List<Callable<Long>> tareas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tareas.add(emitir(crearBorrador(puntoA)));
            tareas.add(emitir(crearBorrador(puntoB)));
        }

        var resultados = EsperaAcotada.correrJuntos(tareas, MAXIMO_SEGUNDOS);

        assertThat(resultados).allSatisfy(r -> assertThat(r.error()).as(r.hilo()).isNull());
        assertThat(numerosEmitidos(puntoA)).containsExactly(1L, 2L, 3L, 4L);
        assertThat(numerosEmitidos(puntoB)).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void dosEmisionesDeLaMismaFacturaDanUnExitoYUnConflicto() {
        Long id = crearBorrador(puntoA);

        var resultados = EsperaAcotada.correrJuntos(List.of(emitir(id), emitir(id)), MAXIMO_SEGUNDOS);

        assertThat(resultados.stream().filter(EsperaAcotada.Resultado::exitoso)).hasSize(1);
        var fallidos = resultados.stream().filter(r -> !r.exitoso()).toList();
        assertThat(fallidos).hasSize(1);
        assertThat(fallidos.get(0).error()).isInstanceOf(ReglaNegocioException.class);
        assertThat(numerosEmitidos(puntoA)).containsExactly(1L);
        assertThat(jdbc.queryForObject("select count(cae) from factura_venta where id = ?", Long.class, id))
                .isEqualTo(1L);
    }
}
