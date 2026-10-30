package com.gestera.controladores;

import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

/** Puerto para comprobar la sesión y obtener el hotel autorizado en un controlador. */
@FunctionalInterface
public interface AutorizadorHotel {
    Hotel autorizar(HttpExchange intercambio) throws IOException;
}
