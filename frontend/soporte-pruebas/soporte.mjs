// Soporte de pruebas: fuera de test/ y sin sufijo .test para que `node --test` no lo ejecute.
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { FacturaService } from '../dist/services/FacturaService.js';

/** Promesa con resolve/reject externos. */
export function diferido() {
  let resolver;
  let rechazar;
  const promesa = new Promise((res, rej) => {
    resolver = res;
    rechazar = rej;
  });
  return { promesa, resolver, rechazar };
}

/** Vacia las microtareas pendientes tras resolver un diferido. */
export async function esperarTareas() {
  for (let i = 0; i < 3; i += 1) {
    await new Promise((r) => setImmediate(r));
  }
}

/** Temporizador manual: el reloj solo avanza cuando la prueba lo pide. */
export function temporizadorManual() {
  let ahora = 0;
  let siguiente = 1;
  const pendientes = new Map();
  return {
    programar(fn, ms) {
      const id = siguiente++;
      pendientes.set(id, { fn, vence: ahora + ms });
      return () => pendientes.delete(id);
    },
    avanzar(ms) {
      ahora += ms;
      for (const [id, t] of [...pendientes]) {
        if (t.vence <= ahora) {
          pendientes.delete(id);
          t.fn();
        }
      }
    },
    cantidadPendientes: () => pendientes.size,
  };
}

/** Response real (global en Node 24) con cuerpo JSON o de texto. */
export function respuesta(status, cuerpo, tipo = 'application/json') {
  const texto = cuerpo === undefined || cuerpo === null ? null : typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo);
  return new Response(status === 204 ? null : texto, { status, headers: texto === null ? {} : { 'Content-Type': tipo } });
}

/** fetch falso: registra las llamadas y responde con lo que devuelva `responder(url, init)`. */
export function fetchFalso(responder = () => respuesta(200, [])) {
  const llamadas = [];
  const fn = async (url, init) => {
    llamadas.push({ url, init });
    return responder(url, init);
  };
  fn.llamadas = llamadas;
  return fn;
}

const htmlIndex = readFileSync(new URL('../index.html', import.meta.url), 'utf8');

/** Pagina real (index.html) en jsdom, sin ejecutar scripts; `url` fija la direccion inicial (con su fragmento). */
export function montarPagina(url = 'http://localhost:5173/') {
  const dom = new JSDOM(htmlIndex, { url });
  return { dom, documento: dom.window.document, ventana: dom.window };
}

const METODOS = ['obtenerFacturas', 'obtenerFactura', 'obtenerCatalogos', 'buscarClientes', 'buscarListaPrecioArticulos', 'crearFactura', 'actualizarFactura', 'cambiarEstado', 'eliminarFactura'];

/**
 * Doble de ApiFacturas. Cada llamada se registra en `llamadas`; si `auto` trae el metodo, responde al instante
 * (valor, funcion de los argumentos, o Error para rechazar); si no, devuelve un diferido que la prueba resuelve
 * con `llamada.resolver(valor)` / `llamada.rechazar(error)`.
 */
export function servicioFalso(auto = {}, base = 'http://localhost:8080/api') {
  const llamadas = [];
  const real = new FacturaService(base, async () => new Response('[]'));
  const servicio = {
    llamadas,
    de: (metodo) => llamadas.filter((l) => l.metodo === metodo),
    urlReporte: (filtros, formato) => real.urlReporte(filtros, formato),
    urlPdfFactura: (id) => real.urlPdfFactura(id),
  };
  for (const metodo of METODOS) {
    servicio[metodo] = (...args) => {
      const d = diferido();
      llamadas.push({ metodo, args, resolver: d.resolver, rechazar: d.rechazar });
      if (metodo in auto) {
        let valor = typeof auto[metodo] === 'function' ? auto[metodo](...args) : auto[metodo];
        // Compatibilidad: un arreglo para obtenerFacturas se entrega como la unica pagina del resultado.
        if (metodo === 'obtenerFacturas' && Array.isArray(valor)) valor = pagina(valor, { tamanio: args[1]?.tamanio ?? 10 });
        if (valor instanceof Error) d.rechazar(valor);
        else d.resolver(valor);
      }
      return d.promesa;
    };
  }
  return servicio;
}

/** Sobre paginado del listado: por defecto una sola pagina con todos los items. */
export function pagina(items, { pagina: numero = 0, tamanio = 10, totalElementos = items.length, totalPaginas = Math.ceil(totalElementos / tamanio) } = {}) {
  return { items, pagina: numero, tamanio, totalElementos, totalPaginas };
}

export const catalogosEjemplo = () => ({
  puntosVenta: [{ id: 1, etiqueta: '0001 - Casa Central', valor: null, codigoMoneda: null }],
  clientes: [
    { id: 1, etiqueta: 'Juan Servicios', valor: null, codigoMoneda: null },
    { id: 2, etiqueta: 'Ana SA', valor: null, codigoMoneda: null },
  ],
  condicionesIva: [{ id: 2, etiqueta: 'Responsable Inscripto', valor: null, codigoMoneda: null }],
  tiposMoneda: [
    { id: 1, etiqueta: 'Pesos', valor: null, codigoMoneda: 'PES' },
    { id: 2, etiqueta: 'Dólares', valor: null, codigoMoneda: 'DOL' },
  ],
  listaPrecioArticulos: [
    { id: 1, etiqueta: 'Martillo', valor: 3000, codigoMoneda: 'PES' },
    { id: 3, etiqueta: 'Cable HDMI', valor: 8000, codigoMoneda: 'PES' },
  ],
});

export const fila = (extra = {}) => ({
  id: 1,
  numeroFactura: 3,
  fechaEmision: '2026-11-01',
  clienteDenominacion: 'Juan Servicios',
  condicionIva: 'Responsable Inscripto',
  puntoVentaDescripcion: '0001 - Casa Central',
  importeTotal: 7260,
  cantidadItems: 2,
  estado: 'EMITIDA',
  codigoMoneda: 'PES',
  ...extra,
});

export const dialogosFalsos = ({ confirmar = true, motivo = '' } = {}) => {
  const d = { confirmaciones: [], motivos: [] };
  d.confirmar = async (m) => {
    d.confirmaciones.push(m);
    return typeof confirmar === 'function' ? confirmar(m) : confirmar;
  };
  d.pedirMotivo = async (m) => {
    d.motivos.push(m);
    return typeof motivo === 'function' ? motivo(m) : motivo;
  };
  return d;
};

/**
 * Doble de VentanaModal: jsdom no implementa showModal()/close(), asi que solo alterna el atributo `open`
 * y registra las aperturas y cierres. Las pruebas simulan Escape disparando el evento `cancel` sobre el dialogo.
 */
export function ventanaModalFalsa() {
  const v = { aperturas: [], cierres: [] };
  v.abrir = (dialogo) => {
    v.aperturas.push(dialogo.id);
    dialogo.setAttribute('open', '');
  };
  v.cerrar = (dialogo) => {
    v.cierres.push(dialogo.id);
    dialogo.removeAttribute('open');
  };
  return v;
}

/**
 * Espera el próximo `hashchange` de la ventana (jsdom lo dispara de forma asincrónica: con asignación, replace,
 * history.back() y clic en un enlace de fragmento) y deja correr las tareas que dispara. Falla si no llega.
 */
export function esperarHashchange(ventana, ms = 1000) {
  return new Promise((resolver, rechazar) => {
    const temporizador = setTimeout(() => rechazar(new Error('No llegó hashchange')), ms);
    ventana.addEventListener(
      'hashchange',
      () => {
        clearTimeout(temporizador);
        esperarTareas().then(resolver);
      },
      { once: true },
    );
  });
}

/** Espera, con sondeo, a que se cumpla una condicion (p. ej. un fragmento que llega tras una navegacion encadenada). */
export async function esperarHasta(condicion, ms = 1000) {
  const limite = Date.now() + ms;
  while (!condicion()) {
    if (Date.now() > limite) throw new Error('No se cumplio la condicion esperada');
    await new Promise((r) => setTimeout(r, 5));
  }
  await esperarTareas();
}

/**
 * Arranca la aplicacion completa sobre index.html en jsdom con dobles (sin red) y espera la carga inicial.
 * `url` fija la direccion de entrada (con su fragmento); `auto` y `dialogos` van al servicio y a los dialogos falsos.
 * `navegar(fragmento)` cambia el fragmento como lo haria el usuario y espera el hashchange.
 */
export async function arrancarApp({ url = 'http://localhost:5173/', auto = {}, dialogos: opcionesDialogos = {}, esperarListo = true } = {}) {
  const { iniciarAplicacion } = await import('../dist/aplicacion.js');
  const pagina = montarPagina(url);
  const dialogos = dialogosFalsos(opcionesDialogos);
  const respuestas = { obtenerCatalogos: catalogosEjemplo(), obtenerFacturas: [], obtenerFactura: facturaDetallada(), ...auto };
  // Un metodo en null queda sin respuesta automatica: la llamada devuelve un diferido que la prueba resuelve.
  for (const [metodo, valor] of Object.entries(respuestas)) if (valor === null) delete respuestas[metodo];
  const servicio = servicioFalso(respuestas);
  const temporizador = temporizadorManual();
  const ventanaModal = ventanaModalFalsa();
  const app = iniciarAplicacion({ documento: pagina.documento, ventana: pagina.ventana, servicio, dialogos, temporizador, ventanaModal });
  // Con la carga de la pantalla de entrada pendiente (diferido sin resolver) no se puede esperar a listo.
  if (esperarListo) await app.listo;
  const $ = (id) => pagina.documento.getElementById(id);
  const visible = (id) => !$(id).hidden;
  const navegar = async (fragmento) => {
    const llegada = esperarHashchange(pagina.ventana);
    pagina.ventana.location.hash = fragmento;
    await llegada;
  };
  return { ...pagina, app, servicio, dialogos, temporizador, ventanaModal, $, visible, navegar };
}

/** Detalle de factura emitida de ejemplo (FacturaDetalladaDTO); extra pisa campos. */
export const facturaDetallada = (extra = {}) => ({
  id: 1,
  numero: 4,
  fechaEmision: '2026-11-01',
  estado: 'EMITIDA',
  clienteId: 2,
  clienteDenominacion: 'Ana SA',
  clienteCuitCuil: '30-71234567-1',
  condicionIvaId: 2,
  condicionIva: 'Responsable Inscripto',
  puntoVentaId: 1,
  puntoVentaNumero: 1,
  puntoVentaDescripcion: '0001 - Casa Central',
  tipoMonedaId: 1,
  codigoMoneda: 'PES',
  importeTotal: 6534,
  importeCobrado: 0,
  importeSaldo: 6534,
  cae: '71234567890123',
  caeFechaVencimiento: '2026-11-11',
  resultadoAfip: 'A',
  fechaAnulacion: null,
  observaciones: null,
  detalles: [
    { id: 1, listaPrecioArticuloId: 1, articuloCodigo: 'MAR', descripcion: 'Martillo', cantidad: 2, precioUnitario: 3000, porcentajeBonificacion: 10, importeNeto: 5400, importeIva: 1134, importeSubtotal: 6534 },
  ],
  ...extra,
});

/** Detalle de un borrador de ejemplo (sin numero ni CAE). */
export const borradorDetallado = (extra = {}) => facturaDetallada({ id: 6, numero: null, estado: 'BORRADOR', cae: null, caeFechaVencimiento: null, resultadoAfip: null, ...extra });
