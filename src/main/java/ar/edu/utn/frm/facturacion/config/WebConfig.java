package ar.edu.utn.frm.facturacion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración web: registra el interceptor de {@code X-Usuario} para las rutas {@code /api/**} y las
 * propiedades de negocio {@code facturacion.*}. Cada petición a la API fija el usuario de auditoría al empezar
 * y lo limpia al terminar. El encabezado declara quién opera; no autentica.
 */
@Configuration
@EnableConfigurationProperties(FacturacionProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final UsuarioActualInterceptor usuarioActualInterceptor;

    public WebConfig(UsuarioActualInterceptor usuarioActualInterceptor) {
        this.usuarioActualInterceptor = usuarioActualInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(usuarioActualInterceptor).addPathPatterns("/api/**");
    }
}
