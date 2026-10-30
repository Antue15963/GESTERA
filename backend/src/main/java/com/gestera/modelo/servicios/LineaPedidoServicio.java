package com.gestera.modelo.servicios;

import java.math.BigDecimal;

/** Instantánea de producto y precio incluida en un pedido. */
public record LineaPedidoServicio(
        String productoCodigo,
        String productoNombre,
        BigDecimal precioUnitario,
        int cantidad) {

    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2);
    }
}
