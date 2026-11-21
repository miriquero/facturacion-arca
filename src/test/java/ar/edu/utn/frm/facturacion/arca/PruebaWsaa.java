package ar.edu.utn.frm.facturacion.arca;

import java.nio.file.Path;

/** Prueba manual de homologación, ejecutada únicamente con probarWsaa. */
public final class PruebaWsaa {

    private PruebaWsaa() {
    }

    public static void main(String[] args) {
        try {
            Path clave = ruta("ARCA_CLAVE_PRIVADA");
            Path certificado = ruta("ARCA_CERTIFICADO");
            var ticket = new TicketWsaa(clave, certificado).obtener();
            if (args.length > 0) {
                long cuit = Long.parseLong(variable("ARCA_CUIT"));
                var wsfe = new WsfeClient();
                String punto = System.getenv("ARCA_PUNTO_VENTA");
                if (punto == null || punto.isBlank()) {
                    System.out.println("Puntos de venta no bloqueados: " + wsfe.puntosVenta(ticket, cuit));
                } else {
                    System.out.println("Último número autorizado para Factura C: "
                            + wsfe.ultimo(ticket, cuit, Integer.parseInt(punto)));
                }
                return;
            }
            System.out.println("Autenticación de homologación correcta. Vencimiento: " + ticket.vence());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("Prueba de WSAA interrumpida.");
            System.exit(1);
        } catch (Exception error) {
            System.err.println("Falló la prueba de homologación: " + error.getMessage());
            System.exit(1);
        }
    }

    private static Path ruta(String variable) {
        return Path.of(variable(variable));
    }

    private static String variable(String variable) {
        String valor = System.getenv(variable);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Configurá la variable de entorno " + variable + ".");
        }
        return valor;
    }
}
