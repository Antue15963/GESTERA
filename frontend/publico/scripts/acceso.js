(() => {
    "use strict";
    const llave = document.querySelector("#llave");
    const alternar = document.querySelector("#alternar-llave");
    const mensaje = document.querySelector("#mensaje-error");
    if (!llave || !alternar) return;

    alternar.hidden = false;
    const mostrarLlave = (visible) => {
        llave.type = visible ? "text" : "password";
        alternar.setAttribute("aria-pressed", String(visible));
        alternar.setAttribute("aria-label", `${visible ? "Ocultar" : "Mostrar"} llave de acceso`);
        alternar.querySelector("span").textContent = visible ? "Ocultar" : "Mostrar";
    };
    alternar.addEventListener("click", () => {
        mostrarLlave(llave.type === "password");
        llave.focus({ preventScroll: true });
    });

    const hayError = mensaje && !mensaje.classList.contains("oculto")
        && mensaje.textContent.trim() && !mensaje.textContent.includes("{{");
    if (hayError) {
        llave.setAttribute("aria-invalid", "true");
        llave.focus({ preventScroll: true });
    }
})();
