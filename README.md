# Gestera

El código está separado en **[frontend](frontend/README.md)** y **[backend](backend/README.md)**. Para encontrar una función y ver sus capas, consulta el **[mapa de funciones y arquitectura MVC](docs/desarrollo/ESTRUCTURA_Y_MVC.md)**. La **[guía de exposición](docs/GUIA_EXPOSICION_GESTERA.md)** utiliza esta organización.

Gestera es un módulo web de acceso y selección para una plataforma de gestión hotelera. Utiliza llaves de acceso en dos etapas, sin campos de usuario, contraseña ni credenciales de evaluación visibles en la interfaz. El backend utiliza Java 17, Spring Boot, Spring JDBC y HikariCP con Microsoft SQL Server. SSMS administra e instala la base.

La captura entregada se utilizó únicamente como referencia conceptual para la distribución, la paleta oscura y el acento amarillo; la aplicación no depende de esa imagen ni de recursos web externos.

## Flujo de la aplicación

1. El formulario inicial solicita la llave general del servicio.
2. Después del acceso se muestra un selector con cuatro hoteles registrados y una búsqueda por nombre.
3. Al elegir un hotel se abre un cuadro modal que solicita la llave secundaria del hotel.
4. Si la llave es válida, la sesión queda autorizada para el hotel seleccionado y se abre su panel de gestión.
5. El panel muestra **Cambio de llave de acceso** mientras la configuración sea obligatoria. Después del primer cambio, todo ese bloque desaparece.
6. La opción **Limpieza de habitaciones** sustituye a **Reservas**. Desde el panel también se abren Habitaciones, Huéspedes, Servicio al cuarto, Áreas comunes y el sexto módulo de Reportes de ingresos; el antiguo apartado **Entradas y salidas** fue retirado.
7. La tuerquita de configuración está disponible desde el panel inicial del hotel, Habitaciones, Limpieza y Huéspedes.
8. Al ocupar una habitación se registran dos huéspedes de forma predeterminada; se pueden agregar o quitar filas hasta un máximo de ocho.

Las dos validaciones se realizan en el servidor. Ninguna llave se guarda en la cookie ni se incorpora al HTML o JavaScript enviado al navegador.

El buscador filtra los hoteles por nombre y el contador informa `1 hotel registrado` o `N hoteles registrados`, según la cantidad visible.

El [modelo vigente de 14 tablas](docs/database-design/SQLSERVER_14_TABLAS.md) incluye el diagrama entidad-relación y el diccionario de cada atributo. El [instalador para SSMS](base-datos/sqlserver/CREAR_GESTERA_SQLSERVER.sql) crea o migra la base.

## Requisitos

- JDK 17 o superior, con `java` y `javac` disponibles en `PATH`.
- PowerShell 5.1 o superior.
- SQL Server 2016 o superior y SSMS para instalar y administrar la base.
- Un navegador web actual.
- Maven Wrapper descarga Maven y las dependencias al primer uso; se requiere conexión a Internet.

La arquitectura, los límites entre MVC, los DAO y las pruebas se describen en
[frameworks y arquitectura](docs/desarrollo/FRAMEWORKS_Y_ARQUITECTURA.md).

El perfil sqlserver es el predeterminado y exige una conexión válida. Para trabajar sin persistencia se solicita explícitamente el perfil memoria. Una conexión fallida no activa memoria automáticamente.

## Ejecutar

### Opcion sencilla en Windows

Desde el Explorador de archivos, abre la carpeta `E:\GESTERA\GESTERA` y haz doble clic en:

- `COMPILAR_GESTERA.cmd` para compilar. La ventana permanece abierta y muestra el resultado.
- `INICIAR_GESTERA.cmd` para compilar e iniciar la pagina web.

Estos lanzadores evitan el bloqueo habitual de ejecucion de archivos `.ps1`.

### Desde PowerShell

Desde la raíz del proyecto:

```powershell
./herramientas/ejecutar.ps1
```

Luego abre [http://127.0.0.1:8080](http://127.0.0.1:8080). El servidor permanece activo hasta presionar `Ctrl+C`.

Para utilizar el JAR ya generado sin recompilar:

```powershell
./herramientas/ejecutar.ps1 -OmitirCompilacion
```

Si PowerShell bloquea los scripts, habilítalos solo para la sesión actual:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## Llaves de evaluación

La llave general predeterminada es `123456789abc` y no cambia al modificar las llaves de los hoteles. Cada hotel comienza con su propia credencial independiente usando el valor inicial `gesteraperu123`. Se documentan exclusivamente para facilitar la evaluación y nunca se muestran, sugieren ni completan en la página.

Antes de cualquier despliegue, reemplázalas mediante variables de entorno:

```powershell
$env:GESTERA_LLAVE_ACCESO = 'una-llave-larga-y-segura'
$env:GESTERA_LLAVE_HOTEL = 'LlaveHotel9'
./herramientas/ejecutar.ps1
```

También puedes configurar cada una mediante una propiedad de la JVM:

```powershell
./herramientas/ejecutar.ps1 -OpcionesJava '-Dgestera.llave.acceso=una-llave-larga-y-segura'
./herramientas/ejecutar.ps1 -OpcionesJava '-Dgestera.llave.hotel=LlaveHotel9'
```

Para cada llave, la propiedad JVM tiene prioridad sobre la variable de entorno y esta, a su vez, sobre el valor predeterminado. Un valor configurado pero vacío detiene el inicio.

### Cambio de llave de un hotel

La llave de cada hotel se administra y versiona por separado. El formulario está disponible tanto en la configuración inicial obligatoria como en **Configuración del hotel**, para permitir cambios posteriores. Solicita `nuevaLlave` y `confirmacionLlave`; ambos valores deben coincidir. La nueva llave debe cumplir todas estas reglas:

- Tener entre 8 y 15 caracteres.
- Incluir al menos una letra mayúscula.
- Incluir al menos un número.

Al cambiar la llave de un hotel, su valor anterior —incluido `gesteraperu123`— deja de permitir el acceso a ese establecimiento. Las llaves de los otros hoteles no cambian. Las sesiones que ya estaban autorizadas para la versión anterior del mismo hotel pierden esa autorización y deben ingresar nuevamente con la llave nueva; la sesión general de Gestera y su llave `123456789abc` permanecen intactas.

En el perfil SQL Server, las llaves se conservan con sal y hash PBKDF2, su versión y el indicador de configuración completada. Reiniciar conserva la llave cambiada. Los valores iniciales solo se usan si aún no existe una credencial. El perfil memoria conserva cambios únicamente durante su ejecución.

## Habitaciones y limpieza

Cada hotel registrado dispone de un inventario independiente que comienza con 12 habitaciones numeradas consecutivamente del `1` al `12`. La cantidad se administra desde **Configuración del hotel**, accesible con la tuerquita desde el panel y los módulos internos, y puede establecerse entre 1 y 50. Después de cada cambio la numeración se mantiene como `1..N`. Al reducir el inventario sólo se eliminan las habitaciones finales y la operación se rechaza si alguna de ellas no está disponible.

### Secciones tarifarias

La configuración permite crear, editar y eliminar hasta 12 secciones tarifarias independientes por hotel. Cada sección define:

- Un color hexadecimal elegido libremente con el formato `#RRGGBB`.
- Un nombre opcional de hasta 40 caracteres. Si se deja vacío, Gestera utiliza `Color #RRGGBB` con el valor elegido.
- Un precio para el servicio de **6 horas**.
- Un precio para el servicio de **día completo**, equivalente a 24 horas.

Ambos precios se expresan en soles (`PEN`), deben estar entre `1.00` y `10000.00` y admiten como máximo dos decimales. El color y el nombre visible no se pueden repetir dentro del mismo hotel. No se puede eliminar la última sección ni una sección que todavía esté asignada a alguna habitación.

La capacidad y cada sección registrada ofrecen un botón propio para guardar únicamente ese bloque. La tarjeta **Nueva sección** incorpora además la acción **Añadir nueva sección**: valida sus datos, los guarda inmediatamente y vuelve a mostrarla como sección registrada. El botón compacto **Guardar todos los cambios**, situado al final de la página, permite confirmar juntos la capacidad, todos los colores, nombres y precios, una nueva sección y las eliminaciones pendientes. Este guardado general valida el conjunto final y lo aplica de forma atómica: si algún valor no es válido, no guarda parcialmente los demás cambios.

La sección concentra el color y las dos tarifas: estos valores ya no se escriben directamente en el formulario de cada cuarto. Al hacer clic en una habitación se seleccionan una sección y el tipo de servicio, **6 horas** o **día completo**, además de su estado y la condición **Pagado** o **No pagado**. Al escoger una sección de color para una habitación nueva, el formulario propone **6 horas** y **Disponible**; ambos valores se pueden cambiar antes de guardar. Una habitación que ya está ocupada conserva su estado y su estancia al cambiar de sección. El detalle también ofrece el control explícito **Cambiar hora para pruebas**, descrito a continuación.

### Registro de huéspedes

Una habitación sólo puede pasar a **Ocupada** si incluye entre uno y ocho huéspedes. El formulario comienza con dos filas y solicita nombres y apellidos, tipo de documento y número. Cada elección aplica reglas específicas tanto en el navegador como en el backend: DNI exige exactamente 8 dígitos; Carnet de extranjería admite entre 9 y 12 caracteres alfanuméricos; Pasaporte admite entre 6 y 12 caracteres alfanuméricos. Las letras se normalizan a mayúsculas, no se permiten símbolos y se impiden documentos repetidos dentro de una misma estancia. El servidor no acepta horarios de check-in enviados por el navegador.

El módulo **Huéspedes** recibe automáticamente esos registros y muestra hotel, ubicación, color histórico cuando corresponde, día visitado, check-in, check-out y estado de la estancia. La ubicación identifica una **Habitación N** o el nombre del área común utilizada. Su buscador permite filtrar por nombres o apellidos, día, intervalo de horas en que la persona estuvo alojada y color de habitación. Cada criterio funciona solo o combinado con los demás. Editar una habitación ocupada actualiza su misma estancia sin duplicarla; confirmar el check-out la marca como finalizada y conserva el historial.

### Horarios y estados

El servidor sigue siendo la autoridad sobre los horarios. Al pasar una habitación a **Ocupada**, registra el check-in con la hora actual de la zona `America/Lima` y calcula automáticamente el check-out sumando 6 o 24 horas, según el servicio seleccionado. Elegir o cambiar el servicio mantiene este comportamiento automático. El precio y el check-in nunca se aceptan desde el navegador.

En **Áreas comunes**, el check-in también aparece precargado con la hora actual de Perú (`America/Lima`, UTC-5). Al seleccionar 1, 3 o 5 horas, el check-out se actualiza inmediatamente usando esa misma hora local, sin depender de la zona horaria configurada en el dispositivo del usuario. El servidor vuelve a calcular la salida al guardar.

Para facilitar pruebas de vencimiento, una habitación **Ocupada** permite activar **Cambiar hora para pruebas** y editar únicamente su check-out. El formulario `POST /hotel/habitaciones/actualizar` sólo aplica ese valor cuando recibe juntos `ajusteManualCheckOut=true` y `proximoCheckOut=yyyy-MM-ddTHH:mm`; sin la señal explícita ignora cualquier horario enviado y conserva las reglas automáticas. La salida manual debe ser igual o posterior al check-in. Las habitaciones disponibles, en mantenimiento o en limpieza no admiten este ajuste.

### Ampliaciones y monto total

El panel de una habitación **Ocupada** permite ampliar la estancia en **+1**, **+2** o **+4 horas**. Cada ampliación exige ingresar un nuevo monto adicional entre `0.01` y `10000.00` soles, con un máximo de dos decimales. Las horas se suman al check-out vigente y tanto el tiempo como el cargo se acumulan con las ampliaciones anteriores. La opción **Sin ampliación** equivale a ambos valores en cero y permite guardar los demás detalles sin volver a aplicar tiempo ni cargos.

El resumen del panel distingue la tarifa original, el adicional acumulado, el nuevo cargo y el **Total a cobrar**. Este total siempre lo calcula el servidor como tarifa aplicada más todos los cargos adicionales. Al registrar un cargo nuevo, la habitación vuelve automáticamente a **No pagado**, incluso si la tarifa anterior ya figuraba como pagada. Cambiar la sección o el servicio durante la misma estancia conserva las ampliaciones; el horario se recalcula desde el check-in con las horas adicionales acumuladas y el total utiliza la nueva tarifa base.

Mientras el módulo permanece abierto, Gestera revisa las habitaciones ocupadas y mantiene una campana grande de alertas en el encabezado. La campana abre una lista con todas las salidas próximas y distingue tres fases por color: amarillo desde 10 minutos antes, naranja desde 5 minutos antes y rojo cuando la hora de check-out ya llegó. Una salida vencida muestra los minutos transcurridos y genera el sonido una sola vez durante esa apertura del módulo. Debido a las políticas de reproducción automática de los navegadores, el audio se habilita después de la primera interacción del usuario con la página; si el vencimiento ocurre antes, el aviso queda pendiente. La alerta identifica la habitación y, si continúa como **No pagado**, añade **Falta registrar el pago** y el **Monto total a cobrar**, incluidos todos los adicionales. Sólo una alerta ya vencida permite abrir el recuadro de confirmación **Confirmo que se realizó el check-out de esta habitación**.

La confirmación se envía mediante `POST /hotel/habitaciones/checkout` con `numeroHabitacion`, `confirmado=true` y el `modo` actual (`normal` o `limpieza`). Sólo se acepta si la habitación sigue **Ocupada** y su hora límite ya llegó; una confirmación anticipada o repetida se rechaza. Al completarla, la habitación pasa automáticamente a **Limpieza pendiente** y conserva los horarios, las horas adicionales, los cargos, el total y el estado de pago de la estancia. Por ello una habitación impaga puede registrar su salida, pero continúa mostrando el pago pendiente hasta que se actualice.

La tarifa aplicada queda guardada como una instantánea de la sección y del servicio elegidos. Por ello, editar posteriormente los precios de una sección no cambia el importe de una estancia que ya está activa. Si durante una estancia se cambia de sección o de servicio desde su panel, el servidor aplica la nueva tarifa, recalcula el check-out desde el check-in original y vuelve a sumar las horas adicionales ya registradas.

Cada habitación conserva uno de estos estados: **Disponible**, **Ocupada**, **Limpieza pendiente**, **En limpieza** o **Mantenimiento**. Los estados de limpieza conservan todos los datos de la estancia para facilitar el seguimiento. Al pasar a **Disponible** o **Mantenimiento** se eliminan los horarios, horas y cargos adicionales, y el cuarto queda marcado como no pagado. Una estancia posterior siempre empieza sin ampliaciones heredadas.

La vista general muestra todo el inventario y permite segmentarlo visualmente por sección y tarifa. Junto al total de habitaciones mostradas aparecen bloques separados para las habitaciones **Disponibles** y **Ocupadas**; los tres valores se recalculan al filtrar por color. La vista de limpieza muestra únicamente habitaciones en **Limpieza pendiente** o **En limpieza**. Ambas vistas trabajan sobre los mismos datos del hotel autorizado.

El perfil SQL Server persiste inventarios, secciones, tarifas, estados, horarios, huéspedes y pagos por hotel. Registrar una ocupación guarda inventario, estancia, personas y pago en una sola transacción. Las habitaciones retiradas se desactivan para preservar su historial. Solo el perfil explícito memoria descarta los cambios al reiniciar.

## Servicio al cuarto

El panel del hotel incorpora un módulo operativo de servicio al cuarto. Sólo las habitaciones actualmente **Ocupadas** pueden recibir pedidos. El formulario permite seleccionar un producto del catálogo controlado, una cantidad entre 1 y 10 y observaciones opcionales de hasta 300 caracteres. El botón **Administrar catálogo** permite editar, agregar y quitar productos, además de crear o eliminar categorías para el hotel autorizado. Una categoría sólo puede eliminarse cuando está vacía y el catálogo siempre conserva al menos una. Los cambios se aplican únicamente a pedidos nuevos; cada pedido anterior conserva la instantánea con la que fue creado. El precio y el total se calculan nuevamente en el backend; los valores mostrados por JavaScript son únicamente una vista previa.

Cada pedido sigue transiciones explícitas: **Pendiente → En preparación → Entregado**. Un pedido pendiente o en preparación también puede cancelarse, pero los pedidos entregados o cancelados quedan cerrados. La cola puede filtrarse visualmente por estado y buscarse en el servidor por fecha, hora exacta y número de habitación; los tres criterios pueden combinarse y permanecen aislados por hotel. Los nombres y precios de los productos se guardan como instantáneas para que un futuro cambio de catálogo no modifique pedidos históricos.

En SQL Server, el catálogo editable, sus categorías y productos también se conservan al reiniciar. Los pedidos pertenecen a una estancia real y sus ítems conservan nombre y precio históricos. La versión del catálogo detecta ediciones concurrentes. El perfil memoria utiliza sus adaptadores temporales.

## Dashboard y reportes de ingresos

El sexto módulo es un dashboard ejecutivo que consolida únicamente ingresos realizados: estancias marcadas como **Pagado** y pedidos de servicio al cuarto que llegaron a **Entregado**. Permite consultar hoy, la semana actual, el mes actual, el año actual o un rango personalizado de hasta diez años, y separar el resultado entre habitaciones, servicio al cuarto o ambos. La vista incluye evolución temporal, mezcla de ingresos, ranking de habitaciones y cada movimiento que forma el resultado.

Los indicadores financieros respetan el periodo y la fuente seleccionados: ingresos confirmados, cantidad de movimientos, tarifa diaria promedio (**ADR**), ingreso por habitación disponible (**RevPAR**), ticket promedio de servicio al cuarto y participación de cada fuente. ADR se calcula como ingresos de habitaciones entre estancias pagadas; RevPAR usa ingresos de habitaciones entre habitaciones operativas actuales por días del periodo.

Los indicadores operativos son una fotografía del momento en que se abre el dashboard: tasa de ocupación sobre habitaciones operativas, distribución entre disponibles, ocupadas, limpieza y mantenimiento, monto y cantidad de estancias pendientes de cobro, y pedidos de servicio activos. La propia pantalla distingue estos datos actuales de las métricas históricas para evitar mezclarlos.

El pago de una estancia se registra por la combinación hotel, habitación y check-in, por lo que volver a guardar la misma estancia actualiza su monto sin duplicarlo. Si el pago se reabre durante esa estancia, se retira del reporte hasta confirmarlo otra vez; al liberar una habitación pagada, su ingreso histórico permanece. En SQL Server, este historial se guarda en pagos_estancia, con FK a estancias. La estancia se crea antes de registrar el pago y ambas operaciones se confirman juntas. En el perfil memoria dura solo durante el proceso.

## Áreas comunes

El módulo de áreas comunes es un servicio independiente de las habitaciones. Ofrece tres espacios fijos: **Jacuzzi**, **Sauna** y **Área de juegos**. El operador selecciona uno de ellos, define la fecha y hora de entrada y elige una duración rápida de **1**, **3** o **5 horas**. La salida se calcula automáticamente y queda bloqueada para evitar inconsistencias. Después registra entre uno y ocho visitantes con las mismas reglas de nombres y documentos utilizadas en las habitaciones.

El servidor no confía en la salida mostrada por el navegador: valida la duración y vuelve a calcularla desde la entrada antes de guardar. También valida el área y todos los visitantes. Al guardar, Gestera redirige al módulo **Huéspedes**, donde cada persona aparece con el nombre del área común en la columna **Ubicación**, sin ocupar ni modificar ningún cuarto.

## SQL Server y SSMS

1. Ejecuta completo base-datos/sqlserver/CREAR_GESTERA_SQLSERVER.sql en SSMS (F5).
2. Habilita TCP/IP y configura el puerto de la instancia; el perfil local utiliza 1433.
3. Compila con ./herramientas/compilar.ps1. Maven incluye el driver y, en Windows x64, la DLL de autenticación integrada.
4. Inicia con ./herramientas/ejecutar.ps1.

La URL local es jdbc:sqlserver://localhost:1433;databaseName=gestera;integratedSecurity=true;encrypt=true;trustServerCertificate=true. La identidad de Windows necesita acceso a la base. En producción se valida un certificado confiable con trustServerCertificate=false.

Para autenticación SQL, define GESTERA_DB_URL sin integratedSecurity=true y las variables GESTERA_DB_USUARIO y GESTERA_DB_CONTRASENA fuera del repositorio.

Para trabajar sin base: ./herramientas/ejecutar.ps1 -ArgumentosAplicacion '--spring.profiles.active=memoria'.

Consulta [la guía SQL Server](base-datos/sqlserver/README.md) y [los frameworks y la arquitectura](docs/desarrollo/FRAMEWORKS_Y_ARQUITECTURA.md). Las migraciones antiguas están archivadas en base-datos/archivo-mysql/.

## Configuración del servidor

| Uso | Variable de entorno | Propiedad JVM | Predeterminado |
|---|---|---|---|
| Llave general del servicio | `GESTERA_LLAVE_ACCESO` | `gestera.llave.acceso` | `123456789abc` |
| Llave inicial independiente de cada hotel | `GESTERA_LLAVE_HOTEL` | `gestera.llave.hotel` | `gesteraperu123` |
| Dirección | `GESTERA_DIRECCION` | `gestera.direccion` | `127.0.0.1` |
| Puerto | `GESTERA_PUERTO` | `gestera.puerto` | `8080` |
| Cookie `Secure` | `GESTERA_COOKIE_SEGURA` | `gestera.cookie.segura` | `false` |
| URL SQL Server | `GESTERA_DB_URL` | `gestera.db.url` | SQL Server local, gestera, TCP 1433 |
| Usuario SQL Server | `GESTERA_DB_USUARIO` | `gestera.db.usuario` | Sin valor |
| Contraseña SQL Server | `GESTERA_DB_CONTRASENA` | `gestera.db.contrasena` | Sin valor |

Ejemplo para cambiar la dirección y el puerto:

```powershell
$env:GESTERA_DIRECCION = '0.0.0.0'
$env:GESTERA_PUERTO = '9090'
./herramientas/ejecutar.ps1
```

`GESTERA_COOKIE_SEGURA=true` añade el atributo `Secure` a la cookie de sesión. Actívalo únicamente cuando el navegador acceda mediante HTTPS, normalmente detrás de un proxy que termine TLS; sobre HTTP el navegador no reenviará esa cookie.

## Compilar y probar

```powershell
# Compila y crea el JAR ejecutable Spring Boot
./herramientas/compilar.ps1

# Compila y ejecuta la suite de pruebas del proyecto
./herramientas/probar.ps1
```

Maven Wrapper descarga Maven y las dependencias declaradas en `pom.xml`. El JAR se genera en `distribucion/gestera.jar`, las dependencias se incluyen dentro del JAR y el caché local en `.compilacion/maven-home/`.

## Estructura

```text
gestera/
├── frontend/
│   ├── vistas/
│   │   ├── paginas/               Pantallas HTML
│   │   └── fragmentos/            HTML reutilizable
│   └── publico/
│       ├── estilos/               CSS
│       ├── scripts/               JavaScript
│       └── imagenes/              Imágenes
├── backend/src/
│   ├── main/
│   │   ├── java/com/gestera/
│   │   │   ├── modelo/            Datos y reglas por función del hotel
│   │   │   ├── controladores/     Coordinación de solicitudes
│   │   │   ├── vistas/            Renderizado HTML desde Java
│   │   │   ├── repositorios/      Contratos DAO y adaptadores JDBC/memoria
│   │   │   ├── seguridad/         Autenticación y sesiones
│   │   │   └── infraestructura/   Configuración, HTTP y transacciones
│   │   └── resources/             Configuración de Spring y registros
│   └── test/java/com/gestera/      Pruebas organizadas por capas
├── base-datos/                    SQL Server vigente y archivo histórico
├── docs/                          Documentación, diagramas y exposición
├── herramientas/                  Compilación, ejecución y pruebas
├── distribucion/                  JAR ejecutable generado
├── .mvn/                          Configuración de Maven Wrapper
├── pom.xml                        Compila backend y empaqueta frontend
├── mvnw.cmd                       Lanzador de Maven Wrapper para Windows
├── COMPILAR_GESTERA.cmd
└── INICIAR_GESTERA.cmd
```

`modelo`, `vistas` y `controladores` identifican las responsabilidades MVC. Los gestores del modelo aplican las reglas de negocio; los repositorios separan las operaciones de datos y sus implementaciones. Maven incorpora las plantillas y los recursos del frontend al mismo JAR del backend, conservando las direcciones web existentes.


Las clases públicas `GestorHabitacionesHoteles`, `ServidorGestera` y
`PlantillasWeb` se mantienen como fachadas estables. La implementación se reparte
solo cuando existe una responsabilidad independiente: `InventarioHotel`,
`RutasGestera`, `SolicitudesHttp`, `RespuestasHttp`, `ComponentesHtml` y
`RecursosWeb`. La validación genérica de formularios vive junto a las solicitudes
que la utilizan, y la carga de plantillas junto a `PlantillasWeb`. Los scripts de
PowerShell comparten sus funciones seguras en `herramientas/comun.ps1`.

## Rutas HTTP

| Método | Ruta | Función |
|---|---|---|
| `GET` | `/` | Muestra el formulario o redirige al panel si la sesión es válida. |
| `POST` | `/acceso` | Valida el campo `llave` y crea la sesión. |
| `GET` | `/panel` | Muestra el selector protegido con cuatro hoteles y búsqueda por nombre. |
| `POST` | `/hotel/acceso` | Valida la llave secundaria y autoriza en la sesión el hotel seleccionado. |
| `GET` | `/hotel` | Muestra el panel de gestión del hotel autorizado. |
| `POST` | `/hotel/llave` | Cambia la llave del hotel autorizado usando `nuevaLlave` y `confirmacionLlave`. |
| `POST` | `/hotel/configuracion/llave` | Cambia la llave sin abandonar la página de configuración. |
| `GET` | `/hotel/habitaciones` | Muestra el inventario completo del hotel autorizado. |
| `GET` | `/hotel/limpieza` | Muestra únicamente las habitaciones pendientes o en proceso de limpieza. |
| `GET` | `/hotel/huespedes` | Muestra el registro actual e histórico de visitantes del hotel autorizado. |
| `GET` | `/hotel/areas-comunes` | Muestra Jacuzzi, Sauna y Área de juegos con su formulario de horarios y visitantes. |
| `POST` | `/hotel/areas-comunes/registrar` | Valida y registra el uso de un área común en el historial de huéspedes. |
| `GET` | `/hotel/servicio-habitacion` | Muestra la cola y permite buscar pedidos por `fecha`, `hora` y `habitacion`. |
| `POST` | `/hotel/servicio-habitacion/pedidos` | Crea un pedido para una habitación ocupada. |
| `POST` | `/hotel/servicio-habitacion/pedidos/estado` | Aplica una transición válida al estado de un pedido. |
| `POST` | `/hotel/servicio-habitacion/catalogo` | Actualiza nombre, categoría y precio de un producto para el hotel autorizado. |
| `POST` | `/hotel/servicio-habitacion/catalogo/productos` | Agrega un producto a una categoría existente. |
| `POST` | `/hotel/servicio-habitacion/catalogo/productos/eliminar` | Quita un producto sin alterar pedidos históricos. |
| `POST` | `/hotel/servicio-habitacion/catalogo/categorias` | Crea una categoría para el catálogo del hotel. |
| `POST` | `/hotel/servicio-habitacion/catalogo/categorias/eliminar` | Elimina una categoría vacía que no sea la última. |
| `GET` | `/hotel/reportes` | Genera ingresos por `periodo`, `tipo` y, para el rango personalizado, `desde` y `hasta`. |
| `GET` | `/hotel/configuracion` | Abre la configuración separada de habitaciones y secciones tarifarias. |
| `POST` | `/hotel/configuracion/guardar` | Aplica atómicamente cantidad, secciones indexadas, altas y eliminaciones desde el guardado general. |
| `POST` | `/hotel/configuracion/habitaciones` | Endpoint compatible para cambiar únicamente `cantidadHabitaciones`. |
| `POST` | `/hotel/configuracion/secciones/guardar` | Endpoint compatible para crear o editar una sola sección. |
| `POST` | `/hotel/configuracion/secciones/eliminar` | Endpoint compatible para eliminar una sola sección permitida. |
| `POST` | `/hotel/habitaciones/actualizar` | Procesa datos operativos y huéspedes; calcula horarios, valida documentos y registra ampliaciones. |
| `POST` | `/hotel/habitaciones/checkout` | Confirma una salida ya vencida y pasa la habitación ocupada a **Limpieza pendiente**, conservando pago, horarios, ampliaciones y total. |
| `POST` | `/salir` | Invalida la sesión y vuelve al acceso. |
| `GET` | `/publico/...` | Sirve únicamente el CSS y JavaScript registrados. |

Las sesiones duran 30 minutos y utilizan una cookie `HttpOnly` con `SameSite=Strict`. La autorización versionada del hotel se conserva únicamente en el servidor y desaparece al cerrar o vencer la sesión, o cuando ese hotel cambia de llave.

## Personalización

- Textos y estructura: `frontend/vistas/paginas/`.
- Colores, medidas y adaptación móvil: `frontend/publico/estilos/gestera.css`; las variables están al inicio del archivo.
- Mostrar u ocultar la llave: `frontend/publico/scripts/acceso.js`.
- Búsqueda y apertura del cuadro de acceso a hoteles: `frontend/publico/scripts/hoteles.js`.
- Interacción y validación previa del cambio de llave: `frontend/publico/scripts/gestion.js`.
- API, límites y validación de habitaciones: `backend/src/main/java/com/gestera/modelo/habitaciones/GestorHabitacionesHoteles.java`.
- Estado y reglas internas de cada inventario: `backend/src/main/java/com/gestera/modelo/habitaciones/InventarioHotel.java`.
- Reglas de huéspedes: `backend/src/main/java/com/gestera/modelo/huespedes/`; contratos y adaptadores de datos: `backend/src/main/java/com/gestera/repositorios/huespedes/`.
- Catálogo y reglas de áreas comunes: `backend/src/main/java/com/gestera/modelo/areas/GestorAreasComunes.java`.
- Reglas de servicio al cuarto: `backend/src/main/java/com/gestera/modelo/servicios/`; adaptadores: `backend/src/main/java/com/gestera/repositorios/servicios/`.
- Reglas y agregación de reportes: `backend/src/main/java/com/gestera/modelo/reportes/`; adaptadores: `backend/src/main/java/com/gestera/repositorios/reportes/`.
- Esquema vigente: base-datos/sqlserver/CREAR_GESTERA_SQLSERVER.sql.
- Estructura de las vistas de habitaciones y limpieza: `frontend/vistas/paginas/habitaciones.html`.
- Formulario de configuración y guardado general: `frontend/vistas/paginas/configuracion-hotel.html`.
- Segmentación, selección de servicio y vista previa de horarios: `frontend/publico/scripts/habitaciones.js`.
- Vista previa y filtros de pedidos: `frontend/publico/scripts/servicio-habitacion.js`.
- Activación del rango personalizado de reportes: `frontend/publico/scripts/reportes.js`.
- Selección, visitantes y validación de horarios de áreas comunes: `frontend/publico/scripts/areas-comunes.js`.
- Interacción de la página de configuración: `frontend/publico/scripts/configuracion-hotel.js`.
- Nombres, ubicaciones e identificadores de los cuatro hoteles: `backend/src/main/java/com/gestera/modelo/hoteles/CatalogoHoteles.java`.
- Credenciales independientes, versiones y reglas de cambio: `backend/src/main/java/com/gestera/modelo/hoteles/GestorLlavesHoteles.java`.
- Llaves, dirección, puerto y cookie segura: `backend/src/main/java/com/gestera/infraestructura/configuracion/ConfiguracionAplicacion.java` o las opciones de configuración anteriores.

Después de modificar recursos, vuelve a ejecutar `./herramientas/compilar.ps1` para incorporarlos al JAR.

## Uso en producción

El servidor utiliza Spring Boot, JDBC y HikariCP sobre un esquema activo de 14 tablas. Inventario, tarifas, credenciales, catálogo e historial operativo son persistentes. Para desplegar se deben configurar las llaves iniciales, HTTPS, el usuario SQL con permisos apropiados y los respaldos. El transporte HTTP actual sigue siendo jdk.httpserver; la aplicación no está empaquetada como WAR de Tomcat.

## Frameworks y limpieza

Consulta [FRAMEWORKS_Y_ARQUITECTURA.md](docs/desarrollo/FRAMEWORKS_Y_ARQUITECTURA.md). Las pruebas usan JUnit y conservan la suite funcional existente.

./herramientas/limpiar.ps1 -WhatIf muestra los archivos generados que se pueden eliminar. Ejecuta sin -WhatIf para limpiar. Agrega -IncluirDistribucion para regenerar también el JAR. La caché Maven y las DLL nativas se conservan.
