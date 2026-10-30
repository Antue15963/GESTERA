package com.gestera.modelo.hoteles;

import com.gestera.repositorios.hoteles.RepositorioCredencialesHotel;

import com.gestera.seguridad.autenticacion.AutenticadorLlave;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Mantiene una credencial independiente y reemplazable para cada hotel registrado. */
public final class GestorLlavesHoteles implements AutoCloseable {
    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 15;

    private final Map<String, CredencialHotel> credenciales;
    private final AtomicBoolean cerrado = new AtomicBoolean();
    private final ReentrantReadWriteLock cicloVida = new ReentrantReadWriteLock();

    /** El gestor toma propiedad del arreglo de la llave inicial y lo borra. */
    public GestorLlavesHoteles(List<Hotel> hoteles, char[] llaveInicial) {
        this(hoteles,llaveInicial,null);
    }

    public GestorLlavesHoteles(List<Hotel> hoteles,char[] llaveInicial,RepositorioCredencialesHotel repositorio) {
        Objects.requireNonNull(hoteles, "hoteles");
        if (hoteles.isEmpty()) {
            borrar(llaveInicial);
            throw new IllegalArgumentException("Debe existir al menos un hotel registrado.");
        }
        if (llaveInicial == null || llaveInicial.length == 0) {
            borrar(llaveInicial);
            throw new IllegalArgumentException("La llave inicial de los hoteles es obligatoria.");
        }

        Map<String, CredencialHotel> credencialesTemporales = new LinkedHashMap<>();
        try {
            for (Hotel hotel : hoteles) {
                Objects.requireNonNull(hotel, "hotel");
                char[] copiaLlave = Arrays.copyOf(llaveInicial, llaveInicial.length);
                CredencialHotel credencial = new CredencialHotel(copiaLlave,hotel.codigo(),repositorio);
                if (credencialesTemporales.putIfAbsent(hotel.codigo(), credencial) != null) {
                    credencial.close();
                    throw new IllegalArgumentException(
                            "El código del hotel está repetido: " + hotel.codigo());
                }
            }
            credenciales = Map.copyOf(credencialesTemporales);
        } catch (RuntimeException | Error exception) {
            credencialesTemporales.values().forEach(CredencialHotel::close);
            throw exception;
        } finally {
            borrar(llaveInicial);
        }
    }

    public AutenticadorLlave.Resultado autenticar(String codigoHotel, char[] llaveRecibida) {
        return autenticarConEstado(codigoHotel, llaveRecibida).resultado();
    }

    public ResultadoAcceso autenticarConEstado(String codigoHotel, char[] llaveRecibida) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            if (codigoHotel == null || codigoHotel.isBlank()) {
                return new ResultadoAcceso(
                        AutenticadorLlave.Resultado.DENEGADO, 0, false);
            }
            CredencialHotel credencial = credenciales.get(codigoHotel);
            if (credencial == null) {
                return new ResultadoAcceso(
                        AutenticadorLlave.Resultado.DENEGADO, 0, false);
            }
            return credencial.autenticarConEstado(llaveRecibida);
        } finally {
            borrar(llaveRecibida);
            cicloVida.readLock().unlock();
        }
    }

    public boolean requiereCambio(String codigoHotel) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            if (codigoHotel == null || codigoHotel.isBlank()) {
                return false;
            }
            CredencialHotel credencial = credenciales.get(codigoHotel);
            return credencial != null && credencial.requiereCambio();
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public boolean esVersionActual(String codigoHotel, long version) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            if (codigoHotel == null || codigoHotel.isBlank() || version < 1) {
                return false;
            }
            CredencialHotel credencial = credenciales.get(codigoHotel);
            return credencial != null && credencial.esVersionActual(version);
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public long versionActual(String codigoHotel) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            if (codigoHotel == null || codigoHotel.isBlank()) {
                return 0;
            }
            CredencialHotel credencial = credenciales.get(codigoHotel);
            return credencial == null ? 0 : credencial.versionActual();
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public ResultadoCambio cambiar(
            String codigoHotel, char[] nuevaLlave, char[] confirmacionLlave) {
        return cambiar(codigoHotel, 0, nuevaLlave, confirmacionLlave);
    }

    public ResultadoCambio cambiar(
            String codigoHotel,
            long versionEsperada,
            char[] nuevaLlave,
            char[] confirmacionLlave) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            if (codigoHotel == null || codigoHotel.isBlank()) {
                return ResultadoCambio.HOTEL_DESCONOCIDO;
            }
            CredencialHotel credencial = credenciales.get(codigoHotel);
            if (credencial == null) {
                return ResultadoCambio.HOTEL_DESCONOCIDO;
            }

            ResultadoCambio validacion = validar(nuevaLlave, confirmacionLlave);
            if (validacion != ResultadoCambio.ACTUALIZADA) {
                return validacion;
            }
            return credencial.cambiar(versionEsperada, nuevaLlave);
        } finally {
            borrar(nuevaLlave);
            borrar(confirmacionLlave);
            cicloVida.readLock().unlock();
        }
    }

    @Override
    public void close() {
        cicloVida.writeLock().lock();
        try {
            if (cerrado.compareAndSet(false, true)) {
                credenciales.values().forEach(CredencialHotel::close);
            }
        } finally {
            cicloVida.writeLock().unlock();
        }
    }

    private static ResultadoCambio validar(char[] nuevaLlave, char[] confirmacionLlave) {
        if (nuevaLlave == null || nuevaLlave.length == 0) {
            return ResultadoCambio.LLAVE_AUSENTE;
        }
        if (nuevaLlave.length < LONGITUD_MINIMA || nuevaLlave.length > LONGITUD_MAXIMA) {
            return ResultadoCambio.LONGITUD_INVALIDA;
        }
        boolean contieneMayuscula = false;
        boolean contieneNumero = false;
        for (char caracter : nuevaLlave) {
            contieneMayuscula |= Character.isUpperCase(caracter);
            contieneNumero |= Character.isDigit(caracter);
        }
        if (!contieneMayuscula) {
            return ResultadoCambio.SIN_MAYUSCULA;
        }
        if (!contieneNumero) {
            return ResultadoCambio.SIN_NUMERO;
        }
        if (confirmacionLlave == null || confirmacionLlave.length == 0) {
            return ResultadoCambio.CONFIRMACION_AUSENTE;
        }
        if (!Arrays.equals(nuevaLlave, confirmacionLlave)) {
            return ResultadoCambio.CONFIRMACION_DISTINTA;
        }
        return ResultadoCambio.ACTUALIZADA;
    }

    private void asegurarAbierto() {
        if (cerrado.get()) {
            throw new IllegalStateException("El gestor de llaves de hoteles ya fue cerrado.");
        }
    }

    private static void borrar(char[] valor) {
        if (valor != null) {
            Arrays.fill(valor, '\0');
        }
    }

    public enum ResultadoCambio {
        ACTUALIZADA,
        HOTEL_DESCONOCIDO,
        LLAVE_AUSENTE,
        LONGITUD_INVALIDA,
        SIN_MAYUSCULA,
        SIN_NUMERO,
        CONFIRMACION_AUSENTE,
        CONFIRMACION_DISTINTA,
        VERSION_DESACTUALIZADA
    }

    public record ResultadoAcceso(
            AutenticadorLlave.Resultado resultado,
            long version,
            boolean requiereCambio) {
    }

    private static final class CredencialHotel implements AutoCloseable {
        private AutenticadorLlave autenticador;
        private boolean requiereCambio = true;
        private long version = 1;

        private final String hotel;
        private final RepositorioCredencialesHotel repositorio;
        private CredencialHotel(char[] llaveInicial,String hotel,RepositorioCredencialesHotel repositorio) {
            this.hotel=hotel;
            this.repositorio=repositorio;
            autenticador = new AutenticadorLlave(llaveInicial);
            if (repositorio!=null) {
                try (var material=autenticador.exportarMaterial()) { repositorio.inicializar(hotel,material); }
                finally { autenticador.close(); autenticador=null; }
            }
        }

        private synchronized ResultadoAcceso autenticarConEstado(char[] llaveRecibida) {
            if (repositorio!=null) {
                try (var credencial=repositorio.obtener(hotel); var validador=AutenticadorLlave.restaurar(credencial.material())) {
                    return new ResultadoAcceso(validador.autenticar(llaveRecibida),credencial.version(),credencial.requiereCambio());
                }
            }
            return new ResultadoAcceso(
                    autenticador.autenticar(llaveRecibida), version, requiereCambio);
        }

        private synchronized boolean requiereCambio() {
            if (repositorio!=null) { try(var credencial=repositorio.obtener(hotel)) { return credencial.requiereCambio(); } }
            return requiereCambio;
        }

        private synchronized boolean esVersionActual(long versionEsperada) {
            return versionActual() == versionEsperada;
        }

        private synchronized long versionActual() {
            if (repositorio!=null) { try(var credencial=repositorio.obtener(hotel)) { return credencial.version(); } }
            return version;
        }

        private synchronized ResultadoCambio cambiar(
                long versionEsperada, char[] nuevaLlave) {
            if (repositorio!=null) {
                try(var nuevo=new AutenticadorLlave(nuevaLlave);var material=nuevo.exportarMaterial()) {
                    return repositorio.cambiar(hotel,versionEsperada,material) ? ResultadoCambio.ACTUALIZADA : ResultadoCambio.VERSION_DESACTUALIZADA;
                }
            }
            if (versionEsperada > 0 && version != versionEsperada) {
                return ResultadoCambio.VERSION_DESACTUALIZADA;
            }
            AutenticadorLlave nuevoAutenticador = new AutenticadorLlave(nuevaLlave);
            AutenticadorLlave anterior = autenticador;
            autenticador = nuevoAutenticador;
            requiereCambio = false;
            version++;
            anterior.close();
            return ResultadoCambio.ACTUALIZADA;
        }

        @Override
        public synchronized void close() {
            if (autenticador!=null) autenticador.close();
        }
    }
}
