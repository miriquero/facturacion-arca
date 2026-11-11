# Metodología, Evaluación y Defensa

Archivo extra (no canónico): condensa lo que la consigna dice sobre cómo se trabaja y cómo se califica.

## Modalidad
Individual; **un repositorio Git personal**; 11 semanas (10 de desarrollo + 1 de defensa). Evaluación: promedio ponderado de etapas **70 %** + defensa individual **30 %**.

| Etapa | Semanas | Peso en el 70 % | Tag | Informe |
|-------|---------|-----------------|-----|---------|
| 0 Puesta en marcha | 1 | sin nota | - | - |
| 1 Persistencia | 2-3 | 20 % | `etapa-1` | `docs/etapa-1.md` |
| 2 Lombok, DTO, JPQL, reportes | 4-5 | 20 % | `etapa-2` | `docs/etapa-2.md` |
| 3 API REST de lectura | 6 | 15 % | `etapa-3` | `docs/etapa-3.md` |
| 4 Escritura, estados, concurrencia | 7-8 | 25 % | `etapa-4` | `docs/etapa-4.md` |
| 5 Frontend | 9-10 | 20 % | `etapa-5` | `docs/etapa-5.md` |
| Final / defensa | 11 | 30 % de la nota final | - | - |

Nota de etapas = E1×0,20 + E2×0,20 + E3×0,15 + E4×0,25 + E5×0,20. Nota final = 0,70 × (nota de etapas) + 0,30 × defensa.

## Rúbrica (misma para cada etapa, solo comportamientos exigidos hasta ese momento)

| Criterio | Peso | Evidencia esperada |
|----------|------|--------------------|
| Funcionamiento y requisitos | 40 % | Circuitos y casos límite resueltos; resultados verificables; errores controlados |
| Diseño y calidad del código | 25 % | Responsabilidades claras, asociaciones correctas, DTO adecuados, reglas en la capa correspondiente |
| Pruebas y verificación | 15 % | Pruebas reproducibles que ejercitan comportamientos, casos adversos y regresiones; evidencia manual cuando corresponda |
| Informe y documentación | 10 % | Instrucciones suficientes y explicación precisa de decisiones y limitaciones |
| Organización individual y Git | 10 % | Historial comprensible, autoría identificable y entregas etiquetadas |

## Git y entregas
- Cinco entregas con tags `etapa-1`..`etapa-5`; la versión evaluada es la del **tag**.
- Pull request hacia la rama principal; conservar historial de decisiones; commits comprensibles; revisar los propios cambios antes de integrar; ejecutar las comprobaciones antes de integrar; preparar el tag.
- Kits de nivelación de la cátedra (si existen): se pueden usar para continuar; declarar qué se incorporó y qué se modificó; la entrega anterior conserva su evaluación; en la defensa se explica también el código del kit.

## Informe `docs/etapa-N.md` (~1 carilla)
Debe registrar: decisiones y alternativas; dificultades y su resolución; evidencia de verificaciones relevantes. **No repetir el enunciado.** Si se usan asistentes de IA: para qué, qué resultados se revisaron, qué se corrigió. Todo código entregado debe ser comprendido por su autor.
Contenido específico por etapa: ver 06 (entregable de cada épica).

## Entrega final (semana 11)
Repo con las cinco etapas identificables, código, configuración de ejemplo, README (instalar, iniciar backend y frontend, H2 o PostgreSQL, controles), `requests.http`, pruebas e informes. Indicar qué comprobaciones se hicieron y en qué entorno. La evidencia debe cubrir: persistencia de agregados, cálculo y límites monetarios, validaciones y rollback, estados, auditoría, concurrencia, API, reportes (vacío, varias páginas, monedas y estados diferentes) e interfaz; revisar visualmente los archivos.

## Defensa individual (25 min)
- **5 min** demostración: consultas, creación y edición de un borrador, emisión, anulación, eliminación de otro borrador, descarga de reportes y al menos un error controlado.
- **15 min** preguntas: cascadas y lado propietario, proyección DTO, transacciones y bloqueos, redondeo, códigos HTTP, auditoría, control de respuestas asíncronas.
- **5 min** cambio breve indicado por el docente (es actividad de evaluación; no vuelve obligatorio lo que está fuera de alcance).

## Guía de estudio: preguntas probables de defensa (con la regla que las responde)

| Pregunta | Respuesta corta | Regla / archivo |
|----------|-----------------|-----------------|
| ¿Quién posee la FK entre factura y detalle? | El detalle (`factura_id`); `mappedBy="factura"` en la cabecera | RN-PE-07 |
| ¿Qué pasa al borrar un borrador? | Se borran factura y detalles (cascade); cliente y catálogos permanecen | RN-PE-09, RN-ES-06 |
| ¿Por qué `LEFT JOIN` en el reporte? | Para no perder facturas sin cliente o sin detalles | RN-FI-08 |
| ¿Por qué el orden del constructor importa en `SELECT new`? | Hibernate lo resuelve por posición y tipo | RN-FI-09 |
| ¿Por qué `Math.round` está mal? | Oculta pérdida de precisión; se exige round-trip BigDecimal↔double | RN-IM-09 |
| ¿IVA sobre neto redondeado o sin redondear? | Sin redondear; cada importe se redondea por separado | RN-IM-04..07 |
| ¿Cómo se evita número duplicado? | Bloqueo pesimista del punto de venta + unique (punto_venta, numero) | RN-NU-02 |
| ¿Por qué 409 y no 400? | El request es válido pero el estado actual lo impide | RN-ES-03 |
| ¿`X-Usuario` y CORS autentican? | No (ver 03) | RN-AU-07, RN-CF-01 |
| ¿Por qué `textContent`? | Evita interpretar datos como HTML (XSS) | RN-FE-14 |
| ¿Cómo evitás que una respuesta vieja pise una nueva? | Id de solicitud/último ganador | RN-FE-13 |
| ¿Por qué el DELETE rompía el frontend? | Se intentaba leer JSON de un 204 | RN-FE-04 |
