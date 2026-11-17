package ar.edu.utn.frm.facturacion.report;

import java.util.List;

/** Encabezados de las nueve columnas compartidas por PDF, Excel y TXT. */
final class ColumnasReporte {

    static final List<String> ENCABEZADOS = List.of("Número", "Fecha", "Cliente", "Condición IVA", "Punto de venta", "Ítems", "Total", "Estado",
            "Moneda");

    private ColumnasReporte() {
    }
}
