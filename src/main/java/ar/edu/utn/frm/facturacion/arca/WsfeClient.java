package ar.edu.utn.frm.facturacion.arca;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;

final class WsfeClient {
    private static final URI URL = URI.create("https://wswhomo.afip.gov.ar/wsfev1/service.asmx");
    private static final String NS = "http://ar.gov.afip.dif.FEV1/";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int FACTURA_C = 11;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    record Solicitud(int punto, long numero, LocalDate fecha, int docTipo, long docNro, int condicion,
                     BigDecimal total, String moneda) {}
    record Autorizacion(String cae, LocalDate vencimiento) {}
    record Resultado(Autorizacion autorizacion, String rechazo) {}

    List<Integer> puntosVenta(WsaaClient.Ticket ticket, long cuit) throws IOException, InterruptedException {
        Element resultado = llamar(ticket, cuit, "FEParamGetPtosVenta", "");
        if (tiene(resultado, "Errors")) {
            throw new IOException("ARCA no pudo consultar los puntos de venta: " + mensaje(resultado));
        }
        var puntos = resultado.getElementsByTagNameNS(NS, "PtoVenta");
        List<Integer> numeros = new ArrayList<>();
        for (int i = 0; i < puntos.getLength(); i++) {
            Element punto = (Element) puntos.item(i);
            if ("N".equals(WsaaClient.texto(punto, "Bloqueado"))) {
                numeros.add(Integer.parseInt(WsaaClient.texto(punto, "Nro")));
            }
        }
        return List.copyOf(numeros);
    }

    long ultimo(WsaaClient.Ticket ticket, long cuit, int punto) throws IOException, InterruptedException {
        Element resultado = llamar(ticket, cuit, "FECompUltimoAutorizado",
                "<PtoVta>%d</PtoVta><CbteTipo>%d</CbteTipo>".formatted(punto, FACTURA_C));
        if (tiene(resultado, "Errors")) {
            throw new IOException("ARCA no pudo consultar la numeración: " + mensaje(resultado));
        }
        return Long.parseLong(WsaaClient.texto(resultado, "CbteNro"));
    }

    BigDecimal cotizacion(WsaaClient.Ticket ticket, long cuit, String moneda, LocalDate fecha)
            throws IOException, InterruptedException {
        Element respuesta = llamar(ticket, cuit, "FEParamGetCotizacion",
                "<MonId>%s</MonId><FchCotiz>%s</FchCotiz>".formatted(escapar(moneda), FECHA.format(fecha)));
        if (!(respuesta.getElementsByTagNameNS(NS, "ResultGet").item(0) instanceof Element resultado)) {
            throw new IOException("ARCA no devolvió una cotización: " + mensaje(respuesta));
        }
        if (!moneda.equals(WsaaClient.texto(resultado, "MonId"))) {
            throw new IOException("ARCA devolvió la cotización de otra moneda.");
        }
        BigDecimal valor = new BigDecimal(WsaaClient.texto(resultado, "MonCotiz"));
        if (valor.signum() <= 0) {
            throw new IOException("ARCA devolvió una cotización no positiva.");
        }
        return valor;
    }

    Resultado solicitar(WsaaClient.Ticket ticket, long cuit, Solicitud s, BigDecimal cotizacion)
            throws IOException, InterruptedException {
        String datos = """
                <FeCAEReq>
                  <FeCabReq><CantReg>1</CantReg><PtoVta>%d</PtoVta><CbteTipo>%d</CbteTipo></FeCabReq>
                  <FeDetReq><FECAEDetRequest>
                    <Concepto>1</Concepto><DocTipo>%d</DocTipo><DocNro>%d</DocNro>
                    <CbteDesde>%d</CbteDesde><CbteHasta>%d</CbteHasta><CbteFch>%s</CbteFch>
                    <ImpTotal>%s</ImpTotal><ImpTotConc>0</ImpTotConc><ImpNeto>%s</ImpNeto>
                    <ImpOpEx>0</ImpOpEx><ImpTrib>0</ImpTrib><ImpIVA>0</ImpIVA>
                    <MonId>%s</MonId><MonCotiz>%s</MonCotiz>
                    <CondicionIVAReceptorId>%d</CondicionIVAReceptorId>
                  </FECAEDetRequest></FeDetReq>
                </FeCAEReq>
                """.formatted(s.punto(), FACTURA_C, s.docTipo(), s.docNro(), s.numero(), s.numero(),
                        FECHA.format(s.fecha()), s.total().toPlainString(), s.total().toPlainString(),
                        escapar(s.moneda()), cotizacion.toPlainString(), s.condicion());
        Element resultado = llamar(ticket, cuit, "FECAESolicitar", datos);
        if (resultado.getElementsByTagNameNS(NS, "FECAEDetResponse").item(0) instanceof Element detalle) {
            String estado = WsaaClient.texto(detalle, "Resultado");
            if ("R".equals(estado)) {
                return new Resultado(null, mensaje(resultado));
            }
            if ("A".equals(estado) && coincideNumero(detalle, s.numero())) {
                return new Resultado(autorizacion(detalle, "CAE", "CAEFchVto"), null);
            }
        }
        // Un error general puede referirse a una colisión de numeración. Se confirma por consulta.
        throw new IOException("Respuesta de autorización sin resultado concluyente: " + mensaje(resultado));
    }

    Optional<Autorizacion> consultar(WsaaClient.Ticket ticket, long cuit, Solicitud s)
            throws IOException, InterruptedException {
        Element respuesta = llamar(ticket, cuit, "FECompConsultar", """
                <FeCompConsReq><CbteTipo>%d</CbteTipo><CbteNro>%d</CbteNro><PtoVta>%d</PtoVta></FeCompConsReq>
                """.formatted(FACTURA_C, s.numero(), s.punto()));
        if (!(respuesta.getElementsByTagNameNS(NS, "ResultGet").item(0) instanceof Element detalle)) {
            return Optional.empty();
        }
        if (!coincideNumero(detalle, s.numero())
                || !Long.toString(s.docNro()).equals(WsaaClient.texto(detalle, "DocNro"))
                || !Integer.toString(s.docTipo()).equals(WsaaClient.texto(detalle, "DocTipo"))
                || !FECHA.format(s.fecha()).equals(WsaaClient.texto(detalle, "CbteFch"))
                || !s.moneda().equals(WsaaClient.texto(detalle, "MonId"))
                || new BigDecimal(WsaaClient.texto(detalle, "ImpTotal")).compareTo(s.total()) != 0
                || !"A".equals(WsaaClient.texto(detalle, "Resultado"))) {
            throw new IOException("El comprobante registrado en ARCA difiere de la solicitud local.");
        }
        return Optional.of(autorizacion(detalle, "CodAutorizacion", "FchVto"));
    }

    private Element llamar(WsaaClient.Ticket ticket, long cuit, String operacion, String datos)
            throws IOException, InterruptedException {
        String xml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><%1$s xmlns="%2$s">
                    <Auth><Token>%3$s</Token><Sign>%4$s</Sign><Cuit>%5$d</Cuit></Auth>
                    %6$s
                  </%1$s></soap:Body>
                </soap:Envelope>
                """.formatted(operacion, NS, escapar(ticket.token()), escapar(ticket.firma()), cuit, datos);
        var solicitud = HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("SOAPAction", "\"" + NS + operacion + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(xml)).build();
        var respuesta = http.send(solicitud, HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() != 200) {
            throw new IOException("WSFEv1 respondió HTTP " + respuesta.statusCode() + ".");
        }
        var doc = WsaaClient.leerXml(respuesta.body());
        if (doc.getElementsByTagNameNS(NS, operacion + "Result").item(0) instanceof Element resultado) {
            return resultado;
        }
        throw new IOException("WSFEv1 devolvió una respuesta SOAP inesperada.");
    }

    private static boolean coincideNumero(Element detalle, long numero) throws IOException {
        return Long.toString(numero).equals(WsaaClient.texto(detalle, "CbteDesde"))
                && Long.toString(numero).equals(WsaaClient.texto(detalle, "CbteHasta"));
    }

    private static Autorizacion autorizacion(Element detalle, String campoCae, String campoFecha) throws IOException {
        String cae = WsaaClient.texto(detalle, campoCae);
        if (!cae.matches("[0-9]{14}")) {
            throw new IOException("WSFEv1 no devolvió un CAE válido.");
        }
        return new Autorizacion(cae, LocalDate.parse(WsaaClient.texto(detalle, campoFecha), FECHA));
    }

    private static boolean tiene(Element elemento, String nombre) {
        return elemento.getElementsByTagNameNS(NS, nombre).getLength() > 0;
    }

    private static String mensaje(Element resultado) {
        var mensajes = resultado.getElementsByTagNameNS(NS, "Msg");
        return mensajes.getLength() == 0 ? "sin detalle" : mensajes.item(0).getTextContent().strip();
    }

    private static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
