package com.gestera.huespedes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Vista inmutable de un huésped asociado a una estancia. */
public record RegistroHuesped(
        long idEstancia,
        String codigoHotel,
        String ubicacion,
        String colorHabitacion,
        String nombreCompleto,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        LocalDate fechaVisita,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        boolean estanciaActiva) {

    public RegistroHuesped {
        codigoHotel = Objects.requireNonNull(codigoHotel, "codigoHotel");
        ubicacion = Objects.requireNonNull(ubicacion, "ubicacion");
        colorHabitacion = Objects.requireNonNull(colorHabitacion, "colorHabitacion");
        nombreCompleto = Objects.requireNonNull(nombreCompleto, "nombreCompleto");
        tipoDocumento = Objects.requireNonNull(tipoDocumento, "tipoDocumento");
        numeroDocumento = Objects.requireNonNull(numeroDocumento, "numeroDocumento");
        fechaVisita = Objects.requireNonNull(fechaVisita, "fechaVisita");
        checkIn = Objects.requireNonNull(checkIn, "checkIn");
        checkOut = Objects.requireNonNull(checkOut, "checkOut");
    }
}
