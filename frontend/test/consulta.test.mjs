import assert from 'node:assert/strict';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { ApiError } from '../dist/services/ApiError.js';
import { arrancarApp, catalogosEjemplo, dialogosFalsos, diferido, esperarHashchange, esperarTareas, fila, montarPagina, pagina, servicioFalso, temporizadorManual, ventanaModalFalsa } from '../soporte-pruebas/soporte.mjs';

const P0 = { pagina: 0, tamanio: 10 };

function arrancar(auto = {}, dialogos = dialogosFalsos()) {
  const pagina = montarPagina();
  const servicio = servicioFalso({ obtenerCatalogos: catalogosEjemplo(), obtenerFacturas: [], buscarClientes: catalogosEjemplo().clientes, ...auto });
  const temporizador = temporizadorManual();
  const app = iniciarAplicacion({ documento: pagina.documento, ventana: pagina.ventana, servicio, dialogos, temporizador, ventanaModal: ventanaModalFalsa() });
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

test('Cliente opcional: el formulario muestra Consumidor Final, el filtro Todos y ninguno tiene id elegido', async () => {
  const t = arrancar();
  await t.app.listo;
  assert.equal(t.$('cliente').textContent, 'Consumidor Final');
  assert.equal(t.$('filtro-cliente').textContent, 'Todos');
  assert.equal(t.$('cliente').hasAttribute('data-id') || t.$('filtro-cliente').hasAttribute('data-id'), false);
});

test('Carga inicial: catalogos una vez y listado sin filtros', async () => {
  const t = arrancar();
  await t.app.listo;
  assert.deepEqual(t.servicio.de('obtenerFacturas')[0].args, [{}, P0]);
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
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ fechaDesde: '2026-11-01', estado: 'EMITIDA' }, P0]);
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
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{}, P0]);
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
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ montoMinimo: 0 }, P0]);
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
  b2.resolver(pagina([fila({ id: 1, estado: 'EMITIDA' })]));
  await esperarTareas();
  b1.resolver(pagina([fila({ id: 6, estado: 'BORRADOR' }), fila({ id: 7, estado: 'BORRADOR' })]));
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
  b2.resolver(pagina([fila({ id: 9 })]));
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
  t.servicio.obtenerFacturas = async () => pagina([]);
  buscar(t);
  assert.equal(t.$('estado').textContent, '');
});

// ---- Etapa 6: filtros de numero, informada y cliente; cantidad desde totalElementos ----

test('Filtros nuevos en el HTML: numero, informada y cliente con salida Todos y dos botones', async () => {
  const t = arrancar();
  await t.app.listo;
  const numero = t.$('filtro-numero');
  assert.equal(numero.getAttribute('type'), 'number');
  assert.equal(numero.getAttribute('min'), '1');
  assert.deepEqual(opciones(t.$('filtro-informada')), [
    { texto: 'Todas', valor: '' },
    { texto: 'Informadas', valor: 'true' },
    { texto: 'No informadas', valor: 'false' },
  ]);
  assert.equal(t.$('filtro-cliente').tagName, 'OUTPUT');
  assert.equal(t.$('filtro-cliente').textContent, 'Todos');
  assert.equal(t.$('filtro-cliente').hasAttribute('data-id'), false);
  assert.equal(t.$('elegir-cliente-filtro').textContent, 'Elegir cliente para filtrar');
  assert.equal(t.$('quitar-cliente-filtro').textContent, 'Quitar cliente del filtro');
  for (const id of ['filtro-numero', 'filtro-informada', 'filtro-cliente']) {
    assert.ok(t.documento.querySelector(`label[for="${id}"]`), `sin label: ${id}`);
  }
});

test('Busqueda por numero e informada: se pide numero 4, informada true y pagina 0', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-numero').value = '4';
  t.$('filtro-informada').value = 'true';
  buscar(t);
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ numero: 4, informada: 'true' }, P0]);
});

test('No informadas: se pide informada false', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-informada').value = 'false';
  buscar(t);
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ informada: 'false' }, P0]);
});

test('Numero 0 o 2.5: campo invalido con mensaje asociado y ninguna peticion; 12 valido limpia la marca', async () => {
  const t = arrancar();
  await t.app.listo;
  for (const invalido of ['0', '2.5']) {
    t.$('filtro-numero').value = invalido;
    buscar(t);
    const campo = t.$('filtro-numero');
    assert.equal(campo.getAttribute('aria-invalid'), 'true', invalido);
    assert.match(t.$(campo.getAttribute('aria-describedby')).textContent, /entero mayor o igual a 1/);
  }
  await esperarTareas();
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
  t.$('filtro-numero').value = '12';
  buscar(t);
  await esperarTareas();
  assert.equal(t.$('filtro-numero').hasAttribute('aria-invalid'), false);
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ numero: 12 }, P0]);
});

test('Cliente del filtro con id elegido: la busqueda lo envia como clienteId', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-cliente').textContent = 'Ana SA';
  t.$('filtro-cliente').setAttribute('data-id', '2');
  buscar(t);
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ clienteId: 2 }, P0]);
});

test('Cantidad desde totalElementos: 10 filas y 23 facturas', async () => {
  const items = Array.from({ length: 10 }, (_, i) => fila({ id: i + 1 }));
  const t = arrancar({ obtenerFacturas: pagina(items, { totalElementos: 23 }) });
  await t.app.listo;
  assert.equal(t.$('filas').rows.length, 10);
  assert.equal(t.$('contador').textContent, '23 facturas');
  assert.equal(t.$('sin-resultados').hidden, true);
});

test('Sin coincidencias con totalElementos 0: 0 facturas y mensaje', async () => {
  const t = arrancar({ obtenerFacturas: pagina([], { totalElementos: 0, totalPaginas: 0 }) });
  await t.app.listo;
  assert.equal(t.$('contador').textContent, '0 facturas');
  assert.equal(t.$('sin-resultados').hidden, false);
});

test('Un unico elemento en total: 1 factura en singular', async () => {
  const t = arrancar({ obtenerFacturas: pagina([fila()], { totalElementos: 1 }) });
  await t.app.listo;
  assert.equal(t.$('contador').textContent, '1 factura');
});

test('Limpiar filtros restablece numero, informada y cliente Todos y pide la pagina 0 sin filtros', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-numero').value = '4';
  t.$('filtro-informada').value = 'true';
  t.$('filtro-cliente').textContent = 'Ana SA';
  t.$('filtro-cliente').setAttribute('data-id', '2');
  t.$('limpiar-filtros').click();
  await esperarTareas();
  assert.equal(t.$('filtro-numero').value, '');
  assert.equal(t.$('filtro-informada').value, '');
  assert.equal(t.$('filtro-cliente').textContent, 'Todos');
  assert.equal(t.$('filtro-cliente').hasAttribute('data-id'), false);
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{}, P0]);
});

test('Enlaces de descarga con informada y sin paginacion', async () => {
  const t = arrancar();
  await t.app.listo;
  t.$('filtro-estado').value = 'ANULADA';
  t.$('filtro-informada').value = 'true';
  buscar(t);
  await esperarTareas();
  assert.equal(t.$('descarga-pdf').getAttribute('href'), 'http://localhost:8080/api/facturas/reporte?formato=pdf&estado=ANULADA&informada=true');
  assert.equal(t.$('descarga-xlsx').getAttribute('href'), 'http://localhost:8080/api/facturas/reporte?formato=xlsx&estado=ANULADA&informada=true');
});

// ---- Etapa 6: cliente del filtro con el explorador ----

async function elegirClienteDelFiltro(t, etiqueta) {
  t.$('elegir-cliente-filtro').click();
  await esperarTareas();
  [...t.documento.querySelectorAll('#explorador-resultados button')].find((b) => b.textContent === etiqueta).click();
  await esperarTareas();
}

test('Cliente del filtro: elegir el id 2 con el explorador lo muestra y no busca facturas hasta Buscar', async () => {
  const t = arrancar();
  await t.app.listo;
  await elegirClienteDelFiltro(t, 'Ana SA');
  assert.equal(t.$('filtro-cliente').textContent, 'Ana SA');
  assert.equal(t.$('filtro-cliente').getAttribute('data-id'), '2');
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
  assert.equal(t.$('explorador-titulo').textContent, 'Buscar cliente');
  buscar(t);
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{ clienteId: 2 }, P0]);
});

test('Quitar el cliente del filtro: vuelve a Todos y el listado se pide sin clienteId', async () => {
  const t = arrancar();
  await t.app.listo;
  await elegirClienteDelFiltro(t, 'Ana SA');
  t.$('quitar-cliente-filtro').click();
  assert.equal(t.$('filtro-cliente').textContent, 'Todos');
  assert.equal(t.$('filtro-cliente').hasAttribute('data-id'), false);
  buscar(t);
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas')[1].args, [{}, P0]);
});

test('Cancelar el explorador del filtro conserva el cliente elegido', async () => {
  const t = arrancar();
  await t.app.listo;
  await elegirClienteDelFiltro(t, 'Ana SA');
  t.$('elegir-cliente-filtro').click();
  await esperarTareas();
  t.$('explorador-cancelar').click();
  await esperarTareas();
  assert.equal(t.$('filtro-cliente').getAttribute('data-id'), '2');
});

test('Entrada por otra pantalla: catalogos una vez, el listado recien al mostrarse y con la pagina 0 sin filtros', async () => {
  const t = await arrancarApp({ url: 'http://localhost:5173/#/nueva' });
  assert.equal(t.servicio.de('obtenerCatalogos').length, 1);
  assert.equal(t.servicio.de('obtenerFacturas').length, 0);
  await t.navegar('#/');
  await esperarTareas();
  assert.equal(t.servicio.de('obtenerCatalogos').length, 1);
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
  assert.deepEqual(t.servicio.de('obtenerFacturas')[0].args, [{}, { pagina: 0, tamanio: 10 }]);
});

test('Listado que falla despues de salir de la pantalla: el error tardio no se muestra', async () => {
  const t = await arrancarApp({ url: 'http://localhost:5173/#/nueva', auto: { obtenerFacturas: null } });
  await t.navegar('#/');
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
  await t.navegar('#/nueva');
  t.servicio.de('obtenerFacturas')[0].rechazar(new ApiError(500, 'Error de servidor'));
  await esperarTareas();
  assert.equal(t.$('estado').textContent, '');
});

// ---- Etapa 7: el estado de la consulta se conserva al salir del listado y volver ----

const clientesFalsos = [
  { id: 1, etiqueta: 'Juan Servicios', valor: null, codigoMoneda: null },
  { id: 2, etiqueta: 'Ana SA', valor: null, codigoMoneda: null },
];
const tresPaginas = (_filtros, solicitud) => pagina([fila({ id: 1 })], { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: 25, totalPaginas: 3 });

test('Volver con filtros y pagina: campos intactos y pedido con estado, cliente y pagina vigentes', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: tresPaginas, buscarClientes: clientesFalsos } });
  await elegirClienteDelFiltro(t, 'Ana SA');
  t.$('filtro-estado').value = 'EMITIDA';
  buscar(t);
  await esperarTareas();
  t.$('pagina-siguiente').click();
  await esperarTareas();
  await t.navegar('#/factura/1');
  const atras = esperarHashchange(t.ventana);
  t.ventana.history.back();
  await atras;
  assert.equal(t.$('pantalla-listado').hidden, false);
  assert.equal(t.$('filtro-estado').value, 'EMITIDA');
  assert.equal(t.$('filtro-cliente').getAttribute('data-id'), '2');
  const ultima = t.servicio.de('obtenerFacturas').at(-1);
  assert.deepEqual(ultima.args, [{ estado: 'EMITIDA', clienteId: 2 }, { pagina: 1, tamanio: 10 }]);
});

test('Filtros editados sin buscar: el campo conserva lo escrito pero el pedido usa los del ultimo resultado', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: tresPaginas } });
  t.$('filtro-estado').value = 'EMITIDA';
  buscar(t);
  await esperarTareas();
  t.$('filtro-estado').value = 'ANULADA';
  await t.navegar('#/nueva');
  await t.navegar('#/');
  assert.equal(t.$('filtro-estado').value, 'ANULADA');
  assert.equal(t.servicio.de('obtenerFacturas').length, 3);
  assert.deepEqual(t.servicio.de('obtenerFacturas').at(-1).args, [{ estado: 'EMITIDA' }, { pagina: 0, tamanio: 10 }]);
});
