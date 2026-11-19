import { mensajeDe, type Entorno } from './contexto.js';
import type { OpcionDTO } from './models/factura.dto.js';
import { Enrutador, type Cambio } from './navegacion/enrutador.js';
import type { Ruta } from './navegacion/rutas.js';
import type { Navegador, Pantalla } from './pantallas/pantalla.js';
import { PantallaDetalle } from './pantallas/pantallaDetalle.js';
import { PantallaFormulario } from './pantallas/pantallaFormulario.js';
import { PantallaListado } from './pantallas/pantallaListado.js';
import { Explorador } from './ui/explorador.js';
import { Formulario } from './ui/formulario.js';
import { RegionEstado } from './ui/regionEstado.js';

export type { ApiFacturas, Dialogos, Entorno } from './contexto.js';
export { mensajeDe } from './contexto.js';

export interface Aplicacion {
  /** Resuelve cuando terminó la carga inicial (catálogos y pantalla de entrada). */
  listo: Promise<void>;
}

type RutaConocida = Exclude<Ruta, { tipo: 'desconocida' }>;

const NOMBRE_SISTEMA = 'Facturación ARCA';

export function iniciarAplicacion(entorno: Entorno): Aplicacion {
  const { documento: doc, servicio, temporizador } = entorno;
  const $ = <T extends HTMLElement>(id: string): T => {
    const e = doc.getElementById(id);
    if (!e) throw new Error(`Falta el elemento #${id} en el HTML.`);
    return e as T;
  };

  const region = new RegionEstado($('estado'), temporizador);
  const explorador = new Explorador(doc, entorno.ventanaModal);
  const buscarClientes = (disparador: HTMLElement, titulo: string): Promise<OpcionDTO | null> =>
    explorador.elegir({ titulo, buscar: (q) => servicio.buscarClientes(q) }, disparador);
  const formulario = new Formulario(doc, {
    cliente: (disparador) => buscarClientes(disparador, 'Buscar cliente'),
    articulo: (disparador, renglon) =>
      explorador.elegir(
        { titulo: `Buscar artículo de lista de precios (renglón ${renglon})`, buscar: (q, listaId) => servicio.buscarListaPrecioArticulos(q, listaId), listas: () => servicio.obtenerListasPrecio(), conPrecio: true },
        disparador,
      ),
  });

  const pantallaListado = new PantallaListado(doc, servicio, region, (disparador) => buscarClientes(disparador, 'Buscar cliente'));

  async function cargarCatalogos(): Promise<void> {
    try {
      formulario.cargarCatalogos(await servicio.obtenerCatalogos());
    } catch (e) {
      region.error(mensajeDe(e));
    }
  }

  // --- Cáscara: una pantalla visible a la vez, según la ruta ---
  /** Las pantallas navegan a través del enrutador, que se crea más abajo (el cierre lo resuelve al usarse). */
  const navegador: Navegador = { ir: (ruta, opciones) => enrutador.ir(ruta, opciones) };
  const pantallaFormulario = new PantallaFormulario(doc, servicio, region, formulario, navegador);
  const pantallaDetalle = new PantallaDetalle(doc, servicio, region, entorno.dialogos, navegador);
  const todas: Pantalla[] = [pantallaListado, pantallaFormulario, pantallaDetalle];

  function pantallaDe(ruta: RutaConocida): Pantalla {
    if (ruta.tipo === 'alta' || ruta.tipo === 'edicion') return pantallaFormulario;
    return ruta.tipo === 'detalle' ? pantallaDetalle : pantallaListado;
  }

  const enlaceListado = $('nav-listado');
  const enlaceAlta = $('nav-alta');

  /** aria-current="page" solo en el enlace de la barra que corresponde a la pantalla (ninguno en detalle ni edición). */
  function marcarNavegacion(ruta: RutaConocida): void {
    const marcar = (enlace: HTMLElement, actual: boolean): void => {
      if (actual) enlace.setAttribute('aria-current', 'page');
      else enlace.removeAttribute('aria-current');
    };
    marcar(enlaceListado, ruta.tipo === 'listado');
    marcar(enlaceAlta, ruta.tipo === 'alta');
  }

  let actual: Pantalla | null = null;

  async function alCambiar(cambio: Cambio): Promise<void> {
    // Una ruta desconocida se resuelve como listado y se explica en la región de estado.
    const ruta: RutaConocida = cambio.ruta.tipo === 'desconocida' ? { tipo: 'listado' } : cambio.ruta;
    const pantalla = pantallaDe(ruta);
    // El explorador es compartido: salir de la pantalla lo cierra (resuelve null) antes de mostrar la nueva.
    explorador.cerrar();
    actual?.salir();
    actual = pantalla;
    for (const p of todas) p.seccion.hidden = p !== pantalla;
    marcarNavegacion(ruta);
    doc.title = `${pantalla.tituloDocumento(ruta)} — ${NOMBRE_SISTEMA}`;
    // El arranque no mueve el foco (el lector empieza por el principio del documento); cada navegación, sí.
    if (!cambio.inicial) pantalla.titulo.focus();
    // El mensaje se escribe después de mover el foco (aria-live espera a que termine la lectura del h1).
    if (cambio.mensaje !== null) {
      if (cambio.mensaje.tipo === 'exito') region.exito(cambio.mensaje.texto);
      else region.error(cambio.mensaje.texto);
    } else if (cambio.ruta.tipo === 'desconocida') {
      region.error(`La dirección ${cambio.ruta.fragmento} no existe; se muestra el listado.`);
    } else if (!cambio.inicial) {
      region.limpiar();
    }
    await pantalla.entrar(ruta);
  }

  const enrutador = new Enrutador(entorno.ventana, alCambiar);
  const listo = Promise.all([cargarCatalogos(), enrutador.iniciar()]).then(() => undefined);
  return { listo };
}
