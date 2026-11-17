package ar.edu.utn.frm.facturacion.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Caracterización: la fuente y su licencia OFL viajan juntas en el classpath (y por lo tanto en el JAR). */
class FuenteYLicenciaTest {

    private static byte[] leer(String recurso) throws IOException {
        try (InputStream entrada = FuenteYLicenciaTest.class.getClassLoader().getResourceAsStream(recurso)) {
            assertThat(entrada).as("recurso %s en el classpath", recurso).isNotNull();
            return entrada.readAllBytes();
        }
    }

    @Test
    void laFuenteNotoSansCjkEstaEnElClasspathYEsUnaFuenteOpenType() throws IOException {
        byte[] fuente = leer("fonts/NotoSansCJKsc-Regular.otf");

        assertThat(fuente).hasSize(16_437_364);
        assertThat(new String(fuente, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("OTTO");
    }

    @Test
    void laLicenciaOflViajaJuntoALaFuente() throws IOException {
        String licencia = new String(leer("fonts/OFL.txt"), StandardCharsets.UTF_8);

        assertThat(licencia).contains("SIL OPEN FONT LICENSE Version 1.1");
    }
}
