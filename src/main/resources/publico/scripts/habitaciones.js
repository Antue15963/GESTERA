(() => {
    "use strict";

    const pagina = document.body;
    const modo = pagina.dataset.modo === "limpieza" ? "limpieza" : "normal";
    const estadosLimpieza = new Set(["limpieza_pendiente", "en_limpieza"]);
    const estadosPermitidos = new Set([
        "disponible", "ocupada", "limpieza_pendiente", "en_limpieza", "mantenimiento"
    ]);
    const tiposServicio = new Set(["seis_horas", "dia_completo"]);
    const coloresAnteriores = {
        amarillo: "#ffbd24",
        celeste: "#59c9e2",
        violeta: "#a98bff",
        verde: "#61d69b",
        coral: "#ff8e7a"
    };

    const colorSeguro = (valor) => {
        const normalizado = String(valor || "").trim().toLowerCase();
        if (/^#[0-9a-f]{6}$/.test(normalizado)) return normalizado;
        return coloresAnteriores[normalizado] || "#ffbd24";
    };

    document.querySelectorAll("[data-modo-destino]").forEach((enlace) => {
        const activo = enlace.dataset.modoDestino === modo;
        enlace.classList.toggle("selector-modo-habitaciones__activo", activo);
        if (activo) enlace.setAttribute("aria-current", "page");
    });

    const insigniaModo = document.querySelector("#insignia-modo");
    if (insigniaModo) {
        insigniaModo.textContent = modo === "limpieza" ? "Modo limpieza" : "Vista general";
    }

    const rejilla = document.querySelector("#rejilla-habitaciones");
    const leyenda = document.querySelector("#leyenda-habitaciones");
    const contador = document.querySelector("#contador-habitaciones");
    const contadorDisponibles = document.querySelector("#contador-habitaciones-disponibles");
    const contadorOcupadas = document.querySelector("#contador-habitaciones-ocupadas");
    const sinHabitaciones = document.querySelector("#sin-habitaciones");
    const tarjetas = rejilla
        ? [...rejilla.querySelectorAll(".tarjeta-habitacion[data-habitacion-numero]")]
        : [];
    let seccionActiva = "todas";

    tarjetas.forEach((tarjeta) => {
        tarjeta.style.setProperty("--color-habitacion", colorSeguro(tarjeta.dataset.habitacionColor));
    });

    const perteneceAlModo = (tarjeta) => modo !== "limpieza"
        || estadosLimpieza.has(tarjeta.dataset.habitacionEstado);

    const claveSeccion = (tarjeta) => tarjeta.dataset.habitacionSeccion
        || `color:${colorSeguro(tarjeta.dataset.habitacionColor)}`;

    const actualizarListado = () => {
        let visibles = 0;
        let disponibles = 0;
        let ocupadas = 0;
        tarjetas.forEach((tarjeta) => {
            const coincideModo = perteneceAlModo(tarjeta);
            const coincideSeccion = seccionActiva === "todas" || claveSeccion(tarjeta) === seccionActiva;
            const visible = coincideModo && coincideSeccion;
            tarjeta.hidden = !visible;
            if (visible) {
                visibles += 1;
                if (tarjeta.dataset.habitacionEstado === "disponible") disponibles += 1;
                if (tarjeta.dataset.habitacionEstado === "ocupada") ocupadas += 1;
            }
        });

        if (contador) {
            contador.textContent = `${visibles} ${visibles === 1 ? "habitación mostrada" : "habitaciones mostradas"}`;
        }
        if (contadorDisponibles) {
            contadorDisponibles.textContent = `${disponibles} ${disponibles === 1
                ? "habitación disponible"
                : "habitaciones disponibles"}`;
        }
        if (contadorOcupadas) {
            contadorOcupadas.textContent = `${ocupadas} ${ocupadas === 1
                ? "habitación ocupada"
                : "habitaciones ocupadas"}`;
        }
        if (sinHabitaciones) sinHabitaciones.hidden = visibles !== 0;
    };

    const seleccionarSeccion = (clave) => {
        seccionActiva = clave;
        leyenda?.querySelectorAll("button[data-seccion]").forEach((boton) => {
            const activo = boton.dataset.seccion === clave;
            boton.setAttribute("aria-pressed", String(activo));
            boton.classList.toggle("leyenda-habitaciones__filtro--activo", activo);
        });
        actualizarListado();
    };

    const crearFiltro = ({ clave, etiqueta, color = "" }) => {
        const boton = document.createElement("button");
        boton.type = "button";
        boton.className = "leyenda-habitaciones__filtro";
        boton.dataset.seccion = clave;
        boton.setAttribute("aria-pressed", "false");

        if (color) {
            const muestra = document.createElement("span");
            muestra.className = "leyenda-habitaciones__color";
            muestra.style.setProperty("--color-habitacion", colorSeguro(color));
            muestra.setAttribute("aria-hidden", "true");
            boton.append(muestra);
        }

        const texto = document.createElement("span");
        texto.textContent = etiqueta;
        boton.append(texto);
        boton.addEventListener("click", () => seleccionarSeccion(clave));
        return boton;
    };

    if (leyenda) {
        leyenda.replaceChildren(crearFiltro({ clave: "todas", etiqueta: "Todas" }));
        const secciones = new Map();

        tarjetas.filter(perteneceAlModo).forEach((tarjeta) => {
            const clave = claveSeccion(tarjeta);
            if (secciones.has(clave)) return;
            const color = colorSeguro(tarjeta.dataset.habitacionColor);
            secciones.set(clave, {
                color,
                nombre: tarjeta.dataset.habitacionSeccionNombre || `Color ${color.toUpperCase()}`
            });
        });

        [...secciones.entries()]
            .sort(([, primera], [, segunda]) => primera.nombre.localeCompare(segunda.nombre, "es"))
            .forEach(([clave, seccion]) => {
                leyenda.append(crearFiltro({ clave, color: seccion.color, etiqueta: seccion.nombre }));
            });
        seleccionarSeccion("todas");
    } else {
        actualizarListado();
    }

    const regionAlertasCheckout = document.querySelector("#modal-alertas-checkout");
    const abrirAlertasCheckout = document.querySelector("#abrir-alertas-checkout");
    const listaAlertasCheckout = document.querySelector("#lista-alertas-checkout");
    const contadorAlertasCheckout = document.querySelector("#contador-alertas-checkout");
    const sinAlertasCheckout = document.querySelector("#sin-alertas-checkout");
    const anuncioAlertasCheckout = document.querySelector("#anuncio-alertas-checkout");
    const modalCheckout = document.querySelector("#modal-checkout");
    const numeroCheckout = modalCheckout?.querySelector("#numero-checkout");
    const numeroCheckoutTitulo = modalCheckout?.querySelector("#numero-checkout-titulo");
    const horaLimiteCheckout = modalCheckout?.querySelector("#hora-limite-checkout");
    const estadoPagoCheckout = modalCheckout?.querySelector("#estado-pago-checkout");
    const resumenMontoCheckout = modalCheckout?.querySelector("#resumen-monto-checkout");
    const montoTotalCheckout = modalCheckout?.querySelector("#monto-total-checkout");
    const avisoPagoCheckout = modalCheckout?.querySelector("#aviso-pago-checkout");
    const confirmacionCheckout = modalCheckout?.querySelector("#confirmacion-checkout");
    const alertasCheckoutActivas = new Map();
    const tarjetasPorNumero = new Map(
        tarjetas.map((tarjeta) => [tarjeta.dataset.habitacionNumero, tarjeta])
    );
    const formateadorCheckout = new Intl.DateTimeFormat("es-PE", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "America/Lima"
    });
    let disparadorCheckout = null;
    let contextoAudio = null;
    let sonidoPendiente = false;
    const checkoutsNotificados = new Set();

    const reproducirCampana = () => {
        if (!contextoAudio || contextoAudio.state !== "running") {
            sonidoPendiente = true;
            return;
        }
        sonidoPendiente = false;
        const inicio = contextoAudio.currentTime;
        [0, 0.18].forEach((retraso, indice) => {
            const oscilador = contextoAudio.createOscillator();
            const volumen = contextoAudio.createGain();
            oscilador.type = "sine";
            oscilador.frequency.setValueAtTime(indice === 0 ? 880 : 1174, inicio + retraso);
            volumen.gain.setValueAtTime(0.0001, inicio + retraso);
            volumen.gain.exponentialRampToValueAtTime(0.16, inicio + retraso + 0.015);
            volumen.gain.exponentialRampToValueAtTime(0.0001, inicio + retraso + 0.42);
            oscilador.connect(volumen).connect(contextoAudio.destination);
            oscilador.start(inicio + retraso);
            oscilador.stop(inicio + retraso + 0.45);
        });
    };

    const habilitarSonido = async () => {
        try {
            const ConstructorAudio = window.AudioContext || window.webkitAudioContext;
            if (!ConstructorAudio) return;
            contextoAudio ||= new ConstructorAudio();
            if (contextoAudio.state === "suspended") await contextoAudio.resume();
            if (sonidoPendiente) reproducirCampana();
        } catch (_) {
            sonidoPendiente = true;
        }
    };

    document.addEventListener("pointerdown", habilitarSonido, { once: true });
    document.addEventListener("keydown", habilitarSonido, { once: true });

    const numeroMonetario = (valor) => {
        const numero = Number(String(valor ?? "").trim());
        return Number.isFinite(numero) && numero >= 0 ? numero : 0;
    };

    const formatearMonto = (valor) => `S/ ${numeroMonetario(valor).toFixed(2)}`;

    const obtenerMontoTotal = (tarjeta) => {
        const totalDeclarado = String(tarjeta.dataset.habitacionMontoTotal || "").trim();
        if (/^\d+(?:\.\d{1,2})?$/.test(totalDeclarado)) return numeroMonetario(totalDeclarado);
        return numeroMonetario(tarjeta.dataset.habitacionPrecio)
            + numeroMonetario(tarjeta.dataset.habitacionMontoAdicional);
    };

    const obtenerEpochCheckout = (tarjeta) => {
        const epoch = Number(tarjeta.dataset.habitacionCheckoutEpoch);
        return Number.isFinite(epoch) && epoch > 0 ? epoch : null;
    };

    const formatearHoraCheckout = (epoch) => {
        if (!Number.isFinite(epoch)) return "Hora no disponible";
        try {
            return formateadorCheckout.format(new Date(epoch));
        } catch (error) {
            return "Hora no disponible";
        }
    };

    const completarAlertaCheckout = (
        boton, tarjeta, epoch, fase, minutosRestantes, minutosVencidos
    ) => {
        const numeroHabitacion = tarjeta.dataset.habitacionNumero || "";
        const pagado = tarjeta.dataset.habitacionPagado === "true";
        boton.className = `alerta-checkout alerta-checkout--${fase}`;
        boton.dataset.habitacionNumero = numeroHabitacion;
        boton.dataset.fase = fase;
        boton.setAttribute("aria-disabled", String(fase !== "vencida"));
        if (fase === "vencida") {
            boton.setAttribute("aria-haspopup", "dialog");
            boton.setAttribute("aria-controls", "modal-checkout");
        } else {
            boton.removeAttribute("aria-haspopup");
            boton.removeAttribute("aria-controls");
        }

        const icono = document.createElement("span");
        icono.className = "alerta-checkout__icono";
        icono.setAttribute("aria-hidden", "true");
        icono.textContent = fase === "vencida" ? "!" : fase === "cinco" ? "5" : "10";

        const contenido = document.createElement("span");
        contenido.className = "alerta-checkout__contenido";
        const titulo = document.createElement("strong");
        titulo.textContent = fase === "vencida"
            ? `Check-out vencido hace ${minutosVencidos} ${minutosVencidos === 1 ? "minuto" : "minutos"} · Habitación ${numeroHabitacion}`
            : `Faltan ${minutosRestantes} ${minutosRestantes === 1 ? "minuto" : "minutos"} para la salida de la habitación ${numeroHabitacion}`;
        const detalle = document.createElement("small");
        detalle.textContent = `${formatearHoraCheckout(epoch)} · ${pagado
            ? "Pago registrado"
            : "Falta registrar el pago"}`;
        contenido.append(titulo, detalle);
        if (!pagado && fase === "vencida") {
            const monto = document.createElement("small");
            monto.className = "alerta-checkout__monto";
            monto.textContent = `Monto total a cobrar: ${formatearMonto(obtenerMontoTotal(tarjeta))}`;
            contenido.append(monto);
        }

        const accion = document.createElement("span");
        accion.className = "alerta-checkout__accion";
        accion.textContent = fase === "vencida" ? "Registrar check-out" : "Próximamente";
        boton.replaceChildren(icono, contenido, accion);
    };

    const abrirModalCheckout = (tarjeta, disparador) => {
        if (!modalCheckout || !numeroCheckout || !numeroCheckoutTitulo
            || !horaLimiteCheckout || !estadoPagoCheckout || !avisoPagoCheckout
            || !resumenMontoCheckout || !montoTotalCheckout || !confirmacionCheckout) return;

        const numeroHabitacion = tarjeta.dataset.habitacionNumero || "";
        const epoch = obtenerEpochCheckout(tarjeta);
        const pagado = tarjeta.dataset.habitacionPagado === "true";
        numeroCheckout.value = numeroHabitacion;
        numeroCheckoutTitulo.textContent = numeroHabitacion;
        horaLimiteCheckout.textContent = formatearHoraCheckout(epoch);
        estadoPagoCheckout.textContent = pagado ? "Pagado" : "Pago pendiente";
        estadoPagoCheckout.classList.toggle("estado-pago-checkout--pendiente", !pagado);
        resumenMontoCheckout.hidden = pagado;
        montoTotalCheckout.textContent = pagado
            ? ""
            : formatearMonto(obtenerMontoTotal(tarjeta));
        avisoPagoCheckout.hidden = pagado;
        avisoPagoCheckout.textContent = pagado
            ? ""
            : `Falta registrar el pago de esta habitación. Monto total a cobrar: ${formatearMonto(obtenerMontoTotal(tarjeta))}. Puedes confirmar el check-out, pero el pago continuará pendiente.`;
        confirmacionCheckout.checked = false;
        confirmacionCheckout.setCustomValidity("");
        disparadorCheckout = disparador;

        if (typeof modalCheckout.showModal === "function" && !modalCheckout.open) {
            modalCheckout.showModal();
        } else {
            modalCheckout.setAttribute("open", "");
        }
        confirmacionCheckout.focus({ preventScroll: true });
    };

    const cerrarModalCheckout = () => {
        if (!modalCheckout) return;
        if (typeof modalCheckout.close === "function" && modalCheckout.open) {
            modalCheckout.close();
        } else {
            modalCheckout.removeAttribute("open");
            disparadorCheckout?.focus({ preventScroll: true });
            disparadorCheckout = null;
        }
    };

    listaAlertasCheckout?.addEventListener("click", (evento) => {
        const boton = evento.target.closest(".alerta-checkout[data-habitacion-numero]");
        if (!boton || !listaAlertasCheckout.contains(boton)) return;
        if (boton.dataset.fase !== "vencida") return;
        const tarjeta = tarjetasPorNumero.get(boton.dataset.habitacionNumero);
        if (tarjeta) {
            cerrarAlertasCheckout();
            abrirModalCheckout(tarjeta, abrirAlertasCheckout);
        }
    });

    const cerrarAlertasCheckout = () => {
        if (!regionAlertasCheckout) return;
        if (typeof regionAlertasCheckout.close === "function" && regionAlertasCheckout.open) {
            regionAlertasCheckout.close();
        } else {
            regionAlertasCheckout.removeAttribute("open");
        }
    };

    abrirAlertasCheckout?.addEventListener("click", () => {
        habilitarSonido();
        if (!regionAlertasCheckout) return;
        if (typeof regionAlertasCheckout.showModal === "function" && !regionAlertasCheckout.open) {
            regionAlertasCheckout.showModal();
        } else {
            regionAlertasCheckout.setAttribute("open", "");
        }
    });
    regionAlertasCheckout?.querySelectorAll("[data-cerrar-alertas-checkout]").forEach((boton) => {
        boton.addEventListener("click", cerrarAlertasCheckout);
    });
    regionAlertasCheckout?.addEventListener("click", (evento) => {
        if (evento.target === regionAlertasCheckout) cerrarAlertasCheckout();
    });
    regionAlertasCheckout?.addEventListener("close", () => {
        abrirAlertasCheckout?.focus({ preventScroll: true });
    });

    modalCheckout?.querySelectorAll("[data-cerrar-checkout]").forEach((boton) => {
        boton.addEventListener("click", cerrarModalCheckout);
    });
    modalCheckout?.addEventListener("click", (evento) => {
        if (evento.target === modalCheckout) cerrarModalCheckout();
    });
    modalCheckout?.addEventListener("close", () => {
        disparadorCheckout?.focus({ preventScroll: true });
        disparadorCheckout = null;
    });
    confirmacionCheckout?.addEventListener("invalid", () => {
        confirmacionCheckout.setCustomValidity(
            "Confirma que se realizó el check-out antes de continuar."
        );
    });
    confirmacionCheckout?.addEventListener("change", () => {
        confirmacionCheckout.setCustomValidity("");
    });

    const actualizarAlertasCheckout = () => {
        if (!regionAlertasCheckout || !listaAlertasCheckout) return;
        const ahora = Date.now();
        const vigentes = new Set();
        const nuevas = [];
        let debeSonar = false;

        tarjetas.forEach((tarjeta) => {
            const numeroHabitacion = tarjeta.dataset.habitacionNumero || "";
            const epoch = obtenerEpochCheckout(tarjeta);
            const restante = epoch === null ? Number.POSITIVE_INFINITY : epoch - ahora;
            const proxima = tarjeta.dataset.habitacionEstado === "ocupada"
                && epoch !== null && restante <= 10 * 60 * 1000;
            const fase = restante <= 0 ? "vencida" : restante <= 5 * 60 * 1000 ? "cinco" : "diez";
            const minutosRestantes = Math.max(1, Math.ceil(restante / 60000));
            const minutosVencidos = Math.floor(Math.max(0, -restante) / 60000);
            const vencida = proxima && fase === "vencida";
            tarjeta.classList.toggle("tarjeta-habitacion--checkout-vencido", vencida);
            tarjeta.classList.toggle("tarjeta-habitacion--checkout-proximo", proxima && !vencida);
            if (!proxima || !numeroHabitacion) return;

            vigentes.add(numeroHabitacion);
            const minutoFirma = fase === "vencida" ? minutosVencidos : minutosRestantes;
            const firma = `${epoch}:${fase}:${minutoFirma}:${tarjeta.dataset.habitacionPagado === "true"}:${obtenerMontoTotal(tarjeta)}`;
            const existente = alertasCheckoutActivas.get(numeroHabitacion);
            if (existente?.firma === firma) return;

            const boton = existente?.boton || document.createElement("button");
            boton.type = "button";
            completarAlertaCheckout(
                boton, tarjeta, epoch, fase, minutosRestantes, minutosVencidos);
            if (!existente) listaAlertasCheckout.append(boton);
            alertasCheckoutActivas.set(numeroHabitacion, { boton, firma });
            nuevas.push(numeroHabitacion);
            const claveNotificacion = `${numeroHabitacion}:${epoch}`;
            if (fase === "vencida" && !checkoutsNotificados.has(claveNotificacion)) {
                checkoutsNotificados.add(claveNotificacion);
                debeSonar = true;
            }
        });

        [...alertasCheckoutActivas.entries()].forEach(([numeroHabitacion, alerta]) => {
            if (vigentes.has(numeroHabitacion)) return;
            alerta.boton.remove();
            alertasCheckoutActivas.delete(numeroHabitacion);
        });

        const cantidad = alertasCheckoutActivas.size;
        if (sinAlertasCheckout) sinAlertasCheckout.hidden = cantidad !== 0;
        if (contadorAlertasCheckout) {
            contadorAlertasCheckout.textContent = String(cantidad);
            contadorAlertasCheckout.setAttribute(
                "aria-label", `${cantidad} ${cantidad === 1 ? "alerta" : "alertas"}`);
        }
        abrirAlertasCheckout?.classList.toggle("boton-alertas-checkout--activo", cantidad > 0);
        const tieneVencida = [...alertasCheckoutActivas.values()]
            .some(({ boton }) => boton.dataset.fase === "vencida");
        const tieneCincoMinutos = [...alertasCheckoutActivas.values()]
            .some(({ boton }) => boton.dataset.fase === "cinco");
        abrirAlertasCheckout?.classList.toggle("boton-alertas-checkout--vencida", tieneVencida);
        abrirAlertasCheckout?.classList.toggle(
            "boton-alertas-checkout--cinco-minutos", !tieneVencida && tieneCincoMinutos);
        if (debeSonar) reproducirCampana();
        if (anuncioAlertasCheckout && nuevas.length > 0) {
            anuncioAlertasCheckout.textContent = nuevas.length === 1
                ? `Nueva alerta de check-out para la habitación ${nuevas[0]}.`
                : `Hay ${nuevas.length} cambios en las alertas de check-out.`;
        }
    };

    actualizarAlertasCheckout();
    if (tarjetas.length > 0) {
        window.setInterval(actualizarAlertasCheckout, 5000);
        document.addEventListener("visibilitychange", () => {
            if (!document.hidden) actualizarAlertasCheckout();
        });
        window.addEventListener("pageshow", actualizarAlertasCheckout);
    }

    const modal = document.querySelector("#modal-habitacion");
    const formulario = modal?.querySelector(".formulario-habitacion");
    const numero = modal?.querySelector("#numero-habitacion");
    const numeroTitulo = modal?.querySelector("#numero-habitacion-titulo");
    const seccion = modal?.querySelector("#seccion-habitacion");
    const tipoServicio = modal?.querySelector("#tipo-servicio-habitacion");
    const precio = modal?.querySelector("#precio-habitacion");
    const estado = modal?.querySelector("#estado-habitacion");
    const checkIn = modal?.querySelector("#checkin-habitacion");
    const checkOut = modal?.querySelector("#checkout-habitacion");
    const ajusteManualCheckOut = modal?.querySelector("#ajuste-manual-checkout");
    const pago = modal?.querySelector("#pago-habitacion");
    const ayudaEstadia = modal?.querySelector("#ayuda-estadia");
    const grupoHuespedes = modal?.querySelector("#grupo-huespedes");
    const listaHuespedes = modal?.querySelector("#lista-huespedes");
    const cantidadHuespedes = modal?.querySelector("#cantidad-huespedes");
    const agregarHuesped = modal?.querySelector("#agregar-huesped");
    const mensajeError = modal?.querySelector("#mensaje-error-habitacion");
    const muestraColor = modal?.querySelector("#muestra-color-tarifa");
    const nombreTarifa = modal?.querySelector("#nombre-tarifa");
    const detalleTarifa = modal?.querySelector("#detalle-tarifa");
    const ampliacionEstadia = modal?.querySelector("#ampliacion-estadia");
    const opcionesHorasAdicionales = ampliacionEstadia
        ? [...ampliacionEstadia.querySelectorAll("input[name='horasAdicionales']")]
        : [];
    const montoAdicional = ampliacionEstadia?.querySelector("#monto-adicional-habitacion");
    const ayudaAmpliacion = ampliacionEstadia?.querySelector("#ayuda-ampliacion-estadia");
    const tarifaOriginalAmpliacion = ampliacionEstadia?.querySelector("#tarifa-original-ampliacion");
    const adicionalAcumuladoAmpliacion = ampliacionEstadia?.querySelector("#adicional-acumulado-ampliacion");
    const horasAcumuladasAmpliacion = ampliacionEstadia?.querySelector("#horas-acumuladas-ampliacion");
    const nuevoCargoAmpliacion = ampliacionEstadia?.querySelector("#nuevo-cargo-ampliacion");
    const totalAmpliacion = ampliacionEstadia?.querySelector("#total-ampliacion");
    const checkoutAmpliacion = ampliacionEstadia?.querySelector("#checkout-ampliacion");
    let tarjetaOrigen = null;

    if (!modal || !formulario || !numero || !numeroTitulo || !seccion || !tipoServicio
        || !precio || !estado || !checkIn || !checkOut || !ajusteManualCheckOut
        || !pago || !grupoHuespedes || !listaHuespedes || !cantidadHuespedes
        || !agregarHuesped || !mensajeError || !ampliacionEstadia || !montoAdicional
        || !ayudaAmpliacion || opcionesHorasAdicionales.length !== 4
        || !tarifaOriginalAmpliacion || !adicionalAcumuladoAmpliacion
        || !horasAcumuladasAmpliacion || !nuevoCargoAmpliacion
        || !totalAmpliacion || !checkoutAmpliacion) return;

    const filaHuespedBase = listaHuespedes.querySelector("[data-fila-huesped]")?.cloneNode(true);
    if (!filaHuespedBase) return;

    const datosHuespedesActuales = () => [...listaHuespedes.querySelectorAll("[data-fila-huesped]")]
        .map((fila) => ({
            nombre: fila.querySelector("input[name$='.nombre']")?.value || "",
            tipoDocumento: fila.querySelector("select[name$='.tipoDocumento']")?.value || "dni",
            numeroDocumento: fila.querySelector("input[name$='.numeroDocumento']")?.value || ""
        }));
    const huespedesPredeterminados = () => [
        { nombre: "", tipoDocumento: "dni", numeroDocumento: "" },
        { nombre: "", tipoDocumento: "dni", numeroDocumento: "" }
    ];
    const reglasDocumento = {
        dni: {
            patron: "[0-9]{8}", expresion: /^[0-9]{8}$/,
            minimo: 8, maximo: 8, ejemplo: "12345678", entrada: "numeric",
            ayuda: "El DNI debe contener exactamente 8 números."
        },
        carnet_extranjeria: {
            patron: "[A-Z0-9]{9,12}", expresion: /^[A-Z0-9]{9,12}$/,
            minimo: 9, maximo: 12, ejemplo: "ABC123456", entrada: "text",
            ayuda: "El carnet de extranjería admite entre 9 y 12 letras o números."
        },
        pasaporte: {
            patron: "[A-Z0-9]{6,12}", expresion: /^[A-Z0-9]{6,12}$/,
            minimo: 6, maximo: 12, ejemplo: "AB123456", entrada: "text",
            ayuda: "El pasaporte admite entre 6 y 12 letras o números."
        }
    };

    const actualizarDocumentoHuesped = (fila) => {
        const tipo = fila.querySelector("select[name$='.tipoDocumento']");
        const documento = fila.querySelector("input[name$='.numeroDocumento']");
        const regla = reglasDocumento[tipo?.value] || reglasDocumento.dni;
        if (!documento) return;
        documento.pattern = regla.patron;
        documento.minLength = regla.minimo;
        documento.maxLength = regla.maximo;
        documento.placeholder = regla.ejemplo;
        documento.inputMode = regla.entrada;
        documento.title = regla.ayuda;
        documento.value = documento.value.toUpperCase().replace(/[^A-Z0-9]/g, "")
            .slice(0, regla.maximo);
    };

    const actualizarIndicesHuespedes = () => {
        const filas = [...listaHuespedes.querySelectorAll("[data-fila-huesped]")];
        filas.forEach((fila, indice) => {
            const visible = indice + 1;
            const titulo = fila.querySelector("h3");
            const quitar = fila.querySelector("[data-quitar-huesped]");
            const nombre = fila.querySelector("input[name$='.nombre']");
            const tipo = fila.querySelector("select[name$='.tipoDocumento']");
            const documento = fila.querySelector("input[name$='.numeroDocumento']");
            if (titulo) titulo.textContent = `Huésped ${visible}`;
            if (quitar) quitar.setAttribute("aria-label", `Quitar huésped ${visible}`);
            [[nombre, "nombre", "nombre"], [tipo, "tipoDocumento", "tipo"],
                [documento, "numeroDocumento", "documento"]].forEach(([campo, nombreCampo, id]) => {
                if (!campo) return;
                campo.name = `huesped.${indice}.${nombreCampo}`;
                campo.id = `${id}-huesped-${visible}`;
                campo.previousElementSibling?.setAttribute("for", campo.id);
            });
        });
        cantidadHuespedes.value = String(filas.length);
        agregarHuesped.disabled = estado.value !== "ocupada" || filas.length >= 8;
        filas.forEach((fila) => {
            const quitar = fila.querySelector("[data-quitar-huesped]");
            if (quitar) quitar.disabled = estado.value !== "ocupada" || filas.length <= 1;
        });
    };

    const crearFilaHuesped = (datos = {}) => {
        const fila = filaHuespedBase.cloneNode(true);
        const nombre = fila.querySelector("input[name$='.nombre']");
        const tipo = fila.querySelector("select[name$='.tipoDocumento']");
        const documento = fila.querySelector("input[name$='.numeroDocumento']");
        if (nombre) nombre.value = String(datos.nombre || "");
        if (tipo) tipo.value = ["dni", "carnet_extranjeria", "pasaporte"].includes(datos.tipoDocumento)
            ? datos.tipoDocumento : "dni";
        if (documento) documento.value = String(datos.numeroDocumento || "");
        tipo?.addEventListener("change", () => actualizarDocumentoHuesped(fila));
        documento?.addEventListener("input", () => actualizarDocumentoHuesped(fila));
        fila.querySelector("[data-quitar-huesped]")?.addEventListener("click", () => {
            if (listaHuespedes.children.length <= 1) return;
            fila.remove();
            actualizarIndicesHuespedes();
        });
        actualizarDocumentoHuesped(fila);
        return fila;
    };

    const mostrarHuespedes = (huespedes) => {
        const datos = Array.isArray(huespedes) && huespedes.length
            ? huespedes.slice(0, 8)
            : huespedesPredeterminados();
        listaHuespedes.replaceChildren(...datos.map(crearFilaHuesped));
        actualizarIndicesHuespedes();
    };

    const leerHuespedesTarjeta = (valor) => {
        try {
            const datos = JSON.parse(valor || "[]");
            return Array.isArray(datos) ? datos : [];
        } catch (_) {
            return [];
        }
    };
    const huespedesServidor = datosHuespedesActuales();

    const opcionesSeccion = [...seccion.options].filter((opcion) => opcion.value);
    const existeSeccion = (valor) => opcionesSeccion.some((opcion) => opcion.value === valor);
    const opcionSeccionActual = () => seccion.selectedOptions[0];
    let montoAdicionalAcumulado = 0;
    let montoTotalActual = 0;
    let horasAdicionalesAcumuladas = 0;

    const limpiarError = () => {
        mensajeError.textContent = "";
        formulario.querySelectorAll("[aria-invalid='true']").forEach((campo) => {
            campo.removeAttribute("aria-invalid");
        });
    };

    const mostrarError = (mensaje, campo) => {
        mensajeError.textContent = mensaje;
        campo.setAttribute("aria-invalid", "true");
        campo.focus({ preventScroll: true });
    };

    const formatearFechaLocal = (fecha) => {
        const dosDigitos = (valor) => String(valor).padStart(2, "0");
        return `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}`
            + `T${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}`;
    };

    const calcularHorarios = () => {
        if (!tiposServicio.has(tipoServicio.value)) return;
        const fechaExistente = checkIn.value ? new Date(checkIn.value) : null;
        const entrada = fechaExistente instanceof Date && Number.isFinite(fechaExistente.getTime())
            && estadoConEstadia()
            ? fechaExistente
            : new Date();
        entrada.setSeconds(0, 0);
        const salida = new Date(entrada.getTime());
        const horasServicio = tipoServicio.value === "seis_horas" ? 6 : 24;
        salida.setHours(salida.getHours() + horasServicio + horasAdicionalesAcumuladas);
        checkIn.value = formatearFechaLocal(entrada);
        checkOut.value = formatearFechaLocal(salida);
    };

    const actualizarTarifa = ({ recalcularHorarios = false, conservarPrecio = false } = {}) => {
        const opcion = opcionSeccionActual();
        const servicioValido = tiposServicio.has(tipoServicio.value);
        const clavePrecio = tipoServicio.value === "seis_horas"
            ? "precioSeisHoras"
            : "precioDiaCompleto";
        const valorPrecio = opcion?.dataset[clavePrecio] || "";
        const color = colorSeguro(opcion?.dataset.color);
        const nombre = opcion?.dataset.nombre || opcion?.textContent?.trim() || "Selecciona una sección";

        if (muestraColor) muestraColor.style.setProperty("--color-tarifa", color);
        if (nombreTarifa) nombreTarifa.textContent = opcion?.value ? nombre : "Selecciona una sección";
        if (detalleTarifa) {
            detalleTarifa.textContent = !opcion?.value
                ? "Elige una sección de color para consultar sus precios."
                : !servicioValido
                    ? "Selecciona 6 horas o día completo."
                    : tipoServicio.value === "seis_horas" ? "Servicio de 6 horas" : "Servicio de día completo";
        }
        if (!conservarPrecio) {
            precio.value = opcion?.value && servicioValido && /^\d+(?:\.\d{1,2})?$/.test(valorPrecio)
                ? Number(valorPrecio).toFixed(2)
                : "";
        }

        if (recalcularHorarios && opcion?.value && servicioValido) calcularHorarios();
    };

    const estadoConEstadia = () => estado.value === "ocupada" || estadosLimpieza.has(estado.value);

    const horasAdicionalesSeleccionadas = () => {
        const seleccion = opcionesHorasAdicionales.find((opcion) => opcion.checked);
        const horas = Number(seleccion?.value || 0);
        return [0, 1, 2, 4].includes(horas) ? horas : 0;
    };

    const formateadorAmpliacion = new Intl.DateTimeFormat("es-PE", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "UTC"
    });

    const fechaCheckoutComoUtc = () => {
        const valor = String(checkOut.value || "");
        if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(valor)) return null;
        const fecha = new Date(`${valor}:00Z`);
        return Number.isFinite(fecha.getTime()) ? fecha : null;
    };

    const actualizarResumenAmpliacion = () => {
        const horas = horasAdicionalesSeleccionadas();
        const nuevoCargo = horas > 0 ? numeroMonetario(montoAdicional.value) : 0;
        const tarifaDisponible = String(precio.value || "").trim() !== "";
        const tarifa = numeroMonetario(precio.value);
        const totalAntesDeAmpliar = tarifaDisponible
            ? tarifa + montoAdicionalAcumulado
            : montoTotalActual;
        tarifaOriginalAmpliacion.textContent = formatearMonto(tarifa);
        adicionalAcumuladoAmpliacion.textContent = formatearMonto(montoAdicionalAcumulado);
        horasAcumuladasAmpliacion.textContent = `${horasAdicionalesAcumuladas} h`;
        nuevoCargoAmpliacion.textContent = formatearMonto(nuevoCargo);
        totalAmpliacion.textContent = formatearMonto(totalAntesDeAmpliar + nuevoCargo);

        const salida = fechaCheckoutComoUtc();
        if (!salida) {
            checkoutAmpliacion.textContent = "Hora no disponible";
            return;
        }
        salida.setUTCHours(salida.getUTCHours() + horas);
        checkoutAmpliacion.textContent = `${formateadorAmpliacion.format(salida)}${horas
            ? ` (+${horas} ${horas === 1 ? "hora" : "horas"})`
            : " (sin cambio)"}`;
    };

    const reiniciarNuevaAmpliacion = () => {
        opcionesHorasAdicionales.forEach((opcion) => {
            opcion.checked = opcion.value === "0";
        });
        montoAdicional.value = "";
        montoAdicional.required = false;
        montoAdicional.disabled = true;
        montoAdicional.removeAttribute("aria-invalid");
        actualizarResumenAmpliacion();
    };

    const actualizarControlesAmpliacion = () => {
        const ocupada = estado.value === "ocupada";
        const horas = horasAdicionalesSeleccionadas();
        ampliacionEstadia.disabled = !ocupada;
        ampliacionEstadia.setAttribute("aria-disabled", String(!ocupada));
        montoAdicional.disabled = !ocupada || horas === 0;
        montoAdicional.required = ocupada && horas > 0;
        ayudaAmpliacion.textContent = ocupada
            ? horas > 0
                ? `Se ampliará la estadía ${horas} ${horas === 1 ? "hora" : "horas"}. Ingresa el nuevo cargo antes de guardar.`
                : "Selecciona +1, +2 o +4 horas para registrar una ampliación."
            : "Esta opción está disponible cuando la habitación se encuentra ocupada.";
        actualizarResumenAmpliacion();
    };

    const actualizarCamposEstadia = ({ limpiar = false, calcularSiFalta = false } = {}) => {
        const admiteEstadia = estadoConEstadia();
        const requiereEstadia = estado.value === "ocupada";

        if (!admiteEstadia && limpiar) {
            checkIn.value = "";
            checkOut.value = "";
            pago.value = "false";
        } else if (admiteEstadia && calcularSiFalta && (!checkIn.value || !checkOut.value)) {
            calcularHorarios();
        }

        pago.disabled = !admiteEstadia;
        checkIn.required = requiereEstadia;
        checkOut.required = requiereEstadia;
        if (!requiereEstadia) ajusteManualCheckOut.checked = false;
        ajusteManualCheckOut.disabled = !requiereEstadia;
        checkOut.readOnly = !requiereEstadia || !ajusteManualCheckOut.checked;
        grupoHuespedes.disabled = !requiereEstadia;
        grupoHuespedes.setAttribute("aria-disabled", String(!requiereEstadia));
        listaHuespedes.querySelectorAll("input, select").forEach((campo) => {
            campo.disabled = !requiereEstadia;
            campo.required = requiereEstadia;
        });
        actualizarIndicesHuespedes();
        if (!requiereEstadia && horasAdicionalesSeleccionadas() !== 0) {
            reiniciarNuevaAmpliacion();
        }
        actualizarControlesAmpliacion();

        if (ayudaEstadia) {
            ayudaEstadia.textContent = requiereEstadia
                ? ajusteManualCheckOut.checked
                    ? "El check-in sigue siendo automático; estás ajustando la salida para una prueba."
                    : "Los horarios se calculan automáticamente según el servicio elegido."
                : admiteEstadia
                    ? "Los datos de la última estadía se conservan durante la limpieza."
                    : "Este estado no conserva horarios ni información de pago.";
        }
    };

    const asignarValorPermitido = (select, valor, permitidos, alternativa) => {
        select.value = permitidos.has(valor) ? valor : alternativa;
    };

    const abrirModal = ({ datos, conservarError = false, origen = null }) => {
        numero.value = datos.numero;
        numeroTitulo.textContent = datos.numero;
        seccion.value = existeSeccion(datos.seccion) ? datos.seccion : "";
        const servicioPredeterminado = datos.estado === "disponible" ? "seis_horas" : "";
        asignarValorPermitido(
            tipoServicio, datos.tipoServicio, tiposServicio, servicioPredeterminado);
        asignarValorPermitido(estado, datos.estado, estadosPermitidos, "disponible");
        precio.value = datos.precio || "";
        checkIn.value = datos.checkIn || "";
        checkOut.value = datos.checkOut || "";
        ajusteManualCheckOut.checked = false;
        montoAdicionalAcumulado = numeroMonetario(datos.montoAdicional);
        montoTotalActual = numeroMonetario(datos.montoTotal);
        horasAdicionalesAcumuladas = Math.max(0, Number.parseInt(datos.horasAdicionales, 10) || 0);
        ampliacionEstadia.dataset.montoAdicionalAcumulado = montoAdicionalAcumulado.toFixed(2);
        ampliacionEstadia.dataset.montoTotalActual = montoTotalActual.toFixed(2);
        ampliacionEstadia.dataset.horasAdicionalesAcumuladas = String(horasAdicionalesAcumuladas);
        reiniciarNuevaAmpliacion();
        pago.value = datos.pagado === "true" ? "true" : "false";
        mostrarHuespedes(datos.huespedes);
        tarjetaOrigen = origen;

        if (!conservarError) limpiarError();
        actualizarTarifa({ conservarPrecio: true });
        actualizarCamposEstadia();
        if (typeof modal.showModal === "function" && !modal.open) modal.showModal();
        else modal.setAttribute("open", "");
        seccion.focus({ preventScroll: true });
    };

    const cerrarModal = () => {
        if (typeof modal.close === "function" && modal.open) modal.close();
        else modal.removeAttribute("open");
    };

    rejilla?.addEventListener("click", (evento) => {
        const tarjeta = evento.target.closest(".tarjeta-habitacion[data-habitacion-numero]");
        if (!tarjeta || !rejilla.contains(tarjeta)) return;
        abrirModal({
            origen: tarjeta,
            datos: {
                numero: tarjeta.dataset.habitacionNumero,
                seccion: tarjeta.dataset.habitacionSeccion,
                tipoServicio: tarjeta.dataset.habitacionTipoServicio,
                precio: tarjeta.dataset.habitacionPrecio,
                estado: tarjeta.dataset.habitacionEstado,
                checkIn: tarjeta.dataset.habitacionCheckin,
                checkOut: tarjeta.dataset.habitacionCheckout,
                pagado: tarjeta.dataset.habitacionPagado,
                montoAdicional: tarjeta.dataset.habitacionMontoAdicional,
                montoTotal: tarjeta.dataset.habitacionMontoTotal,
                horasAdicionales: tarjeta.dataset.habitacionHorasAdicionales,
                huespedes: leerHuespedesTarjeta(tarjeta.dataset.habitacionHuespedes)
            }
        });
    });

    agregarHuesped.addEventListener("click", () => {
        if (estado.value !== "ocupada" || listaHuespedes.children.length >= 8) return;
        const fila = crearFilaHuesped();
        listaHuespedes.append(fila);
        actualizarIndicesHuespedes();
        fila.querySelector("input")?.focus({ preventScroll: true });
    });

    modal.querySelectorAll("[data-cerrar-habitacion]").forEach((boton) => {
        boton.addEventListener("click", cerrarModal);
    });
    modal.addEventListener("click", (evento) => {
        if (evento.target === modal) cerrarModal();
    });
    modal.addEventListener("close", () => {
        tarjetaOrigen?.focus({ preventScroll: true });
        tarjetaOrigen = null;
    });

    seccion.addEventListener("change", () => {
        limpiarError();
        const seccionValida = existeSeccion(seccion.value);
        const requiereValoresIniciales = seccionValida
            && !tiposServicio.has(tipoServicio.value)
            && !estadoConEstadia()
            && !checkIn.value
            && !checkOut.value;
        if (requiereValoresIniciales) {
            tipoServicio.value = "seis_horas";
            estado.value = "disponible";
        }
        reiniciarNuevaAmpliacion();
        actualizarTarifa();
        actualizarCamposEstadia({ limpiar: seccionValida });
    });
    tipoServicio.addEventListener("change", () => {
        limpiarError();
        if (tipoServicio.value
            && (estado.value === "disponible" || estado.value === "mantenimiento")) {
            estado.value = "ocupada";
        }
        ajusteManualCheckOut.checked = false;
        reiniciarNuevaAmpliacion();
        actualizarTarifa({ recalcularHorarios: true });
        actualizarCamposEstadia();
    });
    estado.addEventListener("change", () => {
        limpiarError();
        actualizarCamposEstadia({ limpiar: true, calcularSiFalta: true });
    });
    ajusteManualCheckOut.addEventListener("change", () => {
        limpiarError();
        if (estado.value !== "ocupada") ajusteManualCheckOut.checked = false;
        if (!ajusteManualCheckOut.checked && estado.value === "ocupada") calcularHorarios();
        actualizarCamposEstadia();
        if (ajusteManualCheckOut.checked) checkOut.focus({ preventScroll: true });
    });
    checkOut.addEventListener("input", () => {
        limpiarError();
        actualizarResumenAmpliacion();
    });
    opcionesHorasAdicionales.forEach((opcion) => {
        opcion.addEventListener("change", () => {
            limpiarError();
            if (horasAdicionalesSeleccionadas() === 0) montoAdicional.value = "";
            actualizarControlesAmpliacion();
            if (!montoAdicional.disabled) montoAdicional.focus({ preventScroll: true });
        });
    });
    montoAdicional.addEventListener("input", () => {
        limpiarError();
        actualizarResumenAmpliacion();
    });
    pago.addEventListener("input", limpiarError);

    formulario.addEventListener("submit", (evento) => {
        limpiarError();
        if (!existeSeccion(seccion.value)) {
            evento.preventDefault();
            mostrarError("Selecciona una sección tarifaria registrada.", seccion);
            return;
        }
        if (!tiposServicio.has(tipoServicio.value)) {
            evento.preventDefault();
            mostrarError("Selecciona el servicio de 6 horas o día completo.", tipoServicio);
            return;
        }
        if (!estadosPermitidos.has(estado.value)) {
            evento.preventDefault();
            mostrarError("Selecciona un estado válido para la habitación.", estado);
            return;
        }
        if (!precio.value) {
            evento.preventDefault();
            mostrarError("La sección seleccionada no tiene un precio válido para este servicio.", tipoServicio);
            return;
        }

        const admiteEstadia = estadoConEstadia();
        if (!admiteEstadia) {
            checkIn.value = "";
            checkOut.value = "";
            pago.value = "false";
        } else if (!checkIn.value || !checkOut.value) {
            evento.preventDefault();
            mostrarError("No fue posible calcular los horarios. Vuelve a seleccionar el servicio.", tipoServicio);
            return;
        }
        if (ajusteManualCheckOut.checked && !checkOut.validity.valid) {
            evento.preventDefault();
            mostrarError("Ingresa una fecha y hora válidas para el check-out de prueba.", checkOut);
            return;
        }

        if (estado.value === "ocupada") {
            const documentos = new Set();
            for (const huesped of datosHuespedesActuales()) {
                const nombreValido = /^[\p{L}][\p{L} .'-]{1,99}$/u.test(huesped.nombre.trim());
                const documento = huesped.numeroDocumento.trim().toUpperCase();
                const regla = reglasDocumento[huesped.tipoDocumento];
                const clave = `${huesped.tipoDocumento}:${documento}`;
                if (!nombreValido || !regla || !regla.expresion.test(documento)) {
                    evento.preventDefault();
                    mostrarError(
                        regla?.ayuda || "Selecciona un tipo de documento válido.",
                        listaHuespedes.querySelector("input"));
                    return;
                }
                if (documentos.has(clave)) {
                    evento.preventDefault();
                    mostrarError(
                        "No puedes registrar dos veces el mismo documento.",
                        listaHuespedes.querySelector("input[name$='.numeroDocumento']"));
                    return;
                }
                documentos.add(clave);
            }
        }

        const horasNuevas = horasAdicionalesSeleccionadas();
        if (estado.value === "ocupada" && horasNuevas > 0) {
            const cargoNuevo = String(montoAdicional.value || "").trim();
            if (!/^\d+(?:\.\d{1,2})?$/.test(cargoNuevo)
                || numeroMonetario(cargoNuevo) <= 0 || !montoAdicional.validity.valid) {
                evento.preventDefault();
                mostrarError(
                    "Ingresa un monto adicional mayor que cero, con un máximo de dos decimales.",
                    montoAdicional);
                return;
            }
            montoAdicional.disabled = false;
        } else {
            montoAdicional.value = "";
            montoAdicional.disabled = true;
        }

        pago.disabled = false;
    });

    const abiertoPorServidor = modal.classList.contains("modal-habitacion--abierto");
    const errorServidor = mensajeError.textContent.trim()
        && !mensajeError.textContent.includes("{{");

    if (abiertoPorServidor) {
        if (errorServidor) seccion.setAttribute("aria-invalid", "true");
        abrirModal({
            conservarError: true,
            datos: {
                numero: numero.value,
                seccion: seccion.dataset.valorServidor,
                tipoServicio: tipoServicio.dataset.valorServidor,
                precio: precio.value,
                estado: estado.dataset.valorServidor,
                checkIn: checkIn.value,
                checkOut: checkOut.value,
                pagado: pago.dataset.valorServidor,
                montoAdicional: ampliacionEstadia.dataset.montoAdicionalAcumulado,
                montoTotal: ampliacionEstadia.dataset.montoTotalActual,
                horasAdicionales: ampliacionEstadia.dataset.horasAdicionalesAcumuladas,
                huespedes: huespedesServidor
            }
        });
    }
})();
