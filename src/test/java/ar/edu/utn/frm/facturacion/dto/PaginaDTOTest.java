package ar.edu.utn.frm.facturacion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaginaDTOTest {

    @Test
    void veintitresElementosDeAZDiezDanTresPaginas() {
        PaginaDTO<String> pagina = PaginaDTO.de(List.of("a", "b"), SolicitudPagina.de(2, 10), 23);

        assertThat(pagina.totalPaginas()).isEqualTo(3);
        assertThat(pagina.totalElementos()).isEqualTo(23L);
        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.tamanio()).isEqualTo(10);
        assertThat(pagina.items()).containsExactly("a", "b");
    }

    @Test
    void sinElementosHayCeroPaginas() {
        assertThat(PaginaDTO.de(List.of(), SolicitudPagina.de(null, null), 0).totalPaginas()).isZero();
    }

    @Test
    void veinteElementosDeADiezDanExactamenteDosPaginas() {
        assertThat(PaginaDTO.de(List.of(), SolicitudPagina.de(0, 10), 20).totalPaginas()).isEqualTo(2);
    }

    @Test
    void unElementoDeATamanioCienDaUnaPagina() {
        assertThat(PaginaDTO.de(List.of(), SolicitudPagina.de(0, 100), 1).totalPaginas()).isEqualTo(1);
    }
}
