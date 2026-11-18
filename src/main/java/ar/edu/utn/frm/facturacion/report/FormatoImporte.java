package ar.edu.utn.frm.facturacion.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Importe con separador de miles y coma decimal (es-AR), a dos decimales con {@code HALF_UP}, sin punto flotante. */
final class FormatoImporte {

    private FormatoImporte() {
    }

    static String importe(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(
                Locale.forLanguageTag("es-AR")));
        return formato.format(valor.setScale(2, RoundingMode.HALF_UP));
    }
}
