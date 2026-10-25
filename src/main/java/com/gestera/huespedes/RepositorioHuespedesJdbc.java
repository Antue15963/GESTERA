package com.gestera.huespedes;

import com.gestera.hoteles.CatalogoHoteles.Hotel;
import com.gestera.persistencia.TransaccionesHotel;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/** Personas y asociaciones sobre las ubicaciones normalizadas del modelo de 14 tablas. */
public final class RepositorioHuespedesJdbc implements RepositorioHuespedes {
    private final JdbcTemplate jdbc;
    private final TransaccionesHotel transacciones;
    private final SimpleJdbcInsert insertarVisita;
    private final SimpleJdbcInsert insertarHuesped;

    public RepositorioHuespedesJdbc(DataSource fuente,List<Hotel> hoteles) {
        jdbc=new JdbcTemplate(fuente);
        transacciones=new TransaccionesHotel(fuente);
        insertarVisita=new SimpleJdbcInsert(fuente).withSchemaName("dbo").withTableName("estancias")
                .usingGeneratedKeyColumns("id").usingColumns("area_comun_id","check_in","check_out","estado");
        insertarHuesped=new SimpleJdbcInsert(fuente).withSchemaName("dbo").withTableName("huespedes")
                .usingGeneratedKeyColumns("id").usingColumns("nombre_completo","tipo_documento","numero_documento");
        for (Hotel hotel:hoteles) transacciones.ejecutar(hotel.codigo(),()->{
            if (jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoteles WHERE codigo=?",Integer.class,hotel.codigo())==0)
                jdbc.update("INSERT dbo.hoteles(codigo,nombre,ubicacion,iniciales) VALUES(?,?,?,?)",
                        hotel.codigo(),hotel.nombre(),hotel.ubicacion(),hotel.iniciales());
            return null;
        });
    }

    @Override
    public void guardarEstancia(String hotel,Integer numero,String ubicacion,String color,LocalDateTime entrada,
            LocalDateTime salida,List<DatosHuesped> personas) {
        if (personas==null || personas.isEmpty() || personas.size()>8)
            throw new IllegalArgumentException("La estancia necesita entre uno y ocho huespedes.");
        try {
            transacciones.ejecutar(hotel,()->{
                Long estancia;
                if (numero!=null) {
                    estancia=jdbc.query("SELECT e.id FROM dbo.estancias e JOIN dbo.habitaciones h ON h.id=e.habitacion_id "
                            + "WHERE h.hotel_codigo=? AND h.numero=? AND e.check_in=? AND e.estado='ACTIVA' AND h.estado='OCUPADA'",
                            r->r.next()?r.getLong(1):null,hotel,numero,Timestamp.valueOf(entrada));
                    if (estancia==null) throw new DataIntegrityViolationException("La habitacion no tiene la estancia activa indicada.");
                    jdbc.update("UPDATE dbo.estancias SET check_out=? WHERE id=?",Timestamp.valueOf(salida),estancia);
                } else {
                    Long area=jdbc.query("SELECT id FROM dbo.areas_comunes WHERE hotel_codigo=? AND nombre=? AND activa=1",
                            r->r.next()?r.getLong(1):null,hotel,ubicacion);
                    if (area==null) throw new DataIntegrityViolationException("El area no pertenece al hotel o esta inactiva.");
                    estancia=jdbc.query("SELECT id FROM dbo.estancias WHERE area_comun_id=? AND check_in=?",
                            r->r.next()?r.getLong(1):null,area,Timestamp.valueOf(entrada));
                    String estado=salida.isAfter(LocalDateTime.now(ZoneId.of("America/Lima")))?"ACTIVA":"FINALIZADA";
                    if (estancia==null) estancia=insertarVisita.executeAndReturnKey(new MapSqlParameterSource()
                            .addValue("area_comun_id",area).addValue("check_in",Timestamp.valueOf(entrada))
                            .addValue("check_out",Timestamp.valueOf(salida)).addValue("estado",estado)).longValue();
                    else jdbc.update("UPDATE dbo.estancias SET check_out=?,estado=? WHERE id=?",Timestamp.valueOf(salida),estado,estancia);
                }
                jdbc.update("DELETE dbo.estancia_huespedes WHERE estancia_id=?",estancia);
                int orden=1;
                for (DatosHuesped persona:personas)
                    jdbc.update("INSERT dbo.estancia_huespedes(estancia_id,huesped_id,orden) VALUES(?,?,?)",
                            estancia,obtenerOCrear(persona),orden++);
                return null;
            });
        } catch (DataAccessException exception) { throw error(exception); }
    }

    private long obtenerOCrear(DatosHuesped persona) {
        Long id=jdbc.query("SELECT id FROM dbo.huespedes WITH(UPDLOCK,HOLDLOCK) WHERE tipo_documento=? AND numero_documento=?",
                r->r.next()?r.getLong(1):null,persona.tipoDocumento().name(),persona.numeroDocumento());
        if (id!=null) {
            jdbc.update("UPDATE dbo.huespedes SET nombre_completo=? WHERE id=?",persona.nombreCompleto(),id);
            return id;
        }
        return insertarHuesped.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("nombre_completo",persona.nombreCompleto())
                .addValue("tipo_documento",persona.tipoDocumento().name(),Types.VARCHAR)
                .addValue("numero_documento",persona.numeroDocumento(),Types.VARCHAR)).longValue();
    }

    @Override
    public void finalizarEstancia(String hotel,int numero,LocalDateTime entrada,LocalDateTime salida) {
        try {
            jdbc.update("UPDATE e SET check_out=?,estado='FINALIZADA' FROM dbo.estancias e "
                    + "JOIN dbo.habitaciones h ON h.id=e.habitacion_id WHERE h.hotel_codigo=? AND h.numero=? AND e.check_in=?",
                    Timestamp.valueOf(salida),hotel,numero,Timestamp.valueOf(entrada));
        } catch (DataAccessException exception) { throw error(exception); }
    }

    @Override
    public List<RegistroHuesped> buscarPorHotel(String hotel,FiltroHuespedes filtro) {
        try {
            jdbc.update("UPDATE e SET estado='FINALIZADA' FROM dbo.estancias e JOIN dbo.areas_comunes a ON a.id=e.area_comun_id "
                    + "WHERE a.hotel_codigo=? AND e.estado='ACTIVA' AND e.check_out<=?",
                    hotel,Timestamp.valueOf(LocalDateTime.now(ZoneId.of("America/Lima"))));
            StringBuilder sql=new StringBuilder("SELECT e.id,COALESCE(ha.hotel_codigo,a.hotel_codigo) AS hotel_codigo,"
                    + "CASE WHEN ha.id IS NULL THEN a.nombre ELSE CONCAT(N'Habitación ',ha.numero) END AS ubicacion,"
                    + "COALESCE(e.color_historico,'#000000') AS color_habitacion,h.nombre_completo,h.tipo_documento,h.numero_documento,"
                    + "e.check_in,e.check_out,e.estado FROM dbo.estancias e "
                    + "LEFT JOIN dbo.habitaciones ha ON ha.id=e.habitacion_id LEFT JOIN dbo.areas_comunes a ON a.id=e.area_comun_id "
                    + "JOIN dbo.estancia_huespedes eh ON eh.estancia_id=e.id JOIN dbo.huespedes h ON h.id=eh.huesped_id "
                    + "WHERE COALESCE(ha.hotel_codigo,a.hotel_codigo)=? ");
            List<Object> valores=new ArrayList<>();
            valores.add(hotel);
            if (!filtro.nombre().isEmpty()) { sql.append("AND h.nombre_completo LIKE ? ESCAPE '!' "); valores.add("%"+escaparLike(filtro.nombre())+"%"); }
            if (filtro.dia()!=null) {
                sql.append("AND e.check_in>=? AND e.check_in<? ");
                valores.add(Timestamp.valueOf(filtro.dia().atStartOfDay()));
                valores.add(Timestamp.valueOf(filtro.dia().plusDays(1).atStartOfDay()));
            }
            if (filtro.estuvoDesde()!=null) { sql.append("AND e.check_out>=? ");valores.add(Timestamp.valueOf(filtro.estuvoDesde())); }
            if (filtro.estuvoHasta()!=null) { sql.append("AND e.check_in<=? ");valores.add(Timestamp.valueOf(filtro.estuvoHasta())); }
            if (!filtro.colorHabitacion().isEmpty()) {
                sql.append("AND e.habitacion_id IS NOT NULL AND e.color_historico=? ");valores.add(filtro.colorHabitacion());
            }
            sql.append("ORDER BY e.check_in DESC,ubicacion,eh.orden");
            return List.copyOf(jdbc.query(sql.toString(),(r,n)->{
                LocalDateTime entrada=r.getTimestamp("check_in").toLocalDateTime();
                return new RegistroHuesped(r.getLong("id"),r.getString("hotel_codigo"),r.getString("ubicacion"),
                        r.getString("color_habitacion"),r.getString("nombre_completo"),
                        TipoDocumento.desdeCodigo(r.getString("tipo_documento")).orElseThrow(),r.getString("numero_documento"),
                        entrada.toLocalDate(),entrada,r.getTimestamp("check_out").toLocalDateTime(),"ACTIVA".equals(r.getString("estado")));
            },valores.toArray()));
        } catch (DataAccessException exception) { throw error(exception); }
    }

    @Override
    public Map<Integer,List<DatosHuesped>> listarActivosPorHabitacion(String hotel) {
        Map<Integer,List<DatosHuesped>> registros=new LinkedHashMap<>();
        try {
            jdbc.query("SELECT ha.numero,h.nombre_completo,h.tipo_documento,h.numero_documento FROM dbo.estancias e "
                    + "JOIN dbo.habitaciones ha ON ha.id=e.habitacion_id JOIN dbo.estancia_huespedes eh ON eh.estancia_id=e.id "
                    + "JOIN dbo.huespedes h ON h.id=eh.huesped_id WHERE ha.hotel_codigo=? AND e.estado='ACTIVA' ORDER BY ha.numero,eh.orden",
                    (RowCallbackHandler)r->registros.computeIfAbsent(r.getInt(1),ignorado->new ArrayList<>())
                            .add(new DatosHuesped(r.getString(2),TipoDocumento.desdeCodigo(r.getString(3)).orElseThrow(),r.getString(4))),hotel);
            registros.replaceAll((numero,personas)->List.copyOf(personas));
            return Map.copyOf(registros);
        } catch (DataAccessException exception) { throw error(exception); }
    }

    @Override public void close() { /* Spring posee el DataSource compartido. */ }
    private static RepositorioHuespedesException error(DataAccessException exception) {
        return new RepositorioHuespedesException("No fue posible acceder a huespedes y estancias.",exception);
    }
    private static String escaparLike(String valor) {
        return valor.replace("!","!!").replace("%","!%").replace("_","!_").replace("[","![");
    }
}
