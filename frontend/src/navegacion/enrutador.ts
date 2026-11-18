import { hrefDe, interpretarRuta, type Ruta } from './rutas.js';

/** Lo mínimo de `window` que usa el enrutador (lo satisfacen `window` y la ventana de jsdom). */
export interface Ventana {
  location: { hash: string; replace(url: string): void };
  addEventListener(tipo: 'hashchange', escucha: () => void): void;
}

/** Mensaje que la aplicación deja escrito en la región de estado de la pantalla de destino. */
export interface Mensaje {
  tipo: 'exito' | 'error';
  texto: string;
}

export interface Cambio {
  ruta: Ruta;
  /** Mensaje pendiente de `ir(ruta, { mensaje })`; null si la navegación la inició el usuario. */
  mensaje: Mensaje | null;
  /** true solo para la ruta con la que arrancó la aplicación. */
  inicial: boolean;
}

export interface OpcionesIr {
  reemplazar?: boolean;
  mensaje?: Mensaje;
}

/**
 * Enrutador por fragmento (`#/…`): traduce `hashchange` en llamadas a `alCambiar` y deja que la aplicación
 * navegue con `ir`. Atrás, Adelante, la recarga y los enlaces `<a href="#/…">` funcionan sin código propio.
 */
export class Enrutador {
  private mensajePendiente: Mensaje | null = null;

  constructor(
    private readonly ventana: Ventana,
    private readonly alCambiar: (cambio: Cambio) => Promise<void> | void,
  ) {}

  get rutaActual(): Ruta {
    return interpretarRuta(this.ventana.location.hash);
  }

  /** Escucha los cambios de fragmento y procesa el actual; resuelve cuando termina esa primera carga. */
  iniciar(): Promise<void> {
    this.ventana.addEventListener('hashchange', () => void this.procesar(false));
    return this.procesar(true);
  }

  /**
   * Navega a `ruta`: agrega una entrada al historial, o reemplaza la actual con `reemplazar` (para que Atrás no
   * vuelva a una pantalla imposible). El `mensaje` se entrega junto con el cambio de pantalla.
   */
  ir(ruta: Ruta, opciones: OpcionesIr = {}): void {
    this.mensajePendiente = opciones.mensaje ?? null;
    const destino = hrefDe(ruta);
    if (this.ventana.location.hash === destino) {
      // Mismo fragmento: el navegador no dispara hashchange, así que se procesa directamente.
      void this.procesar(false);
    } else if (opciones.reemplazar === true) {
      this.ventana.location.replace(destino);
    } else {
      this.ventana.location.hash = destino;
    }
  }

  private procesar(inicial: boolean): Promise<void> {
    const mensaje = this.mensajePendiente;
    this.mensajePendiente = null;
    return Promise.resolve(this.alCambiar({ ruta: this.rutaActual, mensaje, inicial }));
  }
}
