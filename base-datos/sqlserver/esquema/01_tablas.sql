-- Modelo operativo de 14 tablas, SQL Server. Los estados son dominios CHECK.
-- Las FK evitan cascadas multiples; las bajas de habitaciones son logicas.
CREATE TABLE dbo.hoteles (
    codigo NVARCHAR(80) NOT NULL CONSTRAINT v2_pk_hoteles PRIMARY KEY,
    nombre NVARCHAR(150) COLLATE Latin1_General_100_CI_AS_SC NOT NULL CONSTRAINT v2_uq_hoteles_nombre UNIQUE,
    ubicacion NVARCHAR(150) NOT NULL,
    iniciales CHAR(2) NOT NULL,
    activo BIT NOT NULL CONSTRAINT v2_df_hoteles_activo DEFAULT 1,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_hoteles_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_hoteles_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_ck_hoteles_codigo CHECK (LEN(LTRIM(RTRIM(codigo))) BETWEEN 1 AND 80),
    CONSTRAINT v2_ck_hoteles_nombre CHECK (LEN(LTRIM(RTRIM(nombre))) BETWEEN 2 AND 150),
    CONSTRAINT v2_ck_hoteles_ubicacion CHECK (LEN(LTRIM(RTRIM(ubicacion))) BETWEEN 2 AND 150),
    CONSTRAINT v2_ck_hoteles_iniciales CHECK (iniciales COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Za-z0-9]%' AND LEN(iniciales)=2)
);
CREATE TABLE dbo.credenciales_hotel (
    hotel_codigo NVARCHAR(80) NOT NULL CONSTRAINT v2_pk_credenciales PRIMARY KEY,
    sal VARBINARY(16) NOT NULL,
    hash_clave VARBINARY(32) NOT NULL,
    algoritmo VARCHAR(40) NOT NULL CONSTRAINT v2_df_credenciales_algoritmo DEFAULT 'PBKDF2WithHmacSHA256',
    iteraciones INT NOT NULL CONSTRAINT v2_df_credenciales_iteraciones DEFAULT 65536,
    version BIGINT NOT NULL CONSTRAINT v2_df_credenciales_version DEFAULT 1,
    requiere_cambio BIT NOT NULL CONSTRAINT v2_df_credenciales_cambio DEFAULT 1,
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_credenciales_fecha DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_fk_credenciales_hotel FOREIGN KEY(hotel_codigo) REFERENCES dbo.hoteles(codigo),
    CONSTRAINT v2_ck_credenciales_material CHECK (DATALENGTH(sal)=16 AND DATALENGTH(hash_clave)=32),
    CONSTRAINT v2_ck_credenciales_algoritmo CHECK (algoritmo='PBKDF2WithHmacSHA256' AND iteraciones BETWEEN 10000 AND 10000000 AND version>=1)
);
CREATE TABLE dbo.secciones_tarifarias (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_secciones PRIMARY KEY,
    hotel_codigo NVARCHAR(80) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre NVARCHAR(40) COLLATE Latin1_General_100_CI_AS_SC NOT NULL,
    color_hex CHAR(7) COLLATE Latin1_General_100_BIN2 NOT NULL,
    precio_seis_horas DECIMAL(10,2) NOT NULL,
    precio_dia_completo DECIMAL(10,2) NOT NULL,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_secciones_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_secciones_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_uq_secciones_id_hotel UNIQUE(id,hotel_codigo),
    CONSTRAINT v2_uq_secciones_codigo UNIQUE(hotel_codigo,codigo),
    CONSTRAINT v2_uq_secciones_nombre UNIQUE(hotel_codigo,nombre),
    CONSTRAINT v2_uq_secciones_color UNIQUE(hotel_codigo,color_hex),
    CONSTRAINT v2_fk_secciones_hotel FOREIGN KEY(hotel_codigo) REFERENCES dbo.hoteles(codigo),
    CONSTRAINT v2_ck_secciones_nombre CHECK (LEN(LTRIM(RTRIM(nombre))) BETWEEN 1 AND 40 AND LEN(LTRIM(RTRIM(codigo))) BETWEEN 1 AND 60),
    CONSTRAINT v2_ck_secciones_precios CHECK (precio_seis_horas BETWEEN 1 AND 10000 AND precio_dia_completo BETWEEN 1 AND 10000),
    CONSTRAINT v2_ck_secciones_color CHECK (LEFT(color_hex,1)='#' AND SUBSTRING(color_hex,2,6) NOT LIKE '%[^0-9A-Fa-f]%' AND LEN(color_hex)=7)
);
CREATE TABLE dbo.habitaciones (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_habitaciones PRIMARY KEY,
    hotel_codigo NVARCHAR(80) NOT NULL,
    numero SMALLINT NOT NULL,
    seccion_tarifaria_id BIGINT NOT NULL,
    estado VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT v2_df_habitaciones_estado DEFAULT 'DISPONIBLE',
    tipo_servicio_preferido VARCHAR(20) NOT NULL CONSTRAINT v2_df_habitaciones_servicio DEFAULT 'SEIS_HORAS',
    activa BIT NOT NULL CONSTRAINT v2_df_habitaciones_activa DEFAULT 1,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_habitaciones_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_habitaciones_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_uq_habitaciones_numero UNIQUE(hotel_codigo,numero),
    CONSTRAINT v2_fk_habitaciones_hotel FOREIGN KEY(hotel_codigo) REFERENCES dbo.hoteles(codigo),
    CONSTRAINT v2_fk_habitaciones_seccion FOREIGN KEY(seccion_tarifaria_id,hotel_codigo) REFERENCES dbo.secciones_tarifarias(id,hotel_codigo),
    CONSTRAINT v2_ck_habitaciones_numero CHECK (numero BETWEEN 1 AND 50),
    CONSTRAINT v2_ck_habitaciones_estado CHECK (estado IN('DISPONIBLE','OCUPADA','LIMPIEZA_PENDIENTE','EN_LIMPIEZA','MANTENIMIENTO') AND DATALENGTH(estado)=LEN(estado)),
    CONSTRAINT v2_ck_habitaciones_servicio CHECK (tipo_servicio_preferido IN('SEIS_HORAS','DIA_COMPLETO') AND DATALENGTH(tipo_servicio_preferido)=LEN(tipo_servicio_preferido)),
    CONSTRAINT v2_ck_habitaciones_baja CHECK (activa=1 OR estado='DISPONIBLE')
);
CREATE INDEX v2_ix_habitaciones_estado ON dbo.habitaciones(hotel_codigo,activa,estado);
CREATE TABLE dbo.areas_comunes (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_areas PRIMARY KEY,
    hotel_codigo NVARCHAR(80) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre NVARCHAR(80) COLLATE Latin1_General_100_CI_AS_SC NOT NULL,
    descripcion NVARCHAR(300) NOT NULL CONSTRAINT v2_df_areas_descripcion DEFAULT N'',
    activa BIT NOT NULL CONSTRAINT v2_df_areas_activa DEFAULT 1,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_areas_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_areas_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_uq_areas_codigo UNIQUE(hotel_codigo,codigo),
    CONSTRAINT v2_uq_areas_nombre UNIQUE(hotel_codigo,nombre),
    CONSTRAINT v2_fk_areas_hotel FOREIGN KEY(hotel_codigo) REFERENCES dbo.hoteles(codigo),
    CONSTRAINT v2_ck_areas_texto CHECK (LEN(LTRIM(RTRIM(nombre))) BETWEEN 2 AND 80 AND LEN(LTRIM(RTRIM(codigo))) BETWEEN 1 AND 60)
);
CREATE TABLE dbo.huespedes (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_huespedes PRIMARY KEY,
    nombre_completo NVARCHAR(100) NOT NULL,
    tipo_documento VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL,
    numero_documento VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_huespedes_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_huespedes_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_uq_huespedes_documento UNIQUE(tipo_documento,numero_documento),
    CONSTRAINT v2_ck_huespedes_nombre CHECK (LEN(LTRIM(RTRIM(nombre_completo))) BETWEEN 2 AND 100),
    CONSTRAINT v2_ck_huespedes_documento CHECK (
        (tipo_documento='DNI' AND DATALENGTH(tipo_documento)=3 AND DATALENGTH(numero_documento)=8 AND numero_documento NOT LIKE '%[^0-9]%')
        OR (tipo_documento='CARNET_EXTRANJERIA' AND DATALENGTH(tipo_documento)=18 AND DATALENGTH(numero_documento) BETWEEN 9 AND 12 AND numero_documento NOT LIKE '%[^A-Z0-9]%')
        OR (tipo_documento='PASAPORTE' AND DATALENGTH(tipo_documento)=9 AND DATALENGTH(numero_documento) BETWEEN 6 AND 12 AND numero_documento NOT LIKE '%[^A-Z0-9]%'))
);
CREATE INDEX v2_ix_huespedes_nombre ON dbo.huespedes(nombre_completo);
CREATE TABLE dbo.estancias (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_estancias PRIMARY KEY,
    habitacion_id BIGINT NULL,
    area_comun_id BIGINT NULL,
    tipo_servicio VARCHAR(20) COLLATE Latin1_General_100_BIN2 NULL,
    check_in DATETIME2(0) NOT NULL,
    check_out DATETIME2(0) NOT NULL,
    tarifa_aplicada DECIMAL(10,2) NULL,
    horas_adicionales INT NOT NULL CONSTRAINT v2_df_estancias_horas DEFAULT 0,
    monto_adicional DECIMAL(12,2) NOT NULL CONSTRAINT v2_df_estancias_adicional DEFAULT 0,
    color_historico CHAR(7) COLLATE Latin1_General_100_BIN2 NULL,
    estado VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT v2_df_estancias_estado DEFAULT 'ACTIVA',
    origen VARCHAR(10) NOT NULL CONSTRAINT v2_df_estancias_origen DEFAULT 'GESTERA',
    habitacion_activa_id AS (CASE WHEN estado='ACTIVA' THEN habitacion_id ELSE NULL END) PERSISTED,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_estancias_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_estancias_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_fk_estancias_habitacion FOREIGN KEY(habitacion_id) REFERENCES dbo.habitaciones(id),
    CONSTRAINT v2_fk_estancias_area FOREIGN KEY(area_comun_id) REFERENCES dbo.areas_comunes(id),
    CONSTRAINT v2_ck_estancias_ubicacion CHECK ((habitacion_id IS NOT NULL AND area_comun_id IS NULL) OR (habitacion_id IS NULL AND area_comun_id IS NOT NULL)),
    CONSTRAINT v2_ck_estancias_estado CHECK (estado IN('ACTIVA','FINALIZADA') AND origen IN('GESTERA','IMPORTADA') AND DATALENGTH(estado)=LEN(estado) AND DATALENGTH(origen)=LEN(origen)),
    CONSTRAINT v2_ck_estancias_horario CHECK (check_out>=check_in),
    CONSTRAINT v2_ck_estancias_importe CHECK (horas_adicionales BETWEEN 0 AND 65535 AND monto_adicional>=0),
    CONSTRAINT v2_ck_estancias_color CHECK (color_historico IS NULL OR (LEFT(color_historico,1)='#' AND SUBSTRING(color_historico,2,6) NOT LIKE '%[^0-9A-Fa-f]%' AND LEN(color_historico)=7)),
    CONSTRAINT v2_ck_estancias_datos CHECK (
        (habitacion_id IS NOT NULL AND color_historico IS NOT NULL AND (
            (origen='GESTERA' AND tipo_servicio IS NOT NULL AND tipo_servicio IN('SEIS_HORAS','DIA_COMPLETO') AND DATALENGTH(tipo_servicio)=LEN(tipo_servicio)
             AND tarifa_aplicada IS NOT NULL AND tarifa_aplicada BETWEEN 1 AND 10000)
            OR (origen='IMPORTADA' AND estado='FINALIZADA' AND tipo_servicio IS NULL AND tarifa_aplicada IS NULL)))
        OR (area_comun_id IS NOT NULL AND tipo_servicio IS NULL AND tarifa_aplicada IS NULL
            AND horas_adicionales=0 AND monto_adicional=0 AND color_historico IS NULL))
);
-- SQL Server considera NULL en UNIQUE: se usan indices filtrados para ambas ubicaciones.
CREATE UNIQUE INDEX v2_uq_estancias_habitacion_fecha ON dbo.estancias(habitacion_id,check_in) WHERE habitacion_id IS NOT NULL;
CREATE UNIQUE INDEX v2_uq_estancias_area_fecha ON dbo.estancias(area_comun_id,check_in) WHERE area_comun_id IS NOT NULL;
CREATE UNIQUE INDEX v2_uq_estancias_activa ON dbo.estancias(habitacion_id) WHERE habitacion_id IS NOT NULL AND estado='ACTIVA';
CREATE INDEX v2_ix_estancias_periodo ON dbo.estancias(check_in,check_out);
CREATE TABLE dbo.estancia_huespedes (
    estancia_id BIGINT NOT NULL,
    huesped_id BIGINT NOT NULL,
    orden TINYINT NOT NULL,
    CONSTRAINT v2_pk_estancia_huespedes PRIMARY KEY(estancia_id,huesped_id),
    CONSTRAINT v2_uq_estancia_huespedes_orden UNIQUE(estancia_id,orden),
    CONSTRAINT v2_fk_eh_estancia FOREIGN KEY(estancia_id) REFERENCES dbo.estancias(id) ON DELETE CASCADE,
    CONSTRAINT v2_fk_eh_huesped FOREIGN KEY(huesped_id) REFERENCES dbo.huespedes(id),
    CONSTRAINT v2_ck_eh_orden CHECK (orden BETWEEN 1 AND 8)
);
CREATE INDEX v2_ix_eh_huesped ON dbo.estancia_huespedes(huesped_id);
CREATE TABLE dbo.pagos_estancia (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_pagos PRIMARY KEY,
    estancia_id BIGINT NOT NULL CONSTRAINT v2_uq_pagos_estancia UNIQUE,
    pagado_en DATETIME2(7) NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_pagos_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_pagos_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_fk_pagos_estancia FOREIGN KEY(estancia_id) REFERENCES dbo.estancias(id),
    CONSTRAINT v2_ck_pagos_monto CHECK (monto>0)
);
CREATE INDEX v2_ix_pagos_fecha ON dbo.pagos_estancia(pagado_en DESC);
CREATE TABLE dbo.catalogos_servicio (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_catalogos PRIMARY KEY,
    hotel_codigo NVARCHAR(80) NOT NULL CONSTRAINT v2_uq_catalogos_hotel UNIQUE,
    version BIGINT NOT NULL CONSTRAINT v2_df_catalogos_version DEFAULT 1,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_catalogos_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_catalogos_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_fk_catalogos_hotel FOREIGN KEY(hotel_codigo) REFERENCES dbo.hoteles(codigo),
    CONSTRAINT v2_ck_catalogos_version CHECK (version>=1)
);
CREATE TABLE dbo.categorias_servicio (
    catalogo_id BIGINT NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre NVARCHAR(60) COLLATE Latin1_General_100_CI_AS_SC NOT NULL,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_categorias_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_categorias_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_pk_categorias PRIMARY KEY(catalogo_id,codigo),
    CONSTRAINT v2_uq_categorias_nombre UNIQUE(catalogo_id,nombre),
    CONSTRAINT v2_fk_categorias_catalogo FOREIGN KEY(catalogo_id) REFERENCES dbo.catalogos_servicio(id),
    CONSTRAINT v2_ck_categorias_texto CHECK (LEN(LTRIM(RTRIM(nombre))) BETWEEN 1 AND 60 AND LEN(LTRIM(RTRIM(codigo))) BETWEEN 1 AND 60)
);
CREATE TABLE dbo.productos_servicio (
    catalogo_id BIGINT NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    categoria_codigo VARCHAR(60) NOT NULL,
    nombre NVARCHAR(120) COLLATE Latin1_General_100_CI_AS_SC NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_productos_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_productos_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_pk_productos PRIMARY KEY(catalogo_id,codigo),
    CONSTRAINT v2_uq_productos_nombre UNIQUE(catalogo_id,nombre),
    CONSTRAINT v2_fk_productos_categoria FOREIGN KEY(catalogo_id,categoria_codigo) REFERENCES dbo.categorias_servicio(catalogo_id,codigo),
    CONSTRAINT v2_ck_productos_texto CHECK (LEN(LTRIM(RTRIM(nombre))) BETWEEN 1 AND 120 AND LEN(LTRIM(RTRIM(codigo))) BETWEEN 1 AND 60),
    CONSTRAINT v2_ck_productos_precio CHECK (precio BETWEEN 0.01 AND 10000)
);
CREATE TABLE dbo.pedidos_servicio (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_pedidos PRIMARY KEY,
    estancia_id BIGINT NOT NULL,
    estado VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT v2_df_pedidos_estado DEFAULT 'PENDIENTE',
    observaciones NVARCHAR(300) NOT NULL CONSTRAINT v2_df_pedidos_observaciones DEFAULT N'',
    creado_en DATETIME2(7) NOT NULL,
    actualizado_en DATETIME2(7) NOT NULL,
    CONSTRAINT v2_fk_pedidos_estancia FOREIGN KEY(estancia_id) REFERENCES dbo.estancias(id),
    CONSTRAINT v2_ck_pedidos_estado CHECK (estado IN('PENDIENTE','EN_PREPARACION','ENTREGADO','CANCELADO') AND DATALENGTH(estado)=LEN(estado))
);
CREATE INDEX v2_ix_pedidos_cola ON dbo.pedidos_servicio(estado,actualizado_en DESC);
CREATE INDEX v2_ix_pedidos_estancia ON dbo.pedidos_servicio(estancia_id,creado_en DESC);
CREATE TABLE dbo.pedido_servicio_items (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_items PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    producto_catalogo_id BIGINT NULL,
    producto_codigo_fk VARCHAR(60) NULL,
    producto_codigo_snapshot NVARCHAR(60) NOT NULL,
    producto_nombre_snapshot NVARCHAR(120) NOT NULL,
    precio_unitario_snapshot DECIMAL(10,2) NOT NULL,
    cantidad TINYINT NOT NULL,
    CONSTRAINT v2_fk_items_pedido FOREIGN KEY(pedido_id) REFERENCES dbo.pedidos_servicio(id) ON DELETE CASCADE,
    CONSTRAINT v2_fk_items_producto FOREIGN KEY(producto_catalogo_id,producto_codigo_fk) REFERENCES dbo.productos_servicio(catalogo_id,codigo) ON DELETE SET NULL,
    CONSTRAINT v2_ck_items_producto CHECK ((producto_catalogo_id IS NULL AND producto_codigo_fk IS NULL) OR (producto_catalogo_id IS NOT NULL AND producto_codigo_fk IS NOT NULL)),
    CONSTRAINT v2_ck_items_importe CHECK (precio_unitario_snapshot BETWEEN 0.01 AND 10000 AND cantidad BETWEEN 1 AND 10)
);
CREATE INDEX v2_ix_items_pedido ON dbo.pedido_servicio_items(pedido_id);
