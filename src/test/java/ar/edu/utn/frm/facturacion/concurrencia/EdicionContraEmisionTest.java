package ar.edu.utn.frm.facturacion.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.dto.CambioEstadoRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Una edicion o una baja que llega mientras otra transaccion tiene bloqueada la factura debe ESPERAR, y al liberarse
 * ver el estado ya cambiado (EMITIDA) y responder 409 (spec concurrencia-facturas). El bloqueo se retiene con un
 * {@code TransactionTemplate} y una compuerta, sin dormir "a ojo" mas de lo necesario.
 */
class EdicionContraEmisionTest extends ConcurrenciaBase {

    private static final long ESPERA_DE_BLOQUEO_MS = 500;

    private ExecutorService pool;
    private Long facturaId;
    private CountDownLatch bloqueada;
    private CountDownLatch liberar;
    private Future<?> retenedor;

    @BeforeEach
    void prepararPool() {
        pool = Executors.newFixedThreadPool(2);
        bloqueada = new CountDownLatch(1);
        liberar = new CountDownLatch(1);
    }

    @AfterEach
    void cerrarPool() {
        liberar.countDown();
        pool.shutdownNow();
    }

    /** Hilo A: bloquea la factura, avisa, espera la orden, la emite y confirma. */
    private void retenerElBloqueoYEmitir() {
        retenedor = pool.submit(() -> {
            UsuarioActual.set(sistema);
            try {
                plantilla().executeWithoutResult(estado -> {
                    facturaRepository.buscarParaActualizar(facturaId);
                    bloqueada.countDown();
                    esperar(liberar);
                    facturaService.cambiarEstado(facturaId, new CambioEstadoRequestDTO("EMITIDA", null));
                });
            } finally {
                UsuarioActual.clear();
            }
        });
        esperar(bloqueada);
    }

    private static void esperar(CountDownLatch compuerta) {
        try {
            if (!compuerta.await(MAXIMO_SEGUNDOS, TimeUnit.SECONDS)) {
                throw new AssertionError("La compuerta no se abrio dentro de " + MAXIMO_SEGUNDOS + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Hilo interrumpido", e);
        }
    }

    private FacturaRequestDTO edicionValida() {
        return new FacturaRequestDTO(null, condicion.getId(), puntoA.getId(), moneda.getId(), null, "editada",
                List.of(new FacturaDetalleRequestDTO(precio.getId(), new BigDecimal("5"), null, null)));
    }

    private void assertEsperaYLuegoConflicto(Future<?> operacion) throws Exception {
        Thread.sleep(ESPERA_DE_BLOQUEO_MS);
        assertThat(operacion.isDone())
                .as("la operacion deberia seguir esperando el bloqueo de la factura").isFalse();

        liberar.countDown();
        retenedor.get(MAXIMO_SEGUNDOS, TimeUnit.SECONDS);

        assertThatThrownBy(() -> operacion.get(MAXIMO_SEGUNDOS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ReglaNegocioException.class);
    }

    private void assertEmitidaIntacta() {
        var fila = jdbc.queryForMap("select estado, numero, observaciones from factura_venta where id = ?", facturaId);
        assertThat(fila.get("estado")).isEqualTo("EMITIDA");
        assertThat(((Number) fila.get("numero")).longValue()).isEqualTo(1L);
        assertThat(fila.get("observaciones")).isNull();
        assertThat(jdbc.queryForList("select cantidad from factura_venta_detalle where factura_id = ?", Double.class,
                facturaId)).containsExactly(1.0);
    }

    @Test
    void unaEdicionEsperaAUnaEmisionConcurrenteYLuegoRecibeConflicto() throws Exception {
        facturaId = crearBorrador(puntoA);
        retenerElBloqueoYEmitir();

        Future<?> edicion = pool.submit(comoSistema(() -> facturaService.actualizar(facturaId, edicionValida())));

        assertEsperaYLuegoConflicto(edicion);
        assertEmitidaIntacta();
    }

    @Test
    void unaEliminacionEsperaAUnaEmisionConcurrenteYLuegoRecibeConflicto() throws Exception {
        facturaId = crearBorrador(puntoA);
        retenerElBloqueoYEmitir();

        Future<?> baja = pool.submit(comoSistema(() -> {
            facturaService.eliminar(facturaId);
            return null;
        }));

        assertEsperaYLuegoConflicto(baja);
        assertEmitidaIntacta();
    }
}
