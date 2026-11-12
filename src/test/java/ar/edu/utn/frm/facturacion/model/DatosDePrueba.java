package ar.edu.utn.frm.facturacion.model;

/** Métodos fábrica para armar entidades en las pruebas sin repetir el armado en cada clase. */
final class DatosDePrueba {

    private DatosDePrueba() {
    }

    static Usuario usuario(String identificador) {
        Usuario u = new Usuario();
        u.setUsuario(identificador);
        u.setClave("1234");
        u.setNombre("Nombre " + identificador);
        u.setApellido("Apellido " + identificador);
        return u;
    }

    static Marca marca(String denominacion, int codigo) {
        Marca m = new Marca();
        m.setDenominacion(denominacion);
        m.setCodigo(codigo);
        return m;
    }

    static Rubro rubro(String denominacion, int codigo) {
        Rubro r = new Rubro();
        r.setDenominacion(denominacion);
        r.setCodigo(codigo);
        return r;
    }

    static CondicionIva condicionIva(int codigoAfip, String denominacion) {
        CondicionIva c = new CondicionIva();
        c.setCodigoAfip(codigoAfip);
        c.setDenominacion(denominacion);
        return c;
    }

    static TipoMoneda tipoMoneda(String codigoAfip, String denominacion, String simbolo) {
        TipoMoneda t = new TipoMoneda();
        t.setCodigoAfip(codigoAfip);
        t.setDenominacion(denominacion);
        t.setSimbolo(simbolo);
        return t;
    }

    static ListaPrecio listaPrecio(String codigo, String denominacion) {
        ListaPrecio l = new ListaPrecio();
        l.setCodigo(codigo);
        l.setDenominacion(denominacion);
        return l;
    }

    static PuntoVenta puntoVenta(int numero) {
        PuntoVenta p = new PuntoVenta();
        p.setNumero(numero);
        p.setDescripcion("Punto de venta " + numero);
        p.setTipoEmision("CAE");
        p.setDomicilioComercial("Domicilio " + numero);
        return p;
    }

    static Articulo articulo(String codigo, String denominacion, Marca marca, Rubro rubro, Double alicuotaIva) {
        Articulo a = new Articulo();
        a.setCodigo(codigo);
        a.setDenominacion(denominacion);
        a.setMarca(marca);
        a.setRubro(rubro);
        a.setAlicuotaIva(alicuotaIva);
        return a;
    }

    static ListaPrecioArticulo precio(ListaPrecio lista, Articulo articulo, double precioVenta) {
        ListaPrecioArticulo p = new ListaPrecioArticulo();
        p.setListaPrecio(lista);
        p.setArticulo(articulo);
        p.setPrecioVenta(precioVenta);
        return p;
    }

    static Contacto contacto(String email) {
        Contacto c = new Contacto();
        c.setEmail(email);
        c.setTelefono("2615550000");
        c.setCelular("2615551111");
        return c;
    }

    static Domicilio domicilio(String calle, String numero) {
        Domicilio d = new Domicilio();
        d.setNombreCalle(calle);
        d.setNumeroCalle(numero);
        return d;
    }

    static Cliente cliente(String denominacion, String cuit, Contacto contacto, Domicilio domicilio) {
        Cliente c = new Cliente();
        c.setDenominacion(denominacion);
        c.setCuitCuil(cuit);
        c.setContacto(contacto);
        c.setDomicilio(domicilio);
        return c;
    }

    static FacturaVentaDetalle detalle(ListaPrecioArticulo precio, double cantidad, double precioUnitario) {
        FacturaVentaDetalle d = new FacturaVentaDetalle();
        d.setListaPrecioArticulo(precio);
        d.setDescripcion("Renglón de prueba");
        d.setCantidad(cantidad);
        d.setPrecioUnitario(precioUnitario);
        d.setImporteSubtotal(precioUnitario);
        return d;
    }

    /** Factura en BORRADOR (sin número ni CAE) con la fecha y el total indicados como datos literales. */
    static FacturaVenta facturaBorrador(PuntoVenta puntoVenta, CondicionIva condicion, TipoMoneda moneda,
            Cliente cliente) {
        FacturaVenta f = new FacturaVenta();
        f.setFechaEmision(java.time.LocalDate.of(2026, 11, 11));
        f.setPuntoVenta(puntoVenta);
        f.setCondicionIva(condicion);
        f.setTipoMoneda(moneda);
        f.setCliente(cliente);
        f.setImporteTotal(1210);
        f.setImporteSaldo(1210);
        f.setEstado(EstadoFactura.BORRADOR);
        return f;
    }
}
