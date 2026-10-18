"""Standalone XML contract checks for the English Android resources (no SDK)."""

from collections import Counter
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
DEFAULT = ROOT / "app/src/main/res/values"
ENGLISH = ROOT / "app/src/main/res/values-en"
RESOURCE_TYPES = {"string", "plurals", "string-array"}
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[tT]?[a-zA-Z%]")


def parse_xml(text):
    """Reject external declarations and processing instructions, not XML markup."""
    if re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", text, re.IGNORECASE):
        raise ValueError("Resource XML must not declare a DTD or entity")
    without_declaration = re.sub(r"^\s*<\?xml\s[^?]*\?>", "", text, count=1)
    if "<?" in without_declaration:
        raise ValueError("Resource XML must not contain processing instructions")
    root = ET.fromstring(text)
    if root.tag != "resources":
        raise ValueError("Expected an Android resources root")
    return root


def entries(root, translatable_only=False):
    return [
        node for node in root
        if node.tag in RESOURCE_TYPES
        and (not translatable_only or node.get("translatable") != "false")
    ]


def key(node):
    return node.tag, node.get("name")


def text_items(node):
    return [node] if node.tag == "string" else list(node)


def text_of(node):
    return "".join(node.itertext())


def format_signature(text):
    indexed, ordered = Counter(), []
    for match in FORMAT.finditer(text):
        if match.group(1):
            indexed[match.group()] += 1
        else:
            ordered.append(match.group())
    return tuple(sorted(indexed.items())), tuple(ordered), text.count("%")


def markup_shape(node):
    return node.tag, tuple(sorted(node.attrib.items())), tuple(
        markup_shape(child) for child in node
    )


def valid_android_escaping(text):
    quoted = len(text) >= 2 and text.startswith('"') and text.endswith('"')
    if quoted:
        text = text[1:-1]
    index = 0
    while index < len(text):
        char = text[index]
        if char == "\\":
            index += 1
            if index >= len(text):
                return False
            escape = text[index]
            if escape == "u":
                if not re.fullmatch(r"[0-9a-fA-F]{4}", text[index + 1:index + 5]):
                    return False
                index += 4
            elif escape not in "\\\'\"ntr@?":
                return False
        elif char == '"' or (char == "'" and not quoted):
            return False
        index += 1
    return True


class EnglishResourceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.default = {}
        for path in sorted(DEFAULT.glob("*.xml")):
            source = entries(parse_xml(path.read_text(encoding="utf-8")), True)
            if source:
                cls.default[path.name] = source
        cls.english = {
            path.name: parse_xml(path.read_text(encoding="utf-8"))
            for path in sorted(ENGLISH.glob("*.xml"))
        }

    def pairs(self):
        self.assertTrue(self.default, "No default translatable resources found")
        self.assertEqual(set(self.default), set(self.english), "English file groups differ")
        for filename, source in self.default.items():
            target = list(self.english[filename])
            self.assertTrue(all(node.tag in RESOURCE_TYPES for node in target), filename)
            source_map = {key(node): node for node in source}
            target_map = {key(node): node for node in target}
            self.assertEqual(len(target), len(target_map), f"Duplicate key in {filename}")
            self.assertEqual(set(source_map), set(target_map), filename)
            for resource_key, original in source_map.items():
                yield filename, original, target_map[resource_key]

    def test_exact_source_file_groups(self):
        self.assertTrue(self.default)
        self.assertEqual(set(self.default), set(self.english))

    def test_exact_translatable_keys_and_types_without_duplicates(self):
        seen = set()
        for filename, source, target in self.pairs():
            with self.subTest(file=filename, resource=source.get("name")):
                self.assertNotIn(key(target), seen)
                seen.add(key(target))
                self.assertNotEqual(target.get("translatable"), "false")
                self.assertEqual(source.attrib, target.attrib)

    def test_plural_quantities_and_array_items(self):
        for filename, source, target in self.pairs():
            with self.subTest(file=filename, resource=source.get("name")):
                if source.tag != "string":
                    self.assertTrue(all(child.tag == "item" for child in target))
                    self.assertEqual(len(source), len(target))
                    self.assertEqual([x.attrib for x in source], [x.attrib for x in target])
                    if source.tag == "plurals":
                        quantities = [x.get("quantity") for x in target]
                        self.assertEqual(len(quantities), len(set(quantities)))
                        self.assertIn("other", quantities)

    def test_format_signatures_for_every_text_item(self):
        for filename, source, target in self.pairs():
            for index, (original, localized) in enumerate(zip(text_items(source), text_items(target))):
                with self.subTest(file=filename, resource=source.get("name"), item=index):
                    self.assertEqual(format_signature(text_of(original)), format_signature(text_of(localized)))

    def test_markup_xliff_and_resource_references_are_preserved(self):
        for filename, source, target in self.pairs():
            with self.subTest(file=filename, resource=source.get("name")):
                self.assertEqual(markup_shape(source), markup_shape(target))
                for original, localized in zip(text_items(source), text_items(target)):
                    if text_of(original).strip().startswith(("@", "?")):
                        self.assertEqual(text_of(original), text_of(localized))

    def test_product_protocol_names_and_literal_links_are_preserved(self):
        protected = (
            "Firma Mobile", "AutoFirma", "Android", "WebView", "GitHub",
            "Google Play", "Google Password Manager", "WebAuthn", "Passkeys",
            "CAdES", "PDF", "PRE", "RSA", "TLS", "HTTPS", "https://",
            "SHA-1", "SHA-256", "X.509", "PKCS#12", ".p12", ".pfx", "32 MiB",
            "github.com/zaguzovmaksim0-hue/workspace-47/issues/new",
        )
        for filename, source, target in self.pairs():
            for original, localized in zip(text_items(source), text_items(target)):
                with self.subTest(file=filename, resource=source.get("name")):
                    for term in protected:
                        self.assertEqual(text_of(original).count(term), text_of(localized).count(term), term)

    def test_nonempty_text_and_android_escaping(self):
        for filename, source, target in self.pairs():
            for index, (original, localized) in enumerate(zip(text_items(source), text_items(target))):
                with self.subTest(file=filename, resource=source.get("name"), item=index):
                    text = text_of(localized)
                    if text_of(original).strip():
                        self.assertTrue(text.strip())
                    self.assertTrue(valid_android_escaping(text), "Invalid Android string escaping")


class ResourceSafetyHelpersTest(unittest.TestCase):
    def test_indexed_format_arguments_can_be_reordered(self):
        self.assertEqual(format_signature("%1$d files: %2$s"), format_signature("%2$s: %1$d files"))

    def test_format_signature_rejects_type_index_and_format_changes(self):
        source = format_signature("%1$d %2$.2f %%")
        for altered in ("%1$s %2$.2f %%", "%3$d %2$.2f %%", "%1$d %2$.3f %%", "%1$d %2$.2f"):
            with self.subTest(altered=altered):
                self.assertNotEqual(source, format_signature(altered))
        self.assertNotEqual(format_signature("%s %d"), format_signature("%d %s"))

    def test_markup_shape_preserves_xliff_ids_and_html_attributes(self):
        source = ET.fromstring('<string xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2" name="sample">Hi <b>there</b> <xliff:g id="host">%1$s</xliff:g></string>')
        translated = ET.fromstring('<string xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2" name="sample">Hello <b>again</b> <xliff:g id="host">%1$s</xliff:g></string>')
        self.assertEqual(markup_shape(source), markup_shape(translated))
        translated[1].set("id", "url")
        self.assertNotEqual(markup_shape(source), markup_shape(translated))
        translated[0].set("onclick", "unexpected")
        self.assertNotEqual(markup_shape(source), markup_shape(translated))

    def test_android_escaping_accepts_escaped_and_rejects_raw_apostrophes(self):
        for text in (r"Server\'s certificate", r'Use \"Open\"', r"Line\nnext", r"\u00a0", "“Open”"):
            with self.subTest(valid=text):
                self.assertTrue(valid_android_escaping(text))
        for text in ("Server's certificate", 'Use "Open"', "trailing\\", r"\q", r"\u123"):
            with self.subTest(invalid=text):
                self.assertFalse(valid_android_escaping(text))

    def test_xml_parser_rejects_dtd_entities_and_processing_instructions(self):
        for text in (
            '<!DOCTYPE resources [<!ENTITY external SYSTEM "file:///unavailable">]><resources/>',
            '<?instruction unsafe?><resources/>',
            '<string name="wrong_root">Text</string>',
        ):
            with self.subTest(xml=text):
                self.assertRaises(ValueError, parse_xml, text)
        self.assertEqual(parse_xml('<?xml version="1.0" encoding="utf-8"?><resources/>').tag, "resources")


if __name__ == "__main__":
    unittest.main()
