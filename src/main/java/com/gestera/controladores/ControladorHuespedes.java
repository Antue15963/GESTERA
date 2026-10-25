package com.gestera.controladores;

import com.gestera.habitaciones.GestorHabitacionesHoteles;
import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.huespedes.FiltroHuespedes;
import com.gestera.huespedes.GestorHuespedesHoteles;
import com.gestera.servidor.RespuestasHttp;
import com.gestera.servidor.SolicitudHttpException;
import com.gestera.servidor.SolicitudesHttp;
import com.gestera.vistas.PlantillasWeb;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Objects;

/** Controlador MVC de la consulta e historial de huéspedes. */
public final class ControladorHuespedes {
    private final GestorHuespedesHoteles huespedes;
    private final GestorHabitacionesHoteles habitaciones;
    private final PlantillasWeb vistas;
    private final AutorizadorHotel autorizadorHotel;

    public ControladorHuespedes(
            GestorHuespedesHoteles huespedes,
            GestorHabitacionesHoteles habitaciones,
            PlantillasWeb vistas,
            AutorizadorHotel autorizadorHotel) {
        this.huespedes = Objects.requireNonNull(huespedes, "huespedes");
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
        FiltroHuespedes filtro = SolicitudesHttp.leerFiltroHuespedes(intercambio);
        RespuestasHttp.html(
                intercambio,
                200,
                vistas.paginaHuespedes(
                        hotel,
                        huespedes.buscar(hotel.codigo(), filtro),
                        filtro,
                        habitaciones.seccionesTarifarias(hotel.codigo())));
    }
}
