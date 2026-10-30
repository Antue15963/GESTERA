package com.gestera.controladores;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.ResumenInventario;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.ResultadoOperacion;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.TipoServicio;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.gestera.modelo.huespedes.DatosHuesped;
import com.gestera.modelo.huespedes.GestorHuespedesHoteles;
import com.gestera.modelo.huespedes.GestorHuespedesHoteles.ResultadoValidacion;
import com.gestera.modelo.reportes.GestorReportes;
import com.gestera.infraestructura.servidor.RespuestasHttp;
import com.gestera.infraestructura.servidor.RutasGestera;
import com.gestera.infraestructura.servidor.SolicitudHttpException;
import com.gestera.infraestructura.servidor.SolicitudesHttp;
import com.gestera.vistas.PlantillasWeb;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import com.gestera.infraestructura.persistencia.TransaccionesHotel;

/** Controlador MVC de inventario, ocupación, limpieza y check-out. */
public final class ControladorHabitaciones {
    private final GestorHabitacionesHoteles habitaciones;
    private final GestorHuespedesHoteles huespedes;
    private final GestorReportes reportes;
    private final PlantillasWeb vistas;
    private final AutorizadorHotel autorizadorHotel;
    private final TransaccionesHotel transacciones;

    public ControladorHabitaciones(
            GestorHabitacionesHoteles habitaciones,
            GestorHuespedesHoteles huespedes,
            GestorReportes reportes,
            PlantillasWeb vistas,
            AutorizadorHotel autorizadorHotel) {
        this(habitaciones,huespedes,reportes,vistas,autorizadorHotel,null);
    }

    public ControladorHabitaciones(GestorHabitacionesHoteles habitaciones,GestorHuespedesHoteles huespedes,
            GestorReportes reportes,PlantillasWeb vistas,AutorizadorHotel autorizadorHotel,TransaccionesHotel transacciones) {
        this.transacciones=transacciones;
        this.habitaciones = Objects.requireNonNull(habitaciones, "habitaciones");
        this.huespedes = Objects.requireNonNull(huespedes, "huespedes");
        this.reportes = Objects.requireNonNull(reportes, "reportes");
        this.vistas = Objects.requireNonNull(vistas, "vistas");
        this.autorizadorHotel = Objects.requireNonNull(autorizadorHotel, "autorizadorHotel");
    }

    public void mostrarHabitaciones(HttpExchange intercambio) throws IOException {
        mostrarModulo(intercambio, PlantillasWeb.ModoHabitaciones.NORMAL);
    }

    public void mostrarLimpieza(HttpExchange intercambio) throws IOException {
        mostrarModulo(intercambio, PlantillasWeb.ModoHabitaciones.LIMPIEZA);
    }

    public void actualizar(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.ActualizacionHabitacion solicitud =
                SolicitudesHttp.leerActualizacionHabitacion(intercambio);
        PlantillasWeb.ModoHabitaciones modo = modoHabitaciones(solicitud.modo());
        Habitacion seleccionada = buscarHabitacion(hotel.codigo(), solicitud.numero());
        if (seleccionada == null) {
            RespuestasHttp.texto(intercambio, 400, "La habitación seleccionada no es válida.");
            return;
        }

        TipoServicio tipoServicio = TipoServicio.desdeCodigo(solicitud.tipoServicio())
                .orElse(null);
        if (tipoServicio == null) {
            responder(
                    intercambio, 400, hotel, modo,
                    PlantillasWeb.EstadoHabitaciones.TIPO_SERVICIO_INVALIDO, seleccionada, null);
            return;
        }
        EstadoHabitacion estado = EstadoHabitacion.desdeCodigo(solicitud.estado()).orElse(null);
        if (estado == null) {
            responder(
                    intercambio, 400, hotel, modo,
                    PlantillasWeb.EstadoHabitaciones.ESTADO_INVALIDO, seleccionada, null);
            return;
        }
        if (estado == EstadoHabitacion.OCUPADA) {
            ResultadoValidacion validacion = huespedes.validar(solicitud.huespedes());
            if (validacion != ResultadoValidacion.VALIDA) {
                PlantillasWeb.EstadoHabitaciones error =
                        validacion == ResultadoValidacion.DOCUMENTO_DUPLICADO
                                ? PlantillasWeb.EstadoHabitaciones.DOCUMENTO_HUESPED_DUPLICADO
                                : PlantillasWeb.EstadoHabitaciones.HUESPED_INVALIDO;
                responder(
                        intercambio, 400, hotel, modo, error, seleccionada,
                        solicitud.huespedes());
                return;
            }
        }

        LocalDateTime checkOutManual = null;
        if (solicitud.ajusteManualCheckOut()) {
            checkOutManual = SolicitudesHttp.leerCheckOutManual(solicitud.proximoCheckOut());
            if (checkOutManual == null) {
                responder(
                        intercambio, 400, hotel, modo,
                        PlantillasWeb.EstadoHabitaciones.HORARIO_CHECKOUT_INVALIDO,
                        seleccionada, null);
                return;
            }
        }
        BigDecimal montoAdicional =
                SolicitudesHttp.leerMontoAdicional(solicitud.montoAdicional());
        if (montoAdicional == null) {
            RespuestasHttp.texto(intercambio, 400, "El monto adicional no es válido.");
            return;
        }

        LocalDateTime salidaManualValidada=checkOutManual;
        Supplier<ResultadoOperacion> operacion=()->{
            Habitacion anteriorTransaccional=buscarHabitacion(hotel.codigo(),solicitud.numero());
            ResultadoOperacion resultado = habitaciones.actualizarHabitacion(
                hotel.codigo(), solicitud.numero(), solicitud.idSeccion(), tipoServicio,
                estado, solicitud.pagado(), salidaManualValidada,
                solicitud.horasAdicionales(), montoAdicional);
        if (resultado == ResultadoOperacion.ACTUALIZADA) {
            Habitacion actualizada = buscarHabitacion(hotel.codigo(), solicitud.numero());
            sincronizarIngreso(hotel.codigo(), anteriorTransaccional, actualizada);
            if (actualizada != null && actualizada.estado() == EstadoHabitacion.OCUPADA) {
                huespedes.guardarEstancia(
                        hotel.codigo(), actualizada.numero(), actualizada.seccion().colorHex(),
                        actualizada.checkIn(), actualizada.proximoCheckOut(), solicitud.huespedes());
            } else if (anteriorTransaccional.estado() == EstadoHabitacion.OCUPADA) {
                huespedes.finalizarEstancia(
                        hotel.codigo(), anteriorTransaccional.numero(),
                        anteriorTransaccional.checkIn(), anteriorTransaccional.proximoCheckOut());
            }
        }
            return resultado;
        };
        ResultadoOperacion resultado=transacciones==null ? operacion.get() : transacciones.ejecutar(hotel.codigo(),operacion);
        if (resultado==ResultadoOperacion.ACTUALIZADA) {
            RespuestasHttp.redirigir(intercambio, rutaHabitaciones(modo));
            return;
        }
        if (resultado == ResultadoOperacion.EXTENSION_NO_PERMITIDA) {
            RespuestasHttp.texto(
                    intercambio, 409,
                    "Solo una habitación ocupada puede recibir tiempo o cargos adicionales.");
            return;
        }
        if (resultado == ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS) {
            RespuestasHttp.texto(intercambio, 400, "Las horas adicionales no son válidas.");
            return;
        }
        if (resultado == ResultadoOperacion.MONTO_ADICIONAL_INVALIDO) {
            RespuestasHttp.texto(intercambio, 400, "El monto adicional no es válido.");
            return;
        }
        responder(intercambio, 400, hotel, modo, estadoHabitaciones(resultado), seleccionada, null);
    }

    public void registrarCheckOut(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel == null) {
            return;
        }
        SolicitudesHttp.RegistroCheckOut solicitud =
                SolicitudesHttp.leerRegistroCheckOut(intercambio);
        Supplier<ResultadoOperacion> operacion=()->{
        Habitacion estancia = buscarHabitacion(hotel.codigo(), solicitud.numero());
        ResultadoOperacion resultado =
                habitaciones.confirmarCheckOut(hotel.codigo(), solicitud.numero());
        if (resultado == ResultadoOperacion.ACTUALIZADA) {
            if (estancia != null) {
                huespedes.finalizarEstancia(
                        hotel.codigo(), estancia.numero(), estancia.checkIn(),
                        estancia.proximoCheckOut());
            }
        }
            return resultado;
        };
        ResultadoOperacion resultado=transacciones==null ? operacion.get() : transacciones.ejecutar(hotel.codigo(),operacion);
        if (resultado==ResultadoOperacion.ACTUALIZADA) {
            RespuestasHttp.redirigir(
                    intercambio, rutaHabitaciones(modoHabitaciones(solicitud.modo())));
            return;
        }
        if (resultado == ResultadoOperacion.CHECKOUT_AUN_NO_VENCE) {
            RespuestasHttp.texto(
                    intercambio, 409,
                    "La hora de check-out de esta habitación aún no ha llegado.");
            return;
        }
        if (resultado == ResultadoOperacion.CHECKOUT_NO_DISPONIBLE) {
            RespuestasHttp.texto(
                    intercambio, 409, "La habitación ya no tiene un check-out pendiente.");
            return;
        }
        RespuestasHttp.texto(intercambio, 400, "La habitación seleccionada no es válida.");
    }

    private void mostrarModulo(
            HttpExchange intercambio, PlantillasWeb.ModoHabitaciones modo) throws IOException {
        Hotel hotel = autorizadorHotel.autorizar(intercambio);
        if (hotel != null) {
            responder(
                    intercambio, 200, hotel, modo,
                    PlantillasWeb.EstadoHabitaciones.INICIAL, null, null);
        }
    }

    private void sincronizarIngreso(
            String codigoHotel, Habitacion anterior, Habitacion actualizada) {
        if (anterior == null || actualizada == null) {
            return;
        }
        boolean mismaEstancia = anterior.checkIn() != null
                && anterior.checkIn().equals(actualizada.checkIn());
        if (actualizada.pagado() && actualizada.checkIn() != null
                && (!mismaEstancia || !anterior.pagado()
                        || anterior.montoTotal().compareTo(actualizada.montoTotal()) != 0)) {
            reportes.guardarPagoHabitacion(
                    codigoHotel, actualizada.numero(), actualizada.checkIn(),
                    actualizada.montoTotal());
        } else if (mismaEstancia && anterior.pagado() && !actualizada.pagado()) {
            reportes.eliminarPagoHabitacion(
                    codigoHotel, anterior.numero(), anterior.checkIn());
        }
    }

    private void responder(
            HttpExchange intercambio,
            int estadoHttp,
            Hotel hotel,
            PlantillasWeb.ModoHabitaciones modo,
            PlantillasWeb.EstadoHabitaciones estado,
            Habitacion seleccionada,
            List<DatosHuesped> huespedesSeleccionados) throws IOException {
        ResumenInventario resumen = habitaciones.resumen(hotel.codigo());
        List<Habitacion> visibles = modo == PlantillasWeb.ModoHabitaciones.LIMPIEZA
                ? resumen.habitaciones().stream()
                        .filter(habitacion ->
                                habitacion.estado() == EstadoHabitacion.LIMPIEZA_PENDIENTE
                                        || habitacion.estado() == EstadoHabitacion.EN_LIMPIEZA)
                        .toList()
                : resumen.habitaciones();
        RespuestasHttp.html(
                intercambio,
                estadoHttp,
                vistas.paginaHabitaciones(
                        hotel,
                        visibles,
                        resumen.seccionesTarifarias(),
                        modo,
                        estado,
                        seleccionada,
                        huespedes.activosPorHabitacion(hotel.codigo()),
                        huespedesSeleccionados));
    }

    private Habitacion buscarHabitacion(String codigoHotel, int numero) {
        return habitaciones.habitaciones(codigoHotel).stream()
                .filter(habitacion -> habitacion.numero() == numero)
                .findFirst()
                .orElse(null);
    }

    private static PlantillasWeb.ModoHabitaciones modoHabitaciones(String valor) {
        return "limpieza".equalsIgnoreCase(valor)
                ? PlantillasWeb.ModoHabitaciones.LIMPIEZA
                : PlantillasWeb.ModoHabitaciones.NORMAL;
    }

    private static String rutaHabitaciones(PlantillasWeb.ModoHabitaciones modo) {
        return modo == PlantillasWeb.ModoHabitaciones.LIMPIEZA
                ? RutasGestera.LIMPIEZA
                : RutasGestera.HABITACIONES;
    }

    private static PlantillasWeb.EstadoHabitaciones estadoHabitaciones(
            ResultadoOperacion resultado) {
        return switch (resultado) {
            case HABITACION_DESCONOCIDA ->
                    PlantillasWeb.EstadoHabitaciones.HABITACION_DESCONOCIDA;
            case SECCION_DESCONOCIDA -> PlantillasWeb.EstadoHabitaciones.SECCION_DESCONOCIDA;
            case TIPO_SERVICIO_INVALIDO ->
                    PlantillasWeb.EstadoHabitaciones.TIPO_SERVICIO_INVALIDO;
            case ESTADO_INVALIDO -> PlantillasWeb.EstadoHabitaciones.ESTADO_INVALIDO;
            case HORARIO_CHECKOUT_INVALIDO ->
                    PlantillasWeb.EstadoHabitaciones.HORARIO_CHECKOUT_INVALIDO;
            case HOTEL_DESCONOCIDO -> PlantillasWeb.EstadoHabitaciones.HOTEL_DESCONOCIDO;
            default -> PlantillasWeb.EstadoHabitaciones.INICIAL;
        };
    }
}
