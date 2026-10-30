package com.gestera.repositorios.huespedes;

import com.gestera.modelo.huespedes.DatosHuesped;
import com.gestera.modelo.huespedes.FiltroHuespedes;
import com.gestera.modelo.huespedes.RegistroHuesped;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Persistencia local para el perfil explicito memoria y las pruebas del dominio. */
public final class RepositorioHuespedesMemoria implements RepositorioHuespedes {
    private final AtomicLong secuencia = new AtomicLong();
    private final Map<ClaveEstancia, EstanciaMemoria> estancias = new LinkedHashMap<>();

    @Override
    public synchronized void guardarEstancia(
            String codigoHotel,
            Integer numeroHabitacion,
            String ubicacion,
            String colorHabitacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            List<DatosHuesped> huespedes) {
        ClaveEstancia clave = new ClaveEstancia(codigoHotel, ubicacion, checkIn);
        EstanciaMemoria anterior = estancias.get(clave);
        long id = anterior == null ? secuencia.incrementAndGet() : anterior.id();
        estancias.put(clave, new EstanciaMemoria(
                id, numeroHabitacion, colorHabitacion, checkOut, true, List.copyOf(huespedes)));
    }

    @Override
    public synchronized void finalizarEstancia(
            String codigoHotel,
            int numeroHabitacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut) {
        ClaveEstancia clave = new ClaveEstancia(
                codigoHotel, "Habitación " + numeroHabitacion, checkIn);
        EstanciaMemoria estancia = estancias.get(clave);
        if (estancia != null) {
            estancias.put(clave, new EstanciaMemoria(
                    estancia.id(), estancia.numeroHabitacion(), estancia.colorHabitacion(),
                    checkOut, false, estancia.huespedes()));
        }
    }

    @Override
    public synchronized List<RegistroHuesped> buscarPorHotel(
            String codigoHotel, FiltroHuespedes filtro) {
        ArrayList<RegistroHuesped> registros = new ArrayList<>();
        estancias.forEach((clave, estancia) -> {
            if (!clave.codigoHotel().equals(codigoHotel)) {
                return;
            }
            for (DatosHuesped huesped : estancia.huespedes()) {
                if (!coincide(clave, estancia, huesped, filtro)) {
                    continue;
                }
                registros.add(new RegistroHuesped(
                        estancia.id(),
                        clave.codigoHotel(),
                        clave.ubicacion(),
                        estancia.colorHabitacion(),
                        huesped.nombreCompleto(),
                        huesped.tipoDocumento(),
                        huesped.numeroDocumento(),
                        clave.checkIn().toLocalDate(),
                        clave.checkIn(),
                        estancia.checkOut(),
                        estancia.activa()));
            }
        });
        registros.sort(Comparator.comparing(RegistroHuesped::checkIn).reversed()
                .thenComparing(RegistroHuesped::ubicacion)
                .thenComparing(RegistroHuesped::nombreCompleto));
        return List.copyOf(registros);
    }

    @Override
    public synchronized Map<Integer, List<DatosHuesped>> listarActivosPorHabitacion(
            String codigoHotel) {
        LinkedHashMap<Integer, List<DatosHuesped>> resultado = new LinkedHashMap<>();
        estancias.forEach((clave, estancia) -> {
            if (estancia.activa() && clave.codigoHotel().equals(codigoHotel)
                    && estancia.numeroHabitacion() != null) {
                resultado.put(estancia.numeroHabitacion(), estancia.huespedes());
            }
        });
        return Map.copyOf(resultado);
    }

    @Override
    public synchronized void close() {
        estancias.clear();
    }

    private record ClaveEstancia(
            String codigoHotel, String ubicacion, LocalDateTime checkIn) {
    }

    private record EstanciaMemoria(
            long id,
            Integer numeroHabitacion,
            String colorHabitacion,
            LocalDateTime checkOut,
            boolean activa,
            List<DatosHuesped> huespedes) {
    }

    private static boolean coincide(
            ClaveEstancia clave,
            EstanciaMemoria estancia,
            DatosHuesped huesped,
            FiltroHuespedes filtro) {
        if (!filtro.nombre().isEmpty()
                && !huesped.nombreCompleto().toLowerCase(Locale.ROOT)
                        .contains(filtro.nombre().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (filtro.dia() != null && !clave.checkIn().toLocalDate().equals(filtro.dia())) {
            return false;
        }
        if (filtro.estuvoDesde() != null
                && estancia.checkOut().isBefore(filtro.estuvoDesde())) {
            return false;
        }
        if (filtro.estuvoHasta() != null
                && clave.checkIn().isAfter(filtro.estuvoHasta())) {
            return false;
        }
        return filtro.colorHabitacion().isEmpty()
                || estancia.numeroHabitacion() != null
                        && estancia.colorHabitacion().equalsIgnoreCase(
                                filtro.colorHabitacion());
    }
}
