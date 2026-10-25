package com.gestera.vistas;

import com.gestera.areas.GestorAreasComunes.AreaComun;
import com.gestera.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.ResumenInventario;
import com.gestera.habitaciones.GestorHabitacionesHoteles.SeccionTarifaria;
import com.gestera.hoteles.CatalogoHoteles;
import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.huespedes.DatosHuesped;
import com.gestera.huespedes.FiltroHuespedes;
import com.gestera.huespedes.GestorHuespedesHoteles;
import com.gestera.huespedes.RegistroHuesped;
import com.gestera.huespedes.TipoDocumento;
import com.gestera.reportes.FiltroReporte.PeriodoReporte;
import com.gestera.reportes.FiltroReporte.TipoIngresoReporte;
import com.gestera.reportes.GestorReportes.ReporteIngresos;
import com.gestera.servicios.EstadoPedidoServicio;
import com.gestera.servicios.FiltroPedidosServicio;
import com.gestera.servicios.PedidoServicio;
import com.gestera.servicios.ProductoServicio;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Carga las plantillas HTML y sustituye exclusivamente contenido controlado. */
public final class PlantillasWeb {
    private static final String TOKEN_CLASE_ERROR = "{{CLASE_ERROR}}";
    private static final String TOKEN_MENSAJE_ERROR = "{{MENSAJE_ERROR}}";
    private static final String TOKEN_TARJETAS_HOTELES = "{{TARJETAS_HOTELES}}";
    private static final String TOKEN_CLASE_MODAL_HOTEL = "{{CLASE_MODAL_HOTEL}}";
    private static final String TOKEN_NOMBRE_HOTEL = "{{NOMBRE_HOTEL}}";
    private static final String TOKEN_CODIGO_HOTEL = "{{CODIGO_HOTEL}}";
    private static final String TOKEN_MENSAJE_ERROR_HOTEL = "{{MENSAJE_ERROR_HOTEL}}";
    private static final String TOKEN_CONFIGURACION_LLAVE_REQUERIDA =
            "{{CONFIGURACION_LLAVE_REQUERIDA}}";
    private static final String TOKEN_CLASE_MODAL_CAMBIO_LLAVE =
            "{{CLASE_MODAL_CAMBIO_LLAVE}}";
    private static final String TOKEN_MENSAJE_ERROR_CAMBIO_LLAVE =
            "{{MENSAJE_ERROR_CAMBIO_LLAVE}}";
    private static final String TOKEN_FORMULARIO_CAMBIO_LLAVE =
            "{{FORMULARIO_CAMBIO_LLAVE}}";
    private static final String TOKEN_FORMULARIO_CAMBIO_LLAVE_CONFIGURACION =
            "{{FORMULARIO_CAMBIO_LLAVE_CONFIGURACION}}";
    private static final String TOKEN_ACCION_CAMBIO_LLAVE = "{{ACCION_CAMBIO_LLAVE}}";
    private static final String TOKEN_ACCION_CANCELAR_CAMBIO_LLAVE =
            "{{ACCION_CANCELAR_CAMBIO_LLAVE}}";
    private static final String TOKEN_MODO_HABITACIONES = "{{MODO_HABITACIONES}}";
    private static final String TOKEN_TITULO_HABITACIONES = "{{TITULO_HABITACIONES}}";
    private static final String TOKEN_DESCRIPCION_HABITACIONES =
            "{{DESCRIPCION_HABITACIONES}}";
    private static final String TOKEN_CANTIDAD_HABITACIONES =
            "{{CANTIDAD_HABITACIONES}}";
    private static final String TOKEN_TARJETAS_HABITACIONES =
            "{{TARJETAS_HABITACIONES}}";
    private static final String TOKEN_TARJETAS_SECCIONES_TARIFARIAS =
            "{{TARJETAS_SECCIONES_TARIFARIAS}}";
    private static final String TOKEN_CANTIDAD_SECCIONES_TARIFARIAS =
            "{{CANTIDAD_SECCIONES_TARIFARIAS}}";
    private static final String TOKEN_OPCIONES_SECCIONES_TARIFARIAS =
            "{{OPCIONES_SECCIONES_TARIFARIAS}}";
    private static final String TOKEN_MENSAJE_ERROR_CONFIGURACION =
            "{{MENSAJE_ERROR_CONFIGURACION}}";
    private static final String TOKEN_CLASE_MODAL_HABITACION =
            "{{CLASE_MODAL_HABITACION}}";
    private static final String TOKEN_NUMERO_HABITACION = "{{NUMERO_HABITACION}}";
    private static final String TOKEN_PRECIO_HABITACION = "{{PRECIO_HABITACION}}";
    private static final String TOKEN_SECCION_TARIFARIA_HABITACION =
            "{{SECCION_TARIFARIA_HABITACION}}";
    private static final String TOKEN_TIPO_SERVICIO_HABITACION =
            "{{TIPO_SERVICIO_HABITACION}}";
    private static final String TOKEN_ESTADO_HABITACION = "{{ESTADO_HABITACION}}";
    private static final String TOKEN_CHECKIN_HABITACION = "{{CHECKIN_HABITACION}}";
    private static final String TOKEN_CHECKOUT_HABITACION = "{{CHECKOUT_HABITACION}}";
    private static final String TOKEN_PAGADO_HABITACION = "{{PAGADO_HABITACION}}";
    private static final String TOKEN_MONTO_ADICIONAL_HABITACION =
            "{{MONTO_ADICIONAL_HABITACION}}";
    private static final String TOKEN_MONTO_TOTAL_HABITACION =
            "{{MONTO_TOTAL_HABITACION}}";
    private static final String TOKEN_HORAS_ADICIONALES_HABITACION =
            "{{HORAS_ADICIONALES_HABITACION}}";
    private static final String TOKEN_MENSAJE_ERROR_HABITACION =
            "{{MENSAJE_ERROR_HABITACION}}";
    private static final String TOKEN_CANTIDAD_HUESPEDES = "{{CANTIDAD_HUESPEDES}}";
    private static final String TOKEN_FILAS_HUESPEDES = "{{FILAS_HUESPEDES}}";
    private static final String TOKEN_CANTIDAD_REGISTROS = "{{CANTIDAD_REGISTROS}}";
    private static final String TOKEN_REGISTROS_HUESPEDES = "{{REGISTROS_HUESPEDES}}";
    private static final String TOKEN_FILTRO_NOMBRE = "{{FILTRO_NOMBRE}}";
    private static final String TOKEN_FILTRO_DIA = "{{FILTRO_DIA}}";
    private static final String TOKEN_FILTRO_DESDE = "{{FILTRO_DESDE}}";
    private static final String TOKEN_FILTRO_HASTA = "{{FILTRO_HASTA}}";
    private static final String TOKEN_OPCIONES_COLORES_FILTRO =
            "{{OPCIONES_COLORES_FILTRO}}";
    private static final String TOKEN_RESUMEN_FILTROS = "{{RESUMEN_FILTROS}}";
    private static final String TOKEN_TOTAL_PEDIDOS_SERVICIO =
            "{{TOTAL_PEDIDOS_SERVICIO}}";
    private static final String TOKEN_PENDIENTES_SERVICIO = "{{PENDIENTES_SERVICIO}}";
    private static final String TOKEN_PREPARACION_SERVICIO = "{{PREPARACION_SERVICIO}}";
    private static final String TOKEN_OPCIONES_HABITACIONES_SERVICIO =
            "{{OPCIONES_HABITACIONES_SERVICIO}}";
    private static final String TOKEN_OPCIONES_PRODUCTOS_SERVICIO =
            "{{OPCIONES_PRODUCTOS_SERVICIO}}";
    private static final String TOKEN_OPCIONES_CATEGORIAS_SERVICIO =
            "{{OPCIONES_CATEGORIAS_SERVICIO}}";
    private static final String TOKEN_CATEGORIAS_CATALOGO_SERVICIO =
            "{{CATEGORIAS_CATALOGO_SERVICIO}}";
    private static final String TOKEN_CONTROLES_PRODUCTO_CATALOGO =
            "{{CONTROLES_PRODUCTO_CATALOGO}}";
    private static final String TOKEN_AVISO_SIN_PRODUCTOS_SERVICIO =
            "{{AVISO_SIN_PRODUCTOS_SERVICIO}}";
    private static final String TOKEN_BOTON_PEDIDO_DESHABILITADO =
            "{{BOTON_PEDIDO_DESHABILITADO}}";
    private static final String TOKEN_AVISO_SIN_HABITACIONES_OCUPADAS =
            "{{AVISO_SIN_HABITACIONES_OCUPADAS}}";
    private static final String TOKEN_PEDIDOS_SERVICIO = "{{PEDIDOS_SERVICIO}}";
    private static final String TOKEN_CLASE_ERROR_SERVICIO = "{{CLASE_ERROR_SERVICIO}}";
    private static final String TOKEN_MENSAJE_ERROR_SERVICIO = "{{MENSAJE_ERROR_SERVICIO}}";
    private static final String TOKEN_CLASE_MODAL_CATALOGO = "{{CLASE_MODAL_CATALOGO}}";
    private static final String TOKEN_FILTRO_FECHA_PEDIDO = "{{FILTRO_FECHA_PEDIDO}}";
    private static final String TOKEN_FILTRO_HORA_PEDIDO = "{{FILTRO_HORA_PEDIDO}}";
    private static final String TOKEN_FILTRO_HABITACION_PEDIDO =
            "{{FILTRO_HABITACION_PEDIDO}}";
    private static final String TOKEN_RESUMEN_FILTRO_PEDIDOS =
            "{{RESUMEN_FILTRO_PEDIDOS}}";
    private static final String TOKEN_PERIODO_DIA = "{{PERIODO_DIA}}";
    private static final String TOKEN_PERIODO_SEMANA = "{{PERIODO_SEMANA}}";
    private static final String TOKEN_PERIODO_MES = "{{PERIODO_MES}}";
    private static final String TOKEN_PERIODO_ANIO = "{{PERIODO_ANIO}}";
    private static final String TOKEN_PERIODO_PERSONALIZADO = "{{PERIODO_PERSONALIZADO}}";
    private static final String TOKEN_TIPO_TODOS = "{{TIPO_TODOS}}";
    private static final String TOKEN_TIPO_HABITACIONES = "{{TIPO_HABITACIONES}}";
    private static final String TOKEN_TIPO_SERVICIOS = "{{TIPO_SERVICIOS}}";
    private static final String TOKEN_REPORTE_DESDE = "{{REPORTE_DESDE}}";
    private static final String TOKEN_REPORTE_HASTA = "{{REPORTE_HASTA}}";
    private static final String TOKEN_RANGO_REPORTE = "{{RANGO_REPORTE}}";
    private static final String TOKEN_TOTAL_REPORTE = "{{TOTAL_REPORTE}}";
    private static final String TOKEN_TOTAL_HABITACIONES_REPORTE =
            "{{TOTAL_HABITACIONES_REPORTE}}";
    private static final String TOKEN_TOTAL_SERVICIOS_REPORTE =
            "{{TOTAL_SERVICIOS_REPORTE}}";
    private static final String TOKEN_MOVIMIENTOS_REPORTE = "{{MOVIMIENTOS_REPORTE}}";
    private static final String TOKEN_TASA_OCUPACION = "{{TASA_OCUPACION}}";
    private static final String TOKEN_HABITACIONES_TOTAL = "{{HABITACIONES_TOTAL}}";
    private static final String TOKEN_HABITACIONES_OPERATIVAS =
            "{{HABITACIONES_OPERATIVAS}}";
    private static final String TOKEN_HABITACIONES_OCUPADAS =
            "{{HABITACIONES_OCUPADAS}}";
    private static final String TOKEN_HABITACIONES_DISPONIBLES =
            "{{HABITACIONES_DISPONIBLES}}";
    private static final String TOKEN_HABITACIONES_LIMPIEZA =
            "{{HABITACIONES_LIMPIEZA}}";
    private static final String TOKEN_HABITACIONES_MANTENIMIENTO =
            "{{HABITACIONES_MANTENIMIENTO}}";
    private static final String TOKEN_REVPAR_REPORTE = "{{REVPAR_REPORTE}}";
    private static final String TOKEN_TARIFA_PROMEDIO_REPORTE =
            "{{TARIFA_PROMEDIO_REPORTE}}";
    private static final String TOKEN_ESTANCIAS_PAGADAS_REPORTE =
            "{{ESTANCIAS_PAGADAS_REPORTE}}";
    private static final String TOKEN_TICKET_PROMEDIO_SERVICIO =
            "{{TICKET_PROMEDIO_SERVICIO}}";
    private static final String TOKEN_PEDIDOS_ENTREGADOS_REPORTE =
            "{{PEDIDOS_ENTREGADOS_REPORTE}}";
    private static final String TOKEN_MONTO_PENDIENTE_COBRO =
            "{{MONTO_PENDIENTE_COBRO}}";
    private static final String TOKEN_PAGOS_PENDIENTES = "{{PAGOS_PENDIENTES}}";
    private static final String TOKEN_PEDIDOS_ACTIVOS = "{{PEDIDOS_ACTIVOS}}";
    private static final String TOKEN_PARTICIPACION_HABITACIONES =
            "{{PARTICIPACION_HABITACIONES}}";
    private static final String TOKEN_PARTICIPACION_SERVICIOS =
            "{{PARTICIPACION_SERVICIOS}}";
    private static final String TOKEN_GRAFICO_INGRESOS = "{{GRAFICO_INGRESOS}}";
    private static final String TOKEN_RANKING_HABITACIONES = "{{RANKING_HABITACIONES}}";
    private static final String TOKEN_FILAS_RESUMEN_REPORTE =
            "{{FILAS_RESUMEN_REPORTE}}";
    private static final String TOKEN_DETALLES_REPORTE = "{{DETALLES_REPORTE}}";
    private static final String TOKEN_TARJETAS_AREAS_COMUNES =
            "{{TARJETAS_AREAS_COMUNES}}";
    private static final String TOKEN_AREA_COMUN_SELECCIONADA =
            "{{AREA_COMUN_SELECCIONADA}}";
    private static final String TOKEN_ENTRADA_AREA_COMUN = "{{ENTRADA_AREA_COMUN}}";
    private static final String TOKEN_SALIDA_AREA_COMUN = "{{SALIDA_AREA_COMUN}}";
    private static final String TOKEN_DURACION_UNA_HORA = "{{DURACION_UNA_HORA}}";
    private static final String TOKEN_DURACION_TRES_HORAS = "{{DURACION_TRES_HORAS}}";
    private static final String TOKEN_DURACION_CINCO_HORAS = "{{DURACION_CINCO_HORAS}}";
    private static final String TOKEN_CLASE_ERROR_AREA = "{{CLASE_ERROR_AREA}}";
    private static final String TOKEN_MENSAJE_ERROR_AREA = "{{MENSAJE_ERROR_AREA}}";
    private final String acceso;
    private final String panel;
    private final String gestion;
    private final String configuracionLlave;
    private final String formularioCambioLlave;
    private final String habitaciones;
    private final String huespedes;
    private final String servicioHabitacion;
    private final String reportes;
    private final String areasComunes;
    private final String configuracionHotel;

    public PlantillasWeb() {
        acceso = cargar("/paginas/acceso.html");
        panel = cargar("/paginas/panel.html");
        gestion = cargar("/paginas/gestion.html");
        configuracionLlave = cargar("/fragmentos/configuracion-llave.html");
        formularioCambioLlave = cargar("/fragmentos/formulario-cambio-llave.html");
        habitaciones = cargar("/paginas/habitaciones.html");
        huespedes = cargar("/paginas/huespedes.html");
        servicioHabitacion = cargar("/paginas/servicio-habitacion.html");
        reportes = cargar("/paginas/reportes.html");
        areasComunes = cargar("/paginas/areas-comunes.html");
        configuracionHotel = cargar("/paginas/configuracion-hotel.html");

        exigirTokens(acceso, "acceso", TOKEN_CLASE_ERROR, TOKEN_MENSAJE_ERROR);
        exigirTokens(
                panel,
                "panel",
                TOKEN_TARJETAS_HOTELES,
                TOKEN_CLASE_MODAL_HOTEL,
                TOKEN_NOMBRE_HOTEL,
                TOKEN_CODIGO_HOTEL,
                TOKEN_MENSAJE_ERROR_HOTEL);
        exigirTokens(
                gestion,
                "gestión",
                TOKEN_NOMBRE_HOTEL,
                TOKEN_CONFIGURACION_LLAVE_REQUERIDA);
        exigirTokens(
                configuracionLlave,
                "configuración de llave",
                TOKEN_CLASE_MODAL_CAMBIO_LLAVE,
                TOKEN_FORMULARIO_CAMBIO_LLAVE);
        exigirTokens(
                formularioCambioLlave,
                "formulario de cambio de llave",
                TOKEN_ACCION_CAMBIO_LLAVE,
                TOKEN_ACCION_CANCELAR_CAMBIO_LLAVE,
                TOKEN_MENSAJE_ERROR_CAMBIO_LLAVE);
        exigirTokens(
                habitaciones,
                "habitaciones",
                TOKEN_NOMBRE_HOTEL,
                TOKEN_MODO_HABITACIONES,
                TOKEN_TITULO_HABITACIONES,
                TOKEN_DESCRIPCION_HABITACIONES,
                TOKEN_TARJETAS_HABITACIONES,
                TOKEN_OPCIONES_SECCIONES_TARIFARIAS,
                TOKEN_CLASE_MODAL_HABITACION,
                TOKEN_NUMERO_HABITACION,
                TOKEN_PRECIO_HABITACION,
                TOKEN_SECCION_TARIFARIA_HABITACION,
                TOKEN_TIPO_SERVICIO_HABITACION,
                TOKEN_ESTADO_HABITACION,
                TOKEN_CHECKIN_HABITACION,
                TOKEN_CHECKOUT_HABITACION,
                TOKEN_PAGADO_HABITACION,
                TOKEN_MONTO_ADICIONAL_HABITACION,
                TOKEN_MONTO_TOTAL_HABITACION,
                TOKEN_HORAS_ADICIONALES_HABITACION,
                TOKEN_CANTIDAD_HUESPEDES,
                TOKEN_FILAS_HUESPEDES,
                TOKEN_MENSAJE_ERROR_HABITACION);
        exigirTokens(
                huespedes,
                "huéspedes",
                TOKEN_NOMBRE_HOTEL,
                TOKEN_CANTIDAD_REGISTROS,
                TOKEN_REGISTROS_HUESPEDES,
                TOKEN_FILTRO_NOMBRE,
                TOKEN_FILTRO_DIA,
                TOKEN_FILTRO_DESDE,
                TOKEN_FILTRO_HASTA,
                TOKEN_OPCIONES_COLORES_FILTRO,
                TOKEN_RESUMEN_FILTROS);
        exigirTokens(
                servicioHabitacion,
                "servicio al cuarto",
                TOKEN_NOMBRE_HOTEL,
                TOKEN_TOTAL_PEDIDOS_SERVICIO,
                TOKEN_PENDIENTES_SERVICIO,
                TOKEN_PREPARACION_SERVICIO,
                TOKEN_OPCIONES_HABITACIONES_SERVICIO,
                TOKEN_OPCIONES_PRODUCTOS_SERVICIO,
                TOKEN_OPCIONES_CATEGORIAS_SERVICIO,
                TOKEN_CATEGORIAS_CATALOGO_SERVICIO,
                TOKEN_CONTROLES_PRODUCTO_CATALOGO,
                TOKEN_AVISO_SIN_PRODUCTOS_SERVICIO,
                TOKEN_BOTON_PEDIDO_DESHABILITADO,
                TOKEN_AVISO_SIN_HABITACIONES_OCUPADAS,
                TOKEN_PEDIDOS_SERVICIO,
                TOKEN_FILTRO_FECHA_PEDIDO,
                TOKEN_FILTRO_HORA_PEDIDO,
                TOKEN_FILTRO_HABITACION_PEDIDO,
                TOKEN_RESUMEN_FILTRO_PEDIDOS,
                TOKEN_CLASE_MODAL_CATALOGO,
                TOKEN_CLASE_ERROR_SERVICIO,
                TOKEN_MENSAJE_ERROR_SERVICIO);
        exigirTokens(
                reportes,
                "reportes",
                TOKEN_NOMBRE_HOTEL,
                TOKEN_PERIODO_DIA,
                TOKEN_PERIODO_SEMANA,
                TOKEN_PERIODO_MES,
                TOKEN_PERIODO_ANIO,
                TOKEN_PERIODO_PERSONALIZADO,
                TOKEN_TIPO_TODOS,
                TOKEN_TIPO_HABITACIONES,
                TOKEN_TIPO_SERVICIOS,
                TOKEN_REPORTE_DESDE,
                TOKEN_REPORTE_HASTA,
                TOKEN_RANGO_REPORTE,
                TOKEN_TOTAL_REPORTE,
                TOKEN_TOTAL_HABITACIONES_REPORTE,
                TOKEN_TOTAL_SERVICIOS_REPORTE,
                TOKEN_MOVIMIENTOS_REPORTE,
                TOKEN_TASA_OCUPACION,
                TOKEN_HABITACIONES_TOTAL,
                TOKEN_HABITACIONES_OPERATIVAS,
                TOKEN_HABITACIONES_OCUPADAS,
                TOKEN_HABITACIONES_DISPONIBLES,
                TOKEN_HABITACIONES_LIMPIEZA,
                TOKEN_HABITACIONES_MANTENIMIENTO,
                TOKEN_REVPAR_REPORTE,
                TOKEN_TARIFA_PROMEDIO_REPORTE,
                TOKEN_ESTANCIAS_PAGADAS_REPORTE,
                TOKEN_TICKET_PROMEDIO_SERVICIO,
                TOKEN_PEDIDOS_ENTREGADOS_REPORTE,
                TOKEN_MONTO_PENDIENTE_COBRO,
                TOKEN_PAGOS_PENDIENTES,
                TOKEN_PEDIDOS_ACTIVOS,
                TOKEN_PARTICIPACION_HABITACIONES,
                TOKEN_PARTICIPACION_SERVICIOS,
                TOKEN_GRAFICO_INGRESOS,
                TOKEN_RANKING_HABITACIONES,
                TOKEN_FILAS_RESUMEN_REPORTE,
                TOKEN_DETALLES_REPORTE);
        exigirTokens(
                areasComunes,
                "áreas comunes",
                TOKEN_NOMBRE_HOTEL,
                TOKEN_TARJETAS_AREAS_COMUNES,
                TOKEN_AREA_COMUN_SELECCIONADA,
                TOKEN_ENTRADA_AREA_COMUN,
                TOKEN_SALIDA_AREA_COMUN,
                TOKEN_DURACION_UNA_HORA,
                TOKEN_DURACION_TRES_HORAS,
                TOKEN_DURACION_CINCO_HORAS,
                TOKEN_CANTIDAD_HUESPEDES,
                TOKEN_FILAS_HUESPEDES,
                TOKEN_CLASE_ERROR_AREA,
                TOKEN_MENSAJE_ERROR_AREA);
        exigirTokens(
                configuracionHotel,
                "configuración",
                TOKEN_NOMBRE_HOTEL,
                TOKEN_CANTIDAD_HABITACIONES,
                TOKEN_TARJETAS_SECCIONES_TARIFARIAS,
                TOKEN_CANTIDAD_SECCIONES_TARIFARIAS,
                TOKEN_FORMULARIO_CAMBIO_LLAVE_CONFIGURACION,
                TOKEN_MENSAJE_ERROR_CONFIGURACION);
    }

    public String paginaAcceso(EstadoAcceso estado) {
        EstadoAcceso estadoSeguro = estado == null ? EstadoAcceso.INICIAL : estado;
        return completar(
                acceso,
                TOKEN_CLASE_ERROR, estadoSeguro.claseCss,
                TOKEN_MENSAJE_ERROR, estadoSeguro.mensaje);
    }

    /** Conserva el punto de entrada anterior para pruebas y usos existentes. */
    public String paginaPanel() {
        return paginaPanel(
                new CatalogoHoteles().todos(), EstadoHotel.INICIAL, null);
    }

    public String paginaPanel(
            List<Hotel> hoteles, EstadoHotel estado, Hotel hotelSeleccionado) {
        Objects.requireNonNull(hoteles, "hoteles");
        EstadoHotel estadoSeguro = estado == null ? EstadoHotel.INICIAL : estado;
        String nombreSeleccionado = hotelSeleccionado == null
                ? "el hotel seleccionado"
                : hotelSeleccionado.nombre();
        String codigoSeleccionado = hotelSeleccionado == null
                ? ""
                : hotelSeleccionado.codigo();
        String claseModal = hotelSeleccionado == null ? "" : estadoSeguro.claseCss;

        return completar(
                panel,
                TOKEN_TARJETAS_HOTELES,
                ComponentesHtml.crearTarjetasHoteles(hoteles),
                TOKEN_CLASE_MODAL_HOTEL,
                ComponentesHtml.escaparAtributo(claseModal),
                TOKEN_NOMBRE_HOTEL,
                ComponentesHtml.escaparHtml(nombreSeleccionado),
                TOKEN_CODIGO_HOTEL,
                ComponentesHtml.escaparAtributo(codigoSeleccionado),
                TOKEN_MENSAJE_ERROR_HOTEL,
                ComponentesHtml.escaparHtml(estadoSeguro.mensaje));
    }

    public String paginaGestion(Hotel hotel) {
        return paginaGestion(hotel, true, EstadoCambioLlave.INICIAL);
    }

    public String paginaGestion(
            Hotel hotel, boolean requiereCambio, EstadoCambioLlave estado) {
        Objects.requireNonNull(hotel, "hotel");
        EstadoCambioLlave estadoSeguro = estado == null
                ? EstadoCambioLlave.INICIAL
                : estado;
        String bloqueConfiguracion = requiereCambio
                ? completar(
                        configuracionLlave,
                        TOKEN_CLASE_MODAL_CAMBIO_LLAVE,
                        estadoSeguro.claseCss,
                        TOKEN_FORMULARIO_CAMBIO_LLAVE,
                        formularioCambioLlave(
                                "/hotel/llave", estadoSeguro, true))
                : "";
        return completar(
                gestion,
                TOKEN_NOMBRE_HOTEL,
                ComponentesHtml.escaparHtml(hotel.nombre()),
                TOKEN_CONFIGURACION_LLAVE_REQUERIDA,
                bloqueConfiguracion);
    }

    public String paginaHabitaciones(
            Hotel hotel,
            List<Habitacion> habitacionesVisibles,
            List<SeccionTarifaria> seccionesTarifarias,
            ModoHabitaciones modo,
            EstadoHabitaciones estado,
            Habitacion habitacionSeleccionada) {
        return paginaHabitaciones(
                hotel,
                habitacionesVisibles,
                seccionesTarifarias,
                modo,
                estado,
                habitacionSeleccionada,
                Map.of(),
                null);
    }

    public String paginaHabitaciones(
            Hotel hotel,
            List<Habitacion> habitacionesVisibles,
            List<SeccionTarifaria> seccionesTarifarias,
            ModoHabitaciones modo,
            EstadoHabitaciones estado,
            Habitacion habitacionSeleccionada,
            Map<Integer, List<DatosHuesped>> huespedesPorHabitacion,
            List<DatosHuesped> huespedesSeleccionados) {
        Objects.requireNonNull(hotel, "hotel");
        Objects.requireNonNull(habitacionesVisibles, "habitacionesVisibles");
        Objects.requireNonNull(seccionesTarifarias, "seccionesTarifarias");
        Objects.requireNonNull(huespedesPorHabitacion, "huespedesPorHabitacion");
        ModoHabitaciones modoSeguro = modo == null ? ModoHabitaciones.NORMAL : modo;
        EstadoHabitaciones estadoSeguro = estado == null
                ? EstadoHabitaciones.INICIAL
                : estado;

        String numero = habitacionSeleccionada == null
                ? ""
                : Integer.toString(habitacionSeleccionada.numero());
        String precio = habitacionSeleccionada == null
                ? ""
                : ComponentesHtml.precioCanonico(habitacionSeleccionada);
        String idSeccion = habitacionSeleccionada == null
                ? ""
                : habitacionSeleccionada.seccion().id();
        String tipoServicio = habitacionSeleccionada == null
                ? ""
                : habitacionSeleccionada.tipoServicio().codigo();
        String estadoHabitacion = habitacionSeleccionada == null
                ? "disponible"
                : habitacionSeleccionada.estado().codigo();
        String checkIn = habitacionSeleccionada == null
                ? ""
                : ComponentesHtml.fechaDato(habitacionSeleccionada.checkIn());
        String checkOut = habitacionSeleccionada == null
                ? ""
                : ComponentesHtml.fechaDato(habitacionSeleccionada.proximoCheckOut());
        String pagado = Boolean.toString(
                habitacionSeleccionada != null && habitacionSeleccionada.pagado());
        String montoAdicional = habitacionSeleccionada == null
                ? "0.00"
                : ComponentesHtml.precioCanonico(habitacionSeleccionada.montoAdicional());
        String montoTotal = habitacionSeleccionada == null
                ? "0.00"
                : ComponentesHtml.precioCanonico(habitacionSeleccionada.montoTotal());
        String horasAdicionales = habitacionSeleccionada == null
                ? "0"
                : Integer.toString(habitacionSeleccionada.horasAdicionales());
        String claseModal = habitacionSeleccionada == null
                ? ""
                : estadoSeguro.claseModal;
        List<DatosHuesped> huespedesFormulario = huespedesSeleccionados;
        if (huespedesFormulario == null && habitacionSeleccionada != null) {
            huespedesFormulario = huespedesPorHabitacion.get(habitacionSeleccionada.numero());
        }
        if (huespedesFormulario == null || huespedesFormulario.isEmpty()) {
            huespedesFormulario = java.util.Collections.nCopies(
                    GestorHuespedesHoteles.HUESPEDES_PREDERMINADOS,
                    new DatosHuesped("", TipoDocumento.DNI, ""));
        }

        return completar(
                habitaciones,
                TOKEN_NOMBRE_HOTEL,
                ComponentesHtml.escaparHtml(hotel.nombre()),
                TOKEN_MODO_HABITACIONES, modoSeguro.codigo,
                TOKEN_TITULO_HABITACIONES,
                ComponentesHtml.escaparHtml(modoSeguro.titulo),
                TOKEN_DESCRIPCION_HABITACIONES,
                ComponentesHtml.escaparHtml(modoSeguro.descripcion),
                TOKEN_TARJETAS_HABITACIONES,
                ComponentesHtml.crearTarjetasHabitaciones(
                        habitacionesVisibles, huespedesPorHabitacion),
                TOKEN_OPCIONES_SECCIONES_TARIFARIAS,
                ComponentesHtml.crearOpcionesSecciones(seccionesTarifarias),
                TOKEN_CLASE_MODAL_HABITACION, claseModal,
                TOKEN_NUMERO_HABITACION,
                ComponentesHtml.escaparAtributo(numero),
                TOKEN_PRECIO_HABITACION,
                ComponentesHtml.escaparAtributo(precio),
                TOKEN_SECCION_TARIFARIA_HABITACION,
                ComponentesHtml.escaparAtributo(idSeccion),
                TOKEN_TIPO_SERVICIO_HABITACION, tipoServicio,
                TOKEN_ESTADO_HABITACION,
                ComponentesHtml.escaparAtributo(estadoHabitacion),
                TOKEN_CHECKIN_HABITACION,
                ComponentesHtml.escaparAtributo(checkIn),
                TOKEN_CHECKOUT_HABITACION,
                ComponentesHtml.escaparAtributo(checkOut),
                TOKEN_PAGADO_HABITACION,
                ComponentesHtml.escaparAtributo(pagado),
                TOKEN_MONTO_ADICIONAL_HABITACION,
                ComponentesHtml.escaparAtributo(montoAdicional),
                TOKEN_MONTO_TOTAL_HABITACION,
                ComponentesHtml.escaparAtributo(montoTotal),
                TOKEN_HORAS_ADICIONALES_HABITACION,
                ComponentesHtml.escaparAtributo(horasAdicionales),
                TOKEN_CANTIDAD_HUESPEDES,
                Integer.toString(huespedesFormulario.size()),
                TOKEN_FILAS_HUESPEDES,
                ComponentesHtml.crearFilasHuespedes(huespedesFormulario),
                TOKEN_MENSAJE_ERROR_HABITACION,
                ComponentesHtml.escaparHtml(estadoSeguro.mensajeHabitacion));
    }

    public String paginaHuespedes(Hotel hotel, List<RegistroHuesped> registros) {
        return paginaHuespedes(
                hotel, registros, FiltroHuespedes.vacio(), List.of());
    }

    public String paginaHuespedes(
            Hotel hotel,
            List<RegistroHuesped> registros,
            FiltroHuespedes filtro,
            List<SeccionTarifaria> seccionesTarifarias) {
        Objects.requireNonNull(hotel, "hotel");
        Objects.requireNonNull(registros, "registros");
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(seccionesTarifarias, "seccionesTarifarias");
        return completar(
                huespedes,
                TOKEN_NOMBRE_HOTEL,
                ComponentesHtml.escaparHtml(hotel.nombre()),
                TOKEN_CANTIDAD_REGISTROS,
                Integer.toString(registros.size()),
                TOKEN_REGISTROS_HUESPEDES,
                ComponentesHtml.crearRegistrosHuespedes(registros),
                TOKEN_FILTRO_NOMBRE,
                ComponentesHtml.escaparAtributo(filtro.nombre()),
                TOKEN_FILTRO_DIA,
                filtro.dia() == null ? "" : filtro.dia().toString(),
                TOKEN_FILTRO_DESDE,
                ComponentesHtml.fechaDato(filtro.estuvoDesde()),
                TOKEN_FILTRO_HASTA,
                ComponentesHtml.fechaDato(filtro.estuvoHasta()),
                TOKEN_OPCIONES_COLORES_FILTRO,
                ComponentesHtml.crearOpcionesColoresFiltro(
                        seccionesTarifarias, filtro.colorHabitacion()),
                TOKEN_RESUMEN_FILTROS,
                filtro.activo()
                        ? registros.size() + (registros.size() == 1
                                ? " resultado con los filtros aplicados."
                                : " resultados con los filtros aplicados.")
                        : "Mostrando todo el historial del hotel.");
    }

    public String paginaServicioHabitacion(
            Hotel hotel,
            List<Habitacion> habitaciones,
            List<ProductoServicio> productos,
            List<PedidoServicio> pedidos,
            EstadoServicioHabitacion estado) {
        return paginaServicioHabitacion(
                hotel,
                habitaciones,
                productos,
                pedidos,
                estado,
                FiltroPedidosServicio.vacio());
    }

    public String paginaServicioHabitacion(
            Hotel hotel,
            List<Habitacion> habitaciones,
            List<ProductoServicio> productos,
            List<PedidoServicio> pedidos,
            EstadoServicioHabitacion estado,
            FiltroPedidosServicio filtro) {
        return paginaServicioHabitacion(
                hotel,
                habitaciones,
                productos,
                categoriasDesdeProductos(productos),
                pedidos,
                estado,
                filtro);
    }

    public String paginaServicioHabitacion(
            Hotel hotel,
            List<Habitacion> habitaciones,
            List<ProductoServicio> productos,
            List<String> categorias,
            List<PedidoServicio> pedidos,
            EstadoServicioHabitacion estado,
            FiltroPedidosServicio filtro) {
        Objects.requireNonNull(hotel, "hotel");
        Objects.requireNonNull(habitaciones, "habitaciones");
        Objects.requireNonNull(productos, "productos");
        Objects.requireNonNull(categorias, "categorias");
        Objects.requireNonNull(pedidos, "pedidos");
        FiltroPedidosServicio filtroSeguro = filtro == null
                ? FiltroPedidosServicio.vacio()
                : filtro;
        EstadoServicioHabitacion estadoSeguro = estado == null
                ? EstadoServicioHabitacion.INICIAL : estado;
        long ocupadas = habitaciones.stream()
                .filter(habitacion -> habitacion.estado()
                        == com.gestera.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion.OCUPADA)
                .count();
        long pendientes = pedidos.stream()
                .filter(pedido -> pedido.estado() == EstadoPedidoServicio.PENDIENTE)
                .count();
        long preparacion = pedidos.stream()
                .filter(pedido -> pedido.estado() == EstadoPedidoServicio.EN_PREPARACION)
                .count();
        return completar(
                servicioHabitacion,
                TOKEN_NOMBRE_HOTEL, ComponentesHtml.escaparHtml(hotel.nombre()),
                TOKEN_TOTAL_PEDIDOS_SERVICIO, Integer.toString(pedidos.size()),
                TOKEN_PENDIENTES_SERVICIO, Long.toString(pendientes),
                TOKEN_PREPARACION_SERVICIO, Long.toString(preparacion),
                TOKEN_OPCIONES_HABITACIONES_SERVICIO,
                ComponentesHtml.crearOpcionesHabitacionesServicio(habitaciones),
                TOKEN_OPCIONES_PRODUCTOS_SERVICIO,
                ComponentesHtml.crearOpcionesProductosServicio(productos),
                TOKEN_OPCIONES_CATEGORIAS_SERVICIO,
                ComponentesHtml.crearOpcionesCategoriasServicio(categorias),
                TOKEN_CATEGORIAS_CATALOGO_SERVICIO,
                ComponentesHtml.crearCategoriasCatalogoServicio(categorias, productos),
                TOKEN_CONTROLES_PRODUCTO_CATALOGO, productos.isEmpty() ? "disabled" : "",
                TOKEN_BOTON_PEDIDO_DESHABILITADO,
                ocupadas == 0 || productos.isEmpty() ? "disabled" : "",
                TOKEN_AVISO_SIN_HABITACIONES_OCUPADAS, ocupadas == 0 ? "" : "hidden",
                TOKEN_AVISO_SIN_PRODUCTOS_SERVICIO, productos.isEmpty() ? "" : "hidden",
                TOKEN_PEDIDOS_SERVICIO, ComponentesHtml.crearPedidosServicio(pedidos),
                TOKEN_FILTRO_FECHA_PEDIDO,
                filtroSeguro.fecha() == null ? "" : filtroSeguro.fecha().toString(),
                TOKEN_FILTRO_HORA_PEDIDO,
                filtroSeguro.hora() == null ? "" : filtroSeguro.hora().toString(),
                TOKEN_FILTRO_HABITACION_PEDIDO,
                filtroSeguro.numeroHabitacion() == null
                        ? "" : filtroSeguro.numeroHabitacion().toString(),
                TOKEN_RESUMEN_FILTRO_PEDIDOS,
                filtroSeguro.activo()
                        ? pedidos.size() + (pedidos.size() == 1
                                ? " pedido encontrado."
                                : " pedidos encontrados.")
                        : "Mostrando todo el historial de pedidos.",
                TOKEN_CLASE_MODAL_CATALOGO,
                estadoSeguro.esErrorCatalogo() ? "modal-catalogo--abierto" : "",
                TOKEN_CLASE_ERROR_SERVICIO, estadoSeguro.claseCss,
                TOKEN_MENSAJE_ERROR_SERVICIO,
                ComponentesHtml.escaparHtml(estadoSeguro.mensaje));
    }

    public String paginaReportes(Hotel hotel, ReporteIngresos reporte) {
        Objects.requireNonNull(hotel, "hotel");
        Objects.requireNonNull(reporte, "reporte");
        PeriodoReporte periodo = reporte.filtro().periodo();
        TipoIngresoReporte tipo = reporte.filtro().tipoIngreso();
        var indicadores = reporte.indicadores();
        int habitacionesOperativas = Math.max(
                0,
                indicadores.habitacionesTotal()
                        - indicadores.habitacionesMantenimiento());
        return completar(
                reportes,
                TOKEN_NOMBRE_HOTEL, ComponentesHtml.escaparHtml(hotel.nombre()),
                TOKEN_PERIODO_DIA, seleccionado(periodo == PeriodoReporte.DIA),
                TOKEN_PERIODO_SEMANA, seleccionado(periodo == PeriodoReporte.SEMANA),
                TOKEN_PERIODO_MES, seleccionado(periodo == PeriodoReporte.MES),
                TOKEN_PERIODO_ANIO, seleccionado(periodo == PeriodoReporte.ANIO),
                TOKEN_PERIODO_PERSONALIZADO,
                seleccionado(periodo == PeriodoReporte.PERSONALIZADO),
                TOKEN_TIPO_TODOS, seleccionado(tipo == TipoIngresoReporte.TODOS),
                TOKEN_TIPO_HABITACIONES,
                seleccionado(tipo == TipoIngresoReporte.HABITACIONES),
                TOKEN_TIPO_SERVICIOS, seleccionado(tipo == TipoIngresoReporte.SERVICIOS),
                TOKEN_REPORTE_DESDE, reporte.filtro().desde().toString(),
                TOKEN_REPORTE_HASTA, reporte.filtro().hasta().toString(),
                TOKEN_RANGO_REPORTE,
                ComponentesHtml.rangoReporte(reporte.filtro()),
                TOKEN_TOTAL_REPORTE, ComponentesHtml.precioCanonico(reporte.totalGeneral()),
                TOKEN_TOTAL_HABITACIONES_REPORTE,
                ComponentesHtml.precioCanonico(reporte.totalHabitaciones()),
                TOKEN_TOTAL_SERVICIOS_REPORTE,
                ComponentesHtml.precioCanonico(reporte.totalServicios()),
                TOKEN_MOVIMIENTOS_REPORTE,
                Integer.toString(indicadores.movimientos()),
                TOKEN_TASA_OCUPACION,
                indicadores.tasaOcupacion().toPlainString(),
                TOKEN_HABITACIONES_TOTAL,
                Integer.toString(indicadores.habitacionesTotal()),
                TOKEN_HABITACIONES_OPERATIVAS,
                Integer.toString(habitacionesOperativas),
                TOKEN_HABITACIONES_OCUPADAS,
                Integer.toString(indicadores.habitacionesOcupadas()),
                TOKEN_HABITACIONES_DISPONIBLES,
                Integer.toString(indicadores.habitacionesDisponibles()),
                TOKEN_HABITACIONES_LIMPIEZA,
                Integer.toString(indicadores.habitacionesLimpieza()),
                TOKEN_HABITACIONES_MANTENIMIENTO,
                Integer.toString(indicadores.habitacionesMantenimiento()),
                TOKEN_REVPAR_REPORTE,
                ComponentesHtml.precioCanonico(
                        indicadores.ingresoPorHabitacionDisponible()),
                TOKEN_TARIFA_PROMEDIO_REPORTE,
                ComponentesHtml.precioCanonico(indicadores.tarifaPromedio()),
                TOKEN_ESTANCIAS_PAGADAS_REPORTE,
                Integer.toString(indicadores.estanciasPagadas()),
                TOKEN_TICKET_PROMEDIO_SERVICIO,
                ComponentesHtml.precioCanonico(indicadores.ticketPromedioServicio()),
                TOKEN_PEDIDOS_ENTREGADOS_REPORTE,
                Integer.toString(indicadores.pedidosEntregados()),
                TOKEN_MONTO_PENDIENTE_COBRO,
                ComponentesHtml.precioCanonico(indicadores.montoPendienteCobro()),
                TOKEN_PAGOS_PENDIENTES,
                Integer.toString(indicadores.pagosPendientes()),
                TOKEN_PEDIDOS_ACTIVOS,
                Integer.toString(indicadores.pedidosActivos()),
                TOKEN_PARTICIPACION_HABITACIONES,
                indicadores.participacionHabitaciones().toPlainString(),
                TOKEN_PARTICIPACION_SERVICIOS,
                indicadores.participacionServicios().toPlainString(),
                TOKEN_GRAFICO_INGRESOS,
                ComponentesHtml.crearGraficoIngresos(reporte.periodos()),
                TOKEN_RANKING_HABITACIONES,
                ComponentesHtml.crearRankingHabitaciones(reporte.rankingHabitaciones()),
                TOKEN_FILAS_RESUMEN_REPORTE,
                ComponentesHtml.crearFilasResumenReporte(reporte.periodos()),
                TOKEN_DETALLES_REPORTE,
                ComponentesHtml.crearDetallesReporte(reporte.detalles()));
    }

    public String paginaAreasComunes(
            Hotel hotel,
            List<AreaComun> areas,
            EstadoAreasComunes estado,
            String areaSeleccionada,
            String entrada,
            String salida,
            int duracionHoras,
            List<DatosHuesped> visitantes) {
        Objects.requireNonNull(hotel, "hotel");
        Objects.requireNonNull(areas, "areas");
        EstadoAreasComunes estadoSeguro = estado == null
                ? EstadoAreasComunes.INICIAL
                : estado;
        List<DatosHuesped> visitantesSeguros = visitantes;
        if (visitantesSeguros == null || visitantesSeguros.isEmpty()) {
            visitantesSeguros = java.util.Collections.nCopies(
                    GestorHuespedesHoteles.HUESPEDES_PREDERMINADOS,
                    new DatosHuesped("", TipoDocumento.DNI, ""));
        }
        String areaSegura = areaSeleccionada == null ? "" : areaSeleccionada;
        return completar(
                areasComunes,
                TOKEN_NOMBRE_HOTEL, ComponentesHtml.escaparHtml(hotel.nombre()),
                TOKEN_TARJETAS_AREAS_COMUNES,
                ComponentesHtml.crearTarjetasAreasComunes(areas, areaSegura),
                TOKEN_AREA_COMUN_SELECCIONADA,
                ComponentesHtml.escaparAtributo(areaSegura),
                TOKEN_ENTRADA_AREA_COMUN,
                ComponentesHtml.escaparAtributo(entrada),
                TOKEN_SALIDA_AREA_COMUN,
                ComponentesHtml.escaparAtributo(salida),
                TOKEN_DURACION_UNA_HORA, duracionHoras == 1 ? "checked" : "",
                TOKEN_DURACION_TRES_HORAS, duracionHoras == 3 ? "checked" : "",
                TOKEN_DURACION_CINCO_HORAS, duracionHoras == 5 ? "checked" : "",
                TOKEN_CANTIDAD_HUESPEDES, Integer.toString(visitantesSeguros.size()),
                TOKEN_FILAS_HUESPEDES,
                ComponentesHtml.crearFilasHuespedes(visitantesSeguros),
                TOKEN_CLASE_ERROR_AREA, estadoSeguro.claseCss,
                TOKEN_MENSAJE_ERROR_AREA,
                ComponentesHtml.escaparHtml(estadoSeguro.mensaje));
    }

    public String paginaConfiguracionHotel(
            Hotel hotel,
            ResumenInventario resumen,
            EstadoConfiguracionHotel estado) {
        return paginaConfiguracionHotel(
                hotel, resumen, estado, EstadoCambioLlave.INICIAL);
    }

    public String paginaConfiguracionHotel(
            Hotel hotel,
            ResumenInventario resumen,
            EstadoConfiguracionHotel estado,
            EstadoCambioLlave estadoLlave) {
        Objects.requireNonNull(hotel, "hotel");
        Objects.requireNonNull(resumen, "resumen");
        EstadoConfiguracionHotel estadoSeguro = estado == null
                ? EstadoConfiguracionHotel.INICIAL
                : estado;
        EstadoCambioLlave estadoLlaveSeguro = estadoLlave == null
                ? EstadoCambioLlave.INICIAL
                : estadoLlave;
        return completar(
                configuracionHotel,
                TOKEN_NOMBRE_HOTEL,
                ComponentesHtml.escaparHtml(hotel.nombre()),
                TOKEN_CANTIDAD_HABITACIONES,
                Integer.toString(resumen.habitaciones().size()),
                TOKEN_TARJETAS_SECCIONES_TARIFARIAS,
                ComponentesHtml.crearTarjetasSecciones(
                        resumen.seccionesTarifarias(), resumen.habitaciones()),
                TOKEN_CANTIDAD_SECCIONES_TARIFARIAS,
                Integer.toString(resumen.seccionesTarifarias().size()),
                TOKEN_FORMULARIO_CAMBIO_LLAVE_CONFIGURACION,
                formularioCambioLlave(
                        "/hotel/configuracion/llave", estadoLlaveSeguro, false),
                TOKEN_MENSAJE_ERROR_CONFIGURACION,
                ComponentesHtml.escaparHtml(estadoSeguro.mensaje));
    }

    private String formularioCambioLlave(
            String accion, EstadoCambioLlave estado, boolean mostrarCancelar) {
        return completar(
                formularioCambioLlave,
                TOKEN_ACCION_CAMBIO_LLAVE,
                ComponentesHtml.escaparAtributo(accion),
                TOKEN_ACCION_CANCELAR_CAMBIO_LLAVE,
                mostrarCancelar
                        ? "<button class=\"boton boton--secundario\" type=\"button\" "
                                + "data-cerrar-cambio-llave>Cancelar</button>"
                        : "",
                TOKEN_MENSAJE_ERROR_CAMBIO_LLAVE,
                ComponentesHtml.escaparHtml(estado.mensaje));
    }

    private static String cargar(String ruta) {
        try (InputStream entrada = PlantillasWeb.class.getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IllegalStateException(
                        "No se encontró el recurso web: " + ruta);
            }
            String html = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            return java.util.regex.Pattern.compile("/publico/(?:estilos|scripts)/[a-z-]+\\.(?:css|js)")
                    .matcher(html).replaceAll(coincidencia -> {
                        String recurso = coincidencia.group();
                        try (InputStream archivo = PlantillasWeb.class.getResourceAsStream(recurso)) {
                            if (archivo == null) {
                                throw new IllegalStateException("No se encontró el recurso: " + recurso);
                            }
                            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(archivo.readAllBytes());
                            return recurso + "?v=" + java.util.HexFormat.of().formatHex(hash, 0, 6);
                        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
                            throw new IllegalStateException("No fue posible versionar: " + recurso, exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No fue posible leer el recurso web: " + ruta, exception);
        }
    }

    private static String seleccionado(boolean condicion) {
        return condicion ? "selected" : "";
    }

    private static List<String> categoriasDesdeProductos(
            List<ProductoServicio> productos) {
        return productos.stream()
                .map(ProductoServicio::categoria)
                .distinct()
                .toList();
    }

    private static void exigirTokens(
            String plantilla, String nombre, String... tokens) {
        for (String token : tokens) {
            if (!plantilla.contains(token)) {
                throw new IllegalStateException(
                        "La plantilla de " + nombre + " no contiene " + token);
            }
        }
    }

    private static String completar(String plantilla, String... sustituciones) {
        if (sustituciones.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Cada token debe tener un valor de sustitución.");
        }
        String resultado = plantilla;
        for (int indice = 0; indice < sustituciones.length; indice += 2) {
            resultado = resultado.replace(
                    sustituciones[indice], sustituciones[indice + 1]);
        }
        return resultado;
    }

    public enum EstadoAcceso {
        INICIAL("oculto", ""),
        LLAVE_AUSENTE("", "Ingresa tu llave de acceso."),
        LLAVE_INVALIDA("", "La llave de acceso no es válida."),
        DEMASIADOS_INTENTOS("", "Demasiados intentos. Espera 30 segundos y vuelve a intentarlo."),
        ERROR_TECNICO("", "No fue posible validar la llave. Inténtalo nuevamente.");

        private final String claseCss;
        private final String mensaje;

        EstadoAcceso(String claseCss, String mensaje) {
            this.claseCss = claseCss;
            this.mensaje = mensaje;
        }
    }

    public enum EstadoHotel {
        INICIAL("", ""),
        LLAVE_AUSENTE("modal-hotel--abierto", "Ingresa la llave de acceso del hotel."),
        LLAVE_INVALIDA("modal-hotel--abierto", "La llave de acceso del hotel no es válida."),
        HOTEL_INVALIDO("", "El hotel seleccionado no es válido."),
        DEMASIADOS_INTENTOS(
                "modal-hotel--abierto",
                "Demasiados intentos. Espera 30 segundos y vuelve a intentarlo.");

        private final String claseCss;
        private final String mensaje;

        EstadoHotel(String claseCss, String mensaje) {
            this.claseCss = claseCss;
            this.mensaje = mensaje;
        }
    }

    public enum EstadoCambioLlave {
        INICIAL("", ""),
        LLAVE_AUSENTE(
                "modal-cambio-llave--abierto",
                "Ingresa la nueva llave de acceso."),
        LONGITUD_INVALIDA(
                "modal-cambio-llave--abierto",
                "La nueva llave debe tener entre 8 y 15 caracteres."),
        SIN_MAYUSCULA(
                "modal-cambio-llave--abierto",
                "La nueva llave debe incluir al menos una letra mayúscula."),
        SIN_NUMERO(
                "modal-cambio-llave--abierto",
                "La nueva llave debe incluir al menos un número."),
        CONFIRMACION_AUSENTE(
                "modal-cambio-llave--abierto",
                "Confirma la nueva llave de acceso."),
        CONFIRMACION_DISTINTA(
                "modal-cambio-llave--abierto",
                "Las llaves ingresadas no coinciden."),
        HOTEL_DESCONOCIDO(
                "modal-cambio-llave--abierto",
                "No fue posible actualizar la llave del hotel."),
        VERSION_DESACTUALIZADA(
                "modal-cambio-llave--abierto",
                "La llave cambió desde otra sesión. Vuelve a ingresar al hotel.");

        private final String claseCss;
        private final String mensaje;

        EstadoCambioLlave(String claseCss, String mensaje) {
            this.claseCss = claseCss;
            this.mensaje = mensaje;
        }
    }

    public enum ModoHabitaciones {
        NORMAL(
                "normal",
                "Habitaciones",
                "Administra la tarifa, el estado, la estadía y el pago de cada cuarto."),
        LIMPIEZA(
                "limpieza",
                "Limpieza de habitaciones",
                "Consulta y actualiza los cuartos pendientes o en proceso de limpieza.");

        private final String codigo;
        private final String titulo;
        private final String descripcion;

        ModoHabitaciones(String codigo, String titulo, String descripcion) {
            this.codigo = codigo;
            this.titulo = titulo;
            this.descripcion = descripcion;
        }
    }

    public enum EstadoHabitaciones {
        INICIAL("", ""),
        HABITACION_DESCONOCIDA(
                "modal-habitacion--abierto",
                "La habitación seleccionada no existe."),
        SECCION_DESCONOCIDA(
                "modal-habitacion--abierto",
                "Selecciona una sección tarifaria registrada."),
        TIPO_SERVICIO_INVALIDO(
                "modal-habitacion--abierto",
                "Selecciona el servicio de 6 horas o día completo."),
        ESTADO_INVALIDO(
                "modal-habitacion--abierto",
                "Selecciona un estado válido."),
        HORARIO_CHECKOUT_INVALIDO(
                "modal-habitacion--abierto",
                "Ingresa una hora de check-out válida, igual o posterior al check-in."),
        HUESPED_INVALIDO(
                "modal-habitacion--abierto",
                "Completa el nombre y un documento válido para cada huésped."),
        DOCUMENTO_HUESPED_DUPLICADO(
                "modal-habitacion--abierto",
                "No puedes registrar dos veces el mismo documento en la habitación."),
        HOTEL_DESCONOCIDO(
                "", "No fue posible consultar las habitaciones del hotel.");

        private final String claseModal;
        private final String mensajeHabitacion;

        EstadoHabitaciones(String claseModal, String mensajeHabitacion) {
            this.claseModal = claseModal;
            this.mensajeHabitacion = mensajeHabitacion;
        }
    }

    public enum EstadoServicioHabitacion {
        INICIAL("", ""),
        HABITACION_NO_OCUPADA(
                "mensaje-error--visible",
                "El servicio al cuarto sólo puede asignarse a una habitación ocupada."),
        PRODUCTO_INVALIDO("mensaje-error--visible", "Selecciona un producto del catálogo."),
        PRODUCTO_CATALOGO_INVALIDO(
                "mensaje-error--visible", "El producto seleccionado ya no existe."),
        PRODUCTO_DUPLICADO(
                "mensaje-error--visible", "Ya existe un producto con ese nombre."),
        CANTIDAD_INVALIDA(
                "mensaje-error--visible", "La cantidad debe estar entre 1 y 10 unidades."),
        OBSERVACIONES_INVALIDAS(
                "mensaje-error--visible", "Las observaciones admiten hasta 300 caracteres."),
        NOMBRE_PRODUCTO_INVALIDO(
                "mensaje-error--visible",
                "El nombre del producto es obligatorio y admite hasta 120 caracteres."),
        CATEGORIA_PRODUCTO_INVALIDA(
                "mensaje-error--visible",
                "La categoría es obligatoria y admite hasta 60 caracteres."),
        CATEGORIA_DESCONOCIDA(
                "mensaje-error--visible", "La categoría seleccionada ya no existe."),
        CATEGORIA_DUPLICADA(
                "mensaje-error--visible", "Ya existe una categoría con ese nombre."),
        CATEGORIA_EN_USO(
                "mensaje-error--visible",
                "Reasigna o quita sus productos antes de eliminar la categoría."),
        ULTIMA_CATEGORIA(
                "mensaje-error--visible", "El catálogo debe conservar al menos una categoría."),
        PRECIO_PRODUCTO_INVALIDO(
                "mensaje-error--visible",
                "El precio debe estar entre S/ 0.01 y S/ 10000.00."),
        PEDIDO_DESCONOCIDO("mensaje-error--visible", "El pedido seleccionado no existe."),
        TRANSICION_INVALIDA(
                "mensaje-error--visible", "El pedido cambió de estado o ya fue finalizado."),
        SOLICITUD_INVALIDA("mensaje-error--visible", "La solicitud de servicio no es válida.");

        private final String claseCss;
        private final String mensaje;

        EstadoServicioHabitacion(String claseCss, String mensaje) {
            this.claseCss = claseCss;
            this.mensaje = mensaje;
        }

        private boolean esErrorCatalogo() {
            return this == PRODUCTO_CATALOGO_INVALIDO
                    || this == PRODUCTO_DUPLICADO
                    || this == NOMBRE_PRODUCTO_INVALIDO
                    || this == CATEGORIA_PRODUCTO_INVALIDA
                    || this == CATEGORIA_DESCONOCIDA
                    || this == CATEGORIA_DUPLICADA
                    || this == CATEGORIA_EN_USO
                    || this == ULTIMA_CATEGORIA
                    || this == PRECIO_PRODUCTO_INVALIDO;
        }
    }

    public enum EstadoAreasComunes {
        INICIAL("", ""),
        AREA_INVALIDA("mensaje-error--visible", "Selecciona una de las tres áreas comunes."),
        DURACION_INVALIDA(
                "mensaje-error--visible",
                "Selecciona una duración de 1, 3 o 5 horas."),
        HORARIO_INVALIDO(
                "mensaje-error--visible",
                "Selecciona una hora de entrada válida."),
        VISITANTE_INVALIDO(
                "mensaje-error--visible",
                "Completa el nombre y un documento válido para cada visitante."),
        DOCUMENTO_DUPLICADO(
                "mensaje-error--visible",
                "No puedes registrar dos veces el mismo documento.");

        private final String claseCss;
        private final String mensaje;

        EstadoAreasComunes(String claseCss, String mensaje) {
            this.claseCss = claseCss;
            this.mensaje = mensaje;
        }
    }

    public enum EstadoConfiguracionHotel {
        INICIAL(""),
        CANTIDAD_INVALIDA("La cantidad debe estar entre 1 y 50 habitaciones."),
        REDUCCION_NO_PERMITIDA(
                "No puedes eliminar habitaciones ocupadas, en limpieza o mantenimiento."),
        SECCION_DESCONOCIDA("La sección tarifaria seleccionada no existe."),
        LIMITE_SECCIONES("Puedes registrar como máximo 12 secciones tarifarias."),
        ULTIMA_SECCION("El hotel debe conservar al menos una sección tarifaria."),
        SECCION_EN_USO(
                "No puedes eliminar una sección asignada. Reasigna primero sus habitaciones."),
        COLOR_INVALIDO("Selecciona un color válido."),
        COLOR_DUPLICADO("Ya existe una sección con ese color."),
        NOMBRE_INVALIDO("El nombre debe tener como máximo 40 caracteres válidos."),
        NOMBRE_DUPLICADO("Ya existe una sección con ese nombre."),
        PRECIO_INVALIDO(
                "Ambos precios deben estar entre S/ 1.00 y S/ 10000.00, con máximo dos decimales."),
        HOTEL_DESCONOCIDO("No fue posible configurar las habitaciones de este hotel.");

        private final String mensaje;

        EstadoConfiguracionHotel(String mensaje) {
            this.mensaje = mensaje;
        }
    }
}
