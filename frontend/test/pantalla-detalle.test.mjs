import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../dist/services/ApiError.js';
import { arrancarApp, borradorDetallado, esperarHashchange, esperarTareas, fila, facturaDetallada } from '../soporte-pruebas/soporte.mjs';

const BASE = 'http://localhost:5173/';
const contenido = (t) => t.$('detalle-contenido');
const botonesDelDetalle = (t) => [...t.$('pantalla-detalle').querySelectorAll('button')];
const enlace = (t, texto) => [...t.$('pantalla-detalle').querySelectorAll('a')].find((a) => a.textContent.trim() === texto);

test('Carga exitosa: un solo pedido con el id 1, la seccion deja de estar ocupada y muestra los datos', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { obtenerFactura: facturaDetallada() } });
  assert.equal(t.servicio.de('obtenerFactura').length, 1);
  assert.deepEqual(t.servicio.de('obtenerFactura')[0].args, [1]);
  assert.equal(t.$('pantalla-detalle').getAttribute('aria-busy'), 'false');
  assert.match(contenido(t).textContent, /Ana SA/);
  assert.equal(t.$('titulo-detalle').textContent, 'Factura 0001-00000004');
});

test('Durante la carga: aria-busy true y el h1 dice Factura 1', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { obtenerFactura: null }, esperarListo: false });
  await esperarTareas();
  assert.equal(t.$('pantalla-detalle').getAttribute('aria-busy'), 'true');
  assert.equal(t.$('titulo-detalle').textContent, 'Factura 1');
  t.servicio.de('obtenerFactura')[0].resolver(facturaDetallada());
  await esperarTareas();
  assert.equal(t.$('pantalla-detalle').getAttribute('aria-busy'), 'false');
});

test('Factura 999 inexistente: el mensaje de la API en la region, h1 Factura 999, Volver al listado y sin botones', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/999`, auto: { obtenerFactura: new ApiError(404, 'No existe la factura 999') } });
  assert.match(t.$('estado').textContent, /No existe la factura 999/);
  assert.equal(t.$('titulo-detalle').textContent, 'Factura 999');
  assert.equal(enlace(t, 'Volver al listado').getAttribute('href'), '#/');
  assert.equal(botonesDelDetalle(t).length, 0);
  assert.equal(contenido(t).textContent, '');
  assert.equal(t.$('pantalla-detalle').getAttribute('aria-busy'), 'false');
});

test('Falla de conexion: el mensaje de conexion en la region y el enlace Volver al listado disponible', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { obtenerFactura: new ApiError(0, 'No se pudo conectar con la API en http://localhost:8080/api. ¿Está corriendo el backend?') } });
  assert.match(t.$('estado').textContent, /No se pudo conectar con la API/);
  assert.ok(enlace(t, 'Volver al listado'));
});

test('Detalle que llega tarde: tras volver al listado el contenido del detalle no se actualiza', async () => {
  const t = await arrancarApp({ auto: { obtenerFactura: null } });
  await t.navegar('#/factura/5');
  await t.navegar('#/');
  t.servicio.de('obtenerFactura')[0].resolver(facturaDetallada({ id: 5 }));
  await esperarTareas();
  assert.equal(t.$('pantalla-listado').hidden, false);
  assert.equal(contenido(t).textContent, '');
  assert.equal(t.$('pantalla-detalle').getAttribute('aria-busy'), 'false');
});

test('Error que llega tarde: tras volver al listado la region no muestra el 404', async () => {
  const t = await arrancarApp({ auto: { obtenerFactura: null } });
  await t.navegar('#/factura/5');
  await t.navegar('#/');
  t.servicio.de('obtenerFactura')[0].rechazar(new ApiError(404, 'No existe la factura 5'));
  await esperarTareas();
  assert.equal(t.$('estado').textContent, '');
});

// ---- Acciones por estado ----

const POR_ID = (id) => ({ 6: borradorDetallado(), 1: facturaDetallada(), 3: facturaDetallada({ id: 3, numero: 2, estado: 'ANULADA' }) })[id];
const nombres = (t) => [...t.$('detalle-acciones').children].map((e) => e.textContent.trim());

test('Tres estados: BORRADOR ofrece Editar, Emitir y Eliminar; EMITIDA Anular e Imprimir PDF; ANULADA solo Imprimir PDF; todos Volver al listado', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: POR_ID } });
  assert.deepEqual(nombres(t), ['Editar borrador 6', 'Emitir borrador 6', 'Eliminar borrador 6', 'Volver al listado']);
  await t.navegar('#/factura/1');
  await esperarTareas();
  assert.deepEqual(nombres(t), ['Anular factura 1', 'Imprimir PDF factura 1', 'Volver al listado']);
  await t.navegar('#/factura/3');
  await esperarTareas();
  assert.deepEqual(nombres(t), ['Imprimir PDF factura 3', 'Volver al listado']);
});

test('Tipos de control: Editar es un enlace a la edicion y Emitir, Anular y Eliminar son botones type=button', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: POR_ID } });
  const [editar, emitir, eliminar] = [...t.$('detalle-acciones').children];
  assert.equal(editar.tagName, 'A');
  assert.equal(editar.getAttribute('href'), '#/factura/6/editar');
  for (const boton of [emitir, eliminar]) {
    assert.equal(boton.tagName, 'BUTTON');
    assert.equal(boton.getAttribute('type'), 'button');
  }
});

test('Informadas sin edicion ni eliminacion: ni EMITIDA ni ANULADA tienen control de editar o eliminar', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { obtenerFactura: POR_ID } });
  await t.navegar('#/factura/3');
  await esperarTareas();
  for (const id of [1, 3]) {
    await t.navegar(`#/factura/${id}`);
    await esperarTareas();
    assert.ok(!nombres(t).some((n) => /Editar|Eliminar/.test(n)), `factura ${id}: ${nombres(t)}`);
  }
});

test('Enlace de la factura 1: Imprimir PDF apunta a la URL del PDF y no pasa por el enrutador', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { obtenerFactura: POR_ID } });
  assert.equal(enlace(t, 'Imprimir PDF factura 1').getAttribute('href'), 'http://localhost:8080/api/facturas/1/pdf');
});

test('Borrador sin impresion: el detalle del borrador 6 no tiene enlace a un PDF', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: POR_ID } });
  assert.equal([...t.$('pantalla-detalle').querySelectorAll('a')].filter((a) => /\/pdf$/.test(a.getAttribute('href'))).length, 0);
});

// ---- Emitir, anular y eliminar desde el detalle ----

const boton = (t, accion) => t.$('detalle-acciones').querySelector(`button[data-accion="${accion}"]`);
/** Primer pedido del detalle devuelve el borrador y los siguientes la factura ya emitida. */
const borradorLuegoEmitida = () => {
  let llamadas = 0;
  return () => (llamadas++ === 0 ? borradorDetallado() : facturaDetallada({ id: 6, numero: 4 }));
};

test('Emision exitosa: anuncia el numero 4, vuelve a pedir el detalle y el anuncio sigue visible', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorLuegoEmitida(), cambiarEstado: fila({ id: 6, numeroFactura: 4, estado: 'EMITIDA' }) } });
  boton(t, 'emitir').click();
  await esperarTareas();
  assert.deepEqual(t.servicio.de('cambiarEstado')[0].args, [6, 'EMITIDA']);
  assert.equal(t.servicio.de('obtenerFactura').length, 2);
  assert.match(t.$('estado').textContent, /emiti.*número 4/);
  assert.equal(t.$('estado').className, 'estado--exito');
  assert.deepEqual(nombres(t), ['Anular factura 6', 'Imprimir PDF factura 6', 'Volver al listado']);
});

test('Emision rechazada: 409 visible, el detalle no cambia y las acciones quedan habilitadas', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorDetallado(), cambiarEstado: new ApiError(409, 'El borrador 6 no tiene renglones') } });
  boton(t, 'emitir').click();
  await esperarTareas();
  assert.match(t.$('estado').textContent, /El borrador 6 no tiene renglones/);
  assert.equal(t.servicio.de('obtenerFactura').length, 1);
  assert.deepEqual(nombres(t), ['Editar borrador 6', 'Emitir borrador 6', 'Eliminar borrador 6', 'Volver al listado']);
  assert.equal(boton(t, 'emitir').disabled, false);
  assert.equal(boton(t, 'eliminar').disabled, false);
});

test('Doble clic en emitir: un solo PATCH y mientras tanto Emitir y Eliminar deshabilitados', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorDetallado(), cambiarEstado: null } });
  boton(t, 'emitir').click();
  boton(t, 'emitir').click();
  await esperarTareas();
  assert.equal(t.servicio.de('cambiarEstado').length, 1);
  assert.equal(boton(t, 'emitir').disabled, true);
  assert.equal(boton(t, 'eliminar').disabled, true);
  t.servicio.de('cambiarEstado')[0].rechazar(new ApiError(409, 'x'));
  await esperarTareas();
  assert.equal(boton(t, 'emitir').disabled, false);
  assert.equal(boton(t, 'eliminar').disabled, false);
});

test('Anular con motivo: PATCH ANULADA con el motivo, anuncio y recarga del detalle', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { obtenerFactura: facturaDetallada(), cambiarEstado: fila({ id: 1, estado: 'ANULADA' }) }, dialogos: { motivo: 'Error en el cliente' } });
  boton(t, 'anular').click();
  await esperarTareas();
  assert.match(t.dialogos.motivos[0], /factura 1/);
  assert.deepEqual(t.servicio.de('cambiarEstado')[0].args, [1, 'ANULADA', 'Error en el cliente']);
  assert.match(t.$('estado').textContent, /anul.*1/);
  assert.equal(t.servicio.de('obtenerFactura').length, 2);
});

for (const vacio of ['', '   ']) {
  test(`Anular sin motivo (${JSON.stringify(vacio)}): se envia ANULADA sin observaciones`, async () => {
    const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { cambiarEstado: fila({ id: 1, estado: 'ANULADA' }) }, dialogos: { motivo: vacio } });
    boton(t, 'anular').click();
    await esperarTareas();
    const [id, estado, observaciones] = t.servicio.de('cambiarEstado')[0].args;
    assert.deepEqual([id, estado, observaciones], [1, 'ANULADA', undefined]);
  });
}

test('Motivo demasiado largo: error que menciona 500 y ninguna peticion', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, dialogos: { motivo: 'm'.repeat(501) } });
  boton(t, 'anular').click();
  await esperarTareas();
  assert.match(t.$('estado').textContent, /500/);
  assert.equal(t.servicio.de('cambiarEstado').length, 0);
  assert.equal(boton(t, 'anular').disabled, false);
});

test('Dialogo de anulacion cancelado: ninguna peticion y el detalle sigue EMITIDA', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, dialogos: { motivo: null } });
  boton(t, 'anular').click();
  await esperarTareas();
  assert.equal(t.dialogos.motivos.length, 1);
  assert.equal(t.servicio.de('cambiarEstado').length, 0);
  assert.equal(t.servicio.de('obtenerFactura').length, 1);
  assert.match(contenido(t).textContent, /EMITIDA/);
  assert.equal(boton(t, 'anular').disabled, false);
});

test('Eliminar confirmado: DELETE 6 con 204, se muestra el listado y se anuncia la eliminacion', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorDetallado(), eliminarFactura: undefined } });
  const llegada = esperarHashchange(t.ventana);
  boton(t, 'eliminar').click();
  await llegada;
  assert.match(t.dialogos.confirmaciones[0], /borrador 6/);
  assert.deepEqual(t.servicio.de('eliminarFactura')[0].args, [6]);
  assert.equal(t.$('pantalla-listado').hidden, false);
  assert.equal(t.$('pantalla-detalle').hidden, true);
  assert.match(t.$('estado').textContent, /elimin.*6/);
  assert.equal(t.$('estado').className, 'estado--exito');
});

test('Eliminar rechazado: ninguna peticion y el detalle sigue visible', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorDetallado() }, dialogos: { confirmar: false } });
  boton(t, 'eliminar').click();
  await esperarTareas();
  assert.equal(t.dialogos.confirmaciones.length, 1);
  assert.equal(t.servicio.de('eliminarFactura').length, 0);
  assert.equal(t.$('pantalla-detalle').hidden, false);
  assert.equal(boton(t, 'eliminar').disabled, false);
});

test('Factura inexistente al eliminar: el 404 queda visible hasta la siguiente accion', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorDetallado(), eliminarFactura: new ApiError(404, 'La factura 6 no existe') } });
  boton(t, 'eliminar').click();
  await esperarTareas();
  t.temporizador.avanzar(60000);
  assert.match(t.$('estado').textContent, /La factura 6 no existe/);
  assert.equal(t.$('pantalla-detalle').hidden, false);
  boton(t, 'emitir').click();
  assert.equal(t.$('estado').textContent, '');
});

// ---- Hallazgo del recorrido real: al reconstruir las acciones el boton con foco desaparecia y el foco caia a body ----

test('Foco tras emitir: el boton Emitir con foco se reemplaza y el foco pasa al h1 del detalle', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorLuegoEmitida(), cambiarEstado: fila({ id: 6, numeroFactura: 4, estado: 'EMITIDA' }) } });
  boton(t, 'emitir').focus();
  assert.equal(t.documento.activeElement, boton(t, 'emitir'));
  boton(t, 'emitir').click();
  await esperarTareas();
  assert.deepEqual(nombres(t), ['Anular factura 6', 'Imprimir PDF factura 6', 'Volver al listado']);
  assert.equal(t.documento.activeElement, t.$('titulo-detalle'));
});

test('Foco tras anular: el foco pasa al h1 del detalle', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/1`, auto: { obtenerFactura: facturaDetallada(), cambiarEstado: fila({ id: 1, estado: 'ANULADA' }) }, dialogos: { motivo: '' } });
  boton(t, 'anular').focus();
  boton(t, 'anular').click();
  await esperarTareas();
  assert.equal(t.documento.activeElement, t.$('titulo-detalle'));
});

test('Foco tras un error: el boton Emitir conserva el foco al liberarse las acciones', async () => {
  const t = await arrancarApp({ url: `${BASE}#/factura/6`, auto: { obtenerFactura: borradorDetallado(), cambiarEstado: new ApiError(409, 'No tiene renglones') } });
  boton(t, 'emitir').focus();
  boton(t, 'emitir').click();
  await esperarTareas();
  assert.equal(t.documento.activeElement, boton(t, 'emitir'));
});
