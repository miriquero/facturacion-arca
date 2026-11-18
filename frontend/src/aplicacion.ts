import type {
  Catalogos,
  FacturaDetalladaDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  FormatoReporte,
  OpcionDTO,
  PaginaDTO,
  SolicitudPagina,
} from './models/factura.dto.js';
import { ApiError } from './services/ApiError.js';
import { limpiarInvalido, marcarInvalido } from './ui/dom.js';
import { Explorador, type VentanaModal } from './ui/explorador.js';
import { Formulario } from './ui/formulario.js';
import { Listado, textoCantidad, type Accion } from './ui/listado.js';
import { Paginacion, textoPagina } from './ui/paginacion.js';
import { RegionEstado, type Temporizador } from './ui/regionEstado.js';
import { ControlSolicitudes } from './ui/solicitudes.js';
import { validarFiltros, validarFormulario, validarMotivo } from './ui/validacion.js';

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
}

export interface Aplicacion {
  /** Resuelve cuando terminó la carga inicial (catálogos y listado). */
  listo: Promise<void>;
}

export function mensajeDe(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Ocurrió un error inesperado.';
}

export function iniciarAplicacion(entorno: Entorno): Aplicacion {
  const { documento: doc, servicio, temporizador } = entorno;
  const $ = <T extends HTMLElement>(id: string): T => {
    const e = doc.getElementById(id);
    if (!e) throw new Error(`Falta el elemento #${id} en el HTML.`);
    return e as T;
  };

  const region = new RegionEstado($('estado'), temporizador);
  const explorador = new Explorador(doc, entorno.ventanaModal);
  const buscarClientes = (disparador: HTMLElement, titulo: string): Promise<OpcionDTO | null> =>
    explorador.elegir({ titulo, buscar: (q) => servicio.buscarClientes(q) }, disparador);
  const formulario = new Formulario(doc, {
    cliente: (disparador) => buscarClientes(disparador, 'Buscar cliente'),
    articulo: (disparador, renglon) =>
      explorador.elegir(
        { titulo: `Buscar artículo de lista de precios (renglón ${renglon})`, buscar: (q) => servicio.buscarListaPrecioArticulos(q), conPrecio: true },
        disparador,
      ),
  });
  const busquedas = new ControlSolicitudes();
  const cargaEdicion = new ControlSolicitudes();
  let filtrosAplicados: FacturaFiltrosDTO = {};
  let guardando = false;
  let accionEnCurso = false;

  const listado = new Listado(doc, $('filas'), $('contador'), $('sin-resultados'), (accion, id) => void accionFila(accion, id), (id) => servicio.urlPdfFactura(id));

  const paginacion = new Paginacion(doc, (cambio) => {
    region.limpiar();
    if (cambio.tipo === 'pagina') void ejecutarConsulta(filtrosAplicados, { pagina: cambio.pagina, tamanio: cambio.tamanio }, 'pagina');
    else void ejecutarConsulta(filtrosAplicados, { pagina: 0, tamanio: cambio.tamanio }, 'pagina');
  });

  /** Tabla, contador, paginación y enlaces cambian juntos con una misma respuesta. */
  function aplicarResultado(resultado: PaginaDTO<FacturaReporteDTO>, filtros: FacturaFiltrosDTO): void {
    filtrosAplicados = filtros;
    listado.mostrar(resultado.items, resultado.totalElementos);
    paginacion.mostrar(resultado);
    $('descarga-pdf').setAttribute('href', servicio.urlReporte(filtros, 'pdf'));
    $('descarga-xlsx').setAttribute('href', servicio.urlReporte(filtros, 'xlsx'));
  }

  type Origen = 'busqueda' | 'pagina' | 'recarga';

  /**
   * Única puerta del listado (búsqueda, cambio de página o de tamaño y recarga tras una acción): un solo
   * ControlSolicitudes descarta la respuesta obsoleta de cualquier origen. Los controles de paginación quedan
   * bloqueados mientras la solicitud vigente está en curso y se liberan siempre al terminar.
   */
  async function ejecutarConsulta(filtros: FacturaFiltrosDTO, solicitud: SolicitudPagina, origen: Origen, reintento = false): Promise<void> {
    const id = busquedas.iniciar();
    paginacion.ocupar(true);
    try {
      const resultado = await servicio.obtenerFacturas(filtros, solicitud);
      if (!busquedas.esVigente(id)) return;
      // Tras eliminar el único elemento de la última página: se pide la última que existe, una sola vez.
      if (origen === 'recarga' && !reintento && resultado.items.length === 0 && resultado.totalPaginas > 0 && solicitud.pagina >= resultado.totalPaginas) {
        await ejecutarConsulta(filtros, { pagina: resultado.totalPaginas - 1, tamanio: solicitud.tamanio }, origen, true);
        return;
      }
      aplicarResultado(resultado, filtros);
      if (origen === 'pagina') region.exito(`${textoPagina(resultado.pagina, resultado.totalPaginas)} (${textoCantidad(resultado.totalElementos)})`);
    } catch (e) {
      if (!busquedas.esVigente(id)) return;
      region.error(mensajeDe(e));
    } finally {
      if (busquedas.esVigente(id)) paginacion.ocupar(false);
    }
  }

  /** Buscar, limpiar y carga inicial: siempre la página 0 con el tamaño elegido. */
  function ejecutarBusqueda(filtros: FacturaFiltrosDTO): Promise<void> {
    return ejecutarConsulta(filtros, { pagina: 0, tamanio: paginacion.tamanio }, 'busqueda');
  }

  /** Después de guardar o de una acción: los mismos filtros y la misma página del resultado mostrado. */
  function recargarListado(): Promise<void> {
    return ejecutarConsulta(filtrosAplicados, { pagina: paginacion.paginaActual, tamanio: paginacion.tamanioMostrado }, 'recarga');
  }

  async function editar(id: number): Promise<void> {
    const carga = cargaEdicion.iniciar();
    try {
      const detalle = await servicio.obtenerFactura(id);
      if (!cargaEdicion.esVigente(carga)) return;
      if (detalle.estado !== 'BORRADOR') {
        region.error(`La factura ${id} ya no es un borrador (estado ${detalle.estado}); no se puede editar.`);
        return;
      }
      formulario.cargar(detalle);
    } catch (e) {
      if (!cargaEdicion.esVigente(carga)) return;
      region.error(mensajeDe(e));
    }
  }

  $('cancelar-edicion').addEventListener('click', () => {
    region.limpiar();
    cargaEdicion.invalidar();
    formulario.vaciar();
  });

  /** Emitir, anular y eliminar: una a la vez; bloquean las demás acciones y el guardado hasta terminar. */
  async function accionFila(accion: Accion, id: number): Promise<void> {
    if (guardando || accionEnCurso) return;
    region.limpiar();
    if (accion === 'editar') {
      await editar(id);
      return;
    }
    accionEnCurso = true;
    listado.deshabilitarAcciones(true);
    $<HTMLButtonElement>('guardar').disabled = true;
    let recargar = false;
    try {
      if (accion === 'emitir') {
        const f = await servicio.cambiarEstado(id, 'EMITIDA');
        region.exito(`Se emitió el borrador ${id}: número ${f.numeroFactura}.`);
        recargar = true;
      } else if (accion === 'eliminar') {
        if (!(await entorno.dialogos.confirmar(`¿Eliminar el borrador ${id}? Esta acción no se puede deshacer.`))) return;
        await servicio.eliminarFactura(id);
        region.exito(`Se eliminó el borrador ${id}.`);
        recargar = true;
      } else {
        const texto = await entorno.dialogos.pedirMotivo(`Motivo de la anulación de la factura ${id} (opcional, hasta 500 caracteres):`);
        if (texto === null) return;
        const motivo = validarMotivo(texto);
        if (motivo.errores.motivo !== undefined) {
          region.error(motivo.errores.motivo);
          return;
        }
        await servicio.cambiarEstado(id, 'ANULADA', motivo.datos);
        region.exito(`Se anuló la factura ${id}.`);
        recargar = true;
      }
    } catch (e) {
      region.error(mensajeDe(e));
    } finally {
      accionEnCurso = false;
      listado.deshabilitarAcciones(false);
      $<HTMLButtonElement>('guardar').disabled = false;
    }
    if (recargar) void recargarListado();
  }

  /** Bloquea el formulario y las acciones de fila mientras se guarda (y los libera en finally). */
  function bloquearGuardado(bloquear: boolean): void {
    guardando = bloquear;
    $('form-factura').setAttribute('aria-busy', String(bloquear));
    $<HTMLFieldSetElement>('contenido-formulario').disabled = bloquear;
    listado.deshabilitarAcciones(bloquear);
  }

  $('form-factura').addEventListener('submit', (evento) => {
    evento.preventDefault();
    void guardar();
  });

  async function guardar(): Promise<void> {
    if (guardando || accionEnCurso) return;
    region.limpiar();
    formulario.limpiarErrores();
    const resultado = validarFormulario(formulario.leer());
    if (resultado.datos === undefined) {
      formulario.mostrarErrores(resultado.errores);
      formulario.enfocarPrimerInvalido();
      return;
    }
    const modo = formulario.modo;
    bloquearGuardado(true);
    region.progreso(modo.tipo === 'edicion' ? 'Guardando cambios…' : 'Guardando borrador…');
    let guardado = false;
    try {
      if (modo.tipo === 'edicion') {
        const f = await servicio.actualizarFactura(modo.id, resultado.datos);
        region.exito(`Se actualizó el borrador ${f.id}.`);
      } else {
        const f = await servicio.crearFactura(resultado.datos);
        region.exito(`Se creó el borrador ${f.id}.`);
      }
      formulario.vaciar();
      guardado = true;
    } catch (e) {
      const sinCampo = e instanceof ApiError ? formulario.mostrarErrores(e.errores) : [];
      region.error(mensajeDe(e), sinCampo);
    } finally {
      bloquearGuardado(false);
    }
    if (guardado) void recargarListado();
    else formulario.enfocarPrimerInvalido();
  }

  const campoMonto = $<HTMLInputElement>('filtro-monto');
  const campoNumero = $<HTMLInputElement>('filtro-numero');
  const salidaCliente = $('filtro-cliente');

  /** Cliente del filtro: el id vive en data-id de la salida (sin input oculto, que no tendría etiqueta). */
  function ponerClienteFiltro(opcion: { id: number; etiqueta: string } | null): void {
    salidaCliente.textContent = opcion === null ? 'Todos' : opcion.etiqueta;
    if (opcion === null) salidaCliente.removeAttribute('data-id');
    else salidaCliente.setAttribute('data-id', String(opcion.id));
  }

  // Elegir el cliente del filtro no busca: solo la acción Buscar pide el listado.
  $('elegir-cliente-filtro').addEventListener('click', (evento) => {
    void buscarClientes(evento.currentTarget as HTMLElement, 'Buscar cliente').then((opcion) => {
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
    void ejecutarBusqueda(resultado.datos);
  });

  $('limpiar-filtros').addEventListener('click', () => {
    region.limpiar();
    $<HTMLFormElement>('form-filtros').reset();
    ponerClienteFiltro(null);
    limpiarInvalido(campoMonto);
    limpiarInvalido(campoNumero);
    void ejecutarBusqueda({});
  });

  async function cargarCatalogos(): Promise<void> {
    try {
      formulario.cargarCatalogos(await servicio.obtenerCatalogos());
    } catch (e) {
      region.error(mensajeDe(e));
    }
  }

  const listo = Promise.all([cargarCatalogos(), ejecutarBusqueda({})]).then(() => undefined);
  return { listo };
}
