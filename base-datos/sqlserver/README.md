# SQL Server: instalación y modelo de 14 tablas

El instalador [CREAR_GESTERA_SQLSERVER.sql](CREAR_GESTERA_SQLSERVER.sql) crea la base gestera, sus 14 tablas, restricciones, índices, triggers, rol de aplicación y datos iniciales. Se ejecuta directamente con F5 en SSMS, sin activar modo SQLCMD. SSMS es la herramienta de administración; el motor al que Java se conecta es SQL Server.

## Instalar o actualizar

1. Detener Gestera para impedir escrituras durante la actualización. Respaldar una base existente con herramientas/respaldar-sqlserver.ps1; el script usa COPY_ONLY y verifica el archivo con RESTORE VERIFYONLY y CHECKSUM.
2. Abrir el instalador en SSMS, conectado a la instancia correcta, y ejecutar todo el archivo. Necesita permisos de creación de base y esquema.
3. Confirmar 14 tablas y la propiedad Gestera.Instalador = GESTERA_SQLSERVER_14_V2. Puede repetirse sin duplicar los datos iniciales.
4. Iniciar Gestera con herramientas/ejecutar.ps1, perfil sqlserver predeterminado.

El instalador reconoce el esquema anterior de siete tablas y lo migra en una transacción. Conserva identificadores, personas, asociaciones, pagos y pedidos con sus importes históricos. Un historial sin tarifa conocida se marca IMPORTADA y mantiene NULL en el desglose desconocido. Rechaza una estancia activa antigua sin información tarifaria, revierte el cambio y exige resolverla antes. No acepta arbitrariamente un esquema desconocido.

Si SSMS creó dbo.sysdiagrams, esa tabla técnica se conserva y se excluye del conteo operativo. El explorador puede mostrar 15 tablas físicas: 14 del programa y la tabla de diagramas.

En una instalación nueva agrega cuatro hoteles, tres secciones, doce habitaciones y tres áreas por hotel, además del catálogo inicial. Las credenciales se inicializan desde Java como material PBKDF2 con sal; una llave ya registrada no se sustituye al reiniciar ni al cambiar las variables iniciales.

## Conexión Java

URL local: jdbc:sqlserver://localhost:1433;databaseName=gestera;integratedSecurity=true;encrypt=true;trustServerCertificate=true.

Java utiliza la identidad de Windows del proceso y el driver Microsoft 13.6 con su biblioteca nativa. Maven y los scripts del proyecto preparan esa biblioteca. TCP/IP debe estar habilitado y escuchando en el puerto indicado. SSMS puede conectarse por memoria compartida aunque TCP esté deshabilitado; Java usa TCP.

Para otra instancia o puerto, definir GESTERA_DB_URL. Para autenticación SQL, quitar integratedSecurity=true de la URL y configurar GESTERA_DB_USUARIO y GESTERA_DB_CONTRASENA fuera del repositorio. En producción, configurar certificado confiable y trustServerCertificate=false; asignar al usuario el rol gestera_aplicacion. El instalador no crea un login con contraseña compartida.

El perfil memoria debe solicitarse expresamente mediante --spring.profiles.active=memoria. Si SQL Server falla, el perfil sqlserver detiene el arranque.

## Integridad y funcionamiento

Inventario, tarifas, preferencias de servicio, huéspedes, estancias, pagos, áreas, credenciales y catálogo se conservan en SQL Server. La sesión HTTP es temporal. Spring Boot gestiona el ciclo de vida; Spring JDBC ejecuta consultas parametrizadas; HikariCP administra las conexiones.

La ocupación guarda habitación, estancia, personas y pago en una misma transacción. TransaccionesHotel serializa los cambios del mismo hotel mediante sp_getapplock. El catálogo y las credenciales tienen versiones para detectar conflictos. Los pagos y pedidos tienen FK a su estancia; no se aceptan pedidos nuevos para una habitación disponible. Los detalles retienen nombre y precio aunque se elimine el producto actual. Las habitaciones retiradas se desactivan para conservar su historial.

Los horarios de negocio corresponden a America/Lima y se guardan como DATETIME2. Las columnas de auditoría usan UTC, salvo las fechas de negocio de pedidos. DECIMAL evita errores de dinero; NVARCHAR conserva Unicode. Consultar el [diccionario de los 102 atributos y diagrama](../../docs/database-design/SQLSERVER_14_TABLAS.md) para claves y reglas concretas.

## Fuentes y pruebas

Las fuentes están en esquema/. Regenerar el instalador con python herramientas/generar-instalador-sqlserver.py; comprobarlo con --verificar. El diccionario se regenera con herramientas/documentar-modelo-sqlserver.py. historico/ conserva el instalador anterior para pruebas de migración.

- herramientas/probar.ps1: pruebas funcionales, configuración y arranque Spring.
- herramientas/probar-sqlserver.ps1: 11 pruebas JDBC/HTTP con base aleatoria aislada, eliminada al finalizar.
- herramientas/probar-sqlserver.ps1 -SoloEsquema: instalación repetida e idempotencia.
- herramientas/probar-sqlserver.ps1 -MigrarDesdeV1: rechazo con rollback, migración con historial e integración completa.

Las pruebas SQL requieren sqlcmd y permisos para crear bases temporales; no escriben datos de prueba en gestera. Ver [validación](VALIDACION.md) y [frameworks](../../docs/desarrollo/FRAMEWORKS_Y_ARQUITECTURA.md).
