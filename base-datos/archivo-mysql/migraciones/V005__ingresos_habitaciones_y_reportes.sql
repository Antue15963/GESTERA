-- Historial contable de estancias pagadas para el modulo de reportes.
-- Ejecutar despues de V001 a V004.
USE gestera;

CREATE TABLE IF NOT EXISTS ingresos_habitacion (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hotel_codigo VARCHAR(80) NOT NULL,
    numero_habitacion SMALLINT UNSIGNED NOT NULL,
    check_in DATETIME NOT NULL,
    pagado_en DATETIME NOT NULL,
    monto DECIMAL(10, 2) UNSIGNED NOT NULL,
    CONSTRAINT pk_ingresos_habitacion PRIMARY KEY (id),
    CONSTRAINT uq_ingresos_habitacion_estancia UNIQUE
        (hotel_codigo, numero_habitacion, check_in),
    CONSTRAINT fk_ingresos_habitacion_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ingresos_habitacion_numero
        CHECK (numero_habitacion BETWEEN 1 AND 50),
    CONSTRAINT chk_ingresos_habitacion_monto CHECK (monto > 0),
    INDEX ix_ingresos_habitacion_hotel_fecha (hotel_codigo, pagado_en DESC)
) ENGINE = InnoDB;
