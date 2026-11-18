package ar.edu.utn.frm.facturacion.exception;

import ar.edu.utn.frm.facturacion.report.GlifoNoSoportadoException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones a {@link ProblemDetail} (RFC 7807) con una marca temporal. Extiende
 * {@link ResponseEntityExceptionHandler} para que también los errores propios de Spring MVC (405, tipos
 * incompatibles, etc.) salgan con el mismo formato.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock reloj;

    public ApiExceptionHandler(Clock reloj) {
        this.reloj = reloj;
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    ResponseEntity<Object> parametroInvalido(ParametroInvalidoException ex, WebRequest request) {
        return respuesta(ex, HttpStatus.BAD_REQUEST, "Parámetro inválido", ex.getMessage(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<Object> noEncontrado(RecursoNoEncontradoException ex, WebRequest request) {
        return respuesta(ex, HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    /** Operación incompatible con el estado actual de la factura u otra regla de negocio: 409 Conflict. */
    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<Object> reglaDeNegocio(ReglaNegocioException ex, WebRequest request) {
        return respuesta(ex, HttpStatus.CONFLICT, "Conflicto con el estado de la factura", ex.getMessage(), request);
    }

    /** Un importe no entra sin pérdida en un campo {@code double}: conflicto de negocio, nada se guarda. */
    @ExceptionHandler(ImporteNoRepresentableException.class)
    ResponseEntity<Object> importeNoRepresentable(ImporteNoRepresentableException ex, WebRequest request) {
        return respuesta(ex, HttpStatus.CONFLICT, "Importe no representable", ex.getMessage(), request);
    }

    /** Decisión D9: el problema está en los datos pedidos en PDF, no en el servidor; ver IN-03 (pendiente). */
    @ExceptionHandler(GlifoNoSoportadoException.class)
    ResponseEntity<Object> glifoNoSoportado(GlifoNoSoportadoException ex, WebRequest request) {
        return respuesta(ex, HttpStatus.BAD_REQUEST, "PDF no disponible para estos datos", ex.getMessage(), request);
    }

    /** Red de seguridad: el cuerpo nunca lleva el mensaje original; se registra en el log para diagnosticarlo. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> errorInesperado(Exception ex, WebRequest request) {
        LOG.error("Error inesperado al atender la petición", ex);
        return respuesta(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Intente nuevamente.", request);
    }

    private ResponseEntity<Object> respuesta(Exception ex, HttpStatus estado, String titulo, String detalle,
            WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return handleExceptionInternal(ex, problema, new HttpHeaders(), estado, request);
    }

    /**
     * Todo cuerpo de error pasa por acá. Los errores propios de Spring MVC (405, etc.) llegan con cuerpo nulo y el
     * {@code ProblemDetail} lo crea recién la superclase, así que la marca temporal se agrega sobre el resultado.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object cuerpo, HttpHeaders encabezados,
            HttpStatusCode estado, WebRequest request) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, cuerpo, encabezados, estado, request);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema) {
            problema.setProperty("timestamp", Instant.now(reloj));
        }
        return respuesta;
    }

    /** Parámetro o variable de ruta con un valor que no se puede convertir (fecha mal formada, texto en un número). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders encabezados,
            HttpStatusCode estado, WebRequest request) {
        String nombre = ex instanceof MethodArgumentTypeMismatchException mm ? mm.getName() : ex.getPropertyName();
        String detalle = "El parámetro '" + nombre + "' tiene un valor inválido: '" + ex.getValue()
                + "'. Formato esperado: " + formatoEsperado(ex.getRequiredType()) + ".";
        return respuesta(ex, HttpStatus.BAD_REQUEST, "Parámetro inválido", detalle, request);
    }

    private static String formatoEsperado(Class<?> tipo) {
        if (tipo == null) {
            return "un valor del tipo correcto";
        }
        if (LocalDate.class.isAssignableFrom(tipo)) {
            return "yyyy-MM-dd";
        }
        if (Number.class.isAssignableFrom(tipo) || tipo.isPrimitive()) {
            return "número";
        }
        return tipo.getSimpleName();
    }

    /**
     * Bean Validation fallida: 400 con la propiedad {@code errores} (ruta del campo, p. ej.
     * {@code detalles[0].cantidad}, a mensaje). Si un campo tiene varios errores se unen con {@code "; "}.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders encabezados, HttpStatusCode estado, WebRequest request) {
        Map<String, String> errores = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.merge(error.getField(), String.valueOf(error.getDefaultMessage()), (a, b) -> a + "; " + b);
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La petición tiene datos inválidos. Revise el detalle por campo.");
        problema.setTitle("Datos inválidos");
        problema.setProperty("errores", errores);
        return handleExceptionInternal(ex, problema, encabezados, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * JSON malformado, incompatible con el DTO o ausente: 400 con un mensaje propio. Nunca se usa
     * {@code ex.getMessage()}, que trae el texto del analizador (clases, línea y columna).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders encabezados, HttpStatusCode estado, WebRequest request) {
        String detalle = "El cuerpo de la petición falta o no es un JSON válido.";
        if (ex.getCause() instanceof MismatchedInputException incompatible && !incompatible.getPath().isEmpty()) {
            detalle = "El campo '" + rutaDelCampo(incompatible) + "' tiene un valor incompatible.";
        }
        return respuesta(ex, HttpStatus.BAD_REQUEST, "Cuerpo ilegible", detalle, request);
    }

    /** Ruta del campo en el JSON, p. ej. {@code detalles[0].cantidad}. */
    private static String rutaDelCampo(MismatchedInputException ex) {
        StringBuilder ruta = new StringBuilder();
        for (var referencia : ex.getPath()) {
            if (referencia.getFieldName() != null) {
                if (!ruta.isEmpty()) {
                    ruta.append('.');
                }
                ruta.append(referencia.getFieldName());
            } else if (referencia.getIndex() >= 0) {
                ruta.append('[').append(referencia.getIndex()).append(']');
            }
        }
        return ruta.toString();
    }
}
