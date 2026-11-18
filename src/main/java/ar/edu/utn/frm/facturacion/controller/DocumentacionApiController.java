package ar.edu.utn.frm.facturacion.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentacionApiController {

    @GetMapping(value = {"/api", "/api/"}, produces = MediaType.TEXT_HTML_VALUE)
    public Resource documentacion() {
        return new ClassPathResource("api/documentacion.html");
    }
}
