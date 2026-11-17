package ar.edu.utn.frm.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.repository.FiltroFacturas;
import ar.edu.utn.frm.facturacion.report.ArchivoReporte;
import ar.edu.utn.frm.facturacion.report.FormatoReporte;
import ar.edu.utn.frm.facturacion.report.ReporteFacturasService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacturaServiceTest {

    @Mock
    private FacturaVentaRepository repositorio;

    @Mock
    private ReporteFacturasService reporte;

    @InjectMocks
    private FacturaService servicio;

    @Test
    void listarNormalizaYPasaElFiltroValidadoAlRepositorio() {
        FiltroFacturas esperado = new FiltroFacturas(LocalDate.of(2026, 11, 5), null, EstadoFactura.EMITIDA, 100.10);
        FacturaReporteDTO fila = new FacturaReporteDTO(1L, 7L, LocalDate.of(2026, 11, 5), "Juan Servicios",
                "Responsable Inscripto", "Casa Central", 250.00, 1L, EstadoFactura.EMITIDA, "PES");
        when(repositorio.buscarReporte(esperado)).thenReturn(List.of(fila));

        List<FacturaReporteDTO> resultado = servicio.listar(
                new FacturaFiltrosDTO(LocalDate.of(2026, 11, 5), null, " emitida ", 100.10));

        assertThat(resultado).containsExactly(fila);
    }

    @Test
    void listarSinFiltrosConsultaConSinFiltros() {
        when(repositorio.buscarReporte(FiltroFacturas.SIN_FILTROS)).thenReturn(List.of());

        assertThat(servicio.listar(new FacturaFiltrosDTO(null, null, "", null))).isEmpty();
    }

    @Test
    void estadoDesconocidoLanzaSinConsultarElRepositorio() {
        assertThatThrownBy(() -> servicio.listar(new FacturaFiltrosDTO(null, null, "PAGADA", null)))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("PAGADA");

        verifyNoInteractions(repositorio);
    }

    private static final FacturaReporteDTO FILA = new FacturaReporteDTO(1L, 7L, LocalDate.of(2026, 11, 5),
            "Juan Servicios", "Responsable Inscripto", "Casa Central", 250.00, 1L, EstadoFactura.EMITIDA, "PES");

    @Test
    void generarReporteConsultaConElFiltroValidadoYDelegaEnElExportadorDelFormato() {
        FiltroFacturas esperado = new FiltroFacturas(null, null, EstadoFactura.EMITIDA, null);
        when(repositorio.buscarReporte(esperado)).thenReturn(List.of(FILA));
        when(reporte.generarExcel(List.of(FILA))).thenReturn(new byte[] {1, 2, 3});
        when(reporte.generarTxt(List.of(FILA))).thenReturn(new byte[] {4});
        when(reporte.generarPdf(List.of(FILA))).thenReturn(new byte[] {5});
        FacturaFiltrosDTO filtros = new FacturaFiltrosDTO(null, null, "emitida", null);

        ArchivoReporte excel = servicio.generarReporte(filtros, " EXCEL ");
        ArchivoReporte txt = servicio.generarReporte(filtros, "tsv");
        ArchivoReporte pdf = servicio.generarReporte(filtros, "pdf");

        assertThat(excel.nombre()).isEqualTo("facturas.xlsx");
        assertThat(excel.tipo()).isEqualTo(FormatoReporte.XLSX.tipoContenido());
        assertThat(excel.contenido()).containsExactly(1, 2, 3);
        assertThat(txt.nombre()).isEqualTo("facturas.txt");
        assertThat(txt.contenido()).containsExactly(4);
        assertThat(pdf.nombre()).isEqualTo("facturas.pdf");
        assertThat(pdf.contenido()).containsExactly(5);
    }

    @Test
    void formatoInvalidoSeRechazaAntesDeConsultarElRepositorio() {
        assertThatThrownBy(() -> servicio.generarReporte(new FacturaFiltrosDTO(null, null, null, null), "doc"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("doc");

        verifyNoInteractions(repositorio, reporte);
    }

    @Test
    void filtroInvalidoEnElReporteSeRechazaSinConsultarNiGenerar() {
        assertThatThrownBy(() -> servicio.generarReporte(new FacturaFiltrosDTO(null, null, "PAGADA", null), "pdf"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("PAGADA");

        verifyNoInteractions(repositorio, reporte);
    }
}
