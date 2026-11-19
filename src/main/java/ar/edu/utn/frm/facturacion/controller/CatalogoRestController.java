package ar.edu.utn.frm.facturacion.controller;

import ar.edu.utn.frm.facturacion.dto.OpcionDTO;
import ar.edu.utn.frm.facturacion.service.CatalogoService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogos de solo lectura para las listas desplegables del frontend. No hay altas, cambios ni bajas. */
@RestController
@RequestMapping("/api/catalogos")
public class CatalogoRestController {

    private final CatalogoService catalogoService;

    public CatalogoRestController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/puntos-venta")
    public List<OpcionDTO> puntosVenta() {
        return catalogoService.puntosVenta();
    }

    @GetMapping("/clientes")
    public List<OpcionDTO> clientes(@RequestParam(required = false) String q) {
        return catalogoService.clientes(q);
    }

    @GetMapping("/condiciones-iva")
    public List<OpcionDTO> condicionesIva() {
        return catalogoService.condicionesIva();
    }

    @GetMapping("/tipos-moneda")
    public List<OpcionDTO> tiposMoneda() {
        return catalogoService.tiposMoneda();
    }

    @GetMapping("/lista-precio-articulos")
    public List<OpcionDTO> listaPrecioArticulos(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long listaId) {
        return listaId == null ? catalogoService.listaPrecioArticulos(q)
                : catalogoService.listaPrecioArticulos(q, listaId);
    }

    /** Listas de precios disponibles para elegir al buscar artículos. */
    @GetMapping("/listas-precio")
    public List<OpcionDTO> listasPrecio() {
        return catalogoService.listasPrecio();
    }
}
