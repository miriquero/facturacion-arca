import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../dist/services/ApiError.js';
import { arrancarApp, esperarHashchange, esperarTareas, fila } from '../soporte-pruebas/soporte.mjs';

const BASE = 'http://localhost:5173/';
const filas = (t) => [...t.$('renglones').querySelectorAll('fieldset.renglon')];
const campo = (t, i, nombre) => filas(t)[i].querySelector(`[name="${nombre}"]`);
const poner = (t, el, valor) => {
  el.value = valor;
  el.dispatchEvent(new t.ventana.Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }));
};

test('Entrar a la alta despues de haber escrito valores: formulario vacio, modo alta, un renglon y titulo Nuevo borrador', async () => {
  const t = await arrancarApp({ url: `${BASE}#/nueva` });
  poner(t, campo(t, 0, 'cantidad'), '7');
  poner(t, t.$('observaciones'), 'algo escrito');
  t.$('agregar-renglon').click();
  assert.equal(filas(t).length, 2);
  await t.navegar('#/');
  await t.navegar('#/nueva');
  assert.equal(t.$('pantalla-formulario').hidden, false);
  assert.equal(filas(t).length, 1);
  assert.equal(campo(t, 0, 'cantidad').value, '');
  assert.equal(t.$('observaciones').value, '');
  assert.equal(t.$('titulo-formulario').textContent, 'Nuevo borrador');
  assert.equal(t.$('guardar').textContent, 'Guardar borrador');
});

test('Cancelar visible en ambos modos: en la alta el boton Cancelar no esta oculto', async () => {
  const t = await arrancarApp({ url: `${BASE}#/nueva` });
  assert.equal(t.$('cancelar-edicion').hidden, false);
  assert.equal(t.$('cancelar-edicion').textContent, 'Cancelar');
  assert.equal(t.$('cancelar-edicion').getAttribute('type'), 'button');
});

test('Cancelar el alta: se muestra el listado y no se envio ningun POST', async () => {
  const t = await arrancarApp({ url: `${BASE}#/nueva` });
  poner(t, campo(t, 0, 'cantidad'), '3');
  const llegada = esperarHashchange(t.ventana);
  t.$('cancelar-edicion').click();
  await llegada;
  assert.equal(t.$('pantalla-listado').hidden, false);
  assert.equal(t.servicio.de('crearFactura').length, 0);
});
