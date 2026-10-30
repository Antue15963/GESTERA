package com.gestera.repositorios.hoteles;

import com.gestera.seguridad.autenticacion.AutenticadorLlave.MaterialClave;
import com.gestera.infraestructura.persistencia.TransaccionesHotel;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RepositorioCredencialesHotelJdbc implements RepositorioCredencialesHotel {
    private final JdbcTemplate jdbc;
    private final TransaccionesHotel transacciones;
    public RepositorioCredencialesHotelJdbc(DataSource fuente) {
        jdbc=new JdbcTemplate(fuente);
        transacciones=new TransaccionesHotel(fuente);
    }
    @Override public void inicializar(String hotel, MaterialClave material) {
        transacciones.ejecutar(hotel,()->{
            if (jdbc.queryForObject("SELECT COUNT(*) FROM dbo.credenciales_hotel WHERE hotel_codigo=?",Integer.class,hotel)==0) {
                byte[] sal=material.sal(),hash=material.hash();
                try { jdbc.update("INSERT dbo.credenciales_hotel(hotel_codigo,sal,hash_clave,iteraciones) VALUES(?,?,?,?)",
                        hotel,sal,hash,material.iteraciones()); }
                finally { Arrays.fill(sal,(byte)0); Arrays.fill(hash,(byte)0); }
            }
            return null;
        });
    }
    @Override public Credencial obtener(String hotel) {
        return jdbc.queryForObject("SELECT sal,hash_clave,iteraciones,version,requiere_cambio FROM dbo.credenciales_hotel WHERE hotel_codigo=?",
                (r,n)->new Credencial(new MaterialClave(r.getBytes(1),r.getBytes(2),r.getInt(3)),r.getLong(4),r.getBoolean(5)),hotel);
    }
    @Override public boolean cambiar(String hotel,long versionEsperada,MaterialClave material) {
        return transacciones.ejecutar(hotel,()->{
            byte[] sal=material.sal(),hash=material.hash();
            try {
                return jdbc.update("UPDATE dbo.credenciales_hotel SET sal=?,hash_clave=?,iteraciones=?,version=version+1,"
                        + "requiere_cambio=0 WHERE hotel_codigo=? AND (?=0 OR version=?)",
                        sal,hash,material.iteraciones(),hotel,versionEsperada,versionEsperada)==1;
            } finally { Arrays.fill(sal,(byte)0); Arrays.fill(hash,(byte)0); }
        });
    }
}
