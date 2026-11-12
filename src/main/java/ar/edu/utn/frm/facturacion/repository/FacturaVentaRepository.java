package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {
}
