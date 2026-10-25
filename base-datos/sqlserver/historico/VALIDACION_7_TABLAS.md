# Validación del instalador de Gestera para SQL Server

**Resultado: instalación e integridad verificadas contra un motor real de SQL Server.**

## Archivo validado

- Archivo: `CREAR_GESTERA_SQLSERVER.sql`.
- Codificación final: UTF-8 con BOM, para identificar correctamente los textos con tildes al abrirlo en SSMS.
- Tamaño: 27109 bytes.
- SHA-256: 6F1369E98BF86431FAC860A54D579436EAFFCC35DD49FD78EF149085E10E5F4D.

## Entorno y aislamiento

- Microsoft SQL Server **17.0.1000.7**, Standard Developer Edition, 64 bits.
- Ejecución con `sqlcmd`, separación normal de lotes `GO` y lectura UTF-8.
- Se ejecutó una copia del instalador que sustituía exclusivamente el nombre fijo de la base por `gestera_validacion_` seguido de un identificador aleatorio. Se comprobó que el resto del contenido SQL coincidiera con el entregable.
- Se ejecutó esa copia dos veces sobre la misma base temporal. Ambas terminaron correctamente y conservaron los cuatro hoteles iniciales sin duplicarlos.
- Las pruebas de datos se ejecutaron dentro de una transacción que terminó en `ROLLBACK`.
- La base temporal fue eliminada al terminar, verificando previamente su nombre y la marca de este instalador. En la validación inicial no se instaló una base real gestera. La migración posterior sí la instaló/verificó; consultar docs/desarrollo/MIGRACION_SQLSERVER.md.

## Comprobaciones satisfactorias

| Área | Comprobación |
|---|---|
| Estructura | Siete tablas, 43 columnas, tipos, longitudes, nulabilidad e identidades previstas. |
| Claves | Siete PK, cuatro restricciones únicas y seis FK. |
| Validaciones | 23 restricciones CHECK y diez valores predeterminados; restricciones habilitadas y confiables. |
| Índices | Presencia y habilitación de los diez índices operativos adicionales. |
| Auditoría | Tres triggers habilitados; actualización de varias filas y conservación del conteo de filas. |
| Datos iniciales | Cuatro hoteles con los códigos del catálogo Java. |
| Textos | Conservación de nombres Unicode, tildes, eñes y ceros iniciales de documentos. |
| Identificadores | Generación de claves `IDENTITY` y recuperación mediante `SCOPE_IDENTITY()` en T-SQL. |
| Documentos | Rechazo de DNI/carnet con longitud incorrecta, pasaporte sin normalizar y código documental con capitalización incorrecta. |
| Duplicados | Rechazo de documento repetido, estancia repetida, orden repetido de huésped e ingreso repetido por estancia. |
| Horarios | Rechazo de salida anterior a la entrada y conservación de precisión temporal en pedidos. |
| Áreas comunes | Admisión de habitación `NULL`, ubicación obligatoria y valores predeterminados. |
| Dominios | Rechazo de habitación fuera de 1–50, posición de huésped fuera de 1–8, cantidad de ítems fuera de 1–10 y color inválido. |
| Estados | Rechazo de estados ajenos a los dominios de estancias y pedidos. |
| Integridad referencial | Rechazo de hotel, huésped o pedido inexistentes; protección frente a eliminar hotel/huésped relacionados. |
| Cascadas | Eliminación de ítems al eliminar su pedido y asociaciones al eliminar su estancia; conservación del huésped. |
| Dinero | Cálculo exacto de `15.50 × 2 = 31.00`; rechazo de precio cero e ingreso negativo. |
| Orden del backend | Admisión del pago antes de insertar la estancia, como hace el controlador actual. |
| Concurrencia del pedido | UPDATE condicionado por estado previo devuelve una fila al aplicar el cambio y cero al repetirlo con el estado anterior. |
| Permisos | Usuario de prueba asociado al rol: lectura y operaciones previstas permitidas, eliminación de hoteles y ALTER del esquema no permitidos. |
| Filtros | Escape literal de `[` mediante `![` y `ESCAPE '!'` comprobado en el motor. |
| Limpieza | Rollback de datos de prueba y eliminación posterior de la base temporal confirmados. |

## Límites de la validación

La ejecución se verificó en SQL Server 17; no se ejecutó en instancias de cada versión anterior. El instalador establece SQL Server 2016 como versión mínima.

La comprobación inicial del esquema se amplió con la migración Java: ahora el backend usa Spring Boot, Spring JDBC, HikariCP y Microsoft JDBC Driver. Las pruebas reales de repositorios y el arranque con conexión cifrada pasaron correctamente.

Consultar [el registro de migración](../../docs/desarrollo/MIGRACION_SQLSERVER.md) para resultados, alcance y limpieza. El esquema conserva siete tablas; los módulos operativos que todavía viven en memoria se señalan en la guía de desarrollo.
