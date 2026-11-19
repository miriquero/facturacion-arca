package ar.edu.utn.frm.facturacion.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades de negocio de la aplicación ({@code facturacion.*}): alícuota de IVA por defecto, usuario de
 * auditoría por defecto y monedas. Los orígenes CORS viven aparte, en {@link CorsProperties}.
 *
 * <p>Los precios de lista están en la moneda base ({@code moneda-base}, pesos). {@code cotizaciones} dice
 * cuántos pesos vale una unidad de cada moneda (por ejemplo {@code DOL: 1550}: un dólar vale 1.550 pesos).
 * Una moneda sin cotización configurada no se convierte.
 */
@ConfigurationProperties("facturacion")
public record FacturacionProperties(
        @DefaultValue("21") BigDecimal ivaPorDefecto,
        @DefaultValue("sistema") String usuarioPorDefecto,
        @DefaultValue("PES") String monedaBase,
        Map<String, BigDecimal> cotizaciones) {

    private static final Map<String, BigDecimal> POR_DEFECTO = Map.of(
            "PES", BigDecimal.ONE,
            "DOL", new BigDecimal("1550"));

    public FacturacionProperties {
        monedaBase = monedaBase == null || monedaBase.isBlank() ? "PES" : monedaBase.strip().toUpperCase(Locale.ROOT);
        Map<String, BigDecimal> todas = new HashMap<>(POR_DEFECTO);
        if (cotizaciones != null) {
            cotizaciones.forEach((codigo, valor) -> todas.put(codigo.strip().toUpperCase(Locale.ROOT), valor));
        }
        todas.forEach((codigo, valor) -> {
            if (valor == null || valor.signum() <= 0) {
                throw new IllegalArgumentException("La cotización de " + codigo + " debe ser mayor que 0");
            }
        });
        cotizaciones = Map.copyOf(todas);
    }

    /** Pesos que vale una unidad de la moneda; 1 si no hay cotización configurada (no se convierte). */
    public BigDecimal cotizacionDe(String codigoMoneda) {
        return codigoMoneda == null ? BigDecimal.ONE
                : cotizaciones.getOrDefault(codigoMoneda.strip().toUpperCase(Locale.ROOT), BigDecimal.ONE);
    }
}
