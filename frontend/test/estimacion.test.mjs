import assert from 'node:assert/strict';
import { test } from 'node:test';
import { netoEstimado, precioEnMoneda } from '../dist/ui/estimacion.js';

test('3000.00 x 2 con 10% mas 8000.00 x 1 estima 13400', () => {
  assert.equal(netoEstimado([
    { precio: 3000, cantidad: 2, bonificacion: 10 },
    { precio: 8000, cantidad: 1, bonificacion: 0 },
  ]), 13400);
});

test('renglon sin opcion o con cantidad invalida no suma', () => {
  assert.equal(netoEstimado([
    { precio: null, cantidad: 2, bonificacion: 0 },
    { precio: 8000, cantidad: null, bonificacion: 0 },
    { precio: 8000, cantidad: 1, bonificacion: 0 },
  ]), 8000);
});

test('precioEnMoneda: 9000 pesos a 1550 pesos por dolar son 5,81', () => {
  assert.equal(precioEnMoneda(9000, 1550), 5.81);
});

test('precioEnMoneda: 8000 pesos son 5,16 dolares (redondeo a dos decimales)', () => {
  assert.equal(precioEnMoneda(8000, 1550), 5.16);
});

test('precioEnMoneda: con cotizacion 1 o invalida el precio queda igual', () => {
  assert.equal(precioEnMoneda(8000.125, 1), 8000.125);
  assert.equal(precioEnMoneda(8000, 0), 8000);
  assert.equal(precioEnMoneda(8000, NaN), 8000);
  assert.equal(precioEnMoneda(8000, -5), 8000);
});
