package com.gestera.configuracion;

import java.util.Locale;
import org.springframework.core.env.Environment;

/** Configuracion exclusiva de SQL Server; las credenciales se mantienen fuera de la URL. */
public record ConfiguracionSqlServer(String url, String usuario, String contrasena) {
    public ConfiguracionSqlServer {
        if (url == null || !url.startsWith("jdbc:sqlserver://")) {
            throw new IllegalArgumentException("GESTERA_DB_URL debe comenzar con jdbc:sqlserver://.");
        }
        String normalizada = url.toLowerCase(Locale.ROOT);
        if (normalizada.matches("(?s).*;(password|pwd|user|username)\\s*=.*")) {
            throw new IllegalArgumentException("Defina usuario y contrasena fuera de GESTERA_DB_URL.");
        }
        boolean integrada = normalizada.matches("(?s).*;integratedsecurity\\s*=\\s*true(?:;.*)?");
        if (!integrada && (usuario == null || usuario.isBlank() || contrasena == null || contrasena.isBlank())) {
            throw new IllegalArgumentException(
                    "La autenticacion SQL requiere GESTERA_DB_USUARIO y GESTERA_DB_CONTRASENA.");
        }
        if (integrada && (usuario != null && !usuario.isBlank() || contrasena != null && !contrasena.isBlank())) {
            throw new IllegalArgumentException("No combine integratedSecurity=true con credenciales SQL.");
        }
    }

    public static ConfiguracionSqlServer cargar(Environment entorno) {
        return new ConfiguracionSqlServer(
                entorno.getProperty("gestera.db.url"),
                entorno.getProperty("gestera.db.usuario"),
                entorno.getProperty("gestera.db.contrasena"));
    }

    @Override
    public String toString() {
        return "ConfiguracionSqlServer[credenciales protegidas]";
    }
}
