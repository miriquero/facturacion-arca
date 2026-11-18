import type { FacturaReporteDTO } from '../models/factura.dto.js';
import { hrefDe } from '../navegacion/rutas.js';
import { crear, reemplazarHijos } from './dom.js';
import { formatearFecha, formatearImporte, formatearNumero } from './formato.js';

/** Tabla de facturas, contador y mensaje sin datos. Cada fila solo navega: «Ver» al detalle e «Imprimir PDF» si está informada. */
export class Listado {
  constructor(
    private readonly doc: Document,
    private readonly tbody: HTMLElement,
    private readonly contador: HTMLElement,
    private readonly sinResultados: HTMLElement,
    private readonly urlPdf: (id: number) => string,
  ) {}

  /** `total` es la cantidad que cumple los filtros (totalElementos), no las filas de esta página. */
  mostrar(filas: FacturaReporteDTO[], total: number): void {
    reemplazarHijos(this.tbody, filas.map((f) => this.crearFila(f)));
    this.contador.textContent = textoCantidad(total);
    this.sinResultados.hidden = total !== 0;
  }

  private crearFila(f: FacturaReporteDTO): HTMLElement {
    const doc = this.doc;
    const tr = crear(doc, 'tr');
    tr.appendChild(crear(doc, 'td', formatearNumero(f.numeroFactura)));
    tr.appendChild(crear(doc, 'td', formatearFecha(f.fechaEmision)));
    tr.appendChild(crear(doc, 'td', f.clienteDenominacion));
    tr.appendChild(crear(doc, 'td', f.condicionIva));
    tr.appendChild(crear(doc, 'td', f.puntoVentaDescripcion));
    tr.appendChild(crear(doc, 'td', String(f.cantidadItems)));
    tr.appendChild(crear(doc, 'td', formatearImporte(f.importeTotal, f.codigoMoneda), { class: 'importe' }));
    const estado = crear(doc, 'td');
    estado.appendChild(crear(doc, 'span', f.estado, { class: `estado-fila estado-fila--${f.estado}` }));
    tr.appendChild(estado);
    tr.appendChild(this.celdaAcciones(f));
    return tr;
  }

  private celdaAcciones(f: FacturaReporteDTO): HTMLElement {
    const doc = this.doc;
    const td = crear(doc, 'td');
    const enlaces = crear(doc, 'div', undefined, { class: 'botones' });
    // El nombre accesible incluye el id de la factura (el texto extra queda visualmente oculto).
    const ver = crear(doc, 'a', 'Ver', { href: hrefDe({ tipo: 'detalle', id: f.id }) });
    ver.appendChild(crear(doc, 'span', ` factura ${f.id}`, { class: 'visualmente-oculto' }));
    enlaces.appendChild(ver);
    if (f.estado !== 'BORRADOR') {
      // Informada a ARCA (EMITIDA o ANULADA): se puede imprimir el PDF; el borrador no.
      const imprimir = crear(doc, 'a', 'Imprimir PDF', { href: this.urlPdf(f.id), class: 'enlace-pdf' });
      imprimir.appendChild(crear(doc, 'span', ` factura ${f.id}`, { class: 'visualmente-oculto' }));
      enlaces.appendChild(imprimir);
    }
    td.appendChild(enlaces);
    return td;
  }
}

/** `3 facturas`, `1 factura`, `0 facturas`. */
export function textoCantidad(total: number): string {
  return `${total} ${total === 1 ? 'factura' : 'facturas'}`;
}
