package ar.edu.utn.frm.facturacion.report;

import org.springframework.http.MediaType;

/** Reporte generado, listo para descargar: nombre sugerido, tipo de contenido y bytes. */
public record ArchivoReporte(String nombre, MediaType tipo, byte[] contenido) {
}
