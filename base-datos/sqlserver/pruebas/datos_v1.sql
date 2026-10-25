-- Fixture aislado: prueba que una migracion real conserva historia e importes.
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
-- SSMS puede agregar metadatos de diagramas; no deben eliminarse al migrar.
CREATE TABLE dbo.sysdiagrams(name sysname NOT NULL, principal_id int NOT NULL,
    diagram_id int IDENTITY PRIMARY KEY, version int NULL, definition varbinary(max) NULL);
INSERT dbo.sysdiagrams(name,principal_id,version,definition) VALUES(N'Diagrama conservado',1,1,0x123456);

INSERT dbo.huespedes(nombre_completo,tipo_documento,numero_documento)
VALUES(N'María Histórico','dni','00998877');
DECLARE @persona bigint=SCOPE_IDENTITY();
INSERT dbo.estancias(hotel_codigo,numero_habitacion,ubicacion,color_habitacion,check_in,check_out,estado)
VALUES(N'brisa-marina',1,N'Habitación 1','#AABBCC','2026-01-01T10:00:00','2026-01-01T16:00:00','FINALIZADA');
DECLARE @estancia bigint=SCOPE_IDENTITY();
INSERT dbo.estancia_huespedes VALUES(@estancia,@persona,1);
INSERT dbo.ingresos_habitacion(hotel_codigo,numero_habitacion,check_in,pagado_en,monto)
VALUES(N'brisa-marina',1,'2026-01-01T10:00:00','2026-01-01T10:05:00',75.25),
      (N'brisa-marina',2,'2026-01-02T08:00:00','2026-01-02T08:05:00',20.25);
INSERT dbo.pedidos_servicio(hotel_codigo,numero_habitacion,estado,observaciones,creado_en,actualizado_en)
VALUES(N'brisa-marina',1,'ENTREGADO',N'Histórico','2026-01-01T11:00:00','2026-01-01T11:10:00');
DECLARE @pedido bigint=SCOPE_IDENTITY();
INSERT dbo.pedido_servicio_items(pedido_id,producto_codigo,producto_nombre,precio_unitario,cantidad)
VALUES(@pedido,N'agua-mineral',N'Agua histórica',9.25,2);
INSERT dbo.pedidos_servicio(hotel_codigo,numero_habitacion,estado,observaciones,creado_en,actualizado_en)
VALUES(N'brisa-marina',3,'ENTREGADO',N'Sin estancia registrada','2026-01-03T09:15:45.1234567','2026-01-03T09:20:00');
SET @pedido=SCOPE_IDENTITY();
INSERT dbo.pedido_servicio_items(pedido_id,producto_codigo,producto_nombre,precio_unitario,cantidad)
VALUES(@pedido,N'producto-retirado',N'Producto histórico',7.50,1);
COMMIT;
