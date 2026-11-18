package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleDTO;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprobante de una factura en PDF: A4 vertical con la fuente incrustada compartida con el reporte. Es un
 * comprobante simulado (ARCA simulada): no tiene validez fiscal.
 */
final class ComprobantePdf {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter FORMATO_GENERACION = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MARGEN = 36;
    private static final String ESTADO_ANULADA = "ANULADA";
    private static final String LEYENDA = "Comprobante simulado con fines académicos, sin validez fiscal. CAE ficticio.";
    static final List<String> ENCABEZADOS = List.of("Código", "Descripción", "Cantidad", "Precio unit.", "Bonif. %",
            "Neto", "IVA", "Subtotal");
    private static final float[] ANCHOS_RELATIVOS = {12, 28, 9, 12, 9, 12, 10, 12};

    private final Clock reloj;
    private final FuentePdf fuentePdf;

    ComprobantePdf(Clock reloj, FuentePdf fuentePdf) {
        this.reloj = reloj;
        this.fuentePdf = fuentePdf;
    }

    byte[] generar(FacturaDetalladaDTO factura) {
        BaseFont base = fuentePdf.fuente();
        new VerificadorGlifos(base).verificarDatos(datos(factura));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, MARGEN, MARGEN, MARGEN, MARGEN);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();
            Font titulo = new Font(base, 18);
            Font texto = new Font(base, 10);

            documento.add(new Paragraph("Factura", titulo));
            documento.add(new Paragraph("Número: " + numero(factura), texto));
            documento.add(new Paragraph("Fecha de emisión: " + factura.fechaEmision().format(FORMATO_FECHA), texto));
            documento.add(new Paragraph("Punto de venta: " + factura.puntoVentaDescripcion(), texto));
            documento.add(new Paragraph("Generado: " + ZonedDateTime.ofInstant(reloj.instant(), ZONA)
                    .format(FORMATO_GENERACION) + " (hora de Argentina)", texto));

            Paragraph cliente = new Paragraph("Cliente: " + factura.clienteDenominacion(), texto);
            cliente.setSpacingBefore(10);
            documento.add(cliente);
            if (factura.clienteCuitCuil() != null) {
                documento.add(new Paragraph("CUIT/CUIL: " + factura.clienteCuitCuil(), texto));
            }
            documento.add(new Paragraph("Condición frente al IVA: " + factura.condicionIva(), texto));
            documento.add(new Paragraph("Moneda: " + factura.codigoMoneda(), texto));

            Paragraph renglones = new Paragraph("Renglones", texto);
            renglones.setSpacingBefore(10);
            renglones.setSpacingAfter(4);
            documento.add(renglones);
            documento.add(tabla(factura.detalles(), new Font(base, 8)));
            documento.add(totales(factura, texto));
            documento.add(autorizacion(factura, texto));
            if (ESTADO_ANULADA.equals(factura.estado())) {
                Paragraph anulada = new Paragraph("ANULADA", new Font(base, 20, Font.BOLD, new Color(0xB0, 0x00, 0x20)));
                anulada.setSpacingBefore(10);
                documento.add(anulada);
                documento.add(new Paragraph("Fecha de anulación: " + factura.fechaAnulacion().format(FORMATO_FECHA),
                        texto));
            }
            if (factura.observaciones() != null && !factura.observaciones().isBlank()) {
                Paragraph observaciones = new Paragraph("Observaciones: " + factura.observaciones(), texto);
                observaciones.setSpacingBefore(10);
                documento.add(observaciones);
            }
            Paragraph leyenda = new Paragraph(LEYENDA, new Font(base, 8));
            leyenda.setSpacingBefore(16);
            documento.add(leyenda);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
        return salida.toByteArray();
    }

    private static PdfPTable tabla(List<FacturaDetalleDTO> renglones, Font letra) throws DocumentException {
        PdfPTable tabla = new PdfPTable(ENCABEZADOS.size());
        tabla.setWidthPercentage(100);
        tabla.setWidths(ANCHOS_RELATIVOS);
        tabla.setHeaderRows(1);
        for (String encabezado : ENCABEZADOS) {
            PdfPCell celda = celda(encabezado, letra, Element.ALIGN_LEFT);
            celda.setBackgroundColor(new Color(0xE0, 0xE0, 0xE0));
            tabla.addCell(celda);
        }
        for (FacturaDetalleDTO renglon : renglones) {
            tabla.addCell(celda(renglon.articuloCodigo(), letra, Element.ALIGN_LEFT));
            tabla.addCell(celda(renglon.descripcion(), letra, Element.ALIGN_LEFT));
            tabla.addCell(celda(cantidad(renglon.cantidad()), letra, Element.ALIGN_RIGHT));
            tabla.addCell(celda(importe(renglon.precioUnitario()), letra, Element.ALIGN_RIGHT));
            tabla.addCell(celda(importe(renglon.porcentajeBonificacion()), letra, Element.ALIGN_RIGHT));
            tabla.addCell(celda(importe(renglon.importeNeto()), letra, Element.ALIGN_RIGHT));
            tabla.addCell(celda(importe(renglon.importeIva()), letra, Element.ALIGN_RIGHT));
            tabla.addCell(celda(importe(renglon.importeSubtotal()), letra, Element.ALIGN_RIGHT));
        }
        return tabla;
    }

    /** Neto e IVA son la suma exacta (BigDecimal) de los renglones; el total es el importe guardado de la factura. */
    private static Paragraph totales(FacturaDetalladaDTO factura, Font letra) {
        BigDecimal neto = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        for (FacturaDetalleDTO renglon : factura.detalles()) {
            neto = neto.add(BigDecimal.valueOf(renglon.importeNeto()));
            iva = iva.add(BigDecimal.valueOf(renglon.importeIva()));
        }
        Paragraph totales = new Paragraph("Total neto: " + FormatoImporte.importe(neto) + "\nTotal IVA: "
                + FormatoImporte.importe(iva) + "\nTotal: " + importe(factura.importeTotal()), letra);
        totales.setSpacingBefore(10);
        return totales;
    }

    private static Paragraph autorizacion(FacturaDetalladaDTO factura, Font letra) {
        Paragraph autorizacion = new Paragraph("Estado: " + factura.estado() + "\nCAE: " + factura.cae()
                + "\nVencimiento del CAE: " + factura.caeFechaVencimiento().format(FORMATO_FECHA)
                + "\nResultado: " + factura.resultadoAfip(), letra);
        autorizacion.setSpacingBefore(10);
        return autorizacion;
    }

    private static PdfPCell celda(String texto, Font letra, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, letra));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(3);
        return celda;
    }

    /** Valor guardado (double) llevado a decimal exacto: sin aritmética de punto flotante. */
    private static String importe(double guardado) {
        return FormatoImporte.importe(BigDecimal.valueOf(guardado));
    }

    /** Cantidad sin ceros sobrantes y con coma decimal (por ejemplo {@code 1} o {@code 2,5}). */
    private static String cantidad(double guardada) {
        return BigDecimal.valueOf(guardada).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** Todo texto del documento con el nombre del dato que lo origina, para verificar glifos antes de escribir. */
    private List<VerificadorGlifos.DatoImpreso> datos(FacturaDetalladaDTO factura) {
        List<VerificadorGlifos.DatoImpreso> datos = new ArrayList<>();
        datos.add(new VerificadorGlifos.DatoImpreso("texto fijo", "Factura Número Fecha de emisión Punto de venta Generado "
                + "(hora de Argentina) Cliente CUIT/CUIL Condición frente al IVA Moneda Renglones " + LEYENDA
                + " Total neto Total IVA Total Estado CAE Vencimiento del CAE Resultado ANULADA Fecha de anulación "
                + "Observaciones " + String.join(" ", ENCABEZADOS)));
        datos.add(new VerificadorGlifos.DatoImpreso("cliente", factura.clienteDenominacion()));
        if (factura.clienteCuitCuil() != null) {
            datos.add(new VerificadorGlifos.DatoImpreso("CUIT/CUIL", factura.clienteCuitCuil()));
        }
        datos.add(new VerificadorGlifos.DatoImpreso("condición frente al IVA", factura.condicionIva()));
        datos.add(new VerificadorGlifos.DatoImpreso("punto de venta", factura.puntoVentaDescripcion()));
        datos.add(new VerificadorGlifos.DatoImpreso("moneda", factura.codigoMoneda()));
        datos.add(new VerificadorGlifos.DatoImpreso("estado", factura.estado()));
        datos.add(new VerificadorGlifos.DatoImpreso("CAE", factura.cae()));
        datos.add(new VerificadorGlifos.DatoImpreso("resultado", factura.resultadoAfip()));
        if (factura.observaciones() != null) {
            datos.add(new VerificadorGlifos.DatoImpreso("observaciones", factura.observaciones()));
        }
        int numero = 1;
        for (FacturaDetalleDTO renglon : factura.detalles()) {
            datos.add(new VerificadorGlifos.DatoImpreso("código de artículo (renglón " + numero + ")",
                    renglon.articuloCodigo()));
            datos.add(new VerificadorGlifos.DatoImpreso("descripción (renglón " + numero + ")", renglon.descripcion()));
            numero++;
        }
        return datos;
    }

    /** Punto de venta con cuatro dígitos, guion y número con ocho dígitos (por ejemplo {@code 0001-00000004}). */
    static String numero(FacturaDetalladaDTO factura) {
        return String.format("%04d-%08d", factura.puntoVentaNumero(), factura.numero());
    }
}
