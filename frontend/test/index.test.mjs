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
