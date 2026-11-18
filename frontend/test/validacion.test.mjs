import assert from 'node:assert/strict';
import { test } from 'node:test';
import { validarFiltros, validarFormulario, validarMotivo } from '../dist/ui/validacion.js';

const renglon = (extra = {}) => ({ listaPrecioArticuloId: '1', cantidad: '1', porcentajeBonificacion: '', descripcion: '', ...extra });
const form = (extra = {}, renglones = [renglon()]) => ({
  clienteId: '', condicionIvaId: '2', puntoVentaId: '1', tipoMonedaId: '1', fechaEmision: '', observaciones: '', renglones, ...extra,
});

test('formulario minimo valido: sin clienteId, fecha ni observaciones; renglon con solo lo obligatorio', () => {
  const r = validarFormulario(form());
  assert.deepEqual(r.errores, {});
  assert.deepEqual(r.datos, { condicionIvaId: 2, puntoVentaId: 1, tipoMonedaId: 1, detalles: [{ listaPrecioArticuloId: 1, cantidad: 1 }] });
});

for (const cantidad of ['0', '-2', '', '1e400']) {
  test(`cantidad invalida "${cantidad}" marca detalles[0].cantidad`, () => {
    const r = validarFormulario(form({}, [renglon({ cantidad })]));
    assert.ok(r.errores['detalles[0].cantidad'], 'falta el error de cantidad');
    assert.equal(r.datos, undefined);
  });
}

test('cantidad 0.5 es valida', () => {
  const r = validarFormulario(form({}, [renglon({ cantidad: '0.5' })]));
  assert.deepEqual(r.errores, {});
  assert.equal(r.datos.detalles[0].cantidad, 0.5);
});

test('bonificacion 0 y 100 validas y enviadas; 100.01 y -0.01 invalidas', () => {
  const ok = validarFormulario(form({}, [renglon({ porcentajeBonificacion: '0' }), renglon({ porcentajeBonificacion: '100' })]));
  assert.deepEqual(ok.errores, {});
  assert.equal(ok.datos.detalles[0].porcentajeBonificacion, 0);
  assert.equal(ok.datos.detalles[1].porcentajeBonificacion, 100);
  for (const valor of ['100.01', '-0.01']) {
    const r = validarFormulario(form({}, [renglon({ porcentajeBonificacion: valor })]));
    assert.ok(r.errores['detalles[0].porcentajeBonificacion'], valor);
  }
});

test('descripcion de 255 valida y completa; 256 invalida con mensaje que menciona 255', () => {
  const ok = validarFormulario(form({}, [renglon({ descripcion: 'a'.repeat(255) })]));
  assert.deepEqual(ok.errores, {});
  assert.equal(ok.datos.detalles[0].descripcion.length, 255);
  const mal = validarFormulario(form({}, [renglon({ descripcion: 'a'.repeat(256) })]));
  assert.match(mal.errores['detalles[0].descripcion'], /255/);
});

test('observaciones 500 validas; 501 invalidas con mensaje que menciona 500', () => {
  assert.deepEqual(validarFormulario(form({ observaciones: 'o'.repeat(500) })).errores, {});
  assert.match(validarFormulario(form({ observaciones: 'o'.repeat(501) })).errores.observaciones, /500/);
});

test('obligatorios de cabecera sin elegir', () => {
  const r = validarFormulario(form({ puntoVentaId: '', tipoMonedaId: '', condicionIvaId: '' }));
  assert.deepEqual(Object.keys(r.errores).sort(), ['condicionIvaId', 'puntoVentaId', 'tipoMonedaId']);
});

test('cliente elegido se envia; vacio se omite', () => {
  assert.equal(validarFormulario(form({ clienteId: '3' })).datos.clienteId, 3);
  assert.equal('clienteId' in validarFormulario(form()).datos, false);
});

test('sin renglones: error en la clave detalles', () => {
  const r = validarFormulario(form({}, []));
  assert.ok(r.errores.detalles);
});

test('fecha de emision informada se envia', () => {
  assert.equal(validarFormulario(form({ fechaEmision: '2026-11-01' })).datos.fechaEmision, '2026-11-01');
});

test('renglon sin opcion elegida marca listaPrecioArticuloId', () => {
  const r = validarFormulario(form({}, [renglon({ listaPrecioArticuloId: '' })]));
  assert.ok(r.errores['detalles[0].listaPrecioArticuloId']);
});

test('motivo: vacio y solo espacios -> sin motivo; 500 valido; 501 invalido', () => {
  assert.deepEqual(validarMotivo('   '), { datos: undefined, errores: {} });
  assert.deepEqual(validarMotivo(' Error '), { datos: 'Error', errores: {} });
  assert.deepEqual(validarMotivo('m'.repeat(500)).errores, {});
  assert.match(validarMotivo('m'.repeat(501)).errores.motivo, /500/);
});

test('montoMinimo: -1 invalido, 0 valido, vacio ausente', () => {
  const f = (montoMinimo) => validarFiltros({ fechaDesde: '', fechaHasta: '', estado: '', montoMinimo });
  assert.ok(f('-1').errores.montoMinimo);
  assert.deepEqual(f('0').datos, { montoMinimo: 0 });
  assert.deepEqual(f('').datos, {});
  assert.ok(f('abc').errores.montoMinimo);
});

test('filtros: estado y fechas informados pasan; vacios se omiten', () => {
  const r = validarFiltros({ fechaDesde: '2026-11-01', fechaHasta: '', estado: 'EMITIDA', montoMinimo: '' });
  assert.deepEqual(r.datos, { fechaDesde: '2026-11-01', estado: 'EMITIDA' });
});
