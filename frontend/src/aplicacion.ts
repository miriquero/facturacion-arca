import type { Catalogos, FacturaDetalladaDTO, FacturaFiltrosDTO, FacturaReporteDTO, FacturaRequestDTO, FormatoReporte } from './models/factura.dto.js';
import { ApiError } from './services/ApiError.js';
import { limpiarInvalido, marcarInvalido } from './ui/dom.js';
import { Formulario } from './ui/formulario.js';
import { Listado, type Accion } from './ui/listado.js';
import { RegionEstado, type Temporizador } from './ui/regionEstado.js';
import { ControlSolicitudes } from './ui/solicitudes.js';
import { validarFiltros, validarFormulario, validarMotivo } from './ui/validacion.js';

/** Interfaz pública del servicio HTTP que usa la aplicación (la satisface FacturaService y los dobles de prueba). */
export interface ApiFacturas {
  obtenerFacturas(filtros: FacturaFiltrosDTO): Promise<FacturaReporteDTO[]>;
  cambiarEstado(id: number, estado: 'EMITIDA' | 'ANULADA', observaciones?: string): Promise<FacturaReporteDTO>;
  eliminarFactura(id: number): Promise<void>;
  obtenerFactura(id: number): Promise<FacturaDetalladaDTO>;
  obtenerCatalogos(): Promise<Catalogos>;
  crearFactura(req: FacturaRequestDTO): Promise<FacturaReporteDTO>;
  actualizarFactura(id: number, req: FacturaRequestDTO): Promise<FacturaReporteDTO>;
  urlReporte(filtros: FacturaFiltrosDTO, formato: FormatoReporte): string;
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
  const formulario = new Formulario(doc);
  const busquedas = new ControlSolicitudes();
  const cargaEdicion = new ControlSolicitudes();
  let filtrosAplicados: FacturaFiltrosDTO = {};
  let guardando = false;
  let accionEnCurso = false;

  const listado = new Listado(doc, $('filas'), $('contador'), $('sin-resultados'), (accion, id) => void accionFila(accion, id));

  /** Tabla, contador y enlaces cambian juntos con una misma respuesta. */
  function aplicarResultado(filas: FacturaReporteDTO[], filtros: FacturaFiltrosDTO): void {
    filtrosAplicados = filtros;
    listado.mostrar(filas);
    $('descarga-pdf').setAttribute('href', servicio.urlReporte(filtros, 'pdf'));
    $('descarga-xlsx').setAttribute('href', servicio.urlReporte(filtros, 'xlsx'));
  }

  async function ejecutarBusqueda(filtros: FacturaFiltrosDTO): Promise<void> {
    const id = busquedas.iniciar();
    try {
      const filas = await servicio.obtenerFacturas(filtros);
      if (!busquedas.esVigente(id)) return;
      aplicarResultado(filas, filtros);
    } catch (e) {
      if (!busquedas.esVigente(id)) return;
      region.error(mensajeDe(e));
    }
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
    if (recargar) void ejecutarBusqueda(filtrosAplicados);
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
    if (guardado) void ejecutarBusqueda(filtrosAplicados);
    else formulario.enfocarPrimerInvalido();
  }

  const campoMonto = $<HTMLInputElement>('filtro-monto');
  $('form-filtros').addEventListener('submit', (evento) => {
    evento.preventDefault();
    region.limpiar();
    const resultado = validarFiltros({
      fechaDesde: $<HTMLInputElement>('filtro-desde').value,
      fechaHasta: $<HTMLInputElement>('filtro-hasta').value,
      estado: $<HTMLSelectElement>('filtro-estado').value,
      montoMinimo: campoMonto.value,
    });
    limpiarInvalido(campoMonto);
    if (resultado.datos === undefined) {
      marcarInvalido(campoMonto, resultado.errores.montoMinimo ?? 'Valor inválido.');
      campoMonto.focus();
      return;
    }
    void ejecutarBusqueda(resultado.datos);
  });

  $('limpiar-filtros').addEventListener('click', () => {
    region.limpiar();
    $<HTMLFormElement>('form-filtros').reset();
    limpiarInvalido(campoMonto);
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
