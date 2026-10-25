package com.gestera.servicios;

import com.gestera.pruebas.Aserciones;
import com.gestera.servicios.GestorServicioHabitacion.ResultadoOperacion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

/** Pruebas del dominio de servicio al cuarto independientes del motor de datos. */
public final class GestorServicioHabitacionPrueba {
    private GestorServicioHabitacionPrueba() {
    }

    public static int ejecutar() {
        creaPedidoConPrecioCalculado();
        validaEntradas();
        controlaTransiciones();
        aislaPedidosPorHotel();
        filtraPedidosPorFechaHoraYHabitacion();
        actualizaElCatalogoPorHotelSinAlterarPedidosHistoricos();
        return 6;
    }

    private static void creaPedidoConPrecioCalculado() {
        try (GestorServicioHabitacion gestor = nuevoGestor()) {
            Aserciones.iguales(
                    ResultadoOperacion.CREADO,
                    gestor.crearPedido(
                            "sol-pucusana", 3, "club-sandwich", 2, "Sin mayonesa"),
                    "Debe crear un pedido válido");
            PedidoServicio pedido = gestor.listar("sol-pucusana").get(0);
            Aserciones.iguales(EstadoPedidoServicio.PENDIENTE, pedido.estado(),
                    "Un pedido nuevo debe quedar pendiente");
            Aserciones.iguales("64.00", pedido.total().toPlainString(),
                    "El total debe calcularse en el dominio y no desde el navegador");
            Aserciones.iguales("Sin mayonesa", pedido.observaciones(),
                    "Debe conservar observaciones normalizadas");
        }
    }

    private static void validaEntradas() {
        try (GestorServicioHabitacion gestor = nuevoGestor()) {
            Aserciones.iguales(ResultadoOperacion.PRODUCTO_INVALIDO,
                    gestor.crearPedido("sol-pucusana", 1, "inventado", 1, ""),
                    "El catálogo debe funcionar como allowlist");
            Aserciones.iguales(ResultadoOperacion.CANTIDAD_INVALIDA,
                    gestor.crearPedido("sol-pucusana", 1, "agua-mineral", 11, ""),
                    "La cantidad debe estar acotada");
            Aserciones.iguales(ResultadoOperacion.OBSERVACIONES_INVALIDAS,
                    gestor.crearPedido("sol-pucusana", 1, "agua-mineral", 1, "x".repeat(301)),
                    "Las observaciones no deben superar su límite");
        }
    }

    private static void controlaTransiciones() {
        try (GestorServicioHabitacion gestor = nuevoGestor()) {
            gestor.crearPedido("sol-pucusana", 2, "cafe-americano", 1, "");
            long id = gestor.listar("sol-pucusana").get(0).id();
            Aserciones.iguales(ResultadoOperacion.TRANSICION_INVALIDA,
                    gestor.cambiarEstado("sol-pucusana", id, "entregado"),
                    "No debe saltar de pendiente a entregado");
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADO,
                    gestor.cambiarEstado("sol-pucusana", id, "en_preparacion"),
                    "Debe iniciar la preparación");
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADO,
                    gestor.cambiarEstado("sol-pucusana", id, "entregado"),
                    "Debe registrar la entrega");
            Aserciones.iguales(ResultadoOperacion.TRANSICION_INVALIDA,
                    gestor.cambiarEstado("sol-pucusana", id, "cancelado"),
                    "Un pedido finalizado debe permanecer inmutable");
        }
    }

    private static void aislaPedidosPorHotel() {
        try (GestorServicioHabitacion gestor = nuevoGestor()) {
            gestor.crearPedido("sol-pucusana", 1, "agua-mineral", 1, "");
            Aserciones.iguales(0, gestor.listar("brisa-marina").size(),
                    "Los pedidos no deben mezclarse entre hoteles");
            long id = gestor.listar("sol-pucusana").get(0).id();
            Aserciones.iguales(ResultadoOperacion.PEDIDO_DESCONOCIDO,
                    gestor.cambiarEstado("brisa-marina", id, "en_preparacion"),
                    "Un hotel no debe modificar pedidos ajenos");
        }
    }

    private static void filtraPedidosPorFechaHoraYHabitacion() {
        try (GestorServicioHabitacion gestor = nuevoGestor()) {
            gestor.crearPedido("sol-pucusana", 4, "agua-mineral", 1, "");
            gestor.crearPedido("sol-pucusana", 7, "cafe-americano", 1, "");
            FiltroPedidosServicio coincidencia = new FiltroPedidosServicio(
                    LocalDate.of(2026, 9, 21), LocalTime.of(10, 0), 7);
            Aserciones.iguales(1, gestor.buscar("sol-pucusana", coincidencia).size(),
                    "Los filtros de fecha, hora y habitacion deben poder combinarse");
            Aserciones.iguales(7,
                    gestor.buscar("sol-pucusana", coincidencia).get(0).numeroHabitacion(),
                    "La busqueda debe conservar solo la habitacion solicitada");
            Aserciones.iguales(0, gestor.buscar(
                    "sol-pucusana",
                    new FiltroPedidosServicio(LocalDate.of(2026, 9, 20), null, null)).size(),
                    "Una fecha distinta no debe producir coincidencias");
        }
    }

    private static void actualizaElCatalogoPorHotelSinAlterarPedidosHistoricos() {
        try (GestorServicioHabitacion gestor = nuevoGestor()) {
            Aserciones.iguales(
                    ResultadoOperacion.ACTUALIZADO,
                    gestor.agregarCategoria("sol-pucusana", "Carta nocturna"),
                    "Debe permitir agregar una categoría");
            Aserciones.iguales(
                    ResultadoOperacion.ACTUALIZADO,
                    gestor.actualizarProducto(
                            "sol-pucusana",
                            "club-sandwich",
                            "Club especial",
                            "Carta nocturna",
                            new BigDecimal("40.00")),
                    "Debe permitir modificar un producto existente");
            gestor.crearPedido("sol-pucusana", 2, "club-sandwich", 1, "");
            PedidoServicio pedidoOriginal = gestor.listar("sol-pucusana").get(0);
            Aserciones.iguales("Club especial",
                    pedidoOriginal.lineas().get(0).productoNombre(),
                    "Los pedidos nuevos deben usar el nombre actualizado");
            Aserciones.iguales("40.00", pedidoOriginal.total().toPlainString(),
                    "Los pedidos nuevos deben usar el precio actualizado");

            gestor.actualizarProducto(
                    "sol-pucusana",
                    "club-sandwich",
                    "Club premium",
                    "Carta nocturna",
                    new BigDecimal("45.00"));
            Aserciones.iguales("40.00", pedidoOriginal.total().toPlainString(),
                    "Cambiar el catalogo no debe reescribir pedidos historicos");
            Aserciones.iguales(
                    ResultadoOperacion.CATEGORIA_EN_USO,
                    gestor.eliminarCategoria("sol-pucusana", "Carta nocturna"),
                    "No debe eliminar una categoría que todavía contiene productos");
            Aserciones.iguales(
                    ResultadoOperacion.ACTUALIZADO,
                    gestor.agregarProducto(
                            "sol-pucusana", "Té de la casa", "Carta nocturna",
                            new BigDecimal("9.50")),
                    "Debe permitir agregar productos a una categoría existente");
            String codigoNuevo = gestor.catalogo("sol-pucusana").stream()
                    .filter(producto -> producto.nombre().equals("Té de la casa"))
                    .findFirst().orElseThrow().codigo();
            Aserciones.iguales(
                    ResultadoOperacion.ACTUALIZADO,
                    gestor.eliminarProducto("sol-pucusana", codigoNuevo),
                    "Debe permitir quitar productos del catálogo");
            gestor.actualizarProducto(
                    "sol-pucusana", "club-sandwich", "Club premium", "Comidas",
                    new BigDecimal("45.00"));
            Aserciones.iguales(
                    ResultadoOperacion.ACTUALIZADO,
                    gestor.eliminarCategoria("sol-pucusana", "Carta nocturna"),
                    "Una categoría vacía debe poder quitarse");
            Aserciones.iguales("32.00",
                    gestor.catalogo("brisa-marina").stream()
                            .filter(producto -> producto.codigo().equals("club-sandwich"))
                            .findFirst().orElseThrow().precio().toPlainString(),
                    "El catalogo de otro hotel debe conservar su precio");
            Aserciones.iguales(
                    ResultadoOperacion.PRECIO_PRODUCTO_INVALIDO,
                    gestor.actualizarProducto(
                            "sol-pucusana", "club-sandwich", "Club", "Comidas",
                            BigDecimal.ZERO),
                    "El servidor debe rechazar precios no positivos");
        }
    }

    private static GestorServicioHabitacion nuevoGestor() {
        Clock reloj = Clock.fixed(
                Instant.parse("2026-09-21T15:00:00Z"), ZoneId.of("America/Lima"));
        return new GestorServicioHabitacion(
                new RepositorioServicioHabitacionMemoria(), reloj);
    }
}
