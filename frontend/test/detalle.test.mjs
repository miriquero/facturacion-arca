import assert from 'node:assert/strict';
import { test } from 'node:test';
import { JSDOM } from 'jsdom';
import { construirDetalle, tituloDetalle } from '../dist/ui/detalle.js';

const doc = new JSDOM('<!doctype html><body></body>').window.document;

/** Factura emitida de ejemplo: los literales de los escenarios de la spec. */
const emitida = (extra = {}) => ({
  id: 4,
  numero: 4,
  fechaEmision: '2026-11-01',
  estado: 'EMITIDA',
  clienteId: 2,
  clienteDenominacion: 'Ana SA',
  clienteCuitCuil: '30-71234567-1',
  condicionIvaId: 2,
  condicionIva: 'Responsable Inscripto',
  puntoVentaId: 1,
  puntoVentaNumero: 1,
  puntoVentaDescripcion: '0001 - Casa Central',
  tipoMonedaId: 1,
  codigoMoneda: 'PES',
  importeTotal: 6534,
  importeCobrado: 0,
  importeSaldo: 6534,
  cae: '71234567890123',
  caeFechaVencimiento: '2026-11-11',
  resultadoAfip: 'A',
  fechaAnulacion: null,
  observaciones: null,
  detalles: [],
  ...extra,
});

const borrador = (extra = {}) =>
  emitida({ id: 6, numero: null, estado: 'BORRADOR', cae: null, caeFechaVencimiento: null, resultadoAfip: null, ...extra });

/** Valor (dd) de la fila cuya etiqueta (dt) es `etiqueta`; undefined si no existe la fila. */
const valorDe = (raiz, etiqueta) => {
  const dt = [...raiz.querySelectorAll('dt')].find((d) => d.textContent === etiqueta);
  return dt?.nextElementSibling?.textContent;
};

test('Factura emitida: titulo, numero de comprobante, fecha dd/mm/aaaa y estado', () => {
  const d = emitida();
  const raiz = construirDetalle(doc, d);
  assert.equal(tituloDetalle(d), 'Factura 0001-00000004');
  assert.equal(valorDe(raiz, 'Número'), '0001-00000004');
  assert.equal(valorDe(raiz, 'Fecha de emisión'), '01/11/2026');
  assert.equal(valorDe(raiz, 'Estado'), 'EMITIDA');
});

test('Borrador sin numero: titulo Borrador 6 y numero como raya', () => {
  const d = borrador();
  const raiz = construirDetalle(doc, d);
  assert.equal(tituloDetalle(d), 'Borrador 6');
  assert.equal(valorDe(raiz, 'Número'), '—');
  assert.equal(valorDe(raiz, 'Estado'), 'BORRADOR');
});

test('Cliente con CUIT: denominacion y CUIT/CUIL', () => {
  const raiz = construirDetalle(doc, emitida());
  assert.equal(valorDe(raiz, 'Cliente'), 'Ana SA');
  assert.equal(valorDe(raiz, 'CUIT/CUIL'), '30-71234567-1');
});

test('Consumidor Final: sin cliente se muestra Consumidor Final y ningun CUIT', () => {
  const raiz = construirDetalle(doc, emitida({ clienteId: null, clienteDenominacion: 'Consumidor Final', clienteCuitCuil: null }));
  assert.equal(valorDe(raiz, 'Cliente'), 'Consumidor Final');
  assert.equal(valorDe(raiz, 'CUIT/CUIL'), undefined);
});

test('Condicion de IVA, punto de venta y moneda', () => {
  const raiz = construirDetalle(doc, emitida());
  assert.equal(valorDe(raiz, 'Condición frente al IVA'), 'Responsable Inscripto');
  assert.equal(valorDe(raiz, 'Punto de venta'), '0001 - Casa Central');
  assert.equal(valorDe(raiz, 'Moneda'), 'PES');
});

const MARTILLO = {
  id: 1,
  listaPrecioArticuloId: 1,
  articuloCodigo: 'MAR',
  descripcion: 'Martillo',
  cantidad: 2,
  precioUnitario: 3000,
  porcentajeBonificacion: 10,
  importeNeto: 5400,
  importeIva: 1134,
  importeSubtotal: 6534,
};
const CABLE = { ...MARTILLO, id: 2, listaPrecioArticuloId: 3, articuloCodigo: 'HDMI', descripcion: null, cantidad: 1, precioUnitario: 8000, porcentajeBonificacion: 0, importeNeto: 8000, importeIva: 1680, importeSubtotal: 9680 };

const celdas = (tr) => [...tr.cells].map((c) => c.textContent);

test('Dos renglones en pesos: tabla con caption y th scope=col, filas en el orden recibido y descripcion nula como raya', () => {
  const raiz = construirDetalle(doc, emitida({ detalles: [MARTILLO, CABLE] }));
  const tabla = raiz.querySelector('table');
  assert.ok(tabla.querySelector('caption'), 'falta el caption');
  const encabezados = [...tabla.querySelectorAll('thead th')];
  assert.deepEqual(encabezados.map((th) => th.textContent), ['Código', 'Descripción', 'Cantidad', 'Precio unitario', 'Bonificación (%)', 'Neto', 'IVA', 'Subtotal']);
  assert.ok(encabezados.every((th) => th.getAttribute('scope') === 'col'));
  const filas = [...tabla.tBodies[0].rows];
  assert.equal(filas.length, 2);
  assert.deepEqual(celdas(filas[0]), ['MAR', 'Martillo', '2', '$ 3.000,00', '10', '$ 5.400,00', '$ 1.134,00', '$ 6.534,00']);
  assert.deepEqual(celdas(filas[1]).slice(0, 2), ['HDMI', '—']);
});

test('Factura en dolares: los importes de los renglones se muestran en dolares con el mismo valor, sin conversion', () => {
  const raiz = construirDetalle(doc, emitida({ codigoMoneda: 'DOL', detalles: [{ ...MARTILLO, precioUnitario: 100, importeNeto: 180 }] }));
  const fila = raiz.querySelector('table').tBodies[0].rows[0];
  assert.equal(celdas(fila)[3], 'US$ 100,00');
  assert.equal(celdas(fila)[5], 'US$ 180,00');
});

test('Factura emitida con CAE: total, cobrado, saldo, CAE, vencimiento y resultado', () => {
  const raiz = construirDetalle(doc, emitida({ importeCobrado: 1000 , importeSaldo: 5534 }));
  assert.equal(valorDe(raiz, 'Importe total'), '$ 6.534,00');
  assert.equal(valorDe(raiz, 'Cobrado'), '$ 1.000,00');
  assert.equal(valorDe(raiz, 'Saldo'), '$ 5.534,00');
  assert.equal(valorDe(raiz, 'CAE'), '71234567890123');
  assert.equal(valorDe(raiz, 'Vencimiento del CAE'), '11/11/2026');
  assert.equal(valorDe(raiz, 'Resultado de ARCA'), 'A');
});

test('Factura en dolares: el total se muestra en dolares con el mismo valor, sin conversion', () => {
  const raiz = construirDetalle(doc, emitida({ codigoMoneda: 'DOL', importeTotal: 100 }));
  assert.equal(valorDe(raiz, 'Importe total'), 'US$ 100,00');
});

test('Borrador sin CAE: CAE, vencimiento, resultado, observaciones y fecha de anulacion como raya', () => {
  const raiz = construirDetalle(doc, borrador({ fechaAnulacion: null, observaciones: null }));
  for (const etiqueta of ['CAE', 'Vencimiento del CAE', 'Resultado de ARCA', 'Observaciones', 'Fecha de anulación']) {
    assert.equal(valorDe(raiz, etiqueta), '—', etiqueta);
  }
});

test('Factura anulada: fecha de anulacion dd/mm/aaaa y observaciones', () => {
  const raiz = construirDetalle(doc, emitida({ estado: 'ANULADA', fechaAnulacion: '2026-11-05', observaciones: 'Error en el cliente' }));
  assert.equal(valorDe(raiz, 'Fecha de anulación'), '05/11/2026');
  assert.equal(valorDe(raiz, 'Observaciones'), 'Error en el cliente');
});

test('Denominacion que parece HTML: queda literal y no crea ningun img', () => {
  const texto = '<img src=x onerror=alert(1)>';
  const raiz = construirDetalle(doc, emitida({ clienteDenominacion: texto, observaciones: texto, detalles: [{ ...MARTILLO, descripcion: texto }] }));
  assert.equal(valorDe(raiz, 'Cliente'), texto);
  assert.equal(raiz.querySelectorAll('img').length, 0);
  assert.equal(raiz.querySelector('table').tBodies[0].rows[0].cells[1].textContent, texto);
});
