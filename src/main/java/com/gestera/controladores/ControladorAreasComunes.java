package com.gestera.controladores;

import com.gestera.areas.GestorAreasComunes;
import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.huespedes.DatosHuesped;
import com.gestera.servidor.RespuestasHttp;
import com.gestera.servidor.RutasGestera;
import com.gestera.servidor.SolicitudHttpException;
import com.gestera.servidor.SolicitudesHttp;
import com.gestera.vistas.PlantillasWeb;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Controlador MVC para el registro de visitas a áreas comunes. */
public final class ControladorAreasComunes {
    private final GestorAreasComunes areasComunes;
    private final PlantillasWeb vistas;
    private final AutorizadorHotel autorizadorHotel;

    public ControladorAreasComunes(
            GestorAreasComunes areasComunes,
            PlantillasWeb vistas,
            AutorizadorHotel autorizadorHotel) {
        this.areasComunes = Objects.requireNonNull(areasComunes, "areasComunes");
        this.vistas = Objects.requireNonNull(vistas, "vistas");
        this.autorizadorHotel = Objects.requireNonNull(autorizadorHotel, "autorizadorHotel");
    }

    public void mostrar(HttpExchange intercambio) throws IOException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        responder(
                intercambio,
                200,
                hotel,
                PlantillasWeb.EstadoAreasComunes.INICIAL,
                "",
                areasComunes.horaActualPeru(),
                1,
                null);
    }

    public void registrar(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.RegistroAreaComun solicitud =
                SolicitudesHttp.leerRegistroAreaComun(intercambio);
        GestorAreasComunes.ResultadoOperacion resultado = areasComunes.registrar(
                hotel.codigo(),
                solicitud.area(),
                solicitud.entrada(),
                solicitud.duracionHoras(),
                solicitud.visitantes());
        if (resultado == GestorAreasComunes.ResultadoOperacion.REGISTRADA) {
            RespuestasHttp.redirigir(intercambio, RutasGestera.HUESPEDES);
            return;
        }
        responder(
                intercambio,
                400,
                hotel,
                estadoAreasComunes(resultado),
                solicitud.area(),
                solicitud.entrada(),
                solicitud.duracionHoras(),
                solicitud.visitantes());
    }

    private void responder(
            HttpExchange intercambio,
            int estadoHttp,
            Hotel hotel,
            PlantillasWeb.EstadoAreasComunes estado,
            String areaSeleccionada,
            LocalDateTime entrada,
            int duracionHoras,
            List<DatosHuesped> visitantes) throws IOException {
        RespuestasHttp.html(
                intercambio,
                estadoHttp,
                vistas.paginaAreasComunes(
                        hotel,
                        areasComunes.areas(hotel.codigo()),
                        estado,
                        areaSeleccionada,
                        fechaFormulario(entrada),
                        fechaFormulario(GestorAreasComunes.calcularSalida(
                                entrada, duracionHoras)),
                        duracionHoras,
                        visitantes));
    }

    private static PlantillasWeb.EstadoAreasComunes estadoAreasComunes(
            GestorAreasComunes.ResultadoOperacion resultado) {
        return switch (resultado) {
            case AREA_INVALIDA -> PlantillasWeb.EstadoAreasComunes.AREA_INVALIDA;
            case DURACION_INVALIDA -> PlantillasWeb.EstadoAreasComunes.DURACION_INVALIDA;
            case HORARIO_INVALIDO -> PlantillasWeb.EstadoAreasComunes.HORARIO_INVALIDO;
            case DOCUMENTO_DUPLICADO -> PlantillasWeb.EstadoAreasComunes.DOCUMENTO_DUPLICADO;
            case VISITANTE_INVALIDO -> PlantillasWeb.EstadoAreasComunes.VISITANTE_INVALIDO;
            case REGISTRADA -> PlantillasWeb.EstadoAreasComunes.INICIAL;
        };
    }

    private static String fechaFormulario(LocalDateTime fecha) {
        return fecha == null ? "" : fecha.toString();
    }
}
