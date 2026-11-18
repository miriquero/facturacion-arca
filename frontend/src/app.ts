import { iniciarAplicacion, type Dialogos } from './aplicacion.js';
import { leerApiBaseUrl } from './config.js';
import { FacturaService } from './services/FacturaService.js';
import type { VentanaModal } from './ui/explorador.js';
import type { Temporizador } from './ui/regionEstado.js';

// Único módulo que usa los globales del navegador: lee el meta, crea las dependencias reales y arranca.
const estadoApi = document.getElementById('estado-api');
try {
  const baseUrl = leerApiBaseUrl(document);
  if (estadoApi) estadoApi.textContent = baseUrl;

  // fetch se llama a través de una función flecha: llamar a window.fetch desligado falla en algunos navegadores.
  const servicio = new FacturaService(baseUrl, (url, init) => fetch(url, init));

  // confirm/prompt nativos: modales, operables por teclado y anunciados por los lectores de pantalla.
  const dialogos: Dialogos = {
    confirmar: (mensaje) => Promise.resolve(window.confirm(mensaje)),
    pedirMotivo: (mensaje) => Promise.resolve(window.prompt(mensaje, '')),
  };

  // showModal() vuelve inerte el resto de la página, maneja el foco y dispara `cancel` con Escape; close() lo cierra.
  const ventanaModal: VentanaModal = {
    abrir: (dialogo) => dialogo.showModal(),
    cerrar: (dialogo) => dialogo.close(),
  };

  const temporizador: Temporizador = {
    programar(fn, ms) {
      const id = window.setTimeout(fn, ms);
      return () => window.clearTimeout(id);
    },
  };

  iniciarAplicacion({ documento: document, servicio, dialogos, temporizador, ventanaModal, ventana: window });
} catch (error) {
  const region = document.getElementById('estado');
  if (region) {
    region.textContent = `Error: ${error instanceof Error ? error.message : 'no se pudo iniciar la aplicación.'}`;
  }
}
