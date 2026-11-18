import type { FacturaDetalladaDTO, FacturaDetalleDTO } from '../models/factura.dto.js';
import { crear } from './dom.js';
import { formatearComprobante, formatearFecha, formatearImporte } from './formato.js';

/** Texto del h1 del detalle: el comprobante si ya tiene número; si no, el borrador con su id. */
export function tituloDetalle(d: FacturaDetalladaDTO): string {
  return d.numero === null ? `Borrador ${d.id}` : `Factura ${formatearComprobante(d.puntoVentaNumero, d.numero)}`;
}

/** Agrega una fila etiqueta/valor a una lista de definición. El valor entra siempre por textContent. */
function fila(doc: Document, lista: HTMLElement, etiqueta: string, valor: string): void {
  lista.appendChild(crear(doc, 'dt', etiqueta));
  lista.appendChild(crear(doc, 'dd', valor));
}

const ENCABEZADOS = ['Código', 'Descripción', 'Cantidad', 'Precio unitario', 'Bonificación (%)', 'Neto', 'IVA', 'Subtotal'];

/** Tabla de renglones en el orden recibido; los importes llevan la moneda de la factura (sin conversión). */
function tablaRenglones(doc: Document, detalles: FacturaDetalleDTO[], moneda: string): HTMLElement {
  const tabla = crear(doc, 'table', undefined, { class: 'renglones-detalle' });
  tabla.appendChild(crear(doc, 'caption', 'Renglones de la factura'));
  const cabecera = crear(doc, 'tr');
  for (const texto of ENCABEZADOS) cabecera.appendChild(crear(doc, 'th', texto, { scope: 'col' }));
  const thead = crear(doc, 'thead');
  thead.appendChild(cabecera);
  tabla.appendChild(thead);
  const cuerpo = crear(doc, 'tbody');
  for (const r of detalles) {
    const tr = crear(doc, 'tr');
    const importe = (valor: number): string => formatearImporte(valor, moneda);
    const textos = [r.articuloCodigo, r.descripcion ?? '—', String(r.cantidad), importe(r.precioUnitario), String(r.porcentajeBonificacion), importe(r.importeNeto), importe(r.importeIva), importe(r.importeSubtotal)];
    textos.forEach((texto, i) => tr.appendChild(crear(doc, 'td', texto, i >= 3 && i !== 4 ? { class: 'importe' } : undefined)));
    cuerpo.appendChild(tr);
  }
  tabla.appendChild(cuerpo);
  const contenedor = crear(doc, 'div', undefined, { class: 'tabla-scroll', role: 'region', 'aria-label': 'Renglones de la factura', tabindex: '0' });
  contenedor.appendChild(tabla);
  return contenedor;
}

/** Contenido del detalle de una factura: solo nodos y textContent (nunca HTML del servidor). */
export function construirDetalle(doc: Document, d: FacturaDetalladaDTO): HTMLElement {
  const raiz = crear(doc, 'div', undefined, { class: 'detalle' });

  const datos = crear(doc, 'dl', undefined, { class: 'datos' });
  fila(doc, datos, 'Número', formatearComprobante(d.puntoVentaNumero, d.numero));
  fila(doc, datos, 'Fecha de emisión', formatearFecha(d.fechaEmision));
  fila(doc, datos, 'Estado', d.estado);
  fila(doc, datos, 'Cliente', d.clienteId === null ? 'Consumidor Final' : d.clienteDenominacion);
  if (d.clienteId !== null) fila(doc, datos, 'CUIT/CUIL', d.clienteCuitCuil ?? '—');
  fila(doc, datos, 'Condición frente al IVA', d.condicionIva);
  fila(doc, datos, 'Punto de venta', d.puntoVentaDescripcion);
  fila(doc, datos, 'Moneda', d.codigoMoneda);
  raiz.appendChild(datos);
  raiz.appendChild(tablaRenglones(doc, d.detalles, d.codigoMoneda));

  const importe = (valor: number): string => formatearImporte(valor, d.codigoMoneda);
  const fechaORaya = (texto: string | null): string => (texto === null ? '—' : formatearFecha(texto));
  const importes = crear(doc, 'dl', undefined, { class: 'datos' });
  fila(doc, importes, 'Importe total', importe(d.importeTotal));
  fila(doc, importes, 'Cobrado', importe(d.importeCobrado));
  fila(doc, importes, 'Saldo', importe(d.importeSaldo));
  fila(doc, importes, 'CAE', d.cae ?? '—');
  fila(doc, importes, 'Vencimiento del CAE', fechaORaya(d.caeFechaVencimiento));
  fila(doc, importes, 'Resultado de ARCA', d.resultadoAfip ?? '—');
  fila(doc, importes, 'Observaciones', d.observaciones ?? '—');
  fila(doc, importes, 'Fecha de anulación', fechaORaya(d.fechaAnulacion));
  raiz.appendChild(importes);
  return raiz;
}
