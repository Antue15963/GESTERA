"""Verificación visual en Chrome headless sin dependencias adicionales."""
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
import threading
import subprocess
import re
import json

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
OUT = ROOT / '.compilacion/temas'
OUT.mkdir(parents=True, exist_ok=True)
CHECK = '''
document.addEventListener('DOMContentLoaded', () => {
  const problemas = [];
  const verificar = (ok, msg) => { if (!ok) problemas.push(msg); };
  const tema = document.documentElement.dataset.tema;
  const esperado = new URLSearchParams(location.search).get('tema');
  if (!sessionStorage.getItem('recargado')) verificar(tema === esperado, 'preferencia inicial');
  verificar(['claro', 'oscuro'].includes(tema), 'tema inicial');
  verificar(getComputedStyle(document.body).color !== getComputedStyle(document.body).backgroundColor, 'contraste');
  verificar(document.documentElement.scrollWidth <= innerWidth, 'desbordamiento horizontal');
  const boton = document.querySelector('[data-alternar-tema]');
  verificar(!!boton, 'selector ausente');
  boton.click();
  verificar(document.documentElement.dataset.tema !== tema, 'cambio de tema');
  verificar(localStorage.getItem('gestera.tema') === document.documentElement.dataset.tema, 'persistencia');
  verificar(boton.getAttribute('aria-label').includes('Activar modo'), 'nombre accesible');
  if (!sessionStorage.getItem('recargado')) {
    sessionStorage.setItem('recargado', localStorage.getItem('gestera.tema'));
    location.reload(); return;
  }
  verificar(tema === sessionStorage.getItem('recargado'), 'restaurar después de recargar');
  sessionStorage.removeItem('recargado');
  const resultado = document.createElement('pre');
  resultado.id = 'verificacion';
  resultado.textContent = problemas.length ? 'FAIL: ' + problemas.join(', ') : 'PASS';
  document.body.append(resultado);
});
'''

class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_GET(self):
        route = self.path.split('?')[0]
        if route == '/check.js':
            data, tipo = CHECK.encode(), 'text/javascript'
        elif route == '/setup.js':
            data = b"if (!sessionStorage.getItem('recargado')) localStorage.setItem('gestera.tema', new URLSearchParams(location.search).get('tema'));"
            tipo = 'text/javascript'
        elif route.startswith('/vista/'):
            name = route.rsplit('/', 1)[1]
            text = (RES / 'paginas' / name).read_text(encoding='utf-8')
            fragment = (RES / 'fragmentos/configuracion-llave.html').read_text(encoding='utf-8')
            text = text.replace('{{CONFIGURACION_LLAVE_REQUERIDA}}', fragment)
            text = text.replace('{{NOMBRE_HOTEL}}', 'Hotel El Sol de Pucusana')
            text = re.sub(r'\{\{[A-Z_]+\}\}', '', text)
            text = text.replace('</body>', '<script src="/check.js"></script></body>')
            text = text.replace('<script src="/publico/scripts/tema.js">', '<script src="/setup.js"></script><script src="/publico/scripts/tema.js">')
            data, tipo = text.encode(), 'text/html; charset=utf-8'
        elif route.startswith('/publico/'):
            path = RES / route.lstrip('/')
            if not path.is_file(): self.send_error(404); return
            data = path.read_bytes()
            tipo = 'text/css' if path.suffix == '.css' else 'text/javascript' if path.suffix == '.js' else 'image/png'
        else:
            self.send_error(404); return
        self.send_response(200)
        self.send_header('Content-Type', tipo)
        self.end_headers()
        self.wfile.write(data)

server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
chrome = r'C:\Program Files\Google\Chrome\Application\chrome.exe'
results = []
try:
    for page in sorted((RES / 'paginas').glob('*.html')):
        for width in (390, 1440):
            for theme in ('light', 'dark'):
                args = [chrome, '--headless', '--no-sandbox', '--disable-gpu', '--no-first-run',
                        '--no-default-browser-check', f'--user-data-dir={OUT / "perfil"}',
                        f'--window-size={width},1000', '--virtual-time-budget=1800', '--dump-dom']
                if theme == 'dark': args.append('--force-dark-mode')
                url = f'http://127.0.0.1:{server.server_port}/vista/{page.name}?tema={"claro" if theme == "light" else "oscuro"}'
                run = subprocess.run(args + [url], capture_output=True, timeout=40)
                dom = run.stdout.decode('utf-8', errors='replace')
                match = re.search(r'<pre id="verificacion">(.*?)</pre>', dom)
                result = match.group(1) if match else 'FAIL: sin resultado del navegador'
                results.append(dict(page=page.name, width=width, theme=theme, result=result))
                print(page.name, width, theme, result, flush=True)
                if page.name == 'gestion.html' and width == 1440:
                    subprocess.run([a for a in args if a != '--dump-dom'] +
                                   [f'--screenshot={OUT / (theme + ".png")}', url], capture_output=True, timeout=40)
    (OUT / 'resultados.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
finally:
    server.shutdown()
if any(r['result'] != 'PASS' for r in results): raise SystemExit(1)
