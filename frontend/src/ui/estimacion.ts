export interface RenglonEstimable {
  precio: number | null;
  cantidad: number | null;
  bonificacion: number;
}

/** Total neto estimado sin IVA. Informativo: el importe definitivo lo calcula el servidor. */
export function netoEstimado(renglones: RenglonEstimable[]): number {
  let total = 0;
  for (const r of renglones) {
    if (r.precio === null || r.cantidad === null) continue;
    total += r.cantidad * r.precio * (1 - r.bonificacion / 100);
  }
  return total;
}

/**
 * Precio de lista (en pesos) expresado en otra moneda: se divide por su cotización (pesos por unidad) y se
 * redondea a dos decimales, igual que el servidor. Sin cotización válida o con cotización 1 queda tal cual.
 */
export function precioEnMoneda(precio: number, cotizacion: number): number {
  if (!Number.isFinite(cotizacion) || cotizacion <= 0 || cotizacion === 1) return precio;
  return Math.round((precio / cotizacion + Number.EPSILON) * 100) / 100;
}
