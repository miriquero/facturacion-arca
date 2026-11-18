export type Ruta =
  | { tipo: 'listado' }
  | { tipo: 'alta' }
  | { tipo: 'detalle'; id: number }
  | { tipo: 'edicion'; id: number }
  | { tipo: 'desconocida'; fragmento: string };

const ID = String.raw`([1-9]\d*)`;
const DETALLE = new RegExp(`^#/factura/${ID}$`);
const EDICION = new RegExp(`^#/factura/${ID}/editar$`);

/** Entero positivo escrito solo con dígitos y representable sin perder precisión; si no, null. */
function aId(texto: string): number | null {
  const n = Number(texto);
  return Number.isSafeInteger(n) ? n : null;
}

/** Fragmento de la URL (`location.hash`) -> pantalla. Todo lo que no figura en la tabla de rutas es desconocida. */
export function interpretarRuta(hash: string): Ruta {
  if (hash === '' || hash === '#/') return { tipo: 'listado' };
  if (hash === '#/nueva') return { tipo: 'alta' };
  const detalle = DETALLE.exec(hash);
  if (detalle !== null) {
    const id = aId(detalle[1] as string);
    if (id !== null) return { tipo: 'detalle', id };
  }
  const edicion = EDICION.exec(hash);
  if (edicion !== null) {
    const id = aId(edicion[1] as string);
    if (id !== null) return { tipo: 'edicion', id };
  }
  return { tipo: 'desconocida', fragmento: hash };
}

/** Pantalla -> fragmento (inversa de interpretarRuta para las rutas conocidas). */
export function hrefDe(ruta: Ruta): string {
  switch (ruta.tipo) {
    case 'listado':
      return '#/';
    case 'alta':
      return '#/nueva';
    case 'detalle':
      return `#/factura/${ruta.id}`;
    case 'edicion':
      return `#/factura/${ruta.id}/editar`;
    case 'desconocida':
      return ruta.fragmento;
  }
}
