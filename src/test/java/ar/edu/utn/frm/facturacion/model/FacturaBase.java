package ar.edu.utn.frm.facturacion.model;

import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.articulo;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.cliente;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.condicionIva;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.contacto;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.domicilio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.listaPrecio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.precio;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.puntoVenta;
import static ar.edu.utn.frm.facturacion.model.DatosDePrueba.tipoMoneda;

import org.junit.jupiter.api.BeforeEach;

/** Catálogos y cliente persistidos que toda factura de prueba necesita. */
abstract class FacturaBase extends PersistenciaBase {

    protected PuntoVenta pv1;
    protected PuntoVenta pv2;
    protected CondicionIva condicion;
    protected TipoMoneda pesos;
    protected ListaPrecio lista;
    protected Articulo martillo;
    protected Articulo televisor;
    protected ListaPrecioArticulo precioMartillo;
    protected ListaPrecioArticulo precioTelevisor;
    protected Cliente juan;

    @BeforeEach
    void prepararCatalogos() {
        pv1 = em.persist(puntoVenta(1));
        pv2 = em.persist(puntoVenta(2));
        condicion = em.persist(condicionIva(5, "Consumidor Final"));
        pesos = em.persist(tipoMoneda("PES", "Peso argentino", "$"));
        lista = em.persist(listaPrecio("GENERAL", "Lista general"));
        martillo = em.persist(articulo("ART-001", "Martillo", null, null, null));
        televisor = em.persist(articulo("ART-002", "Televisor", null, null, null));
        precioMartillo = em.persist(precio(lista, martillo, 3000));
        precioTelevisor = em.persist(precio(lista, televisor, 60000));
        juan = em.persist(cliente("Juan Servicios", "20-12345678-9",
                contacto("juan@ejemplo.com"), domicilio("San Martín", "100")));
        em.flush();
        em.clear();
    }
}
