"""Comprueba la coherencia de POM, README, changelog y etiqueta de entrega."""
import argparse
from datetime import date
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
VERSION = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'
RELEASE = re.compile(r'^## \[(' + VERSION + r')\] - ([0-9]{4}-[0-9]{2}-[0-9]{2})$', re.MULTILINE)


def verificar(etiqueta=None):
    namespaces = {'m': 'http://maven.apache.org/POM/4.0.0'}
    project = ET.parse(ROOT / 'pom.xml').getroot()
    version = (project.findtext('m:version', namespaces=namespaces) or '').strip()
    if not re.fullmatch(VERSION, version):
        raise ValueError('project/version debe tener el formato MAYOR.MENOR.PARCHE.')

    changelog = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')
    releases = RELEASE.findall(changelog)
    if not releases or releases[0][0] != version:
        raise ValueError('La version mas reciente de CHANGELOG.md debe coincidir con pom.xml.')

    versions = [tuple(map(int, number.split('.'))) for number, _ in releases]
    if len(set(versions)) != len(versions):
        raise ValueError('CHANGELOG.md contiene versiones duplicadas.')
    if versions != sorted(versions, reverse=True):
        raise ValueError('Las versiones del changelog deben estar ordenadas de mayor a menor.')
    dates = [date.fromisoformat(release_date) for _, release_date in releases]
    if dates != sorted(dates, reverse=True):
        raise ValueError('Las fechas del changelog deben estar ordenadas de mas reciente a mas antigua.')

    readme = (ROOT / 'README.md').read_text(encoding='utf-8')
    displayed = re.search(r'\*\*Versión actual: (' + VERSION + r')\.\*\*', readme)
    if displayed is None or displayed.group(1) != version:
        raise ValueError('La version visible del README.md debe coincidir con pom.xml.')
    if etiqueta is not None and etiqueta != 'v' + version:
        raise ValueError(f'La etiqueta debe ser v{version}; se recibio {etiqueta}.')
    return version


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--etiqueta', help='Etiqueta Git de la entrega, por ejemplo v1.1.0.')
    args = parser.parse_args()
    try:
        version = verificar(args.etiqueta)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f'ERROR de versionamiento: {error}', file=sys.stderr)
        return 1
    print(f'Version {version}: POM, README y changelog sincronizados.')
    if args.etiqueta is not None:
        print(f'Etiqueta {args.etiqueta} valida para esta entrega.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
