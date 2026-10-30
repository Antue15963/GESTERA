package com.gestera.repositorios.huespedes;

/** Error controlado de acceso al almacenamiento de huéspedes. */
public final class RepositorioHuespedesException extends RuntimeException {
    public RepositorioHuespedesException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
