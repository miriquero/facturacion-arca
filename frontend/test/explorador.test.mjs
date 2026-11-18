import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../dist/services/ApiError.js';
import { Explorador } from '../dist/ui/explorador.js';
import { diferido, esperarTareas, montarPagina, ventanaModalFalsa } from '../soporte-pruebas/soporte.mjs';

const CLIENTES = [
  { id: 1, etiqueta: 'Ana SA (30-11111111-1)', valor: null, codigoMoneda: null },
  { id: 2, etiqueta: 'Juan Servicios (20-12345678-9)', valor: null, codigoMoneda: null },
];

/** Pagina real + doble de VentanaModal + un boton disparador; `buscar` devuelve diferidos que la prueba resuelve. */
function montar() {
  const pagina = montarPagina();
  const doc = pagina.documento;
  const modal = ventanaModalFalsa();
  const explorador = new Explorador(doc, modal);
  const disparador = doc.createElement('button');
  disparador.type = 'button';
  disparador.textContent = 'Elegir cliente';
  doc.body.appendChild(disparador);
  const busquedas = [];
  const buscar = (q) => {
    const d = diferido();
    busquedas.push({ q, resolver: d.resolver, rechazar: d.rechazar });
    return d.promesa;
  };
  const $ = (id) => doc.getElementById(id);
  const abrir = (extra = {}) => {
    const promesa = explorador.elegir({ titulo: 'Buscar cliente', buscar, ...extra }, disparador);
    return promesa;
  };
  const nombres = () => [...$('explorador-resultados').querySelectorAll('button')].map((b) => b.textContent);
  const buscarTexto = async (texto) => {
    $('explorador-q').value = texto;
    $('explorador-form').requestSubmit();
    await esperarTareas();
  };
  return { ...pagina, doc, modal, explorador, disparador, busquedas, abrir, $, nombres, buscarTexto };
}

test('Apertura: dialogo abierto, titulo Buscar cliente por aria-labelledby, campo etiquetado, Cancelar y foco en el campo', async () => {
  const t = montar();
  t.abrir();
  const dialogo = t.$('explorador');
  assert.equal(dialogo.open, true);
  assert.deepEqual(t.modal.aperturas, ['explorador']);
  assert.equal(t.$(dialogo.getAttribute('aria-labelledby')).textContent, 'Buscar cliente');
  assert.equal(t.doc.querySelector('label[for="explorador-q"]').textContent, 'Texto a buscar');
  assert.equal(t.$('explorador-cancelar').textContent, 'Cancelar');
  assert.equal(t.doc.activeElement, t.$('explorador-q'));
});

test('Apertura: busca sin texto y lista las opciones como botones con su etiqueta', async () => {
  const t = montar();
  t.abrir();
  assert.deepEqual(t.busquedas.map((b) => b.q), ['']);
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  assert.deepEqual(t.nombres(), ['Ana SA (30-11111111-1)', 'Juan Servicios (20-12345678-9)']);
  for (const b of t.$('explorador-resultados').querySelectorAll('button')) assert.equal(b.getAttribute('type'), 'button');
  assert.equal(t.$('explorador-cantidad').textContent, '2 resultados');
  assert.equal(t.$('explorador-vacio').hidden, true);
});

test('Buscar juan: se pide buscar("juan") y la lista muestra solo lo recibido', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  await t.buscarTexto('juan');
  assert.equal(t.busquedas[1].q, 'juan');
  t.busquedas[1].resolver([CLIENTES[1]]);
  await esperarTareas();
  assert.deepEqual(t.nombres(), ['Juan Servicios (20-12345678-9)']);
  assert.equal(t.$('explorador-cantidad').textContent, '1 resultado');
});

test('Sin coincidencias: mensaje de que no hay resultados, cantidad 0 y ninguna lista', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  await t.buscarTexto('zzz');
  t.busquedas[1].resolver([]);
  await esperarTareas();
  assert.equal(t.$('explorador-vacio').hidden, false);
  assert.equal(t.$('explorador-cantidad').textContent, '0 resultados');
  assert.deepEqual(t.nombres(), []);
});

test('Error de busqueda: el mensaje sale dentro del dialogo con role=alert y el dialogo sigue abierto', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].rechazar(new ApiError(500, 'Falló la búsqueda'));
  await esperarTareas();
  const error = t.$('explorador-error');
  assert.equal(error.getAttribute('role'), 'alert');
  assert.equal(error.hidden, false);
  assert.equal(error.textContent, 'Falló la búsqueda');
  assert.equal(t.$('explorador').open, true);
  assert.ok(t.$('explorador').contains(error));
});

test('Un error se retira al buscar de nuevo con exito', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].rechazar(new ApiError(500, 'Falló'));
  await esperarTareas();
  await t.buscarTexto('ana');
  t.busquedas[1].resolver([CLIENTES[0]]);
  await esperarTareas();
  assert.equal(t.$('explorador-error').hidden, true);
  assert.equal(t.$('explorador-error').textContent, '');
  assert.deepEqual(t.nombres(), ['Ana SA (30-11111111-1)']);
});

test('Respuestas en orden inverso: ju (1) y juan (2); llega antes la 2 y queda juan', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].resolver([]);
  await esperarTareas();
  await t.buscarTexto('ju');
  await t.buscarTexto('juan');
  t.busquedas[2].resolver([CLIENTES[1]]);
  await esperarTareas();
  t.busquedas[1].resolver(CLIENTES);
  await esperarTareas();
  assert.deepEqual(t.nombres(), ['Juan Servicios (20-12345678-9)']);
  assert.equal(t.$('explorador-cantidad').textContent, '1 resultado');
});

test('Un error tardio de una busqueda vieja no se muestra', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].resolver([]);
  await esperarTareas();
  await t.buscarTexto('ju');
  await t.buscarTexto('juan');
  t.busquedas[2].resolver([CLIENTES[1]]);
  await esperarTareas();
  t.busquedas[1].rechazar(new ApiError(500, 'Vieja'));
  await esperarTareas();
  assert.equal(t.$('explorador-error').hidden, true);
});

test('Elegir un resultado: resuelve la opcion, cierra el dialogo y devuelve el foco al disparador', async () => {
  const t = montar();
  const promesa = t.abrir();
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  t.$('explorador-resultados').querySelectorAll('button')[1].click();
  assert.deepEqual(await promesa, CLIENTES[1]);
  assert.equal(t.$('explorador').open, false);
  assert.deepEqual(t.modal.cierres, ['explorador']);
  assert.equal(t.doc.activeElement, t.disparador);
});

test('Cancelar: resuelve null, cierra y devuelve el foco al disparador', async () => {
  const t = montar();
  const promesa = t.abrir();
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  t.$('explorador-cancelar').click();
  assert.equal(await promesa, null);
  assert.equal(t.$('explorador').open, false);
  assert.equal(t.doc.activeElement, t.disparador);
});

test('Escape (evento cancel): resuelve null, cierra por codigo (cancelando el cierre nativo) y devuelve el foco', async () => {
  const t = montar();
  const promesa = t.abrir();
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  const evento = new t.ventana.Event('cancel', { cancelable: true });
  t.$('explorador').dispatchEvent(evento);
  assert.equal(await promesa, null);
  assert.equal(evento.defaultPrevented, true);
  assert.equal(t.$('explorador').open, false);
  assert.equal(t.doc.activeElement, t.disparador);
});

test('Etiqueta que parece HTML: se muestra literal y el dialogo no contiene ningun img', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].resolver([{ id: 9, etiqueta: '<img src=x onerror=alert(1)>', valor: null, codigoMoneda: null }]);
  await esperarTareas();
  assert.deepEqual(t.nombres(), ['<img src=x onerror=alert(1)>']);
  assert.equal(t.$('explorador').querySelector('img'), null);
});

test('Titulo con texto del servidor o del renglon: textContent, sin interpretar HTML', async () => {
  const t = montar();
  t.abrir({ titulo: 'Buscar artículo de lista de precios (renglón 2) <b>' });
  assert.equal(t.$('explorador-titulo').textContent, 'Buscar artículo de lista de precios (renglón 2) <b>');
  assert.equal(t.$('explorador').querySelector('b'), null);
});

test('Articulos con precio: el nombre accesible del boton incluye la etiqueta y el precio formateado por moneda', async () => {
  const t = montar();
  t.abrir({ conPrecio: true });
  t.busquedas[0].resolver([
    { id: 7, etiqueta: 'ART-001 - Martillo (GENERAL)', valor: 3000, codigoMoneda: 'PES' },
    { id: 8, etiqueta: 'ART-002 - Cable', valor: 12.5, codigoMoneda: 'DOL' },
  ]);
  await esperarTareas();
  const [a, b] = t.nombres();
  assert.match(a, /^ART-001 - Martillo \(GENERAL\) — \$\s3\.000,00$/);
  assert.match(b, /^ART-002 - Cable — US\$\s12,50$/);
});

test('Sin conPrecio el boton muestra solo la etiqueta aunque la opcion traiga valor', async () => {
  const t = montar();
  t.abrir();
  t.busquedas[0].resolver([{ id: 7, etiqueta: 'Martillo', valor: 3000, codigoMoneda: 'PES' }]);
  await esperarTareas();
  assert.deepEqual(t.nombres(), ['Martillo']);
});

test('Reabrir: campo y resultados anteriores se limpian y se vuelve a buscar sin texto', async () => {
  const t = montar();
  const primera = t.abrir();
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  await t.buscarTexto('juan');
  t.$('explorador-cancelar').click();
  await primera;
  t.abrir();
  assert.equal(t.$('explorador-q').value, '');
  assert.deepEqual(t.nombres(), []);
  assert.equal(t.busquedas.at(-1).q, '');
});

test('Un solo explorador a la vez: abrir otro mientras hay uno abierto resuelve null sin buscar', async () => {
  const t = montar();
  t.abrir();
  const cantidad = t.busquedas.length;
  assert.equal(await t.abrir({ titulo: 'Otro' }), null);
  assert.equal(t.busquedas.length, cantidad);
  assert.equal(t.$('explorador-titulo').textContent, 'Buscar cliente');
  assert.equal(t.modal.aperturas.length, 1);
});

test('Estilos del dialogo: usa las variables de la paleta con contraste calculado y define el ::backdrop', async () => {
  const { readFileSync } = await import('node:fs');
  const css = readFileSync(new URL('../styles.css', import.meta.url), 'utf8');
  const bloque = /dialog#explorador\s*\{([^}]*)\}/.exec(css)?.[1] ?? '';
  assert.match(bloque, /color:\s*var\(--texto\)/);
  assert.match(bloque, /background:\s*var\(--fondo\)/);
  assert.match(bloque, /border:\s*2px solid var\(--borde\)/);
  assert.match(css, /dialog#explorador::backdrop\s*\{[^}]*background:/);
});

test('index.html: un unico dialogo #explorador con aria-labelledby y sin method=dialog', () => {
  const { dom } = montarPagina();
  const dialogos = dom.window.document.querySelectorAll('dialog');
  assert.equal(dialogos.length, 1);
  assert.equal(dialogos[0].id, 'explorador');
  assert.equal(dialogos[0].getAttribute('aria-labelledby'), 'explorador-titulo');
  assert.equal(dom.window.document.querySelector('#explorador form').hasAttribute('method'), false);
});

test('Hallazgo del recorrido real: el campo de busqueda no es type=search (Chrome lo vacia con la primera Escape y el dialogo tarda dos Escape en cerrar)', () => {
  const { documento } = montarPagina();
  const campo = documento.getElementById('explorador-q');
  assert.equal(campo.getAttribute('type'), 'text');
  assert.equal(campo.getAttribute('enterkeyhint'), 'search');
});

test('Hallazgo del recorrido real: el catalogo de articulos viene con codigoMoneda nulo y el boton no debe decir "Sin moneda"', async () => {
  const t = montar();
  t.abrir({ conPrecio: true });
  t.busquedas[0].resolver([{ id: 3, etiqueta: 'ART-003 - Cable HDMI (GENERAL)', valor: 8000, codigoMoneda: null }]);
  await esperarTareas();
  assert.deepEqual(t.nombres(), ['ART-003 - Cable HDMI (GENERAL) — 8.000,00']);
});

// ---- Etapa 7: cerrar desde afuera (al salir de la pantalla) ----

test('cerrar() con el explorador abierto: lo cierra, resuelve null y devuelve el foco al disparador', async () => {
  const t = montar();
  const promesa = t.abrir();
  assert.equal(t.$('explorador').open, true);
  t.explorador.cerrar();
  assert.equal(await promesa, null);
  assert.equal(t.$('explorador').open, false);
  assert.deepEqual(t.modal.cierres, ['explorador']);
  assert.equal(t.doc.activeElement, t.disparador);
});

test('cerrar() sin explorador abierto no hace nada y el siguiente elegir() funciona', async () => {
  const t = montar();
  t.explorador.cerrar();
  assert.deepEqual(t.modal.cierres, []);
  const promesa = t.abrir();
  t.busquedas[0].resolver(CLIENTES);
  await esperarTareas();
  [...t.$('explorador-resultados').querySelectorAll('button')][0].click();
  assert.deepEqual(await promesa, CLIENTES[0]);
});
