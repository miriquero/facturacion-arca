package ar.edu.utn.frm.facturacion.arca;

import ar.edu.utn.frm.facturacion.exception.ReglaNegocioException;
import ar.edu.utn.frm.facturacion.model.FacturaVenta;
import ar.edu.utn.frm.facturacion.model.IntentoEmisionArca;
import ar.edu.utn.frm.facturacion.repository.IntentoEmisionArcaRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("homologacion")
public class EmisionArca {
    private final long cuit;
    private final Set<Integer> puntos;
    private final IntentoEmisionArcaRepository intentos;
    private final TransactionTemplate transaccion;
    private final WsfeClient wsfe = new WsfeClient();
    private final TicketWsaa tickets;
    private final Clock reloj;

    public record Autorizada(long numero, String cae, LocalDate vencimiento) {}

    public EmisionArca(@Value("${arca.cuit}") long cuit, @Value("${arca.punto-venta}") int punto,
            @Value("${arca.puntos-adicionales:}") String adicionales,
            @Value("${arca.clave-privada}") Path clave, @Value("${arca.certificado}") Path certificado,
            IntentoEmisionArcaRepository intentos, PlatformTransactionManager gestor, Clock reloj) {
        if (cuit < 10000000000L || cuit > 99999999999L || punto < 1 || punto > 99999) {
            throw new IllegalArgumentException("Configurá un CUIT de 11 dígitos y un punto de venta de homologación.");
        }
        this.cuit = cuit;
        var habilitados = new HashSet<Integer>();
        habilitados.add(punto);
        if (!adicionales.isBlank()) {
            for (String adicional : adicionales.split(",", -1)) {
                int numero = Integer.parseInt(adicional.strip());
                if (numero < 1 || numero > 99999) {
                    throw new IllegalArgumentException("El punto de venta adicional debe estar entre 1 y 99999.");
                }
                habilitados.add(numero);
            }
        }
        this.puntos = Set.copyOf(habilitados);
        this.intentos = intentos;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tickets = new TicketWsaa(clave, certificado);
    }

    public Autorizada emitir(FacturaVenta factura) {
        validar(factura);
        int punto = factura.getPuntoVenta().getNumero();
        try {
            var ticket = tickets.obtener();
            var previo = intentos.findById(factura.getId()).orElse(null);
            if (previo != null) {
                if (previo.getCuit() != cuit || previo.getPunto() != punto) {
                    throw new ReglaNegocioException("La emisión pendiente pertenece a otra configuración de ARCA.");
                }
                return recuperar(ticket, solicitud(factura, previo.getNumero()));
            }
            long numero = wsfe.ultimo(ticket, cuit, punto) + 1;
            var solicitud = solicitud(factura, numero);
            BigDecimal cotizacion = BigDecimal.ONE;
            if ("DOL".equals(solicitud.moneda())) {
                LocalDate hoy = LocalDate.now(reloj);
                LocalDate fechaBase = solicitud.fecha().isAfter(hoy) ? hoy : solicitud.fecha();
                try {
                    cotizacion = wsfe.cotizacion(ticket, cuit, solicitud.moneda(), fechaBase.minusDays(1));
                } catch (IOException | NumberFormatException error) {
                    throw new ReglaNegocioException("No se pudo obtener la cotización del dólar de ARCA. "
                            + error.getMessage());
                }
            }
            transaccion.executeWithoutResult(estado -> intentos.saveAndFlush(
                    new IntentoEmisionArca(factura.getId(), cuit, punto, numero)));
            WsfeClient.Resultado respuesta;
            try {
                respuesta = wsfe.solicitar(ticket, cuit, solicitud, cotizacion);
            } catch (IOException | RuntimeException error) {
                return recuperar(ticket, solicitud);
            }
            if (respuesta.autorizacion() == null) {
                transaccion.executeWithoutResult(estado -> intentos.deleteById(factura.getId()));
                throw new ReglaNegocioException("ARCA rechazó la factura: " + respuesta.rechazo());
            }
            return autorizada(numero, respuesta.autorizacion());
        } catch (ReglaNegocioException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ReglaNegocioException("La comunicación con ARCA se interrumpió.");
        } catch (Exception error) {
            throw new ReglaNegocioException("No se pudo completar la comunicación con ARCA. "
                    + "Si hay una emisión pendiente, al volver a emitir se consultará el comprobante reservado.");
        }
    }

    private Autorizada recuperar(WsaaClient.Ticket ticket, WsfeClient.Solicitud solicitud)
            throws IOException, InterruptedException {
        return wsfe.consultar(ticket, cuit, solicitud).map(autorizacion -> autorizada(solicitud.numero(), autorizacion))
                .orElseThrow(() -> new ReglaNegocioException("Emisión pendiente de confirmar en ARCA. "
                        + "Volvé a pulsar Emitir para consultar el mismo número sin enviar otro comprobante."));
    }

    private static Autorizada autorizada(long numero, WsfeClient.Autorizacion autorizacion) {
        return new Autorizada(numero, autorizacion.cae(), autorizacion.vencimiento());
    }

    private void validar(FacturaVenta factura) {
        String moneda = factura.getTipoMoneda().getCodigoAfip();
        if (!puntos.contains(factura.getPuntoVenta().getNumero())
                || !Set.of("PES", "DOL").contains(moneda)
                || factura.getDetalles().stream().anyMatch(detalle -> detalle.getImporteIva() != 0)
                || factura.getImporteTotal() <= 0) {
            throw new ReglaNegocioException("Homologación requiere Factura C en pesos o dólares, total positivo, "
                    + "sin IVA discriminado y un punto de venta configurado.");
        }
    }

    private WsfeClient.Solicitud solicitud(FacturaVenta factura, long numero) {
        var cliente = factura.getCliente();
        String documento = cliente == null ? "0" : cliente.getCuitCuil().replace("-", "").strip();
        if (cliente != null && !documento.matches("[0-9]{11}")) {
            throw new ReglaNegocioException("El receptor debe tener un CUIT de 11 dígitos.");
        }
        int condicion = factura.getCondicionIva().getCodigoAfip();
        if (cliente == null && condicion != 5) {
            throw new ReglaNegocioException("Una factura sin cliente debe usar la condición Consumidor Final.");
        }
        var total = BigDecimal.valueOf(factura.getImporteTotal()).setScale(2, RoundingMode.UNNECESSARY);
        return new WsfeClient.Solicitud(factura.getPuntoVenta().getNumero(), numero, factura.getFechaEmision(), cliente == null ? 99 : 80,
                Long.parseLong(documento), condicion, total, factura.getTipoMoneda().getCodigoAfip());
    }
}
