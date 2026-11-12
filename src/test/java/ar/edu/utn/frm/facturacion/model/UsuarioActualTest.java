package ar.edu.utn.frm.facturacion.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UsuarioActualTest {

    @AfterEach
    void limpiar() {
        UsuarioActual.clear();
    }

    @Test
    void fijarUsuarioLoDevuelveYLimpiarLoQuita() {
        Usuario sistema = new Usuario();

        UsuarioActual.set(sistema);
        assertThat(UsuarioActual.get()).isSameAs(sistema);

        UsuarioActual.clear();
        assertThat(UsuarioActual.get()).isNull();
    }

    @Test
    void fijarOtroUsuarioReemplazaAlAnterior() {
        Usuario sistema = new Usuario();
        Usuario otro = new Usuario();

        UsuarioActual.set(sistema);
        UsuarioActual.set(otro);

        assertThat(UsuarioActual.get()).isSameAs(otro);
    }

    @Test
    void otroHiloNoVeElUsuarioFijado() throws InterruptedException {
        UsuarioActual.set(new Usuario());
        AtomicReference<Usuario> vistoPorOtroHilo = new AtomicReference<>(new Usuario());

        Thread otro = new Thread(() -> vistoPorOtroHilo.set(UsuarioActual.get()));
        otro.start();
        otro.join();

        assertThat(vistoPorOtroHilo.get()).isNull();
        assertThat(UsuarioActual.get()).isNotNull();
    }
}
