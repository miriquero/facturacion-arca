/**
 * Lee la URL base de la API desde <meta name="api-base-url"> (RN-CF-04).
 * Lanza un error explicito si el meta no existe o esta vacio.
 */
export function leerApiBaseUrl(doc: Document): string {
  const contenido = doc
    .querySelector('meta[name="api-base-url"]')
    ?.getAttribute('content')
    ?.trim();

  if (!contenido) {
    throw new Error('Falta la configuracion <meta name="api-base-url"> en el HTML o esta vacia.');
  }
  return contenido;
}
