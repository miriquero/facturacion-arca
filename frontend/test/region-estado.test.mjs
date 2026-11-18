import assert from 'node:assert/strict';
import { test } from 'node:test';
import { RegionEstado } from '../dist/ui/regionEstado.js';
import { JSDOM } from 'jsdom';
import { temporizadorManual } from '../soporte-pruebas/soporte.mjs';

function region() {
  const { document: documento } = new JSDOM('<div id="estado" role="status" aria-live="polite"></div>').window;
  const temporizador = temporizadorManual();
  const elemento = documento.getElementById('estado');
  return { documento, temporizador, elemento, region: new RegionEstado(elemento, temporizador) };
}

test('Limpieza a los 6 s: queda vacia y sigue en el DOM con role y aria-live', () => {
  const { region: r, temporizador, elemento, documento } = region();
  r.exito('Se creó el borrador 15');
  assert.equal(elemento.textContent, 'Se creó el borrador 15');
  temporizador.avanzar(5999);
  assert.equal(elemento.textContent, 'Se creó el borrador 15');
  temporizador.avanzar(1);
  assert.equal(elemento.textContent, '');
  assert.equal(documento.getElementById('estado'), elemento);
  assert.equal(elemento.getAttribute('role'), 'status');
  assert.equal(elemento.getAttribute('aria-live'), 'polite');
});

test('Mensaje nuevo antes de los 6 s: el error sigue visible', () => {
  const { region: r, temporizador, elemento } = region();
  r.exito('ok');
  temporizador.avanzar(3000);
  r.error('Falló algo');
  temporizador.avanzar(4000);
  assert.match(elemento.textContent, /Falló algo/);
});

test('Error persistente a los 60 s con prefijo Error:; limpiar() lo retira', () => {
  const { region: r, temporizador, elemento } = region();
  r.error('No hay conexión');
  temporizador.avanzar(60000);
  assert.equal(elemento.textContent, 'Error: No hay conexión');
  r.limpiar();
  assert.equal(elemento.textContent, '');
});

test('Detalles por campo como lista', () => {
  const { region: r, elemento } = region();
  r.error('Datos inválidos', ['puntoVentaId: es obligatorio', 'tipoMonedaId: es obligatorio']);
  const items = [...elemento.querySelectorAll('ul > li')].map((li) => li.textContent);
  assert.deepEqual(items, ['puntoVentaId: es obligatorio', 'tipoMonedaId: es obligatorio']);
});

test('progreso no se limpia solo', () => {
  const { region: r, temporizador, elemento } = region();
  r.progreso('Guardando borrador…');
  temporizador.avanzar(60000);
  assert.equal(elemento.textContent, 'Guardando borrador…');
});

test('mensaje de error que parece HTML queda literal y no crea script', () => {
  const { region: r, elemento } = region();
  r.error('<script>x()</script>');
  assert.equal(elemento.querySelector('script'), null);
  assert.equal(elemento.textContent, 'Error: <script>x()</script>');
});
