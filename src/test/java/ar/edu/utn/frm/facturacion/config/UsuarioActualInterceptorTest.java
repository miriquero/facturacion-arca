package ar.edu.utn.frm.facturacion.config;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Interceptor de X-Usuario (spec auditoria-por-request), sin Spring: repositorio simulado. */
@ExtendWith(MockitoExtension.class)
class UsuarioActualInterceptorTest {

    @Mock
    private UsuarioRepository usuarios;

    private final FacturacionProperties propiedades = new FacturacionProperties(new BigDecimal("21"), "sistema");

    @AfterEach
    void limpiar() {
        UsuarioActual.clear();
    }

    private UsuarioActualInterceptor interceptor() {
        return new UsuarioActualInterceptor(usuarios, propiedades);
    }

    private MockHttpServletRequest pedidoCon(String xUsuario) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/facturas");
        if (xUsuario != null) {
            request.addHeader("X-Usuario", xUsuario);
        }
        return request;
    }

    @Test
    void usuarioDeclaradoExistenteQuedaEnElContexto() {
        Usuario acortez = usuario("acortez");
        when(usuarios.findByUsuario("acortez")).thenReturn(Optional.of(acortez));

        boolean sigue = interceptor().preHandle(pedidoCon("acortez"), new MockHttpServletResponse(), new Object());

        assertThat(sigue).isTrue();
        assertThat(UsuarioActual.get()).isSameAs(acortez);
    }

    @Test
    void losEspaciosExterioresSeIgnoran() {
        Usuario acortez = usuario("acortez");
        when(usuarios.findByUsuario("acortez")).thenReturn(Optional.of(acortez));

        interceptor().preHandle(pedidoCon("  acortez  "), new MockHttpServletResponse(), new Object());

        assertThat(UsuarioActual.get()).isSameAs(acortez);
    }

    @ParameterizedTest(name = "encabezado [{0}] -> sistema")
    @NullSource
    @ValueSource(strings = {"", "   ", "intruso"})
    void ausenteVacioODesconocidoUsaElUsuarioPorDefecto(String xUsuario) {
        Usuario sistema = usuario("sistema");
        lenient().when(usuarios.findByUsuario("sistema")).thenReturn(Optional.of(sistema));
        lenient().when(usuarios.findByUsuario("intruso")).thenReturn(Optional.empty());

        boolean sigue = interceptor().preHandle(pedidoCon(xUsuario), new MockHttpServletResponse(), new Object());

        assertThat(sigue).as("la petición nunca se rechaza por el encabezado").isTrue();
        assertThat(UsuarioActual.get()).isSameAs(sistema);
    }

    @Test
    void sinUsuarioPorDefectoElContextoQuedaVacioSinExcepcion() {
        when(usuarios.findByUsuario("sistema")).thenReturn(Optional.empty());

        boolean sigue = interceptor().preHandle(pedidoCon(null), new MockHttpServletResponse(), new Object());

        assertThat(sigue).isTrue();
        assertThat(UsuarioActual.get()).isNull();
    }

    @Test
    void afterCompletionLimpiaElContextoSinExcepcion() throws Exception {
        UsuarioActual.set(usuario("acortez"));

        interceptor().afterCompletion(pedidoCon("acortez"), new MockHttpServletResponse(), new Object(), null);

        assertThat(UsuarioActual.get()).isNull();
    }

    @Test
    void afterCompletionLimpiaElContextoConExcepcion() throws Exception {
        UsuarioActual.set(usuario("acortez"));

        interceptor().afterCompletion(pedidoCon("acortez"), new MockHttpServletResponse(), new Object(),
                new IllegalStateException("falló el controlador"));

        assertThat(UsuarioActual.get()).isNull();
    }
}
