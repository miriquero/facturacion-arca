package ar.edu.utn.frm.facturacion.report;

import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.fila;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Genera los archivos de muestra para la revisión visual de la etapa en {@code build/muestras-reportes/}
 * (ignorado por Git y borrado por {@code ./gradlew clean}). Los exportadores en sí no escriben nada: el archivo
 * lo escribe esta prueba.
 */
class MuestrasReportesTest {

    private static final Path DIRECTORIO = Path.of("build", "muestras-reportes");

    private static final ReporteFacturasService servicio = new ReporteFacturasService(
            Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"), ZoneOffset.UTC));

    @BeforeAll
    static void crearDirectorio() throws IOException {
        Files.createDirectories(DIRECTORIO);
    }

    /** Lista representativa que el PDF puede imprimir por completo (sin tabulaciones ni caracteres sin glifo). */
    private static List<FacturaReporteDTO> listaParaPdf() {
        return new ArrayList<>(List.of(
                fila(101, 15L, LocalDate.of(2026, 11, 10), "Ferretería Ñandú", "Responsable Inscripto",
                        "Casa Central", 1210.50, 2, EstadoFactura.EMITIDA, "PES"),
                fila(102, 1L, LocalDate.of(2026, 11, 9), "Cía. Importadora «Del Sur»", "Responsable Inscripto",
                        "Sucursal Mendoza", 10.50, 1, EstadoFactura.EMITIDA, "DOL"),
                fila(103, 16L, LocalDate.of(2026, 11, 8), "测试客户 (cliente de prueba)", "Consumidor Final",
                        "Casa Central", 1234567.89, 3, EstadoFactura.EMITIDA, "PES"),
                fila(104, 17L, LocalDate.of(2026, 11, 7), "Juan Servicios", "Monotributo", "Casa Central", 500.00,
                        1, EstadoFactura.ANULADA, "PES"),
                fila(105, null, LocalDate.of(2026, 11, 6), "Consumidor Final", "Consumidor Final", "Casa Central",
                        70.00, 1, EstadoFactura.BORRADOR, "PES"),
                fila(106, null, LocalDate.of(2026, 11, 5), "Ñoño & Hijos — \"Comillas\"", "Responsable Inscripto",
                        "Sucursal Mendoza", 40.00, 0, EstadoFactura.BORRADOR, "DOL")));
    }

    private static List<FacturaReporteDTO> listaParaTxtYExcel() {
        List<FacturaReporteDTO> lista = listaParaPdf();
        lista.add(fila(107, 18L, LocalDate.of(2026, 11, 4), "Ruta C:\\nuevo\tTramo\nSegunda línea",
                "Responsable Inscripto", "Casa Central", 321.10, 1, EstadoFactura.EMITIDA, "PES"));
        return lista;
    }

    private static Path escribir(String nombre, byte[] contenido) throws IOException {
        Path archivo = DIRECTORIO.resolve(nombre);
        Files.write(archivo, contenido);
        assertThat(archivo).exists();
        assertThat(Files.size(archivo)).isPositive();
        return archivo;
    }

    @Test
    void generaLasMuestrasTxtYExcel() throws IOException {
        escribir("facturas.txt", servicio.generarTxt(listaParaTxtYExcel()));
        escribir("facturas.xlsx", servicio.generarExcel(listaParaTxtYExcel()));
    }

    @Test
    void generaLasMuestrasPdf() throws IOException {
        escribir("facturas.pdf", servicio.generarPdf(listaParaPdf()));

        List<FacturaReporteDTO> muchas = IntStream.rangeClosed(1, 80)
                .mapToObj(i -> fila(1000 + i, (long) i, LocalDate.of(2026, 11, 1).plusDays(i % 28),
                        i % 7 == 0 ? "Cliente 测试 " + i : "Cliente número " + i, "Responsable Inscripto",
                        "Casa Central", 100.25 * i, 1 + i % 4,
                        i % 10 == 0 ? EstadoFactura.ANULADA : EstadoFactura.EMITIDA, i % 5 == 0 ? "DOL" : "PES"))
                .toList();
        escribir("facturas-varias-paginas.pdf", servicio.generarPdf(muchas));
    }
}
