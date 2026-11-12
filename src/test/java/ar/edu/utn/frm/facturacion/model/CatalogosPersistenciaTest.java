package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.condicionIva;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.listaPrecio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.puntoVenta;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.rubro;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.tipoMoneda;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frm.facturacion.repository.PuntoVentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class CatalogosPersistenciaTest extends PersistenciaBase {

    @Autowired
    private PuntoVentaRepository puntoVentaRepository;

    @Test
    void rubroIdaYVuelta() {
        Rubro leido = persistirYReleer(rubro("Ferretería", 10), Rubro.class);

        assertThat(leido.getDenominacion()).isEqualTo("Ferretería");
        assertThat(leido.getCodigo()).isEqualTo(10);
    }

    @Test
    void condicionIvaIdaYVuelta() {
        CondicionIva leida = persistirYReleer(condicionIva(5, "Consumidor Final"), CondicionIva.class);

        assertThat(leida.getCodigoAfip()).isEqualTo(5);
        assertThat(leida.getDenominacion()).isEqualTo("Consumidor Final");
    }

    @Test
    void tipoMonedaIdaYVuelta() {
        TipoMoneda pesos = persistirYReleer(tipoMoneda("PES", "Peso argentino", "$"), TipoMoneda.class);
        TipoMoneda dolares = persistirYReleer(tipoMoneda("DOL", "Dólar estadounidense", "US$"), TipoMoneda.class);

        assertThat(pesos.getCodigoAfip()).isEqualTo("PES");
        assertThat(pesos.getSimbolo()).isEqualTo("$");
        assertThat(dolares.getCodigoAfip()).isEqualTo("DOL");
        assertThat(dolares.getSimbolo()).isEqualTo("US$");
    }

    @Test
    void listaPrecioIdaYVuelta() {
        ListaPrecio leida = persistirYReleer(listaPrecio("GENERAL", "Lista general"), ListaPrecio.class);

        assertThat(leida.getCodigo()).isEqualTo("GENERAL");
        assertThat(leida.getDenominacion()).isEqualTo("Lista general");
    }

    @Test
    void puntoVentaIdaYVuelta() {
        PuntoVenta leido = persistirYReleer(puntoVenta(5), PuntoVenta.class);

        assertThat(leido.getNumero()).isEqualTo(5);
        assertThat(leido.getDescripcion()).isEqualTo("Punto de venta 5");
        assertThat(leido.getTipoEmision()).isEqualTo("CAE");
        assertThat(leido.getDomicilioComercial()).isEqualTo("Domicilio 5");
    }

    @Test
    void puntoVentaDuplicadoViolaLaUnicidad() {
        puntoVentaRepository.saveAndFlush(puntoVenta(1));

        assertThatThrownBy(() -> puntoVentaRepository.saveAndFlush(puntoVenta(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void puntoVentaDistintosConviven() {
        puntoVentaRepository.saveAndFlush(puntoVenta(1));
        puntoVentaRepository.saveAndFlush(puntoVenta(2));

        assertThat(puntoVentaRepository.count()).isEqualTo(2);
    }
}
