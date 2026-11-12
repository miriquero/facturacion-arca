package ar.edu.utn.frm.facturacion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.CondicionIva;
import ar.edu.utn.frm.facturacion.model.Contacto;
import ar.edu.utn.frm.facturacion.model.Domicilio;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.Marca;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import ar.edu.utn.frm.facturacion.model.Rubro;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import ar.edu.utn.frm.facturacion.model.Usuario;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.support.Repositories;

@DataJpaTest
class RepositoriosTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void hayRepositorioParaCadaRaizYCatalogo() {
        Repositories repositorios = new Repositories(contexto);

        List<Class<?>> conRepositorio = List.of(Usuario.class, FacturaVenta.class, Cliente.class, PuntoVenta.class,
                CondicionIva.class, TipoMoneda.class, Articulo.class, ListaPrecio.class, ListaPrecioArticulo.class,
                Marca.class, Rubro.class);

        assertThat(conRepositorio).hasSize(11)
                .allSatisfy(tipo -> assertThat(repositorios.hasRepositoryFor(tipo))
                        .as("repositorio para %s", tipo.getSimpleName()).isTrue());
    }

    @Test
    void noHayRepositorioParaDetalleContactoNiDomicilio() {
        Repositories repositorios = new Repositories(contexto);

        assertThat(List.of(FacturaVentaDetalle.class, Contacto.class, Domicilio.class))
                .allSatisfy(tipo -> assertThat(repositorios.hasRepositoryFor(tipo))
                        .as("repositorio para %s", tipo.getSimpleName()).isFalse());
    }
}
