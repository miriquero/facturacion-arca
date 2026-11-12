package ar.edu.utn.frm.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsolaH2Test {

    @Autowired
    private TestRestTemplate cliente;

    @Test
    void consolaH2RespondeConLaPaginaDeLogin() {
        ResponseEntity<String> respuesta = cliente.getForEntity("/h2-console/", String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).contains("H2 Console");
    }
}
