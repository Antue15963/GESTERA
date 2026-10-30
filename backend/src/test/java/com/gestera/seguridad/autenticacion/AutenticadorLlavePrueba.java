package com.gestera.seguridad.autenticacion;

import com.gestera.pruebas.Aserciones;

/** Verifica el contrato de autenticación y el borrado de datos sensibles. */
public final class AutenticadorLlavePrueba {
    private static final String LLAVE = "123456789abc";

    private AutenticadorLlavePrueba() {
    }

    public static int ejecutar() {
        aceptaLaLlaveExactaYBorraLosArreglos();
        rechazaUnaLlaveDistintaYLaBorra();
        informaEntradasAusentes();
        comparaSinCambiarMayusculasNiEspacios();
        rechazaConfiguracionesInvalidas();
        cerrarEsIdempotenteEImpideReutilizar();
        return 6;
    }

    private static void aceptaLaLlaveExactaYBorraLosArreglos() {
        char[] configurada = LLAVE.toCharArray();
        try (AutenticadorLlave autenticador = new AutenticadorLlave(configurada)) {
            Aserciones.arregloBorrado(
                    configurada, "El constructor debe borrar la llave que recibe");
            char[] recibida = LLAVE.toCharArray();
            Aserciones.iguales(
                    AutenticadorLlave.Resultado.CONCEDIDO,
                    autenticador.autenticar(recibida),
                    "La llave exacta debe conceder el acceso");
            Aserciones.arregloBorrado(
                    recibida, "Una llave aceptada debe borrarse después de validarla");
        }
    }

    private static void rechazaUnaLlaveDistintaYLaBorra() {
        try (AutenticadorLlave autenticador = nuevoAutenticador()) {
            char[] recibida = "llave-incorrecta".toCharArray();
            Aserciones.iguales(
                    AutenticadorLlave.Resultado.DENEGADO,
                    autenticador.autenticar(recibida),
                    "Una llave distinta debe denegarse");
            Aserciones.arregloBorrado(
                    recibida, "También debe borrarse una llave rechazada");
        }
    }

    private static void informaEntradasAusentes() {
        try (AutenticadorLlave autenticador = nuevoAutenticador()) {
            Aserciones.iguales(
                    AutenticadorLlave.Resultado.AUSENTE,
                    autenticador.autenticar(null),
                    "Una referencia nula debe considerarse ausente");
            Aserciones.iguales(
                    AutenticadorLlave.Resultado.AUSENTE,
                    autenticador.autenticar(new char[0]),
                    "Un arreglo vacío debe considerarse ausente");
        }
    }

    private static void comparaSinCambiarMayusculasNiEspacios() {
        try (AutenticadorLlave autenticador = nuevoAutenticador()) {
            Aserciones.iguales(
                    AutenticadorLlave.Resultado.DENEGADO,
                    autenticador.autenticar("123456789ABC".toCharArray()),
                    "La llave debe distinguir mayúsculas de minúsculas");
            Aserciones.iguales(
                    AutenticadorLlave.Resultado.DENEGADO,
                    autenticador.autenticar((" " + LLAVE + " ").toCharArray()),
                    "Los espacios forman parte de la llave y no deben recortarse");
        }
    }

    private static void rechazaConfiguracionesInvalidas() {
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new AutenticadorLlave(null),
                "No debe admitirse una llave configurada nula");
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new AutenticadorLlave(new char[0]),
                "No debe admitirse una llave configurada vacía");
    }

    private static void cerrarEsIdempotenteEImpideReutilizar() {
        AutenticadorLlave autenticador = nuevoAutenticador();
        autenticador.close();
        autenticador.close();
        char[] recibida = LLAVE.toCharArray();
        Aserciones.lanza(
                IllegalStateException.class,
                () -> autenticador.autenticar(recibida),
                "Un autenticador cerrado no debe aceptar operaciones");
        Aserciones.arregloBorrado(
                recibida, "Incluso una operación inválida debe borrar el arreglo recibido");
    }

    private static AutenticadorLlave nuevoAutenticador() {
        return new AutenticadorLlave(LLAVE.toCharArray());
    }
}
