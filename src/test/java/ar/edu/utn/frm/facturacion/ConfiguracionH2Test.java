package ar.edu.utn.frm.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

@ActiveProfiles("test")
@SpringBootTest
class ConfiguracionH2Test {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void hibernateActualizaEsquemaYRegistraSqlFormateado() {
        Map<String, Object> propiedades = entityManagerFactory.getProperties();

        assertThat(propiedades.get("hibernate.hbm2ddl.auto")).isEqualTo("update");
        assertThat(propiedades.get("hibernate.show_sql")).isEqualTo("true");
        assertThat(propiedades.get("hibernate.format_sql")).isEqualTo("true");
    }

    @Test
    void sinPerfilActivoNoSeActivaPostgresYLaConsolaH2EstaHabilitada(@Autowired Environment entorno) {
        assertThat(entorno.getActiveProfiles()).doesNotContain("postgres");
        assertThat(entorno.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:facturacion");
        assertThat(entorno.getProperty("spring.h2.console.enabled")).isEqualTo("true");
    }

    @Test
    void openEntityManagerInViewEstaDesactivado() {
        assertThat(contexto.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }

    @Test
    void datasourceUsaH2EnMemoriaFacturacionConUsuarioSa() throws SQLException {
        try (Connection conexion = dataSource.getConnection()) {
            assertThat(conexion.getMetaData().getURL()).startsWith("jdbc:h2:mem:facturacion");
            assertThat(conexion.getMetaData().getUserName()).isEqualTo("SA");
        }
    }
}
