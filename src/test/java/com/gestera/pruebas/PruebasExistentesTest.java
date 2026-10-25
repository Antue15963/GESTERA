package com.gestera.pruebas;

import org.junit.jupiter.api.Test;

/** Mantiene la suite funcional anterior dentro del ciclo normal de JUnit/Maven. */
class PruebasExistentesTest {
    @Test
    void conservaLasReglasYLasPantallas() throws Exception {
        TodasLasPruebas.main(new String[0]);
    }
}
