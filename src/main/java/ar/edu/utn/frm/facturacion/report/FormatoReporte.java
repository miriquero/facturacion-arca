package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import java.util.Locale;
import org.springframework.http.MediaType;

/** Formatos de descarga del reporte. */
public enum FormatoReporte {
    PDF(MediaType.APPLICATION_PDF, "pdf"),
    XLSX(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "xlsx"),
    TXT(MediaType.parseMediaType("text/plain;charset=UTF-8"), "txt");

    private final MediaType tipoContenido;
    private final String extension;

    FormatoReporte(MediaType tipoContenido, String extension) {
        this.tipoContenido = tipoContenido;
        this.extension = extension;
    }

    public MediaType tipoContenido() {
        return tipoContenido;
    }

    public String extension() {
        return extension;
    }

    public String nombreArchivo() {
        return "facturas." + extension;
    }

    /**
     * Interpreta el parámetro {@code formato}: sin distinguir mayúsculas ni espacios exteriores; {@code excel} es
     * alias de {@code xlsx} y {@code tsv} de {@code txt}.
     *
     * @throws ParametroInvalidoException si el valor no es ninguno de los admitidos
     */
    public static FormatoReporte desde(String texto) {
        String normalizado = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        return switch (normalizado) {
            case "pdf" -> PDF;
            case "xlsx", "excel" -> XLSX;
            case "txt", "tsv" -> TXT;
            default -> throw new ParametroInvalidoException("El formato '" + (texto == null ? "" : texto.strip())
                    + "' no es válido. Formatos admitidos: pdf, xlsx, excel, txt, tsv.");
        };
    }
}
