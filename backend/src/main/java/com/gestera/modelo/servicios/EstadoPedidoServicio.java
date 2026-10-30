package com.gestera.modelo.servicios;

import java.util.Arrays;
import java.util.Optional;

/** Estados y transiciones permitidas para un pedido de servicio al cuarto. */
public enum EstadoPedidoServicio {
    PENDIENTE("pendiente", "Pendiente"),
    EN_PREPARACION("en_preparacion", "En preparación"),
    ENTREGADO("entregado", "Entregado"),
    CANCELADO("cancelado", "Cancelado");

    private final String codigo;
    private final String etiqueta;

    EstadoPedidoServicio(String codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    public String codigo() {
        return codigo;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean permite(EstadoPedidoServicio destino) {
        return switch (this) {
            case PENDIENTE -> destino == EN_PREPARACION || destino == CANCELADO;
            case EN_PREPARACION -> destino == ENTREGADO || destino == CANCELADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }

    public static Optional<EstadoPedidoServicio> desdeCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(estado -> estado.codigo.equalsIgnoreCase(codigo))
                .findFirst();
    }
}
