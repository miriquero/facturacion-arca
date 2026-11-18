import assert from 'node:assert/strict';
import { test } from 'node:test';
import { iniciarAplicacion } from '../dist/aplicacion.js';
import { ApiError } from '../dist/services/ApiError.js';
import { catalogosEjemplo, dialogosFalsos, esperarHashchange, esperarHasta, esperarTareas, fila, montarPagina, servicioFalso, temporizadorManual, ventanaModalFalsa } from '../soporte-pruebas/soporte.mjs';

const detalle = (id, extra = {}) => ({
  id, numero: null, fechaEmision: '2026-11-03', estado: 'BORRADOR', clienteId: 2, clienteDenominacion: 'Ana SA',
  condicionIvaId: 2, condicionIva: 'Responsable Inscripto', puntoVentaId: 1, puntoVentaNumero: 1, puntoVentaDescripcion: '0001 - Casa Central',
  tipoMonedaId: 1, codigoMoneda: 'PES', importeTotal: 0, importeCobrado: 0, importeSaldo: 0, cae: null, caeFechaVencimiento: null,
  resultadoAfip: null, fechaAnulacion: null, observaciones: 'Entrega en sucursal',
  detalles: [
    { id: 10, listaPrecioArticuloId: 1, articuloCodigo: 'MAR', descripcion: 'Martillo grande', cantidad: 2, precioUnitario: 3000, porcentajeBonificacion: 10, importeNeto: 5400, importeIva: 0, importeSubtotal: 0 },
    { id: 11, listaPrecioArticuloId: 3, articuloCodigo: 'HDMI', descripcion: null, cantidad: 1, precioUnitario: 8000, porcentajeBonificacion: 0, importeNeto: 8000, importeIva: 0, importeSubtotal: 0 },
  ],
  ...extra,
});

async function arrancar(auto = {}) {
  const pagina = montarPagina();
  const servicio = servicioFalso({
    obtenerCatalogos: catalogosEjemplo(),
    obtenerFacturas: [fila({ id: 5, estado: 'BORRADOR', numeroFactura: null }), fila({ id: 6, estado: 'BORRADOR', numeroFactura: null })],
    ...auto,
  });
  const app = iniciarAplicacion({ documento: pagina.documento, ventana: pagina.ventana, servicio, dialogos: dialogosFalsos(), temporizador: temporizadorManual(), ventanaModal: ventanaModalFalsa() });
  await app.listo;
  const $ = (id) => pagina.documento.getElementById(id);
  const filas = () => [...$('renglones').querySelectorAll('fieldset.renglon')];
  const campo = (i, nombre) => filas()[i].querySelector(`[name="${nombre}"]`);
  // Etapa 7: editar es navegar a la ruta de edicion (antes se hacia clic en el boton de la fila).
  const editar = async (id) => {
    const llegada = esperarHashchange(pagina.ventana);
    pagina.ventana.location.hash = `#/factura/${id}/editar`;
    await llegada;
  };
  const poner = (el, valor) => {
    el.value = valor;
    el.dispatchEvent(new pagina.ventana.Event('input', { bubbles: true }));
  };
  return { ...pagina, servicio, $, filas, campo, editar, poner };
}

test('Borrador con dos renglones: cabecera, renglones, titulo y foco en el titulo', async () => {
  const t = await arrancar({ obtenerFactura: detalle(5) });
  await t.editar(5);
  assert.deepEqual(t.servicio.de('obtenerFactura')[0].args, [5]);
  assert.equal(t.$('cliente').getAttribute('data-id'), '2');
  assert.equal(t.$('cliente').textContent, 'Ana SA');
  assert.equal(t.$('tipo-moneda').value, '1');
  assert.equal(t.$('fecha-emision').value, '2026-11-03');
  assert.equal(t.$('observaciones').value, 'Entrega en sucursal');
  assert.equal(t.filas().length, 2);
  assert.deepEqual([0, 1].map((i) => [t.campo(i, 'listaPrecioArticuloId').getAttribute('data-id'), t.campo(i, 'listaPrecioArticuloId').textContent, t.campo(i, 'cantidad').value, t.campo(i, 'porcentajeBonificacion').value, t.campo(i, 'descripcion').value]),
    [['1', 'Martillo', '2', '10', 'Martillo grande'], ['3', 'Cable HDMI', '1', '0', '']]);
  assert.equal(t.$('titulo-formulario').textContent, 'Editar borrador 5');
  assert.equal(t.documento.activeElement, t.$('titulo-formulario'));
  assert.equal(t.$('cancelar-edicion').hidden, false);
  assert.equal(t.$('guardar').textContent, 'Guardar cambios');
  assert.equal(t.$('total-estimado').textContent, '$ 13.400,00');
});

test('Ya fue emitido: se muestra el detalle 5 con el error, sin entrada de historial para la edicion imposible', async () => {
  const t = await arrancar({ obtenerFactura: detalle(5, { estado: 'EMITIDA' }) });
  const antes = t.ventana.history.length;
  await t.editar(5);
  await esperarHasta(() => !t.$('pantalla-detalle').hidden);
  assert.equal(t.ventana.location.hash, '#/factura/5');
  assert.equal(t.ventana.history.length, antes + 1);
  assert.equal(t.$('pantalla-detalle').hidden, false);
  assert.match(t.$('estado').textContent, /Error: .*5.*EMITIDA/);
  assert.equal(t.$('titulo-formulario').textContent, 'Nuevo borrador');
  // Etapa 7: Cancelar existe en ambos modos (antes estaba oculto fuera de la edicion).
  assert.equal(t.$('cancelar-edicion').hidden, false);
  assert.equal(t.campo(0, 'cantidad').value, '');
});

test('Edicion exitosa: PUT con la fecha cargada reenviada, detalle 5 con el anuncio y formulario vacio', async () => {
  const t = await arrancar({ obtenerFactura: detalle(5), actualizarFactura: fila({ id: 5, estado: 'BORRADOR' }) });
  await t.editar(5);
  t.poner(t.campo(0, 'cantidad'), '4');
  const llegada = esperarHashchange(t.ventana);
  t.$('form-factura').requestSubmit();
  await llegada;
  assert.deepEqual(t.servicio.de('actualizarFactura')[0].args, [5, {
    clienteId: 2, condicionIvaId: 2, puntoVentaId: 1, tipoMonedaId: 1, fechaEmision: '2026-11-03', observaciones: 'Entrega en sucursal',
    detalles: [
      { listaPrecioArticuloId: 1, cantidad: 4, porcentajeBonificacion: 10, descripcion: 'Martillo grande' },
      { listaPrecioArticuloId: 3, cantidad: 1, porcentajeBonificacion: 0 },
    ],
  }]);
  assert.match(t.$('estado').textContent, /actualiz.*5/);
  assert.equal(t.ventana.location.hash, '#/factura/5');
  assert.equal(t.$('pantalla-detalle').hidden, false);
  assert.equal(t.servicio.de('obtenerFactura').length, 2);
  assert.equal(t.$('titulo-formulario').textContent, 'Nuevo borrador');
  assert.equal(t.filas().length, 1);
  assert.equal(t.campo(0, 'cantidad').value, '');
});

test('Conflicto al editar: 409 conserva valores y modo edicion y muestra el detalle', async () => {
  const t = await arrancar({ obtenerFactura: detalle(5), actualizarFactura: new ApiError(409, 'La factura 5 ya no es un borrador') });
  await t.editar(5);
  t.$('form-factura').requestSubmit();
  await esperarTareas();
  assert.match(t.$('estado').textContent, /La factura 5 ya no es un borrador/);
  assert.equal(t.$('titulo-formulario').textContent, 'Editar borrador 5');
  assert.equal(t.filas().length, 2);
  assert.equal(t.campo(0, 'cantidad').value, '2');
  assert.equal(t.$('cliente').getAttribute('data-id'), '2');
});

test('Cancelar sin guardar: se muestra el detalle de la factura 5 y no se envio ningun PUT', async () => {
  const t = await arrancar({ obtenerFactura: detalle(5) });
  await t.editar(5);
  t.poner(t.campo(0, 'cantidad'), '9');
  const llegada = esperarHashchange(t.ventana);
  t.$('cancelar-edicion').click();
  await llegada;
  assert.equal(t.ventana.location.hash, '#/factura/5');
  assert.equal(t.$('pantalla-detalle').hidden, false);
  assert.equal(t.servicio.de('actualizarFactura').length, 0);
});

test('Cancelacion antes de la respuesta: sigue el detalle 5 y el formulario no queda en modo edicion', async () => {
  const t = await arrancar();
  await t.editar(5);
  const llegada = esperarHashchange(t.ventana);
  t.$('cancelar-edicion').click();
  await llegada;
  t.servicio.de('obtenerFactura')[0].resolver(detalle(5));
  await esperarTareas();
  assert.equal(t.$('pantalla-detalle').hidden, false);
  assert.equal(t.$('titulo-formulario').textContent, 'Nuevo borrador');
  assert.equal(t.campo(0, 'cantidad').value, '');
});

test('Otro borrador elegido despues: queda el 6 aunque el 5 llegue despues', async () => {
  const t = await arrancar();
  await t.editar(5);
  await t.editar(6);
  const [p5, p6] = t.servicio.de('obtenerFactura');
  p6.resolver(detalle(6, { observaciones: 'Del seis' }));
  await esperarTareas();
  p5.resolver(detalle(5, { observaciones: 'Del cinco' }));
  await esperarTareas();
  assert.equal(t.$('titulo-formulario').textContent, 'Editar borrador 6');
  assert.equal(t.$('observaciones').value, 'Del seis');
});

test('Error al cargar el detalle: lleva al listado con el error; uno tardio de una carga vieja no se muestra', async () => {
  const t = await arrancar();
  await t.editar(5);
  await t.editar(6);
  const [p5, p6] = t.servicio.de('obtenerFactura');
  p5.rechazar(new ApiError(404, 'La factura 5 no existe'));
  await esperarTareas();
  assert.equal(t.$('estado').textContent, '');
  const llegada = esperarHashchange(t.ventana);
  p6.rechazar(new ApiError(404, 'La factura 6 no existe'));
  await llegada;
  assert.match(t.$('estado').textContent, /La factura 6 no existe/);
  assert.equal(t.ventana.location.hash, '#/');
  assert.equal(t.$('pantalla-listado').hidden, false);
  assert.equal(t.$('titulo-formulario').textContent, 'Nuevo borrador');
});

// ---- Etapa 6: etiquetas del cliente y de cada opcion al editar ----

test('Editar con catalogos que no traen al cliente ni al articulo: respaldo con los datos del detalle', async () => {
  const cat = catalogosEjemplo();
  cat.clientes = [];
  cat.listaPrecioArticulos = [];
  const t = await arrancar({ obtenerCatalogos: cat, obtenerFactura: detalle(5, { clienteCuitCuil: '20-12345678-9' }) });
  await t.editar(5);
  assert.equal(t.$('cliente').textContent, 'Ana SA (20-12345678-9)');
  assert.equal(t.$('cliente').getAttribute('data-id'), '2');
  assert.equal(t.campo(0, 'listaPrecioArticuloId').textContent, 'MAR - Martillo grande');
  assert.equal(t.campo(1, 'listaPrecioArticuloId').textContent, 'HDMI');
  assert.equal(t.campo(1, 'listaPrecioArticuloId').getAttribute('data-id'), '3');
  assert.equal(t.filas()[1].querySelector('output[name="precio"]').textContent, '8.000,00');
});

test('Editar un borrador sin cliente: Consumidor Final y sin id', async () => {
  const t = await arrancar({ obtenerFactura: detalle(5, { clienteId: null, clienteDenominacion: 'Consumidor Final', clienteCuitCuil: null }) });
  await t.editar(5);
  assert.equal(t.$('cliente').textContent, 'Consumidor Final');
  assert.equal(t.$('cliente').hasAttribute('data-id'), false);
});

test('Editar y guardar sin tocar el cliente ni los articulos: el PUT conserva sus ids', async () => {
  const t = await arrancar({ obtenerFactura: detalle(5), actualizarFactura: fila({ id: 5, estado: 'BORRADOR' }) });
  await t.editar(5);
  t.$('form-factura').requestSubmit();
  await esperarTareas();
  const req = t.servicio.de('actualizarFactura')[0].args[1];
  assert.equal(req.clienteId, 2);
  assert.deepEqual(req.detalles.map((d) => d.listaPrecioArticuloId), [1, 3]);
});
