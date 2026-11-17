package ar.edu.utn.frm.facturacion.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

class FormatoReporteTest {

    @ParameterizedTest
    @CsvSource({
            "pdf, PDF, application/pdf, pdf",
            "xlsx, XLSX, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, xlsx",
            "excel, XLSX, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, xlsx",
            "txt, TXT, text/plain;charset=UTF-8, txt",
            "tsv, TXT, text/plain;charset=UTF-8, txt",
            "' XLSX ', XLSX, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, xlsx",
            "PDF, PDF, application/pdf, pdf",
            "Excel, XLSX, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, xlsx"})
    void aliasMayusculasYEspaciosSeNormalizan(String texto, FormatoReporte esperado, String tipo, String extension) {
        FormatoReporte formato = FormatoReporte.desde(texto);

        assertThat(formato).isEqualTo(esperado);
        assertThat(formato.tipoContenido()).isEqualTo(MediaType.parseMediaType(tipo));
        assertThat(formato.extension()).isEqualTo(extension);
        assertThat(formato.nombreArchivo()).isEqualTo("facturas." + extension);
    }

    @Test
    void formatoDesconocidoNombraElValorYLosAdmitidos() {
        assertThatThrownBy(() -> FormatoReporte.desde("doc"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("doc")
                .hasMessageContaining("pdf")
                .hasMessageContaining("xlsx")
                .hasMessageContaining("excel")
                .hasMessageContaining("txt")
                .hasMessageContaining("tsv");
    }

    @Test
    void formatoNuloSeRechaza() {
        assertThatThrownBy(() -> FormatoReporte.desde(null)).isInstanceOf(ParametroInvalidoException.class);
    }
}
