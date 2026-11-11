package ar.edu.utn.frm.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EstructuraPaquetesTest {

    @ParameterizedTest(name = "el paquete {0} tiene package-info")
    @ValueSource(strings = {"model", "dto", "repository", "service", "controller", "exception", "config", "report"})
    void cadaPaqueteTieneDescriptor(String paquete) {
        String recurso = "ar/edu/utn/frm/facturacion/" + paquete + "/package-info.class";

        assertThat(getClass().getClassLoader().getResource(recurso))
                .as("descriptor de paquete %s", recurso)
                .isNotNull();
    }
}
