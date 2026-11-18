package ar.edu.utn.frm.facturacion.repository;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.detalle;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.facturaBorrador;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.model.FacturaBase;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Consultas con bloqueo pesimista (spec concurrencia-facturas). Se prueban con la base H2 de {@code @DataJpaTest}. */
@ExtendWith(OutputCaptureExtension.class)
class BloqueosRepositorioTest extends FacturaBase {

    @Autowired
    private FacturaVentaRepository facturaRepository;

    private Long guardarBorrador() {
        FacturaVenta factura = facturaBorrador(pv1, condicion, pesos, null);
        factura.agregarDetalle(detalle(precioMartillo, 1, 3000));
        Long id = facturaRepository.saveAndFlush(factura).getId();
        em.clear();
        return id;
    }

    @Test
    void buscarParaActualizarDevuelveLaFacturaConBloqueoDeEscritura() {
        Long id = guardarBorrador();

        FacturaVenta factura = facturaRepository.buscarParaActualizar(id).orElseThrow();

        assertThat(factura.getId()).isEqualTo(id);
        assertThat(em.getEntityManager().getLockMode(factura)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void laSentenciaGeneradaTerminaEnForUpdate(CapturedOutput salida) {
        Long id = guardarBorrador();

        facturaRepository.buscarParaActualizar(id);

        String sql = salida.getOut().toLowerCase().replaceAll("\\s+", " ");
        assertThat(sql).contains("from factura_venta");
        assertThat(sql.substring(sql.lastIndexOf("select"))).containsPattern("where \\w+\\.id=\\? for update");
    }

    @Test
    void unIdInexistenteDevuelveVacio() {
        assertThat(facturaRepository.buscarParaActualizar(999999L)).isEmpty();
    }

    // --- Bloqueo del punto de venta y máximo número emitido (spec ciclo-de-vida-factura, D6/D7) ---

    @Autowired
    private PuntoVentaRepository puntoVentaRepository;

    private void guardarEmitida(PuntoVenta punto, Long numero) {
        FacturaVenta factura = facturaBorrador(punto, condicion, pesos, null);
        factura.setNumero(numero);
        factura.setEstado(ar.edu.utn.frm.facturacion.model.EstadoFactura.EMITIDA);
        factura.agregarDetalle(detalle(precioMartillo, 1, 3000));
        facturaRepository.saveAndFlush(factura);
    }

    @Test
    void bloquearPorIdDevuelveElPuntoConBloqueoDeEscrituraYVacioSiNoExiste() {
        var punto = puntoVentaRepository.bloquearPorId(pv1.getId()).orElseThrow();

        assertThat(punto.getId()).isEqualTo(pv1.getId());
        assertThat(em.getEntityManager().getLockMode(punto)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(puntoVentaRepository.bloquearPorId(999999L)).isEmpty();
    }

    @Test
    void laSentenciaDeBloqueoDelPuntoDeVentaTerminaEnForUpdate(CapturedOutput salida) {
        puntoVentaRepository.bloquearPorId(pv1.getId());

        String sql = salida.getOut().toLowerCase().replaceAll("\\s+", " ");
        assertThat(sql.substring(sql.lastIndexOf("select")))
                .containsPattern("from punto_venta \\w+ where \\w+\\.id=\\? for update");
    }

    @Test
    void sinEmitidasElMaximoEsCero() {
        guardarBorrador();

        assertThat(facturaRepository.maximoNumero(pv1)).isZero();
    }

    @Test
    void conEmitidasElMaximoIgnoraBorradoresYOtrosPuntosDeVenta() {
        guardarEmitida(pv1, 1L);
        guardarEmitida(pv1, 2L);
        guardarEmitida(pv2, 7L);
        guardarBorrador();

        assertThat(facturaRepository.maximoNumero(pv1)).isEqualTo(2L);
        assertThat(facturaRepository.maximoNumero(pv2)).isEqualTo(7L);
    }
}
