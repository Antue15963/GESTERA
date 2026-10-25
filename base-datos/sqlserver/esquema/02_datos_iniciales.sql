-- Se ejecuta solo durante la creacion/migracion, no al reiniciar Java.
INSERT dbo.secciones_tarifarias(hotel_codigo,codigo,nombre,color_hex,precio_seis_horas,precio_dia_completo)
SELECT h.codigo,s.codigo,s.nombre,s.color_hex,s.precio6,s.precio24
FROM dbo.hoteles h CROSS JOIN (VALUES
    ('seccion-1',N'Amarillo','#FBBF24',50.00,90.00),
    ('seccion-2',N'Celeste','#38BDF8',65.00,120.00),
    ('seccion-3',N'Violeta','#A78BFA',80.00,150.00)
) s(codigo,nombre,color_hex,precio6,precio24);
INSERT dbo.habitaciones(hotel_codigo,numero,seccion_tarifaria_id)
SELECT h.codigo,n.numero,s.id FROM dbo.hoteles h
CROSS JOIN (VALUES(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12)) n(numero)
JOIN dbo.secciones_tarifarias s ON s.hotel_codigo=h.codigo
 AND s.codigo=CONCAT('seccion-',((n.numero-1)/4)%3+1);
INSERT dbo.areas_comunes(hotel_codigo,codigo,nombre,descripcion)
SELECT h.codigo,a.codigo,a.nombre,a.descripcion FROM dbo.hoteles h CROSS JOIN (VALUES
    ('jacuzzi',N'Jacuzzi',N'Registra el horario reservado para disfrutar del jacuzzi.'),
    ('sauna',N'Sauna',N'Organiza el ingreso y la salida de los visitantes del sauna.'),
    ('area-juegos',N'Área de juegos',N'Reserva un horario para compartir en el área de juegos.')
) a(codigo,nombre,descripcion);
INSERT dbo.catalogos_servicio(hotel_codigo) SELECT codigo FROM dbo.hoteles;
INSERT dbo.categorias_servicio(catalogo_id,codigo,nombre)
SELECT c.id,k.codigo,k.nombre FROM dbo.catalogos_servicio c CROSS JOIN (VALUES
    ('desayunos',N'Desayunos'),('comidas',N'Comidas'),('bebidas',N'Bebidas')
) k(codigo,nombre);
INSERT dbo.productos_servicio(catalogo_id,codigo,categoria_codigo,nombre,precio)
SELECT c.id,p.codigo,p.categoria,p.nombre,p.precio FROM dbo.catalogos_servicio c CROSS JOIN (VALUES
    ('desayuno-continental','desayunos',N'Desayuno continental',25.00),
    ('club-sandwich','comidas',N'Club sándwich',32.00),
    ('sopa-del-dia','comidas',N'Sopa del día',18.00),
    ('cafe-americano','bebidas',N'Café americano',8.00),
    ('agua-mineral','bebidas',N'Agua mineral',6.00)
) p(codigo,categoria,nombre,precio);
-- Las credenciales se inicializan desde Java con sal aleatoria y PBKDF2.
-- No se incluyen llaves ni hashes predeterminados en el SQL.
