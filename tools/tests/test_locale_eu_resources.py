"""Euskara resource groundwork: XML contracts only, not Android/linguistic acceptance."""

from collections import Counter
from pathlib import Path
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
RESOURCE_TYPES = {"string", "plurals", "string-array"}
QUANTITIES = {"zero", "one", "two", "few", "many", "other"}
FORMAT = re.compile(
    r"%(?:(?P<index>[1-9]\d*)\$)?(?P<flags>[-#+ 0,(<]*)"
    r"(?P<width>\d+)?(?P<precision>\.\d+)?"
    r"(?P<conversion>[tT][A-Za-z]|[bBhHsScCdoxXeEfgGaA%n])"
)
ANDROID_ESCAPE = re.compile(r"\\(?:[\\'\"ntr@?]|u[0-9a-fA-F]{4})")
BIDI_CONTROLS = set("\u061c\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069")
FIXED_NAMES = (
    "Firma Mobile", "AutoFirma", "Android", "WebView", "Google Play",
    "GitHub", "Google Password Manager", "JavaScript", "Passkeys", "WebAuthn",
    "PKCS#12", "X.509", "RSA", "TLS", "HTTPS", "CAdES", "PAdES", "PDF",
    "SHA-1", "SHA-256", "PRE", "MiB", ".p12", ".pfx",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inventory(directory, *, defaults=False):
    entries = {}
    global_names = set()
    for path in sorted(directory.glob("*.xml")):
        raw = path.read_text(encoding="utf-8")
        require(not re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", raw, re.I),
                f"DTD/entity declaration: {path.name}")
        root = ET.fromstring(raw)
        require(root.tag == "resources", f"Not a resources document: {path.name}")
        for entry in root:
            if entry.tag not in RESOURCE_TYPES:
                require(defaults, f"Unexpected locale resource type: {entry.tag}")
                continue
            if defaults and entry.get("translatable", "true") == "false":
                continue
            name = entry.get("name", "")
            require(re.fullmatch(r"[a-z][a-z0-9_]*", name), f"Invalid name: {name}")
            key = (path.name, name)
            global_key = (entry.tag, name)
            require(key not in entries and global_key not in global_names,
                    f"Duplicate resource: {name}")
            entries[key] = entry
            global_names.add(global_key)
    return entries


def value_items(entry):
    if entry.tag == "string":
        return {"text": entry}
    require(all(child.tag == "item" for child in entry), "Non-item child")
    require(bool(len(entry)), "Empty plural/array")
    if entry.tag == "string-array":
        return dict(enumerate(entry))
    items = {}
    for item in entry:
        quantity = item.get("quantity")
        require(quantity in QUANTITIES and quantity not in items,
                f"Invalid/duplicate plural quantity: {quantity}")
        items[quantity] = item
    require("other" in items, "Plural must include other")
    return items


def markup_shape(element):
    return tuple((child.tag, tuple(sorted(child.attrib.items())), markup_shape(child))
                 for child in element)


def format_signature(text):
    result = Counter()
    cursor = 0
    implicit_index = 0
    last_index = None
    while (position := text.find("%", cursor)) != -1:
        token = FORMAT.match(text, position)
        require(token is not None, "Invalid format token")
        fields = token.groupdict()
        conversion = fields["conversion"]
        if conversion in {"%", "n"}:
            index = None
        elif fields["index"]:
            index = int(fields["index"])
            last_index = index
        elif "<" in fields["flags"]:
            require(last_index is not None, "Relative format without an argument")
            index = last_index
        else:
            implicit_index += 1
            index = implicit_index
            last_index = index
        result[(index, bool(fields["index"]), fields["flags"], fields["width"],
                fields["precision"], conversion)] += 1
        cursor = token.end()
    return result


def android_text_signature(text):
    require(bool(text.strip()), "Empty translated value")
    require(not text.lstrip().startswith(("@", "?")), "Unexpected resource reference")
    controls = Counter()
    cursor = 0
    while cursor < len(text):
        char = text[cursor]
        require(char not in BIDI_CONTROLS and (ord(char) >= 32 or char in "\n\t\r"),
                "Unsafe control character")
        if char == "\\":
            escape = ANDROID_ESCAPE.match(text, cursor)
            require(escape is not None, "Invalid Android escape")
            escaped = escape.group()[1:]
            if escaped.startswith("u"):
                require(chr(int(escaped[1:], 16)) not in BIDI_CONTROLS,
                        "Escaped bidi control")
            if escaped in {"n", "r", "t"}:
                controls[escaped] += 1
            cursor = escape.end()
        else:
            require(char not in "'\"", "Unescaped Android quote/apostrophe")
            if char in "\n\t\r":
                controls[char] += 1
            cursor += 1
    return controls


def validate_pair(source, translated):
    require(source.tag == translated.tag, "Changed resource type")
    require(translated.get("translatable", "true") != "false", "Nontranslatable override")
    source_attrs = {k: v for k, v in source.attrib.items() if k != "translatable"}
    target_attrs = {k: v for k, v in translated.attrib.items() if k != "translatable"}
    require(source_attrs == target_attrs, "Changed resource attributes")
    expected_items = value_items(source)
    actual_items = value_items(translated)
    require(expected_items.keys() == actual_items.keys(), "Changed quantities/array length")
    for key, expected in expected_items.items():
        actual = actual_items[key]
        require(expected.attrib == actual.attrib, "Changed item attributes")
        require(markup_shape(expected) == markup_shape(actual), "Changed markup/XLIFF")
        source_text = "".join(expected.itertext())
        target_text = "".join(actual.itertext())
        if source.get("formatted", "true") != "false":
            require(format_signature(source_text) == format_signature(target_text),
                    "Changed format signature")
        require(android_text_signature(source_text) == android_text_signature(target_text),
                "Changed Android line/tab escapes")
        require(Counter(re.findall(r"</?[A-Za-z][^>]*>", source_text)) ==
                Counter(re.findall(r"</?[A-Za-z][^>]*>", target_text)),
                "Changed escaped markup")
        for name in FIXED_NAMES:
            require(source_text.count(name) == target_text.count(name),
                    f"Changed product/protocol name: {name}")
        source_numbers = Counter(re.findall(r"\d+(?:[.,]\d+)*", FORMAT.sub("", source_text)))
        target_numbers = Counter(re.findall(r"\d+(?:[.,]\d+)*", FORMAT.sub("", target_text)))
        require(source_numbers == target_numbers, "Changed numeric limit/version")
        source_links = Counter(re.findall(r"(?:https?://|github\.com/)[^\s]*", source_text))
        target_links = Counter(re.findall(r"(?:https?://|github\.com/)[^\s]*", target_text))
        require(source_links == target_links, "Changed literal URL/scheme")


def validate_resources(default_directory, locale_directory):
    expected = inventory(default_directory, defaults=True)
    actual = inventory(locale_directory)
    require(bool(expected), "No default translatable resources found")
    missing = expected.keys() - actual.keys()
    extra = actual.keys() - expected.keys()
    require(not missing and not extra,
            f"Resource coverage: {len(missing)} missing, {len(extra)} extra; "
            f"examples: {sorted(missing | extra)[:5]}")
    for key, source in expected.items():
        try:
            validate_pair(source, actual[key])
        except ValueError as error:
            raise ValueError(f"{key}: {error}") from error


DEFAULT_FIXTURE = '''<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
<string name="hello">Hello <xliff:g id="site">%1$s</xliff:g> <b>%2$d</b></string>
<plurals name="count"><item quantity="one">%1$d file</item><item quantity="other">%1$d files</item></plurals>
<string-array name="formats"><item>PDF %1$d</item><item>AutoFirma</item></string-array>
<string name="limit">32 MiB</string>
<string name="internal" translatable="false">Internal</string>
<color name="background">#ffffff</color>
</resources>'''
EU_FIXTURE = '''<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
<string name="hello">Kaixo <xliff:g id="site">%1$s</xliff:g> <b>%2$d</b></string>
<plurals name="count"><item quantity="one">%1$d fitxategi</item><item quantity="other">%1$d fitxategi</item></plurals>
<string-array name="formats"><item>PDF %1$d</item><item>AutoFirma</item></string-array>
<string name="limit">32 MiB</string>
</resources>'''


class LocaleEuResourcesTest(unittest.TestCase):
    def fixture(self, localized, defaults=DEFAULT_FIXTURE, locale_filename="sample.xml"):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            source = base / "values"
            locale = base / "values-eu"
            source.mkdir()
            locale.mkdir()
            (source / "sample.xml").write_text(defaults, encoding="utf-8")
            (locale / locale_filename).write_text(localized, encoding="utf-8")
            validate_resources(source, locale)

    def test_euskara_resources_match_every_translatable_default_contract(self):
        try:
            validate_resources(ROOT / "app/src/main/res/values", ROOT / "app/src/main/res/values-eu")
        except ValueError as error:
            self.fail(str(error))

    def test_valid_strings_plurals_arrays_xliff_and_nontranslatable_exclusion(self):
        self.fixture(EU_FIXTURE)

    def test_missing_key_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "coverage"):
            self.fixture(EU_FIXTURE.replace('<string name="limit">32 MiB</string>', ""))

    def test_nontranslatable_or_unknown_extra_key_is_rejected(self):
        for name in ("internal", "unexpected"):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "coverage"):
                self.fixture(EU_FIXTURE.replace("</resources>",
                    f'<string name="{name}">Ez kopiatu</string></resources>'))

    def test_duplicate_key_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.fixture(EU_FIXTURE.replace("</resources>",
                '<string name="limit">32 MiB</string></resources>'))

    def test_source_file_grouping_is_preserved(self):
        with self.assertRaisesRegex(ValueError, "coverage"):
            self.fixture(EU_FIXTURE, locale_filename="moved.xml")

    def test_resource_type_change_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "type"):
            self.fixture(EU_FIXTURE.replace('<string name="limit">32 MiB</string>',
                '<string-array name="limit"><item>32 MiB</item></string-array>'))

    def test_plural_quantities_are_exact_and_other_is_required(self):
        for localized in (
            EU_FIXTURE.replace('quantity="one"', 'quantity="two"'),
            EU_FIXTURE.replace('<item quantity="other">%1$d fitxategi</item>', ""),
            EU_FIXTURE.replace('quantity="one"', 'quantity="other"'),
        ):
            with self.subTest(localized=localized), self.assertRaises(ValueError):
                self.fixture(localized)

    def test_array_count_order_and_item_formats_are_preserved(self):
        for localized in (
            EU_FIXTURE.replace('<item>AutoFirma</item>', ""),
            EU_FIXTURE.replace('<item>PDF %1$d</item><item>AutoFirma</item>',
                               '<item>AutoFirma</item><item>PDF %1$d</item>'),
        ):
            with self.subTest(localized=localized), self.assertRaises(ValueError):
                self.fixture(localized)

    def test_argument_indices_types_repetition_and_flags_are_preserved(self):
        for replacement in ("%2$s", "%1$d", "%s", "%1$10s", "%1$s %1$s", "%1$q"):
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                self.fixture(EU_FIXTURE.replace("%1$s", replacement))

    def test_markup_namespace_and_xliff_attributes_cannot_change(self):
        for localized in (
            EU_FIXTURE.replace("<b>", "<i>").replace("</b>", "</i>"),
            EU_FIXTURE.replace('id="site"', 'id="other"'),
            EU_FIXTURE.replace("urn:oasis:names:tc:xliff:document:1.2", "urn:unexpected"),
            EU_FIXTURE.replace("32 MiB</string>", "32 MiB<script>code</script></string>"),
            EU_FIXTURE.replace("32 MiB</string>", "32 MiB &lt;script&gt;code&lt;/script&gt;</string>"),
        ):
            with self.subTest(localized=localized), self.assertRaises(ValueError):
                self.fixture(localized)

    def test_android_quotes_escapes_and_controls_are_checked(self):
        for replacement in ("d'Artagnan", '"Kaixo"', r"Kaixo\q", "Kaixo\u202e", r"Kaixo\u202e", "@string/other"):
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                self.fixture(EU_FIXTURE.replace("Kaixo", replacement))

    def test_android_escaped_quotes_and_apostrophes_are_valid(self):
        self.fixture(EU_FIXTURE.replace("Kaixo", r"\"Kaixo\" d\'Artagnan"))

    def test_line_escapes_literal_percent_and_date_formats_are_preserved(self):
        source = r'<resources><string name="line">%1$tY %%\n%2$s</string></resources>'
        localized = r'<resources><string name="line">%1$tY %%\n%2$s</string></resources>'
        self.fixture(localized, defaults=source)
        for replacement in ("%1$ty", "%", ""):
            changed = localized.replace("%1$tY" if replacement == "%1$ty" else "%%" if replacement == "%" else r"\n", replacement)
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                self.fixture(changed, defaults=source)

    def test_literal_limits_and_protocol_names_cannot_change(self):
        for localized in (EU_FIXTURE.replace("32 MiB", "64 MiB"),
                          EU_FIXTURE.replace("AutoFirma", "OtherApp")):
            with self.subTest(localized=localized), self.assertRaises(ValueError):
                self.fixture(localized)

    def test_empty_translation_and_unexpected_resource_types_are_rejected(self):
        for localized in (
            EU_FIXTURE.replace("32 MiB", ""),
            EU_FIXTURE.replace("</resources>", '<color name="new">#ffffff</color></resources>'),
        ):
            with self.subTest(localized=localized), self.assertRaises(ValueError):
                self.fixture(localized)

    def test_dtd_entities_and_malformed_xml_are_rejected(self):
        for localized in (
            '<!DOCTYPE resources [<!ENTITY x "value">]>' + EU_FIXTURE,
            EU_FIXTURE.replace("</resources>", ""),
        ):
            with self.subTest(localized=localized), self.assertRaises((ValueError, ET.ParseError)):
                self.fixture(localized)


if __name__ == "__main__":
    unittest.main()
