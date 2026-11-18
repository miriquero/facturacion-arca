import type { PaginaDTO } from '../models/factura.dto.js';

/** Texto del indicador de página: la página se muestra desde 1 y nunca hay menos de una. */
export function textoPagina(pagina: number, totalPaginas: number): string {
  return `Página ${pagina + 1} de ${Math.max(1, totalPaginas)}`;
}

export type CambioPagina = { tipo: 'pagina'; pagina: number; tamanio: number } | { tipo: 'tamanio'; tamanio: number };

/**
 * Controles de paginación del `<nav>` estático del HTML: Anterior, Siguiente, indicador y tamaño.
 * Solo dibuja el último resultado recibido y avisa qué cambio pide el usuario; no hace pedidos.
 */
export class Paginacion {
  private readonly anterior: HTMLButtonElement;
  private readonly siguiente: HTMLButtonElement;
  private readonly indicador: HTMLElement;
  private readonly selectTamanio: HTMLSelectElement;
  private ultimo: PaginaDTO<unknown> | null = null;
  private ocupado = false;

  constructor(doc: Document, onCambio: (cambio: CambioPagina) => void) {
    const e = <T extends HTMLElement>(id: string): T => {
      const el = doc.getElementById(id);
      if (!el) throw new Error(`Falta el elemento #${id} en el HTML.`);
      return el as T;
    };
    this.anterior = e('pagina-anterior');
    this.siguiente = e('pagina-siguiente');
    this.indicador = e('pagina-indicador');
    this.selectTamanio = e('tamanio-pagina');
    this.anterior.addEventListener('click', () => this.pedir(-1, onCambio));
    this.siguiente.addEventListener('click', () => this.pedir(1, onCambio));
    this.selectTamanio.addEventListener('change', () => onCambio({ tipo: 'tamanio', tamanio: this.tamanio }));
    this.dibujar();
  }

  /** Tamaño de página elegido en el selector. */
  get tamanio(): number {
    return Number(this.selectTamanio.value);
  }

  /** Página (base 0) del último resultado mostrado, o 0 si todavía no hay ninguno. */
  get paginaActual(): number {
    return this.ultimo?.pagina ?? 0;
  }

  /** Tamaño del último resultado mostrado, o el elegido si todavía no hay ninguno. */
  get tamanioMostrado(): number {
    return this.ultimo?.tamanio ?? this.tamanio;
  }

  mostrar(resultado: PaginaDTO<unknown>): void {
    this.ultimo = resultado;
    this.dibujar();
  }

  /** Bloquea los controles mientras hay un pedido en curso; al liberar vuelven al estado del último resultado. */
  ocupar(ocupado: boolean): void {
    this.ocupado = ocupado;
    this.dibujar();
  }

  private pedir(sentido: -1 | 1, onCambio: (cambio: CambioPagina) => void): void {
    if (this.ocupado || this.ultimo === null) return;
    onCambio({ tipo: 'pagina', pagina: this.ultimo.pagina + sentido, tamanio: this.ultimo.tamanio });
  }

  private dibujar(): void {
    const r = this.ultimo;
    this.indicador.textContent = textoPagina(r?.pagina ?? 0, r?.totalPaginas ?? 0);
    this.anterior.disabled = this.ocupado || r === null || r.pagina <= 0;
    this.siguiente.disabled = this.ocupado || r === null || r.pagina + 1 >= r.totalPaginas;
    this.selectTamanio.disabled = this.ocupado;
    if (r !== null && [...this.selectTamanio.options].some((o) => Number(o.value) === r.tamanio)) {
      this.selectTamanio.value = String(r.tamanio);
    }
  }
}
