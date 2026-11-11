# Facturación ARCA (TP Integrador UTN FRM) — Instrucciones para Agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Generado a partir de `knowledge-base/` y `CHANGES.md`. No editar a mano sin re-sincronizar ambos archivos.

Aplicación académica de facturación con ARCA **simulada**: API REST Spring Boot + frontend TypeScript sin framework. Una factura pasa BORRADOR → EMITIDA (número por punto de venta, CAE ficticio de 14 dígitos) → ANULADA. Trabajo **individual**, entregado en 5 etapas con tags `etapa-1`..`etapa-5` y defendido oralmente: todo el código tiene que poder explicarse.

Consigna completa (autoridad ante cualquier duda): [docs/consigna/tp-integrador-facturacion-arca.md](docs/consigna/tp-integrador-facturacion-arca.md)

---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|------|------------|---------|
| Lenguaje backend | Java | 17 |
| Framework | Spring Boot (Web, Data JPA, Validation) | 3.3.5 |
| Build | Gradle wrapper | 8.10.2 |
| Persistencia | Jakarta Persistence / Hibernate, Lombok | las de Boot 3.3.5 |
| BD | H2 en memoria por defecto; PostgreSQL vía perfil `postgres` + `docker-compose.yml` | H2 / PostgreSQL 16 |
| Reportes | OpenPDF / Apache POI / TXT UTF-8 | 2.0.3 / 5.3.0 / — |
| Frontend | HTML, CSS, TypeScript estricto, módulos ES2020, sin framework, `http-server` en 5173 | TypeScript 5.6 |
| Pruebas | JUnit Platform, Spring Boot Test, MockMvc; Node + jsdom | — |

Coordenadas: proyecto `facturacion-arca`, grupo `ar.edu.utn.frm`, paquete `ar.edu.utn.frm.facturacion`, backend en 8080. Detalle: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md)

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/`. **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---------|---------------|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Propósito, alcance y fuera de alcance |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Por qué no hay login; `X-Usuario` y CORS no autentican |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | 14 entidades + 2 superclases, relaciones, unicidades, seed |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas codificadas RN-XX, cada una con su etapa |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias por etapa, con verificación y entregable |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Flujos extremo a extremo y máquina de estados |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Capas, estructura, propiedades, gotchas de entorno |
| [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md) | DD-01..13 y SU-01..09; **DD-13: trabajo previo como semilla por etapa** |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | ⚠️ Inconsistencias a resolver ANTES de codear |
| [11_metodologia_evaluacion_y_defensa.md](knowledge-base/11_metodologia_evaluacion_y_defensa.md) | Rúbrica, informes `docs/etapa-N.md`, defensa |
| [12_errores_frecuentes_y_glosario.md](knowledge-base/12_errores_frecuentes_y_glosario.md) | Anexo A de la consigna y glosario |

> ⚠️ Resolver las preguntas de prioridad **Alta** de `10_preguntas_abiertas.md` que bloqueen el change en curso antes de implementarlo.

**Código de referencia (fuera del repo, solo lectura):**
- Semilla propia por etapa (DD-13): `C:\Users\zbook\Documents\Facultad\Desarrollo\StringBoot\api-facturas` y los TPs de `JPA\` y `tp-desarrollo\`. Cada change de `CHANGES.md` indica qué traer en su línea **Semilla**.
- Solución del docente: `C:\Users\zbook\DesarrolloSoftware\arca-spirng`. Solo consulta, nunca fuente de copia.

---

## Skills Disponibles

| Agente | Rol | Skills que carga |
|--------|-----|------------------|
| **Backend** | Entidades, repositorios, servicios, REST, reportes (etapas 0-4) | `springboot-tdd`, `jpa-patterns`, `spring-data-jpa`, `springboot-patterns`, `java-springboot`, `java-junit`, `gradle-best-practices` |
| **Frontend** | Cliente TS, formularios, controles asíncronos, accesibilidad (etapa 5) | `tdd`, `accessibility`, `accessibility-review` |
| **Orquestación** | Fundación OPSX, KB, roadmap, reglas | `active-orchestrator`, `kb-creator`, `roadmap-generator`, `skill-registry`, `agent-instruction` |

Cargá la skill correspondiente al contexto ANTES de escribir código. Las skills de Backend y Frontend son temporales para este TP y se desinstalan al terminarlo.

> Los compact rules de cada skill los resuelve el orquestador desde `.atl/skill-registry.md` (generado por `skill-registry`; no versionado — no está en el repo). Esta tabla solo mapea skill→rol.

---

## Roadmap de Changes

El plan completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 6 changes en 4 fases, uno por etapa de la consigna, estrictamente secuenciales.
- **Camino crítico** (6): `C-01 → C-02 → C-03 → C-04 → C-05 → C-06`.
- **Primer change**: `C-01` (`etapa-0-puesta-en-marcha`).
- **Mayor peso y riesgo**: `C-05` (`etapa-4-escritura-estados-concurrencia`, 25 %).

**Antes de cualquier `/opsx:propose`**: leé [CHANGES.md](CHANGES.md), verificá que la dependencia del change esté archivada y leé sus archivos de "Leer antes" y su línea "Semilla".

---

## Reglas Duras (específicas del proyecto)

> Reglas **globales** ya definidas en `~/.claude/CLAUDE.md` (orquestador, governance, TDD estricto, engram): el proyecto las **hereda**, no se repiten acá.

**Backend y dominio**
- NUNCA compilar ni testear con el JDK 26 por defecto de esta máquina → usar `JAVA_HOME=C:\Users\zbook\.jdks\ms-17.0.20.1` (o toolchain Gradle 17).
- NUNCA cambiar las versiones fijadas por la consigna (Spring Boot 3.3.5, Gradle 8.10.2, OpenPDF 2.0.3, Apache POI 5.3.0, TypeScript 5.6) → si algo parece requerir otra versión, consultarlo primero.
- NUNCA devolver entidades JPA en respuestas HTTP → usar DTOs construidos dentro del servicio (`open-in-view: false`).
- NUNCA usar `@Data` en entidades ni incluir asociaciones bidireccionales en `toString`/`equals`/`hashCode` → `@Getter`/`@Setter` y exclusiones explícitas.
- NUNCA calcular importes con `double` ni `Math.round` → `BigDecimal` con `HALF_UP` a 2 decimales y control de ida y vuelta antes de guardar en los campos `double`.
- NUNCA agregar login, cobros, paginación, CRUD de catálogos, conversión de monedas ni integración fiscal real → están fuera del alcance de la consigna.

**Frontend** (lo que fija la consigna)
- NUNCA agregar React, Vue u otro framework o bundler → HTML, CSS y TypeScript con módulos ES2020.
- NUNCA desactivar `strict` en `tsconfig` → tipar los DTO del contrato HTTP.
- NUNCA insertar datos del servidor con `innerHTML` → construir nodos y asignar `textContent`.

**Git** (lo decide y ejecuta el estudiante)
- NUNCA ejecutar `git commit`, `git tag`, `git push` ni crear PRs → al cerrar cada etapa, entregar los comandos listos para que el estudiante los corra.
- Cada mensaje de commit propuesto termina con la línea `Co-Authored-By: Claude <modelo en uso> <noreply@anthropic.com>`, coherente con el uso de IA declarado en `docs/etapa-N.md`.

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)          → entender el dominio
2. Identificar el change en CHANGES.md             → dependencias, "Leer antes" y "Semilla"
3. /opsx:propose <nombre-del-change>               → proposal + design + specs + tasks
4. /opsx:apply (TDD estricto, cargando skills)     → traer la semilla solo detrás de un test en rojo
5. docs/etapa-N.md + README                        → decisiones, verificaciones, semilla e IA declaradas
6. Comandos de Git para el estudiante              → commit(s), PR y tag etapa-N los corre él
7. /opsx:archive <nombre-del-change> + marcar [x]  → cerrar el change
```

Aplicar TODAS las reglas duras en cada paso. Ante conflicto entre la KB y este archivo, las reglas duras prevalecen; ante conflicto con la consigna, manda la consigna.
