package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {

    /** Ofertas con artículo y lista ya cargados (una sola consulta, sin N+1), ordenadas por id. */
    @Query("select lpa from ListaPrecioArticulo lpa join fetch lpa.articulo join fetch lpa.listaPrecio order by lpa.id")
    List<ListaPrecioArticulo> findAllConArticuloYLista();

    /**
     * Ofertas cuyo código o denominación de artículo, o código de lista, contiene el texto (sin distinguir
     * mayúsculas), con {@code join fetch}, por id. El patrón llega armado y escapado con {@code \}.
     */
    @Query("select lpa from ListaPrecioArticulo lpa join fetch lpa.articulo a join fetch lpa.listaPrecio l "
            + "where lower(a.codigo) like :patron escape '\\' or lower(a.denominacion) like :patron escape '\\' "
            + "or lower(l.codigo) like :patron escape '\\' order by lpa.id")
    List<ListaPrecioArticulo> buscarConArticuloYLista(@Param("patron") String patron);
}
