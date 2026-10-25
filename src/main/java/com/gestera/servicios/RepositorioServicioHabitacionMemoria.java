package com.gestera.servicios;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Repositorio local para el perfil explicito memoria y las pruebas. */
public final class RepositorioServicioHabitacionMemoria
        implements RepositorioServicioHabitacion {
    private final AtomicLong secuencia = new AtomicLong();
    private final Map<Long, PedidoServicio> pedidos = new LinkedHashMap<>();

    @Override
    public synchronized void crear(
            String codigoHotel,
            int numeroHabitacion,
            String observaciones,
            LocalDateTime creadoEn,
            List<LineaPedidoServicio> lineas) {
        long id = secuencia.incrementAndGet();
        pedidos.put(id, new PedidoServicio(
                id,
                codigoHotel,
                numeroHabitacion,
                EstadoPedidoServicio.PENDIENTE,
                observaciones,
                creadoEn,
                creadoEn,
                lineas));
    }

    @Override
    public synchronized List<PedidoServicio> listarPorHotel(String codigoHotel) {
        ArrayList<PedidoServicio> resultado = new ArrayList<>();
        pedidos.values().stream()
                .filter(pedido -> pedido.codigoHotel().equals(codigoHotel))
                .sorted(Comparator.comparing(PedidoServicio::creadoEn).reversed())
                .forEach(resultado::add);
        return List.copyOf(resultado);
    }

    @Override
    public synchronized boolean cambiarEstado(
            String codigoHotel,
            long pedidoId,
            EstadoPedidoServicio estadoActual,
            EstadoPedidoServicio estadoNuevo,
            LocalDateTime actualizadoEn) {
        PedidoServicio pedido = pedidos.get(pedidoId);
        if (pedido == null
                || !pedido.codigoHotel().equals(codigoHotel)
                || pedido.estado() != estadoActual) {
            return false;
        }
        pedidos.put(pedidoId, new PedidoServicio(
                pedido.id(), pedido.codigoHotel(), pedido.numeroHabitacion(), estadoNuevo,
                pedido.observaciones(), pedido.creadoEn(), actualizadoEn, pedido.lineas()));
        return true;
    }

    @Override
    public synchronized void close() {
        pedidos.clear();
    }
}
