package com.gestera.configuracion;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.Function;
import org.springframework.core.env.Environment;

/** Centraliza los valores que pueden cambiar entre instalaciones de Gestera. */
public final class ConfiguracionAplicacion implements AutoCloseable {
    public static final String NOMBRE_APLICACION = "Gestera";
    public static final String PROPIEDAD_LLAVE = "gestera.llave.acceso";
    public static final String VARIABLE_LLAVE = "GESTERA_LLAVE_ACCESO";
    public static final String PROPIEDAD_LLAVE_HOTEL = "gestera.llave.hotel";
    public static final String VARIABLE_LLAVE_HOTEL = "GESTERA_LLAVE_HOTEL";
    public static final String PROPIEDAD_PUERTO = "gestera.puerto";
    public static final String VARIABLE_PUERTO = "GESTERA_PUERTO";
    public static final String PROPIEDAD_DIRECCION = "gestera.direccion";
    public static final String VARIABLE_DIRECCION = "GESTERA_DIRECCION";
    public static final String PROPIEDAD_COOKIE_SEGURA = "gestera.cookie.segura";
    public static final String VARIABLE_COOKIE_SEGURA = "GESTERA_COOKIE_SEGURA";

    private static final String LLAVE_PREDETERMINADA = "123456789abc";
    private static final String LLAVE_HOTEL_PREDETERMINADA = "gesteraperu123";
    private static final int PUERTO_PREDETERMINADO = 8080;
    private static final String DIRECCION_PREDETERMINADA = "127.0.0.1";
    private static final Duration DURACION_SESION = Duration.ofMinutes(30);

    private final String direccion;
    private final int puerto;
    private final Duration duracionSesion;
    private final boolean cookieSegura;
    private char[] llaveAcceso;
    private char[] llaveHotel;

    public ConfiguracionAplicacion(
            String direccion,
            int puerto,
            char[] llaveAcceso,
            Duration duracionSesion,
            boolean cookieSegura) {
        this(
                direccion,
                puerto,
                llaveAcceso,
                LLAVE_HOTEL_PREDETERMINADA.toCharArray(),
                duracionSesion,
                cookieSegura);
    }

    public ConfiguracionAplicacion(
            String direccion,
            int puerto,
            char[] llaveAcceso,
            char[] llaveHotel,
            Duration duracionSesion,
            boolean cookieSegura) {
        if (direccion == null || direccion.isBlank()) {
            throw new IllegalArgumentException("La dirección del servidor es obligatoria.");
        }
        if (puerto < 0 || puerto > 65_535) {
            throw new IllegalArgumentException("El puerto debe estar entre 0 y 65535.");
        }
        if (llaveAcceso == null || llaveAcceso.length == 0) {
            throw new IllegalArgumentException("La llave de acceso es obligatoria.");
        }
        if (llaveHotel == null || llaveHotel.length == 0) {
            throw new IllegalArgumentException("La llave del hotel es obligatoria.");
        }
        if (duracionSesion == null || duracionSesion.isZero() || duracionSesion.isNegative()) {
            throw new IllegalArgumentException("La duración de sesión debe ser positiva.");
        }

        this.direccion = direccion;
        this.puerto = puerto;
        this.llaveAcceso = Arrays.copyOf(llaveAcceso, llaveAcceso.length);
        this.llaveHotel = Arrays.copyOf(llaveHotel, llaveHotel.length);
        this.duracionSesion = duracionSesion;
        this.cookieSegura = cookieSegura;
    }

    public static ConfiguracionAplicacion cargar() {
        return cargar(System::getProperty);
    }

    public static ConfiguracionAplicacion cargar(Environment entorno) {
        return cargar(entorno::getProperty);
    }

    private static ConfiguracionAplicacion cargar(Function<String, String> propiedades) {
        char[] llave = null;
        char[] llaveHotel = null;
        try {
            llave = resolverLlave(
                    propiedades.apply(PROPIEDAD_LLAVE),
                    System.getenv(VARIABLE_LLAVE));
            llaveHotel = resolverLlaveHotel(
                    propiedades.apply(PROPIEDAD_LLAVE_HOTEL),
                    System.getenv(VARIABLE_LLAVE_HOTEL));
            String direccion = resolverTexto(
                    propiedades.apply(PROPIEDAD_DIRECCION),
                    System.getenv(VARIABLE_DIRECCION),
                    DIRECCION_PREDETERMINADA,
                    PROPIEDAD_DIRECCION);
            int puerto = resolverPuerto(
                    propiedades.apply(PROPIEDAD_PUERTO),
                    System.getenv(VARIABLE_PUERTO));
            boolean cookieSegura = resolverBooleano(
                    propiedades.apply(PROPIEDAD_COOKIE_SEGURA),
                    System.getenv(VARIABLE_COOKIE_SEGURA),
                    false,
                    PROPIEDAD_COOKIE_SEGURA);
            return new ConfiguracionAplicacion(
                    direccion, puerto, llave, llaveHotel, DURACION_SESION, cookieSegura);
        } finally {
            borrar(llave);
            borrar(llaveHotel);
        }
    }

    public static char[] copiarLlaveHotelPredeterminada() {
        return LLAVE_HOTEL_PREDETERMINADA.toCharArray();
    }

    public String direccion() {
        return direccion;
    }

    public int puerto() {
        return puerto;
    }

    public Duration duracionSesion() {
        return duracionSesion;
    }

    public boolean cookieSegura() {
        return cookieSegura;
    }

    public synchronized char[] copiarLlaveAcceso() {
        asegurarAbierta();
        return Arrays.copyOf(llaveAcceso, llaveAcceso.length);
    }

    public synchronized char[] copiarLlaveHotel() {
        asegurarAbierta();
        return Arrays.copyOf(llaveHotel, llaveHotel.length);
    }

    @Override
    public synchronized void close() {
        if (llaveAcceso != null) {
            Arrays.fill(llaveAcceso, '\0');
            llaveAcceso = null;
        }
        if (llaveHotel != null) {
            Arrays.fill(llaveHotel, '\0');
            llaveHotel = null;
        }
    }

    static char[] resolverLlave(String propiedad, String entorno) {
        String valor = seleccionar(propiedad, entorno, LLAVE_PREDETERMINADA, PROPIEDAD_LLAVE);
        return valor.toCharArray();
    }

    static char[] resolverLlaveHotel(String propiedad, String entorno) {
        String valor = seleccionar(
                propiedad, entorno, LLAVE_HOTEL_PREDETERMINADA, PROPIEDAD_LLAVE_HOTEL);
        return valor.toCharArray();
    }

    static int resolverPuerto(String propiedad, String entorno) {
        String valor = seleccionar(
                propiedad,
                entorno,
                Integer.toString(PUERTO_PREDETERMINADO),
                PROPIEDAD_PUERTO);
        try {
            int puerto = Integer.parseInt(valor);
            if (puerto < 1 || puerto > 65_535) {
                throw new NumberFormatException();
            }
            return puerto;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("El puerto configurado no es válido: " + valor, exception);
        }
    }

    private static String resolverTexto(
            String propiedad,
            String entorno,
            String predeterminado,
            String nombre) {
        return seleccionar(propiedad, entorno, predeterminado, nombre);
    }

    private static boolean resolverBooleano(
            String propiedad,
            String entorno,
            boolean predeterminado,
            String nombre) {
        String valor = seleccionar(
                propiedad, entorno, Boolean.toString(predeterminado), nombre);
        if (valor.equalsIgnoreCase("true")) {
            return true;
        }
        if (valor.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalStateException(nombre + " debe ser true o false.");
    }

    private static String seleccionar(
            String propiedad,
            String entorno,
            String predeterminado,
            String nombre) {
        if (propiedad != null) {
            return exigirContenido(propiedad, nombre);
        }
        if (entorno != null) {
            return exigirContenido(entorno, nombre);
        }
        return predeterminado;
    }

    private static String exigirContenido(String valor, String nombre) {
        if (valor.isBlank()) {
            throw new IllegalStateException(nombre + " fue definido, pero está vacío.");
        }
        return valor;
    }

    private void asegurarAbierta() {
        if (llaveAcceso == null || llaveHotel == null) {
            throw new IllegalStateException("La configuración ya fue cerrada.");
        }
    }

    private static void borrar(char[] valor) {
        if (valor != null) {
            Arrays.fill(valor, '\0');
        }
    }
}
