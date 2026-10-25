package com.gestera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/** Spring administra configuracion, conexiones y cierre del servidor existente. */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class GesteraAplicacion {
    public static void main(String[] argumentos) {
        SpringApplication.run(GesteraAplicacion.class, argumentos);
    }
}
