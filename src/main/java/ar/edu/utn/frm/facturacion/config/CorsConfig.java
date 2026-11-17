package ar.edu.utn.frm.facturacion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS de las rutas {@code /api/**}. Es una política que aplica el navegador: no autentica ni identifica a nadie
 * (un {@code curl} o un script la ignoran). Solo lectura en esta etapa: únicamente {@code GET}.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties propiedades;

    public CorsConfig(CorsProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/**")
                .allowedOrigins(propiedades.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET")
                .exposedHeaders("Content-Disposition");
    }
}
