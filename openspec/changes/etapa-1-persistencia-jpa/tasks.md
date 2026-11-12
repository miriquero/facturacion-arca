# Tasks

> Reglas para todo el apply:
> - Gradle siempre con `JAVA_HOME=C:\Users\zbook\.jdks\ms-17.0.20.1` (nunca el JDK 26). Para correr una clase: `./gradlew test --tests '<FQN>'`.
> - TDD estricto: cada **GREEN** va precedida de su **RED**, y la RED se cierra solo tras ver la falla **por la razón esperada** (aserción o violación esperada, no un error de compilación ajeno: si el test no compila, primero crear la clase/campo vacío mínimo para que compile y falle en la aserción). Toda pieza de la semilla (`api-facturas/model`, `CargaDatosPrueba`) entra solo en un GREEN y se anota en `docs/etapa-1.md` qué se trajo y qué se cambió.
> - Persistencia con `@DataJpaTest` + `TestEntityManager`; toda verificación relee **después de `flush()` + `clear()`**. Violaciones: `assertThatThrownBy(() -> repo.saveAndFlush(x)).isInstanceOf(DataIntegrityViolationException.class)`, una por método. AssertJ (ya incluido en `spring-boot-starter-test`); sin aserciones triviales.
> - Pruebas que persisten entidades auditables fijan `UsuarioActual` en `@BeforeEach` (con un `Usuario` persistido) y lo limpian en `@AfterEach`.
> - Sin comandos Git que cambien estado. Gobernanza ALTA: antes de empezar, el estudiante aprobó `design.md` y respondió la Open Question bloqueante (D9).
> - Paquetes de prueba: `ar.edu.utn.frm.facturacion.model`, `.repository`, `.config`.

## 1. Red de seguridad y perfil de pruebas

- [ ] 1.1 Red de seguridad: `./gradlew clean test` con JDK 17 y anotar el baseline ("N pruebas en verde") en el borrador de `docs/etapa-1.md`; si algo falla, detenerse y reportarlo como falla preexistente
- [ ] 1.2 Agregar `@ActiveProfiles("test")` a `FacturacionApplicationTests`, `ConfiguracionH2Test` y `ConsolaH2Test` (D10); verificar que la suite sigue en el mismo número de pruebas en verde y que `PerfilPostgresTest` no cambió
- [ ] 1.3 Crear `docs/etapa-1.md` con el esqueleto de secciones (motor probado, modelo y relaciones, raíz de agregado, borrar un borrador, SQL generado, semilla reutilizada, decisiones, verificaciones, uso de IA); verificar que el archivo existe y declara "H2 2.x embebida (`@DataJpaTest`)"

## 2. Contexto de auditoría y superclases (spec `auditoria-entidades`)

- [ ] 2.1 RED: `model/UsuarioActualTest` (unitario): fijar `sistema` → `get()` lo devuelve; `clear()` → `get()` es `null`; otro hilo (`Executors` o `Thread` + `join`) no ve el usuario fijado. Falla porque `UsuarioActual` no existe (crear la clase vacía para que compile y falle en la aserción)
- [ ] 2.2 GREEN: `UsuarioActual` final, constructor privado, `ThreadLocal<Usuario>` estático con `set/get/clear` (nuevo; no está en la semilla); 2.1 en verde. Requiere `Usuario` mínimo (siguiente tarea) — crear solo la clase con `@Entity` e id para compilar
- [ ] 2.3 RED: `model/UsuarioPersistenciaTest` (`@DataJpaTest`): guardar `sistema` con `UsuarioRepository`, flush/clear y releer → id no nulo y campos iguales; falla sin mapeo/repositorio
- [ ] 2.4 GREEN: `EntityId` (`@MappedSuperclass`, `Long id` IDENTITY) y `Usuario extends EntityId` traídos de la semilla y adaptados (D4: sin `@Builder/@AllArgsConstructor/@EqualsAndHashCode`, `@ToString` con `clave` excluida; `usuario` 50, `nombre`/`apellido` 100, todo not null); `repository/UsuarioRepository`; 2.3 en verde
- [ ] 2.5 RED + TRIANGULATE (spec `modelo-persistente` › Usuario único): `sistema` duplicado → `DataIntegrityViolationException`; `sistema` + `acortez` → ambos persisten; usuario sin identificador → violación. Falla el duplicado
- [ ] 2.6 GREEN: `@UniqueConstraint(name="uk_usuario_usuario", columnNames="usuario")` (D7); 2.5 en verde
- [ ] 2.7 RED: `model/AuditoriaAppTest` (`@DataJpaTest`) sobre `Marca`: con `UsuarioActual = sistema`, persistir, flush/clear, releer → `fechaAlta`, `fechaModificacion` no nulas y usuario de carga/modificación = `sistema`; triangular con `acortez`; baja nula. Falla porque `Marca`/`AuditoriaApp` no existen
- [ ] 2.8 GREEN: `AuditoriaApp extends EntityId` traída de la semilla y adaptada (`LocalDateTime`, `ManyToOne(fetch=LAZY)`, `@ToString.Exclude` en los tres usuarios) con `@PrePersist` (D1); `Marca extends AuditoriaApp` (`denominacion`, `codigo` Integer not null) + `MarcaRepository`; 2.7 en verde
- [ ] 2.9 RED: en `AuditoriaAppTest`, (a) contexto vacío → `saveAndFlush` lanza `DataIntegrityViolationException`; (b) cambiar en memoria `fechaAlta` y `usuarioCarga` de una marca persistida, flush/clear, releer → valores originales; (c) tras `clear()`, `PersistenceUnitUtil.isLoaded(marca, "usuarioCarga")` es `false`. Falla (b) mientras falte `updatable=false`
- [ ] 2.10 GREEN: `updatable=false` en `fecha_alta` y `usuario_carga_id` (D2); 2.9 en verde. REFACTOR: extraer `DatosDePrueba` (fábricas de `Usuario`/`Marca`) en `src/test/.../model` y re-ejecutar el grupo
- [ ] 2.11 Documentar en `docs/etapa-1.md` superclases, `@PrePersist` + `UsuarioActual`, por qué `updatable=false`, y la fila de semilla de `EntityId`/`AuditoriaApp`/`Usuario`; verificar `./gradlew test` en verde

## 3. Catálogos y sus unicidades (spec `modelo-persistente`)

- [ ] 3.1 RED: `model/CatalogosPersistenciaTest`: ida y vuelta (flush/clear) de `Rubro`, `CondicionIva`, `TipoMoneda` (`PES`, símbolo `$`), `ListaPrecio`, `PuntoVenta`; falla porque no existen
- [ ] 3.2 GREEN: traer de la semilla `Rubro`, `CondicionIva`, `TipoMoneda`, `ListaPrecio`, `PuntoVenta` adaptados (D4, longitudes: `codigoAfip` moneda 10, `simbolo` 5, `codigo` lista 20) y sus repositorios; 3.1 en verde
- [ ] 3.3 RED + TRIANGULATE (Número de punto de venta único): PV 1 duplicado → violación; PV 1 y PV 2 → ambos persisten
- [ ] 3.4 GREEN: `uk_punto_venta_numero` (D7); 3.3 en verde
- [ ] 3.5 RED: `Articulo` sin marca, sin rubro y sin alícuota → releído con los tres nulos; artículo con marca y rubro → releído con ambos (LAZY no inicializado tras `clear`); falla porque `Articulo` no existe
- [ ] 3.6 GREEN: `Articulo` de la semilla adaptado (LAZY, `codigo` 30) + campo nuevo `alicuotaIva` `Double` nullable + `ArticuloRepository`; 3.5 en verde
- [ ] 3.7 RED + TRIANGULATE (Artículo único por lista): mismo artículo dos veces en la misma lista → violación; mismo artículo en dos listas → ambos persisten; ida y vuelta de `precioVenta`
- [ ] 3.8 GREEN: `ListaPrecioArticulo` de la semilla adaptado (LAZY, obligatorios) + `uk_lista_precio_articulo` + repositorio; 3.7 en verde. REFACTOR: fábricas de catálogos en `DatosDePrueba`; re-ejecutar
- [ ] 3.9 Documentar en `docs/etapa-1.md` los catálogos, las cuatro unicidades con su nombre y la adaptación de la semilla (`alicuotaIva` agregado, EAGER → LAZY); verificar `./gradlew test` en verde

## 4. Agregado Cliente (spec `agregado-cliente`)

- [ ] 4.1 RED: `model/ClienteAgregadoTest`: guardar cliente con contacto y domicilio nuevos **solo con `ClienteRepository`**, flush/clear, releer → contacto y domicilio con sus datos; falla porque no existen
- [ ] 4.2 GREEN: `Contacto`, `Domicilio` (extends `EntityId`) y `Cliente` de la semilla adaptados (`cuitCuil` 13, D6 sin `orphanRemoval` todavía) + `ClienteRepository`; 4.1 en verde
- [ ] 4.3 RED + TRIANGULATE: cliente sin domicilio → violación; cliente sin contacto → violación; eliminar cliente → contacto y domicilio no existen (`TestEntityManager.find` devuelve `null`); reemplazar domicilio → el anterior no existe. Falla el reemplazo (sin `orphanRemoval`)
- [ ] 4.4 GREEN: `orphanRemoval=true`, `optional=false`, LAZY en ambos `@OneToOne` (D6); 4.3 en verde
- [ ] 4.5 Documentar en `docs/etapa-1.md` el agregado Cliente (FK en `cliente`, por qué no hay repositorio de contacto/domicilio, cambio `orphanRemoval` respecto de la semilla); verificar `./gradlew test` en verde

## 5. Agregado Factura (spec `agregado-factura` y estado/fechas de `modelo-persistente`)

- [ ] 5.1 RED: `model/FacturaVentaTest` (unitario, sin Spring): factura nueva → `getDetalles()` vacía y no nula; `agregarDetalle` ×2 → la factura tiene 2 y cada detalle referencia a la factura; `reemplazarDetalles(List.of(nuevo))` → misma instancia de colección (`isSameAs`), un detalle, el nuevo apunta a la factura; `quitarDetalle` → el detalle deja de referenciar a la factura. Falla porque no existen
- [ ] 5.2 GREEN: `EstadoFactura { BORRADOR, EMITIDA, ANULADA }` (nuevo); `FacturaVentaDetalle` y `FacturaVenta` de la semilla adaptados (`LocalDate`, `EstadoFactura`, sin `@Builder`, `detalles` sin setter) con `agregarDetalle` (semilla) y `quitarDetalle`/`reemplazarDetalles` (nuevos, D5) — solo la lógica de colección, sin mapeo todavía; 5.1 en verde
- [ ] 5.3 RED: `model/FacturaVentaAgregadoTest` (`@DataJpaTest`): guardar factura BORRADOR sin cliente con 2 detalles solo vía `FacturaVentaRepository`, flush/clear, releer → 2 detalles, cada `detalle.getFactura()` es la factura releída, cliente `null`; triangular con 1 detalle. Falla sin mapeo/cascada
- [ ] 5.4 GREEN: mapeo completo (D3, D5, D11): tabla `factura_venta`, ManyToOne LAZY obligatorias/opcional, `@OneToMany(mappedBy="factura", cascade=ALL, orphanRemoval=true)`, `@Enumerated(STRING)` length 20, `cae` 14, `observaciones` 500, `descripcion` 255, `@ToString.Exclude` en asociaciones; `FacturaVentaRepository`; 5.3 en verde
- [ ] 5.5 RED + TRIANGULATE: (a) releer, `quitarDetalle`, flush/clear → 1 detalle y el quitado no existe; (b) `reemplazarDetalles` sobre la factura gestionada → misma colección, solo el nuevo en la base; (c) eliminar BORRADOR con cliente → factura y detalles no existen, cliente/contacto/domicilio/PV/condición/moneda/lista/artículo/LPA existen; (d) factura con PV nuevo no guardado → `saveAndFlush` falla (`InvalidDataAccessApiUsageException` por entidad transitoria) y el PV no se insertó. Si alguno ya pasa en verde, demostrar que falla quitando temporalmente la anotación responsable y restaurarla
- [ ] 5.6 RED + TRIANGULATE (unicidad y nulabilidad): nº 1 duplicado en PV 1 → violación; nº 1 en PV 1 y PV 2 → ambos; dos BORRADOR con `numero` null en PV 1 → ambos; factura sin PV → violación; detalle sin artículo de lista → violación; observaciones de 500 → persiste, de 501 → violación. Falla el duplicado
- [ ] 5.7 GREEN: `uk_factura_venta_punto_venta_numero` (D7); 5.6 en verde
- [ ] 5.8 RED + TRIANGULATE (estado y fechas): consulta nativa `select estado from factura_venta where id = ?` devuelve `'EMITIDA'`; ida y vuelta de los tres estados; `fechaEmision` 2026-11-11 releída exacta. Falla si el estado no es STRING (verificarlo quitando temporalmente `@Enumerated` y restaurando)
- [ ] 5.9 REFACTOR: fábricas de factura/detalle en `DatosDePrueba`; re-ejecutar grupos 2-5
- [ ] 5.10 Documentar en `docs/etapa-1.md`: lado propietario y `mappedBy`, por qué la factura es raíz de agregado, **qué datos permanecen al eliminar un borrador** (con el test 5.5c como evidencia), y un **ejemplo de SQL generado** (DDL de `factura_venta` y `factura_venta_detalle` + INSERT de cabecera y detalles copiados del log de 5.3); verificar `./gradlew test` en verde

## 6. Modelo completo: inventario, esquema, `toString` y repositorios (spec `modelo-persistente`)

- [ ] 6.1 RED: `model/ModeloMapeoTest` (`@DataJpaTest`): metamodelo con exactamente las 14 entidades (y sin `EstadoFactura`/`UsuarioActual`); `INFORMATION_SCHEMA.TABLES` del esquema `PUBLIC` = 14 tablas, sin `entity_id`/`auditoria_app` ni tabla intermedia `factura_venta_detalles`; `factura_venta_detalle.factura_id` existe y es `NOT NULL`; columnas de auditoría presentes en `marca`. Debe pasar si los grupos 2-5 están completos: demostrar la RED quitando temporalmente `mappedBy` (aparece la tabla intermedia) y restaurar
- [ ] 6.2 RED: `model/RepresentacionTextualTest` (unitario): `toString()` de factura con 2 detalles enlazados no lanza `StackOverflowError` y no contiene `detalles`; `toString()` de usuario no contiene la clave. Verificar la RED agregando temporalmente una asociación sin exclusión y restaurar
- [ ] 6.3 RED: `repository/RepositoriosTest` (`@DataJpaTest`): con `new Repositories(applicationContext)`, `hasRepositoryFor` es `true` para las 11 raíces/catálogos y `false` para `FacturaVentaDetalle`, `Contacto`, `Domicilio`; falla si falta alguno (completar los que no se hayan creado)
- [ ] 6.4 GREEN: completar repositorios faltantes (D8); 6.1-6.3 en verde; revisar que ninguna entidad use `@Data`, `@Builder`, `@AllArgsConstructor` ni `@EqualsAndHashCode` (`grep` sobre `model/` sin resultados)
- [ ] 6.5 Documentar en `docs/etapa-1.md` el diagrama de relaciones (quién tiene cada FK) y la lista de repositorios con la justificación de los tres ausentes; verificar `./gradlew test` en verde

## 7. `DataLoader` provisional (spec `datos-de-ejemplo`)

- [ ] 7.1 RED: `config/DataLoaderPerfilTest` (`@DataJpaTest @ActiveProfiles("test") @Import(DataLoader.class)`): el contexto no tiene bean `DataLoader` y `usuarioRepository.count() == 0`; falla porque `DataLoader` no existe (crear la clase vacía con `@Component` sin perfil: debe fallar porque el bean existe)
- [ ] 7.2 GREEN: `@Profile("!test")` en `DataLoader`; 7.1 en verde
- [ ] 7.3 RED: `config/DataLoaderTest` (`@DataJpaTest @Import(DataLoader.class)`, sin perfil): tras arrancar existen exactamente `sistema`, `acortez` y `mriquero`; `UsuarioActual.get()` es `null`; falla porque `run` no hace nada
- [ ] 7.4 GREEN: `DataLoader implements CommandLineRunner`, `@Transactional run`, chequeo `count() > 0`, usuarios, `UsuarioActual.set(sistema)` y `finally { clear() }` (estructura de `CargaDatosPrueba` adaptada: repositorios en lugar de `EntityManager`, perfil en lugar de propiedad); 7.3 en verde
- [ ] 7.5 RED + GREEN (catálogos): `DataLoaderTest` exige monedas `PES` y `DOL`, ≥ 2 PV, ≥ 1 condición de IVA, artículos con precio en una lista (uno sin marca), ≥ 2 clientes con contacto y domicilio, todos con usuario de carga; traer los catálogos de `CargaDatosPrueba` (agregar `DOL`, contacto/domicilio por cascada)
- [ ] 7.6 RED: `DataLoaderTest` exige 6 facturas (3 EMITIDA, 1 ANULADA, 2 BORRADOR); BORRADOR con `numero` y `cae` nulos; EMITIDA/ANULADA con número y `cae` que cumple `\d{14}`; al menos una sin cliente; cada una con ≥ 1 detalle; `importeCobrado == 0`. Falla (0 facturas) — **solo si el estudiante confirmó D9**
- [ ] 7.7 GREEN: seis facturas con importes literales (D9; sin operaciones aritméticas), comentario de clase "provisional: C-05 la reemplaza por `FacturaService` (DD-11)"; 7.6 en verde
- [ ] 7.8 RED + GREEN (idempotencia): en `DataLoaderTest`, llamar `dataLoader.run()` otra vez → cantidades de usuarios, catálogos y facturas sin cambios
- [ ] 7.9 RED + GREEN (atomicidad y limpieza ante error): `config/DataLoaderFalloTest` con perfil `test`, `@Transactional(propagation = NOT_SUPPORTED)`, `@SpyBean` sobre un repositorio de catálogo que lanza en su `save`, loader creado con `AutowireCapableBeanFactory.createBean(DataLoader.class)` (D10): la excepción se propaga, `UsuarioActual.get()` es `null` y `usuarioRepository.count() == 0`
- [ ] 7.10 Verificación manual: `./gradlew bootRun` (JDK 17), consola H2 en `http://localhost:8080/h2-console` con `jdbc:h2:mem:facturacion`, `select estado, count(*) from factura_venta group by estado` → 3/1/2 y `select * from usuario` → 2 filas; registrar el resultado en `docs/etapa-1.md`
- [ ] 7.11 Documentar en `docs/etapa-1.md` el `DataLoader` (provisional, qué carga, tabla de facturas con sus importes literales, qué cambió respecto de `CargaDatosPrueba` y por qué no calcula importes); verificar `./gradlew test` en verde

## 8. Informe y README

- [ ] 8.1 Completar `docs/etapa-1.md` según `knowledge-base/11` (sin repetir el enunciado): decisiones y alternativas (D1-D11 resumidas), dificultades y su resolución, evidencia de verificaciones (comando y cantidad de pruebas), motor probado (**H2**; PostgreSQL pendiente antes de C-05), tabla de **semilla reutilizada** (origen `api-facturas` y `TP2_JPA_corregido`, trabajos grupales previos; qué se trajo, qué se adaptó, qué es nuevo), consulta a `arca-spirng` solo para nombres (D12) y **uso de IA** (para qué, qué se revisó, qué se corrigió) con nota de que el formato está pendiente de confirmar con el docente (pregunta Alta de `10_preguntas_abiertas.md`); verificar que cada sección de la consigna (SQL, relaciones, raíz de agregado, borrado de borrador) está presente
- [ ] 8.2 Actualizar `README.md`: sección "Etapa 1" con cómo ver los datos de ejemplo en la consola H2, que las pruebas usan el perfil `test` (sin `DataLoader`) y H2 embebida, y enlace a `docs/etapa-1.md`; verificar que cada comando documentado se ejecutó tal cual

## 9. Verificación integral

- [ ] 9.1 Con `JAVA_HOME` = JDK 17: `./gradlew clean test bootJar` en verde y genera `build/libs/facturacion-arca-1.0.0.jar`; anotar en `docs/etapa-1.md` la cantidad final de pruebas (baseline de 1.1 + nuevas) y que todas corren sobre H2
- [ ] 9.2 Revisión final contra las specs: cada scenario de `auditoria-entidades`, `modelo-persistente`, `agregado-factura`, `agregado-cliente` y `datos-de-ejemplo` tiene al menos un test que lo ejercita (tabla scenario → test en el resumen del apply)

## Workflow follow-up

- Entregar al estudiante los comandos Git (rama `etapa-1`, commits, PR, tag `etapa-1`) con la línea `Co-Authored-By`; él los ejecuta.
- Verificación con PostgreSQL 16 (contenedor en 5433) antes de C-05.
- `/opsx:archive etapa-1-persistencia-jpa` y marcar `[x]` C-02 en `CHANGES.md`.
