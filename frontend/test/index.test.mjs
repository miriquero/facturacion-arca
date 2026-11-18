import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { JSDOM } from 'jsdom';

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const documento = new JSDOM(html).window.document;

test('index.html declara el meta api-base-url con la URL del backend', () => {
  const meta = documento.querySelector('meta[name="api-base-url"]');
  assert.ok(meta, 'falta <meta name="api-base-url">');
  assert.equal(meta.getAttribute('content'), 'http://localhost:8080/api');
});

test('index.html carga el modulo principal como type="module"', () => {
  const script = documento.querySelector('script[type="module"]');
  assert.ok(script, 'falta <script type="module">');
  assert.equal(script.getAttribute('src'), './dist/app.js');
});

test('index.html tiene el contenedor #estado-api para mostrar la URL', () => {
  assert.ok(documento.querySelector('#estado-api'), 'falta #estado-api');
});

test('existe una unica #estado con role=status y aria-live=polite, sin hidden', () => {
  const regiones = documento.querySelectorAll('[role="status"]');
  assert.equal(regiones.length, 1);
  const estado = documento.querySelector('#estado');
  assert.equal(estado, regiones[0]);
  assert.equal(estado.getAttribute('aria-live'), 'polite');
  assert.equal(estado.hasAttribute('hidden'), false);
});

test('formularios de filtros y de factura con novalidate', () => {
  assert.ok(documento.querySelector('form#form-filtros[novalidate]'));
  assert.ok(documento.querySelector('form#form-factura[novalidate]'));
});

test('la tabla tiene caption y encabezados th scope=col', () => {
  const tabla = documento.querySelector('table');
  assert.ok(tabla.querySelector('caption'));
  const ths = [...tabla.querySelectorAll('thead th')];
  assert.ok(ths.length >= 9);
  assert.ok(ths.every((th) => th.getAttribute('scope') === 'col'));
});

test('enlaces de descarga PDF y Excel con href y ninguno de TXT', () => {
  for (const id of ['descarga-pdf', 'descarga-xlsx']) {
    const a = documento.getElementById(id);
    assert.equal(a.tagName, 'A');
    assert.ok(a.hasAttribute('href'));
  }
  const textos = [...documento.querySelectorAll('a')].map((a) => `${a.id} ${a.textContent} ${a.getAttribute('href')}`.toLowerCase());
  assert.ok(!textos.some((t) => t.includes('txt')));
});

test('todo input, select y textarea tiene un label asociado', () => {
  for (const campo of documento.querySelectorAll('input, select, textarea')) {
    const porFor = campo.id && documento.querySelector(`label[for="${campo.id}"]`);
    assert.ok(porFor || campo.closest('label'), `sin label: ${campo.id || campo.name}`);
  }
});

test('ningun tabindex positivo y todos los button declaran type', () => {
  for (const el of documento.querySelectorAll('[tabindex]')) assert.ok(Number(el.getAttribute('tabindex')) <= 0);
  for (const b of documento.querySelectorAll('button')) assert.ok(b.hasAttribute('type'), b.textContent);
});

test('ids unicos en todo el documento', () => {
  const ids = [...documento.querySelectorAll('[id]')].map((e) => e.id);
  assert.deepEqual(ids.filter((id, i) => ids.indexOf(id) !== i), []);
});

// Cambio por el pedido (etapa 7): antes «una sola h1 y un landmark main»; ahora cada pantalla tiene su h1,
// exactamente uno está visible (las demás secciones llevan hidden) y sigue habiendo un solo main.
test('un h1 por pantalla, exactamente uno visible, y un main', () => {
  const secciones = [...documento.querySelectorAll('main > section.pantalla')];
  assert.equal(secciones.length, 3);
  for (const seccion of secciones) {
    const titulos = seccion.querySelectorAll('h1');
    assert.equal(titulos.length, 1, seccion.id);
    assert.equal(titulos[0].getAttribute('tabindex'), '-1', seccion.id);
  }
  assert.equal(documento.querySelectorAll('h1').length, 3);
  assert.equal(documento.querySelectorAll('h1').length, documento.querySelectorAll('section.pantalla h1').length);
  const visibles = secciones.filter((s) => !s.hidden);
  assert.equal(visibles.length, 1);
  assert.equal(documento.querySelectorAll('main').length, 1);
});

test('las tres secciones de pantalla tienen los ids del diseno', () => {
  assert.deepEqual(
    [...documento.querySelectorAll('section.pantalla')].map((s) => s.id),
    ['pantalla-listado', 'pantalla-formulario', 'pantalla-detalle'],
  );
});

test('barra de navegacion nombrada con Facturas (#/) y Nueva factura (#/nueva)', () => {
  const nav = documento.querySelector('nav[aria-label="Principal"]');
  assert.ok(nav, 'falta la nav Principal');
  const enlaces = [...nav.querySelectorAll('a')].map((a) => [a.textContent.trim(), a.getAttribute('href')]);
  assert.deepEqual(enlaces, [
    ['Facturas', '#/'],
    ['Nueva factura', '#/nueva'],
  ]);
  assert.equal(nav.closest('section.pantalla'), null);
});

test('la region de estado y el dialogo explorador quedan fuera de las secciones de pantalla', () => {
  assert.equal(documento.getElementById('estado').closest('section.pantalla'), null);
  assert.equal(documento.getElementById('explorador').closest('section.pantalla'), null);
  assert.equal(documento.querySelectorAll('dialog#explorador').length, 1);
});

test('el titulo del formulario es el h1 de su pantalla', () => {
  const titulo = documento.getElementById('titulo-formulario');
  assert.equal(titulo.tagName, 'H1');
  assert.equal(titulo.closest('section.pantalla').id, 'pantalla-formulario');
});
