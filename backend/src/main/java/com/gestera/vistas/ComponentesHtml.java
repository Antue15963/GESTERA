package com.gestera.vistas;

import com.gestera.modelo.areas.GestorAreasComunes.AreaComun;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.Habitacion;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.SeccionTarifaria;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.gestera.modelo.huespedes.DatosHuesped;
import com.gestera.modelo.huespedes.RegistroHuesped;
import com.gestera.modelo.huespedes.TipoDocumento;
import com.gestera.modelo.reportes.FiltroReporte;
import com.gestera.modelo.reportes.GestorReportes.DetalleIngreso;
import com.gestera.modelo.reportes.GestorReportes.ResumenHabitacion;
import com.gestera.modelo.reportes.GestorReportes.ResumenPeriodo;
import com.gestera.modelo.servicios.EstadoPedidoServicio;
import com.gestera.modelo.servicios.LineaPedidoServicio;
import com.gestera.modelo.servicios.PedidoServicio;
import com.gestera.modelo.servicios.ProductoServicio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Construye y formatea los fragmentos HTML dinámicos usados por las plantillas. */
final class ComponentesHtml {
    private static final DateTimeFormatter FORMATO_FECHA_DATO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA_VISIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_DIA_VISIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId ZONA_HOTEL = ZoneId.of("America/Lima");

    private ComponentesHtml() {
    }

    static String crearTarjetasHoteles(List<Hotel> hoteles) {
        StringBuilder tarjetas = new StringBuilder(hoteles.size() * 480);
        for (Hotel hotel : hoteles) {
            Objects.requireNonNull(hotel, "hotel");
            tarjetas.append("<button class=\"tarjeta-hotel\" type=\"button\"")
                    .append(" data-hotel-codigo=\"")
                    .append(escaparAtributo(hotel.codigo()))
                    .append("\" data-hotel-nombre=\"")
                    .append(escaparAtributo(hotel.nombre()))
                    .append("\" aria-haspopup=\"dialog\" aria-controls=\"modal-hotel\">")
                    .append("<span class=\"tarjeta-hotel__monograma\" aria-hidden=\"true\">")
                    .append(escaparHtml(hotel.iniciales()))
                    .append("</span><span class=\"tarjeta-hotel__estado\">Registrado</span>")
                    .append("<span class=\"tarjeta-hotel__nombre\">")
                    .append(escaparHtml(hotel.nombre()))
                    .append("</span><span class=\"tarjeta-hotel__ubicacion\">")
                    .append(escaparHtml(hotel.ubicacion()))
                    .append("</span><span class=\"tarjeta-hotel__accion\">Gestionar ")
                    .append("<span aria-hidden=\"true\">&rarr;</span></span></button>");
        }
        return tarjetas.toString();
    }

    static String crearTarjetasHabitaciones(List<Habitacion> habitaciones) {
        return crearTarjetasHabitaciones(habitaciones, Map.of());
    }

    static String crearTarjetasHabitaciones(
            List<Habitacion> habitaciones,
            Map<Integer, List<DatosHuesped>> huespedesPorHabitacion) {
        StringBuilder tarjetas = new StringBuilder(habitaciones.size() * 900);
        for (Habitacion habitacion : habitaciones) {
            Objects.requireNonNull(habitacion, "habitacion");
            String numero = Integer.toString(habitacion.numero());
            String precio = precioCanonico(habitacion);
            String montoAdicional = precioCanonico(habitacion.montoAdicional());
            String montoTotal = precioCanonico(habitacion.montoTotal());
            String checkIn = fechaDato(habitacion.checkIn());
            String checkOut = fechaDato(habitacion.proximoCheckOut());
            String checkOutEpoch = instanteDato(habitacion.proximoCheckOut());
            String huespedes = serializarHuespedes(
                    huespedesPorHabitacion.getOrDefault(habitacion.numero(), List.of()));
            tarjetas.append("<button class=\"tarjeta-habitacion\" type=\"button\"")
                    .append(" data-habitacion-numero=\"").append(numero)
                    .append("\" data-habitacion-precio=\"").append(precio)
                    .append("\" data-habitacion-monto-adicional=\"")
                    .append(montoAdicional)
                    .append("\" data-habitacion-monto-total=\"")
                    .append(montoTotal)
                    .append("\" data-habitacion-horas-adicionales=\"")
                    .append(habitacion.horasAdicionales())
                    .append("\" data-habitacion-color=\"")
                    .append(escaparAtributo(habitacion.seccion().colorHex()))
                    .append("\" data-habitacion-seccion=\"")
                    .append(escaparAtributo(habitacion.seccion().id()))
                    .append("\" data-habitacion-seccion-nombre=\"")
                    .append(escaparAtributo(habitacion.seccion().nombre()))
                    .append("\" data-habitacion-tipo-servicio=\"")
                    .append(escaparAtributo(habitacion.tipoServicio().codigo()))
                    .append("\" data-habitacion-estado=\"")
                    .append(escaparAtributo(habitacion.estado().codigo()))
                    .append("\" data-habitacion-checkin=\"")
                    .append(escaparAtributo(checkIn))
                    .append("\" data-habitacion-checkout=\"")
                    .append(escaparAtributo(checkOut))
                    .append("\" data-habitacion-checkout-epoch=\"")
                    .append(escaparAtributo(checkOutEpoch))
                    .append("\" data-habitacion-pagado=\"")
                    .append(habitacion.pagado())
                    .append("\" data-habitacion-huespedes=\"")
                    .append(escaparAtributo(huespedes))
                    .append("\" aria-haspopup=\"dialog\" aria-controls=\"modal-habitacion\">")
                    .append("<span class=\"tarjeta-habitacion__cabecera\">")
                    .append("<span class=\"tarjeta-habitacion__etiqueta\">HABITACIÓN</span>")
                    .append("<span class=\"tarjeta-habitacion__estado\">")
                    .append(escaparHtml(habitacion.estado().etiqueta()))
                    .append("</span></span><strong class=\"tarjeta-habitacion__numero\">")
                    .append(String.format(Locale.ROOT, "%02d", habitacion.numero()))
                    .append("</strong><span class=\"tarjeta-habitacion__precio\">S/ ")
                    .append(precio)
                    .append(" &middot; ")
                    .append(escaparHtml(habitacion.tipoServicio().etiqueta()))
                    .append("</span><span class=\"tarjeta-habitacion__detalle\">")
                    .append(detalleHabitacion(habitacion))
                    .append("</span><span class=\"tarjeta-habitacion__accion\">")
                    .append("<span>Editar detalles</span><span aria-hidden=\"true\">&rarr;</span>")
                    .append("</span></button>");
        }
        return tarjetas.toString();
    }

    static String crearFilasHuespedes(List<DatosHuesped> huespedes) {
        StringBuilder filas = new StringBuilder(huespedes.size() * 650);
        for (int indice = 0; indice < huespedes.size(); indice++) {
            DatosHuesped huesped = huespedes.get(indice);
            TipoDocumento tipoSeleccionado = huesped.tipoDocumento() == null
                    ? TipoDocumento.DNI
                    : huesped.tipoDocumento();
            String sufijo = Integer.toString(indice + 1);
            String prefijo = "huesped." + indice + ".";
            filas.append("<article class=\"fila-huesped\" data-fila-huesped>")
                    .append("<div class=\"fila-huesped__cabecera\"><h3>Huésped ")
                    .append(sufijo)
                    .append("</h3><button class=\"quitar-huesped\" type=\"button\" ")
                    .append("data-quitar-huesped aria-label=\"Quitar huésped ")
                    .append(sufijo).append("\">Quitar</button></div>")
                    .append("<div class=\"campos-huesped\"><div class=\"campo campo--hotel\">")
                    .append("<label for=\"nombre-huesped-").append(sufijo)
                    .append("\">NOMBRES Y APELLIDOS</label><input id=\"nombre-huesped-")
                    .append(sufijo).append("\" name=\"").append(prefijo)
                    .append("nombre\" type=\"text\" maxlength=\"100\" autocomplete=\"name\" value=\"")
                    .append(escaparAtributo(huesped.nombreCompleto())).append("\"></div>")
                    .append("<div class=\"campo campo--hotel\"><label for=\"tipo-huesped-")
                    .append(sufijo).append("\">DOCUMENTO</label><select id=\"tipo-huesped-")
                    .append(sufijo).append("\" name=\"").append(prefijo)
                    .append("tipoDocumento\">");
            for (TipoDocumento tipo : TipoDocumento.values()) {
                filas.append("<option value=\"").append(tipo.codigo()).append("\"")
                        .append(tipo == tipoSeleccionado ? " selected" : "")
                        .append(">").append(escaparHtml(tipo.etiqueta())).append("</option>");
            }
            filas.append("</select></div><div class=\"campo campo--hotel\">")
                    .append("<label for=\"documento-huesped-").append(sufijo)
                    .append("\">NÚMERO DE DOCUMENTO</label><input id=\"documento-huesped-")
                    .append(sufijo).append("\" name=\"").append(prefijo)
                    .append("numeroDocumento\" type=\"text\" data-documento-huesped ")
                    .append("minlength=\"").append(tipoSeleccionado.longitudMinima())
                    .append("\" maxlength=\"").append(tipoSeleccionado.longitudMaxima())
                    .append("\" pattern=\"").append(tipoSeleccionado.patron())
                    .append("\" inputmode=\"").append(tipoSeleccionado.modoEntrada())
                    .append("\" placeholder=\"").append(tipoSeleccionado.ejemplo())
                    .append("\" autocapitalize=\"characters\" spellcheck=\"false\" value=\"")
                    .append(escaparAtributo(huesped.numeroDocumento()))
                    .append("\"></div></div></article>");
        }
        return filas.toString();
    }

    static String crearTarjetasAreasComunes(
            List<AreaComun> areas, String areaSeleccionada) {
        StringBuilder tarjetas = new StringBuilder(areas.size() * 450);
        for (AreaComun area : areas) {
            boolean seleccionada = area.codigo().equals(areaSeleccionada);
            tarjetas.append("<button class=\"tarjeta-area-comun")
                    .append(seleccionada ? " tarjeta-area-comun--seleccionada" : "")
                    .append("\" type=\"button\" role=\"radio\" aria-checked=\"")
                    .append(seleccionada).append("\" data-area-comun=\"")
                    .append(escaparAtributo(area.codigo())).append("\">")
                    .append("<span class=\"tarjeta-area-comun__cabecera\">")
                    .append("<span class=\"tarjeta-area-comun__simbolo\" aria-hidden=\"true\">")
                    .append(escaparHtml(area.nombre().substring(0, 1)))
                    .append("</span><span class=\"tarjeta-area-comun__marca\">")
                    .append(seleccionada ? "Seleccionada" : "Seleccionar")
                    .append("</span></span><strong>")
                    .append(escaparHtml(area.nombre())).append("</strong><p>")
                    .append(escaparHtml(area.descripcion())).append("</p></button>");
        }
        return tarjetas.toString();
    }

    static String crearRegistrosHuespedes(List<RegistroHuesped> registros) {
        if (registros.isEmpty()) {
            return "<div class=\"sin-resultados sin-resultados--huespedes\">"
                    + "<span aria-hidden=\"true\">◇</span><h2>Aún no hay huéspedes</h2>"
                    + "<p>Los visitantes aparecerán aquí al registrar una habitación o un área común.</p></div>";
        }
        StringBuilder contenido = new StringBuilder(registros.size() * 700);
        contenido.append("<div class=\"tabla-huespedes\" role=\"table\" ")
                .append("aria-label=\"Historial de huéspedes\"><div class=\"tabla-huespedes__fila ")
                .append("tabla-huespedes__cabecera\" role=\"row\">")
                .append("<span role=\"columnheader\">Huésped</span>")
                .append("<span role=\"columnheader\">Documento</span>")
                .append("<span role=\"columnheader\">Ubicación</span>")
                .append("<span role=\"columnheader\">Color</span>")
                .append("<span role=\"columnheader\">Día visitado</span>")
                .append("<span role=\"columnheader\">Check-in</span>")
                .append("<span role=\"columnheader\">Check-out</span>")
                .append("<span role=\"columnheader\">Estado</span></div>");
        for (RegistroHuesped registro : registros) {
            boolean perteneceAHabitacion = registro.ubicacion().startsWith("Habitación ");
            contenido.append("<article class=\"tabla-huespedes__fila\" role=\"row\">")
                    .append(celda("Huésped", registro.nombreCompleto()))
                    .append(celda("Documento", registro.tipoDocumento().etiqueta()
                            + " · " + registro.numeroDocumento()))
                    .append(celda("Ubicación", registro.ubicacion()));
            if (perteneceAHabitacion) {
                contenido.append("<span role=\"cell\" data-etiqueta=\"Color\"><span class=\"color-estancia\" ")
                        .append("style=\"--color-estancia:")
                        .append(escaparAtributo(registro.colorHabitacion()))
                        .append("\"><i aria-hidden=\"true\"></i>")
                        .append(escaparHtml(registro.colorHabitacion()))
                        .append("</span></span>");
            } else {
                contenido.append(celda("Color", "No aplica"));
            }
            contenido
                    .append(celda("Día visitado", registro.fechaVisita().format(FORMATO_DIA_VISIBLE)))
                    .append(celda("Check-in", registro.checkIn().format(FORMATO_FECHA_VISIBLE)))
                    .append(celda("Check-out", registro.checkOut().format(FORMATO_FECHA_VISIBLE)))
                    .append("<span role=\"cell\" data-etiqueta=\"Estado\"><strong class=\"estado-estancia ")
                    .append(registro.estanciaActiva() ? "estado-estancia--activa" : "")
                    .append("\">").append(perteneceAHabitacion
                            ? (registro.estanciaActiva() ? "En curso" : "Finalizada")
                            : "Registrada")
                    .append("</strong></span></article>");
        }
        return contenido.append("</div>").toString();
    }

    static String crearOpcionesColoresFiltro(
            List<SeccionTarifaria> secciones, String colorSeleccionado) {
        StringBuilder opciones = new StringBuilder();
        for (SeccionTarifaria seccion : secciones) {
            opciones.append("<option value=\"")
                    .append(escaparAtributo(seccion.colorHex()))
                    .append("\"")
                    .append(seccion.colorHex().equalsIgnoreCase(colorSeleccionado)
                            ? " selected" : "")
                    .append(">")
                    .append(escaparHtml(seccion.nombre()))
                    .append(" (").append(escaparHtml(seccion.colorHex())).append(")</option>");
        }
        return opciones.toString();
    }

    static String crearOpcionesHabitacionesServicio(List<Habitacion> habitaciones) {
        StringBuilder opciones = new StringBuilder();
        habitaciones.stream()
                .filter(habitacion -> habitacion.estado()
                        == com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.EstadoHabitacion.OCUPADA)
                .forEach(habitacion -> opciones.append("<option value=\"")
                        .append(habitacion.numero()).append("\">Habitación ")
                        .append(String.format(Locale.ROOT, "%02d", habitacion.numero()))
                        .append("</option>"));
        return opciones.toString();
    }

    static String crearOpcionesProductosServicio(List<ProductoServicio> productos) {
        if (productos.isEmpty()) {
            return "<option value=\"\">No hay productos en el catálogo</option>";
        }
        StringBuilder opciones = new StringBuilder(productos.size() * 150);
        String categoriaAnterior = "";
        for (ProductoServicio producto : productos) {
            if (!categoriaAnterior.equals(producto.categoria())) {
                if (!categoriaAnterior.isEmpty()) {
                    opciones.append("</optgroup>");
                }
                categoriaAnterior = producto.categoria();
                opciones.append("<optgroup label=\"")
                        .append(escaparAtributo(categoriaAnterior)).append("\">");
            }
            opciones.append("<option value=\"")
                    .append(escaparAtributo(producto.codigo()))
                    .append("\" data-nombre=\"")
                    .append(escaparAtributo(producto.nombre()))
                    .append("\" data-categoria=\"")
                    .append(escaparAtributo(producto.categoria()))
                    .append("\" data-precio=\"").append(precioCanonico(producto.precio()))
                    .append("\">").append(escaparHtml(producto.nombre()))
                    .append(" · S/ ").append(precioCanonico(producto.precio()))
                    .append("</option>");
        }
        if (!categoriaAnterior.isEmpty()) {
            opciones.append("</optgroup>");
        }
        return opciones.toString();
    }

    static String crearOpcionesCategoriasServicio(List<String> categorias) {
        StringBuilder opciones = new StringBuilder(categorias.size() * 90);
        for (String categoria : categorias) {
            opciones.append("<option value=\"")
                    .append(escaparAtributo(categoria))
                    .append("\">")
                    .append(escaparHtml(categoria))
                    .append("</option>");
        }
        return opciones.toString();
    }

    static String crearCategoriasCatalogoServicio(
            List<String> categorias, List<ProductoServicio> productos) {
        StringBuilder contenido = new StringBuilder(categorias.size() * 320);
        for (String categoria : categorias) {
            long cantidad = productos.stream()
                    .filter(producto -> producto.categoria().equals(categoria))
                    .count();
            boolean ultima = categorias.size() == 1;
            boolean bloqueada = cantidad > 0 || ultima;
            contenido.append("<article class=\"categoria-catalogo-servicio\">")
                    .append("<div><strong>").append(escaparHtml(categoria))
                    .append("</strong><span>").append(cantidad)
                    .append(cantidad == 1 ? " producto" : " productos")
                    .append("</span></div><form action=\"")
                    .append("/hotel/servicio-habitacion/catalogo/categorias/eliminar")
                    .append("\" method=\"post\"><input name=\"categoria\" type=\"hidden\" value=\"")
                    .append(escaparAtributo(categoria)).append("\">")
                    .append("<button class=\"boton boton--peligro boton--categoria-catalogo\" ")
                    .append("type=\"submit\" data-eliminar-categoria");
            if (bloqueada) {
                contenido.append(" disabled title=\"")
                        .append(ultima
                                ? "El catálogo debe conservar al menos una categoría"
                                : "Reasigna o quita sus productos antes de eliminarla")
                        .append("\"");
            }
            contenido.append(">Quitar</button></form></article>");
        }
        return contenido.toString();
    }

    static String crearPedidosServicio(List<PedidoServicio> pedidos) {
        if (pedidos.isEmpty()) {
            return "<div class=\"sin-resultados sin-resultados--servicio\">"
                    + "<span aria-hidden=\"true\">◇</span><h3>Aún no hay pedidos</h3>"
                    + "<p>Las solicitudes creadas aparecerán en esta cola operativa.</p></div>";
        }
        StringBuilder tarjetas = new StringBuilder(pedidos.size() * 750);
        for (PedidoServicio pedido : pedidos) {
            tarjetas.append("<article class=\"pedido-servicio pedido-servicio--")
                    .append(pedido.estado().codigo()).append("\" data-pedido-servicio")
                    .append(" data-pedido-estado=\"").append(pedido.estado().codigo())
                    .append("\"><header><div><span class=\"pedido-servicio__numero\">Pedido #")
                    .append(pedido.id()).append("</span><h3>Habitación ")
                    .append(String.format(Locale.ROOT, "%02d", pedido.numeroHabitacion()))
                    .append("</h3></div><strong class=\"estado-pedido-servicio\">")
                    .append(escaparHtml(pedido.estado().etiqueta()))
                    .append("</strong></header><p class=\"pedido-servicio__hora\">Creado ")
                    .append(pedido.creadoEn().format(FORMATO_FECHA_VISIBLE)).append("</p>")
                    .append("<ul class=\"lineas-pedido-servicio\">");
            for (LineaPedidoServicio linea : pedido.lineas()) {
                tarjetas.append("<li><span>").append(linea.cantidad()).append(" × ")
                        .append(escaparHtml(linea.productoNombre()))
                        .append("</span><strong>S/ ").append(precioCanonico(linea.subtotal()))
                        .append("</strong></li>");
            }
            tarjetas.append("</ul>");
            if (!pedido.observaciones().isEmpty()) {
                tarjetas.append("<p class=\"pedido-servicio__nota\"><strong>Nota:</strong> ")
                        .append(escaparHtml(pedido.observaciones())).append("</p>");
            }
            tarjetas.append("<footer><strong>Total S/ ")
                    .append(precioCanonico(pedido.total())).append("</strong>")
                    .append(accionesPedidoServicio(pedido)).append("</footer></article>");
        }
        return tarjetas.toString();
    }

    private static String accionesPedidoServicio(PedidoServicio pedido) {
        if (pedido.estado() == EstadoPedidoServicio.ENTREGADO
                || pedido.estado() == EstadoPedidoServicio.CANCELADO) {
            return "<span class=\"pedido-servicio__cerrado\">Proceso finalizado</span>";
        }
        StringBuilder acciones = new StringBuilder("<div class=\"acciones-pedido-servicio\">");
        if (pedido.estado() == EstadoPedidoServicio.PENDIENTE) {
            acciones.append(formularioEstadoPedido(pedido.id(), "en_preparacion", "Preparar", false));
        } else {
            acciones.append(formularioEstadoPedido(pedido.id(), "entregado", "Entregado", false));
        }
        acciones.append(formularioEstadoPedido(pedido.id(), "cancelado", "Cancelar", true));
        return acciones.append("</div>").toString();
    }

    private static String formularioEstadoPedido(
            long pedidoId, String estado, String etiqueta, boolean secundario) {
        return "<form action=\"/hotel/servicio-habitacion/pedidos/estado\" method=\"post\">"
                + "<input name=\"pedidoId\" type=\"hidden\" value=\"" + pedidoId + "\">"
                + "<input name=\"estado\" type=\"hidden\" value=\"" + estado + "\">"
                + "<button class=\"boton "
                + (secundario ? "boton--secundario" : "boton--principal")
                + " boton--pedido\" type=\"submit\">" + etiqueta + "</button></form>";
    }

    static String crearOpcionesSecciones(List<SeccionTarifaria> secciones) {
        StringBuilder opciones = new StringBuilder(secciones.size() * 280);
        for (SeccionTarifaria seccion : secciones) {
            Objects.requireNonNull(seccion, "seccion");
            opciones.append("<option value=\"")
                    .append(escaparAtributo(seccion.id()))
                    .append("\" data-color=\"")
                    .append(escaparAtributo(seccion.colorHex()))
                    .append("\" data-nombre=\"")
                    .append(escaparAtributo(seccion.nombre()))
                    .append("\" data-precio-seis-horas=\"")
                    .append(precioCanonico(seccion.precioSeisHoras()))
                    .append("\" data-precio-dia-completo=\"")
                    .append(precioCanonico(seccion.precioDiaCompleto()))
                    .append("\">")
                    .append(escaparHtml(seccion.nombre()))
                    .append("</option>");
        }
        return opciones.toString();
    }

    static String crearTarjetasSecciones(
            List<SeccionTarifaria> secciones, List<Habitacion> habitaciones) {
        StringBuilder tarjetas = new StringBuilder(secciones.size() * 1_500);
        for (int indice = 0; indice < secciones.size(); indice++) {
            SeccionTarifaria seccion = Objects.requireNonNull(secciones.get(indice));
            long cantidadAsignada = habitaciones.stream()
                    .filter(habitacion -> habitacion.seccion().id().equals(seccion.id()))
                    .count();
            String sufijo = Integer.toString(indice + 1);
            String id = escaparAtributo(seccion.id());
            String color = escaparAtributo(seccion.colorHex());
            String nombre = escaparHtml(seccion.nombre());
            String nombreAtributo = escaparAtributo(seccion.nombre());
            String nombreFormulario = seccion.nombre().equals("Color " + seccion.colorHex())
                    ? ""
                    : nombreAtributo;
            String prefijoCampo = "seccion." + indice + ".";
            tarjetas.append("<article class=\"tarjeta-seccion-tarifaria\" ")
                    .append("data-seccion-tarifaria data-color=\"").append(color)
                    .append("\" data-seccion-id=\"").append(id)
                    .append("\" data-habitaciones-asignadas=\"").append(cantidadAsignada)
                    .append("\"><div class=\"formulario-seccion-tarifaria\" ")
                    .append("data-formulario-seccion>")
                    .append("<input name=\"").append(prefijoCampo)
                    .append("id\" type=\"hidden\" value=\"").append(id)
                    .append("\"><input name=\"").append(prefijoCampo)
                    .append("accion\" type=\"hidden\" value=\"guardar\" data-accion-seccion>")
                    .append("<div class=\"tarjeta-seccion-tarifaria__cabecera\">")
                    .append("<span class=\"muestra-color-seccion\" data-muestra-color aria-hidden=\"true\"></span>")
                    .append("<div><p class=\"sobrelinea\">SECCIÓN REGISTRADA</p><h3 id=\"titulo-seccion-")
                    .append(sufijo)
                    .append("\" data-nombre-visible>")
                    .append(nombre).append("</h3></div></div><div class=\"campos-seccion\">")
                    .append(campoSeccion(
                            "seccion-color-" + sufijo,
                            "COLOR",
                            prefijoCampo + "color",
                            "color",
                            color,
                            " data-campo-seccion=\"color\" required"
                                    + " aria-describedby=\"error-seccion-" + sufijo + "\""))
                    .append(campoSeccion(
                            "seccion-nombre-" + sufijo,
                            "NOMBRE OPCIONAL",
                            prefijoCampo + "nombre",
                            "text",
                            nombreFormulario,
                            " data-campo-seccion=\"nombre\" maxlength=\"40\""
                                    + " placeholder=\"Ej.: Premium\""
                                    + " aria-describedby=\"error-seccion-" + sufijo + "\""))
                    .append(campoSeccion(
                            "seccion-precio-6-" + sufijo,
                            "PRECIO POR 6 HORAS (S/)",
                            prefijoCampo + "precio6Horas",
                            "number",
                            precioCanonico(seccion.precioSeisHoras()),
                            " data-campo-seccion=\"precio6Horas\" min=\"1\" max=\"10000\""
                                    + " step=\"0.01\" inputmode=\"decimal\" required"
                                    + " aria-describedby=\"error-seccion-" + sufijo + "\""))
                    .append(campoSeccion(
                            "seccion-precio-dia-" + sufijo,
                            "PRECIO DÍA COMPLETO (S/)",
                            prefijoCampo + "precioDiaCompleto",
                            "number",
                            precioCanonico(seccion.precioDiaCompleto()),
                            " data-campo-seccion=\"precioDiaCompleto\" min=\"1\" max=\"10000\""
                                    + " step=\"0.01\" inputmode=\"decimal\" required"
                                    + " aria-describedby=\"error-seccion-" + sufijo + "\""))
                    .append("</div><p class=\"ayuda-estadia\">")
                    .append(cantidadAsignada).append(cantidadAsignada == 1
                            ? " habitación asignada"
                            : " habitaciones asignadas")
                    .append(".</p><p id=\"error-seccion-").append(sufijo)
                    .append("\" class=\"mensaje-error mensaje-error--seccion\" ")
                    .append("data-error-seccion role=\"alert\" aria-live=\"polite\"></p>")
                    .append("<div class=\"tarjeta-seccion-tarifaria__acciones\">")
                    .append("<button class=\"boton boton--principal boton--guardar-seccion\" ")
                    .append("type=\"button\" ")
                    .append("data-ruta-guardado=\"/hotel/configuracion/secciones/guardar\" ")
                    .append("data-guardar-seccion aria-label=\"Guardar cambios de ")
                    .append(nombreAtributo)
                    .append("\">Guardar cambios</button>")
                    .append("<button class=\"boton boton--peligro\" type=\"button\" ")
                    .append("data-eliminar-seccion");
            if (cantidadAsignada > 0) {
                tarjetas.append(" disabled title=\"Asigna estas habitaciones a otra sección antes de eliminarla\"");
            }
            tarjetas.append(">Eliminar sección</button>")
                    .append("<button class=\"boton boton--secundario\" type=\"button\" ")
                    .append("data-deshacer-eliminacion hidden>Deshacer eliminación</button>")
                    .append("</div></div></article>");
        }
        return tarjetas.toString();
    }

    static String precioCanonico(Habitacion habitacion) {
        return precioCanonico(habitacion.precioAplicado());
    }

    static String rangoReporte(FiltroReporte filtro) {
        return filtro.desde().format(FORMATO_DIA_VISIBLE)
                + " - " + filtro.hasta().format(FORMATO_DIA_VISIBLE);
    }

    static String crearFilasResumenReporte(List<ResumenPeriodo> periodos) {
        StringBuilder filas = new StringBuilder(periodos.size() * 260);
        for (ResumenPeriodo periodo : periodos) {
            filas.append("<div class=\"tabla-reporte__fila\" role=\"row\">")
                    .append(celda("Periodo", periodo.etiqueta()))
                    .append(celda("Habitaciones", "S/ " + precioCanonico(periodo.habitaciones())))
                    .append(celda("Servicio al cuarto", "S/ " + precioCanonico(periodo.servicios())))
                    .append("<strong role=\"cell\" data-etiqueta=\"Total\">S/ ")
                    .append(precioCanonico(periodo.total()))
                    .append("</strong></div>");
        }
        return filas.toString();
    }

    static String crearGraficoIngresos(List<ResumenPeriodo> periodos) {
        BigDecimal maximo = periodos.stream()
                .map(ResumenPeriodo::total)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        StringBuilder grafico = new StringBuilder(periodos.size() * 360);
        for (ResumenPeriodo periodo : periodos) {
            String porcentajeHabitaciones = porcentajeBarra(
                    periodo.habitaciones(), maximo);
            String porcentajeServicios = porcentajeBarra(periodo.servicios(), maximo);
            String detalle = periodo.etiqueta() + ": S/ " + precioCanonico(periodo.total())
                    + "; habitaciones S/ " + precioCanonico(periodo.habitaciones())
                    + "; servicio S/ " + precioCanonico(periodo.servicios());
            grafico.append("<div class=\"grafico-ingresos__fila\" aria-label=\"")
                    .append(escaparAtributo(detalle))
                    .append("\"><span class=\"grafico-ingresos__etiqueta\">")
                    .append(escaparHtml(periodo.etiqueta()))
                    .append("</span><div class=\"grafico-ingresos__pista\">")
                    .append("<i class=\"grafico-ingresos__habitaciones\" style=\"--valor: ")
                    .append(porcentajeHabitaciones)
                    .append("%\"></i><i class=\"grafico-ingresos__servicios\" style=\"--valor: ")
                    .append(porcentajeServicios)
                    .append("%\"></i></div><strong>S/ ")
                    .append(precioCanonico(periodo.total()))
                    .append("</strong></div>");
        }
        return grafico.toString();
    }

    static String crearRankingHabitaciones(List<ResumenHabitacion> habitaciones) {
        if (habitaciones.isEmpty()) {
            return "<div class=\"ranking-habitaciones__vacio\">"
                    + "<span aria-hidden=\"true\">&#9671;</span>"
                    + "<p>Aún no hay ingresos para construir el ranking.</p></div>";
        }
        BigDecimal maximo = habitaciones.get(0).monto();
        StringBuilder ranking = new StringBuilder(habitaciones.size() * 260);
        int posicion = 1;
        for (ResumenHabitacion habitacion : habitaciones) {
            ranking.append("<div class=\"ranking-habitaciones__fila\">")
                    .append("<span class=\"ranking-habitaciones__posicion\">")
                    .append(posicion++)
                    .append("</span><div><div class=\"ranking-habitaciones__datos\">")
                    .append("<strong>Habitación ")
                    .append(String.format(Locale.ROOT, "%02d", habitacion.numeroHabitacion()))
                    .append("</strong><span>S/ ")
                    .append(precioCanonico(habitacion.monto()))
                    .append("</span></div><div class=\"ranking-habitaciones__barra\"><i style=\"--valor: ")
                    .append(porcentajeBarra(habitacion.monto(), maximo))
                    .append("%\"></i></div><small>")
                    .append(habitacion.movimientos())
                    .append(habitacion.movimientos() == 1 ? " movimiento" : " movimientos")
                    .append("</small></div></div>");
        }
        return ranking.toString();
    }

    private static String porcentajeBarra(BigDecimal valor, BigDecimal maximo) {
        if (maximo.signum() <= 0) {
            return "0.00";
        }
        return valor.multiply(BigDecimal.valueOf(100))
                .divide(maximo, 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100))
                .toPlainString();
    }

    static String crearDetallesReporte(List<DetalleIngreso> detalles) {
        if (detalles.isEmpty()) {
            return "<div class=\"sin-resultados sin-resultados--reporte\">"
                    + "<span aria-hidden=\"true\">&#9671;</span>"
                    + "<h3>No hay ingresos en este periodo</h3>"
                    + "<p>Ajusta el intervalo o el tipo de ingreso para consultar otros movimientos.</p>"
                    + "</div>";
        }
        StringBuilder filas = new StringBuilder(detalles.size() * 310);
        for (DetalleIngreso detalle : detalles) {
            String origen = detalle.tipo()
                    == FiltroReporte.TipoIngresoReporte.HABITACIONES
                    ? "Habitación" : "Servicio al cuarto";
            filas.append("<div class=\"tabla-reporte__fila tabla-reporte__fila--detalle\" role=\"row\">")
                    .append(celda("Fecha y hora", detalle.fecha().format(FORMATO_FECHA_VISIBLE)))
                    .append(celda("Origen", origen))
                    .append(celda("Habitacion", String.format(
                            Locale.ROOT, "Habitación %02d", detalle.numeroHabitacion())))
                    .append(celda("Concepto", detalle.concepto()))
                    .append("<strong role=\"cell\" data-etiqueta=\"Monto\">S/ ")
                    .append(precioCanonico(detalle.monto()))
                    .append("</strong></div>");
        }
        return filas.toString();
    }

    static String precioCanonico(BigDecimal precio) {
        return precio.setScale(2).toPlainString();
    }

    static String fechaDato(LocalDateTime fecha) {
        return fecha == null ? "" : fecha.format(FORMATO_FECHA_DATO);
    }

    static String escaparHtml(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String escaparAtributo(String texto) {
        return escaparHtml(texto);
    }

    private static String celda(String etiqueta, String valor) {
        return "<span role=\"cell\" data-etiqueta=\"" + escaparAtributo(etiqueta)
                + "\">" + escaparHtml(valor) + "</span>";
    }

    private static String serializarHuespedes(List<DatosHuesped> huespedes) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < huespedes.size(); indice++) {
            DatosHuesped huesped = huespedes.get(indice);
            if (indice > 0) {
                json.append(',');
            }
            json.append("{\"nombre\":\"").append(escaparJson(huesped.nombreCompleto()))
                    .append("\",\"tipoDocumento\":\"")
                    .append(huesped.tipoDocumento().codigo())
                    .append("\",\"numeroDocumento\":\"")
                    .append(escaparJson(huesped.numeroDocumento())).append("\"}");
        }
        return json.append(']').toString();
    }

    private static String escaparJson(String texto) {
        StringBuilder resultado = new StringBuilder();
        for (int indice = 0; indice < texto.length(); indice++) {
            char caracter = texto.charAt(indice);
            switch (caracter) {
                case '\\' -> resultado.append("\\\\");
                case '"' -> resultado.append("\\\"");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> resultado.append(caracter);
            }
        }
        return resultado.toString();
    }

    private static String campoSeccion(
            String id,
            String etiqueta,
            String nombre,
            String tipo,
            String valor,
            String atributos) {
        return "<div class=\"campo campo--hotel"
                + ("color".equals(tipo) ? " campo--color" : "")
                + "\"><label for=\"" + id + "\">" + etiqueta + "</label><input id=\""
                + id + "\" name=\"" + nombre + "\" type=\"" + tipo + "\" value=\""
                + valor + "\"" + atributos + "></div>";
    }

    private static String detalleHabitacion(Habitacion habitacion) {
        if (habitacion.proximoCheckOut() != null) {
            String pago = habitacion.pagado() ? "Pagado" : "Pago pendiente";
            return "Check-out "
                    + escaparHtml(habitacion.proximoCheckOut().format(FORMATO_FECHA_VISIBLE))
                    + " &middot; " + pago;
        }
        return switch (habitacion.estado()) {
            case DISPONIBLE -> "Sin estadía programada";
            case OCUPADA -> "Estadía sin horario registrado";
            case LIMPIEZA_PENDIENTE -> "Pendiente de asignar limpieza";
            case EN_LIMPIEZA -> "Limpieza en curso";
            case MANTENIMIENTO -> "Fuera de servicio temporalmente";
        };
    }

    private static String instanteDato(LocalDateTime fecha) {
        return fecha == null
                ? ""
                : Long.toString(fecha.atZone(ZONA_HOTEL).toInstant().toEpochMilli());
    }
}
