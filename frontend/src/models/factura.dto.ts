/** Espejo tipado del contrato HTTP de la API (records Java del paquete dto). */

export type EstadoFactura = 'BORRADOR' | 'EMITIDA' | 'ANULADA';

export type FormatoReporte = 'pdf' | 'xlsx' | 'txt';

export interface FacturaReporteDTO {
  id: number;
  numeroFactura: number | null;
  fechaEmision: string;
  clienteDenominacion: string;
  condicionIva: string;
  puntoVentaDescripcion: string;
  importeTotal: number;
  cantidadItems: number;
  estado: EstadoFactura;
  codigoMoneda: string;
}

export interface FacturaDetalleDTO {
  id: number;
  listaPrecioArticuloId: number;
  articuloCodigo: string;
  descripcion: string | null;
  cantidad: number;
  precioUnitario: number;
  porcentajeBonificacion: number;
  importeNeto: number;
  importeIva: number;
  importeSubtotal: number;
}

export interface FacturaDetalladaDTO {
  id: number;
  numero: number | null;
  fechaEmision: string;
  estado: EstadoFactura;
  clienteId: number | null;
  clienteDenominacion: string;
  clienteCuitCuil: string | null;
  condicionIvaId: number;
  condicionIva: string;
  puntoVentaId: number;
  puntoVentaNumero: number;
  puntoVentaDescripcion: string;
  tipoMonedaId: number;
  codigoMoneda: string;
  importeTotal: number;
  importeCobrado: number;
  importeSaldo: number;
  cae: string | null;
  caeFechaVencimiento: string | null;
  resultadoAfip: string | null;
  fechaAnulacion: string | null;
  observaciones: string | null;
  detalles: FacturaDetalleDTO[];
}

/** Request de alta y de reemplazo: sin precios ni importes (el servidor los calcula). */
export interface FacturaDetalleRequestDTO {
  listaPrecioArticuloId: number;
  cantidad: number;
  porcentajeBonificacion?: number;
  descripcion?: string;
}

export interface FacturaRequestDTO {
  clienteId?: number;
  condicionIvaId: number;
  puntoVentaId: number;
  tipoMonedaId: number;
  fechaEmision?: string;
  observaciones?: string;
  detalles: FacturaDetalleRequestDTO[];
}

export interface CambioEstadoRequestDTO {
  estado: 'EMITIDA' | 'ANULADA';
  observaciones?: string;
}

export interface FacturaFiltrosDTO {
  fechaDesde?: string;
  fechaHasta?: string;
  estado?: EstadoFactura;
  montoMinimo?: number;
  numero?: number;
  clienteId?: number;
  informada?: 'true' | 'false';
}

/** Sobre paginado del listado (GET /api/facturas). */
export interface PaginaDTO<T> {
  items: T[];
  /** Base 0. */
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface SolicitudPagina {
  /** Base 0. */
  pagina: number;
  tamanio: number;
}

/**
 * Opción de un catálogo. En `tipos-moneda`, `valor` es la cotización: pesos que vale una unidad de la moneda
 * (PES = 1, DOL = 1550). En `lista-precio-articulos`, `valor` es el precio de lista y `codigoMoneda` su moneda
 * (pesos); el servidor lo divide por la cotización al guardar una factura en otra moneda.
 */
export interface OpcionDTO {
  id: number;
  etiqueta: string;
  valor: number | null;
  codigoMoneda: string | null;
}

export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  timestamp?: string;
  errores?: Record<string, string>;
}

export interface Catalogos {
  puntosVenta: OpcionDTO[];
  clientes: OpcionDTO[];
  condicionesIva: OpcionDTO[];
  tiposMoneda: OpcionDTO[];
  listaPrecioArticulos: OpcionDTO[];
}
