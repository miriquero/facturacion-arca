package ar.edu.utn.frm.facturacion.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Orígenes web autorizados a leer la API desde un navegador ({@code facturacion.cors.allowed-origins}). */
@ConfigurationProperties("facturacion.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
