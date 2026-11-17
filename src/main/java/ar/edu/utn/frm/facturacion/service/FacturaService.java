package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.repository.FiltroFacturas;
import ar.edu.utn.frm.facturacion.report.ArchivoReporte;
import ar.edu.utn.frm.facturacion.report.FormatoReporte;
import ar.edu.utn.frm.facturacion.report.ReporteFacturasService;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de lectura de facturas. Las transacciones son de solo lectura; los DTO se arman dentro de ellas. */
@Service
@Transactional(readOnly = true)
public class FacturaService {

    private static final String CONSUMIDOR_FINAL = "Consumidor Final";

    private final FacturaVentaRepository facturaRepository;
    private final ReporteFacturasService reporteService;

    public FacturaService(FacturaVentaRepository facturaRepository, ReporteFacturasService reporteService) {
        this.facturaRepository = facturaRepository;
        this.reporteService = reporteService;
    }

    public List<FacturaReporteDTO> listar(FacturaFiltrosDTO filtros) {
        return facturaRepository.buscarReporte(FiltroFacturas.desde(filtros));
    }

    /**
     * Detalle de una factura. El DTO se arma por completo acá, dentro de la transacción de solo lectura: con
     * {@code open-in-view: false} las asociaciones diferidas (cliente, catálogos, renglones, artículos) solo se
     * pueden leer mientras la sesión sigue abierta.
     */
    public FacturaDetalladaDTO obtenerDetalle(Long id) {
        FacturaVenta factura = facturaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
        Cliente cliente = factura.getCliente();
        List<FacturaDetalleDTO> renglones = factura.getDetalles().stream()
                .sorted(Comparator.comparing(FacturaVentaDetalle::getId))
                .map(FacturaService::aDto)
                .toList();
        return new FacturaDetalladaDTO(
                factura.getId(),
                factura.getNumero(),
                factura.getFechaEmision(),
                factura.getEstado().name(),
                cliente == null ? null : cliente.getId(),
                cliente == null ? CONSUMIDOR_FINAL : cliente.getDenominacion(),
                factura.getCondicionIva().getId(),
                factura.getCondicionIva().getDenominacion(),
                factura.getPuntoVenta().getId(),
                factura.getPuntoVenta().getNumero(),
                factura.getPuntoVenta().getDescripcion(),
                factura.getTipoMoneda().getId(),
                factura.getTipoMoneda().getCodigoAfip(),
                factura.getImporteTotal(),
                factura.getImporteCobrado(),
                factura.getImporteSaldo(),
                factura.getCae(),
                factura.getCaeFechaVencimiento(),
                factura.getResultadoAfip(),
                factura.getFechaAnulacion(),
                factura.getObservaciones(),
                renglones);
    }

    private static FacturaDetalleDTO aDto(FacturaVentaDetalle renglon) {
        ListaPrecioArticulo oferta = renglon.getListaPrecioArticulo();
        return new FacturaDetalleDTO(
                renglon.getId(),
                oferta.getId(),
                oferta.getArticulo().getCodigo(),
                renglon.getDescripcion(),
                renglon.getCantidad(),
                renglon.getPrecioUnitario(),
                renglon.getPorcentajeBonificacion(),
                renglon.getImporteNeto(),
                renglon.getImporteIva(),
                renglon.getImporteSubtotal());
    }

    /**
     * Reporte con los mismos filtros que {@link #listar}. Valida el formato y los filtros antes de consultar, para
     * no ejecutar la consulta ante un pedido que de todos modos se rechaza.
     */
    public ArchivoReporte generarReporte(FacturaFiltrosDTO filtros, String formato) {
        FormatoReporte tipo = FormatoReporte.desde(formato);
        List<FacturaReporteDTO> filas = facturaRepository.buscarReporte(FiltroFacturas.desde(filtros));
        byte[] contenido = switch (tipo) {
            case PDF -> reporteService.generarPdf(filas);
            case XLSX -> reporteService.generarExcel(filas);
            case TXT -> reporteService.generarTxt(filas);
        };
        return new ArchivoReporte(tipo.nombreArchivo(), tipo.tipoContenido(), contenido);
    }
}
