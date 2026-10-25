package com.gestera.servicios;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/** Criterios opcionales y combinables para consultar pedidos de un hotel. */
public record FiltroPedidosServicio(
        LocalDate fecha,
        LocalTime hora,
        Integer numeroHabitacion) {

    public FiltroPedidosServicio {
        if (hora != null) {
            hora = hora.truncatedTo(ChronoUnit.MINUTES);
        }
        if (numeroHabitacion != null
                && (numeroHabitacion < 1 || numeroHabitacion > 50)) {
            throw new IllegalArgumentException("La habitación debe estar entre 1 y 50.");
        }
    }

    public static FiltroPedidosServicio vacio() {
        return new FiltroPedidosServicio(null, null, null);
    }

    public boolean activo() {
        return fecha != null || hora != null || numeroHabitacion != null;
    }

    public boolean coincide(PedidoServicio pedido) {
        if (fecha != null && !pedido.creadoEn().toLocalDate().equals(fecha)) {
            return false;
        }
        if (hora != null
                && !pedido.creadoEn().toLocalTime().truncatedTo(ChronoUnit.MINUTES).equals(hora)) {
            return false;
        }
        return numeroHabitacion == null
                || pedido.numeroHabitacion() == numeroHabitacion;
    }
}
