import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ControlSolicitudes } from '../dist/ui/solicitudes.js';

test('iniciar dos veces: solo el segundo id es vigente', () => {
  const c = new ControlSolicitudes();
  const a = c.iniciar();
  const b = c.iniciar();
  assert.notEqual(a, b);
  assert.equal(c.esVigente(a), false);
  assert.equal(c.esVigente(b), true);
});

test('invalidar deja sin vigente al ultimo', () => {
  const c = new ControlSolicitudes();
  const a = c.iniciar();
  c.invalidar();
  assert.equal(c.esVigente(a), false);
});
