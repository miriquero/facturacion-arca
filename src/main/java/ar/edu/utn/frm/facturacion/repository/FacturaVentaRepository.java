package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long>, FacturaReporteRepository {

    /**
     * Carga la factura con bloqueo pesimista de escritura ({@code select ... for update}): quien llega después
     * espera hasta que la transacción actual confirme o revierta. Es la primera sentencia de toda escritura sobre
     * una factura existente (orden de adquisición: factura, luego punto de venta).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FacturaVenta f where f.id = :id")
    Optional<FacturaVenta> buscarParaActualizar(@Param("id") Long id);

    /**
     * Mayor número de factura emitida del punto de venta, o 0 si no hay ninguna (los borradores tienen número nulo
     * y no cuentan; las anuladas conservan el suyo). Se llama con el punto de venta ya bloqueado.
     */
    @Query("select coalesce(max(f.numero), 0) from FacturaVenta f where f.puntoVenta = :puntoVenta")
    Long maximoNumero(@Param("puntoVenta") PuntoVenta puntoVenta);
}
