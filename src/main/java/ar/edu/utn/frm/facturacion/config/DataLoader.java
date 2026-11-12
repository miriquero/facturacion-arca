package ar.edu.utn.frm.facturacion.config;

import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.CondicionIva;
import ar.edu.utn.frm.facturacion.model.Contacto;
import ar.edu.utn.frm.facturacion.model.Domicilio;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
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
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga PROVISORIA de datos de ejemplo (etapa 1): usuarios, catálogos, clientes y seis facturas con
 * importes escritos como literales. No calcula importes ni aplica reglas de numeración o CAE: son datos.
 * En la etapa 4 el bloque de facturas se reemplaza por llamadas a {@code FacturaService} (DD-11).
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

    public DataLoader(UsuarioRepository usuarioRepository, PuntoVentaRepository puntoVentaRepository,
            CondicionIvaRepository condicionIvaRepository, TipoMonedaRepository tipoMonedaRepository,
            RubroRepository rubroRepository, MarcaRepository marcaRepository, ArticuloRepository articuloRepository,
            ListaPrecioRepository listaPrecioRepository, ListaPrecioArticuloRepository listaPrecioArticuloRepository,
            ClienteRepository clienteRepository, FacturaVentaRepository facturaVentaRepository) {
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
            LOG.info("[DataLoader] Datos de ejemplo cargados.");
        } finally {
            UsuarioActual.clear();
        }
    }

    /** Catálogos y clientes que las facturas necesitan referenciar. */
    private record Base(PuntoVenta pv1, PuntoVenta pv2, CondicionIva condicion, TipoMoneda pesos,
            TipoMoneda dolares, ListaPrecioArticulo martillo, ListaPrecioArticulo televisor,
            ListaPrecioArticulo cable) {
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

        ListaPrecio general = listaPrecioRepository.save(listaPrecio("GENERAL", "Lista general"));
        ListaPrecioArticulo precioMartillo = listaPrecioArticuloRepository.save(precio(general, martillo, 3000));
        ListaPrecioArticulo precioTelevisor = listaPrecioArticuloRepository.save(precio(general, televisor, 60000));
        ListaPrecioArticulo precioCable = listaPrecioArticuloRepository.save(precio(general, cable, 8000));
        return new Base(pv1, pv2, consumidorFinal, pesos, dolares, precioMartillo, precioTelevisor, precioCable);
    }

    private Cliente[] cargarClientes() {
        return new Cliente[] {
            clienteRepository.save(cliente("Juan Servicios", "20-12345678-9", "juan@ejemplo.com", "San Martín", "100")),
            clienteRepository.save(cliente("María Electrónica", "27-87654321-0", "maria@ejemplo.com", "Belgrano", "250"))
        };
    }

    /**
     * Seis facturas de ejemplo con importes LITERALES (IVA 21 % ya incluido en los números): no se calcula nada.
     * Numeración y CAE son datos escritos a mano; la lógica real llega en la etapa 4.
     */
    private void cargarFacturas(Base b, Cliente juan, Cliente maria) {
        // EMITIDA nº 1, PV 1: 2 martillos
        factura(b, b.pv1(), 1L, 60, juan, b.pesos(), EstadoFactura.EMITIDA, "70000000000001", 7260,
                renglon(b.martillo(), "Martillo", 2, 3000, 6000, 1260, 7260));
        // EMITIDA nº 2, PV 1, sin cliente: 1 televisor
        factura(b, b.pv1(), 2L, 40, null, b.pesos(), EstadoFactura.EMITIDA, "70000000000002", 72600,
                renglon(b.televisor(), "Televisor", 1, 60000, 60000, 12600, 72600));
        // ANULADA nº 3, PV 1: 3 cables
        FacturaVenta anulada = factura(b, b.pv1(), 3L, 30, maria, b.pesos(), EstadoFactura.ANULADA, "70000000000003",
                29040, renglon(b.cable(), "Cable HDMI", 3, 8000, 24000, 5040, 29040));
        anulada.setFechaAnulacion(LocalDate.now().minusDays(29));
        // EMITIDA nº 1, PV 2, en dólares: 1 cable
        factura(b, b.pv2(), 1L, 20, juan, b.dolares(), EstadoFactura.EMITIDA, "70000000000004", 9680,
                renglon(b.cable(), "Cable HDMI", 1, 8000, 8000, 1680, 9680));
        // BORRADOR con cliente: 1 martillo y 2 cables
        factura(b, b.pv1(), null, 5, juan, b.pesos(), EstadoFactura.BORRADOR, null, 22990,
                renglon(b.martillo(), "Martillo", 1, 3000, 3000, 630, 3630),
                renglon(b.cable(), "Cable HDMI", 2, 8000, 16000, 3360, 19360));
        // BORRADOR sin cliente: 1 televisor
        factura(b, b.pv2(), null, 1, null, b.pesos(), EstadoFactura.BORRADOR, null, 72600,
                renglon(b.televisor(), "Televisor", 1, 60000, 60000, 12600, 72600));
    }

    private FacturaVenta factura(Base b, PuntoVenta puntoVenta, Long numero, int diasAtras, Cliente cliente, TipoMoneda moneda,
            EstadoFactura estado, String cae, double total, FacturaVentaDetalle... renglones) {
        FacturaVenta f = new FacturaVenta();
        f.setPuntoVenta(puntoVenta);
        f.setNumero(numero);
        f.setFechaEmision(LocalDate.now().minusDays(diasAtras));
        f.setCliente(cliente);
        f.setCondicionIva(b.condicion());
        f.setTipoMoneda(moneda);
        f.setEstado(estado);
        f.setImporteTotal(total);
        f.setImporteCobrado(0);
        f.setImporteSaldo(total);
        if (cae != null) {
            f.setCae(cae);
            f.setCaeFechaVencimiento(f.getFechaEmision().plusDays(10));
            f.setResultadoAfip("A");
        }
        for (FacturaVentaDetalle renglon : renglones) {
            f.agregarDetalle(renglon);
        }
        return facturaVentaRepository.save(f);
    }

    private FacturaVentaDetalle renglon(ListaPrecioArticulo precio, String descripcion, double cantidad,
            double precioUnitario, double neto, double iva, double subtotal) {
        FacturaVentaDetalle d = new FacturaVentaDetalle();
        d.setListaPrecioArticulo(precio);
        d.setDescripcion(descripcion);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(precioUnitario);
        d.setImporteNeto(neto);
        d.setImporteIva(iva);
        d.setImporteSubtotal(subtotal);
        return d;
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
