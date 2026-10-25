"""Diccionario reproducible a partir de las columnas del DDL normalizado."""
from pathlib import Path
import argparse
import re

ROOT=Path(__file__).resolve().parent.parent
SQL=(ROOT/"base-datos/sqlserver/esquema/01_tablas.sql").read_text(encoding="utf-8")
DEST=ROOT/"docs/database-design/SQLSERVER_14_TABLAS.md"
DESCRIPCIONES={
 "hoteles":"Identidad y ubicación de cada establecimiento.",
 "credenciales_hotel":"Material PBKDF2 y versión de la llave independiente del hotel.",
 "secciones_tarifarias":"Catálogo de colores y tarifas de seis y veinticuatro horas.",
 "habitaciones":"Inventario permanente y estado operativo de cada cuarto.",
 "areas_comunes":"Espacios disponibles para registrar visitas de cada hotel.",
 "huespedes":"Identidad documental reutilizable de cada persona.",
 "estancias":"Ocupaciones y visitas, con una ubicación exclusiva y sus datos históricos.",
 "estancia_huespedes":"Relación N:M y orden de los participantes de la estancia.",
 "pagos_estancia":"Pago consolidado opcional de una estancia de habitación.",
 "catalogos_servicio":"Catálogo independiente por hotel y versión para concurrencia.",
 "categorias_servicio":"Agrupación de productos dentro de un catálogo.",
 "productos_servicio":"Productos vigentes y sus precios actuales.",
 "pedidos_servicio":"Pedido asociado a una estancia concreta.",
 "pedido_servicio_items":"Cantidades y snapshots históricos de los productos solicitados."
}
EXTRAS={
 "habitaciones.activa":"Baja lógica: reducir el inventario conserva las referencias históricas.",
 "habitaciones.tipo_servicio_preferido":"Conserva la selección de servicio incluso cuando el cuarto está disponible.",
 "catalogos_servicio.version":"Versión optimista: evita sobrescribir cambios de otra sesión.",
 "estancias.origen":"GESTERA para registros nuevos; IMPORTADA para historia V1 con tarifas desconocidas.",
 "estancias.habitacion_activa_id":"Calculado; la exclusividad se garantiza con un índice único filtrado.",
 "estancias.monto_adicional":"Acumulado de ampliaciones. DECIMAL(12,2) evita limitar el total al máximo de un único cargo.",
 "credenciales_hotel.sal":"16 bytes aleatorios, distintos por credencial.",
 "credenciales_hotel.hash_clave":"32 bytes derivados; no contiene la llave en texto claro.",
 "creado_en":"Auditoría automática en UTC, salvo la fecha de negocio proporcionada para pedidos.",
 "actualizado_en":"Auditoría UTC; en pedidos es la hora local del último cambio de estado.",
 "check_in":"Entrada en hora local America/Lima; precisión de segundos.",
 "check_out":"Salida calculada o ajustada; no anterior a la entrada.",
 "pagado_en":"Fecha de negocio del pago, en America/Lima.",
}
SIGNIFICADOS={
 "id":"Identificador interno generado por SQL Server.",
 "codigo":"Identificador estable usado por Java y los formularios.",
 "hotel_codigo":"Hotel propietario del registro.",
 "nombre":"Nombre visible en las pantallas.",
 "ubicacion":"Ubicación del establecimiento.",
 "iniciales":"Dos caracteres mostrados en el selector de hoteles.",
 "activo":"Permite mostrar el hotel en el catálogo operativo.",
 "activa":"Incluye la habitación en el inventario actual.",
 "sal":"Dato aleatorio usado al derivar la llave.",
 "hash_clave":"Resultado de la derivación criptográfica de la llave.",
 "algoritmo":"Algoritmo de derivación admitido: PBKDF2WithHmacSHA256.",
 "iteraciones":"Costo de la derivación; entre 10000 y 10000000.",
 "version":"Contador de cambios para controlar concurrencia.",
 "requiere_cambio":"Indica que todavía se debe completar el cambio inicial de llave.",
 "color_hex":"Color actual de la sección, en formato #RRGGBB.",
 "precio_seis_horas":"Tarifa actual de alojamiento de seis horas, en PEN.",
 "precio_dia_completo":"Tarifa actual de alojamiento de veinticuatro horas, en PEN.",
 "numero":"Número visible de habitación, entre 1 y 50 dentro de su hotel.",
 "seccion_tarifaria_id":"Sección de tarifas asignada a la habitación.",
 "tipo_servicio_preferido":"Selección guardada: SEIS_HORAS o DIA_COMPLETO.",
 "estado":"Situación operativa validada mediante CHECK.",
 "descripcion":"Texto explicativo mostrado para el área común.",
 "nombre_completo":"Nombres y apellidos del huésped; no es su identificador.",
 "tipo_documento":"DNI, CARNET_EXTRANJERIA o PASAPORTE; valores canónicos en mayúsculas.",
 "numero_documento":"Documento como texto para conservar ceros iniciales; único junto con su tipo.",
 "habitacion_id":"Habitación de la estancia; NULL cuando se utiliza un área.",
 "area_comun_id":"Área visitada; NULL cuando se utiliza una habitación.",
 "origen":"Distingue datos nuevos de históricos importados.",
 "check_in":"Fecha y hora de entrada del hecho operativo.",
 "check_out":"Fecha y hora de salida prevista o confirmada.",
 "tipo_servicio":"Duración de alojamiento contratada; no se aplica a áreas.",
 "tarifa_aplicada":"Precio de alojamiento registrado para esa estancia, independiente del catálogo actual.",
 "horas_adicionales":"Horas acumuladas de ampliación, entre 0 y 65535.",
 "monto_adicional":"Importe acumulado de ampliaciones, en PEN.",
 "color_historico":"Color registrado para identificar históricamente la ocupación.",
 "habitacion_activa_id":"Identificador derivado de la habitación cuando la estancia está activa.",
 "estancia_id":"Estancia concreta relacionada con participantes, pago o pedido.",
 "huesped_id":"Identidad documental de una persona de la estancia.",
 "orden":"Posición del huésped, entre 1 y 8; no se repite en la estancia.",
 "pagado_en":"Fecha de confirmación del pago.",
 "monto":"Importe consolidado confirmado del alojamiento, mayor que cero.",
 "catalogo_id":"Catálogo del hotel; delimita códigos de categorías y productos.",
 "categoria_codigo":"Categoría del mismo catálogo al que pertenece el producto.",
 "precio":"Precio unitario vigente del producto, entre 0.01 y 10000 PEN.",
 "observaciones":"Indicaciones del pedido; cadena vacía si no se proporcionan.",
 "pedido_id":"Cabecera del pedido al que pertenece la línea.",
 "producto_catalogo_id":"Catálogo del producto vigente; puede quedar NULL al retirarlo.",
 "producto_codigo_fk":"Código del producto vigente; puede quedar NULL al retirarlo.",
 "producto_codigo_snapshot":"Código registrado al solicitar el producto; se conserva como historial.",
 "producto_nombre_snapshot":"Nombre registrado al solicitar el producto.",
 "precio_unitario_snapshot":"Precio unitario aplicado al pedido, aunque cambie el precio actual.",
 "cantidad":"Unidades solicitadas, entre 1 y 10 por línea.",
 "creado_en":"Fecha de creación del registro.",
 "actualizado_en":"Fecha del último cambio del registro.",
 "habitaciones.estado":"DISPONIBLE, OCUPADA, LIMPIEZA_PENDIENTE, EN_LIMPIEZA o MANTENIMIENTO.",
 "estancias.estado":"ACTIVA o FINALIZADA.",
 "pedidos_servicio.estado":"PENDIENTE, EN_PREPARACION, ENTREGADO o CANCELADO.",
}

def generar():
    texto="""# Modelo activo: SQL Server, 14 tablas

Este documento corresponde al backend Spring Boot/JDBC/HikariCP y al instalador CREAR_GESTERA_SQLSERVER.sql. Se genera desde esquema/01_tablas.sql con herramientas/documentar-modelo-sqlserver.py.

## Correspondencia con la referencia visual

Hotel, habitación, huésped, área, servicio y pago mantienen entidades propias. La entrada, la salida y la ocupación pertenecen a estancias; el seguimiento de limpieza utiliza el estado de la habitación. Se modelan también tarifas, credenciales y catálogo porque forman parte del funcionamiento real de Gestera.

La relación N:M entre huéspedes y estancias se resuelve con estancia_huespedes. Categorías y productos usan claves compuestas por catálogo. Los snapshots conservan hechos históricos aunque cambie el catálogo.

"""
    tablas=list(re.finditer(r"(?ms)^CREATE TABLE dbo\.(\w+) \(\n(.*?)^\);",SQL))
    total=0
    for tabla in tablas:
        nombre,cuerpo=tabla.groups()
        pks=set()
        fks=set()
        for resultado in re.finditer(r"PRIMARY KEY\s*\(([^)]+)\)",cuerpo):
            pks.update(x.strip() for x in resultado.group(1).split(","))
        for resultado in re.finditer(r"FOREIGN KEY\s*\(([^)]+)\)",cuerpo):
            fks.update(x.strip() for x in resultado.group(1).split(","))
        texto+=f"## {nombre}\n\n{DESCRIPCIONES[nombre]}\n\n| Atributo | Significado | Tipo | Clave y reglas |\n|---|---|---|---|\n"
        for linea in cuerpo.splitlines():
            resultado=re.match(r"\s+([a-z_]+)\s+((?:NVARCHAR|VARCHAR|VARBINARY|CHAR|BIGINT|SMALLINT|TINYINT|INT|BIT|DECIMAL|DATETIME2)(?:\([^)]*\))?|AS\b.*)",linea)
            if not resultado:
                continue
            columna,tipo=resultado.groups()
            total+=1
            resto=linea[resultado.end():].strip().rstrip(",")
            if tipo.startswith("AS"):
                tipo="BIGINT calculado";resto="Generado y persistido; no se escribe desde Java."
            else:
                resto=re.sub(r"CONSTRAINT\s+\w+\s+","",resto)
                resto=re.sub(r"COLLATE\s+\w+\s*","",resto)
            claves=[]
            if columna in pks or "PRIMARY KEY" in resto: claves.append("PK")
            if columna in fks: claves.append("FK")
            adicional=EXTRAS.get(nombre+"."+columna,EXTRAS.get(columna,""))
            reglas="; ".join(x for x in ["/".join(claves),resto,adicional] if x)
            significado=SIGNIFICADOS.get(nombre+"."+columna,SIGNIFICADOS[columna])
            texto+=f"| {columna} | {significado} | {tipo} | {reglas} |\n"
        referencias=re.findall(r"FOREIGN KEY\s*\(([^)]+)\)\s*REFERENCES dbo\.(\w+)\(([^)]+)\)",cuerpo)
        if referencias:
            texto+="\nRelaciones: "+"; ".join(f"({a}) → {b}({c})" for a,b,c in referencias)+".\n"
        texto+="\n"
    texto+="""## Reglas que cruzan tablas

- Una estancia pertenece exactamente a una habitación o a un área común.
- Los índices únicos filtrados permiten muchas visitas a áreas distintas en la misma hora, pero una sola estancia activa por habitación.
- Una habitación usa una sección de su propio hotel.
- Un producto usa una categoría de su catálogo.
- Los pedidos nuevos requieren habitación ocupada y estancia activa.
- Los pagos de alojamiento solo corresponden a estancias de habitación.
- Los ítems conservan nombre, código y precio históricos; eliminar un producto deja su referencia vigente en NULL.
- El origen de una estancia con pagos/pedidos queda protegido frente a traslados.
- Los cambios de habitación, estancia, personas y pago se confirman en una sola transacción Spring.
- La clave del hotel se verifica con PBKDF2 y su versión se conserva al reiniciar. Las sesiones HTTP siguen siendo efímeras.

## Normalización

1FN: valores atómicos y productos/personas en filas separadas. 2FN: las claves compuestas de categorías, productos y asociaciones determinan sus atributos completos. 3FN: hotel, sección, recurso, persona, catálogo y hechos operativos tienen responsabilidades separadas.

Tarifa, color y snapshots de pedido describen el hecho histórico, no el valor actual del catálogo. Su conservación es deliberada. Los totales del alojamiento y del pedido se calculan a partir de sus componentes.

## Migración desde siete tablas

El instalador reconoce V1 y V2. La migración conserva identidades, asociaciones, importes y snapshots en una transacción. Los datos históricos que no contenían tarifa/servicio se marcan IMPORTADA y mantienen NULL en esos campos; no se inventa su desglose.

Se rechaza migrar una ocupación activa V1 sin sus datos tarifarios, revirtiendo la operación. Antes de aplicar una migración real se detiene la aplicación y se verifica un respaldo COPY_ONLY.

La fuente física completa y todas las restricciones están en base-datos/sqlserver/esquema. Los archivos de siete tablas se conservan como históricos.
"""
    texto+="\n## Diagrama entidad-relación vigente\n\n" + "```mermaid\nerDiagram\n"
    texto+="""    hoteles ||--|| credenciales_hotel : autentica
    hoteles ||--|{ secciones_tarifarias : define
    hoteles ||--|{ habitaciones : dispone
    secciones_tarifarias ||--o{ habitaciones : clasifica
    hoteles ||--|{ areas_comunes : ofrece
    habitaciones o|--o{ estancias : aloja
    areas_comunes o|--o{ estancias : recibe
    estancias ||--|{ estancia_huespedes : registra
    huespedes ||--o{ estancia_huespedes : participa
    estancias ||--o| pagos_estancia : cobra
    hoteles ||--|| catalogos_servicio : administra
    catalogos_servicio ||--|{ categorias_servicio : agrupa
    categorias_servicio ||--o{ productos_servicio : contiene
    estancias ||--o{ pedidos_servicio : solicita
    pedidos_servicio ||--|{ pedido_servicio_items : detalla
    productos_servicio o|--o{ pedido_servicio_items : referencia
""" + "```\n\nLas líneas de habitación y área representan alternativas excluyentes: cada estancia tiene exactamente una de ellas. Las cardinalidades mínimas operativas se validan también en Java; una transacción SQL puede insertar primero la cabecera.\n"
    texto+=f"\nTotal: **{len(tablas)} tablas y {total} columnas**, incluidas las calculadas.\n"
    return texto

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verificar',action='store_true')
    argumentos=parser.parse_args()
    contenido=generar()
    if argumentos.verificar:
        if not DEST.exists() or DEST.read_text(encoding='utf-8')!=contenido:
            raise SystemExit('El diccionario no coincide con el esquema; regenerarlo.')
        print('Diccionario sincronizado.')
    else:
        DEST.write_text(contenido,encoding='utf-8')
        print(DEST)
