CREATE TRIGGER dbo.v2_trg_pagos_origen ON dbo.pagos_estancia AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.estancias e ON e.id=i.estancia_id WHERE e.habitacion_id IS NULL)
    THROW 54201,N'Un pago de alojamiento debe pertenecer a una estancia de habitacion.',1;
END;
GO
CREATE TRIGGER dbo.v2_trg_pedidos_origen ON dbo.pedidos_servicio AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.estancias e ON e.id=i.estancia_id WHERE e.habitacion_id IS NULL)
    THROW 54202,N'El servicio al cuarto requiere una estancia de habitacion.',1;
 IF UPDATE(estancia_id) AND EXISTS(SELECT 1 FROM inserted i
    JOIN dbo.estancias e ON e.id=i.estancia_id JOIN dbo.habitaciones h ON h.id=e.habitacion_id
    WHERE e.estado<>'ACTIVA' OR h.estado<>'OCUPADA' OR h.activa=0)
    THROW 54203,N'Un pedido nuevo requiere una estancia activa en una habitacion ocupada.',1;
 IF UPDATE(estancia_id) AND EXISTS(SELECT 1 FROM inserted i JOIN dbo.estancias e ON e.id=i.estancia_id
    JOIN dbo.habitaciones h ON h.id=e.habitacion_id JOIN dbo.pedido_servicio_items it ON it.pedido_id=i.id
    JOIN dbo.catalogos_servicio c ON c.id=it.producto_catalogo_id WHERE c.hotel_codigo<>h.hotel_codigo)
    THROW 54204,N'Los productos referenciados deben pertenecer al hotel del pedido.',1;
END;
GO
CREATE TRIGGER dbo.v2_trg_items_hotel ON dbo.pedido_servicio_items AFTER INSERT,UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.pedidos_servicio p ON p.id=i.pedido_id
    JOIN dbo.estancias e ON e.id=p.estancia_id JOIN dbo.habitaciones h ON h.id=e.habitacion_id
    JOIN dbo.catalogos_servicio c ON c.id=i.producto_catalogo_id WHERE c.hotel_codigo<>h.hotel_codigo)
    THROW 54205,N'El producto del item pertenece a otro hotel.',1;
END;
GO
CREATE TRIGGER dbo.v2_trg_estancias_origen ON dbo.estancias AFTER UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF UPDATE(habitacion_id) OR UPDATE(area_comun_id)
 BEGIN
    IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id
        WHERE (ISNULL(i.habitacion_id,-1)<>ISNULL(d.habitacion_id,-1)
            OR ISNULL(i.area_comun_id,-1)<>ISNULL(d.area_comun_id,-1))
        AND (EXISTS(SELECT 1 FROM dbo.pagos_estancia p WHERE p.estancia_id=i.id)
            OR EXISTS(SELECT 1 FROM dbo.pedidos_servicio p WHERE p.estancia_id=i.id)))
        THROW 54206,N'No se puede trasladar una estancia que ya tiene pagos o pedidos.',1;
 END;
END;
