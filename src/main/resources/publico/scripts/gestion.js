(() => {
    "use strict";

    const disparador = document.querySelector("#abrir-cambio-llave");
    const modal = document.querySelector("#modal-cambio-llave");
    const formulario = document.querySelector(".formulario-cambio-llave");
    const nuevaLlave = formulario?.querySelector("#nueva-llave");
    const confirmacion = formulario?.querySelector("#confirmacion-llave");
    const mensajeError = formulario?.querySelector("#mensaje-error-cambio-llave");

    if (!formulario || !nuevaLlave || !confirmacion || !mensajeError) return;

    const limpiarError = () => {
        mensajeError.textContent = "";
        nuevaLlave.removeAttribute("aria-invalid");
        confirmacion.removeAttribute("aria-invalid");
    };

    const mostrarError = (mensaje, campo) => {
        mensajeError.textContent = mensaje;
        campo.setAttribute("aria-invalid", "true");
        campo.focus({ preventScroll: true });
    };

    const abrirModal = ({ conservarError = false } = {}) => {
        nuevaLlave.value = "";
        confirmacion.value = "";
        if (!conservarError) limpiarError();

        if (typeof modal.showModal === "function" && !modal.open) modal.showModal();
        else modal.setAttribute("open", "");
        nuevaLlave.focus({ preventScroll: true });
    };

    const cerrarModal = () => {
        if (typeof modal.close === "function" && modal.open) modal.close();
        else modal.removeAttribute("open");
    };

    if (disparador && modal) {
        disparador.addEventListener("click", () => abrirModal());
        modal.querySelectorAll("[data-cerrar-cambio-llave]").forEach((boton) => {
            boton.addEventListener("click", cerrarModal);
        });
        modal.addEventListener("click", (evento) => {
            if (evento.target === modal) cerrarModal();
        });
        modal.addEventListener("close", () => disparador.focus({ preventScroll: true }));
    }

    [nuevaLlave, confirmacion].forEach((campo) => {
        campo.addEventListener("input", limpiarError);
    });

    formulario.addEventListener("submit", (evento) => {
        limpiarError();
        const valor = nuevaLlave.value;

        if (valor.length < 8 || valor.length > 15) {
            evento.preventDefault();
            mostrarError("La nueva llave debe tener entre 8 y 15 caracteres.", nuevaLlave);
            return;
        }
        if (!/[A-ZÁÉÍÓÚÜÑ]/.test(valor)) {
            evento.preventDefault();
            mostrarError("La nueva llave debe incluir al menos una letra mayúscula.", nuevaLlave);
            return;
        }
        if (!/[0-9]/.test(valor)) {
            evento.preventDefault();
            mostrarError("La nueva llave debe incluir al menos un número.", nuevaLlave);
            return;
        }
        if (valor !== confirmacion.value) {
            evento.preventDefault();
            mostrarError("Las llaves ingresadas no coinciden.", confirmacion);
        }
    });

    const abiertoPorServidor = modal?.classList.contains("modal-cambio-llave--abierto");
    const errorServidor = mensajeError.textContent.trim()
        && !mensajeError.textContent.includes("{{");

    if (abiertoPorServidor && modal) {
        if (errorServidor) {
            nuevaLlave.setAttribute("aria-invalid", "true");
            confirmacion.setAttribute("aria-invalid", "true");
        }
        abrirModal({ conservarError: true });
    }
})();
