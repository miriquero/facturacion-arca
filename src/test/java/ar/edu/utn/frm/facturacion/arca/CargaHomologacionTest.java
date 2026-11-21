package ar.edu.utn.frm.facturacion.arca;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.repository.IntentoEmisionArcaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("homologacion")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carga-homologacion",
        "spring.jpa.show-sql=false",
        "arca.cuit=20123456789",
        "arca.punto-venta=9",
        "arca.clave-privada=sin-clave.key",
        "arca.certificado=sin-certificado.pem"
})
class CargaHomologacionTest {
    @Autowired
    private FacturaVentaRepository facturas;
    @Autowired
    private IntentoEmisionArcaRepository intentos;

    @Test
    void arrancaSinCredencialesDisponiblesYCargaSoloBorradoresSinCae() {
        assertThat(facturas.findAll()).hasSize(2).allSatisfy(factura -> {
            assertThat(factura.getEstado()).isEqualTo(EstadoFactura.BORRADOR);
            assertThat(factura.getCae()).isNull();
            assertThat(factura.getNumero()).isNull();
        });
        assertThat(intentos.count()).isZero();
    }
}
