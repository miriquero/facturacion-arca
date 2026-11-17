package ar.edu.utn.frm.facturacion.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColumnasReporteTest {

    @Test
    void losNueveEncabezadosEstanEnOrdenFijoYSinElIdentificador() {
        assertThat(ColumnasReporte.ENCABEZADOS).containsExactly("Número", "Fecha", "Cliente", "Condición IVA",
                "Punto de venta", "Ítems", "Total", "Estado", "Moneda");
    }
}
