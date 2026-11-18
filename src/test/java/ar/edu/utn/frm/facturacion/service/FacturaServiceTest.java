package ar.edu.utn.frm.facturacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.dto.PaginaDTO;
import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
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
        when(repositorio.buscarPagina(esperado, 0L, 10)).thenReturn(List.of(fila));
        when(repositorio.contar(esperado)).thenReturn(1L);

        PaginaDTO<FacturaReporteDTO> resultado = servicio.listar(
                new FacturaFiltrosDTO(LocalDate.of(2026, 11, 5), null, " emitida ", 100.10), null, null);

        assertThat(resultado.items()).containsExactly(fila);
        assertThat(resultado.totalElementos()).isEqualTo(1L);
    }

    @Test
    void listarSinFiltrosConsultaConSinFiltrosYArmaElSobreConPaginaYTotal() {
        FacturaReporteDTO fila = new FacturaReporteDTO(1L, 7L, LocalDate.of(2026, 11, 5), "Juan Servicios",
                "Responsable Inscripto", "Casa Central", 250.00, 1L, EstadoFactura.EMITIDA, "PES");
        when(repositorio.buscarPagina(FiltroFacturas.SIN_FILTROS, 20L, 10)).thenReturn(List.of(fila));
        when(repositorio.contar(FiltroFacturas.SIN_FILTROS)).thenReturn(23L);

        PaginaDTO<FacturaReporteDTO> resultado = servicio.listar(new FacturaFiltrosDTO(null, null, "", null), 2, 10);

        assertThat(resultado.items()).containsExactly(fila);
        assertThat(resultado.pagina()).isEqualTo(2);
        assertThat(resultado.tamanio()).isEqualTo(10);
        assertThat(resultado.totalElementos()).isEqualTo(23L);
        assertThat(resultado.totalPaginas()).isEqualTo(3);
    }

    @Test
    void estadoDesconocidoLanzaSinConsultarElRepositorio() {
        assertThatThrownBy(() -> servicio.listar(new FacturaFiltrosDTO(null, null, "PAGADA", null), null, null))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("PAGADA");

        verifyNoInteractions(repositorio);
    }

    @Test
    void paginaInvalidaLanzaSinConsultarElRepositorio() {
        assertThatThrownBy(() -> servicio.listar(filtrosVacios(), 0, 101))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("tamanio");
        assertThatThrownBy(() -> servicio.listar(filtrosVacios(), -1, 10))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("pagina");

        verifyNoInteractions(repositorio);
    }

    @Test
    void paginaPosteriorALaUltimaDevuelveItemsVaciosConElTotalReal() {
        when(repositorio.buscarPagina(FiltroFacturas.SIN_FILTROS, 50L, 10)).thenReturn(List.of());
        when(repositorio.contar(FiltroFacturas.SIN_FILTROS)).thenReturn(23L);

        PaginaDTO<FacturaReporteDTO> resultado = servicio.listar(filtrosVacios(), 5, 10);

        assertThat(resultado.items()).isEmpty();
        assertThat(resultado.totalElementos()).isEqualTo(23L);
        assertThat(resultado.totalPaginas()).isEqualTo(3);
    }

    private static FacturaFiltrosDTO filtrosVacios() {
        return new FacturaFiltrosDTO(null, null, null, null);
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

    // --- Comprobante PDF de una factura (etapa 6) ---

    private static ar.edu.utn.frm.facturacion.model.FacturaVenta facturaCon(EstadoFactura estado, Long numero) {
        var punto = DatosDePrueba.puntoVenta(1);
        punto.setId(2L);
        punto.setDescripcion("Casa Central");
        var condicion = DatosDePrueba.condicionIva(1, "Responsable Inscripto");
        condicion.setId(3L);
        var moneda = DatosDePrueba.tipoMoneda("PES", "Pesos", "$");
        moneda.setId(4L);
        var factura = DatosDePrueba.facturaBorrador(punto, condicion, moneda, null);
        factura.setId(9L);
        factura.setEstado(estado);
        factura.setNumero(numero);
        return factura;
    }

    @Test
    void generarComprobanteDeUnBorradorEs409ConBorradorYNoInformadaAArca() {
        when(repositorio.findById(9L)).thenReturn(java.util.Optional.of(facturaCon(EstadoFactura.BORRADOR, null)));

        assertThatThrownBy(() -> servicio.generarComprobante(9L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("BORRADOR")
                .hasMessageContaining("no fue informada a ARCA");

        verifyNoInteractions(reporte);
    }

    @Test
    void generarComprobanteDeUnaFacturaInexistenteEs404() {
        when(repositorio.findById(999999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> servicio.generarComprobante(999999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("999999");

        verifyNoInteractions(reporte);
    }

    @Test
    void generarComprobanteDeUnaEmitidaDevuelveElPdfConElNombreDelPuntoDeVentaYElNumero() {
        when(repositorio.findById(9L)).thenReturn(java.util.Optional.of(facturaCon(EstadoFactura.EMITIDA, 4L)));
        when(reporte.generarComprobante(org.mockito.ArgumentMatchers.argThat(d -> d.numero() == 4L
                && d.puntoVentaNumero() == 1))).thenReturn(new byte[] {7, 7});

        ArchivoReporte archivo = servicio.generarComprobante(9L);

        assertThat(archivo.nombre()).isEqualTo("factura-0001-00000004.pdf");
        assertThat(archivo.tipo()).isEqualTo(org.springframework.http.MediaType.APPLICATION_PDF);
        assertThat(archivo.contenido()).containsExactly(7, 7);
    }
}
