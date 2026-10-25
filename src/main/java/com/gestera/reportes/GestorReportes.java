package com.gestera.reportes;

import com.gestera.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.reportes.FiltroReporte.PeriodoReporte;
import com.gestera.reportes.FiltroReporte.TipoIngresoReporte;
import com.gestera.servicios.EstadoPedidoServicio;
import com.gestera.servicios.PedidoServicio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Consolida pagos de habitaciones y pedidos entregados por hotel. */
public final class GestorReportes implements AutoCloseable {
    private static final ZoneId ZONA_HOTEL = ZoneId.of("America/Lima");
    private static final Locale IDIOMA = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FORMATO_DIA =
            DateTimeFormatter.ofPattern("dd MMM", IDIOMA);
    private static final DateTimeFormatter FORMATO_MES =
            DateTimeFormatter.ofPattern("MMMM yyyy", IDIOMA);
    private static final BigDecimal CERO = new BigDecimal("0.00");

    private final RepositorioIngresosHabitacion repositorio;
    private final Clock reloj;

    public GestorReportes(RepositorioIngresosHabitacion repositorio) {
        this(repositorio, Clock.system(ZONA_HOTEL));
    }

    public GestorReportes(RepositorioIngresosHabitacion repositorio, Clock reloj) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    public void guardarPagoHabitacion(
            String codigoHotel,
            int numeroHabitacion,
            LocalDateTime checkIn,
            BigDecimal monto) {
        if (codigoHotel == null || codigoHotel.isBlank()
                || numeroHabitacion < 1 || checkIn == null
                || monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El pago de habitación no es válido.");
        }
        repositorio.guardarPago(
                codigoHotel,
                numeroHabitacion,
                checkIn,
                LocalDateTime.now(reloj),
                monto.setScale(2));
    }

    public void eliminarPagoHabitacion(
            String codigoHotel, int numeroHabitacion, LocalDateTime checkIn) {
        if (checkIn != null) {
            repositorio.eliminarPago(codigoHotel, numeroHabitacion, checkIn);
        }
    }

    public ReporteIngresos generar(
            String codigoHotel,
            FiltroReporte filtro,
            List<PedidoServicio> pedidos) {
        return generar(codigoHotel, filtro, pedidos, List.of());
    }

    /**
     * Genera el reporte financiero y los indicadores operativos del dashboard.
     * Los ingresos respetan el periodo; la ocupacion y los pendientes representan
     * el estado actual del inventario recibido.
     */
    public ReporteIngresos generar(
            String codigoHotel,
            FiltroReporte filtro,
            List<PedidoServicio> pedidos,
            List<Habitacion> habitaciones) {
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(pedidos, "pedidos");
        Objects.requireNonNull(habitaciones, "habitaciones");
        LocalDateTime desde = filtro.desde().atStartOfDay();
        LocalDateTime hastaExclusive = filtro.hasta().plusDays(1).atStartOfDay();
        ArrayList<DetalleIngreso> detalles = new ArrayList<>();

        if (filtro.tipoIngreso().incluyeHabitaciones()) {
            for (IngresoHabitacion ingreso : repositorio.listar(
                    codigoHotel, desde, hastaExclusive)) {
                detalles.add(new DetalleIngreso(
                        TipoIngresoReporte.HABITACIONES,
                        ingreso.pagadoEn(),
                        ingreso.numeroHabitacion(),
                        "Estancia de habitación",
                        ingreso.monto(),
                        "H-" + ingreso.id()));
            }
        }
        if (filtro.tipoIngreso().incluyeServicios()) {
            pedidos.stream()
                    .filter(pedido -> pedido.estado() == EstadoPedidoServicio.ENTREGADO)
                    .filter(pedido -> !pedido.actualizadoEn().isBefore(desde))
                    .filter(pedido -> pedido.actualizadoEn().isBefore(hastaExclusive))
                    .map(pedido -> new DetalleIngreso(
                            TipoIngresoReporte.SERVICIOS,
                            pedido.actualizadoEn(),
                            pedido.numeroHabitacion(),
                            "Pedido #" + pedido.id(),
                            pedido.total(),
                            "S-" + pedido.id()))
                    .forEach(detalles::add);
        }
        detalles.sort(Comparator.comparing(DetalleIngreso::fecha).reversed());

        BigDecimal ingresoHabitaciones = totalPorTipo(
                detalles, TipoIngresoReporte.HABITACIONES);
        BigDecimal ingresoServicios = totalPorTipo(
                detalles, TipoIngresoReporte.SERVICIOS);
        BigDecimal total = ingresoHabitaciones.add(ingresoServicios).setScale(2);
        return new ReporteIngresos(
                filtro,
                ingresoHabitaciones,
                ingresoServicios,
                total,
                crearPeriodos(filtro, detalles),
                detalles,
                crearIndicadores(filtro, detalles, pedidos, habitaciones, total),
                crearRankingHabitaciones(detalles));
    }

    @Override
    public void close() {
        repositorio.close();
    }

    private static List<ResumenPeriodo> crearPeriodos(
            FiltroReporte filtro, List<DetalleIngreso> detalles) {
        boolean porMes = filtro.periodo() == PeriodoReporte.ANIO
                || ChronoUnit.DAYS.between(filtro.desde(), filtro.hasta()) > 62;
        Map<String, AcumuladoPeriodo> acumulados = new LinkedHashMap<>();
        if (porMes) {
            YearMonth actual = YearMonth.from(filtro.desde());
            YearMonth ultimo = YearMonth.from(filtro.hasta());
            while (!actual.isAfter(ultimo)) {
                String clave = actual.toString();
                acumulados.put(clave, new AcumuladoPeriodo(actual.format(FORMATO_MES)));
                actual = actual.plusMonths(1);
            }
        } else {
            LocalDate actual = filtro.desde();
            while (!actual.isAfter(filtro.hasta())) {
                acumulados.put(actual.toString(), new AcumuladoPeriodo(actual.format(FORMATO_DIA)));
                actual = actual.plusDays(1);
            }
        }
        for (DetalleIngreso detalle : detalles) {
            String clave = porMes
                    ? YearMonth.from(detalle.fecha()).toString()
                    : detalle.fecha().toLocalDate().toString();
            AcumuladoPeriodo acumulado = acumulados.get(clave);
            if (acumulado != null) {
                acumulado.agregar(detalle);
            }
        }
        return acumulados.values().stream().map(AcumuladoPeriodo::resultado).toList();
    }

    private static BigDecimal totalPorTipo(
            List<DetalleIngreso> detalles, TipoIngresoReporte tipo) {
        return detalles.stream()
                .filter(detalle -> detalle.tipo() == tipo)
                .map(DetalleIngreso::monto)
                .reduce(CERO, BigDecimal::add)
                .setScale(2);
    }

    private static IndicadoresDashboard crearIndicadores(
            FiltroReporte filtro,
            List<DetalleIngreso> detalles,
            List<PedidoServicio> pedidos,
            List<Habitacion> habitaciones,
            BigDecimal total) {
        int estanciasPagadas = (int) detalles.stream()
                .filter(detalle -> detalle.tipo() == TipoIngresoReporte.HABITACIONES)
                .count();
        int pedidosEntregados = (int) detalles.stream()
                .filter(detalle -> detalle.tipo() == TipoIngresoReporte.SERVICIOS)
                .count();
        BigDecimal ingresoHabitaciones = totalPorTipo(
                detalles, TipoIngresoReporte.HABITACIONES);
        BigDecimal ingresoServicios = totalPorTipo(
                detalles, TipoIngresoReporte.SERVICIOS);

        int totalHabitaciones = habitaciones.size();
        int ocupadas = contarEstado(habitaciones, EstadoHabitacion.OCUPADA);
        int disponibles = contarEstado(habitaciones, EstadoHabitacion.DISPONIBLE);
        int limpieza = contarEstado(habitaciones, EstadoHabitacion.LIMPIEZA_PENDIENTE)
                + contarEstado(habitaciones, EstadoHabitacion.EN_LIMPIEZA);
        int mantenimiento = contarEstado(habitaciones, EstadoHabitacion.MANTENIMIENTO);
        int operativas = Math.max(0, totalHabitaciones - mantenimiento);

        List<Habitacion> cuentasPendientes = habitaciones.stream()
                .filter(habitacion -> habitacion.checkIn() != null)
                .filter(habitacion -> !habitacion.pagado())
                .toList();
        BigDecimal montoPendiente = cuentasPendientes.stream()
                .map(Habitacion::montoTotal)
                .reduce(CERO, BigDecimal::add)
                .setScale(2);
        int pedidosActivos = (int) pedidos.stream()
                .filter(pedido -> pedido.estado() == EstadoPedidoServicio.PENDIENTE
                        || pedido.estado() == EstadoPedidoServicio.EN_PREPARACION)
                .count();
        long dias = ChronoUnit.DAYS.between(filtro.desde(), filtro.hasta()) + 1;
        long nochesDisponibles = Math.max(0L, (long) operativas * dias);

        return new IndicadoresDashboard(
                detalles.size(),
                estanciasPagadas,
                pedidosEntregados,
                promedio(ingresoHabitaciones, estanciasPagadas),
                promedio(ingresoServicios, pedidosEntregados),
                promedio(ingresoHabitaciones, nochesDisponibles),
                porcentaje(ingresoHabitaciones, total),
                porcentaje(ingresoServicios, total),
                totalHabitaciones,
                ocupadas,
                disponibles,
                limpieza,
                mantenimiento,
                porcentaje(BigDecimal.valueOf(ocupadas), BigDecimal.valueOf(operativas)),
                montoPendiente,
                cuentasPendientes.size(),
                pedidosActivos);
    }

    private static int contarEstado(
            List<Habitacion> habitaciones, EstadoHabitacion estado) {
        return (int) habitaciones.stream()
                .filter(habitacion -> habitacion.estado() == estado)
                .count();
    }

    private static BigDecimal promedio(BigDecimal total, long cantidad) {
        if (cantidad <= 0) {
            return CERO;
        }
        return total.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total.signum() <= 0) {
            return new BigDecimal("0.0");
        }
        return parte.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }

    private static List<ResumenHabitacion> crearRankingHabitaciones(
            List<DetalleIngreso> detalles) {
        Map<Integer, AcumuladoHabitacion> acumulados = new LinkedHashMap<>();
        for (DetalleIngreso detalle : detalles) {
            acumulados.computeIfAbsent(
                    detalle.numeroHabitacion(), numero -> new AcumuladoHabitacion())
                    .agregar(detalle.monto());
        }
        return acumulados.entrySet().stream()
                .map(entrada -> new ResumenHabitacion(
                        entrada.getKey(),
                        entrada.getValue().monto.setScale(2),
                        entrada.getValue().movimientos))
                .sorted(Comparator.comparing(ResumenHabitacion::monto).reversed()
                        .thenComparing(ResumenHabitacion::numeroHabitacion))
                .limit(5)
                .toList();
    }

    public record DetalleIngreso(
            TipoIngresoReporte tipo,
            LocalDateTime fecha,
            int numeroHabitacion,
            String concepto,
            BigDecimal monto,
            String referencia) {
        public DetalleIngreso {
            tipo = Objects.requireNonNull(tipo, "tipo");
            fecha = Objects.requireNonNull(fecha, "fecha");
            concepto = Objects.requireNonNull(concepto, "concepto");
            monto = Objects.requireNonNull(monto, "monto").setScale(2);
            referencia = Objects.requireNonNull(referencia, "referencia");
        }
    }

    public record ResumenPeriodo(
            String etiqueta,
            BigDecimal habitaciones,
            BigDecimal servicios,
            BigDecimal total) {
    }

    public record IndicadoresDashboard(
            int movimientos,
            int estanciasPagadas,
            int pedidosEntregados,
            BigDecimal tarifaPromedio,
            BigDecimal ticketPromedioServicio,
            BigDecimal ingresoPorHabitacionDisponible,
            BigDecimal participacionHabitaciones,
            BigDecimal participacionServicios,
            int habitacionesTotal,
            int habitacionesOcupadas,
            int habitacionesDisponibles,
            int habitacionesLimpieza,
            int habitacionesMantenimiento,
            BigDecimal tasaOcupacion,
            BigDecimal montoPendienteCobro,
            int pagosPendientes,
            int pedidosActivos) {
    }

    public record ResumenHabitacion(
            int numeroHabitacion,
            BigDecimal monto,
            int movimientos) {
    }

    public record ReporteIngresos(
            FiltroReporte filtro,
            BigDecimal totalHabitaciones,
            BigDecimal totalServicios,
            BigDecimal totalGeneral,
            List<ResumenPeriodo> periodos,
            List<DetalleIngreso> detalles,
            IndicadoresDashboard indicadores,
            List<ResumenHabitacion> rankingHabitaciones) {
        public ReporteIngresos {
            periodos = List.copyOf(periodos);
            detalles = List.copyOf(detalles);
            indicadores = Objects.requireNonNull(indicadores, "indicadores");
            rankingHabitaciones = List.copyOf(rankingHabitaciones);
        }
    }

    private static final class AcumuladoPeriodo {
        private final String etiqueta;
        private BigDecimal habitaciones = CERO;
        private BigDecimal servicios = CERO;

        private AcumuladoPeriodo(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        private void agregar(DetalleIngreso detalle) {
            if (detalle.tipo() == TipoIngresoReporte.HABITACIONES) {
                habitaciones = habitaciones.add(detalle.monto());
            } else {
                servicios = servicios.add(detalle.monto());
            }
        }

        private ResumenPeriodo resultado() {
            return new ResumenPeriodo(
                    etiqueta,
                    habitaciones.setScale(2),
                    servicios.setScale(2),
                    habitaciones.add(servicios).setScale(2));
        }
    }

    private static final class AcumuladoHabitacion {
        private BigDecimal monto = CERO;
        private int movimientos;

        private void agregar(BigDecimal valor) {
            monto = monto.add(valor);
            movimientos++;
        }
    }
}
