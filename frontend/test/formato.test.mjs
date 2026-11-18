import assert from 'node:assert/strict';
import { test } from 'node:test';
import { formatearFecha, formatearImporte, formatearNumero } from '../dist/ui/formato.js';

test('PES se muestra en pesos argentinos con dos decimales', () => {
  assert.equal(formatearImporte(7260, 'PES'), '$ 7.260,00');
});

test('DOL se muestra en dolares con el mismo valor, sin conversion', () => {
  assert.equal(formatearImporte(9680, 'DOL'), 'US$ 9.680,00');
});

test('codigo desconocido deja EUR visible junto al importe', () => {
  const texto = formatearImporte(100, 'EUR');
  assert.ok(texto.includes('EUR'));
  assert.ok(texto.includes('100,00'));
});

test('sin moneda queda visible como Sin moneda', () => {
  assert.equal(formatearImporte(100, null), 'Sin moneda 100,00');
});

test('fecha 2026-11-01 se muestra 01/11/2026', () => {
  assert.equal(formatearFecha('2026-11-01'), '01/11/2026');
});

test('fecha 2026-12-31 se muestra 31/12/2026', () => {
  assert.equal(formatearFecha('2026-12-31'), '31/12/2026');
});

test('numero de factura nulo es un guion y 3 es 3', () => {
  assert.equal(formatearNumero(null), '—');
  assert.equal(formatearNumero(3), '3');
});
