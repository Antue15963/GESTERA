(() => {
    "use strict";

    const nombreColorPredeterminado = (hexadecimal) => {
        const valor = String(hexadecimal || "").toUpperCase();
        return /^#[0-9A-F]{6}$/.test(valor) ? `Color ${valor}` : "Color personalizado";
    };

    const precioValido = (valor) => {
        if (!/^\d+(?:\.\d{1,2})?$/.test(valor)) return false;
        const numero = Number(valor);
        return Number.isFinite(numero) && numero >= 1 && numero <= 10000;
    };

    const normalizarNombreComparado = (valor) => String(valor || "")
        .trim()
        .replace(/\s+/g, " ")
        .toLocaleLowerCase("es");

    const formularioGeneral = document.querySelector("[data-formulario-configuracion-general]");
    const cantidadHabitaciones = document.querySelector("#cantidad-habitaciones");
    const botonGuardar = document.querySelector("#guardar-configuracion-general");
    const botonGuardarCantidad = document.querySelector("[data-guardar-cantidad]");
    const estadoCambios = document.querySelector("#estado-cambios-configuracion");
    const mensajeGeneral = document.querySelector("#mensaje-error-configuracion");
    const contador = document.querySelector("#contador-secciones");
    const tarjetas = [...document.querySelectorAll("[data-seccion-tarifaria]")];
    const tarjetaNueva = tarjetas.find((tarjeta) => tarjeta.dataset.esNueva === "true");

    if (!(formularioGeneral instanceof HTMLFormElement)
        || !(cantidadHabitaciones instanceof HTMLInputElement)) return;

    const configuraciones = [];
    let enviando = false;

    const limpiarErrorGeneral = () => {
        if (mensajeGeneral) mensajeGeneral.textContent = "";
        cantidadHabitaciones.removeAttribute("aria-invalid");
    };

    const marcarCambios = () => {
        limpiarErrorGeneral();
        if (botonGuardar) botonGuardar.disabled = false;
        if (estadoCambios) estadoCambios.textContent = "Hay cambios pendientes de guardar.";
    };

    tarjetas.forEach((tarjeta, indice) => {
        const contenedor = tarjeta.querySelector("[data-formulario-seccion]");
        if (!contenedor) return;

        const color = contenedor.querySelector('[data-campo-seccion="color"]');
        const nombre = contenedor.querySelector('[data-campo-seccion="nombre"]');
        const precio6Horas = contenedor.querySelector('[data-campo-seccion="precio6Horas"]');
        const precioDiaCompleto = contenedor.querySelector('[data-campo-seccion="precioDiaCompleto"]');
        const accion = contenedor.querySelector("[data-accion-seccion]");
        const muestra = tarjeta.querySelector("[data-muestra-color]");
        const nombreVisible = tarjeta.querySelector("[data-nombre-visible]");
        const error = tarjeta.querySelector("[data-error-seccion]");
        const botonEliminar = tarjeta.querySelector("[data-eliminar-seccion]");
        const botonDeshacer = tarjeta.querySelector("[data-deshacer-eliminacion]");
        const botonGuardarSeccion = tarjeta.querySelector("[data-guardar-seccion]");
        const esNueva = tarjeta.dataset.esNueva === "true";

        if (!(color instanceof HTMLInputElement) || !(nombre instanceof HTMLInputElement)
            || !(precio6Horas instanceof HTMLInputElement)
            || !(precioDiaCompleto instanceof HTMLInputElement)) return;

        const identificador = tarjeta.dataset.seccionId || `nueva-${indice}`;
        [color, nombre, precio6Horas, precioDiaCompleto].forEach((campo, campoIndice) => {
            if (!campo.id) campo.id = `seccion-${identificador}-campo-${campoIndice}`;
            const etiqueta = campo.closest(".campo")?.querySelector("label");
            if (etiqueta && !etiqueta.htmlFor) etiqueta.htmlFor = campo.id;
        });

        const limpiarError = () => {
            if (error) error.textContent = "";
            contenedor.querySelectorAll("[aria-invalid='true']").forEach((campo) => {
                campo.removeAttribute("aria-invalid");
            });
        };

        const mostrarError = (mensaje, campo) => {
            if (error) error.textContent = mensaje;
            campo.setAttribute("aria-invalid", "true");
            campo.focus();
            return false;
        };

        const actualizarVista = () => {
            const nombreMostrado = nombre.value.trim() || nombreColorPredeterminado(color.value);
            tarjeta.style.setProperty("--color-seccion", color.value);
            if (muestra) muestra.style.setProperty("--color-seccion", color.value);
            if (nombreVisible) nombreVisible.textContent = nombreMostrado;
            if (botonGuardarSeccion instanceof HTMLButtonElement) {
                botonGuardarSeccion.setAttribute(
                    "aria-label", esNueva
                        ? "Añadir nueva sección"
                        : `Guardar cambios de ${nombreMostrado}`);
            }
        };

        const nuevaSolicitada = () => esNueva && Boolean(
            nombre.value.trim() || precio6Horas.value.trim() || precioDiaCompleto.value.trim());
        const estaIncluida = () => esNueva
            ? nuevaSolicitada()
            : accion?.value !== "eliminar";
        const nombreEfectivo = () => nombre.value.trim() || nombreColorPredeterminado(color.value);
        const colorPersistido = color.value.trim().toLowerCase();
        const nombrePersistido = normalizarNombreComparado(nombreEfectivo());

        const validar = (forzarInclusion = false) => {
            limpiarError();
            if (!forzarInclusion && !estaIncluida()) return true;
            if (forzarInclusion && esNueva && !nuevaSolicitada()) {
                return mostrarError(
                    "Completa ambos precios para añadir la nueva sección.",
                    precio6Horas);
            }
            if (!/^#[0-9a-f]{6}$/i.test(color.value)) {
                return mostrarError("Selecciona un color válido.", color);
            }
            if (nombre.value.trim().length > 40
                || /[\u0000-\u001F\u007F]/.test(nombre.value)) {
                return mostrarError("El nombre puede tener como máximo 40 caracteres válidos.", nombre);
            }
            if (!precioValido(precio6Horas.value)) {
                return mostrarError(
                    "El precio de 6 horas debe estar entre S/ 1.00 y S/ 10000.00, con máximo dos decimales.",
                    precio6Horas);
            }
            if (!precioValido(precioDiaCompleto.value)) {
                return mostrarError(
                    "El precio del día completo debe estar entre S/ 1.00 y S/ 10000.00, con máximo dos decimales.",
                    precioDiaCompleto);
            }
            return true;
        };

        color.addEventListener("input", () => {
            limpiarError();
            actualizarVista();
        });
        nombre.addEventListener("input", () => {
            limpiarError();
            actualizarVista();
        });
        [precio6Horas, precioDiaCompleto].forEach((campo) => {
            campo.addEventListener("input", limpiarError);
        });

        if (!esNueva && accion instanceof HTMLInputElement
            && botonEliminar instanceof HTMLButtonElement
            && botonDeshacer instanceof HTMLButtonElement) {
            botonEliminar.addEventListener("click", () => {
                const etiqueta = nombreVisible?.textContent?.trim() || "esta sección";
                if (!window.confirm(
                    `¿Marcar ${etiqueta} para eliminar? La eliminación se aplicará al guardar todos los cambios.`)) {
                    return;
                }
                accion.value = "eliminar";
                [color, nombre, precio6Horas, precioDiaCompleto].forEach((campo) => {
                    campo.disabled = true;
                });
                tarjeta.classList.add("tarjeta-seccion-tarifaria--pendiente-eliminar");
                tarjeta.setAttribute("aria-label", `${etiqueta}, pendiente de eliminación`);
                botonEliminar.hidden = true;
                botonDeshacer.hidden = false;
                if (botonGuardarSeccion instanceof HTMLButtonElement) {
                    botonGuardarSeccion.disabled = true;
                }
                limpiarError();
                marcarCambios();
                actualizarResumenSecciones();
            });
            botonDeshacer.addEventListener("click", () => {
                accion.value = "guardar";
                [color, nombre, precio6Horas, precioDiaCompleto].forEach((campo) => {
                    campo.disabled = false;
                });
                tarjeta.classList.remove("tarjeta-seccion-tarifaria--pendiente-eliminar");
                tarjeta.removeAttribute("aria-label");
                botonEliminar.hidden = false;
                botonDeshacer.hidden = true;
                if (botonGuardarSeccion instanceof HTMLButtonElement) {
                    botonGuardarSeccion.disabled = false;
                }
                marcarCambios();
                actualizarResumenSecciones();
            });
        }

        configuraciones.push({
            color,
            colorPersistido,
            idSeccion: tarjeta.dataset.seccionId || "",
            nombre,
            nombreEfectivo,
            nombrePersistido,
            botonGuardarSeccion,
            precio6Horas,
            precioDiaCompleto,
            estaIncluida,
            esNueva,
            actualizarVista,
            mostrarError,
            validar
        });
        actualizarVista();
    });

    const configuracionNueva = configuraciones.find((configuracion) => configuracion.esNueva);
    const seccionesFinales = () => configuraciones.filter(
        (configuracion) => configuracion.estaIncluida());
    const validarAltaSinDuplicados = (configuracion) => {
        const registradas = configuraciones.filter((actual) => !actual.esNueva);
        const colorNormalizado = configuracion.color.value.trim().toLowerCase();
        if (registradas.some((actual) => actual.colorPersistido === colorNormalizado)) {
            return configuracion.mostrarError(
                "Ya existe una sección registrada con ese color.", configuracion.color);
        }
        const nombreNormalizado = normalizarNombreComparado(configuracion.nombreEfectivo());
        if (registradas.some((actual) => actual.nombrePersistido === nombreNormalizado)) {
            return configuracion.mostrarError(
                "Ya existe una sección registrada con ese nombre.", configuracion.nombre);
        }
        return true;
    };

    function actualizarResumenSecciones() {
        const finales = seccionesFinales().length;
        const etiqueta = finales === 1 ? "sección al guardar" : "secciones al guardar";
        if (contador) contador.textContent = `${finales} ${etiqueta}`;

        if (!tarjetaNueva || !configuracionNueva) return;
        const existentesRegistradas = configuraciones.filter(
            (configuracion) => !configuracion.esNueva).length;
        const existentesConservadas = configuraciones.filter(
            (configuracion) => !configuracion.esNueva && configuracion.estaIncluida()).length;
        const limiteSinEspacio = existentesConservadas >= 12;
        const limiteAltaInmediata = existentesRegistradas >= 12;
        tarjetaNueva.classList.toggle("tarjeta-seccion-tarifaria--limite", limiteSinEspacio);
        tarjetaNueva.querySelectorAll("[data-campo-seccion]").forEach((control) => {
            control.disabled = limiteSinEspacio;
        });
        if (configuracionNueva.botonGuardarSeccion instanceof HTMLButtonElement) {
            configuracionNueva.botonGuardarSeccion.disabled = limiteAltaInmediata;
        }
        const sobrelinea = tarjetaNueva.querySelector(".sobrelinea");
        const titulo = tarjetaNueva.querySelector("[data-nombre-visible]");
        const aviso = tarjetaNueva.querySelector("[data-limite-secciones]");
        const descripcionAlta = tarjetaNueva.querySelector("[data-descripcion-alta]");
        if (sobrelinea) sobrelinea.textContent = limiteSinEspacio
            ? "LÍMITE DE SECCIONES"
            : "NUEVA SECCIÓN";
        if (titulo && limiteSinEspacio) titulo.textContent = "Máximo alcanzado";
        else if (!limiteSinEspacio) configuracionNueva.actualizarVista();
        if (aviso) aviso.hidden = !limiteSinEspacio;
        if (descripcionAlta) {
            descripcionAlta.textContent = limiteSinEspacio
                ? "El alta inmediata se habilitará cuando haya menos de 12 secciones registradas."
                : limiteAltaInmediata
                    ? "Guarda primero la eliminación o usa Guardar todos para reemplazarla."
                    : "Al añadirla, la nueva sección se guardará de inmediato y aparecerá como registrada.";
        }
    }

    formularioGeneral.addEventListener("input", () => {
        marcarCambios();
        actualizarResumenSecciones();
    });

    const cantidadValida = () => /^\d+$/.test(cantidadHabitaciones.value)
        && Number(cantidadHabitaciones.value) >= 1
        && Number(cantidadHabitaciones.value) <= 50;

    const mostrarErrorCantidad = () => {
        if (mensajeGeneral) {
            mensajeGeneral.textContent = "La cantidad debe estar entre 1 y 50 habitaciones.";
        }
        cantidadHabitaciones.setAttribute("aria-invalid", "true");
        cantidadHabitaciones.focus();
    };

    const crearFormularioIndividual = (ruta, campos) => {
        const formulario = document.createElement("form");
        formulario.method = "post";
        formulario.action = ruta;
        formulario.hidden = true;
        Object.entries(campos).forEach(([nombre, valor]) => {
            const campo = document.createElement("input");
            campo.type = "hidden";
            campo.name = nombre;
            campo.value = valor;
            formulario.append(campo);
        });
        document.body.append(formulario);
        return formulario;
    };

    const marcarEnvio = (boton, textoBoton, textoEstado, etiquetaAccesible) => {
        enviando = true;
        boton.textContent = textoBoton;
        boton.disabled = true;
        boton.setAttribute("aria-busy", "true");
        if (etiquetaAccesible) boton.setAttribute("aria-label", etiquetaAccesible);
        if (estadoCambios) estadoCambios.textContent = textoEstado;
    };

    if (botonGuardarCantidad instanceof HTMLButtonElement) {
        botonGuardarCantidad.addEventListener("click", () => {
            if (enviando) return;
            limpiarErrorGeneral();
            if (!cantidadValida()) {
                mostrarErrorCantidad();
                return;
            }
            const ruta = botonGuardarCantidad.dataset.rutaGuardado;
            if (!ruta) return;
            const formulario = crearFormularioIndividual(
                ruta,
                { cantidadHabitaciones: cantidadHabitaciones.value });
            marcarEnvio(
                botonGuardarCantidad,
                "Guardando…",
                "Guardando la cantidad de habitaciones.");
            formulario.submit();
        });
    }

    configuraciones.forEach((configuracion) => {
        const boton = configuracion.botonGuardarSeccion;
        if (!(boton instanceof HTMLButtonElement)) return;
        boton.addEventListener("click", () => {
            if (enviando) return;
            limpiarErrorGeneral();
            if (!configuracion.esNueva && !configuracion.estaIncluida()) return;
            if (!configuracion.validar(configuracion.esNueva)) return;
            if (configuracion.esNueva && !validarAltaSinDuplicados(configuracion)) return;
            const ruta = boton.dataset.rutaGuardado;
            if (!ruta) return;
            const formulario = crearFormularioIndividual(ruta, {
                idSeccion: configuracion.idSeccion,
                color: configuracion.color.value,
                nombre: configuracion.nombre.value,
                precio6Horas: configuracion.precio6Horas.value,
                precioDiaCompleto: configuracion.precioDiaCompleto.value
            });
            marcarEnvio(
                boton,
                configuracion.esNueva ? "Añadiendo…" : "Guardando…",
                configuracion.esNueva
                    ? "Añadiendo y guardando la nueva sección."
                    : "Guardando los cambios de la sección seleccionada.",
                configuracion.esNueva ? "Añadiendo nueva sección" : "");
            formulario.submit();
        });
    });

    formularioGeneral.addEventListener("submit", (evento) => {
        if (enviando) {
            evento.preventDefault();
            return;
        }
        limpiarErrorGeneral();

        if (!cantidadValida()) {
            evento.preventDefault();
            mostrarErrorCantidad();
            return;
        }

        const finales = seccionesFinales();
        if (finales.length < 1 || finales.length > 12) {
            evento.preventDefault();
            if (mensajeGeneral) {
                mensajeGeneral.textContent = finales.length < 1
                    ? "El hotel debe conservar al menos una sección tarifaria."
                    : "Puedes registrar como máximo 12 secciones tarifarias.";
            }
            botonGuardar?.focus({ preventScroll: true });
            return;
        }
        for (const configuracion of finales) {
            if (!configuracion.validar()) {
                evento.preventDefault();
                return;
            }
        }

        const colores = new Set();
        const nombres = new Set();
        for (const configuracion of finales) {
            const color = configuracion.color.value.toLowerCase();
            const nombre = configuracion.nombreEfectivo().toLocaleLowerCase("es");
            if (colores.has(color)) {
                evento.preventDefault();
                configuracion.mostrarError("Ya existe otra sección con ese color.", configuracion.color);
                return;
            }
            if (nombres.has(nombre)) {
                evento.preventDefault();
                configuracion.mostrarError("Ya existe otra sección con ese nombre.", configuracion.nombre);
                return;
            }
            colores.add(color);
            nombres.add(nombre);
        }

        enviando = true;
        if (botonGuardar) {
            botonGuardar.disabled = true;
            botonGuardar.textContent = "Guardando cambios…";
        }
        if (estadoCambios) estadoCambios.textContent = "Guardando toda la configuración.";
    });

    const hayErrorServidor = Boolean(mensajeGeneral?.textContent.trim());
    if (botonGuardar && !hayErrorServidor) botonGuardar.disabled = true;
    actualizarResumenSecciones();
})();
