package ar.edu.utn.frm.facturacion.controller;

import ar.edu.utn.frm.facturacion.dto.CambioEstadoRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.frm.facturacion.dto.PaginaDTO;
import ar.edu.utn.frm.facturacion.report.ArchivoReporte;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Facturas: traduce HTTP a DTO y delega todo en el servicio. */
@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {

    private final FacturaService facturaService;

    public FacturaRestController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    /**
     * Listado paginado con filtros. {@code pagina} (base 0, por defecto 0) y {@code tamanio} (por defecto 10, máximo
     * 100) se validan en el servicio; {@code informada} llega como texto para admitir solo {@code true} y
     * {@code false}.
     */
    @GetMapping
    public ResponseEntity<PaginaDTO<FacturaReporteDTO>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Double montoMinimo,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String informada,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanio) {
        return ResponseEntity.ok(facturaService.listar(new FacturaFiltrosDTO(fechaDesde, fechaHasta, estado,
                montoMinimo, numero, clienteId, informada), pagina, tamanio));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaDetalladaDTO> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.obtenerDetalle(id));
    }

    /**
     * PDF del comprobante de una factura informada (EMITIDA o ANULADA). Ruta hija del recurso: no mezcla el
     * comprobante con el listado ni con el reporte.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        ArchivoReporte archivo = facturaService.generarComprobante(id);
        return ResponseEntity.ok()
                .contentType(archivo.tipo())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
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
            @RequestParam(required = false) Double montoMinimo,
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String informada) {
        ArchivoReporte archivo = facturaService.generarReporte(new FacturaFiltrosDTO(fechaDesde, fechaHasta, estado,
                montoMinimo, numero, clienteId, informada), formato);
        return ResponseEntity.ok()
                .contentType(archivo.tipo())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }

    /** Alta de un borrador: 201 con {@code Location} de la factura creada y su fila de reporte. */
    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@Valid @RequestBody FacturaRequestDTO solicitud) {
        FacturaReporteDTO creada = facturaService.crear(solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.getId()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    /** Reemplazo completo de un borrador (cabecera y renglones). */
    @PutMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> actualizar(@PathVariable Long id,
            @Valid @RequestBody FacturaRequestDTO solicitud) {
        return ResponseEntity.ok(facturaService.actualizar(id, solicitud));
    }

    /** Eliminación física de un borrador: 204 sin cuerpo. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        facturaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Cambio de estado (emitir o anular) con una observación opcional. */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<FacturaReporteDTO> cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody CambioEstadoRequestDTO solicitud) {
        return ResponseEntity.ok(facturaService.cambiarEstado(id, solicitud));
    }
}
