package com.gestera.repositorios.reportes;

import com.gestera.modelo.reportes.IngresoHabitacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Adaptador local de pagos para el perfil explicito memoria. */
public final class RepositorioIngresosHabitacionMemoria
        implements RepositorioIngresosHabitacion {
    private final AtomicLong secuencia = new AtomicLong();
    private final Map<ClaveEstancia, IngresoHabitacion> ingresos = new LinkedHashMap<>();

    @Override
    public synchronized void guardarPago(
            String codigoHotel,
            int numeroHabitacion,
            LocalDateTime checkIn,
            LocalDateTime pagadoEn,
            BigDecimal monto) {
        ClaveEstancia clave = new ClaveEstancia(codigoHotel, numeroHabitacion, checkIn);
        IngresoHabitacion anterior = ingresos.get(clave);
        long id = anterior == null ? secuencia.incrementAndGet() : anterior.id();
        ingresos.put(clave, new IngresoHabitacion(
                id, codigoHotel, numeroHabitacion, checkIn, pagadoEn, monto));
    }

    @Override
    public synchronized void eliminarPago(
            String codigoHotel, int numeroHabitacion, LocalDateTime checkIn) {
        ingresos.remove(new ClaveEstancia(codigoHotel, numeroHabitacion, checkIn));
    }

    @Override
    public synchronized List<IngresoHabitacion> listar(
            String codigoHotel, LocalDateTime desdeInclusive, LocalDateTime hastaExclusive) {
        ArrayList<IngresoHabitacion> resultado = new ArrayList<>();
        ingresos.values().stream()
                .filter(ingreso -> ingreso.codigoHotel().equals(codigoHotel))
                .filter(ingreso -> !ingreso.pagadoEn().isBefore(desdeInclusive))
                .filter(ingreso -> ingreso.pagadoEn().isBefore(hastaExclusive))
                .sorted(Comparator.comparing(IngresoHabitacion::pagadoEn).reversed())
                .forEach(resultado::add);
        return List.copyOf(resultado);
    }

    @Override
    public synchronized void close() {
        ingresos.clear();
    }

    private record ClaveEstancia(
            String codigoHotel, int numeroHabitacion, LocalDateTime checkIn) {
    }
}
