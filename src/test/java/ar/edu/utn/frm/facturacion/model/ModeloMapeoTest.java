package ar.edu.utn.frm.facturacion.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class ModeloMapeoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private List<String> tablas() {
        return jdbc.queryForList("select lower(table_name) from information_schema.tables "
                + "where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'", String.class);
    }

    private List<String> columnas(String tabla) {
        return jdbc.queryForList("select lower(column_name) from information_schema.columns "
                + "where table_schema = 'PUBLIC' and lower(table_name) = ?", String.class, tabla);
    }

    @Test
    void elMetamodeloTieneExactamenteLasCatorceEntidades() {
        Set<String> entidades = em.getEntityManager().getMetamodel().getEntities().stream()
                .map(EntityType::getName).collect(Collectors.toSet());

        assertThat(entidades).containsExactlyInAnyOrder("Usuario", "FacturaVenta", "FacturaVentaDetalle",
                "PuntoVenta", "Cliente", "Contacto", "Domicilio", "Articulo", "Rubro", "Marca", "ListaPrecio",
                "ListaPrecioArticulo", "CondicionIva", "TipoMoneda");
        assertThat(entidades).doesNotContain("EstadoFactura", "UsuarioActual", "EntityId", "AuditoriaApp");
    }

    @Test
    void existenCatorceTablasYNingunaIntermediaEntreFacturaYDetalle() {
        List<String> tablas = tablas();

        assertThat(tablas).hasSize(14).contains("factura_venta", "factura_venta_detalle");
        assertThat(tablas).doesNotContain("factura_venta_detalles", "entity_id", "auditoria_app");
        assertThat(tablas).noneMatch(t -> t.contains("factura_venta") && t.contains("_detalle") && !t.equals("factura_venta_detalle"));
    }

    @Test
    void laClaveForaneaDelRenglonEstaEnSuTablaYNoEsNula() {
        String nulable = jdbc.queryForObject("select is_nullable from information_schema.columns "
                + "where table_schema = 'PUBLIC' and lower(table_name) = 'factura_venta_detalle' "
                + "and lower(column_name) = 'factura_id'", String.class);
        Integer referencias = jdbc.queryForObject("select count(*) from information_schema.referential_constraints rc "
                + "join information_schema.table_constraints tc on tc.constraint_name = rc.constraint_name "
                + "and tc.constraint_schema = rc.constraint_schema "
                + "where lower(tc.table_name) = 'factura_venta_detalle'", Integer.class);

        assertThat(nulable).isEqualTo("NO");
        assertThat(referencias).isGreaterThanOrEqualTo(2);
        assertThat(columnas("factura_venta")).doesNotContain("detalle_id", "detalles_id");
    }

    @Test
    void lasColumnasDeAuditoriaAparecenEnLasTablasAuditables() {
        List<String> auditoria = List.of("fecha_alta", "fecha_modificacion", "fecha_baja",
                "usuario_carga_id", "usuario_modificacion_id", "usuario_baja_id");

        assertThat(columnas("marca")).containsAll(auditoria);
        assertThat(columnas("factura_venta")).containsAll(auditoria);
        assertThat(columnas("usuario")).doesNotContainAnyElementsOf(auditoria);
    }
}
