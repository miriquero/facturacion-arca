package ar.edu.utn.frm.facturacion.config;

import ar.edu.utn.frm.facturacion.service.CalculadoraImportes;
import ar.edu.utn.frm.facturacion.service.GeneradorCae;
import ar.edu.utn.frm.facturacion.service.GeneradorCaeAleatorio;
import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans de negocio sin estado: la calculadora de importes es una clase pura que se registra acá. */
@Configuration
public class NegocioConfig {

    @Bean
    public CalculadoraImportes calculadoraImportes() {
        return new CalculadoraImportes();
    }

    /** CAE ficticio con una fuente aleatoria real; las pruebas lo reemplazan por uno fijo. */
    @Bean
    public GeneradorCae generadorCae() {
        return new GeneradorCaeAleatorio(new SecureRandom());
    }
}
