package ar.edu.utn.frm.facturacion.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Configuración local del repositorio, con menor prioridad que el entorno y los argumentos. */
public final class ConfiguracionArcaLocal {
    private ConfiguracionArcaLocal() {}

    public static Map<String, Object> cargar() {
        Path archivo = Path.of("arca.local.json").toAbsolutePath();
        if (Files.notExists(archivo)) {
            return Map.of();
        }
        try (var entrada = Files.newInputStream(archivo)) {
            JsonNode datos = new ObjectMapper().readTree(entrada);
            if (datos == null || !datos.isObject()) {
                throw new IllegalStateException("arca.local.json debe contener un objeto JSON.");
            }
            return Map.of(
                    "spring.profiles.default", "homologacion",
                    "ARCA_CUIT", valor(datos, "cuit"),
                    "ARCA_PUNTO_VENTA", valor(datos, "puntoVenta"),
                    "ARCA_PUNTOS_ADICIONALES", datos.path("puntosAdicionales").asText(""),
                    "ARCA_CLAVE_PRIVADA", archivo.resolveSibling(valor(datos, "clavePrivada")).normalize().toString(),
                    "ARCA_CERTIFICADO", archivo.resolveSibling(valor(datos, "certificado")).normalize().toString());
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo leer arca.local.json.");
        }
    }

    private static String valor(JsonNode datos, String campo) {
        String valor = datos.path(campo).asText("").strip();
        if (valor.isEmpty()) {
            throw new IllegalStateException("Falta " + campo + " en arca.local.json.");
        }
        return valor;
    }
}
