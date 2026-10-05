from pathlib import Path
import hashlib
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]
LEGAL = ROOT / 'app/src/main/assets/legal'


class LegalBundleTest(unittest.TestCase):
    def test_exact_runtime_inventory_is_bound_to_current_lock(self):
        inventory = json.loads((LEGAL / 'dependencies/inventory.json').read_text())
        self.assertEqual(inventory['lock_sha256'], hashlib.sha256((ROOT / 'app/gradle.lockfile').read_bytes()).hexdigest())
        for module in inventory['modules']:
            for artifact in module['artifacts']:
                for notice in artifact['notices']:
                    data = (LEGAL / 'dependencies' / (notice['sha256'] + '.txt')).read_bytes()
                    self.assertEqual(notice['sha256'], hashlib.sha256(data).hexdigest())

    def test_display_index_contains_all_preserved_notices_and_required_documents(self):
        documents = json.loads((LEGAL / 'documents.json').read_text())
        paths = {row['asset'] for row in documents}
        self.assertIn('legal/project-license.txt', paths)
        self.assertIn('legal/project-notice.txt', paths)
        self.assertIn('legal/components.txt', paths)
        self.assertIn('legal/upstream/bebas-neue-OFL.txt', paths)
        self.assertIn('legal/upstream/liberation-LICENSE.txt', paths)
        self.assertIn('legal/upstream/pdfbox-LICENSE.txt', paths)
        for notice in (LEGAL / 'dependencies').glob('*.txt'):
            self.assertIn('legal/dependencies/' + notice.name, paths)
        for row in documents:
            self.assertNotIn('..', row['asset'].split('/'))
            self.assertTrue((LEGAL.parent / row['asset']).is_file(), row['asset'])

    def test_current_project_notices_and_privacy_are_identical_to_public_documents(self):
        self.assertEqual((ROOT / 'LICENSE').read_bytes(), (LEGAL / 'project-license.txt').read_bytes())
        self.assertEqual((ROOT / 'NOTICE').read_bytes(), (LEGAL / 'project-notice.txt').read_bytes())
        self.assertEqual((ROOT / 'docs/privacidad.md').read_bytes(), (LEGAL / 'privacy.txt').read_bytes())

    def test_every_runtime_component_has_a_reviewed_license_route(self):
        mappings = json.loads((LEGAL / 'component-licenses.json').read_text())
        inventory = json.loads((LEGAL / 'dependencies/inventory.json').read_text())
        self.assertEqual({m['coordinate'] for m in inventory['modules']}, set(mappings))
        for coordinate, docs in mappings.items():
            self.assertTrue(docs, coordinate)
            for path in docs:
                self.assertTrue((LEGAL.parent / path).is_file(), (coordinate, path))
