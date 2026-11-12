package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.cliente;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.contacto;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.domicilio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.repository.ClienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ClienteAgregadoTest extends PersistenciaBase {

    @Autowired
    private ClienteRepository clienteRepository;

    private Cliente guardarYReleer(Cliente cliente) {
        Long id = clienteRepository.saveAndFlush(cliente).getId();
        em.clear();
        return clienteRepository.findById(id).orElseThrow();
    }

    @Test
    void guardarElClienteGuardaContactoYDomicilio() {
        Cliente leido = guardarYReleer(cliente("Juan Servicios", "20-12345678-9",
                contacto("juan@ejemplo.com"), domicilio("San Martín", "100")));

        assertThat(leido.getContacto()).isNotNull();
        assertThat(leido.getContacto().getEmail()).isEqualTo("juan@ejemplo.com");
        assertThat(leido.getDomicilio()).isNotNull();
        assertThat(leido.getDomicilio().getNombreCalle()).isEqualTo("San Martín");
        assertThat(leido.getDomicilio().getNumeroCalle()).isEqualTo("100");
    }

    @Test
    void otroClienteConservaSusPropiosDependientes() {
        Cliente maria = guardarYReleer(cliente("María Electrónica", "27-87654321-0",
                contacto("maria@ejemplo.com"), domicilio("Belgrano", "250")));

        assertThat(maria.getContacto()).isNotNull();
        assertThat(maria.getContacto().getEmail()).isEqualTo("maria@ejemplo.com");
        assertThat(maria.getDomicilio().getNombreCalle()).isEqualTo("Belgrano");
        assertThat(maria.getDomicilio().getId()).isNotNull();
    }

    @Test
    void clienteSinDomicilioEsRechazado() {
        Cliente sinDomicilio = cliente("Sin Domicilio", "20-11111111-1", contacto("a@b.com"), null);

        assertThatThrownBy(() -> clienteRepository.saveAndFlush(sinDomicilio))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clienteSinContactoEsRechazado() {
        Cliente sinContacto = cliente("Sin Contacto", "20-22222222-2", null, domicilio("Mitre", "1"));

        assertThatThrownBy(() -> clienteRepository.saveAndFlush(sinContacto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eliminarElClienteEliminaContactoYDomicilio() {
        Cliente guardado = clienteRepository.saveAndFlush(cliente("Juan Servicios", "20-12345678-9",
                contacto("juan@ejemplo.com"), domicilio("San Martín", "100")));
        Long clienteId = guardado.getId();
        Long contactoId = guardado.getContacto().getId();
        Long domicilioId = guardado.getDomicilio().getId();
        em.clear();

        clienteRepository.delete(clienteRepository.findById(clienteId).orElseThrow());
        em.flush();
        em.clear();

        assertThat(em.find(Cliente.class, clienteId)).isNull();
        assertThat(em.find(Contacto.class, contactoId)).isNull();
        assertThat(em.find(Domicilio.class, domicilioId)).isNull();
    }

    @Test
    void reemplazarElDomicilioEliminaElAnterior() {
        Cliente guardado = clienteRepository.saveAndFlush(cliente("Juan Servicios", "20-12345678-9",
                contacto("juan@ejemplo.com"), domicilio("San Martín", "100")));
        Long clienteId = guardado.getId();
        Long domicilioAnteriorId = guardado.getDomicilio().getId();

        guardado.setDomicilio(domicilio("Rivadavia", "500"));
        em.flush();
        em.clear();

        Cliente releido = clienteRepository.findById(clienteId).orElseThrow();
        assertThat(releido.getDomicilio().getNombreCalle()).isEqualTo("Rivadavia");
        assertThat(releido.getDomicilio().getId()).isNotEqualTo(domicilioAnteriorId);
        assertThat(em.find(Domicilio.class, domicilioAnteriorId)).isNull();
    }

    @Test
    void contactoYDomicilioSeCarganDiferidos() {
        Cliente leido = guardarYReleer(cliente("Juan Servicios", "20-12345678-9",
                contacto("juan@ejemplo.com"), domicilio("San Martín", "100")));

        var unidad = em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(unidad.isLoaded(leido, "contacto")).isFalse();
        assertThat(unidad.isLoaded(leido, "domicilio")).isFalse();
    }
}
