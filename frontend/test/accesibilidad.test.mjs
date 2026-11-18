import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { catalogosEjemplo, dialogosFalsos, esperarTareas, fila, montarPagina, servicioFalso, temporizadorManual, ventanaModalFalsa } from '../soporte-pruebas/soporte.mjs';

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
  assert.ok(pos('descarga-xlsx') < pos('elegir-cliente'));
  assert.ok(pos('agregar-renglon') < pos('guardar'));
});

// ---- Etapa 6: explorador modal y paginacion ----

const OPCIONES = catalogosEjemplo();

async function paginaConExplorador() {
  const pagina = montarPagina();
  const servicio = servicioFalso({
    obtenerCatalogos: catalogosEjemplo(),
    obtenerFacturas: [fila({ id: 6, estado: 'BORRADOR', numeroFactura: null }), fila({ id: 1 })],
    buscarClientes: OPCIONES.clientes,
    buscarListaPrecioArticulos: OPCIONES.listaPrecioArticulos,
  });
  const app = iniciarAplicacion({ documento: pagina.documento, servicio, dialogos: dialogosFalsos(), temporizador: temporizadorManual(), ventanaModal: ventanaModalFalsa() });
  await app.listo;
  const doc = pagina.documento;
  doc.getElementById('agregar-renglon').click();
  return { ...pagina, doc, servicio };
}

async function abrir(t, idBoton) {
  t.doc.getElementById(idBoton).click();
  await esperarTareas();
}

const nombreAccesible = (el) => (el.getAttribute('aria-label') ?? el.textContent).trim();

test('explorador abierto: el dialogo se nombra por aria-labelledby, el campo tiene label y el error se anuncia con role=alert', async () => {
  const t = await paginaConExplorador();
  await abrir(t, 'elegir-cliente');
  const dialogo = t.doc.getElementById('explorador');
  assert.equal(dialogo.open, true);
  assert.equal(t.doc.getElementById(dialogo.getAttribute('aria-labelledby')).textContent, 'Buscar cliente');
  assert.equal(t.doc.querySelector('label[for="explorador-q"]').textContent, 'Texto a buscar');
  assert.equal(t.doc.getElementById('explorador-error').getAttribute('role'), 'alert');
  assert.equal(t.doc.getElementById('explorador-cantidad').getAttribute('aria-live'), 'polite');
  assert.equal(t.doc.activeElement, t.doc.getElementById('explorador-q'));
});

test('explorador abierto: todos los accionables son nativos, sin tabindex positivo y con type; todo campo tiene label', async () => {
  const t = await paginaConExplorador();
  await abrir(t, 'elegir-cliente');
  const accionables = t.doc.querySelectorAll('button, a, input, select, textarea, [role="button"], [onclick]');
  for (const el of accionables) {
    assert.ok(['BUTTON', 'A', 'INPUT', 'SELECT', 'TEXTAREA'].includes(el.tagName), `no nativo: ${el.outerHTML.slice(0, 60)}`);
    if (el.tagName === 'A') assert.ok(el.hasAttribute('href'), 'enlace sin href');
    if (el.tagName === 'BUTTON') assert.ok(el.getAttribute('type'), `boton sin type: ${nombreAccesible(el)}`);
  }
  assert.equal(t.doc.querySelectorAll('[tabindex]:not([tabindex="0"]):not([tabindex="-1"])').length, 0);
  for (const campo of t.doc.querySelectorAll('input, select, textarea')) {
    assert.ok((campo.id && t.doc.querySelector(`label[for="${campo.id}"]`)) || campo.closest('label'), `sin label: ${campo.id || campo.name}`);
  }
  for (const salida of t.doc.querySelectorAll('output[id]')) {
    assert.ok(t.doc.querySelector(`label[for="${salida.id}"]`), `salida sin label: ${salida.id}`);
  }
});

test('botones que abren exploradores: nombran que se elige (cliente del formulario, del filtro y articulo de los renglones 1 y 2)', async () => {
  const t = await paginaConExplorador();
  const nombre = (id) => nombreAccesible(t.doc.getElementById(id));
  assert.equal(nombre('elegir-cliente'), 'Elegir cliente');
  assert.equal(nombre('elegir-cliente-filtro'), 'Elegir cliente para filtrar');
  assert.equal(nombre('quitar-cliente-filtro'), 'Quitar cliente del filtro');
  const articulos = [...t.doc.querySelectorAll('#renglones button[data-elegir]')].map(nombreAccesible);
  assert.deepEqual(articulos, ['Elegir artículo (renglón 1)', 'Elegir artículo (renglón 2)']);
});

test('resultados del explorador de articulos: el nombre accesible lleva la etiqueta y el precio', async () => {
  const t = await paginaConExplorador();
  t.doc.querySelector('#renglones button[data-elegir]').click();
  await esperarTareas();
  const nombres = [...t.doc.querySelectorAll('#explorador-resultados button')].map((b) => b.textContent);
  assert.equal(nombres.length, 2);
  assert.match(nombres[0], /^Martillo — .*3\.000,00$/);
});

test('paginacion: nav con nombre, botones con type, selector con label, indicador con aria-current y anuncio en la unica region de estado', async () => {
  const t = await paginaConExplorador();
  const nav = t.doc.getElementById('paginacion');
  assert.match(nav.getAttribute('aria-label'), /Paginación de facturas/);
  assert.ok([...nav.querySelectorAll('button')].every((b) => b.getAttribute('type') === 'button'));
  assert.ok(t.doc.querySelector('label[for="tamanio-pagina"]'));
  assert.equal(t.doc.getElementById('pagina-indicador').getAttribute('aria-current'), 'page');
  assert.equal(t.doc.querySelectorAll('[role="status"]').length, 1);
  assert.equal(nav.closest('[role="status"], [aria-live]'), null);
});

test('orden de tabulacion: filtros (numero, cliente, informada) antes de descargas, tabla, paginacion y formulario', async () => {
  const t = await paginaConExplorador();
  const orden = [...t.doc.querySelectorAll('a[href], button, input, select, textarea, [tabindex="0"]')].map((e) => e.id || e.getAttribute('name') || e.tagName);
  const pos = (id) => orden.indexOf(id);
  assert.ok(pos('filtro-numero') < pos('elegir-cliente-filtro'));
  assert.ok(pos('elegir-cliente-filtro') < pos('quitar-cliente-filtro'));
  assert.ok(pos('quitar-cliente-filtro') < pos('filtro-informada'));
  assert.ok(pos('buscar') < pos('descarga-pdf'));
  assert.ok(pos('descarga-xlsx') < pos('pagina-anterior'));
  assert.ok(pos('pagina-anterior') < pos('pagina-siguiente'));
  assert.ok(pos('pagina-siguiente') < pos('tamanio-pagina'));
  assert.ok(pos('tamanio-pagina') < pos('elegir-cliente'));
});

test('estilos del dialogo, la paginacion y el enlace de PDF: solo colores de la paleta con contraste calculado y objetivos de al menos 44 px', () => {
  const bloque = (selector) => {
    const inicio = css.indexOf(`${selector} {`);
    return inicio < 0 ? '' : css.slice(css.indexOf('{', inicio) + 1, css.indexOf('}', inicio));
  };
  const dialogo = bloque('dialog#explorador');
  const pdf = bloque('td .botones a.enlace-pdf');
  for (const regla of [dialogo, pdf]) {
    assert.ok(regla.length > 0, 'regla no encontrada');
    assert.equal(/#[0-9a-fA-F]{3,6}\b|rgb/.test(regla), false, 'color fijo fuera de la paleta');
  }
  assert.match(pdf, /min-height:\s*2\.75rem/);
  assert.match(pdf, /min-width:\s*2\.75rem/);
  // los botones de la paginacion y del explorador usan la regla general de button (2,75rem)
  assert.match(css, /button[^{]*\{[^}]*min-height:\s*2\.75rem/);
  assert.match(css, /input, select, textarea \{[^}]*min-height:\s*2\.75rem/);
});
