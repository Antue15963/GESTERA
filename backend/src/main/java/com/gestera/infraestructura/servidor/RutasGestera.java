package com.gestera.infraestructura.servidor;

/** Rutas HTTP estables expuestas por Gestera. */
public final class RutasGestera {
    public static final String INICIO = "/";
    public static final String ACCESO = "/acceso";
    public static final String PANEL = "/panel";
    public static final String HOTEL = "/hotel";
    public static final String ACCESO_HOTEL = "/hotel/acceso";
    public static final String LLAVE_HOTEL = "/hotel/llave";
    public static final String LLAVE_HOTEL_CONFIGURACION = "/hotel/configuracion/llave";
    public static final String CONFIGURACION_HOTEL = "/hotel/configuracion";
    public static final String GUARDAR_CONFIGURACION = "/hotel/configuracion/guardar";
    public static final String CONFIGURAR_HABITACIONES =
            "/hotel/configuracion/habitaciones";
    public static final String GUARDAR_SECCION =
            "/hotel/configuracion/secciones/guardar";
    public static final String ELIMINAR_SECCION =
            "/hotel/configuracion/secciones/eliminar";
    public static final String HABITACIONES = "/hotel/habitaciones";
    public static final String HUESPEDES = "/hotel/huespedes";
    public static final String AREAS_COMUNES = "/hotel/areas-comunes";
    public static final String REGISTRAR_AREA_COMUN = "/hotel/areas-comunes/registrar";
    public static final String SERVICIO_HABITACION = "/hotel/servicio-habitacion";
    public static final String REPORTES = "/hotel/reportes";
    public static final String CREAR_PEDIDO_SERVICIO = "/hotel/servicio-habitacion/pedidos";
    public static final String ACTUALIZAR_CATALOGO_SERVICIO =
            "/hotel/servicio-habitacion/catalogo";
    public static final String AGREGAR_PRODUCTO_SERVICIO =
            "/hotel/servicio-habitacion/catalogo/productos";
    public static final String ELIMINAR_PRODUCTO_SERVICIO =
            "/hotel/servicio-habitacion/catalogo/productos/eliminar";
    public static final String AGREGAR_CATEGORIA_SERVICIO =
            "/hotel/servicio-habitacion/catalogo/categorias";
    public static final String ELIMINAR_CATEGORIA_SERVICIO =
            "/hotel/servicio-habitacion/catalogo/categorias/eliminar";
    public static final String CAMBIAR_ESTADO_PEDIDO_SERVICIO =
            "/hotel/servicio-habitacion/pedidos/estado";
    public static final String ACTUALIZAR_HABITACION = "/hotel/habitaciones/actualizar";
    public static final String CHECKOUT_HABITACION = "/hotel/habitaciones/checkout";
    public static final String LIMPIEZA = "/hotel/limpieza";
    public static final String SALIR = "/salir";
    public static final String RECURSOS_PUBLICOS = "/publico/";

    private RutasGestera() {
    }
}
