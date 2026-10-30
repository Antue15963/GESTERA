package com.gestera.repositorios.habitaciones;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.*;
import com.gestera.infraestructura.persistencia.TransaccionesHotel;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** Traduce el inventario a habitaciones/secciones y sus estancias; no guarda JSON. */
public final class RepositorioInventarioJdbc implements RepositorioInventario {
    private final JdbcTemplate jdbc;
    private final TransaccionesHotel transacciones;
    private final SimpleJdbcInsert insertarEstancia;

    public RepositorioInventarioJdbc(DataSource fuente) {
        jdbc = new JdbcTemplate(fuente);
        transacciones = new TransaccionesHotel(fuente);
        insertarEstancia = new SimpleJdbcInsert(fuente).withSchemaName("dbo").withTableName("estancias")
                .usingGeneratedKeyColumns("id").usingColumns("habitacion_id","tipo_servicio","check_in","check_out",
                        "tarifa_aplicada","horas_adicionales","monto_adicional","color_historico","estado");
    }

    @Override
    public <T> T ejecutar(String hotel, Supplier<T> operacion) {
        return transacciones.ejecutar(hotel,operacion);
    }

    @Override
    public ResumenInventario cargar(String hotel) {
        return ejecutar(hotel, () -> {
            List<SeccionTarifaria> secciones = jdbc.query(
                    "SELECT codigo,color_hex,nombre,precio_seis_horas,precio_dia_completo FROM dbo.secciones_tarifarias "
                    + "WHERE hotel_codigo=? ORDER BY id", (r,n) -> new SeccionTarifaria(r.getString(1),r.getString(2),
                            r.getString(3),r.getBigDecimal(4),r.getBigDecimal(5)),hotel);
            Map<String,SeccionTarifaria> porCodigo = new HashMap<>();
            secciones.forEach(s -> porCodigo.put(s.id(),s));
            List<Habitacion> habitaciones = jdbc.query(
                    "SELECT h.numero,s.codigo,h.estado,h.tipo_servicio_preferido,e.tipo_servicio,e.check_in,e.check_out,"
                    + "e.tarifa_aplicada,e.horas_adicionales,e.monto_adicional,p.id AS pago_id "
                    + "FROM dbo.habitaciones h JOIN dbo.secciones_tarifarias s ON s.id=h.seccion_tarifaria_id "
                    + "OUTER APPLY(SELECT TOP(1) x.* FROM dbo.estancias x WHERE x.habitacion_id=h.id "
                    + "ORDER BY CASE WHEN x.estado='ACTIVA' THEN 0 ELSE 1 END,x.check_in DESC,x.id DESC) e "
                    + "LEFT JOIN dbo.pagos_estancia p ON p.estancia_id=e.id WHERE h.hotel_codigo=? AND h.activa=1 ORDER BY h.numero",
                    (r,n) -> {
                        SeccionTarifaria seccion = porCodigo.get(r.getString("codigo"));
                        EstadoHabitacion estado = EstadoHabitacion.valueOf(r.getString("estado"));
                        boolean conserva = estado==EstadoHabitacion.OCUPADA || estado==EstadoHabitacion.LIMPIEZA_PENDIENTE
                                || estado==EstadoHabitacion.EN_LIMPIEZA;
                        String tipo = conserva ? r.getString("tipo_servicio") : null;
                        TipoServicio servicio = TipoServicio.valueOf(tipo==null ? r.getString("tipo_servicio_preferido") : tipo);
                        Timestamp entrada = conserva ? r.getTimestamp("check_in") : null;
                        Timestamp salida = conserva ? r.getTimestamp("check_out") : null;
                        BigDecimal tarifa = conserva ? r.getBigDecimal("tarifa_aplicada") : null;
                        return new Habitacion(r.getInt("numero"),seccion,servicio,
                                tarifa==null ? seccion.precio(servicio) : tarifa,estado,
                                entrada==null ? null : entrada.toLocalDateTime(),salida==null ? null : salida.toLocalDateTime(),
                                conserva && r.getObject("pago_id")!=null,conserva ? r.getInt("horas_adicionales") : 0,
                                conserva && r.getBigDecimal("monto_adicional")!=null ? r.getBigDecimal("monto_adicional") : BigDecimal.ZERO);
                    },hotel);
            return new ResumenInventario(habitaciones,secciones);
        });
    }

    @Override
    public void guardar(String hotel, ResumenInventario inventario) {
        ejecutar(hotel, () -> {
            Map<String,Long> ids = new LinkedHashMap<>();
            jdbc.query("SELECT id,codigo FROM dbo.secciones_tarifarias WHERE hotel_codigo=?",
                    (org.springframework.jdbc.core.RowCallbackHandler) r -> ids.put(r.getString("codigo"),r.getLong("id")),hotel);
            Set<String> colores = new HashSet<>();
            inventario.seccionesTarifarias().forEach(s -> colores.add(s.colorHex().toUpperCase(Locale.ROOT)));
            colores.addAll(jdbc.queryForList("SELECT color_hex FROM dbo.secciones_tarifarias WHERE hotel_codigo=?",String.class,hotel));
            int candidato = 0;
            // Emula validacion diferida de UNIQUE para permitir intercambiar nombres y colores.
            for (long id : ids.values()) {
                String temporal;
                do { temporal=String.format("#%06X",candidato++); } while (colores.contains(temporal));
                colores.add(temporal);
                jdbc.update("UPDATE dbo.secciones_tarifarias SET nombre=?,color_hex=? WHERE id=?",
                        "TMP"+UUID.randomUUID().toString().replace("-",""),temporal,id);
            }
            for (SeccionTarifaria seccion : inventario.seccionesTarifarias()) {
                if (ids.containsKey(seccion.id())) {
                    jdbc.update("UPDATE dbo.secciones_tarifarias SET nombre=?,color_hex=?,precio_seis_horas=?,"
                            + "precio_dia_completo=? WHERE id=?",seccion.nombre(),seccion.colorHex(),
                            seccion.precioSeisHoras(),seccion.precioDiaCompleto(),ids.get(seccion.id()));
                } else {
                    jdbc.update("INSERT dbo.secciones_tarifarias(hotel_codigo,codigo,nombre,color_hex,precio_seis_horas,precio_dia_completo) "
                            + "VALUES(?,?,?,?,?,?)",hotel,seccion.id(),seccion.nombre(),seccion.colorHex(),
                            seccion.precioSeisHoras(),seccion.precioDiaCompleto());
                    ids.put(seccion.id(),jdbc.queryForObject("SELECT id FROM dbo.secciones_tarifarias WHERE hotel_codigo=? AND codigo=?",
                            Long.class,hotel,seccion.id()));
                }
            }
            for (Habitacion habitacion : inventario.habitaciones()) guardarHabitacion(hotel,habitacion,ids.get(habitacion.seccion().id()));
            int cantidad = inventario.habitaciones().size();
            jdbc.update("UPDATE dbo.habitaciones SET activa=0,estado='DISPONIBLE' WHERE hotel_codigo=? AND numero>?",hotel,cantidad);
            Set<String> conservadas = new HashSet<>();
            inventario.seccionesTarifarias().forEach(s -> conservadas.add(s.id()));
            long reemplazo = ids.get(inventario.seccionesTarifarias().get(0).id());
            for (var seccion : ids.entrySet()) {
                if (!conservadas.contains(seccion.getKey())) {
                    jdbc.update("UPDATE dbo.habitaciones SET seccion_tarifaria_id=? WHERE hotel_codigo=? AND activa=0 AND seccion_tarifaria_id=?",
                            reemplazo,hotel,seccion.getValue());
                    jdbc.update("DELETE dbo.secciones_tarifarias WHERE id=?",seccion.getValue());
                }
            }
            return null;
        });
    }

    private void guardarHabitacion(String hotel, Habitacion habitacion, long seccion) {
        List<Map<String,Object>> previas = jdbc.queryForList("SELECT id,estado,seccion_tarifaria_id FROM dbo.habitaciones WHERE hotel_codigo=? AND numero=?",
                hotel,habitacion.numero());
        Long id;
        boolean ocupadaAntes = false;
        boolean cambiaSeccion = true;
        if (previas.isEmpty()) {
            jdbc.update("INSERT dbo.habitaciones(hotel_codigo,numero,seccion_tarifaria_id,estado,tipo_servicio_preferido) VALUES(?,?,?,?,?)",
                    hotel,habitacion.numero(),seccion,habitacion.estado().name(),habitacion.tipoServicio().name());
            id=jdbc.queryForObject("SELECT id FROM dbo.habitaciones WHERE hotel_codigo=? AND numero=?",Long.class,hotel,habitacion.numero());
        } else {
            id=((Number)previas.get(0).get("id")).longValue();
            ocupadaAntes="OCUPADA".equals(previas.get(0).get("estado"));
            cambiaSeccion=((Number)previas.get(0).get("seccion_tarifaria_id")).longValue()!=seccion;
            jdbc.update("UPDATE dbo.habitaciones SET seccion_tarifaria_id=?,estado=?,tipo_servicio_preferido=?,activa=1 WHERE id=?",
                    seccion,habitacion.estado().name(),habitacion.tipoServicio().name(),id);
        }
        if (habitacion.checkIn()==null) {
            jdbc.update("UPDATE dbo.estancias SET estado='FINALIZADA' WHERE habitacion_id=? AND estado='ACTIVA'",id);
            return;
        }
        LocalDateTime entrada=habitacion.checkIn(),salida=habitacion.proximoCheckOut();
        List<Map<String,Object>> existentes=jdbc.queryForList("SELECT id,estado FROM dbo.estancias WHERE habitacion_id=? AND check_in=?",
                id,Timestamp.valueOf(entrada));
        boolean nuevaOcupacion=habitacion.estado()==EstadoHabitacion.OCUPADA && !ocupadaAntes;
        if (nuevaOcupacion && !existentes.isEmpty()) {
            LocalDateTime original=entrada;
            do {
                entrada=entrada.plusSeconds(1);
                existentes=jdbc.queryForList("SELECT id,estado FROM dbo.estancias WHERE habitacion_id=? AND check_in=?",id,Timestamp.valueOf(entrada));
            } while (!existentes.isEmpty());
            salida=salida.plusSeconds(ChronoUnit.SECONDS.between(original,entrada));
        }
        String estado = habitacion.estado()==EstadoHabitacion.OCUPADA ? "ACTIVA" : "FINALIZADA";
        if ("ACTIVA".equals(estado)) {
            jdbc.update("UPDATE dbo.estancias SET estado='FINALIZADA' WHERE habitacion_id=? AND estado='ACTIVA' AND check_in<>?",
                    id,Timestamp.valueOf(entrada));
        }
        Long estancia;
        if (existentes.isEmpty()) {
            estancia=insertarEstancia.executeAndReturnKey(new MapSqlParameterSource()
                    .addValue("habitacion_id",id).addValue("tipo_servicio",habitacion.tipoServicio().name())
                    .addValue("check_in",Timestamp.valueOf(entrada)).addValue("check_out",Timestamp.valueOf(salida))
                    .addValue("tarifa_aplicada",habitacion.precioAplicado()).addValue("horas_adicionales",habitacion.horasAdicionales())
                    .addValue("monto_adicional",habitacion.montoAdicional()).addValue("color_historico",habitacion.seccion().colorHex())
                    .addValue("estado",estado)).longValue();
        } else {
            estancia=((Number)existentes.get(0).get("id")).longValue();
            jdbc.update("UPDATE dbo.estancias SET tipo_servicio=?,check_out=?,tarifa_aplicada=?,horas_adicionales=?,"
                    + "monto_adicional=?,color_historico=CASE WHEN ?=1 THEN ? ELSE color_historico END,estado=? WHERE id=?",habitacion.tipoServicio().name(),
                    Timestamp.valueOf(salida),habitacion.precioAplicado(),habitacion.horasAdicionales(),
                    habitacion.montoAdicional(),cambiaSeccion ? 1 : 0,habitacion.seccion().colorHex(),estado,estancia);
        }
        if (habitacion.pagado()) {
            if (jdbc.update("UPDATE dbo.pagos_estancia SET monto=? WHERE estancia_id=?",habitacion.montoTotal(),estancia)==0) {
                jdbc.update("INSERT dbo.pagos_estancia(estancia_id,pagado_en,monto) VALUES(?,?,?)",
                        estancia,Timestamp.valueOf(LocalDateTime.now(ZoneId.of("America/Lima"))),habitacion.montoTotal());
            }
        } else jdbc.update("DELETE dbo.pagos_estancia WHERE estancia_id=?",estancia);
    }
}
