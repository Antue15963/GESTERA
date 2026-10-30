# Versionamiento de Gestera

## Versión y registro de entregas

La versión vigente se define en el elemento `project/version` del [pom.xml](../../pom.xml). El [CHANGELOG.md](../../CHANGELOG.md) registra los cambios de cada entrega y el [README](../../README.md) muestra la versión actual. Las etiquetas Git identifican los commits publicados.

| Versión | Etiqueta | Fecha de entrega | Contenido |
|---|---|---|---|
| 1.0.0 | `v1.0.0` | 2026-10-25 | Commit inicial `b955d51`. La etiqueta se incorpora para identificar la primera versión. |
| 1.1.0 | `v1.1.0` | 2026-10-29 | Separación frontend/backend, capas MVC, repositorios y documentación del proyecto. |

Las fechas de las entregas se expresan en `America/Bogota`. La configuración horaria de las operaciones del hotel continúa definida por la aplicación.

## Cómo elegir el número

Se utilizan tres números enteros: `MAYOR.MENOR.PARCHE`.

| Parte | Cuándo se aumenta | Ejemplo desde 1.1.0 |
|---|---|---|
| MAYOR | Cambios incompatibles para quien usa o integra la aplicación, como reemplazar un contrato HTTP existente. | `2.0.0` |
| MENOR | Funciones o mejoras compatibles de la aplicación o su organización. | `1.2.0` |
| PARCHE | Correcciones compatibles de errores, documentación o mantenimiento. | `1.1.1` |

Al aumentar MAYOR, MENOR y PARCHE vuelven a cero. Al aumentar MENOR, PARCHE vuelve a cero. Las etiquetas se escriben con prefijo `v`, por ejemplo `v1.1.0`.

Gestera se entrega como aplicación ejecutable. Los paquetes Java internos pueden cambiar entre entregas; las rutas HTTP y la conservación de datos se revisan al decidir la compatibilidad.

## Ramas y commits

`main` contiene las entregas y los cambios integrados. Para desarrollar una tarea se crea una rama corta desde `main`, por ejemplo:

- `feat/nombre`: una función o mejora.
- `fix/nombre`: una corrección.
- `docs/nombre`: documentación.
- `refactor/nombre`: organización interna del código.

Los commits describen el resultado con un prefijo: `feat:`, `fix:`, `docs:`, `refactor:` o `chore:`. El commit que prepara una entrega puede usar `chore(release): publicar version 1.1.0`.

Los cambios se integran después de revisar el diff y ejecutar las verificaciones aplicables. Para trabajo compartido se utiliza una solicitud de integración en GitHub. Esta política es una convención del proyecto; la protección de ramas se configura por separado en el repositorio.

## Preparar la próxima entrega

1. Integrar los cambios previstos y revisar `git status` y `git diff`.
2. Elegir la versión y actualizar `project/version` en `pom.xml` y la versión visible del `README.md`.
3. Crear la entrada de esa versión en `CHANGELOG.md`, con fecha local, cambios y notas de compatibilidad. Actualizar los enlaces de comparación y la tabla de entregas de este documento.
4. Ejecutar `python herramientas/verificar-version.py` y las pruebas que correspondan. La suite general se ejecuta con `herramientas/probar.ps1`; la integración SQL aislada, con `herramientas/probar-sqlserver.ps1`.
5. Revisar y registrar los archivos de la entrega en un commit.
6. Crear una etiqueta anotada apuntando a ese commit.
7. Subir la rama y las etiquetas explícitas. Verificar que los identificadores remotos coinciden con los locales y que la integración continua finaliza correctamente.

Ejemplo para una futura entrega 1.1.1, después de actualizar y comprobar sus archivos:

```powershell
python herramientas/verificar-version.py --etiqueta v1.1.1
git diff --check
git add -- pom.xml README.md CHANGELOG.md docs/desarrollo/VERSIONAMIENTO.md
git commit -m "chore(release): publicar version 1.1.1"
git tag -a v1.1.1 -m "Gestera 1.1.1: correcciones de mantenimiento"
git push --atomic origin main refs/tags/v1.1.1
git ls-remote origin refs/heads/main refs/tags/v1.1.1 'refs/tags/v1.1.1^{}'
```

Los archivos de la corrección deben estar incluidos en el commit o integrados previamente en `main`. `--atomic` publica la rama y la etiqueta juntas si el servidor acepta ambas referencias.

Las etiquetas de una entrega se conservan: cualquier corrección posterior recibe otra versión. Para inspeccionar una entrega sin modificar `main`, se puede usar `git switch --detach v1.1.0`; para regresar, `git switch main`, partiendo de un árbol de trabajo limpio.

## Verificación automática

[verificar-version.py](../../herramientas/verificar-version.py) comprueba:

- Formato `MAYOR.MENOR.PARCHE` de la versión del proyecto.
- Coincidencia de la versión del POM con la entrada más reciente del changelog y la versión visible del README.
- Orden, fechas válidas y ausencia de versiones duplicadas en el historial.
- Coincidencia de una etiqueta de entrega con la versión del proyecto cuando se proporciona `--etiqueta`.

La integración continua ejecuta esta comprobación antes de compilar. Cuando se envía una etiqueta, también comprueba su nombre. Las pruebas SQL reales se ejecutan localmente sobre una base temporal porque necesitan una instancia de SQL Server disponible.

## Código y archivos generados

Git conserva fuentes, pruebas, documentación, archivos SQL, configuración y scripts. `.gitignore` excluye `target`, `.compilacion`, `distribucion`, archivos locales y variables de entorno.

El paquete se genera mediante `COMPILAR_GESTERA.cmd` o `herramientas/compilar.ps1`. La versión queda registrada en los metadatos Maven incluidos en el JAR, aunque el nombre de ejecución se mantenga como `gestera.jar`.
