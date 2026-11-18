package ar.edu.utn.frm.facturacion.service;

import java.util.random.RandomGenerator;

/**
 * CAE ficticio de 14 dígitos: un número aleatorio menor que 10^14, con ceros a la izquierda. La fuente
 * aleatoria se inyecta: en producción es un {@code SecureRandom} y en las pruebas una fuente fija o con semilla,
 * así el CAE es determinista.
 */
public class GeneradorCaeAleatorio implements GeneradorCae {

    private static final long TOPE_EXCLUSIVO = 100_000_000_000_000L;

    private final RandomGenerator fuente;

    public GeneradorCaeAleatorio(RandomGenerator fuente) {
        this.fuente = fuente;
    }

    @Override
    public String generar() {
        return String.format("%014d", fuente.nextLong(TOPE_EXCLUSIVO));
    }
}
