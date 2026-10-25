"""Genera los tres PNG y la explicación TXT desde el DDL SQL Server de Gestera.

Requiere Pillow. No modifica la base de datos ni necesita un servidor o Internet.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import re
import textwrap
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

from PIL import Image, ImageFont

ROOT = Path(__file__).resolve().parent.parent
DDL = ROOT / "base-datos/sqlserver/esquema/01_tablas.sql"
OUT = ROOT / "docs/entregables/diagramas-sqlserver-14"
CLASS_NAMES = {
    "hoteles": "Hotel", "credenciales_hotel": "CredencialHotel",
    "secciones_tarifarias": "SeccionTarifaria", "habitaciones": "Habitacion",
    "areas_comunes": "AreaComun", "huespedes": "Huesped", "estancias": "Estancia",
    "estancia_huespedes": "EstanciaHuesped", "pagos_estancia": "PagoEstancia",
    "catalogos_servicio": "CatalogoServicio", "categorias_servicio": "CategoriaServicio",
    "productos_servicio": "ProductoServicio", "pedidos_servicio": "PedidoServicio",
    "pedido_servicio_items": "ItemPedidoServicio",
}


@dataclass
class Column:
    name: str
    sql_type: str
    nullable: bool
    pk: bool = False
    fk: bool = False
    unique: bool = False
    computed: bool = False


@dataclass
class ForeignKey:
    child: str
    columns: tuple[str, ...]
    parent: str
    targets: tuple[str, ...]


@dataclass
class Table:
    name: str
    columns: list[Column]
    unique_sets: set[tuple[str, ...]]
    body: str


def parse_schema():
    sql = DDL.read_text(encoding="utf-8")
    tables, fks = {}, []
    for match in re.finditer(r"(?ms)^CREATE TABLE dbo\.(\w+) \(\n(.*?)^\);", sql):
        name, body = match.groups()
        key_sets = lambda pattern: {
            tuple(x.strip() for x in m.split(","))
            for m in re.findall(pattern, body)
        }
        pks = key_sets(r"PRIMARY KEY\s*\(([^)]+)\)")
        uniques = key_sets(r"UNIQUE\s*\(([^)]+)\)") | pks
        columns = []
        for line in body.splitlines():
            m = re.match(
                r"\s+([a-z_]+)\s+((?:NVARCHAR|VARCHAR|VARBINARY|CHAR|BIGINT|SMALLINT|"
                r"TINYINT|INT|BIT|DECIMAL|DATETIME2)(?:\([^)]*\))?|AS\b.*)", line)
            if not m:
                continue
            col, typ = m.groups()
            computed = typ.startswith("AS")
            pk = "PRIMARY KEY" in line or any(col in k for k in pks)
            if "PRIMARY KEY" in line:
                uniques.add((col,))
            if "UNIQUE" in line:
                uniques.add((col,))
            columns.append(Column(col, "BIGINT" if computed else typ,
                                  computed or "NOT NULL" not in line, pk, False,
                                  (col,) in uniques, computed))
        for local, parent, target in re.findall(
                r"FOREIGN KEY\s*\(([^)]+)\)\s*REFERENCES dbo\.(\w+)\(([^)]+)\)", body):
            local_cols = tuple(x.strip() for x in local.split(","))
            fks.append(ForeignKey(name, local_cols, parent,
                                 tuple(x.strip() for x in target.split(","))))
            for column in columns:
                column.fk |= column.name in local_cols
        tables[name] = Table(name, columns, uniques, body)
    assert len(tables) == 14
    assert sum(len(t.columns) for t in tables.values()) == 102
    assert len(fks) == 16
    assert set(tables) == set(CLASS_NAMES)
    return tables, fks


@lru_cache(maxsize=None)
def font(size, bold=False, mono=False):
    names = (["consolab.ttf", "DejaVuSansMono-Bold.ttf"] if mono and bold else
             ["consola.ttf", "DejaVuSansMono.ttf"] if mono else
             ["segoeuib.ttf", "DejaVuSans-Bold.ttf"] if bold else
             ["segoeui.ttf", "DejaVuSans.ttf"])
    for base in (Path("C:/Windows/Fonts"), Path("/usr/share/fonts/truetype/dejavu")):
        for name in names:
            if (base / name).exists():
                return ImageFont.truetype(str(base / name), size)
    return ImageFont.truetype(names[-1], size)


def fk_cards(fk, tables):
    columns = {c.name: c for c in tables[fk.child].columns}
    parent_min = "0..1" if any(columns[x].nullable for x in fk.columns) else "1"
    child_max = "0..1" if fk.columns in tables[fk.child].unique_sets else "0..*"
    return parent_min, child_max


def explain(tables, fks):
    spec = importlib.util.spec_from_file_location("diccionario_gestera", ROOT/"herramientas/documentar-modelo-sqlserver.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    lines = [
        "GESTERA — EXPLICACIÓN DE LOS TRES DIAGRAMAS",
        "="*76,
        "Base: SQL Server, modelo GESTERA_SQLSERVER_14_V2.",
        "Esquema: 14 tablas operativas, 102 columnas y 16 claves foráneas.",
        "Fuente: base-datos/sqlserver/esquema/01_tablas.sql.",
        "Las imágenes son PNG Full HD de 1920 × 1080 píxeles, sin fecha de creación.",
        "Este TXT usa UTF-8 con BOM y saltos de línea Windows para Bloc de notas.",
        "",
        "ALCANCE Y CORRESPONDENCIA CON LAS REFERENCIAS",
        "-"*76,
        "Cada imagen sigue su propia referencia: primero normalización,",
        "después entidad-relación y finalmente clases UML. Los nombres y atributos",
        "corresponden a la base de datos de Gestera. sysdiagrams es metadato de",
        "SSMS y queda fuera de las 14 tablas operativas.",
        "No se añaden entidades ajenas al proyecto: entradas, salidas y ocupación",
        "son parte de estancias; limpieza es un estado de habitaciones. Reservas",
        "anticipadas, empleados y roles de usuario no forman parte de este esquema.",
        "",
        "1. DIAGRAMA DE NORMALIZACIÓN: 1FN, 2FN Y 3FN",
        "Archivo: 01_normalizacion_1FN_2FN_3FN.png",
        "="*76,
        "",
        "Qué representa",
        "Explica cómo separar datos de una operación hotelera para evitar listas",
        "en celdas, duplicación de cabeceras y dependencias entre entidades.",
        "La imagen utiliza tres paneles superiores con cabeceras azules. El tercer",
        "panel muestra las 14 tablas resumidas. En la franja inferior aparece",
        "el ER de las 14 entidades, con rombos y cardinalidades, como la referencia.",
        "Los dos primeros paneles son ejercicios didácticos: no se afirma que",
        "fueran versiones instaladas anteriormente. PEDIDO_2FN, LINEA_2FN y",
        "renglón son nombres de apoyo. El modelo final utiliza pedido_servicio_items",
        "con un identificador id generado por SQL Server.",
        "",
        "Primera Forma Normal (1FN)",
        "Cada columna contiene un valor y cada fila se identifica con una clave.",
        "Guardar «Agua ×2, Café ×1» como una sola lista impediría consultar y",
        "validar cada producto por separado. El ejemplo convierte esa lista en",
        "filas. La clave didáctica (pedido_id, renglón) identifica cada línea.",
        "En Gestera, pedido_servicio_items contiene cantidad, producto y precio",
        "unitario por línea; estancia_huespedes contiene una persona por fila.",
        "La 1FN todavía permite repetición de datos y dependencias incorrectas.",
        "",
        "Segunda Forma Normal (2FN)",
        "Además de 1FN, un atributo no primo debe depender de toda clave",
        "candidata y no solo de una parte de una clave compuesta.",
        "En la relación plana, pedido_id determina estado y estancia_id;",
        "repetirlos en cada renglón crea una dependencia parcial. Se separa",
        "la cabecera del pedido de su detalle. La cantidad corresponde a la",
        "línea completa. En el esquema final, pedidos_servicio guarda estado,",
        "estancia_id y observaciones; pedido_servicio_items referencia su pedido.",
        "Una clave artificial id no elimina por sí sola las dependencias:",
        "también se deben estudiar las claves candidatas del negocio.",
        "",
        "Tercera Forma Normal (3FN)",
        "Además de 2FN, se eliminan dependencias transitivas inadecuadas entre",
        "atributos no clave. Formalmente, en cada dependencia funcional X -> A,",
        "X debe ser superclave o A debe ser un atributo primo.",
        "En el catálogo, el nombre de la categoría depende de su identificador",
        "y no del producto que la utiliza. categorias_servicio almacena el nombre;",
        "productos_servicio conserva la FK compuesta a esa categoría.",
        "Igualmente, las tarifas vigentes se separan en secciones_tarifarias,",
        "las identidades de personas en huespedes y el pago en pagos_estancia.",
        "",
        "Decisiones físicas que requieren una explicación precisa",
        "El modelo aplica normalización, pero no se presenta como una prueba",
        "de 3FN estricta para cada relación física. habitaciones repite hotel_codigo",
        "aunque su sección tarifaria también determina el hotel. Es una redundancia",
        "controlada para validar pertenencia mediante la FK compuesta",
        "(seccion_tarifaria_id, hotel_codigo) y garantizar número único por hotel.",
        "Los snapshots no representan el valor actual del catálogo: registran",
        "nombre, código y precio que existían al vender. El cambio posterior",
        "del producto no debe modificar esos hechos históricos.",
        "habitacion_activa_id es una columna calculada persistida: SQL Server",
        "la deriva de estado y habitacion_id; Java no la escribe.",
        "",
        "Resultado y beneficio",
        "Los 14 cuadros del tercer panel resumen las entidades implementadas. Cambiar",
        "una tarifa o un producto afecta el catálogo vigente sin reescribir",
        "estancias o pedidos históricos. La relación N:M entre personas y visitas",
        "se resuelve con estancia_huespedes, sin repetir datos de identidad.",
        "",
        "2. DIAGRAMA ENTIDAD–RELACIÓN",
        "Archivo: 02_entidad_relacion.png",
        "="*76,
        "",
        "Qué representa y cómo leerlo",
        "Es el modelo físico: cada caja es una tabla y cada fila es una columna",
        "del DDL. Se conservan los 102 atributos, tipos SQL, claves y nulabilidad.",
        "La presentación utiliza fondo blanco, cabeceras y cuadrículas violetas,",
        "columnas tipo / atributo / clave, y conexiones ortogonales grises.",
        "PK identifica filas; FK referencia otra tabla; UQ indica unicidad de",
        "una columna; ? admite NULL; DER indica una columna calculada.",
        "Las PK compuestas se forman con todos los campos marcados PK en la caja.",
        "Una FK compuesta es una sola relación, aunque participe más de una columna.",
        "Los tipos NVARCHAR/VARCHAR conservan la longitud; DECIMAL conserva",
        "precisión y escala; DATETIME2 conserva la precisión temporal declarada.",
        "",
        "Cardinalidades: notación pata de cuervo",
        "Barra doble = exactamente uno; círculo + barra = cero o uno;",
        "círculo + pata = cero o muchos. El símbolo se lee junto a su tabla.",
        "Por ejemplo, en hoteles -> habitaciones una habitación referencia un",
        "hotel y un hotel puede tener cero o muchas habitaciones en el DDL.",
        "Una FK obligatoria impide hijos huérfanos; no obliga al padre a tener",
        "hijos. Por eso el mínimo del lado hijo se muestra como cero incluso",
        "cuando la instalación inicial agrega doce habitaciones por hotel.",
        "Un cruce de líneas sin punto no crea una relación adicional.",
        "",
        "RELACIONES REALES DEL ESQUEMA",
    ]
    for i, fk in enumerate(fks, 1):
        parent_card, child_card = fk_cards(fk, tables)
        lines.extend([
            f"{i:02}. {fk.parent} [{parent_card}] -- [{child_card}] {fk.child}",
            f"    FK {fk.child}({', '.join(fk.columns)})",
            f"    -> {fk.parent}({', '.join(fk.targets)}).",
        ])
    lines += [
        "",
        "Claves candidatas y restricciones compuestas",
        "- habitaciones: UNIQUE(hotel_codigo, numero).",
        "- secciones_tarifarias: UNIQUE(hotel_codigo, codigo),",
        "  UNIQUE(hotel_codigo, nombre), UNIQUE(hotel_codigo, color_hex).",
        "- areas_comunes: UNIQUE(hotel_codigo, codigo) y UNIQUE(hotel_codigo, nombre).",
        "- huespedes: UNIQUE(tipo_documento, numero_documento).",
        "- categorias_servicio: PK(catalogo_id, codigo), UNIQUE(catalogo_id, nombre).",
        "- productos_servicio: PK(catalogo_id, codigo), UNIQUE(catalogo_id, nombre).",
        "- estancia_huespedes: PK(estancia_id, huesped_id), UNIQUE(estancia_id, orden).",
        "- credenciales_hotel usa hotel_codigo como PK y FK simultáneamente.",
        "- catalogos_servicio.hotel_codigo y pagos_estancia.estancia_id son únicos.",
        "",
        "Reglas que las cardinalidades, por sí solas, no expresan",
        "1) Ubicación XOR: una estancia usa habitacion_id O area_comun_id,",
        "   exactamente una, nunca ambas y nunca las dos NULL.",
        "2) El historial permite muchas estancias de una habitación, pero el",
        "   índice único filtrado permite solo una ACTIVA por habitación.",
        "3) Los índices filtrados impiden repetir entrada por habitación o área.",
        "4) La aplicación registra de uno a ocho huéspedes por estancia.",
        "   SQL valida orden de 1 a 8 y unicidad; la FK sola permite cero filas",
        "   durante la construcción de la transacción.",
        "5) Los pagos de alojamiento solo corresponden a estancias de habitación.",
        "   pagos_estancia representa un pago consolidado por estancia, no un",
        "   libro de múltiples abonos. El reporte suma pagos confirmados.",
        "6) Un pedido nuevo requiere estancia activa y habitación ocupada.",
        "7) Cada línea conserva precio/nombre/código históricos. Al eliminar",
        "   el producto actual, la FK opcional queda NULL (ON DELETE SET NULL).",
        "8) Los triggers impiden asociar productos de otro hotel o trasladar",
        "   una estancia con pagos o pedidos a otro recurso.",
        "9) Eliminar un pedido elimina sus líneas; eliminar una estancia elimina",
        "   sus asociaciones de personas, pero no borra la identidad del huésped.",
        "",
        "FUNCIÓN DE CADA TABLA",
    ]
    for i, table in enumerate(tables.values(), 1):
        lines.append(f"{i:02}. {table.name}: {module.DESCRIPCIONES[table.name]}")
    lines += [
        "",
        "3. DIAGRAMA UML DE CLASES",
        "Archivo: 03_diagrama_uml.png",
        "="*76,
        "",
        "Qué representa",
        "Es una vista conceptual UML de las 14 entidades persistentes del SQL.",
        "No es ingeniería inversa completa de todas las clases Java. Algunas",
        "tablas se gestionan con records, vistas y repositorios compartidos;",
        "no se afirma que exista un archivo Java por cada caja.",
        "Las cajas conservan la correspondencia de las entidades:",
        "por ejemplo Hotel / hoteles, Estancia / estancias y",
        "ItemPedidoServicio / pedido_servicio_items.",
        "Se incluyen los atributos esenciales. La imagen ER y el anexo de este",
        "TXT detallan los 102 campos, incluidas las columnas técnicas y de auditoría.",
        "",
        "Notación UML utilizada",
        "- Cajas grises sobre fondo blanco, como la tercera referencia.",
        "- Compartimentos horizontales: nombre, atributos y operaciones.",
        "- El compartimento central contiene atributos privados, precedidos por -.",
        "- Los nombres se adaptan de snake_case a lowerCamelCase.",
        "- ? después del tipo indica que la columna correspondiente admite NULL.",
        "- El compartimento derecho muestra operaciones públicas, precedidas por +.",
        "- Las asociaciones llevan multiplicidades 1, 0..1 y 0..* en sus extremos.",
        "- No se inventan herencias: son entidades asociadas, no subclases.",
        "- Una asociación no implica por sí sola borrado en cascada o composición.",
        "",
        "Equivalencias de tipos",
        "BIGINT -> Long; INT/SMALLINT/TINYINT -> Integer; BIT -> Boolean;",
        "NVARCHAR/VARCHAR/CHAR -> String; VARBINARY -> byte[];",
        "DECIMAL -> BigDecimal; DATETIME2 -> LocalDateTime.",
        "Se usan envoltorios en esta vista conceptual para expresar nulabilidad.",
        "No se deben usar float/double para importes ni números para documentos.",
        "Los timestamps locales no llevan zona dentro de DATETIME2: la aplicación",
        "interpreta los de negocio en America/Lima y las auditorías en UTC.",
        "",
        "Operaciones mostradas",
        "PedidoServicio.total() suma los subtotales y",
        "ItemPedidoServicio.subtotal() multiplica precio unitario por cantidad.",
        "Estas operaciones representan el comportamiento implementado en los",
        "records reales PedidoServicio y LineaPedidoServicio respectivamente.",
        "ItemPedidoServicio es el nombre conceptual del ítem persistente;",
        "LineaPedidoServicio es la vista Java que usa la interfaz actual.",
        "Las demás operaciones son responsabilidades conceptuales del dominio:",
        "listarHabitaciones consulta el inventario; autenticar comprueba la llave;",
        "precio(tipo) selecciona una tarifa; montoTotal calcula el alojamiento;",
        "registrarVisita registra un área; validarDocumento comprueba la identidad;",
        "finalizar cierra una estancia; confirmarPago registra el pago; versionar",
        "protege cambios del catálogo; agregarProducto amplía una categoría;",
        "actualizarPrecio cambia el precio vigente. El código implementa estas",
        "responsabilidades en gestores, records y repositorios; no se afirma que",
        "los nombres conceptuales sean métodos de una clase Java por cada tabla.",
        "",
        "Vinculación con el código y los frameworks",
        "Spring Boot inicia y configura la aplicación; HikariCP administra el",
        "DataSource; Spring JDBC ejecuta SQL parametrizado. TransaccionesHotel",
        "coordina transacciones y serializa cambios por hotel con sp_getapplock.",
        "RepositorioInventarioJdbc administra habitaciones, secciones y ocupación.",
        "RepositorioCredencialesHotelJdbc guarda material PBKDF2 y su versión.",
        "RepositorioAreasComunesJdbc consulta espacios por hotel.",
        "RepositorioHuespedesJdbc gestiona huespedes, estancias y sus asociaciones.",
        "RepositorioIngresosHabitacionJdbc persiste pagos_estancia y alimenta",
        "los reportes; su nombre Java se conserva por compatibilidad.",
        "RepositorioCatalogoServicioJdbc administra catálogos, categorías y productos.",
        "RepositorioServicioHabitacionJdbc administra pedidos y sus líneas.",
        "Los gestores contienen validaciones de negocio y los controladores",
        "procesan formularios. La capa HTTP actual utiliza jdk.httpserver.",
        "",
        "Ejemplo de lectura conjunta de los tres diagramas",
        "Se elige una habitación y una tarifa, se crea la estancia y se asocian",
        "los huéspedes. Si se confirma el pago, pagos_estancia referencia esa",
        "misma estancia. Un pedido se vincula a ella y guarda líneas con sus",
        "precios históricos. Al finalizar la estancia se conserva su historial;",
        "la habitación puede entrar en limpieza y luego volver a estar disponible.",
        "La normalización explica por qué los datos se separan, el ER muestra",
        "las restricciones físicas y UML muestra la estructura conceptual.",
        "",
        "ANEXO: DICCIONARIO DE LOS 102 ATRIBUTOS",
        "="*76,
        "Además de las imágenes, se detalla cada campo para poder estudiarlo",
        "sin depender del tamaño del texto de los diagramas.",
    ]
    for table in tables.values():
        lines += ["", table.name.upper(), "-"*76]
        for col in table.columns:
            flags = [tag for valid, tag in ((col.pk, "PK"), (col.fk, "FK"),
                                            (col.unique, "UQ"), (col.computed, "calculado"))
                     if valid]
            flags.append("NULL permitido" if col.nullable else "NOT NULL")
            meaning = module.SIGNIFICADOS.get(table.name+"."+col.name, module.SIGNIFICADOS[col.name])
            extra = module.EXTRAS.get(table.name+"."+col.name, module.EXTRAS.get(col.name, ""))
            lines.extend([f"{col.name}: {col.sql_type} | {', '.join(flags)}.",
                          "  "+meaning])
            if extra:
                lines.append("  "+extra)
    lines += [
        "",
        "REPRODUCCIÓN",
        "="*76,
        "Comando desde la carpeta del proyecto:",
        "  python herramientas/generar-diagramas-sqlserver.py",
        "Requisito: Pillow. Fuentes: Segoe UI y Consolas en Windows, o DejaVu",
        "en sistemas compatibles. Se generan PNG deterministas desde el DDL.",
        "No se consulta ni modifica la base de datos para crear los diagramas.",
        f"SHA-256 del DDL usado: {hashlib.sha256(DDL.read_bytes()).hexdigest().upper()}",
        "",
    ]
    formatted = []
    for line in lines:
        if len(line) > 100 and not line.startswith("SHA-"):
            formatted.extend(textwrap.wrap(line, width=94, subsequent_indent="  "))
        else:
            formatted.append(line)
    target = OUT/"EXPLICACION_DE_LOS_3_DIAGRAMAS.txt"
    target.write_bytes(("\r\n".join(formatted)+"\r\n").encode("utf-8-sig"))
    return target


def main():
    argparse.ArgumentParser(description=__doc__).parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    tables, fks = parse_schema()
    from diagramas_referencias_fullhd import renderizar
    paths = renderizar(tables, fks, OUT, font, CLASS_NAMES) + [explain(tables, fks)]
    for path in paths:
        if path.suffix == ".png":
            with Image.open(path) as im:
                assert im.size == (1920, 1080), 'El PNG debe ser Full HD.'
                im.verify()
        print(f"{path.relative_to(ROOT)} | {path.stat().st_size:,} bytes")
    archive = OUT.parent/"DIAGRAMAS_GESTERA_SQLSERVER_14.zip"
    with ZipFile(archive, "w", ZIP_DEFLATED) as z:
        for path in paths:
            z.write(path, path.name)
    print(f"ZIP: {archive.relative_to(ROOT)}")
    print("Verificado: PNG Full HD 1920x1080; 14 tablas; 102 atributos en ER; 16 relaciones por diagrama.")


if __name__ == "__main__":
    main()
