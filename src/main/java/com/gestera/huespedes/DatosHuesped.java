package com.gestera.huespedes;

/** Datos identificativos recibidos al registrar una habitación ocupada. */
public record DatosHuesped(
        String nombreCompleto,
        TipoDocumento tipoDocumento,
        String numeroDocumento) {
}
