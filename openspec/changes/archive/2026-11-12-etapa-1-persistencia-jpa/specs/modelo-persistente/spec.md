# Spec Delta

## Purpose

Define las 14 entidades persistentes del dominio de facturación, sus tablas, obligatoriedad, longitudes y unicidades, cómo se guarda el estado de una factura y desde qué puntos de acceso se leen y guardan los datos, para que las etapas siguientes se apoyen en un modelo verificado.

## ADDED Requirements

### Requirement: Catorce entidades con su tabla
El modelo MUST tener exactamente 14 entidades persistentes —usuario, factura de venta, detalle de factura, cliente, contacto, domicilio, punto de venta, condición de IVA, tipo de moneda, artículo, lista de precios, artículo de lista de precios, marca y rubro—, cada una con su propia tabla; la factura de venta MUST usar la tabla `factura_venta`.

#### Scenario: Inventario de entidades
- **WHEN** se consulta el metamodelo de persistencia
- **THEN** contiene exactamente las 14 entidades enumeradas y ni el estado de factura ni el contexto de auditoría son entidades

#### Scenario: Tablas generadas
- **WHEN** se inspecciona el esquema generado
- **THEN** existen 14 tablas de entidades, entre ellas `factura_venta` y `factura_venta_detalle`, y no existe ninguna tabla intermedia entre factura y detalle

### Requirement: Campos obligatorios rechazados cuando faltan
La base MUST rechazar la inserción de una entidad a la que le falta un campo obligatorio del modelo: en la factura, punto de venta, condición de IVA, moneda, fecha de emisión, estado e importe total; en el detalle, factura y artículo de lista; en el usuario, identificador de usuario, clave, nombre y apellido; en el cliente, CUIT/CUIL, denominación, contacto y domicilio.

#### Scenario: Factura sin punto de venta
- **WHEN** se intenta guardar una factura completa salvo el punto de venta
- **THEN** la operación falla con una violación de integridad de datos

#### Scenario: Usuario sin identificador
- **WHEN** se intenta guardar un usuario sin identificador de usuario
- **THEN** la operación falla con una violación de integridad de datos

#### Scenario: Detalle sin artículo de lista
- **WHEN** se intenta guardar una factura con un renglón sin artículo de lista de precios
- **THEN** la operación falla con una violación de integridad de datos

### Requirement: Referencias opcionales admitidas
La factura MUST admitir no tener cliente (se presenta como Consumidor Final) y el artículo MUST admitir no tener marca, rubro ni alícuota de IVA propia.

#### Scenario: Factura sin cliente
- **WHEN** se guarda una factura sin cliente, se limpia el contexto de persistencia y se relee
- **THEN** la factura existe y su cliente es nulo

#### Scenario: Artículo sin marca ni rubro
- **WHEN** se guarda un artículo sin marca, sin rubro y sin alícuota de IVA
- **THEN** al releerlo esos tres datos son nulos

### Requirement: Longitudes máximas del modelo
Los textos MUST respetar las longitudes del modelo: identificador de usuario 50; nombre y apellido 100; CUIT/CUIL 13; CAE 14; estado 20; observaciones 500; descripción del renglón 255; código de moneda 10 y símbolo 5; código de artículo 30; código de lista 20. Un valor más largo MUST ser rechazado por la base.

#### Scenario: Observaciones en el límite
- **WHEN** se guarda una factura con observaciones de exactamente 500 caracteres
- **THEN** se persiste y se relee con los 500 caracteres

#### Scenario: Observaciones excedidas
- **WHEN** se intenta guardar una factura con observaciones de 501 caracteres
- **THEN** la operación falla con una violación de integridad de datos

### Requirement: Usuario único
El identificador de usuario MUST ser único.

#### Scenario: Usuario duplicado
- **WHEN** existe el usuario `sistema` y se intenta guardar otro usuario `sistema`
- **THEN** la operación falla con una violación de integridad de datos

#### Scenario: Usuarios distintos
- **WHEN** se guardan los usuarios `sistema` y `acortez`
- **THEN** ambos se persisten

### Requirement: Número de punto de venta único
El número de punto de venta MUST ser único.

#### Scenario: Punto de venta duplicado
- **WHEN** existe el punto de venta 1 y se intenta guardar otro punto de venta 1
- **THEN** la operación falla con una violación de integridad de datos

### Requirement: Artículo único por lista de precios
La combinación de lista de precios y artículo MUST ser única; un mismo artículo MAY figurar en listas distintas.

#### Scenario: Misma lista y artículo
- **WHEN** un artículo ya tiene precio en una lista y se intenta guardar otro precio para el mismo artículo en la misma lista
- **THEN** la operación falla con una violación de integridad de datos

#### Scenario: Mismo artículo en otra lista
- **WHEN** un artículo ya tiene precio en una lista y se guarda su precio en una segunda lista
- **THEN** ambos precios se persisten

### Requirement: Número de factura único por punto de venta
La combinación de punto de venta y número de factura MUST ser única cuando el número está asignado; las facturas en BORRADOR MUST mantener el número nulo y varias pueden coexistir en el mismo punto de venta.

#### Scenario: Número repetido en el mismo punto de venta
- **WHEN** existe la factura número 1 del punto de venta 1 y se intenta guardar otra factura número 1 en ese punto de venta
- **THEN** la operación falla con una violación de integridad de datos

#### Scenario: Mismo número en otro punto de venta
- **WHEN** existe la factura número 1 del punto de venta 1 y se guarda la factura número 1 del punto de venta 2
- **THEN** ambas se persisten

#### Scenario: Dos borradores sin número
- **WHEN** se guardan dos facturas en BORRADOR con número nulo en el mismo punto de venta
- **THEN** ambas se persisten con número nulo

### Requirement: Estado de factura persistido por nombre
El estado de la factura MUST tomar uno de los valores BORRADOR, EMITIDA o ANULADA y MUST guardarse en la base como el nombre del estado, nunca como su posición.

#### Scenario: Valor almacenado
- **WHEN** se guarda una factura en estado EMITIDA y se lee la columna de estado con una consulta nativa
- **THEN** el valor almacenado es el texto `EMITIDA`

#### Scenario: Ida y vuelta de cada estado
- **WHEN** se guardan facturas en BORRADOR, EMITIDA y ANULADA y se releen en un contexto limpio
- **THEN** cada una conserva su estado

### Requirement: Fechas de negocio sin hora
La fecha de emisión, la fecha de anulación y el vencimiento del CAE MUST guardarse como fechas sin hora.

#### Scenario: Fecha de emisión conservada
- **WHEN** se guarda una factura con fecha de emisión 2026-11-11 y se relee en un contexto limpio
- **THEN** su fecha de emisión es exactamente 2026-11-11

### Requirement: Representación textual sin recursión ni clave
La representación textual de las entidades MUST NOT recorrer asociaciones (evitando recursión infinita y carga diferida) y la del usuario MUST NOT incluir su clave.

#### Scenario: Factura con detalles
- **WHEN** se obtiene la representación textual de una factura que tiene dos detalles enlazados en ambos sentidos
- **THEN** se obtiene sin error y no incluye los detalles

#### Scenario: Usuario
- **WHEN** se obtiene la representación textual de un usuario con clave
- **THEN** no contiene el valor de la clave

### Requirement: Acceso a datos solo por raíces y catálogos
El sistema MUST ofrecer acceso de lectura y escritura para usuario, factura, cliente y cada catálogo (punto de venta, condición de IVA, tipo de moneda, artículo, lista de precios, artículo de lista, marca y rubro), y MUST NOT ofrecer acceso independiente para detalle de factura, contacto ni domicilio.

#### Scenario: Accesos disponibles
- **WHEN** se inspecciona el contexto de la aplicación
- **THEN** existe un repositorio para cada una de las 11 entidades raíz o catálogo y ninguno para detalle, contacto o domicilio
