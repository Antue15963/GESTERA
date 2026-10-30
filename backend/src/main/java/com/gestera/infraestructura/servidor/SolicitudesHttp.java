package com.gestera.infraestructura.servidor;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles;
import com.gestera.modelo.huespedes.DatosHuesped;
import com.gestera.modelo.huespedes.FiltroHuespedes;
import com.gestera.modelo.huespedes.GestorHuespedesHoteles;
import com.gestera.modelo.huespedes.TipoDocumento;
import com.gestera.modelo.reportes.FiltroReporte;
import com.gestera.modelo.servicios.FiltroPedidosServicio;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Lectura acotada de formularios y cookies HTTP. */
public final class SolicitudesHttp {
    private static final int MAXIMO_FORMULARIO_BYTES = 4_096;
    private static final int MAXIMO_CONFIGURACION_GENERAL_BYTES = 16_384;
    private static final int MAXIMO_HABITACION_BYTES = 16_384;
    private static final Pattern FORMATO_PRECIO =
            Pattern.compile("[0-9]{1,5}(?:\\.[0-9]{1,2})?");
    private static final Pattern FORMATO_MONTO_ADICIONAL =
            Pattern.compile("(?:0|[1-9][0-9]{0,4})(?:\\.[0-9]{1,2})?");
    private static final Pattern FORMATO_TEXTO_CHECKOUT =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}");
    private static final DateTimeFormatter FORMATO_CHECKOUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")
                    .withResolverStyle(ResolverStyle.STRICT);

    private SolicitudesHttp() {
    }

    static char[] leerLlave(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        String valor = leerFormulario(intercambio).get("llave");
        return valor == null ? new char[0] : valor.toCharArray();
    }

    static AccesoHotel leerAccesoHotel(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        String codigoHotel = formulario.getOrDefault("hotel", "");
        if (codigoHotel.length() > 80) {
            throw new SolicitudHttpException(400, "El hotel seleccionado no es válido.");
        }
        String valorLlave = formulario.get("llaveHotel");
        return new AccesoHotel(
                codigoHotel,
                valorLlave == null ? new char[0] : valorLlave.toCharArray());
    }

    static CambioLlaveHotel leerCambioLlaveHotel(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        String nuevaLlave = formulario.get("nuevaLlave");
        String confirmacionLlave = formulario.get("confirmacionLlave");
        return new CambioLlaveHotel(
                nuevaLlave == null ? new char[0] : nuevaLlave.toCharArray(),
                confirmacionLlave == null ? new char[0] : confirmacionLlave.toCharArray());
    }

    public static int leerCantidadHabitaciones(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        return leerEntero(
                formulario.get("cantidadHabitaciones"),
                "La cantidad de habitaciones no es válida.");
    }

    public static ConfiguracionGeneral leerConfiguracionGeneral(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(
                intercambio, MAXIMO_CONFIGURACION_GENERAL_BYTES);
        int cantidadHabitaciones = leerEntero(
                formulario.get("cantidadHabitaciones"),
                "La cantidad de habitaciones no es válida.");
        int cantidadSecciones = leerEntero(
                formulario.get("cantidadSecciones"),
                "La cantidad de secciones no es válida.");
        if (cantidadSecciones < 1 || cantidadSecciones > 12) {
            throw new SolicitudHttpException(400, "La cantidad de secciones no es válida.");
        }

        ArrayList<CambioConfiguracionSeccion> cambios =
                new ArrayList<>(cantidadSecciones + 1);
        for (int indice = 0; indice < cantidadSecciones; indice++) {
            String prefijo = "seccion." + indice + ".";
            String accion = campoAcotado(
                    formulario, prefijo + "accion", 16);
            if (!accion.equals("guardar") && !accion.equals("eliminar")) {
                throw new SolicitudHttpException(400, "La acción de una sección no es válida.");
            }
            cambios.add(new CambioConfiguracionSeccion(
                    campoAcotado(formulario, prefijo + "id", 32),
                    accion.equals("eliminar"),
                    campoAcotado(formulario, prefijo + "color", 16),
                    campoAcotado(formulario, prefijo + "nombre", 80),
                    campoAcotado(
                            formulario, prefijo + "precio6Horas", 32),
                    campoAcotado(
                            formulario, prefijo + "precioDiaCompleto", 32)));
        }

        String colorNuevo = campoAcotado(
                formulario, "nuevaSeccion.color", 16);
        String nombreNuevo = campoAcotado(
                formulario, "nuevaSeccion.nombre", 80);
        String precioSeisNuevo = campoAcotado(
                formulario, "nuevaSeccion.precio6Horas", 32);
        String precioDiaNuevo = campoAcotado(
                formulario, "nuevaSeccion.precioDiaCompleto", 32);
        boolean solicitaNueva = !nombreNuevo.isBlank()
                || !precioSeisNuevo.isBlank()
                || !precioDiaNuevo.isBlank();
        if (solicitaNueva) {
            cambios.add(new CambioConfiguracionSeccion(
                    "", false, colorNuevo, nombreNuevo, precioSeisNuevo, precioDiaNuevo));
        }
        return new ConfiguracionGeneral(cantidadHabitaciones, List.copyOf(cambios));
    }

    public static ConfiguracionSeccion leerConfiguracionSeccion(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        return new ConfiguracionSeccion(
                campoAcotado(formulario, "idSeccion", 32),
                campoAcotado(formulario, "color", 16),
                campoAcotado(formulario, "nombre", 80),
                campoAcotado(formulario, "precio6Horas", 32),
                campoAcotado(
                        formulario, "precioDiaCompleto", 32));
    }

    public static String leerIdSeccionEliminada(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        return campoAcotado(formulario, "idSeccion", 32);
    }

    public static ActualizacionHabitacion leerActualizacionHabitacion(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio, MAXIMO_HABITACION_BYTES);
        String pagado = campoAcotado(formulario, "pagado", 8);
        if (!pagado.equals("true") && !pagado.equals("false")) {
            throw new SolicitudHttpException(400, "El estado del pago no es válido.");
        }
        String ajusteManualCheckOut = campoAcotado(
                formulario, "ajusteManualCheckOut", 8);
        if (!ajusteManualCheckOut.isEmpty()
                && !ajusteManualCheckOut.equals("true")
                && !ajusteManualCheckOut.equals("false")) {
            throw new SolicitudHttpException(
                    400, "El ajuste manual del check-out no es válido.");
        }
        String horasAdicionalesTexto = campoAcotado(
                formulario, "horasAdicionales", 8);
        int horasAdicionales = switch (horasAdicionalesTexto) {
            case "", "0" -> 0;
            case "1" -> 1;
            case "2" -> 2;
            case "4" -> 4;
            default -> throw new SolicitudHttpException(
                    400, "Las horas adicionales no son válidas.");
        };
        String montoAdicional = campoAcotado(
                formulario, "montoAdicional", 32);
        if (montoAdicional.isEmpty()) {
            montoAdicional = "0";
        }
        String estado = campoAcotado(formulario, "estado", 32);
        List<DatosHuesped> huespedes = estado.equals("ocupada")
                ? leerHuespedes(formulario)
                : List.of();
        return new ActualizacionHabitacion(
                leerEntero(
                        formulario.get("numeroHabitacion"),
                        "La habitación seleccionada no es válida."),
                campoAcotado(formulario, "idSeccion", 32),
                campoAcotado(formulario, "tipoServicio", 24),
                estado,
                pagado.equals("true"),
                campoAcotado(formulario, "modo", 16),
                ajusteManualCheckOut.equals("true"),
                campoAcotado(formulario, "proximoCheckOut", 16),
                horasAdicionales,
                montoAdicional,
                huespedes);
    }

    private static List<DatosHuesped> leerHuespedes(Map<String, String> formulario)
            throws SolicitudHttpException {
        int cantidad = leerEntero(
                formulario.get("cantidadHuespedes"),
                "La cantidad de huéspedes no es válida.");
        if (cantidad < 1 || cantidad > GestorHuespedesHoteles.HUESPEDES_MAXIMOS) {
            throw new SolicitudHttpException(
                    400, "La cantidad de huéspedes debe estar entre 1 y 8.");
        }
        ArrayList<DatosHuesped> huespedes = new ArrayList<>(cantidad);
        for (int indice = 0; indice < cantidad; indice++) {
            String prefijo = "huesped." + indice + ".";
            String tipo = campoAcotado(formulario, prefijo + "tipoDocumento", 24);
            huespedes.add(new DatosHuesped(
                    campoAcotado(formulario, prefijo + "nombre", 120),
                    TipoDocumento.desdeCodigo(tipo).orElse(null),
                    campoAcotado(formulario, prefijo + "numeroDocumento", 30)));
        }
        return List.copyOf(huespedes);
    }

    public static RegistroCheckOut leerRegistroCheckOut(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        if (!campoAcotado(
                formulario, "confirmado", 8).equals("true")) {
            throw new SolicitudHttpException(
                    400, "Debes confirmar que el check-out fue realizado.");
        }
        String modo = campoAcotado(formulario, "modo", 16);
        if (!modo.equals("normal") && !modo.equals("limpieza")) {
            throw new SolicitudHttpException(400, "La vista de habitaciones no es válida.");
        }
        return new RegistroCheckOut(
                leerEntero(
                        formulario.get("numeroHabitacion"),
                        "La habitación seleccionada no es válida."),
                modo);
    }

    public static RegistroAreaComun leerRegistroAreaComun(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(
                intercambio, MAXIMO_HABITACION_BYTES);
        String duracion = campoAcotado(formulario, "duracionHoras", 2);
        return new RegistroAreaComun(
                campoAcotado(formulario, "area", 32),
                leerCheckOutManual(campoAcotado(formulario, "entrada", 16)),
                switch (duracion) {
                    case "1" -> 1;
                    case "3" -> 3;
                    case "5" -> 5;
                    default -> 0;
                },
                leerHuespedes(formulario));
    }

    public static NuevoPedidoServicio leerNuevoPedidoServicio(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        return new NuevoPedidoServicio(
                leerEntero(
                        formulario.get("numeroHabitacion"),
                        "La habitación seleccionada no es válida."),
                campoAcotado(formulario, "producto", 60),
                leerEntero(formulario.get("cantidad"), "La cantidad no es válida."),
                campoAcotado(formulario, "observaciones", 300));
    }

    public static CambioEstadoPedidoServicio leerCambioEstadoPedidoServicio(
            HttpExchange intercambio) throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        return new CambioEstadoPedidoServicio(
                leerLong(
                        formulario.get("pedidoId"),
                        "El pedido seleccionado no es válido."),
                campoAcotado(formulario, "estado", 24));
    }

    public static CambioProductoCatalogo leerCambioProductoCatalogo(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        return new CambioProductoCatalogo(
                campoAcotado(formulario, "producto", 60),
                campoAcotado(formulario, "nombre", 120),
                campoAcotado(formulario, "categoria", 60),
                campoAcotado(formulario, "precio", 32));
    }

    public static NuevoProductoCatalogo leerNuevoProductoCatalogo(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        Map<String, String> formulario = leerFormulario(intercambio);
        return new NuevoProductoCatalogo(
                campoAcotado(formulario, "nombre", 120),
                campoAcotado(formulario, "categoria", 60),
                campoAcotado(formulario, "precio", 32));
    }

    public static String leerProductoCatalogoEliminado(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        return campoAcotado(leerFormulario(intercambio), "producto", 60);
    }

    public static String leerCategoriaCatalogo(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        return campoAcotado(leerFormulario(intercambio), "categoria", 60);
    }

    public static FiltroHuespedes leerFiltroHuespedes(HttpExchange intercambio)
            throws SolicitudHttpException {
        String consulta = intercambio.getRequestURI().getRawQuery();
        if (consulta == null || consulta.isEmpty()) {
            return FiltroHuespedes.vacio();
        }
        if (consulta.length() > 2_048) {
            throw new SolicitudHttpException(414, "Los filtros de búsqueda son demasiado largos.");
        }
        Map<String, String> parametros = decodificarFormulario(consulta);
        String nombre = campoAcotado(parametros, "nombre", 100).trim();
        String diaTexto = campoAcotado(parametros, "dia", 10);
        String desdeTexto = campoAcotado(parametros, "estuvoDesde", 16);
        String hastaTexto = campoAcotado(parametros, "estuvoHasta", 16);
        String color = campoAcotado(parametros, "color", 7).toUpperCase(java.util.Locale.ROOT);
        if (!nombre.isEmpty() && nombre.codePoints().anyMatch(Character::isISOControl)) {
            throw new SolicitudHttpException(400, "El nombre buscado no es válido.");
        }
        LocalDate dia = null;
        try {
            if (!diaTexto.isEmpty()) {
                dia = LocalDate.parse(diaTexto);
            }
        } catch (DateTimeParseException exception) {
            throw new SolicitudHttpException(400, "El día de visita no es válido.");
        }
        LocalDateTime desde = desdeTexto.isEmpty() ? null : leerCheckOutManual(desdeTexto);
        LocalDateTime hasta = hastaTexto.isEmpty() ? null : leerCheckOutManual(hastaTexto);
        if ((!desdeTexto.isEmpty() && desde == null)
                || (!hastaTexto.isEmpty() && hasta == null)) {
            throw new SolicitudHttpException(400, "El intervalo de horas no es válido.");
        }
        if (!color.isEmpty() && !color.matches("#[0-9A-F]{6}")) {
            throw new SolicitudHttpException(400, "El color de habitación no es válido.");
        }
        try {
            return new FiltroHuespedes(nombre, dia, desde, hasta, color);
        } catch (IllegalArgumentException exception) {
            throw new SolicitudHttpException(400, exception.getMessage());
        }
    }

    public static FiltroPedidosServicio leerFiltroPedidosServicio(HttpExchange intercambio)
            throws SolicitudHttpException {
        Map<String, String> parametros = leerConsulta(intercambio);
        String fechaTexto = campoAcotado(parametros, "fecha", 10);
        String horaTexto = campoAcotado(parametros, "hora", 5);
        String habitacionTexto = campoAcotado(parametros, "habitacion", 2);
        try {
            LocalDate fecha = fechaTexto.isEmpty() ? null : LocalDate.parse(fechaTexto);
            LocalTime hora = horaTexto.isEmpty()
                    ? null
                    : LocalTime.parse(horaTexto, DateTimeFormatter.ofPattern("HH:mm"));
            Integer habitacion = habitacionTexto.isEmpty()
                    ? null
                    : leerEntero(habitacionTexto, "La habitación buscada no es válida.");
            return new FiltroPedidosServicio(fecha, hora, habitacion);
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw new SolicitudHttpException(
                    400, "Los filtros de pedidos no son válidos.");
        }
    }

    public static FiltroReporte leerFiltroReporte(HttpExchange intercambio, LocalDate hoy)
            throws SolicitudHttpException {
        Map<String, String> parametros = leerConsulta(intercambio);
        String periodo = campoAcotado(parametros, "periodo", 20);
        String tipo = campoAcotado(parametros, "tipo", 20);
        String desdeTexto = campoAcotado(parametros, "desde", 10);
        String hastaTexto = campoAcotado(parametros, "hasta", 10);
        try {
            LocalDate desde = desdeTexto.isEmpty() ? null : LocalDate.parse(desdeTexto);
            LocalDate hasta = hastaTexto.isEmpty() ? null : LocalDate.parse(hastaTexto);
            return FiltroReporte.crear(periodo, tipo, desde, hasta, hoy);
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw new SolicitudHttpException(400, exception.getMessage());
        }
    }

    public static BigDecimal leerPrecio(String valor) {
        return leerDecimal(valor, FORMATO_PRECIO);
    }

    public static BigDecimal leerMontoAdicional(String valor) {
        BigDecimal monto = leerDecimal(valor, FORMATO_MONTO_ADICIONAL);
        return monto != null
                        && monto.compareTo(
                                GestorHabitacionesHoteles.MONTO_ADICIONAL_MAXIMO) <= 0
                ? monto
                : null;
    }

    public static LocalDateTime leerCheckOutManual(String valor) {
        if (valor == null || !FORMATO_TEXTO_CHECKOUT.matcher(valor).matches()) {
            return null;
        }
        try {
            return LocalDateTime.parse(valor, FORMATO_CHECKOUT);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static BigDecimal leerDecimal(String valor, Pattern formato) {
        if (valor == null || !formato.matcher(valor).matches()) {
            return null;
        }
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Map<String, String> leerFormulario(HttpExchange intercambio)
            throws IOException, SolicitudHttpException {
        return leerFormulario(intercambio, MAXIMO_FORMULARIO_BYTES);
    }

    private static Map<String, String> leerConsulta(HttpExchange intercambio)
            throws SolicitudHttpException {
        String consulta = intercambio.getRequestURI().getRawQuery();
        if (consulta == null || consulta.isEmpty()) {
            return Map.of();
        }
        if (consulta.length() > 2_048) {
            throw new SolicitudHttpException(414, "Los filtros son demasiado largos.");
        }
        return decodificarFormulario(consulta);
    }

    private static Map<String, String> leerFormulario(
            HttpExchange intercambio, int maximoBytes)
            throws IOException, SolicitudHttpException {
        String tipo = intercambio.getRequestHeaders().getFirst("Content-Type");
        String medio = tipo == null ? "" : tipo.split(";", 2)[0].trim();
        if (!medio.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            throw new SolicitudHttpException(
                    415, "El formulario debe enviarse codificado.");
        }

        long longitudDeclarada = longitudDeclarada(intercambio);
        if (longitudDeclarada > maximoBytes) {
            throw new SolicitudHttpException(
                    413, "El formulario supera el tamaño permitido.");
        }

        byte[] cuerpo = intercambio.getRequestBody().readNBytes(maximoBytes + 1);
        if (cuerpo.length > maximoBytes) {
            Arrays.fill(cuerpo, (byte) 0);
            throw new SolicitudHttpException(
                    413, "El formulario supera el tamaño permitido.");
        }

        try {
            return decodificarFormulario(
                    new String(cuerpo, StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(cuerpo, (byte) 0);
        }
    }

    private static Map<String, String> decodificarFormulario(String formulario)
            throws SolicitudHttpException {
        Map<String, String> valores = new LinkedHashMap<>();
        try {
            for (String parametro : formulario.split("&", -1)) {
                if (parametro.isEmpty()) {
                    continue;
                }
                int separador = parametro.indexOf('=');
                String nombreCodificado = separador < 0
                        ? parametro
                        : parametro.substring(0, separador);
                String nombre = URLDecoder.decode(
                        nombreCodificado, StandardCharsets.UTF_8);
                String valorCodificado = separador < 0
                        ? ""
                        : parametro.substring(separador + 1);
                String valor = URLDecoder.decode(
                        valorCodificado, StandardCharsets.UTF_8);
                if (valores.putIfAbsent(nombre, valor) != null) {
                    throw new SolicitudHttpException(
                            400, "El formulario contiene campos repetidos.");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new SolicitudHttpException(
                    400, "El formulario no tiene una codificación válida.");
        }
        return valores;
    }

    private static int leerEntero(String valor, String mensaje)
            throws SolicitudHttpException {
        if (valor == null || valor.length() > 10) {
            throw new SolicitudHttpException(400, mensaje);
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException exception) {
            throw new SolicitudHttpException(400, mensaje);
        }
    }

    private static long leerLong(String valor, String mensaje)
            throws SolicitudHttpException {
        if (valor == null || valor.length() > 19) {
            throw new SolicitudHttpException(400, mensaje);
        }
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException exception) {
            throw new SolicitudHttpException(400, mensaje);
        }
    }

    private static String campoAcotado(
            Map<String, String> formulario, String nombre, int longitudMaxima)
            throws SolicitudHttpException {
        String valor = formulario.getOrDefault(nombre, "");
        if (valor.length() > longitudMaxima) {
            throw new SolicitudHttpException(
                    400, "El formulario contiene un valor no válido.");
        }
        return valor;
    }

    private static long longitudDeclarada(HttpExchange intercambio)
            throws SolicitudHttpException {
        String valor = intercambio.getRequestHeaders().getFirst("Content-Length");
        if (valor == null) {
            return -1;
        }
        try {
            long longitud = Long.parseLong(valor);
            if (longitud < 0) {
                throw new NumberFormatException();
            }
            return longitud;
        } catch (NumberFormatException exception) {
            throw new SolicitudHttpException(400, "Content-Length no es válido.");
        }
    }

    static String leerCookie(HttpExchange intercambio, String nombre) {
        List<String> encabezados = intercambio.getRequestHeaders().get("Cookie");
        if (encabezados == null) {
            return null;
        }
        for (String encabezado : encabezados) {
            for (String fragmento : encabezado.split(";")) {
                int separador = fragmento.indexOf('=');
                if (separador > 0 && fragmento.substring(0, separador).trim().equals(nombre)) {
                    return fragmento.substring(separador + 1).trim();
                }
            }
        }
        return null;
    }

    record AccesoHotel(String codigoHotel, char[] llave) implements AutoCloseable {
        AccesoHotel {
            codigoHotel = codigoHotel == null ? "" : codigoHotel;
            llave = llave == null ? new char[0] : llave;
        }

        @Override
        public void close() {
            Arrays.fill(llave, '\0');
        }
    }

    record CambioLlaveHotel(char[] nuevaLlave, char[] confirmacionLlave)
            implements AutoCloseable {
        CambioLlaveHotel {
            nuevaLlave = nuevaLlave == null ? new char[0] : nuevaLlave;
            confirmacionLlave = confirmacionLlave == null
                    ? new char[0]
                    : confirmacionLlave;
        }

        @Override
        public void close() {
            Arrays.fill(nuevaLlave, '\0');
            Arrays.fill(confirmacionLlave, '\0');
        }
    }

    public record ConfiguracionGeneral(
            int cantidadHabitaciones,
            List<CambioConfiguracionSeccion> cambios) {
        public ConfiguracionGeneral {
            cambios = List.copyOf(cambios);
        }
    }

    public record NuevoPedidoServicio(
            int numeroHabitacion,
            String producto,
            int cantidad,
            String observaciones) {
    }

    public record CambioEstadoPedidoServicio(long pedidoId, String estado) {
    }

    public record CambioProductoCatalogo(
            String producto, String nombre, String categoria, String precio) {
    }

    public record NuevoProductoCatalogo(String nombre, String categoria, String precio) {
    }

    public record CambioConfiguracionSeccion(
            String idSeccion,
            boolean eliminar,
            String color,
            String nombre,
            String precioSeisHoras,
            String precioDiaCompleto) {
    }

    public record ConfiguracionSeccion(
            String idSeccion,
            String color,
            String nombre,
            String precioSeisHoras,
            String precioDiaCompleto) {
    }

    public record ActualizacionHabitacion(
            int numero,
            String idSeccion,
            String tipoServicio,
            String estado,
            boolean pagado,
            String modo,
            boolean ajusteManualCheckOut,
            String proximoCheckOut,
            int horasAdicionales,
            String montoAdicional,
            List<DatosHuesped> huespedes) {
        public ActualizacionHabitacion {
            huespedes = List.copyOf(huespedes);
        }
    }

    public record RegistroCheckOut(int numero, String modo) {
    }

    public record RegistroAreaComun(
            String area,
            LocalDateTime entrada,
            int duracionHoras,
            List<DatosHuesped> visitantes) {
        public RegistroAreaComun {
            visitantes = List.copyOf(visitantes);
        }
    }
}
