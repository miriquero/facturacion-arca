package ar.edu.utn.frm.facturacion;

import ar.edu.utn.frm.facturacion.config.ConfiguracionArcaLocal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FacturacionApplication {

    public static void main(String[] args) {
        var aplicacion = new SpringApplication(FacturacionApplication.class);
        aplicacion.setDefaultProperties(ConfiguracionArcaLocal.cargar());
        aplicacion.run(args);
    }
}
