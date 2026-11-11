# Spec Delta

## Purpose

Define con qué motor de base de datos trabaja la aplicación en cada perfil, cómo se comporta Hibernate y qué límites tiene la sesión de persistencia, para que las etapas siguientes partan de una configuración conocida y verificable.

## ADDED Requirements

### Requirement: H2 en memoria por defecto
Sin perfil activo, la aplicación MUST conectarse a H2 en memoria con la URL `jdbc:h2:mem:facturacion;DB_CLOSE_DELAY=-1`, usuario `sa` y contraseña vacía, definidos en `application.yml`.

#### Scenario: Datasource por defecto
- **WHEN** la aplicación arranca sin perfil activo
- **THEN** la conexión obtenida del datasource reporta una URL `jdbc:h2:mem:facturacion` y usuario `SA`

#### Scenario: Datos temporales
- **WHEN** se detiene y se vuelve a iniciar el proceso del backend con la configuración por defecto
- **THEN** los datos creados en la ejecución anterior ya no existen

### Requirement: Consola H2 disponible solo con H2
Con la configuración por defecto la consola web de H2 MUST estar disponible en `/h2-console`; con el perfil `postgres` MUST estar deshabilitada.

#### Scenario: Consola accesible
- **WHEN** el backend corre con la configuración por defecto y se solicita `GET /h2-console/`
- **THEN** responde 200 con la página de conexión de H2

#### Scenario: Conexión desde la consola
- **WHEN** desde la consola se conecta con driver `org.h2.Driver`, URL `jdbc:h2:mem:facturacion`, usuario `sa` y sin contraseña
- **THEN** la conexión se establece y `SHOW TABLES;` se ejecuta sin error

#### Scenario: Consola deshabilitada en postgres
- **WHEN** el perfil `postgres` está activo
- **THEN** la propiedad de habilitación de la consola H2 resuelve a `false`

### Requirement: Comportamiento de Hibernate
Hibernate MUST actualizar el esquema en el arranque (`ddl-auto: update`) y MUST registrar las sentencias SQL formateadas.

#### Scenario: Propiedades efectivas de Hibernate
- **WHEN** la aplicación arranca sin perfil activo
- **THEN** la unidad de persistencia tiene `hibernate.hbm2ddl.auto` en `update`, `hibernate.show_sql` en `true` y `hibernate.format_sql` en `true`

### Requirement: Sin sesión de persistencia abierta durante la respuesta HTTP
La aplicación MUST desactivar Open Session in View (`spring.jpa.open-in-view: false`), de modo que la sesión de persistencia no quede abierta durante la serialización de respuestas.

#### Scenario: Interceptor ausente
- **WHEN** el contexto web de la aplicación arranca
- **THEN** no existe ningún interceptor de Open EntityManager in View registrado

### Requirement: Perfil postgres
El perfil `postgres` MUST apuntar a `jdbc:postgresql://localhost:5432/facturacion` con usuario y contraseña `facturacion` y driver PostgreSQL, conservando `ddl-auto: update`, y su configuración MUST NOT alterar la del perfil por defecto.

#### Scenario: Propiedades del perfil postgres
- **WHEN** se resuelve la configuración con el perfil `postgres` activo, sin necesidad de una base en ejecución
- **THEN** la URL, el usuario, la contraseña y el driver del datasource son los de PostgreSQL indicados

#### Scenario: Perfil por defecto intacto
- **WHEN** se resuelve la configuración sin perfil activo
- **THEN** la URL del datasource sigue siendo la de H2 en memoria

#### Scenario: Arranque contra PostgreSQL
- **WHEN** el contenedor `db` está levantado y el backend se inicia con `--spring.profiles.active=postgres`
- **THEN** el backend arranca, Hibernate crea el esquema en PostgreSQL y los datos sobreviven a un reinicio del backend

### Requirement: Contenedor PostgreSQL 16
El repositorio MUST incluir un `docker-compose.yml` con un único servicio `db` basado en PostgreSQL 16, base, usuario y contraseña `facturacion`, puerto publicado 5432 y volumen nombrado para los datos.

#### Scenario: Definición válida
- **WHEN** se valida la definición con `docker compose config`
- **THEN** la definición es válida y expone el servicio `db` en el puerto 5432 con imagen PostgreSQL 16

### Requirement: Zona horaria de la JVM
Las JVM que Gradle lanza para ejecutar la aplicación y sus pruebas MUST usar la zona horaria `America/Argentina/Buenos_Aires`, para que el driver PostgreSQL en Windows no envíe una zona rechazada por el servidor.

#### Scenario: Zona horaria en pruebas
- **WHEN** se ejecutan las pruebas del backend con Gradle
- **THEN** la zona horaria por defecto de la JVM es `America/Argentina/Buenos_Aires`
