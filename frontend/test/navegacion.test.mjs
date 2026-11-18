import assert from 'node:assert/strict';
import { test } from 'node:test';
import { arrancarApp, borradorDetallado, esperarHashchange, esperarTareas } from '../soporte-pruebas/soporte.mjs';

const BASE = 'http://localhost:5173/';
const PANTALLAS = ['pantalla-listado', 'pantalla-formulario', 'pantalla-detalle'];
const ocultas = (t) => PANTALLAS.filter((id) => t.$(id).hidden);

test('Entrada directa al detalle: solo el detalle visible y no se pide el listado', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/5` });
  assert.deepEqual(ocultas(t), ['pantalla-listado', 'pantalla-formulario']);
  assert.equal(t.$('pantalla-detalle').hidden, false);
  assert.equal(t.servicio.de('obtenerFacturas').length, 0);
});

test('Entrada sin fragmento: el listado visible y se pide su primera pagina', async () => {
  const t = await arrancarApp();
  assert.deepEqual(ocultas(t), ['pantalla-formulario', 'pantalla-detalle']);
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
  assert.deepEqual(t.servicio.de('obtenerFacturas')[0].args, [{}, { pagina: 0, tamanio: 10 }]);
});

test('Cambio de fragmento: con el listado visible, #/nueva muestra la alta', async () => {
  const t = await arrancarApp();
  await t.navegar('#/nueva');
  assert.deepEqual(ocultas(t), ['pantalla-listado', 'pantalla-detalle']);
  assert.equal(t.$('pantalla-formulario').hidden, false);
});

const enlaceNav = (t, texto) => [...t.documento.querySelectorAll('nav[aria-label="Principal"] a')].find((a) => a.textContent.trim() === texto);

test('Listado actual: Facturas lleva aria-current=page y Nueva factura no', async () => {
  const t = await arrancarApp();
  assert.equal(enlaceNav(t, 'Facturas').getAttribute('aria-current'), 'page');
  assert.equal(enlaceNav(t, 'Nueva factura').hasAttribute('aria-current'), false);
  await t.navegar('#/nueva');
  assert.equal(enlaceNav(t, 'Nueva factura').getAttribute('aria-current'), 'page');
  assert.equal(enlaceNav(t, 'Facturas').hasAttribute('aria-current'), false);
});

test('Detalle sin enlace actual: ninguno de los dos enlaces tiene aria-current', async () => {
  const t = await arrancarApp({ url: 'http://localhost:5173/#/factura/5' });
  assert.equal(enlaceNav(t, 'Facturas').hasAttribute('aria-current'), false);
  assert.equal(enlaceNav(t, 'Nueva factura').hasAttribute('aria-current'), false);
});

test('Titulos de las pantallas: listado, alta, detalle y edicion nombran la pantalla y terminan en Facturacion ARCA', async () => {
  const t = await arrancarApp({ auto: { obtenerFactura: borradorDetallado({ id: 5 }) } });
  assert.equal(t.documento.title, 'Facturas — Facturación ARCA');
  await t.navegar('#/nueva');
  assert.equal(t.documento.title, 'Nueva factura — Facturación ARCA');
  await t.navegar('#/factura/5');
  assert.equal(t.documento.title, 'Factura 5 — Facturación ARCA');
  await t.navegar('#/factura/5/editar');
  assert.equal(t.documento.title, 'Editar borrador 5 — Facturación ARCA');
});

const h1Visibles = (t) => [...t.documento.querySelectorAll('h1')].filter((h) => !h.closest('[hidden]'));

test('Foco al navegar: el h1 de la pantalla nueva; en el arranque el foco no se mueve', async () => {
  const t = await arrancarApp({ url: 'http://localhost:5173/#/factura/5' });
  assert.equal(t.documento.activeElement, t.documento.body, 'el arranque no mueve el foco');
  await t.navegar('#/');
  assert.equal(t.documento.activeElement, t.$('titulo-listado'));
  await t.navegar('#/factura/7');
  assert.equal(t.documento.activeElement, t.$('titulo-detalle'));
  await t.navegar('#/nueva');
  assert.equal(t.documento.activeElement, t.$('titulo-formulario'));
});

test('Un h1 visible: exactamente un h1 fuera de las secciones ocultas en cada pantalla', async () => {
  const t = await arrancarApp();
  for (const fragmento of ['#/', '#/nueva', '#/factura/5', '#/factura/5/editar']) {
    await t.navegar(fragmento);
    assert.equal(h1Visibles(t).length, 1, fragmento);
  }
});

test('Error viejo al navegar: el enlace Facturas deja la region de estado vacia', async () => {
  const t = await arrancarApp({ url: 'http://localhost:5173/#/factura/5', auto: { obtenerCatalogos: new Error('sin catalogos') } });
  assert.match(t.$('estado').textContent, /Error/);
  const llegada = esperarHashchange(t.ventana);
  enlaceNav(t, 'Facturas').click();
  await llegada;
  assert.equal(t.$('estado').textContent, '');
  assert.equal(t.$('pantalla-listado').hidden, false);
});

test('Direccion inexistente: #/facturas muestra el listado y explica que la direccion no existe', async () => {
  const t = await arrancarApp({ url: 'http://localhost:5173/#/facturas' });
  assert.equal(t.$('pantalla-listado').hidden, false);
  assert.match(t.$('estado').textContent, /La dirección #\/facturas no existe; se muestra el listado/);
  assert.equal(t.$('estado').className, 'estado--error');
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
});

test('Salir de la pantalla con el explorador abierto lo cierra y el foco va al h1 de la pantalla nueva', async () => {
  const t = await arrancarApp({ url: 'http://localhost:5173/#/nueva', auto: { buscarClientes: [{ id: 2, etiqueta: 'Ana SA', valor: null, codigoMoneda: null }] } });
  t.$('elegir-cliente').click();
  await esperarTareas();
  assert.equal(t.$('explorador').open, true);
  await t.navegar('#/');
  assert.equal(t.$('explorador').open, false);
  assert.deepEqual(t.ventanaModal.cierres, ['explorador']);
  assert.equal(t.documento.activeElement, t.$('titulo-listado'));
});
