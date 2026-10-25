# Actualización del modelo operativo de Gestera

El 24 de octubre de 2026 se migró la base local gestera al esquema GESTERA_SQLSERVER_14_V2 y se inició el JAR actualizado. La página está en http://127.0.0.1:8080. El instalador ya se ejecutó; no es necesario volver a crear la base para usar la página. En SSMS, actualizar el nodo Tablas de gestera para ver el modelo vigente.

## Adaptación de la referencia

Se aplican entidades separadas, claves foráneas y asociaciones como en la imagen, ajustadas a las pantallas existentes. Entrada, salida y ocupación se representan mediante estancias; limpieza se representa mediante el estado de la habitación. No se incorporaron módulos de reservas anticipadas, empleados o roles de usuario que aún no forman parte del programa.

Las 14 tablas son hoteles, credenciales_hotel, secciones_tarifarias, habitaciones, areas_comunes, huespedes, estancias, estancia_huespedes, pagos_estancia, catalogos_servicio, categorias_servicio, productos_servicio, pedidos_servicio y pedido_servicio_items.

## Código y comportamiento

- RepositorioInventarioJdbc conserva habitaciones, preferencias, secciones, precios y estado operativo. La baja de una habitación es lógica para preservar su historia.
- RepositorioCredencialesHotelJdbc conserva sal/hash PBKDF2, versión e indicador de cambio. No se almacena la llave en texto claro; una configuración inicial no reemplaza una credencial ya existente.
- RepositorioCatalogoServicioJdbc conserva catálogo, categorías y productos con control de versión.
- Los repositorios de huéspedes, reportes y pedidos usan las claves del modelo nuevo. El pago se registra después de crear la estancia, con su FK real.
- TransaccionesHotel coordina Spring JDBC y un bloqueo transaccional por hotel. Registrar una ocupación confirma habitación, estancia, personas y pago juntos.
- El catálogo histórico de un pedido conserva código, nombre y precio aunque se retire el producto vigente. La tarifa histórica de una estancia tampoco cambia al editar las tarifas actuales.
- El backend obtiene hoteles y áreas de SQL Server; se conserva el perfil memoria explícito para desarrollo y pruebas.

Spring Boot mantiene el ciclo de vida y los perfiles, Spring JDBC ejecuta las operaciones SQL y HikariCP administra el pool. El transporte web sigue siendo jdk.httpserver; no es un WAR Tomcat. Las sesiones HTTP terminan al reiniciar y requieren volver a ingresar; los datos del negocio permanecen.

## Entregables y respaldo

- [SQL para ejecutar directamente en SSMS](../../base-datos/sqlserver/CREAR_GESTERA_SQLSERVER.sql).
- [Diccionario de 102 atributos y diagrama ER](../database-design/SQLSERVER_14_TABLAS.md).
- [Frameworks y puntos de extensión](FRAMEWORKS_Y_ARQUITECTURA.md).
- [Resultados de pruebas e instalación](../../base-datos/sqlserver/VALIDACION.md).

El respaldo previo fue verificado con CHECKSUM y RESTORE VERIFYONLY y está en:

    C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\gestera_antes_migracion_94ce7a9ced2b4449a13973561248ef00.bak

Se conservó el diagrama existente de SSMS y su tabla técnica sysdiagrams. Por ello SSMS puede mostrar 15 tablas físicas: 14 del programa y una de metadatos de diagramas. No debe borrarse esta última para intentar ajustar el conteo.

## Continuación

Cambiar el DDL modular de base-datos/sqlserver/esquema y regenerar el instalador antes de entregar futuras migraciones. Actualizar a la vez los repositorios y el diccionario; CI verifica que los archivos generados coincidan. Ejecutar probar-sqlserver.ps1 -MigrarDesdeV1 para comprobar rollback, migración histórica, 11 pruebas SQL/HTTP y limpieza de la base temporal. Los registros de esta actualización quedan en .compilacion/modelo14/.
