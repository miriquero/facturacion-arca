package ar.edu.utn.frm.facturacion.config;

import ar.edu.utn.frm.facturacion.service.GeneradorCae;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Sustituto disponible únicamente en el classpath de las pruebas. */
@Configuration
@Profile("(carga-prueba | test) & !homologacion")
class CaeDePruebaConfig {
    @Bean
    GeneradorCae generadorCae() {
        return () -> "00000000000000";
    }
}
