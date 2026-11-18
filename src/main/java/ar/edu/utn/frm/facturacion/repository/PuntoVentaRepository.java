package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PuntoVentaRepository extends JpaRepository<PuntoVenta, Long> {

    /**
     * Bloquea la fila del punto de venta ({@code select ... for update}) para serializar las emisiones del mismo
     * punto: la numeración consulta el máximo recién después de obtener este bloqueo. No se puede bloquear con
     * {@code max(...)} en la misma sentencia, por eso se bloquea la fila y el máximo se consulta aparte.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PuntoVenta p where p.id = :id")
    Optional<PuntoVenta> bloquearPorId(@Param("id") Long id);
}
