package com.gestera.hoteles;

import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.pruebas.Aserciones;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Comprueba que el directorio solo exponga los hoteles acordados. */
public final class CatalogoHotelesPrueba {
    private CatalogoHotelesPrueba() {
    }

    public static int ejecutar() {
        contieneLosCuatroHotelesEsperados();
        buscaPorCodigoExacto();
        entregaUnaColeccionInmutable();
        validaLosDatosObligatorios();
        return 4;
    }

    private static void contieneLosCuatroHotelesEsperados() {
        List<Hotel> hoteles = new CatalogoHoteles().todos();
        Aserciones.iguales(4, hoteles.size(),
                "El directorio debe contener exactamente cuatro hoteles");

        String[][] esperados = {
            {"sol-pucusana", "Hotel El Sol de Pucusana"},
            {"brisa-marina", "Hotel Brisa Marina"},
            {"valle-dorado", "Hotel Valle Dorado"},
            {"costa-serena", "Hotel Costa Serena"}
        };
        Set<String> codigos = new HashSet<>();
        Set<String> nombres = new HashSet<>();
        for (int indice = 0; indice < esperados.length; indice++) {
            Hotel hotel = hoteles.get(indice);
            Aserciones.iguales(
                    esperados[indice][0], hotel.codigo(), "El codigo del hotel debe ser estable");
            Aserciones.iguales(
                    esperados[indice][1], hotel.nombre(), "El nombre del hotel debe coincidir");
            Aserciones.verdadero(codigos.add(hotel.codigo()), "Los codigos no deben repetirse");
            Aserciones.verdadero(nombres.add(hotel.nombre()), "Los nombres no deben repetirse");
            Aserciones.falso(hotel.ubicacion().isBlank(), "Cada hotel debe indicar su ubicacion");
            Aserciones.falso(hotel.iniciales().isBlank(), "Cada hotel debe indicar sus iniciales");
        }
    }

    private static void buscaPorCodigoExacto() {
        CatalogoHoteles catalogo = new CatalogoHoteles();
        for (Hotel hotel : catalogo.todos()) {
            Aserciones.iguales(
                    hotel,
                    catalogo.buscarPorCodigo(hotel.codigo()).orElse(null),
                    "Cada codigo registrado debe recuperar su hotel");
        }
        for (String desconocido : new String[] {null, "", " ", "SOL-PUCUSANA", "no-existe"}) {
            Aserciones.verdadero(
                    catalogo.buscarPorCodigo(desconocido).isEmpty(),
                    "Un codigo desconocido no debe seleccionar un hotel");
        }
    }

    private static void entregaUnaColeccionInmutable() {
        List<Hotel> hoteles = new CatalogoHoteles().todos();
        Aserciones.lanza(
                UnsupportedOperationException.class,
                hoteles::clear,
                "El catalogo compartido no debe poder modificarse desde una vista");
    }

    private static void validaLosDatosObligatorios() {
        for (String[] datos : new String[][] {
            {"", "Hotel", "Lima", "H"},
            {"codigo", " ", "Lima", "H"},
            {"codigo", "Hotel", "", "H"},
            {"codigo", "Hotel", "Lima", " "}
        }) {
            Aserciones.lanza(
                    IllegalArgumentException.class,
                    () -> new Hotel(datos[0], datos[1], datos[2], datos[3]),
                    "Un hotel debe rechazar datos obligatorios vacios");
        }
    }
}
