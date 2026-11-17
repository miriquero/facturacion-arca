package ar.edu.utn.frm.facturacion.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EscapeTxtTest {

    @Test
    void escapaBarraTabulacionRetornoYSaltoDeLineaYDejaLasComillas() {
        String original = "A\\B\tC\r\n\"D\"";

        assertThat(EscapeTxt.escapar(original)).isEqualTo("A\\\\B\\tC\\r\\n\"D\"");
    }

    @Test
    void laBarraInvertidaSeguidaDeLetraNoSeConfundeConUnSaltoDeLinea() {
        assertThat(EscapeTxt.escapar("C:\\nuevo")).isEqualTo("C:\\\\nuevo");
    }

    @Test
    void textoSinEspecialesQuedaIgual() {
        assertThat(EscapeTxt.escapar("Ñandú & Cía. — 测试 «comillas»")).isEqualTo("Ñandú & Cía. — 测试 «comillas»");
    }

    @Test
    void cadaCaracterDeControlSeEscapaPorSeparado() {
        assertThat(EscapeTxt.escapar("\t")).isEqualTo("\\t");
        assertThat(EscapeTxt.escapar("\r")).isEqualTo("\\r");
        assertThat(EscapeTxt.escapar("\n")).isEqualTo("\\n");
        assertThat(EscapeTxt.escapar("\\")).isEqualTo("\\\\");
    }
}
