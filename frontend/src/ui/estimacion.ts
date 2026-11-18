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
