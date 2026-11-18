import assert from 'node:assert/strict';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { ApiError } from '../dist/services/ApiError.js';
import { catalogosEjemplo, dialogosFalsos, esperarTareas, fila, montarPagina, servicioFalso, temporizadorManual } from '../soporte-pruebas/soporte.mjs';

const LISTADO = () => [
  fila({ id: 6, estado: 'BORRADOR', numeroFactura: null }),
  fila({ id: 1, estado: 'EMITIDA', numeroFactura: 3 }),
  fila({ id: 3, estado: 'ANULADA', numeroFactura: 2 }),
];

async function arrancar(auto = {}, opcionesDialogos = {}) {
  const pagina = montarPagina();
  const dialogos = dialogosFalsos(opcionesDialogos);
  const servicio = servicioFalso({ obtenerCatalogos: catalogosEjemplo(), obtenerFacturas: LISTADO(), ...auto });
  const temporizador = temporizadorManual();
  const app = iniciarAplicacion({ documento: pagina.documento, servicio, dialogos, temporizador });
  await app.listo;
  const $ = (id) => pagina.documento.getElementById(id);
  const botones = (id) => [...$('filas').querySelectorAll(`button[data-id="${id}"]`)];
  const boton = (accion, id) => $('filas').querySelector(`button[data-accion="${accion}"][data-id="${id}"]`);
  const idsEnTabla = () => [...$('filas').rows].map((r) => r.querySelector('button')?.getAttribute('data-id') ?? '-');
  return { ...pagina, servicio, dialogos, temporizador, $, botones, boton, idsEnTabla };
}

test('Tres estados: BORRADOR ofrece editar, emitir y eliminar; EMITIDA anular; ANULADA nada', async () => {
  const t = await arrancar();
  assert.deepEqual(t.botones(6).map((b) => b.getAttribute('data-accion')), ['editar', 'emitir', 'eliminar']);
  assert.deepEqual(t.botones(1).map((b) => b.getAttribute('data-accion')), ['anular']);
  assert.equal(t.$('filas').rows[2].querySelectorAll('button').length, 0);
});

test('Nombre accesible: incluye la accion y el identificador', async () => {
  const t = await arrancar();
  assert.equal(t.boton('emitir', 6).textContent, 'Emitir borrador 6');
  assert.equal(t.boton('anular', 1).textContent, 'Anular factura 1');
  assert.equal(t.boton('eliminar', 6).textContent, 'Eliminar borrador 6');
  assert.equal(t.boton('editar', 6).textContent, 'Editar borrador 6');
});

test('Emision exitosa: anuncia el numero 4 y recarga el listado', async () => {
  const t = await arrancar({ cambiarEstado: fila({ id: 6, numeroFactura: 4, estado: 'EMITIDA' }) });
  t.boton('emitir', 6).click();
  await esperarTareas();
  assert.deepEqual(t.servicio.de('cambiarEstado')[0].args, [6, 'EMITIDA']);
  assert.match(t.$('estado').textContent, /emiti.*número 4/);
  assert.equal(t.servicio.de('obtenerFacturas').length, 2);
});

test('Emision rechazada: 409 visible y la tabla no cambia', async () => {
  const t = await arrancar({ cambiarEstado: new ApiError(409, 'El borrador 6 no tiene renglones') });
  t.boton('emitir', 6).click();
  await esperarTareas();
  assert.match(t.$('estado').textContent, /El borrador 6 no tiene renglones/);
  assert.deepEqual(t.idsEnTabla(), ['6', '1', '-']);
  assert.equal(t.servicio.de('obtenerFacturas').length, 1);
  assert.ok(t.botones(6).every((b) => !b.disabled));
});

test('Doble clic en emitir: un solo PATCH y acciones liberadas al terminar', async () => {
  const t = await arrancar();
  t.boton('emitir', 6).click();
  t.boton('emitir', 6).click();
  assert.equal(t.servicio.de('cambiarEstado').length, 1);
  assert.ok([...t.$('filas').querySelectorAll('button')].every((b) => b.disabled));
  t.servicio.de('cambiarEstado')[0].resolver(fila({ id: 6, numeroFactura: 4 }));
  await esperarTareas();
  assert.ok([...t.$('filas').querySelectorAll('button')].every((b) => !b.disabled));
});

test('Una accion en curso bloquea tambien el guardado del formulario', async () => {
  const t = await arrancar();
  t.$('condicion-iva').value = '2';
  t.$('punto-venta').value = '1';
  t.$('tipo-moneda').value = '1';
  t.$('renglones').querySelector('[name="listaPrecioArticuloId"]').value = '1';
  t.$('renglones').querySelector('[name="cantidad"]').value = '1';
  t.boton('emitir', 6).click();
  t.$('form-factura').requestSubmit();
  await esperarTareas();
  assert.equal(t.servicio.de('crearFactura').length, 0);
  assert.equal(t.$('estado').textContent, '');
});

test('Eliminar confirmado: DELETE 6 con 204 (undefined), anuncio y recarga', async () => {
  const t = await arrancar({ eliminarFactura: undefined });
  t.boton('eliminar', 6).click();
  await esperarTareas();
  assert.match(t.dialogos.confirmaciones[0], /borrador 6/);
  assert.deepEqual(t.servicio.de('eliminarFactura')[0].args, [6]);
  assert.match(t.$('estado').textContent, /elimin.*6/);
  assert.equal(t.servicio.de('obtenerFacturas').length, 2);
});

test('Eliminar rechazado: ninguna llamada y la tabla no cambia', async () => {
  const t = await arrancar({}, { confirmar: false });
  t.boton('eliminar', 6).click();
  await esperarTareas();
  assert.equal(t.dialogos.confirmaciones.length, 1);
  assert.equal(t.servicio.de('eliminarFactura').length, 0);
  assert.deepEqual(t.idsEnTabla(), ['6', '1', '-']);
  assert.ok(t.botones(6).every((b) => !b.disabled));
});

test('Factura inexistente: el 404 queda visible hasta la siguiente accion', async () => {
  const t = await arrancar({ eliminarFactura: new ApiError(404, 'La factura 6 no existe') });
  t.boton('eliminar', 6).click();
  await esperarTareas();
  t.temporizador.avanzar(60000);
  assert.match(t.$('estado').textContent, /La factura 6 no existe/);
  t.$('form-filtros').requestSubmit();
  assert.equal(t.$('estado').textContent, '');
});

test('Anular con motivo: PATCH ANULADA con el motivo y recarga', async () => {
  const t = await arrancar({ cambiarEstado: fila({ id: 1, estado: 'ANULADA' }) }, { motivo: 'Error en el cliente' });
  t.boton('anular', 1).click();
  await esperarTareas();
  assert.deepEqual(t.servicio.de('cambiarEstado')[0].args, [1, 'ANULADA', 'Error en el cliente']);
  assert.match(t.$('estado').textContent, /anul.*1/);
  assert.equal(t.servicio.de('obtenerFacturas').length, 2);
});

for (const vacio of ['', '   ']) {
  test(`Anular sin motivo (${JSON.stringify(vacio)}): se envia ANULADA sin observaciones`, async () => {
    const t = await arrancar({ cambiarEstado: fila({ id: 1, estado: 'ANULADA' }) }, { motivo: vacio });
    t.boton('anular', 1).click();
    await esperarTareas();
    const [id, estado, observaciones] = t.servicio.de('cambiarEstado')[0].args;
    assert.deepEqual([id, estado, observaciones], [1, 'ANULADA', undefined]);
  });
}

test('Motivo demasiado largo: error que menciona 500 y ninguna llamada', async () => {
  const t = await arrancar({}, { motivo: 'm'.repeat(501) });
  t.boton('anular', 1).click();
  await esperarTareas();
  assert.match(t.$('estado').textContent, /500/);
  assert.equal(t.servicio.de('cambiarEstado').length, 0);
});

test('Dialogo de anulacion cancelado: ninguna llamada y la fila sigue EMITIDA', async () => {
  const t = await arrancar({}, { motivo: null });
  t.boton('anular', 1).click();
  await esperarTareas();
  assert.equal(t.dialogos.motivos.length, 1);
  assert.equal(t.servicio.de('cambiarEstado').length, 0);
  assert.equal(t.$('filas').rows[1].cells[7].textContent, 'EMITIDA');
  assert.ok(t.botones(1).every((b) => !b.disabled));
});
