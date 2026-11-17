package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import com.lowagie.text.pdf.BaseFont;
import java.util.List;

/**
 * Comprueba, antes de abrir el documento, que la fuente tenga glifo para cada carácter que se va a imprimir.
 * OpenPDF omite en silencio los caracteres sin glifo, y después de generar ya no se puede saber cuál faltó.
 * Recorre por code point ({@code codePoints()}, no por {@code char}) para no partir pares sustitutos.
 */
final class VerificadorGlifos {

    /** Campo que se informa cuando el carácter problemático está en un texto fijo del documento. */
    private static final int SIN_FILA = 0;
    private static final int SALTO_DE_LINEA = '\n';

    private final BaseFont fuente;

    VerificadorGlifos(BaseFont fuente) {
        this.fuente = fuente;
    }

    /** Verifica los textos fijos y los dinámicos de cada fila; lanza ante el primer carácter sin glifo. */
    void verificar(List<String> textosFijos, List<FacturaReporteDTO> filas) {
        for (String fijo : textosFijos) {
            verificar(fijo, "«" + fijo + "»", SIN_FILA);
        }
        int numeroDeFila = 1;
        for (FacturaReporteDTO fila : filas) {
            verificar(fila.getClienteDenominacion(), "cliente", numeroDeFila);
            verificar(fila.getCondicionIva(), "condición IVA", numeroDeFila);
            verificar(fila.getPuntoVentaDescripcion(), "punto de venta", numeroDeFila);
            verificar(fila.getEstado(), "estado", numeroDeFila);
            verificar(fila.getCodigoMoneda(), "moneda", numeroDeFila);
            numeroDeFila++;
        }
    }

    private void verificar(String texto, String campo, int fila) {
        for (int i = 0; i < texto.length(); ) {
            int codePoint = texto.codePointAt(i);
            i += Character.charCount(codePoint);
            // El salto de línea es un corte de línea para OpenPDF, no un glifo.
            if (codePoint != SALTO_DE_LINEA && !fuente.charExists(codePoint)) {
                throw new GlifoNoSoportadoException(codePoint, campo, fila);
            }
        }
    }
}
