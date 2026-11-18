import assert from 'node:assert/strict';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { ApiError } from '../dist/services/ApiError.js';
import { catalogosEjemplo, dialogosFalsos, diferido, esperarTareas, fila, montarPagina, servicioFalso, temporizadorManual } from '../soporte-pruebas/soporte.mjs';

function arrancar(auto = {}, dialogos = dialogosFalsos()) {
  const pagina = montarPagina();
  const servicio = servicioFalso({ obtenerCatalogos: catalogosEjemplo(), obtenerFacturas: [], ...auto });
  const temporizador = temporizadorManual();
  const app = iniciarAplicacion({ documento: pagina.documento, servicio, dialogos, temporizador });
  const $ = (id) => pagina.documento.getElementById(id);
  return { ...pagina, servicio, temporizador, app, $, dialogos };
}

/** Desde este momento el metodo devuelve diferidos que la prueba resuelve a mano. */
function manual(servicio, metodo) {
  servicio[metodo] = (...args) => {
    const d = diferido();
    servicio.llamadas.push({ metodo, args, resolver: d.resolver, rechazar: d.rechazar });
    return d.promesa;
  };
}

const opciones = (select) => [...select.options].map((o) => ({ texto: o.text, valor: o.value }));
const buscar = (t) => t.$('form-filtros').requestSubmit();

test('Selectores completos: etiquetas del servidor con el id del catalogo como valor', async () => {
  const t = arrancar();
  await t.app.listo;
  assert.ok(opciones(t.$('punto-venta')).some((o) => o.texto === '0001 - Casa Central' && o.valor === '1'));
  assert.ok(opciones(t.$('tipo-moneda')).some((o) => o.texto === 'Pesos' && o.valor === '1'));
});

test('Cliente opcional: primera opcion Consumidor Final con valor vacio', async () => {
  const t = arrancar();
  await t.app.listo;
  assert.deepEqual(opciones(t.$('cliente'))[0], { texto: 'Consumidor Final', valor: '' });
  assert.equal(opciones(t.$('cliente')).length, 3);
});

test('Carga inicial: catalogos una vez y listado sin filtros', async () => {
  const t = arrancar();
  await t.app.listo;
  assert.deepEqual(t.servicio.de('obtenerFacturas')[0].args, [{}]);
  assert.equal(t.servicio.de('obtenerCatalogos').length, 1);
});

test('Catalogo que falla: error en la region y la tabla igual se muestra', async () => {
  const t = arrancar({ obtenerCatalogos: new ApiError(500, 'Falló el catálogo'), obtenerFacturas: [fila()] });
  await t.app.listo;
  assert.equal(t.$('estado').textContent, 'Error: Falló el catálogo');
  assert.equal(t.$('filas').rows.length, 1);
});

test('Tabla de resultados: dos filas en orden, 3 y guion, importes y fechas formateados', async () => {
  const t = arrancar({
    obtenerFacturas: [
      fila({ id: 1, numeroFactura: 3 }),
      fila({ id: 6, numeroFactura: null, estado: 'BORRADOR', importeTotal: 9680, codigoMoneda: 'DOL', fechaEmision: '2026-12-31' }),
    ],
  });
  await t.app.listo;
  const filas = [...t.$('filas').rows].map((r) => [...r.cells].map((c) => c.textContent));
  assert.equal(filas.length, 2);
  assert.equal(filas[0][0], '3');
  assert.equal(filas[1][0], '—');
  assert.equal(filas[0][1], '01/11/2026');
  assert.equal(filas[0][2], 'Juan Servicios');
  assert.equal(filas[0][6], '$ 7.260,00');
  assert.equal(filas[1][6], 'US$ 9.680,00');
  assert.equal(filas[1][1], '31/12/2026');
});

test('Texto que parece HTML en la tabla y en las opciones queda literal', async () => {
  const cat = catalogosEjemplo();
  cat.puntosVenta[0].etiqueta = '<b>Casa</b>';
  const t = arrancar({ obtenerCatalogos: cat, obtenerFacturas: [fila({ clienteDenominacion: '<img src=x onerror=alert(1)>' })] });
  await t.app.listo;
  assert.equal(t.documento.querySelector('img'), null);
  assert.equal(t.documento.querySelector('b'), null);
  assert.equal(t.$('filas').rows[0].cells[2].textContent, '<img src=x onerror=alert(1)>');
  assert.equal(t.$('punto-venta').options[1].text, '<b>Casa</b>');
});

test('Tres coincidencias: 3 facturas', async () => {
  const t = arrancar({ obtenerFacturas: [fila({ id: 1 }), fila({ id: 2 }), fila({ id: 3 })] });
  await t.app.listo;
  assert.equal(t.$('contador').textContent, '3 facturas');
  assert.equal(t.$('sin-resultados').hidden, true);
});

test('Sin coincidencias: 0 facturas y mensaje en lugar de filas', async () => {
  const t = arrancar({ obtenerFacturas: [] });
  await t.app.listo;
  assert.equal(t.$('contador').textContent, '0 facturas');
  assert.equal(t.$('sin-resultados').hidden, false);
  assert.equal(t.$('filas').rows.length, 0);
});

test('Una factura: 1 factura en singular', async () => {
  const t = arrancar({ obtenerFacturas: [fila()] });
  await t.app.listo;
  assert.equal(t.$('contador').textContent, '1 factura');
});

test('Busqueda por estado y fecha: el servicio recibe exactamente esos filtros', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-estado').value = 'EMITIDA';
  t.$('filtro-desde').value = '2026-11-01';
  buscar(t);
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ fechaDesde: '2026-11-01', estado: 'EMITIDA' }]);
});

test('Limpiar filtros: campos vacios y se pide el listado sin filtros', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-estado').value = 'EMITIDA';
  t.$('filtro-monto').value = '5';
  t.$('limpiar-filtros').click();
  await esperarTareas();
  assert.equal(t.$('filtro-estado').value, '');
  assert.equal(t.$('filtro-monto').value, '');
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{}]);
});

test('Monto negativo: campo invalido con mensaje asociado y ninguna llamada', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-monto').value = '-1';
  buscar(t);
  await esperarTareas();
  const campo = t.$('filtro-monto');
  assert.equal(campo.getAttribute('aria-invalid'), 'true');
  assert.match(t.$(campo.getAttribute('aria-describedby')).textContent, /mayor o igual a 0/);
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
});

test('Monto cero: se pide con montoMinimo 0 y se quitan las marcas de error', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-monto').value = '-1';
  buscar(t);
  t.$('filtro-monto').value = '0';
  buscar(t);
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ montoMinimo: 0 }]);
  assert.equal(t.$('filtro-monto').hasAttribute('aria-invalid'), false);
  assert.equal(t.$('filtro-monto').hasAttribute('aria-describedby'), false);
});

test('Enlaces tras una busqueda: PDF y Excel con los filtros del resultado', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-estado').value = 'ANULADA';
  buscar(t);
  await esperarTareas();
  assert.equal(t.$('descarga-pdf').getAttribute('href'), 'http://localhost:8080/api/facturas/reporte?formato=pdf&estado=ANULADA');
  assert.equal(t.$('descarga-xlsx').getAttribute('href'), 'http://localhost:8080/api/facturas/reporte?formato=xlsx&estado=ANULADA');
});

test('Respuestas en orden inverso: quedan tabla, contador y enlaces de EMITIDA', async () => {
  const t = arrancar();
  await t.app.listo;
  manual(t.servicio, 'obtenerFacturas');
  t.$('filtro-estado').value = 'BORRADOR';
  buscar(t);
  t.$('filtro-estado').value = 'EMITIDA';
  buscar(t);
  const [, b1, b2] = t.servicio.de('obtenerFacturas');
  b2.resolver([fila({ id: 1, estado: 'EMITIDA' })]);
  await esperarTareas();
  b1.resolver([fila({ id: 6, estado: 'BORRADOR' }), fila({ id: 7, estado: 'BORRADOR' })]);
  await esperarTareas();
  assert.equal(t.$('filas').rows.length, 1);
  assert.equal(t.$('filas').rows[0].cells[7].textContent.includes('EMITIDA'), true);
  assert.equal(t.$('contador').textContent, '1 factura');
  assert.ok(t.$('descarga-pdf').getAttribute('href').endsWith('estado=EMITIDA'));
  assert.ok(t.$('descarga-xlsx').getAttribute('href').endsWith('estado=EMITIDA'));
});

test('Error tardio de una busqueda vieja: sin error y tabla de la busqueda 2', async () => {
  const t = arrancar();
  await t.app.listo;
  manual(t.servicio, 'obtenerFacturas');
  buscar(t);
  buscar(t);
  const [, b1, b2] = t.servicio.de('obtenerFacturas');
  b2.resolver([fila({ id: 9 })]);
  await esperarTareas();
  b1.rechazar(new ApiError(500, 'Falló la búsqueda vieja'));
  await esperarTareas();
  assert.equal(t.$('estado').textContent, '');
  assert.equal(t.$('filas').rows.length, 1);
});

test('Busqueda rechazada por la API: 400 visible y tabla, contador y enlaces conservados', async () => {
  const t = arrancar({ obtenerFacturas: [fila({ id: 1 })] });
  await t.app.listo;
  const hrefAntes = t.$('descarga-pdf').getAttribute('href');
  manual(t.servicio, 'obtenerFacturas');
  t.$('filtro-desde').value = '2026-11-10';
  t.$('filtro-hasta').value = '2026-11-01';
  buscar(t);
  t.servicio.de('obtenerFacturas')[1].rechazar(new ApiError(400, 'fechaDesde no puede ser posterior a fechaHasta'));
  await esperarTareas();
  assert.match(t.$('estado').textContent, /fechaDesde no puede ser posterior a fechaHasta/);
  assert.equal(t.$('filas').rows.length, 1);
  assert.equal(t.$('contador').textContent, '1 factura');
  assert.equal(t.$('descarga-pdf').getAttribute('href'), hrefAntes);
});

test('Un error visible se retira al iniciar una nueva busqueda', async () => {
  const t = arrancar({ obtenerFacturas: new ApiError(500, 'Caído') });
  await t.app.listo;
  assert.match(t.$('estado').textContent, /Caído/);
  t.servicio.obtenerFacturas = async () => [];
  buscar(t);
  assert.equal(t.$('estado').textContent, '');
});
