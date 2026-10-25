package com.gestera.pruebas;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Cliente HTTP pequeño que conserva visibles todos los detalles de la respuesta. */
public final class ClienteHttpPrueba {
    private final URI direccionBase;
    private final HttpClient cliente;

    public ClienteHttpPrueba(URI direccionBase) {
        this.direccionBase = direccionBase;
        cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Respuesta get(String ruta) throws IOException, InterruptedException {
        return enviar("GET", ruta, null, null, null);
    }

    public Respuesta get(String ruta, String cookie) throws IOException, InterruptedException {
        return enviar("GET", ruta, null, null, cookie);
    }

    public Respuesta postFormulario(String ruta, String formulario)
            throws IOException, InterruptedException {
        return postFormulario(ruta, formulario, null);
    }

    public Respuesta postFormulario(String ruta, String formulario, String cookie)
            throws IOException, InterruptedException {
        return enviar(
                "POST",
                ruta,
                formulario,
                "application/x-www-form-urlencoded; charset=UTF-8",
                cookie);
    }

    public Respuesta post(String ruta, String cuerpo, String tipoContenido, String cookie)
            throws IOException, InterruptedException {
        return enviar("POST", ruta, cuerpo, tipoContenido, cookie);
    }

    public Respuesta enviar(
            String metodo,
            String ruta,
            String cuerpo,
            String tipoContenido,
            String cookie) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publicador = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(direccionBase.resolve(ruta))
                .timeout(Duration.ofSeconds(5))
                .method(metodo, publicador);
        if (tipoContenido != null) {
            solicitud.header("Content-Type", tipoContenido);
        }
        if (cookie != null) {
            solicitud.header("Cookie", cookie);
        }

        HttpRequest solicitudConstruida = solicitud.build();
        HttpResponse<String> respuesta;
        try {
            respuesta = cliente.send(
                    solicitudConstruida,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException primerFallo) {
            // El servidor de pruebas cierra cada intercambio; Windows puede descartar
            // excepcionalmente el socket justo antes de entregar los encabezados.
            respuesta = cliente.send(
                    solicitudConstruida,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        return new Respuesta(respuesta.statusCode(), respuesta.headers(), respuesta.body());
    }

    public record Respuesta(int estado, HttpHeaders encabezados, String cuerpo) {
        public String encabezado(String nombre) {
            return encabezados.firstValue(nombre).orElse(null);
        }
    }
}
