"""Catalan resource-groundwork contracts; no Android or network dependency."""
from collections import Counter
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
KINDS = {"string", "plurals", "string-array"}
FORMAT = re.compile(
    r"%(?:(?P<position>[1-9]\d*)\$)?(?P<flags>[-#+ 0,(<]*)"
    r"(?P<width>\d+)?(?:\.(?P<precision>\d+))?"
    r"(?P<conversion>[tT][a-zA-Z]|[a-zA-Z%])"
)
INLINE_TAG = re.compile(r"</?[A-Za-z][^>]*>")
PRODUCTS = (
    "Firma Mobile", "AutoFirma", "Android", "WebView", "GitHub", "WebAuthn",
    "Google Password Manager", "Junta de Andalucía", "CAdES", "XAdES", "PAdES",
    "PKCS#12", "PKCS12", "HTTPS", "HTTP", "TLS", "RSA", "SHA-1", "SHA-256",
    "JavaScript", "Base64", "UTF-8", "PDF", "API", "PRE", "POST", "MiB",
)


def load_files(directory, *, localized=False):
    files = {}
    for path in sorted(directory.glob("*.xml")):
        raw = path.read_text(encoding="utf-8")
        if "<!DOCTYPE" in raw or "<!ENTITY" in raw:
            raise ValueError(f"External entities/DTD are not resource text: {path.name}")
        root = ET.fromstring(raw)
        if root.tag != "resources":
            raise ValueError(f"Expected resources root: {path.name}")
        entries = {}
        for element in root:
            if element.tag not in KINDS:
                if localized:
                    raise ValueError(f"Unexpected resource type: {element.tag}")
                continue
            if not localized and element.get("translatable", "true") == "false":
                continue
            key = element.get("name")
            if not key or key in entries:
                raise ValueError(f"Missing/duplicate resource name in {path.name}: {key}")
            entries[key] = element
        if entries or localized:
            files[path.name] = entries
    return files


def value_nodes(element):
    return [element] if element.tag == "string" else list(element)


def text_of(element):
    return "".join(element.itertext())


def value_shape(element):
    return element.tag, tuple((item.tag, tuple(sorted(item.attrib.items())))
                              for item in element if element.tag != "string")


def format_signature(text):
    signature = Counter()
    implicit = 0
    previous = None
    matches = list(FORMAT.finditer(text))
    for token in matches:
        flags = token.group("flags")
        conversion = token.group("conversion")
        if conversion in {"%", "n"}:
            position = 0
        elif "<" in flags:
            if previous is None:
                raise ValueError("Relative formatter has no previous argument")
            position = previous
        elif token.group("position"):
            position = int(token.group("position"))
        else:
            implicit += 1
            position = implicit
        if conversion not in {"%", "n"}:
            previous = position
        signature[(position, flags.replace("<", ""), token.group("width"),
                   token.group("precision"), conversion)] += 1
    signature[("literal-percent",)] = text.count("%") - sum(
        token.group().count("%") for token in matches
    )
    return signature


def markup_signature(element):
    return tuple((child.tag, tuple(sorted(child.attrib.items())), markup_signature(child))
                 for child in element)


def has_unescaped_quote(text):
    return any(len(match.group(1)) % 2 == 0
               for match in re.finditer(r"(\\*)['\"]", text))


class CatalanResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = load_files(RES / "values")
        cls.catalan = load_files(RES / "values-ca", localized=True)

    def pairs(self):
        for filename, entries in self.catalan.items():
            for key, translated in entries.items():
                source = self.source.get(filename, {}).get(key)
                if source is not None:
                    yield filename, key, source, translated

    def test_complete_translatable_keys_grouped_by_source_filename(self):
        self.assertEqual(
            {name: set(entries) for name, entries in self.source.items()},
            {name: set(entries) for name, entries in self.catalan.items()},
        )

    def test_no_nontranslatable_or_other_resource_kinds_are_copied(self):
        for filename, entries in self.catalan.items():
            self.assertIn(filename, self.source)
            for key, element in entries.items():
                with self.subTest(file=filename, key=key):
                    self.assertIn(key, self.source[filename])
                    self.assertIn(element.tag, KINDS)
                    self.assertNotEqual("false", element.get("translatable"))

    def test_types_attributes_plural_quantities_and_array_items_match(self):
        for filename, key, source, translated in self.pairs():
            with self.subTest(file=filename, key=key):
                self.assertEqual(source.attrib, translated.attrib)
                self.assertEqual(value_shape(source), value_shape(translated))
                if source.tag == "plurals":
                    quantities = [item.get("quantity") for item in translated]
                    self.assertIn("other", quantities)
                    self.assertEqual(len(quantities), len(set(quantities)))
                self.assertEqual(len(value_nodes(source)), len(value_nodes(translated)))

    def test_format_arguments_and_control_escapes_match_each_value(self):
        for filename, key, source, translated in self.pairs():
            for index, (before, after) in enumerate(zip(value_nodes(source), value_nodes(translated))):
                with self.subTest(file=filename, key=key, item=index):
                    original, localized = text_of(before), text_of(after)
                    if source.get("formatted", "true") != "false":
                        self.assertEqual(format_signature(original), format_signature(localized))
                    self.assertEqual(Counter(re.findall(r"\\[nrt]", original)),
                                     Counter(re.findall(r"\\[nrt]", localized)))
                    if original.startswith(("@", "?")):
                        self.assertEqual(original, localized)

    def test_xliff_xml_and_escaped_markup_are_preserved(self):
        for filename, key, source, translated in self.pairs():
            with self.subTest(file=filename, key=key):
                self.assertEqual(markup_signature(source), markup_signature(translated))
                for before, after in zip(value_nodes(source), value_nodes(translated)):
                    self.assertEqual(INLINE_TAG.findall(text_of(before)),
                                     INLINE_TAG.findall(text_of(after)))

    def test_android_quotes_and_controls_are_safe_and_values_nonempty(self):
        for filename, key, source, translated in self.pairs():
            for before, after in zip(value_nodes(source), value_nodes(translated)):
                text = text_of(after)
                with self.subTest(file=filename, key=key):
                    self.assertFalse(has_unescaped_quote(text), "Escape Android apostrophes/quotes")
                    self.assertFalse(any(ord(c) < 32 and c not in "\n\r\t" for c in text))
                    self.assertFalse(any(c in text for c in "\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069"))
                    if text_of(before).strip():
                        self.assertTrue(text.strip())

    def test_product_protocol_names_and_fixed_endpoints_are_preserved(self):
        endpoint = re.compile(r"(?:https?://|github\.com/)[^\s<>]+")
        for filename, key, source, translated in self.pairs():
            for before, after in zip(value_nodes(source), value_nodes(translated)):
                original, localized = text_of(before), text_of(after)
                with self.subTest(file=filename, key=key):
                    for product in PRODUCTS:
                        pattern = re.compile(r"(?<!\w)" + re.escape(product) + r"(?!\w)")
                        self.assertEqual(len(pattern.findall(original)), len(pattern.findall(localized)), product)
                    self.assertEqual(endpoint.findall(original), endpoint.findall(localized))


class CatalanResourceValidatorTest(unittest.TestCase):
    def test_formatter_keeps_argument_types_positions_repetitions_and_literals(self):
        self.assertEqual(format_signature("%1$s: %2$d %%"), format_signature("%2$d: %1$s %%"))
        for changed in ("%1$d: %2$d %%", "%1$s %%", "%1$s: %3$d %%", "%1$s: %2$d"):
            self.assertNotEqual(format_signature("%1$s: %2$d %%"), format_signature(changed))
        self.assertNotEqual(format_signature("%s %d"), format_signature("%d %s"))
        self.assertEqual(format_signature("%1$s %<s"), format_signature("%1$s %1$s"))

    def test_plural_quantities_and_array_order_are_part_of_the_contract(self):
        plural = ET.fromstring('<plurals><item quantity="one">%d</item><item quantity="other">%d</item></plurals>')
        changed = ET.fromstring('<plurals><item quantity="other">%d</item></plurals>')
        self.assertNotEqual(value_shape(plural), value_shape(changed))
        array = ET.fromstring('<string-array><item>%s</item><item>%d</item></string-array>')
        self.assertEqual(["%s", "%d"], [text_of(item) for item in value_nodes(array)])

    def test_xliff_identifiers_and_markup_attributes_cannot_be_replaced(self):
        before = ET.fromstring('<string xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"><xliff:g id="count">%1$d</xliff:g></string>')
        after = ET.fromstring('<string xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"><xliff:g id="other">%1$d</xliff:g></string>')
        self.assertNotEqual(markup_signature(before), markup_signature(after))
        self.assertNotEqual(INLINE_TAG.findall('<b>Text</b>'), INLINE_TAG.findall('<script>Text</script>'))

    def test_android_quote_check_distinguishes_escaped_and_bare_apostrophes(self):
        self.assertTrue(has_unescaped_quote("l'aplicació"))
        self.assertFalse(has_unescaped_quote(r"l\'aplicació"))
        self.assertFalse(has_unescaped_quote("l’aplicació"))
        self.assertTrue(has_unescaped_quote('"text"'))


if __name__ == "__main__":
    unittest.main()
