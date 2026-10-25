package com.gestera.pruebas;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Aserciones mínimas para ejecutar las pruebas sin bibliotecas externas. */
public final class Aserciones {
    private Aserciones() {
    }

    public static void iguales(Object esperado, Object obtenido, String mensaje) {
        if (!Objects.equals(esperado, obtenido)) {
            throw new AssertionError(
                    mensaje + " (esperado: " + esperado + ", obtenido: " + obtenido + ")");
        }
    }

    public static void verdadero(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    public static void falso(boolean condicion, String mensaje) {
        verdadero(!condicion, mensaje);
    }

    public static void contiene(String texto, String fragmento, String mensaje) {
        if (texto == null || !texto.contains(fragmento)) {
            throw new AssertionError(mensaje + " (no se encontró: " + fragmento + ")");
        }
    }

    public static void noContieneIgnorandoMayusculas(
            String texto,
            String fragmento,
            String mensaje) {
        String textoNormalizado = texto == null ? "" : texto.toLowerCase(Locale.ROOT);
        String fragmentoNormalizado = fragmento.toLowerCase(Locale.ROOT);
        if (textoNormalizado.contains(fragmentoNormalizado)) {
            throw new AssertionError(mensaje + " (se encontró: " + fragmento + ")");
        }
    }

    public static void caracteresIguales(String esperado, char[] obtenido, String mensaje) {
        if (!Arrays.equals(esperado.toCharArray(), obtenido)) {
            throw new AssertionError(mensaje);
        }
    }

    public static void arregloBorrado(char[] valor, String mensaje) {
        if (valor == null) {
            throw new AssertionError(mensaje + " (el arreglo es nulo)");
        }
        for (char caracter : valor) {
            if (caracter != '\0') {
                throw new AssertionError(mensaje);
            }
        }
    }

    public static <T extends Throwable> T lanza(
            Class<T> tipoEsperado,
            Operacion operacion,
            String mensaje) {
        try {
            operacion.ejecutar();
        } catch (Throwable error) {
            if (tipoEsperado.isInstance(error)) {
                return tipoEsperado.cast(error);
            }
            throw new AssertionError(
                    mensaje + " (se obtuvo " + error.getClass().getName() + ")", error);
        }
        throw new AssertionError(mensaje + " (no se lanzó ninguna excepción)");
    }

    @FunctionalInterface
    public interface Operacion {
        void ejecutar() throws Throwable;
    }
}
