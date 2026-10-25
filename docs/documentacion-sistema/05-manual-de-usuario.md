> Referencia histórica anterior al modelo SQL Server V2. El esquema implementado, sus 14 tablas y atributos se describen en [SQLSERVER_14_TABLAS.md](../database-design/SQLSERVER_14_TABLAS.md).

# Manual de usuario de Gestera

## 1. Presentación

Gestera ayuda a controlar la operación de un hotel desde el navegador. El sistema reúne habitaciones, huéspedes, limpieza, servicio al cuarto, áreas comunes y reportes de ingresos. Este manual está dirigido a la persona encargada de registrar y consultar esas operaciones.

El proyecto no maneja cuentas personales. El acceso se realiza con una llave general del servicio y una segunda llave del hotel elegido.

## 2. Antes de comenzar

1. Verifique que Gestera esté iniciado.
2. Abra `http://127.0.0.1:8080` en un navegador actual.
3. Tenga disponibles la llave general y la llave del hotel que va a operar.

Para una evaluación local sin configuración adicional:

- llave general: `123456789abc`;
- llave inicial de cada hotel: `gesteraperu123`.

Estas llaves son públicas sólo por tratarse de valores de demostración. No deben mantenerse en un despliegue real.

## 3. Ingreso al sistema

### 3.1 Ingresar al servicio

1. En **Llave de acceso**, escriba la llave general.
2. Si necesita comprobar lo escrito, use el botón **Mostrar**.
3. Seleccione **Ingresar al servicio**.
4. Si la llave es válida, se abrirá el directorio de hoteles.

La llave distingue mayúsculas de minúsculas. Después de cinco intentos incorrectos consecutivos desde el mismo origen, el sistema pausa nuevos intentos durante 30 segundos.

### 3.2 Seleccionar un hotel

El panel presenta cuatro establecimientos:

- Hotel El Sol de Pucusana;
- Hotel Brisa Marina;
- Hotel Valle Dorado;
- Hotel Costa Serena.

Escriba parte del nombre en el buscador si desea reducir la lista. Seleccione la tarjeta del hotel, ingrese su llave en el cuadro emergente y confirme. La autorización obtenida sólo sirve para ese hotel.

### 3.3 Cambiar la llave inicial

Al ingresar por primera vez, el panel muestra **Configuración requerida: Cambio de llave de acceso**.

1. Abra esa opción.
2. Escriba la nueva llave.
3. Repítala en el campo de confirmación.
4. Seleccione **Guardar nueva llave**.

La llave debe tener entre 8 y 15 caracteres, una letra mayúscula y un número. El cambio afecta únicamente al hotel actual. Cuando se acepta, la tarjeta obligatoria desaparece y la llave anterior deja de funcionar.

También se puede cambiar posteriormente desde **Configuración**. Los cambios de llave viven en memoria: al reiniciar la aplicación, todos los hoteles recuperan la llave inicial configurada.

## 4. Panel de gestión

El panel del hotel ofrece seis opciones:

1. **Limpieza de habitaciones:** cuartos pendientes o en proceso de limpieza.
2. **Habitaciones:** inventario completo, ocupación, huéspedes, pagos y horarios.
3. **Huéspedes:** historial de personas alojadas o visitantes.
4. **Servicio al cuarto:** catálogo y pedidos por habitación.
5. **Áreas comunes:** uso del Jacuzzi, Sauna y Área de juegos.
6. **Reportes de ingresos:** pagos de habitaciones y pedidos entregados.

La opción **Configuración** aparece en la cabecera de las páginas operativas. **Volver a gestión** regresa al panel del hotel y **Cerrar sesión** termina por completo la sesión.

## 5. Configuración del hotel

### 5.1 Cantidad de habitaciones

1. Abra **Configuración**.
2. Localice **Cantidad de habitaciones**.
3. Escriba un valor entre 1 y 50.
4. Seleccione **Guardar cantidad** para aplicar sólo ese bloque, o **Guardar todos los cambios** al final de la página.

Las habitaciones se numeran de `1` a `N`. Al reducir la capacidad, Gestera elimina las habitaciones finales. La operación se rechaza si alguna de ellas está ocupada, en limpieza o en mantenimiento.

### 5.2 Secciones tarifarias

Cada sección reúne un color, un nombre y dos precios: 6 horas y día completo (24 horas).

Para editar una sección:

1. Cambie el color, nombre o precios de su tarjeta.
2. Use el botón de guardado de esa tarjeta o el guardado general.

Para crear una sección:

1. Complete la tarjeta **Nueva sección**.
2. Elija un color que no esté repetido.
3. Escriba un nombre opcional. Si se deja vacío, Gestera usa `Color #RRGGBB`.
4. Ingrese ambas tarifas.
5. Seleccione **Añadir nueva sección**.

Reglas:

- debe existir entre 1 y 12 secciones;
- los nombres y colores no se repiten dentro del hotel;
- cada precio se encuentra entre S/ 1.00 y S/ 10 000.00;
- se permiten como máximo dos decimales;
- no se elimina la última sección ni una sección asignada a habitaciones.

El botón **Guardar todos los cambios** valida capacidad, ediciones, alta nueva y eliminaciones como un solo conjunto. Si una parte falla, no se guarda parcialmente el resto.

## 6. Habitaciones

### 6.1 Interpretar la vista

La vista muestra el total de habitaciones, disponibles y ocupadas. Puede filtrar por sección de color. Cada tarjeta indica el número, estado, sección y datos operativos disponibles.

Estados posibles:

- **Disponible:** lista para una nueva estancia.
- **Ocupada:** estancia activa.
- **Limpieza pendiente:** el check-out ya fue confirmado.
- **En limpieza:** trabajo de limpieza iniciado.
- **Mantenimiento:** fuera de servicio.

### 6.2 Ocupar una habitación

1. Abra **Habitaciones**.
2. Seleccione una habitación.
3. Elija la **Sección de color**.
4. Seleccione **6 horas** o **Día completo (24 horas)**.
5. Cambie el estado a **Ocupada**.
6. Seleccione **Pagado** o **No pagado**.
7. Complete los datos de los huéspedes. El formulario comienza con dos filas.
8. Use **Añadir huésped** o quite filas hasta quedar entre 1 y 8 personas.
9. Seleccione **Guardar cambios**.

El sistema registra el check-in con la hora actual de Perú y calcula el check-out. El usuario no escribe el precio ni el check-in.

### 6.3 Documentos admitidos

| Tipo | Formato |
|---|---|
| DNI | Exactamente 8 dígitos |
| Carnet de extranjería | 9–12 letras o números |
| Pasaporte | 6–12 letras o números |

No use símbolos en los documentos. Las letras se convierten a mayúsculas y un mismo documento no se puede repetir dentro de la estancia.

### 6.4 Editar una estancia

Vuelva a abrir la habitación ocupada, modifique la sección, servicio, pago o huéspedes y guarde. Gestera actualiza la misma estancia sin duplicarla.

Si se cambia la sección o el tipo de servicio, se usa la nueva tarifa y el check-out se recalcula desde el check-in original. Las ampliaciones ya registradas se conservan.

### 6.5 Registrar una ampliación

1. Abra una habitación Ocupada.
2. En **Ampliación de estadía**, elija `+1 h`, `+2 h` o `+4 h`.
3. Ingrese el nuevo cargo adicional.
4. Revise tarifa original, adicional acumulado, nuevo cargo y total.
5. Guarde.

El cargo debe estar entre S/ 0.01 y S/ 10 000.00. El tiempo y los cargos se acumulan. Registrar un cargo nuevo cambia automáticamente el pago a **No pagado**.

### 6.6 Ajustar check-out para pruebas

La opción **Cambiar hora para pruebas** sólo está disponible para una habitación Ocupada. Actívela, escriba una fecha y hora igual o posterior al check-in y guarde. Esta función permite demostrar alertas y salidas sin esperar 6 o 24 horas; no representa el flujo normal de producción.

### 6.7 Alertas y check-out

La campana de la cabecera muestra próximas salidas:

- amarillo: faltan 10 minutos o menos;
- naranja: faltan 5 minutos o menos;
- rojo: el check-out ya venció.

Una alerta vencida permite confirmar la salida:

1. Abra la campana.
2. Seleccione la habitación vencida.
3. Marque **Confirmo que se realizó el check-out de esta habitación**.
4. Confirme la operación.

La habitación pasa a **Limpieza pendiente**. Puede completarse el check-out aunque el pago siga pendiente; la advertencia y el total se conservan.

## 7. Limpieza

1. Desde el panel, abra **Limpieza de habitaciones**.
2. Seleccione una habitación en **Limpieza pendiente**.
3. Cambie el estado a **En limpieza** y guarde cuando inicie la tarea.
4. Cuando termine, cambie el estado a **Disponible** y guarde.

Al pasar a Disponible se eliminan del estado operativo los horarios, ampliaciones y cargos de esa estancia. Su historial de huéspedes y un ingreso ya confirmado permanecen en el repositorio correspondiente.

## 8. Registro de huéspedes

El módulo **Huéspedes** reúne personas asociadas a habitaciones y áreas comunes. Presenta nombre, documento, ubicación, color histórico cuando corresponde, fecha, check-in, check-out y estado.

Puede combinar estos filtros:

- nombres o apellidos;
- día visitado;
- fecha y hora **Estuvo desde**;
- fecha y hora **Estuvo hasta**;
- color de habitación.

El intervalo encuentra estancias que se cruzan con las horas seleccionadas. El filtro de color no aplica a áreas comunes.

## 9. Servicio al cuarto

### 9.1 Crear un pedido

1. Abra **Servicio al cuarto**.
2. Seleccione una habitación Ocupada.
3. Elija un producto.
4. Defina una cantidad entre 1 y 10.
5. Escriba observaciones opcionales, hasta 300 caracteres.
6. Revise el total estimado.
7. Seleccione **Crear pedido**.

El pedido queda **Pendiente**. El total definitivo lo calcula el servidor.

### 9.2 Actualizar el estado

En la tarjeta del pedido use las acciones disponibles:

```text
Pendiente ──> En preparación ──> Entregado
    └────────────> Cancelado <──────┘
```

La flecha hacia Cancelado sólo representa que un pedido Pendiente o En preparación puede cancelarse. Entregado y Cancelado son estados cerrados y no admiten nuevos cambios.

Los botones de filtro visual separan todos, pendientes, preparación y finalizados. La búsqueda del servidor permite combinar fecha, hora exacta y habitación.

### 9.3 Administrar el catálogo

Seleccione **Administrar catálogo** para:

- cambiar nombre, categoría o precio de un producto;
- agregar un producto;
- quitar un producto;
- crear una categoría;
- eliminar una categoría vacía.

Siempre debe quedar al menos una categoría. No se elimina una categoría que todavía tenga productos. Los pedidos existentes conservan sus nombres y precios originales.

## 10. Áreas comunes

1. Abra **Áreas comunes**.
2. Seleccione Jacuzzi, Sauna o Área de juegos.
3. Revise o ajuste la hora de entrada.
4. Elija una duración de 1, 3 o 5 horas.
5. Compruebe la salida calculada automáticamente.
6. Registre entre 1 y 8 visitantes.
7. Seleccione **Registrar uso del área**.

La visita aparece en **Huéspedes** con el nombre del área en la columna Ubicación. No ocupa ni modifica una habitación.

## 11. Reportes de ingresos

1. Abra **Reportes de ingresos**.
2. Seleccione el periodo: Hoy, Esta semana, Este mes, Este año o Rango personalizado.
3. Si eligió rango personalizado, indique Desde y Hasta.
4. Seleccione el tipo: ambos, sólo habitaciones o sólo servicio al cuarto.
5. Pulse **Generar reporte**.

La pantalla muestra total general, habitaciones pagadas, servicio entregado, resumen por día o mes y el detalle de cada movimiento.

Se incluyen únicamente:

- estancias marcadas como Pagado;
- pedidos cuyo estado llegó a Entregado.

Una estancia pendiente o un pedido pendiente, en preparación o cancelado no aparece como ingreso.

## 12. Cerrar sesión

Use **Cerrar sesión** en la cabecera. Gestera invalida la sesión y vuelve al formulario inicial. Si no hay actividad de renovación —las sesiones no se prolongan con cada petición—, la sesión deja de ser válida 30 minutos después de su creación.

## 13. Mensajes y solución básica

| Situación | Acción recomendada |
|---|---|
| Llave inválida | Verificar mayúsculas, minúsculas y hotel seleccionado |
| Demasiados intentos | Esperar 30 segundos antes de reintentar |
| Sesión vencida | Ingresar nuevamente con las dos llaves |
| No se puede reducir habitaciones | Liberar las habitaciones finales que no estén Disponibles |
| No se puede eliminar sección | Reasignar sus habitaciones o conservarla |
| No aparecen habitaciones en servicio | Ocupar una habitación primero |
| No se permite el check-out | Esperar la hora límite o usar el ajuste de prueba |
| Un ingreso no aparece | Confirmar pago de estancia o estado Entregado del pedido |
| Los cambios desaparecen al reiniciar | Revisar qué módulos tienen persistencia y configurar MySQL |

## 14. Alcances que el usuario no debe esperar

Gestera no crea reservas, no factura, no procesa tarjetas, no envía correos, no administra empleados y no exporta reportes. Tampoco existe recuperación automática de llaves. Estas ausencias corresponden al alcance real del proyecto y no son opciones ocultas de la interfaz.

