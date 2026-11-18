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

test('una sola h1 y un landmark main', () => {
  assert.equal(documento.querySelectorAll('h1').length, 1);
  assert.equal(documento.querySelectorAll('main').length, 1);
});
