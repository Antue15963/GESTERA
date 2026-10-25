package com.gestera.reportes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Intervalo y origen de ingresos seleccionados para un reporte. */
public record FiltroReporte(
        PeriodoReporte periodo,
        TipoIngresoReporte tipoIngreso,
        LocalDate desde,
        LocalDate hasta) {
    public static final long DIAS_PERSONALIZADOS_MAXIMOS = 3_653;

    public FiltroReporte {
        periodo = Objects.requireNonNull(periodo, "periodo");
        tipoIngreso = Objects.requireNonNull(tipoIngreso, "tipoIngreso");
        desde = Objects.requireNonNull(desde, "desde");
        hasta = Objects.requireNonNull(hasta, "hasta");
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(desde, hasta)
                > DIAS_PERSONALIZADOS_MAXIMOS) {
            throw new IllegalArgumentException("El intervalo no puede superar diez años.");
        }
    }

    public static FiltroReporte crear(
            String periodo,
            String tipoIngreso,
            LocalDate desdePersonalizado,
            LocalDate hastaPersonalizado,
            LocalDate hoy) {
        LocalDate fechaActual = Objects.requireNonNull(hoy, "hoy");
        PeriodoReporte periodoSeguro = PeriodoReporte.desdeCodigo(periodo)
                .orElseThrow(() -> new IllegalArgumentException("El periodo no es válido."));
        TipoIngresoReporte tipoSeguro = TipoIngresoReporte.desdeCodigo(tipoIngreso)
                .orElseThrow(() -> new IllegalArgumentException("El tipo de ingreso no es válido."));
        LocalDate desde;
        LocalDate hasta;
        switch (periodoSeguro) {
            case DIA -> {
                desde = fechaActual;
                hasta = fechaActual;
            }
            case SEMANA -> {
                desde = fechaActual.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                hasta = desde.plusDays(6);
            }
            case MES -> {
                desde = fechaActual.withDayOfMonth(1);
                hasta = fechaActual.with(TemporalAdjusters.lastDayOfMonth());
            }
            case ANIO -> {
                desde = fechaActual.withDayOfYear(1);
                hasta = fechaActual.with(TemporalAdjusters.lastDayOfYear());
            }
            case PERSONALIZADO -> {
                if (desdePersonalizado == null || hastaPersonalizado == null) {
                    throw new IllegalArgumentException(
                            "Selecciona la fecha inicial y final del reporte.");
                }
                desde = desdePersonalizado;
                hasta = hastaPersonalizado;
            }
            default -> throw new IllegalStateException("Periodo no soportado.");
        }
        return new FiltroReporte(periodoSeguro, tipoSeguro, desde, hasta);
    }

    public enum PeriodoReporte {
        DIA("dia"),
        SEMANA("semana"),
        MES("mes"),
        ANIO("anio"),
        PERSONALIZADO("personalizado");

        private final String codigo;

        PeriodoReporte(String codigo) {
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }

        public static Optional<PeriodoReporte> desdeCodigo(String codigo) {
            String valor = codigo == null || codigo.isBlank() ? "dia" : codigo;
            return Arrays.stream(values())
                    .filter(periodo -> periodo.codigo.equalsIgnoreCase(valor))
                    .findFirst();
        }
    }

    public enum TipoIngresoReporte {
        TODOS("todos"),
        HABITACIONES("habitaciones"),
        SERVICIOS("servicios");

        private final String codigo;

        TipoIngresoReporte(String codigo) {
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }

        public boolean incluyeHabitaciones() {
            return this == TODOS || this == HABITACIONES;
        }

        public boolean incluyeServicios() {
            return this == TODOS || this == SERVICIOS;
        }

        public static Optional<TipoIngresoReporte> desdeCodigo(String codigo) {
            String valor = codigo == null || codigo.isBlank() ? "todos" : codigo;
            return Arrays.stream(values())
                    .filter(tipo -> tipo.codigo.equalsIgnoreCase(valor))
                    .findFirst();
        }
    }
}
