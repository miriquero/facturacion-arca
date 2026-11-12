package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}
