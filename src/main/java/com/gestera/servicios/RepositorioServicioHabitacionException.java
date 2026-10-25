package com.gestera.servicios;

/** Error controlado del almacenamiento de pedidos de servicio. */
public final class RepositorioServicioHabitacionException extends RuntimeException {
    public RepositorioServicioHabitacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
