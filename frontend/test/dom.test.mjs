import assert from 'node:assert/strict';
import { test } from 'node:test';
import { crear, opcion, reemplazarHijos } from '../dist/ui/dom.js';
import { JSDOM } from 'jsdom';

const montarPagina = () => ({ documento: new JSDOM('<!doctype html><body></body>').window.document });

test('crear: texto que parece HTML queda literal y no crea img', () => {
  const { documento } = montarPagina();
  const td = crear(documento, 'td', '<img src=x onerror=alert(1)>');
  assert.equal(td.textContent, '<img src=x onerror=alert(1)>');
  assert.equal(td.querySelector('img'), null);
});

test('crear aplica atributos', () => {
  const { documento } = montarPagina();
  const b = crear(documento, 'button', 'Ir', { type: 'button', 'data-id': '4' });
  assert.equal(b.getAttribute('type'), 'button');
  assert.equal(b.getAttribute('data-id'), '4');
});

test('opcion: etiqueta <b>Casa</b> literal y valor asignado', () => {
  const { documento } = montarPagina();
  const select = documento.createElement('select');
  select.appendChild(opcion(documento, '7', '<b>Casa</b>'));
  assert.equal(select.options[0].text, '<b>Casa</b>');
  assert.equal(select.options[0].value, '7');
  assert.equal(select.querySelector('b'), null);
});

test('reemplazarHijos deja solo los nodos nuevos', () => {
  const { documento } = montarPagina();
  const ul = documento.createElement('ul');
  ul.appendChild(documento.createElement('li'));
  reemplazarHijos(ul, [crear(documento, 'li', 'a'), crear(documento, 'li', 'b')]);
  assert.deepEqual([...ul.children].map((n) => n.textContent), ['a', 'b']);
});
