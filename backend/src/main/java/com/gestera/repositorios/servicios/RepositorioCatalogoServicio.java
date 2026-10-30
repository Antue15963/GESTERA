package com.gestera.repositorios.servicios;

import com.gestera.modelo.servicios.ProductoServicio;

import java.util.List;
import java.util.function.Supplier;

public interface RepositorioCatalogoServicio {
    DatosCatalogo cargar(String hotel);
    void inicializar(String hotel,List<ProductoServicio> productos,List<String> categorias);
    boolean guardar(String hotel,DatosCatalogo catalogo);
    <T> T ejecutar(String hotel,Supplier<T> operacion);
    record DatosCatalogo(List<ProductoServicio> productos,List<String> categorias,long version) {
        public DatosCatalogo { productos=List.copyOf(productos); categorias=List.copyOf(categorias); }
    }
}
