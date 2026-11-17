package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reporte de facturas en PDF: A4 apaisado, tabla de nueve columnas con encabezado repetido en cada página y
 * totales por moneda. Usa la fuente Noto Sans CJK SC incrustada con {@code Identity-H}, que se lee del classpath
 * la primera vez que se necesita (Excel y TXT no la requieren).
 */
final class ReportePdf {

    static final String RUTA_FUENTE = "fonts/NotoSansCJKsc-Regular.otf";

    private static final ZoneId ZONA_REPORTE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter FORMATO_GENERACION = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TITULO = "Reporte de facturas";
    private static final String TEXTO_TOTAL = "Total facturado (EMITIDA)";
    private static final String SIN_RESULTADOS = "No hay facturas para informar.";
    private static final float[] ANCHOS_RELATIVOS = {7, 9, 24, 15, 15, 6, 11, 8, 7};
    private static final int MARGEN = 36;

    private final Clock reloj;
    private final String rutaFuente;
    private volatile BaseFont fuenteBase;

    ReportePdf(Clock reloj, String rutaFuente) {
        this.reloj = reloj;
        this.rutaFuente = rutaFuente;
    }

    byte[] generar(List<FacturaReporteDTO> filas) {
        BaseFont base = fuente();
        String generado = ZonedDateTime.ofInstant(reloj.instant(), ZONA_REPORTE).format(FORMATO_GENERACION);
        String informacion = "Generado: " + generado + " (hora de Argentina) — Registros: " + filas.size();
        List<String> textosFijos = new ArrayList<>(ColumnasReporte.ENCABEZADOS);
        textosFijos.addAll(List.of(TITULO, informacion, SIN_RESULTADOS, TEXTO_TOTAL, "-"));
        new VerificadorGlifos(base).verificar(textosFijos, filas);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), MARGEN, MARGEN, MARGEN, MARGEN);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();
            Font letraTitulo = new Font(base, 14);
            Font letraTexto = new Font(base, 9);
            Font letraCelda = new Font(base, 8);

            documento.add(new Paragraph(TITULO, letraTitulo));
            Paragraph lineaInformacion = new Paragraph(informacion, letraTexto);
            lineaInformacion.setSpacingAfter(10);
            documento.add(lineaInformacion);

            if (filas.isEmpty()) {
                documento.add(new Paragraph(SIN_RESULTADOS, letraTexto));
            } else {
                documento.add(tabla(filas, letraCelda));
                for (Paragraph total : totales(filas, letraTexto)) {
                    documento.add(total);
                }
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
        return salida.toByteArray();
    }

    private static PdfPTable tabla(List<FacturaReporteDTO> filas, Font letra) throws DocumentException {
        PdfPTable tabla = new PdfPTable(ColumnasReporte.ENCABEZADOS.size());
        tabla.setWidthPercentage(100);
        tabla.setWidths(ANCHOS_RELATIVOS);
        tabla.setHeaderRows(1);
        for (String encabezado : ColumnasReporte.ENCABEZADOS) {
            PdfPCell celda = celda(encabezado, letra, Element.ALIGN_LEFT);
            celda.setBackgroundColor(new Color(0xE0, 0xE0, 0xE0));
            tabla.addCell(celda);
        }
        for (FacturaReporteDTO fila : filas) {
            tabla.addCell(celda(fila.getNumeroFactura() == null ? "-" : fila.getNumeroFactura().toString(), letra,
                    Element.ALIGN_RIGHT));
            tabla.addCell(celda(fila.getFechaEmision().format(FORMATO_FECHA), letra, Element.ALIGN_LEFT));
            tabla.addCell(celda(fila.getClienteDenominacion(), letra, Element.ALIGN_LEFT));
            tabla.addCell(celda(fila.getCondicionIva(), letra, Element.ALIGN_LEFT));
            tabla.addCell(celda(fila.getPuntoVentaDescripcion(), letra, Element.ALIGN_LEFT));
            tabla.addCell(celda(Long.toString(fila.getCantidadItems()), letra, Element.ALIGN_RIGHT));
            tabla.addCell(celda(importe(BigDecimal.valueOf(fila.getImporteTotal())), letra, Element.ALIGN_RIGHT));
            tabla.addCell(celda(fila.getEstado(), letra, Element.ALIGN_LEFT));
            tabla.addCell(celda(fila.getCodigoMoneda(), letra, Element.ALIGN_LEFT));
        }
        return tabla;
    }

    private static PdfPCell celda(String texto, Font letra, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, letra));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(3);
        return celda;
    }

    /** Un párrafo por moneda; todos con la fuente incrustada (un Paragraph vacío traería Helvetica sin incrustar). */
    private static List<Paragraph> totales(List<FacturaReporteDTO> filas, Font letra) {
        List<Paragraph> totales = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> total : TotalesPorMoneda.calcular(filas).entrySet()) {
            Paragraph linea = new Paragraph(TEXTO_TOTAL + " " + total.getKey() + ": "
                    + importe(total.getValue()), letra);
            if (totales.isEmpty()) {
                linea.setSpacingBefore(10);
            }
            totales.add(linea);
        }
        return totales;
    }

    /** Importe con separador de miles y coma decimal (es-AR), sin aritmética de punto flotante. */
    private static String importe(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(
                Locale.forLanguageTag("es-AR")));
        return formato.format(valor.setScale(2, RoundingMode.HALF_UP));
    }

    private BaseFont fuente() {
        BaseFont base = fuenteBase;
        if (base == null) {
            synchronized (this) {
                base = fuenteBase;
                if (base == null) {
                    base = cargarFuente();
                    fuenteBase = base;
                }
            }
        }
        return base;
    }

    private BaseFont cargarFuente() {
        try (InputStream entrada = ReportePdf.class.getClassLoader().getResourceAsStream(rutaFuente)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró la fuente " + rutaFuente
                        + " en el classpath; el PDF la necesita (ver README, Etapa 2)");
            }
            byte[] bytes = entrada.readAllBytes();
            String nombre = rutaFuente.substring(rutaFuente.lastIndexOf('/') + 1);
            return BaseFont.createFont(nombre, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("No se pudo cargar la fuente " + rutaFuente, e);
        }
    }
}
