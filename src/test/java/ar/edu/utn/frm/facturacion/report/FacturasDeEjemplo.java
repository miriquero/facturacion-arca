package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.time.LocalDate;

/** Soporte de pruebas: arma filas de reporte legibles, sin tocar la base. */
final class FacturasDeEjemplo {

    private FacturasDeEjemplo() {
    }

    /** Fila con todos los datos explícitos (el id es interno y no debe aparecer en ningún archivo). */
    static FacturaReporteDTO fila(long id, Long numero, LocalDate fecha, String cliente, String condicionIva,
            String puntoVenta, double total, long items, EstadoFactura estado, String moneda) {
        return new FacturaReporteDTO(id, numero, fecha, cliente, condicionIva, puntoVenta, total, items, estado,
                moneda);
    }

    /** Fila de una factura en el estado y la moneda indicados, con datos de relleno legibles. */
    static FacturaReporteDTO factura(EstadoFactura estado, String moneda, double total) {
        Long numero = estado == EstadoFactura.BORRADOR ? null : 1L;
        return fila(1L, numero, LocalDate.of(2026, 11, 10), "Cliente de prueba", "Responsable Inscripto",
                "Casa Central", total, 1, estado, moneda);
    }

    static FacturaReporteDTO emitida(String moneda, double total) {
        return factura(EstadoFactura.EMITIDA, moneda, total);
    }
}
