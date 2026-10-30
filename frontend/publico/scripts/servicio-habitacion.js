(() => {
    "use strict";

    const producto = document.querySelector("#producto-servicio");
    const cantidad = document.querySelector("#cantidad-servicio");
    const total = document.querySelector("#total-pedido-servicio");
    const tarjetas = [...document.querySelectorAll("[data-pedido-servicio]")];
    const sinResultados = document.querySelector("#sin-pedidos-filtrados");
    const modalCatalogo = document.querySelector("#modal-catalogo-servicio");
    const selectorCatalogo = document.querySelector("[data-producto-catalogo]");
    const nombreCatalogo = document.querySelector("[data-nombre-catalogo]");
    const categoriaCatalogo = document.querySelector("[data-categoria-catalogo]");
    const precioCatalogo = document.querySelector("[data-precio-catalogo]");

    const actualizarTotal = () => {
        if (!producto || !cantidad || !total) return;
        const precio = Number(producto.selectedOptions[0]?.dataset.precio || 0);
        const unidades = Number(cantidad.value || 0);
        const calculado = Number.isFinite(precio) && Number.isFinite(unidades)
            ? precio * unidades : 0;
        total.textContent = `S/ ${Math.max(0, calculado).toFixed(2)}`;
    };

    producto?.addEventListener("change", actualizarTotal);
    cantidad?.addEventListener("input", actualizarTotal);
    actualizarTotal();

    const cargarProductoCatalogo = () => {
        const opcion = selectorCatalogo?.selectedOptions[0];
        if (!opcion) return;
        if (nombreCatalogo) nombreCatalogo.value = opcion.dataset.nombre || "";
        if (categoriaCatalogo) categoriaCatalogo.value = opcion.dataset.categoria || "";
        if (precioCatalogo) precioCatalogo.value = opcion.dataset.precio || "";
    };

    const abrirCatalogo = () => {
        if (!modalCatalogo) return;
        if (selectorCatalogo && producto?.value) selectorCatalogo.value = producto.value;
        cargarProductoCatalogo();
        if (typeof modalCatalogo.showModal === "function" && !modalCatalogo.open) {
            modalCatalogo.showModal();
        }
        nombreCatalogo?.focus();
    };

    const cerrarCatalogo = () => {
        if (typeof modalCatalogo?.close === "function" && modalCatalogo.open) {
            modalCatalogo.close();
        }
    };

    document.querySelector("[data-abrir-catalogo]")
        ?.addEventListener("click", abrirCatalogo);
    document.querySelectorAll("[data-cerrar-catalogo]")
        .forEach((boton) => boton.addEventListener("click", cerrarCatalogo));
    selectorCatalogo?.addEventListener("change", cargarProductoCatalogo);
    modalCatalogo?.addEventListener("click", (evento) => {
        if (evento.target === modalCatalogo) cerrarCatalogo();
    });
    if (modalCatalogo?.classList.contains("modal-catalogo--abierto")) {
        abrirCatalogo();
    }
    document.querySelector("[data-eliminar-producto]")
        ?.addEventListener("click", (evento) => {
            const nombre = selectorCatalogo?.selectedOptions[0]?.dataset.nombre || "este producto";
            if (!window.confirm(`¿Quitar ${nombre} del catálogo?`)) evento.preventDefault();
        });
    document.querySelectorAll("[data-eliminar-categoria]").forEach((boton) => {
        boton.addEventListener("click", (evento) => {
            const categoria = boton.form?.elements.categoria?.value || "esta categoría";
            if (!window.confirm(`¿Quitar ${categoria} del catálogo?`)) evento.preventDefault();
        });
    });

    document.querySelectorAll("[data-filtro-pedido]").forEach((boton) => {
        boton.addEventListener("click", () => {
            const filtro = boton.dataset.filtroPedido;
            let visibles = 0;
            document.querySelectorAll("[data-filtro-pedido]").forEach((actual) => {
                const activo = actual === boton;
                actual.classList.toggle("activo", activo);
                actual.setAttribute("aria-pressed", String(activo));
            });
            tarjetas.forEach((tarjeta) => {
                const estado = tarjeta.dataset.pedidoEstado;
                const visible = filtro === "todos"
                    || estado === filtro
                    || filtro === "finalizados" && ["entregado", "cancelado"].includes(estado);
                tarjeta.hidden = !visible;
                if (visible) visibles += 1;
            });
            if (sinResultados) sinResultados.hidden = visibles !== 0;
        });
    });
})();
