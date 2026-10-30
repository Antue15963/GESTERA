# Guía para exponer Gestera

Esta guía describe las carpetas y la implementación actual del proyecto. Incluye un guion para una persona, de aproximadamente 7 a 9 minutos según el ritmo y las pausas de la demostración.

## 1. Qué es el programa

Gestera es una aplicación web de gestión operativa hotelera. Se ejecuta mediante un servidor Java y se utiliza desde un navegador. Permite controlar habitaciones, registrar huéspedes, organizar la limpieza, gestionar pedidos de servicio al cuarto, registrar el uso de áreas comunes y consultar reportes de ingresos.

El catálogo contiene cuatro hoteles. El acceso se realiza en dos etapas: una llave general y una llave independiente para el hotel elegido. Los datos operativos se mantienen separados por establecimiento.

Las tecnologías principales son Java 17, Spring Boot, Spring JDBC, HikariCP, SQL Server, HTML, CSS y JavaScript. Maven administra las dependencias y el empaquetado. Spring Boot administra el arranque y la infraestructura; las solicitudes web se atienden con `HttpServer`, incluido en Java.

## 2. Cómo se dividen las carpetas

```text
GESTERA/
├── frontend/            Interfaz que utiliza la persona
│   ├── vistas/          Plantillas HTML: paginas y fragmentos
│   └── publico/         Estilos CSS, JavaScript e imágenes
├── backend/src/         Código Java y pruebas
│   ├── main/
│   │   ├── java/com/gestera/
│   │   │   ├── modelo/          Datos y reglas por función
│   │   │   ├── controladores/   Coordinación de solicitudes
│   │   │   ├── vistas/          Renderizado HTML en Java
│   │   │   ├── repositorios/    Contratos y adaptadores de datos
│   │   │   ├── seguridad/      Autenticación y sesiones
│   │   │   └── infraestructura/ Configuración, HTTP y transacciones
│   │   └── resources/   Configuración de Spring y registros
│   └── test/java/       Pruebas automatizadas por capas
├── base-datos/          Instalador, esquema y pruebas de la base de datos
├── docs/                Documentación, diagramas y entregables
├── herramientas/        Scripts para compilar, ejecutar, probar y mantener
├── distribucion/        Archivo ejecutable gestera.jar
├── target/              Resultados generados por Maven
├── .compilacion/        Apoyo local, registros y archivos temporales
├── .mvn/                Configuración de Maven Wrapper
├── .github/             Automatización de verificaciones en GitHub
└── .git/                Historial y metadatos de Git
```

### `backend/src/main/java/com/gestera`: funcionamiento del programa

`com/gestera` identifica el paquete base del proyecto. Dentro se encuentra `GesteraAplicacion.java`, cuya función `main` inicia Spring Boot.

| Carpeta | Qué contiene y para qué sirve | Ejemplo real |
|---|---|---|
| `modelo/areas` | Reglas del uso de jacuzzi, sauna y área de juegos. | `GestorAreasComunes.java` |
| `seguridad/autenticacion` | Validación de la llave general y limitación de intentos fallidos. | `AutenticadorLlave.java`, `LimitadorIntentos.java` |
| `infraestructura/configuracion` | Parámetros de ejecución, conexión con SQL Server y creación de la infraestructura administrada por Spring. | `ConfiguracionSqlServer.java`, `InfraestructuraSpring.java` |
| `controladores` | Reciben solicitudes de los módulos, comprueban la autorización, leen formularios y coordinan la respuesta. | `ControladorHabitaciones.java` |
| `modelo/habitaciones` | Inventario, secciones tarifarias, estados, horarios y ampliaciones. | `InventarioHotel.java`, `GestorHabitacionesHoteles.java` |
| `modelo/hoteles` | Catálogo de los cuatro hoteles y administración de sus llaves independientes. | `CatalogoHoteles.java`, `GestorLlavesHoteles.java` |
| `modelo/huespedes` | Datos personales, tipos de documento, validaciones, estancias, historial y búsquedas. | `DatosHuesped.java`, `GestorHuespedesHoteles.java` |
| `infraestructura/persistencia` | Coordinación de transacciones para guardar juntas las operaciones relacionadas de un hotel. | `TransaccionesHotel.java` |
| `modelo/reportes` | Filtros, registros de ingresos y cálculos del dashboard. | `GestorReportes.java`, `FiltroReporte.java` |
| `modelo/servicios` | Productos, catálogo, pedidos de servicio al cuarto y cambios de estado. | `PedidoServicio.java`, `GestorServicioHabitacion.java` |
| `infraestructura/servidor` | Servidor HTTP, direcciones de las páginas, lectura de solicitudes y construcción de respuestas. | `ServidorGestera.java`, `RutasGestera.java` |
| `seguridad/sesiones` | Sesiones temporales y autorización de acceso a los hoteles. | `GestorSesiones.java` |
| `vistas` | Carga de plantillas, generación de componentes HTML y entrega de recursos web. | `PlantillasWeb.java`, `ComponentesHtml.java` |
| `repositorios` | Contratos y adaptadores de acceso a datos, separados por habitaciones, huéspedes, hoteles, áreas, reportes y servicios. | `habitaciones/RepositorioInventarioJdbc.java` |

Los repositorios se encuentran en su propia capa: por ejemplo, `RepositorioInventarioJdbc.java` está en `repositorios/habitaciones` y `RepositorioHuespedesJdbc.java` está en `repositorios/huespedes`. La carpeta `infraestructura/persistencia` contiene la coordinación compartida de transacciones.

El modelo MVC está en `modelo`; el controlador está en `controladores`; y la vista comprende los renderizadores Java de `vistas` y los HTML, CSS y JavaScript de `frontend`. Los gestores constituyen los servicios de negocio. El [mapa de funciones y estructuras](desarrollo/ESTRUCTURA_Y_MVC.md) muestra cómo localizar cada operación.

Tres nombres ayudan a explicar el código:

- **Controlador:** recibe la acción del navegador y coordina lo que debe suceder.
- **Gestor:** aplica las reglas de funcionamiento, como validar una ocupación o calcular un reporte.
- **Repositorio:** consulta o guarda información. Las implementaciones `Jdbc` acceden a SQL Server; las variantes `Memoria` guardan información temporal durante la ejecución.

### `frontend`: interfaz

| Carpeta o archivo | Contenido |
|---|---|
| `vistas/paginas/` | Pantallas HTML de acceso, selección de hotel, gestión, habitaciones, huéspedes, áreas comunes, servicio al cuarto, configuración y reportes. |
| `vistas/fragmentos/` | Partes HTML reutilizables, como formularios de cambio de llave. |
| `publico/estilos/` | `gestera.css`: colores, tamaños, distribución y apariencia de las pantallas. |
| `publico/scripts/` | JavaScript para formularios, filtros, ventanas, alertas, tema visual y vistas previas. |
| `publico/imagenes/` | Imágenes utilizadas por la interfaz. |

### `backend/src/main/resources`: configuración

| Archivo | Contenido |
|---|---|
| `application.properties` | Configuración general de Spring y selección del perfil predeterminado. |
| `application-sqlserver.properties` | Configuración del perfil que utiliza SQL Server. |
| `application-memoria.properties` | Configuración del perfil temporal en memoria. |
| `logback.xml` | Configuración de los registros de ejecución y errores. |

HTML define el contenido; CSS define la apariencia; JavaScript agrega interacción. El servidor vuelve a validar los datos y calcula los importes y horarios definitivos.

### `backend/src/test/java`: pruebas

Contiene pruebas organizadas por módulos, como autenticación, sesiones, habitaciones, huéspedes, servicios y reportes. También incluye pruebas del servidor, del arranque con Spring y de integración con SQL Server.

Las pruebas permiten comprobar comportamientos y detectar errores al modificar el proyecto.

### `base-datos`: estructura de la información

| Subcarpeta | Contenido |
|---|---|
| `sqlserver/` | Implementación vigente. Incluye `CREAR_GESTERA_SQLSERVER.sql`, el instalador para crear o migrar la base desde SSMS. |
| `sqlserver/esquema/` | Fuentes SQL separadas: tablas, datos iniciales, importación del modelo anterior y reglas de integridad. |
| `sqlserver/pruebas/` | Datos y consultas para verificar migraciones. |
| `sqlserver/historico/` | Instalador y documentación del modelo anterior de siete tablas. |
| `archivo-mysql/` | Migraciones de la implementación anterior, conservadas como referencia histórica. |

El modelo vigente tiene 14 tablas del programa: hoteles, credenciales, secciones tarifarias, habitaciones, áreas comunes, huéspedes, estancias, relación estancia-huéspedes, pagos, catálogos, categorías, productos, pedidos e ítems de pedidos.

SQL Server almacena y consulta los datos. SSMS es la herramienta con la que se administra ese motor y se ejecuta el instalador.

### `docs`: explicación y entregables

| Subcarpeta | Contenido |
|---|---|
| `desarrollo/` | Arquitectura vigente, tecnologías, modelo de 14 tablas y migración a SQL Server. |
| `database-design/` | Diccionario vigente `SQLSERVER_14_TABLAS.md`, diagramas y archivos de diseño. También conserva materiales anteriores; algunos documentos están marcados como históricos. |
| `database-design/01-logical/` | Material del diseño lógico. |
| `database-design/02-physical/` | Material del diseño físico. |
| `database-design/03-uml/` | Diagramas UML. |
| `database-design/04-sql/` | Archivo SQL de diseño conservado en la documentación. El instalador vigente está en `base-datos/sqlserver/`. |
| `database-design/05-documentation/` | Documentación del diseño. |
| `documentacion-sistema/` | Descripción, manual, funciones, guías, preguntas frecuentes y documentación Java. Parte del material corresponde a la etapa anterior del sistema. |
| `entregables/` | PDF de indicadores, muestras del dashboard y diagramas preparados para presentar. |

Para explicar la implementación actual, conviene apoyarse en `docs/desarrollo/FRAMEWORKS_Y_ARQUITECTURA.md` y `docs/database-design/SQLSERVER_14_TABLAS.md`.

### Carpetas de ejecución y herramientas

- **`herramientas/`:** scripts para compilar, iniciar, reiniciar, probar, limpiar, respaldar SQL Server y generar documentación o diagramas.
- **`distribucion/`:** contiene `gestera.jar`, el paquete ejecutable con el programa y sus dependencias.
- **`target/`:** contiene clases compiladas, recursos copiados, clases de pruebas, reportes de pruebas y otros resultados que genera Maven.
- **`.compilacion/`:** contiene archivos de apoyo, registros de ejecución, la instalación local de Maven usada por los scripts, la biblioteca nativa de autenticación de SQL Server y resultados temporales.
- **`.mvn/wrapper/`:** contiene la configuración del lanzador de Maven.
- **`.github/workflows/`:** contiene `ci.yml`, que configura verificaciones automáticas al enviar cambios o abrir una solicitud de integración en GitHub.
- **`.git/`:** contiene el historial y los metadatos del control de versiones.

### Archivos de la carpeta principal

| Archivo | Función |
|---|---|
| `README.md` | Presentación del proyecto, requisitos, instrucciones y funcionamiento. |
| `pom.xml` | Define dependencias, versión de Java y configuración de compilación, pruebas y empaquetado. |
| `mvnw.cmd` | Lanzador de Maven Wrapper para Windows. |
| `COMPILAR_GESTERA.cmd` | Ejecuta la compilación y genera el paquete. |
| `INICIAR_GESTERA.cmd` | Compila e inicia el programa. |
| `.gitignore` | Indica qué archivos generados o locales no se incluyen en el historial de Git. |

## 3. Un ejemplo para conectar todas las carpetas

Cuando el operador registra una habitación ocupada:

1. La pantalla de `frontend/vistas/paginas` muestra el formulario, con estilos CSS e interacción JavaScript.
2. El navegador envía los datos al servidor; las rutas identifican la operación.
3. `ControladorHabitaciones` comprueba la autorización y coordina la solicitud.
4. Los gestores validan huéspedes, estado, tarifa y horarios.
5. Los repositorios guardan habitación, estancia, personas y pago en SQL Server. La transacción permite confirmar todo el conjunto o revertirlo si falla una parte.
6. El servidor responde y el navegador muestra la información actualizada.

Puedes decir: «La pantalla recoge la información, el controlador coordina, los gestores aplican las reglas y los repositorios guardan los datos».

## 4. Guion para exponer

Las indicaciones entre corchetes son acciones para el expositor; no hace falta leerlas en voz alta.

### Presentación

[Mostrar la página de acceso.]

«Buenos días. Hoy voy a presentar Gestera, una aplicación web para organizar las tareas diarias de un hotel. Su objetivo es relacionar la disponibilidad de habitaciones con los huéspedes, los servicios solicitados y los ingresos registrados.

El programa se utiliza desde un navegador y su funcionamiento está desarrollado principalmente en Java. Para mostrar cómo está construido, primero explicaré sus carpetas y después recorreré sus principales funciones».

### Organización del proyecto

[Mostrar la carpeta principal del proyecto.]

«La estructura se divide en frontend, backend, base de datos, documentación, herramientas y archivos generados.

Frontend contiene las pantallas HTML y los recursos visuales. Backend contiene el código Java, la configuración y las pruebas automatizadas.

En el código Java distinguimos modelo, vista y controlador. El modelo está organizado por habitaciones, hoteles, huéspedes, áreas comunes, servicios y reportes. Los controladores coordinan las solicitudes y las vistas preparan el HTML. También separamos los repositorios para consultar y guardar datos, la seguridad para el acceso y las sesiones, y la infraestructura para la configuración, el servidor y las transacciones.

Esta distribución permite ubicar cada responsabilidad. Si necesitamos cambiar las reglas de una habitación, buscamos su módulo. Si queremos cambiar el diseño de una pantalla, trabajamos en los recursos de la interfaz».

### Tecnologías y base de datos

[Mostrar brevemente pom.xml, frontend, backend y base-datos/sqlserver.]

«La interfaz utiliza HTML para el contenido, CSS para el diseño y JavaScript para la interacción. El servidor utiliza Java 17 y Spring Boot. Las consultas a la base se realizan con Spring JDBC y las conexiones se administran con HikariCP.

La información se almacena en SQL Server. En base-datos se encuentra el instalador que se ejecuta desde SQL Server Management Studio, también llamado SSMS. El modelo vigente tiene 14 tablas relacionadas para guardar hoteles, habitaciones, huéspedes, estancias, pagos y pedidos, entre otros datos.

En docs tenemos los manuales, diagramas y entregables. En herramientas encontramos los scripts para compilar, ejecutar, probar y respaldar. El resultado del empaquetado es gestera.jar, ubicado en distribucion».

### Acceso al hotel

[Ingresar con las llaves preparadas para la demostración y seleccionar un hotel.]

«El acceso tiene dos etapas. Primero se valida una llave general y luego se solicita la llave del hotel seleccionado. De esta manera, la autorización queda asociada a ese establecimiento.

El catálogo incluye cuatro hoteles y cada uno mantiene su propia información operativa. En el panel tenemos seis módulos principales y una opción de configuración para administrar la cantidad de habitaciones, las secciones tarifarias y la llave del hotel».

### Habitaciones, huéspedes y limpieza

[Abrir Habitaciones y mostrar el formulario de una habitación.]

«En Habitaciones podemos consultar la disponibilidad y registrar una ocupación. Elegimos una sección tarifaria y un servicio de seis horas o de día completo, que equivale a veinticuatro horas.

Para ocupar una habitación se registran los datos de uno a ocho huéspedes. El sistema valida sus documentos y calcula los horarios y el importe en el servidor. También permite registrar ampliaciones de tiempo con cargos adicionales y controlar si la estancia está pagada.

Las habitaciones pueden estar disponibles, ocupadas, con limpieza pendiente, en limpieza o en mantenimiento. Las alertas ayudan a identificar las salidas próximas o vencidas».

[Abrir Huéspedes y después Limpieza.]

«Los datos de las personas aparecen en el módulo de Huéspedes, donde se puede consultar su historial y filtrar los registros. Cuando se confirma una salida vencida, la habitación pasa a limpieza pendiente. El módulo de Limpieza muestra los cuartos que requieren esa atención».

### Servicios, áreas comunes y reportes

[Abrir brevemente los tres módulos.]

«Servicio al cuarto permite registrar pedidos para habitaciones ocupadas, administrar un catálogo y seguir los estados de pendiente, en preparación y entregado. También permite cancelar un pedido antes de que sea entregado.

Áreas comunes registra visitantes y horarios de uso del jacuzzi, sauna y área de juegos.

Reportes de ingresos reúne las estancias marcadas como pagadas y los pedidos entregados. Podemos filtrar por periodo y por origen del ingreso, y consultar indicadores y gráficos. Los indicadores operativos actuales se muestran diferenciados de los ingresos del periodo».

### Explicación de una operación y cierre

[Volver al proyecto o al panel principal.]

«Para entender cómo se conectan las partes, pensemos en registrar una ocupación. La pantalla envía el formulario, el controlador coordina la solicitud, los gestores validan las reglas y los repositorios guardan los datos en SQL Server. La respuesta permite mostrar el resultado actualizado.

Esta organización facilita encontrar el código, realizar cambios y comprobar el funcionamiento mediante pruebas. Con Gestera podemos seguir la operación de una estancia desde el registro de los huéspedes hasta los servicios, el pago y la limpieza de la habitación. Muchas gracias».

## 5. Respuestas breves para posibles preguntas

**¿Cuál es la diferencia entre frontend y backend?**

El frontend es la interfaz que aparece en el navegador. El backend recibe las solicitudes, aplica las reglas y se comunica con la base de datos.

**¿Para qué sirve Spring Boot en este programa?**

Administra el arranque, la configuración, los componentes y el cierre. El transporte HTTP actual utiliza el servidor incluido en Java.

**¿Qué diferencia hay entre SQL Server y SSMS?**

SQL Server es el motor que guarda los datos. SSMS es la herramienta para administrarlo y ejecutar instrucciones SQL.

**¿Por qué existen repositorios Jdbc y Memoria?**

Permiten utilizar almacenamiento persistente o temporal detrás de contratos comunes. SQL Server es el perfil predeterminado; memoria debe elegirse expresamente.

**¿Qué pasa al reiniciar el programa?**

Con SQL Server se conservan los datos guardados. En el perfil memoria se pierden los cambios temporales. Las sesiones de acceso son temporales.

**¿Qué aporta una transacción?**

Permite guardar juntas las partes de una operación. Si falla una parte, se revierten los cambios de esa transacción.

**¿Por qué la limpieza no tiene una carpeta Java propia?**

Comparte el inventario y la lógica del módulo de habitaciones. Su vista muestra las habitaciones en los estados de limpieza.

**¿Dónde está la base de datos real?**

En la instancia de SQL Server configurada. La carpeta base-datos contiene los archivos SQL para construir y mantener su estructura.

## 6. Preparación de la demostración

- Iniciar el programa con tiempo y comprobar que abre en `http://127.0.0.1:8080`, si se conserva la configuración predeterminada.
- Tener a mano las llaves vigentes y resolver previamente el cambio inicial si la aplicación lo solicita.
- Preparar datos ficticios para la demostración y conocer qué habitación se va a mostrar.
- Mantener disponibles la carpeta del proyecto, el navegador y los diagramas que se quieran explicar.
- Mostrar primero el recorrido de uso y explicar cada carpeta con su función, sin leer todos los nombres de archivos.
- Si se dispone de menos tiempo, resumir las carpetas auxiliares y concentrarse en frontend, backend, base-datos y el recorrido de una ocupación.
