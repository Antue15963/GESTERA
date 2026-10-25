(() => {
    "use strict";

    const formulario = document.querySelector(".formulario-area-comun");
    const area = document.querySelector("#area-comun");
    const opcionesArea = [...document.querySelectorAll("[data-area-comun]")];
    const entrada = document.querySelector("#entrada-area");
    const salida = document.querySelector("#salida-area");
    const opcionesDuracion = [...document.querySelectorAll("input[name='duracionHoras']")];
    const lista = document.querySelector("#lista-huespedes");
    const cantidad = document.querySelector("#cantidad-huespedes");
    const agregar = document.querySelector("#agregar-huesped");
    const mensajeError = document.querySelector("#mensaje-error-area");
    if (!formulario || !area || opcionesArea.length !== 3 || !entrada || !salida
            || opcionesDuracion.length !== 3
            || !lista || !cantidad || !agregar || !mensajeError) return;

    const areasPermitidas = new Set(["jacuzzi", "sauna", "area-juegos"]);
    const duracionesPermitidas = new Set([1, 3, 5]);
    const reglasDocumento = {
        dni: { patron: "[0-9]{8}", longitudMinima: 8, longitudMaxima: 8,
            modo: "numeric", ejemplo: "12345678", expresion: /^[0-9]{8}$/,
            ayuda: "El DNI debe contener exactamente 8 dígitos." },
        carnet_extranjeria: { patron: "[A-Za-z0-9]{9,12}", longitudMinima: 9,
            longitudMaxima: 12, modo: "text", ejemplo: "ABC123456",
            expresion: /^[A-Z0-9]{9,12}$/,
            ayuda: "El carnet admite entre 9 y 12 letras o números." },
        pasaporte: { patron: "[A-Za-z0-9]{6,12}", longitudMinima: 6,
            longitudMaxima: 12, modo: "text", ejemplo: "PE123456",
            expresion: /^[A-Z0-9]{6,12}$/,
            ayuda: "El pasaporte admite entre 6 y 12 letras o números." }
    };

    const mostrarError = (texto, control) => {
        mensajeError.textContent = texto;
        mensajeError.classList.add("mensaje-error--visible");
        if (control) {
            control.setAttribute("aria-invalid", "true");
            control.focus();
        }
    };

    const limpiarError = () => {
        mensajeError.textContent = "";
        mensajeError.classList.remove("mensaje-error--visible");
        formulario.querySelectorAll("[aria-invalid='true']")
            .forEach((control) => control.removeAttribute("aria-invalid"));
    };

    const seleccionarArea = (codigo) => {
        if (!areasPermitidas.has(codigo)) return;
        area.value = codigo;
        opcionesArea.forEach((opcion) => {
            const seleccionada = opcion.dataset.areaComun === codigo;
            opcion.setAttribute("aria-checked", String(seleccionada));
            opcion.classList.toggle("tarjeta-area-comun--seleccionada", seleccionada);
            const marca = opcion.querySelector(".tarjeta-area-comun__marca");
            if (marca) marca.textContent = seleccionada ? "Seleccionada" : "Seleccionar";
        });
        limpiarError();
    };

    opcionesArea.forEach((opcion) => {
        opcion.addEventListener("click", () => seleccionarArea(opcion.dataset.areaComun));
    });
    if (areasPermitidas.has(area.value)) seleccionarArea(area.value);

    const dosDigitos = (numero) => String(numero).padStart(2, "0");
    const fechaPeru = (valor) => {
        const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(valor);
        if (!partes) return null;
        const numeros = partes.slice(1).map(Number);
        const [anio, mes, dia, hora, minuto] = numeros;
        const fecha = new Date(Date.UTC(anio, mes - 1, dia, hora, minuto));
        if (fecha.getUTCFullYear() !== anio || fecha.getUTCMonth() !== mes - 1
                || fecha.getUTCDate() !== dia || fecha.getUTCHours() !== hora
                || fecha.getUTCMinutes() !== minuto) return null;
        return fecha;
    };
    const fechaFormulario = (fecha) => `${fecha.getUTCFullYear()}`
        + `-${dosDigitos(fecha.getUTCMonth() + 1)}-${dosDigitos(fecha.getUTCDate())}`
        + `T${dosDigitos(fecha.getUTCHours())}:${dosDigitos(fecha.getUTCMinutes())}`;
    const duracionSeleccionada = () => {
        const seleccionada = opcionesDuracion.find((opcion) => opcion.checked);
        return seleccionada ? Number(seleccionada.value) : 0;
    };
    const actualizarSalida = () => {
        const inicio = fechaPeru(entrada.value);
        const horas = duracionSeleccionada();
        if (!inicio || !duracionesPermitidas.has(horas)) {
            salida.value = "";
            return;
        }
        const fin = new Date(inicio.getTime() + horas * 60 * 60 * 1000);
        salida.value = fechaFormulario(fin);
    };

    const actualizarFilas = () => {
        const filas = [...lista.querySelectorAll("[data-fila-huesped]")];
        filas.forEach((fila, indice) => {
            fila.querySelector("h3").textContent = `Visitante ${indice + 1}`;
            const quitar = fila.querySelector("[data-quitar-huesped]");
            quitar.disabled = filas.length === 1;
            quitar.setAttribute("aria-label", `Quitar visitante ${indice + 1}`);
            fila.querySelectorAll("input, select").forEach((control) => {
                const parte = control.name.split(".").pop();
                control.name = `huesped.${indice}.${parte}`;
            });
        });
        cantidad.value = String(filas.length);
        agregar.disabled = filas.length >= 8;
    };

    const configurarDocumento = (fila) => {
        const tipo = fila.querySelector("select[name$='.tipoDocumento']");
        const documento = fila.querySelector("input[name$='.numeroDocumento']");
        const regla = reglasDocumento[tipo.value];
        if (!regla) return;
        documento.pattern = regla.patron;
        documento.minLength = regla.longitudMinima;
        documento.maxLength = regla.longitudMaxima;
        documento.inputMode = regla.modo;
        documento.placeholder = regla.ejemplo;
        documento.value = documento.value.toUpperCase().replace(/[^A-Z0-9]/g, "")
            .slice(0, regla.longitudMaxima);
    };

    const enlazarFila = (fila) => {
        const tipo = fila.querySelector("select[name$='.tipoDocumento']");
        const documento = fila.querySelector("input[name$='.numeroDocumento']");
        tipo.addEventListener("change", () => {
            configurarDocumento(fila);
            limpiarError();
        });
        documento.addEventListener("input", () => {
            configurarDocumento(fila);
            limpiarError();
        });
        fila.querySelector("[data-quitar-huesped]").addEventListener("click", () => {
            if (lista.querySelectorAll("[data-fila-huesped]").length <= 1) return;
            fila.remove();
            actualizarFilas();
            limpiarError();
        });
    };

    lista.querySelectorAll("[data-fila-huesped]").forEach(enlazarFila);
    actualizarFilas();

    agregar.addEventListener("click", () => {
        const filas = lista.querySelectorAll("[data-fila-huesped]");
        if (filas.length >= 8) return;
        const nueva = filas[0].cloneNode(true);
        nueva.querySelectorAll("input").forEach((control) => { control.value = ""; });
        nueva.querySelector("select").value = "dni";
        lista.append(nueva);
        enlazarFila(nueva);
        configurarDocumento(nueva);
        actualizarFilas();
        nueva.querySelector("input").focus();
        limpiarError();
    });

    entrada.addEventListener("input", () => {
        actualizarSalida();
        limpiarError();
    });
    opcionesDuracion.forEach((opcion) => {
        opcion.addEventListener("change", () => {
            actualizarSalida();
            limpiarError();
        });
    });
    actualizarSalida();

    formulario.addEventListener("submit", (evento) => {
        limpiarError();
        if (!areasPermitidas.has(area.value)) {
            evento.preventDefault();
            mostrarError("Selecciona una de las tres áreas comunes.", opcionesArea[0]);
            return;
        }
        const horas = duracionSeleccionada();
        if (!duracionesPermitidas.has(horas)) {
            evento.preventDefault();
            mostrarError("Selecciona una duración de 1, 3 o 5 horas.", opcionesDuracion[0]);
            return;
        }
        const inicio = fechaPeru(entrada.value);
        if (!inicio) {
            evento.preventDefault();
            mostrarError("Selecciona una hora de entrada válida.", entrada);
            return;
        }
        actualizarSalida();
        if (!salida.value) {
            evento.preventDefault();
            mostrarError("No fue posible calcular la hora de salida.", entrada);
            return;
        }

        const documentos = new Set();
        for (const fila of lista.querySelectorAll("[data-fila-huesped]")) {
            const nombre = fila.querySelector("input[name$='.nombre']");
            const tipo = fila.querySelector("select[name$='.tipoDocumento']");
            const documento = fila.querySelector("input[name$='.numeroDocumento']");
            const valorDocumento = documento.value.trim().toUpperCase();
            const regla = reglasDocumento[tipo.value];
            if (!/^[\p{L}][\p{L} .'-]{1,99}$/u.test(nombre.value.trim())) {
                evento.preventDefault();
                mostrarError("Completa los nombres y apellidos de cada visitante.", nombre);
                return;
            }
            if (!regla || !regla.expresion.test(valorDocumento)) {
                evento.preventDefault();
                mostrarError(regla?.ayuda || "Selecciona un documento válido.", documento);
                return;
            }
            const clave = `${tipo.value}:${valorDocumento}`;
            if (documentos.has(clave)) {
                evento.preventDefault();
                mostrarError("No puedes registrar dos veces el mismo documento.", documento);
                return;
            }
            documentos.add(clave);
            documento.value = valorDocumento;
        }
    });
})();
