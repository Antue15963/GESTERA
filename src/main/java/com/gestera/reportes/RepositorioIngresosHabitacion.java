package com.gestera.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Puerto de persistencia del historial de ingresos por habitaciones. */
public interface RepositorioIngresosHabitacion extends AutoCloseable {
    void guardarPago(
            String codigoHotel,
            int numeroHabitacion,
            LocalDateTime checkIn,
            LocalDateTime pagadoEn,
            BigDecimal monto);

    void eliminarPago(String codigoHotel, int numeroHabitacion, LocalDateTime checkIn);

    List<IngresoHabitacion> listar(
            String codigoHotel, LocalDateTime desdeInclusive, LocalDateTime hastaExclusive);

    @Override
    void close();
}
