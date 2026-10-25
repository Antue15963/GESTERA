package com.gestera.vistas;

import com.gestera.areas.GestorAreasComunes;
import com.gestera.hoteles.CatalogoHoteles;
import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.habitaciones.GestorHabitacionesHoteles;
import com.gestera.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.SeccionTarifaria;
import com.gestera.habitaciones.GestorHabitacionesHoteles.TipoServicio;
import com.gestera.pruebas.Aserciones;
import com.gestera.servicios.EstadoPedidoServicio;
import com.gestera.servicios.LineaPedidoServicio;
import com.gestera.servicios.PedidoServicio;
import com.gestera.servicios.ProductoServicio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Verifica el contenido funcional de las paginas y sus recursos separados. */
public final class PlantillasWebPrueba {
    private static final String LLAVE_GLOBAL = "123456789abc";
    private static final String LLAVE_HOTEL = "gesteraperu123";

    private PlantillasWebPrueba() {
    }

    public static int ejecutar() {
        presentaElAccesoSolicitado();
        contieneUnSoloFormularioDeLlave();
        noExponeCredencialesNiLaLlavePredeterminada();
        representaEstadosDeAccesoControlados();
        presentaElDirectorioDeCuatroHoteles();
        preparaElDialogoDeAccesoAlHotel();
        representaEstadosDeHotelControlados();
        preparaLaGestionDelHotel();
        preparaLasAreasComunes();
        preparaElServicioAlCuarto();
        actualizaElEstadoVisualDespuesDelCambio();
        representaErroresDeCambioControlados();
        preparaElInventarioDeHabitaciones();
        exponeElVencimientoDeCheckOutComoInstanteSeguro();
        preparaLaConfiguracionSeparada();
        preparaLaVistaFiltradaDeLimpieza();
        representaErroresDeHabitacionesControlados();
        representaErroresDeConfiguracionControlados();
        escapaElHotelEnElModuloDeHabitaciones();
        escapaLosDatosRenderizados();
        cargaRecursosPublicosDeFormaDefensiva();
        versionaLosRecursosDelTemaAntesDelCss();
        return 22;
    }

    private static void versionaLosRecursosDelTemaAntesDelCss() {
        String pagina = plantillas().paginaAcceso(PlantillasWeb.EstadoAcceso.INICIAL);
        RecursosWeb recursos = new RecursosWeb();
        for (String ruta : List.of("/publico/scripts/tema.js", "/publico/estilos/gestera.css")) {
            try {
                byte[] contenido = recursos.buscar(ruta).orElseThrow().contenido();
                String version = java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(contenido), 0, 6);
                Aserciones.contiene(pagina, ruta + "?v=" + version,
                        "El navegador debe recibir una URL que cambia con el contenido del recurso");
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new AssertionError(exception);
            }
        }
        Aserciones.verdadero(pagina.indexOf("/publico/scripts/tema.js")
                        < pagina.indexOf("/publico/estilos/gestera.css"),
                "El tema debe aplicarse antes de cargar el CSS");
        Aserciones.contiene(pagina, "data-alternar-tema", "El acceso debe incluir el selector global");
        Aserciones.verdadero(recursos.buscar("/publico/imagenes/hotel-costa.png").isPresent(),
                "La imagen debe estar publicada por el servidor real");
    }

    private static void presentaElAccesoSolicitado() {
        String pagina = plantillas().paginaAcceso(PlantillasWeb.EstadoAcceso.INICIAL);
        Aserciones.contiene(pagina, "<html lang=\"es\">",
                "La pagina debe declarar el idioma espanol");
        Aserciones.contiene(pagina, "LLAVE DE ACCESO", "Debe mostrar el titulo solicitado");
        Aserciones.contiene(pagina, "Bienvenido a Gestera", "Debe dar la bienvenida a Gestera");
        Aserciones.contiene(
                pagina,
                "Introduce tu llave de acceso al servicio.",
                "Debe explicar claramente que dato se solicita");
        Aserciones.contiene(
                pagina,
                "Control inteligente de tu hotel.",
                "Debe conservar el enfoque de gestion hotelera de la referencia");
    }

    private static void contieneUnSoloFormularioDeLlave() {
        String pagina = plantillas().paginaAcceso(PlantillasWeb.EstadoAcceso.INICIAL);
        Aserciones.contiene(
                pagina,
                "action=\"/acceso\" method=\"post\"",
                "La llave debe enviarse por POST al endpoint de acceso");
        Aserciones.iguales(1, contar(pagina, "name=\"llave\""),
                "Debe existir exactamente un campo de llave");
        Aserciones.contiene(pagina, "type=\"password\"", "La llave debe ocultarse al escribirla");
        Aserciones.contiene(pagina, "required autofocus",
                "El campo debe ser obligatorio y recibir foco");
    }

    private static void noExponeCredencialesNiLaLlavePredeterminada() {
        String pagina = plantillas().paginaAcceso(PlantillasWeb.EstadoAcceso.INICIAL);
        Aserciones.noContieneIgnorandoMayusculas(
                pagina, "usuario", "No debe existir ningun campo o texto de usuario");
        Aserciones.noContieneIgnorandoMayusculas(
                pagina, "contrase\u00f1a", "No debe pedir una contrasena convencional");
        Aserciones.noContieneIgnorandoMayusculas(
                pagina, "credenciales de prueba", "No debe mostrar credenciales de prueba");
        comprobarQueNoExponeLlaves(pagina, "La pagina de acceso");
    }

    private static void representaEstadosDeAccesoControlados() {
        PlantillasWeb plantillas = plantillas();
        String inicial = plantillas.paginaAcceso(null);
        String ausente = plantillas.paginaAcceso(PlantillasWeb.EstadoAcceso.LLAVE_AUSENTE);
        String invalida = plantillas.paginaAcceso(PlantillasWeb.EstadoAcceso.LLAVE_INVALIDA);
        Aserciones.contiene(inicial, "mensaje-error oculto",
                "El estado inicial debe ocultar el error");
        Aserciones.contiene(ausente, "Ingresa tu llave de acceso.",
                "Debe informar una llave ausente");
        Aserciones.contiene(invalida, "La llave de acceso no es v\u00e1lida.",
                "Debe informar una llave invalida");
        for (String pagina : new String[] {inicial, ausente, invalida}) {
            Aserciones.falso(pagina.contains("{{"),
                    "No deben quedar tokens de plantilla visibles en el acceso");
            comprobarQueNoExponeLlaves(pagina, "Un estado del acceso");
        }
    }

    private static void presentaElDirectorioDeCuatroHoteles() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        String panel = plantillas().paginaPanel(
                catalogo.todos(), PlantillasWeb.EstadoHotel.INICIAL, null);
        Aserciones.contiene(panel, "Elige el hotel que deseas gestionar.",
                "El panel debe explicar su funcion de selector");
        Aserciones.iguales(4, contar(panel, "class=\"tarjeta-hotel\""),
                "Debe renderizar exactamente cuatro tarjetas de hotel");
        for (Hotel hotel : catalogo.todos()) {
            Aserciones.contiene(panel, hotel.nombre(),
                    "Cada nombre registrado debe mostrarse en una tarjeta");
            Aserciones.contiene(panel, "data-hotel-codigo=\"" + hotel.codigo() + "\"",
                    "Cada tarjeta debe conservar su codigo controlado");
        }
        Aserciones.contiene(panel, "id=\"buscar-hotel\" type=\"search\"",
                "El directorio debe incluir busqueda por nombre");
        Aserciones.contiene(panel, "id=\"sin-resultados\" hidden",
                "Debe preparar un estado vacio para busquedas sin coincidencias");
        Aserciones.contiene(panel, "/publico/scripts/hoteles.js",
                "La pagina debe cargar el filtro de hoteles");
        comprobarQueNoExponeLlaves(panel, "El directorio de hoteles");
    }

    private static void preparaElDialogoDeAccesoAlHotel() {
        String panel = plantillas().paginaPanel();
        Aserciones.contiene(panel, "<dialog", "El acceso al hotel debe abrirse en un dialogo");
        Aserciones.contiene(
                panel,
                "action=\"/hotel/acceso\" method=\"post\"",
                "El dialogo debe enviar el acceso del hotel por POST");
        Aserciones.iguales(1, contar(panel, "name=\"hotel\""),
                "Debe enviar un unico codigo de hotel");
        Aserciones.iguales(1, contar(panel, "name=\"llaveHotel\""),
                "Debe solicitar una unica llave del hotel");
        Aserciones.contiene(panel, "name=\"hotel\" type=\"hidden\"",
                "El codigo seleccionado debe viajar en un campo oculto");
        Aserciones.contiene(panel, "name=\"llaveHotel\" type=\"password\"",
                "La llave del hotel debe permanecer oculta al escribirla");
        Aserciones.contiene(panel, "action=\"/salir\" method=\"post\"",
                "El cierre de sesion debe seguir usando POST");
        comprobarQueNoExponeLlaves(panel, "El formulario del hotel");
    }

    private static void representaEstadosDeHotelControlados() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        Hotel hotel = catalogo.buscarPorCodigo("sol-pucusana").orElseThrow();
        PlantillasWeb plantillas = plantillas();
        String ausente = plantillas.paginaPanel(
                catalogo.todos(), PlantillasWeb.EstadoHotel.LLAVE_AUSENTE, hotel);
        String invalida = plantillas.paginaPanel(
                catalogo.todos(), PlantillasWeb.EstadoHotel.LLAVE_INVALIDA, hotel);
        String desconocido = plantillas.paginaPanel(
                catalogo.todos(), PlantillasWeb.EstadoHotel.HOTEL_INVALIDO, null);
        String bloqueado = plantillas.paginaPanel(
                catalogo.todos(), PlantillasWeb.EstadoHotel.DEMASIADOS_INTENTOS, hotel);

        Aserciones.contiene(ausente, "Ingresa la llave de acceso del hotel.",
                "Debe explicar que falta la llave del hotel");
        Aserciones.contiene(invalida, "La llave de acceso del hotel no es v\u00e1lida.",
                "Debe mostrar un error generico para una llave incorrecta");
        Aserciones.contiene(desconocido, "El hotel seleccionado no es v\u00e1lido.",
                "Debe rechazar codigos que no pertenecen al catalogo");
        Aserciones.contiene(bloqueado, "Demasiados intentos.",
                "Debe informar el bloqueo temporal");
        for (String pagina : new String[] {ausente, invalida, desconocido, bloqueado}) {
            Aserciones.falso(pagina.contains("{{"),
                    "No deben quedar tokens de plantilla en los errores del hotel");
            comprobarQueNoExponeLlaves(pagina, "Un estado de acceso al hotel");
        }
    }

    private static void preparaLaGestionDelHotel() {
        Hotel hotel = new CatalogoHoteles().buscarPorCodigo("sol-pucusana").orElseThrow();
        String gestion = plantillas().paginaGestion(
                hotel, true, PlantillasWeb.EstadoCambioLlave.INICIAL);
        Aserciones.contiene(gestion, hotel.nombre(),
                "La gestion debe identificar el hotel autorizado");
        for (String opcion : new String[] {
            "Limpieza de habitaciones", "Habitaciones", "Hu\u00e9spedes",
            "Servicio al cuarto", "Áreas comunes"
        }) {
            Aserciones.contiene(gestion, opcion,
                    "La gestion debe anticipar la opcion: " + opcion);
        }
        Aserciones.noContieneIgnorandoMayusculas(
                gestion, "Reservas", "El modulo de Reservas ya no debe mostrarse");
        Aserciones.contiene(gestion, "href=\"/hotel/limpieza\"",
                "Limpieza debe abrir su vista filtrada");
        Aserciones.contiene(gestion, "href=\"/hotel/habitaciones\"",
                "Habitaciones debe abrir el inventario completo");
        Aserciones.noContieneIgnorandoMayusculas(
                gestion, "Entradas y salidas", "El apartado retirado no debe mostrarse");
        Aserciones.contiene(gestion, "href=\"/hotel/configuracion\"",
                "La configuracion debe estar disponible desde el primer panel del hotel");
        Aserciones.contiene(gestion, "aria-label=\"Abrir configuración del hotel\"",
                "La gestion general debe mostrar la tuerquita de configuracion");
        Aserciones.contiene(gestion, "href=\"/hotel/huespedes\"",
                "Huespedes debe abrir el registro consolidado");
        Aserciones.contiene(gestion, "href=\"/hotel/servicio-habitacion\"",
                "Servicio al cuarto debe abrir su módulo operativo");
        Aserciones.contiene(gestion, "href=\"/hotel/areas-comunes\"",
                "Áreas comunes debe abrir su servicio independiente");
        Aserciones.contiene(gestion, "href=\"/panel\"", "Debe permitir cambiar de hotel");
        Aserciones.contiene(gestion, "action=\"/salir\" method=\"post\"",
                "La gestion debe permitir cerrar la sesion por POST");
        Aserciones.contiene(gestion, "id=\"abrir-cambio-llave\"",
                "La opcion de cambio de llave debe permanecer accesible");
        Aserciones.contiene(gestion, "Cambio de llave de acceso",
                "La gestion debe identificar claramente la nueva opcion");
        Aserciones.contiene(gestion, "opcion-cambio-llave--requerida",
                "Antes del primer cambio la opcion debe estar destacada");
        Aserciones.contiene(gestion, "Configuraci\u00f3n requerida",
                "El estado inicial debe exigir configurar una llave propia");
        Aserciones.contiene(
                gestion,
                "action=\"/hotel/llave\" method=\"post\"",
                "El cambio de llave debe enviarse por POST");
        Aserciones.iguales(1, contar(gestion, "name=\"nuevaLlave\""),
                "Debe solicitar una sola llave nueva");
        Aserciones.iguales(1, contar(gestion, "name=\"confirmacionLlave\""),
                "Debe solicitar una sola confirmacion");
        Aserciones.contiene(gestion, "name=\"nuevaLlave\" type=\"password\"",
                "La nueva llave no debe mostrarse al escribir");
        Aserciones.contiene(gestion, "name=\"confirmacionLlave\" type=\"password\"",
                "La confirmacion tampoco debe mostrarse");
        Aserciones.contiene(gestion, "Entre 8 y 15 caracteres",
                "Debe explicar el limite de longitud");
        Aserciones.contiene(gestion, "letra may\u00fascula",
                "Debe explicar la regla de mayuscula");
        Aserciones.contiene(gestion, "un n\u00famero",
                "Debe explicar la regla numerica");
        Aserciones.contiene(gestion, "/publico/scripts/gestion.js",
                "La gestion debe cargar las mejoras del formulario");
        Aserciones.falso(gestion.contains("{{"),
                "La gestion no debe dejar tokens de plantilla visibles");
        comprobarQueNoExponeLlaves(gestion, "La gestion del hotel");
    }

    private static void preparaLasAreasComunes() {
        Hotel hotel = new CatalogoHoteles().buscarPorCodigo("sol-pucusana").orElseThrow();
        GestorAreasComunes gestor = new GestorAreasComunes(
                new com.gestera.huespedes.GestorHuespedesHoteles(
                        new com.gestera.huespedes.RepositorioHuespedesMemoria()));
        String pagina = plantillas().paginaAreasComunes(
                hotel,
                gestor.areas(),
                PlantillasWeb.EstadoAreasComunes.INICIAL,
                "",
                "",
                "",
                1,
                null);
        for (String area : new String[] {"Jacuzzi", "Sauna", "Área de juegos"}) {
            Aserciones.contiene(pagina, area,
                    "La página debe mostrar el área común: " + area);
        }
        Aserciones.contiene(pagina, "action=\"/hotel/areas-comunes/registrar\"",
                "El registro debe usar un endpoint separado");
        Aserciones.contiene(pagina, "name=\"entrada\" type=\"datetime-local\"",
                "Debe permitir seleccionar la hora de entrada");
        for (String duracion : new String[] {"1", "3", "5"}) {
            Aserciones.contiene(pagina,
                    "name=\"duracionHoras\" value=\"" + duracion + "\"",
                    "Debe ofrecer la duración rápida de " + duracion + " horas");
        }
        Aserciones.contiene(pagina, "id=\"salida-area\" type=\"datetime-local\"",
                "Debe mostrar la hora de salida calculada");
        Aserciones.contiene(pagina, "value=\"\" readonly",
                "La hora de salida calculada no debe editarse manualmente");
        Aserciones.contiene(pagina, "Zona horaria de Perú activa",
                "Debe indicar claramente que el horario de Perú está activo");
        Aserciones.contiene(pagina, "America/Lima · UTC-5",
                "Debe mostrar la zona horaria aplicada al formulario");
        Aserciones.contiene(pagina, "name=\"huesped.0.nombre\"",
                "Debe reutilizar el registro de visitantes");
        Aserciones.contiene(pagina, "/publico/scripts/areas-comunes.js",
                "El módulo debe cargar su comportamiento independiente");
        Aserciones.falso(pagina.contains("{{"),
                "Áreas comunes no debe dejar tokens visibles");
    }

    private static void preparaElServicioAlCuarto() {
        Hotel hotel = new CatalogoHoteles().buscarPorCodigo("sol-pucusana").orElseThrow();
        SeccionTarifaria seccion = new SeccionTarifaria(
                "seccion-1", "#123456", "Estándar",
                new BigDecimal("75.00"), new BigDecimal("140.00"));
        LocalDateTime creado = LocalDateTime.of(2026, 9, 21, 12, 30);
        Habitacion ocupada = new Habitacion(
                4, seccion, TipoServicio.SEIS_HORAS, new BigDecimal("75.00"),
                EstadoHabitacion.OCUPADA, creado.minusHours(1), creado.plusHours(5), false);
        ProductoServicio producto = new ProductoServicio(
                "club-sandwich", "Club sándwich", "Comidas", new BigDecimal("32.00"));
        PedidoServicio pedido = new PedidoServicio(
                9,
                hotel.codigo(),
                4,
                EstadoPedidoServicio.PENDIENTE,
                "Sin mayonesa",
                creado,
                creado,
                List.of(new LineaPedidoServicio(
                        producto.codigo(), producto.nombre(), producto.precio(), 2)));
        String pagina = plantillas().paginaServicioHabitacion(
                hotel,
                List.of(ocupada),
                List.of(producto),
                List.of(pedido),
                PlantillasWeb.EstadoServicioHabitacion.INICIAL);
        Aserciones.contiene(pagina, "action=\"/hotel/servicio-habitacion/pedidos\"",
                "El formulario debe crear pedidos mediante POST");
        Aserciones.contiene(pagina, "value=\"4\">Habitación 04",
                "Sólo las habitaciones ocupadas deben estar disponibles");
        Aserciones.contiene(pagina, "data-precio=\"32.00\"",
                "El producto debe exponer su precio para la vista previa");
        Aserciones.contiene(pagina, "data-nombre=\"Club sándwich\"",
                "El editor debe recibir el nombre actual del producto");
        Aserciones.contiene(pagina, "data-abrir-catalogo",
                "El módulo debe mostrar el botón para editar el catálogo");
        Aserciones.contiene(
                pagina, "action=\"/hotel/servicio-habitacion/catalogo\" method=\"post\"",
                "Los cambios del catálogo deben usar un endpoint separado");
        Aserciones.contiene(
                pagina, "action=\"/hotel/servicio-habitacion/catalogo/productos\" method=\"post\"",
                "El catálogo debe permitir agregar productos");
        Aserciones.contiene(
                pagina, "/hotel/servicio-habitacion/catalogo/productos/eliminar",
                "El catálogo debe permitir quitar productos");
        Aserciones.contiene(
                pagina, "action=\"/hotel/servicio-habitacion/catalogo/categorias\" method=\"post\"",
                "El catálogo debe permitir agregar categorías");
        Aserciones.contiene(
                pagina, "/hotel/servicio-habitacion/catalogo/categorias/eliminar",
                "El catálogo debe permitir quitar categorías vacías");
        Aserciones.contiene(pagina, "Pedido #9",
                "La cola debe renderizar pedidos existentes");
        Aserciones.contiene(pagina, "Total S/ 64.00",
                "El total renderizado debe provenir del dominio");
        Aserciones.contiene(
                pagina, "action=\"/hotel/servicio-habitacion/pedidos/estado\"",
                "Los estados deben modificarse por un endpoint separado");
        Aserciones.contiene(pagina, "/publico/scripts/servicio-habitacion.js",
                "El módulo debe cargar su comportamiento desacoplado");
        Aserciones.falso(pagina.contains("{{"),
                "Servicio al cuarto no debe dejar tokens visibles");
    }

    private static void actualizaElEstadoVisualDespuesDelCambio() {
        Hotel hotel = new CatalogoHoteles().buscarPorCodigo("sol-pucusana").orElseThrow();
        String gestion = plantillas().paginaGestion(
                hotel, false, PlantillasWeb.EstadoCambioLlave.INICIAL);
        Aserciones.noContieneIgnorandoMayusculas(
                gestion, "id=\"abrir-cambio-llave\"",
                "La opcion debe desaparecer despues del primer cambio");
        Aserciones.noContieneIgnorandoMayusculas(
                gestion, "Cambio de llave de acceso",
                "El panel debe quedar limpio despues de configurar la llave");
        Aserciones.noContieneIgnorandoMayusculas(
                gestion,
                "opcion-cambio-llave--requerida",
                "La opcion debe perder el destacado despues del cambio");
        Aserciones.noContieneIgnorandoMayusculas(
                gestion,
                "Configuraci\u00f3n requerida",
                "El aviso inicial debe desaparecer despues del cambio");
        Aserciones.falso(gestion.contains("{{"),
                "El estado posterior no debe dejar tokens visibles");
        comprobarQueNoExponeLlaves(gestion, "El estado posterior al cambio");
    }

    private static void representaErroresDeCambioControlados() {
        Hotel hotel = new CatalogoHoteles().buscarPorCodigo("sol-pucusana").orElseThrow();
        PlantillasWeb plantillas = plantillas();
        Object[][] casos = {
            {PlantillasWeb.EstadoCambioLlave.LLAVE_AUSENTE,
                    "Ingresa la nueva llave de acceso."},
            {PlantillasWeb.EstadoCambioLlave.LONGITUD_INVALIDA,
                    "La nueva llave debe tener entre 8 y 15 caracteres."},
            {PlantillasWeb.EstadoCambioLlave.SIN_MAYUSCULA,
                    "La nueva llave debe incluir al menos una letra may\u00fascula."},
            {PlantillasWeb.EstadoCambioLlave.SIN_NUMERO,
                    "La nueva llave debe incluir al menos un n\u00famero."},
            {PlantillasWeb.EstadoCambioLlave.CONFIRMACION_AUSENTE,
                    "Confirma la nueva llave de acceso."},
            {PlantillasWeb.EstadoCambioLlave.CONFIRMACION_DISTINTA,
                    "Las llaves ingresadas no coinciden."},
            {PlantillasWeb.EstadoCambioLlave.HOTEL_DESCONOCIDO,
                    "No fue posible actualizar la llave del hotel."},
            {PlantillasWeb.EstadoCambioLlave.VERSION_DESACTUALIZADA,
                    "La llave cambi\u00f3 desde otra sesi\u00f3n."}
        };
        for (Object[] caso : casos) {
            String pagina = plantillas.paginaGestion(
                    hotel, true, (PlantillasWeb.EstadoCambioLlave) caso[0]);
            Aserciones.contiene(pagina, (String) caso[1],
                    "Cada rechazo debe mostrar un mensaje controlado");
            Aserciones.contiene(pagina, "modal-cambio-llave--abierto",
                    "Un rechazo debe reabrir el dialogo de cambio");
            Aserciones.falso(pagina.contains("{{"),
                    "Un rechazo no debe dejar tokens de plantilla");
            comprobarQueNoExponeLlaves(pagina, "Un error de cambio de llave");
        }
    }

    private static void preparaElInventarioDeHabitaciones() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        Hotel hotel = catalogo.buscarPorCodigo("sol-pucusana").orElseThrow();
        try (GestorHabitacionesHoteles gestor =
                new GestorHabitacionesHoteles(catalogo.todos())) {
            var resumen = gestor.resumen(hotel.codigo());
            String pagina = plantillas().paginaHabitaciones(
                    hotel,
                    resumen.habitaciones(),
                    resumen.seccionesTarifarias(),
                    PlantillasWeb.ModoHabitaciones.NORMAL,
                    PlantillasWeb.EstadoHabitaciones.INICIAL,
                    null);
            Aserciones.contiene(pagina, hotel.nombre(),
                    "El inventario debe identificar el hotel autorizado");
            Aserciones.contiene(pagina, "data-modo=\"normal\"",
                    "La vista general debe declarar su modo");
            Aserciones.contiene(pagina, "<h1 id=\"habitaciones-titulo\">Habitaciones</h1>",
                    "La vista general debe usar el titulo de Habitaciones");
            Aserciones.iguales(12, contar(pagina, "class=\"tarjeta-habitacion\""),
                    "Debe renderizar las doce habitaciones iniciales");
            Aserciones.contiene(pagina, "data-habitacion-numero=\"1\"",
                    "Debe renderizar la primera habitacion");
            Aserciones.contiene(pagina, "data-habitacion-numero=\"12\"",
                    "Debe renderizar la duodecima habitacion");
            for (String atributo : new String[] {
                "data-habitacion-seccion", "data-habitacion-seccion-nombre",
                "data-habitacion-tipo-servicio", "data-habitacion-precio",
                "data-habitacion-horas-adicionales",
                "data-habitacion-monto-adicional", "data-habitacion-monto-total",
                "data-habitacion-color", "data-habitacion-estado",
                "data-habitacion-checkin", "data-habitacion-checkout",
                "data-habitacion-checkout-epoch",
                "data-habitacion-pagado", "data-habitacion-huespedes"
            }) {
                Aserciones.contiene(pagina, atributo + "=\"",
                        "Cada tarjeta debe exponer el dato seguro: " + atributo);
            }

            Aserciones.contiene(
                    pagina,
                    "action=\"/hotel/habitaciones/actualizar\" method=\"post\"",
                    "Cada habitacion debe actualizarse mediante POST");
            for (String campo : new String[] {
                "numeroHabitacion", "idSeccion", "tipoServicio",
                "estado", "pagado", "modo", "proximoCheckOut",
                "ajusteManualCheckOut", "horasAdicionales", "montoAdicional"
            }) {
                Aserciones.contiene(pagina, "name=\"" + campo + "\"",
                        "El formulario debe incluir el campo: " + campo);
            }
            Aserciones.contiene(pagina, "name=\"cantidadHuespedes\"",
                    "El formulario debe declarar cuantos huespedes envia");
            Aserciones.contiene(pagina, "name=\"huesped.0.nombre\"",
                    "Debe mostrar el primer huesped predeterminado");
            Aserciones.contiene(pagina, "name=\"huesped.1.nombre\"",
                    "Debe mostrar dos huespedes de forma predeterminada");
            Aserciones.contiene(pagina, "id=\"agregar-huesped\"",
                    "Debe permitir agregar huespedes");
            for (String campoCalculado : new String[] {
                "precio", "checkIn", "montoTotal"
            }) {
                Aserciones.noContieneIgnorandoMayusculas(
                        pagina,
                        "name=\"" + campoCalculado + "\"",
                        "El navegador no debe enviar el valor reservado: " + campoCalculado);
            }
            Aserciones.contiene(pagina, "id=\"ajuste-manual-checkout\"",
                    "El cambio de hora debe requerir un control manual explicito");
            Aserciones.contiene(pagina, "Cambiar hora para pruebas",
                    "La interfaz debe explicar el uso excepcional del ajuste");
            Aserciones.contiene(pagina, "id=\"precio-habitacion\"",
                    "Debe mostrar el precio calculado");
            Aserciones.contiene(pagina, "id=\"contador-habitaciones\"",
                    "Debe mostrar la cantidad de habitaciones filtradas");
            Aserciones.contiene(pagina, "id=\"contador-habitaciones-disponibles\"",
                    "Debe mostrar por separado las habitaciones disponibles");
            Aserciones.contiene(pagina, "id=\"contador-habitaciones-ocupadas\"",
                    "Debe mostrar las habitaciones ocupadas junto a las disponibles");
            Aserciones.contiene(pagina, "class=\"contadores-habitaciones__estados\"",
                    "Disponibles y ocupadas deben permanecer agrupadas al adaptar la vista");
            Aserciones.contiene(
                    pagina,
                    "class=\"contadores-habitaciones\" role=\"status\" aria-live=\"polite\"",
                    "Los contadores deben anunciar juntos los cambios del filtro");
            Aserciones.contiene(pagina, "value=\"seis_horas\"",
                    "Debe ofrecer el servicio de seis horas");
            Aserciones.contiene(pagina, "value=\"dia_completo\"",
                    "Debe ofrecer el servicio de dia completo");
            Aserciones.contiene(pagina, "data-precio-seis-horas=\"50.00\"",
                    "Cada opcion debe exponer su tarifa de seis horas");
            Aserciones.contiene(pagina, "data-precio-dia-completo=\"90.00\"",
                    "Cada opcion debe exponer su tarifa diaria");
            Aserciones.verdadero(
                    contar(pagina, " readonly") >= 3,
                    "Precio y check-in deben permanecer protegidos y el check-out iniciar bloqueado");
            Aserciones.contiene(pagina, "id=\"abrir-alertas-checkout\"",
                    "La vista debe mantener visible la campana de salidas");
            Aserciones.contiene(pagina, "id=\"modal-alertas-checkout\"",
                    "La campana debe abrir el listado de habitaciones proximas");
            for (String fase : new String[] {"10 minutos", "5 minutos", "Hora de salida"}) {
                Aserciones.contiene(pagina, fase,
                        "La leyenda debe explicar la fase: " + fase);
            }
            Aserciones.contiene(
                    pagina,
                    "role=\"status\" aria-live=\"assertive\" aria-atomic=\"true\"",
                    "Las nuevas alertas deben anunciarse con prioridad accesible");
            Aserciones.contiene(pagina, "id=\"modal-checkout\"",
                    "La alerta debe abrir un recuadro de confirmacion");
            Aserciones.contiene(
                    pagina,
                    "action=\"/hotel/habitaciones/checkout\"",
                    "La confirmacion debe usar su endpoint dedicado");
            Aserciones.contiene(
                    pagina,
                    "name=\"confirmado\"",
                    "El formulario debe exigir confirmar que se realizo el check-out");
            Aserciones.contiene(pagina, "Falta registrar el pago",
                    "El recuadro debe contemplar una salida con pago pendiente");
            Aserciones.contiene(pagina, "id=\"ampliacion-estadia\"",
                    "El panel debe incluir un bloque para ampliar una estancia ocupada");
            for (String horas : new String[] {"0", "1", "2", "4"}) {
                Aserciones.contiene(
                        pagina,
                        "name=\"horasAdicionales\" value=\"" + horas + "\"",
                        "El panel debe ofrecer exactamente las horas adicionales permitidas");
            }
            Aserciones.contiene(
                    pagina,
                    "name=\"montoAdicional\"",
                    "El cargo de la ampliacion debe enviarse junto con el panel actual");
            Aserciones.contiene(
                    pagina,
                    "min=\"0.01\" max=\"10000.00\" step=\"0.01\"",
                    "El monto adicional debe declarar los limites visibles del servidor");
            Aserciones.contiene(pagina, "id=\"total-ampliacion\"",
                    "El panel debe anticipar el total base, acumulado y nuevo");
            Aserciones.contiene(pagina, "id=\"monto-total-checkout\"",
                    "La confirmacion de una salida impaga debe reservar el total a cobrar");
            Aserciones.contiene(pagina, "MONTO TOTAL A COBRAR",
                    "El total pendiente debe estar rotulado de forma explicita");
            Aserciones.contiene(pagina, "href=\"/hotel/configuracion\"",
                    "La tuerquita debe abrir la configuracion separada");
            Aserciones.noContieneIgnorandoMayusculas(
                    pagina, "name=\"cantidadHabitaciones\"",
                    "La capacidad ya no debe editarse dentro del inventario");
            Aserciones.contiene(pagina, "/publico/scripts/habitaciones.js",
                    "El modulo debe cargar su JavaScript separado");
            Aserciones.falso(pagina.contains("{{"),
                    "El inventario no debe dejar tokens de plantilla");
            comprobarQueNoExponeLlaves(pagina, "El inventario de habitaciones");
        }
    }

    private static void exponeElVencimientoDeCheckOutComoInstanteSeguro() {
        Hotel hotel = new CatalogoHoteles().buscarPorCodigo("sol-pucusana").orElseThrow();
        SeccionTarifaria seccion = new SeccionTarifaria(
                "seccion-1", "#123456", "Prueba",
                new BigDecimal("75.00"), new BigDecimal("140.00"));
        LocalDateTime salida = LocalDateTime.of(2026, 9, 1, 15, 30);
        Habitacion impaga = new Habitacion(
                7,
                seccion,
                TipoServicio.SEIS_HORAS,
                new BigDecimal("75.00"),
                EstadoHabitacion.OCUPADA,
                salida.minusHours(6),
                salida,
                false,
                7,
                new BigDecimal("37.25"));
        String pagina = plantillas().paginaHabitaciones(
                hotel,
                List.of(impaga),
                List.of(seccion),
                PlantillasWeb.ModoHabitaciones.NORMAL,
                PlantillasWeb.EstadoHabitaciones.INICIAL,
                null);
        String epoch = Long.toString(
                salida.atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli());
        Aserciones.contiene(
                pagina,
                "data-habitacion-checkout-epoch=\"" + epoch + "\"",
                "La alerta debe comparar un instante independiente de la zona del navegador");
        Aserciones.contiene(
                pagina,
                "data-habitacion-pagado=\"false\"",
                "La tarjeta debe exponer si falta el pago para completar la alerta");
        Aserciones.contiene(
                pagina,
                "data-habitacion-horas-adicionales=\"7\"",
                "La tarjeta debe exponer el tiempo adicional acumulado");
        Aserciones.contiene(
                pagina,
                "data-habitacion-monto-adicional=\"37.25\"",
                "La tarjeta debe exponer el cargo adicional acumulado");
        Aserciones.contiene(
                pagina,
                "data-habitacion-monto-total=\"112.25\"",
                "La alerta debe recibir el total calculado por el servidor");
    }

    private static void preparaLaConfiguracionSeparada() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        Hotel hotel = catalogo.buscarPorCodigo("sol-pucusana").orElseThrow();
        try (GestorHabitacionesHoteles gestor =
                new GestorHabitacionesHoteles(catalogo.todos())) {
            String pagina = plantillas().paginaConfiguracionHotel(
                    hotel,
                    gestor.resumen(hotel.codigo()),
                    PlantillasWeb.EstadoConfiguracionHotel.INICIAL);
            Aserciones.contiene(
                    pagina, "<h1 id=\"configuracion-hotel-titulo\">",
                    "La pagina debe identificarse como configuracion del hotel");
            Aserciones.contiene(
                    pagina,
                    "action=\"/hotel/configuracion/guardar\" method=\"post\"",
                    "Toda la configuracion debe usar el guardado general");
            Aserciones.iguales(
                    1,
                    contar(pagina, "action=\"/hotel/configuracion/guardar\""),
                    "Debe existir un unico formulario de guardado general");
            Aserciones.iguales(3, contar(pagina, "<form"),
                    "La pagina debe incluir llave, guardado general y cierre de sesion");
            Aserciones.contiene(
                    pagina,
                    "action=\"/hotel/configuracion/llave\" method=\"post\"",
                    "La configuracion debe permitir cambiar la llave del hotel");
            Aserciones.contiene(pagina, "name=\"nuevaLlave\" type=\"password\"",
                    "La nueva llave debe permanecer protegida en configuracion");
            Aserciones.contiene(
                    pagina,
                    "name=\"cantidadHabitaciones\" type=\"number\"",
                    "Debe permitir editar la cantidad");
            Aserciones.contiene(
                    pagina, "min=\"1\" max=\"50\" step=\"1\" value=\"12\"",
                    "La cantidad debe publicar sus limites y valor actual");
            Aserciones.contiene(
                    pagina,
                    "name=\"cantidadSecciones\" type=\"hidden\"",
                    "El lote debe declarar cuantas secciones existentes envia");
            Aserciones.contiene(
                    pagina,
                    "Guardar todos los cambios",
                    "Debe conservar el boton de guardado general");
            Aserciones.contiene(
                    pagina, "Guardar cantidad",
                    "La cantidad debe ofrecer un boton independiente");
            Aserciones.contiene(
                    pagina, "data-ruta-guardado=\"/hotel/configuracion/habitaciones\"",
                    "El boton de cantidad debe usar su endpoint existente");
            Aserciones.iguales(
                    gestor.seccionesTarifarias(hotel.codigo()).size() + 1,
                    contar(pagina, "data-guardar-seccion"),
                    "Cada seccion registrada y la nueva deben ofrecer su guardado individual");
            Aserciones.contiene(
                    pagina, "aria-label=\"Guardar cambios de Amarillo\"",
                    "Cada guardado individual debe identificar su seccion para lectores de pantalla");
            Aserciones.iguales(
                    gestor.seccionesTarifarias(hotel.codigo()).size() + 1,
                    contar(
                            pagina,
                            "data-ruta-guardado=\"/hotel/configuracion/secciones/guardar\""),
                    "Cada boton de seccion, incluida la nueva, debe usar su endpoint existente");
            Aserciones.contiene(
                    pagina,
                    "type=\"button\" data-ruta-guardado=",
                    "Los botones individuales no deben capturar el envio implicito del formulario");
            Aserciones.noContieneIgnorandoMayusculas(
                    pagina, "/hotel/configuracion/secciones/eliminar",
                    "Las eliminaciones deben viajar con el guardado general");
            for (String campo : new String[] {
                "id", "accion", "color", "nombre", "precio6Horas", "precioDiaCompleto"
            }) {
                Aserciones.contiene(pagina, "name=\"seccion.0." + campo + "\"",
                        "La seccion indexada debe incluir el campo: " + campo);
            }
            Aserciones.contiene(pagina, "name=\"nuevaSeccion.color\" type=\"color\"",
                    "El usuario debe elegir libremente un color");
            Aserciones.contiene(pagina, "data-bloque-anadir-seccion",
                    "La tarjeta nueva debe destacar su accion de alta inmediata");
            Aserciones.contiene(pagina, "aria-label=\"Añadir nueva sección\"",
                    "El alta inmediata debe tener un nombre accesible propio");
            Aserciones.contiene(pagina, ">Añadir nueva sección</button>",
                    "La tarjeta nueva debe ofrecer una accion visible para añadirla");
            Aserciones.contiene(pagina, "data-guardar-seccion disabled",
                    "Sin JavaScript, el boton inmediato debe permanecer inerte y usar el guardado general");
            Aserciones.contiene(pagina, "data-eliminar-seccion",
                    "La eliminacion debe quedar pendiente hasta el guardado general");
            Aserciones.contiene(pagina, "data-deshacer-eliminacion",
                    "Una eliminacion pendiente debe poder deshacerse");
            Aserciones.contiene(pagina, "maxlength=\"40\"",
                    "El nombre opcional debe mostrar su limite");
            Aserciones.contiene(pagina, "Si dejas el nombre vac",
                    "Debe explicar el nombre automatico basado en el color");
            Aserciones.iguales(
                    gestor.seccionesTarifarias(hotel.codigo()).size(),
                    contar(pagina, "class=\"tarjeta-seccion-tarifaria\" data-seccion-tarifaria"),
                    "Debe renderizar una tarjeta por seccion registrada");
            Aserciones.contiene(pagina, "#FBBF24",
                    "Debe renderizar el HEX configurado");
            Aserciones.contiene(pagina, "value=\"50.00\"",
                    "Debe renderizar el precio de seis horas");
            Aserciones.contiene(pagina, "value=\"90.00\"",
                    "Debe renderizar el precio diario");
            Aserciones.contiene(pagina, "/publico/scripts/configuracion-hotel.js",
                    "La configuracion debe cargar su script dedicado");
            Aserciones.falso(pagina.contains("{{"),
                    "La configuracion no debe dejar tokens visibles");
            comprobarQueNoExponeLlaves(pagina, "La configuracion del hotel");
        }
    }

    private static void preparaLaVistaFiltradaDeLimpieza() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        Hotel hotel = catalogo.buscarPorCodigo("sol-pucusana").orElseThrow();
        try (GestorHabitacionesHoteles gestor =
                new GestorHabitacionesHoteles(catalogo.todos())) {
            List<Habitacion> habitaciones = gestor.habitaciones(hotel.codigo());
            gestor.actualizarHabitacion(
                    hotel.codigo(), 2, habitaciones.get(1).seccion().id(),
                    TipoServicio.SEIS_HORAS, EstadoHabitacion.LIMPIEZA_PENDIENTE, true);
            gestor.actualizarHabitacion(
                    hotel.codigo(), 3, habitaciones.get(2).seccion().id(),
                    TipoServicio.DIA_COMPLETO, EstadoHabitacion.EN_LIMPIEZA, false);
            gestor.actualizarHabitacion(
                    hotel.codigo(), 4, habitaciones.get(3).seccion().id(),
                    TipoServicio.SEIS_HORAS, EstadoHabitacion.OCUPADA, true);

            String pagina = plantillas().paginaHabitaciones(
                    hotel,
                    gestor.habitacionesParaLimpieza(hotel.codigo()),
                    gestor.seccionesTarifarias(hotel.codigo()),
                    PlantillasWeb.ModoHabitaciones.LIMPIEZA,
                    PlantillasWeb.EstadoHabitaciones.INICIAL,
                    null);
            Aserciones.contiene(pagina, "data-modo=\"limpieza\"",
                    "La vista de limpieza debe declarar su modo fijo");
            Aserciones.contiene(
                    pagina,
                    "<h1 id=\"habitaciones-titulo\">Limpieza de habitaciones</h1>",
                    "Debe mostrar el titulo propio de Limpieza");
            Aserciones.iguales(2, contar(pagina, "class=\"tarjeta-habitacion\""),
                    "Limpieza debe renderizar solo los dos estados operativos");
            Aserciones.contiene(pagina, "data-habitacion-numero=\"2\"",
                    "Debe incluir la limpieza pendiente");
            Aserciones.contiene(pagina, "data-habitacion-numero=\"3\"",
                    "Debe incluir la limpieza en curso");
            Aserciones.noContieneIgnorandoMayusculas(
                    pagina, "data-habitacion-numero=\"4\"",
                    "Limpieza no debe renderizar una habitacion ocupada");
            Aserciones.contiene(pagina, "href=\"/hotel/configuracion\"",
                    "Limpieza tambien debe ofrecer la tuerquita");
            Aserciones.noContieneIgnorandoMayusculas(
                    pagina, "name=\"cantidadHabitaciones\"",
                    "Limpieza no debe mezclar la configuracion de capacidad");
            Aserciones.falso(pagina.contains("{{"),
                    "Limpieza no debe dejar tokens visibles");
        }
    }

    private static void representaErroresDeHabitacionesControlados() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        Hotel hotel = catalogo.buscarPorCodigo("sol-pucusana").orElseThrow();
        try (GestorHabitacionesHoteles gestor =
                new GestorHabitacionesHoteles(catalogo.todos())) {
            var resumen = gestor.resumen(hotel.codigo());
            Habitacion seleccionada = resumen.habitaciones().get(0);
            Object[][] casos = {
                {PlantillasWeb.EstadoHabitaciones.HABITACION_DESCONOCIDA,
                        "La habitaci\u00f3n seleccionada no existe."},
                {PlantillasWeb.EstadoHabitaciones.SECCION_DESCONOCIDA,
                        "Selecciona una secci\u00f3n tarifaria registrada."},
                {PlantillasWeb.EstadoHabitaciones.TIPO_SERVICIO_INVALIDO,
                        "Selecciona el servicio de 6 horas o d\u00eda completo."},
                {PlantillasWeb.EstadoHabitaciones.ESTADO_INVALIDO,
                        "Selecciona un estado v\u00e1lido."},
                {PlantillasWeb.EstadoHabitaciones.HORARIO_CHECKOUT_INVALIDO,
                        "Ingresa una hora de check-out v\u00e1lida, igual o posterior al check-in."},
                {PlantillasWeb.EstadoHabitaciones.HOTEL_DESCONOCIDO,
                        "No fue posible consultar las habitaciones del hotel."}
            };
            for (Object[] caso : casos) {
                String pagina = plantillas().paginaHabitaciones(
                        hotel,
                        resumen.habitaciones(),
                        resumen.seccionesTarifarias(),
                        PlantillasWeb.ModoHabitaciones.NORMAL,
                        (PlantillasWeb.EstadoHabitaciones) caso[0],
                        seleccionada);
                Aserciones.contiene(pagina, (String) caso[1],
                        "Cada error debe mostrar un mensaje controlado");
                if (caso[0] != PlantillasWeb.EstadoHabitaciones.HOTEL_DESCONOCIDO) {
                    Aserciones.contiene(pagina, "modal-habitacion--abierto",
                            "Un error de habitacion debe reabrir el dialogo");
                }
                Aserciones.falso(pagina.contains("{{"),
                        "El error de habitacion no debe dejar tokens");
                comprobarQueNoExponeLlaves(pagina, "Un error de habitacion");
            }
        }
    }

    private static void representaErroresDeConfiguracionControlados() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        Hotel hotel = catalogo.buscarPorCodigo("sol-pucusana").orElseThrow();
        try (GestorHabitacionesHoteles gestor =
                new GestorHabitacionesHoteles(catalogo.todos())) {
            Object[][] casos = {
                {PlantillasWeb.EstadoConfiguracionHotel.CANTIDAD_INVALIDA,
                        "La cantidad debe estar entre 1 y 50 habitaciones."},
                {PlantillasWeb.EstadoConfiguracionHotel.REDUCCION_NO_PERMITIDA,
                        "No puedes eliminar habitaciones ocupadas"},
                {PlantillasWeb.EstadoConfiguracionHotel.SECCION_DESCONOCIDA,
                        "La secci\u00f3n tarifaria seleccionada no existe."},
                {PlantillasWeb.EstadoConfiguracionHotel.LIMITE_SECCIONES,
                        "m\u00e1ximo 12 secciones tarifarias"},
                {PlantillasWeb.EstadoConfiguracionHotel.ULTIMA_SECCION,
                        "conservar al menos una secci\u00f3n"},
                {PlantillasWeb.EstadoConfiguracionHotel.SECCION_EN_USO,
                        "No puedes eliminar una secci\u00f3n asignada"},
                {PlantillasWeb.EstadoConfiguracionHotel.COLOR_INVALIDO,
                        "Selecciona un color v\u00e1lido."},
                {PlantillasWeb.EstadoConfiguracionHotel.COLOR_DUPLICADO,
                        "Ya existe una secci\u00f3n con ese color."},
                {PlantillasWeb.EstadoConfiguracionHotel.NOMBRE_INVALIDO,
                        "m\u00e1ximo 40 caracteres v\u00e1lidos"},
                {PlantillasWeb.EstadoConfiguracionHotel.NOMBRE_DUPLICADO,
                        "Ya existe una secci\u00f3n con ese nombre."},
                {PlantillasWeb.EstadoConfiguracionHotel.PRECIO_INVALIDO,
                        "Ambos precios deben estar entre S/ 1.00 y S/ 10000.00"},
                {PlantillasWeb.EstadoConfiguracionHotel.HOTEL_DESCONOCIDO,
                        "No fue posible configurar las habitaciones"}
            };
            for (Object[] caso : casos) {
                String pagina = plantillas().paginaConfiguracionHotel(
                        hotel,
                        gestor.resumen(hotel.codigo()),
                        (PlantillasWeb.EstadoConfiguracionHotel) caso[0]);
                Aserciones.contiene(pagina, (String) caso[1],
                        "Cada error de configuracion debe ser comprensible");
                Aserciones.falso(pagina.contains("{{"),
                        "El error de configuracion no debe dejar tokens");
                comprobarQueNoExponeLlaves(pagina, "Un error de configuracion");
            }
        }
    }

    private static void escapaElHotelEnElModuloDeHabitaciones() {
        Hotel manipulado = new Hotel(
                "hotel-malicioso",
                "<script>alert(9)</script>",
                "Lima",
                "HM");
        SeccionTarifaria seccion = new SeccionTarifaria(
                "seccion-1",
                "#123456",
                "<script>alert(8)</script>",
                new BigDecimal("100.00"),
                new BigDecimal("180.00"));
        Habitacion habitacion = new Habitacion(
                1,
                seccion,
                TipoServicio.SEIS_HORAS,
                new BigDecimal("100.00"),
                EstadoHabitacion.DISPONIBLE,
                null,
                null,
                false);
        String habitaciones = plantillas().paginaHabitaciones(
                manipulado,
                java.util.List.of(habitacion),
                java.util.List.of(seccion),
                PlantillasWeb.ModoHabitaciones.NORMAL,
                PlantillasWeb.EstadoHabitaciones.INICIAL,
                null);
        var resumen = new GestorHabitacionesHoteles.ResumenInventario(
                java.util.List.of(habitacion), java.util.List.of(seccion));
        String configuracion = plantillas().paginaConfiguracionHotel(
                manipulado,
                resumen,
                PlantillasWeb.EstadoConfiguracionHotel.INICIAL);
        for (String pagina : new String[] {habitaciones, configuracion}) {
            Aserciones.noContieneIgnorandoMayusculas(
                    pagina, "<script>alert(9)</script>",
                    "El nombre del hotel no debe inyectar HTML");
            Aserciones.noContieneIgnorandoMayusculas(
                    pagina, "<script>alert(8)</script>",
                    "El nombre de una seccion no debe inyectar HTML");
            Aserciones.contiene(pagina, "&lt;script&gt;",
                    "Los nombres controlados deben renderizarse escapados");
            Aserciones.falso(pagina.contains("{{"),
                    "El render seguro no debe dejar tokens");
        }
    }

    private static void escapaLosDatosRenderizados() {
        Hotel manipulado = new Hotel(
                "hotel-\" onclick=\"alert(1)",
                "<script>alert(1)</script>",
                "<Lima>",
                "<&>");
        PlantillasWeb plantillas = plantillas();
        String panel = plantillas.paginaPanel(
                java.util.List.of(manipulado),
                PlantillasWeb.EstadoHotel.LLAVE_INVALIDA,
                manipulado);
        String gestion = plantillas.paginaGestion(manipulado);
        for (String pagina : new String[] {panel, gestion}) {
            Aserciones.noContieneIgnorandoMayusculas(
                    pagina, "<script>alert(1)</script>",
                    "Los datos del hotel no deben inyectar etiquetas HTML");
        }
        Aserciones.contiene(panel, "&quot; onclick=&quot;alert(1)",
                "Los datos usados en atributos deben escaparse");
        Aserciones.contiene(gestion, "&lt;script&gt;alert(1)&lt;/script&gt;",
                "El nombre controlado tambien debe escaparse en la gestion");
    }

    private static void cargaRecursosPublicosDeFormaDefensiva() {
        RecursosWeb recursos = new RecursosWeb();
        RecursosWeb.Recurso css = recursos.buscar("/publico/estilos/gestera.css").orElseThrow();
        RecursosWeb.Recurso accesoJs = recursos.buscar("/publico/scripts/acceso.js").orElseThrow();
        RecursosWeb.Recurso hotelesJs = recursos.buscar("/publico/scripts/hoteles.js").orElseThrow();
        RecursosWeb.Recurso gestionJs = recursos.buscar("/publico/scripts/gestion.js").orElseThrow();
        RecursosWeb.Recurso habitacionesJs =
                recursos.buscar("/publico/scripts/habitaciones.js").orElseThrow();
        RecursosWeb.Recurso configuracionJs = recursos.buscar(
                "/publico/scripts/configuracion-hotel.js").orElseThrow();
        RecursosWeb.Recurso areasJs = recursos.buscar(
                "/publico/scripts/areas-comunes.js").orElseThrow();
        RecursosWeb.Recurso servicioJs = recursos.buscar(
                "/publico/scripts/servicio-habitacion.js").orElseThrow();
        RecursosWeb.Recurso reportesJs = recursos.buscar(
                "/publico/scripts/reportes.js").orElseThrow();
        Aserciones.iguales("text/css; charset=UTF-8", css.tipoContenido(),
                "Debe declarar correctamente el CSS");
        Aserciones.iguales("text/javascript; charset=UTF-8", accesoJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript del acceso");
        Aserciones.iguales("text/javascript; charset=UTF-8", hotelesJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript de hoteles");
        Aserciones.iguales("text/javascript; charset=UTF-8", gestionJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript de gestion");
        Aserciones.iguales("text/javascript; charset=UTF-8", habitacionesJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript de habitaciones");
        Aserciones.iguales("text/javascript; charset=UTF-8", configuracionJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript de configuracion");
        Aserciones.iguales("text/javascript; charset=UTF-8", areasJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript de areas comunes");
        Aserciones.iguales("text/javascript; charset=UTF-8", servicioJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript de servicio al cuarto");
        Aserciones.iguales("text/javascript; charset=UTF-8", reportesJs.tipoContenido(),
                "Debe declarar correctamente el JavaScript de reportes");
        Aserciones.contiene(
                new String(css.contenido(), StandardCharsets.UTF_8),
                "@media",
                "Los estilos deben adaptarse a pantallas pequenas");
        Aserciones.contiene(
                new String(accesoJs.contenido(), StandardCharsets.UTF_8),
                "alternar-llave",
                "El acceso debe conservar la mejora del campo de llave");

        String filtro = new String(hotelesJs.contenido(), StandardCharsets.UTF_8);
        Aserciones.contiene(filtro, ".normalize(\"NFD\")",
                "La busqueda debe ignorar diferencias de acentos");
        Aserciones.contiene(filtro, "addEventListener(\"input\"",
                "La lista debe filtrarse mientras se escribe");
        Aserciones.contiene(filtro, "tarjeta.hidden = !coincide",
                "El filtro debe ocultar las tarjetas que no coinciden");
        Aserciones.contiene(filtro, "sinResultados.hidden",
                "La busqueda debe mostrar un estado sin resultados");
        Aserciones.contiene(filtro, "hotel registrado",
                "Una sola coincidencia debe usar el contador singular solicitado");
        Aserciones.contiene(filtro, "hoteles registrados",
                "Varias coincidencias deben usar el contador plural solicitado");
        Aserciones.noContieneIgnorandoMayusculas(
                filtro, "disponible",
                "El contador del directorio ya no debe hablar de disponibilidad");

        String cambio = new String(gestionJs.contenido(), StandardCharsets.UTF_8);
        Aserciones.contiene(cambio, "#abrir-cambio-llave",
                "El script debe abrir la opcion de cambio");
        Aserciones.contiene(cambio, "#modal-cambio-llave",
                "El script debe controlar el dialogo de cambio");
        Aserciones.contiene(cambio, "valor.length < 8 || valor.length > 15",
                "La mejora cliente debe validar los limites visibles");
        Aserciones.contiene(cambio, "formulario.addEventListener(\"submit\"",
                "El formulario debe validarse antes del envio");

        String habitaciones =
                new String(habitacionesJs.contenido(), StandardCharsets.UTF_8);
        Aserciones.contiene(
                habitaciones,
                "new Set([\"limpieza_pendiente\", \"en_limpieza\"])",
                "El cliente debe reconocer exactamente los estados de Limpieza");
        Aserciones.contiene(habitaciones, "estadosPermitidos = new Set",
                "Los estados deben usar una allowlist cliente");
        Aserciones.contiene(habitaciones, "tiposServicio = new Set",
                "Los servicios deben usar una allowlist cliente");
        Aserciones.contiene(habitaciones, "const colorSeguro",
                "Los colores HEX dinamicos deben validarse antes de usarlos");
        Aserciones.contiene(habitaciones, "const calcularHorarios",
                "El cliente debe anticipar los horarios automaticos");
        Aserciones.contiene(habitaciones, "tipoServicio.value === \"seis_horas\" ? 6 : 24",
                "La vista previa debe distinguir seis y veinticuatro horas");
        Aserciones.contiene(habitaciones, "#ajuste-manual-checkout",
                "El cliente debe controlar la activacion explicita del ajuste de prueba");
        Aserciones.contiene(
                habitaciones,
                "ajusteManualCheckOut.addEventListener(\"change\"",
                "El check-out solo debe habilitarse mediante el control manual");
        Aserciones.contiene(
                habitaciones,
                "checkOut.readOnly = !requiereEstadia || !ajusteManualCheckOut.checked",
                "La salida debe permanecer bloqueada mientras no se solicite el ajuste");
        Aserciones.contiene(habitaciones, "tarjeta.dataset.habitacionCheckoutEpoch",
                "Las alertas deben comparar el instante seguro renderizado por el servidor");
        Aserciones.contiene(
                habitaciones,
                "tarjeta.dataset.habitacionEstado === \"ocupada\"",
                "Solo una habitacion ocupada debe generar alertas de check-out");
        Aserciones.contiene(habitaciones, "restante <= 10 * 60 * 1000",
                "La primera alerta debe aparecer diez minutos antes");
        Aserciones.contiene(habitaciones, "restante <= 5 * 60 * 1000",
                "La segunda alerta debe aparecer cinco minutos antes");
        Aserciones.contiene(habitaciones, "restante <= 0",
                "La tercera alerta debe aparecer al alcanzar la hora limite");
        Aserciones.contiene(
                habitaciones,
                "window.setInterval(actualizarAlertasCheckout, 5000)",
                "La pagina abierta debe volver a comprobar periodicamente los vencimientos");
        Aserciones.contiene(habitaciones, "Falta registrar el pago",
                "La alerta impaga debe avisar que falta registrar el pago");
        Aserciones.contiene(habitaciones, "Monto total a cobrar",
                "La alerta y el recuadro impagos deben mostrar el importe completo");
        Aserciones.contiene(habitaciones, "tarjeta.dataset.habitacionMontoTotal",
                "El cliente debe tomar el total confiable publicado por el servidor");
        Aserciones.contiene(
                habitaciones,
                "${epoch}:${fase}:${minutoFirma}:${tarjeta.dataset.habitacionPagado === \"true\"}:${obtenerMontoTotal(tarjeta)}",
                "La alerta existente debe refrescarse al cambiar de fase o importe");
        Aserciones.contiene(habitaciones, "reproducirCampana",
                "El vencimiento debe generar un aviso sonoro");
        Aserciones.contiene(habitaciones, "minutosVencidos",
                "La alerta vencida debe contar los minutos transcurridos");
        Aserciones.contiene(habitaciones, "#modal-checkout",
                "El cliente debe controlar el recuadro de confirmacion de salida");
        Aserciones.contiene(habitaciones, "modalCheckout.showModal()",
                "Seleccionar una alerta debe abrir el recuadro de check-out");
        Aserciones.contiene(habitaciones, "dataset.habitacionSeccion",
                "Las habitaciones deben filtrarse por su seccion configurada");
        Aserciones.contiene(habitaciones, "perteneceAlModo",
                "El cliente debe conservar el filtro de Limpieza");
        Aserciones.contiene(
                habitaciones,
                "#contador-habitaciones-disponibles",
                "El cliente debe controlar el contador separado de disponibles");
        Aserciones.contiene(
                habitaciones,
                "#contador-habitaciones-ocupadas",
                "El cliente debe controlar el contador separado de ocupadas");
        Aserciones.contiene(
                habitaciones,
                "tarjeta.dataset.habitacionEstado === \"disponible\"",
                "Solo las habitaciones disponibles y visibles deben entrar en el contador");
        Aserciones.contiene(
                habitaciones,
                "tarjeta.dataset.habitacionEstado === \"ocupada\"",
                "Solo las habitaciones ocupadas y visibles deben entrar en el contador");
        Aserciones.contiene(
                habitaciones,
                "tipoServicio.value = \"seis_horas\"",
                "Escoger un color debe preseleccionar el servicio de seis horas");
        Aserciones.contiene(
                habitaciones,
                "estado.value = \"disponible\"",
                "Escoger un color debe preseleccionar el estado Disponible");
        Aserciones.contiene(
                habitaciones,
                "actualizarCamposEstadia({ limpiar: seccionValida })",
                "El valor predeterminado Disponible debe limpiar estancia y pago");
        Aserciones.contiene(habitaciones, "opcionesHorasAdicionales.length !== 4",
                "El cliente debe exigir las cuatro opciones de ampliacion esperadas");
        Aserciones.contiene(habitaciones, "[0, 1, 2, 4].includes(horas)",
                "Las horas adicionales deben validarse mediante una allowlist cliente");
        Aserciones.contiene(
                habitaciones,
                "montoAdicional.required = ocupada && horas > 0",
                "Seleccionar tiempo debe hacer obligatorio el nuevo cargo");
        Aserciones.contiene(
                habitaciones,
                "numeroMonetario(cargoNuevo) <= 0 || !montoAdicional.validity.valid",
                "El cliente debe rechazar cargos vacios, cero o fuera de limites");
        Aserciones.contiene(
                habitaciones,
                "totalAntesDeAmpliar + nuevoCargo",
                "La vista previa debe sumar base, cargos anteriores y cargo nuevo");
        Aserciones.contiene(
                habitaciones,
                "const salida = fechaCheckoutComoUtc()",
                "La vista previa debe calcularse sobre una fecha separada del campo guardado");
        Aserciones.contiene(
                habitaciones,
                "const requiereValoresIniciales = seccionValida",
                "Cambiar la seccion solo debe aplicar valores iniciales a una habitacion nueva");
        Aserciones.contiene(habitaciones, "&& !estadoConEstadia()",
                "Una habitacion ocupada debe conservar estado y estancia al cambiar de seccion");
        Aserciones.contiene(habitaciones, "formulario.addEventListener(\"submit\"",
                "La edicion debe validar el formulario antes del envio");
        Aserciones.noContieneIgnorandoMayusculas(
                habitaciones, "innerHTML",
                "El script no debe construir contenido con HTML sin escapar");

        String areas = new String(areasJs.contenido(), StandardCharsets.UTF_8);
        Aserciones.contiene(areas, "Date.UTC",
                "Las areas deben sumar horas sin heredar la zona del navegador");
        Aserciones.contiene(areas, "getUTCFullYear",
                "El resultado debe conservar la hora local ingresada para Perú");
        Aserciones.contiene(areas, "horas * 60 * 60 * 1000",
                "El check-out debe sumar exactamente la duracion rápida elegida");
        Aserciones.noContieneIgnorandoMayusculas(
                areas, "new Date(entrada.value)",
                "El horario de Perú no debe interpretarse como hora local del dispositivo");
        Aserciones.noContieneIgnorandoMayusculas(
                areas, "innerHTML",
                "El script de areas no debe construir contenido con HTML sin escapar");

        String configuracion =
                new String(configuracionJs.contenido(), StandardCharsets.UTF_8);
        Aserciones.contiene(configuracion, "nombreColorPredeterminado",
                "Un nombre vacio debe visualizarse a partir del HEX");
        Aserciones.contiene(configuracion, "const precioValido",
                "Los dos precios deben validarse en el cliente");
        Aserciones.contiene(configuracion, "limiteSinEspacio",
                "La interfaz debe respetar dinamicamente el maximo de secciones");
        Aserciones.contiene(configuracion, "[data-eliminar-seccion]",
                "La eliminacion pendiente debe pedir confirmacion");
        Aserciones.contiene(configuracion, "[data-deshacer-eliminacion]",
                "El cliente debe permitir deshacer una eliminacion pendiente");
        Aserciones.contiene(
                configuracion,
                "formularioGeneral.addEventListener(\"submit\"",
                "El guardado general debe validar todo antes de enviarse");
        Aserciones.contiene(
                configuracion,
                "crearFormularioIndividual",
                "Los guardados individuales deben enviar solo sus propios campos");
        Aserciones.contiene(
                configuracion,
                "`Guardar cambios de ${nombreMostrado}`",
                "El nombre accesible del guardado debe seguir el nombre visible de la seccion");
        Aserciones.contiene(
                configuracion,
                "? \"Añadir nueva sección\"",
                "El alta debe conservar un nombre accesible distinto al de una edicion");
        Aserciones.contiene(
                configuracion,
                "Completa ambos precios para añadir la nueva sección.",
                "Un alta vacia debe explicar que precios faltan antes de enviarse");
        Aserciones.contiene(
                configuracion,
                "configuracion.validar(configuracion.esNueva)",
                "Pulsar añadir debe forzar la validacion aunque la tarjeta este vacia");
        Aserciones.contiene(
                configuracion,
                "configuracionNueva.botonGuardarSeccion.disabled = limiteAltaInmediata",
                "El alta inmediata debe deshabilitarse al llegar al maximo persistido de secciones");
        Aserciones.contiene(
                configuracion,
                "const existentesRegistradas = configuraciones.filter",
                "El limite inmediato debe contar incluso secciones pendientes de eliminacion");
        Aserciones.contiene(
                configuracion,
                "Guarda primero la eliminación o usa Guardar todos para reemplazarla.",
                "Un reemplazo por lote debe orientar al guardado general sin habilitar el alta inmediata");
        Aserciones.contiene(
                configuracion,
                "validarAltaSinDuplicados",
                "El alta inmediata debe anticipar duplicados antes del POST");
        Aserciones.contiene(
                configuracion,
                "actual.colorPersistido === colorNormalizado",
                "El alta debe comparar el color con los valores persistidos normalizados");
        Aserciones.contiene(
                configuracion,
                "actual.nombrePersistido === nombreNormalizado",
                "El alta debe comparar el nombre efectivo con los valores persistidos normalizados");
        Aserciones.contiene(
                configuracion,
                ".replace(/\\s+/g, \" \")",
                "La comparacion de nombres debe compactar espacios como el servidor");
        Aserciones.contiene(
                configuracion,
                "configuraciones.filter((actual) => !actual.esNueva)",
                "Los duplicados deben incluir secciones marcadas para eliminar hasta guardar el lote");
        Aserciones.contiene(
                configuracion,
                "configuracion.esNueva ? \"Añadiendo…\" : \"Guardando…\"",
                "El boton nuevo debe anunciar un estado de alta, no de edicion");
        Aserciones.contiene(
                configuracion,
                "dataset.rutaGuardado",
                "El cliente debe usar el endpoint declarado por cada boton individual");
        Aserciones.contiene(
                configuracion,
                "botonGuardarCantidad.addEventListener(\"click\"",
                "El guardado de cantidad no debe interferir con el envio implicito general");
        Aserciones.contiene(configuracion, "const seccionesFinales",
                "La validacion debe trabajar sobre la configuracion final");
        Aserciones.contiene(configuracion, "Hay cambios pendientes de guardar",
                "La interfaz debe informar los cambios sin guardar");
        Aserciones.noContieneIgnorandoMayusculas(
                configuracion, "innerHTML",
                "La configuracion no debe insertar nombres mediante HTML inseguro");

        byte[] primeraCopia = hotelesJs.contenido();
        primeraCopia[0] = 0;
        Aserciones.falso(hotelesJs.contenido()[0] == 0,
                "El contenido de un recurso debe entregarse mediante copias defensivas");
        Aserciones.verdadero(
                recursos.buscar("/publico/../paginas/panel.html").isEmpty(),
                "El catalogo cerrado no debe permitir recorridos de ruta");
    }

    private static PlantillasWeb plantillas() {
        return new PlantillasWeb();
    }

    private static void comprobarQueNoExponeLlaves(String pagina, String contexto) {
        Aserciones.noContieneIgnorandoMayusculas(
                pagina, LLAVE_GLOBAL, contexto + " no debe revelar la llave global");
        Aserciones.noContieneIgnorandoMayusculas(
                pagina, LLAVE_HOTEL, contexto + " no debe revelar la llave del hotel");
    }

    private static int contar(String texto, String fragmento) {
        int cantidad = 0;
        int posicion = 0;
        while ((posicion = texto.indexOf(fragmento, posicion)) >= 0) {
            cantidad++;
            posicion += fragmento.length();
        }
        return cantidad;
    }
}
