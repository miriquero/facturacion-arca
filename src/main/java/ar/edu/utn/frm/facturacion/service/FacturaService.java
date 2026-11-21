package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.arca.EmisionArca;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalladaDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaFiltrosDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaRequestDTO;
import ar.edu.utn.frm.facturacion.dto.PaginaDTO;
import ar.edu.utn.frm.facturacion.dto.SolicitudPagina;
import ar.edu.utn.frm.facturacion.config.FacturacionProperties;
import ar.edu.utn.frm.facturacion.dto.CambioEstadoRequestDTO;
import ar.edu.utn.frm.facturacion.dto.FacturaDetalleRequestDTO;
import ar.edu.utn.frm.facturacion.exception.ParametroInvalidoException;
import ar.edu.utn.frm.facturacion.exception.RecursoNoEncontradoException;
import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.Articulo;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.model.CondicionIva;
import ar.edu.utn.frm.facturacion.model.EstadoFactura;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.FacturaVentaDetalle;
import ar.edu.utn.frm.facturacion.model.ListaPrecioArticulo;
import ar.edu.utn.frm.facturacion.model.PuntoVenta;
import ar.edu.utn.frm.facturacion.model.TipoMoneda;
import ar.edu.utn.frm.facturacion.repository.ClienteRepository;
import ar.edu.utn.frm.facturacion.repository.CondicionIvaRepository;
import ar.edu.utn.frm.facturacion.repository.FacturaVentaRepository;
import ar.edu.utn.frm.facturacion.repository.FiltroFacturas;
import ar.edu.utn.frm.facturacion.repository.IntentoEmisionArcaRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.PuntoVentaRepository;
import ar.edu.utn.frm.facturacion.repository.TipoMonedaRepository;
import ar.edu.utn.frm.facturacion.report.ArchivoReporte;
import ar.edu.utn.frm.facturacion.report.FormatoReporte;
import ar.edu.utn.frm.facturacion.report.ReporteFacturasService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de lectura de facturas. Las transacciones son de solo lectura; los DTO se arman dentro de ellas. */
@Service
@Transactional(readOnly = true)
public class FacturaService {

    private static final String CONSUMIDOR_FINAL = "Consumidor Final";
    private static final long VENCIMIENTO_CAE_DIAS = 10;
    private static final String RESULTADO_APROBADO = "A";

    private final FacturaVentaRepository facturaRepository;
    private final ReporteFacturasService reporteService;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final CalculadoraImportes calculadora;
    private final FacturacionProperties propiedades;
    private final Optional<GeneradorCae> generadorCae;
    private final Optional<EmisionArca> emisionArca;
    private final IntentoEmisionArcaRepository intentosArca;
    private final Clock reloj;

    public FacturaService(FacturaVentaRepository facturaRepository, ReporteFacturasService reporteService,
            ClienteRepository clienteRepository, CondicionIvaRepository condicionIvaRepository,
            PuntoVentaRepository puntoVentaRepository, TipoMonedaRepository tipoMonedaRepository,
            ListaPrecioArticuloRepository listaPrecioArticuloRepository, CalculadoraImportes calculadora,
            FacturacionProperties propiedades, Optional<GeneradorCae> generadorCae, Clock reloj,
            Optional<EmisionArca> emisionArca, IntentoEmisionArcaRepository intentosArca) {
        this.facturaRepository = facturaRepository;
        this.reporteService = reporteService;
        this.clienteRepository = clienteRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.calculadora = calculadora;
        this.propiedades = propiedades;
        this.generadorCae = generadorCae;
        this.emisionArca = emisionArca;
        this.intentosArca = intentosArca;
        this.reloj = reloj;
    }

    /**
     * Alta de un borrador. Orden: (1) resolver todas las referencias (404) y (2) calcular y convertir todos los
     * importes (409) antes de (3) construir la entidad y guardarla: un rechazo no deja nada a medias. Número, CAE,
     * vencimiento y resultado quedan nulos hasta la emisión.
     */
    @Transactional
    public FacturaReporteDTO crear(FacturaRequestDTO solicitud) {
        Cabecera cabecera = resolverCabecera(solicitud);
        Renglones renglones = calcularRenglones(solicitud.detalles(), cabecera.tipoMoneda());

        FacturaVenta factura = new FacturaVenta();
        factura.setEstado(EstadoFactura.BORRADOR);
        aplicar(factura, solicitud, cabecera, renglones);
        return aReporte(facturaRepository.save(factura));
    }

    /**
     * Reemplazo completo de un borrador. Orden: (1) bloquear la factura (404 si no existe), (2) verificar que sea
     * BORRADOR (409), (3) resolver todo y calcular todo, (4) recién entonces modificar la entidad: cabecera,
     * renglones (misma colección, {@code orphanRemoval}) y {@code registrarModificacion()}, para que la
     * auditoría de la cabecera se actualice aunque solo cambien los renglones.
     */
    @Transactional
    public FacturaReporteDTO actualizar(Long id, FacturaRequestDTO solicitud) {
        FacturaVenta factura = bloquear(id);
        exigirBorrador(factura, "editar");
        exigirSinEmisionPendiente(id);
        Cabecera cabecera = resolverCabecera(solicitud);
        Renglones renglones = calcularRenglones(solicitud.detalles(), cabecera.tipoMoneda());

        aplicar(factura, solicitud, cabecera, renglones);
        factura.registrarModificacion();
        return aReporte(factura);
    }

    /**
     * Eliminación física de un borrador: bloquea la factura (404 si no existe), verifica que sea BORRADOR (409) y
     * la borra; la cascada alcanza a los renglones y no a los catálogos. Una factura emitida se anula, no se borra.
     */
    @Transactional
    public void eliminar(Long id) {
        FacturaVenta factura = bloquear(id);
        exigirBorrador(factura, "eliminar");
        exigirSinEmisionPendiente(id);
        facturaRepository.delete(factura);
    }

    /**
     * Cambia el estado de una factura. Orden: (1) bloquear la factura (404), (2) normalizar el estado pedido
     * (400 si el nombre no existe), (3) verificar la transición con {@link EstadoFactura#puedePasarA} (409),
     * (4) emitir (número y CAE) o anular (fecha de anulación y baja lógica). Una observación no en blanco
     * reemplaza la de la factura.
     */
    @Transactional
    public FacturaReporteDTO cambiarEstado(Long id, CambioEstadoRequestDTO solicitud) {
        FacturaVenta factura = bloquear(id);
        EstadoFactura destino = estadoPedido(solicitud.estado());
        if (!factura.getEstado().puedePasarA(destino)) {
            throw new ReglaNegocioException("No se puede pasar la factura " + id + " de " + factura.getEstado()
                    + " a " + destino + ": solo se admite BORRADOR a EMITIDA y EMITIDA a ANULADA");
        }
        if (destino == EstadoFactura.EMITIDA) {
            emitir(factura);
        } else {
            anular(factura);
        }
        factura.setEstado(destino);
        if (solicitud.observaciones() != null && !solicitud.observaciones().isBlank()) {
            factura.setObservaciones(solicitud.observaciones());
        }
        return aReporte(factura);
    }

    /** Normaliza el texto (sin espacios exteriores, sin distinguir mayúsculas) y lo convierte; 400 si no existe. */
    private static EstadoFactura estadoPedido(String texto) {
        String normalizado = texto.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(EstadoFactura.values()).filter(e -> e.name().equals(normalizado)).findFirst()
                .orElseThrow(() -> new ParametroInvalidoException("El estado '" + texto.strip()
                        + "' no es válido. Valores admitidos: " + Arrays.toString(EstadoFactura.values()) + "."));
    }

    /**
     * Serializa las emisiones del mismo punto de venta. En homologación la numeración y el CAE vienen de ARCA.
     */
    private void emitir(FacturaVenta factura) {
        if (factura.getDetalles().isEmpty()) {
            throw new ReglaNegocioException("No se puede emitir la factura " + factura.getId()
                    + " porque no tiene renglones");
        }
        PuntoVenta puntoVenta = puntoVentaRepository.bloquearPorId(factura.getPuntoVenta().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Punto de venta",
                        factura.getPuntoVenta().getId()));
        if (emisionArca.isPresent()) {
            var autorizada = emisionArca.orElseThrow().emitir(factura);
            factura.setNumero(autorizada.numero());
            factura.setCae(autorizada.cae());
            factura.setCaeFechaVencimiento(autorizada.vencimiento());
            factura.setResultadoAfip(RESULTADO_APROBADO);
            return;
        }
        exigirSinEmisionPendiente(factura.getId());
        GeneradorCae simulador = generadorCae.orElseThrow(() -> new ReglaNegocioException(
                "Para emitir, iniciá el backend con el perfil de homologación configurado."));
        factura.setNumero(facturaRepository.maximoNumero(puntoVenta) + 1);
        factura.setCae(simulador.generar());
        factura.setCaeFechaVencimiento(LocalDate.now(reloj).plusDays(VENCIMIENTO_CAE_DIAS));
        factura.setResultadoAfip(RESULTADO_APROBADO);
    }

    /** Anulación local: se conserva el comprobante y su autorización en ARCA. */
    private void anular(FacturaVenta factura) {
        factura.setFechaAnulacion(LocalDate.now(reloj));
        factura.darDeBaja();
    }

    /** Carga la factura con bloqueo pesimista de escritura; 404 si no existe. */
    private FacturaVenta bloquear(Long id) {
        return facturaRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
    }

    private void exigirSinEmisionPendiente(Long id) {
        if (intentosArca.existsById(id)) {
            throw new ReglaNegocioException("Hay una emisión pendiente de confirmar en ARCA. Pulsá Emitir para consultarla.");
        }
    }

    private static void exigirBorrador(FacturaVenta factura, String operacion) {
        if (factura.getEstado() != EstadoFactura.BORRADOR) {
            throw new ReglaNegocioException("No se puede " + operacion + " la factura " + factura.getId()
                    + ": está en estado " + factura.getEstado() + " y solo se admite en estado BORRADOR");
        }
    }

    /** Referencias de la cabecera ya resueltas (el cliente puede faltar: «Consumidor Final»). */
    private record Cabecera(Cliente cliente, CondicionIva condicionIva, PuntoVenta puntoVenta, TipoMoneda tipoMoneda) {
    }

    /** Renglones ya calculados y convertidos, con los importes de cabecera. */
    private record Renglones(List<FacturaVentaDetalle> lista, double total, double saldo) {
    }

    private Cabecera resolverCabecera(FacturaRequestDTO solicitud) {
        CondicionIva condicion = condicionIvaRepository.findById(solicitud.condicionIvaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Condición de IVA", solicitud.condicionIvaId()));
        Cliente cliente = solicitud.clienteId() == null ? null : clienteRepository.findById(solicitud.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", solicitud.clienteId()));
        PuntoVenta puntoVenta = puntoVentaRepository.findById(solicitud.puntoVentaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Punto de venta", solicitud.puntoVentaId()));
        TipoMoneda moneda = tipoMonedaRepository.findById(solicitud.tipoMonedaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de moneda", solicitud.tipoMonedaId()));
        return new Cabecera(cliente, condicion, puntoVenta, moneda);
    }

    /**
     * Resuelve cada lista-artículo (404), calcula con {@link CalculadoraImportes} y convierte cada importe con
     * {@link ImporteDouble} (409). No toca ninguna entidad administrada.
     */
    private Renglones calcularRenglones(List<FacturaDetalleRequestDTO> solicitados, TipoMoneda moneda) {
        List<FacturaVentaDetalle> lista = new ArrayList<>();
        List<BigDecimal> subtotales = new ArrayList<>();
        for (FacturaDetalleRequestDTO pedido : solicitados) {
            ListaPrecioArticulo oferta = listaPrecioArticuloRepository.findById(pedido.listaPrecioArticuloId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Lista de precios del artículo",
                            pedido.listaPrecioArticuloId()));
            Articulo articulo = oferta.getArticulo();
            BigDecimal precio = precioEn(moneda, oferta.getPrecioVenta());
            BigDecimal alicuota = BigDecimal.ZERO;
            if (emisionArca.isEmpty()) {
                alicuota = articulo.getAlicuotaIva() == null ? propiedades.ivaPorDefecto()
                        : decimalDe(articulo.getAlicuotaIva(), "alicuotaIva");
            }
            BigDecimal bonificacion = pedido.porcentajeBonificacion() == null ? BigDecimal.ZERO
                    : pedido.porcentajeBonificacion();
            ImportesRenglon importes = calculadora.calcularRenglon(pedido.cantidad(), precio, bonificacion, alicuota);

            FacturaVentaDetalle renglon = new FacturaVentaDetalle();
            renglon.setListaPrecioArticulo(oferta);
            renglon.setDescripcion(pedido.descripcion() == null || pedido.descripcion().isBlank()
                    ? articulo.getDenominacion() : pedido.descripcion());
            renglon.setCantidad(ImporteDouble.aDouble(pedido.cantidad(), "cantidad"));
            renglon.setPrecioUnitario(ImporteDouble.aDouble(precio, "precioUnitario"));
            renglon.setPorcentajeBonificacion(ImporteDouble.aDouble(bonificacion, "porcentajeBonificacion"));
            renglon.setImporteNeto(ImporteDouble.aDouble(importes.neto(), "importeNeto"));
            renglon.setImporteIva(ImporteDouble.aDouble(importes.iva(), "importeIva"));
            renglon.setImporteSubtotal(ImporteDouble.aDouble(importes.subtotal(), "importeSubtotal"));
            lista.add(renglon);
            subtotales.add(importes.subtotal());
        }
        BigDecimal total = calculadora.sumar(subtotales);
        BigDecimal saldo = calculadora.saldo(total, BigDecimal.ZERO);
        return new Renglones(lista, ImporteDouble.aDouble(total, "importeTotal"),
                ImporteDouble.aDouble(saldo, "importeSaldo"));
    }

    /**
     * Precio unitario en la moneda de la factura. El precio de lista está en pesos: en pesos se usa tal cual y en
     * otra moneda se divide por su cotización (pesos por unidad) y se redondea a dos decimales con HALF_UP, para
     * que el valor siga siendo exacto al guardarlo como {@code double}.
     */
    private BigDecimal precioEn(TipoMoneda moneda, double precioDeLista) {
        BigDecimal precio = decimalDe(precioDeLista, "precioUnitario");
        BigDecimal cotizacion = propiedades.cotizacionDe(moneda.getCodigoAfip());
        return cotizacion.compareTo(BigDecimal.ONE) == 0 ? precio
                : precio.divide(cotizacion, 2, RoundingMode.HALF_UP);
    }

    /** Lee un {@code double} guardado como decimal exacto; un valor no finito no se puede usar para calcular. */
    private static BigDecimal decimalDe(double valor, String campo) {
        if (!Double.isFinite(valor)) {
            throw new ReglaNegocioException("El valor de '" + campo + "' guardado no es un número finito");
        }
        return BigDecimal.valueOf(valor);
    }

    /** Copia cabecera, fecha, observaciones y renglones a la entidad (única parte que modifica el estado). */
    private void aplicar(FacturaVenta factura, FacturaRequestDTO solicitud, Cabecera cabecera, Renglones renglones) {
        factura.setCliente(cabecera.cliente());
        factura.setCondicionIva(cabecera.condicionIva());
        factura.setPuntoVenta(cabecera.puntoVenta());
        factura.setTipoMoneda(cabecera.tipoMoneda());
        factura.setFechaEmision(solicitud.fechaEmision() == null ? LocalDate.now(reloj) : solicitud.fechaEmision());
        factura.setObservaciones(solicitud.observaciones());
        factura.reemplazarDetalles(renglones.lista());
        factura.setImporteTotal(renglones.total());
        factura.setImporteCobrado(0.0);
        factura.setImporteSaldo(renglones.saldo());
    }

    /** Misma proyección que la consulta JPQL del listado, armada con la factura ya cargada. */
    private static FacturaReporteDTO aReporte(FacturaVenta factura) {
        Cliente cliente = factura.getCliente();
        return new FacturaReporteDTO(
                factura.getId(),
                factura.getNumero(),
                factura.getFechaEmision(),
                cliente == null ? CONSUMIDOR_FINAL : cliente.getDenominacion(),
                factura.getCondicionIva().getDenominacion(),
                factura.getPuntoVenta().getDescripcion(),
                factura.getImporteTotal(),
                (long) factura.getDetalles().size(),
                factura.getEstado(),
                factura.getTipoMoneda().getCodigoAfip());
    }

    /**
     * Listado paginado. Valida filtros y página (400) antes de consultar; la página y el total son dos consultas
     * con el mismo filtro (el conteo no une renglones: ver {@code FacturaReporteRepositoryImpl}).
     */
    public PaginaDTO<FacturaReporteDTO> listar(FacturaFiltrosDTO filtros, Integer pagina, Integer tamanio) {
        FiltroFacturas filtro = FiltroFacturas.desde(filtros);
        SolicitudPagina solicitud = SolicitudPagina.de(pagina, tamanio);
        List<FacturaReporteDTO> items = facturaRepository.buscarPagina(filtro, solicitud.desplazamiento(),
                solicitud.tamanio());
        return PaginaDTO.de(items, solicitud, facturaRepository.contar(filtro));
    }

    /**
     * Detalle de una factura. El DTO se arma por completo acá, dentro de la transacción de solo lectura: con
     * {@code open-in-view: false} las asociaciones diferidas (cliente, catálogos, renglones, artículos) solo se
     * pueden leer mientras la sesión sigue abierta.
     */
    public FacturaDetalladaDTO obtenerDetalle(Long id) {
        FacturaVenta factura = facturaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
        Cliente cliente = factura.getCliente();
        List<FacturaDetalleDTO> renglones = factura.getDetalles().stream()
                .sorted(Comparator.comparing(FacturaVentaDetalle::getId))
                .map(FacturaService::aDto)
                .toList();
        return new FacturaDetalladaDTO(
                factura.getId(),
                factura.getNumero(),
                factura.getFechaEmision(),
                factura.getEstado().name(),
                cliente == null ? null : cliente.getId(),
                cliente == null ? CONSUMIDOR_FINAL : cliente.getDenominacion(),
                cliente == null ? null : cliente.getCuitCuil(),
                factura.getCondicionIva().getId(),
                factura.getCondicionIva().getDenominacion(),
                factura.getPuntoVenta().getId(),
                factura.getPuntoVenta().getNumero(),
                factura.getPuntoVenta().getDescripcion(),
                factura.getTipoMoneda().getId(),
                factura.getTipoMoneda().getCodigoAfip(),
                factura.getImporteTotal(),
                factura.getImporteCobrado(),
                factura.getImporteSaldo(),
                factura.getCae(),
                factura.getCaeFechaVencimiento(),
                factura.getResultadoAfip(),
                factura.getFechaAnulacion(),
                factura.getObservaciones(),
                renglones);
    }

    private static FacturaDetalleDTO aDto(FacturaVentaDetalle renglon) {
        ListaPrecioArticulo oferta = renglon.getListaPrecioArticulo();
        return new FacturaDetalleDTO(
                renglon.getId(),
                oferta.getId(),
                oferta.getArticulo().getCodigo(),
                renglon.getDescripcion(),
                renglon.getCantidad(),
                renglon.getPrecioUnitario(),
                renglon.getPorcentajeBonificacion(),
                renglon.getImporteNeto(),
                renglon.getImporteIva(),
                renglon.getImporteSubtotal());
    }

    /**
     * PDF del comprobante de una factura informada (EMITIDA o ANULADA). Todo el detalle se arma dentro de esta
     * transacción de solo lectura: 404 si no existe y 409 si es un BORRADOR (no fue informada a ARCA).
     */
    public ArchivoReporte generarComprobante(Long id) {
        FacturaDetalladaDTO detalle = obtenerDetalle(id);
        if (EstadoFactura.BORRADOR.name().equals(detalle.estado())) {
            throw new ReglaNegocioException("La factura " + id + " está en BORRADOR: no fue informada a ARCA; "
                    + "emitila para imprimirla");
        }
        byte[] contenido = intentosArca.existsById(id)
                ? reporteService.generarComprobanteHomologacion(detalle)
                : reporteService.generarComprobante(detalle);
        String nombre = String.format("factura-%04d-%08d.pdf", detalle.puntoVentaNumero(), detalle.numero());
        return new ArchivoReporte(nombre, MediaType.APPLICATION_PDF, contenido);
    }

    /**
     * Reporte con los mismos filtros que {@link #listar}. Valida el formato y los filtros antes de consultar, para
     * no ejecutar la consulta ante un pedido que de todos modos se rechaza.
     */
    public ArchivoReporte generarReporte(FacturaFiltrosDTO filtros, String formato) {
        FormatoReporte tipo = FormatoReporte.desde(formato);
        List<FacturaReporteDTO> filas = facturaRepository.buscarReporte(FiltroFacturas.desde(filtros));
        byte[] contenido = switch (tipo) {
            case PDF -> reporteService.generarPdf(filas);
            case XLSX -> reporteService.generarExcel(filas);
            case TXT -> reporteService.generarTxt(filas);
        };
        return new ArchivoReporte(tipo.nombreArchivo(), tipo.tipoContenido(), contenido);
    }
}
