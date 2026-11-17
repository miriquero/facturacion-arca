package ar.edu.utn.frm.facturacion.controller;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.report.ArchivoReporte;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lectura de facturas: traduce HTTP a {@link FacturaFiltrosDTO} y delega todo en el servicio. */
@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {

    private final FacturaService facturaService;

    public FacturaRestController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    @GetMapping
    public ResponseEntity<List<FacturaReporteDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo) {
        return ResponseEntity.ok(facturaService.listar(new FacturaFiltrosDTO(fechaDesde, fechaHasta, estado, montoMinimo)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaDetalladaDTO> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.obtenerDetalle(id));
    }

    /**
     * Descarga el reporte con los mismos filtros que el listado. {@code defaultValue} también cubre el parámetro
     * vacío ({@code formato=}), que Spring trata como ausente.
     */
    @GetMapping("/reporte")
    public ResponseEntity<byte[]> reporte(
            @RequestParam(defaultValue = "pdf") String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo) {
        ArchivoReporte archivo = facturaService.generarReporte(
                new FacturaFiltrosDTO(fechaDesde, fechaHasta, estado, montoMinimo), formato);
        return ResponseEntity.ok()
                .contentType(archivo.tipo())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
