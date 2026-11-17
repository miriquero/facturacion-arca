package ar.edu.utn.frm.facturacion.controller;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.articulo;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.cliente;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.condicionIva;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.contacto;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.domicilio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.listaPrecio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.precio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.puntoVenta;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.tipoMoneda;

import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.CondicionIva;
import ar.edu.utn.frm.facturacion.model.DatosDePrueba;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import ar.edu.utn.frm.facturacion.model.ListaPrecio;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas de integración HTTP. NO es {@code @Transactional} a propósito: una transacción de prueba
 * mantendría abierta la sesión durante la petición de MockMvc (mismo hilo) y ocultaría una
 * {@code LazyInitializationException}. Cada prueba arma sus datos con los repositorios y los borra al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegracionApiBase {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected FacturaVentaRepository facturaRepositorio;
    @Autowired
    protected ClienteRepository clienteRepositorio;
    @Autowired
    protected PuntoVentaRepository puntoVentaRepositorio;
    @Autowired
    protected CondicionIvaRepository condicionRepositorio;
    @Autowired
    protected TipoMonedaRepository monedaRepositorio;
    @Autowired
    protected ArticuloRepository articuloRepositorio;
    @Autowired
    protected ListaPrecioRepository listaRepositorio;
    @Autowired
    protected ListaPrecioArticuloRepository precioRepositorio;
    @Autowired
    protected MarcaRepository marcaRepositorio;
    @Autowired
    protected RubroRepository rubroRepositorio;
    @Autowired
    protected UsuarioRepository usuarioRepositorio;

    protected PuntoVenta casaCentral;
    protected CondicionIva responsableInscripto;
    protected TipoMoneda pesos;
    protected Cliente juan;

    @BeforeEach
    void prepararBaseDeDatos() {
        limpiarBaseDeDatos();
        Usuario sistema = usuarioRepositorio.save(DatosDePrueba.usuario("sistema"));
        UsuarioActual.set(sistema);
        PuntoVenta punto = puntoVenta(1);
        punto.setDescripcion("Casa Central");
        casaCentral = puntoVentaRepositorio.save(punto);
        responsableInscripto = condicionRepositorio.save(condicionIva(1, "Responsable Inscripto"));
        pesos = monedaRepositorio.save(tipoMoneda("PES", "Pesos", "$"));
        juan = clienteRepositorio.save(cliente("Juan Servicios", "20-12345678-9",
                contacto("juan@ejemplo.com"), domicilio("San Martín", "100")));
    }

    @AfterEach
    void limpiarAlTerminar() {
        limpiarBaseDeDatos();
        UsuarioActual.clear();
    }

    /** Borra todo en orden de dependencias (las facturas primero). */
    private void limpiarBaseDeDatos() {
        facturaRepositorio.deleteAll();
        precioRepositorio.deleteAll();
        articuloRepositorio.deleteAll();
        listaRepositorio.deleteAll();
        clienteRepositorio.deleteAll();
        puntoVentaRepositorio.deleteAll();
        condicionRepositorio.deleteAll();
        monedaRepositorio.deleteAll();
        marcaRepositorio.deleteAll();
        rubroRepositorio.deleteAll();
        usuarioRepositorio.deleteAll();
    }

    protected Articulo guardarArticulo(String codigo, String denominacion) {
        return articuloRepositorio.save(articulo(codigo, denominacion, null, null, null));
    }

    protected ListaPrecio guardarLista(String codigo, String denominacion) {
        return listaRepositorio.save(listaPrecio(codigo, denominacion));
    }

    protected ListaPrecioArticulo guardarPrecio(ListaPrecio lista, Articulo articulo, double precioVenta) {
        return precioRepositorio.save(precio(lista, articulo, precioVenta));
    }

    /** Arma (sin guardar) una factura cuyos datos son literales de la prueba (no se calcula nada). */
    protected FacturaVenta armarFactura(Long numero, LocalDate fecha, double total, EstadoFactura estado,
            Cliente cliente, FacturaVentaDetalle... renglones) {
        FacturaVenta factura = DatosDePrueba.facturaBorrador(casaCentral, responsableInscripto, pesos, cliente);
        factura.setNumero(numero);
        factura.setFechaEmision(fecha);
        factura.setImporteTotal(total);
        factura.setEstado(estado);
        for (FacturaVentaDetalle renglon : renglones) {
            factura.agregarDetalle(renglon);
        }
        return factura;
    }

    protected FacturaVenta guardarFactura(Long numero, LocalDate fecha, double total, EstadoFactura estado,
            Cliente cliente, FacturaVentaDetalle... renglones) {
        return facturaRepositorio.save(armarFactura(numero, fecha, total, estado, cliente, renglones));
    }

    /** Renglón con todos sus importes como literales. */
    protected static FacturaVentaDetalle renglon(ListaPrecioArticulo precio, String descripcion, double cantidad,
            double precioUnitario, double neto, double iva, double subtotal) {
        FacturaVentaDetalle d = DatosDePrueba.detalle(precio, cantidad, precioUnitario);
        d.setDescripcion(descripcion);
        d.setImporteNeto(neto);
        d.setImporteIva(iva);
        d.setImporteSubtotal(subtotal);
        return d;
    }
}
