package ar.edu.utn.frm.facturacion.model;

import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Base de las pruebas de persistencia: H2 embebida con {@code @DataJpaTest}, usuarios de auditoría
 * persistidos y {@link UsuarioActual} fijado en {@code sistema} (y limpiado al terminar cada prueba).
 */
@DataJpaTest
abstract class PersistenciaBase {

    @Autowired
    protected TestEntityManager em;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    protected Usuario sistema;
    protected Usuario acortez;

    @BeforeEach
    void prepararAuditoria() {
        sistema = usuarioRepository.save(DatosDePrueba.usuario("sistema"));
        acortez = usuarioRepository.save(DatosDePrueba.usuario("acortez"));
        UsuarioActual.set(sistema);
    }

    @AfterEach
    void limpiarAuditoria() {
        UsuarioActual.clear();
    }

    /** Guarda, sincroniza, limpia el contexto de persistencia y relee desde la base. */
    protected <T extends EntityId> T persistirYReleer(T entidad, Class<T> tipo) {
        em.persist(entidad);
        em.flush();
        em.clear();
        return em.find(tipo, entidad.getId());
    }
}
