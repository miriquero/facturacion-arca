package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.Cliente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Clientes cuya denominación o CUIT/CUIL contiene el texto (sin distinguir mayúsculas), por id. El patrón llega
     * armado y con los comodines del usuario escapados con {@code \}; el {@code escape} es explícito por
     * portabilidad entre H2 y PostgreSQL.
     */
    @Query("select c from Cliente c where lower(c.denominacion) like :patron escape '\\' "
            + "or lower(c.cuitCuil) like :patron escape '\\' order by c.id")
    List<Cliente> buscar(@Param("patron") String patron);
}
