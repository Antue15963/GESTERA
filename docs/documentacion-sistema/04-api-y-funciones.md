> Referencia histórica anterior al modelo SQL Server V2. El esquema implementado, sus 14 tablas y atributos se describen en [SQLSERVER_14_TABLAS.md](../database-design/SQLSERVER_14_TABLAS.md).

# Gestera — API HTTP y funciones principales

## 1. Alcance

Gestera no expone una API REST/JSON. Su interfaz HTTP está orientada a páginas y formularios HTML. Las operaciones `POST` reciben `application/x-www-form-urlencoded`; las consultas `GET` usan parámetros de URL. Las respuestas normales son HTML (`200`) o redirecciones `303`, mientras que algunos errores se devuelven como texto.

La URL local predeterminada es `http://127.0.0.1:8080`. Salvo `/`, `/acceso` y los recursos públicos, las rutas requieren la cookie `GESTERA_SESION`. Las rutas bajo `/hotel` exigen además que la sesión esté autorizada para un hotel y para la versión actual de su llave.

## 2. Convenciones HTTP

### 2.1 Cookie de sesión

```text
GESTERA_SESION=<token opaco>; Path=/; Max-Age=1800; HttpOnly; SameSite=Strict
```

Se añade `Secure` si `gestera.cookie.segura` o `GESTERA_COOKIE_SEGURA` vale `true`.

### 2.2 Tipos de respuesta

| Código | Uso |
|---|---|
| `200 OK` | Página o recurso solicitado |
| `303 See Other` | Operación aceptada y redirección posterior |
| `400 Bad Request` | Campo, formato o regla básica inválida |
| `401 Unauthorized` | Llave incorrecta |
| `404 Not Found` | Ruta o recurso no registrado |
| `405 Method Not Allowed` | Método distinto del permitido |
| `409 Conflict` | Estado actual incompatible con la operación |
| `413 Payload Too Large` | Formulario mayor al límite |
| `414 URI Too Long` | Consulta mayor a 2 048 caracteres |
| `415 Unsupported Media Type` | `POST` no codificado como formulario |
| `429 Too Many Requests` | Bloqueo temporal por intentos fallidos |
| `500 Internal Server Error` | Error no controlado, sin exponer detalles internos |

Todos los formularios rechazan nombres de campo repetidos. El límite general es 4 096 bytes; la configuración general y la actualización de habitación admiten hasta 16 384 bytes.

## 3. Rutas de acceso y navegación

### `GET /`

Muestra el formulario de llave general. Si la sesión ya es válida, redirige a `/panel`.

### `POST /acceso`

Valida la llave general y crea la sesión.

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `llave` | texto sensible | Sí | Llave general; distingue mayúsculas y minúsculas |

Resultado: `303 /panel` si se concede; HTML `400`, `401` o `429` si falla.

### `GET /panel`

Muestra y permite filtrar visualmente los cuatro hoteles registrados. Requiere sesión general.

### `POST /hotel/acceso`

Autoriza un hotel dentro de la sesión.

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `hotel` | texto, máximo 80 | Sí | Código de `CatalogoHoteles` |
| `llaveHotel` | texto sensible | Sí | Llave vigente del hotel |

Resultado: `303 /hotel` al conceder acceso. La sesión conserva el código de hotel y la versión de llave.

### `GET /hotel`

Muestra el panel del hotel autorizado y los enlaces a seis módulos operativos.

### `POST /salir`

Elimina la sesión, vence la cookie y redirige a `/`.

## 4. Llave y configuración del hotel

### `POST /hotel/llave`

Cambia la llave desde el panel principal del hotel.

### `POST /hotel/configuracion/llave`

Realiza el mismo cambio desde la página de configuración y vuelve a ella.

Ambas rutas reciben:

| Campo | Tipo | Reglas |
|---|---|---|
| `nuevaLlave` | texto sensible | 8–15 caracteres, al menos una mayúscula y un número |
| `confirmacionLlave` | texto sensible | Debe ser idéntico a `nuevaLlave` |

La actualización incrementa la versión de la credencial. Las sesiones autorizadas con una versión anterior dejan de operar sobre ese hotel.

### `GET /hotel/configuracion`

Muestra cambio de llave, cantidad de habitaciones y secciones tarifarias.

### `POST /hotel/configuracion/habitaciones`

Actualiza sólo la capacidad.

| Campo | Tipo | Reglas |
|---|---|---|
| `cantidadHabitaciones` | entero | De 1 a 50 |

Devuelve conflicto si la reducción eliminaría una habitación cuyo estado no sea Disponible.

### `POST /hotel/configuracion/secciones/guardar`

Crea una sección si `idSeccion` está vacío o actualiza la sección indicada.

| Campo | Tipo | Reglas |
|---|---|---|
| `idSeccion` | texto, máximo 32 | Vacío para alta; ID existente para edición |
| `color` | texto | Hexadecimal `#RRGGBB` |
| `nombre` | texto | Opcional, máximo real de 40 caracteres |
| `precio6Horas` | decimal | S/ 1.00–10 000.00, máximo 2 decimales |
| `precioDiaCompleto` | decimal | S/ 1.00–10 000.00, máximo 2 decimales |

Si el nombre está vacío se crea como `Color #RRGGBB`. Color y nombre deben ser únicos dentro del hotel.

### `POST /hotel/configuracion/secciones/eliminar`

| Campo | Tipo | Reglas |
|---|---|---|
| `idSeccion` | texto | Sección existente, que no sea la última ni esté en uso |

### `POST /hotel/configuracion/guardar`

Aplica capacidad y secciones como una transacción lógica: si un dato es inválido, no se aplica ningún cambio.

| Campo | Descripción |
|---|---|
| `cantidadHabitaciones` | Entero entre 1 y 50 |
| `cantidadSecciones` | Número de secciones existentes, entre 1 y 12 |
| `seccion.i.id` | ID de la sección en el índice `i` |
| `seccion.i.accion` | `guardar` o `eliminar` |
| `seccion.i.color` | Color hexadecimal |
| `seccion.i.nombre` | Nombre visible |
| `seccion.i.precio6Horas` | Tarifa de 6 horas |
| `seccion.i.precioDiaCompleto` | Tarifa de 24 horas |
| `nuevaSeccion.color` | Color de una alta opcional |
| `nuevaSeccion.nombre` | Nombre de una alta opcional |
| `nuevaSeccion.precio6Horas` | Tarifa de una alta opcional |
| `nuevaSeccion.precioDiaCompleto` | Tarifa de una alta opcional |

`i` recorre `0..cantidadSecciones-1`.

## 5. Habitaciones, limpieza y huéspedes

### `GET /hotel/habitaciones`

Muestra todo el inventario del hotel autorizado.

### `GET /hotel/limpieza`

Muestra únicamente habitaciones en `limpieza_pendiente` o `en_limpieza`.

### `POST /hotel/habitaciones/actualizar`

Actualiza una habitación y, cuando el estado solicitado es Ocupada, registra sus huéspedes.

| Campo | Tipo | Valores o reglas |
|---|---|---|
| `numeroHabitacion` | entero | Habitación existente del hotel |
| `idSeccion` | texto | ID de sección existente |
| `tipoServicio` | texto | `seis_horas` o `dia_completo` |
| `estado` | texto | `disponible`, `ocupada`, `limpieza_pendiente`, `en_limpieza`, `mantenimiento` |
| `pagado` | booleano textual | Exactamente `true` o `false` |
| `modo` | texto | `normal` o `limpieza`; define la página de retorno |
| `ajusteManualCheckOut` | booleano textual | `true` sólo para activar el ajuste de prueba |
| `proximoCheckOut` | fecha/hora | `yyyy-MM-ddTHH:mm`; se ignora sin la señal anterior |
| `horasAdicionales` | entero | `0`, `1`, `2` o `4` |
| `montoAdicional` | decimal | Cero sin ampliación; S/ 0.01–10 000.00 con ampliación |
| `cantidadHuespedes` | entero | 1–8 si `estado=ocupada` |
| `huesped.i.nombre` | texto | Nombre de 2–100 caracteres |
| `huesped.i.tipoDocumento` | texto | `dni`, `carnet_extranjeria` o `pasaporte` |
| `huesped.i.numeroDocumento` | texto | Formato según el tipo |

El servidor no recibe el check-in ni el precio. Ambos se calculan internamente. Cuando una habitación deja de estar ocupada, la estancia anterior se finaliza; cuando continúa ocupada, el registro se actualiza sin duplicarlo.

### `POST /hotel/habitaciones/checkout`

Confirma una salida cuya hora límite ya llegó.

| Campo | Tipo | Valores |
|---|---|---|
| `numeroHabitacion` | entero | Habitación ocupada |
| `confirmado` | texto | Debe ser `true` |
| `modo` | texto | `normal` o `limpieza` |

Resultado: la habitación pasa a Limpieza pendiente. No es obligatorio que figure como pagada, pero el pendiente se conserva.

### `GET /hotel/huespedes`

Consulta el historial. Todos los filtros son opcionales y combinables.

| Parámetro de consulta | Formato | Semántica |
|---|---|---|
| `nombre` | texto, máximo 100 | Coincidencia parcial en nombres o apellidos |
| `dia` | `yyyy-MM-dd` | Día de check-in |
| `estuvoDesde` | `yyyy-MM-ddTHH:mm` | La estancia termina en o después de este instante |
| `estuvoHasta` | `yyyy-MM-ddTHH:mm` | La estancia inicia en o antes de este instante |
| `color` | `#RRGGBB` | Sólo estancias de habitación con ese color histórico |

## 6. Áreas comunes

### `GET /hotel/areas-comunes`

Muestra Jacuzzi, Sauna y Área de juegos, el horario y dos visitantes iniciales.

### `POST /hotel/areas-comunes/registrar`

| Campo | Tipo | Valores o reglas |
|---|---|---|
| `area` | texto | `jacuzzi`, `sauna` o `area-juegos` |
| `entrada` | fecha/hora | `yyyy-MM-ddTHH:mm` |
| `duracionHoras` | entero | `1`, `3` o `5` |
| `cantidadHuespedes` | entero | 1–8 |
| `huesped.i.nombre` | texto | Igual que una estancia de habitación |
| `huesped.i.tipoDocumento` | texto | Tipo admitido |
| `huesped.i.numeroDocumento` | texto | Documento válido y no repetido |

La salida no se recibe: el servidor la calcula sumando la duración. Al aceptar el registro redirige a `/hotel/huespedes`.

## 7. Servicio al cuarto

### `GET /hotel/servicio-habitacion`

Muestra catálogo y cola de pedidos. Filtros opcionales:

| Parámetro | Formato |
|---|---|
| `fecha` | `yyyy-MM-dd` |
| `hora` | `HH:mm`, coincidencia exacta al minuto |
| `habitacion` | entero de 1 a 50 |

### `POST /hotel/servicio-habitacion/pedidos`

| Campo | Tipo | Reglas |
|---|---|---|
| `numeroHabitacion` | entero | Debe estar Ocupada |
| `producto` | texto | Código vigente del catálogo del hotel |
| `cantidad` | entero | 1–10 |
| `observaciones` | texto | Opcional, máximo 300 caracteres |

El pedido nace Pendiente. El backend conserva código, nombre y precio del producto como instantánea.

### `POST /hotel/servicio-habitacion/pedidos/estado`

| Campo | Tipo | Reglas |
|---|---|---|
| `pedidoId` | entero largo | Pedido existente del hotel |
| `estado` | texto | `en_preparacion`, `entregado` o `cancelado`, según transición permitida |

Se usa control de concurrencia optimista: la actualización sólo ocurre si el estado almacenado todavía coincide con el leído.

### Operaciones de catálogo

| Método y ruta | Campos | Resultado |
|---|---|---|
| `POST /hotel/servicio-habitacion/catalogo` | `producto`, `nombre`, `categoria`, `precio` | Edita un producto |
| `POST /hotel/servicio-habitacion/catalogo/productos` | `nombre`, `categoria`, `precio` | Agrega un producto |
| `POST /hotel/servicio-habitacion/catalogo/productos/eliminar` | `producto` | Elimina un producto vigente |
| `POST /hotel/servicio-habitacion/catalogo/categorias` | `categoria` | Agrega una categoría |
| `POST /hotel/servicio-habitacion/catalogo/categorias/eliminar` | `categoria` | Elimina una categoría vacía que no sea la última |

Nombres de producto: máximo 120 caracteres. Categorías: máximo 60. Precios: mayores que cero y hasta S/ 10 000.00, con máximo dos decimales. Los cambios sólo afectan pedidos futuros.

## 8. Reportes y recursos

### `GET /hotel/reportes`

| Parámetro | Valores |
|---|---|
| `periodo` | `dia`, `semana`, `mes`, `anio`, `personalizado` |
| `tipo` | `todos`, `habitaciones`, `servicios` |
| `desde` | `yyyy-MM-dd`, obligatorio para personalizado |
| `hasta` | `yyyy-MM-dd`, obligatorio para personalizado |

El rango máximo personalizado es de 3 653 días. Habitaciones se reconocen por pagos registrados; servicio al cuarto, por pedidos Entregados cuya fecha de actualización cae en el intervalo.

### `GET /publico/...`

Sirve sólo los archivos CSS y JavaScript registrados por `RecursosWeb`. No es un servidor de archivos genérico y no permite explorar el sistema de archivos.

## 9. Ejemplos HTTP

Los siguientes ejemplos asumen el entorno local de evaluación. Las claves no deben usarse en producción.

### 9.1 Crear sesión general

```bash
curl -i -c cookies.txt \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "llave=123456789abc" \
  http://127.0.0.1:8080/acceso
```

Respuesta esperada: `303 See Other`, `Location: /panel` y cookie `GESTERA_SESION`.

### 9.2 Autorizar un hotel

```bash
curl -i -b cookies.txt -c cookies.txt \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "hotel=sol-pucusana" \
  --data-urlencode "llaveHotel=gesteraperu123" \
  http://127.0.0.1:8080/hotel/acceso
```

### 9.3 Consultar pedidos filtrados

```bash
curl -b cookies.txt \
  "http://127.0.0.1:8080/hotel/servicio-habitacion?fecha=2026-09-27&habitacion=1"
```

La respuesta es una página HTML, no JSON.

## 10. Funciones Java principales

### Configuración y ciclo de vida

| Clase y función | Parámetros | Retorno o efecto |
|---|---|---|
| `ConfiguracionAplicacion.cargar()` | Ninguno | Resuelve llaves, dirección, puerto y cookie |
| `ConfiguracionMySql.cargar()` | Ninguno | `Optional<ConfiguracionMySql>` |
| `ServidorGestera.crear(configuracion)` | Configuración abierta | Crea el servidor y selecciona repositorios |
| `ServidorGestera.iniciar()` | Ninguno | Inicia `HttpServer` una sola vez |
| `ServidorGestera.direccionBase()` | Ninguno | `URI` efectiva del servidor |
| `close()` | Ninguno | Libera servidor, gestores, sesiones y datos sensibles |

### Acceso y sesiones

| Función | Parámetros | Retorno |
|---|---|---|
| `AutenticadorLlave.autenticar` | `char[] llaveRecibida` | `CONCEDIDO`, `AUSENTE` o `DENEGADO` |
| `GestorLlavesHoteles.autenticarConEstado` | Código y llave | Resultado, versión y requisito de cambio |
| `GestorLlavesHoteles.cambiar` | Código, versión esperada, nueva llave y confirmación | `ResultadoCambio` |
| `GestorSesiones.crear` | Ninguno | Token aleatorio URL-safe |
| `GestorSesiones.autorizarHotel` | Token, código y versión | `boolean` |
| `GestorSesiones.autorizacionHotel` | Token | `Optional<AutorizacionHotel>` |

### Operación hotelera

| Función | Parámetros principales | Retorno |
|---|---|---|
| `GestorHabitacionesHoteles.resumen` | Código de hotel | Habitaciones y secciones del mismo instante lógico |
| `guardarConfiguracion` | Hotel, cantidad y lista de cambios | `ResultadoOperacion` |
| `actualizarHabitacion` | Hotel, número, sección, servicio, estado, pago, horario y ampliación | `ResultadoOperacion` |
| `confirmarCheckOut` | Hotel y número | `ResultadoOperacion` |
| `GestorHuespedesHoteles.validar` | Lista de huéspedes | `ResultadoValidacion` |
| `guardarEstancia` | Hotel, habitación, color, horarios y huéspedes | Guarda o actualiza la estancia |
| `buscar` | Hotel y `FiltroHuespedes` | Lista de `RegistroHuesped` |
| `GestorAreasComunes.registrar` | Hotel, área, entrada, duración y visitantes | `ResultadoOperacion` |
| `GestorServicioHabitacion.crearPedido` | Hotel, habitación, producto, cantidad y observación | `ResultadoOperacion` |
| `cambiarEstado` | Hotel, ID de pedido y estado destino | `ResultadoOperacion` |
| `GestorReportes.generar` | Hotel, filtro y pedidos | `ReporteIngresos` |

### Ejemplo de uso del dominio

```java
var hoteles = new CatalogoHoteles();
try (var gestor = new GestorHabitacionesHoteles(hoteles.todos())) {
    var resultado = gestor.actualizarHabitacion(
            "sol-pucusana",
            1,
            "seccion-1",
            GestorHabitacionesHoteles.TipoServicio.SEIS_HORAS,
            GestorHabitacionesHoteles.EstadoHabitacion.OCUPADA,
            false);

    if (resultado != GestorHabitacionesHoteles.ResultadoOperacion.ACTUALIZADA) {
        throw new IllegalStateException("No fue posible actualizar: " + resultado);
    }
}
```

Este ejemplo modifica sólo el inventario. En el flujo HTTP real, `ServidorGestera` valida y guarda también los huéspedes requeridos para una habitación ocupada.

## 11. Referencias de implementación

- Rutas: `backend/src/main/java/com/gestera/infraestructura/servidor/RutasGestera.java`.
- Lectura de parámetros: `SolicitudesHttp.java`.
- Coordinación y códigos HTTP: `ServidorGestera.java` y `RespuestasHttp.java`.
- Funciones públicas: sitio incluido en `01-javadoc-gestera.zip`.

