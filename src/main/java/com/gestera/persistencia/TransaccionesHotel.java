package com.gestera.persistencia;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Serializa la operacion completa del hotel, incluso entre varias instancias Java. */
public final class TransaccionesHotel {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;

    public TransaccionesHotel(DataSource fuente) {
        jdbc = new JdbcTemplate(fuente);
        transacciones = new TransactionTemplate(new JdbcTransactionManager(fuente));
    }

    public <T> T ejecutar(String hotel, Supplier<T> operacion) {
        return transacciones.execute(estado -> {
            // EXEC no inicia la transaccion implicita del driver. El bloqueo necesita
            // una transaccion fisica ya abierta antes de llamar a sp_getapplock.
            jdbc.execute("IF @@TRANCOUNT=0 BEGIN TRANSACTION;");
            Integer resultado = jdbc.execute((ConnectionCallback<Integer>) conexion -> {
                try (CallableStatement llamada = conexion.prepareCall("{? = call sys.sp_getapplock(?, ?, ?, ?)}")) {
                    llamada.registerOutParameter(1, Types.INTEGER);
                    llamada.setNString(2, "GESTERA_HOTEL:" + hotel);
                    llamada.setString(3, "Exclusive");
                    llamada.setString(4, "Transaction");
                    llamada.setInt(5, 10000);
                    llamada.execute();
                    return llamada.getInt(1);
                }
            });
            if (resultado == null || resultado < 0) {
                throw new IllegalStateException("No se pudo bloquear el hotel para completar la operacion: "+resultado);
            }
            return operacion.get();
        });
    }
}
