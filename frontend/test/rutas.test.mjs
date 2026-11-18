import assert from 'node:assert/strict';
import { test } from 'node:test';
import { hrefDe, interpretarRuta } from '../dist/navegacion/rutas.js';

test('Rutas conocidas: listado, alta, detalle y edicion', () => {
  assert.deepEqual(interpretarRuta(''), { tipo: 'listado' });
  assert.deepEqual(interpretarRuta('#/'), { tipo: 'listado' });
  assert.deepEqual(interpretarRuta('#/nueva'), { tipo: 'alta' });
  assert.deepEqual(interpretarRuta('#/factura/5'), { tipo: 'detalle', id: 5 });
  assert.deepEqual(interpretarRuta('#/factura/5/editar'), { tipo: 'edicion', id: 5 });
});

test('Rutas desconocidas: id no positivo o no entero, ruta ajena y sufijo extra', () => {
  for (const fragmento of ['#/facturas', '#/factura/abc', '#/factura/0', '#/factura/-3', '#/factura/2.5', '#/factura/5/borrar']) {
    assert.deepEqual(interpretarRuta(fragmento), { tipo: 'desconocida', fragmento }, fragmento);
  }
});

test('Un id mayor que Number.MAX_SAFE_INTEGER es ruta desconocida', () => {
  const fragmento = '#/factura/9007199254740993';
  assert.deepEqual(interpretarRuta(fragmento), { tipo: 'desconocida', fragmento });
});

test('Un id de varios digitos se interpreta completo', () => {
  assert.deepEqual(interpretarRuta('#/factura/120'), { tipo: 'detalle', id: 120 });
  assert.deepEqual(interpretarRuta('#/factura/9007199254740991/editar'), { tipo: 'edicion', id: 9007199254740991 });
});

test('hrefDe: direccion de cada ruta, con ida y vuelta por interpretarRuta', () => {
  const casos = [
    [{ tipo: 'listado' }, '#/'],
    [{ tipo: 'alta' }, '#/nueva'],
    [{ tipo: 'detalle', id: 5 }, '#/factura/5'],
    [{ tipo: 'edicion', id: 5 }, '#/factura/5/editar'],
  ];
  for (const [ruta, esperado] of casos) {
    assert.equal(hrefDe(ruta), esperado);
    assert.deepEqual(interpretarRuta(hrefDe(ruta)), ruta);
  }
});
