import assert from 'node:assert/strict';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { ApiError } from '../dist/services/ApiError.js';
import { textoPagina } from '../dist/ui/paginacion.js';
import { catalogosEjemplo, dialogosFalsos, diferido, esperarTareas, fila, montarPagina, pagina, servicioFalso, temporizadorManual } from '../soporte-pruebas/soporte.mjs';

const TOTAL = 23;

/** Servidor falso de 23 facturas: responde la pagina pedida con filas que identifican la pagina (id = pagina * 100 + n). */
const paginaDe = (_filtros, solicitud) => {
  const desde = solicitud.pagina * solicitud.tamanio;
  const cantidad = Math.max(0, Math.min(solicitud.tamanio, TOTAL - desde));
  const items = Array.from({ length: cantidad }, (_, i) => fila({ id: solicitud.pagina * 100 + i + 1 }));
  return pagina(items, { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: TOTAL, totalPaginas: Math.ceil(TOTAL / solicitud.tamanio) });
};

async function arrancar(auto = {}) {
  const pag = montarPagina();
  const servicio = servicioFalso({ obtenerCatalogos: catalogosEjemplo(), obtenerFacturas: paginaDe, ...auto });
  const temporizador = temporizadorManual();
  const app = iniciarAplicacion({ documento: pag.documento, ventana: pag.ventana, servicio, dialogos: dialogosFalsos(), temporizador });
  await app.listo;
  const $ = (id) => pag.documento.getElementById(id);
  const siguiente = () => $('pagina-siguiente');
  const anterior = () => $('pagina-anterior');
  const ir = async (boton) => {
    boton.click();
    await esperarTareas();
  };
  const cambiarTamanio = async (valor) => {
    $('tamanio-pagina').value = valor;
    $('tamanio-pagina').dispatchEvent(new pag.ventana.Event('change', { bubbles: true }));
    await esperarTareas();
  };
  const buscar = async () => {
    $('form-filtros').requestSubmit();
    await esperarTareas();
  };
  const ultima = () => servicio.de('obtenerFacturas').at(-1).args;
  // Etapa 7: las filas ya no tienen botones; el id sale del enlace Ver (#/factura/{id}).
  const primerId = () => $('filas').rows[0].querySelector('a[href^="#/factura/"]')?.getAttribute('href').split('/').pop() ?? $('filas').rows[0].cells[0].textContent;
  return { ...pag, servicio, temporizador, app, $, siguiente, anterior, ir, cambiarTamanio, buscar, ultima, primerId };
}

/** Desde este momento el metodo devuelve diferidos que la prueba resuelve a mano. */
function manual(servicio, metodo) {
  servicio[metodo] = (...args) => {
    const d = diferido();
    servicio.llamadas.push({ metodo, args, resolver: d.resolver, rechazar: d.rechazar });
    return d.promesa;
  };
}

test('textoPagina: primera de 3, ultima de 3 y sin resultados (Pagina 1 de 1)', () => {
  assert.equal(textoPagina(0, 3), 'Página 1 de 3');
  assert.equal(textoPagina(2, 3), 'Página 3 de 3');
  assert.equal(textoPagina(0, 0), 'Página 1 de 1');
});

test('Primera de tres paginas: Pagina 1 de 3, Anterior deshabilitado y Siguiente habilitado', async () => {
  const t = await arrancar();
  assert.equal(t.$('pagina-indicador').textContent, 'Página 1 de 3');
  assert.equal(t.anterior().disabled, true);
  assert.equal(t.siguiente().disabled, false);
});

test('Ultima pagina: Pagina 3 de 3, Siguiente deshabilitado y Anterior habilitado', async () => {
  const t = await arrancar();
  await t.ir(t.siguiente());
  await t.ir(t.siguiente());
  assert.equal(t.$('pagina-indicador').textContent, 'Página 3 de 3');
  assert.equal(t.siguiente().disabled, true);
  assert.equal(t.anterior().disabled, false);
  assert.equal(t.$('filas').rows.length, 3);
});

test('Sin resultados: Pagina 1 de 1 y ambos botones deshabilitados', async () => {
  const t = await arrancar({ obtenerFacturas: pagina([], { totalElementos: 0, totalPaginas: 0 }) });
  assert.equal(t.$('pagina-indicador').textContent, 'Página 1 de 1');
  assert.equal(t.anterior().disabled, true);
  assert.equal(t.siguiente().disabled, true);
});

test('Region nombrada: nav con nombre accesible de paginacion de facturas, botones type=button, selector 10/20/50 con etiqueta y aria-current', async () => {
  const t = await arrancar();
  const nav = t.$('paginacion');
  assert.equal(nav.tagName, 'NAV');
  assert.match(nav.getAttribute('aria-label'), /Paginación de facturas/);
  for (const b of nav.querySelectorAll('button')) assert.equal(b.getAttribute('type'), 'button');
  assert.equal(t.$('pagina-indicador').getAttribute('aria-current'), 'page');
  assert.deepEqual([...t.$('tamanio-pagina').options].map((o) => o.value), ['10', '20', '50']);
  assert.equal(t.$('tamanio-pagina').value, '10');
  assert.equal(t.documento.querySelector('label[for="tamanio-pagina"]').textContent, 'Facturas por página');
});

test('Siguiente con los filtros del resultado mostrado, no con los editados sin buscar; anuncia pagina y cantidad', async () => {
  const t = await arrancar();
  t.$('filtro-estado').value = 'EMITIDA';
  await t.buscar();
  t.$('filtro-estado').value = 'BORRADOR';
  await t.ir(t.siguiente());
  assert.deepEqual(t.ultima(), [{ estado: 'EMITIDA' }, { pagina: 1, tamanio: 10 }]);
  assert.equal(t.$('pagina-indicador').textContent, 'Página 2 de 3');
  assert.equal(t.$('estado').textContent, 'Página 2 de 3 (23 facturas)');
  assert.equal(t.$('pagina-indicador').getAttribute('aria-current'), 'page');
  assert.equal(t.primerId(), '101');
});

test('Anterior pide la pagina previa con los mismos filtros', async () => {
  const t = await arrancar();
  await t.ir(t.siguiente());
  await t.ir(t.anterior());
  assert.deepEqual(t.ultima(), [{}, { pagina: 0, tamanio: 10 }]);
  assert.equal(t.$('pagina-indicador').textContent, 'Página 1 de 3');
});

test('Tamano 20 desde la pagina 2 (indice 1): se pide pagina 0 con tamanio 20 y los filtros vigentes', async () => {
  const t = await arrancar();
  t.$('filtro-estado').value = 'EMITIDA';
  await t.buscar();
  await t.ir(t.siguiente());
  await t.cambiarTamanio('20');
  assert.deepEqual(t.ultima(), [{ estado: 'EMITIDA' }, { pagina: 0, tamanio: 20 }]);
  assert.equal(t.$('pagina-indicador').textContent, 'Página 1 de 2');
});

test('Nueva busqueda desde la pagina 3: se pide la pagina 0, conservando el tamanio elegido', async () => {
  const t = await arrancar();
  await t.cambiarTamanio('20');
  await t.ir(t.siguiente());
  t.$('filtro-estado').value = 'ANULADA';
  await t.buscar();
  assert.deepEqual(t.ultima(), [{ estado: 'ANULADA' }, { pagina: 0, tamanio: 20 }]);
});

test('Limpiar filtros desde la pagina 2 pide la pagina 0', async () => {
  const t = await arrancar();
  await t.ir(t.siguiente());
  t.$('limpiar-filtros').click();
  await esperarTareas();
  assert.deepEqual(t.ultima(), [{}, { pagina: 0, tamanio: 10 }]);
});

test('Doble clic en Siguiente: un solo pedido de la pagina 1 y botones bloqueados mientras tanto', async () => {
  const t = await arrancar();
  manual(t.servicio, 'obtenerFacturas');
  t.siguiente().click();
  t.siguiente().click();
  assert.equal(t.servicio.de('obtenerFacturas').length, 2);
  assert.deepEqual(t.ultima(), [{}, { pagina: 1, tamanio: 10 }]);
  assert.equal(t.siguiente().disabled, true);
  assert.equal(t.anterior().disabled, true);
  assert.equal(t.$('tamanio-pagina').disabled, true);
  t.servicio.de('obtenerFacturas')[1].resolver(paginaDe({}, { pagina: 1, tamanio: 10 }));
  await esperarTareas();
  assert.equal(t.siguiente().disabled, false);
  assert.equal(t.$('tamanio-pagina').disabled, false);
});

test('Respuesta vieja de Siguiente tras una busqueda: quedan tabla, cantidad, controles y enlaces del pedido 2', async () => {
  const t = await arrancar();
  manual(t.servicio, 'obtenerFacturas');
  t.siguiente().click();
  t.$('filtro-estado').value = 'ANULADA';
  t.$('form-filtros').requestSubmit();
  const [, p1, p2] = t.servicio.de('obtenerFacturas');
  p2.resolver(pagina([fila({ id: 7 })], { totalElementos: 1, totalPaginas: 1 }));
  await esperarTareas();
  p1.resolver(paginaDe({}, { pagina: 1, tamanio: 10 }));
  await esperarTareas();
  assert.equal(t.$('filas').rows.length, 1);
  assert.equal(t.$('contador').textContent, '1 factura');
  assert.equal(t.$('pagina-indicador').textContent, 'Página 1 de 1');
  assert.ok(t.$('descarga-pdf').getAttribute('href').endsWith('estado=ANULADA'));
  assert.equal(t.siguiente().disabled, true);
});

test('Error al paginar: muestra el error, conserva la pagina 0 y libera los botones segun esa pagina', async () => {
  const t = await arrancar();
  const antes = t.$('filas').rows.length;
  manual(t.servicio, 'obtenerFacturas');
  t.siguiente().click();
  t.servicio.de('obtenerFacturas')[1].rechazar(new ApiError(500, 'Falló la página'));
  await esperarTareas();
  assert.match(t.$('estado').textContent, /Falló la página/);
  assert.equal(t.$('pagina-indicador').textContent, 'Página 1 de 3');
  assert.equal(t.$('filas').rows.length, antes);
  assert.equal(t.primerId(), '1');
  assert.equal(t.anterior().disabled, true);
  assert.equal(t.siguiente().disabled, false);
});

test('Error al cambiar el tamano: el selector vuelve al tamano del resultado mostrado', async () => {
  const t = await arrancar();
  manual(t.servicio, 'obtenerFacturas');
  t.$('tamanio-pagina').value = '50';
  t.$('tamanio-pagina').dispatchEvent(new t.ventana.Event('change', { bubbles: true }));
  t.servicio.de('obtenerFacturas')[1].rechazar(new ApiError(500, 'Falló el tamaño'));
  await esperarTareas();
  assert.equal(t.$('tamanio-pagina').value, '10');
});

test('Los enlaces de descarga no cambian al paginar ni llevan pagina o tamanio', async () => {
  const t = await arrancar();
  t.$('filtro-estado').value = 'EMITIDA';
  await t.buscar();
  const antes = t.$('descarga-pdf').getAttribute('href');
  await t.ir(t.siguiente());
  assert.equal(t.$('descarga-pdf').getAttribute('href'), antes);
  assert.ok(!/pagina|tamanio/.test(antes));
});
