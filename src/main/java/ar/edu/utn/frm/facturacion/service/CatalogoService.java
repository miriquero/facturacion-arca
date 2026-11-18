package ar.edu.utn.frm.facturacion.service;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.model.Cliente;
import ar.edu.utn.frm.facturacion.repository.ClienteRepository;
import ar.edu.utn.frm.facturacion.repository.CondicionIvaRepository;
import ar.edu.utn.frm.facturacion.repository.ListaPrecioArticuloRepository;
import ar.edu.utn.frm.facturacion.repository.PuntoVentaRepository;
import ar.edu.utn.frm.facturacion.repository.TipoMonedaRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opciones de los catálogos para el frontend. Solo lectura; cada lista sale ordenada por id ascendente. */
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private static final Sort POR_ID = Sort.by("id");

    private final PuntoVentaRepository puntoVentaRepository;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;

    public CatalogoService(PuntoVentaRepository puntoVentaRepository, ClienteRepository clienteRepository,
            CondicionIvaRepository condicionIvaRepository, TipoMonedaRepository tipoMonedaRepository,
            ListaPrecioArticuloRepository listaPrecioArticuloRepository) {
        this.puntoVentaRepository = puntoVentaRepository;
        this.clienteRepository = clienteRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
    }

    /** Etiqueta: número con cuatro dígitos, guion y descripción (por ejemplo {@code 0001 - Casa Central}). */
    public List<OpcionDTO> puntosVenta() {
        return puntoVentaRepository.findAll(POR_ID).stream()
                .map(p -> new OpcionDTO(p.getId(), String.format("%04d - %s", p.getNumero(), p.getDescripcion()),
                        null, null))
                .toList();
    }

    /** Etiqueta: denominación y, entre paréntesis, el CUIT/CUIL. */
    public List<OpcionDTO> clientes() {
        return clientes(null);
    }

    /** Búsqueda por texto en la denominación o el CUIT/CUIL; sin texto devuelve el catálogo completo. */
    public List<OpcionDTO> clientes(String q) {
        return PatronBusqueda.de(q)
                .map(clienteRepository::buscar)
                .orElseGet(() -> clienteRepository.findAll(POR_ID))
                .stream()
                .map(CatalogoService::aOpcion)
                .toList();
    }

    private static OpcionDTO aOpcion(Cliente c) {
        return new OpcionDTO(c.getId(), c.getDenominacion() + " (" + c.getCuitCuil() + ")", null, null);
    }

    public List<OpcionDTO> condicionesIva() {
        return condicionIvaRepository.findAll(POR_ID).stream()
                .map(c -> new OpcionDTO(c.getId(), c.getDenominacion(), null, null))
                .toList();
    }

    /** El código de moneda viaja en {@code codigoMoneda}. */
    public List<OpcionDTO> tiposMoneda() {
        return tipoMonedaRepository.findAll(POR_ID).stream()
                .map(m -> new OpcionDTO(m.getId(), m.getDenominacion(), null, m.getCodigoAfip()))
                .toList();
    }

    /**
     * Una opción por oferta de un artículo en una lista: el {@code id} es el de la oferta (el que recibirá el
     * renglón de una factura) y {@code valor} su precio de venta.
     */
    public List<OpcionDTO> listaPrecioArticulos() {
        return listaPrecioArticulos(null);
    }

    /** Búsqueda por código o denominación del artículo o por código de lista; sin texto, todas las ofertas. */
    public List<OpcionDTO> listaPrecioArticulos(String q) {
        return PatronBusqueda.de(q)
                .map(listaPrecioArticuloRepository::buscarConArticuloYLista)
                .orElseGet(listaPrecioArticuloRepository::findAllConArticuloYLista).stream()
                .map(o -> new OpcionDTO(o.getId(), o.getArticulo().getCodigo() + " - "
                        + o.getArticulo().getDenominacion() + " (" + o.getListaPrecio().getCodigo() + ")",
                        o.getPrecioVenta(), null))
                .toList();
    }
}
