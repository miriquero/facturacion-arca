package ar.edu.utn.frm.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Solo resuelve el Environment: la configuracion anidada no activa ninguna autoconfiguracion,
 * por lo que no se crea DataSource ni hace falta una base PostgreSQL en ejecucion.
 */
@SpringBootTest(classes = PerfilPostgresTest.SinAutoconfiguracion.class)
@ActiveProfiles("postgres")
class PerfilPostgresTest {

    @Configuration
    static class SinAutoconfiguracion {
    }

    @Autowired
    private Environment entorno;

    @Test
    void datasourceApuntaAPostgresql() {
        assertThat(entorno.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/facturacion");
        assertThat(entorno.getProperty("spring.datasource.username")).isEqualTo("facturacion");
        assertThat(entorno.getProperty("spring.datasource.password")).isEqualTo("facturacion");
        assertThat(entorno.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.postgresql.Driver");
    }

    @Test
    void consolaH2QuedaDeshabilitada() {
        assertThat(entorno.getProperty("spring.h2.console.enabled")).isEqualTo("false");
    }

    @Test
    void heredaDdlAutoUpdateDelDocumentoPorDefecto() {
        assertThat(entorno.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
    }
}
