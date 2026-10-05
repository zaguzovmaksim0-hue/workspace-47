"""Rebuild the human-readable index over reviewed, vendored license evidence."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
LEGAL = ROOT / 'app/src/main/assets/legal'


def build():
    inventory = json.loads((LEGAL / 'dependencies/inventory.json').read_text())
    docs = [
        {'title': 'Firma Mobile — Apache-2.0', 'asset': 'legal/project-license.txt'},
        {'title': 'Firma Mobile — Avisos y atribuciones', 'asset': 'legal/project-notice.txt'},
        {'title': 'Índice de componentes y condiciones', 'asset': 'legal/components.txt'},
    ]
    titles = {
        'Apache-2.0.txt': 'Apache License 2.0 — texto común de bibliotecas',
        'bebas-neue-OFL.txt': 'Bebas Neue — SIL Open Font License',
        'liberation-LICENSE.txt': 'Liberation Sans — SIL Open Font License',
        'pdfbox-LICENSE.txt': 'PDFBox Android — licencia y componentes adicionales',
        'pdfbox-NOTICE.txt': 'PDFBox Android — avisos de autoría',
        'slf4j-LICENSE.txt': 'SLF4J — licencia MIT',
        'stax2-BSD-2-Clause.txt': 'Stax2 API — BSD de dos cláusulas',
        'google-sdk-terms.txt': 'Componentes Google — condiciones de SDK',
    }
    docs.extend({'title': title, 'asset': 'legal/upstream/' + name} for name, title in titles.items())
    owners = {}
    mappings = {}
    for module in inventory['modules']:
        coordinate = module['coordinate']
        routes = []
        for artifact in module['artifacts']:
            for notice in artifact['notices']:
                asset = 'legal/dependencies/' + notice['sha256'] + '.txt'
                routes.append(asset)
                owners.setdefault(asset, set()).add(coordinate)
        pom_names = {lic['name'].lower() for pom in module['poms'] for lic in pom['licenses']}
        if any('apache' in name for name in pom_names) or coordinate.startswith(('com.google.guava:', 'commons-codec:')):
            routes.append('legal/upstream/Apache-2.0.txt')
        if coordinate.startswith(('com.google.android.gms:', 'com.google.android.libraries.identity.googleid:')):
            routes.append('legal/upstream/google-sdk-terms.txt')
        if coordinate.startswith('org.slf4j:'):
            routes.append('legal/upstream/slf4j-LICENSE.txt')
        if coordinate.startswith('org.codehaus.woodstox:'):
            routes.append('legal/upstream/stax2-BSD-2-Clause.txt')
        if coordinate.startswith('com.tom-roush:'):
            routes += ['legal/upstream/pdfbox-LICENSE.txt', 'legal/upstream/pdfbox-NOTICE.txt',
                       'legal/upstream/liberation-LICENSE.txt']
        if not routes:
            raise ValueError('No reviewed route for ' + coordinate)
        mappings[coordinate] = sorted(set(routes))
    for index, (asset, components) in enumerate(sorted(owners.items()), 1):
        label = sorted(components)[0]
        if len(components) > 1:
            label += f' (+{len(components) - 1} componentes)'
        docs.append({'title': f'Aviso {index:02d} — {label}', 'asset': asset})
    (LEGAL / 'documents.json').write_text(json.dumps(docs, ensure_ascii=False, indent=2) + '\n')
    (LEGAL / 'component-licenses.json').write_text(json.dumps(mappings, ensure_ascii=False, indent=2) + '\n')
    text = ['Componentes de Firma Mobile',
            'Inventario vinculado al bloqueo de dependencias. Los módulos de metadatos pueden no contener código binario.',
            'La licencia de Firma Mobile no sustituye los términos de estas bibliotecas.',
            'Bebas Neue: copyright 2019 The Bebas Neue Project Authors; aviso OFL upstream: Dharma Type 2010.',
            'Liberation Sans: datos digitalizados copyright 2010 Google Corporation; copyright 2012 Red Hat, Inc.',
            'Fuentes de avisos adicionales: PDFBox Android v2.0.27.0 (LICENSE.txt y NOTICE.txt), SLF4J v_1.7.36 (LICENSE.txt), Liberation Fonts (LICENSE).',
            'Los avisos de dependencias se preservan íntegros, incluso cuando contienen referencias a herramientas o componentes no usados directamente por esta aplicación.']
    for coordinate, routes in sorted(mappings.items()):
        text.append(coordinate + '\n' + '\n'.join(routes))
    (LEGAL / 'components.txt').write_text('\n\n'.join(text) + '\n')


if __name__ == '__main__':
    build()
