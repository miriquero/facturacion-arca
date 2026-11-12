# Spec Delta

## Purpose

Asegura que el repositorio contenga todo lo necesario para reproducir el proyecto (código, configuración, wrapper, lockfile y README) y nada que se genere localmente, de modo que cualquier persona pueda arrancarlo siguiendo el README.

## ADDED Requirements

### Requirement: Exclusión de artefactos generados
El repositorio MUST excluir del control de versiones los artefactos de compilación (`build/`, `.gradle/`, `frontend/dist/`), las dependencias instaladas (`node_modules/`) y los archivos locales de IDE y sistema operativo.

#### Scenario: Árbol limpio tras compilar
- **WHEN** se ejecutan la compilación y las pruebas del backend y del frontend
- **THEN** ninguno de los archivos generados aparece como no versionado en el estado del repositorio

### Requirement: Inclusión de lo necesario para reproducir
El repositorio MUST versionar el código, `application.yml`, `docker-compose.yml`, los scripts y la configuración del wrapper Gradle (incluido su JAR) y `frontend/package-lock.json`, con finales de línea que permitan ejecutar `gradlew` en POSIX y `gradlew.bat` en Windows.

#### Scenario: Wrapper no ignorado
- **WHEN** se consulta si `gradle/wrapper/gradle-wrapper.jar` y `frontend/package-lock.json` están ignorados
- **THEN** ninguno de los dos está ignorado

### Requirement: README reproducible
El `README.md` MUST documentar los requisitos (JDK 17, Node >= 18.13, Docker opcional), los comandos de arranque, pruebas y compilación del backend y del frontend en PowerShell y Bash, el uso del perfil `postgres`, la consola H2 y el motor de base de datos con el que se verificó cada resultado.

#### Scenario: Arranque siguiendo el README
- **WHEN** el estudiante sigue el README en una máquina con JDK 17 y Node >= 18.13
- **THEN** logra iniciar el backend en 8080, abrir la consola H2, servir el frontend en 5173 y generar el JAR sin pasos no documentados

#### Scenario: Motor declarado
- **WHEN** se lee la sección de verificación del README
- **THEN** indica explícitamente qué verificaciones se hicieron con H2 y cuáles con PostgreSQL
