package com.gestera.infraestructura.servidor;

import com.gestera.pruebas.Aserciones;
import com.gestera.pruebas.ClienteHttpPrueba;
import com.gestera.pruebas.ClienteHttpPrueba.Respuesta;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pruebas de extremo a extremo sobre servidores reales en puertos efimeros. */
public final class ServidorGesteraPrueba {
    private static final String LLAVE = "123456789abc";
    private static final String LLAVE_HOTEL = "gesteraperu123";
    private static final String LLAVE_SOL_ACTUALIZADA = "NuevaSol8";

    private ServidorGesteraPrueba() {
    }

    public static int ejecutar() throws Exception {
        try (ServidorGestera servidor = nuevoServidor(false)) {
            servidor.iniciar();
            ClienteHttpPrueba cliente = new ClienteHttpPrueba(servidor.direccionBase());
            entregaLaPaginaDeAcceso(cliente);
            agregaEncabezadosDeSeguridad(cliente);
            concedeAccesoYEmiteCookie(cliente);
            protegeElPanelConLaSesion(cliente);
            rechazaUnaLlaveIncorrectaSinFiltrarla(cliente);
            informaUnaLlaveAusente(cliente);
            rechazaSesionesInexistentes(cliente);
            protegeLasRutasDelHotel(cliente);
            concedeAccesoAlHotelYAbreLaGestion(cliente);
            mantieneLaSeleccionSeparadaPorSesion(cliente);
            rechazaLaLlaveDelHotelSinFiltrarla(cliente);
            validaLosCamposDelHotelSinFiltrarLlaves(cliente);
            cierraLaSesion(cliente);
            sirveSoloRecursosPublicosConocidos(cliente);
            restringeLosMetodosHttp(cliente);
            validaElFormularioDeAccesoGeneral(cliente);
            validaElFormularioDelHotel(cliente);
            limitaElTamanoYRespondeRutasDesconocidas(cliente);
        }
        bloqueaIntentosConsecutivosDelAccesoGeneral();
        bloqueaIntentosConsecutivosDelHotel();
        pruebaElFlujoCompletoDeCambioDeLlave();
        pruebaElFlujoCompletoDeHabitaciones();
        return 36;
    }

    private static void entregaLaPaginaDeAcceso(ClienteHttpPrueba cliente) throws Exception {
        Respuesta respuesta = cliente.get("/");
        Aserciones.iguales(200, respuesta.estado(), "GET / debe responder correctamente");
        Aserciones.contiene(
                respuesta.encabezado("Content-Type"),
                "text/html; charset=UTF-8",
                "La pagina debe declarar HTML UTF-8");
        Aserciones.contiene(respuesta.cuerpo(), "LLAVE DE ACCESO",
                "Debe entregar el modulo de acceso");
        Aserciones.noContieneIgnorandoMayusculas(
                respuesta.cuerpo(), "credenciales de prueba",
                "La respuesta no debe incluir datos de prueba");
        comprobarQueNoExponeLlaves(respuesta, "La pagina de acceso");
    }

    private static void agregaEncabezadosDeSeguridad(ClienteHttpPrueba cliente) throws Exception {
        Respuesta respuesta = cliente.get("/");
        Aserciones.iguales("no-store", respuesta.encabezado("Cache-Control"),
                "El acceso no debe almacenarse");
        Aserciones.iguales(
                "nosniff",
                respuesta.encabezado("X-Content-Type-Options"),
                "Debe impedir inferencia de tipos de contenido");
        Aserciones.iguales("DENY", respuesta.encabezado("X-Frame-Options"),
                "Debe impedir iframes");
        Aserciones.iguales("no-referrer", respuesta.encabezado("Referrer-Policy"),
                "No debe filtrar rutas como referencia");
        String politica = respuesta.encabezado("Content-Security-Policy");
        Aserciones.contiene(politica, "default-src 'self'",
                "Debe restringir los origenes de contenido");
        Aserciones.contiene(politica, "form-action 'self'",
                "Los formularios solo deben enviarse al mismo origen");
        Aserciones.contiene(politica, "frame-ancestors 'none'",
                "La CSP tambien debe impedir marcos");
    }

    private static void concedeAccesoYEmiteCookie(ClienteHttpPrueba cliente) throws Exception {
        Respuesta respuesta = cliente.postFormulario("/acceso", "llave=" + LLAVE);
        Aserciones.iguales(303, respuesta.estado(),
                "La llave valida debe redirigir despues del POST");
        Aserciones.iguales("/panel", respuesta.encabezado("Location"),
                "Debe redirigir al selector de hoteles");
        String cookie = respuesta.encabezado("Set-Cookie");
        Aserciones.contiene(cookie, "GESTERA_SESION=", "Debe crear una cookie de sesion opaca");
        Aserciones.contiene(cookie, "; Path=/", "La cookie debe cubrir todos los modulos");
        Aserciones.contiene(cookie, "; HttpOnly", "JavaScript no debe acceder a la sesion");
        Aserciones.contiene(cookie, "; SameSite=Strict",
                "La cookie debe limitar solicitudes cruzadas");
        comprobarTextoSinLlaves(cookie, "La cookie de sesion");
    }

    private static void protegeElPanelConLaSesion(ClienteHttpPrueba cliente) throws Exception {
        String cookie = iniciarSesion(cliente);
        Respuesta panel = cliente.get("/panel", cookie);
        Aserciones.iguales(200, panel.estado(), "Una sesion valida debe abrir el selector");
        for (String nombre : new String[] {
            "Hotel El Sol de Pucusana", "Hotel Brisa Marina",
            "Hotel Valle Dorado", "Hotel Costa Serena"
        }) {
            Aserciones.contiene(panel.cuerpo(), nombre,
                    "El selector debe mostrar el hotel registrado: " + nombre);
        }
        Aserciones.contiene(panel.cuerpo(), "Buscar hotel por nombre",
                "El panel debe ofrecer busqueda por nombre");
        comprobarQueNoExponeLlaves(panel, "El selector de hoteles");

        Respuesta raiz = cliente.get("/", cookie);
        Aserciones.iguales(303, raiz.estado(),
                "Una sesion activa no debe volver al formulario global");
        Aserciones.iguales("/panel", raiz.encabezado("Location"),
                "Debe enviar la sesion activa al selector");
    }

    private static void rechazaUnaLlaveIncorrectaSinFiltrarla(ClienteHttpPrueba cliente)
            throws Exception {
        String enviada = "llave-super-secreta-incorrecta";
        Respuesta respuesta = cliente.postFormulario("/acceso", "llave=" + enviada);
        Aserciones.iguales(401, respuesta.estado(), "Una llave incorrecta debe denegarse");
        Aserciones.contiene(respuesta.cuerpo(), "La llave de acceso no es v\u00e1lida.",
                "Debe mostrar un error controlado");
        Aserciones.iguales(null, respuesta.encabezado("Set-Cookie"),
                "No debe crear sesion al fallar");
        comprobarNoRefleja(respuesta, enviada,
                "La pagina de error nunca debe reflejar la llave global enviada");
    }

    private static void informaUnaLlaveAusente(ClienteHttpPrueba cliente) throws Exception {
        for (String formulario : new String[] {"", "otro=valor", "llave="}) {
            Respuesta respuesta = cliente.postFormulario("/acceso", formulario);
            Aserciones.iguales(400, respuesta.estado(),
                    "Una llave ausente debe producir una solicitud invalida");
            Aserciones.contiene(respuesta.cuerpo(), "Ingresa tu llave de acceso.",
                    "Debe explicar que falta la llave");
            comprobarQueNoExponeLlaves(respuesta, "El error por llave global ausente");
        }
    }

    private static void rechazaSesionesInexistentes(ClienteHttpPrueba cliente) throws Exception {
        Respuesta sinCookie = cliente.get("/panel");
        Respuesta falsa = cliente.get("/panel", "GESTERA_SESION=token-inexistente");
        for (Respuesta respuesta : new Respuesta[] {sinCookie, falsa}) {
            Aserciones.iguales(303, respuesta.estado(),
                    "El panel debe rechazar una sesion inexistente");
            Aserciones.iguales("/", respuesta.encabezado("Location"),
                    "Debe regresar al acceso");
            Aserciones.contiene(respuesta.encabezado("Set-Cookie"), "Max-Age=0",
                    "Debe limpiar cualquier cookie de sesion invalida");
        }
    }

    private static void protegeLasRutasDelHotel(ClienteHttpPrueba cliente) throws Exception {
        for (String cookie : new String[] {null, "GESTERA_SESION=token-inexistente"}) {
            Respuesta gestion = cliente.get("/hotel", cookie);
            Aserciones.iguales(303, gestion.estado(),
                    "La gestion debe exigir una sesion global valida");
            Aserciones.iguales("/", gestion.encabezado("Location"),
                    "Sin sesion debe regresar al acceso global");
            Aserciones.contiene(gestion.encabezado("Set-Cookie"), "Max-Age=0",
                    "Una cookie invalida debe eliminarse");
        }

        String secreto = "secreto-enviado-sin-sesion";
        Respuesta postSinSesion = cliente.postFormulario(
                "/hotel/acceso",
                formularioHotel("sol-pucusana", secreto));
        Aserciones.iguales(303, postSinSesion.estado(),
                "El POST del hotel tambien debe exigir sesion");
        Aserciones.iguales("/", postSinSesion.encabezado("Location"),
                "Un POST sin sesion debe volver al acceso global");
        comprobarNoRefleja(postSinSesion, secreto,
                "No debe procesar ni reflejar la llave sin sesion");

        String cookie = iniciarSesion(cliente);
        Respuesta sinHotel = cliente.get("/hotel", cookie);
        Aserciones.iguales(303, sinHotel.estado(),
                "Una sesion sin hotel autorizado no debe abrir la gestion");
        Aserciones.iguales("/panel", sinHotel.encabezado("Location"),
                "Debe regresar al selector para elegir un hotel");
    }

    private static void concedeAccesoAlHotelYAbreLaGestion(ClienteHttpPrueba cliente)
            throws Exception {
        String cookie = iniciarSesion(cliente);
        Respuesta acceso = autorizarHotel(cliente, cookie, "sol-pucusana");
        Aserciones.iguales(303, acceso.estado(),
                "La llave valida del hotel debe redirigir");
        Aserciones.iguales("/hotel", acceso.encabezado("Location"),
                "Debe abrir la gestion del hotel seleccionado");
        Aserciones.iguales(null, acceso.encabezado("Set-Cookie"),
                "Seleccionar hotel debe reutilizar la sesion existente");

        Respuesta gestion = cliente.get("/hotel", cookie);
        Aserciones.iguales(200, gestion.estado(),
                "La sesion autorizada debe ver la gestion");
        Aserciones.contiene(gestion.cuerpo(), "Hotel El Sol de Pucusana",
                "La gestion debe mostrar el nombre seleccionado");
        for (String opcion : new String[] {
            "Limpieza de habitaciones", "Habitaciones", "Hu\u00e9spedes",
            "Servicio al cuarto", "Áreas comunes"
        }) {
            Aserciones.contiene(gestion.cuerpo(), opcion,
                    "Debe anticipar la opcion de gestion: " + opcion);
        }
        Aserciones.noContieneIgnorandoMayusculas(
                gestion.cuerpo(), "Reservas",
                "La gestion ya no debe mostrar el modulo de Reservas");
        Aserciones.contiene(
                gestion.cuerpo(), "href=\"/hotel/configuracion\"",
                "La configuracion debe estar disponible desde el panel del hotel");
        Aserciones.noContieneIgnorandoMayusculas(
                gestion.cuerpo(), "Entradas y salidas",
                "La gestion no debe mostrar el apartado eliminado");
        comprobarQueNoExponeLlaves(gestion, "La gestion autorizada");
    }

    private static void mantieneLaSeleccionSeparadaPorSesion(ClienteHttpPrueba cliente)
            throws Exception {
        String primeraSesion = iniciarSesion(cliente);
        String segundaSesion = iniciarSesion(cliente);
        Aserciones.iguales(303,
                autorizarHotel(cliente, primeraSesion, "sol-pucusana").estado(),
                "Debe autorizar el primer hotel");
        Aserciones.iguales(303,
                autorizarHotel(cliente, segundaSesion, "brisa-marina").estado(),
                "Debe autorizar el segundo hotel");

        Respuesta primeraGestion = cliente.get("/hotel", primeraSesion);
        Respuesta segundaGestion = cliente.get("/hotel", segundaSesion);
        Aserciones.contiene(primeraGestion.cuerpo(), "Hotel El Sol de Pucusana",
                "La primera sesion debe conservar su hotel");
        Aserciones.noContieneIgnorandoMayusculas(
                primeraGestion.cuerpo(), "Hotel Brisa Marina",
                "La primera sesion no debe heredar el hotel de otra");
        Aserciones.contiene(segundaGestion.cuerpo(), "Hotel Brisa Marina",
                "La segunda sesion debe conservar su propio hotel");
        Aserciones.noContieneIgnorandoMayusculas(
                segundaGestion.cuerpo(), "Hotel El Sol de Pucusana",
                "La segunda sesion debe estar aislada");

        Aserciones.iguales(303,
                autorizarHotel(cliente, primeraSesion, "costa-serena").estado(),
                "Una sesion debe poder cambiar de hotel");
        Respuesta actualizada = cliente.get("/hotel", primeraSesion);
        Aserciones.contiene(actualizada.cuerpo(), "Hotel Costa Serena",
                "La seleccion nueva debe reemplazar la anterior");
        Aserciones.noContieneIgnorandoMayusculas(
                actualizada.cuerpo(), "Hotel El Sol de Pucusana",
                "La gestion no debe conservar el nombre anterior");
    }

    private static void rechazaLaLlaveDelHotelSinFiltrarla(ClienteHttpPrueba cliente)
            throws Exception {
        String cookie = iniciarSesion(cliente);
        String enviada = "llave-hotel-super-secreta-incorrecta";
        Respuesta respuesta = cliente.postFormulario(
                "/hotel/acceso", formularioHotel("sol-pucusana", enviada), cookie);
        Aserciones.iguales(401, respuesta.estado(),
                "Una llave incorrecta del hotel debe denegarse");
        Aserciones.contiene(
                respuesta.cuerpo(),
                "La llave de acceso del hotel no es v\u00e1lida.",
                "Debe mostrar un error generico del hotel");
        Aserciones.iguales(null, respuesta.encabezado("Set-Cookie"),
                "El fallo no debe reemplazar la cookie de sesion");
        comprobarNoRefleja(respuesta, enviada,
                "La respuesta nunca debe reflejar la llave del hotel enviada");
        comprobarQueNoExponeLlaves(respuesta, "El error de acceso al hotel");

        Respuesta gestion = cliente.get("/hotel", cookie);
        Aserciones.iguales(303, gestion.estado(),
                "Una llave incorrecta no debe autorizar la gestion");
        Aserciones.iguales("/panel", gestion.encabezado("Location"),
                "Debe permanecer en el selector");
    }

    private static void validaLosCamposDelHotelSinFiltrarLlaves(ClienteHttpPrueba cliente)
            throws Exception {
        String cookie = iniciarSesion(cliente);
        String[][] casos = {
            {"", ""},
            {"hotel=sol-pucusana", ""},
            {"hotel=sol-pucusana&llaveHotel=", ""},
            {"llaveHotel=secreto-sin-hotel", "secreto-sin-hotel"},
            {"hotel=no-existe&llaveHotel=secreto-desconocido", "secreto-desconocido"},
            {"hotel=sol-pucusana&hotel=brisa-marina&llaveHotel=secreto-hotel-repetido",
                    "secreto-hotel-repetido"},
            {"hotel=sol-pucusana&llaveHotel=primera&llaveHotel=secreto-llave-repetida",
                    "secreto-llave-repetida"},
            {"hotel=" + "h".repeat(81) + "&llaveHotel=secreto-codigo-largo",
                    "secreto-codigo-largo"}
        };
        for (String[] caso : casos) {
            Respuesta respuesta = cliente.postFormulario("/hotel/acceso", caso[0], cookie);
            Aserciones.iguales(400, respuesta.estado(),
                    "Los campos ausentes, repetidos o desconocidos deben rechazarse");
            if (!caso[1].isEmpty()) {
                comprobarNoRefleja(respuesta, caso[1],
                        "Un error de campos no debe reflejar ninguna llave enviada");
            }
            comprobarQueNoExponeLlaves(respuesta, "Un error de formulario del hotel");
            Aserciones.iguales(null, respuesta.encabezado("Set-Cookie"),
                    "Un formulario invalido no debe crear otra sesion");
        }
        Respuesta gestion = cliente.get("/hotel", cookie);
        Aserciones.iguales(303, gestion.estado(),
                "Ningun formulario invalido debe autorizar el hotel");
    }

    private static void cierraLaSesion(ClienteHttpPrueba cliente) throws Exception {
        String cookie = iniciarSesion(cliente);
        autorizarHotel(cliente, cookie, "valle-dorado");
        Respuesta salida = cliente.post("/salir", "", null, cookie);
        Aserciones.iguales(303, salida.estado(), "Cerrar sesion debe redirigir");
        Aserciones.iguales("/", salida.encabezado("Location"),
                "Debe regresar al acceso");
        Aserciones.contiene(salida.encabezado("Set-Cookie"), "Max-Age=0",
                "Debe vencer la cookie en el navegador");
        Aserciones.iguales(303, cliente.get("/panel", cookie).estado(),
                "El token cerrado no debe reutilizarse en el panel");
        Respuesta gestion = cliente.get("/hotel", cookie);
        Aserciones.iguales(303, gestion.estado(),
                "El token cerrado tampoco debe reutilizar su hotel");
        Aserciones.iguales("/", gestion.encabezado("Location"),
                "Debe exigir un nuevo acceso global");
    }

    private static void sirveSoloRecursosPublicosConocidos(ClienteHttpPrueba cliente)
            throws Exception {
        Respuesta css = cliente.get("/publico/estilos/gestera.css");
        Aserciones.iguales(200, css.estado(), "Debe servir la hoja de estilos");
        Aserciones.contiene(css.encabezado("Content-Type"), "text/css",
                "Debe declarar el tipo CSS");
        Aserciones.verdadero(css.encabezado("Cache-Control") != null,
                "Los recursos deben declarar su politica de cache");

        for (String ruta : new String[] {
            "/publico/scripts/acceso.js",
            "/publico/scripts/hoteles.js",
            "/publico/scripts/gestion.js",
            "/publico/scripts/habitaciones.js",
            "/publico/scripts/configuracion-hotel.js",
            "/publico/scripts/areas-comunes.js",
            "/publico/scripts/servicio-habitacion.js",
            "/publico/scripts/reportes.js"
        }) {
            Respuesta javascript = cliente.get(ruta);
            Aserciones.iguales(200, javascript.estado(),
                    "Debe servir el JavaScript registrado: " + ruta);
            Aserciones.contiene(javascript.encabezado("Content-Type"), "text/javascript",
                    "Debe declarar JavaScript");
        }
        Aserciones.iguales(404, cliente.get("/publico/no-existe.css").estado(),
                "No debe servir archivos fuera del catalogo");
    }

    private static void restringeLosMetodosHttp(ClienteHttpPrueba cliente) throws Exception {
        comprobarMetodo(cliente.get("/acceso"), 405, "POST",
                "El endpoint de acceso solo admite POST");
        comprobarMetodo(cliente.get("/salir"), 405, "POST",
                "El cierre de sesion solo admite POST");
        comprobarMetodo(cliente.post("/panel", "", null, null), 405, "GET",
                "El selector solo admite GET");
        comprobarMetodo(cliente.get("/hotel/acceso"), 405, "POST",
                "El acceso al hotel solo admite POST");
        comprobarMetodo(cliente.get("/hotel/llave"), 405, "POST",
                "El cambio de llave solo admite POST");
        comprobarMetodo(cliente.get("/hotel/configuracion/llave"), 405, "POST",
                "El cambio de llave desde configuracion solo admite POST");
        comprobarMetodo(cliente.post("/hotel", "", null, null), 405, "GET",
                "La gestion del hotel solo admite GET");
        comprobarMetodo(cliente.post("/hotel/habitaciones", "", null, null), 405, "GET",
                "La vista de habitaciones solo admite GET");
        comprobarMetodo(cliente.post("/hotel/limpieza", "", null, null), 405, "GET",
                "La vista de limpieza solo admite GET");
        comprobarMetodo(cliente.post("/hotel/huespedes", "", null, null), 405, "GET",
                "El registro de huespedes solo admite GET");
        comprobarMetodo(
                cliente.post("/hotel/servicio-habitacion", "", null, null), 405, "GET",
                "Servicio al cuarto solo se consulta mediante GET");
        comprobarMetodo(
                cliente.post("/hotel/areas-comunes", "", null, null), 405, "GET",
                "Áreas comunes solo se consulta mediante GET");
        comprobarMetodo(cliente.get("/hotel/areas-comunes/registrar"), 405, "POST",
                "El uso de un área común solo se registra mediante POST");
        comprobarMetodo(cliente.get("/hotel/servicio-habitacion/pedidos"), 405, "POST",
                "Los pedidos solo se crean mediante POST");
        comprobarMetodo(
                cliente.get("/hotel/servicio-habitacion/pedidos/estado"), 405, "POST",
                "Los estados de pedido solo se modifican mediante POST");
        comprobarMetodo(cliente.post("/hotel/configuracion", "", null, null), 405, "GET",
                "La configuracion separada solo admite GET");
        comprobarMetodo(cliente.get("/hotel/configuracion/guardar"), 405, "POST",
                "El guardado general solo admite POST");
        comprobarMetodo(cliente.get("/hotel/configuracion/habitaciones"), 405, "POST",
                "La cantidad de habitaciones solo se modifica mediante POST");
        comprobarMetodo(cliente.get("/hotel/configuracion/secciones/guardar"), 405, "POST",
                "Las secciones solo se guardan mediante POST");
        comprobarMetodo(cliente.get("/hotel/configuracion/secciones/eliminar"), 405, "POST",
                "Las secciones solo se eliminan mediante POST");
        comprobarMetodo(cliente.get("/hotel/habitaciones/actualizar"), 405, "POST",
                "Los datos de una habitacion solo se modifican mediante POST");
        comprobarMetodo(cliente.get("/hotel/habitaciones/checkout"), 405, "POST",
                "La confirmacion del check-out solo se registra mediante POST");
    }

    private static void validaElFormularioDeAccesoGeneral(ClienteHttpPrueba cliente)
            throws Exception {
        Respuesta tipoIncorrecto = cliente.post("/acceso", "llave=" + LLAVE,
                "application/json", null);
        Aserciones.iguales(415, tipoIncorrecto.estado(),
                "Debe exigir formularios URL encoded");
        Respuesta malCodificado = cliente.postFormulario("/acceso", "llave=%ZZ");
        Aserciones.iguales(400, malCodificado.estado(),
                "Debe rechazar escapes porcentuales invalidos");
        Respuesta repetido = cliente.postFormulario("/acceso", "llave=una&llave=otra");
        Aserciones.iguales(400, repetido.estado(),
                "Debe rechazar campos de llave repetidos");
    }

    private static void validaElFormularioDelHotel(ClienteHttpPrueba cliente) throws Exception {
        String cookie = iniciarSesion(cliente);
        String secretoTipo = "secreto-tipo-hotel";
        Respuesta tipoIncorrecto = cliente.post(
                "/hotel/acceso",
                formularioHotel("sol-pucusana", secretoTipo),
                "application/json",
                cookie);
        Aserciones.iguales(415, tipoIncorrecto.estado(),
                "El hotel debe exigir formularios URL encoded");
        comprobarNoRefleja(tipoIncorrecto, secretoTipo,
                "El error de tipo no debe reflejar la llave");

        String secretoCodificacion = "secreto-codificacion";
        Respuesta malCodificado = cliente.postFormulario(
                "/hotel/acceso",
                "hotel=sol-pucusana&llaveHotel=" + secretoCodificacion + "%ZZ",
                cookie);
        Aserciones.iguales(400, malCodificado.estado(),
                "El hotel debe rechazar escapes porcentuales invalidos");
        comprobarNoRefleja(malCodificado, secretoCodificacion,
                "El error de codificacion no debe reflejar la llave");

        String marcadorGrande = "secreto-formulario-hotel-grande";
        Respuesta enorme = cliente.postFormulario(
                "/hotel/acceso",
                formularioHotel("sol-pucusana", marcadorGrande + "x".repeat(4_096)),
                cookie);
        Aserciones.iguales(413, enorme.estado(),
                "Debe limitar el formulario de acceso al hotel");
        comprobarNoRefleja(enorme, marcadorGrande,
                "El error de tamano no debe reflejar la llave");
        Aserciones.iguales(303, cliente.get("/hotel", cookie).estado(),
                "Los errores tecnicos no deben autorizar el hotel");
    }

    private static void limitaElTamanoYRespondeRutasDesconocidas(ClienteHttpPrueba cliente)
            throws Exception {
        Respuesta enorme = cliente.postFormulario("/acceso", "llave=" + "x".repeat(4_096));
        Aserciones.iguales(413, enorme.estado(),
                "Debe limitar el cuerpo del formulario global");
        for (String ruta : new String[] {
            "/ruta-desconocida", "/hotel/desconocido", "/hotel/acceso/extra",
            "/hotel/llave/extra", "/hotel/habitaciones/extra", "/hotel/limpieza/extra",
            "/hotel/configuracion/extra", "/hotel/habitaciones/configurar",
            "/hotel/habitaciones/checkout/extra"
        }) {
            Respuesta desconocida = cliente.get(ruta);
            Aserciones.iguales(404, desconocida.estado(),
                    "Una ruta no registrada debe responder 404: " + ruta);
            Aserciones.noContieneIgnorandoMayusculas(
                    desconocida.cuerpo(), "exception",
                    "La respuesta 404 no debe filtrar detalles internos");
        }
    }

    private static void bloqueaIntentosConsecutivosDelAccesoGeneral() throws Exception {
        try (ServidorGestera servidor = nuevoServidor(false)) {
            servidor.iniciar();
            ClienteHttpPrueba cliente = new ClienteHttpPrueba(servidor.direccionBase());
            for (int intento = 0; intento < 5; intento++) {
                String enviada = "incorrecta-global-" + intento;
                Respuesta respuesta = cliente.postFormulario("/acceso", "llave=" + enviada);
                Aserciones.iguales(401, respuesta.estado(),
                        "Los primeros cinco fallos globales deben ser genericos");
                comprobarNoRefleja(respuesta, enviada,
                        "Un fallo global no debe reflejar la llave");
            }
            String enviada = "otro-intento-global-secreto";
            Respuesta bloqueada = cliente.postFormulario("/acceso", "llave=" + enviada);
            Aserciones.iguales(429, bloqueada.estado(),
                    "El sexto intento global debe bloquearse");
            Aserciones.contiene(bloqueada.cuerpo(), "Demasiados intentos",
                    "Debe explicar el bloqueo temporal global");
            comprobarNoRefleja(bloqueada, enviada,
                    "El bloqueo global no debe reflejar la llave");
        }
    }

    private static void bloqueaIntentosConsecutivosDelHotel() throws Exception {
        try (ServidorGestera servidor = nuevoServidor(false)) {
            servidor.iniciar();
            ClienteHttpPrueba cliente = new ClienteHttpPrueba(servidor.direccionBase());
            String cookie = iniciarSesion(cliente);
            for (int intento = 0; intento < 5; intento++) {
                String enviada = "incorrecta-hotel-" + intento;
                Respuesta respuesta = cliente.postFormulario(
                        "/hotel/acceso", formularioHotel("sol-pucusana", enviada), cookie);
                Aserciones.iguales(401, respuesta.estado(),
                        "Los primeros cinco fallos del hotel deben ser genericos");
                comprobarNoRefleja(respuesta, enviada,
                        "Un fallo del hotel no debe reflejar la llave");
            }
            String enviada = "otro-intento-hotel-secreto";
            Respuesta bloqueada = cliente.postFormulario(
                    "/hotel/acceso", formularioHotel("sol-pucusana", enviada), cookie);
            Aserciones.iguales(429, bloqueada.estado(),
                    "El sexto intento del hotel debe bloquearse");
            Aserciones.contiene(bloqueada.cuerpo(), "Demasiados intentos",
                    "Debe explicar el bloqueo temporal del hotel");
            comprobarNoRefleja(bloqueada, enviada,
                    "El bloqueo del hotel no debe reflejar la llave");
            Aserciones.iguales(303, cliente.get("/hotel", cookie).estado(),
                    "El bloqueo no debe conceder acceso a la gestion");
        }
    }

    private static void pruebaElFlujoCompletoDeCambioDeLlave() throws Exception {
        try (ServidorGestera servidor = nuevoServidor(false)) {
            servidor.iniciar();
            ClienteHttpPrueba cliente = new ClienteHttpPrueba(servidor.direccionBase());
            protegeElCambioDeLlave(cliente);
            cambiaLaLlaveYActualizaElEstado(cliente);
            aceptaSoloLaNuevaLlaveEnElHotelActualizado(cliente);
            rechazaCambiosInvalidosSinFiltrarSecretos(cliente);
            validaElFormularioTecnicoDelCambio(cliente);
        }
    }

    private static void protegeElCambioDeLlave(ClienteHttpPrueba cliente) throws Exception {
        String secreto = "SecretoSinSesion8";
        Respuesta sinSesion = cliente.postFormulario(
                "/hotel/llave", formularioCambio(secreto, secreto));
        Aserciones.iguales(303, sinSesion.estado(),
                "El cambio de llave debe exigir una sesion global");
        Aserciones.iguales("/", sinSesion.encabezado("Location"),
                "Sin sesion debe regresar al acceso global");
        Aserciones.contiene(sinSesion.encabezado("Set-Cookie"), "Max-Age=0",
                "Una sesion inexistente debe limpiar la cookie");
        comprobarNoRefleja(sinSesion, secreto,
                "Una solicitud sin sesion no debe reflejar sus secretos");

        String cookie = iniciarSesion(cliente);
        String sinSeleccion = "SecretoSinHotel8";
        Respuesta respuesta = cliente.postFormulario(
                "/hotel/llave", formularioCambio(sinSeleccion, sinSeleccion), cookie);
        Aserciones.iguales(303, respuesta.estado(),
                "Una sesion sin hotel seleccionado no debe cambiar llaves");
        Aserciones.iguales("/panel", respuesta.encabezado("Location"),
                "Debe volver al selector para autorizar un hotel");
        comprobarNoRefleja(respuesta, sinSeleccion,
                "No debe procesar secretos sin autorizacion de hotel");
    }

    private static void cambiaLaLlaveYActualizaElEstado(ClienteHttpPrueba cliente)
            throws Exception {
        String sesionPrincipal = iniciarSesionConHotel(cliente, "sol-pucusana", LLAVE_HOTEL);
        String sesionPrevia = iniciarSesionConHotel(cliente, "sol-pucusana", LLAVE_HOTEL);

        Respuesta inicial = cliente.get("/hotel", sesionPrincipal);
        Aserciones.iguales(200, inicial.estado(),
                "El hotel debe abrir la gestion antes del primer cambio");
        Aserciones.contiene(inicial.cuerpo(), "Cambio de llave de acceso",
                "La opcion de cambio debe mostrarse desde el inicio");
        Aserciones.contiene(inicial.cuerpo(), "Configuraci\u00f3n requerida",
                "La credencial predeterminada debe requerir configuracion");
        Aserciones.contiene(inicial.cuerpo(), "opcion-cambio-llave--requerida",
                "La opcion inicial debe estar destacada");

        Respuesta cambio = cliente.postFormulario(
                "/hotel/llave",
                formularioCambio(LLAVE_SOL_ACTUALIZADA, LLAVE_SOL_ACTUALIZADA),
                sesionPrincipal);
        Aserciones.iguales(303, cambio.estado(),
                "Una llave valida debe actualizarse mediante POST");
        Aserciones.iguales("/hotel", cambio.encabezado("Location"),
                "El cambio exitoso debe regresar a la gestion");
        Aserciones.iguales(null, cambio.encabezado("Set-Cookie"),
                "El cambio debe conservar la cookie de sesion");
        comprobarNoRefleja(cambio, LLAVE_SOL_ACTUALIZADA,
                "La redireccion no debe reflejar la llave nueva");

        Respuesta actualizada = cliente.get("/hotel", sesionPrincipal);
        Aserciones.iguales(200, actualizada.estado(),
                "La sesion que cambio la llave debe conservar acceso");
        Aserciones.noContieneIgnorandoMayusculas(
                actualizada.cuerpo(), "Cambio de llave de acceso",
                "La opcion debe desaparecer despues de configurarla");
        Aserciones.noContieneIgnorandoMayusculas(
                actualizada.cuerpo(), "opcion-cambio-llave--requerida",
                "La opcion debe perder el destacado despues del cambio");
        Aserciones.noContieneIgnorandoMayusculas(
                actualizada.cuerpo(), "Configuraci\u00f3n requerida",
                "El aviso inicial debe desaparecer");
        comprobarNoRefleja(actualizada, LLAVE_SOL_ACTUALIZADA,
                "La gestion nunca debe renderizar la llave nueva");
        comprobarQueNoExponeLlaves(actualizada,
                "La gestion posterior al cambio");

        Respuesta autorizacionAnterior = cliente.get("/hotel", sesionPrevia);
        Aserciones.iguales(303, autorizacionAnterior.estado(),
                "Cambiar una llave debe invalidar autorizaciones anteriores del hotel");
        Aserciones.iguales("/panel", autorizacionAnterior.encabezado("Location"),
                "Una autorizacion desactualizada debe volver al selector");
    }

    private static void aceptaSoloLaNuevaLlaveEnElHotelActualizado(
            ClienteHttpPrueba cliente) throws Exception {
        String sesionSol = iniciarSesion(cliente);
        Respuesta llaveAnterior = cliente.postFormulario(
                "/hotel/acceso",
                formularioHotel("sol-pucusana", LLAVE_HOTEL),
                sesionSol);
        Aserciones.iguales(401, llaveAnterior.estado(),
                "La llave predeterminada debe dejar de abrir el hotel actualizado");
        comprobarQueNoExponeLlaves(llaveAnterior,
                "El rechazo de la llave anterior");

        Respuesta llaveNueva = cliente.postFormulario(
                "/hotel/acceso",
                formularioHotel("sol-pucusana", LLAVE_SOL_ACTUALIZADA),
                sesionSol);
        Aserciones.iguales(303, llaveNueva.estado(),
                "La nueva llave debe abrir el hotel actualizado");
        Aserciones.iguales("/hotel", llaveNueva.encabezado("Location"),
                "La nueva credencial debe llevar a la gestion");

        String sesionBrisa = iniciarSesion(cliente);
        Respuesta llaveAjena = cliente.postFormulario(
                "/hotel/acceso",
                formularioHotel("brisa-marina", LLAVE_SOL_ACTUALIZADA),
                sesionBrisa);
        Aserciones.iguales(401, llaveAjena.estado(),
                "La llave de Sol no debe abrir otro hotel");
        comprobarNoRefleja(llaveAjena, LLAVE_SOL_ACTUALIZADA,
                "El rechazo del otro hotel no debe reflejar la llave");

        Respuesta llavePredeterminada = cliente.postFormulario(
                "/hotel/acceso",
                formularioHotel("brisa-marina", LLAVE_HOTEL),
                sesionBrisa);
        Aserciones.iguales(303, llavePredeterminada.estado(),
                "Los otros hoteles deben conservar la llave predeterminada");
        Respuesta gestionBrisa = cliente.get("/hotel", sesionBrisa);
        Aserciones.contiene(gestionBrisa.cuerpo(), "Hotel Brisa Marina",
                "La credencial independiente debe abrir el hotel correcto");
        Aserciones.contiene(gestionBrisa.cuerpo(), "Configuraci\u00f3n requerida",
                "Otro hotel debe conservar su estado inicial");
    }

    private static void rechazaCambiosInvalidosSinFiltrarSecretos(
            ClienteHttpPrueba cliente) throws Exception {
        String cookie = iniciarSesionConHotel(cliente, "valle-dorado", LLAVE_HOTEL);
        String larga = "A" + "b".repeat(14) + "1";
        String[][] casos = {
            {"", "", "Ingresa la nueva llave de acceso."},
            {"CortaA1", "CortaA1", "entre 8 y 15 caracteres"},
            {larga, larga, "entre 8 y 15 caracteres"},
            {"sincapital8", "sincapital8", "al menos una letra may\u00fascula"},
            {"SinNumero", "SinNumero", "al menos un n\u00famero"},
            {"Confirmar8", "", "Confirma la nueva llave de acceso."},
            {"Coincide8", "Diferente9", "Las llaves ingresadas no coinciden."}
        };
        for (String[] caso : casos) {
            Respuesta respuesta = cliente.postFormulario(
                    "/hotel/llave", formularioCambio(caso[0], caso[1]), cookie);
            Aserciones.iguales(400, respuesta.estado(),
                    "Una regla incumplida debe responder como solicitud invalida");
            Aserciones.contiene(respuesta.cuerpo(), caso[2],
                    "Debe explicar la regla de cambio incumplida");
            Aserciones.contiene(respuesta.cuerpo(), "Configuraci\u00f3n requerida",
                    "Un rechazo debe conservar el estado inicial");
            if (!caso[0].isEmpty()) {
                comprobarNoRefleja(respuesta, caso[0],
                        "El error no debe reflejar la llave propuesta");
            }
            if (!caso[1].isEmpty() && !caso[1].equals(caso[0])) {
                comprobarNoRefleja(respuesta, caso[1],
                        "El error no debe reflejar la confirmacion");
            }
            comprobarQueNoExponeLlaves(respuesta,
                    "Un rechazo de cambio de llave");
        }

        String sesionNueva = iniciarSesion(cliente);
        Respuesta conservada = cliente.postFormulario(
                "/hotel/acceso",
                formularioHotel("valle-dorado", LLAVE_HOTEL),
                sesionNueva);
        Aserciones.iguales(303, conservada.estado(),
                "Los cambios rechazados deben conservar la credencial anterior");
    }

    private static void validaElFormularioTecnicoDelCambio(ClienteHttpPrueba cliente)
            throws Exception {
        String cookie = iniciarSesionConHotel(cliente, "costa-serena", LLAVE_HOTEL);
        String[][] casos = {
            {"nuevaLlave=PrimeraA1&nuevaLlave=SecretoDuplicado8"
                    + "&confirmacionLlave=PrimeraA1", "400", "SecretoDuplicado8"},
            {"nuevaLlave=PrimeraA1&confirmacionLlave=PrimeraA1"
                    + "&confirmacionLlave=SecretoConfirmacion8", "400", "SecretoConfirmacion8"},
            {"nuevaLlave=Secreto%ZZ&confirmacionLlave=SecretoA8",
                    "400", "Secreto"}
        };
        for (String[] caso : casos) {
            Respuesta respuesta = cliente.postFormulario("/hotel/llave", caso[0], cookie);
            Aserciones.iguales(Integer.parseInt(caso[1]), respuesta.estado(),
                    "Duplicados y codificacion invalida deben rechazarse");
            comprobarNoRefleja(respuesta, caso[2],
                    "Un error tecnico no debe reflejar secretos");
        }

        String secretoTipo = "SecretoTipoA8";
        Respuesta tipo = cliente.post(
                "/hotel/llave",
                formularioCambio(secretoTipo, secretoTipo),
                "application/json",
                cookie);
        Aserciones.iguales(415, tipo.estado(),
                "El cambio debe exigir formularios URL encoded");
        comprobarNoRefleja(tipo, secretoTipo,
                "El error de tipo no debe reflejar la llave");

        String marcadorGrande = "SecretoGrandeA8";
        Respuesta enorme = cliente.postFormulario(
                "/hotel/llave",
                formularioCambio(marcadorGrande + "x".repeat(4_096), marcadorGrande),
                cookie);
        Aserciones.iguales(413, enorme.estado(),
                "Debe limitar el tamano del formulario de cambio");
        comprobarNoRefleja(enorme, marcadorGrande,
                "El error de tamano no debe reflejar la llave");

        Respuesta gestion = cliente.get("/hotel", cookie);
        Aserciones.iguales(200, gestion.estado(),
                "Los errores tecnicos no deben revocar la autorizacion actual");
        Aserciones.contiene(gestion.cuerpo(), "Configuraci\u00f3n requerida",
                "Los errores tecnicos no deben completar el cambio");

        String sesionNueva = iniciarSesion(cliente);
        Aserciones.iguales(
                303,
                cliente.postFormulario(
                        "/hotel/acceso",
                        formularioHotel("costa-serena", LLAVE_HOTEL),
                        sesionNueva).estado(),
                "Los errores tecnicos deben conservar la llave anterior");
    }

    private static void pruebaElFlujoCompletoDeHabitaciones() throws Exception {
        try (ServidorGestera servidor = nuevoServidor(false)) {
            servidor.iniciar();
            ClienteHttpPrueba cliente = new ClienteHttpPrueba(servidor.direccionBase());
            protegeLaConfiguracionYLasHabitaciones(cliente);
            String sesionSol = iniciarSesionConHotel(
                    cliente, "sol-pucusana", LLAVE_HOTEL);
            muestraLaConfiguracionSeparadaYElInventario(cliente, sesionSol);
            configuraTarifasYCantidadSinMezclarHoteles(cliente, sesionSol);
            aplicaServiciosYHorariosAutomaticos(cliente, sesionSol);
            compruebaRegistroAutomaticoDeHuespedes(cliente, sesionSol);
            registraVisitantesDeAreasComunes(cliente, sesionSol);
            gestionaPedidosDeServicioAlCuarto(cliente, sesionSol);
            ajustaYConfirmaCheckOutsVencidos(cliente, sesionSol);
            impideReducirHabitacionesActivas(cliente, sesionSol);
            rechazaFormulariosInvalidosSinMutarDatos(cliente, sesionSol);
            cambiaLaLlaveDesdeConfiguracion(cliente, sesionSol);
        }
    }

    private static void protegeLaConfiguracionYLasHabitaciones(
            ClienteHttpPrueba cliente) throws Exception {
        for (String ruta : new String[] {
            "/hotel/habitaciones", "/hotel/limpieza", "/hotel/configuracion",
            "/hotel/huespedes", "/hotel/servicio-habitacion", "/hotel/areas-comunes"
        }) {
            Respuesta sinSesion = cliente.get(ruta);
            Aserciones.iguales(303, sinSesion.estado(),
                    "Las vistas del hotel deben exigir una sesion global");
            Aserciones.iguales("/", sinSesion.encabezado("Location"),
                    "Sin sesion deben volver al acceso global");
        }

        String[][] operaciones = {
            {"/hotel/configuracion/guardar", formularioConfiguracionGeneral(
                    "12", seccionesIniciales(), null)},
            {"/hotel/configuracion/habitaciones", "cantidadHabitaciones=15"},
            {"/hotel/configuracion/secciones/guardar",
                    formularioSeccion("", "%23123456", "Privada", "70", "130")},
            {"/hotel/configuracion/secciones/eliminar", "idSeccion=seccion-1"},
            {"/hotel/habitaciones/actualizar",
                    formularioHabitacion(
                            "1", "seccion-1", "seis_horas", "disponible", "false",
                            "normal")},
            {"/hotel/habitaciones/checkout",
                    "numeroHabitacion=1&confirmado=true&modo=normal"},
            {"/hotel/servicio-habitacion/pedidos",
                    "numeroHabitacion=1&producto=agua-mineral&cantidad=1"},
            {"/hotel/servicio-habitacion/pedidos/estado",
                    "pedidoId=1&estado=en_preparacion"},
            {"/hotel/areas-comunes/registrar",
                    "area=sauna&entrada=2026-09-23T10%3A00&salida=2026-09-23T11%3A00"
                            + "&cantidadHuespedes=1&huesped.0.nombre=Ana+Torres"
                            + "&huesped.0.tipoDocumento=dni&huesped.0.numeroDocumento=12345678"}
        };
        for (String[] operacion : operaciones) {
            Respuesta sinSesion = cliente.postFormulario(operacion[0], operacion[1]);
            Aserciones.iguales(303, sinSesion.estado(),
                    "Toda modificacion del hotel debe exigir sesion global");
            Aserciones.iguales("/", sinSesion.encabezado("Location"),
                    "Un POST sin sesion debe volver al acceso global");
        }

        String cookieGlobal = iniciarSesion(cliente);
        for (String ruta : new String[] {
            "/hotel/habitaciones", "/hotel/limpieza", "/hotel/configuracion",
            "/hotel/huespedes", "/hotel/servicio-habitacion", "/hotel/areas-comunes"
        }) {
            Respuesta sinHotel = cliente.get(ruta, cookieGlobal);
            Aserciones.iguales(303, sinHotel.estado(),
                    "Las vistas deben exigir un hotel autorizado");
            Aserciones.iguales("/panel", sinHotel.encabezado("Location"),
                    "Una sesion sin hotel debe volver al selector");
        }
        for (String[] operacion : operaciones) {
            Respuesta sinHotel = cliente.postFormulario(
                    operacion[0], operacion[1], cookieGlobal);
            Aserciones.iguales(303, sinHotel.estado(),
                    "Toda modificacion debe exigir autorizacion del hotel");
            Aserciones.iguales("/panel", sinHotel.encabezado("Location"),
                    "Un POST sin hotel debe volver al selector");
        }
    }

    private static void muestraLaConfiguracionSeparadaYElInventario(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta habitaciones = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales(200, habitaciones.estado(),
                "El hotel autorizado debe abrir sus habitaciones");
        Aserciones.contiene(habitaciones.cuerpo(), "Hotel El Sol de Pucusana",
                "La vista debe identificar el hotel autorizado");
        Aserciones.contiene(
                habitaciones.cuerpo(), "href=\"/hotel/configuracion\"",
                "La tuerquita debe abrir una configuracion separada");
        Aserciones.iguales(12,
                contarOcurrencias(habitaciones.cuerpo(), "class=\"tarjeta-habitacion\""),
                "Cada hotel debe iniciar con doce tarjetas");
        Aserciones.noContieneIgnorandoMayusculas(
                habitaciones.cuerpo(),
                "action=\"/hotel/configuracion/habitaciones\"",
                "La cantidad no debe configurarse dentro del inventario");

        Respuesta configuracion = cliente.get("/hotel/configuracion", cookie);
        Aserciones.iguales(200, configuracion.estado(),
                "La tuerquita debe abrir la configuracion del hotel");
        Aserciones.contiene(configuracion.cuerpo(), "name=\"cantidadHabitaciones\"",
                "La configuracion debe incluir la cantidad");
        Aserciones.contiene(
                configuracion.cuerpo(),
                "action=\"/hotel/configuracion/guardar\"",
                "Cantidad y colores deben conservar el guardado general");
        Aserciones.contiene(
                configuracion.cuerpo(),
                "Guardar todos los cambios",
                "La configuracion debe ofrecer un boton general");
        Aserciones.contiene(
                configuracion.cuerpo(),
                "data-ruta-guardado=\"/hotel/configuracion/habitaciones\"",
                "La interfaz debe ofrecer el guardado aislado de cantidad");
        Aserciones.contiene(
                configuracion.cuerpo(),
                "data-ruta-guardado=\"/hotel/configuracion/secciones/guardar\"",
                "Cada tarjeta debe enlazar su guardado individual");
        Aserciones.contiene(
                configuracion.cuerpo(),
                "data-bloque-anadir-seccion",
                "La tarjeta nueva debe mostrar su bloque de guardado inmediato");
        Aserciones.contiene(
                configuracion.cuerpo(),
                "aria-label=\"Añadir nueva sección\"",
                "La accion de alta debe distinguirse de editar una seccion registrada");
        for (String campo : new String[] {
            "id", "accion", "color", "nombre", "precio6Horas", "precioDiaCompleto"
        }) {
            Aserciones.contiene(
                    configuracion.cuerpo(), "name=\"seccion.0." + campo + "\"",
                    "Falta el campo de configuracion: " + campo);
        }
        Aserciones.contiene(configuracion.cuerpo(), "type=\"color\"",
                "Debe ofrecer un selector libre de color");
        comprobarQueNoExponeLlaves(configuracion, "La configuracion del hotel");

        Respuesta limpieza = cliente.get("/hotel/limpieza", cookie);
        Aserciones.iguales(200, limpieza.estado(),
                "El hotel autorizado debe abrir la vista de limpieza");
        Aserciones.iguales(0,
                contarOcurrencias(limpieza.cuerpo(), "class=\"tarjeta-habitacion\""),
                "Al inicio no debe haber habitaciones por limpiar");
    }

    private static void configuraTarifasYCantidadSinMezclarHoteles(
            ClienteHttpPrueba cliente, String sesionSol) throws Exception {
        Respuesta guardadoGeneral = cliente.postFormulario(
                "/hotel/configuracion/guardar",
                formularioConfiguracionGeneral(
                        "15",
                        seccionesIniciales(),
                        new String[] {"%23123456", "Ejecutiva", "75.50", "140.00"})
                        + "&hotel=brisa-marina",
                sesionSol);
        Aserciones.iguales(303, guardadoGeneral.estado(),
                "Cantidad, colores y una seccion nueva deben guardarse juntos");
        Aserciones.iguales("/hotel/configuracion", guardadoGeneral.encabezado("Location"),
                "El guardado general debe aplicar Post/Redirect/Get");

        Respuesta configuracionSol = cliente.get("/hotel/configuracion", sesionSol);
        for (String dato : new String[] {
            "seccion-4", "#123456", "Ejecutiva", "75.50", "140.00"
        }) {
            Aserciones.contiene(configuracionSol.cuerpo(), dato,
                    "La configuracion debe renderizar la nueva tarifa: " + dato);
        }
        Respuesta habitacionesSol = cliente.get("/hotel/habitaciones", sesionSol);
        Aserciones.iguales(15,
                contarOcurrencias(
                        habitacionesSol.cuerpo(), "class=\"tarjeta-habitacion\""),
                "Sol debe crecer hasta quince habitaciones");

        // Un campo hotel adicional nunca debe cambiar el hotel derivado de la sesion.
        Respuesta altaConHotelFalsificado = cliente.postFormulario(
                "/hotel/configuracion/secciones/guardar",
                formularioSeccion("", "%23654321", "Solo Sol", "80", "150")
                        + "&hotel=brisa-marina",
                sesionSol);
        Aserciones.iguales(303, altaConHotelFalsificado.estado(),
                "Los campos adicionales no deben alterar una configuracion valida");
        Aserciones.iguales(
                "/hotel/configuracion",
                altaConHotelFalsificado.encabezado("Location"),
                "El alta individual debe volver a la configuracion mediante Post/Redirect/Get");
        Respuesta configuracionTrasAlta = cliente.get(
                altaConHotelFalsificado.encabezado("Location"), sesionSol);
        Aserciones.contiene(
                configuracionTrasAlta.cuerpo(),
                "data-seccion-id=\"seccion-5\"",
                "Tras la redireccion, la nueva seccion debe aparecer como registrada");
        Aserciones.contiene(
                configuracionTrasAlta.cuerpo(),
                ">Solo Sol</h3>",
                "La seccion añadida debe mostrar su nombre registrado tras la redireccion");

        String sesionBrisa = iniciarSesionConHotel(
                cliente, "brisa-marina", LLAVE_HOTEL);
        Respuesta habitacionesBrisa = cliente.get("/hotel/habitaciones", sesionBrisa);
        Aserciones.iguales(12,
                contarOcurrencias(
                        habitacionesBrisa.cuerpo(), "class=\"tarjeta-habitacion\""),
                "Brisa debe conservar su cantidad independiente");
        Respuesta configuracionBrisa = cliente.get("/hotel/configuracion", sesionBrisa);
        Aserciones.noContieneIgnorandoMayusculas(
                configuracionBrisa.cuerpo(), "Ejecutiva",
                "Brisa no debe heredar la tarifa de Sol");
        Aserciones.noContieneIgnorandoMayusculas(
                configuracionBrisa.cuerpo(), "Solo Sol",
                "El hotel falsificado en el formulario debe ignorarse");
    }

    private static void aplicaServiciosYHorariosAutomaticos(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta seisHoras = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "1", "seccion-4", "seis_horas", "ocupada", "true", "normal")
                        + "&precio=9999.00&checkIn=2000-01-01T00%3A00"
                        + "&proximoCheckOut=2099-01-01T00%3A00",
                cookie);
        Aserciones.iguales(303, seisHoras.estado(),
                "Debe activar una estancia de seis horas");
        Aserciones.iguales("/hotel/habitaciones", seisHoras.encabezado("Location"),
                "La edicion normal debe volver al inventario");

        Respuesta inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("seccion-4",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-seccion"),
                "Debe guardar la seccion elegida");
        Aserciones.iguales("seis_horas",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-tipo-servicio"),
                "Debe guardar el servicio de seis horas");
        Aserciones.iguales("75.50",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-precio"),
                "El servidor debe aplicar la tarifa configurada e ignorar el precio cliente");
        Aserciones.iguales("#123456",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-color"),
                "La tarjeta debe tomar el color de su seccion");
        Aserciones.iguales("ocupada",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-estado"),
                "Debe marcar la habitacion como ocupada");
        LocalDateTime entrada = LocalDateTime.parse(
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-checkin"));
        LocalDateTime salidaSeis = LocalDateTime.parse(
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-checkout"));
        Aserciones.iguales(entrada.plusHours(6), salidaSeis,
                "El check-out de seis horas debe calcularse en el servidor");
        Aserciones.falso(entrada.getYear() == 2000,
                "El servidor debe ignorar horarios enviados por el navegador");

        Respuesta diaCompleto = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "1", "seccion-4", "dia_completo", "ocupada", "true", "normal"),
                cookie);
        Aserciones.iguales(303, diaCompleto.estado(),
                "Debe cambiar a dia completo");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        LocalDateTime entradaConservada = LocalDateTime.parse(
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-checkin"));
        LocalDateTime salidaDia = LocalDateTime.parse(
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-checkout"));
        Aserciones.iguales(entrada, entradaConservada,
                "Cambiar servicio debe conservar la hora real de check-in");
        Aserciones.iguales(entrada.plusHours(24), salidaDia,
                "Dia completo debe recalcular la salida a veinticuatro horas");
        Aserciones.iguales("140.00",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-precio"),
                "Dia completo debe elegir automaticamente su tarifa");

        Respuesta pendiente = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "1", "seccion-4", "dia_completo",
                        "limpieza_pendiente", "true", "limpieza"),
                cookie);
        Aserciones.iguales(303, pendiente.estado(),
                "Debe enviar la habitacion a limpieza");
        Aserciones.iguales("/hotel/limpieza", pendiente.encabezado("Location"),
                "La edicion de limpieza debe conservar su contexto");
        Respuesta limpieza = cliente.get("/hotel/limpieza", cookie);
        Aserciones.contiene(
                limpieza.cuerpo(), "data-habitacion-numero=\"1\"",
                "Limpieza debe incluir la habitacion pendiente");
        Aserciones.iguales(entrada.toString(),
                atributoHabitacion(limpieza.cuerpo(), 1, "data-habitacion-checkin"),
                "Limpieza debe conservar el check-in calculado");

        Respuesta disponible = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "1", "seccion-4", "dia_completo",
                        "disponible", "true", "normal"),
                cookie);
        Aserciones.iguales(303, disponible.estado(),
                "Debe liberar la habitacion");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-checkin"),
                "Disponible debe limpiar el check-in");
        Aserciones.iguales("",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-checkout"),
                "Disponible debe limpiar el check-out");
        Aserciones.iguales("false",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-pagado"),
                "Disponible debe limpiar el pago");
        Aserciones.iguales("dia_completo",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-tipo-servicio"),
                "Disponible debe conservar el servicio para la vista previa");
        Aserciones.iguales("140.00",
                atributoHabitacion(inventario.cuerpo(), 1, "data-habitacion-precio"),
                "Disponible debe conservar la tarifa visible");
    }

    private static void compruebaRegistroAutomaticoDeHuespedes(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta registro = cliente.get("/hotel/huespedes", cookie);
        Aserciones.iguales(200, registro.estado(),
                "El hotel autorizado debe abrir su registro de huespedes");
        for (String dato : new String[] {
            "Ana Torres", "Luis Paredes", "12345678", "PE123456",
            "Día visitado", "Check-in", "Check-out", "Finalizada"
        }) {
            Aserciones.contiene(registro.cuerpo(), dato,
                    "El historial debe mostrar el dato registrado: " + dato);
        }
        for (String campo : new String[] {
            "nombre", "dia", "estuvoDesde", "estuvoHasta", "color"
        }) {
            Aserciones.contiene(registro.cuerpo(), "name=\"" + campo + "\"",
                    "La busqueda debe ofrecer el filtro: " + campo);
        }
        String dia = LocalDate.now(ZoneId.of("America/Lima")).toString();
        Respuesta filtrada = cliente.get(
                "/hotel/huespedes?nombre=Ana+Torres&dia=" + dia + "&color=%23123456",
                cookie);
        Aserciones.iguales(200, filtrada.estado(),
                "Los filtros combinados deben producir una consulta valida");
        Aserciones.contiene(filtrada.cuerpo(), "Ana Torres",
                "La coincidencia combinada debe permanecer visible");
        Aserciones.noContieneIgnorandoMayusculas(filtrada.cuerpo(), "Luis Paredes",
                "El nombre debe excluir registros que no coinciden");
        Respuesta intervaloInvalido = cliente.get(
                "/hotel/huespedes?estuvoDesde=2026-09-22T12%3A00"
                        + "&estuvoHasta=2026-09-21T12%3A00",
                cookie);
        Aserciones.iguales(400, intervaloInvalido.estado(),
                "El servidor debe rechazar intervalos invertidos");

        String sinHuespedes = "numeroHabitacion=4&idSeccion=seccion-4"
                + "&tipoServicio=seis_horas&estado=ocupada&pagado=false&modo=normal";
        Respuesta rechazada = cliente.postFormulario(
                "/hotel/habitaciones/actualizar", sinHuespedes, cookie);
        Aserciones.iguales(400, rechazada.estado(),
                "Una ocupacion sin huespedes debe rechazarse");
        Aserciones.iguales("disponible",
                atributoHabitacion(
                        cliente.get("/hotel/habitaciones", cookie).cuerpo(),
                        4,
                        "data-habitacion-estado"),
                "El rechazo no debe ocupar parcialmente la habitacion");
    }

    private static void registraVisitantesDeAreasComunes(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        ZoneId zonaPeru = ZoneId.of("America/Lima");
        LocalDateTime antesDeAbrir = LocalDateTime.now(zonaPeru)
                .truncatedTo(ChronoUnit.MINUTES);
        Respuesta pagina = cliente.get("/hotel/areas-comunes", cookie);
        LocalDateTime despuesDeAbrir = LocalDateTime.now(zonaPeru)
                .truncatedTo(ChronoUnit.MINUTES);
        Aserciones.iguales(200, pagina.estado(),
                "El hotel autorizado debe abrir sus áreas comunes");
        for (String area : new String[] {"Jacuzzi", "Sauna", "Área de juegos"}) {
            Aserciones.contiene(pagina.cuerpo(), area,
                    "Debe mostrar el espacio disponible: " + area);
        }
        Aserciones.iguales(3, contarOcurrencias(pagina.cuerpo(), "data-area-comun=\""),
                "Debe ofrecer exactamente las tres áreas comunes");
        Aserciones.contiene(pagina.cuerpo(), "Zona horaria de Perú activa",
                "El formulario debe confirmar la zona horaria aplicada");
        LocalDateTime entradaPredeterminada = fechaDeCampo(
                pagina.cuerpo(), "entrada-area");
        Aserciones.verdadero(
                !entradaPredeterminada.isBefore(antesDeAbrir)
                        && !entradaPredeterminada.isAfter(despuesDeAbrir),
                "El check-in debe iniciar con la hora actual de America/Lima");
        Aserciones.iguales(
                entradaPredeterminada.plusHours(1),
                fechaDeCampo(pagina.cuerpo(), "salida-area"),
                "La duración predeterminada de una hora debe calcular el check-out");

        String visitantes = "&cantidadHuespedes=2"
                + "&huesped.0.nombre=Maria+Lopez"
                + "&huesped.0.tipoDocumento=dni"
                + "&huesped.0.numeroDocumento=87654321"
                + "&huesped.1.nombre=Carlos+Ramos"
                + "&huesped.1.tipoDocumento=pasaporte"
                + "&huesped.1.numeroDocumento=AR123456";
        Respuesta registrada = cliente.postFormulario(
                "/hotel/areas-comunes/registrar",
                "area=sauna&entrada=2026-09-23T18%3A00"
                        + "&duracionHoras=3&salida=2026-09-24T18%3A00" + visitantes,
                cookie);
        Aserciones.iguales(303, registrada.estado(),
                "Un horario y visitantes válidos deben guardarse");
        Aserciones.iguales("/hotel/huespedes", registrada.encabezado("Location"),
                "El registro debe llevar al historial consolidado");

        Respuesta historial = cliente.get("/hotel/huespedes", cookie);
        for (String dato : new String[] {
            "Maria Lopez", "Carlos Ramos", "87654321", "AR123456", "Sauna",
            "23/09/2026 21:00", "Registrada"
        }) {
            Aserciones.contiene(historial.cuerpo(), dato,
                    "El historial debe mostrar el visitante del área: " + dato);
        }

        Respuesta duracionInvalida = cliente.postFormulario(
                "/hotel/areas-comunes/registrar",
                "area=jacuzzi&entrada=2026-09-23T20%3A00"
                        + "&duracionHoras=2" + visitantes,
                cookie);
        Aserciones.iguales(400, duracionInvalida.estado(),
                "No debe guardar una duración fuera de las opciones rápidas");
        Aserciones.contiene(duracionInvalida.cuerpo(),
                "Selecciona una duración de 1, 3 o 5 horas",
                "El rechazo debe explicar las duraciones permitidas");
    }

    private static void gestionaPedidosDeServicioAlCuarto(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta inicial = cliente.get("/hotel/servicio-habitacion", cookie);
        Aserciones.iguales(200, inicial.estado(),
                "El hotel autorizado debe abrir servicio al cuarto");
        Aserciones.contiene(inicial.cuerpo(), "Desayuno continental",
                "El módulo debe mostrar el catálogo controlado");
        Aserciones.contiene(
                inicial.cuerpo(),
                "action=\"/hotel/servicio-habitacion/pedidos\"",
                "El formulario debe apuntar al endpoint de pedidos");
        Aserciones.contiene(inicial.cuerpo(), "method=\"post\"",
                "Los pedidos deben enviarse por POST");
        Aserciones.contiene(inicial.cuerpo(), "data-abrir-catalogo",
                "El módulo debe ofrecer un botón para modificar el catálogo");
        Respuesta catalogoActualizado = cliente.postFormulario(
                "/hotel/servicio-habitacion/catalogo",
                "producto=agua-mineral&nombre=Agua+premium"
                        + "&categoria=Bebidas&precio=7.50",
                cookie);
        Aserciones.iguales(303, catalogoActualizado.estado(),
                "Un producto válido debe poder actualizarse");
        Aserciones.iguales(
                "/hotel/servicio-habitacion", catalogoActualizado.encabezado("Location"),
                "La edición del catálogo debe aplicar Post/Redirect/Get");
        Respuesta catalogoVisible = cliente.get("/hotel/servicio-habitacion", cookie);
        Aserciones.contiene(catalogoVisible.cuerpo(), "Agua premium",
                "El catálogo debe reflejar el nombre modificado");
        Aserciones.contiene(catalogoVisible.cuerpo(), "data-precio=\"7.50\"",
                "El catálogo debe reflejar el precio modificado");
        Aserciones.iguales(
                303,
                cliente.postFormulario(
                        "/hotel/servicio-habitacion/catalogo/categorias",
                        "categoria=Postres",
                        cookie).estado(),
                "Debe permitir agregar una categoría al hotel");
        Aserciones.iguales(
                303,
                cliente.postFormulario(
                        "/hotel/servicio-habitacion/catalogo/productos",
                        "nombre=Torta&categoria=Postres&precio=15.00",
                        cookie).estado(),
                "Debe permitir agregar un producto a la categoría nueva");
        Aserciones.contiene(
                cliente.get("/hotel/servicio-habitacion", cookie).cuerpo(),
                "data-nombre=\"Torta\"",
                "El producto agregado debe aparecer en el catálogo");
        Respuesta categoriaEnUso = cliente.postFormulario(
                "/hotel/servicio-habitacion/catalogo/categorias/eliminar",
                "categoria=Postres",
                cookie);
        Aserciones.iguales(400, categoriaEnUso.estado(),
                "No debe quitar una categoría con productos");
        Aserciones.contiene(categoriaEnUso.cuerpo(), "Reasigna o quita sus productos",
                "Debe explicar cómo liberar una categoría en uso");
        Aserciones.iguales(
                303,
                cliente.postFormulario(
                        "/hotel/servicio-habitacion/catalogo/productos/eliminar",
                        "producto=torta",
                        cookie).estado(),
                "Debe permitir quitar un producto del catálogo");
        Aserciones.iguales(
                303,
                cliente.postFormulario(
                        "/hotel/servicio-habitacion/catalogo/categorias/eliminar",
                        "categoria=Postres",
                        cookie).estado(),
                "Debe permitir quitar una categoría vacía");
        Aserciones.iguales(
                400,
                cliente.postFormulario(
                        "/hotel/servicio-habitacion/catalogo",
                        "producto=agua-mineral&nombre=Agua"
                                + "&categoria=Bebidas&precio=0",
                        cookie).estado(),
                "El servidor debe rechazar un precio de catálogo inválido");
        for (String filtro : new String[] {
            "name=\"fecha\"", "name=\"hora\"", "name=\"habitacion\""
        }) {
            Aserciones.contiene(inicial.cuerpo(), filtro,
                    "La cola debe exponer la busqueda por fecha, hora y habitacion");
        }

        Respuesta sinOcupar = cliente.postFormulario(
                "/hotel/servicio-habitacion/pedidos",
                "numeroHabitacion=5&producto=agua-mineral&cantidad=1",
                cookie);
        Aserciones.iguales(409, sinOcupar.estado(),
                "Una habitación disponible no debe recibir pedidos");

        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/habitaciones/actualizar",
                        formularioHabitacion(
                                "5", "seccion-4", "seis_horas", "ocupada", "true", "normal"),
                        cookie).estado(),
                "La prueba debe ocupar una habitación para solicitar servicio");

        Respuesta creado = cliente.postFormulario(
                "/hotel/servicio-habitacion/pedidos",
                "numeroHabitacion=5&producto=club-sandwich&cantidad=2"
                        + "&observaciones=Sin+mayonesa",
                cookie);
        Aserciones.iguales(303, creado.estado(), "Debe crear el pedido válido");
        Aserciones.iguales("/hotel/servicio-habitacion", creado.encabezado("Location"),
                "La creación debe aplicar Post/Redirect/Get");

        Respuesta cola = cliente.get("/hotel/servicio-habitacion", cookie);
        for (String dato : new String[] {
            "Pedido #1", "Habitación 05", "2 × Club sándwich", "Total S/ 64.00", "Sin mayonesa"
        }) {
            Aserciones.contiene(cola.cuerpo(), dato,
                    "La cola debe mostrar el dato calculado: " + dato);
        }
        Aserciones.contiene(
                cliente.get("/hotel/servicio-habitacion?habitacion=5", cookie).cuerpo(),
                "Pedido #1",
                "La busqueda debe encontrar el pedido por habitacion");
        Aserciones.noContieneIgnorandoMayusculas(
                cliente.get("/hotel/servicio-habitacion?habitacion=6", cookie).cuerpo(),
                "Pedido #1",
                "La busqueda debe excluir habitaciones que no coinciden");
        Aserciones.iguales(
                400,
                cliente.get("/hotel/servicio-habitacion?hora=99%3A99", cookie).estado(),
                "El servidor debe rechazar una hora de busqueda invalida");

        Respuesta preparando = cliente.postFormulario(
                "/hotel/servicio-habitacion/pedidos/estado",
                "pedidoId=1&estado=en_preparacion",
                cookie);
        Aserciones.iguales(303, preparando.estado(),
                "Un pedido pendiente debe pasar a preparación");
        Aserciones.contiene(
                cliente.get("/hotel/servicio-habitacion", cookie).cuerpo(),
                "En preparación",
                "La cola debe reflejar el nuevo estado");

        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/servicio-habitacion/pedidos/estado",
                        "pedidoId=1&estado=entregado",
                        cookie).estado(),
                "Un pedido en preparación debe entregarse");
        Aserciones.iguales(400,
                cliente.postFormulario(
                        "/hotel/servicio-habitacion/pedidos/estado",
                        "pedidoId=1&estado=cancelado",
                        cookie).estado(),
                "Un pedido entregado no debe reabrirse ni cancelarse");

        Respuesta reporte = cliente.get(
                "/hotel/reportes?periodo=dia&tipo=todos", cookie);
        Aserciones.iguales(200, reporte.estado(),
                "El sexto modulo debe abrir los reportes del hotel autorizado");
        Aserciones.contiene(reporte.cuerpo(), "Reportes de ingresos",
                "El reporte debe mostrar su encabezado");
        Aserciones.contiene(reporte.cuerpo(), "S/ 64.00",
                "Un pedido entregado debe incorporarse al reporte");
        Aserciones.iguales(
                400,
                cliente.get(
                        "/hotel/reportes?periodo=personalizado&tipo=todos"
                                + "&desde=2026-09-24&hasta=2026-09-20",
                        cookie).estado(),
                "El reporte debe rechazar rangos personalizados invertidos");
        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/habitaciones/actualizar",
                        formularioHabitacion(
                                "5", "seccion-4", "seis_horas", "disponible", "false", "normal"),
                        cookie).estado(),
                "La prueba debe liberar la habitación sin borrar el historial del pedido");
    }

    private static void cambiaLaLlaveDesdeConfiguracion(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta configuracion = cliente.get("/hotel/configuracion", cookie);
        Aserciones.contiene(
                configuracion.cuerpo(),
                "action=\"/hotel/configuracion/llave\" method=\"post\"",
                "La configuracion debe exponer el cambio de llave");
        Respuesta invalida = cliente.postFormulario(
                "/hotel/configuracion/llave",
                formularioCambio("ConfigHotel8", "NoCoincide9"),
                cookie);
        Aserciones.iguales(400, invalida.estado(),
                "La configuracion debe rechazar llaves que no coinciden");
        Aserciones.contiene(invalida.cuerpo(), "Las llaves ingresadas no coinciden.",
                "El error debe mostrarse dentro de la pagina de configuracion");
        Aserciones.contiene(
                invalida.cuerpo(),
                "action=\"/hotel/configuracion/llave\" method=\"post\"",
                "Un error debe conservar al usuario en el formulario de configuracion");
        comprobarNoRefleja(invalida, "NoCoincide9",
                "La configuracion no debe reflejar la confirmacion sensible");
        String nueva = "ConfigHotel8";
        Respuesta cambio = cliente.postFormulario(
                "/hotel/configuracion/llave", formularioCambio(nueva, nueva), cookie);
        Aserciones.iguales(303, cambio.estado(),
                "La llave debe poder cambiarse desde configuracion");
        Aserciones.iguales("/hotel/configuracion", cambio.encabezado("Location"),
                "El cambio debe conservar el contexto de configuracion");
        Aserciones.iguales(200,
                cliente.get("/hotel/configuracion", cookie).estado(),
                "La sesion que cambia la llave debe conservar autorizacion");
    }

    private static void ajustaYConfirmaCheckOutsVencidos(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta ocupacionImpaga = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "2", "seccion-4", "seis_horas", "ocupada", "true", "normal"),
                cookie);
        Aserciones.iguales(303, ocupacionImpaga.estado(),
                "Debe iniciar una estancia pagada con el horario automatico");
        Respuesta inventario = cliente.get("/hotel/habitaciones", cookie);
        LocalDateTime entradaImpaga = LocalDateTime.parse(
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-checkin"));
        LocalDateTime salidaAutomatica = LocalDateTime.parse(
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-checkout"));
        Aserciones.iguales(entradaImpaga.plusHours(6), salidaAutomatica,
                "La posibilidad de ajuste no debe cambiar el calculo automatico de seis horas");

        Object[][] ampliaciones = {
            {"1", "10.00", "1", "10.00", "85.50"},
            {"2", "7.25", "3", "17.25", "92.75"},
            {"4", "20.00", "7", "37.25", "112.75"}
        };
        for (Object[] ampliacion : ampliaciones) {
            Respuesta ampliada = cliente.postFormulario(
                    "/hotel/habitaciones/actualizar",
                    formularioHabitacion(
                            "2", "seccion-4", "seis_horas", "ocupada", "true", "normal")
                            + "&horasAdicionales=" + ampliacion[0]
                            + "&montoAdicional=" + ampliacion[1],
                    cookie);
            Aserciones.iguales(303, ampliada.estado(),
                    "Las opciones +1, +2 y +4 deben guardarse desde el panel existente");
            inventario = cliente.get("/hotel/habitaciones", cookie);
            int horasAcumuladas = Integer.parseInt((String) ampliacion[2]);
            Aserciones.iguales(
                    salidaAutomatica.plusHours(horasAcumuladas).toString(),
                    atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-checkout"),
                    "Cada POST debe sumar tiempo al check-out previamente guardado");
            Aserciones.iguales((String) ampliacion[2],
                    atributoHabitacion(
                            inventario.cuerpo(), 2, "data-habitacion-horas-adicionales"),
                    "La tarjeta debe publicar las horas acumuladas");
            Aserciones.iguales((String) ampliacion[3],
                    atributoHabitacion(
                            inventario.cuerpo(), 2, "data-habitacion-monto-adicional"),
                    "La tarjeta debe publicar el cargo adicional acumulado");
            Aserciones.iguales((String) ampliacion[4],
                    atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-monto-total"),
                    "El total publicado debe sumar la tarifa y todos los cargos");
            Aserciones.iguales("false",
                    atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-pagado"),
                    "Agregar un cargo debe volver a dejar pendiente una estancia pagada");
        }

        Respuesta ajusteManual = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "2", "seccion-4", "seis_horas", "ocupada", "false", "normal")
                        + "&ajusteManualCheckOut=true&proximoCheckOut="
                        + fechaFormulario(entradaImpaga),
                cookie);
        Aserciones.iguales(303, ajusteManual.estado(),
                "El ajuste manual explicito debe permitir preparar un vencimiento de prueba");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales(entradaImpaga.toString(),
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-checkout"),
                "El servidor debe guardar el check-out manual solicitado");
        Aserciones.iguales("false",
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-pagado"),
                "El ajuste de hora no debe marcar una habitacion como pagada");
        Aserciones.iguales("7",
                atributoHabitacion(
                        inventario.cuerpo(), 2, "data-habitacion-horas-adicionales"),
                "El ajuste manual no debe borrar el tiempo agregado");
        Aserciones.iguales("37.25",
                atributoHabitacion(
                        inventario.cuerpo(), 2, "data-habitacion-monto-adicional"),
                "El ajuste manual no debe borrar los cargos acumulados");
        Aserciones.iguales("112.75",
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-monto-total"),
                "El vencimiento de prueba debe conservar el total completo a cobrar");
        long epoch = Long.parseLong(
                atributoHabitacion(
                        inventario.cuerpo(), 2, "data-habitacion-checkout-epoch"));
        Aserciones.verdadero(epoch > 0,
                "La tarjeta debe publicar un instante valido para activar la alerta");

        Respuesta confirmado = cliente.postFormulario(
                "/hotel/habitaciones/checkout",
                "numeroHabitacion=2&confirmado=true&modo=normal",
                cookie);
        Aserciones.iguales(303, confirmado.estado(),
                "Debe registrar el check-out vencido aunque falte el pago");
        Aserciones.iguales("/hotel/habitaciones", confirmado.encabezado("Location"),
                "La confirmacion debe conservar el contexto del inventario");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("limpieza_pendiente",
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-estado"),
                "Confirmar la salida debe enviar automaticamente el cuarto a limpieza");
        Aserciones.iguales("false",
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-pagado"),
                "El pago pendiente debe conservarse despues del check-out");
        Aserciones.iguales("7",
                atributoHabitacion(
                        inventario.cuerpo(), 2, "data-habitacion-horas-adicionales"),
                "El check-out debe conservar las horas adicionales para limpieza");
        Aserciones.iguales("37.25",
                atributoHabitacion(
                        inventario.cuerpo(), 2, "data-habitacion-monto-adicional"),
                "El check-out debe conservar el cargo adicional pendiente");
        Aserciones.iguales("112.75",
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-monto-total"),
                "Limpieza debe recibir el total completo de la estancia");
        Respuesta limpieza = cliente.get("/hotel/limpieza", cookie);
        Aserciones.contiene(limpieza.cuerpo(), "data-habitacion-numero=\"2\"",
                "La salida confirmada debe aparecer en la vista de limpieza");

        Respuesta repetido = cliente.postFormulario(
                "/hotel/habitaciones/checkout",
                "numeroHabitacion=2&confirmado=true&modo=normal",
                cookie);
        Aserciones.iguales(409, repetido.estado(),
                "Un check-out ya confirmado no debe procesarse dos veces");

        Respuesta liberada = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "2", "seccion-4", "seis_horas", "disponible", "true", "normal"),
                cookie);
        Aserciones.iguales(303, liberada.estado(),
                "Debe poder liberar una habitacion despues de la limpieza pendiente");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("0",
                atributoHabitacion(
                        inventario.cuerpo(), 2, "data-habitacion-horas-adicionales"),
                "Liberar la habitacion debe reiniciar las horas acumuladas");
        Aserciones.iguales("0.00",
                atributoHabitacion(
                        inventario.cuerpo(), 2, "data-habitacion-monto-adicional"),
                "Liberar la habitacion debe reiniciar los cargos acumulados");
        Aserciones.iguales("75.50",
                atributoHabitacion(inventario.cuerpo(), 2, "data-habitacion-monto-total"),
                "Tras liberar, el total debe volver a la tarifa base visible");

        Respuesta ocupacionFutura = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "3", "seccion-4", "seis_horas", "ocupada", "true", "normal"),
                cookie);
        Aserciones.iguales(303, ocupacionFutura.estado(),
                "Debe preparar una estancia cuyo check-out aun no vence");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        LocalDateTime entradaFutura = LocalDateTime.parse(
                atributoHabitacion(inventario.cuerpo(), 3, "data-habitacion-checkin"));
        String salidaFutura = atributoHabitacion(
                inventario.cuerpo(), 3, "data-habitacion-checkout");
        Respuesta anticipado = cliente.postFormulario(
                "/hotel/habitaciones/checkout",
                "numeroHabitacion=3&confirmado=true&modo=normal",
                cookie);
        Aserciones.iguales(409, anticipado.estado(),
                "No debe confirmar el check-out antes de la hora limite");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("ocupada",
                atributoHabitacion(inventario.cuerpo(), 3, "data-habitacion-estado"),
                "La confirmacion anticipada no debe cambiar el estado");
        Aserciones.iguales(salidaFutura,
                atributoHabitacion(inventario.cuerpo(), 3, "data-habitacion-checkout"),
                "La confirmacion anticipada no debe cambiar el horario");

        Respuesta manualAnterior = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "3", "seccion-4", "seis_horas", "ocupada", "false", "normal")
                        + "&ajusteManualCheckOut=true&proximoCheckOut="
                        + fechaFormulario(entradaFutura.minusMinutes(1)),
                cookie);
        Aserciones.iguales(400, manualAnterior.estado(),
                "Debe rechazar una salida manual anterior al check-in");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("true",
                atributoHabitacion(inventario.cuerpo(), 3, "data-habitacion-pagado"),
                "El rechazo manual debe conservar tambien el estado del pago");
        Aserciones.iguales(salidaFutura,
                atributoHabitacion(inventario.cuerpo(), 3, "data-habitacion-checkout"),
                "El ajuste invalido no debe mutar la estancia");

        for (String formularioInvalido : new String[] {
            formularioHabitacion(
                    "4", "seccion-4", "seis_horas", "ocupada", "false", "normal")
                    + "&ajusteManualCheckOut=true&proximoCheckOut=no-es-fecha",
            formularioHabitacion(
                    "4", "seccion-4", "seis_horas", "disponible", "false", "normal")
                    + "&ajusteManualCheckOut=true&proximoCheckOut=2099-01-01T00%3A00"
        }) {
            Aserciones.iguales(400,
                    cliente.postFormulario(
                            "/hotel/habitaciones/actualizar", formularioInvalido, cookie).estado(),
                    "El servidor debe rechazar cada ajuste manual fuera del contrato");
        }
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("disponible",
                atributoHabitacion(inventario.cuerpo(), 4, "data-habitacion-estado"),
                "Los ajustes manuales rechazados no deben ocupar la habitacion");

        for (String confirmacionInvalida : new String[] {
            "numeroHabitacion=3&confirmado=false&modo=normal",
            "numeroHabitacion=3&confirmado=true",
            "numeroHabitacion=3&confirmado=true&modo=desconocido",
            "numeroHabitacion=999&confirmado=true&modo=normal"
        }) {
            Aserciones.iguales(400,
                    cliente.postFormulario(
                            "/hotel/habitaciones/checkout", confirmacionInvalida, cookie).estado(),
                    "La confirmacion incompleta o desconocida debe rechazarse");
        }
    }

    private static void impideReducirHabitacionesActivas(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta inventario = cliente.get("/hotel/habitaciones", cookie);
        String seccionUltima = atributoHabitacion(
                inventario.cuerpo(), 15, "data-habitacion-seccion");
        String servicioUltimo = atributoHabitacion(
                inventario.cuerpo(), 15, "data-habitacion-tipo-servicio");
        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/habitaciones/actualizar",
                        formularioHabitacion(
                                "15", seccionUltima, servicioUltimo,
                                "mantenimiento", "false", "normal"),
                        cookie).estado(),
                "Debe preparar la ultima habitacion como activa");

        Respuesta rechazada = cliente.postFormulario(
                "/hotel/configuracion/habitaciones",
                "cantidadHabitaciones=14",
                cookie);
        Aserciones.iguales(409, rechazada.estado(),
                "No debe eliminar una habitacion activa");
        Aserciones.iguales(15,
                contarOcurrencias(
                        cliente.get("/hotel/habitaciones", cookie).cuerpo(),
                        "class=\"tarjeta-habitacion\""),
                "La reduccion rechazada debe conservar el inventario");

        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/habitaciones/actualizar",
                        formularioHabitacion(
                                "15", seccionUltima, servicioUltimo,
                                "disponible", "false", "normal"),
                        cookie).estado(),
                "Debe poder liberar la ultima habitacion");
        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/configuracion/habitaciones",
                        "cantidadHabitaciones=14",
                        cookie).estado(),
                "Debe reducir un sufijo disponible");
        Aserciones.iguales(14,
                contarOcurrencias(
                        cliente.get("/hotel/habitaciones", cookie).cuerpo(),
                        "class=\"tarjeta-habitacion\""),
                "La reduccion valida debe eliminar solo la ultima habitacion");
    }

    private static void rechazaFormulariosInvalidosSinMutarDatos(
            ClienteHttpPrueba cliente, String cookie) throws Exception {
        Respuesta configuracionAntesDelLote = cliente.get("/hotel/configuracion", cookie);
        String[][] loteDuplicado = {
            {"seccion-1", "guardar", "%23FBBF24", "Mutada", "50.00", "90.00"},
            {"seccion-2", "guardar", "%23FBBF24", "Celeste", "65.00", "120.00"},
            {"seccion-3", "guardar", "%23A78BFA", "Violeta", "80.00", "150.00"},
            {"seccion-4", "guardar", "%23123456", "Ejecutiva", "75.50", "140.00"},
            {"seccion-5", "guardar", "%23654321", "Solo Sol", "80.00", "150.00"}
        };
        Respuesta loteRechazado = cliente.postFormulario(
                "/hotel/configuracion/guardar",
                formularioConfiguracionGeneral("13", loteDuplicado, null),
                cookie);
        Aserciones.iguales(409, loteRechazado.estado(),
                "Un duplicado debe rechazar el guardado general completo");
        Aserciones.iguales(
                configuracionAntesDelLote.cuerpo(),
                cliente.get("/hotel/configuracion", cookie).cuerpo(),
                "El lote rechazado no debe cambiar cantidad ni otras secciones");

        String[][] habitacionesInvalidas = {
            {formularioHabitacion(
                    "5", "seccion-inexistente", "seis_horas",
                    "disponible", "false", "normal")},
            {formularioHabitacion(
                    "5", "seccion-1", "servicio-invalido",
                    "disponible", "false", "normal")},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "estado-invalido", "false", "normal")},
            {formularioHabitacion(
                    "999", "seccion-1", "seis_horas",
                    "disponible", "false", "normal")},
            {"numeroHabitacion=5&idSeccion=seccion-1"
                    + "&tipoServicio=seis_horas&estado=disponible&modo=normal"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "disponible", "valor-invalido", "normal")},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "disponible", "false", "normal")
                    + "&ajusteManualCheckOut=si&proximoCheckOut=2099-01-01T00%3A00"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=3&montoAdicional=1.00"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=-1&montoAdicional=1.00"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=no-numero&montoAdicional=1.00"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=0&montoAdicional=5.00"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=1&montoAdicional=0"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=1&montoAdicional="},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=1&montoAdicional=-1"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=1&montoAdicional=0.001"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=1&montoAdicional=10000.01"},
            {formularioHabitacion(
                    "5", "seccion-1", "seis_horas",
                    "ocupada", "false", "normal")
                    + "&horasAdicionales=1&montoAdicional=1e2"}
        };
        for (String[] caso : habitacionesInvalidas) {
            Respuesta respuesta = cliente.postFormulario(
                    "/hotel/habitaciones/actualizar", caso[0], cookie);
            Aserciones.iguales(400, respuesta.estado(),
                    "Una habitacion fuera del contrato debe rechazarse");
        }
        Respuesta inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("disponible",
                atributoHabitacion(inventario.cuerpo(), 5, "data-habitacion-estado"),
                "Los rechazos deben conservar intacta la habitacion");
        Aserciones.iguales("0.00",
                atributoHabitacion(
                        inventario.cuerpo(), 5, "data-habitacion-monto-adicional"),
                "Los rechazos tampoco deben guardar cargos parciales");

        Respuesta extensionDisponible = cliente.postFormulario(
                "/hotel/habitaciones/actualizar",
                formularioHabitacion(
                        "5", "seccion-1", "seis_horas", "disponible", "false", "normal")
                        + "&horasAdicionales=1&montoAdicional=5.00",
                cookie);
        Aserciones.iguales(409, extensionDisponible.estado(),
                "Una ampliacion valida no debe aplicarse a una habitacion disponible");
        inventario = cliente.get("/hotel/habitaciones", cookie);
        Aserciones.iguales("0",
                atributoHabitacion(
                        inventario.cuerpo(), 5, "data-habitacion-horas-adicionales"),
                "El conflicto de estado no debe guardar tiempo adicional");

        for (String cantidad : new String[] {"0", "51", "-1", "no-numero"}) {
            Respuesta respuesta = cliente.postFormulario(
                    "/hotel/configuracion/habitaciones",
                    "cantidadHabitaciones=" + cantidad,
                    cookie);
            Aserciones.iguales(400, respuesta.estado(),
                    "La cantidad debe permanecer entre uno y cincuenta");
        }

        String[][] seccionesInvalidas = {
            {formularioSeccion("", "123456", "Sin numeral", "70", "130")},
            {formularioSeccion("", "%23GGGGGG", "HEX invalido", "70", "130")},
            {formularioSeccion("", "%23ABCDEF", "x".repeat(41), "70", "130")},
            {formularioSeccion("", "%23ABCDEF", "Precio bajo", "0.99", "130")},
            {formularioSeccion("", "%23ABCDEF", "Precio alto", "70", "10000.01")},
            {formularioSeccion(
                    "seccion-inexistente", "%23ABCDEF", "No existe", "70", "130")},
            {formularioSeccion("", "%23123456", "Color repetido", "70", "130")},
            {formularioSeccion("", "%23ABCDEF", "Ejecutiva", "70", "130")}
        };
        for (String[] caso : seccionesInvalidas) {
            Respuesta respuesta = cliente.postFormulario(
                    "/hotel/configuracion/secciones/guardar", caso[0], cookie);
            Aserciones.verdadero(
                    respuesta.estado() == 400 || respuesta.estado() == 409,
                    "Una seccion invalida o duplicada debe rechazarse");
        }

        Respuesta asignada = cliente.postFormulario(
                "/hotel/configuracion/secciones/eliminar",
                "idSeccion=seccion-4",
                cookie);
        Aserciones.iguales(409, asignada.estado(),
                "No debe eliminar una seccion asignada a una habitacion");

        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/configuracion/secciones/guardar",
                        formularioSeccion(
                                "", "%23ABCDEF", "Temporal", "85", "155"),
                        cookie).estado(),
                "Debe preparar una seccion eliminable");
        Aserciones.iguales(303,
                cliente.postFormulario(
                        "/hotel/configuracion/secciones/eliminar",
                        "idSeccion=seccion-6",
                        cookie).estado(),
                "Debe eliminar una seccion sin habitaciones asignadas");

        Respuesta repetida = cliente.postFormulario(
                "/hotel/configuracion/secciones/guardar",
                formularioSeccion("", "%23ABCDEF", "Duplicada", "85", "155")
                        + "&color=%23111111",
                cookie);
        Aserciones.iguales(400, repetida.estado(),
                "Los campos repetidos deben rechazarse");
        Respuesta malCodificada = cliente.postFormulario(
                "/hotel/configuracion/secciones/guardar",
                "idSeccion=&color=%ZZ&nombre=Error&precio6Horas=80"
                        + "&precioDiaCompleto=150",
                cookie);
        Aserciones.iguales(400, malCodificada.estado(),
                "Debe rechazar una codificacion invalida");
        Respuesta tipoIncorrecto = cliente.post(
                "/hotel/configuracion/habitaciones",
                "cantidadHabitaciones=14",
                "application/json",
                cookie);
        Aserciones.iguales(415, tipoIncorrecto.estado(),
                "La configuracion debe exigir formularios URL encoded");
        Respuesta enorme = cliente.postFormulario(
                "/hotel/configuracion/secciones/guardar",
                formularioSeccion(
                        "", "%23ABCDEF", "Grande", "85", "155")
                        + "&relleno=" + "x".repeat(4_096),
                cookie);
        Aserciones.iguales(413, enorme.estado(),
                "Debe limitar el tamano del formulario de configuracion");
        String formularioGeneralValido = formularioConfiguracionGeneral(
                "14",
                new String[][] {
                    {"seccion-1", "guardar", "%23FBBF24", "Amarillo", "50.00", "90.00"},
                    {"seccion-2", "guardar", "%2338BDF8", "Celeste", "65.00", "120.00"},
                    {"seccion-3", "guardar", "%23A78BFA", "Violeta", "80.00", "150.00"},
                    {"seccion-4", "guardar", "%23123456", "Ejecutiva", "75.50", "140.00"},
                    {"seccion-5", "guardar", "%23654321", "Solo Sol", "80.00", "150.00"}
                },
                null);
        Respuesta repetidoGeneral = cliente.postFormulario(
                "/hotel/configuracion/guardar",
                formularioGeneralValido + "&cantidadHabitaciones=14",
                cookie);
        Aserciones.iguales(400, repetidoGeneral.estado(),
                "El guardado general debe rechazar campos exactos repetidos");
        Respuesta tipoGeneral = cliente.post(
                "/hotel/configuracion/guardar",
                formularioGeneralValido,
                "application/json",
                cookie);
        Aserciones.iguales(415, tipoGeneral.estado(),
                "El guardado general debe exigir formularios URL encoded");
        Respuesta enormeGeneral = cliente.postFormulario(
                "/hotel/configuracion/guardar",
                formularioGeneralValido + "&relleno=" + "x".repeat(16_384),
                cookie);
        Aserciones.iguales(413, enormeGeneral.estado(),
                "El lote general debe conservar un limite de tamano acotado");

        for (int indice = 1; indice <= 7; indice++) {
            String color = String.format("%%23%06X", 0x100000 + indice);
            Respuesta altaHastaLimite = cliente.postFormulario(
                    "/hotel/configuracion/secciones/guardar",
                    formularioSeccion(
                            "", color, "Limite " + indice, "90", "160"),
                    cookie);
            Aserciones.iguales(303, altaHastaLimite.estado(),
                    "El endpoint individual debe permitir completar doce secciones");
        }
        Respuesta seccionTrece = cliente.postFormulario(
                "/hotel/configuracion/secciones/guardar",
                formularioSeccion("", "%23100008", "Limite 8", "90", "160"),
                cookie);
        Aserciones.iguales(409, seccionTrece.estado(),
                "El endpoint individual debe rechazar una seccion trece");
        Aserciones.iguales(
                12,
                contarOcurrencias(
                        cliente.get("/hotel/configuracion", cookie).cuerpo(),
                        "class=\"tarjeta-seccion-tarifaria\" data-seccion-tarifaria"),
                "El rechazo del alta trece debe conservar las doce secciones registradas");

        Respuesta configuracion = cliente.get("/hotel/configuracion", cookie);
        comprobarQueNoExponeLlaves(configuracion,
                "La configuracion posterior a errores");
        Aserciones.noContieneIgnorandoMayusculas(
                configuracion.cuerpo(), "valor-invalido",
                "Los valores rechazados no deben persistir ni reflejarse");
    }

    private static String[][] seccionesIniciales() {
        return new String[][] {
            {"seccion-1", "guardar", "%23FBBF24", "Amarillo", "50.00", "90.00"},
            {"seccion-2", "guardar", "%2338BDF8", "Celeste", "65.00", "120.00"},
            {"seccion-3", "guardar", "%23A78BFA", "Violeta", "80.00", "150.00"}
        };
    }

    private static String formularioConfiguracionGeneral(
            String cantidadHabitaciones,
            String[][] secciones,
            String[] nuevaSeccion) {
        StringBuilder formulario = new StringBuilder()
                .append("cantidadHabitaciones=").append(cantidadHabitaciones)
                .append("&cantidadSecciones=").append(secciones.length);
        for (int indice = 0; indice < secciones.length; indice++) {
            String prefijo = "&seccion." + indice + ".";
            formulario.append(prefijo).append("id=").append(secciones[indice][0])
                    .append(prefijo).append("accion=").append(secciones[indice][1])
                    .append(prefijo).append("color=").append(secciones[indice][2])
                    .append(prefijo).append("nombre=")
                    .append(secciones[indice][3].replace(" ", "+"))
                    .append(prefijo).append("precio6Horas=").append(secciones[indice][4])
                    .append(prefijo).append("precioDiaCompleto=").append(secciones[indice][5]);
        }
        if (nuevaSeccion != null) {
            formulario.append("&nuevaSeccion.color=").append(nuevaSeccion[0])
                    .append("&nuevaSeccion.nombre=").append(nuevaSeccion[1].replace(" ", "+"))
                    .append("&nuevaSeccion.precio6Horas=").append(nuevaSeccion[2])
                    .append("&nuevaSeccion.precioDiaCompleto=").append(nuevaSeccion[3]);
        }
        return formulario.toString();
    }

    private static String formularioSeccion(
            String id,
            String colorCodificado,
            String nombre,
            String precio6,
            String precioDia) {
        return "idSeccion=" + id
                + "&color=" + colorCodificado
                + "&nombre=" + nombre.replace(" ", "+")
                + "&precio6Horas=" + precio6
                + "&precioDiaCompleto=" + precioDia;
    }

    private static String formularioHabitacion(
            String numero,
            String idSeccion,
            String tipoServicio,
            String estado,
            String pagado,
            String modo) {
        return "numeroHabitacion=" + numero
                + "&idSeccion=" + idSeccion
                + "&tipoServicio=" + tipoServicio
                + "&estado=" + estado
                + "&pagado=" + pagado
                + "&modo=" + modo
                + ("ocupada".equals(estado)
                        ? "&cantidadHuespedes=2"
                                + "&huesped.0.nombre=Ana+Torres"
                                + "&huesped.0.tipoDocumento=dni"
                                + "&huesped.0.numeroDocumento=12345678"
                                + "&huesped.1.nombre=Luis+Paredes"
                                + "&huesped.1.tipoDocumento=pasaporte"
                                + "&huesped.1.numeroDocumento=PE123456"
                        : "");
    }

    private static String fechaFormulario(LocalDateTime fecha) {
        return fecha.toString().replace(":", "%3A");
    }

    private static String atributoHabitacion(
            String pagina, int numero, String atributo) {
        String comienzo = "data-habitacion-numero=\"" + numero + "\"";
        int posicion = pagina.indexOf(comienzo);
        Aserciones.verdadero(posicion >= 0,
                "Debe existir la tarjeta de la habitacion " + numero);
        int fin = pagina.indexOf(">", posicion);
        Aserciones.verdadero(fin > posicion,
                "La tarjeta debe cerrar su etiqueta de apertura");
        String apertura = pagina.substring(posicion, fin);
        String prefijo = atributo + "=\"";
        int inicioValor = apertura.indexOf(prefijo);
        Aserciones.verdadero(inicioValor >= 0,
                "La tarjeta debe declarar " + atributo);
        inicioValor += prefijo.length();
        int finValor = apertura.indexOf('"', inicioValor);
        Aserciones.verdadero(finValor >= inicioValor,
                "El atributo debe cerrar sus comillas");
        return apertura.substring(inicioValor, finValor);
    }

    private static int contarOcurrencias(String texto, String fragmento) {
        int cantidad = 0;
        int posicion = 0;
        while ((posicion = texto.indexOf(fragmento, posicion)) >= 0) {
            cantidad++;
            posicion += fragmento.length();
        }
        return cantidad;
    }

    private static String iniciarSesionConHotel(
            ClienteHttpPrueba cliente, String codigoHotel, String llaveHotel) throws Exception {
        String cookie = iniciarSesion(cliente);
        Respuesta acceso = cliente.postFormulario(
                "/hotel/acceso", formularioHotel(codigoHotel, llaveHotel), cookie);
        Aserciones.iguales(303, acceso.estado(),
                "La preparacion debe autorizar el hotel solicitado");
        Aserciones.iguales("/hotel", acceso.encabezado("Location"),
                "La preparacion debe entrar a la gestion");
        return cookie;
    }

    private static String formularioCambio(String nuevaLlave, String confirmacionLlave) {
        return "nuevaLlave=" + nuevaLlave + "&confirmacionLlave=" + confirmacionLlave;
    }

    private static String iniciarSesion(ClienteHttpPrueba cliente) throws Exception {
        Respuesta respuesta = cliente.postFormulario("/acceso", "llave=" + LLAVE);
        Aserciones.iguales(303, respuesta.estado(),
                "La preparacion de la sesion debe funcionar");
        String setCookie = respuesta.encabezado("Set-Cookie");
        Aserciones.verdadero(setCookie != null,
                "El servidor debe emitir una cookie de sesion");
        int fin = setCookie.indexOf(';');
        return fin < 0 ? setCookie : setCookie.substring(0, fin);
    }

    private static Respuesta autorizarHotel(
            ClienteHttpPrueba cliente, String cookie, String codigo) throws Exception {
        return cliente.postFormulario(
                "/hotel/acceso", formularioHotel(codigo, LLAVE_HOTEL), cookie);
    }

    private static String formularioHotel(String codigo, String llave) {
        return "hotel=" + codigo + "&llaveHotel=" + llave;
    }

    private static void comprobarMetodo(
            Respuesta respuesta, int estado, String permitido, String mensaje) {
        Aserciones.iguales(estado, respuesta.estado(), mensaje);
        Aserciones.iguales(permitido, respuesta.encabezado("Allow"),
                "Debe anunciar el metodo permitido");
    }

    private static void comprobarQueNoExponeLlaves(Respuesta respuesta, String contexto) {
        comprobarTextoSinLlaves(respuesta.cuerpo(), contexto);
    }

    private static void comprobarTextoSinLlaves(String texto, String contexto) {
        Aserciones.noContieneIgnorandoMayusculas(
                texto, LLAVE, contexto + " no debe exponer la llave global");
        Aserciones.noContieneIgnorandoMayusculas(
                texto, LLAVE_HOTEL, contexto + " no debe exponer la llave del hotel");
    }

    private static void comprobarNoRefleja(
            Respuesta respuesta, String llaveEnviada, String mensaje) {
        Aserciones.noContieneIgnorandoMayusculas(respuesta.cuerpo(), llaveEnviada, mensaje);
    }

    private static LocalDateTime fechaDeCampo(String html, String id) {
        Pattern patron = Pattern.compile(
                "id=\\\"" + Pattern.quote(id) + "\\\"[^>]*value=\\\"([^\\\"]+)\\\"");
        Matcher coincidencia = patron.matcher(html);
        if (!coincidencia.find()) {
            throw new AssertionError("No se encontró el valor del campo " + id);
        }
        return LocalDateTime.parse(coincidencia.group(1));
    }

    private static ServidorGestera nuevoServidor(boolean cookieSegura) throws Exception {
        return new ServidorGestera(
                new InetSocketAddress("127.0.0.1", 0),
                LLAVE.toCharArray(),
                LLAVE_HOTEL.toCharArray(),
                Duration.ofMinutes(5),
                cookieSegura);
    }
}
