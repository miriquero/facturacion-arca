package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.articulo;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.listaPrecio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.precio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.repository.ListaPrecioArticuloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ListaPrecioArticuloPersistenciaTest extends PersistenciaBase {

    @Autowired
    private ListaPrecioArticuloRepository precioRepository;

    private ListaPrecio general;
    private ListaPrecio mayorista;
    private Articulo martillo;

    @BeforeEach
    void prepararCatalogos() {
        general = em.persist(listaPrecio("GENERAL", "Lista general"));
        mayorista = em.persist(listaPrecio("MAYORISTA", "Lista mayorista"));
        martillo = em.persist(articulo("ART-001", "Martillo", null, null, null));
    }

    @Test
    void precioIdaYVuelta() {
        ListaPrecioArticulo leido = persistirYReleer(precio(general, martillo, 3000.5), ListaPrecioArticulo.class);

        assertThat(leido.getPrecioVenta()).isEqualTo(3000.5);
        assertThat(leido.getListaPrecio().getId()).isEqualTo(general.getId());
        assertThat(leido.getArticulo().getId()).isEqualTo(martillo.getId());
    }

    @Test
    void mismoArticuloDosVecesEnLaMismaListaViolaLaUnicidad() {
        precioRepository.saveAndFlush(precio(general, martillo, 3000));

        assertThatThrownBy(() -> precioRepository.saveAndFlush(precio(general, martillo, 3500)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mismoArticuloEnDosListasConvive() {
        precioRepository.saveAndFlush(precio(general, martillo, 3000));
        precioRepository.saveAndFlush(precio(mayorista, martillo, 2500));

        assertThat(precioRepository.count()).isEqualTo(2);
    }

    @Test
    void precioSinListaOSinArticuloEsRechazado() {
        assertThatThrownBy(() -> precioRepository.saveAndFlush(precio(null, martillo, 3000)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
