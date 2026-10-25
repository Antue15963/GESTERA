/*
    GESTERA - instalador para Microsoft SQL Server / SQL Server Management Studio
    Contrato: tablas utilizadas por los repositorios Java y migraciones V001..V005.
    Version del instalador: GESTERA_SQLSERVER_V001_V005_1

    EJECUCION
    1. Conectarse en SSMS a SQL Server 2016 o posterior.
    2. Abrir este archivo y ejecutarlo completo (F5), en una ventana nueva.
       No requiere activar el modo SQLCMD. GO es un separador de lotes de SSMS.
    3. La cuenta instaladora necesita CREATE DATABASE si gestera no existe,
       y permisos para crear tablas, restricciones, indices, triggers y roles.

    ALCANCE
    - Crea gestera, las 7 tablas actuales, indices, auditoria y los 4 hoteles.
    - No elimina bases, tablas ni registros existentes. No crea logins/passwords.
    - Una segunda ejecucion verifica la instalacion y conserva sus datos.
    - Rechaza una base con tablas ajenas a esta version del instalador.
    - La creacion de la base queda fuera de la transaccion: SQL Server lo exige.
      El esquema, los datos iniciales, el rol y la marca se instalan atomicamente.
      Si falla esa transaccion, puede quedar creada la base vacia.

    JAVA / TOMCAT
    El backend utiliza Spring Boot, Spring JDBC, HikariCP y el driver Microsoft.
    Instalar esta base antes de iniciar el perfil sqlserver de la aplicacion.
    Seguir README.md de esta carpeta para autenticacion, TCP/IP y certificados.
    Las pantallas existentes usan jdk.httpserver administrado por Spring.

    TIPOS Y HORARIOS
    - NVARCHAR para nombres, ubicaciones y observaciones Unicode.
    - BIGINT IDENTITY para claves que Java obtiene como long.
    - DECIMAL(10,2) para dinero; no FLOAT/MONEY ni conversiones de cadenas.
    - check_in/check_out: DATETIME2(0), horario local America/Lima del backend;
      las entradas de habitaciones se normalizan al minuto en Java.
    - pedidos y pagado_en: DATETIME2(7), horario local America/Lima.
    - creado_en/actualizado_en automaticos: DATETIME2(7), auditoria en UTC.
      TIMESTAMP de SQL Server significa ROWVERSION: no se usa para fechas.

    No sustituye al diseno futuro normalizado de 14 tablas. Conserva la forma
    actual de estancias, pedidos e ingresos para reducir los cambios en Java.
*/

USE [master];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

-- No cerrar ni confirmar una transaccion que pertenezca al operador.
IF @@TRANCOUNT <> 0
    THROW 51000, N'Ejecute el archivo completo en una ventana sin transacciones abiertas.', 1;

SET IMPLICIT_TRANSACTIONS OFF;

IF CONVERT(INT, SERVERPROPERTY('ProductMajorVersion')) < 13
    THROW 51001, N'Este instalador requiere SQL Server 2016 o posterior.', 1;

IF DB_ID(N'gestera') IS NULL
BEGIN
    -- Sin rutas MDF/LDF fijas: se usan los directorios configurados del servidor.
    EXEC(N'CREATE DATABASE [gestera] COLLATE Latin1_General_100_CI_AI_SC;');
END;
GO

USE [gestera];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;

-- SSMS puede continuar con el siguiente lote si un lote anterior falla.
-- Esta comprobacion evita crear objetos en master u otra base por accidente.
IF DB_NAME() <> N'gestera'
    THROW 51002, N'No se pudo seleccionar gestera. Revise el error del lote anterior.', 1;

IF @@TRANCOUNT <> 0
    THROW 51003, N'Hay una transaccion abierta. Use una ventana nueva de SSMS.', 1;

SET IMPLICIT_TRANSACTIONS OFF;

IF CONVERT(INT, SERVERPROPERTY('ProductMajorVersion')) < 13
    THROW 51004, N'Este instalador requiere SQL Server 2016 o posterior.', 1;

IF CONVERT(SYSNAME, DATABASEPROPERTYEX(DB_NAME(), 'Collation')) <> N'Latin1_General_100_CI_AI_SC'
    THROW 51005, N'gestera ya existe con otra intercalacion. Use una base nueva con Latin1_General_100_CI_AI_SC.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    -- Serializa dos instalaciones concurrentes sobre esta misma base.
    DECLARE @resultado_bloqueo INT;
    EXEC @resultado_bloqueo = sys.sp_getapplock
        @Resource = N'GESTERA_INSTALACION_SQLSERVER',
        @LockMode = N'Exclusive',
        @LockOwner = N'Transaction',
        @LockTimeout = 10000;

    IF @resultado_bloqueo < 0
        THROW 51006, N'No se pudo obtener el bloqueo de instalacion. Reintente cuando finalice la otra ejecucion.', 1;

    DECLARE @version_instalada NVARCHAR(128);
    SELECT @version_instalada = CONVERT(NVARCHAR(128), value)
    FROM sys.extended_properties
    WHERE class = 0 AND name = N'Gestera.Instalador';

    IF @version_instalada IS NOT NULL
       AND @version_instalada <> N'GESTERA_SQLSERVER_V001_V005_1'
        THROW 51007, N'La base pertenece a otra version del instalador. Se necesita una migracion explicita.', 1;

    IF @version_instalada IS NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM sys.tables WHERE is_ms_shipped = 0)
            THROW 51008, N'gestera contiene tablas sin la marca de este instalador. No se modifico su esquema.', 1;

        CREATE TABLE dbo.hoteles (
            codigo NVARCHAR(80) NOT NULL,
            nombre NVARCHAR(150) NOT NULL,
            ubicacion NVARCHAR(150) NOT NULL,
            creado_en DATETIME2(7) NOT NULL
                CONSTRAINT df_hoteles_creado DEFAULT (SYSUTCDATETIME()),
            actualizado_en DATETIME2(7) NOT NULL
                CONSTRAINT df_hoteles_actualizado DEFAULT (SYSUTCDATETIME()),
            CONSTRAINT pk_hoteles PRIMARY KEY CLUSTERED (codigo),
            CONSTRAINT chk_hoteles_codigo CHECK (LEN(LTRIM(RTRIM(codigo))) BETWEEN 1 AND 80),
            CONSTRAINT chk_hoteles_nombre CHECK (LEN(LTRIM(RTRIM(nombre))) BETWEEN 2 AND 150),
            CONSTRAINT chk_hoteles_ubicacion CHECK (LEN(LTRIM(RTRIM(ubicacion))) BETWEEN 2 AND 150)
        );

        CREATE TABLE dbo.huespedes (
            id BIGINT IDENTITY(1,1) NOT NULL,
            nombre_completo NVARCHAR(100) NOT NULL,
            tipo_documento VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL,
            numero_documento VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL,
            creado_en DATETIME2(7) NOT NULL
                CONSTRAINT df_huespedes_creado DEFAULT (SYSUTCDATETIME()),
            actualizado_en DATETIME2(7) NOT NULL
                CONSTRAINT df_huespedes_actualizado DEFAULT (SYSUTCDATETIME()),
            CONSTRAINT pk_huespedes PRIMARY KEY CLUSTERED (id),
            CONSTRAINT uq_huespedes_documento UNIQUE (tipo_documento, numero_documento),
            CONSTRAINT chk_huespedes_id CHECK (id > 0),
            CONSTRAINT chk_huespedes_nombre CHECK (LEN(LTRIM(RTRIM(nombre_completo))) BETWEEN 2 AND 100),
            CONSTRAINT chk_huespedes_tipo CHECK (
                tipo_documento IN ('dni', 'carnet_extranjeria', 'pasaporte')
                AND DATALENGTH(tipo_documento) = LEN(tipo_documento)
            ),
            -- Replica las reglas de TipoDocumento.java; conserva ceros iniciales.
            CONSTRAINT chk_huespedes_documento CHECK (
                (tipo_documento = 'dni' AND DATALENGTH(numero_documento) = 8
                    AND numero_documento NOT LIKE '%[^0-9]%')
                OR (tipo_documento = 'carnet_extranjeria'
                    AND DATALENGTH(numero_documento) BETWEEN 9 AND 12
                    AND numero_documento NOT LIKE '%[^A-Z0-9]%')
                OR (tipo_documento = 'pasaporte'
                    AND DATALENGTH(numero_documento) BETWEEN 6 AND 12
                    AND numero_documento NOT LIKE '%[^A-Z0-9]%')
            )
        );

        CREATE TABLE dbo.estancias (
            id BIGINT IDENTITY(1,1) NOT NULL,
            hotel_codigo NVARCHAR(80) NOT NULL,
            numero_habitacion SMALLINT NULL,
            ubicacion NVARCHAR(80) NOT NULL,
            color_habitacion CHAR(7) COLLATE Latin1_General_100_BIN2 NOT NULL
                CONSTRAINT df_estancias_color DEFAULT ('#000000'),
            check_in DATETIME2(0) NOT NULL,
            check_out DATETIME2(0) NOT NULL,
            estado VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL
                CONSTRAINT df_estancias_estado DEFAULT ('ACTIVA'),
            creado_en DATETIME2(7) NOT NULL
                CONSTRAINT df_estancias_creado DEFAULT (SYSUTCDATETIME()),
            actualizado_en DATETIME2(7) NOT NULL
                CONSTRAINT df_estancias_actualizado DEFAULT (SYSUTCDATETIME()),
            CONSTRAINT pk_estancias PRIMARY KEY CLUSTERED (id),
            CONSTRAINT fk_estancias_hotel FOREIGN KEY (hotel_codigo)
                REFERENCES dbo.hoteles (codigo) ON UPDATE CASCADE ON DELETE NO ACTION,
            CONSTRAINT uq_estancias_ubicacion_checkin UNIQUE (hotel_codigo, ubicacion, check_in),
            CONSTRAINT chk_estancias_id CHECK (id > 0),
            CONSTRAINT chk_estancias_habitacion CHECK (
                numero_habitacion IS NULL OR numero_habitacion BETWEEN 1 AND 50
            ),
            CONSTRAINT chk_estancias_ubicacion CHECK (LEN(LTRIM(RTRIM(ubicacion))) BETWEEN 2 AND 80),
            CONSTRAINT chk_estancias_color CHECK (
                LEFT(color_habitacion, 1) = '#'
                AND SUBSTRING(color_habitacion, 2, 6) NOT LIKE '%[^0-9A-Fa-f]%'
                AND LEN(color_habitacion) = 7
            ),
            CONSTRAINT chk_estancias_horario CHECK (check_out >= check_in),
            CONSTRAINT chk_estancias_estado CHECK (estado IN ('ACTIVA', 'FINALIZADA')
                AND DATALENGTH(estado) = LEN(estado))
        );

        CREATE TABLE dbo.estancia_huespedes (
            estancia_id BIGINT NOT NULL,
            huesped_id BIGINT NOT NULL,
            orden TINYINT NOT NULL,
            CONSTRAINT pk_estancia_huespedes PRIMARY KEY CLUSTERED (estancia_id, huesped_id),
            CONSTRAINT uq_estancia_huespedes_orden UNIQUE (estancia_id, orden),
            CONSTRAINT fk_estancia_huespedes_estancia FOREIGN KEY (estancia_id)
                REFERENCES dbo.estancias (id) ON UPDATE CASCADE ON DELETE CASCADE,
            CONSTRAINT fk_estancia_huespedes_huesped FOREIGN KEY (huesped_id)
                REFERENCES dbo.huespedes (id) ON UPDATE CASCADE ON DELETE NO ACTION,
            CONSTRAINT chk_estancia_huespedes_orden CHECK (orden BETWEEN 1 AND 8)
        );

        CREATE TABLE dbo.pedidos_servicio (
            id BIGINT IDENTITY(1,1) NOT NULL,
            hotel_codigo NVARCHAR(80) NOT NULL,
            numero_habitacion SMALLINT NOT NULL,
            estado VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL
                CONSTRAINT df_pedidos_servicio_estado DEFAULT ('PENDIENTE'),
            observaciones NVARCHAR(300) NOT NULL
                CONSTRAINT df_pedidos_servicio_observaciones DEFAULT (N''),
            creado_en DATETIME2(7) NOT NULL,
            actualizado_en DATETIME2(7) NOT NULL,
            CONSTRAINT pk_pedidos_servicio PRIMARY KEY CLUSTERED (id),
            CONSTRAINT fk_pedidos_servicio_hotel FOREIGN KEY (hotel_codigo)
                REFERENCES dbo.hoteles (codigo) ON UPDATE CASCADE ON DELETE NO ACTION,
            CONSTRAINT chk_pedidos_servicio_id CHECK (id > 0),
            CONSTRAINT chk_pedidos_servicio_habitacion CHECK (numero_habitacion BETWEEN 1 AND 50),
            CONSTRAINT chk_pedidos_servicio_estado CHECK (
                estado IN ('PENDIENTE', 'EN_PREPARACION', 'ENTREGADO', 'CANCELADO')
                AND DATALENGTH(estado) = LEN(estado)
            )
        );

        CREATE TABLE dbo.pedido_servicio_items (
            id BIGINT IDENTITY(1,1) NOT NULL,
            pedido_id BIGINT NOT NULL,
            producto_codigo NVARCHAR(60) NOT NULL,
            producto_nombre NVARCHAR(120) NOT NULL,
            precio_unitario DECIMAL(10,2) NOT NULL,
            cantidad TINYINT NOT NULL,
            CONSTRAINT pk_pedido_servicio_items PRIMARY KEY CLUSTERED (id),
            CONSTRAINT fk_pedido_servicio_items_pedido FOREIGN KEY (pedido_id)
                REFERENCES dbo.pedidos_servicio (id) ON UPDATE CASCADE ON DELETE CASCADE,
            CONSTRAINT chk_pedido_servicio_items_id CHECK (id > 0),
            CONSTRAINT chk_pedido_servicio_items_precio CHECK (precio_unitario > 0),
            CONSTRAINT chk_pedido_servicio_items_cantidad CHECK (cantidad BETWEEN 1 AND 10)
        );

        CREATE TABLE dbo.ingresos_habitacion (
            id BIGINT IDENTITY(1,1) NOT NULL,
            hotel_codigo NVARCHAR(80) NOT NULL,
            numero_habitacion SMALLINT NOT NULL,
            check_in DATETIME2(0) NOT NULL,
            pagado_en DATETIME2(7) NOT NULL,
            monto DECIMAL(10,2) NOT NULL,
            CONSTRAINT pk_ingresos_habitacion PRIMARY KEY CLUSTERED (id),
            CONSTRAINT uq_ingresos_habitacion_estancia UNIQUE (hotel_codigo, numero_habitacion, check_in),
            CONSTRAINT fk_ingresos_habitacion_hotel FOREIGN KEY (hotel_codigo)
                REFERENCES dbo.hoteles (codigo) ON UPDATE CASCADE ON DELETE NO ACTION,
            CONSTRAINT chk_ingresos_habitacion_id CHECK (id > 0),
            CONSTRAINT chk_ingresos_habitacion_numero CHECK (numero_habitacion BETWEEN 1 AND 50),
            CONSTRAINT chk_ingresos_habitacion_monto CHECK (monto > 0)
        );

        -- No se agrega FK de ingresos a estancias: el controlador actual registra
        -- el pago ANTES de guardar la estancia. Agregarla romperia esa operacion.
        -- No se inventan FK a habitaciones/productos: hoy viven en memoria.

        CREATE INDEX ix_estancias_hotel_estado_fecha
            ON dbo.estancias (hotel_codigo, estado, check_in DESC);
        CREATE INDEX ix_estancias_hotel_habitacion_estado
            ON dbo.estancias (hotel_codigo, numero_habitacion, estado);
        CREATE INDEX ix_estancias_hotel_color_fecha
            ON dbo.estancias (hotel_codigo, color_habitacion, check_in DESC);
        CREATE INDEX ix_estancias_hotel_periodo
            ON dbo.estancias (hotel_codigo, check_in, check_out);
        CREATE INDEX ix_estancias_hotel_ubicacion_fecha
            ON dbo.estancias (hotel_codigo, ubicacion, check_in DESC);
        CREATE INDEX ix_estancia_huespedes_huesped
            ON dbo.estancia_huespedes (huesped_id);
        CREATE INDEX ix_pedidos_servicio_hotel_estado_fecha
            ON dbo.pedidos_servicio (hotel_codigo, estado, creado_en DESC);
        CREATE INDEX ix_pedidos_servicio_hotel_habitacion_fecha
            ON dbo.pedidos_servicio (hotel_codigo, numero_habitacion, creado_en DESC);
        CREATE INDEX ix_pedido_servicio_items_pedido
            ON dbo.pedido_servicio_items (pedido_id);
        CREATE INDEX ix_ingresos_habitacion_hotel_fecha
            ON dbo.ingresos_habitacion (hotel_codigo, pagado_en DESC);

        -- Cada CREATE TRIGGER necesita su propio lote. EXEC permite mantenerlos
        -- dentro de la transaccion. Los triggers admiten UPDATE de multiples filas.
        -- NOCOUNT evita conteos extra que alteren executeUpdate() del driver JDBC.
        EXEC(N'CREATE TRIGGER dbo.trg_hoteles_actualizado ON dbo.hoteles AFTER UPDATE AS
        BEGIN
            SET NOCOUNT ON;
            IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.trg_hoteles_actualizado'')) > 1 RETURN;
            UPDATE destino SET actualizado_en = SYSUTCDATETIME()
            FROM dbo.hoteles AS destino JOIN inserted AS origen ON origen.codigo = destino.codigo;
        END;');

        EXEC(N'CREATE TRIGGER dbo.trg_huespedes_actualizado ON dbo.huespedes AFTER UPDATE AS
        BEGIN
            SET NOCOUNT ON;
            IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.trg_huespedes_actualizado'')) > 1 RETURN;
            UPDATE destino SET actualizado_en = SYSUTCDATETIME()
            FROM dbo.huespedes AS destino JOIN inserted AS origen ON origen.id = destino.id;
        END;');

        EXEC(N'CREATE TRIGGER dbo.trg_estancias_actualizado ON dbo.estancias AFTER UPDATE AS
        BEGIN
            SET NOCOUNT ON;
            IF TRIGGER_NESTLEVEL(OBJECT_ID(N''dbo.trg_estancias_actualizado'')) > 1 RETURN;
            UPDATE destino SET actualizado_en = SYSUTCDATETIME()
            FROM dbo.estancias AS destino JOIN inserted AS origen ON origen.id = destino.id;
        END;');

        -- Solo hoteles del CatalogoHoteles.java; sin huespedes/pedidos ficticios.
        INSERT INTO dbo.hoteles (codigo, nombre, ubicacion)
        VALUES
            (N'sol-pucusana', N'Hotel El Sol de Pucusana', N'Pucusana, Lima'),
            (N'brisa-marina', N'Hotel Brisa Marina', N'Miraflores, Lima'),
            (N'valle-dorado', N'Hotel Valle Dorado', N'Urubamba, Cusco'),
            (N'costa-serena', N'Hotel Costa Serena', N'Máncora, Piura');

        -- Rol de base de datos para las operaciones reales de los repositorios.
        -- El administrador asigna posteriormente un usuario de conexion a este rol.
        IF DATABASE_PRINCIPAL_ID(N'gestera_aplicacion') IS NULL
            EXEC(N'CREATE ROLE [gestera_aplicacion] AUTHORIZATION [dbo];');
        ELSE IF NOT EXISTS (SELECT 1 FROM sys.database_principals
                            WHERE name = N'gestera_aplicacion' AND type = 'R')
            THROW 51009, N'Existe gestera_aplicacion, pero no es un rol de base de datos.', 1;

        GRANT SELECT, INSERT, UPDATE ON OBJECT::dbo.hoteles TO gestera_aplicacion;
        GRANT SELECT, INSERT, UPDATE ON OBJECT::dbo.huespedes TO gestera_aplicacion;
        GRANT SELECT, INSERT, UPDATE ON OBJECT::dbo.estancias TO gestera_aplicacion;
        GRANT SELECT, INSERT, DELETE ON OBJECT::dbo.estancia_huespedes TO gestera_aplicacion;
        GRANT SELECT, INSERT, UPDATE ON OBJECT::dbo.pedidos_servicio TO gestera_aplicacion;
        GRANT SELECT, INSERT ON OBJECT::dbo.pedido_servicio_items TO gestera_aplicacion;
        GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.ingresos_habitacion TO gestera_aplicacion;

        EXEC sys.sp_addextendedproperty
            @name = N'Gestera.Instalador',
            @value = N'GESTERA_SQLSERVER_V001_V005_1';
    END;

    -- Verifica las 43 columnas usadas por el contrato actual. Se ejecuta tambien
    -- al repetir el archivo; no oculta una tabla faltante o un tipo alterado.
    DECLARE @columnas TABLE (
        tabla SYSNAME, columna SYSNAME, tipo SYSNAME,
        longitud SMALLINT NULL, escala TINYINT NULL,
        permite_null BIT, identidad BIT,
        PRIMARY KEY (tabla, columna)
    );
    INSERT INTO @columnas VALUES
        (N'hoteles',N'codigo',N'nvarchar',160,NULL,0,0),
        (N'hoteles',N'nombre',N'nvarchar',300,NULL,0,0),
        (N'hoteles',N'ubicacion',N'nvarchar',300,NULL,0,0),
        (N'hoteles',N'creado_en',N'datetime2',NULL,7,0,0),
        (N'hoteles',N'actualizado_en',N'datetime2',NULL,7,0,0),
        (N'huespedes',N'id',N'bigint',NULL,NULL,0,1),
        (N'huespedes',N'nombre_completo',N'nvarchar',200,NULL,0,0),
        (N'huespedes',N'tipo_documento',N'varchar',20,NULL,0,0),
        (N'huespedes',N'numero_documento',N'varchar',20,NULL,0,0),
        (N'huespedes',N'creado_en',N'datetime2',NULL,7,0,0),
        (N'huespedes',N'actualizado_en',N'datetime2',NULL,7,0,0),
        (N'estancias',N'id',N'bigint',NULL,NULL,0,1),
        (N'estancias',N'hotel_codigo',N'nvarchar',160,NULL,0,0),
        (N'estancias',N'numero_habitacion',N'smallint',NULL,NULL,1,0),
        (N'estancias',N'ubicacion',N'nvarchar',160,NULL,0,0),
        (N'estancias',N'color_habitacion',N'char',7,NULL,0,0),
        (N'estancias',N'check_in',N'datetime2',NULL,0,0,0),
        (N'estancias',N'check_out',N'datetime2',NULL,0,0,0),
        (N'estancias',N'estado',N'varchar',12,NULL,0,0),
        (N'estancias',N'creado_en',N'datetime2',NULL,7,0,0),
        (N'estancias',N'actualizado_en',N'datetime2',NULL,7,0,0),
        (N'estancia_huespedes',N'estancia_id',N'bigint',NULL,NULL,0,0),
        (N'estancia_huespedes',N'huesped_id',N'bigint',NULL,NULL,0,0),
        (N'estancia_huespedes',N'orden',N'tinyint',NULL,NULL,0,0),
        (N'pedidos_servicio',N'id',N'bigint',NULL,NULL,0,1),
        (N'pedidos_servicio',N'hotel_codigo',N'nvarchar',160,NULL,0,0),
        (N'pedidos_servicio',N'numero_habitacion',N'smallint',NULL,NULL,0,0),
        (N'pedidos_servicio',N'estado',N'varchar',20,NULL,0,0),
        (N'pedidos_servicio',N'observaciones',N'nvarchar',600,NULL,0,0),
        (N'pedidos_servicio',N'creado_en',N'datetime2',NULL,7,0,0),
        (N'pedidos_servicio',N'actualizado_en',N'datetime2',NULL,7,0,0),
        (N'pedido_servicio_items',N'id',N'bigint',NULL,NULL,0,1),
        (N'pedido_servicio_items',N'pedido_id',N'bigint',NULL,NULL,0,0),
        (N'pedido_servicio_items',N'producto_codigo',N'nvarchar',120,NULL,0,0),
        (N'pedido_servicio_items',N'producto_nombre',N'nvarchar',240,NULL,0,0),
        (N'pedido_servicio_items',N'precio_unitario',N'decimal',NULL,2,0,0),
        (N'pedido_servicio_items',N'cantidad',N'tinyint',NULL,NULL,0,0),
        (N'ingresos_habitacion',N'id',N'bigint',NULL,NULL,0,1),
        (N'ingresos_habitacion',N'hotel_codigo',N'nvarchar',160,NULL,0,0),
        (N'ingresos_habitacion',N'numero_habitacion',N'smallint',NULL,NULL,0,0),
        (N'ingresos_habitacion',N'check_in',N'datetime2',NULL,0,0,0),
        (N'ingresos_habitacion',N'pagado_en',N'datetime2',NULL,7,0,0),
        (N'ingresos_habitacion',N'monto',N'decimal',NULL,2,0,0);

    IF EXISTS (
        SELECT 1 FROM @columnas AS esperado
        LEFT JOIN sys.tables AS tabla
            ON tabla.name = esperado.tabla AND tabla.schema_id = SCHEMA_ID(N'dbo')
        LEFT JOIN sys.columns AS columna
            ON columna.object_id = tabla.object_id AND columna.name = esperado.columna
        WHERE columna.column_id IS NULL
           OR TYPE_NAME(columna.user_type_id) <> esperado.tipo
           OR (esperado.longitud IS NOT NULL AND columna.max_length <> esperado.longitud)
           OR (esperado.escala IS NOT NULL AND columna.scale <> esperado.escala)
           OR (esperado.tipo = N'decimal' AND columna.precision <> 10)
           OR columna.is_nullable <> esperado.permite_null
           OR columna.is_identity <> esperado.identidad
    )
        THROW 51010, N'El esquema no coincide con las columnas/tipos esperados. No se aplico una migracion automatica.', 1;

    IF (SELECT COUNT(*) FROM sys.columns AS c JOIN sys.tables AS t ON t.object_id = c.object_id
        WHERE t.schema_id = SCHEMA_ID(N'dbo') AND t.name IN (SELECT DISTINCT tabla FROM @columnas)) <> 43
        THROW 51011, N'Las tablas tienen columnas adicionales. Revise la version antes de repetir el instalador.', 1;

    IF (SELECT COUNT(*) FROM sys.key_constraints
        WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + tabla) FROM @columnas)
          AND type = 'PK') <> 7
       OR (SELECT COUNT(*) FROM sys.key_constraints
           WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + tabla) FROM @columnas)
             AND type = 'UQ') <> 4
       OR (SELECT COUNT(*) FROM sys.foreign_keys
           WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + tabla) FROM @columnas)) <> 6
        THROW 51012, N'Falta una clave primaria, unica o foranea de la instalacion.', 1;

    IF EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + tabla) FROM @columnas)
                 AND (is_disabled = 1 OR is_not_trusted = 1))
       OR EXISTS (SELECT 1 FROM sys.check_constraints
                  WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + tabla) FROM @columnas)
                    AND (is_disabled = 1 OR is_not_trusted = 1))
        THROW 51013, N'Hay restricciones deshabilitadas o sin validar.', 1;

    IF (SELECT COUNT(*) FROM sys.triggers
        WHERE object_id IN (OBJECT_ID(N'dbo.trg_hoteles_actualizado'),
                            OBJECT_ID(N'dbo.trg_huespedes_actualizado'),
                            OBJECT_ID(N'dbo.trg_estancias_actualizado'))
          AND is_disabled = 0) <> 3
        THROW 51014, N'Falta un trigger de auditoria o esta deshabilitado.', 1;

    IF (SELECT COUNT(*) FROM sys.check_constraints
        WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + tabla) FROM @columnas)) <> 23
       OR (SELECT COUNT(*) FROM sys.default_constraints
           WHERE parent_object_id IN (SELECT OBJECT_ID(N'dbo.' + tabla) FROM @columnas)) <> 10
        THROW 51015, N'Falta una validacion CHECK o un valor predeterminado.', 1;

    IF EXISTS (
        SELECT 1 FROM (VALUES
            (N'estancias', N'ix_estancias_hotel_estado_fecha'),
            (N'estancias', N'ix_estancias_hotel_habitacion_estado'),
            (N'estancias', N'ix_estancias_hotel_color_fecha'),
            (N'estancias', N'ix_estancias_hotel_periodo'),
            (N'estancias', N'ix_estancias_hotel_ubicacion_fecha'),
            (N'estancia_huespedes', N'ix_estancia_huespedes_huesped'),
            (N'pedidos_servicio', N'ix_pedidos_servicio_hotel_estado_fecha'),
            (N'pedidos_servicio', N'ix_pedidos_servicio_hotel_habitacion_fecha'),
            (N'pedido_servicio_items', N'ix_pedido_servicio_items_pedido'),
            (N'ingresos_habitacion', N'ix_ingresos_habitacion_hotel_fecha')
        ) AS esperado(tabla, indice)
        LEFT JOIN sys.indexes AS indice
            ON indice.object_id = OBJECT_ID(N'dbo.' + esperado.tabla)
           AND indice.name = esperado.indice
        WHERE indice.index_id IS NULL OR indice.is_disabled = 1 OR indice.is_hypothetical = 1
    )
        THROW 51016, N'Falta un indice operativo o esta deshabilitado.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.database_principals
                   WHERE name = N'gestera_aplicacion' AND type = 'R')
        THROW 51017, N'Falta el rol gestera_aplicacion.', 1;

    COMMIT TRANSACTION;

    PRINT N'GESTERA: instalacion/verificacion completada. 7 tablas y 43 columnas.';
    PRINT N'JAVA: iniciar el perfil sqlserver de Gestera con el driver Microsoft JDBC.';

    SELECT DB_NAME() AS base_de_datos,
           CONVERT(NVARCHAR(128), SERVERPROPERTY('ProductVersion')) AS version_sql_server,
           N'GESTERA_SQLSERVER_V001_V005_1' AS version_esquema;

    SELECT SCHEMA_NAME(t.schema_id) AS esquema, t.name AS tabla, COUNT(c.column_id) AS columnas
    FROM sys.tables AS t JOIN sys.columns AS c ON c.object_id = t.object_id
    WHERE t.schema_id = SCHEMA_ID(N'dbo') AND t.name IN (SELECT DISTINCT tabla FROM @columnas)
    GROUP BY t.schema_id, t.name ORDER BY t.name;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Vinculacion de un LOGIN ya provisionado por el administrador (ejemplo manual):
-- USE [gestera];
-- CREATE USER [gestera_app] FOR LOGIN [gestera_app] WITH DEFAULT_SCHEMA = [dbo];
-- ALTER ROLE [gestera_aplicacion] ADD MEMBER [gestera_app];
-- No use sa/db_owner como identidad de ejecucion del backend.
