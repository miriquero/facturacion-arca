package ar.edu.utn.frm.facturacion.report;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.BaseFont;
import java.io.IOException;
import java.io.InputStream;

/**
 * Fuente Noto Sans CJK SC incrustada con {@code Identity-H}, leída del classpath la primera vez que se necesita
 * (Excel y TXT no la requieren). La comparten el reporte y el comprobante.
 */
final class FuentePdf {

    static final String RUTA_FUENTE = "fonts/NotoSansCJKsc-Regular.otf";

    private final String rutaFuente;
    private volatile BaseFont fuenteBase;

    FuentePdf(String rutaFuente) {
        this.rutaFuente = rutaFuente;
    }

    BaseFont fuente() {
        BaseFont base = fuenteBase;
        if (base == null) {
            synchronized (this) {
                base = fuenteBase;
                if (base == null) {
                    base = cargarFuente();
                    fuenteBase = base;
                }
            }
        }
        return base;
    }

    private BaseFont cargarFuente() {
        try (InputStream entrada = FuentePdf.class.getClassLoader().getResourceAsStream(rutaFuente)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró la fuente " + rutaFuente
                        + " en el classpath; el PDF la necesita (ver README, Etapa 2)");
            }
            byte[] bytes = entrada.readAllBytes();
            String nombre = rutaFuente.substring(rutaFuente.lastIndexOf('/') + 1);
            return BaseFont.createFont(nombre, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("No se pudo cargar la fuente " + rutaFuente, e);
        }
    }
}
