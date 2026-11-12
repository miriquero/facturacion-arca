package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.marca;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.repository.MarcaRepository;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class AuditoriaAppTest {

    @Autowired
    private MarcaRepository marcaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager em;

    private Usuario sistema;
    private Usuario acortez;

    @BeforeEach
    void prepararUsuarios() {
        sistema = usuarioRepository.save(usuario("sistema"));
        acortez = usuarioRepository.save(usuario("acortez"));
        UsuarioActual.set(sistema);
    }

    @AfterEach
    void limpiarContexto() {
        UsuarioActual.clear();
    }

    private Marca persistirYReleer(Marca marca) {
        marcaRepository.save(marca);
        em.flush();
        em.clear();
        return marcaRepository.findById(marca.getId()).orElseThrow();
    }

    @Test
    void altaCompletadaConElUsuarioDelContexto() {
        Marca leida = persistirYReleer(marca("Acme", 100));

        assertThat(leida.getFechaAlta()).isNotNull();
        assertThat(leida.getFechaModificacion()).isNotNull();
        assertThat(leida.getUsuarioCarga().getId()).isEqualTo(sistema.getId());
        assertThat(leida.getUsuarioModificacion().getId()).isEqualTo(sistema.getId());
    }

    @Test
    void altaCompletadaConOtroUsuarioDelContexto() {
        UsuarioActual.set(acortez);

        Marca leida = persistirYReleer(marca("TecnoSur", 200));

        assertThat(leida.getUsuarioCarga().getId()).isEqualTo(acortez.getId());
        assertThat(leida.getUsuarioModificacion().getId()).isEqualTo(acortez.getId());
    }

    @Test
    void bajaNulaAlCrear() {
        Marca leida = persistirYReleer(marca("Acme", 100));

        assertThat(leida.getFechaBaja()).isNull();
        assertThat(leida.getUsuarioBaja()).isNull();
    }

    @Test
    void contextoVacioHaceQueLaBaseRechaceElAlta() {
        UsuarioActual.clear();
        Marca marca = marca("Sin usuario", 300);

        assertThatThrownBy(() -> marcaRepository.saveAndFlush(marca))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(marca.getId()).isNull();
    }

    @Test
    void cambiarElAltaEnMemoriaNoModificaLaBase() {
        Marca original = persistirYReleer(marca("Acme", 100));
        LocalDateTime altaOriginal = original.getFechaAlta();
        Long cargaOriginal = original.getUsuarioCarga().getId();

        original.setFechaAlta(altaOriginal.minusYears(1));
        original.setUsuarioCarga(em.find(Usuario.class, acortez.getId()));
        original.setDenominacion("Acme modificada");
        em.flush();
        em.clear();

        Marca releida = marcaRepository.findById(original.getId()).orElseThrow();
        assertThat(releida.getDenominacion()).isEqualTo("Acme modificada");
        assertThat(releida.getFechaAlta()).isEqualTo(altaOriginal);
        assertThat(releida.getUsuarioCarga().getId()).isEqualTo(cargaOriginal).isEqualTo(sistema.getId());
    }

    @Test
    void usuarioDeCargaSeCargaDiferido() {
        Marca leida = persistirYReleer(marca("Acme", 100));

        var unidad = em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(unidad.isLoaded(leida, "usuarioCarga")).isFalse();
        assertThat(unidad.isLoaded(leida, "usuarioModificacion")).isFalse();
    }
}
