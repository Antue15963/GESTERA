(() => {
    "use strict";

    const lista = document.querySelector("#lista-hoteles");
    const buscador = document.querySelector("#buscar-hotel");
    const limpiar = document.querySelector("#limpiar-busqueda");
    const contador = document.querySelector("#contador-hoteles");
    const sinResultados = document.querySelector("#sin-resultados");

    const normalizar = (texto) => texto
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLocaleLowerCase("es")
        .replace(/\s+/g, " ")
        .trim();

    if (lista && buscador) {
        const tarjetas = [...lista.querySelectorAll(".tarjeta-hotel[data-hotel-codigo][data-hotel-nombre]")];

        const actualizarResultados = () => {
            const consulta = normalizar(buscador.value);
            let visibles = 0;

            tarjetas.forEach((tarjeta) => {
                const coincide = normalizar(tarjeta.dataset.hotelNombre || "").includes(consulta);
                tarjeta.hidden = !coincide;
                if (coincide) visibles += 1;
            });

            if (contador) {
                contador.textContent = `${visibles} ${visibles === 1 ? "hotel registrado" : "hoteles registrados"}`;
            }
            if (sinResultados) sinResultados.hidden = visibles !== 0;
            if (limpiar) limpiar.hidden = consulta.length === 0;
        };

        buscador.addEventListener("input", actualizarResultados);
        limpiar?.addEventListener("click", () => {
            buscador.value = "";
            actualizarResultados();
            buscador.focus();
        });
        actualizarResultados();
    }

    const modal = document.querySelector("#modal-hotel");
    const formulario = modal?.querySelector(".formulario-hotel");
    const codigoHotel = modal?.querySelector("#codigo-hotel");
    const nombreHotel = modal?.querySelector("#nombre-hotel-modal");
    const llaveHotel = modal?.querySelector("#llave-hotel");
    const mensajeError = modal?.querySelector("#mensaje-error-hotel");
    let tarjetaOrigen = null;

    if (!modal || !formulario || !codigoHotel || !nombreHotel || !llaveHotel) return;

    const abrirModal = ({ codigo, nombre, conservarError = false, origen = null }) => {
        codigoHotel.value = codigo;
        nombreHotel.textContent = nombre;
        tarjetaOrigen = origen;

        if (!conservarError) {
            llaveHotel.value = "";
            llaveHotel.removeAttribute("aria-invalid");
            if (mensajeError) mensajeError.textContent = "";
        }

        if (typeof modal.showModal === "function" && !modal.open) modal.showModal();
        else modal.setAttribute("open", "");
        llaveHotel.focus({ preventScroll: true });
    };

    const cerrarModal = () => {
        if (typeof modal.close === "function" && modal.open) modal.close();
        else modal.removeAttribute("open");
    };

    lista?.addEventListener("click", (evento) => {
        const tarjeta = evento.target.closest(".tarjeta-hotel[data-hotel-codigo][data-hotel-nombre]");
        if (!tarjeta || !lista.contains(tarjeta)) return;
        abrirModal({
            codigo: tarjeta.dataset.hotelCodigo,
            nombre: tarjeta.dataset.hotelNombre,
            origen: tarjeta
        });
    });

    modal.querySelectorAll("[data-cerrar-modal]").forEach((boton) => {
        boton.addEventListener("click", cerrarModal);
    });
    modal.addEventListener("click", (evento) => {
        if (evento.target === modal) cerrarModal();
    });
    modal.addEventListener("close", () => {
        tarjetaOrigen?.focus({ preventScroll: true });
        tarjetaOrigen = null;
    });

    const modalAbiertoPorServidor = modal.classList.contains("modal-hotel--abierto");
    const errorServidor = Boolean(
        mensajeError?.textContent.trim()
        && !mensajeError.textContent.includes("{{")
    );
    const codigoServidor = codigoHotel.value.trim();
    const nombreServidor = nombreHotel.textContent.trim();
    const seleccionServidor = Boolean(
        codigoServidor && nombreServidor
        && !codigoServidor.includes("{{")
        && !nombreServidor.includes("{{")
    );

    if (modalAbiertoPorServidor && seleccionServidor) {
        if (errorServidor) llaveHotel.setAttribute("aria-invalid", "true");
        abrirModal({
            codigo: codigoServidor,
            nombre: nombreServidor,
            conservarError: true
        });
    }
})();
