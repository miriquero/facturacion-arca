package ar.edu.utn.frm.facturacion.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/** Prueba del propio utilitario de espera acotada (spec concurrencia-facturas: bloqueo no liberado). */
class EsperaAcotadaTest {

    @Test
    void unHiloQueNoTerminaDentroDelMaximoHaceFallarLaPruebaNombrandoElHilo() {
        Callable<String> rapido = () -> "listo";
        Callable<String> colgado = () -> {
            Thread.sleep(10_000);
            return "tarde";
        };

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> EsperaAcotada.correrJuntos(List.of(rapido, colgado), 1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("hilo-2")
                .hasMessageContaining("1 s");
        long segundos = (System.nanoTime() - inicio) / 1_000_000_000L;

        assertThat(segundos).as("la prueba falla por el tiempo maximo, no espera al hilo colgado").isLessThan(8);
    }

    @Test
    void sinBloqueosDevuelveElValorOLaExcepcionDeCadaHilo() {
        Callable<String> bueno = () -> "ok";
        Callable<String> malo = () -> {
            throw new IllegalStateException("fallo del hilo");
        };

        var resultados = EsperaAcotada.correrJuntos(List.of(bueno, malo), 5);

        assertThat(resultados.get(0).exitoso()).isTrue();
        assertThat(resultados.get(0).valor()).isEqualTo("ok");
        assertThat(resultados.get(1).exitoso()).isFalse();
        assertThat(resultados.get(1).error()).isInstanceOf(IllegalStateException.class);
    }
}
