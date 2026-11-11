# Facturación ARCA (simulada) — Base de Conocimiento

Base de conocimiento generada (modo ingest) a partir de `docs/consigna/tp-integrador-facturacion-arca.md` (autoridad) con apoyo de la solución del docente `arca-spirng` (referencia de solo lectura; lo que solo está allí se marca `(ref)`). Pensada como guía de estudio para defender el TP y como entrada del roadmap (un change por etapa).

## Índice de Archivos

| Archivo | Contenido |
|---------|-----------|
| [01_vision_y_objetivos.md](01_vision_y_objetivos.md) | Propósito, objetivos por actor, alcance y fuera de alcance, métricas |
| [02_descripcion_general.md](02_descripcion_general.md) | Stack fijado, arquitectura en capas, integraciones, resumen de API |
| [03_actores_y_roles.md](03_actores_y_roles.md) | Actores reales; no hay RBAC; por qué `X-Usuario` y CORS no autentican |
| [04_modelo_de_datos.md](04_modelo_de_datos.md) | 14 entidades + 2 superclases, ERD, constraints, DTOs, seed |
| [05_reglas_de_negocio.md](05_reglas_de_negocio.md) | Reglas RN-PE/IM/ES/NU/CA/CC/AU/VA/FI/CT/ER/RE/CF/FE/GE |
| [06_funcionalidades.md](06_funcionalidades.md) | Historias por etapa 0-5 + defensa, con verificación y entregable |
| [07_flujos_principales.md](07_flujos_principales.md) | Flujos extremo a extremo por etapa y máquina de estados |
| [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md) | Patrones, estructura de directorios, seguridad, variables, gotchas de entorno (JDK 17, TZ) |
| [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md) | DD-01..12 y SU-01..09 |
| [10_preguntas_abiertas.md](10_preguntas_abiertas.md) | Inconsistencias IN-01..10 y preguntas priorizadas (metodología, Git, IA) |
| [11_metodologia_evaluacion_y_defensa.md](11_metodologia_evaluacion_y_defensa.md) | Extra: pesos, rúbrica, Git, informes, defensa, preguntas probables |
| [12_errores_frecuentes_y_glosario.md](12_errores_frecuentes_y_glosario.md) | Extra: Anexo A mapeado a reglas + glosario |

## Quick Start para Desarrolladores

1. Entender el dominio y el alcance → [01](01_vision_y_objetivos.md), [03](03_actores_y_roles.md)
2. Entender los datos → [04](04_modelo_de_datos.md)
3. Entender las reglas → [05](05_reglas_de_negocio.md)
4. Entender la arquitectura y el entorno → [02](02_descripcion_general.md), [08](08_arquitectura_propuesta.md)
5. Planificar por etapas → [06](06_funcionalidades.md), [07](07_flujos_principales.md), [11](11_metodologia_evaluacion_y_defensa.md)
6. Antes de codificar → [10](10_preguntas_abiertas.md), [09](09_decisiones_y_supuestos.md)
7. Al preparar la defensa → [11](11_metodologia_evaluacion_y_defensa.md), [12](12_errores_frecuentes_y_glosario.md)

## Resumen Ejecutivo

Aplicación académica de facturación (ARCA simulada): API REST Spring Boot 3.3.5 / Java 17 / JPA con H2 (y PostgreSQL 16) más frontend TypeScript sin framework. Una factura pasa BORRADOR → EMITIDA (número por punto de venta con bloqueo pesimista, CAE ficticio de 14 dígitos) → ANULADA; importes en BigDecimal HALF_UP con control de round-trip a double; auditoría por `X-Usuario` sin autenticación; reportes PDF/XLSX/TXT con totales solo de emitidas por moneda. Se entrega en 5 etapas (tags `etapa-1`..`etapa-5`, informe `docs/etapa-N.md`) y se defiende individualmente (70 % etapas, 30 % defensa).
