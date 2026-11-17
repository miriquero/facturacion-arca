package ar.edu.utn.frm.facturacion.service;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.articulo;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.cliente;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.condicionIva;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.contacto;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.domicilio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.listaPrecio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.precio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.puntoVenta;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.tipoMoneda;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.PersistenciaBase;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(CatalogoService.class)
class CatalogoServiceIntegracionTest extends PersistenciaBase {

    @Autowired
    private CatalogoService servicio;

    private void sincronizar() {
        em.flush();
        em.clear();
    }

    @Test
    void puntoDeVentaUnoSeEtiquetaConCuatroDigitosYDescripcion() {
        PuntoVenta casa = puntoVenta(1);
        casa.setDescripcion("Casa Central");
        casa = em.persist(casa);
        sincronizar();

        List<OpcionDTO> opciones = servicio.puntosVenta();

        assertThat(opciones).containsExactly(new OpcionDTO(casa.getId(), "0001 - Casa Central", null, null));
    }

    @Test
    void puntoDeVentaDeDosCifrasSeRellenaConCeros() {
        PuntoVenta sucursal = puntoVenta(12);
        sucursal.setDescripcion("Sucursal Norte");
        em.persist(sucursal);
        sincronizar();

        assertThat(servicio.puntosVenta()).extracting(OpcionDTO::etiqueta).containsExactly("0012 - Sucursal Norte");
    }

    @Test
    void clienteConCuitMuestraDenominacionYCuitEntreParentesis() {
        var juan = em.persist(cliente("Juan Servicios", "20-12345678-9", contacto("j@ejemplo.com"),
                domicilio("San Martín", "100")));
        sincronizar();

        assertThat(servicio.clientes())
                .containsExactly(new OpcionDTO(juan.getId(), "Juan Servicios (20-12345678-9)", null, null));
    }

    @Test
    void condicionIvaUsaLaDenominacionComoEtiqueta() {
        var ri = em.persist(condicionIva(1, "Responsable Inscripto"));
        sincronizar();

        assertThat(servicio.condicionesIva())
                .containsExactly(new OpcionDTO(ri.getId(), "Responsable Inscripto", null, null));
    }

    @Test
    void pesosYDolaresExponenSuCodigoYNoTienenValor() {
        var pesos = em.persist(tipoMoneda("PES", "Pesos", "$"));
        var dolares = em.persist(tipoMoneda("DOL", "Dólar estadounidense", "US$"));
        sincronizar();

        assertThat(servicio.tiposMoneda()).containsExactly(
                new OpcionDTO(pesos.getId(), "Pesos", null, "PES"),
                new OpcionDTO(dolares.getId(), "Dólar estadounidense", null, "DOL"));
    }

    @Test
    void martilloEnLaListaGeneralUsaElIdDeLaOfertaYSuPrecio() {
        ListaPrecio general = em.persist(listaPrecio("GENERAL", "Lista general"));
        Articulo martillo = em.persist(articulo("ART-001", "Martillo", null, null, null));
        ListaPrecioArticulo oferta = em.persist(precio(general, martillo, 3000.00));
        sincronizar();

        assertThat(servicio.listaPrecioArticulos())
                .containsExactly(new OpcionDTO(oferta.getId(), "ART-001 - Martillo (GENERAL)", 3000.00, null));
    }

    @Test
    void mismoArticuloEnDosListasDaDosOpcionesConSuPropioPrecio() {
        ListaPrecio general = em.persist(listaPrecio("GENERAL", "Lista general"));
        ListaPrecio mayorista = em.persist(listaPrecio("MAYORISTA", "Lista mayorista"));
        Articulo martillo = em.persist(articulo("ART-001", "Martillo", null, null, null));
        ListaPrecioArticulo enGeneral = em.persist(precio(general, martillo, 3000.00));
        ListaPrecioArticulo enMayorista = em.persist(precio(mayorista, martillo, 2500.00));
        sincronizar();

        assertThat(servicio.listaPrecioArticulos()).containsExactly(
                new OpcionDTO(enGeneral.getId(), "ART-001 - Martillo (GENERAL)", 3000.00, null),
                new OpcionDTO(enMayorista.getId(), "ART-001 - Martillo (MAYORISTA)", 2500.00, null));
    }

    @Test
    void lasOpcionesVienenOrdenadasPorIdAscendente() {
        var segundo = em.persist(cliente("Zeta SA", "30-1-1", contacto("z@ejemplo.com"), domicilio("A", "1")));
        var primero = em.persist(cliente("Alfa SA", "30-2-2", contacto("a@ejemplo.com"), domicilio("B", "2")));
        sincronizar();

        assertThat(servicio.clientes()).extracting(OpcionDTO::id)
                .containsExactly(segundo.getId(), primero.getId());
    }

    @Test
    void sinRegistrosCadaCatalogoDevuelveListaVacia() {
        assertThat(servicio.puntosVenta()).isEmpty();
        assertThat(servicio.clientes()).isEmpty();
        assertThat(servicio.condicionesIva()).isEmpty();
        assertThat(servicio.tiposMoneda()).isEmpty();
        assertThat(servicio.listaPrecioArticulos()).isEmpty();
    }
}
