package com.gestera.infraestructura.servidor;

import com.gestera.modelo.areas.GestorAreasComunes;
import com.gestera.repositorios.areas.RepositorioAreasComunesJdbc;
import com.gestera.repositorios.habitaciones.RepositorioInventarioJdbc;
import com.gestera.repositorios.hoteles.RepositorioCredencialesHotelJdbc;
import com.gestera.repositorios.servicios.RepositorioCatalogoServicioJdbc;
import com.gestera.infraestructura.persistencia.TransaccionesHotel;
import java.time.Clock;
import java.time.ZoneId;
import com.gestera.seguridad.autenticacion.AutenticadorLlave;
import com.gestera.seguridad.autenticacion.LimitadorIntentos;
import com.gestera.infraestructura.configuracion.ConfiguracionAplicacion;
import com.gestera.controladores.ControladorAreasComunes;
import com.gestera.controladores.ControladorConfiguracionHotel;
import com.gestera.controladores.ControladorHabitaciones;
import com.gestera.controladores.ControladorHuespedes;
import com.gestera.controladores.ControladorReportes;
import com.gestera.controladores.ControladorServicioHabitacion;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.ResumenInventario;
import com.gestera.modelo.hoteles.CatalogoHoteles;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.gestera.modelo.hoteles.GestorLlavesHoteles;
import com.gestera.modelo.hoteles.GestorLlavesHoteles.ResultadoAcceso;
import com.gestera.modelo.hoteles.GestorLlavesHoteles.ResultadoCambio;
import com.gestera.modelo.huespedes.GestorHuespedesHoteles;
import com.gestera.repositorios.huespedes.RepositorioHuespedes;
import com.gestera.repositorios.huespedes.RepositorioHuespedesJdbc;
import com.gestera.repositorios.huespedes.RepositorioHuespedesMemoria;
import com.gestera.modelo.reportes.GestorReportes;
import com.gestera.repositorios.reportes.RepositorioIngresosHabitacion;
import com.gestera.repositorios.reportes.RepositorioIngresosHabitacionJdbc;
import com.gestera.repositorios.reportes.RepositorioIngresosHabitacionMemoria;
import com.gestera.modelo.servicios.GestorServicioHabitacion;
import com.gestera.repositorios.servicios.RepositorioServicioHabitacion;
import com.gestera.repositorios.servicios.RepositorioServicioHabitacionJdbc;
import com.gestera.repositorios.servicios.RepositorioServicioHabitacionMemoria;
import com.gestera.seguridad.sesiones.GestorSesiones;
import com.gestera.seguridad.sesiones.GestorSesiones.AutorizacionHotel;
import com.gestera.vistas.PlantillasWeb;
import com.gestera.vistas.RecursosWeb;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Servidor HTTP autocontenido del módulo web de acceso. */
public final class ServidorGestera implements AutoCloseable {
    public static final String NOMBRE_COOKIE = "GESTERA_SESION";
    private static final Logger LOGGER = LoggerFactory.getLogger(ServidorGestera.class);

    private final HttpServer servidor;
    private final ExecutorService ejecutor;
    private final AutenticadorLlave autenticador;
    private final GestorLlavesHoteles llavesHoteles;
    private final GestorHabitacionesHoteles habitacionesHoteles;
    private final GestorHuespedesHoteles huespedesHoteles;
    private final GestorServicioHabitacion servicioHabitacion;
    private final GestorReportes reportes;
    private final GestorAreasComunes areasComunes;
    private final LimitadorIntentos limitadorIntentos;
    private final LimitadorIntentos limitadorIntentosHotel;
    private final GestorSesiones sesiones;
    private final CatalogoHoteles catalogoHoteles;
    private final PlantillasWeb plantillas;
    private final RecursosWeb recursos;
    private final ControladorHuespedes controladorHuespedes;
    private final ControladorAreasComunes controladorAreasComunes;
    private final ControladorReportes controladorReportes;
    private final ControladorServicioHabitacion controladorServicioHabitacion;
    private final ControladorConfiguracionHotel controladorConfiguracionHotel;
    private final ControladorHabitaciones controladorHabitaciones;
    private final Duration duracionSesion;
    private final boolean cookieSegura;
    private final AtomicBoolean iniciado = new AtomicBoolean();
    private final AtomicBoolean cerrado = new AtomicBoolean();

    public ServidorGestera(
            InetSocketAddress direccion,
            char[] llaveAcceso,
            Duration duracionSesion,
            boolean cookieSegura) throws IOException {
        this(
                direccion,
                llaveAcceso,
                ConfiguracionAplicacion.copiarLlaveHotelPredeterminada(),
                duracionSesion,
                cookieSegura);
    }

    public ServidorGestera(
            InetSocketAddress direccion,
            char[] llaveAcceso,
            char[] llaveHotel,
            Duration duracionSesion,
            boolean cookieSegura) throws IOException {
        this(
                direccion,
                llaveAcceso,
                llaveHotel,
                duracionSesion,
                cookieSegura,
                new RepositorioHuespedesMemoria(),
                new RepositorioServicioHabitacionMemoria(),
                new RepositorioIngresosHabitacionMemoria(),null);
    }

    private ServidorGestera(
            InetSocketAddress direccion,
            char[] llaveAcceso,
            char[] llaveHotel,
            Duration duracionSesion,
            boolean cookieSegura,
            RepositorioHuespedes repositorioHuespedes,
            RepositorioServicioHabitacion repositorioServicio,
            RepositorioIngresosHabitacion repositorioIngresos,DataSource fuente) throws IOException {
        AutenticadorLlave autenticadorLocal = null;
        GestorLlavesHoteles llavesHotelesLocales = null;
        GestorHabitacionesHoteles habitacionesLocales = null;
        GestorHuespedesHoteles huespedesLocales = null;
        GestorServicioHabitacion servicioLocal = null;
        GestorReportes reportesLocal = null;
        LimitadorIntentos limitadorLocal = null;
        LimitadorIntentos limitadorHotelLocal = null;
        GestorSesiones sesionesLocales = null;
        HttpServer servidorLocal = null;
        ExecutorService ejecutorLocal = null;

        try {
            Objects.requireNonNull(direccion, "direccion");
            this.duracionSesion = Objects.requireNonNull(duracionSesion, "duracionSesion");
            this.cookieSegura = cookieSegura;

            CatalogoHoteles catalogoLocal = fuente==null ? new CatalogoHoteles() : new CatalogoHoteles(fuente);
            habitacionesLocales = new GestorHabitacionesHoteles(catalogoLocal.todos(),Clock.system(ZoneId.of("America/Lima")),
                    fuente==null ? null : new RepositorioInventarioJdbc(fuente));
            huespedesLocales = new GestorHuespedesHoteles(repositorioHuespedes);
            GestorAreasComunes areasComunesLocales =
                    new GestorAreasComunes(huespedesLocales,Clock.system(ZoneId.of("America/Lima")),
                            fuente==null ? null : new RepositorioAreasComunesJdbc(fuente));
            servicioLocal = new GestorServicioHabitacion(repositorioServicio,Clock.system(ZoneId.of("America/Lima")),
                    fuente==null ? null : new RepositorioCatalogoServicioJdbc(fuente));
            reportesLocal = new GestorReportes(repositorioIngresos);
            PlantillasWeb plantillasLocales = new PlantillasWeb();
            RecursosWeb recursosLocales = new RecursosWeb();
            servidorLocal = HttpServer.create(direccion, 0);
            AtomicInteger consecutivoHilos = new AtomicInteger();
            ejecutorLocal = new ThreadPoolExecutor(
                    4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), tarea -> {
                Thread hilo = new Thread(
                        tarea, "gestera-http-" + consecutivoHilos.incrementAndGet());
                hilo.setDaemon(false);
                return hilo;
            }, new ThreadPoolExecutor.CallerRunsPolicy());

            autenticadorLocal = new AutenticadorLlave(llaveAcceso);
            llavesHotelesLocales = new GestorLlavesHoteles(
                    catalogoLocal.todos(), llaveHotel,fuente==null ? null : new RepositorioCredencialesHotelJdbc(fuente));
            limitadorLocal = new LimitadorIntentos(5, Duration.ofSeconds(30));
            limitadorHotelLocal = new LimitadorIntentos(5, Duration.ofSeconds(30));
            sesionesLocales = new GestorSesiones(duracionSesion);

            autenticador = autenticadorLocal;
            llavesHoteles = llavesHotelesLocales;
            habitacionesHoteles = habitacionesLocales;
            huespedesHoteles = huespedesLocales;
            areasComunes = areasComunesLocales;
            servicioHabitacion = servicioLocal;
            reportes = reportesLocal;
            limitadorIntentos = limitadorLocal;
            limitadorIntentosHotel = limitadorHotelLocal;
            sesiones = sesionesLocales;
            catalogoHoteles = catalogoLocal;
            plantillas = plantillasLocales;
            recursos = recursosLocales;
            controladorHuespedes = new ControladorHuespedes(
                    huespedesHoteles,
                    habitacionesHoteles,
                    plantillas,
                    this::hotelAutorizadoParaControlador);
            controladorAreasComunes = new ControladorAreasComunes(
                    areasComunes,
                    plantillas,
                    this::hotelAutorizadoParaControlador);
            controladorReportes = new ControladorReportes(
                    reportes,
                    servicioHabitacion,
                    habitacionesHoteles,
                    plantillas,
                    this::hotelAutorizadoParaControlador);
            controladorServicioHabitacion = new ControladorServicioHabitacion(
                    servicioHabitacion,
                    habitacionesHoteles,
                    plantillas,
                    this::hotelAutorizadoParaControlador);
            controladorConfiguracionHotel = new ControladorConfiguracionHotel(
                    habitacionesHoteles,
                    plantillas,
                    this::hotelAutorizadoParaControlador);
            controladorHabitaciones = new ControladorHabitaciones(
                    habitacionesHoteles,
                    huespedesHoteles,
                    reportes,
                    plantillas,
                    this::hotelAutorizadoParaControlador,fuente==null ? null : new TransaccionesHotel(fuente));
            servidor = servidorLocal;
            ejecutor = ejecutorLocal;
            servidor.setExecutor(ejecutor);
            registrarRutas();
        } catch (IOException | RuntimeException | Error exception) {
            if (servidorLocal != null) {
                servidorLocal.stop(0);
            }
            if (ejecutorLocal != null) {
                ejecutorLocal.shutdownNow();
            }
            if (sesionesLocales != null) {
                sesionesLocales.close();
            }
            if (limitadorHotelLocal != null) {
                limitadorHotelLocal.close();
            }
            if (limitadorLocal != null) {
                limitadorLocal.close();
            }
            if (llavesHotelesLocales != null) {
                llavesHotelesLocales.close();
            }
            if (habitacionesLocales != null) {
                habitacionesLocales.close();
            }
            if (huespedesLocales != null) {
                huespedesLocales.close();
            } else {
                repositorioHuespedes.close();
            }
            if (servicioLocal != null) {
                servicioLocal.close();
            } else {
                repositorioServicio.close();
            }
            if (reportesLocal != null) {
                reportesLocal.close();
            } else {
                repositorioIngresos.close();
            }
            if (autenticadorLocal != null) {
                autenticadorLocal.close();
            }
            throw exception;
        } finally {
            borrar(llaveAcceso);
            borrar(llaveHotel);
        }
    }

    public static ServidorGestera crear(ConfiguracionAplicacion configuracion, DataSource fuente)
            throws IOException {
        Objects.requireNonNull(configuracion, "configuracion");
        char[] llaveAcceso = null;
        char[] llaveHotel = null;
        try {
            llaveAcceso = configuracion.copiarLlaveAcceso();
            llaveHotel = configuracion.copiarLlaveHotel();
            CatalogoHoteles catalogo = fuente==null ? new CatalogoHoteles() : new CatalogoHoteles(fuente);
            RepositorioHuespedes repositorio = fuente == null
                    ? new RepositorioHuespedesMemoria()
                    : new RepositorioHuespedesJdbc(fuente, catalogo.todos());
            RepositorioServicioHabitacion repositorioServicio = fuente == null
                    ? new RepositorioServicioHabitacionMemoria()
                    : new RepositorioServicioHabitacionJdbc(fuente);
            RepositorioIngresosHabitacion repositorioIngresos = fuente == null
                    ? new RepositorioIngresosHabitacionMemoria()
                    : new RepositorioIngresosHabitacionJdbc(fuente);
            return new ServidorGestera(
                    new InetSocketAddress(configuracion.direccion(), configuracion.puerto()),
                    llaveAcceso,
                    llaveHotel,
                    configuracion.duracionSesion(),
                    configuracion.cookieSegura(),
                    repositorio,
                    repositorioServicio,
                    repositorioIngresos,fuente);
        } finally {
            borrar(llaveAcceso);
            borrar(llaveHotel);
        }
    }

    public void iniciar() {
        if (cerrado.get()) {
            throw new IllegalStateException("El servidor ya fue cerrado.");
        }
        if (iniciado.compareAndSet(false, true)) {
            servidor.start();
            LOGGER.info("Gestera está disponible en {}", direccionBase());
        }
    }

    public URI direccionBase() {
        InetSocketAddress direccion = servidor.getAddress();
        String host = direccion.getAddress() == null
                ? direccion.getHostString()
                : direccion.getAddress().getHostAddress();
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        return URI.create("http://" + host + ":" + direccion.getPort());
    }

    @Override
    public void close() {
        if (cerrado.compareAndSet(false, true)) {
            servidor.stop(0);
            ejecutor.shutdownNow();
            limitadorIntentos.close();
            limitadorIntentosHotel.close();
            sesiones.close();
            autenticador.close();
            llavesHoteles.close();
            habitacionesHoteles.close();
            huespedesHoteles.close();
            servicioHabitacion.close();
            reportes.close();
            LOGGER.info("Servidor Gestera detenido.");
        }
    }

    private void registrarRutas() {
        registrar(RutasGestera.INICIO, "GET", this::mostrarAcceso);
        registrar(RutasGestera.ACCESO, "POST", this::procesarAcceso);
        registrar(RutasGestera.PANEL, "GET", this::mostrarPanel);
        registrar(RutasGestera.ACCESO_HOTEL, "POST", this::procesarAccesoHotel);
        registrar(
                RutasGestera.LLAVE_HOTEL,
                "POST",
                this::procesarCambioLlaveHotel);
        registrar(
                RutasGestera.LLAVE_HOTEL_CONFIGURACION,
                "POST",
                this::procesarCambioLlaveDesdeConfiguracion);
        registrar(
                RutasGestera.GUARDAR_CONFIGURACION,
                "POST",
                controladorConfiguracionHotel::guardarTodo);
        registrar(
                RutasGestera.CONFIGURAR_HABITACIONES,
                "POST",
                controladorConfiguracionHotel::configurarCantidad);
        registrar(
                RutasGestera.GUARDAR_SECCION,
                "POST",
                controladorConfiguracionHotel::guardarSeccion);
        registrar(
                RutasGestera.ELIMINAR_SECCION,
                "POST",
                controladorConfiguracionHotel::eliminarSeccion);
        registrar(
                RutasGestera.CONFIGURACION_HOTEL,
                "GET",
                controladorConfiguracionHotel::mostrar);
        registrar(
                RutasGestera.ACTUALIZAR_HABITACION,
                "POST",
                controladorHabitaciones::actualizar);
        registrar(
                RutasGestera.CHECKOUT_HABITACION,
                "POST",
                controladorHabitaciones::registrarCheckOut);
        registrar(
                RutasGestera.HABITACIONES,
                "GET",
                controladorHabitaciones::mostrarHabitaciones);
        registrar(RutasGestera.HUESPEDES, "GET", controladorHuespedes::mostrar);
        registrar(RutasGestera.AREAS_COMUNES, "GET", controladorAreasComunes::mostrar);
        registrar(
                RutasGestera.REGISTRAR_AREA_COMUN,
                "POST",
                controladorAreasComunes::registrar);
        registrar(
                RutasGestera.SERVICIO_HABITACION,
                "GET",
                controladorServicioHabitacion::mostrar);
        registrar(RutasGestera.REPORTES, "GET", controladorReportes::mostrar);
        registrar(
                RutasGestera.CREAR_PEDIDO_SERVICIO,
                "POST",
                controladorServicioHabitacion::crearPedido);
        registrar(
                RutasGestera.ACTUALIZAR_CATALOGO_SERVICIO,
                "POST",
                controladorServicioHabitacion::actualizarCatalogo);
        registrar(
                RutasGestera.AGREGAR_PRODUCTO_SERVICIO,
                "POST",
                controladorServicioHabitacion::agregarProducto);
        registrar(
                RutasGestera.ELIMINAR_PRODUCTO_SERVICIO,
                "POST",
                controladorServicioHabitacion::eliminarProducto);
        registrar(
                RutasGestera.AGREGAR_CATEGORIA_SERVICIO,
                "POST",
                controladorServicioHabitacion::agregarCategoria);
        registrar(
                RutasGestera.ELIMINAR_CATEGORIA_SERVICIO,
                "POST",
                controladorServicioHabitacion::eliminarCategoria);
        registrar(
                RutasGestera.CAMBIAR_ESTADO_PEDIDO_SERVICIO,
                "POST",
                controladorServicioHabitacion::cambiarEstadoPedido);
        registrar(RutasGestera.LIMPIEZA, "GET", controladorHabitaciones::mostrarLimpieza);
        registrar(RutasGestera.HOTEL, "GET", this::mostrarGestionHotel);
        registrar(RutasGestera.SALIR, "POST", this::cerrarSesion);
        servidor.createContext(
                RutasGestera.RECURSOS_PUBLICOS, protegido(this::servirRecurso));
    }

    private void registrar(String ruta, String metodo, AccionHttp accion) {
        servidor.createContext(ruta, protegido(intercambio -> {
            if (rutaYMetodo(intercambio, ruta, metodo)) {
                accion.ejecutar(intercambio);
            }
        }));
    }

    private HttpHandler protegido(AccionHttp accion) {
        return intercambio -> {
            RespuestasHttp.prepararSeguridad(intercambio);
            try {
                accion.ejecutar(intercambio);
            } catch (SolicitudHttpException exception) {
                RespuestasHttp.texto(intercambio, exception.estado(), exception.getMessage());
            } catch (Exception exception) {
                LOGGER.error("Error HTTP procesando {} {}",
                        intercambio.getRequestMethod(),
                        intercambio.getRequestURI().getPath(),
                        exception);
                try {
                    RespuestasHttp.texto(intercambio, 500, "Error interno del servicio.");
                } catch (IOException ignored) {
                    // La conexión ya puede estar cerrada.
                }
            } finally {
                intercambio.close();
            }
        };
    }

    private void mostrarAcceso(HttpExchange intercambio) throws IOException {
        if (sesionValida(intercambio)) {
            RespuestasHttp.redirigir(intercambio, RutasGestera.PANEL);
            return;
        }
        RespuestasHttp.html(intercambio, 200,
                plantillas.paginaAcceso(PlantillasWeb.EstadoAcceso.INICIAL));
    }

    private void procesarAcceso(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {

        String origen = origen(intercambio);
        if (!limitadorIntentos.permite(origen)) {
            RespuestasHttp.html(intercambio, 429,
                    plantillas.paginaAcceso(PlantillasWeb.EstadoAcceso.DEMASIADOS_INTENTOS));
            return;
        }

        AutenticadorLlave.Resultado resultado =
                autenticador.autenticar(SolicitudesHttp.leerLlave(intercambio));
        switch (resultado) {
            case CONCEDIDO -> {
                limitadorIntentos.registrarExito(origen);
                concederAcceso(intercambio);
            }
            case AUSENTE -> RespuestasHttp.html(intercambio, 400,
                    plantillas.paginaAcceso(PlantillasWeb.EstadoAcceso.LLAVE_AUSENTE));
            case DENEGADO -> {
                limitadorIntentos.registrarFallo(origen);
                RespuestasHttp.html(intercambio, 401,
                        plantillas.paginaAcceso(PlantillasWeb.EstadoAcceso.LLAVE_INVALIDA));
            }
        }
    }

    private void concederAcceso(HttpExchange intercambio) throws IOException {
        String token = sesiones.crear();
        intercambio.getResponseHeaders().add("Set-Cookie", cookieSesion(token));
        RespuestasHttp.redirigir(intercambio, RutasGestera.PANEL);
    }

    private void mostrarPanel(HttpExchange intercambio) throws IOException {
        if (!sesionValida(intercambio)) {
            intercambio.getResponseHeaders().add("Set-Cookie", cookieVencida());
            RespuestasHttp.redirigir(intercambio, RutasGestera.INICIO);
            return;
        }
        responderPanel(
                intercambio, 200, PlantillasWeb.EstadoHotel.INICIAL, null);
    }

    private void procesarAccesoHotel(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {

        String token = tokenSesion(intercambio);
        if (!sesiones.esValida(token)) {
            intercambio.getResponseHeaders().add("Set-Cookie", cookieVencida());
            RespuestasHttp.redirigir(intercambio, RutasGestera.INICIO);
            return;
        }

        try (SolicitudesHttp.AccesoHotel solicitud =
                SolicitudesHttp.leerAccesoHotel(intercambio)) {
            Hotel hotel = catalogoHoteles.buscarPorCodigo(solicitud.codigoHotel()).orElse(null);
            if (hotel == null) {
                RespuestasHttp.texto(
                        intercambio, 400, "El hotel seleccionado no es válido.");
                return;
            }

            String origen = origen(intercambio);
            if (!limitadorIntentosHotel.permite(origen)) {
                responderPanel(
                        intercambio,
                        429,
                        PlantillasWeb.EstadoHotel.DEMASIADOS_INTENTOS,
                        hotel);
                return;
            }

            ResultadoAcceso acceso =
                    llavesHoteles.autenticarConEstado(hotel.codigo(), solicitud.llave());
            switch (acceso.resultado()) {
                case CONCEDIDO -> {
                    if (sesiones.autorizarHotel(
                            token, hotel.codigo(), acceso.version())) {
                        limitadorIntentosHotel.registrarExito(origen);
                        RespuestasHttp.redirigir(intercambio, RutasGestera.HOTEL);
                    } else {
                        intercambio.getResponseHeaders().add("Set-Cookie", cookieVencida());
                        RespuestasHttp.redirigir(intercambio, RutasGestera.INICIO);
                    }
                }
                case AUSENTE -> responderPanel(
                        intercambio, 400, PlantillasWeb.EstadoHotel.LLAVE_AUSENTE, hotel);
                case DENEGADO -> {
                    limitadorIntentosHotel.registrarFallo(origen);
                    responderPanel(
                            intercambio, 401, PlantillasWeb.EstadoHotel.LLAVE_INVALIDA, hotel);
                }
            }
        }
    }

    private void mostrarGestionHotel(HttpExchange intercambio) throws IOException {

        AccesoHotelSesion acceso = accesoHotelAutorizado(intercambio);
        if (acceso == null) {
            return;
        }
        responderGestion(
                intercambio,
                200,
                acceso.hotel(),
                PlantillasWeb.EstadoCambioLlave.INICIAL);
    }

    private void procesarCambioLlaveHotel(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        procesarCambioLlaveHotel(intercambio, false);
    }

    private void procesarCambioLlaveDesdeConfiguracion(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        procesarCambioLlaveHotel(intercambio, true);
    }

    private void procesarCambioLlaveHotel(
            HttpExchange intercambio, boolean desdeConfiguracion)
            throws IOException, SolicitudHttpException {

        AccesoHotelSesion acceso = accesoHotelAutorizado(intercambio);
        if (acceso == null) {
            return;
        }
        Hotel hotel = acceso.hotel();

        try (SolicitudesHttp.CambioLlaveHotel solicitud =
                SolicitudesHttp.leerCambioLlaveHotel(intercambio)) {
            ResultadoCambio resultado = llavesHoteles.cambiar(
                    hotel.codigo(),
                    acceso.autorizacion().versionLlave(),
                    solicitud.nuevaLlave(),
                    solicitud.confirmacionLlave());
            if (resultado == ResultadoCambio.ACTUALIZADA) {
                long nuevaVersion = llavesHoteles.versionActual(hotel.codigo());
                if (sesiones.autorizarHotel(
                        acceso.token(), hotel.codigo(), nuevaVersion)) {
                    RespuestasHttp.redirigir(
                            intercambio,
                            desdeConfiguracion
                                    ? RutasGestera.CONFIGURACION_HOTEL
                                    : RutasGestera.HOTEL);
                } else {
                    intercambio.getResponseHeaders().add("Set-Cookie", cookieVencida());
                    RespuestasHttp.redirigir(intercambio, RutasGestera.INICIO);
                }
                return;
            }
            if (resultado == ResultadoCambio.VERSION_DESACTUALIZADA) {
                sesiones.revocarHotel(acceso.token());
                RespuestasHttp.redirigir(intercambio, RutasGestera.PANEL);
                return;
            }
            if (desdeConfiguracion) {
                responderConfiguracion(
                        intercambio,
                        400,
                        hotel,
                        PlantillasWeb.EstadoConfiguracionHotel.INICIAL,
                        estadoCambioLlave(resultado));
            } else {
                responderGestion(
                        intercambio, 400, hotel, estadoCambioLlave(resultado));
            }
        }
    }

    private void responderGestion(
            HttpExchange intercambio,
            int estadoHttp,
            Hotel hotel,
            PlantillasWeb.EstadoCambioLlave estado) throws IOException {
        RespuestasHttp.html(
                intercambio,
                estadoHttp,
                plantillas.paginaGestion(
                        hotel, llavesHoteles.requiereCambio(hotel.codigo()), estado));
    }

    private void responderConfiguracion(
            HttpExchange intercambio,
            int estadoHttp,
            Hotel hotel,
            PlantillasWeb.EstadoConfiguracionHotel estado) throws IOException {
        responderConfiguracion(
                intercambio,
                estadoHttp,
                hotel,
                estado,
                PlantillasWeb.EstadoCambioLlave.INICIAL);
    }

    private void responderConfiguracion(
            HttpExchange intercambio,
            int estadoHttp,
            Hotel hotel,
            PlantillasWeb.EstadoConfiguracionHotel estado,
            PlantillasWeb.EstadoCambioLlave estadoLlave) throws IOException {
        ResumenInventario resumen = habitacionesHoteles.resumen(hotel.codigo());
        RespuestasHttp.html(
                intercambio,
                estadoHttp,
                plantillas.paginaConfiguracionHotel(hotel, resumen, estado, estadoLlave));
    }

    private AccesoHotelSesion accesoHotelVigente(String token) {
        AutorizacionHotel autorizacion = sesiones.autorizacionHotel(token).orElse(null);
        if (autorizacion == null) {
            return null;
        }
        Hotel hotel = catalogoHoteles.buscarPorCodigo(autorizacion.codigoHotel()).orElse(null);
        if (hotel == null || !llavesHoteles.esVersionActual(
                hotel.codigo(), autorizacion.versionLlave())) {
            sesiones.revocarHotel(token);
            return null;
        }
        return new AccesoHotelSesion(token, hotel, autorizacion);
    }

    private AccesoHotelSesion accesoHotelAutorizado(HttpExchange intercambio)
            throws IOException {
        String token = tokenSesion(intercambio);
        AccesoHotelSesion acceso = accesoHotelVigente(token);
        if (acceso == null) {
            redirigirSinAccesoHotel(intercambio, token);
        }
        return acceso;
    }

    private Hotel hotelAutorizadoParaControlador(HttpExchange intercambio)
            throws IOException {
        AccesoHotelSesion acceso = accesoHotelAutorizado(intercambio);
        return acceso == null ? null : acceso.hotel();
    }

    private void redirigirSinAccesoHotel(HttpExchange intercambio, String token)
            throws IOException {
        if (sesiones.esValida(token)) {
            RespuestasHttp.redirigir(intercambio, RutasGestera.PANEL);
        } else {
            intercambio.getResponseHeaders().add("Set-Cookie", cookieVencida());
            RespuestasHttp.redirigir(intercambio, RutasGestera.INICIO);
        }
    }

    private void responderPanel(
            HttpExchange intercambio,
            int estadoHttp,
            PlantillasWeb.EstadoHotel estado,
            Hotel hotelSeleccionado) throws IOException {
        RespuestasHttp.html(
                intercambio,
                estadoHttp,
                plantillas.paginaPanel(
                        catalogoHoteles.todos(), estado, hotelSeleccionado));
    }

    private void cerrarSesion(HttpExchange intercambio) throws IOException {
        sesiones.cerrar(tokenSesion(intercambio));
        intercambio.getResponseHeaders().add("Set-Cookie", cookieVencida());
        RespuestasHttp.redirigir(intercambio, RutasGestera.INICIO);
    }

    private void servirRecurso(HttpExchange intercambio) throws IOException {
        if (!metodoPermitido(intercambio, "GET")) {
            return;
        }
        RecursosWeb.Recurso recurso = recursos.buscar(intercambio.getRequestURI().getPath())
                .orElse(null);
        if (recurso == null) {
            RespuestasHttp.noEncontrado(intercambio);
            return;
        }
        RespuestasHttp.recurso(
                intercambio, recurso.tipoContenido(), recurso.contenido());
    }

    private static boolean rutaYMetodo(
            HttpExchange intercambio, String ruta, String metodo) throws IOException {
        if (!intercambio.getRequestURI().getPath().equals(ruta)) {
            RespuestasHttp.noEncontrado(intercambio);
            return false;
        }
        return metodoPermitido(intercambio, metodo);
    }

    private static boolean metodoPermitido(
            HttpExchange intercambio, String permitido) throws IOException {
        if (!intercambio.getRequestMethod().equalsIgnoreCase(permitido)) {
            RespuestasHttp.metodoNoPermitido(intercambio, permitido);
            return false;
        }
        return true;
    }

    private static PlantillasWeb.EstadoCambioLlave estadoCambioLlave(
            ResultadoCambio resultado) {
        return switch (resultado) {
            case LLAVE_AUSENTE -> PlantillasWeb.EstadoCambioLlave.LLAVE_AUSENTE;
            case LONGITUD_INVALIDA -> PlantillasWeb.EstadoCambioLlave.LONGITUD_INVALIDA;
            case SIN_MAYUSCULA -> PlantillasWeb.EstadoCambioLlave.SIN_MAYUSCULA;
            case SIN_NUMERO -> PlantillasWeb.EstadoCambioLlave.SIN_NUMERO;
            case CONFIRMACION_AUSENTE ->
                    PlantillasWeb.EstadoCambioLlave.CONFIRMACION_AUSENTE;
            case CONFIRMACION_DISTINTA ->
                    PlantillasWeb.EstadoCambioLlave.CONFIRMACION_DISTINTA;
            case HOTEL_DESCONOCIDO ->
                    PlantillasWeb.EstadoCambioLlave.HOTEL_DESCONOCIDO;
            case VERSION_DESACTUALIZADA ->
                    PlantillasWeb.EstadoCambioLlave.VERSION_DESACTUALIZADA;
            case ACTUALIZADA -> PlantillasWeb.EstadoCambioLlave.INICIAL;
        };
    }

    private boolean sesionValida(HttpExchange intercambio) {
        return sesiones.esValida(tokenSesion(intercambio));
    }

    private String tokenSesion(HttpExchange intercambio) {
        return SolicitudesHttp.leerCookie(intercambio, NOMBRE_COOKIE);
    }

    private String cookieSesion(String token) {
        return NOMBRE_COOKIE + "=" + token
                + "; Path=/; Max-Age=" + duracionSesion.toSeconds()
                + "; HttpOnly; SameSite=Strict"
                + (cookieSegura ? "; Secure" : "");
    }

    private String cookieVencida() {
        return NOMBRE_COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict"
                + (cookieSegura ? "; Secure" : "");
    }

    private static String origen(HttpExchange intercambio) {
        InetSocketAddress remoto = intercambio.getRemoteAddress();
        return remoto.getAddress() == null
                ? remoto.getHostString()
                : remoto.getAddress().getHostAddress();
    }

    private static void borrar(char[] valor) {
        if (valor != null) {
            Arrays.fill(valor, '\0');
        }
    }

    private record AccesoHotelSesion(
            String token, Hotel hotel, AutorizacionHotel autorizacion) {
    }

    @FunctionalInterface
    private interface AccionHttp {
        void ejecutar(HttpExchange intercambio) throws Exception;
    }
}
