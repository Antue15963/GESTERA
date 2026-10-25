package com.gestera.servicios;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import com.gestera.persistencia.TransaccionesHotel;
import org.springframework.dao.DataIntegrityViolationException;


/** Cabecera e items SQL Server confirmados en una sola transaccion. */
public final class RepositorioServicioHabitacionJdbc implements RepositorioServicioHabitacion {
    private final JdbcTemplate jdbc;
    private final TransaccionesHotel transacciones;
    private final SimpleJdbcInsert insertarPedido;

    public RepositorioServicioHabitacionJdbc(DataSource fuente) {
        jdbc = new JdbcTemplate(fuente);
        transacciones = new TransaccionesHotel(fuente);
        insertarPedido = new SimpleJdbcInsert(fuente).withSchemaName("dbo").withTableName("pedidos_servicio")
                .usingGeneratedKeyColumns("id").usingColumns("estancia_id", "estado",
                        "observaciones", "creado_en", "actualizado_en");
    }

    @Override
    public void crear(String hotel, int numero, String observaciones, LocalDateTime creado, List<LineaPedidoServicio> lineas) {
        if (lineas==null || lineas.isEmpty()) throw new IllegalArgumentException("Un pedido necesita al menos un item.");
        try {
            transacciones.ejecutar(hotel,() -> {
                Long estancia=jdbc.query("SELECT e.id FROM dbo.estancias e JOIN dbo.habitaciones h ON h.id=e.habitacion_id "
                        + "WHERE h.hotel_codigo=? AND h.numero=? AND h.activa=1 AND h.estado='OCUPADA' AND e.estado='ACTIVA'",
                        r->r.next()?r.getLong(1):null,hotel,numero);
                if (estancia==null) throw new DataIntegrityViolationException("La habitacion ya no tiene una estancia activa.");
                long pedido = insertarPedido.executeAndReturnKey(new MapSqlParameterSource()
                        .addValue("estancia_id", estancia)
                        .addValue("estado", "PENDIENTE").addValue("observaciones", observaciones)
                        .addValue("creado_en", Timestamp.valueOf(creado)).addValue("actualizado_en", Timestamp.valueOf(creado)))
                        .longValue();
                jdbc.batchUpdate("INSERT INTO dbo.pedido_servicio_items "
                        + "(pedido_id, producto_codigo_snapshot, producto_nombre_snapshot, precio_unitario_snapshot, cantidad,producto_catalogo_id,producto_codigo_fk) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        lineas, 100, (sentencia, linea) -> {
                            sentencia.setLong(1, pedido);
                            sentencia.setNString(2, linea.productoCodigo());
                            sentencia.setNString(3, linea.productoNombre());
                            sentencia.setBigDecimal(4, linea.precioUnitario());
                            sentencia.setInt(5, linea.cantidad());
                            Long catalogo=jdbc.query("SELECT p.catalogo_id FROM dbo.productos_servicio p JOIN dbo.catalogos_servicio c "
                                    + "ON c.id=p.catalogo_id WHERE c.hotel_codigo=? AND p.codigo=?",
                                    r->r.next()?r.getLong(1):null,hotel,linea.productoCodigo());
                            if (catalogo==null) { sentencia.setNull(6,java.sql.Types.BIGINT); sentencia.setNull(7,java.sql.Types.VARCHAR); }
                            else { sentencia.setLong(6,catalogo); sentencia.setString(7,linea.productoCodigo()); }
                        });
                return null;
            });
        } catch (DataAccessException exception) {
            throw error(exception);
        }
    }

    @Override
    public List<PedidoServicio> listarPorHotel(String hotel) {
        LinkedHashMap<Long, PedidoEnConstruccion> pedidos = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT p.id, h.hotel_codigo, h.numero AS numero_habitacion, p.estado, p.observaciones, "
                    + "p.creado_en, p.actualizado_en, i.producto_codigo_snapshot AS producto_codigo, i.producto_nombre_snapshot AS producto_nombre, i.precio_unitario_snapshot AS precio_unitario, i.cantidad "
                    + "FROM dbo.pedidos_servicio p JOIN dbo.estancias e ON e.id=p.estancia_id JOIN dbo.habitaciones h ON h.id=e.habitacion_id JOIN dbo.pedido_servicio_items i ON i.pedido_id = p.id "
                    + "WHERE h.hotel_codigo = ? ORDER BY p.creado_en DESC, p.id DESC, i.id", (RowCallbackHandler) fila -> {
                        long id = fila.getLong("id");
                        PedidoEnConstruccion pedido = pedidos.get(id);
                        if (pedido == null) {
                            pedido = new PedidoEnConstruccion(id, fila.getString("hotel_codigo"), fila.getInt("numero_habitacion"),
                                    EstadoPedidoServicio.valueOf(fila.getString("estado")), fila.getString("observaciones"),
                                    fila.getTimestamp("creado_en").toLocalDateTime(), fila.getTimestamp("actualizado_en").toLocalDateTime());
                            pedidos.put(id, pedido);
                        }
                        pedido.lineas.add(new LineaPedidoServicio(fila.getString("producto_codigo"), fila.getString("producto_nombre"),
                                fila.getBigDecimal("precio_unitario"), fila.getInt("cantidad")));
                    }, hotel);
            return pedidos.values().stream().map(PedidoEnConstruccion::construir).toList();
        } catch (DataAccessException exception) {
            throw error(exception);
        }
    }

    @Override
    public boolean cambiarEstado(String hotel, long pedido, EstadoPedidoServicio actual,
            EstadoPedidoServicio nuevo, LocalDateTime actualizado) {
        try {
            return jdbc.update("UPDATE p SET estado = ?, actualizado_en = ? FROM dbo.pedidos_servicio p "
                    + "JOIN dbo.estancias e ON e.id=p.estancia_id JOIN dbo.habitaciones h ON h.id=e.habitacion_id "
                    + "WHERE p.id = ? AND h.hotel_codigo = ? AND p.estado = ?", nuevo.name(), Timestamp.valueOf(actualizado),
                    pedido, hotel, actual.name()) == 1;
        } catch (DataAccessException exception) {
            throw error(exception);
        }
    }

    @Override
    public void close() {
        // Spring cierra el pool compartido.
    }

    private static RepositorioServicioHabitacionException error(DataAccessException exception) {
        return new RepositorioServicioHabitacionException("No fue posible acceder a los pedidos en SQL Server.", exception);
    }

    private record PedidoEnConstruccion(long id, String hotel, int numero, EstadoPedidoServicio estado,
            String observaciones, LocalDateTime creado, LocalDateTime actualizado, List<LineaPedidoServicio> lineas) {
        PedidoEnConstruccion(long id, String hotel, int numero, EstadoPedidoServicio estado,
                String observaciones, LocalDateTime creado, LocalDateTime actualizado) {
            this(id, hotel, numero, estado, observaciones, creado, actualizado, new ArrayList<>());
        }
        PedidoServicio construir() {
            return new PedidoServicio(id, hotel, numero, estado, observaciones, creado, actualizado, lineas);
        }
    }
}
