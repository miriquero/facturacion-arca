package ar.edu.utn.frm.facturacion.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj inyectable en hora de Argentina (DD-12): las pruebas lo reemplazan por uno fijo. */
@Configuration
public class RelojConfig {

    public static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public Clock reloj() {
        return Clock.system(ZONA_ARGENTINA);
    }
}
