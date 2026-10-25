SET NOCOUNT ON;
IF (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND NOT (name=N'sysdiagrams' AND schema_id=SCHEMA_ID(N'dbo')))<>14
    THROW 54301,N'La migracion no termino con 14 tablas.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.huespedes WHERE nombre_completo=N'María Histórico'
    AND tipo_documento='DNI' AND numero_documento='00998877')
    THROW 54302,N'No se conservo la identidad de la persona.',1;
IF (SELECT SUM(monto) FROM dbo.pagos_estancia)<>95.50 OR (SELECT COUNT(*) FROM dbo.pagos_estancia)<>2
    THROW 54303,N'Los pagos anteriores no coinciden.',1;
IF (SELECT COUNT(*) FROM dbo.pedidos_servicio)<>2 OR (SELECT COUNT(*) FROM dbo.pedido_servicio_items)<>2
    THROW 54304,N'Faltan pedidos o items anteriores.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.pedido_servicio_items WHERE producto_nombre_snapshot=N'Agua histórica'
    AND precio_unitario_snapshot=9.25 AND cantidad=2)
    THROW 54305,N'El precio historico fue alterado.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.pedido_servicio_items WHERE producto_codigo_snapshot=N'producto-retirado'
    AND producto_catalogo_id IS NULL AND producto_codigo_fk IS NULL)
    THROW 54306,N'No se conservo el producto retirado.',1;
IF EXISTS(SELECT 1 FROM dbo.estancias WHERE origen='IMPORTADA' AND habitacion_id IS NOT NULL
    AND (tarifa_aplicada IS NOT NULL OR tipo_servicio IS NOT NULL))
    THROW 54307,N'Se inventaron tarifas historicas que no existian en V1.',1;
PRINT N'Migracion verificada: identidad, pagos, pedidos, snapshots y referencias conservados.';

IF NOT EXISTS(SELECT 1 FROM dbo.sysdiagrams WHERE name=N'Diagrama conservado' AND definition=0x123456) THROW 54312,N'Se altero el diagrama de SSMS.',1;
