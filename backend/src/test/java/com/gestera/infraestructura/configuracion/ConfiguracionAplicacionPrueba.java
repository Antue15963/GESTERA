package com.gestera.infraestructura.configuracion;

import com.gestera.pruebas.Aserciones;
import java.time.Duration;
import java.util.Arrays;

/** Pruebas de seleccion, validacion y proteccion de la configuracion. */
public final class ConfiguracionAplicacionPrueba {
    private ConfiguracionAplicacionPrueba() {
    }

    public static int ejecutar() {
        usaLlavePredeterminada();
        usaLlaveDelEntorno();
        propiedadDeLlaveTienePrioridad();
        rechazaFuentesDeLlaveVacias();
        usaLlaveHotelPredeterminada();
        propiedadDeLlaveHotelTienePrioridad();
        rechazaFuentesDeLlaveHotelVacias();
        resuelvePuertoPredeterminado();
        resuelvePuertoConfiguradoConPrioridad();
        rechazaPuertosInvalidos();
        protegeLasCopiasDeLasLlaves();
        conservaElConstructorCompatible();
        return 12;
    }

    private static void usaLlavePredeterminada() {
        comprobarLlave("123456789abc", null, null,
                "Debe conservar la llave predeterminada solicitada");
    }

    private static void usaLlaveDelEntorno() {
        comprobarLlave("llave-del-entorno", null, "llave-del-entorno",
                "Debe usar el entorno cuando no hay propiedad");
    }

    private static void propiedadDeLlaveTienePrioridad() {
        comprobarLlave("llave-de-propiedad", "llave-de-propiedad", "llave-del-entorno",
                "La propiedad debe prevalecer sobre el entorno");
    }

    private static void rechazaFuentesDeLlaveVacias() {
        Aserciones.lanza(
                IllegalStateException.class,
                () -> ConfiguracionAplicacion.resolverLlave("  ", "respaldo"),
                "Una propiedad vacia no debe caer silenciosamente al entorno");
        Aserciones.lanza(
                IllegalStateException.class,
                () -> ConfiguracionAplicacion.resolverLlave(null, "\t"),
                "Una variable vacia debe rechazarse");
    }

    private static void usaLlaveHotelPredeterminada() {
        comprobarLlaveHotel("gesteraperu123", null, null,
                "Debe usar la llave de hotel predeterminada solicitada");
    }

    private static void propiedadDeLlaveHotelTienePrioridad() {
        comprobarLlaveHotel(
                "llave-hotel-propiedad",
                "llave-hotel-propiedad",
                "llave-hotel-entorno",
                "La propiedad de hotel debe prevalecer sobre el entorno");
        comprobarLlaveHotel(
                "llave-hotel-entorno",
                null,
                "llave-hotel-entorno",
                "Debe admitir la llave de hotel configurada en el entorno");
    }

    private static void rechazaFuentesDeLlaveHotelVacias() {
        Aserciones.lanza(
                IllegalStateException.class,
                () -> ConfiguracionAplicacion.resolverLlaveHotel("  ", "respaldo"),
                "Una propiedad de hotel vacia no debe caer silenciosamente al entorno");
        Aserciones.lanza(
                IllegalStateException.class,
                () -> ConfiguracionAplicacion.resolverLlaveHotel(null, "\t"),
                "Una variable de llave de hotel vacia debe rechazarse");
    }

    private static void resuelvePuertoPredeterminado() {
        Aserciones.iguales(8080, ConfiguracionAplicacion.resolverPuerto(null, null),
                "El puerto predeterminado debe ser 8080");
    }

    private static void resuelvePuertoConfiguradoConPrioridad() {
        Aserciones.iguales(
                9090,
                ConfiguracionAplicacion.resolverPuerto("9090", "7070"),
                "La propiedad del puerto debe tener prioridad");
        Aserciones.iguales(
                65_535,
                ConfiguracionAplicacion.resolverPuerto(null, "65535"),
                "Debe aceptar el mayor puerto valido");
    }

    private static void rechazaPuertosInvalidos() {
        for (String valor : new String[] {"texto", "0", "-1", "65536", " "}) {
            Aserciones.lanza(
                    IllegalStateException.class,
                    () -> ConfiguracionAplicacion.resolverPuerto(valor, null),
                    "Debe rechazar el puerto invalido: " + valor);
        }
    }

    private static void protegeLasCopiasDeLasLlaves() {
        char[] origen = "llave-privada".toCharArray();
        char[] origenHotel = "llave-hotel-privada".toCharArray();
        ConfiguracionAplicacion configuracion = new ConfiguracionAplicacion(
                "127.0.0.1", 0, origen, origenHotel, Duration.ofMinutes(5), false);
        try {
            origen[0] = 'X';
            origenHotel[0] = 'X';
            char[] primera = configuracion.copiarLlaveAcceso();
            char[] primeraHotel = configuracion.copiarLlaveHotel();
            Aserciones.caracteresIguales(
                    "llave-privada", primera, "El constructor debe guardar una copia independiente");
            Aserciones.caracteresIguales(
                    "llave-hotel-privada",
                    primeraHotel,
                    "El constructor debe copiar independientemente la llave del hotel");
            primera[0] = 'Y';
            primeraHotel[0] = 'Y';
            char[] segunda = configuracion.copiarLlaveAcceso();
            char[] segundaHotel = configuracion.copiarLlaveHotel();
            try {
                Aserciones.caracteresIguales(
                        "llave-privada", segunda, "Cada lectura debe entregar una copia nueva");
                Aserciones.caracteresIguales(
                        "llave-hotel-privada",
                        segundaHotel,
                        "Cada lectura de la llave del hotel debe entregar una copia nueva");
            } finally {
                Arrays.fill(segunda, '\0');
                Arrays.fill(segundaHotel, '\0');
            }
            Arrays.fill(primera, '\0');
            Arrays.fill(primeraHotel, '\0');
        } finally {
            Arrays.fill(origen, '\0');
            Arrays.fill(origenHotel, '\0');
            configuracion.close();
        }
        Aserciones.lanza(
                IllegalStateException.class,
                configuracion::copiarLlaveAcceso,
                "Una configuracion cerrada no debe revelar nuevamente la llave");
        Aserciones.lanza(
                IllegalStateException.class,
                configuracion::copiarLlaveHotel,
                "Una configuracion cerrada no debe revelar la llave del hotel");
    }

    private static void conservaElConstructorCompatible() {
        char[] llave = "llave-global".toCharArray();
        try (ConfiguracionAplicacion configuracion = new ConfiguracionAplicacion(
                "127.0.0.1", 0, llave, Duration.ofMinutes(5), false)) {
            char[] llaveHotel = configuracion.copiarLlaveHotel();
            try {
                Aserciones.caracteresIguales(
                        "gesteraperu123",
                        llaveHotel,
                        "El constructor anterior debe conservar una llave de hotel funcional");
            } finally {
                Arrays.fill(llaveHotel, '\0');
            }
        } finally {
            Arrays.fill(llave, '\0');
        }
    }

    private static void comprobarLlave(
            String esperada, String propiedad, String entorno, String mensaje) {
        char[] llave = ConfiguracionAplicacion.resolverLlave(propiedad, entorno);
        try {
            Aserciones.caracteresIguales(esperada, llave, mensaje);
        } finally {
            Arrays.fill(llave, '\0');
        }
    }

    private static void comprobarLlaveHotel(
            String esperada, String propiedad, String entorno, String mensaje) {
        char[] llave = ConfiguracionAplicacion.resolverLlaveHotel(propiedad, entorno);
        try {
            Aserciones.caracteresIguales(esperada, llave, mensaje);
        } finally {
            Arrays.fill(llave, '\0');
        }
    }
}
