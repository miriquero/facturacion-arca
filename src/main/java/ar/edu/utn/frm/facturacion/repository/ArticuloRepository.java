package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.Articulo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {
}
