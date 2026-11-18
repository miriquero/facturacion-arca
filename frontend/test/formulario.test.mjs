import assert from 'node:assert/strict';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { ApiError } from '../dist/services/ApiError.js';
import { catalogosEjemplo, dialogosFalsos, esperarTareas, fila, montarPagina, servicioFalso, temporizadorManual } from '../soporte-pruebas/soporte.mjs';

async function arrancar(auto = {}) {
  const pagina = montarPagina();
  const servicio = servicioFalso({ obtenerCatalogos: catalogosEjemplo(), obtenerFacturas: [], ...auto });
  const temporizador = temporizadorManual();
  const app = iniciarAplicacion({ documento: pagina.documento, servicio, dialogos: dialogosFalsos(), temporizador });
  await app.listo;
  const $ = (id) => pagina.documento.getElementById(id);
  const filas = () => [...$('renglones').querySelectorAll('fieldset.renglon')];
  const campo = (i, nombre) => filas()[i].querySelector(`[name="${nombre}"]`);
  const evento = (tipo) => new pagina.ventana.Event(tipo, { bubbles: true });
  const poner = (el, valor) => {
    el.value = valor;
    el.dispatchEvent(evento(el.tagName === 'SELECT' ? 'change' : 'input'));
  };
  const enviar = () => $('form-factura').requestSubmit();
  const llenarCabecera = () => {
    poner($('condicion-iva'), '2');
    poner($('punto-venta'), '1');
    poner($('tipo-moneda'), '1');
  };
  const llenarRenglon = (i, { articulo = '1', cantidad = '1', bonificacion, descripcion } = {}) => {
    poner(campo(i, 'listaPrecioArticuloId'), articulo);
    poner(campo(i, 'cantidad'), cantidad);
    if (bonificacion !== undefined) poner(campo(i, 'porcentajeBonificacion'), bonificacion);
    if (descripcion !== undefined) poner(campo(i, 'descripcion'), descripcion);
  };
  return { ...pagina, servicio, temporizador, app, $, filas, campo, poner, enviar, llenarCabecera, llenarRenglon };
}

test('Arranca con un renglon vacio y el total estimado en cero', async () => {
  const t = await arrancar();
  assert.equal(t.filas().length, 1);
  assert.equal(t.$('total-estimado').textContent, '0,00');
});

test('Agregar y quitar: tres renglones, se quita el segundo, quedan el primero y el tercero', async () => {
  const t = await arrancar();
  t.$('agregar-renglon').click();
  t.$('agregar-renglon').click();
  assert.equal(t.filas().length, 3);
  ['1', '2', '3'].forEach((c, i) => t.poner(t.campo(i, 'cantidad'), c));
  t.filas()[1].querySelector('button[data-quitar]').click();
  assert.deepEqual(t.filas().map((_, i) => t.campo(i, 'cantidad').value), ['1', '3']);
  const ids = [...t.$('renglones').querySelectorAll('[id]')].map((e) => e.id);
  assert.deepEqual(ids.filter((id, i) => ids.indexOf(id) !== i), []);
});

test('Quitar renglon: el foco pasa al siguiente; si no hay, a Agregar renglon', async () => {
  const t = await arrancar();
  t.$('agregar-renglon').click();
  t.$('agregar-renglon').click();
  t.filas()[1].querySelector('button[data-quitar]').click();
  assert.equal(t.documento.activeElement, t.campo(1, 'listaPrecioArticuloId'));
  t.filas()[1].querySelector('button[data-quitar]').click();
  assert.equal(t.documento.activeElement, t.$('agregar-renglon'));
});

test('Los campos del renglon 2 se llaman "... renglon 2" y se renumeran al quitar', async () => {
  const t = await arrancar();
  t.$('agregar-renglon').click();
  t.$('agregar-renglon').click();
  const etiqueta = (i, nombre) => t.documento.querySelector(`label[for="${t.campo(i, nombre).id}"]`).textContent;
  assert.equal(etiqueta(1, 'cantidad'), 'Cantidad (renglón 2)');
  assert.equal(etiqueta(0, 'cantidad'), 'Cantidad (renglón 1)');
  t.filas()[0].querySelector('button[data-quitar]').click();
  assert.equal(etiqueta(0, 'cantidad'), 'Cantidad (renglón 1)');
  assert.equal(t.filas()[1].querySelector('legend').textContent, 'Renglón 2');
  assert.equal(t.filas()[1].querySelector('button[data-quitar]').textContent, 'Quitar renglón 2');
});

test('Estimacion con bonificacion: precios 3.000,00 y 8.000,00 y total 13.400,00', async () => {
  const t = await arrancar();
  t.$('agregar-renglon').click();
  t.poner(t.$('tipo-moneda'), '');
  t.llenarRenglon(0, { articulo: '1', cantidad: '2', bonificacion: '10' });
  t.llenarRenglon(1, { articulo: '3', cantidad: '1' });
  assert.equal(t.filas()[0].querySelector('output').textContent, '3.000,00');
  assert.equal(t.filas()[1].querySelector('output').textContent, '8.000,00');
  assert.equal(t.$('total-estimado').textContent, '13.400,00');
  assert.match(t.$('total-estimado').parentElement.textContent, /sin IVA/);
});

test('Estimacion con moneda elegida: el total lleva el simbolo de la moneda', async () => {
  const t = await arrancar();
  t.poner(t.$('tipo-moneda'), '1');
  t.llenarRenglon(0, { articulo: '3', cantidad: '1' });
  assert.equal(t.$('total-estimado').textContent, '$ 8.000,00');
});

test('Renglon incompleto: sin opcion o con cantidad invalida no suma ni muestra precio', async () => {
  const t = await arrancar();
  t.$('agregar-renglon').click();
  t.$('agregar-renglon').click();
  t.llenarRenglon(0, { articulo: '', cantidad: '2' });
  t.llenarRenglon(1, { articulo: '3', cantidad: '0' });
  t.llenarRenglon(2, { articulo: '1', cantidad: '1' });
  assert.equal(t.filas()[0].querySelector('output').textContent, '');
  assert.equal(t.filas()[1].querySelector('output').textContent, '');
  assert.equal(t.$('total-estimado').textContent, '3.000,00');
});

test('Obligatorios sin elegir: punto de venta y moneda invalidos, mensaje asociado y ninguna llamada', async () => {
  const t = await arrancar();
  t.poner(t.$('condicion-iva'), '2');
  t.llenarRenglon(0);
  t.enviar();
  await esperarTareas();
  for (const id of ['punto-venta', 'tipo-moneda']) {
    const c = t.$(id);
    assert.equal(c.getAttribute('aria-invalid'), 'true', id);
    assert.ok(t.$(c.getAttribute('aria-describedby')).textContent.length > 0);
  }
  assert.equal(t.$('condicion-iva').hasAttribute('aria-invalid'), false);
  assert.equal(t.servicio.de('crearFactura').length, 0);
  assert.equal(t.documento.activeElement, t.$('punto-venta'));
});

test('Sin renglones: error que pide al menos un renglon y ninguna llamada', async () => {
  const t = await arrancar();
  t.llenarCabecera();
  t.filas()[0].querySelector('button[data-quitar]').click();
  t.enviar();
  await esperarTareas();
  assert.equal(t.$('error-detalles').hidden, false);
  assert.match(t.$('error-detalles').textContent, /al menos un renglón/);
  assert.equal(t.servicio.de('crearFactura').length, 0);
});

test('Cantidad, bonificacion y textos fuera de limites: campos invalidos y ninguna llamada', async () => {
  const t = await arrancar();
  t.llenarCabecera();
  t.$('agregar-renglon').click();
  t.llenarRenglon(0, { cantidad: '0', bonificacion: '100.01', descripcion: 'a'.repeat(256) });
  t.llenarRenglon(1, { cantidad: '1e400', bonificacion: '-0.01' });
  t.poner(t.$('observaciones'), 'o'.repeat(501));
  t.enviar();
  await esperarTareas();
  for (const [i, n] of [[0, 'cantidad'], [0, 'porcentajeBonificacion'], [0, 'descripcion'], [1, 'cantidad'], [1, 'porcentajeBonificacion']]) {
    assert.equal(t.campo(i, n).getAttribute('aria-invalid'), 'true', `${i} ${n}`);
  }
  assert.match(t.documento.getElementById(t.campo(0, 'descripcion').getAttribute('aria-describedby')).textContent, /255/);
  assert.match(t.documento.getElementById(t.$('observaciones').getAttribute('aria-describedby')).textContent, /500/);
  assert.equal(t.servicio.de('crearFactura').length, 0);
});

test('Correccion: al guardar con el valor corregido se quitan las marcas y el mensaje', async () => {
  const t = await arrancar({ crearFactura: fila({ id: 15, estado: 'BORRADOR', numeroFactura: null }) });
  t.llenarCabecera();
  t.llenarRenglon(0, { cantidad: '0' });
  t.enviar();
  const idError = t.campo(0, 'cantidad').getAttribute('aria-describedby');
  assert.ok(t.$(idError));
  t.poner(t.campo(0, 'cantidad'), '2');
  t.enviar();
  await esperarTareas();
  assert.equal(t.servicio.de('crearFactura').length, 1);
  assert.equal(t.documento.querySelectorAll('[aria-invalid="true"]').length, 0);
  assert.equal(t.$(idError), null);
});

test('Alta exitosa: request exacto, anuncia el borrador 15, formulario vacio y recarga con los filtros aplicados', async () => {
  const t = await arrancar({ crearFactura: fila({ id: 15, estado: 'BORRADOR', numeroFactura: null }) });
  t.$('filtro-estado').value = 'EMITIDA';
  t.$('form-filtros').requestSubmit();
  await esperarTareas();
  t.llenarCabecera();
  t.llenarRenglon(0, { cantidad: '0.5', descripcion: 'a'.repeat(255) });
  t.enviar();
  await esperarTareas();
  assert.deepEqual(t.servicio.de('crearFactura')[0].args, [
    { condicionIvaId: 2, puntoVentaId: 1, tipoMonedaId: 1, detalles: [{ listaPrecioArticuloId: 1, cantidad: 0.5, descripcion: 'a'.repeat(255) }] },
  ]);
  assert.match(t.$('estado').textContent, /borrador 15/);
  assert.equal(t.$('punto-venta').value, '');
  assert.equal(t.filas().length, 1);
  assert.equal(t.campo(0, 'cantidad').value, '');
  assert.deepEqual(t.servicio.de('obtenerFacturas').at(-1).args, [{ estado: 'EMITIDA' }]);
  assert.equal(t.servicio.de('obtenerFacturas').length, 3);
});

test('Consumidor Final: el request no incluye clienteId; con cliente elegido si', async () => {
  const t = await arrancar({ crearFactura: fila({ id: 15 }) });
  t.llenarCabecera();
  t.llenarRenglon(0);
  t.enviar();
  await esperarTareas();
  assert.equal('clienteId' in t.servicio.de('crearFactura')[0].args[0], false);
  t.llenarCabecera();
  t.llenarRenglon(0);
  t.poner(t.$('cliente'), '2');
  t.enviar();
  await esperarTareas();
  assert.equal(t.servicio.de('crearFactura')[1].args[0].clienteId, 2);
});

test('Bloqueo durante el alta: aria-busy, fieldset y acciones de fila deshabilitados; doble envio ignorado', async () => {
  const t = await arrancar({ obtenerFacturas: [fila({ id: 6, estado: 'BORRADOR', numeroFactura: null })] });
  t.llenarCabecera();
  t.llenarRenglon(0);
  t.enviar();
  t.enviar();
  assert.equal(t.servicio.de('crearFactura').length, 1);
  assert.equal(t.$('form-factura').getAttribute('aria-busy'), 'true');
  assert.equal(t.$('contenido-formulario').disabled, true);
  assert.ok([...t.$('filas').querySelectorAll('button')].every((b) => b.disabled));
  t.servicio.de('crearFactura')[0].resolver(fila({ id: 15 }));
  await esperarTareas();
  assert.equal(t.$('form-factura').getAttribute('aria-busy'), 'false');
  assert.equal(t.$('contenido-formulario').disabled, false);
  assert.ok([...t.$('filas').querySelectorAll('button')].every((b) => !b.disabled));
});

test('Liberacion tras error 400: controles habilitados, aria-busy false y se puede reintentar', async () => {
  const t = await arrancar();
  t.llenarCabecera();
  t.llenarRenglon(0);
  t.enviar();
  t.servicio.de('crearFactura')[0].rechazar(new ApiError(400, 'Datos inválidos'));
  await esperarTareas();
  assert.equal(t.$('form-factura').getAttribute('aria-busy'), 'false');
  assert.equal(t.$('contenido-formulario').disabled, false);
  t.enviar();
  assert.equal(t.servicio.de('crearFactura').length, 2);
});

test('Valores conservados ante un 500 sin detalle legible', async () => {
  const t = await arrancar({ crearFactura: new ApiError(500, 'El servidor respondió 500 sin un detalle legible.') });
  t.llenarCabecera();
  t.llenarRenglon(0, { cantidad: '3', descripcion: 'Con descripción' });
  t.poner(t.$('observaciones'), 'Entrega mañana');
  t.enviar();
  await esperarTareas();
  assert.match(t.$('estado').textContent, /500 sin un detalle legible/);
  assert.equal(t.$('punto-venta').value, '1');
  assert.equal(t.campo(0, 'cantidad').value, '3');
  assert.equal(t.campo(0, 'descripcion').value, 'Con descripción');
  assert.equal(t.$('observaciones').value, 'Entrega mañana');
});

test('Errores del servidor por campo: detalles[1].descripcion marca el segundo renglon', async () => {
  const errores = { 'detalles[1].descripcion': 'admite hasta 255 caracteres' };
  const t = await arrancar({ crearFactura: new ApiError(400, 'Datos inválidos', undefined, errores) });
  t.llenarCabecera();
  t.$('agregar-renglon').click();
  t.llenarRenglon(0);
  t.llenarRenglon(1);
  t.enviar();
  await esperarTareas();
  const campo = t.campo(1, 'descripcion');
  assert.equal(campo.getAttribute('aria-invalid'), 'true');
  assert.equal(t.$(campo.getAttribute('aria-describedby')).textContent, 'admite hasta 255 caracteres');
  assert.equal(t.campo(0, 'descripcion').hasAttribute('aria-invalid'), false);
});

test('Clave desconocida del servidor: se lista en la region junto con el detalle', async () => {
  const t = await arrancar({ crearFactura: new ApiError(400, 'Datos inválidos', undefined, { rareza: 'no se entiende' }) });
  t.llenarCabecera();
  t.llenarRenglon(0);
  t.enviar();
  await esperarTareas();
  assert.match(t.$('estado').textContent, /Datos inválidos/);
  assert.deepEqual([...t.$('estado').querySelectorAll('li')].map((li) => li.textContent), ['rareza: no se entiende']);
});
