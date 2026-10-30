package com.gestera.infraestructura.configuracion;

import static org.junit.jupiter.api.Assertions.*;
import com.gestera.GesteraAplicacion;
import com.gestera.infraestructura.servidor.ServidorGestera;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class ArranqueSpringTest {
    @Test
    void springAbreLaPaginaYCierraElServidorEnPerfilMemoria() throws Exception {
        int puerto;
        try (var reserva = new ServerSocket(0)) { puerto = reserva.getLocalPort(); }
        var contexto = SpringApplication.run(GesteraAplicacion.class,
                "--spring.profiles.active=memoria", "--gestera.puerto=" + puerto);
        ServidorGestera servidor = contexto.getBean(ServidorGestera.class);
        try {
            assertTrue(contexto.getBeansOfType(DataSource.class).isEmpty());
            var respuesta = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(servidor.direccionBase()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200,respuesta.statusCode());
            assertTrue(respuesta.body().contains("LLAVE DE ACCESO"));
        } finally {
            contexto.close();
        }
        assertDoesNotThrow(servidor::close);
    }
}
