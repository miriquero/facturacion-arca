import assert from 'node:assert/strict';
import { test } from 'node:test';
import { FacturaService, consultaFiltros } from '../dist/services/FacturaService.js';
import { ApiError } from '../dist/services/ApiError.js';
import { fetchFalso, respuesta } from '../soporte-pruebas/soporte.mjs';

test('Base configurada: el listado se pide a la URL base del meta', async () => {
  const f = fetchFalso();
  await new FacturaService('http://127.0.0.1:9090/api', f).obtenerFacturas({});
  assert.equal(f.llamadas.length, 1);
  assert.equal(f.llamadas[0].url, 'http://127.0.0.1:9090/api/facturas');
});

test('Base con barra final: sin doble barra', async () => {
  const f = fetchFalso();
  await new FacturaService('http://localhost:8080/api/', f).obtenerFacturas({});
  assert.equal(f.llamadas[0].url, 'http://localhost:8080/api/facturas');
});

test('Encabezados: X-Usuario en lectura y en DELETE; sin Content-Type sin cuerpo', async () => {
  const f = fetchFalso(() => respuesta(200, {}));
  const s = new FacturaService('http://x/api', f);
  await s.obtenerFactura(7);
  await s.eliminarFactura(6);
  for (const llamada of f.llamadas) {
    assert.deepEqual(Object.keys(llamada.init.headers), ['X-Usuario']);
    assert.equal(llamada.init.headers['X-Usuario'], 'sistema');
    assert.equal(llamada.init.body, undefined);
  }
  assert.equal(f.llamadas[0].url, 'http://x/api/facturas/7');
  assert.equal(f.llamadas[0].init.method, 'GET');
});

test('Encabezados: Content-Type application/json solo con cuerpo (alta)', async () => {
  const f = fetchFalso(() => respuesta(201, { id: 15 }));
  const req = { condicionIvaId: 2, puntoVentaId: 1, tipoMonedaId: 1, detalles: [{ listaPrecioArticuloId: 1, cantidad: 1 }] };
  await new FacturaService('http://x/api', f).crearFactura(req);
  const { url, init } = f.llamadas[0];
  assert.equal(url, 'http://x/api/facturas');
  assert.equal(init.method, 'POST');
  assert.deepEqual(init.headers, { 'X-Usuario': 'sistema', 'Content-Type': 'application/json' });
  assert.equal(init.body, JSON.stringify(req));
});

test('consultaFiltros: sin filtros no hay "?"', async () => {
  const f = fetchFalso();
  await new FacturaService('http://x/api', f).obtenerFacturas({});
  assert.equal(f.llamadas[0].url, 'http://x/api/facturas');
});

test('consultaFiltros: filtros combinados, montoMinimo=0 presente y sin fechaHasta', async () => {
  const f = fetchFalso();
  await new FacturaService('http://x/api', f).obtenerFacturas({ fechaDesde: '2026-11-01', estado: 'EMITIDA', montoMinimo: 0 });
  assert.equal(f.llamadas[0].url, 'http://x/api/facturas?fechaDesde=2026-11-01&estado=EMITIDA&montoMinimo=0');
  assert.ok(!f.llamadas[0].url.includes('fechaHasta'));
});

test('consultaFiltros: cadena vacia omitida', async () => {
  const f = fetchFalso();
  await new FacturaService('http://x/api', f).obtenerFacturas({ fechaDesde: '', fechaHasta: '2026-12-31', estado: '' });
  assert.equal(f.llamadas[0].url, 'http://x/api/facturas?fechaHasta=2026-12-31');
});

const REQ = { condicionIvaId: 2, puntoVentaId: 1, tipoMonedaId: 1, detalles: [{ listaPrecioArticuloId: 1, cantidad: 1 }] };

test('operaciones: actualizarFactura(5) es PUT /facturas/5 con el request completo', async () => {
  const f = fetchFalso(() => respuesta(200, { id: 5 }));
  const fila = await new FacturaService('http://x/api', f).actualizarFactura(5, REQ);
  assert.equal(f.llamadas[0].url, 'http://x/api/facturas/5');
  assert.equal(f.llamadas[0].init.method, 'PUT');
  assert.equal(f.llamadas[0].init.body, JSON.stringify(REQ));
  assert.deepEqual(fila, { id: 5 });
});

test('operaciones: cambiarEstado anula con motivo (cuerpo exacto)', async () => {
  const f = fetchFalso(() => respuesta(200, { id: 12 }));
  await new FacturaService('http://x/api', f).cambiarEstado(12, 'ANULADA', 'Error en el cliente');
  assert.equal(f.llamadas[0].url, 'http://x/api/facturas/12/estado');
  assert.equal(f.llamadas[0].init.method, 'PATCH');
  assert.equal(f.llamadas[0].init.body, '{"estado":"ANULADA","observaciones":"Error en el cliente"}');
});

test('operaciones: cambiarEstado sin motivo no incluye observaciones (ni con blancos)', async () => {
  const f = fetchFalso(() => respuesta(200, { id: 12 }));
  const s = new FacturaService('http://x/api', f);
  await s.cambiarEstado(12, 'EMITIDA');
  await s.cambiarEstado(12, 'ANULADA', '   ');
  assert.equal(f.llamadas[0].init.body, '{"estado":"EMITIDA"}');
  assert.equal(f.llamadas[1].init.body, '{"estado":"ANULADA"}');
});

test('operaciones: eliminarFactura(6) es DELETE /facturas/6', async () => {
  const f = fetchFalso(() => respuesta(200, {}));
  await new FacturaService('http://x/api', f).eliminarFactura(6);
  assert.equal(f.llamadas[0].url, 'http://x/api/facturas/6');
  assert.equal(f.llamadas[0].init.method, 'DELETE');
});

test('operaciones: obtenerCatalogos pide las cinco rutas y devuelve el resultado por nombre', async () => {
  const f = fetchFalso((url) => respuesta(200, [{ id: 1, etiqueta: url.split('/').pop(), valor: null, codigoMoneda: null }]));
  const c = await new FacturaService('http://x/api', f).obtenerCatalogos();
  assert.deepEqual(f.llamadas.map((l) => l.url).sort(), [
    'http://x/api/catalogos/clientes',
    'http://x/api/catalogos/condiciones-iva',
    'http://x/api/catalogos/lista-precio-articulos',
    'http://x/api/catalogos/puntos-venta',
    'http://x/api/catalogos/tipos-moneda',
  ]);
  assert.equal(c.puntosVenta[0].etiqueta, 'puntos-venta');
  assert.equal(c.clientes[0].etiqueta, 'clientes');
  assert.equal(c.condicionesIva[0].etiqueta, 'condiciones-iva');
  assert.equal(c.tiposMoneda[0].etiqueta, 'tipos-moneda');
  assert.equal(c.listaPrecioArticulos[0].etiqueta, 'lista-precio-articulos');
});

test('urlReporte: xlsx con estado=EMITIDA', () => {
  const s = new FacturaService('http://x/api/', fetchFalso());
  assert.equal(s.urlReporte({ estado: 'EMITIDA' }, 'xlsx'), 'http://x/api/facturas/reporte?formato=xlsx&estado=EMITIDA');
});

test('urlReporte: txt sin filtros lleva solo formato=txt', () => {
  const s = new FacturaService('http://x/api', fetchFalso());
  assert.equal(s.urlReporte({}, 'txt'), 'http://x/api/facturas/reporte?formato=txt');
});

test('urlReporte: pdf usa los mismos parametros que consultaFiltros', () => {
  const s = new FacturaService('http://x/api', fetchFalso());
  const filtros = { fechaDesde: '2026-11-01', fechaHasta: '2026-12-31', estado: 'ANULADA', montoMinimo: 10 };
  const url = new URL(s.urlReporte(filtros, 'pdf'));
  const esperado = new URLSearchParams(consultaFiltros(filtros));
  esperado.set('formato', 'pdf');
  assert.deepEqual([...url.searchParams].sort(), [...esperado].sort());
});

test('204: se resuelve en undefined sin leer el cuerpo', async () => {
  let lecturas = 0;
  const r = respuesta(204);
  r.json = async () => { lecturas++; throw new Error('no debe leerse'); };
  r.text = async () => { lecturas++; throw new Error('no debe leerse'); };
  const f = fetchFalso(() => r);
  const res = await new FacturaService('http://x/api', f).eliminarFactura(6);
  assert.equal(res, undefined);
  assert.equal(lecturas, 0);
});

const servicioCon = (status, cuerpo, tipo) => new FacturaService('http://x/api', fetchFalso(() => respuesta(status, cuerpo, tipo)));
const rechazo = async (p) => { try { await p; } catch (e) { return e; } assert.fail('debia rechazar'); };

test('ApiError: 409 con detail usa el detail como mensaje', async () => {
  const e = await rechazo(servicioCon(409, { status: 409, title: 'Conflicto con el estado de la factura', detail: 'La factura está EMITIDA' }).obtenerFactura(1));
  assert.ok(e instanceof ApiError);
  assert.equal(e.status, 409);
  assert.equal(e.message, 'La factura está EMITIDA');
});

test('ApiError: 400 conserva las dos claves de errores', async () => {
  const errores = { tipoMonedaId: 'es obligatorio', 'detalles[0].cantidad': 'debe ser mayor que 0' };
  const e = await rechazo(servicioCon(400, { status: 400, detail: 'Datos inválidos', errores }).crearFactura(REQ));
  assert.equal(e.status, 400);
  assert.deepEqual(e.errores, errores);
});

test('ApiError: 502 con HTML da mensaje de respaldo con 502 y sin errores', async () => {
  const e = await rechazo(servicioCon(502, '<html>Bad Gateway</html>', 'text/html').obtenerFactura(1));
  assert.equal(e.status, 502);
  assert.match(e.message, /502/);
  assert.deepEqual(e.errores, {});
});

test('ApiError: 500 vacio da mensaje de respaldo', async () => {
  const e = await rechazo(servicioCon(500, '').obtenerFactura(1));
  assert.equal(e.status, 500);
  assert.equal(e.message, 'El servidor respondió 500 sin un detalle legible.');
});

test('ApiError: JSON sin detail con title usa el title', async () => {
  const e = await rechazo(servicioCon(404, { title: 'Recurso no encontrado' }).obtenerFactura(1));
  assert.equal(e.message, 'Recurso no encontrado');
  assert.equal(e.titulo, 'Recurso no encontrado');
});

test('ApiError: errores con un valor no texto se descarta', async () => {
  const e = await rechazo(servicioCon(400, { detail: 'x', errores: { a: 'ok', b: 5 } }).crearFactura(REQ));
  assert.deepEqual(e.errores, {});
});

test('ApiError: cuerpo JSON que no es objeto se trata como ilegible', async () => {
  const e = await rechazo(servicioCon(500, '[1,2]').obtenerFactura(1));
  assert.match(e.message, /500/);
});

test('falla de red: ApiError estado 0 con la URL base en el mensaje', async () => {
  const f = async () => { throw new TypeError('Failed to fetch'); };
  const e = await rechazo(new FacturaService('http://127.0.0.1:9/api', f).obtenerFacturas({}));
  assert.ok(e instanceof ApiError);
  assert.equal(e.status, 0);
  assert.match(e.message, /http:\/\/127\.0\.0\.1:9\/api/);
});

test('Request sin precios ni importes en el JSON enviado', async () => {
  const f = fetchFalso(() => respuesta(201, { id: 1 }));
  const origen = { ...REQ, detalles: [
    { listaPrecioArticuloId: 1, cantidad: 2, porcentajeBonificacion: 10, precioUnitario: 3000, importeNeto: 5400 },
    { listaPrecioArticuloId: 3, cantidad: 1, importeTotal: 9680, importeIva: 1, importeSubtotal: 2 },
  ], importeTotal: 1, importeNeto: 2 };
  await new FacturaService('http://x/api', f).crearFactura(origen);
  const enviado = f.llamadas[0].init.body;
  for (const clave of ['precioUnitario', 'importeNeto', 'importeIva', 'importeSubtotal', 'importeTotal']) {
    assert.ok(!enviado.includes(clave), `no debe enviarse ${clave}`);
  }
  assert.ok(enviado.includes('listaPrecioArticuloId'));
});
