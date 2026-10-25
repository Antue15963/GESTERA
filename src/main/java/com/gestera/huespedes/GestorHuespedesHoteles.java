package com.gestera.huespedes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Reglas de negocio y fachada del registro de huéspedes por hotel. */
public final class GestorHuespedesHoteles implements AutoCloseable {
    public static final int HUESPEDES_PREDERMINADOS = 2;
    public static final int HUESPEDES_MAXIMOS = 8;
    public static final int LONGITUD_NOMBRE_MAXIMA = 100;
    public static final int LONGITUD_DOCUMENTO_MAXIMA = 12;
    private static final Pattern NOMBRE_VALIDO = Pattern.compile(
            "[\\p{L}][\\p{L} .'-]*", Pattern.UNICODE_CHARACTER_CLASS);

    private final RepositorioHuespedes repositorio;

    public GestorHuespedesHoteles(RepositorioHuespedes repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
    }

    public ResultadoValidacion validar(List<DatosHuesped> huespedes) {
        if (huespedes == null || huespedes.isEmpty()) {
            return ResultadoValidacion.HUESPEDES_AUSENTES;
        }
        if (huespedes.size() > HUESPEDES_MAXIMOS) {
            return ResultadoValidacion.DEMASIADOS_HUESPEDES;
        }
        Set<String> documentos = new HashSet<>();
        for (DatosHuesped huesped : huespedes) {
            if (huesped == null) {
                return ResultadoValidacion.HUESPED_INVALIDO;
            }
            String nombre = normalizarNombre(huesped.nombreCompleto());
            String documento = normalizarDocumento(huesped.numeroDocumento());
            if (nombre == null) {
                return ResultadoValidacion.NOMBRE_INVALIDO;
            }
            if (huesped.tipoDocumento() == null) {
                return ResultadoValidacion.TIPO_DOCUMENTO_INVALIDO;
            }
            if (documento == null || !huesped.tipoDocumento().admite(documento)) {
                return ResultadoValidacion.DOCUMENTO_INVALIDO;
            }
            if (!documentos.add(huesped.tipoDocumento().codigo() + ':' + documento)) {
                return ResultadoValidacion.DOCUMENTO_DUPLICADO;
            }
        }
        return ResultadoValidacion.VALIDA;
    }

    public void guardarEstancia(
            String codigoHotel,
            int numeroHabitacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            List<DatosHuesped> huespedes) {
        guardarEstancia(
                codigoHotel, numeroHabitacion, "#000000", checkIn, checkOut, huespedes);
    }

    public void guardarEstancia(
            String codigoHotel,
            int numeroHabitacion,
            String colorHabitacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            List<DatosHuesped> huespedes) {
        ResultadoValidacion resultado = validar(huespedes);
        if (resultado != ResultadoValidacion.VALIDA) {
            throw new IllegalArgumentException("Los datos de huéspedes no son válidos: " + resultado);
        }
        if (checkIn == null || checkOut == null || checkOut.isBefore(checkIn)) {
            throw new IllegalArgumentException("La estancia no tiene horarios válidos.");
        }
        if (colorHabitacion == null || !colorHabitacion.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("El color de la habitación no es válido.");
        }
        ArrayList<DatosHuesped> normalizados = new ArrayList<>(huespedes.size());
        for (DatosHuesped huesped : huespedes) {
            normalizados.add(new DatosHuesped(
                    normalizarNombre(huesped.nombreCompleto()),
                    huesped.tipoDocumento(),
                    normalizarDocumento(huesped.numeroDocumento())));
        }
        repositorio.guardarEstancia(
                codigoHotel,
                numeroHabitacion,
                "Habitación " + numeroHabitacion,
                colorHabitacion.toUpperCase(Locale.ROOT),
                checkIn,
                checkOut,
                List.copyOf(normalizados));
    }

    /** Guarda visitantes de un servicio ajeno al inventario de habitaciones. */
    public void guardarVisita(
            String codigoHotel,
            String ubicacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            List<DatosHuesped> huespedes) {
        ResultadoValidacion resultado = validar(huespedes);
        if (resultado != ResultadoValidacion.VALIDA) {
            throw new IllegalArgumentException("Los datos de huéspedes no son válidos: " + resultado);
        }
        String ubicacionNormalizada = ubicacion == null ? "" : ubicacion.trim();
        if (ubicacionNormalizada.isEmpty() || ubicacionNormalizada.length() > 80) {
            throw new IllegalArgumentException("La ubicación no es válida.");
        }
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("La visita no tiene horarios válidos.");
        }
        ArrayList<DatosHuesped> normalizados = new ArrayList<>(huespedes.size());
        for (DatosHuesped huesped : huespedes) {
            normalizados.add(new DatosHuesped(
                    normalizarNombre(huesped.nombreCompleto()),
                    huesped.tipoDocumento(),
                    normalizarDocumento(huesped.numeroDocumento())));
        }
        repositorio.guardarEstancia(
                codigoHotel,
                null,
                ubicacionNormalizada,
                "#000000",
                checkIn,
                checkOut,
                List.copyOf(normalizados));
    }

    public void finalizarEstancia(
            String codigoHotel,
            int numeroHabitacion,
            LocalDateTime checkIn,
            LocalDateTime checkOut) {
        if (checkIn != null && checkOut != null) {
            repositorio.finalizarEstancia(
                    codigoHotel, numeroHabitacion, checkIn, checkOut);
        }
    }

    public List<RegistroHuesped> listar(String codigoHotel) {
        return buscar(codigoHotel, FiltroHuespedes.vacio());
    }

    public List<RegistroHuesped> buscar(String codigoHotel, FiltroHuespedes filtro) {
        return repositorio.buscarPorHotel(
                codigoHotel, filtro == null ? FiltroHuespedes.vacio() : filtro);
    }

    public Map<Integer, List<DatosHuesped>> activosPorHabitacion(String codigoHotel) {
        return repositorio.listarActivosPorHabitacion(codigoHotel);
    }

    @Override
    public void close() {
        repositorio.close();
    }

    private static String normalizarNombre(String valor) {
        String limpio = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
        return limpio.length() >= 2
                        && limpio.length() <= LONGITUD_NOMBRE_MAXIMA
                        && NOMBRE_VALIDO.matcher(limpio).matches()
                ? limpio
                : null;
    }

    private static String normalizarDocumento(String valor) {
        String limpio = valor == null
                ? ""
                : valor.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        return limpio.length() <= LONGITUD_DOCUMENTO_MAXIMA
                        && limpio.chars().allMatch(caracter ->
                                caracter >= '0' && caracter <= '9'
                                        || caracter >= 'A' && caracter <= 'Z')
                ? limpio
                : null;
    }

    public enum ResultadoValidacion {
        VALIDA,
        HUESPEDES_AUSENTES,
        DEMASIADOS_HUESPEDES,
        HUESPED_INVALIDO,
        NOMBRE_INVALIDO,
        TIPO_DOCUMENTO_INVALIDO,
        DOCUMENTO_INVALIDO,
        DOCUMENTO_DUPLICADO
    }
}
