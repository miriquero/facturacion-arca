package ar.edu.utn.frm.facturacion.report;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReporteFacturasService {

    private static final String HOJA_EXCEL = "Facturas";

    private final ReportePdf pdf;
    private final ComprobantePdf comprobante;

    @Autowired
    public ReporteFacturasService(Clock reloj) {
        this(reloj, FuentePdf.RUTA_FUENTE);
    }

    /** Solo para pruebas: permite indicar otra ruta de la fuente (p. ej. una inexistente). */
    ReporteFacturasService(Clock reloj, String rutaFuente) {
        FuentePdf fuente = new FuentePdf(rutaFuente);
        this.pdf = new ReportePdf(reloj, fuente);
        this.comprobante = new ComprobantePdf(reloj, fuente);
    }

    /** Comprobante PDF de una factura (A4 vertical) a partir de su detalle ya armado. */
    public byte[] generarComprobante(FacturaDetalladaDTO factura) {
        return comprobante.generar(factura);
    }

    public byte[] generarComprobanteHomologacion(FacturaDetalladaDTO factura) {
        return comprobante.generar(factura, true);
    }

    public byte[] generarPdf(List<FacturaReporteDTO> filas) {
        return pdf.generar(filas);
    }

    public byte[] generarExcel(List<FacturaReporteDTO> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(HOJA_EXCEL);
            escribirEncabezado(libro, hoja);
            CellStyle estiloFecha = libro.createCellStyle();
            estiloFecha.setDataFormat(libro.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle estiloEntero = libro.createCellStyle();
            estiloEntero.setDataFormat(libro.getCreationHelper().createDataFormat().getFormat("0"));
            CellStyle estiloImporte = libro.createCellStyle();
            estiloImporte.setDataFormat(libro.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
            int fila = 1;
            for (FacturaReporteDTO factura : filas) {
                Row renglon = hoja.createRow(fila++);
                if (factura.getNumeroFactura() == null) {
                    renglon.createCell(0, CellType.BLANK);
                } else {
                    Cell numero = renglon.createCell(0);
                    numero.setCellValue(factura.getNumeroFactura());
                    numero.setCellStyle(estiloEntero);
                }
                Cell fecha = renglon.createCell(1);
                fecha.setCellValue(factura.getFechaEmision());
                fecha.setCellStyle(estiloFecha);
                renglon.createCell(2).setCellValue(factura.getClienteDenominacion());
                renglon.createCell(3).setCellValue(factura.getCondicionIva());
                renglon.createCell(4).setCellValue(factura.getPuntoVentaDescripcion());
                Cell items = renglon.createCell(5);
                items.setCellValue(factura.getCantidadItems());
                items.setCellStyle(estiloEntero);
                Cell total = renglon.createCell(6);
                total.setCellValue(factura.getImporteTotal());
                total.setCellStyle(estiloImporte);
                renglon.createCell(7).setCellValue(factura.getEstado());
                renglon.createCell(8).setCellValue(factura.getCodigoMoneda());
            }
            escribirTotales(hoja, filas, estiloImporte);
            XSSFFormulaEvaluator.evaluateAllFormulaCells(libro);
            libro.setForceFormulaRecalculation(true);
            for (int columna = 0; columna < ColumnasReporte.ENCABEZADOS.size(); columna++) {
                hoja.autoSizeColumn(columna);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Texto tabulado UTF-8 (sin BOM): línea de encabezado y una línea física por factura, campos separados por
     * tabulación y saltos de línea {@code \n} fijos (no el separador del sistema operativo).
     */
    public byte[] generarTxt(List<FacturaReporteDTO> filas) {
        StringBuilder txt = new StringBuilder(String.join("\t", ColumnasReporte.ENCABEZADOS)).append('\n');
        for (FacturaReporteDTO fila : filas) {
            txt.append(String.join("\t", camposTxt(fila))).append('\n');
        }
        return txt.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Una fila por moneda debajo de las facturas (dejando una fila en blanco): la columna G lleva un SUMIFS sobre
     * el rango de detalle, nunca sobre las propias filas de total.
     */
    private static void escribirTotales(Sheet hoja, List<FacturaReporteDTO> filas, CellStyle estiloImporte) {
        int primeraFilaExcel = 2;
        int ultimaFilaExcel = filas.size() + 1;
        int fila = filas.size() + 2;
        for (String moneda : TotalesPorMoneda.calcular(filas).keySet()) {
            Row total = hoja.createRow(fila++);
            int filaExcel = fila;
            total.createCell(0).setCellValue("Total facturado (EMITIDA)");
            total.createCell(8).setCellValue(moneda);
            Cell suma = total.createCell(6);
            suma.setCellFormula(String.format("SUMIFS($G$%1$d:$G$%2$d,$H$%1$d:$H$%2$d,\"EMITIDA\",$I$%1$d:$I$%2$d,I%3$d)",
                    primeraFilaExcel, ultimaFilaExcel, filaExcel));
            suma.setCellStyle(estiloImporte);
        }
    }

    private static void escribirEncabezado(XSSFWorkbook libro, Sheet hoja) {
        Font negrita = libro.createFont();
        negrita.setBold(true);
        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        Row encabezado = hoja.createRow(0);
        for (int columna = 0; columna < ColumnasReporte.ENCABEZADOS.size(); columna++) {
            Cell celda = encabezado.createCell(columna);
            celda.setCellValue(ColumnasReporte.ENCABEZADOS.get(columna));
            celda.setCellStyle(estilo);
        }
    }

    private static List<String> camposTxt(FacturaReporteDTO fila) {
        return List.of(
                fila.getNumeroFactura() == null ? "" : fila.getNumeroFactura().toString(),
                fila.getFechaEmision().toString(),
                EscapeTxt.escapar(fila.getClienteDenominacion()),
                EscapeTxt.escapar(fila.getCondicionIva()),
                EscapeTxt.escapar(fila.getPuntoVentaDescripcion()),
                Long.toString(fila.getCantidadItems()),
                BigDecimal.valueOf(fila.getImporteTotal()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                EscapeTxt.escapar(fila.getEstado()),
                EscapeTxt.escapar(fila.getCodigoMoneda()));
    }
}
