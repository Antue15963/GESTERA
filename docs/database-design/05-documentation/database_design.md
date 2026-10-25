> Referencia histórica anterior al modelo SQL Server V2. El esquema implementado, sus 14 tablas y atributos se describen en [SQLSERVER_14_TABLAS.md](../SQLSERVER_14_TABLAS.md).

# Diseño de datos y clases de Gestera

## Alcance analizado

El modelo se obtuvo del proyecto ejecutable, no de la imagen de referencia. Se revisaron el `README.md`, las cinco migraciones MySQL, las 55 fuentes Java principales, los repositorios JDBC y en memoria, las rutas y formularios HTTP, las plantillas y scripts del frontend, y las 16 fuentes de prueba. La compilación y las 144 pruebas automatizadas finalizaron correctamente antes de elaborar los diagramas.

La imagen aportada se usó sólo como guía visual: tablas blancas, encabezados lavanda, columnas separadas para claves/nombres/tipos y relaciones ortogonales con cardinalidades. No se incorporaron sus entidades de reservas, empleados, roles, limpieza o check-in/check-out porque Gestera no implementa esos conceptos como módulos independientes.

## 1. Entidades identificadas

- **Hotel y CredencialHotel**: los cuatro hoteles del catálogo poseen configuración e inventario independientes. La credencial es 1:1, versionada y derivada con PBKDF2; nunca se almacena texto claro.
- **SeccionTarifaria y Habitacion**: cada hotel mantiene de 1 a 12 secciones y de 1 a 50 habitaciones. La sección concentra color y tarifas de 6 y 24 horas; la habitación conserva su estado operativo.
- **AreaComun**: representa Jacuzzi, Sauna y Área de juegos dentro de cada hotel.
- **Estancia, Huesped y EstanciaHuesped**: una estancia corresponde exclusivamente a una habitación o un área común. `EstanciaHuesped` resuelve la relación N:M y limita el orden de visitantes a 1..8.
- **PagoEstancia**: pago consolidado opcional de una estancia de habitación. Su ausencia equivale a pago pendiente.
- **CatalogoServicio, CategoriaServicio y ProductoServicio**: catálogo editable e independiente por hotel.
- **PedidoServicio e ItemPedidoServicio**: cabecera e ítems del servicio al cuarto. Los ítems conservan código, nombre y precio históricos.

Los filtros de huéspedes/pedidos, los períodos del reporte y los resúmenes diarios o mensuales no son entidades: son objetos de consulta o resultados calculados. Las sesiones HTTP tampoco se persisten; son opacas, duran 30 minutos y viven deliberadamente en memoria.

## 2. Relaciones principales

- `Hotel 1 — 1..50 Habitacion` y `Hotel 1 — 1..12 SeccionTarifaria`.
- `SeccionTarifaria 1 — 0..* Habitacion`; no se elimina una sección en uso.
- `Hotel 1 — 0..* AreaComun` y `Hotel 1 — 1 CatalogoServicio`.
- `Estancia` usa una ubicación XOR: `Habitacion` o `AreaComun`, nunca ambas.
- `Estancia N — M Huesped`, resuelta mediante `EstanciaHuesped`.
- `Estancia 1 — 0..1 PagoEstancia`.
- Una estancia activa de habitación puede originar `0..* PedidoServicio`; cada pedido contiene `1..* ItemPedidoServicio`.
- `CatalogoServicio 1 — 1..* CategoriaServicio — 0..* ProductoServicio`.
- Un ítem puede conservar una referencia opcional al producto vigente, pero su snapshot histórico no depende de que el producto siga existiendo.

## 3. Normalización

El diseño se encuentra en 3FN para los datos operativos:

- hotel, sección, habitación, área, huésped, catálogo, categoría y producto tienen responsabilidades separadas;
- la relación N:M de huéspedes y estancias se resolvió con una entidad asociativa;
- categorías y productos usan claves compuestas por catálogo, permitiendo el mismo código en hoteles distintos sin duplicar `hotel_codigo` en cada producto;
- el total de una estancia se deriva de `tarifa_aplicada + monto_adicional`, y el total de un pedido se deriva de sus ítems, por lo que no se almacenan totales redundantes;
- los reportes se calculan a partir de pagos y pedidos entregados y no se materializan.

Las instantáneas de tarifa, color, código, nombre y precio no constituyen duplicación accidental: describen el hecho histórico en el momento de la estancia o del pedido. Sin ellas, editar una tarifa o retirar un producto alteraría el pasado, contradiciendo las reglas implementadas.

## 4. Decisiones del diseño lógico

Se separó el estado estable de la habitación de los datos temporales de la estancia. En el código actual ambos aparecen reunidos en el `record Habitacion`, pero una habitación puede tener muchas estancias históricas y sólo una activa.

Las visitas a áreas comunes reutilizan `Estancia` y `EstanciaHuesped`, tal como hace `GestorHuespedesHoteles.guardarVisita`, pero se reemplazó el texto libre `ubicacion` por una FK real a `AreaComun`. La restricción XOR evita ubicaciones ambiguas.

El catálogo de servicio se modeló explícitamente porque `GestorServicioHabitacion` mantiene un `CatalogoHotel` por código de hotel. No se crearon usuarios, empleados, roles ni reservas: el acceso real usa una llave general y una llave versionada por hotel.

## 5. Decisiones del diseño físico

El script [database_schema.sql](../04-sql/database_schema.sql) está ordenado por dependencias y usa MySQL 8.0, InnoDB y `utf8mb4`. Incluye PK, FK, `UNIQUE`, `NOT NULL`, valores predeterminados, `AUTO_INCREMENT`, checks, índices y acciones `ON UPDATE`/`ON DELETE`.

Decisiones relevantes:

- `DATETIME` conserva los valores de negocio calculados en `America/Lima`; la aplicación sigue siendo responsable de esa zona horaria.
- Los importes usan `DECIMAL(10,2) UNSIGNED` y checks compatibles con los límites del backend.
- `habitacion_activa_id` es una columna generada con índice único: impide dos estancias activas simultáneas para la misma habitación.
- Las FK compuestas aseguran que una habitación sólo use una sección de su mismo hotel y que un producto sólo use una categoría de su catálogo.
- Dos triggers validan que un pago pertenezca a una estancia de habitación; otro exige una estancia activa y una habitación ocupada al crear un pedido.
- `ON DELETE RESTRICT` protege historia e inventario; `CASCADE` se usa únicamente en dependencias sin vida propia, como asociaciones e ítems.

Este archivo representa el **esquema objetivo completo**. Es ejecutable para crear una base nueva, pero no es una migración directa desde V001–V005 ni puede sustituirlas sin adaptar los repositorios Java, porque normaliza varias estructuras que hoy tienen otra forma.

## 6. Clases principales

El UML muestra clases del dominio y servicios reales, no una clase por tabla:

- `ServidorGestera` coordina autenticación, sesiones, inventario, huéspedes, áreas, servicio y reportes.
- `GestorHabitacionesHoteles` actúa como fachada; `InventarioHotel` concentra la mutación atómica de habitaciones y secciones.
- `Habitacion` y `SeccionTarifaria` son records de dominio con cálculo de total y selección de tarifa.
- `GestorHuespedesHoteles` valida documentos y opera a través de `RepositorioHuespedes`.
- `GestorAreasComunes` calcula salidas y delega el registro de visitantes.
- `GestorServicioHabitacion` administra catálogo y transiciones; `PedidoServicio` compone líneas que calculan subtotales.
- `GestorReportes` combina `IngresoHabitacion` con pedidos entregados según `FiltroReporte`.
- Los tres puertos de persistencia tienen implementaciones JDBC y memoria, lo que mantiene el dominio desacoplado de MySQL.

## 7. Relación entre clases y persistencia

Los records `DatosHuesped`, `PedidoServicio`, `LineaPedidoServicio` e `IngresoHabitacion` son transferidos por los puertos de repositorio. Los gestores contienen las reglas y los adaptadores JDBC traducen a SQL. `Habitacion`, `SeccionTarifaria`, `AreaComun`, credenciales y catálogo editable aún no tienen repositorio en el código actual; sus tablas del diseño físico indican cómo completar esa persistencia sin cambiar las responsabilidades del dominio.

`ReporteIngresos`, `DetalleIngreso` y `ResumenPeriodo` son proyecciones calculadas, por eso no tienen tablas. `GestorSesiones` también permanece fuera del esquema por su carácter efímero y porque no existe una identidad de usuario persistente.

## 8. Supuestos e inconsistencias encontradas

- Las migraciones V001–V005 sólo persisten hoteles, huéspedes/estancias, pedidos/ítems e ingresos de habitación. Inventario, secciones, tarifas, estado operativo, credenciales y catálogo editable se pierden al reiniciar; el diseño objetivo añade esas tablas.
- V004 representa áreas comunes mediante `estancias.ubicacion` como texto y `numero_habitacion = NULL`; no existe FK a un catálogo de áreas. El diseño objetivo normaliza esa ubicación.
- Una visita a área común se inserta hoy con estado `ACTIVA` y no hay una operación que la finalice, aunque tiene `check_out` calculado. El modelo conserva el estado, pero una futura adaptación debería finalizarla automáticamente al vencer o tratarla como visita programada.
- V005 identifica el ingreso por `(hotel, habitación, check-in)` porque el esquema vigente no enlaza ingreso y estancia. El objetivo usa `pagos_estancia.estancia_id UNIQUE`, que expresa la misma regla sin repetir la identidad.
- Los pedidos vigentes referencian hotel y número de habitación, no la estancia concreta. El objetivo los enlaza a la estancia activa para evitar ambigüedad histórica.
- Los enums de documento de las migraciones están en minúsculas y los enums Java tienen constantes en mayúsculas con códigos en minúsculas. El esquema objetivo usa nombres canónicos en mayúsculas; el adaptador deberá mapearlos de forma explícita.
- La llave general de Gestera continúa siendo configuración del proceso y no se modela en MySQL. La tabla `credenciales_hotel` cubre únicamente las llaves independientes y versionadas por hotel.

## Validación final

- Todas las FK apuntan a PK o claves `UNIQUE` compatibles.
- La única relación N:M está resuelta por `estancia_huespedes`.
- Las cardinalidades de los diagramas lógico y físico coinciden.
- El SQL contiene las mismas 14 tablas del modelo físico y respeta el orden de dependencias.
- El UML representa servicios, records y puertos reales, sin replicar mecánicamente las tablas.
- Los PNG y SVG se generan de forma reproducible con `python docs/database-design/render_diagrams.py`.

