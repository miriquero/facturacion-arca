import type { OpcionDTO } from '../models/factura.dto.js';
import { ApiError } from '../services/ApiError.js';
import { crear, reemplazarHijos } from './dom.js';
import { formatearDecimal, formatearImporte } from './formato.js';
import { ControlSolicitudes } from './solicitudes.js';

/** Abre y cierra un `<dialog>` como modal. En producción usa showModal()/close(); en las pruebas un doble. */
export interface VentanaModal {
  abrir(dialogo: HTMLDialogElement): void;
  cerrar(dialogo: HTMLDialogElement): void;
}

export interface ConfigExplorador {
  titulo: string;
  /** Pide las opciones al servidor; el texto vacío trae el catálogo completo. */
  buscar(q: string): Promise<OpcionDTO[]>;
  /** Muestra el precio de cada opción junto a su etiqueta (artículos). */
  conPrecio?: boolean;
}

/**
 * Ventana exploradora para elegir una opción de un catálogo (clientes, artículos de lista de precios).
 * Usa el único `<dialog id="explorador">` del HTML, así que solo puede haber uno abierto a la vez.
 * Todo texto del servidor entra por textContent.
 */
export class Explorador {
  private readonly dialogo: HTMLDialogElement;
  private readonly titulo: HTMLElement;
  private readonly campo: HTMLInputElement;
  private readonly cantidad: HTMLElement;
  private readonly error: HTMLElement;
  private readonly vacio: HTMLElement;
  private readonly lista: HTMLElement;
  private readonly busquedas = new ControlSolicitudes();
  private config: ConfigExplorador | null = null;
  private disparador: HTMLElement | null = null;
  private resolver: ((opcion: OpcionDTO | null) => void) | null = null;

  constructor(private readonly doc: Document, private readonly ventana: VentanaModal) {
    const e = <T extends HTMLElement>(id: string): T => {
      const el = doc.getElementById(id);
      if (!el) throw new Error(`Falta el elemento #${id} en el HTML.`);
      return el as T;
    };
    this.dialogo = e<HTMLDialogElement>('explorador');
    this.titulo = e('explorador-titulo');
    this.campo = e<HTMLInputElement>('explorador-q');
    this.cantidad = e('explorador-cantidad');
    this.error = e('explorador-error');
    this.vacio = e('explorador-vacio');
    this.lista = e('explorador-resultados');

    e('explorador-form').addEventListener('submit', (evento) => {
      evento.preventDefault();
      void this.buscar(this.campo.value);
    });
    e('explorador-cancelar').addEventListener('click', () => this.terminar(null));
    // Escape: el navegador dispara `cancel` y cierra solo; se cancela ese cierre para cerrar siempre por código
    // (así se resuelve la promesa y el foco vuelve al botón que abrió la ventana).
    this.dialogo.addEventListener('cancel', (evento) => {
      evento.preventDefault();
      this.terminar(null);
    });
    // Respaldo: si el navegador cerrara el diálogo por su cuenta, la promesa igual se resuelve.
    this.dialogo.addEventListener('close', () => this.terminar(null));
  }

  /** Abre la ventana y resuelve con la opción elegida, o con null si se cancela (botón o Escape). */
  elegir(config: ConfigExplorador, disparador: HTMLElement): Promise<OpcionDTO | null> {
    if (this.resolver !== null) return Promise.resolve(null);
    this.config = config;
    this.disparador = disparador;
    this.titulo.textContent = config.titulo;
    this.campo.value = '';
    this.cantidad.textContent = '';
    this.error.hidden = true;
    this.error.textContent = '';
    this.vacio.hidden = true;
    reemplazarHijos(this.lista, []);
    return new Promise((resolver) => {
      this.resolver = resolver;
      this.ventana.abrir(this.dialogo);
      this.campo.focus();
      void this.buscar('');
    });
  }

  private terminar(opcion: OpcionDTO | null): void {
    const resolver = this.resolver;
    if (resolver === null) return;
    this.resolver = null;
    this.busquedas.invalidar();
    this.ventana.cerrar(this.dialogo);
    this.disparador?.focus();
    resolver(opcion);
  }

  private async buscar(texto: string): Promise<void> {
    const config = this.config;
    if (config === null) return;
    const id = this.busquedas.iniciar();
    try {
      const opciones = await config.buscar(texto.trim());
      if (!this.busquedas.esVigente(id)) return;
      this.error.hidden = true;
      this.error.textContent = '';
      this.mostrar(opciones, config.conPrecio === true);
    } catch (e) {
      if (!this.busquedas.esVigente(id)) return;
      this.error.textContent = e instanceof ApiError ? e.message : 'Ocurrió un error inesperado.';
      this.error.hidden = false;
    }
  }

  private mostrar(opciones: OpcionDTO[], conPrecio: boolean): void {
    const items = opciones.map((o) => {
      // El catálogo de artículos trae la moneda nula: sin código se muestra el número, igual que el precio del renglón.
      const importe = o.valor === null ? '' : o.codigoMoneda === null ? formatearDecimal(o.valor) : formatearImporte(o.valor, o.codigoMoneda);
      const precio = conPrecio && importe !== '' ? ` — ${importe}` : '';
      const boton = crear(this.doc, 'button', `${o.etiqueta}${precio}`, { type: 'button' });
      boton.addEventListener('click', () => this.terminar(o));
      const li = crear(this.doc, 'li');
      li.appendChild(boton);
      return li;
    });
    reemplazarHijos(this.lista, items);
    this.cantidad.textContent = `${opciones.length} ${opciones.length === 1 ? 'resultado' : 'resultados'}`;
    this.vacio.hidden = opciones.length !== 0;
  }
}
