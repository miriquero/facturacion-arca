package ar.edu.utn.frm.facturacion.report;

/**
 * El PDF no puede representar un carácter con la fuente incrustada. Se informa en lugar de sustituirlo u
 * omitirlo en silencio; el reporte sigue disponible en Excel y TXT.
 */
public class GlifoNoSoportadoException extends RuntimeException {

    private final int codePoint;
    private final String campo;
    private final int fila;

    /**
     * @param codePoint carácter sin glifo
     * @param campo     nombre del campo que lo contiene (p. ej. {@code cliente}, o {@code título} para textos fijos)
     * @param fila      número de fila (desde 1) dentro de la lista; 0 si es un texto fijo del documento
     */
    public GlifoNoSoportadoException(int codePoint, String campo, int fila) {
        super(mensaje(codePoint, campo, fila));
        this.codePoint = codePoint;
        this.campo = campo;
        this.fila = fila;
    }

    /**
     * Variante del comprobante de una factura: identifica el dato por su nombre y no sugiere otro formato (un
     * comprobante no tiene versión en Excel ni TXT).
     */
    public GlifoNoSoportadoException(int codePoint, String dato) {
        super(String.format("El carácter U+%04X del dato «%s» no está disponible en la fuente del PDF.", codePoint,
                dato));
        this.codePoint = codePoint;
        this.campo = dato;
        this.fila = 0;
    }

    private static String mensaje(int codePoint, String campo, int fila) {
        String ubicacion = fila > 0 ? "campo " + campo + " (fila " + fila + ")" : "texto fijo " + campo;
        return String.format("El carácter U+%04X del %s no está disponible en la fuente del PDF; exportá en Excel o TXT.",
                codePoint, ubicacion);
    }

    public int getCodePoint() {
        return codePoint;
    }

    public String getCampo() {
        return campo;
    }

    public int getFila() {
        return fila;
    }
}
