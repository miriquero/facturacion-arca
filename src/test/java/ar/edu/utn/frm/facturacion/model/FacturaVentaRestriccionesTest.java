package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.detalle;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.facturaBorrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Restricciones de esquema de la factura; cada violación vive en su método porque la sesión queda inutilizable. */
class FacturaVentaRestriccionesTest extends FacturaBase {

    @Autowired
    private FacturaVentaRepository facturaRepository;

    private FacturaVenta emitida(PuntoVenta puntoVenta, long numero) {
        FacturaVenta f = facturaBorrador(puntoVenta, condicion, pesos, null);
        f.setNumero(numero);
        f.setEstado(EstadoFactura.EMITIDA);
        f.agregarDetalle(detalle(precioMartillo, 1, 3000));
        return f;
    }

    @Test
    void numeroRepetidoEnElMismoPuntoDeVentaViolaLaUnicidad() {
        facturaRepository.saveAndFlush(emitida(pv1, 1));

        assertThatThrownBy(() -> facturaRepository.saveAndFlush(emitida(pv1, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mismoNumeroEnOtroPuntoDeVentaConvive() {
        facturaRepository.saveAndFlush(emitida(pv1, 1));
        facturaRepository.saveAndFlush(emitida(pv2, 1));

        assertThat(facturaRepository.count()).isEqualTo(2);
    }

    @Test
    void numerosDistintosEnElMismoPuntoDeVentaConviven() {
        facturaRepository.saveAndFlush(emitida(pv1, 1));
        facturaRepository.saveAndFlush(emitida(pv1, 2));

        assertThat(facturaRepository.count()).isEqualTo(2);
    }

    @Test
    void dosBorradoresSinNumeroConvivenEnElMismoPuntoDeVenta() {
        facturaRepository.saveAndFlush(facturaBorrador(pv1, condicion, pesos, null));
        facturaRepository.saveAndFlush(facturaBorrador(pv1, condicion, pesos, null));
        em.clear();

        var borradores = facturaRepository.findAll();
        assertThat(borradores).hasSize(2);
        assertThat(borradores).allSatisfy(f -> assertThat(f.getNumero()).isNull());
    }

    @Test
    void facturaSinPuntoDeVentaEsRechazada() {
        FacturaVenta sinPuntoDeVenta = facturaBorrador(null, condicion, pesos, null);

        assertThatThrownBy(() -> facturaRepository.saveAndFlush(sinPuntoDeVenta))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void renglonSinArticuloDeListaEsRechazado() {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, null);
        factura.agregarDetalle(detalle(null, 1, 3000));

        assertThatThrownBy(() -> facturaRepository.saveAndFlush(factura))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void observacionesDeExactamente500CaracteresSePersisten() {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, null);
        factura.setObservaciones("x".repeat(500));
        Long id = facturaRepository.saveAndFlush(factura).getId();
        em.clear();

        assertThat(facturaRepository.findById(id).orElseThrow().getObservaciones()).hasSize(500);
    }

    @Test
    void observacionesDe501CaracteresSonRechazadas() {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, null);
        factura.setObservaciones("x".repeat(501));

        assertThatThrownBy(() -> facturaRepository.saveAndFlush(factura))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
