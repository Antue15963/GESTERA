package com.gestera.repositorios.hoteles;

import com.gestera.seguridad.autenticacion.AutenticadorLlave.MaterialClave;

public interface RepositorioCredencialesHotel {
    void inicializar(String hotel, MaterialClave material);
    Credencial obtener(String hotel);
    boolean cambiar(String hotel, long versionEsperada, MaterialClave material);
    record Credencial(MaterialClave material,long version,boolean requiereCambio) implements AutoCloseable {
        @Override public void close() { material.close(); }
    }
}
