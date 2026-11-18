package ar.edu.utn.frm.facturacion.config;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import static org.assertj.core.api.Assertions.within;
import ar.edu.utn.frm.facturacion.model.AuditoriaApp;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sin perfil: el runner corre al levantar el contexto, como en el arranque real de la aplicación, con el contexto
 * completo (el loader usa FacturaService) y una base embebida propia (no la compartida {@code facturacion}). Cada
 * prueba es transaccional y se revierte; la carga ya quedó confirmada al arrancar.
 */
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
class DataLoaderTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TipoMonedaRepository tipoMonedaRepository;
    @Autowired
    private PuntoVentaRepository puntoVentaRepository;
    @Autowired
    private CondicionIvaRepository condicionIvaRepository;
    @Autowired
    private ArticuloRepository articuloRepository;
    @Autowired
    private ListaPrecioRepository listaPrecioRepository;
    @Autowired
    private ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private MarcaRepository marcaRepository;
    @Autowired
    private RubroRepository rubroRepository;
    @Autowired
    private FacturaVentaRepository facturaVentaRepository;
    @Autowired
    private DataLoader dataLoader;
    @Autowired
    private Clock reloj;

    @Test
    void creaExactamenteLosTresUsuariosDeEjemplo() {
        assertThat(usuarioRepository.findAll()).extracting(Usuario::getUsuario)
                .containsExactlyInAnyOrder("sistema", "acortez", "mriquero");
    }

    @Test
    void dejaElContextoDeAuditoriaVacio() {
        assertThat(UsuarioActual.get()).isNull();
    }

    @Test
    void cargaLasMonedasPesoYDolar() {
        assertThat(tipoMonedaRepository.findAll()).extracting(TipoMoneda::getCodigoAfip)
                .contains("PES", "DOL");
    }

    @Test
    void cargaPuntosDeVentaYCondicionesDeIva() {
        assertThat(puntoVentaRepository.count()).isGreaterThanOrEqualTo(2);
        assertThat(condicionIvaRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(listaPrecioRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(marcaRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(rubroRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void cargaArticulosConPrecioEnUnaListaYAlgunoSinMarca() {
        List<Articulo> articulos = articuloRepository.findAll();

        assertThat(articulos).hasSizeGreaterThanOrEqualTo(3);
        assertThat(articulos).anyMatch(a -> a.getMarca() == null);
        assertThat(listaPrecioArticuloRepository.count()).isEqualTo(articulos.size());
    }

    @Test
    void cargaAlMenosDosClientesConContactoYDomicilio() {
        var clientes = clienteRepository.findAll();

        assertThat(clientes).hasSizeGreaterThanOrEqualTo(2);
        assertThat(clientes).allSatisfy(c -> {
            assertThat(c.getContacto().getEmail()).isNotBlank();
            assertThat(c.getDomicilio().getNombreCalle()).isNotBlank();
        });
    }

    @Test
    void todoLoCargadoTieneUsuarioDeCarga() {
        List<AuditoriaApp> cargados = new java.util.ArrayList<>();
        cargados.addAll(tipoMonedaRepository.findAll());
        cargados.addAll(puntoVentaRepository.findAll());
        cargados.addAll(condicionIvaRepository.findAll());
        cargados.addAll(articuloRepository.findAll());
        cargados.addAll(listaPrecioRepository.findAll());
        cargados.addAll(listaPrecioArticuloRepository.findAll());
        cargados.addAll(clienteRepository.findAll());
        cargados.addAll(marcaRepository.findAll());
        cargados.addAll(rubroRepository.findAll());

        assertThat(cargados).isNotEmpty().allSatisfy(e -> {
            assertThat(e.getUsuarioCarga()).isNotNull();
            assertThat(e.getUsuarioCarga().getUsuario()).isEqualTo("sistema");
            assertThat(e.getFechaAlta()).isNotNull();
        });
    }

    private List<FacturaVenta> facturas(EstadoFactura estado) {
        return facturaVentaRepository.findAll().stream().filter(f -> f.getEstado() == estado).toList();
    }

    @Test
    void cargaSeisFacturasTresEmitidasUnaAnuladaYDosBorradores() {
        assertThat(facturaVentaRepository.count()).isEqualTo(6);
        assertThat(facturas(EstadoFactura.EMITIDA)).hasSize(3);
        assertThat(facturas(EstadoFactura.ANULADA)).hasSize(1);
        assertThat(facturas(EstadoFactura.BORRADOR)).hasSize(2);
    }

    @Test
    void losBorradoresNoTienenNumeroNiCae() {
        assertThat(facturas(EstadoFactura.BORRADOR)).hasSize(2).allSatisfy(f -> {
            assertThat(f.getNumero()).isNull();
            assertThat(f.getCae()).isNull();
            assertThat(f.getCaeFechaVencimiento()).isNull();
            assertThat(f.getResultadoAfip()).isNull();
        });
    }

    @Test
    void lasEmitidasYAnuladasTienenNumeroYCaeDeCatorceDigitos() {
        List<FacturaVenta> numeradas = facturaVentaRepository.findAll().stream()
                .filter(f -> f.getEstado() != EstadoFactura.BORRADOR).toList();

        assertThat(numeradas).hasSize(4).allSatisfy(f -> {
            assertThat(f.getNumero()).isNotNull();
            assertThat(f.getCae()).matches("[0-9]{14}");
            assertThat(f.getCaeFechaVencimiento()).isEqualTo(LocalDate.now(reloj).plusDays(10));
        });
        assertThat(facturas(EstadoFactura.ANULADA)).allSatisfy(f -> assertThat(f.getFechaAnulacion()).isNotNull());
    }

    @Test
    void lasFacturasTienenRenglonesImportesCoherentesYCobradoEnCero() {
        assertThat(facturaVentaRepository.findAll()).hasSize(6).allSatisfy(f -> {
            assertThat(f.getDetalles()).isNotEmpty();
            double suma = f.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteSubtotal).sum();
            assertThat(f.getImporteTotal()).isCloseTo(suma, within(0.001));
            assertThat(f.getImporteCobrado()).isZero();
            assertThat(f.getImporteSaldo()).isEqualTo(f.getImporteTotal());
            assertThat(f.getUsuarioCarga().getUsuario()).isEqualTo("sistema");
        });
    }

    @Test
    void hayFacturasSinClienteYUnaEnDolares() {
        assertThat(facturaVentaRepository.findAll()).anyMatch(f -> f.getCliente() == null)
                .anyMatch(f -> "DOL".equals(f.getTipoMoneda().getCodigoAfip()));
        assertThat(facturas(EstadoFactura.BORRADOR)).anyMatch(f -> f.getCliente() == null);
    }

    @Test
    void ejecutarLaCargaDosVecesNoDuplicaNada() {
        long usuarios = usuarioRepository.count();
        long catalogos = puntoVentaRepository.count() + tipoMonedaRepository.count() + articuloRepository.count()
                + listaPrecioArticuloRepository.count() + clienteRepository.count();
        long facturas = facturaVentaRepository.count();

        dataLoader.run();

        assertThat(usuarioRepository.count()).isEqualTo(usuarios).isEqualTo(3);
        assertThat(puntoVentaRepository.count() + tipoMonedaRepository.count() + articuloRepository.count()
                + listaPrecioArticuloRepository.count() + clienteRepository.count()).isEqualTo(catalogos);
        assertThat(facturaVentaRepository.count()).isEqualTo(facturas).isEqualTo(6);
        assertThat(UsuarioActual.get()).isNull();
    }

    // --- DataLoader integrado con FacturaService (DD-11): reglas reales de numeración, importes y baja ---

    private List<Long> numeros(int numeroPuntoVenta) {
        return facturaVentaRepository.findAll().stream()
                .filter(f -> f.getPuntoVenta().getNumero() == numeroPuntoVenta && f.getNumero() != null)
                .map(FacturaVenta::getNumero).sorted().toList();
    }

    @Test
    void laNumeracionEsLaRealPorPuntoDeVentaYElResultadoEsA() {
        assertThat(numeros(1)).containsExactly(1L, 2L, 3L);
        assertThat(numeros(2)).containsExactly(1L);
        assertThat(facturaVentaRepository.findAll().stream().filter(f -> f.getNumero() != null))
                .allSatisfy(f -> assertThat(f.getResultadoAfip()).isEqualTo("A"));
    }

    @Test
    void losImportesSonLosCalculadosPorElServicio() {
        var mixta = facturaVentaRepository.findAll().stream()
                .filter(f -> f.getEstado() == EstadoFactura.BORRADOR && f.getDetalles().size() == 2)
                .findFirst().orElseThrow();

        assertThat(mixta.getDetalles()).extracting(FacturaVentaDetalle::getImporteSubtotal)
                .containsExactlyInAnyOrder(3630.0, 19360.0);
        assertThat(mixta.getImporteTotal()).isEqualTo(22990.0);
        assertThat(facturaVentaRepository.findAll()).allSatisfy(f -> {
            assertThat(f.getImporteCobrado()).isZero();
            assertThat(f.getImporteSaldo()).isEqualTo(f.getImporteTotal());
        });
        assertThat(facturaVentaRepository.findAll()).extracting(FacturaVenta::getImporteTotal)
                .containsExactlyInAnyOrder(7260.0, 72600.0, 29040.0, 9680.0, 22990.0, 72600.0);
    }

    @Test
    void laAnuladaTieneBajaLogicaConFechaDeHoyYUsuarioSistema() {
        FacturaVenta anulada = facturas(EstadoFactura.ANULADA).get(0);

        assertThat(anulada.getFechaAnulacion()).isEqualTo(LocalDate.now(reloj));
        assertThat(anulada.getFechaBaja()).isNotNull();
        assertThat(anulada.getUsuarioBaja().getUsuario()).isEqualTo("sistema");
        assertThat(anulada.getNumero()).isEqualTo(3L);
        assertThat(anulada.getObservaciones()).isNotBlank();
        assertThat(facturas(EstadoFactura.EMITIDA)).allSatisfy(f -> assertThat(f.getFechaBaja()).isNull());
    }
}
