package ar.edu.utn.frm.facturacion.concurrencia;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Elige el motor de base de datos de la suite de concurrencia.
 *
 * <ul>
 *   <li>Por defecto (la corrida normal de {@code ./gradlew test}): una H2 en memoria propia, con espera de bloqueo
 *       de 10 s para que la cola de ocho hilos no venza por lentitud de la maquina.
 *   <li>Con {@code -Dfacturacion.pruebas.motor=postgres} (tarea {@code testPostgres}): el PostgreSQL local, en la
 *       URL de {@code facturacion.pruebas.postgres-url} (por defecto el puerto 5433 del PostgreSQL portatil).
 * </ul>
 */
public final class MotorDePruebas {

    /** Propiedad de sistema que pide el motor: {@code postgres} o, por defecto, H2. */
    public static final String PROPIEDAD_MOTOR = "facturacion.pruebas.motor";
    public static final String PROPIEDAD_URL_POSTGRES = "facturacion.pruebas.postgres-url";
    public static final String URL_POSTGRES_POR_DEFECTO = "jdbc:postgresql://localhost:5433/facturacion";
    public static final String URL_H2 = "jdbc:h2:mem:concurrencia;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";

    private MotorDePruebas() {
    }

    public static boolean pidePostgres() {
        return "postgres".equalsIgnoreCase(System.getProperty(PROPIEDAD_MOTOR));
    }

    /** Nombre del producto que debe informar {@code DatabaseMetaData}: {@code PostgreSQL} o {@code H2}. */
    public static String motorPedido() {
        return pidePostgres() ? "PostgreSQL" : "H2";
    }

    public static void registrar(DynamicPropertyRegistry registro) {
        if (pidePostgres()) {
            registro.add("spring.datasource.url",
                    () -> System.getProperty(PROPIEDAD_URL_POSTGRES, URL_POSTGRES_POR_DEFECTO));
            registro.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registro.add("spring.datasource.username", () -> "facturacion");
            registro.add("spring.datasource.password", () -> "facturacion");
            registro.add("spring.h2.console.enabled", () -> "false");
        } else {
            registro.add("spring.datasource.url", () -> URL_H2);
        }
    }
}
