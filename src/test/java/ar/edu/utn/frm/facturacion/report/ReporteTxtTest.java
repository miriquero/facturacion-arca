package ar.edu.utn.frm.facturacion.report;

import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.emitida;
import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.factura;
import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.fila;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReporteTxtTest {

    private final ReporteFacturasService servicio = new ReporteFacturasService(
            Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"), ZoneOffset.UTC));

    private String generar(List<FacturaReporteDTO> filas) {
        return new String(servicio.generarTxt(filas), StandardCharsets.UTF_8);
    }

    private static String[] lineas(String txt) {
        return txt.split("\n");
    }

    @Test
    void tresFacturas_cuatroLineasConNueveCamposYEncabezadoDeLasColumnas() {
        String txt = generar(List.of(
                emitida("PES", 100.00),
                factura(EstadoFactura.BORRADOR, "PES", 50.00),
                factura(EstadoFactura.ANULADA, "DOL", 10.00)));

        String[] lineas = lineas(txt);

        assertThat(lineas).hasSize(4);
        assertThat(lineas[0]).isEqualTo(String.join("\t", ColumnasReporte.ENCABEZADOS));
        for (String linea : lineas) {
            assertThat(linea.split("\t", -1)).hasSize(9);
        }
    }

    @Test
    void listaVacia_soloLaLineaDeEncabezado() {
        String txt = generar(List.of());

        assertThat(lineas(txt)).containsExactly(String.join("\t", ColumnasReporte.ENCABEZADOS));
    }

    @Test
    void fechaIsoEImporteConDosDecimalesYPuntoSinSeparadorDeMiles() {
        FacturaReporteDTO factura = fila(900L, 15L, LocalDate.of(2026, 3, 5), "Cliente", "Responsable Inscripto",
                "Casa Central", 1234567.5, 2, EstadoFactura.EMITIDA, "PES");

        String[] campos = lineas(generar(List.of(factura)))[1].split("\t", -1);

        assertThat(campos[1]).isEqualTo("2026-03-05");
        assertThat(campos[6]).isEqualTo("1234567.50");
    }

    @Test
    void encabezadosYValores_laFacturaEmitidaNumero15SeEscribeCompletaYSinElId() {
        FacturaReporteDTO factura = fila(900L, 15L, LocalDate.of(2026, 11, 10), "Ferretería", "Responsable Inscripto",
                "Casa Central", 1210.5, 2, EstadoFactura.EMITIDA, "PES");

        String[] lineas = lineas(generar(List.of(factura)));

        assertThat(lineas[0].split("\t")).containsExactly("Número", "Fecha", "Cliente", "Condición IVA",
                "Punto de venta", "Ítems", "Total", "Estado", "Moneda");
        assertThat(lineas[1].split("\t", -1)).containsExactly("15", "2026-11-10", "Ferretería",
                "Responsable Inscripto", "Casa Central", "2", "1210.50", "EMITIDA", "PES");
        assertThat(lineas[1]).doesNotContain("900");
    }

    @Test
    void borrador_elNumeroQuedaVacio() {
        String[] campos = lineas(generar(List.of(factura(EstadoFactura.BORRADOR, "PES", 70.00))))[1].split("\t", -1);

        assertThat(campos[0]).isEmpty();
        assertThat(campos[7]).isEqualTo("BORRADOR");
    }

    @Test
    void sinPieDeTotales_lineasIgualFacturasMasUnoConDosMonedasEmitidas() {
        String txt = generar(List.of(emitida("PES", 1000.00), emitida("DOL", 10.50)));

        String[] lineas = lineas(txt);

        assertThat(lineas).hasSize(3);
        assertThat(lineas[1]).endsWith("\tEMITIDA\tPES");
        assertThat(lineas[2]).endsWith("\tEMITIDA\tDOL");
        assertThat(txt).doesNotContainIgnoringCase("total facturado");
    }

    /** Decodificador de un solo recorrido: ve una barra y mira el carácter siguiente (no encadena replace). */
    private static String decodificar(String campo) {
        StringBuilder original = new StringBuilder();
        for (int i = 0; i < campo.length(); i++) {
            char c = campo.charAt(i);
            if (c != '\\') {
                original.append(c);
                continue;
            }
            char siguiente = campo.charAt(++i);
            switch (siguiente) {
                case '\\' -> original.append('\\');
                case 't' -> original.append('\t');
                case 'r' -> original.append('\r');
                case 'n' -> original.append('\n');
                default -> throw new IllegalArgumentException("Escape desconocido: \\" + siguiente);
            }
        }
        return original.toString();
    }

    private static FacturaReporteDTO conTextos(String cliente, String condicionIva, String puntoVenta) {
        return fila(900L, 15L, LocalDate.of(2026, 11, 10), cliente, condicionIva, puntoVenta, 100.00, 1,
                EstadoFactura.EMITIDA, "PES");
    }

    @Test
    void reconstruccionDelOriginal_barrasTabulacionesRetornosYSaltosEnTresCampos() {
        String cliente = "A\\B\tC\r\n\"D\"";
        String condicion = "Resp\tInscripto\\n";
        String puntoVenta = "PV\r1\\";

        String txt = generar(List.of(conTextos(cliente, condicion, puntoVenta)));
        String[] lineas = lineas(txt);
        String[] campos = lineas[1].split("\t", -1);

        assertThat(lineas).hasSize(2);
        assertThat(campos).hasSize(9);
        assertThat(campos[2]).isEqualTo("A\\\\B\\tC\\r\\n\"D\"");
        assertThat(decodificar(campos[2])).isEqualTo(cliente);
        assertThat(decodificar(campos[3])).isEqualTo(condicion);
        assertThat(decodificar(campos[4])).isEqualTo(puntoVenta);
    }

    @Test
    void barraInvertidaSeguidaDeLetra_noSeConvierteEnSaltoDeLinea() {
        String[] lineas = lineas(generar(List.of(conTextos("C:\\nuevo", "RI", "Casa"))));
        String campo = lineas[1].split("\t", -1)[2];

        assertThat(lineas).hasSize(2);
        assertThat(campo).isEqualTo("C:\\\\nuevo");
        assertThat(decodificar(campo)).isEqualTo("C:\\nuevo");
    }

    @Test
    void caracteresNoAscii_seLeenIgualComoUtf8SinMarcaDeOrdenDeBytes() {
        byte[] bytes = servicio.generarTxt(List.of(conTextos("Ñandú & Cía. — 测试", "RI", "Casa")));

        String txt = new String(bytes, StandardCharsets.UTF_8);

        assertThat(txt.split("\n")[1].split("\t", -1)[2]).isEqualTo("Ñandú & Cía. — 测试");
        assertThat(bytes[0]).isEqualTo((byte) 'N');
        assertThat(new byte[] {bytes[0], bytes[1], bytes[2]}).isNotEqualTo(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    }
}
