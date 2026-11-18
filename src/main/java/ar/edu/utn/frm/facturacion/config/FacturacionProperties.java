package ar.edu.utn.frm.facturacion.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades de negocio de la aplicación ({@code facturacion.*}): alícuota de IVA por defecto y usuario de
 * auditoría por defecto. Los orígenes CORS viven aparte, en {@link CorsProperties}.
 */
@ConfigurationProperties("facturacion")
public record FacturacionProperties(
        @DefaultValue("21") BigDecimal ivaPorDefecto,
        @DefaultValue("sistema") String usuarioPorDefecto) {
}
