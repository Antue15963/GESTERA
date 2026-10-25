/*
GESTERA - modelo operativo normalizado de 14 tablas para SQL Server 2016+.
Ejecutar TODO en SSMS (F5), sin modo SQLCMD. Fuente: carpeta esquema.
Generado por herramientas/generar-instalador-sqlserver.py; no editar a mano.

Instalacion nueva o migracion transaccional del esquema de 7 tablas.
Antes de migrar: detener Gestera y ejecutar herramientas/respaldar-sqlserver.ps1.
No ejecutar una instancia anterior del backend contra el esquema nuevo.
Las credenciales hoteleras se derivan en Java: no se incluyen llaves en el SQL.
Los datos historicos sin tarifa conocida se conservan como IMPORTADA con NULL.
Se rechaza migrar habitaciones activas de V1 sin datos tarifarios persistidos.
Los cambios de tablas/datos/permisos/version son atomicos. La creacion de la
base queda fuera de la transaccion por requerimiento de SQL Server.
*/
USE [master];
GO
SET NOCOUNT ON;
IF @@TRANCOUNT<>0 THROW 54010,N'Use una ventana sin transacciones abiertas.',1;
SET IMPLICIT_TRANSACTIONS OFF;
IF CONVERT(int,SERVERPROPERTY('ProductMajorVersion'))<13
 THROW 54011,N'Se requiere SQL Server 2016 o posterior.',1;
IF DB_ID(N'gestera') IS NULL EXEC(N'CREATE DATABASE [gestera] COLLATE Latin1_General_100_CI_AI_SC;');
GO
USE [gestera];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_WARNINGS ON;
SET ANSI_PADDING ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME()<>N'gestera' THROW 54012,N'No se selecciono la base esperada.',1;
IF @@TRANCOUNT<>0 THROW 54013,N'Use una ventana sin transacciones abiertas.',1;
SET IMPLICIT_TRANSACTIONS OFF;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @bloqueo int;
 EXEC @bloqueo=sys.sp_getapplock @Resource=N'GESTERA_INSTALACION_SQLSERVER',
  @LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=10000;
 IF @bloqueo<0 THROW 54014,N'Hay otra instalacion en curso.',1;
 DECLARE @version nvarchar(128);
 SELECT @version=CONVERT(nvarchar(128),value) FROM sys.extended_properties
  WHERE class=0 AND name=N'Gestera.Instalador';
 IF @version IS NOT NULL AND @version NOT IN(N'GESTERA_SQLSERVER_V001_V005_1',N'GESTERA_SQLSERVER_14_V2')
  THROW 54015,N'Version de esquema desconocida; se requiere una migracion explicita.',1;
 IF @version IS NULL AND EXISTS(SELECT 1 FROM sys.tables WHERE is_ms_shipped=0 AND NOT (name=N'sysdiagrams' AND schema_id=SCHEMA_ID(N'dbo')))
  THROW 54016,N'La base contiene tablas sin la marca de Gestera. No se modificaron.',1;
 IF @version IS NULL OR @version=N'GESTERA_SQLSERVER_V001_V005_1'
 BEGIN
  DECLARE @migrar bit=CASE WHEN @version=N'GESTERA_SQLSERVER_V001_V005_1' THEN 1 ELSE 0 END;
  IF @migrar=1
  BEGIN
   EXEC sys.sp_rename N'dbo.hoteles',N'__v1_hoteles';
   EXEC sys.sp_rename N'dbo.huespedes',N'__v1_huespedes';
   EXEC sys.sp_rename N'dbo.estancias',N'__v1_estancias';
   EXEC sys.sp_rename N'dbo.estancia_huespedes',N'__v1_estancia_huespedes';
   EXEC sys.sp_rename N'dbo.pedidos_servicio',N'__v1_pedidos_servicio';
   EXEC sys.sp_rename N'dbo.pedido_servicio_items',N'__v1_pedido_servicio_items';
   EXEC sys.sp_rename N'dbo.ingresos_habitacion',N'__v1_ingresos_habitacion';
  END;
EXEC sys.sp_executesql N'-- Modelo operativo de 14 tablas, SQL Server. Los estados son dominios CHECK.
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
    CONSTRAINT v2_ck_hoteles_iniciales CHECK (iniciales COLLATE Latin1_General_100_BIN2 NOT LIKE ''%[^A-Za-z0-9]%'' AND LEN(iniciales)=2)
);
CREATE TABLE dbo.credenciales_hotel (
    hotel_codigo NVARCHAR(80) NOT NULL CONSTRAINT v2_pk_credenciales PRIMARY KEY,
    sal VARBINARY(16) NOT NULL,
    hash_clave VARBINARY(32) NOT NULL,
    algoritmo VARCHAR(40) NOT NULL CONSTRAINT v2_df_credenciales_algoritmo DEFAULT ''PBKDF2WithHmacSHA256'',
    iteraciones INT NOT NULL CONSTRAINT v2_df_credenciales_iteraciones DEFAULT 65536,
    version BIGINT NOT NULL CONSTRAINT v2_df_credenciales_version DEFAULT 1,
    requiere_cambio BIT NOT NULL CONSTRAINT v2_df_credenciales_cambio DEFAULT 1,
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_credenciales_fecha DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_fk_credenciales_hotel FOREIGN KEY(hotel_codigo) REFERENCES dbo.hoteles(codigo),
    CONSTRAINT v2_ck_credenciales_material CHECK (DATALENGTH(sal)=16 AND DATALENGTH(hash_clave)=32),
    CONSTRAINT v2_ck_credenciales_algoritmo CHECK (algoritmo=''PBKDF2WithHmacSHA256'' AND iteraciones BETWEEN 10000 AND 10000000 AND version>=1)
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
    CONSTRAINT v2_ck_secciones_color CHECK (LEFT(color_hex,1)=''#'' AND SUBSTRING(color_hex,2,6) NOT LIKE ''%[^0-9A-Fa-f]%'' AND LEN(color_hex)=7)
);
CREATE TABLE dbo.habitaciones (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_habitaciones PRIMARY KEY,
    hotel_codigo NVARCHAR(80) NOT NULL,
    numero SMALLINT NOT NULL,
    seccion_tarifaria_id BIGINT NOT NULL,
    estado VARCHAR(24) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT v2_df_habitaciones_estado DEFAULT ''DISPONIBLE'',
    tipo_servicio_preferido VARCHAR(20) NOT NULL CONSTRAINT v2_df_habitaciones_servicio DEFAULT ''SEIS_HORAS'',
    activa BIT NOT NULL CONSTRAINT v2_df_habitaciones_activa DEFAULT 1,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_habitaciones_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_habitaciones_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_uq_habitaciones_numero UNIQUE(hotel_codigo,numero),
    CONSTRAINT v2_fk_habitaciones_hotel FOREIGN KEY(hotel_codigo) REFERENCES dbo.hoteles(codigo),
    CONSTRAINT v2_fk_habitaciones_seccion FOREIGN KEY(seccion_tarifaria_id,hotel_codigo) REFERENCES dbo.secciones_tarifarias(id,hotel_codigo),
    CONSTRAINT v2_ck_habitaciones_numero CHECK (numero BETWEEN 1 AND 50),
    CONSTRAINT v2_ck_habitaciones_estado CHECK (estado IN(''DISPONIBLE'',''OCUPADA'',''LIMPIEZA_PENDIENTE'',''EN_LIMPIEZA'',''MANTENIMIENTO'') AND DATALENGTH(estado)=LEN(estado)),
    CONSTRAINT v2_ck_habitaciones_servicio CHECK (tipo_servicio_preferido IN(''SEIS_HORAS'',''DIA_COMPLETO'') AND DATALENGTH(tipo_servicio_preferido)=LEN(tipo_servicio_preferido)),
    CONSTRAINT v2_ck_habitaciones_baja CHECK (activa=1 OR estado=''DISPONIBLE'')
);
CREATE INDEX v2_ix_habitaciones_estado ON dbo.habitaciones(hotel_codigo,activa,estado);
CREATE TABLE dbo.areas_comunes (
    id BIGINT IDENTITY NOT NULL CONSTRAINT v2_pk_areas PRIMARY KEY,
    hotel_codigo NVARCHAR(80) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    nombre NVARCHAR(80) COLLATE Latin1_General_100_CI_AS_SC NOT NULL,
    descripcion NVARCHAR(300) NOT NULL CONSTRAINT v2_df_areas_descripcion DEFAULT N'''',
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
        (tipo_documento=''DNI'' AND DATALENGTH(tipo_documento)=3 AND DATALENGTH(numero_documento)=8 AND numero_documento NOT LIKE ''%[^0-9]%'')
        OR (tipo_documento=''CARNET_EXTRANJERIA'' AND DATALENGTH(tipo_documento)=18 AND DATALENGTH(numero_documento) BETWEEN 9 AND 12 AND numero_documento NOT LIKE ''%[^A-Z0-9]%'')
        OR (tipo_documento=''PASAPORTE'' AND DATALENGTH(tipo_documento)=9 AND DATALENGTH(numero_documento) BETWEEN 6 AND 12 AND numero_documento NOT LIKE ''%[^A-Z0-9]%''))
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
    estado VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT v2_df_estancias_estado DEFAULT ''ACTIVA'',
    origen VARCHAR(10) NOT NULL CONSTRAINT v2_df_estancias_origen DEFAULT ''GESTERA'',
    habitacion_activa_id AS (CASE WHEN estado=''ACTIVA'' THEN habitacion_id ELSE NULL END) PERSISTED,
    creado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_estancias_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en DATETIME2(7) NOT NULL CONSTRAINT v2_df_estancias_actualizado DEFAULT SYSUTCDATETIME(),
    CONSTRAINT v2_fk_estancias_habitacion FOREIGN KEY(habitacion_id) REFERENCES dbo.habitaciones(id),
    CONSTRAINT v2_fk_estancias_area FOREIGN KEY(area_comun_id) REFERENCES dbo.areas_comunes(id),
    CONSTRAINT v2_ck_estancias_ubicacion CHECK ((habitacion_id IS NOT NULL AND area_comun_id IS NULL) OR (habitacion_id IS NULL AND area_comun_id IS NOT NULL)),
    CONSTRAINT v2_ck_estancias_estado CHECK (estado IN(''ACTIVA'',''FINALIZADA'') AND origen IN(''GESTERA'',''IMPORTADA'') AND DATALENGTH(estado)=LEN(estado) AND DATALENGTH(origen)=LEN(origen)),
    CONSTRAINT v2_ck_estancias_horario CHECK (check_out>=check_in),
    CONSTRAINT v2_ck_estancias_importe CHECK (horas_adicionales BETWEEN 0 AND 65535 AND monto_adicional>=0),
    CONSTRAINT v2_ck_estancias_color CHECK (color_historico IS NULL OR (LEFT(color_historico,1)=''#'' AND SUBSTRING(color_historico,2,6) NOT LIKE ''%[^0-9A-Fa-f]%'' AND LEN(color_historico)=7)),
    CONSTRAINT v2_ck_estancias_datos CHECK (
        (habitacion_id IS NOT NULL AND color_historico IS NOT NULL AND (
            (origen=''GESTERA'' AND tipo_servicio IS NOT NULL AND tipo_servicio IN(''SEIS_HORAS'',''DIA_COMPLETO'') AND DATALENGTH(tipo_servicio)=LEN(tipo_servicio)
             AND tarifa_aplicada IS NOT NULL AND tarifa_aplicada BETWEEN 1 AND 10000)
            OR (origen=''IMPORTADA'' AND estado=''FINALIZADA'' AND tipo_servicio IS NULL AND tarifa_aplicada IS NULL)))
        OR (area_comun_id IS NOT NULL AND tipo_servicio IS NULL AND tarifa_aplicada IS NULL
            AND horas_adicionales=0 AND monto_adicional=0 AND color_historico IS NULL))
);
-- SQL Server considera NULL en UNIQUE: se usan indices filtrados para ambas ubicaciones.
CREATE UNIQUE INDEX v2_uq_estancias_habitacion_fecha ON dbo.estancias(habitacion_id,check_in) WHERE habitacion_id IS NOT NULL;
CREATE UNIQUE INDEX v2_uq_estancias_area_fecha ON dbo.estancias(area_comun_id,check_in) WHERE area_comun_id IS NOT NULL;
CREATE UNIQUE INDEX v2_uq_estancias_activa ON dbo.estancias(habitacion_id) WHERE habitacion_id IS NOT NULL AND estado=''ACTIVA'';
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
    estado VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT v2_df_pedidos_estado DEFAULT ''PENDIENTE'',
    observaciones NVARCHAR(300) NOT NULL CONSTRAINT v2_df_pedidos_observaciones DEFAULT N'''',
    creado_en DATETIME2(7) NOT NULL,
    actualizado_en DATETIME2(7) NOT NULL,
    CONSTRAINT v2_fk_pedidos_estancia FOREIGN KEY(estancia_id) REFERENCES dbo.estancias(id),
    CONSTRAINT v2_ck_pedidos_estado CHECK (estado IN(''PENDIENTE'',''EN_PREPARACION'',''ENTREGADO'',''CANCELADO'') AND DATALENGTH(estado)=LEN(estado))
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
';
  IF @migrar=1
  BEGIN
EXEC sys.sp_executesql N'INSERT dbo.hoteles(codigo,nombre,ubicacion,iniciales,activo,creado_en,actualizado_en)
SELECT codigo,nombre,ubicacion,CASE codigo WHEN ''sol-pucusana'' THEN ''EP'' WHEN ''brisa-marina'' THEN ''BM''
 WHEN ''valle-dorado'' THEN ''VD'' WHEN ''costa-serena'' THEN ''CS'' ELSE ''HT'' END,1,creado_en,actualizado_en
FROM dbo.__v1_hoteles;';
  END ELSE BEGIN
EXEC sys.sp_executesql N'INSERT dbo.hoteles(codigo,nombre,ubicacion,iniciales) VALUES
(N''sol-pucusana'',N''Hotel El Sol de Pucusana'',N''Pucusana, Lima'',''EP''),
(N''brisa-marina'',N''Hotel Brisa Marina'',N''Miraflores, Lima'',''BM''),
(N''valle-dorado'',N''Hotel Valle Dorado'',N''Urubamba, Cusco'',''VD''),
(N''costa-serena'',N''Hotel Costa Serena'',N''Máncora, Piura'',''CS'');';
  END;
EXEC sys.sp_executesql N'-- Se ejecuta solo durante la creacion/migracion, no al reiniciar Java.
INSERT dbo.secciones_tarifarias(hotel_codigo,codigo,nombre,color_hex,precio_seis_horas,precio_dia_completo)
SELECT h.codigo,s.codigo,s.nombre,s.color_hex,s.precio6,s.precio24
FROM dbo.hoteles h CROSS JOIN (VALUES
    (''seccion-1'',N''Amarillo'',''#FBBF24'',50.00,90.00),
    (''seccion-2'',N''Celeste'',''#38BDF8'',65.00,120.00),
    (''seccion-3'',N''Violeta'',''#A78BFA'',80.00,150.00)
) s(codigo,nombre,color_hex,precio6,precio24);
INSERT dbo.habitaciones(hotel_codigo,numero,seccion_tarifaria_id)
SELECT h.codigo,n.numero,s.id FROM dbo.hoteles h
CROSS JOIN (VALUES(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12)) n(numero)
JOIN dbo.secciones_tarifarias s ON s.hotel_codigo=h.codigo
 AND s.codigo=CONCAT(''seccion-'',((n.numero-1)/4)%3+1);
INSERT dbo.areas_comunes(hotel_codigo,codigo,nombre,descripcion)
SELECT h.codigo,a.codigo,a.nombre,a.descripcion FROM dbo.hoteles h CROSS JOIN (VALUES
    (''jacuzzi'',N''Jacuzzi'',N''Registra el horario reservado para disfrutar del jacuzzi.''),
    (''sauna'',N''Sauna'',N''Organiza el ingreso y la salida de los visitantes del sauna.''),
    (''area-juegos'',N''Área de juegos'',N''Reserva un horario para compartir en el área de juegos.'')
) a(codigo,nombre,descripcion);
INSERT dbo.catalogos_servicio(hotel_codigo) SELECT codigo FROM dbo.hoteles;
INSERT dbo.categorias_servicio(catalogo_id,codigo,nombre)
SELECT c.id,k.codigo,k.nombre FROM dbo.catalogos_servicio c CROSS JOIN (VALUES
    (''desayunos'',N''Desayunos''),(''comidas'',N''Comidas''),(''bebidas'',N''Bebidas'')
) k(codigo,nombre);
INSERT dbo.productos_servicio(catalogo_id,codigo,categoria_codigo,nombre,precio)
SELECT c.id,p.codigo,p.categoria,p.nombre,p.precio FROM dbo.catalogos_servicio c CROSS JOIN (VALUES
    (''desayuno-continental'',''desayunos'',N''Desayuno continental'',25.00),
    (''club-sandwich'',''comidas'',N''Club sándwich'',32.00),
    (''sopa-del-dia'',''comidas'',N''Sopa del día'',18.00),
    (''cafe-americano'',''bebidas'',N''Café americano'',8.00),
    (''agua-mineral'',''bebidas'',N''Agua mineral'',6.00)
) p(codigo,categoria,nombre,precio);
-- Las credenciales se inicializan desde Java con sal aleatoria y PBKDF2.
-- No se incluyen llaves ni hashes predeterminados en el SQL.
';
  IF @migrar=1 BEGIN
EXEC sys.sp_executesql N'-- Datos historicos: se conservan identificadores, personas, fechas, pedidos e importes.
-- El modelo anterior no guardaba tarifas/servicios historicos: se marcan como
-- IMPORTADA y NULL en vez de inventar un desglose economico.
IF EXISTS(SELECT 1 FROM dbo.__v1_estancias WHERE estado=''ACTIVA'' AND numero_habitacion IS NOT NULL)
    THROW 54101,N''Hay estancias de habitacion activas del modelo anterior. Exporte y cierre su operacion antes de migrar; no se inventaran tarifas.'',1;
IF EXISTS(SELECT 1 FROM dbo.__v1_estancias WHERE numero_habitacion IS NOT NULL
    GROUP BY hotel_codigo,numero_habitacion,check_in HAVING COUNT(*)>1)
    THROW 54102,N''Hay estancias anteriores con identidad de habitacion/entrada duplicada. Reviselas antes de migrar.'',1;

;WITH numeros AS (SELECT TOP(50) ROW_NUMBER() OVER(ORDER BY (SELECT NULL)) AS numero FROM sys.all_objects),
maximos AS (
 SELECT hotel_codigo,MAX(numero_habitacion) AS cantidad FROM (
 SELECT hotel_codigo,numero_habitacion FROM dbo.__v1_estancias
 UNION ALL SELECT hotel_codigo,numero_habitacion FROM dbo.__v1_pedidos_servicio
 UNION ALL SELECT hotel_codigo,numero_habitacion FROM dbo.__v1_ingresos_habitacion) x GROUP BY hotel_codigo
)
INSERT dbo.habitaciones(hotel_codigo,numero,seccion_tarifaria_id)
SELECT m.hotel_codigo,n.numero,s.id FROM maximos m JOIN numeros n ON n.numero<=m.cantidad
JOIN dbo.secciones_tarifarias s ON s.hotel_codigo=m.hotel_codigo AND s.codigo=CONCAT(''seccion-'',((n.numero-1)/4)%3+1)
WHERE NOT EXISTS(SELECT 1 FROM dbo.habitaciones h WHERE h.hotel_codigo=m.hotel_codigo AND h.numero=n.numero);

;WITH areas AS (
 SELECT DISTINCT hotel_codigo,ubicacion FROM dbo.__v1_estancias WHERE numero_habitacion IS NULL
), faltantes AS (
 SELECT x.*,ROW_NUMBER() OVER(ORDER BY x.hotel_codigo,x.ubicacion) AS numero FROM areas x
 WHERE NOT EXISTS(SELECT 1 FROM dbo.areas_comunes a WHERE a.hotel_codigo=x.hotel_codigo AND a.nombre=x.ubicacion COLLATE Latin1_General_100_CI_AS_SC)
)
INSERT dbo.areas_comunes(hotel_codigo,codigo,nombre,descripcion)
SELECT hotel_codigo,CONCAT(''importada-'',numero),ubicacion,N''Área conservada del historial anterior.'' FROM faltantes;

SET IDENTITY_INSERT dbo.huespedes ON;
INSERT dbo.huespedes(id,nombre_completo,tipo_documento,numero_documento,creado_en,actualizado_en)
SELECT id,nombre_completo,UPPER(tipo_documento),numero_documento,creado_en,actualizado_en FROM dbo.__v1_huespedes;
SET IDENTITY_INSERT dbo.huespedes OFF;
SET IDENTITY_INSERT dbo.estancias ON;
INSERT dbo.estancias(id,habitacion_id,area_comun_id,check_in,check_out,color_historico,estado,origen,creado_en,actualizado_en)
SELECT e.id,h.id,a.id,e.check_in,e.check_out,
 CASE WHEN h.id IS NULL THEN NULL ELSE e.color_habitacion END,
 CASE WHEN h.id IS NULL THEN e.estado ELSE ''FINALIZADA'' END,''IMPORTADA'',e.creado_en,e.actualizado_en
FROM dbo.__v1_estancias e
LEFT JOIN dbo.habitaciones h ON h.hotel_codigo=e.hotel_codigo AND h.numero=e.numero_habitacion
LEFT JOIN dbo.areas_comunes a ON e.numero_habitacion IS NULL AND a.hotel_codigo=e.hotel_codigo AND a.nombre=e.ubicacion COLLATE Latin1_General_100_CI_AS_SC;
SET IDENTITY_INSERT dbo.estancias OFF;
INSERT dbo.estancia_huespedes(estancia_id,huesped_id,orden)
SELECT estancia_id,huesped_id,orden FROM dbo.__v1_estancia_huespedes;

-- El modelo anterior permitia registrar el pago antes de crear su estancia.
INSERT dbo.estancias(habitacion_id,check_in,check_out,color_historico,estado,origen)
SELECT h.id,i.check_in,i.check_in,''#000000'',''FINALIZADA'',''IMPORTADA''
FROM dbo.__v1_ingresos_habitacion i JOIN dbo.habitaciones h ON h.hotel_codigo=i.hotel_codigo AND h.numero=i.numero_habitacion
WHERE NOT EXISTS(SELECT 1 FROM dbo.estancias e WHERE e.habitacion_id=h.id AND e.check_in=i.check_in);
SET IDENTITY_INSERT dbo.pagos_estancia ON;
INSERT dbo.pagos_estancia(id,estancia_id,pagado_en,monto)
SELECT i.id,e.id,i.pagado_en,i.monto FROM dbo.__v1_ingresos_habitacion i
JOIN dbo.habitaciones h ON h.hotel_codigo=i.hotel_codigo AND h.numero=i.numero_habitacion
JOIN dbo.estancias e ON e.habitacion_id=h.id AND e.check_in=i.check_in;
SET IDENTITY_INSERT dbo.pagos_estancia OFF;

-- Un pedido sin estancia identificable conserva un hecho importado propio.
INSERT dbo.estancias(habitacion_id,check_in,check_out,color_historico,estado,origen)
SELECT DISTINCT h.id,CONVERT(datetime2(0),p.creado_en),CONVERT(datetime2(0),p.creado_en),''#000000'',''FINALIZADA'',''IMPORTADA''
FROM dbo.__v1_pedidos_servicio p JOIN dbo.habitaciones h ON h.hotel_codigo=p.hotel_codigo AND h.numero=p.numero_habitacion
WHERE NOT EXISTS(SELECT 1 FROM dbo.estancias e WHERE e.habitacion_id=h.id AND e.check_in<=p.creado_en AND e.check_out>=p.creado_en)
 AND NOT EXISTS(SELECT 1 FROM dbo.estancias e WHERE e.habitacion_id=h.id AND e.check_in=CONVERT(datetime2(0),p.creado_en));
SET IDENTITY_INSERT dbo.pedidos_servicio ON;
INSERT dbo.pedidos_servicio(id,estancia_id,estado,observaciones,creado_en,actualizado_en)
SELECT p.id,e.id,p.estado,p.observaciones,p.creado_en,p.actualizado_en
FROM dbo.__v1_pedidos_servicio p JOIN dbo.habitaciones h ON h.hotel_codigo=p.hotel_codigo AND h.numero=p.numero_habitacion
CROSS APPLY(SELECT TOP(1) e.id FROM dbo.estancias e WHERE e.habitacion_id=h.id
 AND ((e.check_in<=p.creado_en AND e.check_out>=p.creado_en) OR e.check_in=CONVERT(datetime2(0),p.creado_en))
 ORDER BY CASE WHEN e.check_in<=p.creado_en AND e.check_out>=p.creado_en THEN 0 ELSE 1 END,e.check_in DESC) e;
SET IDENTITY_INSERT dbo.pedidos_servicio OFF;
SET IDENTITY_INSERT dbo.pedido_servicio_items ON;
INSERT dbo.pedido_servicio_items(id,pedido_id,producto_catalogo_id,producto_codigo_fk,
 producto_codigo_snapshot,producto_nombre_snapshot,precio_unitario_snapshot,cantidad)
SELECT i.id,i.pedido_id,pr.catalogo_id,pr.codigo,i.producto_codigo,i.producto_nombre,i.precio_unitario,i.cantidad
FROM dbo.__v1_pedido_servicio_items i JOIN dbo.__v1_pedidos_servicio p ON p.id=i.pedido_id
JOIN dbo.catalogos_servicio c ON c.hotel_codigo=p.hotel_codigo
LEFT JOIN dbo.productos_servicio pr ON pr.catalogo_id=c.id AND pr.codigo=i.producto_codigo;
SET IDENTITY_INSERT dbo.pedido_servicio_items OFF;

IF (SELECT COUNT_BIG(*) FROM dbo.__v1_huespedes)<>(SELECT COUNT_BIG(*) FROM dbo.huespedes)
 OR (SELECT COUNT_BIG(*) FROM dbo.__v1_estancia_huespedes)<>(SELECT COUNT_BIG(*) FROM dbo.estancia_huespedes)
 OR (SELECT COUNT_BIG(*) FROM dbo.__v1_pedidos_servicio)<>(SELECT COUNT_BIG(*) FROM dbo.pedidos_servicio)
 OR (SELECT COUNT_BIG(*) FROM dbo.__v1_pedido_servicio_items)<>(SELECT COUNT_BIG(*) FROM dbo.pedido_servicio_items)
 OR (SELECT COUNT_BIG(*) FROM dbo.__v1_ingresos_habitacion)<>(SELECT COUNT_BIG(*) FROM dbo.pagos_estancia)
 OR COALESCE((SELECT SUM(monto) FROM dbo.__v1_ingresos_habitacion),0)<>COALESCE((SELECT SUM(monto) FROM dbo.pagos_estancia),0)
 OR EXISTS(SELECT 1 FROM dbo.__v1_estancias vieja LEFT JOIN dbo.estancias nueva ON nueva.id=vieja.id WHERE nueva.id IS NULL)
    THROW 54103,N''La verificacion de registros/importes migrados fallo. Se revierte toda la migracion.'',1;
DROP TABLE dbo.__v1_estancia_huespedes,dbo.__v1_pedido_servicio_items;
DROP TABLE dbo.__v1_pedidos_servicio,dbo.__v1_ingresos_habitacion;
DROP TABLE dbo.__v1_estancias,dbo.__v1_huespedes,dbo.__v1_hoteles;
';
  END;
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_pagos_origen ON dbo.pagos_estancia AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.estancias e ON e.id=i.estancia_id WHERE e.habitacion_id IS NULL)
    THROW 54201,N''Un pago de alojamiento debe pertenecer a una estancia de habitacion.'',1;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_pedidos_origen ON dbo.pedidos_servicio AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.estancias e ON e.id=i.estancia_id WHERE e.habitacion_id IS NULL)
    THROW 54202,N''El servicio al cuarto requiere una estancia de habitacion.'',1;
 IF UPDATE(estancia_id) AND EXISTS(SELECT 1 FROM inserted i
    JOIN dbo.estancias e ON e.id=i.estancia_id JOIN dbo.habitaciones h ON h.id=e.habitacion_id
    WHERE e.estado<>''ACTIVA'' OR h.estado<>''OCUPADA'' OR h.activa=0)
    THROW 54203,N''Un pedido nuevo requiere una estancia activa en una habitacion ocupada.'',1;
 IF UPDATE(estancia_id) AND EXISTS(SELECT 1 FROM inserted i JOIN dbo.estancias e ON e.id=i.estancia_id
    JOIN dbo.habitaciones h ON h.id=e.habitacion_id JOIN dbo.pedido_servicio_items it ON it.pedido_id=i.id
    JOIN dbo.catalogos_servicio c ON c.id=it.producto_catalogo_id WHERE c.hotel_codigo<>h.hotel_codigo)
    THROW 54204,N''Los productos referenciados deben pertenecer al hotel del pedido.'',1;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_items_hotel ON dbo.pedido_servicio_items AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.pedidos_servicio p ON p.id=i.pedido_id
    JOIN dbo.estancias e ON e.id=p.estancia_id JOIN dbo.habitaciones h ON h.id=e.habitacion_id
    JOIN dbo.catalogos_servicio c ON c.id=i.producto_catalogo_id WHERE c.hotel_codigo<>h.hotel_codigo)
    THROW 54205,N''El producto del item pertenece a otro hotel.'',1;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_estancias_origen ON dbo.estancias AFTER UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF UPDATE(habitacion_id) OR UPDATE(area_comun_id)
 BEGIN
    IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id
        WHERE (ISNULL(i.habitacion_id,-1)<>ISNULL(d.habitacion_id,-1)
            OR ISNULL(i.area_comun_id,-1)<>ISNULL(d.area_comun_id,-1))
        AND (EXISTS(SELECT 1 FROM dbo.pagos_estancia p WHERE p.estancia_id=i.id)
            OR EXISTS(SELECT 1 FROM dbo.pedidos_servicio p WHERE p.estancia_id=i.id)))
        THROW 54206,N''No se puede trasladar una estancia que ya tiene pagos o pedidos.'',1;
 END;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_hoteles_auditoria ON dbo.hoteles AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_hoteles_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.hoteles d JOIN inserted i ON d.codigo=i.codigo;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_credenciales_hotel_auditoria ON dbo.credenciales_hotel AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_credenciales_hotel_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.credenciales_hotel d JOIN inserted i ON d.hotel_codigo=i.hotel_codigo;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_secciones_tarifarias_auditoria ON dbo.secciones_tarifarias AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_secciones_tarifarias_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.secciones_tarifarias d JOIN inserted i ON d.id=i.id;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_habitaciones_auditoria ON dbo.habitaciones AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_habitaciones_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.habitaciones d JOIN inserted i ON d.id=i.id;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_areas_comunes_auditoria ON dbo.areas_comunes AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_areas_comunes_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.areas_comunes d JOIN inserted i ON d.id=i.id;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_huespedes_auditoria ON dbo.huespedes AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_huespedes_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.huespedes d JOIN inserted i ON d.id=i.id;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_estancias_auditoria ON dbo.estancias AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_estancias_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.estancias d JOIN inserted i ON d.id=i.id;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_pagos_estancia_auditoria ON dbo.pagos_estancia AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_pagos_estancia_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.pagos_estancia d JOIN inserted i ON d.id=i.id;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_catalogos_servicio_auditoria ON dbo.catalogos_servicio AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_catalogos_servicio_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.catalogos_servicio d JOIN inserted i ON d.id=i.id;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_categorias_servicio_auditoria ON dbo.categorias_servicio AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_categorias_servicio_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.categorias_servicio d JOIN inserted i ON d.catalogo_id=i.catalogo_id AND d.codigo=i.codigo;
END;';
EXEC sys.sp_executesql N'CREATE TRIGGER dbo.v2_trg_productos_servicio_auditoria ON dbo.productos_servicio AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.v2_trg_productos_servicio_auditoria''))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.productos_servicio d JOIN inserted i ON d.catalogo_id=i.catalogo_id AND d.codigo=i.codigo;
END;';
  IF DATABASE_PRINCIPAL_ID(N'gestera_aplicacion') IS NULL
   EXEC(N'CREATE ROLE gestera_aplicacion AUTHORIZATION dbo;');
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.hoteles TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.credenciales_hotel TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.secciones_tarifarias TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.habitaciones TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.areas_comunes TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.huespedes TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.estancias TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.pagos_estancia TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.catalogos_servicio TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.categorias_servicio TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.productos_servicio TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.estancia_huespedes TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.pedidos_servicio TO gestera_aplicacion;
  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.pedido_servicio_items TO gestera_aplicacion;
  IF @version IS NULL
   EXEC sys.sp_addextendedproperty @name=N'Gestera.Instalador',@value=N'GESTERA_SQLSERVER_14_V2';
  ELSE
   EXEC sys.sp_updateextendedproperty @name=N'Gestera.Instalador',@value=N'GESTERA_SQLSERVER_14_V2';
 END;
 DECLARE @tablas TABLE(nombre sysname PRIMARY KEY);
 INSERT @tablas VALUES (N'hoteles'),(N'credenciales_hotel'),(N'secciones_tarifarias'),(N'habitaciones'),(N'areas_comunes'),(N'huespedes'),(N'estancias'),(N'pagos_estancia'),(N'catalogos_servicio'),(N'categorias_servicio'),(N'productos_servicio'),(N'estancia_huespedes'),(N'pedidos_servicio'),(N'pedido_servicio_items');
 IF EXISTS(SELECT 1 FROM @tablas WHERE OBJECT_ID(N'dbo.'+nombre,N'U') IS NULL)
  OR (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND NOT (name=N'sysdiagrams' AND schema_id=SCHEMA_ID(N'dbo')))<>14
  THROW 54017,N'El esquema no contiene exactamente las 14 tablas esperadas.',1;
 IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1)
  OR EXISTS(SELECT 1 FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1)
  THROW 54018,N'Hay restricciones deshabilitadas o sin validar.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name=N'v2_uq_estancias_activa' AND is_disabled=0 AND has_filter=1)
  THROW 54019,N'Falta la restriccion de una estancia activa por habitacion.',1;
 COMMIT TRANSACTION;
 PRINT N'GESTERA: modelo de 14 tablas instalado/verificado correctamente.';
 SELECT t.name AS tabla FROM sys.tables t WHERE is_ms_shipped=0 AND NOT (t.name=N'sysdiagrams' AND t.schema_id=SCHEMA_ID(N'dbo')) ORDER BY t.name;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
