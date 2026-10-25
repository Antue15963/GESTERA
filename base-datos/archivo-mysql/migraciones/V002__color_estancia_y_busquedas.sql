-- Conserva el color de la habitación como instantánea de cada estancia.
USE gestera;

ALTER TABLE estancias
    ADD COLUMN color_habitacion CHAR(7) NOT NULL DEFAULT '#000000'
        AFTER numero_habitacion,
    ADD CONSTRAINT chk_estancias_color
        CHECK (color_habitacion REGEXP '^#[0-9A-Fa-f]{6}$'),
    ADD INDEX ix_estancias_hotel_color_fecha
        (hotel_codigo, color_habitacion, check_in DESC),
    ADD INDEX ix_estancias_hotel_periodo
        (hotel_codigo, check_in, check_out);
