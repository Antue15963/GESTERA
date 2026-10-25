package com.gestera.areas;

import com.gestera.areas.GestorAreasComunes.AreaComun;
import java.util.List;

public interface RepositorioAreasComunes {
    List<AreaComun> listar(String hotel);
}
