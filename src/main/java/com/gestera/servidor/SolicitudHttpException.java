package com.gestera.servidor;

/** Error de solicitud que puede convertirse de forma segura en una respuesta HTTP. */
public final class SolicitudHttpException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int estado;

    public SolicitudHttpException(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    int estado() {
        return estado;
    }
}
