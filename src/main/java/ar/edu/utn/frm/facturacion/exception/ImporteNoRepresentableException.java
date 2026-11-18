package ar.edu.utn.frm.facturacion.exception;

import java.math.BigDecimal;

/**
 * Un importe decimal no puede guardarse sin pérdida en un campo {@code double} (desborde o pérdida de
 * centavos). Es un conflicto de negocio: se traduce a 409 Conflict.
 */
public class ImporteNoRepresentableException extends ReglaNegocioException {

    private final String campo;
    private final BigDecimal valor;

    public ImporteNoRepresentableException(String campo, BigDecimal valor) {
        super(mensaje(campo, valor));
        this.campo = campo;
        this.valor = valor;
    }

    private static String mensaje(String campo, BigDecimal valor) {
        return "El importe '" + campo + "' con valor " + valor.toPlainString()
                + " no puede representarse sin pérdida";
    }

    public String getCampo() {
        return campo;
    }

    public BigDecimal getValor() {
        return valor;
    }
}
