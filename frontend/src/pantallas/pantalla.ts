import type { Mensaje, OpcionesIr } from '../navegacion/enrutador.js';
import type { Ruta } from '../navegacion/rutas.js';

/** Una pantalla de la aplicación: una sección del HTML con su controlador. */
export interface Pantalla {
  /** La `<section>` que la cáscara muestra u oculta con `hidden`. */
  readonly seccion: HTMLElement;
  /** Su `h1` (enfocable con tabindex="-1"): recibe el foco al navegar a la pantalla. */
  readonly titulo: HTMLElement;
  /** Texto que va delante de « — Facturación ARCA» en `document.title`. */
  tituloDocumento(ruta: Ruta): string;
  /** Prepara la pantalla para la ruta; resuelve cuando termina su carga. */
  entrar(ruta: Ruta): Promise<void>;
  /** Se llama al salir: descarta las respuestas pendientes de su carga. */
  salir(): void;
}

/** Lo que las pantallas necesitan del enrutador para navegar (y para decir si siguen activas). */
export interface Navegador {
  ir(ruta: Ruta, opciones?: OpcionesIr): void;
}

export type { Mensaje };
