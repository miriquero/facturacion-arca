package ar.edu.utn.frm.facturacion.arca;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Autenticación de homologación para el servicio de facturación electrónica. */
public final class WsaaClient {

    private static final URI ENDPOINT = URI.create("https://wsaahomo.afip.gov.ar/ws/services/LoginCms");
    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String WSAA = "http://wsaa.view.sua.dvadac.desein.afip.gov";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record Ticket(String token, String firma, Instant vence) {
        @Override
        public String toString() {
            return "Ticket WSAA con vencimiento " + vence;
        }
    }

    public Ticket autenticar(Path rutaClave, Path rutaCertificado) throws IOException, InterruptedException,
            GeneralSecurityException, CMSException, OperatorCreationException {
        String cms = firmarSolicitud(rutaClave, rutaCertificado);
        String mensaje = """
                <soap:Envelope xmlns:soap="%s" xmlns:wsaa="%s">
                  <soap:Body>
                    <wsaa:loginCms><wsaa:in0>%s</wsaa:in0></wsaa:loginCms>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(SOAP, WSAA, cms);
        HttpRequest solicitud = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(mensaje))
                .build();
        HttpResponse<String> respuesta = http.send(solicitud, HttpResponse.BodyHandlers.ofString());
        return leerRespuesta(respuesta.statusCode(), respuesta.body());
    }

    private String firmarSolicitud(Path rutaClave, Path rutaCertificado)
            throws IOException, GeneralSecurityException, CMSException, OperatorCreationException {
        X509Certificate certificado;
        try (var entrada = Files.newInputStream(rutaCertificado)) {
            certificado = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(entrada);
        }
        certificado.checkValidity();
        PrivateKey clave = leerClave(rutaClave);
        Instant ahora = Instant.now();
        String tra = """
                <loginTicketRequest version="1.0">
                  <header>
                    <uniqueId>%d</uniqueId>
                    <generationTime>%s</generationTime>
                    <expirationTime>%s</expirationTime>
                  </header>
                  <service>wsfe</service>
                </loginTicketRequest>
                """.formatted(ahora.getEpochSecond(), ahora.minusSeconds(300), ahora.plusSeconds(600));

        var firmante = new JcaContentSignerBuilder("SHA256withRSA").build(clave);
        var digests = new JcaDigestCalculatorProviderBuilder().build();
        var generador = new CMSSignedDataGenerator();
        generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(digests).build(firmante, certificado));
        generador.addCertificates(new JcaCertStore(List.of(certificado)));
        var firmado = generador.generate(new CMSProcessableByteArray(tra.getBytes(StandardCharsets.UTF_8)), true);
        return Base64.getEncoder().encodeToString(firmado.getEncoded());
    }

    private PrivateKey leerClave(Path rutaClave) throws IOException {
        try (var parser = new PEMParser(Files.newBufferedReader(rutaClave, StandardCharsets.US_ASCII))) {
            Object contenido = parser.readObject();
            var conversor = new JcaPEMKeyConverter();
            if (contenido instanceof PrivateKeyInfo info) {
                return conversor.getPrivateKey(info);
            }
            if (contenido instanceof PEMKeyPair par) {
                return conversor.getKeyPair(par).getPrivate();
            }
            throw new IOException("Se esperaba una clave privada PEM sin cifrar.");
        }
    }

    private Ticket leerRespuesta(int estado, String xml) throws IOException {
        Document sobre = leerXml(xml);
        if (sobre.getElementsByTagNameNS(SOAP, "Fault").item(0) instanceof Element falla) {
            String codigo = texto(falla, "faultcode");
            throw new IOException("WSAA rechazó la solicitud (" + codigo + ").");
        }
        if (estado != 200) {
            throw new IOException("WSAA respondió HTTP " + estado + ".");
        }
        Document ticket = leerXml(texto(sobre.getDocumentElement(), "loginCmsReturn"));
        String token = texto(ticket.getDocumentElement(), "token");
        String firma = texto(ticket.getDocumentElement(), "sign");
        Instant vence = OffsetDateTime.parse(texto(ticket.getDocumentElement(), "expirationTime")).toInstant();
        if (!vence.isAfter(Instant.now())) {
            throw new IOException("WSAA devolvió un ticket vencido.");
        }
        return new Ticket(token, firma, vence);
    }

    static Document leerXml(String xml) throws IOException {
        try {
            var fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return fabrica.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException error) {
            throw new IOException("WSAA devolvió XML inválido.", error);
        }
    }

    static String texto(Element elemento, String nombre) throws IOException {
        var nodo = elemento.getElementsByTagNameNS("*", nombre).item(0);
        if (nodo == null || nodo.getTextContent().isBlank()) {
            throw new IOException("Falta " + nombre + " en la respuesta de WSAA.");
        }
        return nodo.getTextContent().strip();
    }
}
