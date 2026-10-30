package com.gestera.repositorios.habitaciones;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles;

import com.gestera.modelo.habitaciones.GestorHabitacionesHoteles.ResumenInventario;
import java.util.function.Supplier;

public interface RepositorioInventario {
    ResumenInventario cargar(String hotel);
    void guardar(String hotel, ResumenInventario inventario);
    <T> T ejecutar(String hotel, Supplier<T> operacion);
}
