# Proposal

## Why

Tras la etapa 0 el backend arranca, pero el paquete `model` está vacío: no existe ninguna entidad, repositorio ni dato de ejemplo, y todas las etapas siguientes (proyección JPQL y reportes en C-03, API de lectura en C-04, escritura, estados y auditoría en C-05) dependen de este modelo. La Etapa 1 de la consigna (semanas 2-3, 20 % de la nota de etapas) exige implementar el modelo persistente y **demostrar con pruebas** que los agregados conservan relaciones, restricciones y dependencias; un error de mapeo aquí (lado propietario, cascada hacia catálogos, estado ordinal, unicidad faltante) se arrastraría a todo lo evaluado después.

## What Changes

- Superclases mapeadas `EntityId` (id `Long` IDENTITY) y `AuditoriaApp` (fechas `LocalDateTime`, usuarios `ManyToOne` LAZY, alta y usuario de carga no actualizables), que completan alta/modificación al persistir con el usuario del contexto de auditoría.
- Auxiliar de contexto `UsuarioActual` (ThreadLocal con `set/get/clear`), mínimo necesario para la auditoría y el `DataLoader`; el interceptor que lo llena desde `X-Usuario` queda para C-05.
- Las 14 entidades del modelo (`Usuario, FacturaVenta, FacturaVentaDetalle, Cliente, Contacto, Domicilio, PuntoVenta, CondicionIva, TipoMoneda, Articulo, ListaPrecio, ListaPrecioArticulo, Marca, Rubro`) con campos obligatorios, longitudes y unicidades del modelo; enum `EstadoFactura` persistido por nombre; `numero` nulo mientras la factura es BORRADOR.
- Agregado Factura–Detalles: el detalle posee la FK, la factura agrega/quita/reemplaza renglones manteniendo ambos extremos y la misma instancia de colección; cascada y eliminación de huérfanos solo hacia los detalles, nunca hacia catálogos ni cliente.
- Agregado Cliente–Contacto/Domicilio con cascada y eliminación de huérfanos.
- Repositorios Spring Data para las raíces y catálogos (11); ninguno para detalle, contacto ni domicilio.
- `DataLoader` **provisional**: `CommandLineRunner` transaccional, excluido del perfil `test`, que omite la carga si ya hay usuarios; crea `sistema`, `acortez` y `mriquero`, catálogos, clientes y seis facturas (3 EMITIDA, 1 ANULADA, 2 BORRADOR) con importes literales precalculados; prepara y limpia el contexto de auditoría. La versión integrada con `FacturaService` la reemplaza en C-05 (DD-11).
- Pruebas de persistencia con `@DataJpaTest` sobre H2 que guardan, limpian el contexto y releen; verifican relación inversa, cliente opcional, catálogos conservados, huérfanos y restricciones con datos que realmente las violan.
- Las pruebas `@SpringBootTest` existentes pasan a ejecutarse con el perfil `test`.
- Informe `docs/etapa-1.md` (SQL generado, relaciones y FK, raíz de agregado, qué queda al borrar un borrador, semilla reutilizada y cambios, motor probado, uso de IA) y actualización del README.
- Semilla (DD-13): modelo de `api-facturas/model/` y `config/CargaDatosPrueba.java` (secundaria `JPA/TP2_JPA_corregido`), adaptados y traídos solo detrás de un test en rojo.

## Capabilities

### New Capabilities
- `auditoria-entidades`: identificador común y datos de auditoría (alta, modificación, baja) de las entidades auditables, completados desde el usuario del contexto y protegidos contra modificación posterior del alta.
- `modelo-persistente`: las 14 entidades y sus tablas, obligatoriedad, longitudes, unicidades, persistencia del estado por nombre, representación textual sin recursión y acceso a datos solo por raíces de agregado y catálogos.
- `agregado-factura`: la factura como raíz de agregado de sus renglones — propiedad de la FK, consistencia bidireccional, cascada, eliminación de huérfanos, reemplazo de renglones y aislamiento de los catálogos.
- `agregado-cliente`: el cliente como dueño de su contacto y su domicilio, persistidos y eliminados con él.
- `datos-de-ejemplo`: carga inicial de usuarios, catálogos, clientes y facturas representativas al arrancar fuera del perfil de pruebas, idempotente y con el contexto de auditoría limpio al terminar.

### Modified Capabilities
<!-- Ninguna: backend-build, configuracion-base-de-datos, frontend-esqueleto y repositorio-reproducible no cambian sus requisitos. Las pruebas de contexto existentes solo agregan el perfil test (detalle de implementación). -->

## Impact

- **Código nuevo**: `src/main/java/ar/edu/utn/frm/facturacion/model/**` (2 superclases, 14 entidades, `EstadoFactura`, `UsuarioActual`), `repository/**` (11 interfaces), `config/DataLoader.java`; pruebas en `src/test/java/ar/edu/utn/frm/facturacion/{model,repository,config}/**` y un soporte de datos de prueba.
- **Código modificado**: `FacturacionApplicationTests`, `ConfiguracionH2Test`, `ConsolaH2Test` (agregan `@ActiveProfiles("test")`); `README.md`.
- **Documentación nueva**: `docs/etapa-1.md`.
- **Dependencias**: ninguna nueva (Data JPA, Lombok, H2 y test ya declarados en etapa 0); `build.gradle` no cambia.
- **Esquema**: con `ddl-auto: update` Hibernate crea 14 tablas en H2; no hay migraciones.
- **Governance**: ALTO (modelo y constraints que todo lo demás referencia). Esta propuesta no escribe código; el apply espera la revisión del estudiante.
- **Fuera de alcance**: endpoints REST, DTO y JPQL (C-03/C-04), cálculo de importes con `BigDecimal`, máquina de estados y numeración/CAE (C-05), interceptor `X-Usuario`, `@PreUpdate`/`darDeBaja` (C-05), login, cobros, paginación, CRUD de catálogos, conversión de monedas, integración fiscal, reportes, verificación contra PostgreSQL (pospuesta a antes de C-05) y cualquier comando Git que cambie estado.
