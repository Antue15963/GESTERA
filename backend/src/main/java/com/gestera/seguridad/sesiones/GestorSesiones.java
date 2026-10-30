package com.gestera.seguridad.sesiones;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Mantiene sesiones opacas y temporales; nunca almacena la llave de acceso. */
public final class GestorSesiones implements AutoCloseable {
    private static final int BYTES_TOKEN = 32;

    private final Duration duracion;
    private final Clock reloj;
    private final SecureRandom aleatorio;
    private final ConcurrentHashMap<String, Sesion> sesiones = new ConcurrentHashMap<>();

    public GestorSesiones(Duration duracion) {
        this(duracion, Clock.systemUTC(), new SecureRandom());
    }

    public GestorSesiones(Duration duracion, Clock reloj, SecureRandom aleatorio) {
        if (duracion == null || duracion.isZero() || duracion.isNegative()) {
            throw new IllegalArgumentException("La duración de sesión debe ser positiva.");
        }
        this.duracion = duracion;
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.aleatorio = Objects.requireNonNull(aleatorio, "aleatorio");
    }

    public String crear() {
        limpiarVencidas();
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        try {
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            sesiones.put(token, new Sesion(reloj.instant().plus(duracion)));
            return token;
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    public boolean esValida(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return false;
        }
        Sesion sesion = sesiones.get(token);
        if (sesion == null) {
            return false;
        }
        if (!sesion.vencimiento.isAfter(reloj.instant())) {
            sesiones.remove(token, sesion);
            return false;
        }
        return true;
    }

    public boolean autorizarHotel(String token, String codigoHotel) {
        return autorizarHotel(token, codigoHotel, 0);
    }

    public boolean autorizarHotel(String token, String codigoHotel, long versionLlave) {
        if (token == null || token.isBlank() || token.length() > 128
                || codigoHotel == null || codigoHotel.isBlank() || versionLlave < 0) {
            return false;
        }
        AtomicBoolean autorizada = new AtomicBoolean();
        sesiones.computeIfPresent(token, (clave, sesion) -> {
            if (!sesion.vencimiento.isAfter(reloj.instant())) {
                return null;
            }
            sesion.autorizacionHotel = new AutorizacionHotel(codigoHotel, versionLlave);
            autorizada.set(true);
            return sesion;
        });
        return autorizada.get();
    }

    public Optional<String> hotelAutorizado(String token) {
        return autorizacionHotel(token).map(AutorizacionHotel::codigoHotel);
    }

    public Optional<AutorizacionHotel> autorizacionHotel(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return Optional.empty();
        }
        AtomicReference<AutorizacionHotel> autorizacion = new AtomicReference<>();
        sesiones.computeIfPresent(token, (clave, sesion) -> {
            if (!sesion.vencimiento.isAfter(reloj.instant())) {
                return null;
            }
            autorizacion.set(sesion.autorizacionHotel);
            return sesion;
        });
        return Optional.ofNullable(autorizacion.get());
    }

    public boolean revocarHotel(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return false;
        }
        AtomicBoolean revocada = new AtomicBoolean();
        sesiones.computeIfPresent(token, (clave, sesion) -> {
            if (!sesion.vencimiento.isAfter(reloj.instant())) {
                return null;
            }
            sesion.autorizacionHotel = null;
            revocada.set(true);
            return sesion;
        });
        return revocada.get();
    }

    public void cerrar(String token) {
        if (token != null) {
            sesiones.remove(token);
        }
    }

    public int cantidadActivas() {
        limpiarVencidas();
        return sesiones.size();
    }

    @Override
    public void close() {
        sesiones.clear();
    }

    private void limpiarVencidas() {
        Instant ahora = reloj.instant();
        sesiones.entrySet().removeIf(entrada -> !entrada.getValue().vencimiento.isAfter(ahora));
    }

    private static final class Sesion {
        private final Instant vencimiento;
        private volatile AutorizacionHotel autorizacionHotel;

        private Sesion(Instant vencimiento) {
            this.vencimiento = vencimiento;
        }
    }

    public record AutorizacionHotel(String codigoHotel, long versionLlave) {
        public AutorizacionHotel {
            if (codigoHotel == null || codigoHotel.isBlank() || versionLlave < 0) {
                throw new IllegalArgumentException("La autorización del hotel no es válida.");
            }
        }
    }
}
