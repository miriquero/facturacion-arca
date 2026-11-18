import assert from 'node:assert/strict';
import { test } from 'node:test';
import { JSDOM } from 'jsdom';
import { esperarHashchange } from '../soporte-pruebas/soporte.mjs';

/** Pagina minima con un enlace de fragmento; registra los hashchange que dispara jsdom. */
function pagina() {
  const dom = new JSDOM('<!doctype html><a id="enlace" href="#/factura/5">Ver</a>', { url: 'http://localhost:5173/' });
  const ventana = dom.window;
  const eventos = [];
  ventana.addEventListener('hashchange', () => eventos.push(ventana.location.hash));
  return { ventana, eventos };
}

test('jsdom (aprendizaje): asignar location.hash dispara hashchange, pero de forma asincrona', async () => {
  const { ventana, eventos } = pagina();
  const llegada = esperarHashchange(ventana);
  ventana.location.hash = '#/nueva';
  assert.deepEqual(eventos, [], 'no es sincrono');
  await llegada;
  assert.deepEqual(eventos, ['#/nueva']);
});

test('jsdom (aprendizaje): location.replace con fragmento dispara hashchange', async () => {
  const { ventana, eventos } = pagina();
  const llegada = esperarHashchange(ventana);
  ventana.location.replace('#/nueva');
  await llegada;
  assert.deepEqual(eventos, ['#/nueva']);
});

test('jsdom (aprendizaje): history.back() dispara hashchange con el fragmento anterior', async () => {
  const { ventana, eventos } = pagina();
  const primero = esperarHashchange(ventana);
  ventana.location.hash = '#/nueva';
  await primero;
  const segundo = esperarHashchange(ventana);
  ventana.history.back();
  await segundo;
  assert.deepEqual(eventos, ['#/nueva', '']);
});

test('jsdom (aprendizaje): el clic en un enlace de fragmento dispara hashchange', async () => {
  const { ventana, eventos } = pagina();
  const llegada = esperarHashchange(ventana);
  ventana.document.getElementById('enlace').click();
  await llegada;
  assert.deepEqual(eventos, ['#/factura/5']);
});

// --- Enrutador ---
import { Enrutador } from '../dist/navegacion/enrutador.js';

function enrutadorEn(url) {
  const dom = new JSDOM('<!doctype html><p>x</p>', { url });
  const ventana = dom.window;
  const cambios = [];
  const enrutador = new Enrutador(ventana, (c) => {
    cambios.push(c);
  });
  return { ventana, cambios, enrutador };
}

test('iniciar() entrega la ruta del fragmento actual como inicial', async () => {
  const { cambios, enrutador } = enrutadorEn('http://localhost:5173/#/factura/5');
  await enrutador.iniciar();
  assert.deepEqual(cambios, [{ ruta: { tipo: 'detalle', id: 5 }, mensaje: null, inicial: true }]);
});

test('un cambio de fragmento llama a alCambiar con la ruta nueva', async () => {
  const { ventana, cambios, enrutador } = enrutadorEn('http://localhost:5173/');
  await enrutador.iniciar();
  const llegada = esperarHashchange(ventana);
  ventana.location.hash = '#/nueva';
  await llegada;
  assert.deepEqual(cambios.at(-1), { ruta: { tipo: 'alta' }, mensaje: null, inicial: false });
  assert.deepEqual(enrutador.rutaActual, { tipo: 'alta' });
});

test('ir(ruta) agrega una entrada al historial; con reemplazar no', async () => {
  const { ventana, cambios, enrutador } = enrutadorEn('http://localhost:5173/');
  await enrutador.iniciar();
  const antes = ventana.history.length;
  const primera = esperarHashchange(ventana);
  enrutador.ir({ tipo: 'detalle', id: 7 });
  await primera;
  assert.equal(ventana.history.length, antes + 1);
  assert.equal(ventana.location.hash, '#/factura/7');
  const segunda = esperarHashchange(ventana);
  enrutador.ir({ tipo: 'listado' }, { reemplazar: true });
  await segunda;
  assert.equal(ventana.history.length, antes + 1);
  assert.equal(ventana.location.hash, '#/');
  assert.deepEqual(cambios.map((c) => c.ruta.tipo), ['listado', 'detalle', 'listado']);
});

test('ir(ruta, { mensaje }) entrega el mensaje con ese cambio y el siguiente cambio del usuario no lo repite', async () => {
  const { ventana, cambios, enrutador } = enrutadorEn('http://localhost:5173/');
  await enrutador.iniciar();
  const mensaje = { tipo: 'exito', texto: 'Se creó el borrador 15.' };
  const primera = esperarHashchange(ventana);
  enrutador.ir({ tipo: 'detalle', id: 15 }, { mensaje });
  await primera;
  assert.deepEqual(cambios.at(-1).mensaje, mensaje);
  const segunda = esperarHashchange(ventana);
  ventana.location.hash = '#/nueva';
  await segunda;
  assert.equal(cambios.at(-1).mensaje, null);
});

test('ir a la misma direccion no dispara hashchange: se procesa directamente con su mensaje', async () => {
  const { cambios, enrutador } = enrutadorEn('http://localhost:5173/#/factura/5');
  await enrutador.iniciar();
  enrutador.ir({ tipo: 'detalle', id: 5 }, { mensaje: { tipo: 'error', texto: 'x' } });
  assert.equal(cambios.length, 2);
  assert.deepEqual(cambios[1].mensaje, { tipo: 'error', texto: 'x' });
});
