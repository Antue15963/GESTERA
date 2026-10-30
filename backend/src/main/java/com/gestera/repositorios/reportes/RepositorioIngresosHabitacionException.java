package com.gestera.repositorios.reportes;

/** Error de infraestructura al consultar o guardar ingresos de habitaciones. */
public final class RepositorioIngresosHabitacionException extends RuntimeException {
    public RepositorioIngresosHabitacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
