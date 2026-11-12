package ar.edu.utn.frm.facturacion.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class UsuarioPersistenciaTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager em;

    private Usuario nuevo(String usuario, String nombre, String apellido) {
        Usuario u = new Usuario();
        u.setUsuario(usuario);
        u.setClave("1234");
        u.setNombre(nombre);
        u.setApellido(apellido);
        return u;
    }

    @Test
    void guardarYReleerConservaTodosLosCampos() {
        Long id = usuarioRepository.save(nuevo("sistema", "Sistema", "Interno")).getId();
        em.flush();
        em.clear();

        Usuario leido = usuarioRepository.findById(id).orElseThrow();

        assertThat(leido.getId()).isNotNull();
        assertThat(leido.getUsuario()).isEqualTo("sistema");
        assertThat(leido.getClave()).isEqualTo("1234");
        assertThat(leido.getNombre()).isEqualTo("Sistema");
        assertThat(leido.getApellido()).isEqualTo("Interno");
    }

    @Test
    void cadaUsuarioRecibeSuPropioIdentificadorGeneradoPorLaBase() {
        Usuario sistema = usuarioRepository.save(nuevo("sistema", "Sistema", "Interno"));
        Usuario acortez = usuarioRepository.save(nuevo("acortez", "Ana", "Cortez"));
        em.flush();

        assertThat(sistema.getId()).isNotNull();
        assertThat(acortez.getId()).isNotNull().isNotEqualTo(sistema.getId());
    }

    @Test
    void usuarioDuplicadoViolaLaUnicidad() {
        usuarioRepository.saveAndFlush(nuevo("sistema", "Sistema", "Interno"));

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(nuevo("sistema", "Otro", "Distinto")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usuariosDistintosConviven() {
        usuarioRepository.saveAndFlush(nuevo("sistema", "Sistema", "Interno"));
        usuarioRepository.saveAndFlush(nuevo("acortez", "Ana", "Cortez"));

        assertThat(usuarioRepository.count()).isEqualTo(2);
    }

    @Test
    void usuarioSinIdentificadorEsRechazado() {
        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(nuevo(null, "Sin", "Identificador")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
