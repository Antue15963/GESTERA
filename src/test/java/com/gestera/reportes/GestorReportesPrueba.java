package com.gestera.reportes;

import com.gestera.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.SeccionTarifaria;
import com.gestera.habitaciones.GestorHabitacionesHoteles.TipoServicio;
import com.gestera.pruebas.Aserciones;
import com.gestera.reportes.FiltroReporte.TipoIngresoReporte;
import com.gestera.servicios.EstadoPedidoServicio;
import com.gestera.servicios.LineaPedidoServicio;
import com.gestera.servicios.PedidoServicio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Pruebas de consolidacion y rangos del modulo de reportes. */
public final class GestorReportesPrueba {
    private GestorReportesPrueba() {
    }

    public static int ejecutar() {
        consolidaSoloIngresosRealizados();
        separaLosOrigenes();
        actualizaYReviertePagosDeUnaEstancia();
        validaRangosPersonalizados();
        calculaIndicadoresDelDashboard();
        return 5;
    }

    private static void consolidaSoloIngresosRealizados() {
        try (GestorReportes gestor = nuevoGestor()) {
            gestor.guardarPagoHabitacion(
                    "sol-pucusana", 3, LocalDateTime.of(2026, 9, 23, 8, 0),
                    new BigDecimal("120.00"));
            FiltroReporte filtro = FiltroReporte.crear(
                    "dia", "todos", null, null, LocalDate.of(2026, 9, 24));
            var reporte = gestor.generar("sol-pucusana", filtro, List.of(
                    pedido(1, EstadoPedidoServicio.ENTREGADO, "35.00"),
                    pedido(2, EstadoPedidoServicio.CANCELADO, "80.00")));
            Aserciones.iguales("155.00", reporte.totalGeneral().toPlainString(),
                    "El total debe sumar habitaciones pagadas y pedidos entregados");
            Aserciones.iguales(2, reporte.detalles().size(),
                    "Los pedidos cancelados no deben considerarse ganancias");
        }
    }

    private static void separaLosOrigenes() {
        try (GestorReportes gestor = nuevoGestor()) {
            gestor.guardarPagoHabitacion(
                    "sol-pucusana", 2, LocalDateTime.of(2026, 9, 23, 9, 0),
                    new BigDecimal("90.00"));
            FiltroReporte filtro = FiltroReporte.crear(
                    "dia", "servicios", null, null, LocalDate.of(2026, 9, 24));
            var reporte = gestor.generar(
                    "sol-pucusana", filtro,
                    List.of(pedido(3, EstadoPedidoServicio.ENTREGADO, "12.00")));
            Aserciones.iguales("0.00", reporte.totalHabitaciones().toPlainString(),
                    "El filtro de servicios debe excluir habitaciones");
            Aserciones.iguales(TipoIngresoReporte.SERVICIOS,
                    reporte.detalles().get(0).tipo(),
                    "El detalle debe identificar su origen");
        }
    }

    private static void actualizaYReviertePagosDeUnaEstancia() {
        RepositorioIngresosHabitacionMemoria repositorio =
                new RepositorioIngresosHabitacionMemoria();
        try (GestorReportes gestor = new GestorReportes(repositorio, reloj())) {
            LocalDateTime checkIn = LocalDateTime.of(2026, 9, 23, 7, 0);
            gestor.guardarPagoHabitacion(
                    "sol-pucusana", 5, checkIn, new BigDecimal("100.00"));
            gestor.guardarPagoHabitacion(
                    "sol-pucusana", 5, checkIn, new BigDecimal("125.00"));
            FiltroReporte filtro = FiltroReporte.crear(
                    "dia", "habitaciones", null, null, LocalDate.of(2026, 9, 24));
            Aserciones.iguales("125.00", gestor.generar(
                    "sol-pucusana", filtro, List.of()).totalGeneral().toPlainString(),
                    "Una estancia debe actualizarse sin duplicar su ingreso");
            gestor.eliminarPagoHabitacion("sol-pucusana", 5, checkIn);
            Aserciones.iguales("0.00", gestor.generar(
                    "sol-pucusana", filtro, List.of()).totalGeneral().toPlainString(),
                    "Reabrir el pago debe retirar la estancia del reporte");
        }
    }

    private static void validaRangosPersonalizados() {
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> FiltroReporte.crear(
                        "personalizado", "todos",
                        LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 20),
                        LocalDate.of(2026, 9, 24)),
                "El rango personalizado no debe aceptar fechas invertidas");
    }

    private static void calculaIndicadoresDelDashboard() {
        try (GestorReportes gestor = nuevoGestor()) {
            gestor.guardarPagoHabitacion(
                    "sol-pucusana", 3, LocalDateTime.of(2026, 9, 24, 8, 0),
                    new BigDecimal("120.00"));
            gestor.guardarPagoHabitacion(
                    "sol-pucusana", 2, LocalDateTime.of(2026, 9, 24, 9, 0),
                    new BigDecimal("80.00"));
            FiltroReporte filtro = FiltroReporte.crear(
                    "dia", "todos", null, null, LocalDate.of(2026, 9, 24));
            List<Habitacion> habitaciones = List.of(
                    habitacion(1, EstadoHabitacion.OCUPADA, false, "150.00"),
                    habitacion(2, EstadoHabitacion.OCUPADA, true, "100.00"),
                    habitacion(3, EstadoHabitacion.DISPONIBLE, false, "100.00"),
                    habitacion(4, EstadoHabitacion.MANTENIMIENTO, false, "100.00"));

            var reporte = gestor.generar(
                    "sol-pucusana",
                    filtro,
                    List.of(pedido(10, EstadoPedidoServicio.ENTREGADO, "30.00")),
                    habitaciones);
            var indicadores = reporte.indicadores();
            Aserciones.iguales("100.00", indicadores.tarifaPromedio().toPlainString(),
                    "ADR debe promediar las estancias pagadas del periodo");
            Aserciones.iguales("30.00", indicadores.ticketPromedioServicio().toPlainString(),
                    "El ticket debe promediar los pedidos entregados");
            Aserciones.iguales("66.67",
                    indicadores.ingresoPorHabitacionDisponible().toPlainString(),
                    "RevPAR debe usar habitaciones operativas y dias del periodo");
            Aserciones.iguales("66.7", indicadores.tasaOcupacion().toPlainString(),
                    "La ocupacion debe excluir habitaciones en mantenimiento");
            Aserciones.iguales("150.00", indicadores.montoPendienteCobro().toPlainString(),
                    "El saldo pendiente debe sumar estancias actuales no pagadas");
            Aserciones.iguales(3, reporte.rankingHabitaciones().get(0).numeroHabitacion(),
                    "El ranking debe consolidar todos los ingresos por habitacion");
        }
    }

    private static PedidoServicio pedido(
            long id, EstadoPedidoServicio estado, String total) {
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 24, 10, 30);
        return new PedidoServicio(
                id,
                "sol-pucusana",
                3,
                estado,
                "",
                fecha.minusMinutes(10),
                fecha,
                List.of(new LineaPedidoServicio(
                        "producto", "Producto", new BigDecimal(total), 1)));
    }

    private static Habitacion habitacion(
            int numero, EstadoHabitacion estado, boolean pagado, String monto) {
        SeccionTarifaria seccion = new SeccionTarifaria(
                "estandar", "#FFBD24", "Estándar",
                new BigDecimal("100.00"), new BigDecimal("180.00"));
        LocalDateTime checkIn = estado == EstadoHabitacion.OCUPADA
                ? LocalDateTime.of(2026, 9, 24, 8, 0)
                : null;
        LocalDateTime checkOut = checkIn == null ? null : checkIn.plusHours(6);
        return new Habitacion(
                numero,
                seccion,
                TipoServicio.SEIS_HORAS,
                new BigDecimal(monto),
                estado,
                checkIn,
                checkOut,
                pagado,
                0,
                BigDecimal.ZERO);
    }

    private static GestorReportes nuevoGestor() {
        return new GestorReportes(new RepositorioIngresosHabitacionMemoria(), reloj());
    }

    private static Clock reloj() {
        return Clock.fixed(
                Instant.parse("2026-09-24T15:30:00Z"), ZoneId.of("America/Lima"));
    }
}
