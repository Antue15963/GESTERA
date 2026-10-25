# Frameworks y arquitectura de desarrollo

## Componentes activos

| Componente | Versión / origen | Responsabilidad |
|---|---|---|
| Java | Objetivo 17 | Dominio hotelero y contratos del lenguaje. |
| Spring Boot | 4.1.2, parent Maven | Arranque, perfiles, configuración, composición y cierre. |
| Spring Framework / Spring JDBC | Administrado por Spring Boot | JdbcTemplate, SimpleJdbcInsert, errores y transacciones JDBC. |
| HikariCP | Administrado por Spring Boot | Pool compartido de conexiones. |
| Microsoft JDBC Driver | 13.6.0.jre11 | Conexión con SQL Server desde Java 17. |
| Microsoft JDBC Auth | 13.6.0.x64, perfil Maven Windows | Autenticación con la identidad de Windows. |
| JUnit Jupiter | Administrado por Spring Boot | Pruebas funcionales y de integración. |
| Maven Wrapper | .mvn/wrapper | Compilación, dependencias y empaquetado. |
| SLF4J / Logback | Administrado por Spring Boot | Registros de aplicación. |
| HTML, CSS y JavaScript | Recursos locales | Pantallas existentes. |
| jdk.httpserver | Incluido en Java | Transporte HTTP administrado por Spring. |

Las versiones efectivas están en pom.xml y su parent. Se consultan con mvn dependency:tree. Las dependencias se incluyen en un único JAR; no se copian drivers manualmente.

Spring Boot administra la aplicación y los datos. La capa web conserva HttpExchange y HttpServer; no se ha convertido a Spring MVC ni empaquetado como WAR. Incorporar Tomcat requiere migrar ese transporte y las rutas. Añadir starter-web sin esa migración crearía otro servidor HTTP.

## Recorrido de una solicitud

Navegador → ServidorGestera/rutas → controladores → gestores de dominio → interfaces de repositorio → Spring JDBC → HikariCP DataSource → SQL Server.

GesteraAplicacion inicia Spring. InfraestructuraSpring crea el pool, comprueba el esquema y construye el servidor. sqlserver es el perfil predeterminado; memoria es una opción explícita de desarrollo.

## Dónde continuar

| Cambio | Archivo o carpeta |
|---|---|
| Conexión y pool | configuracion/ConfiguracionSqlServer.java, InfraestructuraSpring.java, application-sqlserver.properties. |
| Reglas de personas y estancias | huespedes/GestorHuespedesHoteles.java. |
| SQL de personas y estancias | huespedes/RepositorioHuespedesJdbc.java. |
| Pedidos y estados | servicios/. |
| Pagos y reportes | reportes/. |
| Rutas y formularios | servidor/RutasGestera.java, SolicitudesHttp.java, controladores/. |
| Pantallas | src/main/resources/paginas, fragmentos y publico. |
| Esquema vigente | base-datos/sqlserver/CREAR_GESTERA_SQLSERVER.sql. |
| Pruebas normales | src/test/java y herramientas/probar.ps1. |
| Integración SQL real | persistencia/SqlServerIntegracionTest.java. |
| Integración continua | .github/workflows/ci.yml, JDK 17 y Maven. |
| Limpieza | herramientas/limpiar.ps1, con vista previa -WhatIf. |

## Reglas de acceso a datos

1. Recibir un DataSource compartido; Spring posee y cierra el pool.
2. Usar parámetros JDBC para valores e indicar dbo en las tablas.
3. Agrupar las operaciones del mismo hecho de negocio con TransactionTemplate.
4. Mantener el bloqueo de la clave natural hasta el commit del UPSERT. La rama INSERT recupera IDENTITY; la rama UPDATE conserva el identificador existente.
5. Conservar las instantáneas históricas de productos, precios y color.
6. Revisar el orden de guardado antes de añadir una FK.
7. Informar un fallo de conexión y no cambiar automáticamente a memoria.
8. Escribir una migración explícita al cambiar un esquema instalado.

## Persistencia del modelo de 14 tablas

El [modelo vigente y diccionario](../database-design/SQLSERVER_14_TABLAS.md) corresponde al instalador y a los repositorios Java. Habitaciones/RepositorioInventarioJdbc persiste secciones, recursos y ocupación; hoteles/RepositorioCredencialesHotelJdbc conserva las credenciales PBKDF2; servicios/RepositorioCatalogoServicioJdbc conserva catálogo, categorías y productos. Los repositorios de huéspedes, pagos y pedidos utilizan las FK de la nueva estancia.

persistencia/TransaccionesHotel comparte la transacción Spring y bloquea por hotel mediante sp_getapplock con propietario Transaction. El controlador de habitaciones confirma el conjunto habitación/estancia/personas/pago después de guardarlo completo. Un fallo revierte el conjunto; el navegador recibe la confirmación después del commit. Catálogo y credenciales incorporan versiones para detectar conflictos. El historial utiliza instantáneas y las habitaciones retiradas se desactivan.

Las fuentes T-SQL viven en base-datos/sqlserver/esquema; herramientas/generar-instalador-sqlserver.py genera el archivo único para SSMS. La migración V1 a V2 es transaccional y se verifica con fixtures históricos y una prueba de rollback. herramientas/respaldar-sqlserver.ps1 genera y verifica el respaldo previo.

## Continuación del desarrollo

1. Mantener sincronizados el esquema modular, el instalador generado, los repositorios JDBC y el diccionario al cambiar un atributo.
2. Agregar las pruebas SQL de nuevas operaciones a SqlServerIntegracionTest y ejecutarlas contra una base temporal.
3. Si se incorporan reservas anticipadas, empleados o roles, diseñar esa migración y sus pantallas. Esas funciones de la referencia visual aún no existen en Gestera.
4. Si se adopta Spring MVC/Tomcat, migrar transporte y sesiones manteniendo las pruebas HTTP. El framework elegido actualmente es Spring Boot con JDBC y HikariCP.
5. Automatizar respaldos y probar la recuperación en una instancia separada antes de un despliegue operativo.

Los antiguos diagramas MySQL se conservan como referencia histórica; el documento SQLSERVER_14_TABLAS.md describe el esquema implementado.

## Referencias oficiales

- [Spring Boot y requisitos](https://docs.spring.io/spring-boot/system-requirements.html).
- [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc.html).
- [HikariCP](https://github.com/brettwooldridge/HikariCP).
- [Driver Microsoft 13.6](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver).
