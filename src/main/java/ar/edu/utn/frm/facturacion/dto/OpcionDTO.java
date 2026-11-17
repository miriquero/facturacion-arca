package ar.edu.utn.frm.facturacion.dto;

/**
 * Opción de un catálogo para listas desplegables. {@code valor} (precio) y {@code codigoMoneda} son nulos
 * salvo en los catálogos que los definen.
 */
public record OpcionDTO(Long id, String etiqueta, Double valor, String codigoMoneda) {
}
