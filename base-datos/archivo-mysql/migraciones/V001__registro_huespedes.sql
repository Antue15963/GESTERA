-- Ejecutar en MySQL Workbench con MySQL 8.0 o superior.
CREATE DATABASE IF NOT EXISTS gestera
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE gestera;

CREATE TABLE IF NOT EXISTS hoteles (
    codigo VARCHAR(80) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    ubicacion VARCHAR(150) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_hoteles PRIMARY KEY (codigo)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS huespedes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre_completo VARCHAR(100) NOT NULL,
    tipo_documento ENUM('dni', 'carnet_extranjeria', 'pasaporte') NOT NULL,
    numero_documento VARCHAR(20) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_huespedes PRIMARY KEY (id),
    CONSTRAINT uq_huespedes_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT chk_huespedes_nombre CHECK (CHAR_LENGTH(TRIM(nombre_completo)) >= 2)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS estancias (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hotel_codigo VARCHAR(80) NOT NULL,
    numero_habitacion SMALLINT UNSIGNED NOT NULL,
    check_in DATETIME NOT NULL,
    check_out DATETIME NOT NULL,
    estado ENUM('ACTIVA', 'FINALIZADA') NOT NULL DEFAULT 'ACTIVA',
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_estancias PRIMARY KEY (id),
    CONSTRAINT fk_estancias_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_estancias_habitacion_checkin
        UNIQUE (hotel_codigo, numero_habitacion, check_in),
    CONSTRAINT chk_estancias_habitacion CHECK (numero_habitacion BETWEEN 1 AND 50),
    CONSTRAINT chk_estancias_horario CHECK (check_out >= check_in),
    INDEX ix_estancias_hotel_estado_fecha (hotel_codigo, estado, check_in DESC),
    INDEX ix_estancias_hotel_habitacion_estado
        (hotel_codigo, numero_habitacion, estado)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS estancia_huespedes (
    estancia_id BIGINT UNSIGNED NOT NULL,
    huesped_id BIGINT UNSIGNED NOT NULL,
    orden TINYINT UNSIGNED NOT NULL,
    CONSTRAINT pk_estancia_huespedes PRIMARY KEY (estancia_id, huesped_id),
    CONSTRAINT uq_estancia_huespedes_orden UNIQUE (estancia_id, orden),
    CONSTRAINT fk_estancia_huespedes_estancia FOREIGN KEY (estancia_id)
        REFERENCES estancias (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_estancia_huespedes_huesped FOREIGN KEY (huesped_id)
        REFERENCES huespedes (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_estancia_huespedes_orden CHECK (orden BETWEEN 1 AND 8),
    INDEX ix_estancia_huespedes_huesped (huesped_id)
) ENGINE = InnoDB;
