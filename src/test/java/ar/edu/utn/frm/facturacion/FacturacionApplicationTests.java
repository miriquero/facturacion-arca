package ar.edu.utn.frm.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContext;

@ActiveProfiles("test")
@SpringBootTest
class FacturacionApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void contextLoads() {
        assertThat(contexto.getBeanDefinitionCount()).isGreaterThan(0);
        assertThat(contexto.getBeanNamesForAnnotation(
                org.springframework.boot.autoconfigure.SpringBootApplication.class)).hasSize(1);
    }
}
