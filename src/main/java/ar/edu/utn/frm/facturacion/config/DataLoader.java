package ar.edu.utn.frm.facturacion.config;

import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.CondicionIva;
import ar.edu.utn.frm.facturacion.model.Contacto;
import ar.edu.utn.frm.facturacion.model.Domicilio;
import ar.edu.utn.frm.facturacion.dto.CambioEstadoRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.Marca;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import ar.edu.utn.frm.facturacion.model.Rubro;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.ArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.ClienteRepository;
import ar.edu.utn.frm.facturacion.repository.CondicionIvaRepository;
import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioRepository;
import ar.edu.utn.frm.facturacion.repository.MarcaRepository;
import ar.edu.utn.frm.facturacion.repository.PuntoVentaRepository;
import ar.edu.utn.frm.facturacion.repository.RubroRepository;
import ar.edu.utn.frm.facturacion.repository.TipoMonedaRepository;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga de datos de ejemplo: usuarios, catálogos, clientes y facturas creadas con {@code FacturaService}
 * (etapa 4, DD-11), de modo que importes, numeración, CAE y baja lógica siguen las reglas reales de la API.
 * Todo ocurre en una transacción y con el usuario {@code sistema} en el contexto de auditoría, que se limpia al terminar.
 * Se omite con el perfil {@code test} y si la base ya tiene usuarios (no repara cargas parciales).
 */
@Component
@Profile("!test")
public class DataLoader implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoader.class);

    private final UsuarioRepository usuarioRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final RubroRepository rubroRepository;
    private final MarcaRepository marcaRepository;
    private final ArticuloRepository articuloRepository;
    private final ListaPrecioRepository listaPrecioRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final ClienteRepository clienteRepository;
    private final FacturaVentaRepository facturaVentaRepository;
    private final FacturaService facturaService;
    private final Clock reloj;

    public DataLoader(UsuarioRepository usuarioRepository, PuntoVentaRepository puntoVentaRepository,
            CondicionIvaRepository condicionIvaRepository, TipoMonedaRepository tipoMonedaRepository,
            RubroRepository rubroRepository, MarcaRepository marcaRepository, ArticuloRepository articuloRepository,
            ListaPrecioRepository listaPrecioRepository, ListaPrecioArticuloRepository listaPrecioArticuloRepository,
            ClienteRepository clienteRepository, FacturaVentaRepository facturaVentaRepository,
            FacturaService facturaService, Clock reloj) {
        this.usuarioRepository = usuarioRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.rubroRepository = rubroRepository;
        this.marcaRepository = marcaRepository;
        this.articuloRepository = articuloRepository;
        this.listaPrecioRepository = listaPrecioRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.clienteRepository = clienteRepository;
        this.facturaVentaRepository = facturaVentaRepository;
        this.facturaService = facturaService;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            LOG.info("[DataLoader] Ya hay usuarios cargados: no se agrega nada.");
            return;
        }
        Usuario sistema = usuarioRepository.save(usuario("sistema", "Sistema", "Interno"));
        usuarioRepository.save(usuario("acortez", "Ana", "Cortez"));
        usuarioRepository.save(usuario("mriquero", "Martín", "Riquero"));

        UsuarioActual.set(sistema);
        try {
            Base base = cargarCatalogos();
            Cliente[] clientes = cargarClientes();
            cargarFacturas(base, clientes[0], clientes[1]);
            // Flush con el usuario aún en el contexto: los UPDATE pendientes (p. ej. los que completan la
            // modificación) se emiten acá y no al confirmar la transacción, cuando el finally ya lo limpió.
            facturaVentaRepository.flush();
            LOG.info("[DataLoader] Datos de ejemplo cargados.");
        } finally {
            UsuarioActual.clear();
        }
    }

    /** Catálogos que las facturas necesitan referenciar. */
    private record Base(PuntoVenta pv1, PuntoVenta pv2, CondicionIva condicion, TipoMoneda pesos,
            TipoMoneda dolares, ListaPrecioArticulo martillo, ListaPrecioArticulo televisor,
            ListaPrecioArticulo cable, ListaPrecioArticulo taladro, ListaPrecioArticulo cinta,
            ListaPrecioArticulo teclado, ListaPrecioArticulo mouse) {
    }

    private Base cargarCatalogos() {
        PuntoVenta pv1 = puntoVentaRepository.save(puntoVenta(1, "Casa Central"));
        PuntoVenta pv2 = puntoVentaRepository.save(puntoVenta(2, "Sucursal Norte"));
        CondicionIva consumidorFinal = condicionIvaRepository.save(condicionIva(5, "Consumidor Final"));
        condicionIvaRepository.save(condicionIva(1, "Responsable Inscripto"));
        TipoMoneda pesos = tipoMonedaRepository.save(moneda("PES", "Peso argentino", "$"));
        TipoMoneda dolares = tipoMonedaRepository.save(moneda("DOL", "Dólar estadounidense", "US$"));

        Rubro ferreteria = rubroRepository.save(rubro("Ferretería", 10));
        Rubro electronica = rubroRepository.save(rubro("Electrónica", 20));
        Marca acme = marcaRepository.save(marca("Acme", 100));
        Marca tecnoSur = marcaRepository.save(marca("TecnoSur", 200));

        Articulo martillo = articuloRepository.save(articulo("ART-001", "Martillo", ferreteria, acme));
        Articulo televisor = articuloRepository.save(articulo("ART-002", "Televisor", electronica, tecnoSur));
        Articulo cable = articuloRepository.save(articulo("ART-003", "Cable HDMI", electronica, null));
        Articulo taladro = articuloRepository.save(articulo("ART-004", "Taladro eléctrico", ferreteria, acme));
        Articulo cinta = articuloRepository.save(articulo("ART-005", "Cinta métrica", ferreteria, acme));
        Articulo teclado = articuloRepository.save(articulo("ART-006", "Teclado USB", electronica, tecnoSur));
        Articulo mouse = articuloRepository.save(articulo("ART-007", "Mouse inalámbrico", electronica, tecnoSur));

        ListaPrecio general = listaPrecioRepository.save(listaPrecio("GENERAL", "Lista general"));
        ListaPrecioArticulo precioMartillo = listaPrecioArticuloRepository.save(precio(general, martillo, 3000));
        ListaPrecioArticulo precioTelevisor = listaPrecioArticuloRepository.save(precio(general, televisor, 60000));
        ListaPrecioArticulo precioCable = listaPrecioArticuloRepository.save(precio(general, cable, 8000));
        ListaPrecioArticulo precioTaladro = listaPrecioArticuloRepository.save(precio(general, taladro, 45000));
        ListaPrecioArticulo precioCinta = listaPrecioArticuloRepository.save(precio(general, cinta, 5500));
        ListaPrecioArticulo precioTeclado = listaPrecioArticuloRepository.save(precio(general, teclado, 18000));
        ListaPrecioArticulo precioMouse = listaPrecioArticuloRepository.save(precio(general, mouse, 12500));
        return new Base(pv1, pv2, consumidorFinal, pesos, dolares, precioMartillo, precioTelevisor, precioCable,
                precioTaladro, precioCinta, precioTeclado, precioMouse);
    }

    private Cliente[] cargarClientes() {
        return new Cliente[] {
            clienteRepository.save(cliente("Juan Servicios", "20-12345678-9", "juan@ejemplo.com", "San Martín", "100")),
            clienteRepository.save(cliente("María Electrónica", "27-87654321-0", "maria@ejemplo.com", "Belgrano", "250"))
        };
    }

    /**
     * Crea y cambia el estado de las facturas de ejemplo con {@link FacturaService}.
     * Toda la carga comparte la transacción del loader.
     */
    private void cargarFacturas(Base b, Cliente juan, Cliente maria) {
        // F1: PV 1, Juan, 2 martillos -> EMITIDA nº 1 (importe 7260)
        emitir(crear(b, b.pv1(), juan, b.pesos(), 60, renglon(b.martillo(), "Martillo", "2")));
        // F2: PV 1, sin cliente, 1 televisor -> EMITIDA nº 2 (72600)
        emitir(crear(b, b.pv1(), null, b.pesos(), 40, renglon(b.televisor(), "Televisor", "1")));
        // F3: PV 1, María, 3 cables -> EMITIDA nº 3 y luego ANULADA con motivo (29040)
        Long anulada = emitir(crear(b, b.pv1(), maria, b.pesos(), 30, renglon(b.cable(), "Cable HDMI", "3")));
        facturaService.cambiarEstado(anulada, new CambioEstadoRequestDTO("ANULADA", "Anulada por error de carga"));
        // F4: PV 2, Juan, en dólares, 1 cable -> EMITIDA nº 1 de ese punto (9680)
        emitir(crear(b, b.pv2(), juan, b.dolares(), 20, renglon(b.cable(), "Cable HDMI", "1")));
        // F5: BORRADOR con cliente: 1 martillo y 2 cables (22990)
        crear(b, b.pv1(), juan, b.pesos(), 5, renglon(b.martillo(), "Martillo", "1"),
                renglon(b.cable(), "Cable HDMI", "2"));
        // F6: BORRADOR sin cliente: 1 televisor (72600)
        crear(b, b.pv2(), null, b.pesos(), 1, renglon(b.televisor(), "Televisor", "1"));

        emitir(crear(b, b.pv1(), juan, b.pesos(), 19,
                renglon(b.taladro(), "Taladro eléctrico", "1"), renglon(b.cinta(), "Cinta métrica", "2")));
        emitir(crear(b, b.pv2(), maria, b.pesos(), 17,
                renglon(b.teclado(), "Teclado USB", "3"), renglon(b.mouse(), "Mouse inalámbrico", "3")));
        emitir(crear(b, b.pv1(), null, b.pesos(), 16,
                renglon(b.cinta(), "Cinta métrica", "1"), renglon(b.martillo(), "Martillo", "2")));
        emitir(crear(b, b.pv2(), maria, b.pesos(), 14,
                renglon(b.televisor(), "Televisor", "2"), renglon(b.cable(), "Cable HDMI", "4")));
        emitir(crear(b, b.pv1(), juan, b.pesos(), 12,
                renglon(b.taladro(), "Taladro eléctrico", "2"), renglon(b.martillo(), "Martillo", "3")));
        emitir(crear(b, b.pv2(), null, b.pesos(), 10,
                renglon(b.mouse(), "Mouse inalámbrico", "1"), renglon(b.cable(), "Cable HDMI", "1")));
        emitir(crear(b, b.pv1(), maria, b.pesos(), 8,
                renglon(b.teclado(), "Teclado USB", "2"), renglon(b.mouse(), "Mouse inalámbrico", "2")));
        emitir(crear(b, b.pv2(), juan, b.pesos(), 6,
                renglon(b.cinta(), "Cinta métrica", "3"), renglon(b.taladro(), "Taladro eléctrico", "1")));
        emitir(crear(b, b.pv1(), maria, b.pesos(), 4,
                renglon(b.televisor(), "Televisor", "1"), renglon(b.teclado(), "Teclado USB", "1"),
                renglon(b.cable(), "Cable HDMI", "2")));
        Long duplicada = emitir(crear(b, b.pv2(), maria, b.pesos(), 3,
                renglon(b.teclado(), "Teclado USB", "1"), renglon(b.mouse(), "Mouse inalámbrico", "1")));
        facturaService.cambiarEstado(duplicada, new CambioEstadoRequestDTO("ANULADA", "Anulada por carga duplicada"));
        crear(b, b.pv1(), juan, b.pesos(), 2,
                renglon(b.taladro(), "Taladro eléctrico", "1"), renglon(b.cinta(), "Cinta métrica", "2"),
                renglon(b.martillo(), "Martillo", "1"));
        crear(b, b.pv2(), null, b.pesos(), 0,
                renglon(b.teclado(), "Teclado USB", "1"), renglon(b.mouse(), "Mouse inalámbrico", "1"));
    }

    private Long crear(Base b, PuntoVenta puntoVenta, Cliente cliente, TipoMoneda moneda, int diasAtras,
            FacturaDetalleRequestDTO... renglones) {
        FacturaRequestDTO solicitud = new FacturaRequestDTO(cliente == null ? null : cliente.getId(),
                b.condicion().getId(), puntoVenta.getId(), moneda.getId(), LocalDate.now(reloj).minusDays(diasAtras),
                null, List.of(renglones));
        return facturaService.crear(solicitud).getId();
    }

    private Long emitir(Long facturaId) {
        facturaService.cambiarEstado(facturaId, new CambioEstadoRequestDTO("EMITIDA", null));
        return facturaId;
    }

    private FacturaDetalleRequestDTO renglon(ListaPrecioArticulo precio, String descripcion, String cantidad) {
        return new FacturaDetalleRequestDTO(precio.getId(), new BigDecimal(cantidad), null, descripcion);
    }

    private Usuario usuario(String identificador, String nombre, String apellido) {
        Usuario u = new Usuario();
        u.setUsuario(identificador);
        u.setClave("1234");
        u.setNombre(nombre);
        u.setApellido(apellido);
        return u;
    }

    private PuntoVenta puntoVenta(int numero, String descripcion) {
        PuntoVenta p = new PuntoVenta();
        p.setNumero(numero);
        p.setDescripcion(descripcion);
        p.setTipoEmision("CAE");
        p.setDomicilioComercial("Domicilio " + numero);
        return p;
    }

    private CondicionIva condicionIva(int codigoAfip, String denominacion) {
        CondicionIva c = new CondicionIva();
        c.setCodigoAfip(codigoAfip);
        c.setDenominacion(denominacion);
        return c;
    }

    private TipoMoneda moneda(String codigoAfip, String denominacion, String simbolo) {
        TipoMoneda m = new TipoMoneda();
        m.setCodigoAfip(codigoAfip);
        m.setDenominacion(denominacion);
        m.setSimbolo(simbolo);
        return m;
    }

    private Rubro rubro(String denominacion, int codigo) {
        Rubro r = new Rubro();
        r.setDenominacion(denominacion);
        r.setCodigo(codigo);
        return r;
    }

    private Marca marca(String denominacion, int codigo) {
        Marca m = new Marca();
        m.setDenominacion(denominacion);
        m.setCodigo(codigo);
        return m;
    }

    private Articulo articulo(String codigo, String denominacion, Rubro rubro, Marca marca) {
        Articulo a = new Articulo();
        a.setCodigo(codigo);
        a.setDenominacion(denominacion);
        a.setRubro(rubro);
        a.setMarca(marca);
        return a;
    }

    private ListaPrecio listaPrecio(String codigo, String denominacion) {
        ListaPrecio l = new ListaPrecio();
        l.setCodigo(codigo);
        l.setDenominacion(denominacion);
        return l;
    }

    private ListaPrecioArticulo precio(ListaPrecio lista, Articulo articulo, double precioVenta) {
        ListaPrecioArticulo p = new ListaPrecioArticulo();
        p.setListaPrecio(lista);
        p.setArticulo(articulo);
        p.setPrecioVenta(precioVenta);
        return p;
    }

    /** Contacto y domicilio se guardan por cascada desde el cliente. */
    private Cliente cliente(String denominacion, String cuit, String email, String calle, String numero) {
        Contacto contacto = new Contacto();
        contacto.setEmail(email);
        contacto.setTelefono("2615550000");
        contacto.setCelular("2615551111");
        Domicilio domicilio = new Domicilio();
        domicilio.setNombreCalle(calle);
        domicilio.setNumeroCalle(numero);
        Cliente c = new Cliente();
        c.setDenominacion(denominacion);
        c.setCuitCuil(cuit);
        c.setContacto(contacto);
        c.setDomicilio(domicilio);
        return c;
    }
}
