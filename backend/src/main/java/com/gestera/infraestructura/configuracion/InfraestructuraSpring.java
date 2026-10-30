package com.gestera.infraestructura.configuracion;

import com.gestera.infraestructura.servidor.ServidorGestera;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/** Punto de composicion: Spring posee el pool y el ciclo de vida del servidor. */
@Configuration(proxyBeanMethods = false)
public class InfraestructuraSpring {
    @Bean(destroyMethod = "close")
    @Profile("!memoria")
    public HikariDataSource dataSource(Environment entorno) {
        ConfiguracionSqlServer configuracion = ConfiguracionSqlServer.cargar(entorno);
        HikariConfig pool = new HikariConfig();
        pool.setPoolName("GesteraSqlServer");
        pool.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        pool.setJdbcUrl(configuracion.url());
        if (configuracion.usuario() != null) {
            pool.setUsername(configuracion.usuario());
            pool.setPassword(configuracion.contrasena());
        }
        pool.setMaximumPoolSize(entorno.getProperty("gestera.db.pool.maximo", Integer.class, 10));
        pool.setMinimumIdle(1);
        pool.setConnectionTimeout(10000);
        pool.setValidationTimeout(5000);
        pool.setInitializationFailTimeout(10000);
        pool.addDataSourceProperty("applicationName", "Gestera");
        HikariDataSource fuente = new HikariDataSource(pool);
        try {
            String version = new JdbcTemplate(fuente).queryForObject(
                    "SELECT CONVERT(nvarchar(128), value) FROM sys.extended_properties "
                    + "WHERE class = 0 AND name = N'Gestera.Instalador'", String.class);
            if (!"GESTERA_SQLSERVER_14_V2".equals(version)) {
                throw new IllegalStateException("Instale CREAR_GESTERA_SQLSERVER.sql en la base configurada.");
            }
            return fuente;
        } catch (RuntimeException exception) {
            fuente.close();
            throw new IllegalStateException(
                    "SQL Server no tiene el esquema esperado. Revise base-datos/sqlserver/README.md.", exception);
        }
    }

    @Bean(initMethod = "iniciar", destroyMethod = "close")
    public ServidorGestera servidorGestera(Environment entorno, ObjectProvider<DataSource> fuentes)
            throws IOException {
        try (ConfiguracionAplicacion configuracion = ConfiguracionAplicacion.cargar(entorno)) {
            return ServidorGestera.crear(configuracion, fuentes.getIfAvailable());
        }
    }
}
