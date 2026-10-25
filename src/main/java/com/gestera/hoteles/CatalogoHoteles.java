package com.gestera.hoteles;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Catálogo central de hoteles visibles en el selector. */
public final class CatalogoHoteles {
    private static final List<Hotel> HOTELES = List.of(
            new Hotel("sol-pucusana", "Hotel El Sol de Pucusana", "Pucusana, Lima", "EP"),
            new Hotel("brisa-marina", "Hotel Brisa Marina", "Miraflores, Lima", "BM"),
            new Hotel("valle-dorado", "Hotel Valle Dorado", "Urubamba, Cusco", "VD"),
            new Hotel("costa-serena", "Hotel Costa Serena", "Máncora, Piura", "CS"));

    private final List<Hotel> hoteles;
    public CatalogoHoteles() { hoteles=HOTELES; }
    public CatalogoHoteles(DataSource fuente) {
        hoteles=List.copyOf(new JdbcTemplate(fuente).query(
                "SELECT codigo,nombre,ubicacion,iniciales FROM dbo.hoteles WHERE activo=1 "
                + "ORDER BY CASE codigo WHEN 'sol-pucusana' THEN 0 WHEN 'brisa-marina' THEN 1 "
                + "WHEN 'valle-dorado' THEN 2 WHEN 'costa-serena' THEN 3 ELSE 4 END,creado_en,codigo",
                (r,n)->new Hotel(r.getString(1),r.getString(2),r.getString(3),r.getString(4))));
        if (hoteles.isEmpty()) throw new IllegalStateException("No hay hoteles activos en SQL Server.");
    }

    public List<Hotel> todos() {
        return hoteles;
    }

    public Optional<Hotel> buscarPorCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return hoteles.stream().filter(hotel -> hotel.codigo().equals(codigo)).findFirst();
    }

    public record Hotel(String codigo, String nombre, String ubicacion, String iniciales) {
        public Hotel {
            if (codigo == null || codigo.isBlank()
                    || nombre == null || nombre.isBlank()
                    || ubicacion == null || ubicacion.isBlank()
                    || iniciales == null || iniciales.isBlank()) {
                throw new IllegalArgumentException("Los datos del hotel son obligatorios.");
            }
        }
    }
}
