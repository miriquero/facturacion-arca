# Design

## Context

Motivación y alcance: ver `proposal.md`. Requisitos verificables: ver `specs/` (5 capacidades).

Estado actual y restricciones que condicionan el enfoque:

- Etapa 0 archivada: Boot 3.3.5, Gradle 8.10.2, JDK 17 por toolchain, H2 en memoria con nombre fijo (`jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1`), `ddl-auto: update`, `show-sql` + `format_sql`, `open-in-view: false`, perfil `postgres` como segundo documento YAML. Data JPA, Validation, Lombok y H2 ya están declarados: **`build.gradle` no cambia**.
- `model/`, `repository/` y `config/` solo tienen `package-info.java`. Hay tres pruebas `@SpringBootTest` sin perfil (`FacturacionApplicationTests`, `ConfiguracionH2Test`, `ConsolaH2Test`) y una con perfil `postgres` sin autoconfiguración (`PerfilPostgresTest`).
- La H2 por defecto es **nombrada y sobrevive al cierre del contexto** dentro de la JVM de pruebas: si el `DataLoader` corriera en los `@SpringBootTest`, sus datos quedarían visibles para otros contextos. Esto condiciona D10.
- PostgreSQL no está disponible (WSL deshabilitado, 5432 ocupado): toda la verificación de esta etapa es con H2 y así se declara (la verificación con PostgreSQL 16 se hace antes de C-05).
- Autoridad: consigna (Etapa 1, ítems 1-8) > reglas duras de `CLAUDE.md` > KB > semilla. TDD estricto: ninguna línea de la semilla entra sin un test en rojo previo.
- Gobernanza ALTA: el apply empieza recién cuando el estudiante aprueba este documento.

### Inventario de la semilla (DD-13)

Semilla principal `api-facturas` (`com.ejemplo.apifacturas.model`, 16 clases, y `config/CargaDatosPrueba.java`). La secundaria `JPA/TP2_JPA_corregido` tiene el **mismo modelo sin Lombok** (constructores y getters a mano) y no aporta nada distinto: solo se cita como origen.

| Semilla | Se trae | Se adapta (motivo) | Es nuevo |
|---|---|---|---|
| `EntityId` | `@MappedSuperclass`, `id` IDENTITY | quitar `@AllArgsConstructor` | — |
| `AuditoriaApp` | 6 campos y nombres de columna | `Date`+`@Temporal` → `LocalDateTime`; `@ManyToOne` EAGER → LAZY; `updatable=false` en alta y usuario de carga; quitar `@EqualsAndHashCode(callSuper=true)` y `@AllArgsConstructor` | `@PrePersist` con `UsuarioActual` (la semilla auditaba a mano en la carga) |
| `FacturaVenta` | campos, tabla `factura_venta`, `mappedBy`+`cascade ALL`+`orphanRemoval`, `agregarDetalle` | `estado` String → `EstadoFactura` STRING length 20; fechas `Date` → `LocalDate`; ManyToOne → LAZY; `cae` 14, `observaciones` 500; quitar `@Builder` (dejaba `detalles` en `null` sin `@Builder.Default`) y `@AllArgsConstructor`; sin setter de `detalles` | `UNIQUE(punto_venta_id, numero)`; `quitarDetalle`, `reemplazarDetalles` |
| `FacturaVentaDetalle` | campos, FK `factura_id` | LAZY; `descripcion` 255; quitar `@Builder` | — |
| `Cliente` | campos, OneToOne con `contacto_id`/`domicilio_id` | agregar `orphanRemoval=true` (la semilla solo tenía cascade); `cuitCuil` 13 | — |
| `Contacto`, `Domicilio` | campos | — | — |
| `Usuario` | campos | `usuario` 50 + **único**; `nombre`/`apellido` 100; `clave` fuera de `toString` | — |
| `PuntoVenta` | campos | `numero` **único** | — |
| `CondicionIva`, `TipoMoneda`, `Marca`, `Rubro`, `ListaPrecio` | campos | longitudes (`codigoAfip` moneda 10, `simbolo` 5, `codigo` lista 20) | — |
| `Articulo` | campos, marca/rubro opcionales | LAZY; `codigo` 30 | `alicuotaIva` `Double` nullable (faltaba en la semilla) |
| `ListaPrecioArticulo` | campos | LAZY | `UNIQUE(lista_precio_id, articulo_id)` |
| — | — | — | `EstadoFactura`, `UsuarioActual`, los 11 repositorios (la carpeta `repository/` de la semilla está vacía; usaba `EntityManager`) |
| `CargaDatosPrueba` | estructura de métodos fábrica, catálogos (rubros, marcas, artículos, lista, precios), chequeo "si hay usuarios no hago nada" | habilitación por propiedad `app.cargar-datos-prueba` → `@Profile("!test")`; `EntityManager` → repositorios; usuarios `admin`/`ana` → `sistema`/`acortez` + `mriquero` (el estudiante, pedido explícito 2026-11-12; la consigna dice "incluyan", no "solo"); auditoría manual → `UsuarioActual` + `@PrePersist`; contacto/domicilio persistidos aparte → por cascada; 7 facturas (6 EMITIDA, 1 ANULADA, 0 BORRADOR) → 6 (3/1/2); `cobrado = total` → `cobrado = 0`, `saldo = total` (SU-06); importes calculados con `double` (`total / 1.21`) → literales precalculados (D9); `System.out` → `Logger`; agregar moneda `DOL` | limpieza del contexto en `finally` |

## Goals / Non-Goals

**Goals:**
- Cada mapeo exigido por la consigna queda respaldado por una prueba que falla sin él y que lo verifica **releyendo desde la base** (flush + clear), no contando sentencias SQL.
- Cada restricción se prueba con datos que realmente la violan y con un caso vecino que no la viola (triangulación).
- Que el estudiante pueda defender cada anotación: el design dice por qué está y qué test la exige.

**Non-Goals:**
- `@PreUpdate`, `registrarModificacion()` y `darDeBaja()`: la auditoría de modificación/baja ligada a operaciones (PUT, anular) es de C-05.
- Transiciones de estado (`puedeTransicionarA`), numeración, CAE, cálculo de importes con `BigDecimal`, toma del precio desde la lista: C-05.
- Bean Validation en entidades (`@NotNull`, `@Size`): la validación de entrada vive en los DTO de request (C-05); en esta etapa las restricciones son de esquema.
- Consultas derivadas o `@Query` en repositorios (C-03/C-04), índices extra, paginación.
- `application-test.yml`: no hace falta (ver D10).
- Pruebas contra PostgreSQL / Testcontainers.

## Decisions

### D1 — Auditoría con `@PrePersist` + `UsuarioActual`, no Spring Data Auditing
`AuditoriaApp` declara un `@PrePersist` que fija `fechaAlta = fechaModificacion = LocalDateTime.now()` y `usuarioCarga = usuarioModificacion = UsuarioActual.get()`. `UsuarioActual` es una clase `final` en `model` con constructor privado y un `ThreadLocal<Usuario>` estático (`set/get/clear`).
- **Por qué**: la consigna nombra `UsuarioActual` como auxiliar de contexto y pide "preparar el contexto de auditoría y limpiarlo"; en C-05 el interceptor de `X-Usuario` lo llenará por petición sin tocar las entidades. Es el mínimo para que la etapa 1 cumpla `nullable=false` en los usuarios de auditoría.
- **Si el contexto está vacío** el callback deja `null` y la base rechaza el INSERT (spec `auditoria-entidades`). No se lanza una excepción propia: la consigna presenta justamente ese error ("restricción de auditoría incumplida → revisar usuario del contexto", RN-AU-08) como algo que el estudiante debe saber interpretar.
- **Alternativa descartada**: `@EnableJpaAuditing` + `AuditorAware` + `@CreatedBy` (lo que sugieren las skills `jpa-patterns`/`spring-data-jpa`): agrega infraestructura de Spring Data dentro del modelo y se aparta del `UsuarioActual` que la consigna nombra. **Descartada también**: auditoría manual en la carga (semilla), porque obliga a cada llamador a acordarse.

### D2 — `updatable = false` para alta y usuario de carga
`@Column(name="fecha_alta", nullable=false, updatable=false)` y `@JoinColumn(name="usuario_carga_id", nullable=false, updatable=false)`.
- **Por qué**: Hibernate excluye esas columnas del UPDATE; el test cambia el objeto en memoria, sincroniza y relee para demostrar que la base conserva el valor original (consigna ítem 1).
- **Alternativa descartada**: no exponer setters: no protege contra un `merge` de un objeto armado a mano.

### D3 — Todas las `@ManyToOne` LAZY y referencias opcionales explícitas
Toda `@ManyToOne` lleva `fetch = LAZY`; las obligatorias además `optional = false` y `@JoinColumn(nullable = false)`; las opcionales (cliente de la factura, marca y rubro del artículo, usuario de baja) quedan `nullable`.
- **Por qué**: el default JPA de `@ManyToOne` es EAGER (la semilla lo usaba) y con `open-in-view: false` todo acceso a datos se hará dentro de servicios transaccionales; LAZY evita cargas en cascada de catálogos y es lo que pide la consigna para la auditoría.
- **Alternativa descartada**: LAZY solo en auditoría: deja EAGER en factura→cliente→contacto, que dispara SELECT adicionales por cada factura en el reporte de C-03.

### D4 — Lombok: `@Getter`/`@Setter`/`@NoArgsConstructor` + `@ToString` con exclusiones; sin `equals/hashCode` generados
Entidades con `@Getter @Setter @NoArgsConstructor @ToString`; **todas** las asociaciones (incluidas las de auditoría) con `@ToString.Exclude`; `Usuario.clave` con `@ToString.Exclude`. No se genera `equals/hashCode` (identidad de objeto, válida dentro de un contexto de persistencia). Sin `@Builder`, `@AllArgsConstructor` ni `@Data`.
- **Por qué**: regla dura (sin `@Data`, sin asociaciones bidireccionales en `toString/equals/hashCode`). Excluir también las `@ManyToOne` evita inicializar proxies LAZY al loguear (o `LazyInitializationException` fuera de transacción). `@EqualsAndHashCode(callSuper=true)` de la semilla sobre una superclase sin `equals` termina comparando identidad de todos modos, pero agrega campos mutables al `hashCode`, lo que rompe `Set`s cuando el id se asigna al persistir.
- **Alternativa descartada**: `equals/hashCode` por `id`: cambia el `hashCode` al persistir (id nulo → asignado); el patrón correcto (id + clase efectiva del proxy) se puede revisar en C-03 cuando la consigna trate Lombok, y ningún test de esta etapa lo necesita.

### D5 — Agregado Factura: el detalle posee la FK; la colección no tiene setter
`FacturaVenta.detalles`: `@OneToMany(mappedBy="factura", cascade=ALL, orphanRemoval=true)`, inicializada con `new ArrayList<>()`, `@Setter(AccessLevel.NONE)`. Métodos de dominio:
- `agregarDetalle(d)`: `detalles.add(d); d.setFactura(this)`.
- `quitarDetalle(d)`: `detalles.remove(d); d.setFactura(null)`.
- `reemplazarDetalles(nuevos)`: `detalles.clear()` y `agregarDetalle` por cada nuevo, **sin** asignar otra lista.
`FacturaVentaDetalle.factura`: `@ManyToOne(fetch=LAZY, optional=false)` + `@JoinColumn(name="factura_id", nullable=false)`.
- **Por qué**: consigna ítem 5; reemplazar la instancia de una colección con `orphanRemoval` hace que Hibernate falle ("A collection with cascade=all-delete-orphan was no longer referenced"). Sin setter, esa trampa no se puede pisar.
- **Cascada solo hacia los detalles**: ninguna `@ManyToOne` de la factura ni del detalle lleva `cascade` (RN-PE-09). El test de "catálogo no persistido" lo demuestra por el lado negativo.
- **Alternativa descartada**: `@OneToMany` unidireccional con `@JoinColumn`: Hibernate hace INSERT + UPDATE de la FK y la relación inversa no existe; y sin `mappedBy` aparece la tabla intermedia que la consigna menciona como error frecuente.

### D6 — Agregado Cliente: `@OneToOne` con FK en `cliente`
`Cliente.contacto` y `Cliente.domicilio`: `@OneToOne(cascade=ALL, orphanRemoval=true, fetch=LAZY, optional=false)` + `@JoinColumn(name="contacto_id"|"domicilio_id", nullable=false)`. Contacto y Domicilio son unidireccionales (no conocen al cliente).
- **Por qué**: así lo modela la consigna/KB (FK en el cliente); unidireccional evita una segunda relación que mantener.
- **Alternativa descartada**: `mappedBy` desde Contacto (FK en contacto): contradice el modelo de columnas y la semilla.

### D7 — Unicidades como `@UniqueConstraint` con nombre
`@Table(uniqueConstraints = @UniqueConstraint(name = "uk_...", columnNames = {...}))` para `usuario(usuario)`, `punto_venta(numero)`, `lista_precio_articulo(lista_precio_id, articulo_id)`, `factura_venta(punto_venta_id, numero)`.
- **Por qué**: un nombre explícito aparece en el mensaje de error y en el DDL que se cita en `docs/etapa-1.md`; las compuestas no se pueden expresar con `@Column(unique=true)`. Un borrador tiene `numero NULL` y H2/PostgreSQL tratan los NULL como distintos en `UNIQUE`, por eso varios borradores coexisten (scenario dedicado).
- **Alternativa descartada**: `@Column(unique = true)` para las simples: nombre aleatorio generado por Hibernate.

### D8 — Repositorios: 11 `JpaRepository` sin métodos propios
`UsuarioRepository, FacturaVentaRepository, ClienteRepository, PuntoVentaRepository, CondicionIvaRepository, TipoMonedaRepository, ArticuloRepository, ListaPrecioRepository, ListaPrecioArticuloRepository, MarcaRepository, RubroRepository`, todos `JpaRepository<X, Long>`. Ninguno para `FacturaVentaDetalle`, `Contacto`, `Domicilio` (consigna ítem 6).
- **Por qué**: el `DataLoader` y las etapas 3-4 (selectores de catálogos, `FacturaService`) necesitan cada uno; los métodos de consulta llegan con su etapa. El test de spec usa `org.springframework.data.repository.support.Repositories` sobre el contexto para verificar presencia/ausencia.
- **Alternativa descartada**: un repositorio genérico o `EntityManager` directo (semilla): se aparta del estilo Spring Data que usan las etapas siguientes.

### D9 — `DataLoader` provisional con seis facturas e importes literales
`config/DataLoader`: `@Component @Profile("!test")`, implementa `CommandLineRunner`, `run` es `@Transactional`. Flujo: si `usuarioRepository.count() > 0` → log y return; si no, guarda `sistema`, `acortez` y `mriquero`, hace `UsuarioActual.set(sistema)` y en `try { ... } finally { UsuarioActual.clear(); }` guarda catálogos, clientes (contacto y domicilio por cascada) y seis facturas con `facturaVentaRepository.save`:
- PV 1: EMITIDA nº 1 y nº 2, ANULADA nº 3 (con `fechaAnulacion`); PV 2: EMITIDA nº 1; dos BORRADOR (`numero`, `cae`, `caeFechaVencimiento`, `resultadoAfip` nulos). Una EMITIDA y un BORRADOR sin cliente; una EMITIDA en `DOL`.
- EMITIDA/ANULADA: CAE literal de 14 dígitos, `caeFechaVencimiento = fechaEmision + 10 días`, `resultadoAfip = "A"`. `importeCobrado = 0`, `importeSaldo = importeTotal` (SU-06).
- Importes de cada renglón y totales como **literales escritos a mano** (p. ej. 2 × 3000 → neto 6000, IVA 21 % 1260, subtotal 7260), sin bonificación, sin ninguna operación aritmética en el código.
- Fechas con `LocalDate.now().minusDays(n)`.
- **Por qué**: la consigna (ítem 7) y `CHANGES.md` piden seis facturas representativas en etapa 1, pero también "no convertir una carga provisoria en la regla final". Con literales no hay cálculo con `double` que defender (regla dura) ni reglas de numeración/CAE implementadas: son **datos**, y C-05 reemplaza este bloque por llamadas a `FacturaService` (DD-11). La clase y `docs/etapa-1.md` lo dicen explícitamente.
- **Alternativa considerada** (IN-06 de `10_preguntas_abiertas.md`): cargar solo usuarios y catálogos en etapa 1 y agregar las facturas en C-05. Más pura, pero deja la base sin facturas para inspeccionar en la consola H2 y para el reporte de C-03. **Confirmado por el estudiante (2026-11-12)**: se cargan las seis facturas con importes literales; IN-06 queda descartada para etapa 1.

### D10 — Pruebas: `@DataJpaTest` sobre H2 embebida; perfil `test` en los `@SpringBootTest`
- Persistencia: `@DataJpaTest` (cada contexto obtiene una H2 embebida con nombre único, `create-drop`, transacción con rollback por test) + `TestEntityManager` para `persistAndFlush`, `flush`, `clear` y `find`. Motor declarado: **H2 2.x embebida** (la versión que gestiona Boot 3.3.5).
- Violaciones de restricción: `assertThatThrownBy(() -> repo.saveAndFlush(x)).isInstanceOf(DataIntegrityViolationException.class)`. Ojo: con IDENTITY el INSERT ocurre en el `save`/`persist`, no en el flush; por eso cada violación vive en su propio método (la sesión queda inutilizable tras la excepción).
- Esquema: consultas a `INFORMATION_SCHEMA.TABLES/COLUMNS` (H2) para "14 tablas, sin tabla intermedia, `factura_id` no nula"; metamodelo vía `EntityManager.getMetamodel().getEntities()`; LAZY vía `PersistenceUnitUtil.isLoaded`.
- Datos de prueba: un soporte `src/test/java/.../model/DatosDePrueba` con métodos fábrica (`usuario(...)`, `puntoVenta(n)`, `facturaBorrador(...)`, `detalle(...)`) para no repetir el armado (skill `springboot-tdd`: test data builders).
- `DataLoader`:
  - Arranque real: `@DataJpaTest @Import(DataLoader.class)` **sin** perfil → el runner corre al levantar el contexto; el test verifica usuarios, catálogos, distribución de facturas, contexto vacío y que una segunda llamada a `run()` no cambia cantidades.
  - Exclusión: `@DataJpaTest @ActiveProfiles("test") @Import(DataLoader.class)` → no hay bean `DataLoader` y la base no tiene usuarios.
  - Atomicidad y limpieza ante error: perfil `test` (sin carga al arrancar), `@Transactional(propagation = NOT_SUPPORTED)` en la clase para que el rollback sea real, `@SpyBean` sobre un repositorio de catálogo que lanza a mitad de la carga, y el loader creado con `AutowireCapableBeanFactory.createBean(DataLoader.class)` (aplica el proxy transaccional). Verifica: excepción propagada, `UsuarioActual.get() == null`, `usuarioRepository.count() == 0`.
- Pruebas `@SpringBootTest` existentes (`FacturacionApplicationTests`, `ConfiguracionH2Test`, `ConsolaH2Test`): agregan `@ActiveProfiles("test")` para que el `DataLoader` no escriba en la H2 nombrada compartida (ver Context). Siguen comprobando lo mismo (la URL `jdbc:h2:mem:facturacion` no depende del perfil).
- **Por qué**: consigna ítem 8 y skill `springboot-tdd` (`@DataJpaTest`, H2 aceptable). `@DataJpaTest` solo levanta JPA: rápido y sin `DataLoader` salvo que se lo importe.
- **Alternativa descartada**: `@SpringBootTest` para la persistencia: carga web, comparte la H2 nombrada y mezcla datos entre clases. **Descartado también**: `application-test.yml` con otra URL: no hace falta mientras las pruebas de contexto no persistan datos.

### D11 — Fechas
Auditoría `LocalDateTime` (columnas `timestamp`), negocio `LocalDate` (columnas `date`), sin `@Temporal` (innecesario con `java.time` en JPA 3.1). La zona de la JVM ya está fijada en `America/Argentina/Buenos_Aires` por etapa 0 (DD-12).

### D12 — Consulta a la referencia del docente
`arca-spirng` se consultó **solo para nombres** (sin copiar código): ubicación de `UsuarioActual` en `model` y de `DataLoader` en `config` con exclusión por perfil, y la lista de repositorios. Coincide con lo que ya documentaba la KB (`04`, `08`, SU-01). Se declara en `docs/etapa-1.md`.

## Risks / Trade-offs

- [Los tests pasan en H2 y fallan en PostgreSQL (tipos `double precision`, NULL en UNIQUE, longitud)] → todas las restricciones usadas son SQL estándar con el mismo comportamiento en PostgreSQL 16; el informe declara "probado en H2" y la verificación PostgreSQL queda agendada antes de C-05.
- [`ddl-auto: update` no borra columnas ni constraints si se renombran durante el desarrollo] → la H2 por defecto es en memoria y se recrea en cada arranque; los tests usan `create-drop`.
- [Literales del `DataLoader` incoherentes entre sí (total ≠ suma de subtotales)] → los valores se eligen enteros y se documentan en una tabla en `docs/etapa-1.md`; C-05 los reemplaza por importes calculados.
- [Un `toString` que incluya un proxy LAZY dispara una consulta o `LazyInitializationException`] → D4 excluye todas las asociaciones; test de spec.
- [`@SpyBean` + `createBean` es una técnica menos común para el estudiante] → se explica en `docs/etapa-1.md`; si resulta frágil, alternativa: test unitario con Mockito para la limpieza del contexto y documentar la atomicidad como garantía de `@Transactional`.
- [`hibernate.check_nullability` queda desactivado al estar Bean Validation en el classpath] → las violaciones de nulabilidad las detecta la base (`DataIntegrityViolationException`), que es lo que piden probar; los tests no dependen de la validación en memoria de Hibernate.
- [Agregar `@ActiveProfiles("test")` cambia la configuración de pruebas de etapa 0] → se corre la suite antes (red de seguridad) y después; ningún requisito de etapa 0 depende del perfil por defecto salvo `PerfilPostgresTest`, que no se toca.

## Migration Plan

No hay datos que migrar (H2 en memoria). Rollback: revertir los archivos del change; el esquema se regenera al arrancar.

## Open Questions

- ~~(Bloqueante)~~ **Resuelta 2026-11-12**: el estudiante eligió seis facturas con importes literales en el `DataLoader` provisional (D9); IN-06 descartada para etapa 1.
- **(No bloquea el apply; sí el cierre del informe)** Formato de la declaración de uso de IA en `docs/etapa-1.md` (pregunta Alta de `10_preguntas_abiertas.md`). Mientras el docente no responda, el informe usa: herramientas usadas, qué se pidió (propuesta OpenSpec, tests, revisión), qué se revisó y qué se corrigió a mano; el commit propuesto lleva `Co-Authored-By`.
