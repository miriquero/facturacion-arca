package ar.edu.utn.frm.facturacion.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** La restriccion unica (punto de venta, numero) es la ultima defensa si fallara el bloqueo. */
class UnicidadDeNumeroTest extends ConcurrenciaBase {

    private FacturaVenta emitidaConNumero(long numero) {
        FacturaVenta factura = DatosDePrueba.facturaBorrador(puntoA, condicion, moneda, null);
        factura.setNumero(numero);
        factura.setEstado(EstadoFactura.EMITIDA);
        factura.agregarDetalle(DatosDePrueba.detalle(precio, 1, 1000));
        return factura;
    }

    @Test
    void unDuplicadoForzadoPorRepositorioEsRechazadoPorLaBase() {
        facturaRepository.saveAndFlush(emitidaConNumero(1));

        assertThatThrownBy(() -> facturaRepository.saveAndFlush(emitidaConNumero(1)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(numerosEmitidos(puntoA)).containsExactly(1L);
    }

    @Test
    void elMismoNumeroEnOtroPuntoDeVentaSiSePermite() {
        facturaRepository.saveAndFlush(emitidaConNumero(1));
        FacturaVenta otra = emitidaConNumero(1);
        otra.setPuntoVenta(puntoB);

        facturaRepository.saveAndFlush(otra);

        assertThat(numerosEmitidos(puntoB)).containsExactly(1L);
    }
}
