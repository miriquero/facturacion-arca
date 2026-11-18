import { mensajeDe, type ApiFacturas } from '../contexto.js';
import type { Ruta } from '../navegacion/rutas.js';
import { ApiError } from '../services/ApiError.js';
import type { Formulario } from '../ui/formulario.js';
import type { RegionEstado } from '../ui/regionEstado.js';
import { ControlSolicitudes } from '../ui/solicitudes.js';
import { validarFormulario } from '../ui/validacion.js';
import type { Navegador, Pantalla } from './pantalla.js';

/** Pantalla del formulario de borrador: alta (`#/nueva`) y edición (`#/factura/{id}/editar`). */
export class PantallaFormulario implements Pantalla {
  readonly seccion: HTMLElement;
  readonly titulo: HTMLElement;
  private readonly form: HTMLElement;
  private readonly contenido: HTMLFieldSetElement;
  private readonly botonGuardar: HTMLButtonElement;
  private readonly cargas = new ControlSolicitudes();
  /** Borrador que se edita (null en el alta): destino de Cancelar. */
  private idEdicion: number | null = null;
  private guardando = false;
  private activa = false;
  /** Cambia en cada entrada y salida: un guardado que termina en otra visita no navega. */
  private visita = 0;

  constructor(
    doc: Document,
    private readonly servicio: ApiFacturas,
    private readonly region: RegionEstado,
    private readonly formulario: Formulario,
    private readonly navegador: Navegador,
  ) {
    const $ = <T extends HTMLElement>(id: string): T => {
      const e = doc.getElementById(id);
      if (!e) throw new Error(`Falta el elemento #${id} en el HTML.`);
      return e as T;
    };
    this.seccion = $('pantalla-formulario');
    this.titulo = $('titulo-formulario');
    this.form = $('form-factura');
    this.contenido = $<HTMLFieldSetElement>('contenido-formulario');
    this.botonGuardar = $<HTMLButtonElement>('guardar');
    this.form.addEventListener('submit', (evento) => {
      evento.preventDefault();
      void this.guardar();
    });
    $('cancelar-edicion').addEventListener('click', () => this.cancelar());
  }

  tituloDocumento(ruta: Ruta): string {
    return ruta.tipo === 'edicion' ? `Editar borrador ${ruta.id}` : 'Nueva factura';
  }

  entrar(ruta: Ruta): Promise<void> {
    this.activa = true;
    this.visita += 1;
    this.cargas.invalidar();
    // Cada visita empieza con el formulario vacío; la edición lo completa cuando llega el borrador.
    this.formulario.vaciar();
    if (ruta.tipo !== 'edicion') {
      this.idEdicion = null;
      return Promise.resolve();
    }
    this.idEdicion = ruta.id;
    this.titulo.textContent = `Editar borrador ${ruta.id}`;
    return this.cargarBorrador(ruta.id);
  }

  /** Una respuesta pendiente de la carga del borrador llega tarde: se descarta. */
  salir(): void {
    this.activa = false;
    this.visita += 1;
    this.cargas.invalidar();
    // Si se sale antes de que llegue el borrador, el título no debe quedar como «Editar borrador N».
    if (this.idEdicion !== null && this.formulario.modo.tipo === 'alta') this.formulario.vaciar();
  }

  /**
   * Pide el borrador y lo carga. Si ya no es borrador, va a su detalle con el error; si la carga falla, al listado
   * con el error. En ambos casos reemplaza la entrada del historial para que Atrás no vuelva a la edición imposible.
   */
  private async cargarBorrador(id: number): Promise<void> {
    const carga = this.cargas.iniciar();
    this.botonGuardar.disabled = true;
    this.form.setAttribute('aria-busy', 'true');
    try {
      const detalle = await this.servicio.obtenerFactura(id);
      if (!this.cargas.esVigente(carga)) return;
      if (detalle.estado !== 'BORRADOR') {
        this.formulario.vaciar();
        const texto = `La factura ${id} ya no es un borrador (estado ${detalle.estado}); no se puede editar.`;
        this.navegador.ir({ tipo: 'detalle', id }, { reemplazar: true, mensaje: { tipo: 'error', texto } });
        return;
      }
      this.formulario.cargar(detalle);
    } catch (e) {
      if (!this.cargas.esVigente(carga)) return;
      this.formulario.vaciar();
      this.navegador.ir({ tipo: 'listado' }, { reemplazar: true, mensaje: { tipo: 'error', texto: mensajeDe(e) } });
    } finally {
      if (this.cargas.esVigente(carga)) {
        this.botonGuardar.disabled = false;
        this.form.setAttribute('aria-busy', 'false');
      }
    }
  }

  /** Cancelar no envía nada: la edición vuelve al detalle del borrador y el alta al listado. */
  private cancelar(): void {
    this.navegador.ir(this.idEdicion === null ? { tipo: 'listado' } : { tipo: 'detalle', id: this.idEdicion });
  }

  /** Bloquea el formulario mientras se guarda y lo libera siempre en finally. */
  private bloquear(bloquear: boolean): void {
    this.guardando = bloquear;
    this.form.setAttribute('aria-busy', String(bloquear));
    this.contenido.disabled = bloquear;
  }

  private async guardar(): Promise<void> {
    if (this.guardando) return;
    this.region.limpiar();
    this.formulario.limpiarErrores();
    const resultado = validarFormulario(this.formulario.leer());
    if (resultado.datos === undefined) {
      this.formulario.mostrarErrores(resultado.errores);
      this.formulario.enfocarPrimerInvalido();
      return;
    }
    const modo = this.formulario.modo;
    const visita = this.visita;
    this.bloquear(true);
    this.region.progreso(modo.tipo === 'edicion' ? 'Guardando cambios…' : 'Guardando borrador…');
    let guardado: { id: number; texto: string } | null = null;
    try {
      if (modo.tipo === 'edicion') {
        const f = await this.servicio.actualizarFactura(modo.id, resultado.datos);
        guardado = { id: f.id, texto: `Se actualizó el borrador ${f.id}.` };
      } else {
        const f = await this.servicio.crearFactura(resultado.datos);
        guardado = { id: f.id, texto: `Se creó el borrador ${f.id}.` };
      }
      this.formulario.vaciar();
    } catch (e) {
      const sinCampo = e instanceof ApiError ? this.formulario.mostrarErrores(e.errores) : [];
      this.region.error(mensajeDe(e), sinCampo);
    } finally {
      this.bloquear(false);
    }
    if (guardado === null) {
      if (this.activa && this.visita === visita) this.formulario.enfocarPrimerInvalido();
    } else if (this.activa && this.visita === visita) {
      this.navegador.ir({ tipo: 'detalle', id: guardado.id }, { mensaje: { tipo: 'exito', texto: guardado.texto } });
    } else {
      // El usuario ya salió de la pantalla: solo se anuncia el resultado, sin navegar.
      this.region.exito(guardado.texto);
    }
  }
}
