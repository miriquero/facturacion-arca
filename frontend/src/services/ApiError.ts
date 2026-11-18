export class ApiError extends Error {
  readonly status: number;
  readonly titulo?: string;
  readonly errores: Readonly<Record<string, string>>;

  constructor(status: number, mensaje: string, titulo?: string, errores: Readonly<Record<string, string>> = {}) {
    super(mensaje);
    this.name = 'ApiError';
    this.status = status;
    if (titulo !== undefined) this.titulo = titulo;
    this.errores = errores;
  }
}
