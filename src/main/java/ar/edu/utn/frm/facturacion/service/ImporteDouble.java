package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.exception.ImporteNoRepresentableException;
import java.math.BigDecimal;

/**
 * Control de ida y vuelta antes de guardar un importe en un campo {@code double} del modelo
 * (consigna, Parte B, punto 4; RN-IM-09).
 *
 * <p>Un valor es aceptable si la conversión es finita y si {@code BigDecimal.valueOf(double)} vuelve a dar
 * el mismo valor decimal. Se compara con {@code compareTo} y no con {@code equals}: {@code 7260.00} y
 * {@code 7260.0} difieren en escala pero no en valor. No se usa {@code Math.round}.
 */
public final class ImporteDouble {

    private ImporteDouble() {}

    /**
     * Convierte {@code valor} a {@code double} o rechaza la operación con {@link
     * ImporteNoRepresentableException} (409) si hay desborde o pérdida de centavos.
     *
     * @param campo nombre del importe, para el mensaje de error
     */
    public static double aDouble(BigDecimal valor, String campo) {
        double convertido = valor.doubleValue();
        if (!Double.isFinite(convertido) || BigDecimal.valueOf(convertido).compareTo(valor) != 0) {
            throw new ImporteNoRepresentableException(campo, valor);
        }
        return convertido;
    }
}
