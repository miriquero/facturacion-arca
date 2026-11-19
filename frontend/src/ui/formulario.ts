import type { Catalogos, FacturaDetalladaDTO, FacturaDetalleDTO, OpcionDTO } from '../models/factura.dto.js';
import { crear, limpiarInvalido, marcarInvalido, opcion, reemplazarHijos } from './dom.js';
import { netoEstimado, precioEnMoneda, type RenglonEstimable } from './estimacion.js';
import { formatearDecimal, formatearImporte } from './formato.js';
import type { FormularioTexto, RenglonTexto } from './validacion.js';

/** Cómo se elige una opción de catálogo: lo resuelve el explorador modal (se inyecta para poder probarlo). */
export interface Selector {
  cliente(disparador: HTMLElement): Promise<OpcionDTO | null>;
  articulo(disparador: HTMLElement, renglon: number): Promise<OpcionDTO | null>;
}

export type Modo = { tipo: 'alta' } | { tipo: 'edicion'; id: number };

const CAMPOS_CABECERA: Record<string, string> = {
  clienteId: 'elegir-cliente',
  condicionIvaId: 'condicion-iva',
  puntoVentaId: 'punto-venta',
  tipoMonedaId: 'tipo-moneda',
  fechaEmision: 'fecha-emision',
  observaciones: 'observaciones',
};

const CAMPOS_RENGLON = ['listaPrecioArticuloId', 'cantidad', 'porcentajeBonificacion', 'descripcion'] as const;

const SIN_ARTICULO = 'Sin elegir';

/** Formulario de alta y edición de borradores: cabecera, renglones, estimación y errores por campo. */
export class Formulario {
  private readonly cliente: HTMLElement;
  private readonly condicionIva: HTMLSelectElement;
  private readonly puntoVenta: HTMLSelectElement;
  private readonly tipoMoneda: HTMLSelectElement;
  private readonly fecha: HTMLInputElement;
  private readonly observaciones: HTMLTextAreaElement;
  private readonly contenedor: HTMLElement;
  private readonly total: HTMLElement;
  private readonly errorDetalles: HTMLElement;
  private readonly agregar: HTMLElement;
  private readonly titulo: HTMLElement;
  private readonly guardar: HTMLElement;
  private articulos: OpcionDTO[] = [];
  private clientes: OpcionDTO[] = [];
  /** Opción lista–artículo elegida en cada renglón (el id va además en data-id de su salida). */
  private readonly elegidas = new WeakMap<HTMLElement, OpcionDTO>();
  private monedas: OpcionDTO[] = [];
  private contador = 0;
  private modoActual: Modo = { tipo: 'alta' };

  constructor(private readonly doc: Document, private readonly selector: Selector) {
    const e = <T extends HTMLElement>(id: string): T => {
      const el = doc.getElementById(id);
      if (!el) throw new Error(`Falta el elemento #${id} en el HTML.`);
      return el as T;
    };
    this.cliente = e('cliente');
    this.condicionIva = e('condicion-iva');
    this.puntoVenta = e('punto-venta');
    this.tipoMoneda = e('tipo-moneda');
    this.fecha = e('fecha-emision');
    this.observaciones = e('observaciones');
    this.contenedor = e('renglones');
    this.total = e('total-estimado');
    this.errorDetalles = e('error-detalles');
    this.agregar = e('agregar-renglon');
    this.titulo = e('titulo-formulario');
    this.guardar = e('guardar');

    this.agregar.addEventListener('click', () => this.agregarRenglon());
    e('elegir-cliente').addEventListener('click', (evento) => {
      void this.selector.cliente(evento.currentTarget as HTMLElement).then((opcion) => {
        if (opcion !== null) this.ponerCliente(opcion);
      });
    });
    e('quitar-cliente').addEventListener('click', () => this.ponerCliente(null));
    this.contenedor.addEventListener('click', (evento) => {
      const objetivo = evento.target as Element;
      const quitar = objetivo.closest('button[data-quitar]');
      if (quitar) this.quitarRenglon(quitar.closest('fieldset.renglon') as HTMLElement);
      const elegir = objetivo.closest('button[data-elegir]');
      if (elegir) this.elegirArticulo(elegir as HTMLElement);
    });
    const recalcular = (): void => this.actualizarEstimacion();
    this.contenedor.addEventListener('input', recalcular);
    this.contenedor.addEventListener('change', recalcular);
    this.tipoMoneda.addEventListener('change', recalcular);
    this.vaciar();
  }

  get modo(): Modo {
    return this.modoActual;
  }

  cargarCatalogos(c: Catalogos): void {
    const llenar = (select: HTMLSelectElement, opciones: OpcionDTO[], vacia: string): void => {
      const nodos = opciones.map((o) => opcion(this.doc, String(o.id), o.etiqueta));
      reemplazarHijos(select, [opcion(this.doc, '', vacia), ...nodos]);
    };
    this.clientes = c.clientes;
    llenar(this.condicionIva, c.condicionesIva, 'Elegí una condición');
    llenar(this.puntoVenta, c.puntosVenta, 'Elegí un punto de venta');
    llenar(this.tipoMoneda, c.tiposMoneda, 'Elegí una moneda');
    this.articulos = c.listaPrecioArticulos;
    this.monedas = c.tiposMoneda;
    this.actualizarEstimacion();
  }

  /** Vuelve al modo alta con los campos vacíos y un renglón en blanco. */
  vaciar(): void {
    this.ponerCliente(null);
    this.condicionIva.value = '';
    this.puntoVenta.value = '';
    this.tipoMoneda.value = '';
    this.fecha.value = '';
    this.observaciones.value = '';
    this.limpiarErrores();
    reemplazarHijos(this.contenedor, []);
    this.agregarRenglon();
    this.ponerModo({ tipo: 'alta' });
  }

  /** Reconstruye cabecera y renglones desde el detalle recibido y entra en modo edición. */
  cargar(d: FacturaDetalladaDTO): void {
    this.limpiarErrores();
    this.ponerCliente(this.opcionDeCliente(d));
    this.condicionIva.value = String(d.condicionIvaId);
    this.puntoVenta.value = String(d.puntoVentaId);
    this.tipoMoneda.value = String(d.tipoMonedaId);
    this.fecha.value = d.fechaEmision;
    this.observaciones.value = d.observaciones ?? '';
    reemplazarHijos(this.contenedor, []);
    for (const det of d.detalles) {
      const r = this.agregarRenglon();
      this.ponerArticulo(r, this.opcionDeArticulo(det, d.codigoMoneda));
      this.campo(r, 'cantidad').value = String(det.cantidad);
      this.campo(r, 'porcentajeBonificacion').value = String(det.porcentajeBonificacion);
      this.campo(r, 'descripcion').value = det.descripcion ?? '';
    }
    this.ponerModo({ tipo: 'edicion', id: d.id });
    this.actualizarEstimacion();
    this.titulo.focus();
  }

  /** Texto crudo de los campos, tal cual lo ve el usuario (lo valida validacion.ts). */
  leer(): FormularioTexto {
    return {
      clienteId: this.cliente.getAttribute('data-id') ?? '',
      condicionIvaId: this.condicionIva.value,
      puntoVentaId: this.puntoVenta.value,
      tipoMonedaId: this.tipoMoneda.value,
      fechaEmision: this.fecha.value,
      observaciones: this.observaciones.value,
      renglones: this.renglones().map((r): RenglonTexto => ({
        listaPrecioArticuloId: this.campo(r, 'listaPrecioArticuloId').getAttribute('data-id') ?? '',
        cantidad: this.campo(r, 'cantidad').value,
        porcentajeBonificacion: this.campo(r, 'porcentajeBonificacion').value,
        descripcion: this.campo(r, 'descripcion').value,
      })),
    };
  }

  /** Pinta errores locales o del servidor (misma notación que la API). Devuelve los que no tienen campo. */
  mostrarErrores(errores: Readonly<Record<string, string>>): string[] {
    const sinCampo: string[] = [];
    for (const [clave, mensaje] of Object.entries(errores)) {
      const destino = this.destino(clave);
      if (destino) marcarInvalido(destino, mensaje);
      else if (clave === 'detalles') {
        this.errorDetalles.textContent = mensaje;
        this.errorDetalles.hidden = false;
      } else sinCampo.push(`${clave}: ${mensaje}`);
    }
    return sinCampo;
  }

  limpiarErrores(): void {
    for (const campo of this.doc.querySelectorAll<HTMLElement>('#form-factura [aria-invalid="true"]')) limpiarInvalido(campo);
    this.errorDetalles.hidden = true;
    this.errorDetalles.textContent = '';
  }

  enfocarPrimerInvalido(): void {
    const primero = this.doc.querySelector<HTMLElement>('#form-factura [aria-invalid="true"]');
    if (primero) primero.focus();
    else if (!this.errorDetalles.hidden) this.agregar.focus();
  }

  agregarRenglon(): HTMLElement {
    const doc = this.doc;
    const k = ++this.contador;
    const r = crear(doc, 'fieldset', undefined, { class: 'renglon' });
    r.appendChild(crear(doc, 'legend'));
    const campoDe = (nombre: string, etiqueta: string, control: HTMLElement): void => {
      control.id = `r${k}-${nombre}`;
      control.setAttribute('name', nombre);
      const div = crear(doc, 'div', undefined, { class: 'campo' });
      div.appendChild(crear(doc, 'label', etiqueta, { for: control.id, 'data-etiqueta': etiqueta }));
      div.appendChild(control);
      r.appendChild(div);
    };
    campoDe('listaPrecioArticuloId', 'Artículo', crear(doc, 'output', SIN_ARTICULO));
    const campoArticulo = r.querySelector('div.campo') as HTMLElement;
    campoArticulo.classList.add('articulo-elegido');
    campoArticulo.appendChild(crear(doc, 'small', undefined, { class: 'lista-articulo', hidden: '' }));
    campoArticulo.appendChild(crear(doc, 'button', undefined, { type: 'button', id: `r${k}-elegir`, 'data-elegir': '' }));
    campoDe('cantidad', 'Cantidad', crear(doc, 'input', undefined, { type: 'number', step: 'any', min: '0' }));
    campoDe('porcentajeBonificacion', 'Bonificación %', crear(doc, 'input', undefined, { type: 'number', step: 'any', min: '0', max: '100' }));
    campoDe('descripcion', 'Descripción (opcional, hasta 255 caracteres)', crear(doc, 'input', undefined, { type: 'text', maxlength: '255' }));
    const precio = crear(doc, 'p', 'Precio de catálogo: ', { class: 'dato-renglon' });
    precio.appendChild(crear(doc, 'output', undefined, { name: 'precio' }));
    r.appendChild(precio);
    const subtotal = crear(doc, 'p', 'Subtotal (sin IVA): ', { class: 'dato-renglon' });
    subtotal.appendChild(crear(doc, 'output', undefined, { name: 'subtotal' }));
    r.appendChild(subtotal);
    r.appendChild(crear(doc, 'button', undefined, { type: 'button', 'data-quitar': '' }));
    this.contenedor.appendChild(r);
    this.renumerar();
    this.actualizarEstimacion();
    return r;
  }

  /** El id elegido vive en data-id de la salida; sin id es Consumidor Final. */
  private ponerCliente(opcion: OpcionDTO | null): void {
    this.cliente.textContent = opcion === null ? 'Consumidor Final' : opcion.etiqueta;
    if (opcion === null) this.cliente.removeAttribute('data-id');
    else this.cliente.setAttribute('data-id', String(opcion.id));
  }

  /** Etiqueta del cliente del detalle: la del catálogo cargado o, si no figura, la denominación y el CUIT del detalle. */
  private opcionDeCliente(d: FacturaDetalladaDTO): OpcionDTO | null {
    if (d.clienteId === null) return null;
    const delCatalogo = this.clientes.find((c) => c.id === d.clienteId);
    if (delCatalogo) return delCatalogo;
    const etiqueta = d.clienteCuitCuil ? `${d.clienteDenominacion} (${d.clienteCuitCuil})` : d.clienteDenominacion;
    return { id: d.clienteId, etiqueta, valor: null, codigoMoneda: null };
  }

  private quitarRenglon(r: HTMLElement): void {
    const lista = this.renglones();
    const posicion = lista.indexOf(r);
    r.remove();
    this.renumerar();
    this.actualizarEstimacion();
    const siguiente = this.renglones()[posicion];
    if (siguiente) (siguiente.querySelector('button[data-elegir]') as HTMLElement).focus();
    else this.agregar.focus();
  }

  private renglones(): HTMLElement[] {
    return [...this.contenedor.querySelectorAll<HTMLElement>('fieldset.renglon')];
  }

  private campo(r: HTMLElement, nombre: string): HTMLInputElement & HTMLSelectElement {
    return r.querySelector(`[name="${nombre}"]`) as HTMLInputElement & HTMLSelectElement;
  }

  /** Legend, etiquetas y botón dicen "renglón N" según la posición actual; los id no cambian. */
  private renumerar(): void {
    this.renglones().forEach((r, i) => {
      const n = i + 1;
      (r.querySelector('legend') as HTMLElement).textContent = `Renglón ${n}`;
      for (const l of r.querySelectorAll<HTMLElement>('label[data-etiqueta]')) {
        l.textContent = `${l.getAttribute('data-etiqueta')} (renglón ${n})`;
      }
      (r.querySelector('button[data-quitar]') as HTMLElement).textContent = `Quitar renglón ${n}`;
      this.rotularBotonArticulo(r, n);
    });
  }

  private rotularBotonArticulo(r: HTMLElement, numero: number): void {
    const boton = r.querySelector('button[data-elegir]') as HTMLButtonElement;
    const elegido = this.elegidas.has(r);
    boton.textContent = elegido ? 'Cambiar' : `Elegir artículo (renglón ${numero})`;
    boton.setAttribute('aria-label', `${elegido ? 'Cambiar' : 'Elegir'} artículo (renglón ${numero})`);
  }

  /** Abre el explorador de artículos del renglón; cancelar no cambia nada. */
  private elegirArticulo(boton: HTMLElement): void {
    const r = boton.closest('fieldset.renglon') as HTMLElement;
    const numero = this.renglones().indexOf(r) + 1;
    void this.selector.articulo(boton, numero).then((opcion) => {
      if (opcion !== null) this.ponerArticulo(r, opcion);
    });
  }

  private ponerArticulo(r: HTMLElement, opcion: OpcionDTO): void {
    const salida = this.campo(r, 'listaPrecioArticuloId');
    // El catálogo devuelve «código - artículo (código de lista)».
    const etiqueta = /^(.*) \(([A-Z][A-Z0-9_-]*)\)$/.exec(opcion.etiqueta);
    salida.textContent = etiqueta?.[1] ?? opcion.etiqueta;
    const lista = r.querySelector('.lista-articulo') as HTMLElement;
    lista.textContent = etiqueta ? `Lista: ${etiqueta[2]}` : '';
    lista.hidden = etiqueta === null;
    salida.setAttribute('data-id', String(opcion.id));
    this.elegidas.set(r, opcion);
    this.rotularBotonArticulo(r, this.renglones().indexOf(r) + 1);
    this.actualizarEstimacion();
  }

  /**
   * Opción del renglón del detalle: la del catálogo cargado (precio de lista, en pesos) o, si no figura, código,
   * descripción y precio del detalle. Ese precio ya está en la moneda de la factura: se marca con su código
   * para que la estimación no lo convierta una segunda vez.
   */
  private opcionDeArticulo(det: FacturaDetalleDTO, codigoMonedaFactura: string): OpcionDTO {
    const delCatalogo = this.articulos.find((a) => a.id === det.listaPrecioArticuloId);
    if (delCatalogo) return delCatalogo;
    const etiqueta = det.descripcion ? `${det.articuloCodigo} - ${det.descripcion}` : det.articuloCodigo;
    return { id: det.listaPrecioArticuloId, etiqueta, valor: det.precioUnitario, codigoMoneda: codigoMonedaFactura };
  }

  private destino(clave: string): HTMLElement | null {
    const cabecera = CAMPOS_CABECERA[clave];
    if (cabecera) return this.doc.getElementById(cabecera);
    const m = /^detalles\[(\d+)\]\.(\w+)$/.exec(clave);
    if (!m || !(CAMPOS_RENGLON as readonly string[]).includes(m[2] as string)) return null;
    const r = this.renglones()[Number(m[1])];
    if (!r) return null;
    // El error del artículo se marca en el botón que abre el explorador (la salida no es un control).
    return m[2] === 'listaPrecioArticuloId' ? (r.querySelector('button[data-elegir]') as HTMLElement) : this.campo(r, m[2] as string);
  }

  private ponerModo(modo: Modo): void {
    this.modoActual = modo;
    this.titulo.textContent = modo.tipo === 'edicion' ? `Editar borrador ${modo.id}` : 'Nuevo borrador';
    this.guardar.textContent = modo.tipo === 'edicion' ? 'Guardar cambios' : 'Guardar borrador';
  }

  /** Pesos que vale una unidad de la moneda (la cotización viaja en el catálogo de monedas); 1 si no se conoce. */
  private cotizacionDe(codigoMoneda: string | null): number {
    const moneda = codigoMoneda === null ? undefined : this.monedas.find((m) => m.codigoMoneda === codigoMoneda);
    return moneda?.valor ?? 1;
  }

  /**
   * Precio de catálogo por renglón y total neto estimado sin IVA (informativo). El precio de lista está en pesos:
   * con otra moneda elegida se divide por su cotización, como hace el servidor al guardar.
   */
  private actualizarEstimacion(): void {
    const monedaElegida = this.monedas.find((m) => String(m.id) === this.tipoMoneda.value);
    const cotizacionDestino = this.cotizacionDe(monedaElegida?.codigoMoneda ?? null);
    const formato = (importe: number): string =>
      monedaElegida?.codigoMoneda ? formatearImporte(importe, monedaElegida.codigoMoneda) : formatearDecimal(importe);
    const estimables: RenglonEstimable[] = this.renglones().map((r) => {
      const salida = r.querySelector('output[name="precio"]') as HTMLElement;
      const opcionElegida = this.elegidas.get(r);
      const cantidadTexto = this.campo(r, 'cantidad').value.trim();
      const cantidad = cantidadTexto === '' ? NaN : Number(cantidadTexto);
      const bonifTexto = this.campo(r, 'porcentajeBonificacion').value.trim();
      const bonificacion = bonifTexto !== '' && Number.isFinite(Number(bonifTexto)) ? Number(bonifTexto) : 0;
      const completo = opcionElegida !== undefined && opcionElegida.valor !== null && Number.isFinite(cantidad) && cantidad > 0;
      const precio = completo
        ? precioEnMoneda((opcionElegida.valor as number) * this.cotizacionDe(opcionElegida.codigoMoneda), cotizacionDestino)
        : null;
      salida.textContent = precio !== null ? formatearDecimal(precio) : '';
      const salidaSubtotal = r.querySelector('output[name="subtotal"]') as HTMLElement;
      salidaSubtotal.textContent = precio !== null ? formato(netoEstimado([{ precio, cantidad, bonificacion }])) : '';
      return {
        precio,
        cantidad: completo ? cantidad : null,
        bonificacion,
      };
    });
    const neto = netoEstimado(estimables);
    this.total.textContent = formato(neto);
  }
}
