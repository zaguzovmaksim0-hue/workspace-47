"""Authorized structural seam only; these contracts do not execute Android UI."""

from hashlib import sha256
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/java/dev/junta/firmamobile/ui/BrowserChromeComponents.kt"
BASELINE = ROOT / "config/detekt/baseline.xml"
# Byte contracts are pinned to accepted main 54bf22e51225622ba4b9252cd781f8168958b8b3.
PREFIX_SHA256 = "e8ef3f9053e6838baa887e1d1c52f4e5fcbca4f69dae6d09b6629c068f4dca7d"
SUFFIX_SHA256 = "1c1250752cfafe2c6dc4107c679824266ba490a5a5e40d18a418c5705f5b3882"
TOOLBAR_BASELINE_IDS_SHA256 = "9ca0193c52e0b4cf91c6d0be793d7f0df36f816a068a0abcb6c00261f6a0d06a"
OWNER = "IndustrialBrowserTopBar"
HELPERS = ("browserToolbarSurface", "browserToolbarControls")
EXPECTED_SIGNATURE = """internal fun IndustrialBrowserTopBar(
    host: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onChangeCertificate: () -> Unit,
    onLockCertificate: () -> Unit,
    onClearCurrentSiteRequested: () -> Unit,
    onClearSessionRequested: () -> Unit,
    onDeleteAllBrowserDataRequested: () -> Unit,
    onOpenInBrowser: (() -> Unit)? = null,
    onShowSiteInformation: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
)"""
REQUIRED_MENU_ACTIONS = (
    "onChangeCertificate", "onLockCertificate", "onClearCurrentSiteRequested",
    "onClearSessionRequested", "onDeleteAllBrowserDataRequested",
)


def code_mask(source: str) -> str:
    """Keep offsets/newlines while ignoring Kotlin comments and string literals."""
    literals = r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    return re.sub(literals, lambda m: re.sub(r"[^\n]", " ", m.group()), source)


def balanced_end(mask: str, opening: int, left: str, right: str) -> int:
    depth = 0
    for index in range(opening, len(mask)):
        if mask[index] == left:
            depth += 1
        elif mask[index] == right:
            depth -= 1
            if depth == 0:
                return index
    raise AssertionError(f"Unbalanced {left}{right} at {opening}")


def function_parts(source: str, name: str) -> tuple[str, str]:
    """Bound the declaration through balanced parameters and the exact body."""
    mask = code_mask(source)
    matches = list(re.finditer(r"(?m)^(?:internal|private) fun " + re.escape(name) + r"\(", mask))
    if len(matches) != 1:
        raise AssertionError(f"Expected one declaration of {name}, got {len(matches)}")
    match = matches[0]
    params_end = balanced_end(mask, match.end() - 1, "(", ")")
    body_start = params_end + 1
    while mask[body_start].isspace():
        body_start += 1
    if mask[body_start] != "{":
        raise AssertionError(f"Expected block body for {name}")
    body_end = balanced_end(mask, body_start, "{", "}")
    return source[match.start():body_start].rstrip(), source[body_start + 1:body_end]


def compact(source: str) -> str:
    return re.sub(r"\s+", "", source)


def call_arguments(body: str, name: str) -> dict[str, str]:
    """Read the one call's named arguments, including nested callback lambdas."""
    mask = code_mask(body)
    matches = list(re.finditer(r"\b" + re.escape(name) + r"\s*\(", mask))
    if len(matches) != 1:
        raise AssertionError(f"Expected one call of {name}, got {len(matches)}")
    opening = matches[0].end() - 1
    closing = balanced_end(mask, opening, "(", ")")
    segments, start, stack = [], opening + 1, []
    pairs = {"(": ")", "[": "]", "{": "}"}
    for index in range(start, closing):
        char = mask[index]
        if char in pairs:
            stack.append(pairs[char])
        elif stack and char == stack[-1]:
            stack.pop()
        elif char == "," and not stack:
            segments.append(body[start:index].strip())
            start = index + 1
    segments.append(body[start:closing].strip())
    result = {}
    for segment in filter(None, segments):
        key, value = segment.split("=", 1)
        key = key.strip()
        if key in result:
            raise AssertionError(f"Duplicate argument {key}")
        result[key] = compact(value)
    return result


class BrowserToolbarDecompositionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = SOURCE.read_text(encoding="utf-8")
        self.signature, self.owner = function_parts(self.source, OWNER)

    def test_owner_fits_long_method_limit_at_exact_function_boundary(self) -> None:
        # Conservative physical-line guard, not a replacement for pinned detekt.
        lines = [line for line in code_mask(self.owner).splitlines() if line.strip()]
        self.assertLessEqual(len(lines), 60, "Decompose presentation, not the API or state owner")

    def test_api_defaults_and_parameter_order_are_unchanged(self) -> None:
        self.assertEqual(self.signature, EXPECTED_SIGNATURE)
        self.assertEqual(self.source.count("@Composable\ninternal fun IndustrialBrowserTopBar("), 1)

    def test_bytes_outside_owner_and_adjacent_helpers_are_unchanged(self) -> None:
        data = SOURCE.read_bytes()
        prefix_end = data.index(b"@Composable\ninternal fun IndustrialBrowserTopBar(")
        suffix_start = data.index(b"@Composable\ninternal fun BrowserServiceIdentity(")
        self.assertEqual(sha256(data[:prefix_end]).hexdigest(), PREFIX_SHA256)
        self.assertEqual(sha256(data[suffix_start:]).hexdigest(), SUFFIX_SHA256)

    def test_helpers_are_private_lower_camel_stateless_and_bounded(self) -> None:
        region = self.source.split("@Composable\ninternal fun BrowserServiceIdentity(", 1)[0]
        region = region.split(self.signature, 1)[1]
        names = re.findall(r"(?m)^(?:private|internal) fun (\w+)\(", code_mask(region))
        self.assertEqual(names, list(HELPERS))
        self.assertEqual(region.count("remember { mutableStateOf(false) }"), 1)
        for name in HELPERS:
            with self.subTest(helper=name):
                signature, body = function_parts(self.source, name)
                self.assertTrue(signature.startswith("private fun " + name + "("))
                self.assertRegex(name, r"^[a-z][a-zA-Z0-9]*$")
                self.assertNotRegex(code_mask(body), r"\b(remember|mutableStateOf|LaunchedEffect|SideEffect|DisposableEffect)\b")
                self.assertNotIn("menuExpanded", body)
                self.assertNotRegex(body, r"\bon(?:Back|Home|Reload)\s*\(\s*\)")
                self.assertLessEqual(len([line for line in code_mask(body).splitlines() if line.strip()]), 60)
        self.assertEqual(len(re.findall(r"(?m)^(?:internal|private) fun \w+\(", code_mask(self.source))), 10)
        self.assertNotIn("@Suppress", region)
        self.assertNotIn("detekt:", region)

    def test_state_open_dismiss_and_menu_anchor_remain_with_owner(self) -> None:
        self.assertEqual(self.owner.count("var menuExpanded by remember { mutableStateOf(false) }"), 1)
        self.assertEqual(self.owner.count("BrowserOverflowMenu("), 1)
        self.assertIn(compact("""Box {
            BrowserChromeIconButton(
                iconRes = R.drawable.ic_browser_more,
                contentDescription = stringResource(R.string.browser_action_more),
                onClick = { menuExpanded = true },
            )
            BrowserOverflowMenu("""), compact(self.owner))
        args = call_arguments(self.owner, "BrowserOverflowMenu")
        self.assertEqual(args["expanded"], "menuExpanded")
        self.assertEqual(args["onDismissRequest"], "{menuExpanded=false}")
        self.assertEqual(call_arguments(self.owner, "browserToolbarSurface"), {
            "modifier": "modifier", "windowInsets": "windowInsets",
        })
        self.assertEqual(call_arguments(self.owner, "browserToolbarControls"), {
            "host": "host", "onBack": "onBack", "onHome": "onHome", "onReload": "onReload",
        })

    def test_every_overflow_callback_closes_before_exactly_one_action(self) -> None:
        args = call_arguments(self.owner, "BrowserOverflowMenu")
        for name in REQUIRED_MENU_ACTIONS:
            with self.subTest(action=name):
                self.assertEqual(args[name], "{menuExpanded=false" + name + "()}")
                self.assertEqual(self.owner.count(name + "()"), 1)
        self.assertEqual(set(args), set(REQUIRED_MENU_ACTIONS) | {
            "onShowSiteInformation", "onOpenInBrowser", "expanded", "onDismissRequest",
        })

    def test_null_optional_items_are_not_replaced_with_no_op_callbacks(self) -> None:
        args = call_arguments(self.owner, "BrowserOverflowMenu")
        self.assertEqual(args["onShowSiteInformation"],
                         "onShowSiteInformation?.let{show->{menuExpanded=false;show()}}")
        self.assertEqual(args["onOpenInBrowser"],
                         "onOpenInBrowser?.let{open->{menuExpanded=false;open()}}")

    def test_surface_insets_colors_height_and_separator_order_are_preserved(self) -> None:
        _, body = function_parts(self.source, "browserToolbarSurface")
        # Literal layout contract taken from the accepted toolbar, not a generated snapshot.
        self.assertEqual(compact(body), compact("""
            Column(
                modifier = modifier.fillMaxWidth().background(JuntaPaper).windowInsetsPadding(windowInsets),
            ) {
                Surface(
                    color = JuntaPaperElevated,
                    contentColor = JuntaInk,
                    modifier = Modifier.fillMaxWidth().height(BrowserIndustrialToolbarHeight - 3.dp),
                ) { content() }
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(JuntaTeal),)
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(JuntaInk.copy(alpha = 0.42f)),)
            }
        """))

    def test_controls_keep_row_weight_box_and_button_accessibility_order(self) -> None:
        _, body = function_parts(self.source, "browserToolbarControls")
        self.assertEqual(compact(body), compact("""
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowserChromeIconButton(
                    iconRes = R.drawable.ic_browser_back,
                    contentDescription = stringResource(R.string.browser_action_back), onClick = onBack,
                )
                Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp),) {
                    Text(text = host, color = JuntaTealDark, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(BROWSER_ADDRESS_LABEL_TAG),)
                }
                BrowserChromeIconButton(
                    iconRes = R.drawable.ic_browser_home,
                    contentDescription = stringResource(R.string.browser_action_home), onClick = onHome,
                )
                BrowserChromeIconButton(
                    iconRes = R.drawable.ic_browser_reload,
                    contentDescription = stringResource(R.string.browser_action_reload), onClick = onReload,
                )
                overflowMenu()
            }
        """))

    def test_only_toolbar_long_method_baseline_id_is_removed(self) -> None:
        data = BASELINE.read_bytes()
        ids = [node.text for node in ET.fromstring(data).iter("ID")]
        chrome_ids = [item for item in ids if "BrowserChromeComponents.kt$" in item]
        self.assertFalse(any(item.startswith("LongMethod:BrowserChromeComponents.kt$") for item in chrome_ids))
        self.assertEqual(sha256("\n".join(chrome_ids).encode()).hexdigest(), TOOLBAR_BASELINE_IDS_SHA256)
        self.assertTrue(any(item.startswith("FunctionNaming:BrowserChromeComponents.kt$@Composable internal fun IndustrialBrowserTopBar(") for item in chrome_ids))
        self.assertEqual(list(ET.fromstring(data).find("ManuallySuppressedIssues")), [])

    def test_function_boundary_ignores_strings_comments_and_neighbor_functions(self) -> None:
        sample = '''// private fun sample() { fake() }
private fun sample(callback: () -> Unit = { println("}") }) {
    callback()
    // } private fun decoy() {
    val text = "}"
    val nested = { menuExpanded = false }
}
private fun neighbor() { forbidden() }
'''
        signature, body = function_parts(sample, "sample")
        self.assertTrue(signature.endswith('println("}") })'))
        self.assertIn("val nested = { menuExpanded = false }", body)
        self.assertNotIn("forbidden", body)
        self.assertEqual(call_arguments('target(onClick = { f(1, 2); g() }, value = null,)', "target"),
                         {"onClick": "{f(1,2);g()}", "value": "null"})


if __name__ == "__main__":
    unittest.main()
