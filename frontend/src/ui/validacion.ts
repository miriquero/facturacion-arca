import type { FacturaDetalleRequestDTO, FacturaFiltrosDTO, FacturaRequestDTO } from '../models/factura.dto.js';

export interface Resultado<T> {
  datos?: T;
  errores: Record<string, string>;
}
export interface RenglonTexto {
  listaPrecioArticuloId: string;
  cantidad: string;
  porcentajeBonificacion: string;
  descripcion: string;
}
export interface FormularioTexto {
  clienteId: string;
  condicionIvaId: string;
  puntoVentaId: string;
  tipoMonedaId: string;
  fechaEmision: string;
  observaciones: string;
  renglones: RenglonTexto[];
}
export interface FiltrosTexto {
  fechaDesde: string;
  fechaHasta: string;
  estado: string;
  montoMinimo: string;
}

const MAX_DESCRIPCION = 255;
const MAX_OBSERVACIONES = 500;

/** '' -> ausente; otro texto -> número (puede ser NaN o Infinity: lo juzga quien llama). */
function numero(texto: string): number | undefined {
  const limpio = texto.trim();
  return limpio === '' ? undefined : Number(limpio);
}

function textoOpcional(texto: string, max: number, clave: string, errores: Record<string, string>): string | undefined {
  const limpio = texto.trim();
  if (limpio === '') return undefined;
  if (limpio.length > max) {
    errores[clave] = `Admite hasta ${max} caracteres (tiene ${limpio.length}).`;
    return undefined;
  }
  return limpio;
}

function idObligatorio(texto: string, clave: string, nombre: string, errores: Record<string, string>): number {
  const n = numero(texto);
  if (n === undefined || !Number.isFinite(n)) {
    errores[clave] = `Elegí ${nombre}.`;
    return 0;
  }
  return n;
}

/** Valida el texto crudo del formulario; las claves de error usan la misma notación que la API. */
export function validarFormulario(f: FormularioTexto): Resultado<FacturaRequestDTO> {
  const errores: Record<string, string> = {};
  const condicionIvaId = idObligatorio(f.condicionIvaId, 'condicionIvaId', 'la condición de IVA', errores);
  const puntoVentaId = idObligatorio(f.puntoVentaId, 'puntoVentaId', 'el punto de venta', errores);
  const tipoMonedaId = idObligatorio(f.tipoMonedaId, 'tipoMonedaId', 'la moneda', errores);
  const clienteId = numero(f.clienteId);
  const observaciones = textoOpcional(f.observaciones, MAX_OBSERVACIONES, 'observaciones', errores);

  if (f.renglones.length === 0) errores.detalles = 'Agregá al menos un renglón.';
  const detalles: FacturaDetalleRequestDTO[] = f.renglones.map((r, i) => {
    const pre = `detalles[${i}]`;
    const articulo = idObligatorio(r.listaPrecioArticuloId, `${pre}.listaPrecioArticuloId`, 'un artículo', errores);
    const cantidad = numero(r.cantidad);
    if (cantidad === undefined || !Number.isFinite(cantidad) || cantidad <= 0) {
      errores[`${pre}.cantidad`] = 'La cantidad debe ser un número mayor que 0.';
    }
    const bonificacion = numero(r.porcentajeBonificacion);
    if (bonificacion !== undefined && (!Number.isFinite(bonificacion) || bonificacion < 0 || bonificacion > 100)) {
      errores[`${pre}.porcentajeBonificacion`] = 'La bonificación debe estar entre 0 y 100.';
    }
    const descripcion = textoOpcional(r.descripcion, MAX_DESCRIPCION, `${pre}.descripcion`, errores);
    const detalle: FacturaDetalleRequestDTO = { listaPrecioArticuloId: articulo, cantidad: cantidad ?? 0 };
    if (bonificacion !== undefined) detalle.porcentajeBonificacion = bonificacion;
    if (descripcion !== undefined) detalle.descripcion = descripcion;
    return detalle;
  });

  if (Object.keys(errores).length > 0) return { errores };
  const datos: FacturaRequestDTO = { condicionIvaId, puntoVentaId, tipoMonedaId, detalles };
  if (clienteId !== undefined) datos.clienteId = clienteId;
  if (f.fechaEmision.trim() !== '') datos.fechaEmision = f.fechaEmision.trim();
  if (observaciones !== undefined) datos.observaciones = observaciones;
  return { datos, errores };
}

/** Las fechas invertidas no se validan acá: las rechaza el backend con 400. */
export function validarFiltros(f: FiltrosTexto): Resultado<FacturaFiltrosDTO> {
  const errores: Record<string, string> = {};
  const datos: FacturaFiltrosDTO = {};
  if (f.fechaDesde !== '') datos.fechaDesde = f.fechaDesde;
  if (f.fechaHasta !== '') datos.fechaHasta = f.fechaHasta;
  if (f.estado !== '') datos.estado = f.estado as FacturaFiltrosDTO['estado'];
  const monto = numero(f.montoMinimo);
  if (monto !== undefined) {
    if (!Number.isFinite(monto) || monto < 0) errores.montoMinimo = 'El monto mínimo debe ser un número mayor o igual a 0.';
    else datos.montoMinimo = monto;
  }
  return Object.keys(errores).length > 0 ? { errores } : { datos, errores };
}

export function validarMotivo(texto: string): Resultado<string | undefined> {
  const limpio = texto.trim();
  if (limpio.length > MAX_OBSERVACIONES) {
    return { errores: { motivo: `El motivo admite hasta ${MAX_OBSERVACIONES} caracteres (tiene ${limpio.length}).` } };
  }
  return { datos: limpio === '' ? undefined : limpio, errores: {} };
}
