> Referencia histórica anterior al modelo SQL Server V2. El esquema implementado, sus 14 tablas y atributos se describen en [SQLSERVER_14_TABLAS.md](../database-design/SQLSERVER_14_TABLAS.md).

# Gestera — preguntas frecuentes (FAQ)

## Preguntas de uso

### 1. ¿Gestera administra reservas?

No. El código actual administra la operación de una estancia desde que una habitación se marca como Ocupada. Aunque una frase promocional de la pantalla inicial menciona reservas, no existen rutas, clases, tablas ni pantallas que implementen un módulo de reservas.

### 2. ¿Cuántos hoteles hay?

Hay cuatro: Hotel El Sol de Pucusana, Hotel Brisa Marina, Hotel Valle Dorado y Hotel Costa Serena. Están definidos en el código y no pueden agregarse desde la interfaz.

### 3. ¿Por qué se solicitan dos llaves?

La primera abre el servicio general y la segunda autoriza un hotel concreto. Así, entrar al directorio no concede automáticamente acceso operativo a todos los establecimientos.

### 4. ¿Cuáles son las llaves para la evaluación local?

La llave general predeterminada es `123456789abc` y cada hotel inicia con `gesteraperu123`. Deben cambiarse antes de cualquier despliegue real.

### 5. ¿Qué requisitos tiene una nueva llave de hotel?

Debe contener entre 8 y 15 caracteres, al menos una letra mayúscula y al menos un número. La confirmación debe coincidir exactamente.

### 6. ¿Cambiar una llave afecta a todos los hoteles?

No. Cada hotel tiene credencial y versión independientes. El cambio invalida autorizaciones anteriores de ese hotel, pero no modifica la llave general ni las de otros hoteles.

### 7. ¿Qué ocurre si olvido una llave cambiada?

No existe recuperación en la interfaz. Como las llaves de hotel se mantienen en memoria, reiniciar el servidor restaura la llave inicial configurada. En un despliegue real esto debe reemplazarse por un procedimiento seguro y persistente.

### 8. ¿Cuánto dura la sesión?

Treinta minutos desde su creación. El token es opaco, se guarda en una cookie `HttpOnly` y no contiene ninguna llave.

### 9. ¿Por qué aparece “Demasiados intentos”?

Después de cinco fallos consecutivos desde un mismo origen, Gestera bloquea ese tipo de acceso durante 30 segundos. La llave general y la llave de hotel tienen limitadores separados.

### 10. ¿Cuántas habitaciones puede tener un hotel?

Entre 1 y 50. Cada hotel inicia con 12 habitaciones numeradas consecutivamente.

### 11. ¿Por qué no puedo reducir la cantidad de habitaciones?

Gestera sólo elimina habitaciones desde el final de la numeración. Si una de las que desaparecería no está Disponible, la reducción se rechaza para no perder una operación activa.

### 12. ¿Cuántas secciones tarifarias se permiten?

De 1 a 12 por hotel. No se pueden repetir color ni nombre y no se puede eliminar la última sección.

### 13. ¿Por qué no puedo eliminar una sección?

Porque sigue asignada a una habitación o porque es la única sección restante. Reasigne primero las habitaciones y conserve al menos una sección.

### 14. ¿Qué tarifas se admiten?

Cada sección tiene un precio de 6 horas y otro de día completo, equivalente a 24 horas. Cada valor debe estar entre S/ 1.00 y S/ 10 000.00 y tener como máximo dos decimales.

### 15. ¿Cuántos huéspedes se registran por habitación?

Entre 1 y 8. La interfaz presenta dos filas al comenzar, pero se pueden agregar o quitar.

### 16. ¿Qué documentos se aceptan?

DNI de 8 dígitos, carnet de extranjería de 9 a 12 caracteres alfanuméricos y pasaporte de 6 a 12 caracteres alfanuméricos. No se aceptan símbolos ni documentos repetidos dentro de la misma estancia.

### 17. ¿Se puede escribir manualmente el check-in o el precio?

No. El servidor registra el check-in actual en `America/Lima` y obtiene el precio de la sección y el servicio. La vista previa del navegador no es la autoridad.

### 18. ¿Para qué sirve “Cambiar hora para pruebas”?

Permite adelantar o modificar el check-out de una habitación Ocupada para probar alertas. El nuevo valor no puede ser anterior al check-in. La función está identificada expresamente como apoyo de pruebas.

### 19. ¿Qué ampliaciones existen?

Se puede añadir 1, 2 o 4 horas. Cada ampliación requiere un cargo positivo de hasta S/ 10 000.00. El tiempo y el dinero se acumulan.

### 20. ¿Por qué una habitación volvió a “No pagado”?

Registrar un cargo adicional reabre el pago para que el nuevo total sea confirmado. Cuando se marque nuevamente Pagado, el ingreso de la misma estancia se actualiza sin duplicarse.

### 21. ¿Se puede confirmar el check-out antes de la hora?

No. El servidor rechaza una confirmación anticipada. Cuando vence, la salida puede confirmarse y la habitación pasa a Limpieza pendiente.

### 22. ¿El check-out exige que la habitación esté pagada?

No. Una salida vencida se puede confirmar aunque el pago esté pendiente. Gestera conserva el indicador, los horarios y el total para mantener visible la deuda operativa.

### 23. ¿Qué significan los colores de la campana?

Amarillo desde 10 minutos antes, naranja desde 5 minutos antes y rojo cuando el check-out ya llegó. El navegador sólo puede reproducir el aviso sonoro después de una interacción del usuario.

### 24. ¿Qué habitaciones aparecen en Limpieza?

Sólo las que están en Limpieza pendiente o En limpieza. Al pasarlas a Disponible dejan de aparecer en esa vista.

### 25. ¿Una habitación Disponible conserva datos de la estancia anterior?

No en su estado operativo: se limpian horarios, ampliaciones, cargos y pago. El historial de huéspedes y los ingresos ya confirmados se conservan en su repositorio.

### 26. ¿Por qué no puedo crear un pedido?

El servicio al cuarto sólo admite habitaciones Ocupadas. También debe existir al menos un producto en el catálogo y la cantidad debe estar entre 1 y 10.

### 27. ¿Qué estados puede tener un pedido?

Pendiente, En preparación, Entregado y Cancelado. El flujo normal es Pendiente → En preparación → Entregado. Pendiente o En preparación pueden pasar a Cancelado; los estados finales ya no cambian.

### 28. ¿Editar un producto cambia pedidos anteriores?

No. Cada pedido guarda una instantánea del código, nombre y precio. Las modificaciones del catálogo sólo afectan pedidos nuevos.

### 29. ¿Se puede eliminar cualquier categoría?

No. Debe estar vacía y siempre debe quedar al menos una categoría en el hotel.

### 30. ¿Qué áreas comunes están disponibles?

Jacuzzi, Sauna y Área de juegos. Se registra una entrada y una duración de 1, 3 o 5 horas, además de 1 a 8 visitantes.

### 31. ¿Usar un área común ocupa una habitación?

No. La visita se añade al historial de huéspedes con el nombre del área como ubicación, pero no modifica el inventario de cuartos.

### 32. ¿Qué cuenta como ingreso?

Una estancia marcada como Pagado y un pedido que llegó a Entregado. Los pagos pendientes y los pedidos pendientes, en preparación o cancelados no se suman.

### 33. ¿Qué periodos permiten los reportes?

Hoy, semana actual, mes actual, año actual o un rango personalizado de hasta 3 653 días. El reporte puede incluir ambos orígenes, sólo habitaciones o sólo servicio al cuarto.

### 34. ¿Se puede exportar el reporte?

No. El proyecto actual sólo presenta el reporte en HTML. No existen exportaciones a PDF, Excel o CSV.

## Preguntas técnicas

### 35. ¿MySQL es obligatorio?

No. Sin configuración MySQL, Gestera usa repositorios en memoria y es plenamente demostrable. Los datos se pierden al cerrar el proceso.

### 36. ¿Qué se guarda en MySQL actualmente?

Hoteles sincronizados, huéspedes, estancias, relación estancia-huésped, pedidos, ítems e ingresos de habitaciones. Las visitas a áreas comunes se guardan como estancias con ubicación textual.

### 37. ¿Qué sigue viviendo en memoria aunque MySQL esté activo?

Llaves y sus versiones, capacidad, secciones, tarifas, estado operativo de habitaciones, indicador operativo de pago y catálogo editable de servicio al cuarto.

### 38. ¿Por qué `database_schema.sql` tiene más tablas que las migraciones?

Ese archivo representa un diseño físico objetivo para una evolución académica. Normaliza credenciales, inventario, áreas, pagos y catálogo, pero los repositorios actuales esperan el esquema progresivo de `V001` a `V005`. No debe sustituirse sin modificar el código.

### 39. ¿Gestera ofrece una API JSON?

No. Usa formularios URL-encoded y responde con páginas HTML, redirecciones o texto. Las rutas se documentan porque forman una interfaz HTTP interna, no porque constituyan una API REST pública.

### 40. ¿Qué tecnologías utiliza?

Java 17, `jdk.httpserver`, JDBC, HTML5, CSS, JavaScript, PowerShell y MySQL opcional. No utiliza Spring, Maven, Gradle, React, Angular, Node.js ni JUnit.

### 41. ¿Cómo se compila?

Con `COMPILAR_GESTERA.cmd` o `./herramientas/compilar.ps1`. El script llama a `javac --release 17`, copia 20 recursos y construye `distribucion/gestera.jar`.

### 42. ¿Cómo se ejecutan las pruebas?

Con `./herramientas/probar.ps1`. En la versión revisada se ejecutan 144 pruebas y todas deben finalizar correctamente.

### 43. ¿Dónde se encuentra la documentación de clases y métodos?

En `01-javadoc-gestera.zip`. Descomprima el archivo y abra `index.html`.

### 44. ¿Está listo para publicarse directamente en Internet?

No. Aunque incorpora derivación segura de llaves, limitación de intentos, cookies protegidas y encabezados HTTP, un entorno real requiere HTTPS, secretos externos, persistencia completa, copias de seguridad, auditoría y endurecimiento de infraestructura.

