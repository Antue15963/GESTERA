package com.gestera.repositorios.reportes;

import com.gestera.modelo.reportes.IngresoHabitacion;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.gestera.infraestructura.persistencia.TransaccionesHotel;
import org.springframework.dao.DataIntegrityViolationException;


/** Historial SQL Server; el UPSERT bloquea la clave natural hasta el commit. */
public final class RepositorioIngresosHabitacionJdbc implements RepositorioIngresosHabitacion {
    private final JdbcTemplate jdbc;
    private final TransaccionesHotel transacciones;

    public RepositorioIngresosHabitacionJdbc(DataSource fuente) {
        jdbc = new JdbcTemplate(fuente);
        transacciones = new TransaccionesHotel(fuente);
    }

    @Override
    public void guardarPago(String hotel, int numero, LocalDateTime entrada, LocalDateTime pagado, BigDecimal monto) {
        try {
            transacciones.ejecutar(hotel,() -> {
                Long estancia=jdbc.query("SELECT e.id FROM dbo.estancias e JOIN dbo.habitaciones h ON h.id=e.habitacion_id "
                        + "WHERE h.hotel_codigo=? AND h.numero=? AND e.check_in=?",r->r.next()?r.getLong(1):null,
                        hotel,numero,Timestamp.valueOf(entrada));
                if (estancia==null) throw new DataIntegrityViolationException("No existe la estancia del pago.");
                int filas=jdbc.update("UPDATE dbo.pagos_estancia SET pagado_en=?,monto=? WHERE estancia_id=?",
                        Timestamp.valueOf(pagado),monto,estancia);
                if (filas==0) jdbc.update("INSERT dbo.pagos_estancia(estancia_id,pagado_en,monto) VALUES(?,?,?)",
                        estancia,Timestamp.valueOf(pagado),monto);
                return null;
            });
        } catch (DataAccessException exception) {
            throw error(exception);
        }
    }

    @Override
    public void eliminarPago(String hotel, int numero, LocalDateTime entrada) {
        try {
            jdbc.update("DELETE p FROM dbo.pagos_estancia p JOIN dbo.estancias e ON e.id=p.estancia_id "
                    + "JOIN dbo.habitaciones h ON h.id=e.habitacion_id WHERE h.hotel_codigo=? AND h.numero=? AND e.check_in=?",
                    hotel, numero, Timestamp.valueOf(entrada));
        } catch (DataAccessException exception) {
            throw error(exception);
        }
    }

    @Override
    public List<IngresoHabitacion> listar(String hotel, LocalDateTime desde, LocalDateTime hasta) {
        try {
            return List.copyOf(jdbc.query("SELECT p.id,h.hotel_codigo,h.numero AS numero_habitacion,e.check_in,p.pagado_en,p.monto "
                    + "FROM dbo.pagos_estancia p JOIN dbo.estancias e ON e.id=p.estancia_id "
                    + "JOIN dbo.habitaciones h ON h.id=e.habitacion_id WHERE h.hotel_codigo=? AND p.pagado_en>=? AND p.pagado_en<? "
                    + "ORDER BY p.pagado_en DESC,p.id DESC", (fila, indice) -> new IngresoHabitacion(fila.getLong("id"),
                            fila.getString("hotel_codigo"), fila.getInt("numero_habitacion"),
                            fila.getTimestamp("check_in").toLocalDateTime(), fila.getTimestamp("pagado_en").toLocalDateTime(),
                            fila.getBigDecimal("monto")), hotel, Timestamp.valueOf(desde), Timestamp.valueOf(hasta)));
        } catch (DataAccessException exception) {
            throw error(exception);
        }
    }

    @Override
    public void close() {
        // Spring cierra el pool compartido.
    }

    private static RepositorioIngresosHabitacionException error(DataAccessException exception) {
        return new RepositorioIngresosHabitacionException("No fue posible acceder al historial de ingresos en SQL Server.", exception);
    }
}
