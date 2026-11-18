package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Busca por el identificador de usuario (columna {@code usuario}, única). */
    Optional<Usuario> findByUsuario(String usuario);
}
