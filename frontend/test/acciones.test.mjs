import assert from 'node:assert/strict';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { ApiError } from '../dist/services/ApiError.js';
import { catalogosEjemplo, dialogosFalsos, esperarTareas, fila, montarPagina, pagina, servicioFalso, temporizadorManual } from '../soporte-pruebas/soporte.mjs';

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

// ---- Etapa 6: la recarga conserva la pagina vigente ----

test('Emision en la pagina 2 (indice 1): se vuelve a pedir el listado con los mismos filtros y pagina=1', async () => {
  const t = await arrancar({
    cambiarEstado: fila({ id: 6, numeroFactura: 4 }),
    obtenerFacturas: (_filtros, solicitud) => pagina(solicitud.pagina === 0 ? LISTADO() : [fila({ id: 16, estado: 'BORRADOR', numeroFactura: null })],
      { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: 11, totalPaginas: 2 }),
  });
  t.$('filtro-estado').value = 'BORRADOR';
  t.$('form-filtros').requestSubmit();
  await esperarTareas();
  t.$('pagina-siguiente').click();
  await esperarTareas();
  t.boton('emitir', 16).click();
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas').at(-1).args, [{ estado: 'BORRADOR' }, { pagina: 1, tamanio: 10 }]);
});

test('Se elimina el unico elemento de la ultima pagina: se pide pagina=2, llega vacia con 2 paginas y se pide pagina=1 una sola vez', async () => {
  let eliminado = false;
  const t = await arrancar({
    eliminarFactura: undefined,
    obtenerFacturas: (_filtros, solicitud) => {
      if (solicitud.pagina === 2) {
        return eliminado
          ? pagina([], { pagina: 2, tamanio: 10, totalElementos: 20, totalPaginas: 2 })
          : pagina([fila({ id: 21, estado: 'BORRADOR', numeroFactura: null })], { pagina: 2, tamanio: 10, totalElementos: 21, totalPaginas: 3 });
      }
      return pagina([fila({ id: 100 + solicitud.pagina })], { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: eliminado ? 20 : 21, totalPaginas: eliminado ? 2 : 3 });
    },
  });
  t.$('pagina-siguiente').click();
  await esperarTareas();
  t.$('pagina-siguiente').click();
  await esperarTareas();
  assert.equal(t.$('pagina-indicador').textContent, 'Página 3 de 3');
  const antes = t.servicio.de('obtenerFacturas').length;
  eliminado = true;
  t.boton('eliminar', 21).click();
  await esperarTareas();
  const nuevas = t.servicio.de('obtenerFacturas').slice(antes).map((l) => l.args[1].pagina);
  assert.deepEqual(nuevas, [2, 1]);
  assert.equal(t.$('pagina-indicador').textContent, 'Página 2 de 2');
  assert.equal(t.$('filas').rows.length, 1);
  assert.equal(t.$('contador').textContent, '20 facturas');
});

test('Si la pagina reintentada tambien llega vacia no se pide una tercera vez (sin bucle)', async () => {
  let eliminado = false;
  const t = await arrancar({
    eliminarFactura: undefined,
    obtenerFacturas: (_filtros, solicitud) => {
      if (!eliminado) {
        return solicitud.pagina === 2
          ? pagina([fila({ id: 21, estado: 'BORRADOR', numeroFactura: null })], { pagina: 2, tamanio: 10, totalElementos: 21, totalPaginas: 3 })
          : pagina([fila({ id: 100 + solicitud.pagina })], { pagina: solicitud.pagina, tamanio: 10, totalElementos: 21, totalPaginas: 3 });
      }
      // Otro usuario siguió borrando: cada página pedida llega vacía y el total sigue achicándose.
      return pagina([], { pagina: solicitud.pagina, tamanio: 10, totalElementos: solicitud.pagina * 10, totalPaginas: solicitud.pagina });
    },
  });
  t.$('pagina-siguiente').click();
  await esperarTareas();
  t.$('pagina-siguiente').click();
  await esperarTareas();
  const antes = t.servicio.de('obtenerFacturas').length;
  eliminado = true;
  t.boton('eliminar', 21).click();
  await esperarTareas();
  assert.deepEqual(t.servicio.de('obtenerFacturas').slice(antes).map((l) => l.args[1].pagina), [2, 1]);
});

test('Filas informadas en la segunda pagina: EMITIDA y ANULADA sin controles de editar ni eliminar', async () => {
  const t = await arrancar({
    obtenerFacturas: (_filtros, solicitud) => pagina(solicitud.pagina === 0 ? [fila({ id: 9, estado: 'BORRADOR', numeroFactura: null })] : [fila({ id: 1, estado: 'EMITIDA' }), fila({ id: 3, estado: 'ANULADA', numeroFactura: 2 })],
      { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: 3, totalPaginas: 2 }),
  });
  t.$('pagina-siguiente').click();
  await esperarTareas();
  assert.equal(t.$('pagina-indicador').textContent, 'Página 2 de 2');
  assert.equal(t.$('filas').querySelectorAll('button[data-accion="editar"], button[data-accion="eliminar"]').length, 0);
  assert.equal(t.$('filas').rows.length, 2);
});

// ---- Etapa 6: Imprimir PDF de las facturas informadas ----

const enlacesDe = (t, indiceFila) => [...t.$('filas').rows[indiceFila].querySelectorAll('a')];

test('EMITIDA: enlace nativo Imprimir PDF a /facturas/1/pdf y boton Anular', async () => {
  const t = await arrancar();
  const [pdf, ...otros] = enlacesDe(t, 1);
  assert.equal(otros.length, 0);
  assert.equal(pdf.getAttribute('href'), 'http://localhost:8080/api/facturas/1/pdf');
  assert.equal(pdf.textContent, 'Imprimir PDF factura 1');
  assert.deepEqual(t.botones(1).map((b) => b.getAttribute('data-accion')), ['anular']);
});

test('ANULADA: solo el enlace Imprimir PDF, sin ningun boton', async () => {
  const t = await arrancar();
  assert.equal(enlacesDe(t, 2).length, 1);
  assert.equal(enlacesDe(t, 2)[0].getAttribute('href'), 'http://localhost:8080/api/facturas/3/pdf');
  assert.equal(enlacesDe(t, 2)[0].textContent, 'Imprimir PDF factura 3');
  assert.equal(t.$('filas').rows[2].querySelectorAll('button').length, 0);
});

test('BORRADOR: sin enlace a ningun PDF', async () => {
  const t = await arrancar();
  assert.equal(enlacesDe(t, 0).length, 0);
  assert.equal(t.$('filas').querySelectorAll('a[href*="/pdf"]').length, 2);
});

test('El enlace de PDF no pasa por la region de estado ni dispara pedidos del listado', async () => {
  const t = await arrancar();
  const pedidos = t.servicio.llamadas.length;
  enlacesDe(t, 1)[0].addEventListener('click', (e) => e.preventDefault());
  enlacesDe(t, 1)[0].click();
  await esperarTareas();
  assert.equal(t.servicio.llamadas.length, pedidos);
  assert.equal(t.$('estado').textContent, '');
});

test('El enlace de PDF sigue disponible mientras hay una accion en curso (no es un boton)', async () => {
  const t = await arrancar();
  t.boton('emitir', 6).click();
  assert.equal(enlacesDe(t, 1)[0].hasAttribute('disabled'), false);
  assert.equal(enlacesDe(t, 1)[0].getAttribute('href'), 'http://localhost:8080/api/facturas/1/pdf');
});
