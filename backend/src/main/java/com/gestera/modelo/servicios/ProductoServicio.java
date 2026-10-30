package com.gestera.modelo.servicios;

import java.math.BigDecimal;

/** Producto controlado del catálogo de servicio al cuarto. */
public record ProductoServicio(
        String codigo, String nombre, String categoria, BigDecimal precio) {
}
