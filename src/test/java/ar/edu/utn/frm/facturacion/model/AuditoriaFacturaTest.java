package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.detalle;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.facturaBorrador;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.usuario;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Auditoría de modificación sobre el agregado factura (spec auditoria-entidades). */
class AuditoriaFacturaTest extends FacturaBase {

    @Autowired
    private FacturaVentaRepository facturaRepository;

    private Long guardarBorradorDeUnRenglonComoSistema() {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, null);
        factura.agregarDetalle(detalle(precioMartillo, 2, 3000));
        Long id = facturaRepository.saveAndFlush(factura).getId();
        em.clear();
        return id;
    }

    private void cambiarSoloLaCantidadDelRenglon(Long id) {
        FacturaVenta factura = facturaRepository.findById(id).orElseThrow();
        factura.getDetalles().get(0).setCantidad(5);
    }

    @Test
    void registrarModificacionActualizaLaCabeceraAunqueSoloCambieUnRenglon() {
        Long id = guardarBorradorDeUnRenglonComoSistema();
        Usuario mriquero = em.persist(usuario("mriquero"));
        UsuarioActual.set(mriquero);

        FacturaVenta factura = facturaRepository.findById(id).orElseThrow();
        factura.getDetalles().get(0).setCantidad(5);
        factura.registrarModificacion();
        em.flush();
        em.clear();

        FacturaVenta releida = facturaRepository.findById(id).orElseThrow();
        assertThat(releida.getUsuarioModificacion().getId()).isEqualTo(mriquero.getId());
        assertThat(releida.getUsuarioCarga().getId()).isEqualTo(sistema.getId());
        assertThat(releida.getDetalles().get(0).getCantidad()).isEqualTo(5.0);
    }

    @Test
    void sinRegistrarModificacionLaCabeceraConservaElUsuarioAnterior() {
        Long id = guardarBorradorDeUnRenglonComoSistema();
        Usuario mriquero = em.persist(usuario("mriquero"));
        UsuarioActual.set(mriquero);

        cambiarSoloLaCantidadDelRenglon(id);
        em.flush();
        em.clear();

        FacturaVenta releida = facturaRepository.findById(id).orElseThrow();
        // El renglón sí se actualizó, pero la cabecera no está sucia: Hibernate no emite UPDATE ni dispara @PreUpdate.
        assertThat(releida.getDetalles().get(0).getCantidad()).isEqualTo(5.0);
        assertThat(releida.getUsuarioModificacion().getId()).isEqualTo(sistema.getId());
    }
}
