# Modelo de Datos

> Autoridad: consigna §3 y Etapa 1. Los nombres exactos de columnas/campos marcados *(ref)* provienen de la solución del docente (`arca-spirng`, `model/`) y no están todos fijados por la consigna: son sugeridos, no obligatorios, salvo donde la consigna los nombra (p. ej. `listaPrecioArticuloId`, `importeTotal`, `numeroFactura` en el DTO).

## Dominios

| Dominio | Entidades |
|---------|-----------|
| Facturación | `FacturaVenta`, `FacturaVentaDetalle`, `PuntoVenta` |
| Clientes | `Cliente`, `Contacto`, `Domicilio` |
| Artículos y precios | `Articulo`, `Rubro`, `Marca`, `ListaPrecio`, `ListaPrecioArticulo` |
| Catálogos fiscales y monetarios | `CondicionIva`, `TipoMoneda` |
| Auditoría | `Usuario` |

**14 `@Entity`** y **2 `@MappedSuperclass`** (`EntityId`, `AuditoriaApp`; las superclases **no** generan tablas). `EstadoFactura` es un enum y `UsuarioActual` un auxiliar ThreadLocal: **no son entidades**.

## Jerarquía de herencia

```
EntityId  (@MappedSuperclass)  id: Long, @Id @GeneratedValue(IDENTITY)
  |-- AuditoriaApp (@MappedSuperclass)  fechas y usuarios de alta / modificación / baja
  |     |-- FacturaVenta, Cliente, PuntoVenta, CondicionIva, TipoMoneda,
  |     |   Articulo, ListaPrecio, ListaPrecioArticulo, Marca, Rubro
  |-- (directo) Usuario, FacturaVentaDetalle, Contacto, Domicilio
```

## ERD (texto)

```
                         +----------------+
   Usuario <--(auditoría, ManyToOne LAZY)-- AuditoriaApp (todas las entidades que la heredan)

 PuntoVenta 1 ----< FacturaVenta >---- 1 CondicionIva
                       |  >---- 1 TipoMoneda
                       |  >---- 0..1 Cliente 1--1 Contacto
                       |                     1--1 Domicilio
                       | 1
                       | (mappedBy="factura", cascade ALL, orphanRemoval)
                       v *
                 FacturaVentaDetalle >---- 1 ListaPrecioArticulo >---- 1 ListaPrecio
                                                         |
                                                         >---- 1 Articulo >---- 0..1 Marca
                                                                           >---- 0..1 Rubro
```

Agregados: **FacturaVenta** (raíz) agrupa sus detalles; **Cliente** (raíz) agrupa Contacto y Domicilio. Los catálogos (PuntoVenta, CondicionIva, TipoMoneda, Articulo, ListaPrecio, ListaPrecioArticulo, Marca, Rubro) tienen **existencia independiente**: eliminar un borrador no elimina artículo, moneda ni cliente.

## Superclases

### EntityId
- `id` `Long`, `@Id`, `@GeneratedValue(strategy = IDENTITY)`.

### AuditoriaApp (extends EntityId)
| Campo | Tipo | Constraint |
|-------|------|-----------|
| `fechaAlta` | `LocalDateTime` | not null, **updatable = false** |
| `usuarioCarga` | `Usuario` ManyToOne **LAZY**, `usuario_carga_id` | not null, **updatable = false** |
| `fechaModificacion` | `LocalDateTime` | not null |
| `usuarioModificacion` | `Usuario` ManyToOne LAZY, `usuario_modificacion_id` | not null |
| `fechaBaja` | `LocalDateTime` | nullable |
| `usuarioBaja` | `Usuario` ManyToOne LAZY, `usuario_baja_id` | nullable |

Comportamiento *(ref)*: `@PrePersist` completa alta/carga/modificación con el usuario de `UsuarioActual`; `@PreUpdate` actualiza modificación; `registrarModificacion()` (llamado explícito en PUT) y `darDeBaja()` (al anular). "Alta y usuario de carga **no se actualizan** después de persistir" (consigna, etapa 1). Fechas de auditoría = `LocalDateTime`; fechas de negocio (emisión, anulación, vencimiento CAE) = `LocalDate`.

## Entidades

### Usuario (extends EntityId)
- `usuario` String, **único**, length 50, not null (clave de auditoría: `sistema`, `acortez`, `mriquero`).
- `clave` String not null *(no se usa para login; excluir de `toString`)*.
- `nombre` String length 100 not null; `apellido` String length 100 not null.
- Unicidad de usuario: exigida por consigna.

### FacturaVenta (extends AuditoriaApp) — tabla `factura_venta`
| Campo | Tipo | Constraint |
|-------|------|-----------|
| `numero` | `Long` | nulo mientras es BORRADOR; **único junto a `punto_venta_id`** |
| `fechaEmision` | `LocalDate` | not null |
| `cliente` | ManyToOne LAZY, `cliente_id` | **opcional** (null = Consumidor Final) |
| `condicionIva` | ManyToOne LAZY | obligatorio |
| `tipoMoneda` | ManyToOne LAZY | obligatorio |
| `puntoVenta` | ManyToOne LAZY | obligatorio |
| `importeCobrado`, `importeSaldo`, `importeTotal` | `double` | total not null; cobrado fuera de alcance (queda 0); `saldo = total - cobrado` |
| `cae` | String length 14 | null hasta emitir |
| `caeFechaVencimiento` | `LocalDate` | null hasta emitir (+10 días) |
| `resultadoAfip` | String | `"A"` al emitir *(nombre ref)* |
| `motivoRechazo` | String | sin uso en el alcance *(ref)* |
| `estado` | `EstadoFactura` `@Enumerated(STRING)` | not null, length 20 |
| `fechaAnulacion` | `LocalDate` | null salvo ANULADA |
| `observaciones` | String length **500** | opcional |
| `detalles` | `List<FacturaVentaDetalle>` | `@OneToMany(mappedBy="factura", cascade=ALL, orphanRemoval=true)`, inicializada `new ArrayList<>()` |

Constraints: `UNIQUE (punto_venta_id, numero)`. Lombok: `toString`/`equals`/`hashCode` **excluyen `detalles`**.
Método de dominio: `agregarDetalle(d)` fija **ambos extremos** (`detalles.add(d)` + `d.setFactura(this)`). Al reemplazar renglones se hace `detalles.clear()` **sin sustituir la instancia** de la colección.

### FacturaVentaDetalle (extends EntityId)
| Campo | Tipo | Constraint |
|-------|------|-----------|
| `factura` | ManyToOne LAZY, `factura_id` | not null (**lado propietario / FK**) |
| `listaPrecioArticulo` | ManyToOne LAZY | not null |
| `descripcion` | String | length **255**, opcional (si null se toma `articulo.denominacion`) |
| `cantidad` | `double` | not null, > 0 |
| `precioUnitario` | `double` | not null (se obtiene de `ListaPrecioArticulo.precioVenta`, nunca del request) |
| `porcentajeBonificacion` | `double` | 0..100, default 0 |
| `importeNeto`, `importeIva` | `double` | calculados |
| `importeSubtotal` | `double` | not null = neto + IVA (redondeados) |

Lombok: excluye `factura` de `toString`/`equals`/`hashCode` (evita recursión).

### Cliente (extends AuditoriaApp)
- `cuitCuil` String length 13 not null; `denominacion` String not null.
- `contacto` OneToOne **cascade ALL + orphanRemoval**, `contacto_id` not null.
- `domicilio` OneToOne cascade ALL + orphanRemoval, `domicilio_id` not null.

### Contacto / Domicilio (extends EntityId)
- Contacto: `email`, `telefono`, `celular` *(ref)*.
- Domicilio: `nombreCalle`, `numeroCalle` *(ref)*.
- Dependientes de Cliente: se persisten y eliminan con él. **No** crear repositorios propios para guardarlos (consigna etapa 1).

### PuntoVenta (extends AuditoriaApp)
- `numero` int, **único**, not null; `descripcion`; `tipoEmision`; `domicilioComercial` *(ref)*. Delimita la numeración de comprobantes.

### CondicionIva (extends AuditoriaApp)
- `codigoAfip` int *(ref; la consigna dice "código")*; `denominacion` not null.

### TipoMoneda (extends AuditoriaApp)
- `codigoAfip` String length 10 not null (valores de ejemplo: `PES`, `DOL`); `denominacion` not null; `simbolo` String length 5 not null.

### Articulo (extends AuditoriaApp)
- `codigo` String length 30 not null; `denominacion` not null; `alicuotaIva` `Double` **opcional** (si null → `facturacion.iva-por-defecto` = 21.0); `marca` ManyToOne LAZY opcional; `rubro` ManyToOne LAZY opcional.

### ListaPrecio (extends AuditoriaApp)
- `codigo` String length 20 not null; `denominacion` not null.

### ListaPrecioArticulo (extends AuditoriaApp)
- `listaPrecio` ManyToOne LAZY not null; `articulo` ManyToOne LAZY not null; `precioVenta` double not null.
- **UNIQUE (lista_precio_id, articulo_id)**.
- Su `id` es lo que viaja como `listaPrecioArticuloId` en el renglón (no el id del artículo aislado).

### Marca / Rubro (extends AuditoriaApp)
- `denominacion` not null; `codigo` Integer not null *(ref)*.

## Enums y auxiliares

- `EstadoFactura { BORRADOR, EMITIDA, ANULADA }`, persistido con `EnumType.STRING` (nunca ordinal). Transiciones permitidas: BORRADOR→EMITIDA, EMITIDA→ANULADA (método `puedeTransicionarA` *(ref)*).
- `UsuarioActual`: holder `ThreadLocal<Usuario>` con `set/get/clear`; constructor privado.

## Constraints de unicidad (resumen, exigidas por consigna)

| Tabla | Unicidad |
|-------|----------|
| `usuario` | `usuario` |
| `punto_venta` | `numero` |
| `lista_precio_articulo` | (`lista_precio_id`, `articulo_id`) |
| `factura_venta` | (`punto_venta_id`, `numero`) — un borrador mantiene `numero = NULL` (NULLs no colisionan) |

## Índices relevantes
No hay índices adicionales exigidos. Los únicos implícitos son PK, FK y las unicidades anteriores. (No hay paginación; la consulta de reporte ordena por `fechaEmision DESC, id DESC`.)

## DTOs del contrato (no son entidades)

| DTO | Campos |
|-----|--------|
| `FacturaReporteDTO` | `id`, `numeroFactura` (Long, null en borrador), `fechaEmision`, `clienteDenominacion`, `condicionIva`, `puntoVentaDescripcion`, `importeTotal`, `cantidadItems` (long), `estado` (String), `codigoMoneda`. La consigna (E2, parte B) lista `id` entre los 10 campos; el comentario de la referencia dice que lo agregó, pero el enunciado vigente ya lo incluye (IN-02) |
| `FacturaDetalladaDTO` | `id`, `numero`, `fechaEmision`, `estado`, `clienteId`, `clienteDenominacion`, `condicionIvaId`, `condicionIva`, `puntoVentaId`, `puntoVentaDescripcion`, `tipoMonedaId`, `tipoMoneda`, `importeTotal`, `importeCobrado`, `importeSaldo`, `cae`, `caeFechaVencimiento`, `fechaAnulacion`, `observaciones`, `detalles[]` |
| `FacturaDetalleResponseDTO` | `id`, `listaPrecioArticuloId`, `articuloCodigo`, `descripcion`, `cantidad`, `precioUnitario`, `porcentajeBonificacion`, `importeNeto`, `importeIva`, `importeSubtotal` |
| `FacturaRequestDTO` | `clienteId?`, `condicionIvaId`, `puntoVentaId`, `tipoMonedaId`, `fechaEmision?`, `observaciones? (<=500)`, `detalles` (no vacía, sin nulos, `@Valid`) |
| `FacturaDetalleRequestDTO` | `listaPrecioArticuloId`, `cantidad > 0`, `porcentajeBonificacion? 0..100`, `descripcion? <=255` |
| `CambioEstadoRequestDTO` | `estado` (no vacío), `observaciones? <=500` |
| `FacturaFiltrosDTO` | `fechaDesde?`, `fechaHasta?`, `estado?`, `montoMinimo?` |
| `OpcionDTO` | `id`, `etiqueta`, `valor?` (precio en lista-precio-articulos), `codigoMoneda?` (en tipos-moneda) |

## Seed data inicial (`DataLoader`)

`CommandLineRunner` **transaccional**, **excluido del perfil `test`**, que **omite toda la carga si ya existe al menos un usuario** (no repara datos parciales). Contenido:
- Usuarios `sistema` y `acortez` (consigna) y `mriquero` (el estudiante, agregado 2026-11-12).
- Catálogos: condiciones de IVA, monedas (`PES`, `DOL`), puntos de venta, rubros, marcas, artículos, lista(s) de precios y su relación lista-artículo, clientes con contacto y domicilio.
- **Seis facturas de ejemplo**: 3 EMITIDA, 1 ANULADA, 2 BORRADOR *(distribución según la referencia; la consigna solo dice "seis facturas representativas")*.
- Prepara el contexto de auditoría (`UsuarioActual`) y lo **limpia al finalizar**.
- Versión final: crea las facturas y cambia sus estados **mediante `FacturaService`** (los catálogos pueden ir por repositorios). Hasta que exista el servicio (etapa 4), en etapa 1 se verifica la persistencia con pruebas controladas, sin convertir la carga provisoria en la regla final (consigna).
