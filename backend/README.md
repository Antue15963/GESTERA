# Backend de Gestera

Aquí está el código Java que recibe solicitudes, valida las operaciones del hotel, prepara las vistas y guarda o consulta datos.

```text
backend/src/
├── main/
│   ├── java/com/gestera/
│   │   ├── GesteraAplicacion.java    Punto de entrada de Spring Boot
│   │   ├── modelo/                  Modelo MVC: datos y reglas de negocio
│   │   │   ├── areas/
│   │   │   ├── habitaciones/
│   │   │   ├── hoteles/
│   │   │   ├── huespedes/
│   │   │   ├── reportes/
│   │   │   └── servicios/           Pedidos de servicio al cuarto
│   │   ├── controladores/           Controlador MVC: coordina solicitudes
│   │   ├── vistas/                  Vista MVC: renderizadores Java
│   │   ├── repositorios/            Acceso a datos, separado por función
│   │   │   ├── areas/
│   │   │   ├── habitaciones/
│   │   │   ├── hoteles/
│   │   │   ├── huespedes/
│   │   │   ├── reportes/
│   │   │   └── servicios/
│   │   ├── seguridad/
│   │   │   ├── autenticacion/       Llaves y limitación de intentos
│   │   │   └── sesiones/            Sesión y autorización temporal
│   │   └── infraestructura/
│   │       ├── configuracion/       Spring, perfiles y conexiones
│   │       ├── servidor/            HTTP, rutas, solicitudes y respuestas
│   │       └── persistencia/        Transacciones compartidas por hotel
│   └── resources/                   application*.properties y logback.xml
└── test/java/com/gestera/            Pruebas con la misma separación por capas
```

| Necesito cambiar... | Buscar en... |
|---|---|
| La regla para ocupar una habitación | `src/main/java/com/gestera/modelo/habitaciones/` |
| La validación de huéspedes | `src/main/java/com/gestera/modelo/huespedes/` |
| La respuesta a un formulario | `src/main/java/com/gestera/controladores/` |
| El HTML generado dinámicamente | `src/main/java/com/gestera/vistas/` |
| Una consulta SQL o el guardado de datos | `src/main/java/com/gestera/repositorios/` |
| La llave o la sesión de acceso | `src/main/java/com/gestera/seguridad/` y `src/main/java/com/gestera/modelo/hoteles/GestorLlavesHoteles.java` |
| La conexión con SQL Server | `src/main/java/com/gestera/infraestructura/configuracion/` |
| Una dirección web | `src/main/java/com/gestera/infraestructura/servidor/RutasGestera.java` |

Las clases `Gestor*` constituyen los servicios de negocio del modelo. Los tipos de datos incluyen records y enumeraciones; algunos están declarados dentro de los propios gestores. `modelo/servicios` es específicamente el módulo de servicio al cuarto.

En cada carpeta de `repositorios`, `Repositorio*.java` define contratos, `*Jdbc.java` implementa el acceso a SQL Server y `*Memoria.java`, cuando existe, proporciona almacenamiento temporal.

Las plantillas HTML, CSS, JavaScript e imágenes están en [frontend](../frontend/README.md). Los renderizadores Java permanecen en `vistas` porque se ejecutan en el servidor.

La compilación se ejecuta desde la raíz del proyecto con `COMPILAR_GESTERA.cmd` o `herramientas/compilar.ps1`. El `pom.xml` de la raíz incorpora frontend y backend en `distribucion/gestera.jar`.

Consulta el [mapa de funciones y explicación de MVC](../docs/desarrollo/ESTRUCTURA_Y_MVC.md).
