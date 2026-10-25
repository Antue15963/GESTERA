> Referencia histórica anterior al modelo SQL Server V2. El esquema implementado, sus 14 tablas y atributos se describen en [SQLSERVER_14_TABLAS.md](../database-design/SQLSERVER_14_TABLAS.md).

# Gestera — guías paso a paso

Estas guías resuelven tareas concretas del proyecto. Cada una indica el punto de partida, las acciones y el resultado que debe comprobarse.

## Guía 1. Iniciar Gestera para una demostración

**Requisito:** JDK 17 o superior instalado.

1. Abra la carpeta raíz del proyecto.
2. Ejecute `INICIAR_GESTERA.cmd`.
3. Espere el mensaje `Gestera está disponible en http://127.0.0.1:8080`.
4. Abra esa dirección en el navegador.
5. Use la llave general local `123456789abc`.
6. Para detener el servidor, vuelva a la consola y pulse `Ctrl+C`.

**Resultado esperado:** aparece el directorio con cuatro hoteles.

**Alternativa PowerShell:**

```powershell
./herramientas/ejecutar.ps1
```

## Guía 2. Completar el primer acceso a un hotel

**Punto de partida:** directorio de hoteles abierto.

1. Seleccione, por ejemplo, **Hotel El Sol de Pucusana**.
2. En la ventana emergente escriba la llave inicial `gesteraperu123`.
3. Pulse el botón de ingreso.
4. En el panel del hotel, abra **Configuración requerida**.
5. Escriba una llave de 8 a 15 caracteres que incluya mayúscula y número; por ejemplo, use un valor propio y no uno compartido en esta guía.
6. Repita exactamente el valor.
7. Guarde.

**Resultado esperado:** el panel continúa autorizado, la tarjeta de configuración requerida desaparece y la llave anterior deja de ingresar a ese hotel.

**Dato importante:** los otros tres hoteles conservan su propia llave. El cambio se pierde al reiniciar porque las credenciales actuales viven en memoria.

## Guía 3. Configurar capacidad y tarifas

**Punto de partida:** panel de un hotel autorizado.

1. Abra **Configuración** desde la cabecera.
2. Cambie **Cantidad de habitaciones** a un valor entre 1 y 50.
3. Edite una sección existente: color, nombre, tarifa de 6 horas y tarifa de día completo.
4. Si necesita una sección adicional, complete **Nueva sección**.
5. Revise que no haya colores o nombres repetidos.
6. Pulse **Guardar todos los cambios**.

**Resultado esperado:** capacidad y secciones se actualizan juntas y las habitaciones conservadas muestran las nuevas referencias de sección.

**Si se rechaza:**

- una habitación que sería eliminada no está Disponible;
- una sección marcada para eliminar todavía está asignada;
- se intenta eliminar la última sección;
- hay un nombre o color repetido;
- una tarifa está fuera de S/ 1.00–10 000.00.

## Guía 4. Registrar una estancia pagada

**Punto de partida:** existe una habitación Disponible.

1. Abra **Habitaciones**.
2. Seleccione una tarjeta Disponible.
3. Escoja la sección tarifaria.
4. Seleccione **6 horas** o **Día completo**.
5. Cambie el estado a **Ocupada**.
6. En **Estado del pago**, seleccione **Pagado**.
7. Complete entre 1 y 8 huéspedes.
8. Verifique cada documento:
   - DNI: 8 dígitos;
   - carnet de extranjería: 9–12 caracteres alfanuméricos;
   - pasaporte: 6–12 caracteres alfanuméricos.
9. Guarde.

**Resultado esperado:** la tarjeta muestra la habitación Ocupada, check-in y check-out calculados. El módulo Huéspedes contiene las personas y Reportes incluye el monto pagado en el periodo correspondiente.

## Guía 5. Ampliar una estancia y volver a registrar el pago

**Punto de partida:** habitación Ocupada, incluso si ya figuraba Pagada.

1. Abra la habitación.
2. Elija `+1 h`, `+2 h` o `+4 h`.
3. Escriba el cargo adicional con máximo dos decimales.
4. Revise el nuevo total.
5. Guarde.
6. Vuelva a abrir la habitación.
7. Compruebe que figura **No pagado** por el cargo nuevo.
8. Cambie el pago a **Pagado** y guarde nuevamente.

**Resultado esperado:** el check-out suma las horas, el cargo queda acumulado y el reporte actualiza el ingreso de la misma estancia sin duplicarlo.

## Guía 6. Demostrar una alerta y completar limpieza

**Punto de partida:** habitación Ocupada.

1. Abra la habitación y active **Cambiar hora para pruebas**.
2. Fije un check-out igual o posterior al check-in y cercano a la hora actual.
3. Guarde.
4. Realice una interacción en la página para permitir el audio del navegador.
5. Abra la campana de alertas y espere hasta que la salida figure vencida.
6. Abra la confirmación de la habitación.
7. Marque la casilla que confirma el check-out.
8. Confirme la salida.
9. Abra **Limpieza de habitaciones**.
10. Cambie la habitación de Limpieza pendiente a En limpieza.
11. Cuando termine la tarea, cámbiela a Disponible.

**Resultado esperado:** la estancia queda finalizada en Huéspedes; la habitación sale de la vista de limpieza cuando vuelve a Disponible.

## Guía 7. Crear y entregar un pedido de servicio al cuarto

**Punto de partida:** al menos una habitación Ocupada.

1. Abra **Servicio al cuarto**.
2. Seleccione la habitación ocupada.
3. Elija un producto del catálogo.
4. Indique una cantidad de 1 a 10.
5. Añada observaciones si son necesarias.
6. Revise el total estimado y cree el pedido.
7. En la cola, cambie el pedido de Pendiente a En preparación.
8. Cambie después de En preparación a Entregado.
9. Abra **Reportes de ingresos** y consulte el periodo actual.

**Resultado esperado:** el pedido entregado aparece en el detalle del reporte como ingreso de servicio. Un pedido sólo Pendiente, En preparación o Cancelado no aparece.

## Guía 8. Personalizar el catálogo sin alterar el historial

**Punto de partida:** módulo Servicio al cuarto.

1. Seleccione **Administrar catálogo**.
2. Para editar, elija un producto, cambie nombre, categoría o precio y guarde.
3. Para agregar, complete nombre, categoría existente y precio.
4. Para crear una categoría, escriba su nombre en el bloque de categorías.
5. Para quitar una categoría, elimine primero todos sus productos y asegúrese de que quede otra categoría.
6. Cierre el cuadro del catálogo.

**Resultado esperado:** los cambios aparecen en pedidos nuevos de ese hotel. Los pedidos anteriores conservan la instantánea de nombre y precio con la que fueron creados.

## Guía 9. Registrar visitantes de un área común

**Punto de partida:** panel del hotel.

1. Abra **Áreas comunes**.
2. Seleccione Jacuzzi, Sauna o Área de juegos.
3. Confirme la entrada precargada con la hora de Perú.
4. Elija 1, 3 o 5 horas.
5. Compruebe la salida calculada.
6. Complete de 1 a 8 visitantes con documentos válidos.
7. Pulse **Registrar uso del área**.

**Resultado esperado:** Gestera redirige a Huéspedes y muestra a cada visitante con el área como Ubicación. Ninguna habitación cambia de estado.

## Guía 10. Buscar una persona en el historial

**Punto de partida:** módulo Huéspedes.

1. Escriba una parte del nombre o apellido.
2. Si es necesario, seleccione el día visitado.
3. Para saber si estuvo presente durante un intervalo, complete **Estuvo desde** y **Estuvo hasta**.
4. Para estancias de habitación, puede elegir también un color.
5. Pulse **Buscar**.

**Resultado esperado:** se muestran únicamente registros que cumplen todos los criterios indicados. El intervalo incluye estancias que se superponen con las horas consultadas.

## Guía 11. Generar un reporte por rango personalizado

**Punto de partida:** módulo Reportes de ingresos.

1. En Periodo, elija **Rango personalizado**.
2. Seleccione la fecha inicial.
3. Seleccione la fecha final, igual o posterior a la inicial.
4. Elija Habitaciones y servicio, Sólo habitaciones o Sólo servicio al cuarto.
5. Pulse **Generar reporte**.
6. Revise los tres totales y el detalle de movimientos.

**Resultado esperado:** un rango de hasta 62 días se resume diariamente; un rango mayor se resume mensualmente. El máximo permitido es de 3 653 días.

## Guía 12. Activar persistencia MySQL

**Destinatario:** desarrollador o encargado técnico.

1. Instale MySQL 8.0 o superior.
2. Ejecute en orden los archivos `V001` a `V005` de `base-datos/migraciones/`.
3. Copie `mysql-connector-j-<version>.jar` en `bibliotecas/`.
4. En la misma terminal defina:

```powershell
$env:GESTERA_DB_URL = 'jdbc:mysql://127.0.0.1:3306/gestera?useUnicode=true&characterEncoding=UTF-8&serverTimezone=America/Lima'
$env:GESTERA_DB_USUARIO = 'gestera_app'
$env:GESTERA_DB_CONTRASENA = 'una-contrasena-segura'
```

5. Ejecute `./herramientas/ejecutar.ps1`.
6. Registre una estancia o un pedido.
7. Reinicie Gestera con las mismas variables y compruebe el historial.

**Resultado esperado:** huéspedes, estancias, visitas, pedidos, ítems e ingresos de habitación se conservan.

**No persistirá todavía:** llave cambiada, capacidad, secciones, estado de habitaciones, pago operativo ni catálogo editable. El archivo `docs/database-design/04-sql/database_schema.sql` propone cómo ampliar el modelo, pero no debe reemplazar las migraciones vigentes sin adaptar los repositorios.

## Guía 13. Compilar y comprobar una modificación

**Destinatario:** desarrollador.

1. Modifique el archivo del módulo responsable.
2. Si cambió una regla, agregue o actualice su prueba.
3. Ejecute:

```powershell
./herramientas/probar.ps1
```

4. Verifique que se compilen 55 archivos principales y 16 de prueba.
5. Confirme el mensaje `OK - 144 pruebas superadas` o una cifra mayor si agregó casos.
6. Compruebe que `distribucion/gestera.jar` fue actualizado.

**Resultado esperado:** no hay fallos de compilación ni regresiones en el runner.

