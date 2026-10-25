# Rediseño visual GESTERA

La presentación comparte dos paletas: azul marino, azul eléctrico y cian en modo oscuro; crema, blanco, naranja y coral en modo claro. No se modificaron controladores, permisos, autenticación, consultas ni modelos de negocio.

## Archivos

- `src/main/resources/paginas/`: las nueve páginas incorporan el selector accesible y cargan `tema.js` antes del CSS. Gestión incorpora iconos SVG locales.
- `src/main/resources/publico/estilos/gestera.css`: tokens globales, colores de componentes, tarjetas, formularios, tablas, estados, reportes, diálogos y adaptación a pantallas pequeñas.
- `src/main/resources/publico/scripts/tema.js`: preferencia `gestera.tema` en localStorage, detección del sistema operativo, restauración inicial, sincronización entre pestañas y cambio sin recarga.
- `src/main/java/com/gestera/vistas/RecursosWeb.java`: registro de los nuevos recursos dentro del catálogo cerrado existente.
- `src/main/resources/publico/imagenes/hotel-costa.png`: imagen generada con la herramienta integrada mediante la habilidad imagegen. Prompt: fotografía arquitectónica panorámica de un hotel costero de categoría premium cerca de Pucusana, terraza frente al Pacífico, atardecer cálido, edificio a la derecha, sin personas, textos ni logotipos. El CSS aplica superposiciones distintas según el tema; no es una fotografía documental del establecimiento.
- `herramientas/verificar-temas.py`: comprobación en Chrome headless, sin dependencias Python adicionales.
- `src/main/java/com/gestera/vistas/PlantillasWeb.java`: URLs de CSS y JavaScript con una versión SHA-256 derivada del contenido para evitar recursos obsoletos en caché.
- `src/main/resources/fragmentos/configuracion-llave.html`: icono de llave SVG, coherente con los módulos.
- `herramientas/ejecutar.ps1`: comprueba el puerto antes de compilar e iniciar y explica cuándo sigue abierto un servidor anterior. Imprime el paquete y la huella de la interfaz que inicia.
- `INICIAR_GESTERA.cmd`: se retiró una línea de URL suelta después de `exit`.
- `herramientas/reiniciar-servidor.ps1`: sustitución explícita de una instancia de este proyecto, validando el PID y el JAR antes de detenerla. Reiniciar cierra sesiones y elimina los registros que solo existan en memoria.
- `herramientas/verificar-interfaz-real.py`: inicia una instancia aislada del JAR y comprueba los módulos en Chrome mediante CDP, sin dependencias externas.
- `src/test/java/com/gestera/vistas/PlantillasWebPrueba.java`: regresión para comprobar las versiones de los recursos, el orden de carga y la publicación de la imagen.

## Por qué no se veían los cambios

La instancia de `http://127.0.0.1:8080` seguía ejecutando `.compilacion/ejecuciones/gestera-edc793a58e654bcd90c81caa21b4fed1.jar`, anterior al rediseño. Se comprobó por HTTP que su página no incluía `data-alternar-tema` y que `/publico/scripts/tema.js` devolvía 404, mientras el JAR de distribución ya contenía los recursos nuevos. Compilar no sustituye una copia del JAR que ya está ejecutándose: debe reiniciarse esa instancia.

Para futuros cambios, detener el servidor con Ctrl+C y ejecutar `INICIAR_GESTERA.cmd`. El arranque ahora detecta un puerto ocupado en lugar de dejar que se confunda el servidor anterior con el paquete recién compilado.

Se reinició la instancia antigua con autorización y se verificó en el puerto 8080 que el HTML incorpora el selector y las URLs versionadas. CSS, JavaScript del tema e imagen devuelven HTTP 200 y coinciden byte por byte con los archivos del proyecto.

## Validación

Ejecutar `herramientas/probar.ps1` con JDK 17 en PATH: 145 pruebas y empaquetado Maven satisfactorios. Se recompilaron artefactos de pruebas obsoletos al encontrar errores de HttpClient en la primera ejecución.

Ejecutar `python herramientas/verificar-temas.py`: 36 combinaciones de nueve plantillas, dos temas y anchos de 390 y 1440 píxeles. Comprueba selector, cambio, almacenamiento, restauración tras recarga y ausencia de desbordamiento horizontal en el contenido renderizado. Guarda resultados y capturas en `.compilacion/temas/`.

La comprobación anterior de plantillas se complementó con `python herramientas/verificar-interfaz-real.py`: 60 combinaciones de 10 rutas reales, dos temas y anchos de 390, 820 y 1440 píxeles. Usa una instancia aislada del JAR, inicia sesión por los formularios HTTP existentes y comprueba persistencia entre módulos y recargas, preferencia del sistema, paleta CSS efectiva, diálogos, movimiento reducido y ausencia de desbordamiento horizontal. Las respuestas de los recursos se comparan byte por byte con los archivos fuente. Guarda resultados y capturas en `.compilacion/interfaz-real/`.

Esta revisión usa los datos iniciales de la instancia aislada; no cubre todas las combinaciones posibles de registros o errores del usuario. No modifica datos de la instancia del puerto 8080.
