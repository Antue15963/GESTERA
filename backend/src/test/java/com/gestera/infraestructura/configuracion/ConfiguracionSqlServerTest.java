package com.gestera.infraestructura.configuracion;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConfiguracionSqlServerTest {
    @Test
    void admiteAutenticacionSqlSinExponerCredenciales() {
        var entorno = new MockEnvironment().withProperty("gestera.db.url", "jdbc:sqlserver://localhost;databaseName=gestera")
                .withProperty("gestera.db.usuario", "gestera_app").withProperty("gestera.db.contrasena", "secreto-de-prueba");
        var configuracion = ConfiguracionSqlServer.cargar(entorno);
        assertEquals("gestera_app", configuracion.usuario());
        assertFalse(configuracion.toString().contains("secreto-de-prueba"));
    }
    @Test
    void admiteWindowsSinUsuarioNiContrasenaSql() {
        assertDoesNotThrow(() -> new ConfiguracionSqlServer(
                "jdbc:sqlserver://localhost;databaseName=gestera;integratedSecurity=true", null, null));
    }
    @Test
    void rechazaMotoresYCredencialesIncompatibles() {
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSqlServer("jdbc:mysql://localhost/gestera", "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSqlServer("jdbc:sqlserver://localhost", null, null));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSqlServer("jdbc:sqlserver://localhost;password=secreto", "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionSqlServer(
                "jdbc:sqlserver://localhost;integratedSecurity=true", "u", "p"));
    }
}
