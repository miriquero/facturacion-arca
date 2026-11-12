import { leerApiBaseUrl } from './config.js';

const estado = document.getElementById('estado-api');
if (estado) {
  estado.textContent = leerApiBaseUrl(document);
}
