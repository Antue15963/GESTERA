package com.gestera.reportes;

import com.gestera.pruebas.Aserciones;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Connection;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Pruebas del DAO JDBC con infraestructura sustituible. */
public final class RepositorioIngresosHabitacionJdbcPrueba {
    private RepositorioIngresosHabitacionJdbcPrueba() {
    }

    public static int ejecutar() {
        AtomicInteger aperturas = new AtomicInteger();
        SQLException falloJdbc = new SQLException("base de datos no disponible");
        AbstractDataSource proveedor = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                aperturas.incrementAndGet();
                throw falloJdbc;
            }
            @Override
            public Connection getConnection(String usuario, String contrasena) throws SQLException {
                return getConnection();
            }
        };
        RepositorioIngresosHabitacionJdbc dao =
                new RepositorioIngresosHabitacionJdbc(proveedor);

        RepositorioIngresosHabitacionException error = Aserciones.lanza(
                RepositorioIngresosHabitacionException.class,
                () -> dao.listar(
                        "sol-pucusana",
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        LocalDateTime.of(2026, 1, 2, 0, 0)),
                "El DAO debe traducir fallos JDBC a su excepción de persistencia");
        Aserciones.iguales(1, aperturas.get(),
                "El DAO debe solicitar la conexión a la interfaz inyectada");
        Aserciones.iguales(falloJdbc, error.getCause().getCause(),
                "El DAO debe conservar la causa del fallo JDBC");
        return 3;
    }
}
