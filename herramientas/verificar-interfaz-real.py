"""Prueba la interfaz servida por el JAR con Chrome y CDP, sin paquetes externos.

Inicia una instancia aislada, inicia sesión por HTTP y navega por los módulos reales.
No usa ni modifica la instancia del usuario en el puerto 8080.
"""
from pathlib import Path
import base64
import http.cookiejar
import json
import os
import socket
import struct
import subprocess
import time
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.compilacion/interfaz-real'
OUT.mkdir(parents=True, exist_ok=True)
CHROME = Path(r'C:\Program Files\Google\Chrome\Application\chrome.exe')
JAVA = Path(r'C:\Program Files\Java\jdk-17\bin\java.exe')


def puerto_libre():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def esperar(funcion, limite=15):
    fin = time.monotonic() + limite
    while time.monotonic() < fin:
        try:
            resultado = funcion()
            if resultado:
                return resultado
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(.1)
    raise TimeoutError('La página o el proceso no respondió a tiempo')


class CDP:
    """Cliente WebSocket mínimo para el protocolo local de Chrome."""
    def __init__(self, url):
        parsed = urllib.parse.urlsplit(url)
        self.sock = socket.create_connection((parsed.hostname, parsed.port), timeout=15)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((f'GET {parsed.path} HTTP/1.1\r\nHost: {parsed.netloc}\r\n'
                           f'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n'
                           'Sec-WebSocket-Version: 13\r\n\r\n').encode())
        header = b''
        while not header.endswith(b'\r\n\r\n'):
            header += self.sock.recv(1)
        assert header.startswith(b'HTTP/1.1 101 '), header
        self.number = 0
        self.errors = []

    def read(self, count):
        data = b''
        while len(data) < count:
            chunk = self.sock.recv(count - len(data))
            if not chunk:
                raise ConnectionError('Chrome cerró la conexión')
            data += chunk
        return data

    def call(self, method, **params):
        self.number += 1
        number = self.number
        payload = json.dumps(dict(id=number, method=method, params=params)).encode()
        mask = os.urandom(4)
        length = len(payload)
        header = bytes([0x81, 0x80 | length]) if length < 126 else bytes([0x81, 0xfe]) + struct.pack('!H', length)
        self.sock.sendall(header + mask + bytes(c ^ mask[i % 4] for i, c in enumerate(payload)))
        while True:
            opcode, size = self.read(2)
            size &= 127
            if size == 126:
                size = struct.unpack('!H', self.read(2))[0]
            elif size == 127:
                size = struct.unpack('!Q', self.read(8))[0]
            message = json.loads(self.read(size))
            if message.get('method') == 'Runtime.exceptionThrown':
                self.errors.append(message['params']['exceptionDetails'].get('text'))
            if message.get('id') == number:
                if 'error' in message:
                    raise RuntimeError(message['error'])
                return message.get('result', {})

    def js(self, expression):
        response = self.call('Runtime.evaluate', expression=expression, returnByValue=True, awaitPromise=True)
        if 'exceptionDetails' in response:
            raise RuntimeError(response['exceptionDetails'])
        return response.get('result', {}).get('value')

    def navigate(self, url):
        self.call('Page.navigate', url=url)
        esperar(lambda: self.js('location.href') == url and self.js('document.readyState') == 'complete')


def main():
    port = puerto_libre()
    debug_port = puerto_libre()
    base = f'http://127.0.0.1:{port}'
    access_key = uuid.uuid4().hex
    hotel_key = 'PruebaHotel8'
    log = (OUT / 'servidor.log').open('w', encoding='utf-8')
    server = subprocess.Popen([str(JAVA), f'-Dgestera.puerto={port}', f'-Dgestera.llave.acceso={access_key}',
                               f'-Dgestera.llave.hotel={hotel_key}', '-jar', str(ROOT / 'distribucion/gestera.jar'),
                               '--spring.profiles.active=memoria'], cwd=ROOT, stdout=log, stderr=log)
    chrome = None
    cdp = None
    results = []
    try:
        esperar(lambda: urllib.request.urlopen(base, timeout=1).status == 200)
        cookies = http.cookiejar.CookieJar()
        client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
        def post(path, values):
            return client.open(base + path, urllib.parse.urlencode(values).encode()).read()
        post('/acceso', dict(llave=access_key))
        post('/hotel/acceso', dict(hotel='sol-pucusana', llaveHotel=hotel_key))
        for path in ['/publico/estilos/gestera.css', '/publico/scripts/tema.js', '/publico/imagenes/hotel-costa.png']:
            data = client.open(base + path).read()
            assert data == (ROOT / 'frontend' / path.lstrip('/')).read_bytes(), path
        chrome = subprocess.Popen([str(CHROME), '--headless', '--no-sandbox', '--disable-gpu', '--no-first-run',
                                   '--no-default-browser-check', f'--remote-debugging-port={debug_port}',
                                   f'--user-data-dir={OUT / ("perfil-" + uuid.uuid4().hex)}', 'about:blank'],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        def target():
            with urllib.request.urlopen(f'http://127.0.0.1:{debug_port}/json', timeout=1) as r:
                return next((t for t in json.load(r) if t['type'] == 'page'), None)
        cdp = CDP(esperar(target)['webSocketDebuggerUrl'])
        cdp.call('Page.enable')
        cdp.call('Runtime.enable')
        cdp.call('Network.enable')
        # Almacenamiento vacío: se debe recuperar el tema del sistema antes del CSS.
        cdp.navigate(base + '/')
        for dark in (False, True):
            cdp.js('localStorage.removeItem("gestera.tema")')
            cdp.call('Emulation.setEmulatedMedia', features=[dict(name='prefers-color-scheme', value='dark' if dark else 'light')])
            cdp.call('Page.reload')
            expected = 'oscuro' if dark else 'claro'
            esperar(lambda: cdp.js('document.readyState') == 'complete' and cdp.js('document.documentElement.dataset.tema') == expected)
        routes = ['/', '/panel', '/hotel', '/hotel/limpieza', '/hotel/habitaciones', '/hotel/huespedes',
                  '/hotel/servicio-habitacion', '/hotel/areas-comunes', '/hotel/reportes', '/hotel/configuracion']
        for theme in ('claro', 'oscuro'):
            cdp.js(f'localStorage.setItem("gestera.tema", "{theme}")')
            cdp.call('Network.clearBrowserCookies')
            cdp.navigate(base + '/')
            cdp.call('Page.reload')
            esperar(lambda: cdp.js('document.readyState') == 'complete' and cdp.js('document.documentElement.dataset.tema') == theme)
            for width in (390, 820, 1440):
                cdp.call('Emulation.setDeviceMetricsOverride', width=width, height=1000, deviceScaleFactor=1, mobile=False)
                for route in routes:
                    if route == '/':
                        cdp.call('Network.clearBrowserCookies')
                    elif route == '/panel':
                        for cookie in cookies:
                            cdp.call('Network.setCookie', name=cookie.name, value=cookie.value, url=base,
                                     path='/', httpOnly=True, sameSite='Strict')
                    cdp.navigate(base + route)
                    state = cdp.js('''(() => ({tema: document.documentElement.dataset.tema,
                        selector: !!document.querySelector('[data-alternar-tema]'),
                        overflow: document.documentElement.scrollWidth > innerWidth,
                        fondo: getComputedStyle(document.body).backgroundColor,
                        css: !!document.querySelector('link[rel="stylesheet"]').sheet,
                        version: /\\?v=[a-f0-9]{12}/.test(document.querySelector('link[rel="stylesheet"]').href)
                    }))()''')
                    assert state['tema'] == theme and state['selector'] and state['css'] and state['version'], (route, state)
                    assert not state['overflow'], (route, width, state)
                    assert state['fondo'] == ('rgb(4, 17, 38)' if theme == 'oscuro' else 'rgb(255, 249, 241)'), state
                    cdp.js("document.querySelector('[data-alternar-tema]').click()")
                    assert cdp.js('document.documentElement.dataset.tema') != theme
                    cdp.js("document.querySelector('[data-alternar-tema]').click()")
                    assert cdp.js('localStorage.getItem("gestera.tema")') == theme
                    if route in ('/hotel', '/hotel/reportes', '/panel') and width in (390, 1440):
                        time.sleep(.3)
                        shot = cdp.call('Page.captureScreenshot', format='png', captureBeyondViewport=True)
                        name = f'{route.strip("/").replace("/", "-")}-{theme}-{width}.png'
                        (OUT / name).write_bytes(base64.b64decode(shot['data']))
                    if route == '/hotel':
                        cdp.js("document.querySelector('#abrir-cambio-llave').click()")
                        assert cdp.js("document.querySelector('#modal-cambio-llave').open")
                        assert cdp.js("getComputedStyle(document.querySelector('#modal-cambio-llave')).color") == ('rgb(245, 249, 255)' if theme == 'oscuro' else 'rgb(16, 26, 51)')
                        cdp.js("document.querySelector('#modal-cambio-llave').close()")
                    results.append(dict(route=route, width=width, theme=theme, result='PASS'))
                print(f'PASS: {theme}, {width}px, 10 rutas reales', flush=True)
        assert not cdp.errors, cdp.errors
        cdp.call('Emulation.setEmulatedMedia', features=[dict(name='prefers-reduced-motion', value='reduce')])
        assert cdp.js("getComputedStyle(document.querySelector('.selector-tema')).transitionDuration") in ('0s', '1e-05s')
        print('PASS: preferencia del sistema, persistencia entre módulos, diálogos y recursos del JAR', flush=True)
        (OUT / 'resultados.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    finally:
        if cdp:
            cdp.sock.close()
        if chrome:
            chrome.terminate()
            chrome.wait(timeout=15)
        server.terminate()
        server.wait(timeout=15)
        log.close()


if __name__ == '__main__':
    main()
