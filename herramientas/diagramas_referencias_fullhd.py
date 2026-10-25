"""Dibujo independiente de cada referencia, a 1920 × 1080 píxeles."""
from __future__ import annotations

import heapq
import math
from collections import defaultdict
from PIL import Image, ImageDraw

SIZE = (1920, 1080)
WHITE, INK = "#FFFFFF", "#303039"
PURPLE, LAVENDER, GREY = "#A78BFA", "#F0EAFE", "#92949B"
BLUE, LIGHTBLUE = "#1277D0", "#D9EAFF"


class Sheet:
    def __init__(self, font):
        self.im = Image.new("RGB", SIZE, WHITE)
        self.d = ImageDraw.Draw(self.im)
        self.font = font
        self.labels = []

    def text(self, x, y, text, size=11, fill=INK, bold=False, mono=False, anchor="lt"):
        self.d.text((x, y), text, font=self.font(size, bold, mono), fill=fill, anchor=anchor)

    def fit(self, x, y, text, width, size=11, **kwargs):
        while self.d.textlength(text, font=self.font(size, kwargs.get("bold", False),
                                                    kwargs.get("mono", False))) > width and size > 8:
            size -= 1
        assert self.d.textlength(text, font=self.font(size, kwargs.get("bold", False),
                                                     kwargs.get("mono", False))) <= width, text
        self.text(x, y, text, size, **kwargs)

    def wrap(self, x, y, text, width, size=12, fill=INK, bold=False, spacing=1.35):
        line, yy = "", y
        for word in text.split():
            test = (line + " " + word).strip()
            if self.d.textlength(test, font=self.font(size, bold)) > width and line:
                self.text(x, yy, line, size, fill, bold)
                yy += size*spacing
                line = word
            else:
                line = test
        if line:
            self.text(x, yy, line, size, fill, bold)
            yy += size*spacing
        return yy

    def bar(self, box, title, size=16):
        self.d.rounded_rectangle(box, radius=3, fill=BLUE)
        self.text(box[0]+10, box[1]+6, title, size, WHITE, True)

    def save(self, path):
        assert self.im.size == SIZE
        self.im.save(path, dpi=(96, 96), optimize=True)
        return path


def endpoint(box, side, fraction=.5):
    x, y, w, h = box
    return {"L": (x, y+h*fraction), "R": (x+w, y+h*fraction),
            "T": (x+w*fraction, y), "B": (x+w*fraction, y+h)}[side]


def outside(point, side, dist=20):
    ux, uy = {"L": (-1, 0), "R": (1, 0), "T": (0, -1), "B": (0, 1)}[side]
    return (point[0]+ux*dist, point[1]+uy*dist)


def simplify(points):
    result = []
    for p in points:
        if result and p == result[-1]:
            continue
        if len(result) >= 2 and (
            result[-2][0] == result[-1][0] == p[0] or
            result[-2][1] == result[-1][1] == p[1]):
            result[-1] = p
        else:
            result.append(p)
    return result


def path_between(start, end, blocked, used, bounds, step=5):
    """A* ortogonal con margen de separación de cajas y penalización de cruces."""
    snap = lambda p: (round(p[0]/step), round(p[1]/step))
    a, b = snap(start), snap(end)
    x0, y0, x1, y1 = [round(n/step) for n in bounds]
    queue = [(0, 0, a[0], a[1], 4)]
    best, previous = {(a[0], a[1], 4): 0}, {}
    moves = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    end_state = None
    while queue:
        _, cost, x, y, direction = heapq.heappop(queue)
        state = (x, y, direction)
        if cost > best.get(state, float("inf")):
            continue
        if (x, y) == b:
            end_state = state
            break
        for new_direction, (dx, dy) in enumerate(moves):
            nx, ny = x+dx, y+dy
            if not (x0 <= nx <= x1 and y0 <= ny <= y1):
                continue
            if (nx, ny) in blocked and (nx, ny) not in (a, b):
                continue
            new_cost = cost + 1 + (.7 if direction not in (4, new_direction) else 0)
            new_cost += used.get((nx, ny), 0)*2.5
            ns = (nx, ny, new_direction)
            if new_cost < best.get(ns, float("inf")):
                best[ns], previous[ns] = new_cost, state
                estimate = abs(nx-b[0])+abs(ny-b[1])
                heapq.heappush(queue, (new_cost+estimate, new_cost, nx, ny, new_direction))
    assert end_state is not None, (start, end)
    points = []
    while end_state in previous:
        x, y, _ = end_state
        points.append((x*step, y*step))
        used[(x, y)] += 1
        end_state = previous[end_state]
    points.append((a[0]*step, a[1]*step))
    points.reverse()
    return simplify([start, (points[0][0], start[1]), *points,
                     (points[-1][0], end[1]), end])


def make_routes(boxes, fks, bounds):
    refs, sides = defaultdict(list), {}
    for i, fk in enumerate(fks):
        def center(name):
            x, y, w, h = boxes[name]
            return x+w/2, y+h/2
        px, py = center(fk.parent)
        cx, cy = center(fk.child)
        dx, dy = cx-px, cy-py
        if abs(dx) > abs(dy)*1.2:
            a, b = ("R", "L") if dx > 0 else ("L", "R")
        else:
            a, b = ("B", "T") if dy > 0 else ("T", "B")
        sides[i] = a, b
        refs[(fk.parent, a)].append((i, cy if a in "LR" else cx, "parent"))
        refs[(fk.child, b)].append((i, py if b in "LR" else px, "child"))
    slots = {}
    for (name, side), values in refs.items():
        for j, (idx, _, role) in enumerate(sorted(values, key=lambda t: t[1])):
            slots[(idx, role)] = .15+.7*(j+1)/(len(values)+1)
    blocked, used = set(), defaultdict(int)
    for x, y, w, h in boxes.values():
        for xx in range(math.floor((x-8)/5), math.ceil((x+w+8)/5)+1):
            for yy in range(math.floor((y-8)/5), math.ceil((y+h+8)/5)+1):
                blocked.add((xx, yy))
    result = []
    for i, fk in enumerate(fks):
        a, b = sides[i]
        start = endpoint(boxes[fk.parent], a, slots[(i, "parent")])
        end = endpoint(boxes[fk.child], b, slots[(i, "child")])
        sp, ep = outside(start, a), outside(end, b)
        middle = path_between(sp, ep, blocked, used, bounds)
        result.append((fk, simplify([start, sp, *middle, ep, end])))
    assert len(result) == 16
    # Comprueba las rutas reales, incluidos los puertos, contra todas las cajas ajenas.
    for fk, points in result:
        for name, (x, y, w, h) in boxes.items():
            if name in (fk.parent, fk.child):
                continue
            for p, q in zip(points, points[1:]):
                if p[0] == q[0]:
                    assert not (x < p[0] < x+w and max(min(p[1], q[1]), y) < min(max(p[1], q[1]), y+h)), (fk, name)
                elif p[1] == q[1]:
                    assert not (y < p[1] < y+h and max(min(p[0], q[0]), x) < min(max(p[0], q[0]), x+w)), (fk, name)
                else:
                    raise AssertionError("Segmento no ortogonal")
    return result


VERBS = {
    ("hoteles", "credenciales_hotel"): "autentica",
    ("hoteles", "secciones_tarifarias"): "define",
    ("hoteles", "habitaciones"): "tiene",
    ("hoteles", "areas_comunes"): "ofrece",
    ("hoteles", "catalogos_servicio"): "administra",
    ("secciones_tarifarias", "habitaciones"): "clasifica",
    ("habitaciones", "estancias"): "aloja",
    ("areas_comunes", "estancias"): "recibe",
    ("huespedes", "estancia_huespedes"): "participa",
    ("estancias", "estancia_huespedes"): "incluye",
    ("estancias", "pagos_estancia"): "genera",
    ("catalogos_servicio", "categorias_servicio"): "agrupa",
    ("categorias_servicio", "productos_servicio"): "contiene",
    ("estancias", "pedidos_servicio"): "solicita",
    ("pedidos_servicio", "pedido_servicio_items"): "detalla",
    ("productos_servicio", "pedido_servicio_items"): "referencia",
}


def cards(fk, tables):
    local = {c.name: c for c in tables[fk.child].columns}
    return ("0..1" if any(local[x].nullable for x in fk.columns) else "1",
            "0..1" if fk.columns in tables[fk.child].unique_sets else "0..*")


def marker(s, end, toward, card):
    dx, dy = toward[0]-end[0], toward[1]-end[1]
    length = math.hypot(dx, dy)
    ux, uy = dx/length, dy/length
    def p(distance, offset=0):
        return end[0]+ux*distance-uy*offset, end[1]+uy*distance+ux*offset
    if card.endswith("*"):
        for side in (-5, 0, 5):
            s.d.line((p(2, side), p(12)), fill=GREY, width=1)
    else:
        s.d.line((p(5, -5), p(5, 5)), fill=GREY, width=1)
    if card.startswith("0"):
        px, py = p(17)
        s.d.ellipse((px-3, py-3, px+3, py+3), outline=GREY, fill=WHITE)
    else:
        s.d.line((p(14, -5), p(14, 5)), fill=GREY, width=1)


def intersects(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def edge_caption(s, text, points, boxes, diamond=False):
    tw = s.d.textlength(text, font=s.font(9))
    halfw, halfh = (max(23, tw/2+9), 12) if diamond else (tw/2+3, 7)
    candidates = []
    for a, b in zip(points, points[1:]):
        length = math.dist(a, b)
        for fraction in (.5, .3, .7):
            x, y = a[0]+fraction*(b[0]-a[0]), a[1]+fraction*(b[1]-a[1])
            box = (x-halfw-2, y-halfh-2, x+halfw+2, y+halfh+2)
            hits = sum(intersects(box, (bx, by, bx+bw, by+bh))
                       for bx, by, bw, bh in boxes.values())
            hits += sum(intersects(box, other) for other in s.labels)
            candidates.append((hits*10000-length, x, y, box))
    _, x, y, box = min(candidates)
    if any(intersects(box, (bx, by, bx+bw, by+bh)) for bx, by, bw, bh in boxes.values()):
        return
    s.labels.append(box)
    if diamond:
        s.d.polygon([(x-halfw, y), (x, y-halfh), (x+halfw, y), (x, y+halfh)],
                    fill=WHITE, outline="#727787", width=1)
    else:
        s.d.rectangle((x-halfw, y-halfh, x+halfw, y+halfh), fill=WHITE)
    s.text(x, y, text, 9, "#656871", anchor="mm")


def draw_graph(s, boxes, tables, fks, bounds, mode):
    routed = make_routes(boxes, fks, bounds)
    for fk, points in routed:
        s.d.line(points, fill="#85858E" if mode != "uml" else "#838996", width=1)
        left, right = cards(fk, tables)
        if mode == "er":
            marker(s, points[0], points[1], left)
            marker(s, points[-1], points[-2], right)
        else:
            for end, toward, card in ((points[0], points[1], left),
                                      (points[-1], points[-2], right)):
                dx, dy = toward[0]-end[0], toward[1]-end[1]
                length = math.hypot(dx, dy)
                ux, uy = dx/length, dy/length
                label = card.replace("*", "N") if mode == "chen" else card
                s.text(end[0]+ux*13-uy*9, end[1]+uy*13+ux*9,
                       label, 10, "#53555E", anchor="mm")
        if mode != "uml":
            edge_caption(s, VERBS[(fk.parent, fk.child)], points, boxes, mode == "chen")


def er_boxes(tables):
    coords = {
        "credenciales_hotel": (25, 75), "hoteles": (25, 405), "areas_comunes": (25, 810),
        "secciones_tarifarias": (410, 75), "habitaciones": (410, 405),
        "catalogos_servicio": (410, 810), "huespedes": (795, 75), "estancias": (795, 405),
        "categorias_servicio": (795, 810), "pagos_estancia": (1180, 75),
        "estancia_huespedes": (1180, 405), "productos_servicio": (1180, 810),
        "pedidos_servicio": (1565, 405), "pedido_servicio_items": (1565, 810),
    }
    return {name: (*coords[name], 330, 24+len(t.columns)*17)
            for name, t in tables.items()}


def er_table(s, t, box):
    x, y, w, h = box
    s.d.rectangle((x, y, x+w, y+h), outline=PURPLE, fill=WHITE, width=1)
    s.d.rectangle((x, y, x+w, y+24), outline=PURPLE, fill=LAVENDER, width=1)
    s.fit(x+w/2, y+12, t.name.upper(), w-12, 12, anchor="mm", bold=True)
    for i, col in enumerate(t.columns):
        yy = y+24+i*17
        s.d.line((x, yy, x+w, yy), fill="#C9BAEC", width=1)
        s.d.line((x+87, yy, x+87, yy+17), fill="#C9BAEC", width=1)
        s.d.line((x+291, yy, x+291, yy+17), fill="#C9BAEC", width=1)
        typ = col.sql_type.lower()
        tags = "/".join(k for flag, k in ((col.pk, "PK"), (col.fk, "FK")) if flag)
        if col.computed:
            tags = "DER"
        elif col.unique and not col.pk:
            tags += "/UQ" if tags else "UQ"
        s.fit(x+4, yy+4, typ, 79, 10)
        s.fit(x+91, yy+4, col.name+(" ?" if col.nullable else ""), 197, 11)
        s.fit(x+294, yy+4, tags, 33, 9, fill="#5D4888")


def render_er(tables, fks, out, font):
    s = Sheet(font)
    s.text(25, 18, "DIAGRAMA ENTIDAD–RELACIÓN · GESTERA", 16, "#5D4888", True)
    bs = er_boxes(tables)
    draw_graph(s, bs, tables, fks, (12, 50, 1905, 1020), "er")
    for name, box in bs.items():
        er_table(s, tables[name], box)
    # Leyenda discreta, como el espacio libre de la segunda referencia.
    s.text(1570, 82, "CLAVES Y CARDINALIDAD", 12, "#5D4888", True)
    for i, line in enumerate([
        "PK: primaria · FK: foránea · UQ: único",
        "?: NULL · DER: columna calculada",
        "Barra doble: 1 · círculo y barra: 0..1",
        "Círculo y pata de cuervo: 0..N",
        "Estancia: habitación O área común (XOR)",
        "14 tablas · 102 atributos · 16 relaciones",
    ]):
        s.text(1570, 111+i*20, line, 11, "#60606A")
    s.text(25, 1043, "Una estancia activa por habitación. Las claves compuestas y los datos históricos se explican en el TXT.", 11, "#5C5C65")
    return s.save(out/"02_entidad_relacion.png")


ESSENTIAL = {
    "hoteles": ["codigo", "nombre", "ubicacion", "activo"],
    "credenciales_hotel": ["hotel_codigo", "sal", "hash_clave", "iteraciones", "version", "requiere_cambio"],
    "secciones_tarifarias": ["id", "hotel_codigo", "nombre", "color_hex", "precio_seis_horas", "precio_dia_completo"],
    "habitaciones": ["id", "hotel_codigo", "numero", "seccion_tarifaria_id", "estado", "tipo_servicio_preferido", "activa"],
    "areas_comunes": ["id", "hotel_codigo", "nombre", "descripcion", "activa"],
    "huespedes": ["id", "nombre_completo", "tipo_documento", "numero_documento"],
    "estancias": ["id", "habitacion_id", "area_comun_id", "tipo_servicio", "check_in", "check_out",
                  "tarifa_aplicada", "monto_adicional", "estado"],
    "estancia_huespedes": ["estancia_id", "huesped_id", "orden"],
    "pagos_estancia": ["id", "estancia_id", "pagado_en", "monto"],
    "catalogos_servicio": ["id", "hotel_codigo", "version"],
    "categorias_servicio": ["catalogo_id", "codigo", "nombre"],
    "productos_servicio": ["catalogo_id", "codigo", "categoria_codigo", "nombre", "precio"],
    "pedidos_servicio": ["id", "estancia_id", "estado", "observaciones", "creado_en"],
    "pedido_servicio_items": ["id", "pedido_id", "producto_nombre_snapshot",
                              "precio_unitario_snapshot", "cantidad"],
}
OPERATIONS = {
    "hoteles": ["+ listarHabitaciones()"],
    "credenciales_hotel": ["+ autenticar()"],
    "secciones_tarifarias": ["+ precio(tipo)"],
    "habitaciones": ["+ montoTotal()"],
    "areas_comunes": ["+ registrarVisita()"],
    "huespedes": ["+ validarDocumento()"],
    "estancias": ["+ finalizar()"],
    "estancia_huespedes": [],
    "pagos_estancia": ["+ confirmarPago()"],
    "catalogos_servicio": ["+ versionar()"],
    "categorias_servicio": ["+ agregarProducto()"],
    "productos_servicio": ["+ actualizarPrecio()"],
    "pedidos_servicio": ["+ total()"],
    "pedido_servicio_items": ["+ subtotal()"],
}


def camel(s):
    a = s.split("_")
    return a[0]+"".join(x.title() for x in a[1:])


def jtype(col):
    return {"BIGINT": "Long", "SMALLINT": "Integer", "TINYINT": "Integer",
            "INT": "Integer", "BIT": "Boolean", "DECIMAL": "BigDecimal",
            "DATETIME2": "LocalDateTime", "VARBINARY": "byte[]"}.get(col.sql_type.split("(")[0], "String")


def uml_boxes():
    positions = {
        "huespedes": (490, 75), "estancias": (960, 60), "pagos_estancia": (1430, 75),
        "hoteles": (20, 325), "habitaciones": (490, 325),
        "pedidos_servicio": (960, 325), "pedido_servicio_items": (1430, 325),
        "credenciales_hotel": (20, 570), "secciones_tarifarias": (490, 570),
        "areas_comunes": (960, 570), "catalogos_servicio": (20, 815),
        "categorias_servicio": (490, 815), "productos_servicio": (960, 815),
        "estancia_huespedes": (1430, 815),
    }
    return {name: (*positions[name], 430, max(76, len(attrs)*17+16))
            for name, attrs in ESSENTIAL.items()}


def uml_class(s, t, box, names):
    x, y, w, h = box
    left, right = x+92, x+314
    s.d.rectangle((x, y, x+w, y+h), fill=WHITE, outline="#9296A3", width=1)
    s.d.line((left, y, left, y+h), fill="#9296A3", width=1)
    s.d.line((right, y, right, y+h), fill="#9296A3", width=1)
    # Compartimentos horizontales de nombre, atributos y operaciones, como la referencia.
    words = re_camel_words(names[t.name])
    lengths = max(len(words), 1)
    for i, word in enumerate(words):
        s.fit(x+46, y+h/2+(i-(lengths-1)/2)*16, word, 86, 12, bold=True, anchor="mm")
    cols = {c.name: c for c in t.columns}
    for i, name in enumerate(ESSENTIAL[t.name]):
        col = cols[name]
        value = "- "+camel(name)+": "+jtype(col)
        if col.nullable:
            value += "?"
        s.fit(left+5, y+8+i*17, value, 212, 10)
    for i, line in enumerate(OPERATIONS[t.name]):
        s.fit(right+5, y+h/2+i*16, line, 106, 10, anchor="lm")


def re_camel_words(value):
    import re
    return re.findall(r"[A-Z][a-z]*", value)


def render_uml(tables, fks, out, font, class_names):
    s = Sheet(font)
    s.text(20, 17, "DIAGRAMA UML · MODELO DE ENTIDADES DE GESTERA", 15, "#4B4F5D", True)
    bs = uml_boxes()
    draw_graph(s, bs, tables, fks, (8, 45, 1908, 1005), "uml")
    for name, box in bs.items():
        uml_class(s, tables[name], box, class_names)
    s.text(20, 1030, "Nombre | atributos | operaciones · 1: uno · 0..1: opcional · 0..*: muchos · ?: nullable · Vista conceptual: responsabilidades delegadas a gestores y repositorios.", 11, "#555963")
    s.text(20, 1050, "Los atributos esenciales se muestran en UML; el diagrama entidad–relación contiene los 102 atributos físicos completos.", 11, "#555963")
    return s.save(out/"03_diagrama_uml.png")


def little_table(s, x, y, w, name, rows, color="#AFCEF2", rowh=15):
    h = 20+len(rows)*rowh
    s.d.rectangle((x, y, x+w, y+h), fill=WHITE, outline=color)
    s.d.rectangle((x, y, x+w, y+20), fill=color, outline=color)
    s.fit(x+5, y+4, name, w-10, 10, bold=True)
    for i, line in enumerate(rows):
        yy = y+20+i*rowh
        s.d.line((x, yy, x+w, yy), fill="#DCE4EF")
        s.fit(x+5, yy+3, line, w-10, 10)
    return h


def sample_grid(s, x, y, widths, headers, rows, rowh=25):
    width = sum(widths)
    s.d.rectangle((x, y, x+width, y+rowh), fill="#B6D7FB", outline="#87B9E9")
    for i, row in enumerate([headers, *rows]):
        xx = x
        if i:
            s.d.rectangle((x, y+i*rowh, x+width, y+(i+1)*rowh), fill=WHITE, outline="#C9D9EA")
        for j, cell in enumerate(row):
            if j:
                s.d.line((xx, y+i*rowh, xx, y+(i+1)*rowh), fill="#C9D9EA")
            s.fit(xx+5, y+i*rowh+7, str(cell), widths[j]-10, 11, bold=(i == 0))
            xx += widths[j]


def normalization_chen(s, tables, fks):
    tops = ["hoteles", "secciones_tarifarias", "habitaciones", "estancias",
            "estancia_huespedes", "huespedes", "credenciales_hotel"]
    bottoms = ["areas_comunes", "catalogos_servicio", "categorias_servicio",
               "productos_servicio", "pedido_servicio_items", "pedidos_servicio", "pagos_estancia"]
    bs = {name: (55+i*270, y, 190, 106)
          for names, y in ((tops, 728), (bottoms, 936))
          for i, name in enumerate(names)}
    draw_graph(s, bs, tables, fks, (12, 698, 1905, 1068), "chen")
    colors = ["#CDE8FF", "#DCC8F5", "#BCE8D2", "#FFC5DB", "#DCE8F1", "#FFE3A8", "#C5DBF8"]
    for j, name in enumerate([*tops, *bottoms]):
        selected = [c for c in tables[name].columns if c.pk or c.fk]
        rows = [f"{c.name} ({'/'.join(t for flag,t in ((c.pk,'PK'),(c.fk,'FK')) if flag)})" for c in selected][:3]
        if len(rows) < 4:
            rows += [c.name for c in tables[name].columns if not (c.pk or c.fk)][:4-len(rows)]
        x, y, w, h = bs[name]
        s.d.rounded_rectangle((x, y, x+w, y+h), radius=3, outline=colors[j%7])
        little_table(s, x, y, w, name, rows, colors[j%7], rowh=21)


def render_normalization(tables, fks, out, font):
    s = Sheet(font)
    panels = [(5, 5, 632, 647), (642, 5, 1269, 647), (1279, 5, 1915, 647)]
    for box in panels:
        s.d.rounded_rectangle(box, radius=3, outline="#CFE7F4", fill=WHITE)
    titles = ["1. Primera Forma Normal (1FN)", "2. Segunda Forma Normal (2FN)", "3. Tercera Forma Normal (3FN)"]
    for (x, y, xx, yy), title in zip(panels, titles):
        s.bar((x, y, xx, 35), title, 18)
    bullet_sets = [
        ["Todos los atributos tienen valores atómicos.",
         "No hay listas ni grupos repetidos en una celda.",
         "Cada fila tiene una clave que la identifica.",
         "Los productos de un pedido se separan en filas."],
        ["Se elimina la dependencia parcial.",
         "Cada atributo depende de la clave completa.",
         "La cabecera del pedido se separa de sus líneas.",
         "La cantidad depende de la línea, no solo del pedido."],
        ["Se eliminan dependencias transitivas inadecuadas.",
         "El nombre de categoría se guarda en su propia entidad.",
         "Las FK relacionan personas, estancias, tarifas y pedidos."],
    ]
    for panel, bullets in zip(panels, bullet_sets):
        x = panel[0]+12
        yy = 48
        for bullet in bullets:
            yy = s.wrap(x, yy, "• "+bullet, panel[2]-panel[0]-24, 13, spacing=1.2)+5
    s.text(18, 170, "Ejemplo: líneas de un pedido (1FN)", 14, "#163B99", True)
    sample_grid(s, 18, 198, [72, 78, 230, 70, 150],
                ["Pedido", "Renglón", "Producto", "Cant.", "Estancia"],
                [(101, 1, "agua-mineral", 2, 501),
                 (101, 2, "cafe-americano", 1, 501),
                 (102, 1, "agua-mineral", 1, 502)])
    s.wrap(18, 323, "Antes: «Agua ×2, Café ×1» en una sola celda. La 1FN convierte esa lista en líneas independientes.", 600, 13)
    s.wrap(18, 390, "Clave didáctica: (pedido_id, renglón). Todavía pueden repetirse datos de la cabecera y del catálogo.", 600, 13)
    s.wrap(18, 463, "La 1FN evita listas; aún no elimina dependencias parciales o transitivas.", 600, 13)
    s.wrap(18, 574, "Ejemplos ilustrativos: renglón explica la clave compuesta. El esquema final utiliza id para cada ítem.", 600, 12)
    s.text(655, 170, "Ejemplo: cabecera y detalle separados (2FN)", 14, "#163B99", True)
    little_table(s, 655, 198, 600, "PEDIDO_2FN", ["pedido_id (PK) | estancia_id | estado"], rowh=22)
    sample_grid(s, 655, 244, [150, 150, 300], ["Pedido", "Estancia", "Estado"],
                [(101, 501, "PENDIENTE"), (102, 502, "PENDIENTE")])
    little_table(s, 655, 344, 600, "LINEA_2FN", ["PK (pedido_id, renglón)"], rowh=22)
    sample_grid(s, 655, 390, [105, 100, 300, 95], ["Pedido", "Renglón", "Producto", "Cantidad"],
                [(101, 1, "agua-mineral", 2), (101, 2, "cafe-americano", 1),
                 (102, 1, "agua-mineral", 1)])
    s.wrap(655, 520, "pedido_id determina estancia y estado. Esos atributos se guardan una sola vez en la cabecera.", 600, 13)
    s.wrap(655, 580, "La cantidad pertenece a la línea completa. Categorías y productos se organizan en el paso siguiente.", 600, 12)

    s.text(1292, 142, "Tablas finales del modelo (atributos principales)", 13, "#163B99", True)
    order = ["hoteles", "habitaciones", "huespedes",
             "estancias", "estancia_huespedes", "pagos_estancia",
             "secciones_tarifarias", "areas_comunes", "credenciales_hotel",
             "catalogos_servicio", "categorias_servicio", "productos_servicio",
             "pedidos_servicio", "pedido_servicio_items"]
    colors = ["#C5E3FC", "#DED0F7", "#FFE8B0", "#C9EAD8", "#D8E1FC", "#F5C9E3"]
    for i, name in enumerate(order):
        x, y = 1292+(i%3)*207, 169+(i//3)*84
        keycols = [c for c in tables[name].columns if c.pk or c.fk]
        rows = [c.name+" "+"/".join(t for flag,t in ((c.pk,"PK"),(c.fk,"FK")) if flag)
                for c in keycols][:3]
        if len(rows) < 3:
            rows += [c.name for c in tables[name].columns if not (c.pk or c.fk)][:3-len(rows)]
        little_table(s, x, y, 198, name, rows, colors[i%len(colors)], rowh=16)
    s.wrap(1292, 602, "Se conservan snapshots históricos y redundancias físicas controladas; detalle en el TXT.", 605, 11)
    s.bar((5, 660, 1915, 689), "Diagrama Entidad–Relación del modelo normalizado", 18)
    s.text(1900, 674, "PK: clave primaria   ·   FK: clave foránea   ·   Relación: rombo", 12, WHITE, anchor="rm")
    normalization_chen(s, tables, fks)
    return s.save(out/"01_normalizacion_1FN_2FN_3FN.png")


def renderizar(tables, fks, out, font, class_names):
    return [render_normalization(tables, fks, out, font),
            render_er(tables, fks, out, font),
            render_uml(tables, fks, out, font, class_names)]
