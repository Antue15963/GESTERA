package com.gestera.autenticacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bloquea temporalmente un origen después de varios intentos consecutivos. */
public final class LimitadorIntentos implements AutoCloseable {
    private final int maximoFallos;
    private final Duration duracionBloqueo;
    private final Clock reloj;
    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    public LimitadorIntentos(int maximoFallos, Duration duracionBloqueo) {
        this(maximoFallos, duracionBloqueo, Clock.systemUTC());
    }

    public LimitadorIntentos(int maximoFallos, Duration duracionBloqueo, Clock reloj) {
        if (maximoFallos < 1) {
            throw new IllegalArgumentException("Debe permitirse al menos un intento.");
        }
        if (duracionBloqueo == null || duracionBloqueo.isZero() || duracionBloqueo.isNegative()) {
            throw new IllegalArgumentException("La duración de bloqueo debe ser positiva.");
        }
        this.maximoFallos = maximoFallos;
        this.duracionBloqueo = duracionBloqueo;
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public boolean permite(String origen) {
        if (origen == null || origen.isBlank()) {
            return false;
        }
        Estado estado = estados.get(origen);
        if (estado == null || estado.bloqueadoHasta == null) {
            return true;
        }
        if (!estado.bloqueadoHasta.isAfter(reloj.instant())) {
            estados.remove(origen, estado);
            return true;
        }
        return false;
    }

    public void registrarFallo(String origen) {
        if (origen == null || origen.isBlank()) {
            return;
        }
        Instant ahora = reloj.instant();
        estados.compute(origen, (clave, estadoActual) -> {
            Estado estado = estadoActual;
            if (estado == null
                    || estado.bloqueadoHasta != null && !estado.bloqueadoHasta.isAfter(ahora)) {
                estado = new Estado();
            }
            estado.fallos++;
            if (estado.fallos >= maximoFallos) {
                estado.bloqueadoHasta = ahora.plus(duracionBloqueo);
            }
            return estado;
        });
    }

    public void registrarExito(String origen) {
        if (origen != null) {
            estados.remove(origen);
        }
    }

    @Override
    public void close() {
        estados.clear();
    }

    private static final class Estado {
        private int fallos;
        private volatile Instant bloqueadoHasta;
    }
}
