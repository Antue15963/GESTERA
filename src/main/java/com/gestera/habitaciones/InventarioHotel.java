package com.gestera.habitaciones;

import com.gestera.habitaciones.GestorHabitacionesHoteles.CambioSeccionTarifaria;
import com.gestera.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.ResumenInventario;
import com.gestera.habitaciones.GestorHabitacionesHoteles.ResultadoOperacion;
import com.gestera.habitaciones.GestorHabitacionesHoteles.SeccionTarifaria;
import com.gestera.habitaciones.GestorHabitacionesHoteles.TipoServicio;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Estado mutable y sincronizado del inventario perteneciente a un hotel. */
final class InventarioHotel implements AutoCloseable {
    private static final BigDecimal MONTO_CERO = new BigDecimal("0.00");

    private final ArrayList<SeccionTarifaria> secciones = new ArrayList<>();
    private final ArrayList<Habitacion> habitaciones = new ArrayList<>();
    private int siguienteSeccion = 1;

    InventarioHotel() {
        secciones.add(crearSeccionInicial("#FBBF24", "Amarillo", "50.00", "90.00"));
        secciones.add(crearSeccionInicial("#38BDF8", "Celeste", "65.00", "120.00"));
        secciones.add(crearSeccionInicial("#A78BFA", "Violeta", "80.00", "150.00"));
        for (int numero = 1;
                numero <= GestorHabitacionesHoteles.CANTIDAD_INICIAL;
                numero++) {
            habitaciones.add(crearPredeterminada(numero));
        }
    }

    InventarioHotel(ResumenInventario persistido) {
        secciones.addAll(persistido.seccionesTarifarias());
        habitaciones.addAll(persistido.habitaciones());
        for (SeccionTarifaria seccion : secciones) {
            if (seccion.id().startsWith("seccion-")) {
                try {
                    siguienteSeccion=Math.max(siguienteSeccion,Integer.parseInt(seccion.id().substring(8))+1);
                } catch (NumberFormatException ignorada) {
                    // Un codigo externo no utiliza la secuencia generada por el formulario.
                }
            }
        }
    }

    synchronized List<Habitacion> consultar(Predicate<Habitacion> filtro) {
        return habitaciones.stream().filter(filtro).toList();
    }

    synchronized List<SeccionTarifaria> seccionesTarifarias() {
        return List.copyOf(secciones);
    }

    synchronized ResumenInventario resumen() {
        return new ResumenInventario(habitaciones, secciones);
    }

    synchronized ResultadoOperacion configurarCantidad(int cantidad) {
        int cantidadActual = habitaciones.size();
        if (cantidad < cantidadActual) {
            boolean algunaActiva = habitaciones.subList(cantidad, cantidadActual).stream()
                    .anyMatch(habitacion ->
                            habitacion.estado() != EstadoHabitacion.DISPONIBLE);
            if (algunaActiva) {
                return ResultadoOperacion.REDUCCION_NO_PERMITIDA;
            }
            habitaciones.subList(cantidad, cantidadActual).clear();
        } else {
            for (int numero = cantidadActual + 1; numero <= cantidad; numero++) {
                habitaciones.add(crearPredeterminada(numero));
            }
        }
        return ResultadoOperacion.ACTUALIZADA;
    }

    synchronized ResultadoOperacion guardarConfiguracion(
            int cantidad, List<CambioSeccionTarifaria> cambios) {
        LinkedHashMap<String, CambioSeccionTarifaria> cambiosExistentes =
                new LinkedHashMap<>();
        CambioSeccionTarifaria cambioNuevo = null;
        for (CambioSeccionTarifaria cambio : cambios) {
            String id = cambio.idSeccion();
            if (id.isEmpty()) {
                if (cambio.eliminar() || cambioNuevo != null) {
                    return ResultadoOperacion.SECCION_DESCONOCIDA;
                }
                cambioNuevo = cambio;
            } else if (cambiosExistentes.putIfAbsent(id, cambio) != null) {
                return ResultadoOperacion.SECCION_DESCONOCIDA;
            }
        }
        if (cambiosExistentes.size() != secciones.size()
                || secciones.stream().anyMatch(seccion ->
                        !cambiosExistentes.containsKey(seccion.id()))) {
            return ResultadoOperacion.SECCION_DESCONOCIDA;
        }

        int cantidadFinalSecciones = (int) cambiosExistentes.values().stream()
                .filter(cambio -> !cambio.eliminar())
                .count() + (cambioNuevo == null ? 0 : 1);
        if (cantidadFinalSecciones == 0) {
            return ResultadoOperacion.ULTIMA_SECCION;
        }
        if (cantidadFinalSecciones > GestorHabitacionesHoteles.SECCIONES_MAXIMAS) {
            return ResultadoOperacion.LIMITE_SECCIONES;
        }

        Set<String> colores = new HashSet<>();
        Set<String> nombres = new HashSet<>();
        for (CambioSeccionTarifaria cambio : cambios) {
            if (cambio.eliminar()) {
                continue;
            }
            if (!colores.add(cambio.colorHex().toLowerCase(Locale.ROOT))) {
                return ResultadoOperacion.COLOR_DUPLICADO;
            }
            if (!nombres.add(cambio.nombre().toLowerCase(Locale.ROOT))) {
                return ResultadoOperacion.NOMBRE_DUPLICADO;
            }
        }

        int cantidadActual = habitaciones.size();
        if (cantidad < cantidadActual
                && habitaciones.subList(cantidad, cantidadActual).stream()
                        .anyMatch(habitacion ->
                                habitacion.estado() != EstadoHabitacion.DISPONIBLE)) {
            return ResultadoOperacion.REDUCCION_NO_PERMITIDA;
        }

        Set<String> idsConservados = new HashSet<>();
        cambiosExistentes.forEach((id, cambio) -> {
            if (!cambio.eliminar()) {
                idsConservados.add(id);
            }
        });
        int habitacionesRetenidas = Math.min(cantidad, cantidadActual);
        if (habitaciones.subList(0, habitacionesRetenidas).stream().anyMatch(
                habitacion -> !idsConservados.contains(habitacion.seccion().id()))) {
            return ResultadoOperacion.SECCION_EN_USO;
        }

        ArrayList<SeccionTarifaria> seccionesFinales =
                new ArrayList<>(cantidadFinalSecciones);
        for (SeccionTarifaria anterior : secciones) {
            CambioSeccionTarifaria cambio = cambiosExistentes.get(anterior.id());
            if (!cambio.eliminar()) {
                seccionesFinales.add(new SeccionTarifaria(
                        anterior.id(),
                        cambio.colorHex(),
                        cambio.nombre(),
                        cambio.precioSeisHoras(),
                        cambio.precioDiaCompleto()));
            }
        }
        if (cambioNuevo != null) {
            seccionesFinales.add(new SeccionTarifaria(
                    "seccion-" + siguienteSeccion,
                    cambioNuevo.colorHex(),
                    cambioNuevo.nombre(),
                    cambioNuevo.precioSeisHoras(),
                    cambioNuevo.precioDiaCompleto()));
        }

        Map<String, SeccionTarifaria> seccionesPorId = new LinkedHashMap<>();
        for (SeccionTarifaria seccion : seccionesFinales) {
            seccionesPorId.put(seccion.id(), seccion);
        }
        ArrayList<Habitacion> habitacionesFinales = new ArrayList<>(cantidad);
        for (int indice = 0; indice < habitacionesRetenidas; indice++) {
            Habitacion anterior = habitaciones.get(indice);
            SeccionTarifaria seccion = seccionesPorId.get(anterior.seccion().id());
            BigDecimal precio = conservaTarifaAplicada(anterior.estado())
                    ? anterior.precioAplicado()
                    : seccion.precio(anterior.tipoServicio()).setScale(2);
            habitacionesFinales.add(new Habitacion(
                    anterior.numero(),
                    seccion,
                    anterior.tipoServicio(),
                    precio,
                    anterior.estado(),
                    anterior.checkIn(),
                    anterior.proximoCheckOut(),
                    anterior.pagado(),
                    anterior.horasAdicionales(),
                    anterior.montoAdicional()));
        }
        for (int numero = cantidadActual + 1; numero <= cantidad; numero++) {
            habitacionesFinales.add(crearPredeterminada(numero, seccionesFinales));
        }

        secciones.clear();
        secciones.addAll(seccionesFinales);
        habitaciones.clear();
        habitaciones.addAll(habitacionesFinales);
        if (cambioNuevo != null) {
            siguienteSeccion += 1;
        }
        return ResultadoOperacion.ACTUALIZADA;
    }

    synchronized ResultadoOperacion guardarSeccion(
            String id,
            String colorHex,
            String nombre,
            BigDecimal precioSeisHoras,
            BigDecimal precioDiaCompleto) {
        int indice = indiceSeccion(id);
        boolean esNueva = id.isEmpty();
        if (!esNueva && indice < 0) {
            return ResultadoOperacion.SECCION_DESCONOCIDA;
        }
        if (esNueva && secciones.size() >= GestorHabitacionesHoteles.SECCIONES_MAXIMAS) {
            return ResultadoOperacion.LIMITE_SECCIONES;
        }
        String idExcluido = esNueva ? "" : id;
        if (secciones.stream().anyMatch(seccion ->
                !seccion.id().equals(idExcluido)
                        && seccion.colorHex().equalsIgnoreCase(colorHex))) {
            return ResultadoOperacion.COLOR_DUPLICADO;
        }
        if (secciones.stream().anyMatch(seccion ->
                !seccion.id().equals(idExcluido)
                        && seccion.nombre().equalsIgnoreCase(nombre))) {
            return ResultadoOperacion.NOMBRE_DUPLICADO;
        }

        String idSeguro = esNueva ? "seccion-" + siguienteSeccion++ : id;
        SeccionTarifaria actualizada = new SeccionTarifaria(
                idSeguro, colorHex, nombre, precioSeisHoras, precioDiaCompleto);
        if (esNueva) {
            secciones.add(actualizada);
        } else {
            secciones.set(indice, actualizada);
            actualizarReferenciaSeccion(actualizada);
        }
        return ResultadoOperacion.ACTUALIZADA;
    }

    synchronized ResultadoOperacion eliminarSeccion(String id) {
        int indice = indiceSeccion(id);
        if (indice < 0) {
            return ResultadoOperacion.SECCION_DESCONOCIDA;
        }
        if (secciones.size() == 1) {
            return ResultadoOperacion.ULTIMA_SECCION;
        }
        if (habitaciones.stream().anyMatch(habitacion ->
                habitacion.seccion().id().equals(id))) {
            return ResultadoOperacion.SECCION_EN_USO;
        }
        secciones.remove(indice);
        return ResultadoOperacion.ACTUALIZADA;
    }

    synchronized ResultadoOperacion actualizar(
            int numero,
            String idSeccion,
            TipoServicio tipoServicio,
            EstadoHabitacion estado,
            boolean pagado,
            LocalDateTime ahora,
            LocalDateTime proximoCheckOutManual,
            int horasAdicionales,
            BigDecimal montoAdicional) {
        if (numero < 1 || numero > habitaciones.size()) {
            return ResultadoOperacion.HABITACION_DESCONOCIDA;
        }
        SeccionTarifaria seccion = buscarSeccion(idSeccion);
        if (seccion == null) {
            return ResultadoOperacion.SECCION_DESCONOCIDA;
        }
        boolean agregaExtension = horasAdicionales != 0
                || montoAdicional.compareTo(BigDecimal.ZERO) != 0;
        if (agregaExtension && estado != EstadoHabitacion.OCUPADA) {
            return ResultadoOperacion.EXTENSION_NO_PERMITIDA;
        }

        Habitacion anterior = habitaciones.get(numero - 1);
        boolean cambiaTarifa = !anterior.seccion().id().equals(seccion.id())
                || anterior.tipoServicio() != tipoServicio;
        BigDecimal precio = cambiaTarifa
                ? seccion.precio(tipoServicio)
                : anterior.precioAplicado();
        LocalDateTime checkIn = anterior.checkIn();
        LocalDateTime checkOut = anterior.proximoCheckOut();
        boolean pagoSeguro = pagado;
        int horasAcumuladas = anterior.horasAdicionales();
        BigDecimal montoAcumulado = anterior.montoAdicional();

        if (estado == EstadoHabitacion.DISPONIBLE
                || estado == EstadoHabitacion.MANTENIMIENTO) {
            checkIn = null;
            checkOut = null;
            pagoSeguro = false;
            precio = seccion.precio(tipoServicio);
            horasAcumuladas = 0;
            montoAcumulado = MONTO_CERO;
        } else if (estado == EstadoHabitacion.OCUPADA) {
            if (anterior.estado() != EstadoHabitacion.OCUPADA || checkIn == null) {
                checkIn = ahora;
                checkOut = ahora.plusHours(tipoServicio.horas());
                precio = seccion.precio(tipoServicio);
                horasAcumuladas = 0;
                montoAcumulado = MONTO_CERO;
            } else if (cambiaTarifa || checkOut == null) {
                checkOut = checkIn.plusHours(
                        (long) tipoServicio.horas() + horasAcumuladas);
                precio = seccion.precio(tipoServicio);
            }
        } else if (cambiaTarifa || checkIn == null || checkOut == null) {
            checkIn = checkIn == null ? ahora : checkIn;
            checkOut = checkIn.plusHours(
                    (long) tipoServicio.horas() + horasAcumuladas);
            precio = seccion.precio(tipoServicio);
        }

        if (proximoCheckOutManual != null) {
            if (estado != EstadoHabitacion.OCUPADA
                    || checkIn == null
                    || proximoCheckOutManual.isBefore(checkIn)) {
                return ResultadoOperacion.HORARIO_CHECKOUT_INVALIDO;
            }
            checkOut = proximoCheckOutManual.truncatedTo(ChronoUnit.MINUTES);
        }

        if (agregaExtension) {
            try {
                checkOut = checkOut.plusHours(horasAdicionales);
                horasAcumuladas = Math.addExact(horasAcumuladas, horasAdicionales);
                if (horasAcumuladas>65535) return ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS;
            } catch (DateTimeException exception) {
                return ResultadoOperacion.HORARIO_CHECKOUT_INVALIDO;
            } catch (ArithmeticException exception) {
                return ResultadoOperacion.HORAS_ADICIONALES_INVALIDAS;
            }
            montoAcumulado = montoAcumulado.add(montoAdicional).setScale(2);
            if (montoAdicional.compareTo(BigDecimal.ZERO) > 0) {
                pagoSeguro = false;
            }
        }

        habitaciones.set(
                numero - 1,
                new Habitacion(
                        numero,
                        seccion,
                        tipoServicio,
                        precio.setScale(2),
                        estado,
                        checkIn,
                        checkOut,
                        pagoSeguro,
                        horasAcumuladas,
                        montoAcumulado));
        return ResultadoOperacion.ACTUALIZADA;
    }

    synchronized ResultadoOperacion confirmarCheckOut(int numero, LocalDateTime ahora) {
        if (numero < 1 || numero > habitaciones.size()) {
            return ResultadoOperacion.HABITACION_DESCONOCIDA;
        }
        Habitacion anterior = habitaciones.get(numero - 1);
        if (anterior.estado() != EstadoHabitacion.OCUPADA
                || anterior.proximoCheckOut() == null) {
            return ResultadoOperacion.CHECKOUT_NO_DISPONIBLE;
        }
        if (anterior.proximoCheckOut().isAfter(ahora)) {
            return ResultadoOperacion.CHECKOUT_AUN_NO_VENCE;
        }
        habitaciones.set(
                numero - 1,
                new Habitacion(
                        anterior.numero(),
                        anterior.seccion(),
                        anterior.tipoServicio(),
                        anterior.precioAplicado(),
                        EstadoHabitacion.LIMPIEZA_PENDIENTE,
                        anterior.checkIn(),
                        anterior.proximoCheckOut(),
                        anterior.pagado(),
                        anterior.horasAdicionales(),
                        anterior.montoAdicional()));
        return ResultadoOperacion.ACTUALIZADA;
    }

    private void actualizarReferenciaSeccion(SeccionTarifaria actualizada) {
        for (int indice = 0; indice < habitaciones.size(); indice++) {
            Habitacion habitacion = habitaciones.get(indice);
            if (!habitacion.seccion().id().equals(actualizada.id())) {
                continue;
            }
            BigDecimal precio = conservaTarifaAplicada(habitacion.estado())
                    ? habitacion.precioAplicado()
                    : actualizada.precio(habitacion.tipoServicio());
            habitaciones.set(
                    indice,
                    new Habitacion(
                            habitacion.numero(),
                            actualizada,
                            habitacion.tipoServicio(),
                            precio.setScale(2),
                            habitacion.estado(),
                            habitacion.checkIn(),
                            habitacion.proximoCheckOut(),
                            habitacion.pagado(),
                            habitacion.horasAdicionales(),
                            habitacion.montoAdicional()));
        }
    }

    private static boolean conservaTarifaAplicada(EstadoHabitacion estado) {
        return estado == EstadoHabitacion.OCUPADA
                || estado == EstadoHabitacion.LIMPIEZA_PENDIENTE
                || estado == EstadoHabitacion.EN_LIMPIEZA;
    }

    private SeccionTarifaria crearSeccionInicial(
            String color, String nombre, String precioSeis, String precioDia) {
        return new SeccionTarifaria(
                "seccion-" + siguienteSeccion++,
                color,
                nombre,
                new BigDecimal(precioSeis),
                new BigDecimal(precioDia));
    }

    private Habitacion crearPredeterminada(int numero) {
        return crearPredeterminada(numero, secciones);
    }

    private Habitacion crearPredeterminada(
            int numero, List<SeccionTarifaria> seccionesDisponibles) {
        int indiceSeccion = ((numero - 1) / 4) % seccionesDisponibles.size();
        SeccionTarifaria seccion = seccionesDisponibles.get(indiceSeccion);
        TipoServicio tipo = TipoServicio.SEIS_HORAS;
        return new Habitacion(
                numero,
                seccion,
                tipo,
                seccion.precio(tipo).setScale(2),
                EstadoHabitacion.DISPONIBLE,
                null,
                null,
                false,
                0,
                MONTO_CERO);
    }

    private SeccionTarifaria buscarSeccion(String id) {
        int indice = indiceSeccion(id);
        return indice < 0 ? null : secciones.get(indice);
    }

    private int indiceSeccion(String id) {
        if (id == null || id.isEmpty()) {
            return -1;
        }
        for (int indice = 0; indice < secciones.size(); indice++) {
            if (secciones.get(indice).id().equals(id)) {
                return indice;
            }
        }
        return -1;
    }

    @Override
    public synchronized void close() {
        habitaciones.clear();
        secciones.clear();
    }
}
