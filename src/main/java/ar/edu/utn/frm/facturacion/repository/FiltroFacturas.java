package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

/**
 * Filtros del listado y del reporte ya normalizados y validados. Es el contrato del repositorio: un campo nulo
 * significa «sin ese filtro». {@link #desde(FacturaFiltrosDTO)} es la única puerta de entrada, de modo que el
 * listado y el reporte no pueden validar distinto.
 */
public record FiltroFacturas(LocalDate desde, LocalDate hasta, EstadoFactura estado, Double montoMinimo) {

    public static final FiltroFacturas SIN_FILTROS = new FiltroFacturas(null, null, null, null);

    /**
     * Función pura: normaliza el estado (sin espacios exteriores, en mayúsculas con {@link Locale#ROOT}; vacío
     * equivale a ausente) y valida fechas y monto.
     *
     * @throws ParametroInvalidoException si algún valor no se admite
     */
    public static FiltroFacturas desde(FacturaFiltrosDTO crudos) {
        validarFechas(crudos.fechaDesde(), crudos.fechaHasta());
        validarMonto(crudos.montoMinimo());
        return new FiltroFacturas(crudos.fechaDesde(), crudos.fechaHasta(), normalizarEstado(crudos.estado()),
                crudos.montoMinimo());
    }

    private static EstadoFactura normalizarEstado(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            return null;
        }
        String normalizado = crudo.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(EstadoFactura.values())
                .filter(e -> e.name().equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new ParametroInvalidoException("El estado '" + crudo.strip()
                        + "' no es válido. Valores admitidos: " + Arrays.toString(EstadoFactura.values()) + "."));
    }

    private static void validarFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ParametroInvalidoException("fechaDesde (" + desde + ") no puede ser posterior a fechaHasta ("
                    + hasta + ").");
        }
    }

    private static void validarMonto(Double monto) {
        if (monto != null && !Double.isFinite(monto)) {
            throw new ParametroInvalidoException("montoMinimo debe ser un número finito (se recibió " + monto + ").");
        }
    }
}
