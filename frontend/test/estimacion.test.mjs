import assert from 'node:assert/strict';
import { test } from 'node:test';
import { netoEstimado } from '../dist/ui/estimacion.js';

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
