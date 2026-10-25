package com.gestera.servicios;

import java.time.LocalDateTime;
import java.util.List;

/** Puerto de persistencia del módulo de servicio al cuarto. */
public interface RepositorioServicioHabitacion extends AutoCloseable {
    void crear(
            String codigoHotel,
            int numeroHabitacion,
            String observaciones,
            LocalDateTime creadoEn,
            List<LineaPedidoServicio> lineas);

    List<PedidoServicio> listarPorHotel(String codigoHotel);

    boolean cambiarEstado(
            String codigoHotel,
            long pedidoId,
            EstadoPedidoServicio estadoActual,
            EstadoPedidoServicio estadoNuevo,
            LocalDateTime actualizadoEn);

    @Override
    void close();
}
