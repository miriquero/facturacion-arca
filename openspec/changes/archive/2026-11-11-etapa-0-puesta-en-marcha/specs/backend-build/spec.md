# Spec Delta

## Purpose

Garantiza que el backend se construya, pruebe y empaquete de forma reproducible con las coordenadas y versiones fijadas por la consigna, sin depender de herramientas instaladas globalmente.

## ADDED Requirements

### Requirement: Coordenadas y versiones fijadas
El proyecto MUST identificarse como `facturacion-arca`, grupo `ar.edu.utn.frm`, versión `1.0.0`, paquete base `ar.edu.utn.frm.facturacion`, y MUST usar Spring Boot 3.3.5 y Java 17 como versión de lenguaje y de bytecode.

#### Scenario: Bytecode Java 17
- **WHEN** se compila el backend
- **THEN** las clases generadas tienen versión de bytecode 61 (Java 17)

#### Scenario: Versión de Spring Boot
- **WHEN** se listan las dependencias resueltas del backend
- **THEN** los artefactos `org.springframework.boot` resueltos son de la versión 3.3.5

### Requirement: Wrapper Gradle versionado
El repositorio MUST incluir el wrapper de Gradle en la versión 8.10.2 (scripts para Windows y POSIX y su configuración), de modo que no sea necesario instalar Gradle globalmente.

#### Scenario: Versión del wrapper
- **WHEN** se ejecuta el wrapper con la opción de versión en una máquina sin Gradle global y con JDK 17
- **THEN** informa Gradle 8.10.2

### Requirement: Dependencias declaradas
El backend MUST declarar soporte Web, Data JPA, Validation, Lombok solo en compilación con procesamiento de anotaciones, drivers H2 y PostgreSQL solo en ejecución, dependencias de prueba sobre JUnit Platform, OpenPDF 2.0.3 y Apache POI 5.3.0 (formato OOXML).

#### Scenario: Versiones de reportes
- **WHEN** se listan las dependencias de compilación resueltas
- **THEN** aparecen OpenPDF 2.0.3 y Apache POI 5.3.0 sin ser reemplazadas por otra versión

#### Scenario: Drivers solo en ejecución
- **WHEN** se listan las dependencias de compilación
- **THEN** los drivers H2 y PostgreSQL no forman parte del classpath de compilación pero sí del de ejecución

### Requirement: Contexto de aplicación arranca
La aplicación MUST arrancar su contexto Spring completo con la configuración por defecto, sin servicios externos.

#### Scenario: Contexto con configuración por defecto
- **WHEN** se ejecutan las pruebas del backend sin base de datos externa ni variables `SPRING_*`
- **THEN** el contexto de la aplicación se inicia sin errores

### Requirement: Estructura de paquetes por responsabilidad
El paquete base MUST contener los subpaquetes `model`, `dto`, `repository`, `service`, `controller`, `exception`, `config` y `report`, cada uno versionado con una descripción de su responsabilidad.

#### Scenario: Paquetes presentes en el classpath
- **WHEN** se compilan las fuentes principales
- **THEN** cada uno de los ocho subpaquetes existe en el classpath con su descriptor de paquete

### Requirement: Artefacto ejecutable
La tarea de empaquetado MUST producir un único JAR ejecutable en `build/libs/facturacion-arca-1.0.0.jar`.

#### Scenario: Pruebas y empaquetado
- **WHEN** se ejecuta `./gradlew test bootJar` (o `gradlew.bat test bootJar`) con JDK 17
- **THEN** la ejecución finaliza con éxito y existe `build/libs/facturacion-arca-1.0.0.jar`

#### Scenario: Arranque en el puerto 8080
- **WHEN** se ejecuta `bootRun` sin argumentos
- **THEN** el backend escucha en el puerto 8080
