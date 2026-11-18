import { mensajeDe, type ApiFacturas } from '../contexto.js';
import type { FacturaFiltrosDTO, FacturaReporteDTO, OpcionDTO, PaginaDTO, SolicitudPagina } from '../models/factura.dto.js';
import type { Ruta } from '../navegacion/rutas.js';
import { limpiarInvalido, marcarInvalido } from '../ui/dom.js';
import { Listado, textoCantidad } from '../ui/listado.js';
import { Paginacion, textoPagina } from '../ui/paginacion.js';
import type { RegionEstado } from '../ui/regionEstado.js';
import { ControlSolicitudes } from '../ui/solicitudes.js';
import { validarFiltros } from '../ui/validacion.js';
import type { Pantalla } from './pantalla.js';

type Origen = 'busqueda' | 'pagina' | 'recarga';

/**
 * Pantalla del listado (`#/`): filtros, tabla y paginación. Su DOM se enlaza una sola vez y no se destruye al salir,
 * así que los campos de filtro conservan sus valores; los filtros aplicados y la página viven en este cierre.
 */
export class PantallaListado implements Pantalla {
  readonly seccion: HTMLElement;
  readonly titulo: HTMLElement;
  private readonly listado: Listado;
  private readonly paginacion: Paginacion;
  private readonly busquedas = new ControlSolicitudes();
  private filtrosAplicados: FacturaFiltrosDTO = {};
  private cargado = false;

  constructor(
    private readonly doc: Document,
    private readonly servicio: ApiFacturas,
    private readonly region: RegionEstado,
    elegirCliente: (disparador: HTMLElement) => Promise<OpcionDTO | null>,
  ) {
    const $ = <T extends HTMLElement>(id: string): T => {
      const e = doc.getElementById(id);
      if (!e) throw new Error(`Falta el elemento #${id} en el HTML.`);
      return e as T;
    };
    this.seccion = $('pantalla-listado');
    this.titulo = $('titulo-listado');
    this.listado = new Listado(doc, $('filas'), $('contador'), $('sin-resultados'), (id) => servicio.urlPdfFactura(id));
    this.paginacion = new Paginacion(doc, (cambio) => {
      region.limpiar();
      if (cambio.tipo === 'pagina') void this.ejecutarConsulta(this.filtrosAplicados, { pagina: cambio.pagina, tamanio: cambio.tamanio }, 'pagina');
      else void this.ejecutarConsulta(this.filtrosAplicados, { pagina: 0, tamanio: cambio.tamanio }, 'pagina');
    });

    const campoMonto = $<HTMLInputElement>('filtro-monto');
    const campoNumero = $<HTMLInputElement>('filtro-numero');
    const salidaCliente = $('filtro-cliente');

    /** Cliente del filtro: el id vive en data-id de la salida (sin input oculto, que no tendría etiqueta). */
    const ponerClienteFiltro = (opcion: { id: number; etiqueta: string } | null): void => {
      salidaCliente.textContent = opcion === null ? 'Todos' : opcion.etiqueta;
      if (opcion === null) salidaCliente.removeAttribute('data-id');
      else salidaCliente.setAttribute('data-id', String(opcion.id));
    };

    // Elegir el cliente del filtro no busca: solo la acción Buscar pide el listado.
    $('elegir-cliente-filtro').addEventListener('click', (evento) => {
      void elegirCliente(evento.currentTarget as HTMLElement).then((opcion) => {
        if (opcion !== null) ponerClienteFiltro(opcion);
      });
    });
    $('quitar-cliente-filtro').addEventListener('click', () => ponerClienteFiltro(null));

    $('form-filtros').addEventListener('submit', (evento) => {
      evento.preventDefault();
      region.limpiar();
      const resultado = validarFiltros({
        fechaDesde: $<HTMLInputElement>('filtro-desde').value,
        fechaHasta: $<HTMLInputElement>('filtro-hasta').value,
        estado: $<HTMLSelectElement>('filtro-estado').value,
        montoMinimo: campoMonto.value,
        numero: campoNumero.value,
        informada: $<HTMLSelectElement>('filtro-informada').value,
        clienteId: salidaCliente.getAttribute('data-id') ?? '',
      });
      limpiarInvalido(campoMonto);
      limpiarInvalido(campoNumero);
      if (resultado.datos === undefined) {
        // El campo de número está antes que el monto en el DOM: el foco va al primero inválido.
        const invalidos: [HTMLInputElement, string | undefined][] = [
          [campoNumero, resultado.errores.numero],
          [campoMonto, resultado.errores.montoMinimo],
        ];
        for (const [campo, mensaje] of invalidos) if (mensaje !== undefined) marcarInvalido(campo, mensaje);
        invalidos.find(([, mensaje]) => mensaje !== undefined)?.[0].focus();
        return;
      }
      void this.ejecutarBusqueda(resultado.datos);
    });

    $('limpiar-filtros').addEventListener('click', () => {
      region.limpiar();
      $<HTMLFormElement>('form-filtros').reset();
      ponerClienteFiltro(null);
      limpiarInvalido(campoMonto);
      limpiarInvalido(campoNumero);
      void this.ejecutarBusqueda({});
    });
  }

  tituloDocumento(_ruta: Ruta): string {
    return 'Facturas';
  }

  /** La primera vez, la página 0 sin filtros; después, los mismos filtros y la misma página para reflejar cambios ajenos. */
  entrar(_ruta: Ruta): Promise<void> {
    if (this.cargado) return this.recargar();
    this.cargado = true;
    return this.ejecutarBusqueda({});
  }

  /** Una respuesta pendiente del listado llega tarde: se descarta y los controles de paginación se liberan. */
  salir(): void {
    this.busquedas.invalidar();
    this.paginacion.ocupar(false);
  }

  /** Buscar, limpiar y carga inicial: siempre la página 0 con el tamaño elegido. */
  ejecutarBusqueda(filtros: FacturaFiltrosDTO): Promise<void> {
    return this.ejecutarConsulta(filtros, { pagina: 0, tamanio: this.paginacion.tamanio }, 'busqueda');
  }

  /** Al volver al listado: los mismos filtros y la misma página del resultado mostrado. */
  private recargar(): Promise<void> {
    return this.ejecutarConsulta(this.filtrosAplicados, { pagina: this.paginacion.paginaActual, tamanio: this.paginacion.tamanioMostrado }, 'recarga');
  }

  /** Tabla, contador, paginación y enlaces cambian juntos con una misma respuesta. */
  private aplicarResultado(resultado: PaginaDTO<FacturaReporteDTO>, filtros: FacturaFiltrosDTO): void {
    this.filtrosAplicados = filtros;
    this.listado.mostrar(resultado.items, resultado.totalElementos);
    this.paginacion.mostrar(resultado);
    this.doc.getElementById('descarga-pdf')?.setAttribute('href', this.servicio.urlReporte(filtros, 'pdf'));
    this.doc.getElementById('descarga-xlsx')?.setAttribute('href', this.servicio.urlReporte(filtros, 'xlsx'));
  }

  /**
   * Única puerta del listado (búsqueda, cambio de página o de tamaño y recarga): un solo ControlSolicitudes descarta
   * la respuesta obsoleta de cualquier origen. Los controles de paginación quedan bloqueados mientras la solicitud
   * vigente está en curso y se liberan siempre al terminar.
   */
  private async ejecutarConsulta(filtros: FacturaFiltrosDTO, solicitud: SolicitudPagina, origen: Origen, reintento = false): Promise<void> {
    const id = this.busquedas.iniciar();
    this.paginacion.ocupar(true);
    try {
      const resultado = await this.servicio.obtenerFacturas(filtros, solicitud);
      if (!this.busquedas.esVigente(id)) return;
      // Tras eliminar el único elemento de la última página: se pide la última que existe, una sola vez.
      if (origen === 'recarga' && !reintento && resultado.items.length === 0 && resultado.totalPaginas > 0 && solicitud.pagina >= resultado.totalPaginas) {
        await this.ejecutarConsulta(filtros, { pagina: resultado.totalPaginas - 1, tamanio: solicitud.tamanio }, origen, true);
        return;
      }
      this.aplicarResultado(resultado, filtros);
      if (origen === 'pagina') this.region.exito(`${textoPagina(resultado.pagina, resultado.totalPaginas)} (${textoCantidad(resultado.totalElementos)})`);
    } catch (e) {
      if (!this.busquedas.esVigente(id)) return;
      this.region.error(mensajeDe(e));
    } finally {
      if (this.busquedas.esVigente(id)) this.paginacion.ocupar(false);
    }
  }
}
