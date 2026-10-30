package com.gestera.modelo.hoteles;

import com.gestera.seguridad.autenticacion.AutenticadorLlave.Resultado;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.gestera.modelo.hoteles.GestorLlavesHoteles.ResultadoCambio;
import com.gestera.pruebas.Aserciones;
import java.util.List;

/** Verifica credenciales reemplazables e independientes para cada hotel. */
public final class GestorLlavesHotelesPrueba {
    private static final String LLAVE_INICIAL = "gesteraperu123";

    private GestorLlavesHotelesPrueba() {
    }

    public static int ejecutar() {
        iniciaTodosLosHotelesConCambioRequerido();
        cambiaSoloLaLlaveDelHotelIndicado();
        versionaYRechazaCambiosDesactualizados();
        aceptaLasLongitudesLimite();
        rechazaLongitudesYComposicionInvalidas();
        rechazaConfirmacionesInvalidas();
        rechazaHotelesDesconocidos();
        validaLaConstruccionYBorraLaLlaveInicial();
        cierraCredencialesYBorraEntradasPosteriores();
        return 9;
    }

    private static void iniciaTodosLosHotelesConCambioRequerido() {
        try (GestorLlavesHoteles gestor = nuevoGestor()) {
            for (Hotel hotel : hoteles()) {
                Aserciones.verdadero(
                        gestor.requiereCambio(hotel.codigo()),
                        "Cada hotel debe comenzar con la configuracion de llave requerida");
                comprobarAutenticacion(
                        gestor,
                        hotel.codigo(),
                        LLAVE_INICIAL,
                        Resultado.CONCEDIDO,
                        "Cada hotel debe aceptar inicialmente la llave predeterminada");
            }
        }
    }

    private static void cambiaSoloLaLlaveDelHotelIndicado() {
        String nueva = "NuevaSol8";
        try (GestorLlavesHoteles gestor = nuevoGestor()) {
            comprobarCambio(
                    gestor,
                    "sol-pucusana",
                    nueva,
                    nueva,
                    ResultadoCambio.ACTUALIZADA,
                    "Una llave valida debe actualizar el hotel indicado");
            Aserciones.falso(
                    gestor.requiereCambio("sol-pucusana"),
                    "El hotel actualizado ya no debe requerir configuracion");
            comprobarAutenticacion(
                    gestor, "sol-pucusana", LLAVE_INICIAL, Resultado.DENEGADO,
                    "La llave anterior debe dejar de entrar al hotel actualizado");
            comprobarAutenticacion(
                    gestor, "sol-pucusana", nueva, Resultado.CONCEDIDO,
                    "La llave nueva debe entrar al hotel actualizado");

            for (String codigo : new String[] {
                "brisa-marina", "valle-dorado", "costa-serena"
            }) {
                Aserciones.verdadero(
                        gestor.requiereCambio(codigo),
                        "Cambiar otro hotel no debe alterar su estado inicial");
                comprobarAutenticacion(
                        gestor, codigo, LLAVE_INICIAL, Resultado.CONCEDIDO,
                        "Los otros hoteles deben conservar la llave predeterminada");
                comprobarAutenticacion(
                        gestor, codigo, nueva, Resultado.DENEGADO,
                        "La nueva llave de un hotel no debe abrir los demas");
            }
        }
    }

    private static void aceptaLasLongitudesLimite() {
        String minima = "A" + "b".repeat(GestorLlavesHoteles.LONGITUD_MINIMA - 2) + "1";
        String maxima = "A" + "b".repeat(GestorLlavesHoteles.LONGITUD_MAXIMA - 2) + "1";
        Aserciones.iguales(GestorLlavesHoteles.LONGITUD_MINIMA, minima.length(),
                "La preparacion debe usar exactamente el limite minimo");
        Aserciones.iguales(GestorLlavesHoteles.LONGITUD_MAXIMA, maxima.length(),
                "La preparacion debe usar exactamente el limite maximo");

        try (GestorLlavesHoteles gestor = nuevoGestor()) {
            comprobarCambio(gestor, "sol-pucusana", minima, minima,
                    ResultadoCambio.ACTUALIZADA,
                    "Una llave de ocho caracteres debe aceptarse");
            comprobarCambio(gestor, "brisa-marina", maxima, maxima,
                    ResultadoCambio.ACTUALIZADA,
                    "Una llave de quince caracteres debe aceptarse");
            comprobarAutenticacion(gestor, "sol-pucusana", minima, Resultado.CONCEDIDO,
                    "La llave minima actualizada debe autenticar");
            comprobarAutenticacion(gestor, "brisa-marina", maxima, Resultado.CONCEDIDO,
                    "La llave maxima actualizada debe autenticar");
        }
    }

    private static void versionaYRechazaCambiosDesactualizados() {
        try (GestorLlavesHoteles gestor = nuevoGestor()) {
            char[] inicial = LLAVE_INICIAL.toCharArray();
            GestorLlavesHoteles.ResultadoAcceso acceso =
                    gestor.autenticarConEstado("sol-pucusana", inicial);
            Aserciones.arregloBorrado(inicial,
                    "La autenticacion con estado tambien debe borrar la llave");
            Aserciones.iguales(Resultado.CONCEDIDO, acceso.resultado(),
                    "La llave inicial debe conceder acceso con estado");
            Aserciones.iguales(1L, acceso.version(),
                    "La primera credencial debe comenzar en la version uno");
            Aserciones.verdadero(acceso.requiereCambio(),
                    "La version inicial debe requerir configuracion");
            Aserciones.verdadero(gestor.esVersionActual("sol-pucusana", acceso.version()),
                    "La version obtenida al autenticar debe estar vigente");

            String nueva = "NuevaSol8";
            char[] nuevaChars = nueva.toCharArray();
            char[] confirmacion = nueva.toCharArray();
            Aserciones.iguales(
                    ResultadoCambio.ACTUALIZADA,
                    gestor.cambiar(
                            "sol-pucusana", acceso.version(), nuevaChars, confirmacion),
                    "La version vigente debe permitir exactamente un cambio");
            Aserciones.arregloBorrado(nuevaChars,
                    "El cambio versionado debe borrar la llave nueva");
            Aserciones.arregloBorrado(confirmacion,
                    "El cambio versionado debe borrar la confirmacion");
            Aserciones.iguales(2L, gestor.versionActual("sol-pucusana"),
                    "Un cambio exitoso debe incrementar la version");
            Aserciones.falso(gestor.esVersionActual("sol-pucusana", acceso.version()),
                    "La version anterior debe quedar obsoleta");

            char[] candidataObsoleta = "OtraSol9".toCharArray();
            char[] confirmaObsoleta = "OtraSol9".toCharArray();
            Aserciones.iguales(
                    ResultadoCambio.VERSION_DESACTUALIZADA,
                    gestor.cambiar(
                            "sol-pucusana",
                            acceso.version(),
                            candidataObsoleta,
                            confirmaObsoleta),
                    "Una segunda solicitud con la version anterior debe rechazarse");
            Aserciones.arregloBorrado(candidataObsoleta,
                    "El rechazo por version debe borrar la candidata");
            Aserciones.arregloBorrado(confirmaObsoleta,
                    "El rechazo por version debe borrar la confirmacion");
            comprobarAutenticacion(gestor, "sol-pucusana", nueva, Resultado.CONCEDIDO,
                    "El rechazo obsoleto debe conservar la primera actualizacion");
            comprobarAutenticacion(gestor, "sol-pucusana", "OtraSol9", Resultado.DENEGADO,
                    "La candidata obsoleta nunca debe instalarse");

            char[] nuevaAutenticacion = nueva.toCharArray();
            GestorLlavesHoteles.ResultadoAcceso actualizado =
                    gestor.autenticarConEstado("sol-pucusana", nuevaAutenticacion);
            Aserciones.arregloBorrado(nuevaAutenticacion,
                    "La consulta posterior debe borrar la credencial recibida");
            Aserciones.iguales(2L, actualizado.version(),
                    "La autenticacion debe entregar la version nueva");
            Aserciones.falso(actualizado.requiereCambio(),
                    "La version rotada ya no debe exigir configuracion inicial");
        }
    }

    private static void rechazaLongitudesYComposicionInvalidas() {
        String corta = "A" + "b".repeat(GestorLlavesHoteles.LONGITUD_MINIMA - 3) + "1";
        String larga = "A" + "b".repeat(GestorLlavesHoteles.LONGITUD_MAXIMA - 1) + "1";
        try (GestorLlavesHoteles gestor = nuevoGestor()) {
            comprobarCambio(gestor, "sol-pucusana", null, null,
                    ResultadoCambio.LLAVE_AUSENTE,
                    "Una llave nula debe informarse como ausente");
            comprobarCambio(gestor, "sol-pucusana", "", "",
                    ResultadoCambio.LLAVE_AUSENTE,
                    "Una llave vacia debe informarse como ausente");
            comprobarCambio(gestor, "sol-pucusana", corta, corta,
                    ResultadoCambio.LONGITUD_INVALIDA,
                    "Una llave de siete caracteres debe rechazarse");
            comprobarCambio(gestor, "sol-pucusana", larga, larga,
                    ResultadoCambio.LONGITUD_INVALIDA,
                    "Una llave de dieciseis caracteres debe rechazarse");
            comprobarCambio(gestor, "sol-pucusana", "abcdefgh1", "abcdefgh1",
                    ResultadoCambio.SIN_MAYUSCULA,
                    "La llave debe contener al menos una mayuscula");
            comprobarCambio(gestor, "sol-pucusana", "Abcdefgh", "Abcdefgh",
                    ResultadoCambio.SIN_NUMERO,
                    "La llave debe contener al menos un numero");

            Aserciones.verdadero(gestor.requiereCambio("sol-pucusana"),
                    "Los rechazos no deben completar la configuracion inicial");
            comprobarAutenticacion(
                    gestor, "sol-pucusana", LLAVE_INICIAL, Resultado.CONCEDIDO,
                    "Los rechazos deben conservar la llave anterior");
        }
    }

    private static void rechazaConfirmacionesInvalidas() {
        try (GestorLlavesHoteles gestor = nuevoGestor()) {
            comprobarCambio(gestor, "sol-pucusana", "NuevaSol8", null,
                    ResultadoCambio.CONFIRMACION_AUSENTE,
                    "Una confirmacion nula debe rechazarse");
            comprobarCambio(gestor, "sol-pucusana", "NuevaSol8", "",
                    ResultadoCambio.CONFIRMACION_AUSENTE,
                    "Una confirmacion vacia debe rechazarse");
            comprobarCambio(gestor, "sol-pucusana", "NuevaSol8", "Diferente9",
                    ResultadoCambio.CONFIRMACION_DISTINTA,
                    "La confirmacion debe coincidir exactamente");
            Aserciones.verdadero(gestor.requiereCambio("sol-pucusana"),
                    "Una confirmacion invalida no debe cambiar el estado");
            comprobarAutenticacion(
                    gestor, "sol-pucusana", LLAVE_INICIAL, Resultado.CONCEDIDO,
                    "Una confirmacion invalida debe conservar la llave anterior");
        }
    }

    private static void rechazaHotelesDesconocidos() {
        try (GestorLlavesHoteles gestor = nuevoGestor()) {
            comprobarCambio(gestor, "no-existe", "NuevaSol8", "NuevaSol8",
                    ResultadoCambio.HOTEL_DESCONOCIDO,
                    "No debe crear credenciales para un codigo desconocido");
            Aserciones.falso(gestor.requiereCambio("no-existe"),
                    "Un codigo desconocido no debe tener estado de configuracion");
            comprobarAutenticacion(
                    gestor, "no-existe", LLAVE_INICIAL, Resultado.DENEGADO,
                    "Un codigo desconocido nunca debe autenticar");
        }
    }

    private static void validaLaConstruccionYBorraLaLlaveInicial() {
        char[] llave = LLAVE_INICIAL.toCharArray();
        try (GestorLlavesHoteles ignored = new GestorLlavesHoteles(hoteles(), llave)) {
            Aserciones.arregloBorrado(llave,
                    "El constructor debe tomar y borrar la llave inicial");
        }

        char[] sinHoteles = LLAVE_INICIAL.toCharArray();
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new GestorLlavesHoteles(List.of(), sinHoteles),
                "El gestor debe exigir al menos un hotel");
        Aserciones.arregloBorrado(sinHoteles,
                "La llave debe borrarse aunque la construccion falle");
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new GestorLlavesHoteles(hoteles(), null),
                "El gestor debe exigir una llave inicial");

        Hotel primero = hoteles().get(0);
        List<Hotel> repetidos = List.of(primero, primero);
        char[] llaveRepetida = LLAVE_INICIAL.toCharArray();
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new GestorLlavesHoteles(repetidos, llaveRepetida),
                "Los codigos de hotel repetidos deben rechazarse");
        Aserciones.arregloBorrado(llaveRepetida,
                "La llave debe borrarse al rechazar hoteles repetidos");
    }

    private static void cierraCredencialesYBorraEntradasPosteriores() {
        GestorLlavesHoteles gestor = nuevoGestor();
        gestor.close();
        gestor.close();
        char[] recibida = LLAVE_INICIAL.toCharArray();
        Aserciones.lanza(
                IllegalStateException.class,
                () -> gestor.autenticar("sol-pucusana", recibida),
                "Un gestor cerrado no debe autenticar");
        Aserciones.arregloBorrado(recibida,
                "Incluso cerrado debe borrar la llave recibida");

        char[] nueva = "NuevaSol8".toCharArray();
        char[] confirmacion = "NuevaSol8".toCharArray();
        Aserciones.lanza(
                IllegalStateException.class,
                () -> gestor.cambiar("sol-pucusana", nueva, confirmacion),
                "Un gestor cerrado no debe cambiar credenciales");
        Aserciones.arregloBorrado(nueva,
                "Debe borrar la llave nueva aunque el gestor este cerrado");
        Aserciones.arregloBorrado(confirmacion,
                "Debe borrar la confirmacion aunque el gestor este cerrado");
        Aserciones.lanza(
                IllegalStateException.class,
                () -> gestor.requiereCambio("sol-pucusana"),
                "Un gestor cerrado no debe consultar estados");
    }

    private static void comprobarCambio(
            GestorLlavesHoteles gestor,
            String codigo,
            String nueva,
            String confirmacion,
            ResultadoCambio esperado,
            String mensaje) {
        char[] nuevaChars = nueva == null ? null : nueva.toCharArray();
        char[] confirmacionChars = confirmacion == null ? null : confirmacion.toCharArray();
        Aserciones.iguales(
                esperado,
                gestor.cambiar(codigo, nuevaChars, confirmacionChars),
                mensaje);
        if (nuevaChars != null) {
            Aserciones.arregloBorrado(nuevaChars,
                    "El cambio siempre debe borrar la llave nueva recibida");
        }
        if (confirmacionChars != null) {
            Aserciones.arregloBorrado(confirmacionChars,
                    "El cambio siempre debe borrar la confirmacion recibida");
        }
    }

    private static void comprobarAutenticacion(
            GestorLlavesHoteles gestor,
            String codigo,
            String llave,
            Resultado esperado,
            String mensaje) {
        char[] recibida = llave.toCharArray();
        Aserciones.iguales(esperado, gestor.autenticar(codigo, recibida), mensaje);
        Aserciones.arregloBorrado(recibida,
                "La autenticacion siempre debe borrar la llave recibida");
    }

    private static GestorLlavesHoteles nuevoGestor() {
        return new GestorLlavesHoteles(hoteles(), LLAVE_INICIAL.toCharArray());
    }

    private static List<Hotel> hoteles() {
        return new CatalogoHoteles().todos();
    }
}
