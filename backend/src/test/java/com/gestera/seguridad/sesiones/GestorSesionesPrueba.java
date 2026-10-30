package com.gestera.seguridad.sesiones;

import com.gestera.pruebas.Aserciones;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Pruebas deterministas del ciclo de vida de las sesiones. */
public final class GestorSesionesPrueba {
    private GestorSesionesPrueba() {
    }

    public static int ejecutar() {
        rechazaDuracionesInvalidas();
        creaUnTokenOpacoValido();
        creaTokensDiferentes();
        rechazaTokensInvalidos();
        sesionNuevaNoTieneHotel();
        guardaYReemplazaElHotelAutorizado();
        guardaLaVersionYRevocaLaAutorizacion();
        mantieneLaSeleccionSeparadaPorSesion();
        rechazaAutorizacionesSinSesionValida();
        venceEnElInstanteExacto();
        cierraSesionesIndividualesYElGestorCompleto();
        return 11;
    }

    private static void rechazaDuracionesInvalidas() {
        for (Duration duracion : new Duration[] {Duration.ZERO, Duration.ofSeconds(-1)}) {
            Aserciones.lanza(
                    IllegalArgumentException.class,
                    () -> new GestorSesiones(duracion),
                    "La duracion de una sesion debe ser positiva");
        }
        Aserciones.lanza(
                IllegalArgumentException.class,
                () -> new GestorSesiones(null),
                "La duracion nula debe rechazarse");
    }

    private static void creaUnTokenOpacoValido() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            String token = sesiones.crear();
            Aserciones.iguales(
                    43, token.length(), "Un token de 32 bytes en Base64 URL debe medir 43 caracteres");
            Aserciones.verdadero(
                    token.matches("[A-Za-z0-9_-]{43}"),
                    "El token debe usar Base64 apto para URL y sin relleno");
            Aserciones.verdadero(sesiones.esValida(token), "Una sesion nueva debe ser valida");
            Aserciones.iguales(1, sesiones.cantidadActivas(), "Debe registrar la sesion creada");
        }
    }

    private static void creaTokensDiferentes() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            String primero = sesiones.crear();
            String segundo = sesiones.crear();
            Aserciones.falso(primero.equals(segundo),
                    "Dos creaciones deben producir tokens diferentes");
            Aserciones.iguales(2, sesiones.cantidadActivas(),
                    "Ambas sesiones deben permanecer activas");
        }
    }

    private static void rechazaTokensInvalidos() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            Aserciones.falso(sesiones.esValida(null), "Un token nulo no es valido");
            Aserciones.falso(sesiones.esValida("   "), "Un token vacio no es valido");
            Aserciones.falso(sesiones.esValida("desconocido"), "Un token desconocido no es valido");
            Aserciones.falso(
                    sesiones.esValida("x".repeat(129)),
                    "Debe rechazar tokens anormalmente largos antes de consultarlos");
        }
    }

    private static void sesionNuevaNoTieneHotel() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            String token = sesiones.crear();
            Aserciones.verdadero(
                    sesiones.hotelAutorizado(token).isEmpty(),
                    "Una sesion nueva no debe entrar a ningun hotel automaticamente");
        }
    }

    private static void guardaYReemplazaElHotelAutorizado() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            String token = sesiones.crear();
            Aserciones.verdadero(
                    sesiones.autorizarHotel(token, "sol-pucusana"),
                    "Una sesion valida debe poder seleccionar un hotel");
            Aserciones.iguales(
                    "sol-pucusana",
                    sesiones.hotelAutorizado(token).orElse(null),
                    "Debe guardar el codigo seleccionado en la sesion");
            Aserciones.verdadero(
                    sesiones.autorizarHotel(token, "costa-serena"),
                    "La misma sesion debe poder cambiar de hotel");
            Aserciones.iguales(
                    "costa-serena",
                    sesiones.hotelAutorizado(token).orElse(null),
                    "La nueva seleccion debe reemplazar la anterior");
        }
    }

    private static void mantieneLaSeleccionSeparadaPorSesion() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            String primero = sesiones.crear();
            String segundo = sesiones.crear();
            sesiones.autorizarHotel(primero, "sol-pucusana");
            sesiones.autorizarHotel(segundo, "valle-dorado");
            Aserciones.iguales(
                    "sol-pucusana",
                    sesiones.hotelAutorizado(primero).orElse(null),
                    "La primera sesion debe conservar su propio hotel");
            Aserciones.iguales(
                    "valle-dorado",
                    sesiones.hotelAutorizado(segundo).orElse(null),
                    "La segunda sesion no debe compartir la seleccion");
        }
    }

    private static void guardaLaVersionYRevocaLaAutorizacion() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            String token = sesiones.crear();
            Aserciones.verdadero(
                    sesiones.autorizarHotel(token, "sol-pucusana", 7),
                    "Una sesion valida debe guardar la version autenticada");
            GestorSesiones.AutorizacionHotel autorizacion =
                    sesiones.autorizacionHotel(token).orElseThrow();
            Aserciones.iguales("sol-pucusana", autorizacion.codigoHotel(),
                    "La autorizacion debe conservar el codigo del hotel");
            Aserciones.iguales(7L, autorizacion.versionLlave(),
                    "La autorizacion debe conservar la version de la llave");
            Aserciones.iguales(
                    "sol-pucusana",
                    sesiones.hotelAutorizado(token).orElse(null),
                    "El metodo compatible debe seguir entregando el codigo");
            Aserciones.verdadero(sesiones.revocarHotel(token),
                    "Debe poder revocar solo el acceso al hotel");
            Aserciones.verdadero(sesiones.esValida(token),
                    "Revocar el hotel no debe cerrar la sesion global");
            Aserciones.verdadero(sesiones.autorizacionHotel(token).isEmpty(),
                    "La autorizacion revocada debe desaparecer");
            Aserciones.falso(
                    sesiones.autorizarHotel(token, "sol-pucusana", -1),
                    "No debe guardar una version negativa");
            Aserciones.falso(sesiones.revocarHotel("token-desconocido"),
                    "No debe afirmar que revoco una sesion desconocida");
        }
    }

    private static void rechazaAutorizacionesSinSesionValida() {
        try (GestorSesiones sesiones = gestor(Duration.ofMinutes(5))) {
            String token = sesiones.crear();
            for (String tokenInvalido : new String[] {
                null, "", "   ", "desconocido", "x".repeat(129)
            }) {
                Aserciones.falso(
                        sesiones.autorizarHotel(tokenInvalido, "sol-pucusana"),
                        "No debe autorizar un hotel sin una sesion valida");
                Aserciones.verdadero(
                        sesiones.hotelAutorizado(tokenInvalido).isEmpty(),
                        "Un token invalido no debe devolver un hotel");
            }
            for (String codigoInvalido : new String[] {null, "", "   "}) {
                Aserciones.falso(
                        sesiones.autorizarHotel(token, codigoInvalido),
                        "No debe almacenar un codigo de hotel vacio");
            }
            Aserciones.verdadero(
                    sesiones.hotelAutorizado(token).isEmpty(),
                    "Los intentos invalidos no deben alterar una sesion valida");
        }
    }

    private static void venceEnElInstanteExacto() {
        RelojMutable reloj = new RelojMutable(Instant.parse("2026-08-27T12:00:00Z"));
        try (GestorSesiones sesiones = new GestorSesiones(
                Duration.ofSeconds(10), reloj, new AleatorioSecuencial())) {
            String token = sesiones.crear();
            Aserciones.verdadero(
                    sesiones.autorizarHotel(token, "sol-pucusana"),
                    "Debe preparar la seleccion para comprobar su vencimiento");
            reloj.avanzar(Duration.ofSeconds(9));
            Aserciones.verdadero(sesiones.esValida(token), "Debe seguir valida antes del limite");
            Aserciones.iguales(
                    "sol-pucusana",
                    sesiones.hotelAutorizado(token).orElse(null),
                    "La seleccion debe existir mientras la sesion este vigente");
            reloj.avanzar(Duration.ofSeconds(1));
            Aserciones.falso(sesiones.esValida(token),
                    "Debe vencer exactamente al llegar al limite");
            Aserciones.verdadero(
                    sesiones.hotelAutorizado(token).isEmpty(),
                    "Al vencer la sesion tambien debe desaparecer el hotel autorizado");
            Aserciones.iguales(0, sesiones.cantidadActivas(),
                    "La sesion vencida debe eliminarse");
        }
    }

    private static void cierraSesionesIndividualesYElGestorCompleto() {
        GestorSesiones sesiones = gestor(Duration.ofMinutes(5));
        String primero = sesiones.crear();
        String segundo = sesiones.crear();
        sesiones.autorizarHotel(primero, "sol-pucusana");
        sesiones.autorizarHotel(segundo, "brisa-marina");
        sesiones.cerrar(primero);
        sesiones.cerrar(null);
        Aserciones.falso(sesiones.esValida(primero), "La sesion cerrada debe invalidarse");
        Aserciones.verdadero(
                sesiones.hotelAutorizado(primero).isEmpty(),
                "Cerrar una sesion debe borrar su seleccion de hotel");
        Aserciones.verdadero(sesiones.esValida(segundo),
                "Cerrar una sesion no debe afectar otra");
        sesiones.close();
        sesiones.close();
        Aserciones.iguales(0, sesiones.cantidadActivas(),
                "Cerrar el gestor debe vaciar las sesiones");
        Aserciones.verdadero(
                sesiones.hotelAutorizado(segundo).isEmpty(),
                "Cerrar el gestor debe borrar todas las selecciones de hotel");
    }

    private static GestorSesiones gestor(Duration duracion) {
        return new GestorSesiones(
                duracion,
                new RelojMutable(Instant.parse("2026-08-27T12:00:00Z")),
                new AleatorioSecuencial());
    }

    private static final class RelojMutable extends Clock {
        private Instant instante;
        private final ZoneId zona;

        private RelojMutable(Instant instante) {
            this(instante, ZoneOffset.UTC);
        }

        private RelojMutable(Instant instante, ZoneId zona) {
            this.instante = instante;
            this.zona = zona;
        }

        private void avanzar(Duration duracion) {
            instante = instante.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId nuevaZona) {
            return new RelojMutable(instante, nuevaZona);
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }

    private static final class AleatorioSecuencial extends SecureRandom {
        private static final long serialVersionUID = 1L;
        private int secuencia = 1;

        @Override
        public void nextBytes(byte[] bytes) {
            int inicio = secuencia++;
            for (int indice = 0; indice < bytes.length; indice++) {
                bytes[indice] = (byte) (inicio + indice);
            }
        }
    }
}
