-- Pedidos normalizados de servicio al cuarto. Ejecutar después de V001 y V002.
USE gestera;

CREATE TABLE IF NOT EXISTS pedidos_servicio (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    hotel_codigo VARCHAR(80) NOT NULL,
    numero_habitacion SMALLINT UNSIGNED NOT NULL,
    estado ENUM('PENDIENTE', 'EN_PREPARACION', 'ENTREGADO', 'CANCELADO')
        NOT NULL DEFAULT 'PENDIENTE',
    observaciones VARCHAR(300) NOT NULL DEFAULT '',
    creado_en DATETIME NOT NULL,
    actualizado_en DATETIME NOT NULL,
    CONSTRAINT pk_pedidos_servicio PRIMARY KEY (id),
    CONSTRAINT fk_pedidos_servicio_hotel FOREIGN KEY (hotel_codigo)
        REFERENCES hoteles (codigo) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_pedidos_servicio_habitacion
        CHECK (numero_habitacion BETWEEN 1 AND 50),
    INDEX ix_pedidos_servicio_hotel_estado_fecha
        (hotel_codigo, estado, creado_en DESC),
    INDEX ix_pedidos_servicio_hotel_habitacion_fecha
        (hotel_codigo, numero_habitacion, creado_en DESC)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS pedido_servicio_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    producto_codigo VARCHAR(60) NOT NULL,
    producto_nombre VARCHAR(120) NOT NULL,
    precio_unitario DECIMAL(10, 2) UNSIGNED NOT NULL,
    cantidad TINYINT UNSIGNED NOT NULL,
    CONSTRAINT pk_pedido_servicio_items PRIMARY KEY (id),
    CONSTRAINT fk_pedido_servicio_items_pedido FOREIGN KEY (pedido_id)
        REFERENCES pedidos_servicio (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_pedido_servicio_items_precio CHECK (precio_unitario > 0),
    CONSTRAINT chk_pedido_servicio_items_cantidad CHECK (cantidad BETWEEN 1 AND 10),
    INDEX ix_pedido_servicio_items_pedido (pedido_id)
) ENGINE = InnoDB;
