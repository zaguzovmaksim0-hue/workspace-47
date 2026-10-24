"""Galician resource contracts only; no Android runtime or third-party modules needed."""
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DEFAULT = ROOT / "app/src/main/res/values"
GALICIAN = ROOT / "app/src/main/res/values-gl"
RESOURCE_TYPES = {"string", "plurals", "string-array"}
FORMAT = re.compile(
    r"%(?:(?P<index>[1-9][0-9]*)\$)?(?P<flags>[-#+ 0,(<]*)"
    r"(?P<width>[0-9]+)?(?P<precision>\.[0-9]+)?(?P<date>[tT])?(?P<kind>[a-zA-Z%])"
)
PROTECTED = re.compile(
    r"(?<![\w])(?:Firma Mobile|AutoFirma|Android|WebView|KeyChain|Cl@ve|DNIe|"
    r"PKCS#12|PKCS#7|X\.509|CAdES|PAdES|XAdES|JAdES|SHA-512|SHA-256|SHA-1|"
    r"AES-GCM|HTTPS|HTTP|TLS|SSL|RSA|CMS|PDF|XML|DER|CSV|SAF|UTF-8)(?![\w])"
)
URL = re.compile(r"(?:https?|afirma|content|file)://[^\s<>\"']*")


def catalog(directory, *, translatable_only):
    result = {}
    for path in sorted(directory.glob("*.xml")):
        raw = path.read_text(encoding="utf-8")
        if "<!DOCTYPE" in raw.upper() or "<!ENTITY" in raw.upper():
            raise ValueError(f"Declarations are not resource text: {path.name}")
        root = ET.fromstring(raw)
        if root.tag != "resources":
            raise ValueError(f"Invalid resource root: {path.name}")
        entries = [e for e in root if e.tag in RESOURCE_TYPES]
        if translatable_only:
            entries = [e for e in entries if e.get("translatable", "true") != "false"]
        elif len(entries) != len(root):
            raise ValueError(f"Unexpected non-text resource in values-gl: {path.name}")
        if entries or not translatable_only:
            result[path.name] = entries
    return result


def key(element):
    return element.tag, element.attrib["name"], element.get("product")


def shape(element):
    return element.tag, tuple(sorted(element.attrib.items())), tuple(shape(c) for c in element)


def values(element):
    if element.tag == "string":
        return {"string": element}
    if element.tag == "plurals":
        return {e.attrib["quantity"]: e for e in element}
    return {str(index): e for index, e in enumerate(element)}


def format_signature(text):
    signature = Counter()
    implicit = 1
    previous = None
    for match in FORMAT.finditer(text):
        index, flags, width, precision, date, kind = match.groups()
        if not date and kind in {"%", "n"}:
            argument = None
        elif "<" in flags:
            if previous is None:
                raise ValueError("Format reuses a missing argument")
            argument = previous
        elif index:
            argument = int(index)
        else:
            argument = implicit
            implicit += 1
        if argument is not None:
            previous = argument
        signature[(argument, (date or "") + kind, flags.replace("<", ""), width, precision)] += 1
    return signature


class Markup(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=False)
        self.tokens = []
        self.feed(text)
        self.close()

    def handle_starttag(self, tag, attrs):
        self.tokens.append(("start", tag, tuple(sorted(attrs))))

    def handle_startendtag(self, tag, attrs):
        self.tokens.append(("empty", tag, tuple(sorted(attrs))))

    def handle_endtag(self, tag):
        self.tokens.append(("end", tag))

    def handle_decl(self, decl):
        self.tokens.append(("declaration", decl))

    def handle_pi(self, data):
        self.tokens.append(("processing", data))

    def handle_comment(self, data):
        self.tokens.append(("comment", data))


def android_text(text):
    """Validate escapes/quotes and return layout escapes, not language punctuation."""
    quoted = len(text) >= 2 and text.startswith('"') and text.endswith('"')
    layout = Counter()
    index = 0
    while index < len(text):
        char = text[index]
        if char == "\\":
            index += 1
            if index == len(text):
                raise ValueError("Trailing Android escape")
            escaped = text[index]
            if escaped == "u":
                digits = text[index + 1:index + 5]
                if len(digits) != 4 or not re.fullmatch(r"[0-9a-fA-F]{4}", digits):
                    raise ValueError("Invalid Android Unicode escape")
                layout["u" + digits.lower()] += 1
                index += 4
            elif escaped not in "nrt\\'\"@? ":
                raise ValueError("Unknown Android escape")
            elif escaped in "nrt":
                layout[escaped] += 1
        elif char == "'" and not quoted:
            raise ValueError("Unescaped Android apostrophe")
        elif char == '"' and not (quoted and index in {0, len(text) - 1}):
            raise ValueError("Unescaped Android quotation mark")
        index += 1
    return layout


class GalicianResourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.default = catalog(DEFAULT, translatable_only=True)
        cls.galician = catalog(GALICIAN, translatable_only=False)

    def pairs(self):
        for filename, expected in self.default.items():
            self.assertIn(filename, self.galician, filename)
            actual = {key(e): e for e in self.galician[filename]}
            for source in expected:
                self.assertIn(key(source), actual, (filename, key(source)))
                yield filename, source, actual[key(source)]

    def test_complete_exact_translatable_keys_grouped_by_source_file(self):
        self.assertTrue(self.default)
        self.assertEqual(set(self.default), set(self.galician))
        for filename, expected in self.default.items():
            with self.subTest(file=filename):
                self.assertEqual(Counter(map(key, expected)), Counter(map(key, self.galician[filename])))

    def test_no_duplicate_locale_resources_or_nontranslatable_overrides(self):
        all_keys = [key(e) for entries in self.galician.values() for e in entries]
        self.assertEqual(len(all_keys), len(set(all_keys)))
        for filename, source, translated in self.pairs():
            with self.subTest(file=filename, resource=key(source)):
                self.assertNotEqual("false", translated.get("translatable"))

    def test_resource_types_attributes_inline_markup_and_xliff_are_preserved(self):
        for filename, source, translated in self.pairs():
            with self.subTest(file=filename, resource=key(source)):
                self.assertEqual(shape(source), shape(translated))

    def test_plural_quantities_and_array_item_order_shape_are_preserved(self):
        for filename, source, translated in self.pairs():
            with self.subTest(file=filename, resource=key(source)):
                self.assertEqual(len(source), len(translated))
                self.assertEqual(list(values(source)), list(values(translated)))
                if source.tag != "string":
                    self.assertTrue(all(item.tag == "item" for item in translated))
                    self.assertEqual(len(values(translated)), len(translated))

    def test_format_signatures_escaped_markup_and_android_escaping_match(self):
        for filename, source, translated in self.pairs():
            for label, original in values(source).items():
                with self.subTest(file=filename, resource=key(source), value=label):
                    target = values(translated)[label]
                    before, after = "".join(original.itertext()), "".join(target.itertext())
                    self.assertEqual(format_signature(before), format_signature(after))
                    self.assertEqual(Markup(before).tokens, Markup(after).tokens)
                    self.assertEqual(android_text(before), android_text(after))
                    if before.strip():
                        self.assertTrue(after.strip())
                    if after.startswith(("@", "?")):
                        self.assertEqual(before, after, "Do not introduce resource references")

    def test_product_protocol_names_and_literal_urls_are_unchanged(self):
        for filename, source, translated in self.pairs():
            for label, original in values(source).items():
                with self.subTest(file=filename, resource=key(source), value=label):
                    before = "".join(original.itertext())
                    after = "".join(values(translated)[label].itertext())
                    self.assertEqual(Counter(PROTECTED.findall(before)), Counter(PROTECTED.findall(after)))
                    self.assertEqual(Counter(URL.findall(before)), Counter(URL.findall(after)))


class ResourceContractHelpersTest(unittest.TestCase):
    def test_argument_reordering_is_safe_but_changed_type_or_index_is_not(self):
        self.assertEqual(format_signature("%1$s: %2$d"), format_signature("%2$d · %1$s"))
        self.assertNotEqual(format_signature("%1$d"), format_signature("%1$s"))
        self.assertNotEqual(format_signature("%1$s"), format_signature("%2$s"))
        self.assertNotEqual(format_signature("%1$s %1$s"), format_signature("%1$s"))

    def test_implicit_reused_date_width_and_literal_percent_formats_are_counted(self):
        self.assertEqual(format_signature("%1$s %<s"), format_signature("%1$s %1$s"))
        self.assertEqual(format_signature("%s %d"), format_signature("%1$s %2$d"))
        self.assertNotEqual(format_signature("%1$02d"), format_signature("%1$d"))
        self.assertNotEqual(format_signature("%1$tY"), format_signature("%1$ty"))
        self.assertNotEqual(format_signature("100%%"), format_signature("100"))

    def test_xliff_ids_markup_and_link_targets_cannot_be_changed(self):
        source = ET.fromstring('<string name="sample" xmlns:x="urn:oasis:names:tc:xliff:document:1.2"><b>Ola</b><x:g id="host">%1$s</x:g></string>')
        translated = ET.fromstring('<string name="sample" xmlns:x="urn:oasis:names:tc:xliff:document:1.2"><b>Texto</b><x:g id="host">%1$s</x:g></string>')
        self.assertEqual(shape(source), shape(translated))
        translated[1].set("id", "other")
        self.assertNotEqual(shape(source), shape(translated))
        self.assertNotEqual(Markup('<a href="https://example.test">Ola</a>').tokens,
                            Markup('<a href="javascript:bad">Ola</a>').tokens)
        self.assertNotEqual(Markup("Texto").tokens, Markup("<script>bad</script>").tokens)

    def test_android_quote_and_escape_errors_are_detected(self):
        self.assertEqual(Counter({"n": 1}), android_text(r"Liña\nseguinte"))
        android_text(r"d\'exemplo")
        android_text('"d\'exemplo"')
        for text in ["d'exemplo", 'Texto "sen escapar"', "final\\", r"\x", r"\u12"]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                android_text(text)


if __name__ == "__main__":
    unittest.main()
