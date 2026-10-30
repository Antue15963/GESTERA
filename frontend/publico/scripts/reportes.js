(() => {
    "use strict";

    const periodo = document.querySelector("[data-periodo-reporte]");
    const fechas = [...document.querySelectorAll("[data-fecha-personalizada]")];
    const formulario = document.querySelector("[data-formulario-reportes]");
    const botonGenerar = document.querySelector("[data-generar-reporte]");
    const desde = document.querySelector("#desde-reporte");
    const hasta = document.querySelector("#hasta-reporte");

    const actualizarFechas = () => {
        const personalizadas = periodo?.value === "personalizado";
        fechas.forEach((campo) => {
            campo.disabled = !personalizadas;
            campo.required = personalizadas;
            campo.closest(".campos-personalizados-reporte")
                ?.classList.toggle("campos-personalizados-reporte--inactivo", !personalizadas);
        });
    };

    periodo?.addEventListener("change", actualizarFechas);
    desde?.addEventListener("change", () => {
        if (hasta) hasta.min = desde.value;
    });
    hasta?.addEventListener("change", () => {
        if (desde) desde.max = hasta.value;
    });
    formulario?.addEventListener("submit", () => {
        if (!formulario.checkValidity() || !botonGenerar) return;
        botonGenerar.disabled = true;
        botonGenerar.textContent = "Actualizando…";
    });
    actualizarFechas();
    if (desde && hasta) {
        hasta.min = desde.value;
        desde.max = hasta.value;
    }
})();
