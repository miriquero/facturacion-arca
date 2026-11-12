# Etapa 1 - Persistencia con JPA

## Motor probado y verificaciones

Todas las pruebas de persistencia corren sobre **H2 2.x embebida (`@DataJpaTest`)**: cada contexto obtiene su propia base y cada prueba hace rollback. **PostgreSQL no se probó en esta etapa**: Docker/WSL no arrancan en este equipo y el puerto 5432 está ocupado; la verificación con PostgreSQL 16 (puerto 5433) queda agendada **antes de la etapa 4**. Las restricciones usadas (UNIQUE, NOT NULL, longitudes) son SQL estándar, pero eso no reemplaza la prueba real.

| Verificación | Resultado |
|---|---|
| Baseline etapa 0 (`./gradlew clean test`, JDK 17) | 19 pruebas en verde |
| Tras agregar `@ActiveProfiles("test")` a los tres `@SpringBootTest` | 19 en verde |
| `./gradlew clean test bootJar` al cerrar la etapa | **105 pruebas, 0 fallas, 0 omitidas** (86 nuevas); genera `build/libs/facturacion-arca-1.0.0.jar` |
| `cd frontend && npm test` | 7 pruebas en verde |
| Manual: arranque del JAR (JDK 17) y consola H2, `jdbc:h2:mem:facturacion` | `select estado, count(*) from factura_venta group by estado` devuelve ANULADA 1, BORRADOR 2, EMITIDA 3; `select usuario from usuario` devuelve `acortez`, `mriquero`, `sistema` |

Para varias pruebas que ya pasaban en su primera ejecución (p. ej. `orphanRemoval`, `EnumType.STRING`, ausencia de tabla intermedia, repositorios, atomicidad del `DataLoader`) se comprobó que **fallan** al quitar temporalmente la anotación responsable (`orphanRemoval`, `@Enumerated`, `mappedBy`, `@ToString.Exclude`, `@Transactional`, `clear()`, un repositorio) y se restauró.

## Modelo y relaciones

14 entidades y 2 superclases `@MappedSuperclass` (`EntityId`: id IDENTITY; `AuditoriaApp`: fechas y usuarios de alta, modificación y baja). Las superclases no generan tabla: sus columnas aparecen en cada tabla hija. Hay una `@ManyToOne` LAZY por cada FK (quien tiene la FK es el lado propietario):

| Entidad | FK (columna) | Referencia | Obligatoria |
|---|---|---|---|
| `factura_venta_detalle` | `factura_id` | factura | sí (lado propietario del agregado) |
| `factura_venta_detalle` | `lista_precio_articulo_id` | precio del artículo en lista | sí |
| `factura_venta` | `punto_venta_id`, `condicion_iva_id`, `tipo_moneda_id` | catálogos | sí |
| `factura_venta` | `cliente_id` | cliente | no (consumidor final) |
| `cliente` | `contacto_id`, `domicilio_id` | contacto, domicilio | sí (`@OneToOne`, FK en `cliente`) |
| `lista_precio_articulo` | `lista_precio_id`, `articulo_id` | lista, artículo | sí |
| `articulo` | `marca_id`, `rubro_id` | marca, rubro | no |
| toda entidad auditable | `usuario_carga_id`, `usuario_modificacion_id` / `usuario_baja_id` | usuario | sí / no |

Unicidades (con nombre): `uk_usuario_usuario`, `uk_punto_venta_numero`, `uk_lista_precio_articulo (lista_precio_id, articulo_id)`, `uk_factura_venta_punto_venta_numero (punto_venta_id, numero)`. Un borrador tiene `numero NULL` y los NULL no colisionan en UNIQUE, por eso varios borradores conviven. Fechas de negocio `LocalDate`, de auditoría `LocalDateTime`; `estado` es `EstadoFactura` con `EnumType.STRING`. Los importes son `double` y en esta etapa **no se calcula ninguno** (son literales en el `DataLoader`).

Auditoría (D1/D2): `@PrePersist` completa alta, modificación y usuarios con `UsuarioActual` (un `ThreadLocal<Usuario>` con `set/get/clear`). `fecha_alta` y `usuario_carga_id` llevan `updatable = false`; si el contexto está vacío el INSERT falla por NOT NULL (`DataIntegrityViolationException`). No se usó `@EnableJpaAuditing`: la consigna nombra `UsuarioActual` y en la etapa 4 el interceptor de `X-Usuario` lo llenará.

Repositorios: 11 `JpaRepository<X, Long>` (usuario, factura, cliente, punto de venta, condición de IVA, moneda, artículo, lista, lista-artículo, marca, rubro). **No hay** para detalle, contacto ni domicilio: son partes de un agregado y solo se alcanzan por su raíz.

## Por qué la factura es raíz de agregado

El renglón no tiene sentido sin su factura: nace, cambia y muere con ella. Por eso `FacturaVenta.detalles` es `@OneToMany(mappedBy = "factura", cascade = ALL, orphanRemoval = true)`: la FK vive en el renglón (sin `mappedBy` Hibernate crearía una tabla intermedia `factura_venta_detalles`; la prueba de esquema lo verifica), guardar la factura guarda los renglones, quitar un renglón de la colección lo borra y borrar la factura borra los renglones. La colección no tiene setter: `agregarDetalle` fija los dos extremos, `quitarDetalle` los desenlaza y `reemplazarDetalles` hace `clear()` + `add` **sobre la misma instancia** (asignar otra lista con `orphanRemoval` hace fallar a Hibernate). La cascada es solo hacia los renglones: cliente, catálogos y precios son referencias sin cascada, y una factura que apunta a un punto de venta no guardado falla sin insertarlo. De la misma forma, `Cliente` es raíz de `Contacto` y `Domicilio` (`@OneToOne`, cascada + `orphanRemoval`).

## Qué datos permanecen al borrar un borrador

Prueba `FacturaVentaAgregadoTest.eliminarUnBorradorConservaCatalogosYCliente`: se borra un BORRADOR con cliente y dos renglones. **Desaparecen** la factura y sus renglones. **Permanecen** el cliente con su contacto y domicilio, los puntos de venta, la condición de IVA, la moneda, la lista de precios, los artículos y los precios de lista (se cuentan las filas de cada tabla con `JdbcTemplate`).

## SQL generado (H2, copiado del log de arranque con `show-sql`)

```sql
create table factura_venta (
    id bigint generated by default as identity,
    fecha_alta timestamp(6) not null,
    fecha_modificacion timestamp(6) not null,
    cae varchar(14),
    estado enum ('ANULADA','BORRADOR','EMITIDA') not null,
    fecha_emision date not null,
    importe_total float(53) not null,
    numero bigint,
    observaciones varchar(500),
    usuario_carga_id bigint not null,
    cliente_id bigint,
    punto_venta_id bigint not null,
    ... (resto de columnas omitidas en este informe)
    primary key (id)
)
create table factura_venta_detalle (
    id bigint generated by default as identity,
    cantidad float(53) not null,
    descripcion varchar(255),
    importe_subtotal float(53) not null,
    precio_unitario float(53) not null,
    factura_id bigint not null,
    lista_precio_articulo_id bigint not null,
    primary key (id)
)
alter table if exists factura_venta
   add constraint uk_factura_venta_punto_venta_numero unique (punto_venta_id, numero)
alter table if exists factura_venta_detalle
   add constraint FKq8ub7hx33xfj7yfaj4059npba foreign key (factura_id) references factura_venta

-- al guardar una factura con renglones (una sola llamada a save sobre la factura):
insert into factura_venta (cae, cae_fecha_vencimiento, cliente_id, condicion_iva_id, estado, fecha_alta, ...,
    punto_venta_id, resultado_afip, tipo_moneda_id, usuario_baja_id, usuario_carga_id, usuario_modificacion_id, id)
    values (?, ?, ?, ?, ?, ?, ..., ?, ?, ?, ?, ?, ?, default)
insert into factura_venta_detalle (cantidad, descripcion, factura_id, importe_iva, importe_neto, importe_subtotal,
    lista_precio_articulo_id, porcentaje_bonificacion, precio_unitario, id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, default)
```

Se ve la FK `factura_id not null` en el renglón, el nombre explícito de la unicidad y que el renglón se inserta después de la cabecera por la cascada.

## DataLoader provisorio

`config/DataLoader` (`CommandLineRunner`, `@Profile("!test")`, `run` transaccional) carga `sistema`, `acortez` y `mriquero` (el tercero a pedido del estudiante; la consigna dice "incluyan"), catálogos (monedas `PES` y `DOL`, 2 puntos de venta, 2 condiciones de IVA, rubros, marcas, 3 artículos con precio en la lista general, uno sin marca), 2 clientes y **seis facturas** con importes escritos a mano (IVA 21 % ya incluido, sin ninguna operación aritmética, `importeCobrado = 0`, `importeSaldo = importeTotal`). Es **provisorio**: números y CAE son datos, no reglas; en la etapa 4 el bloque de facturas se reemplaza por llamadas a `FacturaService`. Omite todo si ya hay usuarios, fija `UsuarioActual` y lo limpia en un `finally`.

| Factura | PV | Estado | Número | Cliente | Moneda | Renglones (neto / IVA / subtotal) | Total |
|---|---|---|---|---|---|---|---|
| 1 | 1 | EMITIDA | 1 | Juan | PES | 2 martillos: 6000 / 1260 / 7260 | 7260 |
| 2 | 1 | EMITIDA | 2 | (sin cliente) | PES | 1 televisor: 60000 / 12600 / 72600 | 72600 |
| 3 | 1 | ANULADA | 3 | Maria | PES | 3 cables: 24000 / 5040 / 29040 | 29040 |
| 4 | 2 | EMITIDA | 1 | Juan | DOL | 1 cable: 8000 / 1680 / 9680 | 9680 |
| 5 | 1 | BORRADOR | nulo | Juan | PES | 1 martillo 3000 / 630 / 3630 + 2 cables 16000 / 3360 / 19360 | 22990 |
| 6 | 2 | BORRADOR | nulo | (sin cliente) | PES | 1 televisor: 60000 / 12600 / 72600 | 72600 |

Las pruebas verifican 3/1/2 por estado, borradores sin número ni CAE, CAE de 14 dígitos, que el total sea la suma de los subtotales, idempotencia (segunda ejecución no cambia nada), exclusión con el perfil `test`, y que ante un error a mitad de la carga no quedan datos parciales y `UsuarioActual` queda vacío (`@SpyBean` sobre un repositorio + loader creado con `createBean` para que tenga el proxy transaccional). Las tres pruebas `@SpringBootTest` de la etapa 0 usan el perfil `test` porque la H2 por defecto tiene nombre fijo y sobrevive entre contextos.

## Semilla reutilizada (DD-13)

Origen: mi proyecto previo `api-facturas` (`model/` y `config/CargaDatosPrueba`), basado en los TPs de JPA/JPQL, que fueron **trabajo grupal**; el `TP2_JPA_corregido` tiene el mismo modelo sin Lombok y solo se cita. Cada pieza entró solo detrás de una prueba en rojo.

| Se trajo | Se adaptó | Es nuevo |
|---|---|---|
| Campos y nombres de columna de las 14 entidades, `mappedBy` + cascada de factura, estructura de `CargaDatosPrueba` (métodos fábrica, catálogos, "si hay usuarios no hago nada") | `Date` a `LocalDate`/`LocalDateTime`; `estado` String a `EstadoFactura` STRING; `@ManyToOne` EAGER a LAZY; auditoría manual a `@PrePersist` + `UsuarioActual`; `updatable = false`; `orphanRemoval` en cliente; sin `@Builder`, `@AllArgsConstructor` ni `@EqualsAndHashCode`; asociaciones y clave fuera de `toString`; longitudes; usuarios `admin`/`ana` a los tres de la consigna; 7 facturas (6 EMITIDA, 1 ANULADA) a 6 (3/1/2); `cobrado = total` a 0; importes calculados con `double` a literales; `System.out` a `Logger`; propiedad `app.cargar-datos-prueba` a `@Profile` | `EstadoFactura`, `UsuarioActual`, `alicuotaIva` en `Articulo`, las 4 unicidades con nombre, `quitarDetalle`/`reemplazarDetalles`, los 11 repositorios (la semilla usaba `EntityManager`), moneda `DOL`, todas las pruebas |

Referencia del docente (`arca-spirng`): consultada **solo para nombres** (ubicación de `UsuarioActual` en `model`, de `DataLoader` en `config` con exclusión por perfil, y la lista de repositorios); no se copió código.

## Decisiones principales

Auditoría con `@PrePersist` + `UsuarioActual` y no Spring Data Auditing (D1); `updatable = false` (D2); todo LAZY y opcionales explícitos (D3); Lombok sin `@Data` ni `equals/hashCode` (D4); colección de renglones sin setter (D5); `@OneToOne` con FK en `cliente` y unidireccional (D6); unicidades con `@UniqueConstraint` con nombre (D7); repositorios sin métodos propios (D8); seis facturas con literales en vez de cargar solo catálogos (D9, confirmado por el estudiante); `@DataJpaTest` y perfil `test` (D10). Dificultades: con IDENTITY el INSERT ocurre en el `save`, por eso cada violación de restricción va en su propio método de prueba; el `DataLoader` corre también dentro de `@DataJpaTest` (lo ejecuta el contexto de Spring Boot), lo que permite probarlo sin `@SpringBootTest`.

## Uso de IA

- Herramientas: Claude Code (Anthropic) con sub-agentes de OpenSpec (propuesta, diseño, tareas y ejecución de la etapa); formato provisional, a confirmar con el docente (pregunta abierta de prioridad Alta en la KB).
- Qué se pidió: la propuesta y el diseño de la etapa, las pruebas en rojo, el código mínimo para pasarlas, la ejecución de las verificaciones y este informe.
- Qué se revisó y qué se corrigió: revisé y aprobé la propuesta y el diseño antes de implementar, y tomé dos decisiones: cargar las seis facturas con importes literales en lugar de solo catálogos, y agregar mi usuario `mriquero` a los datos de ejemplo. Después de la implementación, la IA me armó una guía de revisión con los puntos clave de cada archivo, y con ella leí y verifiqué `FacturaVenta` (agregado, `mappedBy`, `orphanRemoval`, colección sin setter, unicidad con número nulo), `AuditoriaApp` y `UsuarioActual` (`@PrePersist`, `ThreadLocal`, `updatable = false`), `Cliente`, el `DataLoader` (perfil, transacción, `finally`, importes literales) y las pruebas de borrado de borrador y de rollback (`@SpyBean`, `NOT_SUPPORTED`, `createBean`). Se corrigieron los acentos del informe, que la herramienta había generado sin tildes.
- Los commits propuestos llevan la línea `Co-Authored-By`.
