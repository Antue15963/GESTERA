-- Datos historicos: se conservan identificadores, personas, fechas, pedidos e importes.
-- El modelo anterior no guardaba tarifas/servicios historicos: se marcan como
-- IMPORTADA y NULL en vez de inventar un desglose economico.
IF EXISTS(SELECT 1 FROM dbo.__v1_estancias WHERE estado='ACTIVA' AND numero_habitacion IS NOT NULL)
    THROW 54101,N'Hay estancias de habitacion activas del modelo anterior. Exporte y cierre su operacion antes de migrar; no se inventaran tarifas.',1;
IF EXISTS(SELECT 1 FROM dbo.__v1_estancias WHERE numero_habitacion IS NOT NULL
    GROUP BY hotel_codigo,numero_habitacion,check_in HAVING COUNT(*)>1)
    THROW 54102,N'Hay estancias anteriores con identidad de habitacion/entrada duplicada. Reviselas antes de migrar.',1;

;WITH numeros AS (SELECT TOP(50) ROW_NUMBER() OVER(ORDER BY (SELECT NULL)) AS numero FROM sys.all_objects),
maximos AS (
 SELECT hotel_codigo,MAX(numero_habitacion) AS cantidad FROM (
 SELECT hotel_codigo,numero_habitacion FROM dbo.__v1_estancias
 UNION ALL SELECT hotel_codigo,numero_habitacion FROM dbo.__v1_pedidos_servicio
 UNION ALL SELECT hotel_codigo,numero_habitacion FROM dbo.__v1_ingresos_habitacion) x GROUP BY hotel_codigo
)
INSERT dbo.habitaciones(hotel_codigo,numero,seccion_tarifaria_id)
SELECT m.hotel_codigo,n.numero,s.id FROM maximos m JOIN numeros n ON n.numero<=m.cantidad
JOIN dbo.secciones_tarifarias s ON s.hotel_codigo=m.hotel_codigo AND s.codigo=CONCAT('seccion-',((n.numero-1)/4)%3+1)
WHERE NOT EXISTS(SELECT 1 FROM dbo.habitaciones h WHERE h.hotel_codigo=m.hotel_codigo AND h.numero=n.numero);

;WITH areas AS (
 SELECT DISTINCT hotel_codigo,ubicacion FROM dbo.__v1_estancias WHERE numero_habitacion IS NULL
), faltantes AS (
 SELECT x.*,ROW_NUMBER() OVER(ORDER BY x.hotel_codigo,x.ubicacion) AS numero FROM areas x
 WHERE NOT EXISTS(SELECT 1 FROM dbo.areas_comunes a WHERE a.hotel_codigo=x.hotel_codigo AND a.nombre=x.ubicacion COLLATE Latin1_General_100_CI_AS_SC)
)
INSERT dbo.areas_comunes(hotel_codigo,codigo,nombre,descripcion)
SELECT hotel_codigo,CONCAT('importada-',numero),ubicacion,N'Área conservada del historial anterior.' FROM faltantes;

SET IDENTITY_INSERT dbo.huespedes ON;
INSERT dbo.huespedes(id,nombre_completo,tipo_documento,numero_documento,creado_en,actualizado_en)
SELECT id,nombre_completo,UPPER(tipo_documento),numero_documento,creado_en,actualizado_en FROM dbo.__v1_huespedes;
SET IDENTITY_INSERT dbo.huespedes OFF;
SET IDENTITY_INSERT dbo.estancias ON;
INSERT dbo.estancias(id,habitacion_id,area_comun_id,check_in,check_out,color_historico,estado,origen,creado_en,actualizado_en)
SELECT e.id,h.id,a.id,e.check_in,e.check_out,
 CASE WHEN h.id IS NULL THEN NULL ELSE e.color_habitacion END,
 CASE WHEN h.id IS NULL THEN e.estado ELSE 'FINALIZADA' END,'IMPORTADA',e.creado_en,e.actualizado_en
FROM dbo.__v1_estancias e
LEFT JOIN dbo.habitaciones h ON h.hotel_codigo=e.hotel_codigo AND h.numero=e.numero_habitacion
LEFT JOIN dbo.areas_comunes a ON e.numero_habitacion IS NULL AND a.hotel_codigo=e.hotel_codigo AND a.nombre=e.ubicacion COLLATE Latin1_General_100_CI_AS_SC;
SET IDENTITY_INSERT dbo.estancias OFF;
INSERT dbo.estancia_huespedes(estancia_id,huesped_id,orden)
SELECT estancia_id,huesped_id,orden FROM dbo.__v1_estancia_huespedes;

-- El modelo anterior permitia registrar el pago antes de crear su estancia.
INSERT dbo.estancias(habitacion_id,check_in,check_out,color_historico,estado,origen)
SELECT h.id,i.check_in,i.check_in,'#000000','FINALIZADA','IMPORTADA'
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
SELECT DISTINCT h.id,CONVERT(datetime2(0),p.creado_en),CONVERT(datetime2(0),p.creado_en),'#000000','FINALIZADA','IMPORTADA'
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
    THROW 54103,N'La verificacion de registros/importes migrados fallo. Se revierte toda la migracion.',1;
DROP TABLE dbo.__v1_estancia_huespedes,dbo.__v1_pedido_servicio_items;
DROP TABLE dbo.__v1_pedidos_servicio,dbo.__v1_ingresos_habitacion;
DROP TABLE dbo.__v1_estancias,dbo.__v1_huespedes,dbo.__v1_hoteles;
