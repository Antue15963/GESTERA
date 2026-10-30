package com.gestera.modelo.servicios;

import com.gestera.repositorios.servicios.RepositorioCatalogoServicio;
import com.gestera.repositorios.servicios.RepositorioServicioHabitacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Reglas de negocio y fachada del servicio al cuarto por hotel. */
public final class GestorServicioHabitacion implements AutoCloseable {
    public static final int CANTIDAD_MAXIMA = 10;
    public static final int OBSERVACIONES_MAXIMAS = 300;
    public static final int NOMBRE_PRODUCTO_MAXIMO = 120;
    public static final int CATEGORIA_PRODUCTO_MAXIMA = 60;
    public static final BigDecimal PRECIO_PRODUCTO_MAXIMO = new BigDecimal("10000.00");
    private static final ZoneId ZONA_HOTEL = ZoneId.of("America/Lima");
    private static final List<ProductoServicio> CATALOGO = List.of(
            producto("desayuno-continental", "Desayuno continental", "Desayunos", "25.00"),
            producto("club-sandwich", "Club sándwich", "Comidas", "32.00"),
            producto("sopa-del-dia", "Sopa del día", "Comidas", "18.00"),
            producto("cafe-americano", "Café americano", "Bebidas", "8.00"),
            producto("agua-mineral", "Agua mineral", "Bebidas", "6.00"));
    private static final List<String> CATEGORIAS = List.of(
            "Desayunos", "Comidas", "Bebidas");

    private final RepositorioServicioHabitacion repositorio;
    private final Clock reloj;
    private final RepositorioCatalogoServicio almacenCatalogo;
    private final Map<String, CatalogoHotel> catalogosPorHotel =
            new ConcurrentHashMap<>();

    public GestorServicioHabitacion(RepositorioServicioHabitacion repositorio) {
        this(repositorio, Clock.system(ZONA_HOTEL));
    }

    public GestorServicioHabitacion(
            RepositorioServicioHabitacion repositorio, Clock reloj) {
        this(repositorio,reloj,null);
    }

    public GestorServicioHabitacion(RepositorioServicioHabitacion repositorio,Clock reloj,RepositorioCatalogoServicio almacenCatalogo) {
        this.almacenCatalogo=almacenCatalogo;
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public List<ProductoServicio> catalogo() {
        return CATALOGO;
    }

    public List<ProductoServicio> catalogo(String codigoHotel) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return CATALOGO;
        }
        return catalogoHotel(codigoHotel).productos();
    }

    public List<String> categorias(String codigoHotel) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return CATEGORIAS;
        }
        return catalogoHotel(codigoHotel).categorias();
    }

    public synchronized ResultadoOperacion actualizarProducto(
            String codigoHotel,
            String codigoProducto,
            String nombre,
            String categoria,
            BigDecimal precio) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return ResultadoOperacion.HOTEL_INVALIDO;
        }
        String codigoSeguro = normalizarCodigo(codigoProducto);
        CatalogoHotel catalogo = catalogoHotel(codigoHotel);
        if (catalogo.productos().stream()
                .noneMatch(producto -> producto.codigo().equals(codigoSeguro))) {
            return ResultadoOperacion.PRODUCTO_INVALIDO;
        }
        String nombreSeguro = normalizarTexto(nombre);
        if (!textoValido(nombreSeguro, NOMBRE_PRODUCTO_MAXIMO)) {
            return ResultadoOperacion.NOMBRE_PRODUCTO_INVALIDO;
        }
        String categoriaSegura = normalizarTexto(categoria);
        if (!textoValido(categoriaSegura, CATEGORIA_PRODUCTO_MAXIMA)) {
            return ResultadoOperacion.CATEGORIA_PRODUCTO_INVALIDA;
        }
        String categoriaExistente = categoriaCanonica(catalogo, categoriaSegura);
        if (categoriaExistente == null) {
            return ResultadoOperacion.CATEGORIA_DESCONOCIDA;
        }
        if (!precioProductoValido(precio)) {
            return ResultadoOperacion.PRECIO_PRODUCTO_INVALIDO;
        }
        boolean nombreDuplicado = catalogo.productos().stream()
                .anyMatch(producto -> !producto.codigo().equals(codigoSeguro)
                        && producto.nombre().equalsIgnoreCase(nombreSeguro));
        if (nombreDuplicado) {
            return ResultadoOperacion.PRODUCTO_DUPLICADO;
        }
        List<ProductoServicio> actualizados = catalogo.productos().stream()
                .map(producto -> producto.codigo().equals(codigoSeguro)
                        ? new ProductoServicio(
                                codigoSeguro,
                                nombreSeguro,
                                categoriaExistente,
                                precio.setScale(2))
                        : producto)
                .toList();
        return guardarCatalogo(codigoHotel,new CatalogoHotel(actualizados, catalogo.categorias(),catalogo.version()))
                ? ResultadoOperacion.ACTUALIZADO : ResultadoOperacion.CONFLICTO_CONCURRENCIA;
    }

    public synchronized ResultadoOperacion agregarProducto(
            String codigoHotel, String nombre, String categoria, BigDecimal precio) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return ResultadoOperacion.HOTEL_INVALIDO;
        }
        String nombreSeguro = normalizarTexto(nombre);
        if (!textoValido(nombreSeguro, NOMBRE_PRODUCTO_MAXIMO)) {
            return ResultadoOperacion.NOMBRE_PRODUCTO_INVALIDO;
        }
        String categoriaSegura = normalizarTexto(categoria);
        CatalogoHotel catalogo = catalogoHotel(codigoHotel);
        String categoriaExistente = categoriaCanonica(catalogo, categoriaSegura);
        if (categoriaExistente == null) {
            return ResultadoOperacion.CATEGORIA_DESCONOCIDA;
        }
        if (!precioProductoValido(precio)) {
            return ResultadoOperacion.PRECIO_PRODUCTO_INVALIDO;
        }
        if (catalogo.productos().stream()
                .anyMatch(producto -> producto.nombre().equalsIgnoreCase(nombreSeguro))) {
            return ResultadoOperacion.PRODUCTO_DUPLICADO;
        }
        String codigo = crearCodigoProducto(nombreSeguro, catalogo.productos());
        java.util.ArrayList<ProductoServicio> productos =
                new java.util.ArrayList<>(catalogo.productos());
        productos.add(new ProductoServicio(
                codigo, nombreSeguro, categoriaExistente, precio.setScale(2)));
        return guardarCatalogo(codigoHotel,new CatalogoHotel(productos, catalogo.categorias(),catalogo.version()))
                ? ResultadoOperacion.ACTUALIZADO : ResultadoOperacion.CONFLICTO_CONCURRENCIA;
    }

    public synchronized ResultadoOperacion eliminarProducto(
            String codigoHotel, String codigoProducto) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return ResultadoOperacion.HOTEL_INVALIDO;
        }
        String codigoSeguro = normalizarCodigo(codigoProducto);
        CatalogoHotel catalogo = catalogoHotel(codigoHotel);
        if (catalogo.productos().stream()
                .noneMatch(producto -> producto.codigo().equals(codigoSeguro))) {
            return ResultadoOperacion.PRODUCTO_INVALIDO;
        }
        List<ProductoServicio> productos = catalogo.productos().stream()
                .filter(producto -> !producto.codigo().equals(codigoSeguro))
                .toList();
        return guardarCatalogo(codigoHotel,new CatalogoHotel(productos, catalogo.categorias(),catalogo.version()))
                ? ResultadoOperacion.ACTUALIZADO : ResultadoOperacion.CONFLICTO_CONCURRENCIA;
    }

    public synchronized ResultadoOperacion agregarCategoria(
            String codigoHotel, String categoria) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return ResultadoOperacion.HOTEL_INVALIDO;
        }
        String categoriaSegura = normalizarTexto(categoria);
        if (!textoValido(categoriaSegura, CATEGORIA_PRODUCTO_MAXIMA)) {
            return ResultadoOperacion.CATEGORIA_PRODUCTO_INVALIDA;
        }
        CatalogoHotel catalogo = catalogoHotel(codigoHotel);
        if (categoriaCanonica(catalogo, categoriaSegura) != null) {
            return ResultadoOperacion.CATEGORIA_DUPLICADA;
        }
        java.util.ArrayList<String> categorias =
                new java.util.ArrayList<>(catalogo.categorias());
        categorias.add(categoriaSegura);
        return guardarCatalogo(codigoHotel,new CatalogoHotel(catalogo.productos(), categorias,catalogo.version()))
                ? ResultadoOperacion.ACTUALIZADO : ResultadoOperacion.CONFLICTO_CONCURRENCIA;
    }

    public synchronized ResultadoOperacion eliminarCategoria(
            String codigoHotel, String categoria) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return ResultadoOperacion.HOTEL_INVALIDO;
        }
        CatalogoHotel catalogo = catalogoHotel(codigoHotel);
        String categoriaExistente = categoriaCanonica(
                catalogo, normalizarTexto(categoria));
        if (categoriaExistente == null) {
            return ResultadoOperacion.CATEGORIA_DESCONOCIDA;
        }
        if (catalogo.categorias().size() == 1) {
            return ResultadoOperacion.ULTIMA_CATEGORIA;
        }
        if (catalogo.productos().stream()
                .anyMatch(producto -> producto.categoria().equals(categoriaExistente))) {
            return ResultadoOperacion.CATEGORIA_EN_USO;
        }
        List<String> categorias = catalogo.categorias().stream()
                .filter(actual -> !actual.equals(categoriaExistente))
                .toList();
        return guardarCatalogo(codigoHotel,new CatalogoHotel(catalogo.productos(), categorias,catalogo.version()))
                ? ResultadoOperacion.ACTUALIZADO : ResultadoOperacion.CONFLICTO_CONCURRENCIA;
    }

    public ResultadoOperacion crearPedido(
            String codigoHotel,
            int numeroHabitacion,
            String codigoProducto,
            int cantidad,
            String observaciones) {
        if (almacenCatalogo!=null && codigoHotel!=null && !codigoHotel.isBlank())
            return almacenCatalogo.ejecutar(codigoHotel,()->crearPedidoValidado(codigoHotel,numeroHabitacion,codigoProducto,cantidad,observaciones));
        return crearPedidoValidado(codigoHotel,numeroHabitacion,codigoProducto,cantidad,observaciones);
    }

    private ResultadoOperacion crearPedidoValidado(String codigoHotel,int numeroHabitacion,String codigoProducto,int cantidad,String observaciones) {
        if (codigoHotel == null || codigoHotel.isBlank()) {
            return ResultadoOperacion.HOTEL_INVALIDO;
        }
        if (numeroHabitacion < 1 || numeroHabitacion > 50) {
            return ResultadoOperacion.HABITACION_INVALIDA;
        }
        String codigoSeguro = normalizarCodigo(codigoProducto);
        ProductoServicio producto = catalogo(codigoHotel).stream()
                .filter(actual -> actual.codigo().equals(codigoSeguro))
                .findFirst()
                .orElse(null);
        if (producto == null) {
            return ResultadoOperacion.PRODUCTO_INVALIDO;
        }
        if (cantidad < 1 || cantidad > CANTIDAD_MAXIMA) {
            return ResultadoOperacion.CANTIDAD_INVALIDA;
        }
        String nota = observaciones == null ? "" : observaciones.trim().replaceAll("\\s+", " ");
        if (nota.length() > OBSERVACIONES_MAXIMAS
                || nota.codePoints().anyMatch(Character::isISOControl)) {
            return ResultadoOperacion.OBSERVACIONES_INVALIDAS;
        }
        repositorio.crear(
                codigoHotel,
                numeroHabitacion,
                nota,
                LocalDateTime.now(reloj),
                List.of(new LineaPedidoServicio(
                        producto.codigo(), producto.nombre(), producto.precio(), cantidad)));
        return ResultadoOperacion.CREADO;
    }

    public ResultadoOperacion cambiarEstado(
            String codigoHotel, long pedidoId, String codigoEstadoNuevo) {
        EstadoPedidoServicio nuevo = EstadoPedidoServicio.desdeCodigo(codigoEstadoNuevo)
                .orElse(null);
        if (pedidoId < 1 || nuevo == null) {
            return ResultadoOperacion.ESTADO_INVALIDO;
        }
        PedidoServicio pedido = listar(codigoHotel).stream()
                .filter(actual -> actual.id() == pedidoId)
                .findFirst()
                .orElse(null);
        if (pedido == null) {
            return ResultadoOperacion.PEDIDO_DESCONOCIDO;
        }
        if (!pedido.estado().permite(nuevo)) {
            return ResultadoOperacion.TRANSICION_INVALIDA;
        }
        return repositorio.cambiarEstado(
                codigoHotel,
                pedidoId,
                pedido.estado(),
                nuevo,
                LocalDateTime.now(reloj))
                ? ResultadoOperacion.ACTUALIZADO
                : ResultadoOperacion.CONFLICTO_CONCURRENCIA;
    }

    public List<PedidoServicio> listar(String codigoHotel) {
        return repositorio.listarPorHotel(codigoHotel);
    }

    public List<PedidoServicio> buscar(
            String codigoHotel, FiltroPedidosServicio filtro) {
        FiltroPedidosServicio filtroSeguro = filtro == null
                ? FiltroPedidosServicio.vacio()
                : filtro;
        return listar(codigoHotel).stream()
                .filter(filtroSeguro::coincide)
                .toList();
    }

    @Override
    public void close() {
        catalogosPorHotel.clear();
        repositorio.close();
    }

    private static ProductoServicio producto(
            String codigo, String nombre, String categoria, String precio) {
        return new ProductoServicio(codigo, nombre, categoria, new BigDecimal(precio));
    }

    private static String normalizarCodigo(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizarTexto(String valor) {
        return valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
    }

    private static boolean textoValido(String valor, int longitudMaxima) {
        return !valor.isEmpty()
                && valor.length() <= longitudMaxima
                && valor.codePoints().noneMatch(Character::isISOControl);
    }

    private boolean guardarCatalogo(String hotel,CatalogoHotel catalogo) {
        if (almacenCatalogo!=null) return almacenCatalogo.guardar(hotel,
                new RepositorioCatalogoServicio.DatosCatalogo(catalogo.productos(),catalogo.categorias(),catalogo.version()));
        catalogosPorHotel.put(hotel,catalogo);
        return true;
    }

    private CatalogoHotel catalogoHotel(String codigoHotel) {
        if (almacenCatalogo!=null) {
            almacenCatalogo.inicializar(codigoHotel,CATALOGO,CATEGORIAS);
            var datos=almacenCatalogo.cargar(codigoHotel);
            return new CatalogoHotel(datos.productos(),datos.categorias(),datos.version());
        }
        return catalogosPorHotel.computeIfAbsent(
                codigoHotel, ignorado -> new CatalogoHotel(CATALOGO, CATEGORIAS));
    }

    private static String categoriaCanonica(CatalogoHotel catalogo, String categoria) {
        return catalogo.categorias().stream()
                .filter(actual -> actual.equalsIgnoreCase(categoria))
                .findFirst()
                .orElse(null);
    }

    private static boolean precioProductoValido(BigDecimal precio) {
        return precio != null
                && precio.scale() <= 2
                && precio.compareTo(BigDecimal.ZERO) > 0
                && precio.compareTo(PRECIO_PRODUCTO_MAXIMO) <= 0;
    }

    private static String crearCodigoProducto(
            String nombre, List<ProductoServicio> productos) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(?:^-|-$)", "");
        if (base.isEmpty()) {
            base = "producto";
        }
        if (base.length() > 48) {
            base = base.substring(0, 48).replaceFirst("-+$", "");
        }
        String candidato = base;
        int sufijo = 2;
        while (codigoExiste(candidato, productos)) {
            candidato = base + "-" + sufijo++;
        }
        return candidato;
    }

    private static boolean codigoExiste(
            String codigo, List<ProductoServicio> productos) {
        return productos.stream().anyMatch(producto -> producto.codigo().equals(codigo));
    }

    public enum ResultadoOperacion {
        CREADO,
        ACTUALIZADO,
        HOTEL_INVALIDO,
        HABITACION_INVALIDA,
        PRODUCTO_INVALIDO,
        PRODUCTO_DUPLICADO,
        NOMBRE_PRODUCTO_INVALIDO,
        CATEGORIA_PRODUCTO_INVALIDA,
        CATEGORIA_DESCONOCIDA,
        CATEGORIA_DUPLICADA,
        CATEGORIA_EN_USO,
        ULTIMA_CATEGORIA,
        PRECIO_PRODUCTO_INVALIDO,
        CANTIDAD_INVALIDA,
        OBSERVACIONES_INVALIDAS,
        PEDIDO_DESCONOCIDO,
        ESTADO_INVALIDO,
        TRANSICION_INVALIDA,
        CONFLICTO_CONCURRENCIA
    }

    private record CatalogoHotel(
            List<ProductoServicio> productos, List<String> categorias,long version) {
        private CatalogoHotel(List<ProductoServicio> productos,List<String> categorias) { this(productos,categorias,0); }
        private CatalogoHotel {
            productos = List.copyOf(productos);
            categorias = List.copyOf(categorias);
        }
    }
}
