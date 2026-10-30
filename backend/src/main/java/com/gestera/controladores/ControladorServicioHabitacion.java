package com.gestera.controladores;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.gestera.infraestructura.servidor.RespuestasHttp;
import com.gestera.infraestructura.servidor.RutasGestera;
import com.gestera.infraestructura.servidor.SolicitudHttpException;
import com.gestera.infraestructura.servidor.SolicitudesHttp;
import com.gestera.modelo.servicios.FiltroPedidosServicio;
import com.gestera.modelo.servicios.GestorServicioHabitacion;
import com.gestera.vistas.PlantillasWeb;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Objects;

/** Controlador MVC de pedidos y catálogo del servicio a la habitación. */
public final class ControladorServicioHabitacion {
    private final GestorServicioHabitacion servicio;
    private final GestorHabitacionesHoteles habitaciones;
    private final PlantillasWeb vistas;
    private final AutorizadorHotel autorizadorHotel;

    public ControladorServicioHabitacion(
            GestorServicioHabitacion servicio,
            GestorHabitacionesHoteles habitaciones,
            PlantillasWeb vistas,
            AutorizadorHotel autorizadorHotel) {
        this.servicio = Objects.requireNonNull(servicio, "servicio");
        this.habitaciones = Objects.requireNonNull(habitaciones, "habitaciones");
        this.vistas = Objects.requireNonNull(vistas, "vistas");
        this.autorizadorHotel = Objects.requireNonNull(autorizadorHotel, "autorizadorHotel");
    }

    public void mostrar(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        responder(
                intercambio,
                200,
                hotel,
                PlantillasWeb.EstadoServicioHabitacion.INICIAL,
                SolicitudesHttp.leerFiltroPedidosServicio(intercambio));
    }

    public void crearPedido(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.NuevoPedidoServicio solicitud =
                SolicitudesHttp.leerNuevoPedidoServicio(intercambio);
        Habitacion habitacion = buscarHabitacion(hotel.codigo(), solicitud.numeroHabitacion());
        if (habitacion == null || habitacion.estado() != EstadoHabitacion.OCUPADA) {
            responder(
                    intercambio,
                    409,
                    hotel,
                    PlantillasWeb.EstadoServicioHabitacion.HABITACION_NO_OCUPADA,
                    FiltroPedidosServicio.vacio());
            return;
        }
        GestorServicioHabitacion.ResultadoOperacion resultado = servicio.crearPedido(
                hotel.codigo(),
                solicitud.numeroHabitacion(),
                solicitud.producto(),
                solicitud.cantidad(),
                solicitud.observaciones());
        if (resultado == GestorServicioHabitacion.ResultadoOperacion.CREADO) {
            RespuestasHttp.redirigir(intercambio, RutasGestera.SERVICIO_HABITACION);
            return;
        }
        responder(
                intercambio,
                400,
                hotel,
                estadoServicio(resultado),
                FiltroPedidosServicio.vacio());
    }

    public void cambiarEstadoPedido(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.CambioEstadoPedidoServicio solicitud =
                SolicitudesHttp.leerCambioEstadoPedidoServicio(intercambio);
        GestorServicioHabitacion.ResultadoOperacion resultado = servicio.cambiarEstado(
                hotel.codigo(), solicitud.pedidoId(), solicitud.estado());
        if (resultado == GestorServicioHabitacion.ResultadoOperacion.ACTUALIZADO) {
            RespuestasHttp.redirigir(intercambio, RutasGestera.SERVICIO_HABITACION);
            return;
        }
        int estadoHttp = resultado
                == GestorServicioHabitacion.ResultadoOperacion.CONFLICTO_CONCURRENCIA
                ? 409 : 400;
        responder(
                intercambio,
                estadoHttp,
                hotel,
                estadoServicio(resultado),
                FiltroPedidosServicio.vacio());
    }

    public void actualizarCatalogo(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.CambioProductoCatalogo solicitud =
                SolicitudesHttp.leerCambioProductoCatalogo(intercambio);
        GestorServicioHabitacion.ResultadoOperacion resultado = servicio.actualizarProducto(
                hotel.codigo(),
                solicitud.producto(),
                solicitud.nombre(),
                solicitud.categoria(),
                SolicitudesHttp.leerPrecio(solicitud.precio()));
        procesarResultadoCatalogo(intercambio, hotel, resultado);
    }

    public void agregarProducto(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.NuevoProductoCatalogo solicitud =
                SolicitudesHttp.leerNuevoProductoCatalogo(intercambio);
        GestorServicioHabitacion.ResultadoOperacion resultado = servicio.agregarProducto(
                hotel.codigo(),
                solicitud.nombre(),
                solicitud.categoria(),
                SolicitudesHttp.leerPrecio(solicitud.precio()));
        procesarResultadoCatalogo(intercambio, hotel, resultado);
    }

    public void eliminarProducto(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        GestorServicioHabitacion.ResultadoOperacion resultado = servicio.eliminarProducto(
                hotel.codigo(), SolicitudesHttp.leerProductoCatalogoEliminado(intercambio));
        procesarResultadoCatalogo(intercambio, hotel, resultado);
    }

    public void agregarCategoria(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        GestorServicioHabitacion.ResultadoOperacion resultado = servicio.agregarCategoria(
                hotel.codigo(), SolicitudesHttp.leerCategoriaCatalogo(intercambio));
        procesarResultadoCatalogo(intercambio, hotel, resultado);
    }

    public void eliminarCategoria(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        GestorServicioHabitacion.ResultadoOperacion resultado = servicio.eliminarCategoria(
                hotel.codigo(), SolicitudesHttp.leerCategoriaCatalogo(intercambio));
        procesarResultadoCatalogo(intercambio, hotel, resultado);
    }

    private void procesarResultadoCatalogo(
            HttpExchange intercambio,
            Hotel hotel,
            GestorServicioHabitacion.ResultadoOperacion resultado) throws IOException {
        if (resultado == GestorServicioHabitacion.ResultadoOperacion.ACTUALIZADO) {
            RespuestasHttp.redirigir(intercambio, RutasGestera.SERVICIO_HABITACION);
            return;
        }
        responder(
                intercambio,
                400,
                hotel,
                estadoCatalogo(resultado),
                FiltroPedidosServicio.vacio());
    }

    private void responder(
            HttpExchange intercambio,
            int estadoHttp,
            Hotel hotel,
            PlantillasWeb.EstadoServicioHabitacion estado,
            FiltroPedidosServicio filtro) throws IOException {
        RespuestasHttp.html(
                intercambio,
                estadoHttp,
                vistas.paginaServicioHabitacion(
                        hotel,
                        habitaciones.habitaciones(hotel.codigo()),
                        servicio.catalogo(hotel.codigo()),
                        servicio.categorias(hotel.codigo()),
                        servicio.buscar(hotel.codigo(), filtro),
                        estado,
                        filtro));
    }

    private Habitacion buscarHabitacion(String codigoHotel, int numero) {
        return habitaciones.habitaciones(codigoHotel).stream()
                .filter(habitacion -> habitacion.numero() == numero)
                .findFirst()
                .orElse(null);
    }

    private static PlantillasWeb.EstadoServicioHabitacion estadoServicio(
            GestorServicioHabitacion.ResultadoOperacion resultado) {
        return switch (resultado) {
            case PRODUCTO_INVALIDO -> PlantillasWeb.EstadoServicioHabitacion.PRODUCTO_INVALIDO;
            case HOTEL_INVALIDO, HABITACION_INVALIDA ->
                    PlantillasWeb.EstadoServicioHabitacion.HABITACION_NO_OCUPADA;
            case CANTIDAD_INVALIDA -> PlantillasWeb.EstadoServicioHabitacion.CANTIDAD_INVALIDA;
            case OBSERVACIONES_INVALIDAS ->
                    PlantillasWeb.EstadoServicioHabitacion.OBSERVACIONES_INVALIDAS;
            case PRODUCTO_DUPLICADO -> PlantillasWeb.EstadoServicioHabitacion.PRODUCTO_DUPLICADO;
            case NOMBRE_PRODUCTO_INVALIDO ->
                    PlantillasWeb.EstadoServicioHabitacion.NOMBRE_PRODUCTO_INVALIDO;
            case CATEGORIA_PRODUCTO_INVALIDA ->
                    PlantillasWeb.EstadoServicioHabitacion.CATEGORIA_PRODUCTO_INVALIDA;
            case PRECIO_PRODUCTO_INVALIDO ->
                    PlantillasWeb.EstadoServicioHabitacion.PRECIO_PRODUCTO_INVALIDO;
            case PEDIDO_DESCONOCIDO ->
                    PlantillasWeb.EstadoServicioHabitacion.PEDIDO_DESCONOCIDO;
            case ESTADO_INVALIDO, TRANSICION_INVALIDA, CONFLICTO_CONCURRENCIA ->
                    PlantillasWeb.EstadoServicioHabitacion.TRANSICION_INVALIDA;
            default -> PlantillasWeb.EstadoServicioHabitacion.SOLICITUD_INVALIDA;
        };
    }

    private static PlantillasWeb.EstadoServicioHabitacion estadoCatalogo(
            GestorServicioHabitacion.ResultadoOperacion resultado) {
        return switch (resultado) {
            case PRODUCTO_INVALIDO ->
                    PlantillasWeb.EstadoServicioHabitacion.PRODUCTO_CATALOGO_INVALIDO;
            case PRODUCTO_DUPLICADO -> PlantillasWeb.EstadoServicioHabitacion.PRODUCTO_DUPLICADO;
            case NOMBRE_PRODUCTO_INVALIDO ->
                    PlantillasWeb.EstadoServicioHabitacion.NOMBRE_PRODUCTO_INVALIDO;
            case CATEGORIA_PRODUCTO_INVALIDA ->
                    PlantillasWeb.EstadoServicioHabitacion.CATEGORIA_PRODUCTO_INVALIDA;
            case CATEGORIA_DESCONOCIDA ->
                    PlantillasWeb.EstadoServicioHabitacion.CATEGORIA_DESCONOCIDA;
            case CATEGORIA_DUPLICADA ->
                    PlantillasWeb.EstadoServicioHabitacion.CATEGORIA_DUPLICADA;
            case CATEGORIA_EN_USO -> PlantillasWeb.EstadoServicioHabitacion.CATEGORIA_EN_USO;
            case ULTIMA_CATEGORIA -> PlantillasWeb.EstadoServicioHabitacion.ULTIMA_CATEGORIA;
            case PRECIO_PRODUCTO_INVALIDO ->
                    PlantillasWeb.EstadoServicioHabitacion.PRECIO_PRODUCTO_INVALIDO;
            default -> PlantillasWeb.EstadoServicioHabitacion.SOLICITUD_INVALIDA;
        };
    }
}
