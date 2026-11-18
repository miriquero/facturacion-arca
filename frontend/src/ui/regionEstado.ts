import { crear } from './dom.js';

export interface Temporizador {
  /** Programa fn dentro de ms milisegundos y devuelve la función que la cancela. */
  programar(fn: () => void, ms: number): () => void;
}

const MS_EXITO = 6000;

/**
 * Región de estado (role=status, aria-live=polite): existe desde la carga y solo cambia su contenido.
 * Éxito se limpia a los 6 s; error y progreso quedan hasta la siguiente acción.
 */
export class RegionEstado {
  private cancelarLimpieza: (() => void) | null = null;

  constructor(private readonly elemento: HTMLElement, private readonly temporizador: Temporizador) {}

  exito(texto: string): void {
    this.escribir('estado--exito', [this.elemento.ownerDocument.createTextNode(texto)]);
    this.cancelarLimpieza = this.temporizador.programar(() => this.limpiar(), MS_EXITO);
  }

  error(texto: string, detalles: string[] = []): void {
    const doc = this.elemento.ownerDocument;
    const nodos: Node[] = [doc.createTextNode(`Error: ${texto}`)];
    if (detalles.length > 0) {
      const lista = crear(doc, 'ul');
      for (const d of detalles) lista.appendChild(crear(doc, 'li', d));
      nodos.push(lista);
    }
    this.escribir('estado--error', nodos);
  }

  progreso(texto: string): void {
    this.escribir('estado--progreso', [this.elemento.ownerDocument.createTextNode(texto)]);
  }

  limpiar(): void {
    this.escribir('', []);
  }

  private escribir(clase: string, nodos: Node[]): void {
    this.cancelarLimpieza?.();
    this.cancelarLimpieza = null;
    this.elemento.className = clase;
    this.elemento.replaceChildren(...nodos);
  }
}
