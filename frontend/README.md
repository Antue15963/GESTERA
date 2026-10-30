# Frontend de Gestera

Aquí están las plantillas y los recursos que forman la interfaz del navegador.

```text
frontend/
├── vistas/
│   ├── paginas/       Pantallas HTML completas
│   └── fragmentos/    Partes HTML reutilizables
└── publico/
    ├── estilos/       Apariencia y distribución: gestera.css
    ├── scripts/       Interacción, formularios, filtros y alertas
    └── imagenes/      Recursos gráficos
```

| Función visible | Plantilla en `vistas/paginas/` | Script en `publico/scripts/` |
|---|---|---|
| Acceso general | `acceso.html` | `acceso.js` |
| Selección de hotel | `panel.html` | `hoteles.js` |
| Panel de gestión | `gestion.html` | `gestion.js` |
| Configuración del hotel | `configuracion-hotel.html` | `configuracion-hotel.js` |
| Habitaciones y limpieza | `habitaciones.html` | `habitaciones.js` |
| Historial de huéspedes | `huespedes.html` | Formularios enviados al backend |
| Servicio al cuarto | `servicio-habitacion.html` | `servicio-habitacion.js` |
| Áreas comunes | `areas-comunes.html` | `areas-comunes.js` |
| Reportes | `reportes.html` | `reportes.js` |

`tema.js` controla el tema visual compartido. `publico/estilos/gestera.css` contiene los estilos de la aplicación.

Las páginas son plantillas con marcadores `{{...}}` que el backend completa mediante `PlantillasWeb` y `ComponentesHtml`. Se utilizan desde el servidor de Gestera para obtener los datos y autorizaciones del hotel.

Maven empaqueta `vistas/paginas` y `vistas/fragmentos` como `paginas` y `fragmentos` dentro del JAR. Los recursos de `publico` conservan su ruta `/publico/...`. Por eso la separación física de carpetas mantiene las direcciones de la aplicación.

Para ejecutar, utiliza `INICIAR_GESTERA.cmd` desde la raíz y abre `http://127.0.0.1:8080`, con la configuración predeterminada.

Consulta el [mapa de funciones y explicación de MVC](../docs/desarrollo/ESTRUCTURA_Y_MVC.md).
