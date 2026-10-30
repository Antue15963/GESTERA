package com.gestera.repositorios.servicios;

import com.gestera.modelo.servicios.ProductoServicio;

import com.gestera.infraestructura.persistencia.TransaccionesHotel;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RepositorioCatalogoServicioJdbc implements RepositorioCatalogoServicio {
    private final JdbcTemplate jdbc;
    private final TransaccionesHotel transacciones;
    public RepositorioCatalogoServicioJdbc(DataSource fuente) {
        jdbc=new JdbcTemplate(fuente);
        transacciones=new TransaccionesHotel(fuente);
    }
    @Override public <T> T ejecutar(String hotel,java.util.function.Supplier<T> operacion) {
        return transacciones.ejecutar(hotel,operacion);
    }
    @Override public void inicializar(String hotel,List<ProductoServicio> productos,List<String> categorias) {
        transacciones.ejecutar(hotel,()->{
            if (jdbc.queryForObject("SELECT COUNT(*) FROM dbo.catalogos_servicio WHERE hotel_codigo=?",Integer.class,hotel)==0) {
                jdbc.update("INSERT dbo.catalogos_servicio(hotel_codigo) VALUES(?)",hotel);
                guardar(hotel,new DatosCatalogo(productos,categorias,1));
            }
            return null;
        });
    }
    @Override public DatosCatalogo cargar(String hotel) {
        return transacciones.ejecutar(hotel,()->{
            Map<String,Object> cabecera=jdbc.queryForMap("SELECT id,version FROM dbo.catalogos_servicio WHERE hotel_codigo=?",hotel);
            long id=((Number)cabecera.get("id")).longValue();
            List<String> categorias=jdbc.queryForList("SELECT nombre FROM dbo.categorias_servicio WHERE catalogo_id=? ORDER BY creado_en,codigo",String.class,id);
            List<ProductoServicio> productos=jdbc.query("SELECT p.codigo,p.nombre,c.nombre AS categoria,p.precio "
                    + "FROM dbo.productos_servicio p JOIN dbo.categorias_servicio c ON c.catalogo_id=p.catalogo_id AND c.codigo=p.categoria_codigo "
                    + "WHERE p.catalogo_id=? ORDER BY p.creado_en,p.codigo",
                    (r,n)->new ProductoServicio(r.getString(1),r.getString(2),r.getString(3),r.getBigDecimal(4)),id);
            return new DatosCatalogo(productos,categorias,((Number)cabecera.get("version")).longValue());
        });
    }
    @Override public boolean guardar(String hotel,DatosCatalogo catalogo) {
        return transacciones.ejecutar(hotel,()->{
            Map<String,Object> cabecera=jdbc.queryForMap("SELECT id,version FROM dbo.catalogos_servicio WHERE hotel_codigo=?",hotel);
            long id=((Number)cabecera.get("id")).longValue();
            if (((Number)cabecera.get("version")).longValue()!=catalogo.version()) return false;
            Set<String> productosConservados=new HashSet<>();
            catalogo.productos().forEach(p->productosConservados.add(p.codigo()));
            for (String codigo:jdbc.queryForList("SELECT codigo FROM dbo.productos_servicio WHERE catalogo_id=?",String.class,id)) {
                if (!productosConservados.contains(codigo))
                    jdbc.update("DELETE dbo.productos_servicio WHERE catalogo_id=? AND codigo=?",id,codigo);
            }
            Map<String,String> categorias=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            jdbc.query("SELECT codigo,nombre FROM dbo.categorias_servicio WHERE catalogo_id=?",
                    (org.springframework.jdbc.core.RowCallbackHandler)r->categorias.put(r.getString("nombre"),r.getString("codigo")),id);
            Set<String> categoriasConservadas=new HashSet<>();
            for (String nombre:catalogo.categorias()) {
                String codigo=categorias.get(nombre);
                if (codigo==null) {
                    codigo="categoria-"+UUID.randomUUID().toString().replace("-","");
                    jdbc.update("INSERT dbo.categorias_servicio(catalogo_id,codigo,nombre) VALUES(?,?,?)",id,codigo,nombre);
                    categorias.put(nombre,codigo);
                }
                categoriasConservadas.add(codigo);
            }
            for (ProductoServicio producto:catalogo.productos()) {
                String categoria=categorias.get(producto.categoria());
                if (categoria==null) throw new IllegalArgumentException("El producto no tiene una categoria valida.");
                if (jdbc.update("UPDATE dbo.productos_servicio SET categoria_codigo=?,nombre=?,precio=? WHERE catalogo_id=? AND codigo=?",
                        categoria,producto.nombre(),producto.precio(),id,producto.codigo())==0) {
                    jdbc.update("INSERT dbo.productos_servicio(catalogo_id,codigo,categoria_codigo,nombre,precio) VALUES(?,?,?,?,?)",
                            id,producto.codigo(),categoria,producto.nombre(),producto.precio());
                }
            }
            for (String codigo:categorias.values()) {
                if (!categoriasConservadas.contains(codigo))
                    jdbc.update("DELETE dbo.categorias_servicio WHERE catalogo_id=? AND codigo=?",id,codigo);
            }
            jdbc.update("UPDATE dbo.catalogos_servicio SET version=version+1 WHERE id=?",id);
            return true;
        });
    }
}
