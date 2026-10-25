-- Gestera - diseño físico objetivo para MySQL 8.0+
-- Derivado del código, las migraciones V001..V005, las pruebas y el README.
-- No modifica la aplicación: amplía el esquema actual para cubrir también los
-- módulos que hoy mantienen su estado únicamente en memoria.

CREATE DATABASE IF NOT EXISTS gestera
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE gestera;

CREATE TABLE IF NOT EXISTS hoteles (
    codigo VARCHAR(80) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    ubicacion VARCHAR(150) NOT NULL,
    iniciales CHAR(2) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_hoteles PRIMARY KEY (codigo),
    CONSTRAINT uq_hoteles_nombre UNIQUE (nombre),
    CONSTRAINT chk_hoteles_codigo CHECK (CHAR_LENGTH(TRIM(codigo)) BETWEEN 1 AND 80),
    CONSTRAINT chk_hoteles_nombre CHECK (CHAR_LENGTH(TRIM(nombre)) BETWEEN 2 AND 150),
    CONSTRAINT chk_hoteles_ubicacion CHECK (CHAR_LENGTH(TRIM(ubicacion)) BETWEEN 2 AND 150),
    CONSTRAINT chk_hoteles_iniciales CHECK (iniciales REGEXP '^[[:alnum:]]{2}$')
) ENGINE = InnoDB;

-- La aplicación usa PBKDF2WithHmacSHA256 (65 536 iteraciones, sal de 16 bytes).
-- Sólo se persiste material derivado; nunca la llave en texto claro.
CREATE TABLE IF NOT EXISTS credenciales_hotel (
    hotel_codigo VARCHAR(80) NOT NULL,
    sal VARBINARY(16) NOT NULL,
    hash_clave VARBINARY(32) NOT NULL,
    algoritmo VARCHAR(40) NOT NULL DEFAULT 'PBKDF2WithHmacSHA256',
    iteraciones INT UNSIGNED NOT NULL DEFAULT 65536,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    requiere_cambio BOOLEAN NOT NULL DEFAULT TRUE,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_credenciales_hotel PRIMARY KEY (hotel_codigo),
    CONSTRAINT fk_credenciales_hotel_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_credenciales_hotel_iteraciones CHECK (iteraciones >= 10000),
    CONSTRAINT chk_credenciales_hotel_version CHECK (version >= 1)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS secciones_tarifarias (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hotel_codigo VARCHAR(80) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre VARCHAR(40) NOT NULL,
    color_hex CHAR(7) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    precio_seis_horas DECIMAL(10, 2) UNSIGNED NOT NULL,
    precio_dia_completo DECIMAL(10, 2) UNSIGNED NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_secciones_tarifarias PRIMARY KEY (id),
    CONSTRAINT uq_secciones_id_hotel UNIQUE (id, hotel_codigo),
    CONSTRAINT uq_secciones_hotel_codigo UNIQUE (hotel_codigo, codigo),
    CONSTRAINT uq_secciones_hotel_nombre UNIQUE (hotel_codigo, nombre),
    CONSTRAINT uq_secciones_hotel_color UNIQUE (hotel_codigo, color_hex),
    CONSTRAINT fk_secciones_tarifarias_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_secciones_codigo CHECK (CHAR_LENGTH(TRIM(codigo)) BETWEEN 1 AND 60),
    CONSTRAINT chk_secciones_nombre CHECK (CHAR_LENGTH(TRIM(nombre)) BETWEEN 1 AND 40),
    CONSTRAINT chk_secciones_color CHECK (color_hex REGEXP '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT chk_secciones_precio_seis
        CHECK (precio_seis_horas BETWEEN 1.00 AND 10000.00),
    CONSTRAINT chk_secciones_precio_dia
        CHECK (precio_dia_completo BETWEEN 1.00 AND 10000.00)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS habitaciones (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hotel_codigo VARCHAR(80) NOT NULL,
    numero SMALLINT UNSIGNED NOT NULL,
    seccion_tarifaria_id BIGINT UNSIGNED NOT NULL,
    estado ENUM(
        'DISPONIBLE',
        'OCUPADA',
        'LIMPIEZA_PENDIENTE',
        'EN_LIMPIEZA',
        'MANTENIMIENTO'
    ) NOT NULL DEFAULT 'DISPONIBLE',
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_habitaciones PRIMARY KEY (id),
    CONSTRAINT uq_habitaciones_hotel_numero UNIQUE (hotel_codigo, numero),
    CONSTRAINT fk_habitaciones_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_habitaciones_seccion_hotel
        FOREIGN KEY (seccion_tarifaria_id, hotel_codigo)
        REFERENCES secciones_tarifarias (id, hotel_codigo)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_habitaciones_numero CHECK (numero BETWEEN 1 AND 50),
    INDEX ix_habitaciones_hotel_estado (hotel_codigo, estado)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS areas_comunes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hotel_codigo VARCHAR(80) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(300) NOT NULL DEFAULT '',
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_areas_comunes PRIMARY KEY (id),
    CONSTRAINT uq_areas_hotel_codigo UNIQUE (hotel_codigo, codigo),
    CONSTRAINT uq_areas_hotel_nombre UNIQUE (hotel_codigo, nombre),
    CONSTRAINT fk_areas_comunes_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_areas_codigo CHECK (CHAR_LENGTH(TRIM(codigo)) BETWEEN 1 AND 60),
    CONSTRAINT chk_areas_nombre CHECK (CHAR_LENGTH(TRIM(nombre)) BETWEEN 2 AND 80),
    INDEX ix_areas_hotel_activa (hotel_codigo, activa)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS huespedes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre_completo VARCHAR(100) NOT NULL,
    tipo_documento ENUM('DNI', 'CARNET_EXTRANJERIA', 'PASAPORTE') NOT NULL,
    numero_documento VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_huespedes PRIMARY KEY (id),
    CONSTRAINT uq_huespedes_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT chk_huespedes_nombre
        CHECK (CHAR_LENGTH(TRIM(nombre_completo)) BETWEEN 2 AND 100),
    CONSTRAINT chk_huespedes_documento CHECK (
        (tipo_documento = 'DNI' AND numero_documento REGEXP '^[0-9]{8}$')
        OR (tipo_documento = 'CARNET_EXTRANJERIA'
            AND numero_documento REGEXP '^[A-Z0-9]{9,12}$')
        OR (tipo_documento = 'PASAPORTE'
            AND numero_documento REGEXP '^[A-Z0-9]{6,12}$')
    ),
    INDEX ix_huespedes_nombre (nombre_completo)
) ENGINE = InnoDB;

-- Una estancia pertenece exactamente a una habitación o a un área común.
-- Los importes y el color son instantáneas históricas, no datos duplicados del
-- catálogo actual. habitacion_activa_id hace cumplir una sola estancia activa
-- por habitación mediante un índice UNIQUE que admite múltiples NULL.
CREATE TABLE IF NOT EXISTS estancias (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    habitacion_id BIGINT UNSIGNED NULL,
    area_comun_id BIGINT UNSIGNED NULL,
    tipo_servicio ENUM('SEIS_HORAS', 'DIA_COMPLETO') NULL,
    check_in DATETIME NOT NULL,
    check_out DATETIME NOT NULL,
    tarifa_aplicada DECIMAL(10, 2) UNSIGNED NULL,
    horas_adicionales SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    monto_adicional DECIMAL(10, 2) UNSIGNED NOT NULL DEFAULT 0.00,
    color_historico CHAR(7) CHARACTER SET ascii COLLATE ascii_general_ci NULL,
    estado ENUM('ACTIVA', 'FINALIZADA') NOT NULL DEFAULT 'ACTIVA',
    habitacion_activa_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN estado = 'ACTIVA' THEN habitacion_id ELSE NULL END
    ) STORED,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_estancias PRIMARY KEY (id),
    CONSTRAINT uq_estancias_habitacion_checkin UNIQUE (habitacion_id, check_in),
    CONSTRAINT uq_estancias_area_checkin UNIQUE (area_comun_id, check_in),
    CONSTRAINT uq_estancias_habitacion_activa UNIQUE (habitacion_activa_id),
    CONSTRAINT fk_estancias_habitacion FOREIGN KEY (habitacion_id)
        REFERENCES habitaciones (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_estancias_area FOREIGN KEY (area_comun_id)
        REFERENCES areas_comunes (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_estancias_ubicacion_exclusiva CHECK (
        (habitacion_id IS NOT NULL AND area_comun_id IS NULL)
        OR (habitacion_id IS NULL AND area_comun_id IS NOT NULL)
    ),
    CONSTRAINT chk_estancias_horario CHECK (check_out >= check_in),
    CONSTRAINT chk_estancias_color CHECK (
        color_historico IS NULL OR color_historico REGEXP '^#[0-9A-Fa-f]{6}$'
    ),
    CONSTRAINT chk_estancias_datos_por_origen CHECK (
        (habitacion_id IS NOT NULL
            AND tipo_servicio IS NOT NULL
            AND tarifa_aplicada BETWEEN 1.00 AND 10000.00
            AND color_historico IS NOT NULL)
        OR (area_comun_id IS NOT NULL
            AND tipo_servicio IS NULL
            AND tarifa_aplicada IS NULL
            AND horas_adicionales = 0
            AND monto_adicional = 0.00
            AND color_historico IS NULL)
    ),
    CONSTRAINT chk_estancias_monto_adicional CHECK (monto_adicional <= 10000.00),
    INDEX ix_estancias_habitacion_estado_fecha (habitacion_id, estado, check_in DESC),
    INDEX ix_estancias_area_fecha (area_comun_id, check_in DESC),
    INDEX ix_estancias_periodo (check_in, check_out)
) ENGINE = InnoDB;

-- Resuelve la relación N:M entre huéspedes y estancias y conserva el orden
-- capturado en el formulario. El dominio 1..8 también limita la ocupación.
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

-- Existe como máximo un pago consolidado por estancia; su eliminación vuelve
-- a abrir el pago en el comportamiento actual de Gestera.
CREATE TABLE IF NOT EXISTS pagos_estancia (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estancia_id BIGINT UNSIGNED NOT NULL,
    pagado_en DATETIME NOT NULL,
    monto DECIMAL(10, 2) UNSIGNED NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_pagos_estancia PRIMARY KEY (id),
    CONSTRAINT uq_pagos_estancia_estancia UNIQUE (estancia_id),
    CONSTRAINT fk_pagos_estancia_estancia FOREIGN KEY (estancia_id)
        REFERENCES estancias (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_pagos_estancia_monto CHECK (monto > 0),
    INDEX ix_pagos_estancia_fecha (pagado_en DESC)
) ENGINE = InnoDB;

-- El catálogo es independiente por hotel, igual que catalogosPorHotel en el
-- servicio Java. Las claves compuestas permiten nombres/códigos repetidos en
-- hoteles distintos sin introducir dependencias transitivas.
CREATE TABLE IF NOT EXISTS catalogos_servicio (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hotel_codigo VARCHAR(80) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_catalogos_servicio PRIMARY KEY (id),
    CONSTRAINT uq_catalogos_servicio_hotel UNIQUE (hotel_codigo),
    CONSTRAINT fk_catalogos_servicio_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS categorias_servicio (
    catalogo_id BIGINT UNSIGNED NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_categorias_servicio PRIMARY KEY (catalogo_id, codigo),
    CONSTRAINT uq_categorias_servicio_nombre UNIQUE (catalogo_id, nombre),
    CONSTRAINT fk_categorias_servicio_catalogo FOREIGN KEY (catalogo_id)
        REFERENCES catalogos_servicio (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_categorias_servicio_codigo
        CHECK (CHAR_LENGTH(TRIM(codigo)) BETWEEN 1 AND 60),
    CONSTRAINT chk_categorias_servicio_nombre
        CHECK (CHAR_LENGTH(TRIM(nombre)) BETWEEN 1 AND 60)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS productos_servicio (
    catalogo_id BIGINT UNSIGNED NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    categoria_codigo VARCHAR(60) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    precio DECIMAL(10, 2) UNSIGNED NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_productos_servicio PRIMARY KEY (catalogo_id, codigo),
    CONSTRAINT uq_productos_servicio_nombre UNIQUE (catalogo_id, nombre),
    CONSTRAINT fk_productos_servicio_categoria
        FOREIGN KEY (catalogo_id, categoria_codigo)
        REFERENCES categorias_servicio (catalogo_id, codigo)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_productos_servicio_codigo
        CHECK (CHAR_LENGTH(TRIM(codigo)) BETWEEN 1 AND 60),
    CONSTRAINT chk_productos_servicio_nombre
        CHECK (CHAR_LENGTH(TRIM(nombre)) BETWEEN 1 AND 120),
    CONSTRAINT chk_productos_servicio_precio CHECK (precio BETWEEN 0.01 AND 10000.00),
    INDEX ix_productos_servicio_categoria (catalogo_id, categoria_codigo)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS pedidos_servicio (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estancia_id BIGINT UNSIGNED NOT NULL,
    estado ENUM('PENDIENTE', 'EN_PREPARACION', 'ENTREGADO', 'CANCELADO')
        NOT NULL DEFAULT 'PENDIENTE',
    observaciones VARCHAR(300) NOT NULL DEFAULT '',
    creado_en DATETIME NOT NULL,
    actualizado_en DATETIME NOT NULL,
    CONSTRAINT pk_pedidos_servicio PRIMARY KEY (id),
    CONSTRAINT fk_pedidos_servicio_estancia FOREIGN KEY (estancia_id)
        REFERENCES estancias (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX ix_pedidos_servicio_estancia_estado_fecha
        (estancia_id, estado, creado_en DESC),
    INDEX ix_pedidos_servicio_estado_actualizacion
        (estado, actualizado_en DESC)
) ENGINE = InnoDB;

-- Los campos *_snapshot son una excepción deliberada a la referencia viva:
-- preservan nombre/código/precio aunque el producto cambie o sea eliminado.
CREATE TABLE IF NOT EXISTS pedido_servicio_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    producto_catalogo_id BIGINT UNSIGNED NULL,
    producto_codigo_fk VARCHAR(60) NULL,
    producto_codigo_snapshot VARCHAR(60) NOT NULL,
    producto_nombre_snapshot VARCHAR(120) NOT NULL,
    precio_unitario_snapshot DECIMAL(10, 2) UNSIGNED NOT NULL,
    cantidad TINYINT UNSIGNED NOT NULL,
    CONSTRAINT pk_pedido_servicio_items PRIMARY KEY (id),
    CONSTRAINT fk_pedido_servicio_items_pedido FOREIGN KEY (pedido_id)
        REFERENCES pedidos_servicio (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_pedido_servicio_items_producto
        FOREIGN KEY (producto_catalogo_id, producto_codigo_fk)
        REFERENCES productos_servicio (catalogo_id, codigo)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_pedido_servicio_items_precio
        CHECK (precio_unitario_snapshot BETWEEN 0.01 AND 10000.00),
    CONSTRAINT chk_pedido_servicio_items_cantidad CHECK (cantidad BETWEEN 1 AND 10),
    INDEX ix_pedido_servicio_items_pedido (pedido_id),
    INDEX ix_pedido_servicio_items_producto
        (producto_catalogo_id, producto_codigo_fk)
) ENGINE = InnoDB;

-- Reglas que requieren consultar otra fila y no pueden expresarse con CHECK.
DELIMITER $$

DROP TRIGGER IF EXISTS trg_pagos_estancia_solo_habitacion$$
CREATE TRIGGER trg_pagos_estancia_solo_habitacion
BEFORE INSERT ON pagos_estancia
FOR EACH ROW
BEGIN
    DECLARE estancia_habitacion_valida INT DEFAULT 0;
    SELECT COUNT(*) INTO estancia_habitacion_valida
      FROM estancias
     WHERE id = NEW.estancia_id
       AND habitacion_id IS NOT NULL;
    IF estancia_habitacion_valida <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo una estancia de habitación puede recibir pagos';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_pagos_estancia_actualizacion_solo_habitacion$$
CREATE TRIGGER trg_pagos_estancia_actualizacion_solo_habitacion
BEFORE UPDATE ON pagos_estancia
FOR EACH ROW
BEGIN
    DECLARE estancia_habitacion_valida INT DEFAULT 0;
    SELECT COUNT(*) INTO estancia_habitacion_valida
      FROM estancias
     WHERE id = NEW.estancia_id
       AND habitacion_id IS NOT NULL;
    IF estancia_habitacion_valida <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Sólo una estancia de habitación puede recibir pagos';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_pedidos_servicio_estancia_activa$$
CREATE TRIGGER trg_pedidos_servicio_estancia_activa
BEFORE INSERT ON pedidos_servicio
FOR EACH ROW
BEGIN
    DECLARE estancia_ocupada_valida INT DEFAULT 0;
    SELECT COUNT(*) INTO estancia_ocupada_valida
      FROM estancias e
      JOIN habitaciones h ON h.id = e.habitacion_id
     WHERE e.id = NEW.estancia_id
       AND e.estado = 'ACTIVA'
       AND h.estado = 'OCUPADA';
    IF estancia_ocupada_valida <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El pedido requiere una estancia activa en habitación ocupada';
    END IF;
END$$

DELIMITER ;

