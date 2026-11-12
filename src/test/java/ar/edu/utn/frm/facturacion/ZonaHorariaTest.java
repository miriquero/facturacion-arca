package ar.edu.utn.frm.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class ZonaHorariaTest {

    @Test
    void laJvmDePruebasUsaLaZonaHorariaDeBuenosAires() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @Test
    void laZonaPorDefectoTieneElOffsetDeArgentinaTodoElAnio() {
        TimeZone zona = TimeZone.getDefault();

        assertThat(zona.getRawOffset()).isEqualTo(-3 * 60 * 60 * 1000);
        assertThat(zona.useDaylightTime()).isFalse();
    }
}
