import assert from 'node:assert/strict';
import { test } from 'node:test';
import { JSDOM } from 'jsdom';
import { leerApiBaseUrl } from '../dist/config.js';

function documentoCon(headHtml) {
  return new JSDOM(`<!doctype html><html><head>${headHtml}</head><body></body></html>`).window.document;
}

test('devuelve la URL del meta api-base-url', () => {
  const doc = documentoCon('<meta name="api-base-url" content="http://127.0.0.1:9090/api">');
  assert.equal(leerApiBaseUrl(doc), 'http://127.0.0.1:9090/api');
});

test('devuelve otra URL distinta cuando el meta cambia', () => {
  const doc = documentoCon('<meta name="api-base-url" content="http://localhost:8081/api">');
  assert.equal(leerApiBaseUrl(doc), 'http://localhost:8081/api');
});

test('falla nombrando api-base-url si el meta no existe', () => {
  assert.throws(() => leerApiBaseUrl(documentoCon('')), /api-base-url/);
});

test('falla nombrando api-base-url si el meta esta vacio', () => {
  const doc = documentoCon('<meta name="api-base-url" content="   ">');
  assert.throws(() => leerApiBaseUrl(doc), /api-base-url/);
});
