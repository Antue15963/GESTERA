package com.gestera.infraestructura.servidor;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Respuestas HTTP uniformes con encabezados de seguridad. */
public final class RespuestasHttp {
    private static final String POLITICA_CONTENIDO = String.join(" ",
            "default-src 'self';",
            "style-src 'self';",
            "style-src-attr 'unsafe-inline';",
            "script-src 'self';",
            "img-src 'self' data:;",
            "form-action 'self';",
            "frame-ancestors 'none';",
            "base-uri 'none'");

    private RespuestasHttp() {
    }

    static void prepararSeguridad(HttpExchange intercambio) {
        Headers encabezados = intercambio.getResponseHeaders();
        encabezados.set("Content-Security-Policy", POLITICA_CONTENIDO);
        encabezados.set("X-Content-Type-Options", "nosniff");
        encabezados.set("X-Frame-Options", "DENY");
        encabezados.set("Referrer-Policy", "no-referrer");
        encabezados.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    public static void html(
            HttpExchange intercambio, int estado, String contenido) throws IOException {
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        enviar(intercambio, estado, "text/html; charset=UTF-8",
                contenido.getBytes(StandardCharsets.UTF_8));
    }

    public static void texto(
            HttpExchange intercambio, int estado, String contenido) throws IOException {
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        enviar(intercambio, estado, "text/plain; charset=UTF-8",
                contenido.getBytes(StandardCharsets.UTF_8));
    }

    static void recurso(
            HttpExchange intercambio,
            String tipoContenido,
            byte[] contenido) throws IOException {
        intercambio.getResponseHeaders().set("Cache-Control", "no-cache");
        enviar(intercambio, 200, tipoContenido, contenido);
    }

    public static void redirigir(HttpExchange intercambio, String destino) throws IOException {
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("Location", destino);
        intercambio.sendResponseHeaders(303, -1);
    }

    static void metodoNoPermitido(HttpExchange intercambio, String permitido) throws IOException {
        intercambio.getResponseHeaders().set("Allow", permitido);
        texto(intercambio, 405, "Método no permitido.");
    }

    static void noEncontrado(HttpExchange intercambio) throws IOException {
        texto(intercambio, 404, "Recurso no encontrado.");
    }

    private static void enviar(
            HttpExchange intercambio,
            int estado,
            String tipoContenido,
            byte[] contenido) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipoContenido);
        intercambio.sendResponseHeaders(estado, contenido.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(contenido);
        }
    }
}
