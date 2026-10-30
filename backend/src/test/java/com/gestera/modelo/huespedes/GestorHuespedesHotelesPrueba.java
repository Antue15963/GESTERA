package com.gestera.modelo.huespedes;

import com.gestera.repositorios.huespedes.RepositorioHuespedesMemoria;

import com.gestera.modelo.huespedes.GestorHuespedesHoteles.ResultadoValidacion;
import com.gestera.pruebas.Aserciones;
import java.time.LocalDateTime;
import java.util.List;

/** Pruebas del dominio de huespedes independientes del motor de datos. */
public final class GestorHuespedesHotelesPrueba {
    private GestorHuespedesHotelesPrueba() {
    }

    public static int ejecutar() {
        validaDatosObligatorios();
        validaDocumentoSegunTipo();
        rechazaDocumentosDuplicados();
        registraUnaEstancia();
        actualizaLosHuespedesSinDuplicarLaEstancia();
        finalizaLaEstanciaYConservaElHistorial();
        combinaFiltrosDeBusqueda();
        return 7;
    }

    private static void validaDatosObligatorios() {
        try (GestorHuespedesHoteles gestor = nuevoGestor()) {
            Aserciones.iguales(ResultadoValidacion.HUESPEDES_AUSENTES,
                    gestor.validar(List.of()), "Una ocupacion debe identificar huespedes");
            Aserciones.iguales(ResultadoValidacion.NOMBRE_INVALIDO,
                    gestor.validar(List.of(new DatosHuesped(
                            "", TipoDocumento.DNI, "12345678"))),
                    "El nombre del huesped es obligatorio");
            Aserciones.iguales(ResultadoValidacion.DOCUMENTO_INVALIDO,
                    gestor.validar(List.of(new DatosHuesped(
                            "Ana Torres", TipoDocumento.DNI, "12"))),
                    "El documento debe respetar su formato seguro");
        }
    }

    private static void rechazaDocumentosDuplicados() {
        try (GestorHuespedesHoteles gestor = nuevoGestor()) {
            Aserciones.iguales(ResultadoValidacion.DOCUMENTO_DUPLICADO,
                    gestor.validar(List.of(
                            new DatosHuesped("Ana Torres", TipoDocumento.DNI, "12345678"),
                            new DatosHuesped("Otra Persona", TipoDocumento.DNI, "12345678"))),
                    "Una habitacion no debe repetir la misma identidad");
        }
    }

    private static void validaDocumentoSegunTipo() {
        try (GestorHuespedesHoteles gestor = nuevoGestor()) {
            for (DatosHuesped valido : List.of(
                    new DatosHuesped("Persona DNI", TipoDocumento.DNI, "12345678"),
                    new DatosHuesped(
                            "Persona Extranjera", TipoDocumento.CARNET_EXTRANJERIA, "ABC123456"),
                    new DatosHuesped("Persona Pasaporte", TipoDocumento.PASAPORTE, "PE123456"))) {
                Aserciones.iguales(ResultadoValidacion.VALIDA,
                        gestor.validar(List.of(valido)),
                        "Cada tipo debe admitir su formato documentario");
            }
            for (DatosHuesped invalido : List.of(
                    new DatosHuesped("DNI Corto", TipoDocumento.DNI, "1234567"),
                    new DatosHuesped("DNI Letras", TipoDocumento.DNI, "12AB5678"),
                    new DatosHuesped(
                            "Carnet Corto", TipoDocumento.CARNET_EXTRANJERIA, "AB123456"),
                    new DatosHuesped("Pasaporte Largo", TipoDocumento.PASAPORTE, "ABCDEFGHIJKLM"))) {
                Aserciones.iguales(ResultadoValidacion.DOCUMENTO_INVALIDO,
                        gestor.validar(List.of(invalido)),
                        "El tipo seleccionado debe imponer caracteres y longitud");
            }
        }
    }

    private static void registraUnaEstancia() {
        try (GestorHuespedesHoteles gestor = nuevoGestor()) {
            LocalDateTime entrada = LocalDateTime.of(2026, 9, 21, 10, 0);
            gestor.guardarEstancia("sol-pucusana", 2, entrada, entrada.plusHours(6),
                    huespedesIniciales());
            Aserciones.iguales(2, gestor.listar("sol-pucusana").size(),
                    "Cada visitante debe aparecer en el registro del hotel");
            Aserciones.iguales(2, gestor.activosPorHabitacion("sol-pucusana").get(2).size(),
                    "La estancia activa debe recuperar todos sus ocupantes");
            Aserciones.iguales(0, gestor.listar("brisa-marina").size(),
                    "Los registros no deben mezclarse entre hoteles");
        }
    }

    private static void actualizaLosHuespedesSinDuplicarLaEstancia() {
        try (GestorHuespedesHoteles gestor = nuevoGestor()) {
            LocalDateTime entrada = LocalDateTime.of(2026, 9, 21, 10, 0);
            gestor.guardarEstancia("sol-pucusana", 2, entrada, entrada.plusHours(6),
                    huespedesIniciales());
            gestor.guardarEstancia("sol-pucusana", 2, entrada, entrada.plusHours(8),
                    List.of(new DatosHuesped(
                            "Ana Torres", TipoDocumento.DNI, "12345678")));
            Aserciones.iguales(1, gestor.listar("sol-pucusana").size(),
                    "Editar la misma estancia debe reemplazar su lista, no duplicarla");
            Aserciones.iguales(entrada.plusHours(8),
                    gestor.listar("sol-pucusana").get(0).checkOut(),
                    "Una ampliacion debe actualizar el check-out del registro");
        }
    }

    private static void finalizaLaEstanciaYConservaElHistorial() {
        try (GestorHuespedesHoteles gestor = nuevoGestor()) {
            LocalDateTime entrada = LocalDateTime.of(2026, 9, 21, 10, 0);
            LocalDateTime salida = entrada.plusHours(6);
            gestor.guardarEstancia("sol-pucusana", 2, entrada, salida, huespedesIniciales());
            gestor.finalizarEstancia("sol-pucusana", 2, entrada, salida);
            Aserciones.iguales(0, gestor.activosPorHabitacion("sol-pucusana").size(),
                    "El check-out debe cerrar la ocupacion activa");
            Aserciones.falso(gestor.listar("sol-pucusana").get(0).estanciaActiva(),
                    "El visitante debe conservarse como historial finalizado");
        }
    }

    private static void combinaFiltrosDeBusqueda() {
        try (GestorHuespedesHoteles gestor = nuevoGestor()) {
            LocalDateTime primera = LocalDateTime.of(2026, 9, 21, 10, 0);
            LocalDateTime segunda = LocalDateTime.of(2026, 9, 22, 18, 0);
            gestor.guardarEstancia(
                    "sol-pucusana", 1, "#FBBF24", primera, primera.plusHours(6),
                    List.of(new DatosHuesped(
                            "Ana Torres Rojas", TipoDocumento.DNI, "12345678")));
            gestor.guardarEstancia(
                    "sol-pucusana", 2, "#38BDF8", segunda, segunda.plusHours(6),
                    List.of(new DatosHuesped(
                            "Luis Paredes", TipoDocumento.PASAPORTE, "PE123456")));

            FiltroHuespedes combinado = new FiltroHuespedes(
                    "torres",
                    primera.toLocalDate(),
                    primera.plusHours(2),
                    primera.plusHours(3),
                    "#FBBF24");
            Aserciones.iguales(1, gestor.buscar("sol-pucusana", combinado).size(),
                    "Nombre, dia, intervalo y color deben combinarse");
            Aserciones.iguales(1, gestor.buscar(
                    "sol-pucusana",
                    new FiltroHuespedes("", null, null, null, "#38BDF8")).size(),
                    "El color debe funcionar como filtro unitario");
            Aserciones.iguales(0, gestor.buscar(
                    "sol-pucusana",
                    new FiltroHuespedes("Ana", null, null, null, "#38BDF8")).size(),
                    "Los criterios combinados deben aplicarse con semantica AND");
        }
    }

    private static GestorHuespedesHoteles nuevoGestor() {
        return new GestorHuespedesHoteles(new RepositorioHuespedesMemoria());
    }

    private static List<DatosHuesped> huespedesIniciales() {
        return List.of(
                new DatosHuesped("Ana Torres", TipoDocumento.DNI, "12345678"),
                new DatosHuesped("Luis Paredes", TipoDocumento.PASAPORTE, "PE123456"));
    }
}
