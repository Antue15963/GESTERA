package com.gestera.vistas;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/** Catálogo cerrado de recursos públicos; evita recorridos arbitrarios de rutas. */
public final class RecursosWeb {
    private final Map<String, Recurso> recursos;

    public RecursosWeb() {
        Map<String, Recurso> base = new java.util.HashMap<>(Map.of(
                "/publico/scripts/tema.js",
                cargar("/publico/scripts/tema.js", "text/javascript; charset=UTF-8"),
                "/publico/estilos/gestera.css",
                cargar("/publico/estilos/gestera.css", "text/css; charset=UTF-8"),
                "/publico/scripts/acceso.js",
                cargar("/publico/scripts/acceso.js", "text/javascript; charset=UTF-8"),
                "/publico/scripts/hoteles.js",
                cargar("/publico/scripts/hoteles.js", "text/javascript; charset=UTF-8"),
                "/publico/scripts/gestion.js",
                cargar("/publico/scripts/gestion.js", "text/javascript; charset=UTF-8"),
                "/publico/scripts/configuracion-hotel.js",
                cargar(
                        "/publico/scripts/configuracion-hotel.js",
                        "text/javascript; charset=UTF-8"),
                "/publico/scripts/habitaciones.js",
                cargar("/publico/scripts/habitaciones.js", "text/javascript; charset=UTF-8"),
                "/publico/scripts/areas-comunes.js",
                cargar("/publico/scripts/areas-comunes.js", "text/javascript; charset=UTF-8"),
                "/publico/scripts/servicio-habitacion.js",
                cargar(
                        "/publico/scripts/servicio-habitacion.js",
                        "text/javascript; charset=UTF-8"),
                "/publico/scripts/reportes.js",
                cargar(
                        "/publico/scripts/reportes.js",
                        "text/javascript; charset=UTF-8")));
        base.put("/publico/imagenes/hotel-costa.png",
                cargar("/publico/imagenes/hotel-costa.png", "image/png"));
        recursos = Map.copyOf(base);
    }

    public Optional<Recurso> buscar(String rutaPublica) {
        return Optional.ofNullable(recursos.get(rutaPublica));
    }

    private static Recurso cargar(String rutaInterna, String tipoContenido) {
        try (InputStream entrada = RecursosWeb.class.getResourceAsStream(rutaInterna)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró el recurso: " + rutaInterna);
            }
            return new Recurso(entrada.readAllBytes(), tipoContenido);
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible leer el recurso: " + rutaInterna, exception);
        }
    }

    public record Recurso(byte[] contenido, String tipoContenido) {
        public Recurso {
            contenido = contenido.clone();
        }

        @Override
        public byte[] contenido() {
            return contenido.clone();
        }
    }
}
