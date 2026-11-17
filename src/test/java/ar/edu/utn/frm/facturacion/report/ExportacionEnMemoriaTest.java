package ar.edu.utn.frm.facturacion.report;

import static ar.edu.utn.frm.facturacion.report.FacturasDeEjemplo.emitida;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Caracterización (spec exportacion-reportes): cada formato devuelve su contenido en memoria y no escribe
 * archivos en el directorio de trabajo.
 */
class ExportacionEnMemoriaTest {

    private final ReporteFacturasService servicio = new ReporteFacturasService(
            Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"), ZoneOffset.UTC));

    private static Set<String> contenidoDelDirectorioDeTrabajo() throws IOException {
        try (Stream<Path> archivos = Files.list(Path.of(System.getProperty("user.dir")))) {
            return archivos.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private void verificarSinArchivos(Function<List<FacturaReporteDTO>, byte[]> formato) throws IOException {
        Set<String> antes = contenidoDelDirectorioDeTrabajo();

        byte[] contenido = formato.apply(List.of(emitida("PES", 100.00), emitida("DOL", 10.50)));

        assertThat(contenido).isNotEmpty();
        assertThat(contenidoDelDirectorioDeTrabajo()).isEqualTo(antes);
    }

    @Test
    void txtSeDevuelveEnMemoriaSinCrearArchivos() throws IOException {
        verificarSinArchivos(servicio::generarTxt);
    }

    @Test
    void excelSeDevuelveEnMemoriaSinCrearArchivos() throws IOException {
        verificarSinArchivos(servicio::generarExcel);
    }

    @Test
    void pdfSeDevuelveEnMemoriaSinCrearArchivos() throws IOException {
        verificarSinArchivos(servicio::generarPdf);
    }

    @Test
    void listaVacia_lostresFormatosDevuelvenContenidoValidoSinError() {
        assertThat(servicio.generarPdf(List.of())).startsWith("%PDF".getBytes());
        assertThat(servicio.generarExcel(List.of())).startsWith("PK".getBytes());
        assertThat(servicio.generarTxt(List.of())).isNotEmpty();
    }
}
