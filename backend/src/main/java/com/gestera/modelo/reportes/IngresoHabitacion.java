package com.gestera.modelo.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Pago consolidado de una estancia, conservado aunque la habitacion sea liberada. */
public record IngresoHabitacion(
        long id,
        String codigoHotel,
        int numeroHabitacion,
        LocalDateTime checkIn,
        LocalDateTime pagadoEn,
        BigDecimal monto) {

    public IngresoHabitacion {
        codigoHotel = Objects.requireNonNull(codigoHotel, "codigoHotel");
        checkIn = Objects.requireNonNull(checkIn, "checkIn");
        pagadoEn = Objects.requireNonNull(pagadoEn, "pagadoEn");
        monto = Objects.requireNonNull(monto, "monto").setScale(2);
    }
}
