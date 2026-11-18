import { mensajeDe, type ApiFacturas, type Dialogos } from '../contexto.js';
import type { FacturaDetalladaDTO } from '../models/factura.dto.js';
import { hrefDe, type Ruta } from '../navegacion/rutas.js';
import { construirDetalle, tituloDetalle } from '../ui/detalle.js';
import { crear, reemplazarHijos } from '../ui/dom.js';
import type { RegionEstado } from '../ui/regionEstado.js';
import { ControlSolicitudes } from '../ui/solicitudes.js';
import { validarMotivo } from '../ui/validacion.js';
import type { Navegador, Pantalla } from './pantalla.js';

type Accion = 'emitir' | 'anular' | 'eliminar';

/** Pantalla del detalle de una factura (`#/factura/{id}`). Su contenido se reconstruye con nodos en cada carga. */
export class PantallaDetalle implements Pantalla {
  readonly seccion: HTMLElement;
  readonly titulo: HTMLElement;
  private readonly contenido: HTMLElement;
  private readonly acciones: HTMLElement;
  private readonly cargas = new ControlSolicitudes();
  private id = 0;
  private activa = false;
  private accionEnCurso = false;

  constructor(
    private readonly doc: Document,
    private readonly servicio: ApiFacturas,
    private readonly region: RegionEstado,
    private readonly dialogos: Dialogos,
    private readonly navegador: Navegador,
  ) {
    const $ = (id: string): HTMLElement => {
      const e = doc.getElementById(id);
      if (!e) throw new Error(`Falta el elemento #${id} en el HTML.`);
      return e;
    };
    this.seccion = $('pantalla-detalle');
    this.titulo = $('titulo-detalle');
    this.contenido = $('detalle-contenido');
    this.acciones = $('detalle-acciones');
    this.acciones.addEventListener('click', (evento) => {
      const boton = (evento.target as Element).closest('button[data-accion]');
      if (boton === null || !this.acciones.contains(boton)) return;
      void this.ejecutar(boton.getAttribute('data-accion') as Accion, boton as HTMLElement);
    });
  }

  tituloDocumento(ruta: Ruta): string {
    return ruta.tipo === 'detalle' ? `Factura ${ruta.id}` : 'Factura';
  }

  entrar(ruta: Ruta): Promise<void> {
    if (ruta.tipo !== 'detalle') return Promise.resolve();
    this.activa = true;
    this.id = ruta.id;
    this.titulo.textContent = `Factura ${ruta.id}`;
    reemplazarHijos(this.contenido, []);
    this.mostrarAcciones();
    return this.cargar(ruta.id);
  }

  /** Respuesta pendiente al salir: se descarta y la sección deja de figurar como ocupada. */
  salir(): void {
    this.activa = false;
    this.cargas.invalidar();
    this.seccion.setAttribute('aria-busy', 'false');
  }

  /** Pide el detalle y lo muestra. Una respuesta obsoleta (otra carga o salida de la pantalla) se descarta. */
  private async cargar(id: number): Promise<void> {
    const carga = this.cargas.iniciar();
    this.seccion.setAttribute('aria-busy', 'true');
    try {
      const detalle = await this.servicio.obtenerFactura(id);
      if (!this.cargas.esVigente(carga)) return;
      this.titulo.textContent = tituloDetalle(detalle);
      reemplazarHijos(this.contenido, [construirDetalle(this.doc, detalle)]);
      this.mostrarAcciones(detalle);
    } catch (e) {
      if (!this.cargas.esVigente(carga)) return;
      this.region.error(mensajeDe(e));
    } finally {
      if (this.cargas.esVigente(carga)) this.seccion.setAttribute('aria-busy', 'false');
    }
  }

  /** Emitir, anular y eliminar: una a la vez; las demás acciones quedan deshabilitadas y se liberan siempre en finally. */
  private async ejecutar(accion: Accion, origen: HTMLElement): Promise<void> {
    if (this.accionEnCurso) return;
    const id = this.id;
    this.region.limpiar();
    this.accionEnCurso = true;
    this.deshabilitarAcciones(true);
    try {
      if (accion === 'emitir') {
        const f = await this.servicio.cambiarEstado(id, 'EMITIDA');
        this.region.exito(`Se emitió el borrador ${id}: número ${f.numeroFactura}.`);
        await this.recargar(id);
      } else if (accion === 'eliminar') {
        if (!(await this.dialogos.confirmar(`¿Eliminar el borrador ${id}? Esta acción no se puede deshacer.`))) return;
        await this.servicio.eliminarFactura(id);
        const texto = `Se eliminó el borrador ${id}.`;
        // El mensaje viaja con la navegación; si el usuario ya salió de la pantalla, solo se anuncia.
        if (this.activa && this.id === id) this.navegador.ir({ tipo: 'listado' }, { mensaje: { tipo: 'exito', texto } });
        else this.region.exito(texto);
      } else if (accion === 'anular') {
        const texto = await this.dialogos.pedirMotivo(`Motivo de la anulación de la factura ${id} (opcional, hasta 500 caracteres):`);
        if (texto === null) return;
        const motivo = validarMotivo(texto);
        if (motivo.errores.motivo !== undefined) {
          this.region.error(motivo.errores.motivo);
          return;
        }
        await this.servicio.cambiarEstado(id, 'ANULADA', motivo.datos);
        this.region.exito(`Se anuló la factura ${id}.`);
        await this.recargar(id);
      }
    } catch (e) {
      this.region.error(mensajeDe(e));
    } finally {
      this.accionEnCurso = false;
      this.deshabilitarAcciones(false);
      // Un botón deshabilitado pierde el foco en el navegador: se lo devuelve al botón, o al h1 si ya se reemplazó.
      if (this.activa) {
        if (origen.isConnected) origen.focus();
        else this.titulo.focus();
      }
    }
  }

  /** Vuelve a pedir el detalle de `id` si el usuario sigue en él; no toca la región de estado. */
  private recargar(id: number): Promise<void> {
    return this.activa && this.id === id ? this.cargar(id) : Promise.resolve();
  }

  private deshabilitarAcciones(deshabilitar: boolean): void {
    for (const b of this.acciones.querySelectorAll('button')) b.disabled = deshabilitar;
  }

  /** Barra de acciones según el estado de la factura; sin factura cargada solo «Volver al listado». */
  private mostrarAcciones(detalle?: FacturaDetalladaDTO): void {
    const doc = this.doc;
    const nodos: HTMLElement[] = [];
    // El nombre accesible de cada acción incluye el objeto y el id (el texto extra queda visualmente oculto).
    const nombrar = (control: HTMLElement, objeto: string, id: number): HTMLElement => {
      control.appendChild(crear(doc, 'span', ` ${objeto} ${id}`, { class: 'visualmente-oculto' }));
      return control;
    };
    const boton = (accion: Accion, texto: string, objeto: string, id: number): HTMLElement =>
      nombrar(crear(doc, 'button', texto, { type: 'button', 'data-accion': accion }), objeto, id);
    if (detalle !== undefined) {
      const id = detalle.id;
      if (detalle.estado === 'BORRADOR') {
        nodos.push(nombrar(crear(doc, 'a', 'Editar', { href: hrefDe({ tipo: 'edicion', id }) }), 'borrador', id));
        nodos.push(boton('emitir', 'Emitir', 'borrador', id));
        nodos.push(boton('eliminar', 'Eliminar', 'borrador', id));
      } else {
        // Informada a ARCA (EMITIDA o ANULADA): nunca editar ni eliminar; anular solo si sigue EMITIDA.
        if (detalle.estado === 'EMITIDA') nodos.push(boton('anular', 'Anular', 'factura', id));
        nodos.push(nombrar(crear(doc, 'a', 'Imprimir PDF', { href: this.servicio.urlPdfFactura(id), class: 'enlace-pdf' }), 'factura', id));
      }
    }
    nodos.push(crear(doc, 'a', 'Volver al listado', { href: hrefDe({ tipo: 'listado' }) }));
    reemplazarHijos(this.acciones, nodos);
    this.deshabilitarAcciones(this.accionEnCurso);
  }
}
