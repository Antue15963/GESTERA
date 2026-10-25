package com.gestera.areas;

import com.gestera.areas.GestorAreasComunes.AreaComun;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RepositorioAreasComunesJdbc implements RepositorioAreasComunes {
    private final JdbcTemplate jdbc;
    public RepositorioAreasComunesJdbc(DataSource fuente) { jdbc=new JdbcTemplate(fuente); }
    @Override public List<AreaComun> listar(String hotel) {
        return jdbc.query("SELECT codigo,nombre,descripcion FROM dbo.areas_comunes WHERE hotel_codigo=? AND activa=1 ORDER BY id",
                (r,n)->new AreaComun(r.getString(1),r.getString(2),r.getString(3)),hotel);
    }
}
