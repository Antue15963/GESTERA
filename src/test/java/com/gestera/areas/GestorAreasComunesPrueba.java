package com.gestera.areas;

import com.gestera.areas.GestorAreasComunes.ResultadoOperacion;
import com.gestera.huespedes.DatosHuesped;
import com.gestera.huespedes.GestorHuespedesHoteles;
import com.gestera.huespedes.RepositorioHuespedesMemoria;
import com.gestera.huespedes.TipoDocumento;
import com.gestera.pruebas.Aserciones;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Pruebas del servicio independiente de áreas comunes. */
public final class GestorAreasComunesPrueba {
    private GestorAreasComunesPrueba() {
    }

    public static int ejecutar() {
        exponeLasTresAreasSolicitadas();
        registraVisitantesEnElHistorialComun();
        rechazaAreasYHorariosInvalidos();
        rechazaDocumentosDuplicados();
        usaLaHoraActualDePeru();
        return 5;
    }

    private static void exponeLasTresAreasSolicitadas() {
        try (GestorHuespedesHoteles huespedes = nuevoGestorHuespedes()) {
            GestorAreasComunes gestor = new GestorAreasComunes(huespedes);
            Aserciones.iguales(3, gestor.areas().size(),
                    "Deben existir exactamente tres áreas comunes");
            Aserciones.iguales(
                    List.of("Jacuzzi", "Sauna", "Área de juegos"),
                    gestor.areas().stream().map(GestorAreasComunes.AreaComun::nombre).toList(),
                    "El catálogo debe conservar los tres nombres solicitados");
        }
    }

    private static void registraVisitantesEnElHistorialComun() {
        try (GestorHuespedesHoteles huespedes = nuevoGestorHuespedes()) {
            GestorAreasComunes gestor = new GestorAreasComunes(huespedes);
            LocalDateTime entrada = LocalDateTime.of(2026, 9, 23, 18, 0);
            Aserciones.iguales(
                    ResultadoOperacion.REGISTRADA,
                    gestor.registrar(
                            "sol-pucusana", "sauna", entrada, 3,
                            visitantes()),
                    "Una visita válida debe registrarse");
            Aserciones.iguales(2, huespedes.listar("sol-pucusana").size(),
                    "Cada visitante debe aparecer en el historial consolidado");
            Aserciones.iguales("Sauna", huespedes.listar("sol-pucusana").get(0).ubicacion(),
                    "El historial debe mostrar el área en lugar de una habitación");
            Aserciones.iguales(entrada.plusHours(3),
                    huespedes.listar("sol-pucusana").get(0).checkOut(),
                    "La salida debe calcularse automáticamente con la duración elegida");
            Aserciones.iguales(0, huespedes.activosPorHabitacion("sol-pucusana").size(),
                    "Un área común no debe ocupar ninguna habitación");
        }
    }

    private static void rechazaAreasYHorariosInvalidos() {
        try (GestorHuespedesHoteles huespedes = nuevoGestorHuespedes()) {
            GestorAreasComunes gestor = new GestorAreasComunes(huespedes);
            LocalDateTime entrada = LocalDateTime.of(2026, 9, 23, 18, 0);
            Aserciones.iguales(ResultadoOperacion.AREA_INVALIDA,
                    gestor.registrar(
                            "sol-pucusana", "piscina", entrada, 1,
                            visitantes()),
                    "No debe aceptar áreas fuera del catálogo");
            Aserciones.iguales(ResultadoOperacion.DURACION_INVALIDA,
                    gestor.registrar(
                            "sol-pucusana", "jacuzzi", entrada, 2, visitantes()),
                    "Sólo deben aceptarse duraciones de una, tres o cinco horas");
            Aserciones.iguales(ResultadoOperacion.HORARIO_INVALIDO,
                    gestor.registrar(
                            "sol-pucusana", "jacuzzi", null, 1, visitantes()),
                    "La hora de entrada sigue siendo obligatoria");
            Aserciones.iguales(0, huespedes.listar("sol-pucusana").size(),
                    "Los rechazos no deben guardar visitantes");
        }
    }

    private static void rechazaDocumentosDuplicados() {
        try (GestorHuespedesHoteles huespedes = nuevoGestorHuespedes()) {
            GestorAreasComunes gestor = new GestorAreasComunes(huespedes);
            LocalDateTime entrada = LocalDateTime.of(2026, 9, 23, 18, 0);
            Aserciones.iguales(
                    ResultadoOperacion.DOCUMENTO_DUPLICADO,
                    gestor.registrar(
                            "sol-pucusana", "area-juegos", entrada, 1,
                            List.of(
                                    new DatosHuesped(
                                            "Ana Torres", TipoDocumento.DNI, "12345678"),
                                    new DatosHuesped(
                                            "Otra Persona", TipoDocumento.DNI, "12345678"))),
                    "Una visita no debe repetir el mismo documento");
        }
    }

    private static void usaLaHoraActualDePeru() {
        Clock relojUtc = Clock.fixed(
                Instant.parse("2026-09-23T15:27:45Z"), ZoneId.of("UTC"));
        try (GestorHuespedesHoteles huespedes = nuevoGestorHuespedes()) {
            GestorAreasComunes gestor = new GestorAreasComunes(huespedes, relojUtc);
            Aserciones.iguales(
                    LocalDateTime.of(2026, 9, 23, 10, 27),
                    gestor.horaActualPeru(),
                    "El check-in predeterminado debe usar America/Lima y precisión de minutos");
        }
    }

    private static GestorHuespedesHoteles nuevoGestorHuespedes() {
        return new GestorHuespedesHoteles(new RepositorioHuespedesMemoria());
    }

    private static List<DatosHuesped> visitantes() {
        return List.of(
                new DatosHuesped("Ana Torres", TipoDocumento.DNI, "12345678"),
                new DatosHuesped("Luis Paredes", TipoDocumento.PASAPORTE, "PE123456"));
    }
}
