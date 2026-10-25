"""Genera un unico archivo SSMS normal a partir de fragmentos SQL revisables."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
PARTS = ROOT / "base-datos/sqlserver/esquema"
DEST = ROOT / "base-datos/sqlserver/CREAR_GESTERA_SQLSERVER.sql"
VERSION = "GESTERA_SQLSERVER_14_V2"

def ejecutar(sql):
    return "EXEC sys.sp_executesql N'" + sql.replace("'", "''") + "';\n"

def generar():
    sql = """/*
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
"""
    for tabla in ("hoteles","huespedes","estancias","estancia_huespedes","pedidos_servicio","pedido_servicio_items","ingresos_habitacion"):
        sql += f"   EXEC sys.sp_rename N'dbo.{tabla}',N'__v1_{tabla}';\n"
    sql += "  END;\n"
    sql += ejecutar((PARTS/"01_tablas.sql").read_text(encoding="utf-8"))
    sql += """  IF @migrar=1
  BEGIN
"""
    sql += ejecutar("""INSERT dbo.hoteles(codigo,nombre,ubicacion,iniciales,activo,creado_en,actualizado_en)
SELECT codigo,nombre,ubicacion,CASE codigo WHEN 'sol-pucusana' THEN 'EP' WHEN 'brisa-marina' THEN 'BM'
 WHEN 'valle-dorado' THEN 'VD' WHEN 'costa-serena' THEN 'CS' ELSE 'HT' END,1,creado_en,actualizado_en
FROM dbo.__v1_hoteles;""")
    sql += "  END ELSE BEGIN\n"
    sql += ejecutar("""INSERT dbo.hoteles(codigo,nombre,ubicacion,iniciales) VALUES
(N'sol-pucusana',N'Hotel El Sol de Pucusana',N'Pucusana, Lima','EP'),
(N'brisa-marina',N'Hotel Brisa Marina',N'Miraflores, Lima','BM'),
(N'valle-dorado',N'Hotel Valle Dorado',N'Urubamba, Cusco','VD'),
(N'costa-serena',N'Hotel Costa Serena',N'Máncora, Piura','CS');""")
    sql += "  END;\n"
    sql += ejecutar((PARTS/"02_datos_iniciales.sql").read_text(encoding="utf-8"))
    sql += "  IF @migrar=1 BEGIN\n"
    sql += ejecutar((PARTS/"03_importar_v1.sql").read_text(encoding="utf-8"))
    sql += "  END;\n"
    for fragmento in re.split(r"(?im)^GO\s*$",(PARTS/"04_integridad.sql").read_text(encoding="utf-8")):
        if fragmento.strip():
            sql += ejecutar(fragmento.strip())
    claves = {"hoteles":["codigo"],"credenciales_hotel":["hotel_codigo"],"secciones_tarifarias":["id"],
              "habitaciones":["id"],"areas_comunes":["id"],"huespedes":["id"],"estancias":["id"],
              "pagos_estancia":["id"],"catalogos_servicio":["id"],"categorias_servicio":["catalogo_id","codigo"],
              "productos_servicio":["catalogo_id","codigo"]}
    for tabla, columnas in claves.items():
        condicion=" AND ".join(f"d.{c}=i.{c}" for c in columnas)
        sql += ejecutar(f"""CREATE TRIGGER dbo.v2_trg_{tabla}_auditoria ON dbo.{tabla} AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
 IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.v2_trg_{tabla}_auditoria'))>1 RETURN;
 UPDATE d SET actualizado_en=SYSUTCDATETIME() FROM dbo.{tabla} d JOIN inserted i ON {condicion};
END;""")
    sql += """  IF DATABASE_PRINCIPAL_ID(N'gestera_aplicacion') IS NULL
   EXEC(N'CREATE ROLE gestera_aplicacion AUTHORIZATION dbo;');
"""
    tablas = list(claves)+["estancia_huespedes","pedidos_servicio","pedido_servicio_items"]
    for tabla in tablas:
        sql += f"  GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.{tabla} TO gestera_aplicacion;\n"
    sql += """  IF @version IS NULL
   EXEC sys.sp_addextendedproperty @name=N'Gestera.Instalador',@value=N'GESTERA_SQLSERVER_14_V2';
  ELSE
   EXEC sys.sp_updateextendedproperty @name=N'Gestera.Instalador',@value=N'GESTERA_SQLSERVER_14_V2';
 END;
 DECLARE @tablas TABLE(nombre sysname PRIMARY KEY);
 INSERT @tablas VALUES """+",".join("(N'"+t+"')" for t in tablas)+""";
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
"""
    return sql

if __name__=="__main__":
    resultado=generar()
    if "--verificar" in sys.argv:
        if DEST.read_text(encoding="utf-8-sig")!=resultado:
            raise SystemExit("El instalador no coincide con sus fuentes; regenere el SQL.")
        print("Instalador SQL sincronizado.")
    else:
        DEST.write_text(resultado,encoding="utf-8-sig",newline="\n")
        print(DEST)
