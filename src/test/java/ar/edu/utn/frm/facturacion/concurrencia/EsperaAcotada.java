package ar.edu.utn.frm.facturacion.concurrencia;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Corre tareas en hilos reales que arrancan juntos (compuerta de largada) y espera cada resultado con un tiempo
 * máximo: un bloqueo que no se libera hace fallar la prueba en lugar de colgar la ejecución.
 */
public final class EsperaAcotada {

    /** Resultado de un hilo: el valor devuelto o la excepción con la que terminó. */
    public record Resultado<T>(String hilo, T valor, Throwable error) {
        public boolean exitoso() {
            return error == null;
        }
    }

    private EsperaAcotada() {
    }

    public static <T> List<Resultado<T>> correrJuntos(List<Callable<T>> tareas, long maximoSegundos) {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < tareas.size(); i++) {
                Callable<T> tarea = tareas.get(i);
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarea.call();
                }));
            }
            largada.countDown();
            List<Resultado<T>> resultados = new ArrayList<>();
            for (int i = 0; i < futuros.size(); i++) {
                String nombre = "hilo-" + (i + 1);
                try {
                    resultados.add(new Resultado<>(nombre, futuros.get(i).get(maximoSegundos, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.TimeoutException e) {
                    throw new AssertionError("El " + nombre + " no termino dentro de " + maximoSegundos
                            + " s (bloqueo no liberado?)", e);
                } catch (ExecutionException e) {
                    resultados.add(new Resultado<>(nombre, null, e.getCause()));
                }
            }
            return resultados;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("La espera de los hilos fue interrumpida", e);
        } finally {
            pool.shutdownNow();
        }
    }
}
