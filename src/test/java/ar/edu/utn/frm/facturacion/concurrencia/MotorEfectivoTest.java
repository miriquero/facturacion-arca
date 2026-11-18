package ar.edu.utn.frm.facturacion.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Evidencia del motor: la suite de concurrencia informa e INSISTE en el motor efectivamente usado, para que una
 * corrida pedida contra PostgreSQL que terminó conectada a otro motor falle en lugar de dar un falso verde.
 */
@Tag("concurrencia")
@SpringBootTest
@ActiveProfiles("test")
class MotorEfectivoTest {

    @DynamicPropertySource
    static void motor(DynamicPropertyRegistry registro) {
        MotorDePruebas.registrar(registro);
    }

    @Autowired
    private DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String url;

    @Test
    void elMotorEfectivoEsElPedidoEInformaNombreYVersion() throws Exception {
        try (Connection conexion = dataSource.getConnection()) {
            var meta = conexion.getMetaData();
            System.out.println("[MOTOR] producto=" + meta.getDatabaseProductName() + " version="
                    + meta.getDatabaseProductVersion() + " url=" + url);

            assertThat(meta.getDatabaseProductName()).containsIgnoringCase(MotorDePruebas.motorPedido());
        }
    }

    @Test
    void sinPedidoExplicitoLaSuiteUsaUnaH2PropiaConEsperaDeBloqueoHolgada() {
        if ("H2".equals(MotorDePruebas.motorPedido())) {
            assertThat(url).startsWith("jdbc:h2:mem:concurrencia").contains("LOCK_TIMEOUT=10000");
        } else {
            assertThat(url).startsWith("jdbc:postgresql://");
        }
    }
}
