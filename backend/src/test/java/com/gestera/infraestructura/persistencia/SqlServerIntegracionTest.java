package com.gestera.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.*;
import com.gestera.seguridad.autenticacion.AutenticadorLlave;
import com.gestera.modelo.habitaciones.*;
import com.gestera.repositorios.habitaciones.*;
import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.*;
import com.gestera.modelo.hoteles.*;
import com.gestera.repositorios.hoteles.*;
import com.gestera.modelo.hoteles.CatalogoHoteles.Hotel;
import com.gestera.modelo.huespedes.*;
import com.gestera.repositorios.huespedes.*;
import com.gestera.repositorios.reportes.RepositorioIngresosHabitacionJdbc;
import com.gestera.modelo.servicios.*;
import com.gestera.repositorios.servicios.*;
import com.gestera.infraestructura.configuracion.ConfiguracionAplicacion;
import com.gestera.infraestructura.servidor.ServidorGestera;
import com.gestera.pruebas.ClienteHttpPrueba;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reglas normalizadas reales; solo admite una base aislada gestera_pruebas_*. */
@EnabledIfEnvironmentVariable(named="GESTERA_PRUEBAS_SQLSERVER_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlServerIntegracionTest {
    private HikariDataSource fuente;
    private JdbcTemplate jdbc;
    private RepositorioHuespedesJdbc huespedes;
    private RepositorioServicioHabitacionJdbc pedidos;
    private RepositorioIngresosHabitacionJdbc ingresos;
    private RepositorioCatalogoServicioJdbc catalogo;
    private GestorServicioHabitacion servicio;
    private GestorHabitacionesHoteles habitaciones;
    private Hotel hotel;
    private final LocalDateTime entrada=LocalDateTime.of(2026,10,24,14,0);
    private final Clock reloj=Clock.fixed(Instant.parse("2026-10-24T19:00:00Z"),ZoneId.of("America/Lima"));

    @BeforeAll void conectar() {
        HikariConfig c=new HikariConfig();
        c.setJdbcUrl(System.getenv("GESTERA_PRUEBAS_SQLSERVER_URL"));
        c.setUsername(System.getenv("GESTERA_PRUEBAS_SQLSERVER_USUARIO"));
        c.setPassword(System.getenv("GESTERA_PRUEBAS_SQLSERVER_CONTRASENA"));
        c.setMaximumPoolSize(10);
        fuente=new HikariDataSource(c);
        jdbc=new JdbcTemplate(fuente);
        assertTrue(jdbc.queryForObject("SELECT DB_NAME()",String.class).matches("gestera_pruebas_[a-f0-9]{32}"));
        assertEquals(14,jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND NOT (name=N'sysdiagrams' AND schema_id=SCHEMA_ID(N'dbo'))",Integer.class));
    }
    @BeforeEach void prepararHotel() {
        hotel=new Hotel("prueba-"+UUID.randomUUID(),"Prueba "+UUID.randomUUID(),"Entorno aislado","PJ");
        huespedes=new RepositorioHuespedesJdbc(fuente,List.of(hotel));
        habitaciones=new GestorHabitacionesHoteles(List.of(hotel),reloj,new RepositorioInventarioJdbc(fuente));
        pedidos=new RepositorioServicioHabitacionJdbc(fuente);
        ingresos=new RepositorioIngresosHabitacionJdbc(fuente);
        catalogo=new RepositorioCatalogoServicioJdbc(fuente);
        servicio=new GestorServicioHabitacion(pedidos,reloj,catalogo);
        servicio.catalogo(hotel.codigo());
        jdbc.update("INSERT dbo.areas_comunes(hotel_codigo,codigo,nombre,descripcion) "
                + "SELECT ?,codigo,nombre,descripcion FROM dbo.areas_comunes WHERE hotel_codigo=N'brisa-marina'",hotel.codigo());
    }
    @AfterEach void cerrarGestores() {
        if(habitaciones!=null) habitaciones.close();
        if(servicio!=null) servicio.close();
    }
    @AfterAll void cerrar() { if(fuente!=null) fuente.close(); }
    private Habitacion ocupar(int numero,boolean pagado) {
        assertEquals(ResultadoOperacion.ACTUALIZADA,habitaciones.actualizarHabitacion(hotel.codigo(),numero,"seccion-1",
                TipoServicio.SEIS_HORAS,EstadoHabitacion.OCUPADA,pagado));
        return habitaciones.habitaciones(hotel.codigo()).get(numero-1);
    }
    private DatosHuesped persona() { return new DatosHuesped("Ángela Muñoz",TipoDocumento.DNI,"01234567"); }

    @Test void reutilizaIdentidadYRevierteAsociacionesIncompletas() {
        Habitacion habitacion=ocupar(1,false);
        huespedes.guardarEstancia(hotel.codigo(),1,"Habitación 1","#FBBF24",habitacion.checkIn(),habitacion.proximoCheckOut(),List.of(persona()));
        long id=huespedes.buscarPorHotel(hotel.codigo(),FiltroHuespedes.vacio()).get(0).idEstancia();
        huespedes.guardarEstancia(hotel.codigo(),1,"Habitación 1","#FBBF24",habitacion.checkIn(),entrada.plusHours(8),List.of(persona()));
        var registros=huespedes.buscarPorHotel(hotel.codigo(),FiltroHuespedes.vacio());
        assertEquals(1,registros.size());assertEquals(id,registros.get(0).idEstancia());
        assertEquals("01234567",registros.get(0).numeroDocumento());assertEquals("Ángela Muñoz",registros.get(0).nombreCompleto());
        assertThrows(RepositorioHuespedesException.class,()->huespedes.guardarEstancia(hotel.codigo(),1,"Habitación 1","#FBBF24",
                habitacion.checkIn(),entrada.plusHours(10),List.of(persona(),new DatosHuesped("Documento invalido",TipoDocumento.DNI,"123"))));
        registros=huespedes.buscarPorHotel(hotel.codigo(),FiltroHuespedes.vacio());
        assertEquals(1,registros.size());assertEquals(entrada.plusHours(8),registros.get(0).checkOut());
        assertTrue(huespedes.buscarPorHotel(hotel.codigo(),new FiltroHuespedes("A[",null,null,null,"")).isEmpty());
    }

    @Test void rollbackIncluyeHabitacionEstanciaPersonasYPago() {
        assertThrows(RepositorioHuespedesException.class,()->new TransaccionesHotel(fuente).ejecutar(hotel.codigo(),()->{
            Habitacion h=ocupar(2,true);
            huespedes.guardarEstancia(hotel.codigo(),2,"Habitación 2","#FBBF24",h.checkIn(),h.proximoCheckOut(),
                    List.of(new DatosHuesped("Documento invalido",TipoDocumento.DNI,"123")));
            return null;
        }));
        assertEquals(EstadoHabitacion.DISPONIBLE,habitaciones.habitaciones(hotel.codigo()).get(1).estado());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.estancias e JOIN dbo.habitaciones h ON h.id=e.habitacion_id "
                + "WHERE h.hotel_codigo=?",Integer.class,hotel.codigo()));
        assertTrue(ingresos.listar(hotel.codigo(),entrada.minusDays(1),entrada.plusDays(1)).isEmpty());
    }

    @Test void distintasAreasAdmitenLaMismaEntradaYRechazanUnaUbicacionDoble() {
        huespedes.guardarEstancia(hotel.codigo(),null,"Jacuzzi","#000000",entrada,entrada.plusHours(1),List.of(persona()));
        huespedes.guardarEstancia(hotel.codigo(),null,"Sauna","#000000",entrada,entrada.plusHours(1),List.of(persona()));
        assertEquals(2,huespedes.buscarPorHotel(hotel.codigo(),FiltroHuespedes.vacio()).size());
        long cuarto=jdbc.queryForObject("SELECT id FROM dbo.habitaciones WHERE hotel_codigo=? AND numero=1",Long.class,hotel.codigo());
        long area=jdbc.queryForObject("SELECT id FROM dbo.areas_comunes WHERE hotel_codigo=? AND codigo='jacuzzi'",Long.class,hotel.codigo());
        assertThrows(DataAccessException.class,()->jdbc.update(
                "INSERT dbo.estancias(habitacion_id,area_comun_id,check_in,check_out) VALUES(?,?,?,?)",cuarto,area,entrada,entrada.plusHours(1)));
    }

    @Test void pagoConcurrenteConsolidaUnaSolaEstancia() throws Exception {
        Habitacion h=ocupar(3,false);
        ExecutorService ejecutor=Executors.newFixedThreadPool(6);
        CountDownLatch salida=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futuros=new ArrayList<>();
            for(int i=0;i<6;i++) futuros.add(ejecutor.submit(()->{
                salida.await();ingresos.guardarPago(hotel.codigo(),3,h.checkIn(),entrada.plusMinutes(1),h.montoTotal());return true;
            }));
            salida.countDown();
            for(var futuro:futuros) assertTrue(futuro.get(20,TimeUnit.SECONDS));
        } finally { ejecutor.shutdownNow(); }
        var pagos=ingresos.listar(hotel.codigo(),entrada.minusDays(1),entrada.plusDays(1));
        assertEquals(1,pagos.size());assertEquals(h.montoTotal(),pagos.get(0).monto());
        ingresos.eliminarPago(hotel.codigo(),3,h.checkIn());
        assertTrue(ingresos.listar(hotel.codigo(),entrada.minusDays(1),entrada.plusDays(1)).isEmpty());
    }

    @Test void inventarioTarifasExtensionYPagoSobrevivenALaReconstruccion() {
        assertEquals(ResultadoOperacion.ACTUALIZADA,habitaciones.configurarCantidad(hotel.codigo(),14));
        assertEquals(ResultadoOperacion.ACTUALIZADA,habitaciones.guardarSeccion(hotel.codigo(),"seccion-1","#FBBF24","Amarillo",new BigDecimal("75.25"),new BigDecimal("100.00")));
        ocupar(1,false);
        assertEquals(ResultadoOperacion.ACTUALIZADA,habitaciones.actualizarHabitacion(hotel.codigo(),1,"seccion-1",
                TipoServicio.SEIS_HORAS,EstadoHabitacion.OCUPADA,false,2,new BigDecimal("20.00")));
        assertEquals(ResultadoOperacion.ACTUALIZADA,habitaciones.actualizarHabitacion(hotel.codigo(),1,"seccion-1",
                TipoServicio.SEIS_HORAS,EstadoHabitacion.OCUPADA,true));
        try(var nuevo=new GestorHabitacionesHoteles(List.of(hotel),reloj,new RepositorioInventarioJdbc(fuente))) {
            assertEquals(14,nuevo.habitaciones(hotel.codigo()).size());
            Habitacion h=nuevo.habitaciones(hotel.codigo()).get(0);
            assertEquals(EstadoHabitacion.OCUPADA,h.estado());assertTrue(h.pagado());
            assertEquals(new BigDecimal("95.25"),h.montoTotal());assertEquals(2,h.horasAdicionales());
            assertEquals(entrada.plusHours(8),h.proximoCheckOut());
            nuevo.guardarSeccion(hotel.codigo(),"seccion-1","#FFAA00","Nueva tarifa",new BigDecimal("99.00"),new BigDecimal("130.00"));
            assertEquals(new BigDecimal("75.25"),nuevo.habitaciones(hotel.codigo()).get(0).precioAplicado());
        }
        assertEquals("#FBBF24",jdbc.queryForObject("SELECT e.color_historico FROM dbo.estancias e JOIN dbo.habitaciones h ON h.id=e.habitacion_id "
                + "WHERE h.hotel_codigo=? AND h.numero=1",String.class,hotel.codigo()));
    }

    @Test void bajaDeHabitacionConHistoriaYNuevaOcupacionEnElMismoMinuto() {
        ocupar(12,true);
        habitaciones.actualizarHabitacion(hotel.codigo(),12,"seccion-1",TipoServicio.SEIS_HORAS,EstadoHabitacion.DISPONIBLE,false);
        assertEquals(ResultadoOperacion.ACTUALIZADA,habitaciones.configurarCantidad(hotel.codigo(),11));
        assertEquals(11,habitaciones.habitaciones(hotel.codigo()).size());
        // El repositorio registra el pago con el reloj real, aunque la estancia use un reloj fijo.
        LocalDateTime fechaPago=LocalDateTime.now(ZoneId.of("America/Lima"));
        assertEquals(1,ingresos.listar(hotel.codigo(),fechaPago.minusDays(1),fechaPago.plusDays(1)).size());
        habitaciones.configurarCantidad(hotel.codigo(),12);
        Habitacion nueva=ocupar(12,false);
        assertTrue(nueva.checkIn().isAfter(entrada));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.estancias e JOIN dbo.habitaciones h ON h.id=e.habitacion_id "
                + "WHERE h.hotel_codigo=? AND h.numero=12",Integer.class,hotel.codigo()));
    }

    @Test void credencialesVersionadasNoSeRestablecenAlReiniciar() {
        var repo=new RepositorioCredencialesHotelJdbc(fuente);
        try(var llaves=new GestorLlavesHoteles(List.of(hotel),"InicialHotel8".toCharArray(),repo)) {
            assertEquals(AutenticadorLlave.Resultado.CONCEDIDO,llaves.autenticar(hotel.codigo(),"InicialHotel8".toCharArray()));
            assertEquals(GestorLlavesHoteles.ResultadoCambio.ACTUALIZADA,llaves.cambiar(hotel.codigo(),1,"NuevaClave9".toCharArray(),"NuevaClave9".toCharArray()));
        }
        try(var llaves=new GestorLlavesHoteles(List.of(hotel),"OtraInicial7".toCharArray(),repo)) {
            assertFalse(llaves.requiereCambio(hotel.codigo()));assertEquals(2,llaves.versionActual(hotel.codigo()));
            assertEquals(AutenticadorLlave.Resultado.DENEGADO,llaves.autenticar(hotel.codigo(),"InicialHotel8".toCharArray()));
            assertEquals(AutenticadorLlave.Resultado.CONCEDIDO,llaves.autenticar(hotel.codigo(),"NuevaClave9".toCharArray()));
            assertEquals(GestorLlavesHoteles.ResultadoCambio.VERSION_DESACTUALIZADA,llaves.cambiar(
                    hotel.codigo(),1,"CambioClave8".toCharArray(),"CambioClave8".toCharArray()));
        }
        assertEquals(16,jdbc.queryForObject("SELECT DATALENGTH(sal) FROM dbo.credenciales_hotel WHERE hotel_codigo=?",Integer.class,hotel.codigo()));
        assertEquals(32,jdbc.queryForObject("SELECT DATALENGTH(hash_clave) FROM dbo.credenciales_hotel WHERE hotel_codigo=?",Integer.class,hotel.codigo()));
    }

    @Test void catalogoPersistidoConservaLosSnapshotsYControlaVersiones() {
        ocupar(4,false);
        assertEquals(GestorServicioHabitacion.ResultadoOperacion.ACTUALIZADO,servicio.agregarCategoria(hotel.codigo(),"Extras"));
        servicio.agregarProducto(hotel.codigo(),"Jugo natural","Extras",new BigDecimal("10.25"));
        ProductoServicio producto=servicio.catalogo(hotel.codigo()).stream().filter(p->p.nombre().equals("Jugo natural")).findFirst().orElseThrow();
        assertEquals(GestorServicioHabitacion.ResultadoOperacion.CREADO,servicio.crearPedido(hotel.codigo(),4,producto.codigo(),2,"Sin hielo"));
        var anterior=catalogo.cargar(hotel.codigo());
        servicio.actualizarProducto(hotel.codigo(),producto.codigo(),"Jugo nuevo","Extras",new BigDecimal("20.00"));
        assertFalse(catalogo.guardar(hotel.codigo(),anterior));
        servicio.eliminarProducto(hotel.codigo(),producto.codigo());
        servicio.eliminarCategoria(hotel.codigo(),"Extras");
        try(var nuevo=new GestorServicioHabitacion(pedidos,reloj,new RepositorioCatalogoServicioJdbc(fuente))) {
            assertFalse(nuevo.catalogo(hotel.codigo()).stream().anyMatch(p->p.codigo().equals(producto.codigo())));
            assertFalse(nuevo.categorias(hotel.codigo()).contains("Extras"));
        }
        var pedido=pedidos.listarPorHotel(hotel.codigo()).get(0);
        assertEquals(new BigDecimal("20.50"),pedido.total());
        assertEquals("Jugo natural",pedido.lineas().get(0).productoNombre());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.pedido_servicio_items WHERE pedido_id=? AND producto_codigo_fk IS NOT NULL",
                Integer.class,pedido.id()));
    }

    @Test void pedidosRequierenEstanciaYRevierteCabeceraAnteItemInvalido() {
        assertThrows(RuntimeException.class,()->pedidos.crear(hotel.codigo(),5,"Sin estancia",entrada,
                List.of(new LineaPedidoServicio("agua-mineral","Agua",new BigDecimal("6.00"),1))));
        ocupar(5,false);
        pedidos.crear(hotel.codigo(),5,"Agua fría",entrada,List.of(new LineaPedidoServicio("agua-mineral","Agua fría",new BigDecimal("6.00"),2)));
        var pedido=pedidos.listarPorHotel(hotel.codigo()).get(0);
        assertTrue(pedidos.cambiarEstado(hotel.codigo(),pedido.id(),EstadoPedidoServicio.PENDIENTE,EstadoPedidoServicio.EN_PREPARACION,entrada.plusMinutes(1)));
        assertFalse(pedidos.cambiarEstado(hotel.codigo(),pedido.id(),EstadoPedidoServicio.PENDIENTE,EstadoPedidoServicio.EN_PREPARACION,entrada.plusMinutes(1)));
        assertThrows(RepositorioServicioHabitacionException.class,()->pedidos.crear(hotel.codigo(),5,"Debe revertirse",entrada,
                List.of(new LineaPedidoServicio("agua-mineral","Agua",BigDecimal.ONE,11))));
        assertEquals(1,pedidos.listarPorHotel(hotel.codigo()).size());
        assertEquals(new BigDecimal("12.00"),pedido.total());
    }

    private ServidorGestera servidorSql() throws Exception {
        try(var configuracion=new ConfiguracionAplicacion("127.0.0.1",0,"AccesoWeb8".toCharArray(),
                "PruebaHotel8".toCharArray(),Duration.ofMinutes(30),false)) {
            ServidorGestera servidor=ServidorGestera.crear(configuracion,fuente);
            servidor.iniciar();
            return servidor;
        }
    }
    private String acceder(ClienteHttpPrueba cliente,String llaveHotel) throws Exception {
        var acceso=cliente.postFormulario("/acceso","llave=AccesoWeb8");
        assertEquals(303,acceso.estado());
        String cookie=acceso.encabezado("Set-Cookie").split(";")[0];
        assertEquals(303,cliente.postFormulario("/hotel/acceso","hotel="+hotel.codigo()+"&llaveHotel="+llaveHotel,cookie).estado());
        return cookie;
    }

    @Test void recorridoHttpCompletoPersisteTrasReiniciarElServidor() throws Exception {
        try(var servidor=servidorSql()) {
            var cliente=new ClienteHttpPrueba(servidor.direccionBase());
            String cookie=acceder(cliente,"PruebaHotel8");
            String formulario="numeroHabitacion=6&idSeccion=seccion-1&tipoServicio=seis_horas&estado=ocupada&pagado=true&modo=normal"
                    + "&cantidadHuespedes=1&huesped.0.nombre=Ana+Torres&huesped.0.tipoDocumento=dni&huesped.0.numeroDocumento=12345678";
            assertEquals(303,cliente.postFormulario("/hotel/habitaciones/actualizar",formulario,cookie).estado());
            assertEquals(303,cliente.postFormulario("/hotel/servicio-habitacion/pedidos",
                    "numeroHabitacion=6&producto=agua-mineral&cantidad=2",cookie).estado());
            var pedido=pedidos.listarPorHotel(hotel.codigo()).get(0);
            assertEquals(303,cliente.postFormulario("/hotel/servicio-habitacion/pedidos/estado",
                    "pedidoId="+pedido.id()+"&estado=en_preparacion",cookie).estado());
            assertEquals(303,cliente.postFormulario("/hotel/servicio-habitacion/pedidos/estado",
                    "pedidoId="+pedido.id()+"&estado=entregado",cookie).estado());
            assertEquals(200,cliente.get("/hotel/reportes",cookie).estado());
            assertTrue(cliente.get("/hotel/huespedes",cookie).cuerpo().contains("Ana Torres"));
            assertEquals(303,cliente.postFormulario("/hotel/llave",
                    "nuevaLlave=NuevaWeb9&confirmacionLlave=NuevaWeb9",cookie).estado());
        }
        try(var servidor=servidorSql()) {
            var cliente=new ClienteHttpPrueba(servidor.direccionBase());
            String cookie=acceder(cliente,"NuevaWeb9");
            assertEquals(200,cliente.get("/hotel/habitaciones",cookie).estado());
            assertTrue(cliente.get("/hotel/huespedes",cookie).cuerpo().contains("Ana Torres"));
            assertEquals(EstadoHabitacion.OCUPADA,habitaciones.habitaciones(hotel.codigo()).get(5).estado());
            assertTrue(habitaciones.habitaciones(hotel.codigo()).get(5).pagado());
            assertEquals(EstadoPedidoServicio.ENTREGADO,pedidos.listarPorHotel(hotel.codigo()).get(0).estado());
        }
    }

    @Test void clavesImpidenCruzarHotelesYDuplicarLaOcupacionActiva() {
        ocupar(1,false);
        long cuarto=jdbc.queryForObject("SELECT id FROM dbo.habitaciones WHERE hotel_codigo=? AND numero=1",Long.class,hotel.codigo());
        long ajena=jdbc.queryForObject("SELECT TOP(1) id FROM dbo.secciones_tarifarias WHERE hotel_codigo=N'brisa-marina' ORDER BY id",Long.class);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE dbo.habitaciones SET seccion_tarifaria_id=? WHERE id=?",ajena,cuarto));
        assertThrows(DataAccessException.class,()->jdbc.update("INSERT dbo.estancias(habitacion_id,tipo_servicio,check_in,check_out,tarifa_aplicada,color_historico) "
                + "VALUES(?,'SEIS_HORAS',?,?,50,'#112233')",cuarto,entrada.plusDays(1),entrada.plusDays(1).plusHours(6)));
    }
}
