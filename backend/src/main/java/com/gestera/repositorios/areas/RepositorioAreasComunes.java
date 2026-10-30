package com.gestera.repositorios.areas;

import com.gestera.modelo.areas.GestorAreasComunes;

import com.gestera.modelo.areas.GestorAreasComunes.AreaComun;
import java.util.List;

public interface RepositorioAreasComunes {
    List<AreaComun> listar(String hotel);
}
