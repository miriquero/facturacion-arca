package ar.edu.utn.frm.facturacion.concurrencia;

import ar.edu.utn.frm.facturacion.dto.FacturaDetalleRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.CondicionIva;
import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import ar.edu.utn.frm.facturacion.model.Usuario;
import ar.edu.utn.frm.facturacion.model.UsuarioActual;
import ar.edu.utn.frm.facturacion.repository.ArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.CondicionIvaRepository;
import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioRepository;
import ar.edu.utn.frm.facturacion.repository.PuntoVentaRepository;
import ar.edu.utn.frm.facturacion.repository.TipoMonedaRepository;
import ar.edu.utn.frm.facturacion.repository.UsuarioRepository;
import ar.edu.utn.frm.facturacion.service.FacturaService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base de la suite de concurrencia. NO es transaccional: las transacciones de los hilos tienen que confirmarse de
 * verdad. Cada prueba crea sus propios datos (puntos de venta 9001 y 9002, catalogos con prefijo CONC) y los borra
 * al empezar y al terminar, porque la base PostgreSQL puede tener datos de corridas anteriores de la aplicacion.
 */
@Tag("concurrencia")
@SpringBootTest
@ActiveProfiles("test")
abstract class ConcurrenciaBase {

    static final int NUMERO_PV_A = 9001;
    static final int NUMERO_PV_B = 9002;
    static final long MAXIMO_SEGUNDOS = 30;

    @DynamicPropertySource
    static void motor(DynamicPropertyRegistry registro) {
        MotorDePruebas.registrar(registro);
    }

    @Autowired
    protected FacturaService facturaService;
    @Autowired
    protected FacturaVentaRepository facturaRepository;
    @Autowired
    protected PuntoVentaRepository puntoVentaRepository;
    @Autowired
    protected UsuarioRepository usuarioRepository;
    @Autowired
    protected CondicionIvaRepository condicionRepository;
    @Autowired
    protected TipoMonedaRepository monedaRepository;
    @Autowired
    protected ListaPrecioRepository listaRepository;
    @Autowired
    protected ArticuloRepository articuloRepository;
    @Autowired
    protected ListaPrecioArticuloRepository precioRepository;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected PlatformTransactionManager transacciones;

    protected Usuario sistema;
    protected boolean sistemaCreadoPorLaPrueba;
    protected PuntoVenta puntoA;
    protected PuntoVenta puntoB;
    protected CondicionIva condicion;
    protected TipoMoneda moneda;
    protected ListaPrecioArticulo precio;

    @BeforeEach
    void prepararDatosPropios() {
        limpiarDatosPropios();
        sistema = usuarioRepository.findByUsuario("sistema").orElse(null);
        sistemaCreadoPorLaPrueba = sistema == null;
        if (sistemaCreadoPorLaPrueba) {
            sistema = usuarioRepository.save(DatosDePrueba.usuario("sistema"));
        }
        UsuarioActual.set(sistema);
        puntoA = puntoVentaRepository.save(puntoVenta(NUMERO_PV_A));
        puntoB = puntoVentaRepository.save(puntoVenta(NUMERO_PV_B));
        condicion = condicionRepository.save(DatosDePrueba.condicionIva(5, "CONC-IVA"));
        moneda = monedaRepository.save(DatosDePrueba.tipoMoneda("PES", "CONC-MONEDA", "$"));
        ListaPrecio lista = listaRepository.save(DatosDePrueba.listaPrecio("CONC-LISTA", "CONC-LISTA"));
        Articulo articulo = articuloRepository.save(DatosDePrueba.articulo("CONC-ART", "CONC-ARTICULO", null, null, null));
        precio = precioRepository.save(DatosDePrueba.precio(lista, articulo, 1000));
    }

    @AfterEach
    void borrarDatosPropios() {
        UsuarioActual.clear();
        limpiarDatosPropios();
        if (sistemaCreadoPorLaPrueba) {
            jdbc.update("delete from usuario where usuario = 'sistema'");
        }
    }

    private static PuntoVenta puntoVenta(int numero) {
        PuntoVenta punto = DatosDePrueba.puntoVenta(numero);
        punto.setDescripcion("CONC-PV " + numero);
        return punto;
    }

    /** Borra solo lo que crean estas pruebas (por numero de punto de venta y prefijo CONC). */
    protected void limpiarDatosPropios() {
        String puntos = "select id from punto_venta where numero in (" + NUMERO_PV_A + ", " + NUMERO_PV_B + ")";
        jdbc.update("delete from factura_venta_detalle where factura_id in"
                + " (select id from factura_venta where punto_venta_id in (" + puntos + "))");
        jdbc.update("delete from factura_venta where punto_venta_id in (" + puntos + ")");
        jdbc.update("delete from punto_venta where numero in (" + NUMERO_PV_A + ", " + NUMERO_PV_B + ")");
        jdbc.update("delete from lista_precio_articulo where articulo_id in"
                + " (select id from articulo where codigo = 'CONC-ART')");
        jdbc.update("delete from articulo where codigo = 'CONC-ART'");
        jdbc.update("delete from lista_precio where codigo = 'CONC-LISTA'");
        jdbc.update("delete from condicion_iva where denominacion = 'CONC-IVA'");
        jdbc.update("delete from tipo_moneda where denominacion = 'CONC-MONEDA'");
    }

    /** Crea un borrador de un renglon en el punto de venta dado, por el servicio (en el hilo de la prueba). */
    protected Long crearBorrador(PuntoVenta punto) {
        FacturaRequestDTO solicitud = new FacturaRequestDTO(null, condicion.getId(), punto.getId(), moneda.getId(),
                null, null, List.of(new FacturaDetalleRequestDTO(precio.getId(), BigDecimal.ONE, null, null)));
        return facturaService.crear(solicitud).getId();
    }

    protected TransactionTemplate plantilla() {
        return new TransactionTemplate(transacciones);
    }

    /** Un hilo de la prueba no hereda el contexto de auditoria: cada tarea lo fija y lo limpia. */
    protected <T> java.util.concurrent.Callable<T> comoSistema(java.util.concurrent.Callable<T> tarea) {
        Usuario usuario = sistema;
        return () -> {
            UsuarioActual.set(usuario);
            try {
                return tarea.call();
            } finally {
                UsuarioActual.clear();
            }
        };
    }

    protected List<Long> numerosEmitidos(PuntoVenta punto) {
        return jdbc.queryForList("select numero from factura_venta where punto_venta_id = ? and numero is not null"
                + " order by numero", Long.class, punto.getId());
    }
}
