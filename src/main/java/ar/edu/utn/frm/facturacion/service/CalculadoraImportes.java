package ar.edu.utn.frm.facturacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cálculo monetario puro (sin Spring ni JPA), con aritmética decimal exacta.
 *
 * <p>Orden fijo (consigna, Parte B; RN-IM-04..07):
 *
 * <ol>
 *   <li>neto sin redondear = cantidad × precio × (1 − bonificación / 100);
 *   <li>IVA = neto sin redondear × alícuota / 100, redondeado a 2 decimales HALF_UP;
 *   <li>neto = neto sin redondear redondeado por separado a 2 decimales HALF_UP;
 *   <li>subtotal = neto + IVA (ya redondeados).
 * </ol>
 *
 * <p>Los porcentajes se dividen por 100 con {@link BigDecimal#movePointLeft(int)}, que desplaza la coma
 * sin división inexacta. No se usa {@code double} ni {@code Math.round}.
 */
public final class CalculadoraImportes {

    private static final int ESCALA = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Calcula neto, IVA y subtotal de un renglón. Una bonificación nula equivale a 0. */
    public ImportesRenglon calcularRenglon(
            BigDecimal cantidad, BigDecimal precio, BigDecimal bonificacion, BigDecimal alicuota) {
        BigDecimal porcentaje = bonificacion == null ? BigDecimal.ZERO : bonificacion;
        BigDecimal factorBonificacion = BigDecimal.ONE.subtract(porcentaje.movePointLeft(2));
        BigDecimal netoSinRedondear = cantidad.multiply(precio).multiply(factorBonificacion);

        BigDecimal iva = netoSinRedondear.multiply(alicuota.movePointLeft(2)).setScale(ESCALA, REDONDEO);
        BigDecimal neto = netoSinRedondear.setScale(ESCALA, REDONDEO);
        return new ImportesRenglon(neto, iva, neto.add(iva));
    }

    /** Suma decimal de los subtotales; una lista vacía da 0.00. */
    public BigDecimal sumar(List<BigDecimal> subtotales) {
        return subtotales.stream().reduce(BigDecimal.ZERO.setScale(ESCALA), BigDecimal::add);
    }

    /** saldo = total − cobrado. */
    public BigDecimal saldo(BigDecimal total, BigDecimal cobrado) {
        return total.subtract(cobrado);
    }
}
