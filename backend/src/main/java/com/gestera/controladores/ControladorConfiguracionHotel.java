package com.gestera.controladores;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.CambioSeccionTarifaria;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.ResultadoOperacion;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.ResumenInventario;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.gestera.infraestructura.servidor.RespuestasHttp;
import com.gestera.infraestructura.servidor.RutasGestera;
import com.gestera.infraestructura.servidor.SolicitudHttpException;
import com.gestera.infraestructura.servidor.SolicitudesHttp;
import com.gestera.vistas.PlantillasWeb;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Controlador MVC para la capacidad y las secciones tarifarias del hotel. */
public final class ControladorConfiguracionHotel {
    private final GestorHabitacionesHoteles habitaciones;
    private final PlantillasWeb vistas;
    private final AutorizadorHotel autorizadorHotel;

    public ControladorConfiguracionHotel(
            GestorHabitacionesHoteles habitaciones,
            PlantillasWeb vistas,
            AutorizadorHotel autorizadorHotel) {
        this.habitaciones = Objects.requireNonNull(habitaciones, "habitaciones");
        this.vistas = Objects.requireNonNull(vistas, "vistas");
        this.autorizadorHotel = Objects.requireNonNull(autorizadorHotel, "autorizadorHotel");
    }

    public void mostrar(HttpExchange intercambio) throws IOException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel != null) {
            responder(intercambio, 200, hotel, PlantillasWeb.EstadoConfiguracionHotel.INICIAL);
        }
    }

    public void guardarTodo(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.ConfiguracionGeneral solicitud =
                SolicitudesHttp.leerConfiguracionGeneral(intercambio);
        List<CambioSeccionTarifaria> cambios = new ArrayList<>(solicitud.cambios().size());
        ResultadoOperacion resultado = ResultadoOperacion.ACTUALIZADA;
        for (SolicitudesHttp.CambioConfiguracionSeccion cambio : solicitud.cambios()) {
            BigDecimal precioSeisHoras = cambio.eliminar()
                    ? null : SolicitudesHttp.leerPrecio(cambio.precioSeisHoras());
            BigDecimal precioDiaCompleto = cambio.eliminar()
                    ? null : SolicitudesHttp.leerPrecio(cambio.precioDiaCompleto());
            if (!cambio.eliminar()
                    && (precioSeisHoras == null || precioDiaCompleto == null)) {
                resultado = ResultadoOperacion.PRECIO_INVALIDO;
                break;
            }
            cambios.add(new CambioSeccionTarifaria(
                    cambio.idSeccion(),
                    cambio.eliminar(),
                    cambio.color(),
                    cambio.nombre(),
                    precioSeisHoras,
                    precioDiaCompleto));
        }
        if (resultado == ResultadoOperacion.ACTUALIZADA) {
            resultado = habitaciones.guardarConfiguracion(
                    hotel.codigo(), solicitud.cantidadHabitaciones(), cambios);
        }
        procesarResultado(intercambio, hotel, resultado);
    }

    public void configurarCantidad(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        ResultadoOperacion resultado = habitaciones.configurarCantidad(
                hotel.codigo(), SolicitudesHttp.leerCantidadHabitaciones(intercambio));
        procesarResultado(intercambio, hotel, resultado);
    }

    public void guardarSeccion(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.ConfiguracionSeccion solicitud =
                SolicitudesHttp.leerConfiguracionSeccion(intercambio);
        BigDecimal precioSeisHoras = SolicitudesHttp.leerPrecio(solicitud.precioSeisHoras());
        BigDecimal precioDiaCompleto = SolicitudesHttp.leerPrecio(solicitud.precioDiaCompleto());
        ResultadoOperacion resultado = precioSeisHoras == null || precioDiaCompleto == null
                ? ResultadoOperacion.PRECIO_INVALIDO
                : habitaciones.guardarSeccion(
                        hotel.codigo(),
                        solicitud.idSeccion(),
                        solicitud.color(),
                        solicitud.nombre(),
                        precioSeisHoras,
                        precioDiaCompleto);
        procesarResultado(intercambio, hotel, resultado);
    }

    public void eliminarSeccion(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        ResultadoOperacion resultado = habitaciones.eliminarSeccion(
                hotel.codigo(), SolicitudesHttp.leerIdSeccionEliminada(intercambio));
        procesarResultado(intercambio, hotel, resultado);
    }

    private void procesarResultado(
            HttpExchange intercambio, Hotel hotel, ResultadoOperacion resultado)
            throws IOException {
        if (resultado == ResultadoOperacion.ACTUALIZADA) {
            RespuestasHttp.redirigir(intercambio, RutasGestera.CONFIGURACION_HOTEL);
            return;
        }
        responder(
                intercambio,
                esConflicto(resultado) ? 409 : 400,
                hotel,
                estadoConfiguracion(resultado));
    }

    private void responder(
            HttpExchange intercambio,
            int estadoHttp,
            Hotel hotel,
            PlantillasWeb.EstadoConfiguracionHotel estado) throws IOException {
        ResumenInventario resumen = habitaciones.resumen(hotel.codigo());
        RespuestasHttp.html(
                intercambio,
                estadoHttp,
                vistas.paginaConfiguracionHotel(hotel, resumen, estado));
    }

    private static boolean esConflicto(ResultadoOperacion resultado) {
        return resultado == ResultadoOperacion.REDUCCION_NO_PERMITIDA
                || resultado == ResultadoOperacion.LIMITE_SECCIONES
                || resultado == ResultadoOperacion.ULTIMA_SECCION
                || resultado == ResultadoOperacion.SECCION_EN_USO
                || resultado == ResultadoOperacion.COLOR_DUPLICADO
                || resultado == ResultadoOperacion.NOMBRE_DUPLICADO;
    }

    private static PlantillasWeb.EstadoConfiguracionHotel estadoConfiguracion(
            ResultadoOperacion resultado) {
        return switch (resultado) {
            case CANTIDAD_INVALIDA -> PlantillasWeb.EstadoConfiguracionHotel.CANTIDAD_INVALIDA;
            case REDUCCION_NO_PERMITIDA ->
                    PlantillasWeb.EstadoConfiguracionHotel.REDUCCION_NO_PERMITIDA;
            case SECCION_DESCONOCIDA ->
                    PlantillasWeb.EstadoConfiguracionHotel.SECCION_DESCONOCIDA;
            case LIMITE_SECCIONES -> PlantillasWeb.EstadoConfiguracionHotel.LIMITE_SECCIONES;
            case ULTIMA_SECCION -> PlantillasWeb.EstadoConfiguracionHotel.ULTIMA_SECCION;
            case SECCION_EN_USO -> PlantillasWeb.EstadoConfiguracionHotel.SECCION_EN_USO;
            case COLOR_INVALIDO -> PlantillasWeb.EstadoConfiguracionHotel.COLOR_INVALIDO;
            case COLOR_DUPLICADO -> PlantillasWeb.EstadoConfiguracionHotel.COLOR_DUPLICADO;
            case NOMBRE_INVALIDO -> PlantillasWeb.EstadoConfiguracionHotel.NOMBRE_INVALIDO;
            case NOMBRE_DUPLICADO -> PlantillasWeb.EstadoConfiguracionHotel.NOMBRE_DUPLICADO;
            case PRECIO_INVALIDO -> PlantillasWeb.EstadoConfiguracionHotel.PRECIO_INVALIDO;
            case HOTEL_DESCONOCIDO -> PlantillasWeb.EstadoConfiguracionHotel.HOTEL_DESCONOCIDO;
            default -> PlantillasWeb.EstadoConfiguracionHotel.INICIAL;
        };
    }
}
