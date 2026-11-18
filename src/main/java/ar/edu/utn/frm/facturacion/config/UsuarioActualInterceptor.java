package ar.edu.utn.frm.facturacion.config;

import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Fija el usuario de auditoría de cada petición a partir del encabezado {@code X-Usuario}. */
@Component
public class UsuarioActualInterceptor implements HandlerInterceptor {

    static final String ENCABEZADO = "X-Usuario";

    private final UsuarioRepository usuarioRepository;
    private final FacturacionProperties propiedades;

    public UsuarioActualInterceptor(UsuarioRepository usuarioRepository, FacturacionProperties propiedades) {
        this.usuarioRepository = usuarioRepository;
        this.propiedades = propiedades;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String declarado = request.getHeader(ENCABEZADO);
        Optional<Usuario> usuario = Optional.empty();
        if (declarado != null && !declarado.isBlank()) {
            usuario = usuarioRepository.findByUsuario(declarado.strip());
        }
        // Falta, vacío o desconocido: nunca se rechaza la petición; se usa el usuario por defecto.
        // Si ni siquiera existe, el contexto queda vacío y una escritura falla por NOT NULL.
        usuario.or(() -> usuarioRepository.findByUsuario(propiedades.usuarioPorDefecto()))
                .ifPresent(UsuarioActual::set);
        return true;
    }

    /** Siempre limpia el contexto del hilo, también si el controlador lanzó una excepción. */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        UsuarioActual.clear();
    }
}
