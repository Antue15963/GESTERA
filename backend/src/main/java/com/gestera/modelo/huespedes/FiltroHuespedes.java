package com.gestera.modelo.huespedes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Criterios opcionales y combinables para consultar el historial de huéspedes. */
public record FiltroHuespedes(
        String nombre,
        LocalDate dia,
        LocalDateTime estuvoDesde,
        LocalDateTime estuvoHasta,
        String colorHabitacion) {

    public FiltroHuespedes {
        nombre = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        colorHabitacion = colorHabitacion == null ? "" : colorHabitacion.toUpperCase();
        if (estuvoDesde != null && estuvoHasta != null && estuvoHasta.isBefore(estuvoDesde)) {
            throw new IllegalArgumentException(
                    "La hora final de búsqueda no puede ser anterior a la inicial.");
        }
    }

    public static FiltroHuespedes vacio() {
        return new FiltroHuespedes("", null, null, null, "");
    }

    public boolean activo() {
        return !nombre.isEmpty()
                || dia != null
                || estuvoDesde != null
                || estuvoHasta != null
                || !colorHabitacion.isEmpty();
    }
}
