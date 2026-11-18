package ar.edu.utn.frm.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** CAE de 14 dígitos con fuente inyectable (spec ciclo-de-vida-factura, IN-09). */
class GeneradorCaeAleatorioTest {

    /** Fuente que devuelve siempre el mismo valor, para fijar el CAE. */
    private static RandomGenerator fija(long valor) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return valor;
            }

            @Override
            public long nextLong(long limite) {
                return valor;
            }
        };
    }

    @Test
    void completaConCerosALaIzquierdaHastaCatorceDigitos() {
        assertThat(new GeneradorCaeAleatorio(fija(42)).generar()).isEqualTo("00000000000042");
    }

    @Test
    void unNumeroDeCatorceDigitosSeConservaTalCual() {
        assertThat(new GeneradorCaeAleatorio(fija(12345678901234L)).generar()).isEqualTo("12345678901234");
    }

    @Test
    void conLaMismaSemillaDosGeneradoresDanLaMismaSecuenciaDeCaesDeCatorceDigitos() {
        GeneradorCaeAleatorio a = new GeneradorCaeAleatorio(new SplittableRandom(7));
        GeneradorCaeAleatorio b = new GeneradorCaeAleatorio(new SplittableRandom(7));

        for (int i = 0; i < 50; i++) {
            String cae = a.generar();
            assertThat(cae).matches("[0-9]{14}").isEqualTo(b.generar());
        }
    }

    @Test
    void elLimiteExcluyeLosCatorceNueves() {
        // La fuente recibe como tope 10^14: el mayor valor posible es 99999999999999 (14 dígitos).
        long[] pedido = new long[1];
        RandomGenerator espia = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public long nextLong(long limite) {
                pedido[0] = limite;
                return limite - 1;
            }
        };

        String cae = new GeneradorCaeAleatorio(espia).generar();

        assertThat(pedido[0]).isEqualTo(100_000_000_000_000L);
        assertThat(cae).isEqualTo("99999999999999");
    }

    @Test
    void conLaFuenteRealCadaCaeTieneCatorceDigitos() {
        GeneradorCaeAleatorio real = new GeneradorCaeAleatorio(new java.security.SecureRandom());

        for (int i = 0; i < 500; i++) {
            assertThat(real.generar()).matches("[0-9]{14}");
        }
    }
}
