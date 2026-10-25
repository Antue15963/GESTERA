package com.gestera.controladores;

import com.gestera.habitaciones.GestorHabitacionesHoteles;
import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.reportes.FiltroReporte;
import com.gestera.reportes.GestorReportes;
import com.gestera.reportes.GestorReportes.ReporteIngresos;
import com.gestera.servidor.RespuestasHttp;
import com.gestera.servidor.SolicitudHttpException;
import com.gestera.servidor.SolicitudesHttp;
import com.gestera.servicios.GestorServicioHabitacion;
import com.gestera.vistas.PlantillasWeb;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Objects;

/** Controlador MVC de la pantalla de reportes de ingresos. */
public final class ControladorReportes {
    private final GestorReportes reportes;
    private final GestorServicioHabitacion servicioHabitacion;
    private final GestorHabitacionesHoteles habitaciones;
    private final PlantillasWeb vistas;
    private final AutorizadorHotel autorizadorHotel;

    public ControladorReportes(
            GestorReportes reportes,
            GestorServicioHabitacion servicioHabitacion,
            GestorHabitacionesHoteles habitaciones,
            PlantillasWeb vistas,
            AutorizadorHotel autorizadorHotel) {
        this.reportes = Objects.requireNonNull(reportes, "reportes");
        this.servicioHabitacion = Objects.requireNonNull(servicioHabitacion, "servicioHabitacion");
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
        FiltroReporte filtro = SolicitudesHttp.leerFiltroReporte(
                intercambio, reportes.hoy());
        ReporteIngresos reporte = reportes.generar(
                hotel.codigo(),
                filtro,
                servicioHabitacion.listar(hotel.codigo()),
                habitaciones.habitaciones(hotel.codigo()));
        RespuestasHttp.html(intercambio, 200, vistas.paginaReportes(hotel, reporte));
    }
}
