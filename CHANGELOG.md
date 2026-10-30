# Historial de cambios de Gestera

Las versiones utilizan el formato `MAYOR.MENOR.PARCHE`. Cada entrega publicada corresponde a una etiqueta Git `vMAYOR.MENOR.PARCHE`. Las fechas se expresan según la zona horaria de Colombia (`America/Bogota`).

## [Sin publicar]

Los cambios posteriores a 1.1.0 se registrarán aquí antes de preparar la siguiente entrega.

## [1.1.0] - 2026-10-29

### Añadido

- Carpetas `frontend` y `backend` con documentación propia.
- Mapa que relaciona cada función con su pantalla, controlador, modelo y repositorio.
- Guía de exposición con explicación de la estructura, guion y preguntas frecuentes.
- Política de versionamiento, etiquetas de entrega y comprobación automática de versiones en integración continua.

### Cambiado

- Código Java organizado en `modelo`, `controladores`, `vistas`, `repositorios`, `seguridad` e `infraestructura`.
- Paquetes e importaciones adaptados a las nuevas capas; pruebas reubicadas junto a la organización del backend.
- Plantillas HTML, estilos, JavaScript e imágenes ubicados en el frontend.
- Maven y los scripts de ejecución y verificación adaptados a las nuevas rutas.
- Documentación y enlaces actualizados para localizar cada responsabilidad.

### Corregido

- Una prueba de integración SQL consultaba los pagos utilizando la fecha fija de la estancia. Ahora utiliza la fecha real del pago para que pueda ejecutarse en días posteriores.

### Compatibilidad y validación

- La aplicación conserva sus direcciones web y el ejecutable `distribucion/gestera.jar`.
- Esta entrega reorganiza el código y no cambia el esquema SQL Server de 14 tablas.
- La reorganización se verificó con 145 comprobaciones funcionales, 11 pruebas de integración SQL y la revisión de diez pantallas en ambos temas y tres tamaños de pantalla.

## [1.0.0] - 2026-10-25

- Primera versión registrada del proyecto, correspondiente al commit `b955d51`.
- Gestión hotelera con Java, Spring Boot, SQL Server e interfaz HTML, CSS y JavaScript.
- Acceso por llaves, habitaciones, huéspedes, limpieza, servicio al cuarto, áreas comunes y reportes de ingresos.

[Sin publicar]: https://github.com/Antue15963/GESTERA/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/Antue15963/GESTERA/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Antue15963/GESTERA/tree/v1.0.0
