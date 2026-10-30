/* Ejecutado antes del CSS para recuperar el tema sin destellos. */
(() => {
    'use strict';
    const raiz = document.documentElement;
    const sistema = window.matchMedia('(prefers-color-scheme: dark)');
    let guardado;
    try { guardado = localStorage.getItem('gestera.tema'); } catch (_) { /* Almacenamiento restringido. */ }
    let preferencia = ['claro', 'oscuro'].includes(guardado) ? guardado : null;
    function aplicar(tema) {
        raiz.dataset.tema = tema;
        const claro = tema === 'claro';
        document.querySelectorAll('[data-alternar-tema]').forEach(boton => {
            const etiqueta = claro ? 'Activar modo oscuro' : 'Activar modo claro';
            boton.setAttribute('aria-label', etiqueta);
            boton.title = etiqueta;
            boton.querySelector('span').textContent = claro ? '☾' : '☀';
        });
        const meta = document.querySelector('meta[name="theme-color"]');
        if (meta) meta.content = claro ? '#fff9f1' : '#041126';
    }
    const inicial = () => preferencia || (sistema.matches ? 'oscuro' : 'claro');
    aplicar(inicial());
    document.addEventListener('DOMContentLoaded', () => {
        aplicar(inicial());
        document.querySelectorAll('[data-alternar-tema]').forEach(boton => {
            boton.addEventListener('click', () => {
                preferencia = raiz.dataset.tema === 'oscuro' ? 'claro' : 'oscuro';
                raiz.classList.add('tema-transicion');
                aplicar(preferencia);
                try { localStorage.setItem('gestera.tema', preferencia); } catch (_) { /* El tema sigue disponible. */ }
                window.setTimeout(() => raiz.classList.remove('tema-transicion'), 250);
            });
        });
    });
    sistema.addEventListener('change', () => { if (!preferencia) aplicar(inicial()); });
    window.addEventListener('storage', evento => {
        if (evento.key === 'gestera.tema') {
            preferencia = ['claro', 'oscuro'].includes(evento.newValue) ? evento.newValue : null;
            aplicar(inicial());
        }
    });
})();
