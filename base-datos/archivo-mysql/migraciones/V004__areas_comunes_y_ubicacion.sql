-- Permite que el historial de huéspedes identifique habitaciones y áreas comunes.
USE gestera;

ALTER TABLE estancias
    ADD COLUMN ubicacion VARCHAR(80) NULL AFTER numero_habitacion;

UPDATE estancias
SET ubicacion = CONCAT('Habitación ', numero_habitacion)
WHERE ubicacion IS NULL;

ALTER TABLE estancias
    MODIFY COLUMN numero_habitacion SMALLINT UNSIGNED NULL,
    MODIFY COLUMN ubicacion VARCHAR(80) NOT NULL,
    DROP INDEX uq_estancias_habitacion_checkin,
    ADD CONSTRAINT uq_estancias_ubicacion_checkin
        UNIQUE (hotel_codigo, ubicacion, check_in),
    ADD CONSTRAINT chk_estancias_ubicacion
        CHECK (CHAR_LENGTH(TRIM(ubicacion)) BETWEEN 2 AND 80),
    ADD INDEX ix_estancias_hotel_ubicacion_fecha
        (hotel_codigo, ubicacion, check_in DESC);
