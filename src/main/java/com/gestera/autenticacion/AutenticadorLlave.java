package com.gestera.autenticacion;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Valida la llave en el servidor y borra los arreglos sensibles que recibe. */
public final class AutenticadorLlave implements AutoCloseable {
    private static final int ITERACIONES = 65_536;
    private static final int BITS_DERIVADOS = 256;
    private static final int BYTES_SAL = 16;

    private byte[] sal;
    private byte[] derivadoEsperado;
    private final int iteraciones;

    /** El constructor toma propiedad del arreglo y lo borra al finalizar. */
    public AutenticadorLlave(char[] llaveConfigurada) {
        iteraciones=ITERACIONES;
        if (llaveConfigurada == null || llaveConfigurada.length == 0) {
            throw new IllegalArgumentException("La llave configurada no puede estar vacía.");
        }

        sal = new byte[BYTES_SAL];
        new SecureRandom().nextBytes(sal);
        try {
            derivadoEsperado = derivar(llaveConfigurada, sal,iteraciones);
        } finally {
            Arrays.fill(llaveConfigurada, '\0');
        }
    }

    private AutenticadorLlave(MaterialClave material,boolean restaurado) {
        sal=material.sal();
        derivadoEsperado=material.hash();
        iteraciones=material.iteraciones();
    }

    public static AutenticadorLlave restaurar(MaterialClave material) {
        return new AutenticadorLlave(material,true);
    }

    public synchronized MaterialClave exportarMaterial() {
        asegurarAbierto();
        return new MaterialClave(sal,derivadoEsperado,iteraciones);
    }

    public record MaterialClave(byte[] sal, byte[] hash, int iteraciones) implements AutoCloseable {
        public MaterialClave {
            if (sal==null || hash==null || sal.length!=16 || hash.length!=32
                    || iteraciones<10000 || iteraciones>10000000)
                throw new IllegalArgumentException("Material de credencial no valido.");
            sal=sal.clone();
            hash=hash.clone();
        }
        @Override public byte[] sal() { return sal.clone(); }
        @Override public byte[] hash() { return hash.clone(); }
        @Override public void close() { Arrays.fill(sal,(byte)0); Arrays.fill(hash,(byte)0); }
        @Override public String toString() { return "MaterialClave[protegido]"; }
    }

    public Resultado autenticar(char[] llaveRecibida) {
        byte[] salDeTrabajo = null;
        byte[] esperadoDeTrabajo = null;
        byte[] derivadoRecibido = null;
        try {
            synchronized (this) {
                asegurarAbierto();
                salDeTrabajo = sal.clone();
                esperadoDeTrabajo = derivadoEsperado.clone();
            }
            if (llaveRecibida == null || llaveRecibida.length == 0) {
                return Resultado.AUSENTE;
            }

            derivadoRecibido = derivar(llaveRecibida, salDeTrabajo,iteraciones);
            return MessageDigest.isEqual(esperadoDeTrabajo, derivadoRecibido)
                    ? Resultado.CONCEDIDO
                    : Resultado.DENEGADO;
        } finally {
            if (llaveRecibida != null) {
                Arrays.fill(llaveRecibida, '\0');
            }
            if (derivadoRecibido != null) {
                Arrays.fill(derivadoRecibido, (byte) 0);
            }
            if (esperadoDeTrabajo != null) {
                Arrays.fill(esperadoDeTrabajo, (byte) 0);
            }
            if (salDeTrabajo != null) {
                Arrays.fill(salDeTrabajo, (byte) 0);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (sal != null) {
            Arrays.fill(sal, (byte) 0);
            sal = null;
        }
        if (derivadoEsperado != null) {
            Arrays.fill(derivadoEsperado, (byte) 0);
            derivadoEsperado = null;
        }
    }

    private static byte[] derivar(char[] llave, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(llave, sal, iteraciones, BITS_DERIVADOS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(especificacion)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No fue posible preparar la validación segura.", exception);
        } finally {
            especificacion.clearPassword();
        }
    }

    private void asegurarAbierto() {
        if (derivadoEsperado == null || sal == null) {
            throw new IllegalStateException("El autenticador ya fue cerrado.");
        }
    }

    public enum Resultado {
        CONCEDIDO,
        AUSENTE,
        DENEGADO
    }
}
