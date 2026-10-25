package com.gestera.habitaciones;

import com.gestera.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.CambioSeccionTarifaria;
import com.gestera.habitaciones.GestorHabitacionesHoteles.ResultadoOperacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.SeccionTarifaria;
import com.gestera.habitaciones.GestorHabitacionesHoteles.TipoServicio;
import com.gestera.hoteles.CatalogoHoteles;
import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.pruebas.Aserciones;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Pruebas del inventario, las secciones tarifarias y las estancias automáticas. */
public final class GestorHabitacionesHotelesPrueba {
    private static final String SOL = "sol-pucusana";
    private static final String BRISA = "brisa-marina";

    private GestorHabitacionesHotelesPrueba() {
    }

    public static int ejecutar() {
        creaInventariosConTarifasValidas();
        mantieneConfiguracionIndependientePorHotel();
        configuraCantidadDentroDeLosLimites();
        impideEliminarHabitacionesNoDisponibles();
        creaYEditaSeccionesTarifarias();
        validaSeccionesSinMutarLaConfiguracion();
        limitaYEliminaSeccionesDeFormaSegura();
        guardaLaConfiguracionGeneralDeFormaAtomica();
        calculaAutomaticamenteEstanciasDeSeisYVeinticuatroHoras();
        permiteAjustarElCheckOutSinPerderElCalculoAutomatico();
        rechazaAjustesManualesInvalidosSinMutarLaHabitacion();
        acumulaAmpliacionesDeTiempoYCargos();
        rechazaAmpliacionesInvalidasSinMutarLaHabitacion();
        conservaYReiniciaAmpliacionesSegunLaEstancia();
        confirmaCheckOutsVencidosYConservaElEstadoDelPago();
        conservaORecalculaLaEstanciaSegunElCambio();
        normalizaLosEstadosSinPerderLaTarifaElegida();
        rechazaReferenciasYValoresDesconocidos();
        validaConstruccionYCierre();
        return 19;
    }

    private static void creaInventariosConTarifasValidas() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            for (Hotel hotel : hoteles()) {
                List<SeccionTarifaria> secciones = gestor.seccionesTarifarias(hotel.codigo());
                Aserciones.verdadero(!secciones.isEmpty(),
                        "Cada hotel debe iniciar con al menos una seccion tarifaria");
                Aserciones.verdadero(secciones.size() <= 12,
                        "Nunca debe superar el maximo de secciones");

                List<Habitacion> habitaciones = gestor.habitaciones(hotel.codigo());
                Aserciones.iguales(
                        GestorHabitacionesHoteles.CANTIDAD_INICIAL,
                        habitaciones.size(),
                        "Cada hotel debe iniciar con doce habitaciones");
                for (int indice = 0; indice < habitaciones.size(); indice++) {
                    Habitacion habitacion = habitaciones.get(indice);
                    Aserciones.iguales(indice + 1, habitacion.numero(),
                            "Las habitaciones deben numerarse consecutivamente");
                    Aserciones.iguales(EstadoHabitacion.DISPONIBLE, habitacion.estado(),
                            "Una habitacion nueva debe estar disponible");
                    Aserciones.verdadero(
                            secciones.stream().anyMatch(seccion ->
                                    seccion.id().equals(habitacion.seccion().id())),
                            "La habitacion debe referenciar una seccion de su hotel");
                    Aserciones.iguales(TipoServicio.SEIS_HORAS, habitacion.tipoServicio(),
                            "Una habitacion nueva debe usar seis horas por defecto");
                    Aserciones.verdadero(precioValido(habitacion.precioAplicado()),
                            "La vista previa debe incluir una tarifa valida");
                    Aserciones.iguales(0, habitacion.horasAdicionales(),
                            "Una habitacion nueva no debe acumular tiempo adicional");
                    Aserciones.iguales(decimal("0.00"), habitacion.montoAdicional(),
                            "Una habitacion nueva no debe acumular cargos adicionales");
                    Aserciones.iguales(habitacion.precioAplicado(), habitacion.montoTotal(),
                            "Sin cargos, el total debe coincidir con la tarifa base");
                    comprobarEstanciaLimpia(habitacion,
                            "Una habitacion disponible no debe tener estancia");
                }
                Aserciones.lanza(
                        UnsupportedOperationException.class,
                        habitaciones::clear,
                        "Las consultas de habitaciones deben ser inmutables");
                Aserciones.lanza(
                        UnsupportedOperationException.class,
                        secciones::clear,
                        "Las consultas de secciones deben ser inmutables");
            }
        }
    }

    private static void mantieneConfiguracionIndependientePorHotel() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.configurarCantidad(SOL, 15),
                    "Debe ampliar solamente el inventario solicitado");
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.guardarSeccion(
                            SOL, "", "#123456", "Suite Sol",
                            decimal("72.50"), decimal("150.00")),
                    "Debe crear una seccion solo en el hotel seleccionado");
            SeccionTarifaria seccionSol = buscarPorColor(gestor, SOL, "#123456");
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 1, seccionSol.id(), TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.OCUPADA, true),
                    "Debe actualizar solamente la habitacion solicitada");

            Aserciones.iguales(15, gestor.habitaciones(SOL).size(),
                    "Sol debe conservar su cantidad configurada");
            Aserciones.iguales(12, gestor.habitaciones(BRISA).size(),
                    "Brisa no debe compartir la cantidad de Sol");
            Aserciones.verdadero(
                    gestor.seccionesTarifarias(BRISA).stream()
                            .noneMatch(seccion -> seccion.colorHex().equals("#123456")),
                    "Brisa no debe heredar secciones de Sol");
            Aserciones.iguales(EstadoHabitacion.DISPONIBLE,
                    gestor.habitaciones(BRISA).get(0).estado(),
                    "Brisa no debe heredar estados de otro hotel");
        }
    }

    private static void configuraCantidadDentroDeLosLimites() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            for (int invalida : new int[] {0, 51, -1}) {
                Aserciones.iguales(ResultadoOperacion.CANTIDAD_INVALIDA,
                        gestor.configurarCantidad(SOL, invalida),
                        "La cantidad debe permanecer entre uno y cincuenta");
            }
            Aserciones.iguales(12, gestor.habitaciones(SOL).size(),
                    "Los rechazos no deben alterar el inventario");

            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.configurarCantidad(
                            SOL, GestorHabitacionesHoteles.CANTIDAD_MAXIMA),
                    "Debe aceptar exactamente cincuenta habitaciones");
            List<Habitacion> ampliadas = gestor.habitaciones(SOL);
            Aserciones.iguales(50, ampliadas.size(),
                    "El inventario debe crecer hasta el maximo");
            for (int indice = 0; indice < ampliadas.size(); indice++) {
                Aserciones.iguales(indice + 1, ampliadas.get(indice).numero(),
                        "Las ampliaciones deben conservar numeracion consecutiva");
            }

            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.configurarCantidad(
                            SOL, GestorHabitacionesHoteles.CANTIDAD_MINIMA),
                    "Debe reducir habitaciones disponibles hasta el minimo");
            Aserciones.iguales(1, gestor.habitaciones(SOL).size(),
                    "La reduccion valida debe conservar la primera habitacion");
        }
    }

    private static void impideEliminarHabitacionesNoDisponibles() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            gestor.configurarCantidad(SOL, 15);
            Habitacion ultima = gestor.habitaciones(SOL).get(14);
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 15, ultima.seccion().id(), ultima.tipoServicio(),
                            EstadoHabitacion.MANTENIMIENTO, false),
                    "Debe preparar una habitacion no disponible al final");
            Aserciones.iguales(ResultadoOperacion.REDUCCION_NO_PERMITIDA,
                    gestor.configurarCantidad(SOL, 14),
                    "No debe eliminar una habitacion activa");
            Aserciones.iguales(15, gestor.habitaciones(SOL).size(),
                    "Una reduccion rechazada debe ser atomica");

            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 15, ultima.seccion().id(), ultima.tipoServicio(),
                            EstadoHabitacion.DISPONIBLE, false),
                    "Debe poder liberar la habitacion final");
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.configurarCantidad(SOL, 14),
                    "Tras liberarla debe poder reducir el inventario");
        }
    }

    private static void creaYEditaSeccionesTarifarias() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.guardarSeccion(
                            SOL, "", "#a1b2c3", "  Ejecutiva  ",
                            decimal("65"), decimal("120.5")),
                    "Un id vacio debe crear una seccion");
            SeccionTarifaria creada = buscarPorColor(gestor, SOL, "#A1B2C3");
            Aserciones.verdadero(creada.id().startsWith("seccion-"),
                    "El servidor debe generar un id estable y opaco");
            Aserciones.iguales("#A1B2C3", creada.colorHex(),
                    "El HEX debe almacenarse en forma canonica");
            Aserciones.iguales("Ejecutiva", creada.nombre(),
                    "El nombre opcional debe recortarse");
            Aserciones.iguales(decimal("65.00"), creada.precioSeisHoras(),
                    "El precio de seis horas debe normalizarse");
            Aserciones.iguales(decimal("120.50"), creada.precioDiaCompleto(),
                    "El precio diario debe normalizarse");

            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.guardarSeccion(
                            SOL, creada.id(), "#C3B2A1", "",
                            decimal("80.00"), decimal("160.00")),
                    "Un id existente debe editar la misma seccion");
            SeccionTarifaria editada = gestor.seccionesTarifarias(SOL).stream()
                    .filter(seccion -> seccion.id().equals(creada.id()))
                    .findFirst()
                    .orElseThrow();
            Aserciones.iguales("#C3B2A1", editada.colorHex(),
                    "La edicion debe conservar el id y cambiar el color");
            Aserciones.iguales("Color #C3B2A1", editada.nombre(),
                    "Un nombre vacio debe usar como etiqueta el color canonico");
            Aserciones.iguales(
                    (long) gestor.seccionesTarifarias(SOL).size(),
                    gestor.seccionesTarifarias(SOL).stream()
                            .map(SeccionTarifaria::id).distinct().count(),
                    "Los ids de las secciones deben ser unicos");
        }
    }

    private static void validaSeccionesSinMutarLaConfiguracion() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            crearSeccion(gestor, SOL, "#102030", "Premium Norte", "80", "160");
            crearSeccion(gestor, SOL, "#405060", "Premium Sur", "90", "180");
            List<SeccionTarifaria> anterior = gestor.seccionesTarifarias(SOL);

            Object[][] casos = {
                {"", "", "Sin color", decimal("80"), decimal("160"),
                        ResultadoOperacion.COLOR_INVALIDO},
                {"", "#FFF", "HEX corto", decimal("80"), decimal("160"),
                        ResultadoOperacion.COLOR_INVALIDO},
                {"", "#GGGGGG", "HEX invalido", decimal("80"), decimal("160"),
                        ResultadoOperacion.COLOR_INVALIDO},
                {"", "#102030", "Otro nombre", decimal("80"), decimal("160"),
                        ResultadoOperacion.COLOR_DUPLICADO},
                {"", "#708090", " premium norte ", decimal("80"), decimal("160"),
                        ResultadoOperacion.NOMBRE_DUPLICADO},
                {"", "#708090", "x".repeat(81), decimal("80"), decimal("160"),
                        ResultadoOperacion.NOMBRE_INVALIDO},
                {"", "#708090", "Precio bajo", decimal("0.99"), decimal("160"),
                        ResultadoOperacion.PRECIO_INVALIDO},
                {"", "#708090", "Precio alto", decimal("80"), decimal("10000.01"),
                        ResultadoOperacion.PRECIO_INVALIDO},
                {"", "#708090", "Decimales", decimal("80.001"), decimal("160"),
                        ResultadoOperacion.PRECIO_INVALIDO},
                {"seccion-inexistente", "#708090", "Desconocida", decimal("80"),
                        decimal("160"), ResultadoOperacion.SECCION_DESCONOCIDA}
            };
            for (Object[] caso : casos) {
                Aserciones.iguales(caso[5],
                        gestor.guardarSeccion(
                                SOL,
                                (String) caso[0],
                                (String) caso[1],
                                (String) caso[2],
                                (BigDecimal) caso[3],
                                (BigDecimal) caso[4]),
                        "Debe rechazar una seccion fuera del contrato");
                Aserciones.iguales(anterior, gestor.seccionesTarifarias(SOL),
                        "Un rechazo no debe modificar parcialmente las secciones");
            }
        }
    }

    private static void limitaYEliminaSeccionesDeFormaSegura() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            int correlativo = 0;
            while (gestor.seccionesTarifarias(SOL).size() < 12) {
                correlativo++;
                crearSeccion(
                        gestor,
                        SOL,
                        String.format("#%06X", 0x900000 + correlativo),
                        "Extra " + correlativo,
                        "75",
                        "145");
            }
            Aserciones.iguales(ResultadoOperacion.LIMITE_SECCIONES,
                    gestor.guardarSeccion(
                            SOL, "", "#FEDCBA", "Una de mas",
                            decimal("80"), decimal("160")),
                    "No debe crear una seccion numero trece");

            SeccionTarifaria noAsignada = gestor.seccionesTarifarias(SOL).stream()
                    .filter(seccion -> seccion.nombre().startsWith("Extra "))
                    .findFirst()
                    .orElseThrow();
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.eliminarSeccion(SOL, noAsignada.id()),
                    "Debe eliminar una seccion que ninguna habitacion usa");
            Aserciones.verdadero(
                    gestor.seccionesTarifarias(SOL).stream()
                            .noneMatch(seccion -> seccion.id().equals(noAsignada.id())),
                    "La seccion eliminada no debe seguir listada");

            SeccionTarifaria asignada = gestor.habitaciones(SOL).get(0).seccion();
            Aserciones.iguales(ResultadoOperacion.SECCION_EN_USO,
                    gestor.eliminarSeccion(SOL, asignada.id()),
                    "No debe eliminar una seccion asignada a habitaciones");
            Aserciones.iguales(ResultadoOperacion.SECCION_DESCONOCIDA,
                    gestor.eliminarSeccion(SOL, "seccion-inexistente"),
                    "Debe rechazar una seccion desconocida");

            for (Habitacion habitacion : gestor.habitaciones(SOL)) {
                gestor.actualizarHabitacion(
                        SOL, habitacion.numero(), asignada.id(), TipoServicio.SEIS_HORAS,
                        EstadoHabitacion.DISPONIBLE, false);
            }
            for (SeccionTarifaria seccion : List.copyOf(gestor.seccionesTarifarias(SOL))) {
                if (!seccion.id().equals(asignada.id())) {
                    Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                            gestor.eliminarSeccion(SOL, seccion.id()),
                            "Debe retirar una seccion ya no utilizada");
                }
            }
            Aserciones.iguales(ResultadoOperacion.ULTIMA_SECCION,
                    gestor.eliminarSeccion(SOL, asignada.id()),
                    "El hotel siempre debe conservar al menos una seccion");
        }
    }

    private static void guardaLaConfiguracionGeneralDeFormaAtomica() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor(relojFijo())) {
            List<SeccionTarifaria> iniciales = gestor.seccionesTarifarias(SOL);
            SeccionTarifaria amarilla = iniciales.get(0);
            SeccionTarifaria celeste = iniciales.get(1);
            SeccionTarifaria violeta = iniciales.get(2);
            List<CambioSeccionTarifaria> cambios = List.of(
                    new CambioSeccionTarifaria(
                            amarilla.id(), false, celeste.colorHex(), celeste.nombre(),
                            decimal("61.00"), decimal("111.00")),
                    new CambioSeccionTarifaria(
                            celeste.id(), false, amarilla.colorHex(), amarilla.nombre(),
                            decimal("52.00"), decimal("92.00")),
                    new CambioSeccionTarifaria(
                            violeta.id(), true, "", "", null, null),
                    new CambioSeccionTarifaria(
                            "", false, "#123456", "Ejecutiva",
                            decimal("75.50"), decimal("140.00")));

            Aserciones.iguales(
                    ResultadoOperacion.ACTUALIZADA,
                    gestor.guardarConfiguracion(SOL, 8, cambios),
                    "Cantidad, intercambios, alta y baja deben confirmarse juntos");
            var guardada = gestor.resumen(SOL);
            Aserciones.iguales(8, guardada.habitaciones().size(),
                    "El guardado general debe aplicar la nueva capacidad");
            Aserciones.iguales(3, guardada.seccionesTarifarias().size(),
                    "Debe sustituir la seccion eliminada por la nueva");
            Aserciones.iguales(celeste.colorHex(), guardada.seccionesTarifarias().get(0).colorHex(),
                    "Debe permitir intercambiar colores en el estado final");
            Aserciones.iguales(amarilla.nombre(), guardada.seccionesTarifarias().get(1).nombre(),
                    "Debe permitir intercambiar nombres sin falsos duplicados");
            Aserciones.iguales("seccion-4", guardada.seccionesTarifarias().get(2).id(),
                    "La nueva seccion debe recibir el siguiente identificador");
            Aserciones.verdadero(guardada.habitaciones().stream().noneMatch(
                    habitacion -> habitacion.seccion().id().equals(violeta.id())),
                    "La seccion usada solo por el sufijo eliminado debe poder desaparecer");

            Aserciones.iguales(
                    ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL,
                            8,
                            celeste.id(),
                            TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.OCUPADA,
                            false),
                    "Debe preparar una reduccion bloqueada");
            var antesDelRechazo = gestor.resumen(SOL);
            List<CambioSeccionTarifaria> cambiosRechazados = List.of(
                    new CambioSeccionTarifaria(
                            amarilla.id(), false, "#111111", "Uno",
                            decimal("70.00"), decimal("130.00")),
                    new CambioSeccionTarifaria(
                            celeste.id(), false, "#222222", "Dos",
                            decimal("80.00"), decimal("140.00")),
                    new CambioSeccionTarifaria(
                            "seccion-4", false, "#333333", "Tres",
                            decimal("90.00"), decimal("150.00")));
            Aserciones.iguales(
                    ResultadoOperacion.REDUCCION_NO_PERMITIDA,
                    gestor.guardarConfiguracion(SOL, 7, cambiosRechazados),
                    "Una reduccion activa debe rechazar todo el lote");
            Aserciones.iguales(antesDelRechazo, gestor.resumen(SOL),
                    "Un rechazo no debe guardar colores, precios ni cantidad parcialmente");
        }
    }

    private static void calculaAutomaticamenteEstanciasDeSeisYVeinticuatroHoras() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            crearSeccion(gestor, SOL, "#123456", "Estancia", "75.50", "140.25");
            SeccionTarifaria seccion = buscarPorColor(gestor, SOL, "#123456");
            LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);

            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 1, seccion.id(), TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.OCUPADA, true),
                    "Debe activar el servicio de seis horas");
            Habitacion seisHoras = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(ahora, seisHoras.checkIn(),
                    "El servidor debe fijar el check-in al minuto actual");
            Aserciones.iguales(ahora.plusHours(6), seisHoras.proximoCheckOut(),
                    "Seis horas debe calcular automaticamente el check-out");
            Aserciones.iguales(decimal("75.50"), seisHoras.precioAplicado(),
                    "Debe aplicar el precio de seis horas de la seccion");
            Aserciones.verdadero(seisHoras.pagado(),
                    "Debe conservar el estado de pago solicitado");

            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 2, seccion.id(), TipoServicio.DIA_COMPLETO,
                            EstadoHabitacion.OCUPADA, false),
                    "Debe activar el servicio de dia completo");
            Habitacion dia = gestor.habitaciones(SOL).get(1);
            Aserciones.iguales(ahora.plusHours(24), dia.proximoCheckOut(),
                    "Dia completo debe sumar exactamente veinticuatro horas");
            Aserciones.iguales(decimal("140.25"), dia.precioAplicado(),
                    "Debe aplicar el precio diario de la seccion");
        }
    }

    private static void permiteAjustarElCheckOutSinPerderElCalculoAutomatico() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            Habitacion inicial = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.OCUPADA, false),
                    "La estancia debe comenzar con el horario automatico");
            Habitacion automatica = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(
                    automatica.checkIn().plusHours(6),
                    automatica.proximoCheckOut(),
                    "Sin ajuste manual debe conservarse el calculo de seis horas");

            LocalDateTime salidaManual = automatica.checkIn().plusMinutes(30);
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 1, automatica.seccion().id(), TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.OCUPADA, false, salidaManual),
                    "Debe aceptar un check-out manual explicito para pruebas");
            Habitacion ajustada = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(automatica.checkIn(), ajustada.checkIn(),
                    "El ajuste de salida no debe cambiar el check-in automatico");
            Aserciones.iguales(salidaManual, ajustada.proximoCheckOut(),
                    "Debe guardar exactamente la salida manual al minuto");
            Aserciones.iguales(automatica.precioAplicado(), ajustada.precioAplicado(),
                    "El ajuste horario no debe cambiar la tarifa aplicada");

            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 1, ajustada.seccion().id(), TipoServicio.DIA_COMPLETO,
                            EstadoHabitacion.OCUPADA, false),
                    "Cambiar el servicio sin ajuste debe usar nuevamente el calculo automatico");
            Habitacion diaCompleto = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(
                    automatica.checkIn().plusHours(24),
                    diaCompleto.proximoCheckOut(),
                    "Dia completo debe recalcular automaticamente veinticuatro horas");
        }
    }

    private static void rechazaAjustesManualesInvalidosSinMutarLaHabitacion() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            Habitacion inicial = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(ResultadoOperacion.HORARIO_CHECKOUT_INVALIDO,
                    gestor.actualizarHabitacion(
                            SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.DISPONIBLE, false,
                            LocalDateTime.now(reloj).plusHours(1)),
                    "Solo una habitacion ocupada debe admitir el ajuste manual");
            Aserciones.iguales(inicial, gestor.habitaciones(SOL).get(0),
                    "Un ajuste en una habitacion disponible no debe mutarla");

            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, true);
            Habitacion ocupada = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(ResultadoOperacion.HORARIO_CHECKOUT_INVALIDO,
                    gestor.actualizarHabitacion(
                            SOL, 1, ocupada.seccion().id(), TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.OCUPADA, false,
                            ocupada.checkIn().minusMinutes(1)),
                    "El check-out manual no puede ser anterior al check-in");
            Aserciones.iguales(ocupada, gestor.habitaciones(SOL).get(0),
                    "El horario invalido tampoco debe cambiar pago ni estancia");
        }
    }

    private static void acumulaAmpliacionesDeTiempoYCargos() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            crearSeccion(gestor, SOL, "#123456", "Extendida", "75.50", "140.25");
            SeccionTarifaria seccion = buscarPorColor(gestor, SOL, "#123456");
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 1, seccion.id(), TipoServicio.SEIS_HORAS,
                            EstadoHabitacion.OCUPADA, true),
                    "Debe iniciar la estancia antes de ampliarla");
            Habitacion inicial = gestor.habitaciones(SOL).get(0);
            LocalDateTime salidaAutomatica = inicial.proximoCheckOut();

            Object[][] ampliaciones = {
                {1, "10.00", 1, "10.00", "85.50"},
                {2, "7.25", 3, "17.25", "92.75"},
                {4, "20.00", 7, "37.25", "112.75"}
            };
            for (Object[] ampliacion : ampliaciones) {
                Habitacion anterior = gestor.habitaciones(SOL).get(0);
                Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                        gestor.actualizarHabitacion(
                                SOL,
                                1,
                                anterior.seccion().id(),
                                anterior.tipoServicio(),
                                EstadoHabitacion.OCUPADA,
                                anterior.pagado(),
                                (int) ampliacion[0],
                                decimal((String) ampliacion[1])),
                        "Cada opcion de una, dos o cuatro horas debe poder acumularse");
                Habitacion ampliada = gestor.habitaciones(SOL).get(0);
                int horasAcumuladas = (int) ampliacion[2];
                Aserciones.iguales(horasAcumuladas, ampliada.horasAdicionales(),
                        "Las horas adicionales deben acumularse sin reemplazar las anteriores");
                Aserciones.iguales(
                        salidaAutomatica.plusHours(horasAcumuladas),
                        ampliada.proximoCheckOut(),
                        "Cada ampliacion debe sumarse al check-out vigente");
                Aserciones.iguales(decimal((String) ampliacion[3]), ampliada.montoAdicional(),
                        "Los cargos adicionales deben acumularse con dos decimales");
                Aserciones.iguales(decimal((String) ampliacion[4]), ampliada.montoTotal(),
                        "El total debe sumar tarifa base y todos los cargos adicionales");
                Aserciones.iguales(inicial.checkIn(), ampliada.checkIn(),
                        "Ampliar la estancia no debe cambiar el check-in original");
                Aserciones.iguales(decimal("75.50"), ampliada.precioAplicado(),
                        "Ampliar la estancia no debe alterar la tarifa base aplicada");
                Aserciones.falso(ampliada.pagado(),
                        "Un nuevo cargo debe volver a dejar el pago como pendiente");
            }

            Habitacion antesDelCambio = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.actualizarHabitacion(
                            SOL, 1, seccion.id(), TipoServicio.DIA_COMPLETO,
                            EstadoHabitacion.OCUPADA, false),
                    "Cambiar el servicio debe conservar la ampliacion ya cobrada");
            Habitacion diaCompleto = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(7, diaCompleto.horasAdicionales(),
                    "El cambio de servicio debe conservar las horas adicionales");
            Aserciones.iguales(
                    diaCompleto.checkIn().plusHours(31),
                    diaCompleto.proximoCheckOut(),
                    "Dia completo debe recalcular 24 horas mas las siete adicionales");
            Aserciones.iguales(antesDelCambio.montoAdicional(), diaCompleto.montoAdicional(),
                    "El cambio de servicio debe conservar los cargos acumulados");
            Aserciones.iguales(decimal("177.50"), diaCompleto.montoTotal(),
                    "El total debe usar la nueva tarifa base sin perder los adicionales");
        }
    }

    private static void rechazaAmpliacionesInvalidasSinMutarLaHabitacion() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            Habitacion disponible = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(ResultadoOperacion.EXTENSION_NO_PERMITIDA,
                    gestor.actualizarHabitacion(
                            SOL, 1, disponible.seccion().id(), disponible.tipoServicio(),
                            EstadoHabitacion.DISPONIBLE, false, 1, decimal("5.00")),
                    "Una habitacion disponible no debe admitir ampliaciones");
            Aserciones.iguales(disponible, gestor.habitaciones(SOL).get(0),
                    "Rechazar una ampliacion disponible no debe mutar la habitacion");

            gestor.actualizarHabitacion(
                    SOL, 1, disponible.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, true);
            Habitacion ocupada = gestor.habitaciones(SOL).get(0);
            Object[][] casos = {
                {3, decimal("1.00"), ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS},
                {-1, decimal("1.00"), ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS},
                {0, decimal("1.00"), ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS},
                {1, null, ResultadoOperacion.MONTO_ADICIONAL_INVALIDO},
                {1, decimal("0.00"), ResultadoOperacion.MONTO_ADICIONAL_INVALIDO},
                {1, decimal("-0.01"), ResultadoOperacion.MONTO_ADICIONAL_INVALIDO},
                {1, decimal("0.001"), ResultadoOperacion.MONTO_ADICIONAL_INVALIDO},
                {1, decimal("10000.01"), ResultadoOperacion.MONTO_ADICIONAL_INVALIDO}
            };
            for (Object[] caso : casos) {
                Aserciones.iguales(caso[2],
                        gestor.actualizarHabitacion(
                                SOL,
                                1,
                                ocupada.seccion().id(),
                                ocupada.tipoServicio(),
                                EstadoHabitacion.OCUPADA,
                                false,
                                (int) caso[0],
                                (BigDecimal) caso[1]),
                        "Las horas y el monto deben formar una ampliacion valida y completa");
                Aserciones.iguales(ocupada, gestor.habitaciones(SOL).get(0),
                        "Una ampliacion rechazada no debe cambiar horario, pago ni montos");
            }

            gestor.actualizarHabitacion(
                    SOL, 1, ocupada.seccion().id(), ocupada.tipoServicio(),
                    EstadoHabitacion.LIMPIEZA_PENDIENTE, true);
            Habitacion limpieza = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(ResultadoOperacion.EXTENSION_NO_PERMITIDA,
                    gestor.actualizarHabitacion(
                            SOL, 1, limpieza.seccion().id(), limpieza.tipoServicio(),
                            EstadoHabitacion.LIMPIEZA_PENDIENTE, true,
                            2, decimal("10.00")),
                    "Una habitacion en limpieza no debe admitir mas tiempo");
            Aserciones.iguales(limpieza, gestor.habitaciones(SOL).get(0),
                    "El rechazo en limpieza debe conservar intacta la estancia");
        }
    }

    private static void conservaYReiniciaAmpliacionesSegunLaEstancia() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            Habitacion inicial = gestor.habitaciones(SOL).get(0);
            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, true);
            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, true, 4, decimal("30.00"));
            Habitacion ampliada = gestor.habitaciones(SOL).get(0);

            LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);
            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, ampliada.pagado(), ahora);
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.confirmarCheckOut(SOL, 1),
                    "Una estancia ampliada y vencida debe poder confirmar su check-out");
            Habitacion pendiente = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(4, pendiente.horasAdicionales(),
                    "El check-out debe conservar las horas agregadas para seguimiento");
            Aserciones.iguales(decimal("30.00"), pendiente.montoAdicional(),
                    "Limpieza pendiente debe conservar el cargo adicional");
            Aserciones.iguales(decimal("80.00"), pendiente.montoTotal(),
                    "Limpieza pendiente debe conservar el total completo");

            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.EN_LIMPIEZA, false);
            Habitacion enLimpieza = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(pendiente.montoAdicional(), enLimpieza.montoAdicional(),
                    "El proceso de limpieza debe conservar los cargos de la estancia");
            Aserciones.iguales(pendiente.horasAdicionales(), enLimpieza.horasAdicionales(),
                    "El proceso de limpieza debe conservar el tiempo agregado");

            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.DISPONIBLE, true);
            Habitacion disponible = gestor.habitaciones(SOL).get(0);
            comprobarEstanciaLimpia(disponible,
                    "Liberar la habitacion debe reiniciar toda la estancia ampliada");
            Aserciones.iguales(disponible.precioAplicado(), disponible.montoTotal(),
                    "Al liberar, el total debe volver a ser solo la tarifa base");

            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, false);
            Habitacion nuevaEstancia = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(0, nuevaEstancia.horasAdicionales(),
                    "Una estancia nueva no debe heredar horas de la anterior");
            Aserciones.iguales(decimal("0.00"), nuevaEstancia.montoAdicional(),
                    "Una estancia nueva no debe heredar cargos de la anterior");
            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, false, 1, decimal("5.00"));
            gestor.actualizarHabitacion(
                    SOL, 1, inicial.seccion().id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.MANTENIMIENTO, false);
            comprobarEstanciaLimpia(
                    gestor.habitaciones(SOL).get(0),
                    "Mantenimiento tambien debe descartar horas y cargos anteriores");
        }
    }

    private static void confirmaCheckOutsVencidosYConservaElEstadoDelPago() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);
            List<Habitacion> iniciales = gestor.habitaciones(SOL);
            for (int numero = 1; numero <= 3; numero++) {
                Habitacion inicial = iniciales.get(numero - 1);
                boolean pagado = numero == 1;
                LocalDateTime salida = numero < 3 ? ahora : ahora.plusMinutes(1);
                Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                        gestor.actualizarHabitacion(
                                SOL, numero, inicial.seccion().id(),
                                TipoServicio.SEIS_HORAS, EstadoHabitacion.OCUPADA,
                                pagado, salida),
                        "Debe preparar la estancia para confirmar su salida");
            }

            Habitacion pagadaAntes = gestor.habitaciones(SOL).get(0);
            Habitacion impagaAntes = gestor.habitaciones(SOL).get(1);
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.confirmarCheckOut(SOL, 1),
                    "La salida pagada debe poder confirmarse justo al llegar al limite");
            Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                    gestor.confirmarCheckOut(SOL, 2),
                    "La falta de pago debe advertirse, pero no bloquear el check-out");

            Habitacion pagadaDespues = gestor.habitaciones(SOL).get(0);
            Habitacion impagaDespues = gestor.habitaciones(SOL).get(1);
            for (Habitacion confirmada : List.of(pagadaDespues, impagaDespues)) {
                Aserciones.iguales(EstadoHabitacion.LIMPIEZA_PENDIENTE, confirmada.estado(),
                        "Confirmar la salida debe enviar la habitacion a limpieza pendiente");
            }
            Aserciones.iguales(pagadaAntes.checkIn(), pagadaDespues.checkIn(),
                    "El check-out debe conservar la estancia pagada");
            Aserciones.iguales(impagaAntes.proximoCheckOut(), impagaDespues.proximoCheckOut(),
                    "El check-out debe conservar el horario de la estancia impaga");
            Aserciones.verdadero(pagadaDespues.pagado(),
                    "Una salida pagada debe mantener el pago registrado");
            Aserciones.falso(impagaDespues.pagado(),
                    "Una salida impaga debe continuar marcada como pago pendiente");

            Habitacion futura = gestor.habitaciones(SOL).get(2);
            Aserciones.iguales(ResultadoOperacion.CHECKOUT_AUN_NO_VENCE,
                    gestor.confirmarCheckOut(SOL, 3),
                    "No debe confirmar una salida antes de su hora limite");
            Aserciones.iguales(futura, gestor.habitaciones(SOL).get(2),
                    "El intento anticipado no debe cambiar la habitacion");
            Aserciones.iguales(ResultadoOperacion.CHECKOUT_NO_DISPONIBLE,
                    gestor.confirmarCheckOut(SOL, 1),
                    "Una salida ya confirmada no debe procesarse dos veces");
            Aserciones.iguales(ResultadoOperacion.CHECKOUT_NO_DISPONIBLE,
                    gestor.confirmarCheckOut(SOL, 4),
                    "Una habitacion disponible no tiene un check-out por confirmar");
            Aserciones.iguales(ResultadoOperacion.HABITACION_DESCONOCIDA,
                    gestor.confirmarCheckOut(SOL, 999),
                    "Debe rechazar una habitacion inexistente");
            Aserciones.iguales(ResultadoOperacion.HOTEL_DESCONOCIDO,
                    gestor.confirmarCheckOut("no-existe", 1),
                    "Debe rechazar el check-out de un hotel desconocido");
        }
    }

    private static void conservaORecalculaLaEstanciaSegunElCambio() {
        RelojAjustable reloj = relojFijo();
        try (GestorHabitacionesHoteles gestor = nuevoGestor(reloj)) {
            crearSeccion(gestor, SOL, "#123456", "Inicial", "70", "130");
            crearSeccion(gestor, SOL, "#654321", "Alterna", "90", "180");
            SeccionTarifaria inicial = buscarPorColor(gestor, SOL, "#123456");
            SeccionTarifaria alterna = buscarPorColor(gestor, SOL, "#654321");

            gestor.actualizarHabitacion(
                    SOL, 1, inicial.id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, false);
            Habitacion primera = gestor.habitaciones(SOL).get(0);
            reloj.avanzar(Duration.ofHours(2));
            gestor.actualizarHabitacion(
                    SOL, 1, inicial.id(), TipoServicio.SEIS_HORAS,
                    EstadoHabitacion.OCUPADA, true);
            Habitacion mismoServicio = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(primera.checkIn(), mismoServicio.checkIn(),
                    "Editar pago/estado con la misma tarifa no debe reiniciar el reloj");
            Aserciones.iguales(primera.precioAplicado(), mismoServicio.precioAplicado(),
                    "La estancia activa debe conservar su precio snapshot");

            gestor.actualizarHabitacion(
                    SOL, 1, inicial.id(), TipoServicio.DIA_COMPLETO,
                    EstadoHabitacion.OCUPADA, true);
            Habitacion servicioCambiado = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(primera.checkIn(), servicioCambiado.checkIn(),
                    "Cambiar el servicio no debe falsear la hora de entrada original");
            Aserciones.iguales(primera.checkIn().plusHours(24),
                    servicioCambiado.proximoCheckOut(),
                    "El nuevo servicio debe recalcular la salida desde el check-in original");

            reloj.avanzar(Duration.ofHours(1));
            gestor.actualizarHabitacion(
                    SOL, 1, alterna.id(), TipoServicio.DIA_COMPLETO,
                    EstadoHabitacion.LIMPIEZA_PENDIENTE, true);
            Habitacion seccionCambiada = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(primera.checkIn(), seccionCambiada.checkIn(),
                    "Limpieza debe conservar el check-in de la estancia");
            Aserciones.iguales(primera.checkIn().plusHours(24),
                    seccionCambiada.proximoCheckOut(),
                    "Cambiar seccion en limpieza recalcula la salida desde la entrada");
            Aserciones.iguales(decimal("180.00"), seccionCambiada.precioAplicado(),
                    "El cambio debe tomar la tarifa de la nueva seccion");

            gestor.actualizarHabitacion(
                    SOL, 1, alterna.id(), TipoServicio.DIA_COMPLETO,
                    EstadoHabitacion.EN_LIMPIEZA, true);
            Habitacion limpieza = gestor.habitaciones(SOL).get(0);
            Aserciones.iguales(seccionCambiada.checkIn(), limpieza.checkIn(),
                    "Cambiar solo el estado de limpieza debe conservar la estancia");
        }
    }

    private static void normalizaLosEstadosSinPerderLaTarifaElegida() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor(relojFijo())) {
            crearSeccion(gestor, SOL, "#123456", "Flexible", "88", "166");
            SeccionTarifaria seccion = buscarPorColor(gestor, SOL, "#123456");
            gestor.actualizarHabitacion(
                    SOL, 1, seccion.id(), TipoServicio.DIA_COMPLETO,
                    EstadoHabitacion.OCUPADA, true);

            for (EstadoHabitacion estado : new EstadoHabitacion[] {
                EstadoHabitacion.DISPONIBLE, EstadoHabitacion.MANTENIMIENTO
            }) {
                Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                        gestor.actualizarHabitacion(
                                SOL, 1, seccion.id(), TipoServicio.DIA_COMPLETO,
                                estado, true),
                        "Debe aceptar el cambio de estado operativo");
                Habitacion habitacion = gestor.habitaciones(SOL).get(0);
                comprobarEstanciaLimpia(habitacion,
                        "Disponible y mantenimiento deben limpiar estancia y pago");
                Aserciones.iguales(seccion.id(), habitacion.seccion().id(),
                        "El estado debe conservar la seccion elegida");
                Aserciones.iguales(TipoServicio.DIA_COMPLETO, habitacion.tipoServicio(),
                        "El estado debe conservar el tipo para la vista previa");
                Aserciones.iguales(decimal("166.00"), habitacion.precioAplicado(),
                        "La vista previa debe conservar la tarifa diaria");
            }
        }
    }

    private static void rechazaReferenciasYValoresDesconocidos() {
        try (GestorHabitacionesHoteles gestor = nuevoGestor()) {
            Habitacion anterior = gestor.habitaciones(SOL).get(0);
            Object[][] casos = {
                {"no-existe", 1, anterior.seccion().id(), TipoServicio.SEIS_HORAS,
                        EstadoHabitacion.DISPONIBLE, ResultadoOperacion.HOTEL_DESCONOCIDO},
                {SOL, 999, anterior.seccion().id(), TipoServicio.SEIS_HORAS,
                        EstadoHabitacion.DISPONIBLE,
                        ResultadoOperacion.HABITACION_DESCONOCIDA},
                {SOL, 1, "seccion-inexistente", TipoServicio.SEIS_HORAS,
                        EstadoHabitacion.DISPONIBLE,
                        ResultadoOperacion.SECCION_DESCONOCIDA},
                {SOL, 1, anterior.seccion().id(), null,
                        EstadoHabitacion.DISPONIBLE,
                        ResultadoOperacion.TIPO_SERVICIO_INVALIDO},
                {SOL, 1, anterior.seccion().id(), TipoServicio.SEIS_HORAS,
                        null, ResultadoOperacion.ESTADO_INVALIDO}
            };
            for (Object[] caso : casos) {
                Aserciones.iguales(caso[5],
                        gestor.actualizarHabitacion(
                                (String) caso[0],
                                (int) caso[1],
                                (String) caso[2],
                                (TipoServicio) caso[3],
                                (EstadoHabitacion) caso[4],
                                false),
                        "Debe rechazar referencias fuera del inventario autorizado");
            }
            Aserciones.iguales(anterior, gestor.habitaciones(SOL).get(0),
                    "Los rechazos no deben alterar la habitacion");
            Aserciones.verdadero(gestor.habitaciones("no-existe").isEmpty(),
                    "Un hotel desconocido no debe exponer habitaciones");
            Aserciones.verdadero(gestor.seccionesTarifarias("no-existe").isEmpty(),
                    "Un hotel desconocido no debe exponer tarifas");
            Aserciones.iguales(ResultadoOperacion.HOTEL_DESCONOCIDO,
                    gestor.guardarSeccion(
                            "no-existe", "", "#123456", "Ajena",
                            decimal("80"), decimal("160")),
                    "No debe crear tarifas para hoteles desconocidos");
            Aserciones.iguales(ResultadoOperacion.HOTEL_DESCONOCIDO,
                    gestor.eliminarSeccion("no-existe", "seccion-1"),
                    "No debe eliminar tarifas de hoteles desconocidos");
        }
    }

    private static void validaConstruccionYCierre() {
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new GestorHabitacionesHoteles(List.of()),
                "El gestor debe exigir al menos un hotel");
        Hotel primero = hoteles().get(0);
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new GestorHabitacionesHoteles(List.of(primero, primero)),
                "El gestor debe rechazar codigos repetidos");

        GestorHabitacionesHoteles gestor = nuevoGestor();
        gestor.close();
        gestor.close();
        Aserciones.lanza(IllegalStateException.class,
                () -> gestor.habitaciones(SOL),
                "Un gestor cerrado no debe exponer habitaciones");
        Aserciones.lanza(IllegalStateException.class,
                () -> gestor.seccionesTarifarias(SOL),
                "Un gestor cerrado no debe exponer secciones");
        Aserciones.lanza(IllegalStateException.class,
                () -> gestor.configurarCantidad(SOL, 10),
                "Un gestor cerrado no debe cambiar cantidades");
        Aserciones.lanza(IllegalStateException.class,
                () -> gestor.guardarSeccion(
                        SOL, "", "#123456", "Cerrada", decimal("80"), decimal("160")),
                "Un gestor cerrado no debe crear secciones");
    }

    private static SeccionTarifaria crearSeccion(
            GestorHabitacionesHoteles gestor,
            String hotel,
            String color,
            String nombre,
            String precio6,
            String precio24) {
        Aserciones.iguales(ResultadoOperacion.ACTUALIZADA,
                gestor.guardarSeccion(
                        hotel, "", color, nombre, decimal(precio6), decimal(precio24)),
                "La preparacion de la seccion debe funcionar");
        return buscarPorColor(gestor, hotel, color.toUpperCase());
    }

    private static SeccionTarifaria buscarPorColor(
            GestorHabitacionesHoteles gestor, String hotel, String color) {
        return gestor.seccionesTarifarias(hotel).stream()
                .filter(seccion -> seccion.colorHex().equalsIgnoreCase(color))
                .findFirst()
                .orElseThrow();
    }

    private static void comprobarEstanciaLimpia(Habitacion habitacion, String mensaje) {
        Aserciones.iguales(null, habitacion.checkIn(), mensaje + " (check-in)");
        Aserciones.iguales(null, habitacion.proximoCheckOut(), mensaje + " (check-out)");
        Aserciones.falso(habitacion.pagado(), mensaje + " (pago)");
        Aserciones.iguales(0, habitacion.horasAdicionales(), mensaje + " (horas adicionales)");
        Aserciones.iguales(decimal("0.00"), habitacion.montoAdicional(),
                mensaje + " (monto adicional)");
    }

    private static boolean precioValido(BigDecimal precio) {
        return precio != null
                && precio.compareTo(GestorHabitacionesHoteles.PRECIO_MINIMO) >= 0
                && precio.compareTo(GestorHabitacionesHoteles.PRECIO_MAXIMO) <= 0
                && precio.scale() == 2;
    }

    private static BigDecimal decimal(String valor) {
        return new BigDecimal(valor);
    }

    private static GestorHabitacionesHoteles nuevoGestor() {
        return new GestorHabitacionesHoteles(hoteles());
    }

    private static GestorHabitacionesHoteles nuevoGestor(Clock reloj) {
        return new GestorHabitacionesHoteles(hoteles(), reloj);
    }

    private static List<Hotel> hoteles() {
        return new CatalogoHoteles().todos();
    }

    private static RelojAjustable relojFijo() {
        return new RelojAjustable(
                Instant.parse("2026-09-01T14:23:45Z"),
                ZoneId.of("America/Bogota"));
    }

    private static final class RelojAjustable extends Clock {
        private Instant instante;
        private final ZoneId zona;

        private RelojAjustable(Instant instante, ZoneId zona) {
            this.instante = instante;
            this.zona = zona;
        }

        private void avanzar(Duration duracion) {
            instante = instante.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId nuevaZona) {
            return new RelojAjustable(instante, nuevaZona);
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
