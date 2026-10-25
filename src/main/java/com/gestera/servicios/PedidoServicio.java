package com.gestera.servicios;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Vista inmutable de un pedido de servicio asociado a una habitación. */
public record PedidoServicio(
        long id,
        String codigoHotel,
        int numeroHabitacion,
        EstadoPedidoServicio estado,
        String observaciones,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        List<LineaPedidoServicio> lineas) {

    public PedidoServicio {
        lineas = List.copyOf(lineas);
    }

    @Override
    public List<LineaPedidoServicio> lineas() {
        return List.copyOf(lineas);
    }

    public BigDecimal total() {
        return lineas.stream()
                .map(LineaPedidoServicio::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }
}
