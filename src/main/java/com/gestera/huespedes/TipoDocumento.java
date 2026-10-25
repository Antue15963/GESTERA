package com.gestera.huespedes;

import java.util.Arrays;
import java.util.Optional;

/** Documentos de identidad admitidos para el registro hotelero. */
public enum TipoDocumento {
    DNI("dni", "DNI", "[0-9]{8}", 8, 8, "12345678", "numeric"),
    CARNET_EXTRANJERIA(
            "carnet_extranjeria",
            "Carnet de extranjería",
            "[A-Z0-9]{9,12}",
            9,
            12,
            "ABC123456",
            "text"),
    PASAPORTE("pasaporte", "Pasaporte", "[A-Z0-9]{6,12}", 6, 12, "AB123456", "text");

    private final String codigo;
    private final String etiqueta;
    private final String patron;
    private final int longitudMinima;
    private final int longitudMaxima;
    private final String ejemplo;
    private final String modoEntrada;

    TipoDocumento(
            String codigo,
            String etiqueta,
            String patron,
            int longitudMinima,
            int longitudMaxima,
            String ejemplo,
            String modoEntrada) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.patron = patron;
        this.longitudMinima = longitudMinima;
        this.longitudMaxima = longitudMaxima;
        this.ejemplo = ejemplo;
        this.modoEntrada = modoEntrada;
    }

    public String codigo() {
        return codigo;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String patron() {
        return patron;
    }

    public int longitudMinima() {
        return longitudMinima;
    }

    public int longitudMaxima() {
        return longitudMaxima;
    }

    public String ejemplo() {
        return ejemplo;
    }

    public String modoEntrada() {
        return modoEntrada;
    }

    public boolean admite(String documento) {
        return documento != null && documento.matches(patron);
    }

    public static Optional<TipoDocumento> desdeCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(tipo -> tipo.codigo.equalsIgnoreCase(codigo))
                .findFirst();
    }
}
