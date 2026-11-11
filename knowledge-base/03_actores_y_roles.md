# Actores y Roles

> **No hay login ni RBAC.** La consigna excluye explícitamente el inicio de sesión. Esta sección describe actores reales y deja claro qué NO es un control de acceso.

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|-------|-------------|-----------------|
| Estudiante / desarrollador (Martín) | Construye el sistema por etapas con TDD; entrega tags y defiende | Git, Gradle, npm, `requests.http`, informes `docs/etapa-N.md` |
| Usuario de auditoría | Identidad *declarada* en el header `X-Usuario` (`sistema`, `acortez` en los datos de ejemplo). Existe como fila de la entidad `Usuario` | Cualquier request HTTP; el interceptor la resuelve y la deja en `UsuarioActual` (ThreadLocal) para los callbacks de auditoría |
| Docente evaluador | Corrige cada etapa por rúbrica y toma la defensa individual (25 min) | Clona el repo/tag, ejecuta README, revisa informes, pregunta y propone un cambio breve |
| Usuario web | Persona que opera el frontend en el navegador | UI TypeScript (consulta, formulario, emitir/anular/eliminar, descargas); el frontend envía `X-Usuario: sistema` |
| Cátedra (indirecto) | Publica kits de nivelación opcionales entre etapas | Su uso debe declararse en el informe |

## Matriz de permisos

**No existe.** Cualquier cliente que alcance la API puede ejecutar cualquier operación. No hay roles, ni permisos por recurso, ni sesiones, ni tokens.

| Operación | Quién puede | Restricción real |
|-----------|-------------|------------------|
| Todas las de `/api/**` | Cualquiera | Solo las **reglas de estado** (p. ej. editar/eliminar solo BORRADOR), no reglas de identidad |

## Por qué `X-Usuario` NO es autenticación

- Es un encabezado que **cualquier cliente puede fijar a mano** (`curl -H "X-Usuario: acortez"`); no hay secreto, firma ni verificación de identidad.
- Si falta, está vacío o no corresponde a un usuario existente, se **cae a `sistema`** (`facturacion.usuario-por-defecto`): el request nunca se rechaza por esto (RN-AU-02).
- Su única función es **dejar rastro de auditoría** (usuario de carga/modificación/baja). `Usuario.clave` existe en el modelo pero **no se usa para autenticar**; `Usuario` no implementa login.

## Por qué CORS NO es autenticación

- CORS es una política del **navegador** que decide qué *orígenes web* pueden leer respuestas de la API desde JavaScript. Se configura para `localhost` y `127.0.0.1` en puertos 5173 y 3000 (RN-CF-01) para que el frontend separado funcione.
- No impide que `curl`, Postman o un script de otro host llamen a la API: esos clientes no aplican CORS. Tampoco identifica al usuario. Es una restricción de lectura cross-origin en navegadores, no un control de acceso. (Pregunta de defensa probable.)

## Rutas públicas

Todas son públicas (sin autenticación):

- `GET /api/facturas`, `GET /api/facturas/{id}`, `GET /api/facturas/reporte`
- `GET /api/catalogos/{puntos-venta | clientes | condiciones-iva | tipos-moneda | lista-precio-articulos}`
- `POST /api/facturas`, `PUT /api/facturas/{id}`, `PATCH /api/facturas/{id}/estado`, `DELETE /api/facturas/{id}`
- `/h2-console` (solo con H2; desactivada en el perfil `postgres`)
- Frontend estático en `http://localhost:5173`

> Riesgo aceptado: es un ejercicio académico local. Cualquier despliegue real exigiría autenticación/autorización, fuera de alcance.
