> Referencia histórica anterior al modelo SQL Server V2. El esquema implementado, sus 14 tablas y atributos se describen en [SQLSERVER_14_TABLAS.md](database-design/SQLSERVER_14_TABLAS.md).

> La organización vigente de frontend, backend y las capas MVC se describe en [ESTRUCTURA_Y_MVC.md](desarrollo/ESTRUCTURA_Y_MVC.md). Este documento conserva la arquitectura de la etapa anterior.

# Arquitectura Java de Gestera

Gestera usa Java 17 y separa la aplicación en módulos de dominio, controladores HTTP, vistas, servicios de aplicación y adaptadores de persistencia. El servidor usa el `HttpServer` incluido en el JDK; no requiere un framework web para arrancar.

## MVC

| Capa | Responsabilidad | Clases representativas |
|---|---|---|
| Modelo | Datos y reglas del hotel, habitaciones, huéspedes, pedidos e ingresos | `Hotel`, `Habitacion`, `DatosHuesped`, `GestorHabitacionesHoteles`, `GestorHuespedesHoteles`, `GestorReportes` |
| Vista | Crear el HTML y cargar recursos de la interfaz | `PlantillasWeb`, `ComponentesHtml`, `RecursosWeb`, plantillas en `src/main/resources/` |
| Controlador | Autorizar la solicitud, interpretar parámetros, invocar servicios y elegir la respuesta | `ControladorHuespedes`, `ControladorAreasComunes`, `ControladorServicioHabitacion`, `ControladorReportes`, `ControladorHabitaciones` y `ControladorConfiguracionHotel` |

`ServidorGestera` conserva el ciclo de vida HTTP, la autenticación, las sesiones y la conexión de rutas. Los controladores reciben los servicios de dominio y las vistas por constructor, de modo que coordinan la solicitud sin mezclar reglas de negocio ni acceso a datos.

## DAO y persistencia

Las interfaces `RepositorioHuespedes`, `RepositorioServicioHabitacion` y `RepositorioIngresosHabitacion` son los contratos DAO. Las implementaciones `*Memoria` y `*Jdbc` permiten cambiar el mecanismo de persistencia sin cambiar los gestores de negocio.

Los DAO JDBC reciben `ProveedorConexiones`, en lugar de crear conexiones por su cuenta. `ProveedorConexionesMySql` adapta `DriverManager` y MySQL Connector/J a ese contrato. En pruebas se inyecta un proveedor falso; en despliegues se puede conectar cualquier `DataSource` JDBC o pool compatible sin acoplar las reglas de negocio a esa librería.

## SOLID aplicado

- **Responsabilidad única:** los gestores mantienen reglas de negocio; las plantillas renderizan; los repositorios guardan o consultan datos; los controladores coordinan solicitudes.
- **Abierto/cerrado:** se pueden agregar implementaciones de repositorio y proveedores JDBC mediante interfaces sin alterar los gestores.
- **Sustitución de Liskov:** las variantes en memoria y JDBC implementan los mismos contratos de persistencia.
- **Segregación de interfaces:** huéspedes, pedidos e ingresos usan contratos DAO separados por dominio.
- **Inversión de dependencias:** gestores dependen de interfaces DAO; los DAO JDBC dependen de `ProveedorConexiones`, no de `DriverManager` directamente.

## TDD y pruebas

Las pruebas están en `backend/src/test/java/` y se ejecutan con `herramientas/probar.ps1`. Maven compila y ejecuta `TodasLasPruebas` durante la fase `test`; las nuevas pruebas siguen el ciclo **rojo, verde y refactor**: describen primero el comportamiento, se implementa hasta pasar y luego se simplifica con la regresión cubierta.

`RepositorioIngresosHabitacionJdbcPrueba` comprueba que un DAO usa el proveedor inyectado, traduce un error JDBC a una excepción de persistencia y conserva la causa original, sin requerir una instancia MySQL.

## Librerías Java

El proyecto usa APIs del JDK y librerías externas con una función concreta:

- `jdk.httpserver`: servidor HTTP ligero.
- `java.sql` y JDBC: sentencias parametrizadas, transacciones y acceso a MySQL.
- `java.time`: horarios, fechas y zona horaria de Perú.
- `java.util` y `java.util.concurrent`: colecciones, validaciones y ejecución concurrente.
- `java.security` y `java.nio`: operaciones criptográficas y manejo de recursos cuando corresponde.
- SLF4J API 2.0.20 y Logback Classic 1.6.5: registro configurable de inicio, cierre y errores HTTP.
- MySQL Connector/J: controlador JDBC externo, opcional y local en `bibliotecas/`.

Maven Wrapper gestiona las dependencias y compila para Java 17. Logback es la librería elegida para registro; Guava, Apache POI y Apache Commons no se añaden porque el proyecto actual no necesita sus funciones.
