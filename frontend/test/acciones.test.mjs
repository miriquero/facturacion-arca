import assert from 'node:assert/strict';
import { test } from 'node:test';
import { arrancarApp, borradorDetallado, esperarHashchange, esperarTareas, fila, pagina } from '../soporte-pruebas/soporte.mjs';

// Etapa 7: las acciones sobre una factura (emitir, anular, eliminar) pasaron del listado al detalle y sus pruebas
// viven en pantalla-detalle.test.mjs. Aca quedan las filas del listado (solo navegacion y PDF) y la recarga del
// listado al volver desde el detalle.

const LISTADO = () => [
  fila({ id: 6, estado: 'BORRADOR', numeroFactura: null }),
  fila({ id: 1, estado: 'EMITIDA', numeroFactura: 3 }),
  fila({ id: 3, estado: 'ANULADA', numeroFactura: 2 }),
];

const enlacesDe = (t, indiceFila) => [...t.$('filas').rows[indiceFila].querySelectorAll('a')];
const textos = (enlaces) => enlaces.map((a) => a.textContent);
/** Activa un enlace de fragmento como lo haria el usuario y espera el cambio de pantalla. */
async function activar(t, enlace) {
  const llegada = esperarHashchange(t.ventana);
  enlace.click();
  await llegada;
}

test('Fila con solo navegacion: Ver a #/factura/{id}, Imprimir PDF solo en las informadas y ningun boton', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: LISTADO() } });
  assert.deepEqual(textos(enlacesDe(t, 0)), ['Ver factura 6']);
  assert.deepEqual(textos(enlacesDe(t, 1)), ['Ver factura 1', 'Imprimir PDF factura 1']);
  assert.deepEqual(textos(enlacesDe(t, 2)), ['Ver factura 3', 'Imprimir PDF factura 3']);
  assert.deepEqual([6, 1, 3].map((id, i) => enlacesDe(t, i)[0].getAttribute('href')), ['#/factura/6', '#/factura/1', '#/factura/3']);
  assert.equal(t.$('filas').querySelectorAll('button').length, 0);
});

test('Nombre accesible: el enlace Ver de la fila 6 incluye la accion y el identificador', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: LISTADO() } });
  assert.equal(enlacesDe(t, 0)[0].textContent, 'Ver factura 6');
  assert.equal(enlacesDe(t, 0)[0].querySelector('.visualmente-oculto').textContent, ' factura 6');
});

test('Filas informadas en la segunda pagina: EMITIDA y ANULADA sin controles de editar ni eliminar', async () => {
  const t = await arrancarApp({
    auto: {
      obtenerFacturas: (_filtros, solicitud) => pagina(solicitud.pagina === 0 ? [fila({ id: 9, estado: 'BORRADOR', numeroFactura: null })] : [fila({ id: 1, estado: 'EMITIDA' }), fila({ id: 3, estado: 'ANULADA', numeroFactura: 2 })],
        { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: 3, totalPaginas: 2 }),
    },
  });
  t.$('pagina-siguiente').click();
  await esperarTareas();
  assert.equal(t.$('pagina-indicador').textContent, 'Página 2 de 2');
  assert.equal(t.$('filas').rows.length, 2);
  assert.equal(t.$('filas').querySelectorAll('button').length, 0);
  assert.ok(!textos([...t.$('filas').querySelectorAll('a')]).some((x) => /Editar|Eliminar/.test(x)));
});

// ---- Etapa 6: Imprimir PDF de las facturas informadas ----

test('EMITIDA: enlace nativo Imprimir PDF a /facturas/1/pdf', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: LISTADO() } });
  const pdf = enlacesDe(t, 1)[1];
  assert.equal(pdf.getAttribute('href'), 'http://localhost:8080/api/facturas/1/pdf');
  assert.equal(pdf.textContent, 'Imprimir PDF factura 1');
});

test('ANULADA: enlace Imprimir PDF a /facturas/3/pdf', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: LISTADO() } });
  assert.equal(enlacesDe(t, 2)[1].getAttribute('href'), 'http://localhost:8080/api/facturas/3/pdf');
  assert.equal(enlacesDe(t, 2)[1].textContent, 'Imprimir PDF factura 3');
});

test('BORRADOR: sin enlace a ningun PDF', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: LISTADO() } });
  assert.equal(enlacesDe(t, 0).length, 1);
  assert.equal(t.$('filas').querySelectorAll('a[href*="/pdf"]').length, 2);
});

test('El enlace de PDF no pasa por la region de estado ni dispara pedidos del listado', async () => {
  const t = await arrancarApp({ auto: { obtenerFacturas: LISTADO() } });
  const pedidos = t.servicio.llamadas.length;
  enlacesDe(t, 1)[1].addEventListener('click', (e) => e.preventDefault());
  enlacesDe(t, 1)[1].click();
  await esperarTareas();
  assert.equal(t.servicio.llamadas.length, pedidos);
  assert.equal(t.$('estado').textContent, '');
});

// ---- Etapa 6, ahora al volver al listado desde el detalle: la recarga conserva la pagina vigente ----

test('Emision en la pagina 2 (indice 1): al volver al listado se pide de nuevo con los mismos filtros y pagina=1', async () => {
  const t = await arrancarApp({
    auto: {
      cambiarEstado: fila({ id: 16, numeroFactura: 4 }),
      obtenerFactura: borradorDetallado({ id: 16 }),
      obtenerFacturas: (_filtros, solicitud) => pagina(solicitud.pagina === 0 ? LISTADO() : [fila({ id: 16, estado: 'BORRADOR', numeroFactura: null })],
        { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: 11, totalPaginas: 2 }),
    },
  });
  t.$('filtro-estado').value = 'BORRADOR';
  t.$('form-filtros').requestSubmit();
  await esperarTareas();
  t.$('pagina-siguiente').click();
  await esperarTareas();
  await activar(t, enlacesDe(t, 0)[0]);
  t.$('detalle-acciones').querySelector('button[data-accion="emitir"]').click();
  await esperarTareas();
  assert.equal(t.servicio.de('cambiarEstado').length, 1);
  const antes = t.servicio.de('obtenerFacturas').length;
  await activar(t, [...t.$('detalle-acciones').querySelectorAll('a')].find((a) => a.textContent === 'Volver al listado'));
  assert.equal(t.servicio.de('obtenerFacturas').length, antes + 1);
  assert.deepEqual(t.servicio.de('obtenerFacturas').at(-1).args, [{ estado: 'BORRADOR' }, { pagina: 1, tamanio: 10 }]);
});

test('Se elimina el unico elemento de la ultima pagina: se pide pagina=2, llega vacia con 2 paginas y se pide pagina=1 una sola vez', async () => {
  let eliminado = false;
  const t = await arrancarApp({
    auto: {
      eliminarFactura: undefined,
      obtenerFactura: borradorDetallado({ id: 21 }),
      obtenerFacturas: (_filtros, solicitud) => {
        if (solicitud.pagina === 2) {
          return eliminado
            ? pagina([], { pagina: 2, tamanio: 10, totalElementos: 20, totalPaginas: 2 })
            : pagina([fila({ id: 21, estado: 'BORRADOR', numeroFactura: null })], { pagina: 2, tamanio: 10, totalElementos: 21, totalPaginas: 3 });
        }
        return pagina([fila({ id: 100 + solicitud.pagina })], { pagina: solicitud.pagina, tamanio: solicitud.tamanio, totalElementos: eliminado ? 20 : 21, totalPaginas: eliminado ? 2 : 3 });
      },
    },
  });
  t.$('pagina-siguiente').click();
  await esperarTareas();
  t.$('pagina-siguiente').click();
  await esperarTareas();
  assert.equal(t.$('pagina-indicador').textContent, 'Página 3 de 3');
  await activar(t, enlacesDe(t, 0)[0]);
  const antes = t.servicio.de('obtenerFacturas').length;
  eliminado = true;
  const llegada = esperarHashchange(t.ventana);
  t.$('detalle-acciones').querySelector('button[data-accion="eliminar"]').click();
  await llegada;
  const nuevas = t.servicio.de('obtenerFacturas').slice(antes).map((l) => l.args[1].pagina);
  assert.deepEqual(nuevas, [2, 1]);
  assert.equal(t.$('pagina-indicador').textContent, 'Página 2 de 2');
  assert.equal(t.$('filas').rows.length, 1);
  assert.equal(t.$('contador').textContent, '20 facturas');
});

test('Si la pagina reintentada tambien llega vacia no se pide una tercera vez (sin bucle)', async () => {
  let eliminado = false;
  const t = await arrancarApp({
    auto: {
      eliminarFactura: undefined,
      obtenerFactura: borradorDetallado({ id: 21 }),
      obtenerFacturas: (_filtros, solicitud) => {
        if (!eliminado) {
          return solicitud.pagina === 2
            ? pagina([fila({ id: 21, estado: 'BORRADOR', numeroFactura: null })], { pagina: 2, tamanio: 10, totalElementos: 21, totalPaginas: 3 })
            : pagina([fila({ id: 100 + solicitud.pagina })], { pagina: solicitud.pagina, tamanio: 10, totalElementos: 21, totalPaginas: 3 });
        }
        // Otro usuario siguió borrando: cada página pedida llega vacía y el total sigue achicándose.
        return pagina([], { pagina: solicitud.pagina, tamanio: 10, totalElementos: solicitud.pagina * 10, totalPaginas: solicitud.pagina });
      },
    },
  });
  t.$('pagina-siguiente').click();
  await esperarTareas();
  t.$('pagina-siguiente').click();
  await esperarTareas();
  await activar(t, enlacesDe(t, 0)[0]);
  const antes = t.servicio.de('obtenerFacturas').length;
  eliminado = true;
  const llegada = esperarHashchange(t.ventana);
  t.$('detalle-acciones').querySelector('button[data-accion="eliminar"]').click();
  await llegada;
  assert.deepEqual(t.servicio.de('obtenerFacturas').slice(antes).map((l) => l.args[1].pagina), [2, 1]);
});
