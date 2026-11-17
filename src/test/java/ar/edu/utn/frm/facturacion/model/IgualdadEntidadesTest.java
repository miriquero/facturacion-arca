package ar.edu.utn.frm.facturacion.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/** Caracterización: la igualdad de las entidades es la identidad de {@link Object} y nunca recorre asociaciones. */
@DataJpaTest
class IgualdadEntidadesTest {

    @Autowired
    private TestEntityManager em;

    private static FacturaVenta facturaConDetalles() {
        FacturaVenta factura = new FacturaVenta();
        factura.setNumero(7L);
        factura.agregarDetalle(new FacturaVentaDetalle());
        factura.agregarDetalle(new FacturaVentaDetalle());
        return factura;
    }

    @Test
    void facturasConLosMismosDatosYDetallesEnlazadosNoRecurrenYSonDistintas() {
        FacturaVenta a = facturaConDetalles();
        FacturaVenta b = facturaConDetalles();

        assertThat(a.equals(b)).isFalse();
        assertThat(a.equals(a)).isTrue();
        assertThat(b.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(a.hashCode());
        assertThat(a.getDetalles().get(0).equals(a.getDetalles().get(1))).isFalse();
    }

    @Test
    void ningunaEntidadDelMetamodeloRedefineEqualsNiHashCode() throws NoSuchMethodException {
        List<Class<?>> entidades = em.getEntityManager().getMetamodel().getEntities().stream()
                .<Class<?>>map(EntityType::getJavaType).toList();

        assertThat(entidades).hasSize(14);
        for (Class<?> tipo : entidades) {
            assertThat(tipo.getMethod("equals", Object.class).getDeclaringClass())
                    .as("equals de %s", tipo.getSimpleName()).isEqualTo(Object.class);
            assertThat(tipo.getMethod("hashCode").getDeclaringClass())
                    .as("hashCode de %s", tipo.getSimpleName()).isEqualTo(Object.class);
        }
    }
}
