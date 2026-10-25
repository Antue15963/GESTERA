package com.gestera.pruebas;

import com.gestera.areas.GestorAreasComunesPrueba;
import com.gestera.autenticacion.AutenticadorLlavePrueba;
import com.gestera.configuracion.ConfiguracionAplicacionPrueba;
import com.gestera.hoteles.CatalogoHotelesPrueba;
import com.gestera.hoteles.GestorLlavesHotelesPrueba;
import com.gestera.habitaciones.GestorHabitacionesHotelesPrueba;
import com.gestera.huespedes.GestorHuespedesHotelesPrueba;
import com.gestera.reportes.GestorReportesPrueba;
import com.gestera.reportes.RepositorioIngresosHabitacionJdbcPrueba;
import com.gestera.servidor.ServidorGesteraPrueba;
import com.gestera.servicios.GestorServicioHabitacionPrueba;
import com.gestera.sesiones.GestorSesionesPrueba;
import com.gestera.vistas.PlantillasWebPrueba;

/** Punto de entrada único de las pruebas Java sin dependencias. */
public final class TodasLasPruebas {
    private TodasLasPruebas() {
    }

    public static void main(String[] argumentos) throws Exception {
        int ejecutadas = 0;
        ejecutadas += ejecutarGrupo("configuración", ConfiguracionAplicacionPrueba::ejecutar);
        ejecutadas += ejecutarGrupo("autenticación", AutenticadorLlavePrueba::ejecutar);
        ejecutadas += ejecutarGrupo("catalogo de hoteles", CatalogoHotelesPrueba::ejecutar);
        ejecutadas += ejecutarGrupo("llaves de hoteles", GestorLlavesHotelesPrueba::ejecutar);
        ejecutadas += ejecutarGrupo(
                "inventario de habitaciones", GestorHabitacionesHotelesPrueba::ejecutar);
        ejecutadas += ejecutarGrupo(
                "registro de huéspedes", GestorHuespedesHotelesPrueba::ejecutar);
        ejecutadas += ejecutarGrupo(
                "áreas comunes", GestorAreasComunesPrueba::ejecutar);
        ejecutadas += ejecutarGrupo("sesiones", GestorSesionesPrueba::ejecutar);
        ejecutadas += ejecutarGrupo(
                "servicio al cuarto", GestorServicioHabitacionPrueba::ejecutar);
        ejecutadas += ejecutarGrupo("reportes", GestorReportesPrueba::ejecutar);
        ejecutadas += ejecutarGrupo(
                "DAO JDBC", RepositorioIngresosHabitacionJdbcPrueba::ejecutar);
        ejecutadas += ejecutarGrupo("plantillas y recursos", PlantillasWebPrueba::ejecutar);
        ejecutadas += ejecutarGrupo("servidor HTTP", ServidorGesteraPrueba::ejecutar);
        System.out.println("OK - " + ejecutadas + " pruebas superadas.");
    }

    private static int ejecutarGrupo(String nombre, GrupoPruebas grupo) throws Exception {
        int cantidad = grupo.ejecutar();
        System.out.println("  [OK] " + nombre + ": " + cantidad);
        return cantidad;
    }

    @FunctionalInterface
    private interface GrupoPruebas {
        int ejecutar() throws Exception;
    }
}
