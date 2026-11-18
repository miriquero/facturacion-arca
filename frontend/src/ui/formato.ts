const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });
const DOLARES = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'USD' });
const DOS_DECIMALES = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** PES -> ARS, DOL -> USD; otro código queda visible junto al importe. Nunca convierte entre monedas. */
export function formatearImporte(valor: number, codigo: string | null): string {
  if (codigo === 'PES') return PESOS.format(valor);
  if (codigo === 'DOL') return DOLARES.format(valor);
  return `${codigo ?? 'Sin moneda'} ${DOS_DECIMALES.format(valor)}`;
}

/** yyyy-MM-dd -> dd/mm/aaaa por partición del texto: nunca new Date (corre un día en UTC-3). */
export function formatearFecha(texto: string): string {
  const [anio, mes, dia] = texto.split('-');
  return `${dia}/${mes}/${anio}`;
}

export function formatearNumero(n: number | null): string {
  return n === null ? '—' : String(n);
}

/** Número con dos decimales en formato argentino, sin símbolo de moneda. */
export function formatearDecimal(valor: number): string {
  return DOS_DECIMALES.format(valor);
}

/** Comprobante `0001-00000004` (punto de venta en cuatro dígitos y número en ocho); `—` si todavía no tiene número. */
export function formatearComprobante(puntoVenta: number, numero: number | null): string {
  if (numero === null) return '—';
  return `${String(puntoVenta).padStart(4, '0')}-${String(numero).padStart(8, '0')}`;
}
