package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.articulo;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.marca;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.rubro;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;

class ArticuloPersistenciaTest extends PersistenciaBase {

    private PersistenceUnitUtil unidad() {
        return em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();
    }

    @Test
    void articuloSinMarcaSinRubroYSinAlicuotaSeRelee() {
        Articulo leido = persistirYReleer(articulo("ART-003", "Cable HDMI", null, null, null), Articulo.class);

        assertThat(leido.getCodigo()).isEqualTo("ART-003");
        assertThat(leido.getMarca()).isNull();
        assertThat(leido.getRubro()).isNull();
        assertThat(leido.getAlicuotaIva()).isNull();
    }

    @Test
    void articuloConMarcaRubroYAlicuotaSeReleeConReferenciasDiferidas() {
        Marca acme = em.persist(marca("Acme", 100));
        Rubro ferreteria = em.persist(rubro("Ferretería", 10));

        Articulo leido = persistirYReleer(articulo("ART-001", "Martillo", acme, ferreteria, 10.5), Articulo.class);

        assertThat(unidad().isLoaded(leido, "marca")).isFalse();
        assertThat(unidad().isLoaded(leido, "rubro")).isFalse();
        assertThat(leido.getMarca().getId()).isEqualTo(acme.getId());
        assertThat(leido.getRubro().getId()).isEqualTo(ferreteria.getId());
        assertThat(leido.getAlicuotaIva()).isEqualTo(10.5);
    }

    @Test
    void articuloConSoloMarcaDejaElRubroNulo() {
        Marca tecno = em.persist(marca("TecnoSur", 200));

        Articulo leido = persistirYReleer(articulo("ART-002", "Televisor", tecno, null, null), Articulo.class);

        assertThat(leido.getMarca().getId()).isEqualTo(tecno.getId());
        assertThat(leido.getRubro()).isNull();
    }
}
