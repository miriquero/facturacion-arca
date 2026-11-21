package ar.edu.utn.frm.facturacion.arca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.operator.OperatorCreationException;

/** Reusa el ticket vigente incluso al reiniciar. El archivo queda junto al certificado, fuera del repo. */
final class TicketWsaa {
    private final Path clave;
    private final Path certificado;
    private final WsaaClient wsaa = new WsaaClient();

    TicketWsaa(Path clave, Path certificado) {
        this.clave = clave;
        this.certificado = certificado.toAbsolutePath();
    }

    synchronized WsaaClient.Ticket obtener() throws IOException, InterruptedException, GeneralSecurityException,
            CMSException, OperatorCreationException {
        String huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(certificado)));
        Path cache = certificado.resolveSibling("wsaa-wsfe-" + huella + ".local");
        var datos = new Properties();
        if (Files.isRegularFile(cache)) {
            try (var entrada = Files.newInputStream(cache)) {
                datos.load(entrada);
            }
            Instant vence = Instant.parse(datos.getProperty("vence"));
            if (vence.isAfter(Instant.now())) {
                return new WsaaClient.Ticket(datos.getProperty("token"), datos.getProperty("firma"), vence);
            }
        }
        Path temporal = Files.createTempFile(cache.getParent(), "wsaa-", ".tmp");
        try {
            var ticket = wsaa.autenticar(clave, certificado);
            datos.setProperty("vence", ticket.vence().toString());
            datos.setProperty("token", ticket.token());
            datos.setProperty("firma", ticket.firma());
            try (var salida = Files.newOutputStream(temporal)) {
                datos.store(salida, null);
            }
            Files.move(temporal, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return ticket;
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}
