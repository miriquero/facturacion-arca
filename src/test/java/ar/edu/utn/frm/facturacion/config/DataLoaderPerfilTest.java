package ar.edu.utn.frm.facturacion.config;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(DataLoader.class)
class DataLoaderPerfilTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void conPerfilTestNoExisteElComponenteDeCarga() {
        assertThat(contexto.getBeanNamesForType(DataLoader.class)).isEmpty();
    }

    @Test
    void conPerfilTestLaBaseNoTieneUsuarios() {
        assertThat(usuarioRepository.count()).isZero();
    }
}
