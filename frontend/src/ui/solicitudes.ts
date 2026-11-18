/** Identificadores de solicitud: solo la última emitida es vigente (descarta respuestas obsoletas). */
export class ControlSolicitudes {
  private ultimo = 0;

  iniciar(): number {
    this.ultimo += 1;
    return this.ultimo;
  }

  esVigente(id: number): boolean {
    return id === this.ultimo;
  }

  /** Cancela lo pendiente sin emitir una solicitud nueva. */
  invalidar(): void {
    this.ultimo += 1;
  }
}
