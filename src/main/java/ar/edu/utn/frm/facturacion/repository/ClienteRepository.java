package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
