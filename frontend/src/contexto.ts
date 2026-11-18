import type { Catalogos, FacturaDetalladaDTO, FacturaFiltrosDTO, FacturaReporteDTO, FacturaRequestDTO, FormatoReporte, OpcionDTO, PaginaDTO, SolicitudPagina } from './models/factura.dto.js';
import type { Ventana } from './navegacion/enrutador.js';
import { ApiError } from './services/ApiError.js';
import type { VentanaModal } from './ui/explorador.js';
import type { Temporizador } from './ui/regionEstado.js';

/** Interfaz pública del servicio HTTP que usa la aplicación (la satisface FacturaService y los dobles de prueba). */
export interface ApiFacturas {
  obtenerFacturas(filtros: FacturaFiltrosDTO, pagina: SolicitudPagina): Promise<PaginaDTO<FacturaReporteDTO>>;
  cambiarEstado(id: number, estado: 'EMITIDA' | 'ANULADA', observaciones?: string): Promise<FacturaReporteDTO>;
  eliminarFactura(id: number): Promise<void>;
  obtenerFactura(id: number): Promise<FacturaDetalladaDTO>;
  obtenerCatalogos(): Promise<Catalogos>;
  crearFactura(req: FacturaRequestDTO): Promise<FacturaReporteDTO>;
  actualizarFactura(id: number, req: FacturaRequestDTO): Promise<FacturaReporteDTO>;
  buscarClientes(q: string): Promise<OpcionDTO[]>;
  buscarListaPrecioArticulos(q: string): Promise<OpcionDTO[]>;
  urlReporte(filtros: FacturaFiltrosDTO, formato: FormatoReporte): string;
  urlPdfFactura(id: number): string;
}

export interface Dialogos {
  confirmar(mensaje: string): Promise<boolean>;
  pedirMotivo(mensaje: string): Promise<string | null>;
}

export interface Entorno {
  documento: Document;
  servicio: ApiFacturas;
  dialogos: Dialogos;
  temporizador: Temporizador;
  /** Abre y cierra el diálogo del explorador (showModal en producción, doble en las pruebas). */
  ventanaModal: VentanaModal;
  /** Ventana del navegador (location y hashchange): la usa el enrutador. */
  ventana: Ventana;
}

export function mensajeDe(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Ocurrió un error inesperado.';
}
