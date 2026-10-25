package com.gestera.habitaciones;

import com.gestera.hoteles.CatalogoHoteles.Hotel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

/** Reglas de inventario por hotel con persistencia opcional y cambios atomicos. */
public final class GestorHabitacionesHoteles implements AutoCloseable {
    public static final int CANTIDAD_INICIAL = 12;
    public static final int CANTIDAD_MINIMA = 1;
    public static final int CANTIDAD_MAXIMA = 50;
    public static final int SECCIONES_MAXIMAS = 12;
    public static final int LONGITUD_NOMBRE_MAXIMA = 40;
    public static final BigDecimal PRECIO_MINIMO = new BigDecimal("1.00");
    public static final BigDecimal PRECIO_MAXIMO = new BigDecimal("10000.00");
    public static final BigDecimal MONTO_ADICIONAL_MAXIMO = new BigDecimal("10000.00");
    private static final BigDecimal MONTO_CERO = new BigDecimal("0.00");

    private final Map<String, InventarioHotel> inventarios;
    private final Clock reloj;
    private final RepositorioInventario repositorio;
    private final AtomicBoolean cerrado = new AtomicBoolean();
    private final ReentrantReadWriteLock cicloVida = new ReentrantReadWriteLock();

    public GestorHabitacionesHoteles(List<Hotel> hoteles) {
        this(hoteles, Clock.system(ZoneId.of("America/Lima")));
    }

    public GestorHabitacionesHoteles(List<Hotel> hoteles, Clock reloj) {
        this(hoteles,reloj,null);
    }

    public GestorHabitacionesHoteles(List<Hotel> hoteles, Clock reloj, RepositorioInventario repositorio) {
        this.repositorio=repositorio;
        Objects.requireNonNull(hoteles, "hoteles");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        if (hoteles.isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos un hotel registrado.");
        }
        java.util.LinkedHashMap<String, InventarioHotel> temporales =
                new java.util.LinkedHashMap<>();
        for (Hotel hotel : hoteles) {
            Objects.requireNonNull(hotel, "hotel");
            if (temporales.putIfAbsent(hotel.codigo(), new InventarioHotel()) != null) {
                throw new IllegalArgumentException(
                        "El código del hotel está repetido: " + hotel.codigo());
            }
        }
        inventarios = Map.copyOf(temporales);
        if (repositorio!=null) {
            for (Hotel hotel : hoteles) repositorio.ejecutar(hotel.codigo(), () -> {
                if (repositorio.cargar(hotel.codigo()).seccionesTarifarias().isEmpty())
                    repositorio.guardar(hotel.codigo(),new InventarioHotel().resumen());
                return null;
            });
        }
    }

    public List<Habitacion> habitaciones(String codigoHotel) {
        return consultar(codigoHotel, habitacion -> true);
    }

    public List<Habitacion> habitacionesParaLimpieza(String codigoHotel) {
        return consultar(codigoHotel, habitacion ->
                habitacion.estado == EstadoHabitacion.LIMPIEZA_PENDIENTE
                        || habitacion.estado == EstadoHabitacion.EN_LIMPIEZA);
    }

    public List<SeccionTarifaria> seccionesTarifarias(String codigoHotel) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            InventarioHotel inventario = inventario(codigoHotel);
            return inventario == null ? List.of() : inventario.seccionesTarifarias();
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    /** Devuelve habitaciones y tarifas pertenecientes al mismo instante lógico. */
    public ResumenInventario resumen(String codigoHotel) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            InventarioHotel inventario = inventario(codigoHotel);
            return inventario == null
                    ? new ResumenInventario(List.of(), List.of())
                    : inventario.resumen();
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public ResultadoOperacion configurarCantidad(String codigoHotel, int cantidad) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            if (cantidad < CANTIDAD_MINIMA || cantidad > CANTIDAD_MAXIMA) {
                return ResultadoOperacion.CANTIDAD_INVALIDA;
            }
            InventarioHotel inventario = inventario(codigoHotel);
            return inventario == null
                    ? ResultadoOperacion.HOTEL_DESCONOCIDO
                    : mutar(codigoHotel,i -> i.configurarCantidad(cantidad));
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    /** Aplica capacidad y secciones como una sola transacción lógica. */
    public ResultadoOperacion guardarConfiguracion(
            String codigoHotel,
            int cantidad,
            List<CambioSeccionTarifaria> cambios) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            if (cantidad < CANTIDAD_MINIMA || cantidad > CANTIDAD_MAXIMA) {
                return ResultadoOperacion.CANTIDAD_INVALIDA;
            }
            InventarioHotel inventario = inventario(codigoHotel);
            if (inventario == null) {
                return ResultadoOperacion.HOTEL_DESCONOCIDO;
            }
            if (cambios == null) {
                return ResultadoOperacion.SECCION_DESCONOCIDA;
            }

            ArrayList<CambioSeccionTarifaria> normalizados = new ArrayList<>(cambios.size());
            for (CambioSeccionTarifaria cambio : cambios) {
                if (cambio == null) {
                    return ResultadoOperacion.SECCION_DESCONOCIDA;
                }
                String id = cambio.idSeccion().trim();
                if (cambio.eliminar()) {
                    normalizados.add(new CambioSeccionTarifaria(
                            id, true, "", "", null, null));
                    continue;
                }
                String colorSeguro = normalizarColor(cambio.colorHex());
                if (colorSeguro == null) {
                    return ResultadoOperacion.COLOR_INVALIDO;
                }
                String nombreSeguro = normalizarNombre(cambio.nombre(), colorSeguro);
                if (nombreSeguro == null) {
                    return ResultadoOperacion.NOMBRE_INVALIDO;
                }
                if (!precioValido(cambio.precioSeisHoras())
                        || !precioValido(cambio.precioDiaCompleto())) {
                    return ResultadoOperacion.PRECIO_INVALIDO;
                }
                normalizados.add(new CambioSeccionTarifaria(
                        id,
                        false,
                        colorSeguro,
                        nombreSeguro,
                        cambio.precioSeisHoras().setScale(2),
                        cambio.precioDiaCompleto().setScale(2)));
            }
            return mutar(codigoHotel,i -> i.guardarConfiguracion(cantidad, normalizados));
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public ResultadoOperacion guardarSeccion(
            String codigoHotel,
            String idSeccion,
            String colorHex,
            String nombre,
            BigDecimal precioSeisHoras,
            BigDecimal precioDiaCompleto) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            InventarioHotel inventario = inventario(codigoHotel);
            if (inventario == null) {
                return ResultadoOperacion.HOTEL_DESCONOCIDO;
            }
            String colorSeguro = normalizarColor(colorHex);
            if (colorSeguro == null) {
                return ResultadoOperacion.COLOR_INVALIDO;
            }
            String nombreSeguro = normalizarNombre(nombre, colorSeguro);
            if (nombreSeguro == null) {
                return ResultadoOperacion.NOMBRE_INVALIDO;
            }
            if (!precioValido(precioSeisHoras) || !precioValido(precioDiaCompleto)) {
                return ResultadoOperacion.PRECIO_INVALIDO;
            }
            return mutar(codigoHotel,i -> i.guardarSeccion(
                    idSeccion == null ? "" : idSeccion.trim(),
                    colorSeguro,
                    nombreSeguro,
                    precioSeisHoras.setScale(2),
                    precioDiaCompleto.setScale(2)));
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public ResultadoOperacion eliminarSeccion(String codigoHotel, String idSeccion) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            InventarioHotel inventario = inventario(codigoHotel);
            return inventario == null
                    ? ResultadoOperacion.HOTEL_DESCONOCIDO
                    : mutar(codigoHotel,i -> i.eliminarSeccion(
                            idSeccion == null ? "" : idSeccion.trim()));
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public ResultadoOperacion actualizarHabitacion(
            String codigoHotel,
            int numero,
            String idSeccion,
            TipoServicio tipoServicio,
            EstadoHabitacion estado,
            boolean pagado) {
        return actualizarHabitacion(
                codigoHotel,
                numero,
                idSeccion,
                tipoServicio,
                estado,
                pagado,
                null,
                0,
                MONTO_CERO);
    }

    public ResultadoOperacion actualizarHabitacion(
            String codigoHotel,
            int numero,
            String idSeccion,
            TipoServicio tipoServicio,
            EstadoHabitacion estado,
            boolean pagado,
            int horasAdicionales,
            BigDecimal montoAdicional) {
        return actualizarHabitacion(
                codigoHotel,
                numero,
                idSeccion,
                tipoServicio,
                estado,
                pagado,
                null,
                horasAdicionales,
                montoAdicional);
    }

    public ResultadoOperacion actualizarHabitacion(
            String codigoHotel,
            int numero,
            String idSeccion,
            TipoServicio tipoServicio,
            EstadoHabitacion estado,
            boolean pagado,
            LocalDateTime proximoCheckOutManual) {
        return actualizarHabitacion(
                codigoHotel,
                numero,
                idSeccion,
                tipoServicio,
                estado,
                pagado,
                proximoCheckOutManual,
                0,
                MONTO_CERO);
    }

    public ResultadoOperacion actualizarHabitacion(
            String codigoHotel,
            int numero,
            String idSeccion,
            TipoServicio tipoServicio,
            EstadoHabitacion estado,
            boolean pagado,
            LocalDateTime proximoCheckOutManual,
            int horasAdicionales,
            BigDecimal montoAdicional) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            InventarioHotel inventario = inventario(codigoHotel);
            if (inventario == null) {
                return ResultadoOperacion.HOTEL_DESCONOCIDO;
            }
            if (tipoServicio == null) {
                return ResultadoOperacion.TIPO_SERVICIO_INVALIDO;
            }
            if (estado == null) {
                return ResultadoOperacion.ESTADO_INVALIDO;
            }
            if (!horasAdicionalesValidas(horasAdicionales)) {
                return ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS;
            }
            if (!montoAdicionalValido(montoAdicional)) {
                return ResultadoOperacion.MONTO_ADICIONAL_INVALIDO;
            }
            if (horasAdicionales == 0
                    && montoAdicional.compareTo(BigDecimal.ZERO) > 0) {
                return ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS;
            }
            if (horasAdicionales > 0
                    && montoAdicional.compareTo(BigDecimal.ZERO) == 0) {
                return ResultadoOperacion.MONTO_ADICIONAL_INVALIDO;
            }
            LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);
            return mutar(codigoHotel,i -> i.actualizar(
                    numero,
                    idSeccion == null ? "" : idSeccion.trim(),
                    tipoServicio,
                    estado,
                    pagado,
                    ahora,
                    proximoCheckOutManual,
                    horasAdicionales,
                    montoAdicional.setScale(2)));
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    public ResultadoOperacion confirmarCheckOut(String codigoHotel, int numero) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            InventarioHotel inventario = inventario(codigoHotel);
            if (inventario == null) {
                return ResultadoOperacion.HOTEL_DESCONOCIDO;
            }
            LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);
            return mutar(codigoHotel,i -> i.confirmarCheckOut(numero, ahora));
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    @Override
    public void close() {
        cicloVida.writeLock().lock();
        try {
            if (cerrado.compareAndSet(false, true)) {
                inventarios.values().forEach(InventarioHotel::close);
            }
        } finally {
            cicloVida.writeLock().unlock();
        }
    }

    private List<Habitacion> consultar(String codigoHotel, Predicate<Habitacion> filtro) {
        cicloVida.readLock().lock();
        try {
            asegurarAbierto();
            InventarioHotel inventario = inventario(codigoHotel);
            return inventario == null ? List.of() : inventario.consultar(filtro);
        } finally {
            cicloVida.readLock().unlock();
        }
    }

    private InventarioHotel inventario(String codigoHotel) {
        if (codigoHotel==null || codigoHotel.isBlank() || !inventarios.containsKey(codigoHotel)) return null;
        return repositorio==null ? inventarios.get(codigoHotel) : new InventarioHotel(repositorio.cargar(codigoHotel));
    }

    private ResultadoOperacion mutar(String hotel, java.util.function.Function<InventarioHotel,ResultadoOperacion> operacion) {
        if (repositorio==null) return operacion.apply(inventarios.get(hotel));
        return repositorio.ejecutar(hotel, () -> {
            InventarioHotel temporal=new InventarioHotel(repositorio.cargar(hotel));
            ResultadoOperacion resultado=operacion.apply(temporal);
            if (resultado==ResultadoOperacion.ACTUALIZADA) repositorio.guardar(hotel,temporal.resumen());
            return resultado;
        });
    }

    private void asegurarAbierto() {
        if (cerrado.get()) {
            throw new IllegalStateException("El gestor de habitaciones ya fue cerrado.");
        }
    }

    private static boolean precioValido(BigDecimal precio) {
        return precio != null
                && precio.scale() <= 2
                && precio.compareTo(PRECIO_MINIMO) >= 0
                && precio.compareTo(PRECIO_MAXIMO) <= 0;
    }

    private static boolean horasAdicionalesValidas(int horas) {
        return horas == 0 || horas == 1 || horas == 2 || horas == 4;
    }

    private static boolean montoAdicionalValido(BigDecimal monto) {
        return monto != null
                && monto.scale() <= 2
                && monto.compareTo(BigDecimal.ZERO) >= 0
                && monto.compareTo(MONTO_ADICIONAL_MAXIMO) <= 0;
    }

    private static String normalizarColor(String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        return color.toUpperCase(Locale.ROOT);
    }

    private static String normalizarNombre(String nombre, String colorSeguro) {
        String limpio = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            return "Color " + colorSeguro;
        }
        if (limpio.length() > LONGITUD_NOMBRE_MAXIMA
                || limpio.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }
        return limpio;
    }

    public enum ResultadoOperacion {
        ACTUALIZADA,
        HOTEL_DESCONOCIDO,
        CANTIDAD_INVALIDA,
        REDUCCION_NO_PERMITIDA,
        HABITACION_DESCONOCIDA,
        SECCION_DESCONOCIDA,
        LIMITE_SECCIONES,
        ULTIMA_SECCION,
        SECCION_EN_USO,
        COLOR_INVALIDO,
        COLOR_DUPLICADO,
        NOMBRE_INVALIDO,
        NOMBRE_DUPLICADO,
        PRECIO_INVALIDO,
        TIPO_SERVICIO_INVALIDO,
        ESTADO_INVALIDO,
        HORARIO_CHECKOUT_INVALIDO,
        HORAS_ADICIONALES_INVALIDAS,
        MONTO_ADICIONAL_INVALIDO,
        EXTENSION_NO_PERMITIDA,
        CHECKOUT_NO_DISPONIBLE,
        CHECKOUT_AUN_NO_VENCE
    }

    public enum TipoServicio {
        SEIS_HORAS("seis_horas", "6 horas", 6),
        DIA_COMPLETO("dia_completo", "Día completo", 24);

        private final String codigo;
        private final String etiqueta;
        private final int horas;

        TipoServicio(String codigo, String etiqueta, int horas) {
            this.codigo = codigo;
            this.etiqueta = etiqueta;
            this.horas = horas;
        }

        public String codigo() {
            return codigo;
        }

        public String etiqueta() {
            return etiqueta;
        }

        public int horas() {
            return horas;
        }

        public static Optional<TipoServicio> desdeCodigo(String codigo) {
            if (codigo == null) {
                return Optional.empty();
            }
            return java.util.Arrays.stream(values())
                    .filter(tipo -> tipo.codigo.equalsIgnoreCase(codigo))
                    .findFirst();
        }
    }

    public enum EstadoHabitacion {
        DISPONIBLE("disponible", "Disponible"),
        OCUPADA("ocupada", "Ocupada"),
        LIMPIEZA_PENDIENTE("limpieza_pendiente", "Limpieza pendiente"),
        EN_LIMPIEZA("en_limpieza", "En limpieza"),
        MANTENIMIENTO("mantenimiento", "Mantenimiento");

        private final String codigo;
        private final String etiqueta;

        EstadoHabitacion(String codigo, String etiqueta) {
            this.codigo = codigo;
            this.etiqueta = etiqueta;
        }

        public String codigo() {
            return codigo;
        }

        public String etiqueta() {
            return etiqueta;
        }

        public static Optional<EstadoHabitacion> desdeCodigo(String codigo) {
            if (codigo == null) {
                return Optional.empty();
            }
            return java.util.Arrays.stream(values())
                    .filter(estado -> estado.codigo.equalsIgnoreCase(codigo))
                    .findFirst();
        }
    }

    public record SeccionTarifaria(
            String id,
            String colorHex,
            String nombre,
            BigDecimal precioSeisHoras,
            BigDecimal precioDiaCompleto) {
        public SeccionTarifaria {
            id = Objects.requireNonNull(id, "id");
            colorHex = Objects.requireNonNull(colorHex, "colorHex");
            nombre = Objects.requireNonNull(nombre, "nombre");
            precioSeisHoras = Objects.requireNonNull(precioSeisHoras, "precioSeisHoras");
            precioDiaCompleto = Objects.requireNonNull(precioDiaCompleto, "precioDiaCompleto");
        }

        public BigDecimal precio(TipoServicio tipoServicio) {
            return tipoServicio == TipoServicio.DIA_COMPLETO
                    ? precioDiaCompleto
                    : precioSeisHoras;
        }
    }

    /** Cambio solicitado desde el formulario general de configuración. */
    public record CambioSeccionTarifaria(
            String idSeccion,
            boolean eliminar,
            String colorHex,
            String nombre,
            BigDecimal precioSeisHoras,
            BigDecimal precioDiaCompleto) {
        public CambioSeccionTarifaria {
            idSeccion = idSeccion == null ? "" : idSeccion;
            colorHex = colorHex == null ? "" : colorHex;
            nombre = nombre == null ? "" : nombre;
        }
    }

    public record Habitacion(
            int numero,
            SeccionTarifaria seccion,
            TipoServicio tipoServicio,
            BigDecimal precioAplicado,
            EstadoHabitacion estado,
            LocalDateTime checkIn,
            LocalDateTime proximoCheckOut,
            boolean pagado,
            int horasAdicionales,
            BigDecimal montoAdicional) {
        public Habitacion {
            seccion = Objects.requireNonNull(seccion, "seccion");
            tipoServicio = Objects.requireNonNull(tipoServicio, "tipoServicio");
            precioAplicado = Objects.requireNonNull(precioAplicado, "precioAplicado").setScale(2);
            estado = Objects.requireNonNull(estado, "estado");
            montoAdicional = Objects.requireNonNull(montoAdicional, "montoAdicional").setScale(2);
        }

        /** Conserva la firma previa para consumidores que no registran extensiones. */
        public Habitacion(
                int numero,
                SeccionTarifaria seccion,
                TipoServicio tipoServicio,
                BigDecimal precioAplicado,
                EstadoHabitacion estado,
                LocalDateTime checkIn,
                LocalDateTime proximoCheckOut,
                boolean pagado) {
            this(
                    numero,
                    seccion,
                    tipoServicio,
                    precioAplicado,
                    estado,
                    checkIn,
                    proximoCheckOut,
                    pagado,
                    0,
                    BigDecimal.ZERO);
        }

        public BigDecimal montoTotal() {
            return precioAplicado.add(montoAdicional).setScale(2);
        }
    }

    public record ResumenInventario(
            List<Habitacion> habitaciones,
            List<SeccionTarifaria> seccionesTarifarias) {
        public ResumenInventario {
            habitaciones = List.copyOf(habitaciones);
            seccionesTarifarias = List.copyOf(seccionesTarifarias);
        }
    }

}
