# Validación vigente: SQL Server V2, 14 tablas

Fecha: 24 de octubre de 2026. Motor real: SQL Server 17.0.1000.7 Standard Developer Edition. Java 17, Spring Boot 4.1.2, Spring JDBC, HikariCP y driver Microsoft 13.6.0.jre11.

## Archivo entregable

- CREAR_GESTERA_SQLSERVER.sql, UTF-8 con BOM, ejecución normal F5 en SSMS.
- Tamaño: 38977 bytes.
- SHA-256: 788E2503FBE63BF13E794D2D3F7B4028D31D38CE4DBAE3AB84F1D6C71F5A9CFA.
- Marca activa: GESTERA_SQLSERVER_14_V2.
- Fuentes modulares y artefacto generado coincidentes; diccionario reproducible validado.

## Resultados comprobados

| Comprobación | Resultado |
|---|---|
| Compilación y empaquetado Java 17 | Maven verify correcto, JAR Spring Boot generado. |
| Funcionalidad previa | 145 comprobaciones correctas. |
| Configuración y ciclo de vida Spring | 3 pruebas de configuración y 1 de arranque correctas. |
| JDBC y HTTP con SQL Server real | 11 pruebas, cero errores, fallos u omisiones en la ejecución SQL. |
| Migración histórica V1 → V2 | Conserva personas, identificadores, asociaciones, dos pagos con total 95.50 y pedidos con precio histórico. |
| Error durante migración | Una ocupación antigua activa sin tarifa provoca rollback completo; esquema y dinero V1 intactos. |
| Metadatos SSMS | Fixture de sysdiagrams conservado con contenido binario; el diagrama local existente también se conserva. |
| Idempotencia | Instalación nueva repetida y ejecución V2 repetida en gestera correctas. |
| Concurrencia | Pagos simultáneos, versión de catálogo y credenciales, y transacciones por hotel comprobados. |
| Integridad | Rechaza FK entre hoteles, dos ocupaciones activas del cuarto y pedido sin estancia ocupada; rollback de cabecera si falla un ítem. |
| Reinicio HTTP | Llave cambiada, ocupación, pago y pedido entregado siguen disponibles al reconstruir el servidor SQL. |
| Inventario e historia | Tarifas y ampliaciones persistentes; baja lógica conserva historia; reocupar en el mismo minuto no pisa la estancia anterior. |
| Instalación local final | 14 tablas operativas, 102 columnas, 16 FK, 31 CHECK y 15 triggers. Cero FK deshabilitadas o sin validar. |
| Datos locales | 4 hoteles, 4 credenciales, 12 secciones, 48 habitaciones, 12 áreas, 4 catálogos, 12 categorías y 20 productos. |
| Conexión | Sesiones Gestera por TCP, encrypt_option=TRUE. Página y formularios accesibles. |
| Comparación previa/posterior | Inventario visible de 48 habitaciones y valores de configuración/catálogo de los 4 hoteles coincidentes. Las categorías pueden aparecer en distinto orden. |

La base local no contenía huéspedes, estancias, pagos ni pedidos antes de actualizar; continúa sin registros operativos ficticios. Las pruebas usaron bases aleatorias aisladas, eliminadas al terminar. La ejecución normal Maven omite las 11 pruebas SQL si no se define su URL; se ejecutaron separadamente mediante probar-sqlserver.ps1 -MigrarDesdeV1.

## Respaldo y alcance

Antes de actualizar gestera se detuvo el proceso de la aplicación y se creó un respaldo COPY_ONLY con CHECKSUM. RESTORE VERIFYONLY confirmó su validez:

    C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\gestera_antes_migracion_94ce7a9ced2b4449a13973561248ef00.bak

RESTORE VERIFYONLY valida el respaldo; no sustituye una prueba completa de recuperación, que no se realizó sobre la base local. SQL Server conserva una tabla técnica adicional dbo.sysdiagrams con un diagrama de SSMS: no forma parte de las 14 tablas operativas y no se elimina. No se probó el instalador contra todas las versiones anteriores del motor.

La validación anterior de siete tablas queda en [el archivo histórico](historico/VALIDACION_7_TABLAS.md). Consultar [modelo y atributos](../../docs/database-design/SQLSERVER_14_TABLAS.md) y [registro de la actualización](../../docs/desarrollo/MODELO_14_TABLAS.md).
