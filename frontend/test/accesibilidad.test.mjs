import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { catalogosEjemplo, dialogosFalsos, esperarTareas, fila, montarPagina, servicioFalso, temporizadorManual } from '../soporte-pruebas/soporte.mjs';

const css = readFileSync(new URL('../styles.css', import.meta.url), 'utf8');
const variables = Object.fromEntries([...css.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map((m) => [m[1], m[2]]));

function luminancia(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
function contraste(a, b) {
  const [x, y] = [luminancia(a), luminancia(b)];
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}

// [texto o control, fondo, minimo, descripcion]: texto >= 4,5; controles, bordes y foco >= 3
const PARES = [
  [variables.texto, variables.fondo, 4.5, 'texto sobre fondo'],
  [variables.apagado, variables.fondo, 4.5, 'texto apagado sobre fondo'],
  ['#ffffff', variables.primario, 4.5, 'texto de boton principal'],
  [variables.primario, variables.fondo, 4.5, 'boton secundario y enlaces'],
  [variables.borde, variables.fondo, 3, 'borde de controles'],
  [variables.foco, variables.fondo, 3, 'indicador de foco'],
  [variables.error, variables['error-fondo'], 4.5, 'mensaje de error'],
  [variables.exito, variables['exito-fondo'], 4.5, 'mensaje de exito'],
  [variables.apagado, '#e6e6e6', 4.5, 'boton deshabilitado'],
];

for (const [primero, segundo, minimo, descripcion] of PARES) {
  test(`contraste calculado de la paleta: ${descripcion} >= ${minimo}:1`, () => {
    assert.ok(primero && segundo, 'variable CSS no encontrada');
    assert.ok(contraste(primero, segundo) >= minimo, `${primero} sobre ${segundo} = ${contraste(primero, segundo).toFixed(2)}`);
  });
}

test('estilos: foco visible, clase visualmente-oculto y botones de al menos 44 px', () => {
  assert.match(css, /:focus-visible\s*\{[^}]*outline:\s*3px/);
  assert.match(css, /\.visualmente-oculto/);
  assert.match(css, /button[^{]*\{[^}]*min-height:\s*2\.75rem/);
});

async function paginaConDatos() {
  const pagina = montarPagina();
  const servicio = servicioFalso({
    obtenerCatalogos: catalogosEjemplo(),
    obtenerFacturas: [fila({ id: 6, estado: 'BORRADOR', numeroFactura: null }), fila({ id: 1 }), fila({ id: 3, estado: 'ANULADA' })],
  });
  const app = iniciarAplicacion({ documento: pagina.documento, servicio, dialogos: dialogosFalsos(), temporizador: temporizadorManual() });
  await app.listo;
  pagina.documento.getElementById('agregar-renglon').click();
  await esperarTareas();
  return pagina.documento;
}

test('pagina con datos: todo campo tiene label, ids unicos, botones con type y sin tabindex positivo', async () => {
  const doc = await paginaConDatos();
  for (const campo of doc.querySelectorAll('input, select, textarea')) {
    assert.ok((campo.id && doc.querySelector(`label[for="${campo.id}"]`)) || campo.closest('label'), `sin label: ${campo.id}`);
  }
  const ids = [...doc.querySelectorAll('[id]')].map((e) => e.id);
  assert.deepEqual(ids.filter((id, i) => ids.indexOf(id) !== i), []);
  for (const b of doc.querySelectorAll('button')) assert.ok(b.getAttribute('type'), b.textContent);
  assert.equal(doc.querySelectorAll('[tabindex]:not([tabindex="0"]):not([tabindex="-1"])').length, 0);
});

test('pagina con datos: los campos de cantidad se distinguen como renglon 1 y renglon 2', async () => {
  const doc = await paginaConDatos();
  const nombres = [...doc.querySelectorAll('#renglones [name="cantidad"]')].map((c) => doc.querySelector(`label[for="${c.id}"]`).textContent);
  assert.deepEqual(nombres, ['Cantidad (renglón 1)', 'Cantidad (renglón 2)']);
});

test('pagina con datos: los botones de accion nombran la accion y la factura', async () => {
  const doc = await paginaConDatos();
  const nombres = [...doc.querySelectorAll('#filas button')].map((b) => b.textContent);
  assert.deepEqual(nombres, ['Editar borrador 6', 'Emitir borrador 6', 'Eliminar borrador 6', 'Anular factura 1']);
});

test('pagina con datos: el estado se comunica con texto, no solo con color', async () => {
  const doc = await paginaConDatos();
  assert.deepEqual([...doc.querySelectorAll('#filas tr')].map((r) => r.cells[7].textContent), ['BORRADOR', 'EMITIDA', 'ANULADA']);
});

test('orden de tabulacion = orden del DOM: filtros, descargas, tabla, formulario, guardar', async () => {
  const doc = await paginaConDatos();
  const orden = [...doc.querySelectorAll('a[href], button, input, select, textarea, [tabindex="0"]')].map((e) => e.id || e.getAttribute('name') || e.tagName);
  const pos = (id) => orden.indexOf(id);
  assert.ok(pos('filtro-desde') < pos('buscar'));
  assert.ok(pos('buscar') < pos('descarga-pdf'));
  assert.ok(pos('descarga-xlsx') < pos('cliente'));
  assert.ok(pos('agregar-renglon') < pos('guardar'));
});
