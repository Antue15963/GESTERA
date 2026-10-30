package com.gestera.modelo.areas;

import com.gestera.repositorios.areas.RepositorioAreasComunes;

import com.gestera.modelo.huespedes.DatosHuesped;
import com.gestera.modelo.huespedes.GestorHuespedesHoteles;
import com.gestera.modelo.huespedes.GestorHuespedesHoteles.ResultadoValidacion;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Servicio independiente para registrar el uso de las áreas comunes del hotel. */
public final class GestorAreasComunes {
    private static final ZoneId ZONA_HORARIA_PERU = ZoneId.of("America/Lima");
    private static final List<AreaComun> AREAS = List.of(
            new AreaComun(
                    "jacuzzi",
                    "Jacuzzi",
                    "Registra el horario reservado para disfrutar del jacuzzi."),
            new AreaComun(
                    "sauna",
                    "Sauna",
                    "Organiza el ingreso y la salida de los visitantes del sauna."),
            new AreaComun(
                    "area-juegos",
                    "Área de juegos",
                    "Reserva un horario para compartir en el área de juegos."));

    private final GestorHuespedesHoteles huespedes;
    private final Clock reloj;
    private final RepositorioAreasComunes repositorio;

    public GestorAreasComunes(GestorHuespedesHoteles huespedes) {
        this(huespedes, Clock.system(ZONA_HORARIA_PERU));
    }

    public GestorAreasComunes(GestorHuespedesHoteles huespedes, Clock reloj) {
        this(huespedes,reloj,null);
    }
    public GestorAreasComunes(GestorHuespedesHoteles huespedes,Clock reloj,RepositorioAreasComunes repositorio) {
        this.repositorio=repositorio;
        this.huespedes = Objects.requireNonNull(huespedes, "huespedes");
        this.reloj = Objects.requireNonNull(reloj, "reloj").withZone(ZONA_HORARIA_PERU);
    }

    public List<AreaComun> areas() {
        return AREAS;
    }

    public List<AreaComun> areas(String hotel) {
        return repositorio==null ? AREAS : repositorio.listar(hotel);
    }
    public Optional<AreaComun> buscar(String hotel,String codigo) {
        return areas(hotel).stream().filter(area -> area.codigo().equals(codigo)).findFirst();
    }
    public Optional<AreaComun> buscar(String codigo) {
        return AREAS.stream()
                .filter(area -> area.codigo().equals(codigo))
                .findFirst();
    }

    public LocalDateTime horaActualPeru() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);
    }

    public ResultadoOperacion registrar(
            String codigoHotel,
            String codigoArea,
            LocalDateTime entrada,
            int duracionHoras,
            List<DatosHuesped> visitantes) {
        AreaComun area = buscar(codigoHotel,codigoArea).orElse(null);
        if (area == null) {
            return ResultadoOperacion.AREA_INVALIDA;
        }
        if (!duracionPermitida(duracionHoras)) {
            return ResultadoOperacion.DURACION_INVALIDA;
        }
        LocalDateTime salida = calcularSalida(entrada, duracionHoras);
        if (salida == null) {
            return ResultadoOperacion.HORARIO_INVALIDO;
        }
        ResultadoValidacion validacion = huespedes.validar(visitantes);
        if (validacion == ResultadoValidacion.DOCUMENTO_DUPLICADO) {
            return ResultadoOperacion.DOCUMENTO_DUPLICADO;
        }
        if (validacion != ResultadoValidacion.VALIDA) {
            return ResultadoOperacion.VISITANTE_INVALIDO;
        }
        huespedes.guardarVisita(
                codigoHotel, area.nombre(), entrada, salida, visitantes);
        return ResultadoOperacion.REGISTRADA;
    }

    public static boolean duracionPermitida(int horas) {
        return horas == 1 || horas == 3 || horas == 5;
    }

    public static LocalDateTime calcularSalida(LocalDateTime entrada, int duracionHoras) {
        if (entrada == null || !duracionPermitida(duracionHoras)) {
            return null;
        }
        try {
            return entrada.plusHours(duracionHoras);
        } catch (DateTimeException exception) {
            return null;
        }
    }

    public record AreaComun(String codigo, String nombre, String descripcion) {
        public AreaComun {
            Objects.requireNonNull(codigo, "codigo");
            Objects.requireNonNull(nombre, "nombre");
            Objects.requireNonNull(descripcion, "descripcion");
        }
    }

    public enum ResultadoOperacion {
        REGISTRADA,
        AREA_INVALIDA,
        DURACION_INVALIDA,
        HORARIO_INVALIDO,
        VISITANTE_INVALIDO,
        DOCUMENTO_DUPLICADO
    }
}
