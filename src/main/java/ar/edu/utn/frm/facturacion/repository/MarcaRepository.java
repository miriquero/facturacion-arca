package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.Marca;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcaRepository extends JpaRepository<Marca, Long> {
}
