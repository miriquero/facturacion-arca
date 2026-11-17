package ar.edu.utn.frm.facturacion.report;

/**
 * Escape reversible de los campos de texto del TXT. Reemplaza la barra invertida por {@code \\}, la tabulación
 * por {@code \t}, el retorno de carro por {@code \r} y el salto de línea por {@code \n}. La barra invertida va
 * primero: si fuera después, volvería a escapar las barras que el propio escape acaba de introducir. Las comillas
 * quedan literales (no es CSV).
 */
final class EscapeTxt {

    private EscapeTxt() {
    }

    static String escapar(String texto) {
        return texto
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
