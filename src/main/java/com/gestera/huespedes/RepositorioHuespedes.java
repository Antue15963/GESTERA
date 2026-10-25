package com.gestera.huespedes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Puerto de persistencia del módulo de huéspedes. */
public interface RepositorioHuespedes extends AutoCloseable {
    void guardarEstancia(
            String codigoHotel,
            Integer numeroHabitacion,
            String ubicacion,
            String colorHabitacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            List<DatosHuesped> huespedes);

    void finalizarEstancia(
            String codigoHotel,
            int numeroHabitacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut);

    List<RegistroHuesped> buscarPorHotel(String codigoHotel, FiltroHuespedes filtro);

    Map<Integer, List<DatosHuesped>> listarActivosPorHabitacion(String codigoHotel);

    @Override
    void close();
}
