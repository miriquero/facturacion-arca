# Visión y Objetivos

> Fuente de autoridad: `docs/consigna/tp-integrador-facturacion-arca.md` (la consigna). La solución completa del docente (`arca-spirng`, solo lectura) es referencia secundaria; donde va más allá de la consigna se indica explícitamente. Ver `10_preguntas_abiertas.md`.

## Propósito del sistema

**Una aplicación de facturación académica ("ARCA simulada") construida desde el modelo persistente hasta una API REST consumida por un frontend TypeScript, que permite crear un borrador, editarlo, emitirlo con autorización simulada (CAE ficticio) y, cuando corresponde, anularlo.**

Es un Trabajo Práctico Integrador (Universidad Nacional Tecnológica / UTN FRM, modalidad **individual**, un repositorio Git personal por estudiante, 11 semanas: 10 de desarrollo + 1 de defensa). No se conecta con los servicios fiscales de ARCA: el CAE es ficticio y las transiciones de estado describen las reglas de *esta* aplicación, no un procedimiento fiscal real. El identificador de usuario (`X-Usuario`) sirve **solo para auditoría**: no implementa autenticación ni autorización.

### Propósito pedagógico (lo que realmente se evalúa)
Un mismo proyecto permite observar cómo las decisiones del modelo repercuten en consultas, contratos HTTP e interfaz. Ejemplos citados por la consigna: una cascada incorrecta impide guardar una factura; un DTO incompleto oculta la moneda; una actualización concurrente invalida una regla de estado. **Se evalúa el resultado y la capacidad de explicar estas relaciones.** Cada estudiante debe poder explicar la totalidad del código que entrega.

### Contexto de este repositorio
Este proyecto (`facturacion-arca`) lo reconstruye **Martín (UTN FRM) desde cero, etapa por etapa, con TDD estricto**, para poder defenderlo oralmente. Esta KB es su guía de estudio y la entrada del roadmap (un change de OpenSpec por etapa).

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|-------|--------------------|-----------------------|
| Estudiante / desarrollador (Martín) | Entregar 5 etapas (tags `etapa-1`..`etapa-5`) funcionando y defenderlas | Escribir pruebas que distingan comportamiento correcto de defectuoso; documentar decisiones en `docs/etapa-N.md`; historial Git comprensible; poder explicar todo el código |
| Docente evaluador | Verificar funcionamiento, diseño, pruebas, documentación y Git por etapa; evaluar la defensa (25 min) | Detectar si el estudiante entiende cascadas, bloqueos, redondeo, códigos HTTP, auditoría, respuestas asíncronas |
| Usuario de auditoría (`sistema`, `acortez`) | Quedar registrado como autor de altas/modificaciones/bajas | No autentica; solo identifica al actor declarado |
| Usuario web (operador del frontend) | Consultar, filtrar, crear/editar borradores, emitir, anular, eliminar borradores y descargar reportes | Recibir errores claros, no perder datos ante un fallo, no ver resultados obsoletos |

## Alcance (obligatorio por la consigna)

- **Facturas**: crear borrador, editar borrador, emitir, anular, eliminar borrador, consultar (listado con filtros y detalle).
- **Consulta de catálogos** (solo lectura): puntos de venta, clientes, condiciones de IVA, tipos de moneda, lista-precio-artículos.
- **Reportes**: PDF, Excel (XLSX) y TXT UTF-8 de las facturas filtradas.
- **Auditoría**: usuario/fecha de alta, modificación y baja vía header `X-Usuario`.
- **Modelo**: 14 entidades + 2 superclases mapeadas (`EntityId`, `AuditoriaApp`).
- **Cálculo de importes** en el servidor con `BigDecimal` (HALF_UP, 2 decimales), numeración por punto de venta con bloqueo pesimista, CAE simulado de 14 dígitos.
- **Frontend** HTML/CSS/TypeScript 5.6 estricto, sin framework, servidor separado (puerto 5173) consumiendo el backend (8080).
- **Entrega por etapas** (0 a 5) con tags, informes `docs/etapa-N.md`, README reproducible y `requests.http`; defensa individual final.

## Fuera de alcance (explícito en la consigna)

Inicio de sesión / autenticación / autorización, administración (CRUD) de catálogos, stock, **cobros** (aunque existan `importeCobrado`/`importeSaldo` en el modelo), conversión de monedas, notas de crédito, paginación e integración fiscal real con ARCA. Además, según la referencia (no la consigna): migraciones versionadas (Flyway/Liquibase; el esquema lo maneja Hibernate `ddl-auto: update`), filtro por punto de venta, CRUD de clientes. La presencia de campos de cobros o de resultado fiscal en el modelo **no** implica que existan esos procesos.

## Métricas de éxito

| Métrica | Cómo se mide |
|---------|--------------|
| Cada etapa cumple su "Verificación"/"Comprobación" | Pruebas en verde + evidencia manual (ver `06_funcionalidades.md`) |
| Rúbrica por etapa | Funcionamiento 40 %, diseño 25 %, pruebas 15 %, informe 10 %, Git 10 % |
| Nota final | 70 % promedio ponderado de etapas (20/20/15/25/20) + 30 % defensa |
| Defensa | Demo de 5 min + 15 min de preguntas + 5 min de cambio breve indicado por el docente |
| Comprensión | El estudiante explica cascadas/lado propietario, proyección DTO, transacciones y bloqueos, redondeo, códigos HTTP, auditoría y control de respuestas asíncronas |
