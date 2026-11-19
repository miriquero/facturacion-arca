import { ApiError } from './ApiError.js';
import type {
  CambioEstadoRequestDTO,
  FacturaDetalleRequestDTO,
  FormatoReporte,
  ProblemDetail,
  Catalogos,
  FacturaDetalladaDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  OpcionDTO,
  PaginaDTO,
  SolicitudPagina,
} from '../models/factura.dto.js';

export type FetchLike = (url: string, init: RequestInit) => Promise<Response>;

/** Usuario declarado en cada petición: declara quién opera, no autentica. */
export const USUARIO_DECLARADO = 'sistema';

/**
 * Query string de los filtros informados, en orden fijo. La usan el listado y las URL de reporte:
 * así los enlaces de descarga no pueden divergir de la consulta.
 */
export function consultaFiltros(filtros: FacturaFiltrosDTO): URLSearchParams {
  const params = new URLSearchParams();
  if (filtros.fechaDesde) params.set('fechaDesde', filtros.fechaDesde);
  if (filtros.fechaHasta) params.set('fechaHasta', filtros.fechaHasta);
  if (filtros.estado) params.set('estado', filtros.estado);
  if (filtros.montoMinimo !== undefined) params.set('montoMinimo', String(filtros.montoMinimo));
  if (filtros.numero !== undefined) params.set('numero', String(filtros.numero));
  if (filtros.clienteId !== undefined) params.set('clienteId', String(filtros.clienteId));
  if (filtros.informada) params.set('informada', filtros.informada);
  return params;
}

/** Parámetro q de la búsqueda de catálogos: se omite si el texto está vacío o en blanco. */
function consultaTexto(q: string): URLSearchParams {
  const params = new URLSearchParams();
  const texto = q.trim();
  if (texto !== '') params.set('q', texto);
  return params;
}

function conQuery(ruta: string, params: URLSearchParams): string {
  const texto = params.toString();
  return texto === '' ? ruta : `${ruta}?${texto}`;
}

/** Copia solo los campos del contrato: nunca viajan precios ni importes calculados. */
function soloContrato(req: FacturaRequestDTO): FacturaRequestDTO {
  const salida: FacturaRequestDTO = {
    condicionIvaId: req.condicionIvaId,
    puntoVentaId: req.puntoVentaId,
    tipoMonedaId: req.tipoMonedaId,
    detalles: req.detalles.map((d): FacturaDetalleRequestDTO => {
      const r: FacturaDetalleRequestDTO = { listaPrecioArticuloId: d.listaPrecioArticuloId, cantidad: d.cantidad };
      if (d.porcentajeBonificacion !== undefined) r.porcentajeBonificacion = d.porcentajeBonificacion;
      if (d.descripcion !== undefined) r.descripcion = d.descripcion;
      return r;
    }),
  };
  if (req.clienteId !== undefined) salida.clienteId = req.clienteId;
  if (req.fechaEmision !== undefined) salida.fechaEmision = req.fechaEmision;
  if (req.observaciones !== undefined) salida.observaciones = req.observaciones;
  return salida;
}

/** Interpreta el cuerpo de un error: solo se acepta un objeto JSON; todo lo demás es ilegible. */
function leerProblema(texto: string): ProblemDetail {
  try {
    const valor: unknown = JSON.parse(texto);
    if (valor !== null && typeof valor === 'object' && !Array.isArray(valor)) return valor as ProblemDetail;
  } catch {
    // cuerpo vacío, HTML o texto: se usa el mensaje de respaldo
  }
  return {};
}

function erroresPorCampo(valor: unknown): Record<string, string> {
  if (valor === null || typeof valor !== 'object' || Array.isArray(valor)) return {};
  const entradas = Object.entries(valor as Record<string, unknown>);
  if (!entradas.every(([, v]) => typeof v === 'string')) return {};
  return Object.fromEntries(entradas) as Record<string, string>;
}

export class FacturaService {
  private readonly base: string;

  constructor(baseUrl: string, private readonly fetchFn: FetchLike) {
    this.base = baseUrl.replace(/\/+$/, '');
  }

  obtenerFacturas(filtros: FacturaFiltrosDTO, pagina: SolicitudPagina): Promise<PaginaDTO<FacturaReporteDTO>> {
    const params = consultaFiltros(filtros);
    params.set('pagina', String(pagina.pagina));
    params.set('tamanio', String(pagina.tamanio));
    return this.enviar<PaginaDTO<FacturaReporteDTO>>(conQuery('/facturas', params), 'GET');
  }

  buscarClientes(q: string): Promise<OpcionDTO[]> {
    return this.enviar<OpcionDTO[]>(conQuery('/catalogos/clientes', consultaTexto(q)), 'GET');
  }

  buscarListaPrecioArticulos(q: string, listaId?: number): Promise<OpcionDTO[]> {
    const params = consultaTexto(q);
    if (listaId !== undefined) params.set('listaId', String(listaId));
    return this.enviar<OpcionDTO[]>(conQuery('/catalogos/lista-precio-articulos', params), 'GET');
  }

  obtenerListasPrecio(): Promise<OpcionDTO[]> {
    return this.enviar<OpcionDTO[]>('/catalogos/listas-precio', 'GET');
  }

  urlPdfFactura(id: number): string {
    return `${this.base}/facturas/${id}/pdf`;
  }

  obtenerFactura(id: number): Promise<FacturaDetalladaDTO> {
    return this.enviar<FacturaDetalladaDTO>(`/facturas/${id}`, 'GET');
  }

  async obtenerCatalogos(): Promise<Catalogos> {
    const pedir = (nombre: string): Promise<OpcionDTO[]> => this.enviar<OpcionDTO[]>(`/catalogos/${nombre}`, 'GET');
    const [puntosVenta, clientes, condicionesIva, tiposMoneda, listaPrecioArticulos] = await Promise.all([
      pedir('puntos-venta'),
      pedir('clientes'),
      pedir('condiciones-iva'),
      pedir('tipos-moneda'),
      pedir('lista-precio-articulos'),
    ]);
    return { puntosVenta, clientes, condicionesIva, tiposMoneda, listaPrecioArticulos };
  }

  crearFactura(req: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    return this.enviar<FacturaReporteDTO>('/facturas', 'POST', soloContrato(req));
  }

  actualizarFactura(id: number, req: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    return this.enviar<FacturaReporteDTO>(`/facturas/${id}`, 'PUT', soloContrato(req));
  }

  cambiarEstado(id: number, estado: CambioEstadoRequestDTO['estado'], observaciones?: string): Promise<FacturaReporteDTO> {
    const cuerpo: CambioEstadoRequestDTO = { estado };
    if (observaciones !== undefined && observaciones.trim() !== '') cuerpo.observaciones = observaciones;
    return this.enviar<FacturaReporteDTO>(`/facturas/${id}/estado`, 'PATCH', cuerpo);
  }

  urlReporte(filtros: FacturaFiltrosDTO, formato: FormatoReporte): string {
    const params = new URLSearchParams({ formato });
    consultaFiltros(filtros).forEach((valor, clave) => params.append(clave, valor));
    return `${this.base}/facturas/reporte?${params.toString()}`;
  }

  eliminarFactura(id: number): Promise<void> {
    return this.enviar<void>(`/facturas/${id}`, 'DELETE');
  }

  private async enviar<T>(ruta: string, metodo: string, cuerpo?: unknown): Promise<T> {
    const headers: Record<string, string> = { 'X-Usuario': USUARIO_DECLARADO };
    const init: RequestInit = { method: metodo, headers };
    if (cuerpo !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(cuerpo);
    }
    let respuesta: Response;
    try {
      respuesta = await this.fetchFn(this.base + ruta, init);
    } catch {
      throw new ApiError(0, `No se pudo conectar con la API en ${this.base}. ¿Está corriendo el backend?`);
    }
    if (!respuesta.ok) {
      const problema = leerProblema(await respuesta.text());
      const detalle = typeof problema.detail === 'string' && problema.detail !== '' ? problema.detail : undefined;
      const titulo = typeof problema.title === 'string' && problema.title !== '' ? problema.title : undefined;
      const mensaje = detalle ?? titulo ?? `El servidor respondió ${respuesta.status} sin un detalle legible.`;
      throw new ApiError(respuesta.status, mensaje, titulo, erroresPorCampo(problema.errores));
    }
    if (respuesta.status === 204) return undefined as T;
    return (await respuesta.json()) as T;
  }
}
