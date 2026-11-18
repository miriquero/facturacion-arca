/** Todo texto externo entra por textContent / Option: nunca se interpreta como HTML. */
export function crear(doc: Document, etiqueta: string, texto?: string, atributos?: Record<string, string>): HTMLElement {
  const elemento = doc.createElement(etiqueta);
  if (texto !== undefined) elemento.textContent = texto;
  if (atributos) {
    for (const [nombre, valor] of Object.entries(atributos)) elemento.setAttribute(nombre, valor);
  }
  return elemento;
}

export function opcion(doc: Document, valor: string, etiqueta: string): HTMLOptionElement {
  const o = doc.createElement('option');
  o.value = valor;
  o.textContent = etiqueta;
  return o;
}

export function reemplazarHijos(contenedor: Element, nodos: Node[]): void {
  contenedor.replaceChildren(...nodos);
}

/** Marca un campo como inválido: aria-invalid y aria-describedby apuntan a un texto de error junto al campo. */
export function marcarInvalido(campo: HTMLElement, mensaje: string): void {
  const doc = campo.ownerDocument;
  const idError = `${campo.id}-error`;
  let error = doc.getElementById(idError);
  if (!error) {
    error = crear(doc, 'p', undefined, { id: idError, class: 'error-campo' });
    campo.insertAdjacentElement('afterend', error);
  }
  error.textContent = mensaje;
  campo.setAttribute('aria-invalid', 'true');
  campo.setAttribute('aria-describedby', idError);
}

export function limpiarInvalido(campo: HTMLElement): void {
  campo.removeAttribute('aria-invalid');
  campo.removeAttribute('aria-describedby');
  campo.ownerDocument.getElementById(`${campo.id}-error`)?.remove();
}
