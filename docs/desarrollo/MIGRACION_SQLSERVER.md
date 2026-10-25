> Registro histórico de la primera migración de MySQL a SQL Server (siete tablas). La actualización vigente de 14 tablas está documentada en [MODELO_14_TABLAS.md](MODELO_14_TABLAS.md).

# Migración a SQL Server y limpieza

Fecha: 24 de octubre de 2026.

## Resultado

La aplicación utiliza Spring Boot, Spring JDBC, HikariCP y Microsoft JDBC Driver. La base gestera quedó instalada/verificada en SQL Server local. Con autorización del usuario, se habilitó TCP/IP en 1433 y se reinició MSSQLSERVER, restaurando los servicios dependientes que estaban activos.

El JAR actualizado respondió HTTP 200 en http://127.0.0.1:8080. En las sesiones del motor se verificó la aplicación Gestera con transporte TCP, cifrado activo y autenticación integrada NTLM. SSMS y el backend utilizan el mismo motor SQL Server.

## Código y dependencias

- Sustituidos ConfiguracionMySql y ProveedorConexionesMySql por configuración SQL Server y un DataSource HikariCP administrado por Spring.
- Retirados el puerto de conexiones propio, los constructores de conexión obsoletos y module-info.java; el ejecutable usa el classpath administrado por Spring Boot.
- Reescritos los tres repositorios con JdbcTemplate y TransactionTemplate, eliminando instrucciones exclusivas del motor anterior y la repetición de apertura, commit, rollback y cierre manual.
- Los UPSERT mantienen bloqueos sobre la clave natural y recuperan correctamente IDENTITY o el identificador existente.
- Adaptados el lanzador, la detección del JDK y la herramienta de revisión de interfaz al JAR ejecutable Spring Boot.
- Incorporados JUnit, el arranque verificable del contexto y un workflow de CI para Java 17. El workflow está preparado; no se ejecutó en GitHub durante esta tarea.

## Pruebas realizadas

| Comprobación | Resultado |
|---|---|
| Compilación y Maven verify con JDK 17 | Correctos. |
| Suite funcional previa | 145 comprobaciones correctas. |
| Configuración SQL Server | Tres pruebas JUnit correctas. |
| Arranque y cierre con Spring, perfil memoria | Prueba JUnit correcta; página HTTP 200. |
| Repositorios con motor SQL Server real | Cuatro pruebas JUnit correctas, sin omisiones. |
| Identidad y actualización de estancia | Misma identidad, una sola estancia y conservación de ceros iniciales/Unicode. |
| Rollback de huésped inválido | Se conserva la estancia y su lista anterior. |
| Áreas comunes | Habitación NULL y ubicación conservada. |
| Pagos concurrentes | Seis solicitudes simultáneas conservan un solo pago; actualización y reapertura verificadas. |
| Pedido y líneas | Importe exacto, recuperación de ID y rollback de cabecera ante ítem inválido. |
| Estado del pedido | Una actualización válida devuelve una fila; repetir el estado anterior devuelve cero. |
| Despliegue local del JAR | HTTP 200 y conexión JDBC cifrada al motor. |

Las pruebas SQL se realizaron en una base con nombre aleatorio gestera_pruebas_*. El script eliminó únicamente esa base al finalizar. La ejecución normal de Maven omite esas cuatro pruebas si no se define su URL; sí se ejecutaron aparte mediante herramientas/probar-sqlserver.ps1.

## Limpieza

Se eliminaron 82 448 590 bytes de compilaciones anteriores, JAR antiguos de ejecución, dependencias duplicadas de distribución, capturas y perfiles de pruebas visuales y archivos temporales de validación. Se regeneró el único JAR necesario para ejecutar la aplicación.

Las migraciones anteriores se archivaron en base-datos/archivo-mysql. Se conservaron las fuentes, pruebas, entregables y documentos históricos. La caché Maven y la DLL de autenticación son recursos de desarrollo necesarios.

herramientas/limpiar.ps1 permite repetir la limpieza con una lista de rutas autorizadas y vista previa -WhatIf. Comprueba que las rutas pertenezcan al proyecto, rechaza enlaces y conserva los JAR de ejecución que estén abiertos.

## Desarrollo siguiente

Consultar [FRAMEWORKS_Y_ARQUITECTURA.md](FRAMEWORKS_Y_ARQUITECTURA.md). Inventario, tarifas, credenciales hoteleras y catálogo editable continúan en memoria. Sus repositorios y la migración de esos datos son el siguiente trabajo; las siete tablas actuales ya conservan huéspedes, estancias, pedidos e ingresos.

La autenticación integrada local utiliza la identidad Windows del proceso. Para producción, provisionar una identidad con el rol gestera_aplicacion y un certificado confiable, siguiendo la guía de SQL Server.
