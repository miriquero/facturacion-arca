package ar.edu.utn.frm.facturacion.repository;

import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {

    /** Ofertas con artículo y lista ya cargados (una sola consulta, sin N+1), ordenadas por id. */
    @Query("select lpa from ListaPrecioArticulo lpa join fetch lpa.articulo join fetch lpa.listaPrecio order by lpa.id")
    List<ListaPrecioArticulo> findAllConArticuloYLista();
}
