package ar.edu.utn.frm.facturacion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.PuntoVentaRepository;
import ar.edu.utn.frm.facturacion.repository.TipoMonedaRepository;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil test (no hay carga al arrancar) y sin transacción de prueba, para que el rollback del
 * DataLoader sea real. El loader se crea con createBean para que Spring le aplique el proxy transaccional.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataLoaderFalloTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PuntoVentaRepository puntoVentaRepository;

    @SpyBean
    private TipoMonedaRepository tipoMonedaRepository;

    @Test
    void siLaCargaFallaAMitadDeCaminoNoQuedanDatosParcialesNiContextoDeAuditoria() {
        doThrow(new IllegalStateException("fallo simulado")).when(tipoMonedaRepository).save(any());
        DataLoader loader = contexto.getAutowireCapableBeanFactory().createBean(DataLoader.class);

        assertThatThrownBy(loader::run).isInstanceOf(IllegalStateException.class).hasMessage("fallo simulado");

        verify(tipoMonedaRepository).save(any());
        assertThat(UsuarioActual.get()).isNull();
        assertThat(usuarioRepository.count()).isZero();
        assertThat(puntoVentaRepository.count()).isZero();
    }

    @Test
    void elLoaderCreadoConCreateBeanTieneProxyTransaccional() {
        AutowireCapableBeanFactory fabrica = contexto.getAutowireCapableBeanFactory();

        DataLoader loader = fabrica.createBean(DataLoader.class);

        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(loader)).isTrue();
    }
}
