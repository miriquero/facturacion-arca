import type { FacturaReporteDTO } from '../models/factura.dto.js';
import { crear, reemplazarHijos } from './dom.js';
import { formatearFecha, formatearImporte, formatearNumero } from './formato.js';

export type Accion = 'editar' | 'emitir' | 'anular' | 'eliminar';

/** Tabla de facturas, contador y mensaje sin datos. Un único listener delegado en el tbody. */
export class Listado {
  private deshabilitadas = false;

  constructor(
    private readonly doc: Document,
    private readonly tbody: HTMLElement,
    private readonly contador: HTMLElement,
    private readonly sinResultados: HTMLElement,
    onAccion: (accion: Accion, id: number) => void,
  ) {
    tbody.addEventListener('click', (evento) => {
      const boton = (evento.target as Element).closest('button[data-accion]');
      if (!boton || !tbody.contains(boton)) return;
      onAccion(boton.getAttribute('data-accion') as Accion, Number(boton.getAttribute('data-id')));
    });
  }

  mostrar(filas: FacturaReporteDTO[]): void {
    reemplazarHijos(this.tbody, filas.map((f) => this.crearFila(f)));
    this.contador.textContent = `${filas.length} ${filas.length === 1 ? 'factura' : 'facturas'}`;
    this.sinResultados.hidden = filas.length !== 0;
    this.deshabilitarAcciones(this.deshabilitadas);
  }

  deshabilitarAcciones(deshabilitar: boolean): void {
    this.deshabilitadas = deshabilitar;
    for (const b of this.tbody.querySelectorAll('button')) b.disabled = deshabilitar;
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
    const botones = crear(doc, 'div', undefined, { class: 'botones' });
    const agregar = (accion: Accion, texto: string, objeto: string): void => {
      const b = crear(doc, 'button', texto, { type: 'button', 'data-accion': accion, 'data-id': String(f.id) });
      b.appendChild(crear(doc, 'span', ` ${objeto} ${f.id}`, { class: 'visualmente-oculto' }));
      botones.appendChild(b);
    };
    if (f.estado === 'BORRADOR') {
      agregar('editar', 'Editar', 'borrador');
      agregar('emitir', 'Emitir', 'borrador');
      agregar('eliminar', 'Eliminar', 'borrador');
    } else if (f.estado === 'EMITIDA') {
      agregar('anular', 'Anular', 'factura');
    }
    td.appendChild(botones);
    return td;
  }
}
