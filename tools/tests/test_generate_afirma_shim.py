"""Deterministic source composition without changing the packaged shim."""
import json
import re
import pathlib
import tempfile
import unittest
from unittest import mock
from tools.generate_afirma_shim import compose, synchronize

ROOT = pathlib.Path(__file__).resolve().parents[2]


def airef_extraction(legacy):
    constants = legacy[legacy.index("  const airefOrigin ="):legacy.index("  const cdtiOrigin =")]
    helpers = legacy[legacy.index("  function isValidAirefPayload"):legacy.index("  function isExactCanariasChallenge")]
    start = legacy.index("    const isAirefOrigin =")
    branch = legacy[start:legacy.index("    if (isAirefOrigin && !isExactAirefCall)", start)]
    module = ("\n  matchesAirefSign = (args, successCallback, errorCallback) => {\n" +
              constants.replace("  const", "    const") +
              "\n" + "".join("  " + line if line.strip() else line for line in helpers.splitlines(keepends=True)) +
              branch + "    return isAirefOrigin ? isExactAirefCall : null;\n  };\n")
    return constants, helpers, branch, module


def cdti_extraction(legacy):
    constants = legacy[legacy.index("  const cdtiOrigin ="):legacy.index("  const policiaOrigin =")]
    start = legacy.index("    const isCdtiOrigin =")
    branch = legacy[start:legacy.index("    if (isCdtiOrigin && !isExactCdtiCall)", start)]
    module = ("\n  matchesCdtiSign = (args, successCallback, errorCallback) => {\n" +
              constants.replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              "    return isCdtiOrigin ? isExactCdtiCall : null;\n  };\n")
    return constants, branch, module


def gran_canaria_extraction(legacy):
    origin = legacy[legacy.index('  const granCanariaOrigin ='):legacy.index('  const canariasOrigin =')]
    constants = legacy[legacy.index("  const granCanariaExtraProperties ="):legacy.index('  const fuerteventuraOrigin =')]
    start = legacy.index('    const isGranCanariaOrigin =')
    branch = legacy[start:legacy.index('    if (isGranCanariaOrigin && !isExactGranCanariaCall)', start)]
    module = ('\n  matchesGranCanariaSign = (args, successCallback, errorCallback) => {\n' +
              (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              '    return isGranCanariaOrigin ? isExactGranCanariaCall : null;\n  };\n')
    return origin, constants, branch, module


def fuerteventura_extraction(legacy):
    origin = ""
    constants = legacy[legacy.index('  const fuerteventuraOrigin ='):legacy.index('  const minecoOrigin =')]
    start = legacy.index('    const isFuerteventuraOrigin =')
    branch = legacy[start:legacy.index('    if (isFuerteventuraOrigin && !isExactFuerteventuraCall)', start)]
    module = ('\n  matchesFuerteventuraSign = (args, successCallback, errorCallback) => {\n' +
              (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              '    return isFuerteventuraOrigin ? isExactFuerteventuraCall : null;\n  };\n')
    return origin, constants, branch, module


def mineco_extraction(legacy):
    origin = ""
    constants = legacy[legacy.index('  const minecoOrigin ='):legacy.index('  const policiaProcedurePage =')]
    start = legacy.index('    const isMinecoOrigin =')
    branch = legacy[start:legacy.index('    if (isMinecoOrigin && !isExactMinecoCall)', start)]
    module = ('\n  matchesMinecoSign = (args, successCallback, errorCallback) => {\n' +
              (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              '    return isMinecoOrigin ? isExactMinecoCall : null;\n  };\n')
    return origin, constants, branch, module


def canarias_extraction(legacy):
    constants = legacy[legacy.index("  const canariasOrigin ="):legacy.index("  const granCanariaExtraProperties =")]
    helpers = legacy[legacy.index("  function isExactCanariasChallenge"):legacy.index("  function isExactXuntaProperties")]
    start = legacy.index("    const isCanariasOrigin =")
    branch = legacy[start:legacy.index("    if (isCanariasOrigin && !isExactCanariasCall)", start)]
    module = ("\n  matchesCanariasSign = (args, successCallback, errorCallback) => {\n" +
              constants.replace("  const", "    const").replace('\n    "', '\n      "') + "\n" +
              "".join("  " + line if line.strip() else line for line in helpers.splitlines(keepends=True)) +
              branch + "    return isCanariasOrigin ? isExactCanariasCall : null;\n  };\n")
    return constants, helpers, branch, module


def xunta_extraction(legacy):
    constants = legacy[legacy.index("  const xuntaOrigin ="):legacy.index("  const maxUriChars =")]
    helpers = legacy[legacy.index("  function isExactXuntaProperties"):legacy.index("  function interceptCertificateSelection")]
    start = legacy.index("xSel && location.href === xuntaPage")
    selection = legacy[start:legacy.index(") {\n      valid = true;", start)]
    start = legacy.index("    const isXuntaOrigin =")
    branch = legacy[start:legacy.index("    if (isXuntaOrigin && !isExactXuntaCall)", start)]
    indent = lambda source: "".join("  " + line if line.strip() else line for line in source.splitlines(keepends=True))
    module = ("\n  {\n" + indent(constants) + "\n" + indent(helpers) +
              "    matchesXuntaSelection = a =>\n      " + selection + ";\n\n" +
              "    matchesXuntaSign = (args, successCallback, errorCallback) => {\n" + indent(branch) +
              "      return isXuntaOrigin ? isExactXuntaCall : null;\n    };\n  }\n")
    return constants, helpers, selection, branch, module


def acceda_extraction(legacy):
    origin = ""
    constants = legacy[legacy.index('  const accedaOrigin ='):legacy.index('  const badajozOrigin =')]
    start = legacy.index('    const isAccedaOrigin =')
    branch = legacy[start:legacy.index('    if (isAccedaOrigin && !isExactAccedaCall)', start)]
    module = ('\n  matchesAccedaSign = (args, successCallback, errorCallback) => {\n' +
              (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
              '    return isAccedaOrigin ? isExactAccedaCall : null;\n  };\n')
    return origin, constants, branch, module


def sevilla_extraction(legacy):
    def section(start, end):
        at = legacy.index(start)
        return legacy[at:legacy.index(end, at + len(start))]
    indent = lambda source, n=2: "".join(" " * n + line if line.strip() else line for line in source.splitlines(keepends=True))
    constants = section("  const sevillaAtseOrigin =", "  const airefOrigin =")
    challenge = section("  function isValidSevillaAtseChallenge", "  function isValidAirefPayload")
    page = section("  function isExactSevillaAtsePage", "  if (functionalSigningEnabled && sevillaAtseCompatibilityEnabled)")
    click = section("  if (functionalSigningEnabled && sevillaAtseCompatibilityEnabled)", "  function interceptSevillaAtseSetupCall")
    setup = section("  function interceptSevillaAtseSetupCall", "  function interceptUgrSetupCall")
    hooks = section("      if (sevillaAtseCompatibilityEnabled)", "      if (includeUgrSetup)")
    sign = section("    const isSevillaAtseOrigin =", "    const isAccedaOrigin =")
    branch = sign[:sign.index("    if (isSevillaAtseOrigin && !isExactSevillaAtseCall)")]
    assert setup.endswith("  }\n\n")
    assigned_setup = setup.replace("  function interceptSevillaAtseSetupCall(call, args) {", "  interceptSevillaAtseSetupCall = (call, args) => {", 1)[:-5] + "  };\n\n"
    module = ("\n  {\n" + indent(constants) + "\n" + indent(challenge) + indent(page) + indent(assigned_setup) +
        "    installSevillaAtseClickHook = () => {\n" + indent(click, 4) + "    };\n\n" +
        "    installSevillaAtseMethodHooks = value => {\n" + hooks + "    };\n\n" +
        "    matchesSevillaAtseSign = (args, successCallback, errorCallback) => {\n" + indent(branch) +
        "      if (isSevillaAtseOrigin && !isExactSevillaAtseCall) sevillaAtseSetupState = 0;\n" +
        "      if (isExactSevillaAtseCall) sevillaAtseSetupState = 0;\n" +
        "      return isSevillaAtseOrigin ? isExactSevillaAtseCall : null;\n    };\n  }\n")
    stub = ("  // null: not applicable; false: reject; true: exact tuple.\n" +
        "  let matchesSevillaAtseSign = () => null;\n" +
        "  let interceptSevillaAtseSetupCall = () => false;\n" +
        "  let installSevillaAtseClickHook = () => {};\n" +
        "  let installSevillaAtseMethodHooks = () => {};\n")
    dispatch = ("    const isExactSevillaAtseCall = matchesSevillaAtseSign(args, successCallback, errorCallback);\n" +
        "    if (isExactSevillaAtseCall === false) {\n      rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n      return true;\n    }\n")
    return dict(constants=constants, challenge=challenge, group=page + click + setup, hooks=hooks,
                sign=sign, module=module, stub=stub, dispatch=dispatch)


def ugr_extraction(legacy):
    def section(start, end):
        at = legacy.index(start)
        return legacy[at:legacy.index(end, at + len(start))]
    indent = lambda source: "".join("  " + line if line.strip() else line for line in source.splitlines(keepends=True))
    origin = section("  const ugrOrigin =", "  const cantabriaOrigin =")
    constants = section("  const ugrLiteral =", "  const jccmOrigin =")
    setup = section("  function interceptUgrSetupCall", "  function receiveMelillaBatchResult")
    hooks = section("      if (includeUgrSetup)", "    } catch (_) {")
    branch = section("    const isExactUgrLiteralCall =", "    const isExactCantabriaCall =")
    assert setup.endswith("  }\n\n")
    assigned_setup = setup.replace("  function interceptUgrSetupCall(call, args) {", "  interceptUgrSetupCall = (call, args) => {", 1)[:-5] + "  };\n\n"
    expression = branch.split("=\n", 1)[1].lstrip()
    module = ("\n  {\n" + indent(origin + constants) + "\n" + indent(assigned_setup) +
        "    installUgrMethodHooks = (value, includeUgrSetup) => {\n" + hooks + "    };\n\n" +
        "    encodeUgrLiteral = () => ugrLiteralBase64;\n" +
        "    matchesUgrSign = args => {\n      return " + expression + "    };\n  }\n")
    stub = ("  let matchesUgrSign = () => false;\n  let interceptUgrSetupCall = () => false;\n" +
        "  let installUgrMethodHooks = () => {};\n  let encodeUgrLiteral = () => null;\n")
    return dict(origin=origin, constants=constants, setup=setup, hooks=hooks, branch=branch,
                module=module, stub=stub)


def badajoz_extraction(legacy):
    def section(start, end):
        at = legacy.index("\n" + start) + 1
        return legacy[at:legacy.index("\n" + end, at) + 1]
    state = section("  let badajozSignHookReadyDiagnosticPosted", "  function markBadajozSignHookReady")
    state = state.removesuffix("\n")
    mark = section("  function markBadajozSignHookReady", "  function postShimDiagnostic")
    once = section("  function postBadajozDiagnosticOnce", '  postShimDiagnostic("SCRIPT_INSTALLED");')
    constants = section("  const badajozOrigin =", "  const euskadiProfileId =")
    wrap = section("  function wrapBadajozDiagnostic", "  function wrapMiniApplet(")
    repair = section("        if (badajozCompatibilityEnabled)", "      } catch (_) {")
    late = section("    if (badajozCompatibilityEnabled)", "    startHookGuard();")
    branch = section("    const isBadajozOrigin =", "    if (isBadajozOrigin && !isExactBadajozCall)")
    first = '        if (call === "SIGN" && badajozCompatibilityEnabled &&\n            !badajozSignHookReadyDiagnosticPosted) {\n          markBadajozSignHookReady();\n        }\n'
    second = '    if (typeof current === "function" && call === "SIGN" && badajozCompatibilityEnabled &&\n        !badajozSignHookReadyDiagnosticPosted) {\n      markBadajozSignHookReady();\n    }\n'
    stub = ('  // null: not applicable; false: reject; true: exact tuple.\n'
            '  let matchesBadajozSign = () => null;\n'
            '  let getBadajozExtraProperties = () => null;\n'
            '  let notifyBadajozSignHook = () => {};\n'
            '  let repairBadajozDiagnostics = () => {};\n'
            '  let installBadajozLateHooks = () => {};\n')
    indent = lambda s: ''.join('  '+line if line.strip() else line for line in s.splitlines(keepends=True))
    module = ('\n  {\n' + indent(state + constants + '\n' + mark + once + wrap) +
              '    getBadajozExtraProperties = () => badajozExtraProperties;\n\n' +
              '    notifyBadajozSignHook = call => {\n' +
              '      if (call === "SIGN" && badajozCompatibilityEnabled &&\n' +
              '          !badajozSignHookReadyDiagnosticPosted) {\n' +
              '        markBadajozSignHookReady();\n      }\n    };\n\n' +
              '    repairBadajozDiagnostics = () => {\n' + repair + '    };\n\n' +
              '    installBadajozLateHooks = () => {\n' + indent(late) + '    };\n\n' +
              '    matchesBadajozSign = (args, successCallback, errorCallback) => {\n' + indent(branch) +
              '      return isBadajozOrigin ? isExactBadajozCall : null;\n    };\n  }\n')
    edits = [
        (state, stub),
        (mark + '  function postShimDiagnostic', '  function postShimDiagnostic'),
        (once + '  postShimDiagnostic("SCRIPT_INSTALLED");', '  postShimDiagnostic("SCRIPT_INSTALLED");'),
        (constants + '  const euskadiProfileId =', '  const euskadiProfileId ='),
        (wrap + '  function wrapMiniApplet(', '  function wrapMiniApplet('),
        (repair, '        repairBadajozDiagnostics();\n'),
        (late, '    installBadajozLateHooks();\n'),
        (first, '        notifyBadajozSignHook(call);\n'),
        (second, '    if (typeof current === "function") notifyBadajozSignHook(call);\n'),
        (branch, '    const isExactBadajozCall = matchesBadajozSign(args, successCallback, errorCallback);\n'),
        ('if (isBadajozOrigin && !isExactBadajozCall)', 'if (isExactBadajozCall === false)'),
        ('extraProperties: isExactBadajozCall ? badajozExtraProperties : args[3]', 'extraProperties: isExactBadajozCall ? getBadajozExtraProperties() : args[3]'),
    ]
    return module, edits


def melilla_batch_extraction(legacy):
    def section(start, end):
        at = legacy.index("\n" + start) + 1
        return legacy[at:legacy.index("\n" + end, at) + 1]
    state = "  let activeMelillaBatch = null;\n"
    body = section("  function isMelillaBatchPage", "  function interceptLugoSetupCall")
    hooks = section("      if (includeMelillaBatch)", "      if (includeLugoBatch)")
    ready = section("    const isStaBatchDocument =", "    const isLugoBatchDocument =")
    assigned, count = re.subn(
        r"(?ms)^  function (rememberMelillaBatchCreate|rememberMelillaBatchDocument|interceptMelillaBatchSign)\(args\) \{(.*?)^  \}",
        lambda m: "  " + m[1] + " = (args) => {" + m[2] + "  };", body)
    assert count == 3
    stub = ("  let rememberMelillaBatchCreate = () => {};\n"
            "  let rememberMelillaBatchDocument = () => {};\n"
            "  let interceptMelillaBatchSign = () => false;\n"
            "  let resetMelillaBatch = () => {};\n"
            "  let installMelillaBatchMethodHooks = () => {};\n"
            "  let isMelillaBatchDocument = () => false;\n")
    indent = lambda text: ''.join('  ' + line if line.strip() else line for line in text.splitlines(keepends=True))
    module = ("\n  {\n" + indent(state + assigned) +
              "    resetMelillaBatch = () => { activeMelillaBatch = null; };\n\n" +
              "    isMelillaBatchDocument = () =>\n      melillaBatchCompatibilityEnabled &&\n      window.location.origin === staBatchOrigin;\n\n" +
              "    installMelillaBatchMethodHooks = (value, includeMelillaBatch) => {\n" + hooks +
              "    };\n  }\n")
    edits = [
        (state, stub),
        (body + "  function interceptLugoSetupCall", "  function interceptLugoSetupCall"),
        (hooks, "      installMelillaBatchMethodHooks(value, includeMelillaBatch);\n"),
        (ready, "    const isStaBatchDocument = isMelillaBatchDocument();\n"),
        ("    pendingBatchCallbacks.clear();\n    activeMelillaBatch = null;",
         "    pendingBatchCallbacks.clear();\n    resetMelillaBatch();"),
    ]
    return module, edits


def lugo_batch_extraction(legacy):
    def section(start, end):
        at = legacy.index("\n" + start) + 1
        return legacy[at:legacy.index("\n" + end, at) + 1]
    constants = section("  const lugoOrigin =", "  const caibOrigin =")
    body = section("  function interceptLugoSetupCall", "  function caibRequestToken").removesuffix("\n")
    hooks = section("      if (includeLugoBatch)", "      if (includeCaibBatch)")
    ready = section("    const isLugoBatchDocument =", "    const isCaibBatchDocument =")
    assigned, count = re.subn(
        r"(?ms)^  function (interceptLugoSetupCall|interceptLugoBatchSign)\(([^)]*)\) \{(.*?)^  \}",
        lambda m: "  " + m[1] + " = (" + m[2] + ") => {" + m[3] + "  };", body)
    assert count == 2
    stub = ("  let interceptLugoSetupCall = () => false;\n"
            "  let interceptLugoBatchSign = () => false;\n"
            "  let matchesLugoBatchDocument = () => false;\n"
            "  let installLugoBatchMethodHooks = () => {};\n")
    indent = lambda text: ''.join('  ' + line if line.strip() else line for line in text.splitlines(keepends=True))
    module = ("\n  {\n" + indent(constants + "\n" + assigned) + "\n" +
              "    matchesLugoBatchDocument = () => {\n" + indent(ready) +
              "      return isLugoBatchDocument;\n    };\n\n" +
              "    installLugoBatchMethodHooks = (value, includeLugoBatch) => {\n" + hooks +
              "    };\n  }\n")
    edits = [
        (constants, stub),
        ("\n" + body + "\n  function caibRequestToken", "\n  function caibRequestToken"),
        (hooks, "      installLugoBatchMethodHooks(value, includeLugoBatch);\n"),
        (ready, "    const isLugoBatchDocument = matchesLugoBatchDocument();\n"),
    ]
    return module, edits, body


def caib_batch_extraction(legacy):
    def section(start, end):
        at = legacy.index("\n" + start) + 1
        return legacy[at:legacy.index("\n" + end, at) + 1]
    constants = section("  const caibOrigin =", "  const iPage =")
    body = section("  function caibRequestToken", "  function isExactSevillaAtsePage").removesuffix("\n")
    hooks = section("      if (includeCaibBatch)", "      if (sevillaAtseCompatibilityEnabled)")
    assigned, count = re.subn(
        r"(?ms)^  function (caibRequestToken|interceptCaibSetupCall|interceptCaibBatchSign)\(([^)]*)\) \{(.*?)^  \}",
        lambda m: "  " + m[1] + " = (" + m[2] + ") => {" + m[3] + "  };", body)
    assert count == 3
    stub = ("  let caibRequestToken = () => null;\n"
            "  let interceptCaibSetupCall = () => false;\n"
            "  let interceptCaibBatchSign = () => false;\n"
            "  let installCaibBatchMethodHooks = () => {};\n")
    indent = lambda text: ''.join('  ' + line if line.strip() else line for line in text.splitlines(keepends=True))
    module = ("\n  {\n" + indent(constants + "\n" + assigned) + "\n" +
              "    installCaibBatchMethodHooks = (value, includeCaibBatch) => {\n" + hooks +
              "    };\n  }\n")
    edits = [
        (constants, stub),
        ("\n" + body + "\n  installSevillaAtseClickHook();", "\n  installSevillaAtseClickHook();"),
        (hooks, "      installCaibBatchMethodHooks(value, includeCaibBatch);\n"),
    ]
    return module, edits, body


def euskadi_post_extraction(legacy):
    def section(start, end):
        at = legacy.index("\n" + start) + 1
        return legacy[at:legacy.index("\n" + end, at) + 1]
    constants = section("  const euskadiProfileId =", "  const xuntaOrigin =")
    ids = "\n" + section("  function secureRequestId", "  let euskadiClientAuthPostSent")
    body = section("  let euskadiClientAuthPostSent", "  const closedErrorMessages =").removesuffix("\n")
    indent = lambda text: ''.join('  ' + line if line.strip() else line for line in text.splitlines(keepends=True))
    module = "\n  {\n" + indent(constants + "\n" + body) + "  }\n"
    anchor = "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesXuntaSign = () => null;"
    edits = [
        (constants + anchor, anchor),
        (body + "\n  const closedErrorMessages =", "\n  const closedErrorMessages ="),
    ]
    return module, edits, ids, body


class AfirmaShimGenerationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.parts = self.root / "config/afirma_shim"
        self.parts.mkdir(parents=True)
        self.manifest = self.parts / "manifest.json"
        self.output = self.root / "app/src/main/res/raw/afirma_shim_bootstrap.js"
        self.output.parent.mkdir(parents=True)
        self.names = [f"{i:02d}-section.js" for i in range(29)]
        self.write_manifest()
        for name in self.names:
            (self.parts / name).write_bytes((name + "\n").encode())

    def write_manifest(self, **changes):
        data = {"format": 1, "parts": self.names}
        data.update(changes)
        self.manifest.write_text(json.dumps(data), encoding="utf-8")

    def test_cantabria_is_packaged_separately_from_unchanged_core(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_cantabria.js"
        self.assertTrue(module.is_file(), "Cantabria needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        constants = legacy[legacy.index("  const cantabriaOrigin"):legacy.index("  const ugrLiteral =")]
        start = legacy.index("    const isExactCantabriaCall =")
        end = legacy.index("    const isJccmOrigin =", start)
        branch = legacy[start:end]
        expression = branch.split("=\n", 1)[1]
        expected_module = "\n  matchesCantabriaSign = args => {\n" + constants.replace("  const", "    const") + "    return " + expression.lstrip() + "  };\n"
        self.assertEqual(expected_module, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        expected_core = legacy.replace(constants, "  let matchesCantabriaSign = () => false;\n").replace(
            branch, "    const isExactCantabriaCall = matchesCantabriaSign(args);\n")
        jccm_constants = legacy[legacy.index("  const jccmOrigin ="):legacy.index("  const jccmRegistroOrigin =")]
        jccm_start = legacy.index("    const isJccmOrigin =")
        jccm_branch = legacy[jccm_start:legacy.index("    const dataB64 =", jccm_start)]
        expected_core = expected_core.replace(jccm_constants, "  let matchesJccmSign = () => false;\n").replace(
            jccm_branch, "    const isExactJccmCall = matchesJccmSign(args);\n")
        registro_constants = legacy[legacy.index("  const jccmRegistroOrigin ="):legacy.index("  const sevillaAtseOrigin =")]
        registro_start = legacy.index("    const isJccmRegistroOrigin =")
        registro_branch = legacy[registro_start:legacy.index("    if (isJccmRegistroOrigin && !isExactJccmRegistroCall)", registro_start)]
        expected_core = expected_core.replace(registro_constants,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesJccmRegistroSign = () => null;\n").replace(
            registro_branch, "    const isExactJccmRegistroCall = matchesJccmRegistroSign(args, successCallback, errorCallback);\n").replace(
            "if (isJccmRegistroOrigin && !isExactJccmRegistroCall)", "if (isExactJccmRegistroCall === false)")
        constants, helpers, branch, _ = airef_extraction(legacy)
        expected_core = expected_core.replace(constants,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesAirefSign = () => null;\n").replace(
            helpers, "").replace(branch,
            "    const isExactAirefCall = matchesAirefSign(args, successCallback, errorCallback);\n").replace(
            "if (isAirefOrigin && !isExactAirefCall)", "if (isExactAirefCall === false)")
        constants, branch, _ = cdti_extraction(legacy)
        expected_core = expected_core.replace(constants,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesCdtiSign = () => null;\n").replace(
            branch, "    const isExactCdtiCall = matchesCdtiSign(args, successCallback, errorCallback);\n").replace(
            "if (isCdtiOrigin && !isExactCdtiCall)", "if (isExactCdtiCall === false)")
        origin = legacy[legacy.index("  const policiaOrigin ="):legacy.index("  const granCanariaOrigin =")]
        constants = legacy[legacy.index("  const policiaProcedurePage ="):legacy.index("  const staBatchOrigin =")]
        start = legacy.index("    const isPoliciaOrigin =")
        branch = legacy[start:legacy.index("    if (isPoliciaOrigin && !isExactPoliciaCall)", start)]
        expected_core = expected_core.replace(origin,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesPoliciaSign = () => null;\n").replace(
            constants, "").replace(branch,
            "    const isExactPoliciaCall = matchesPoliciaSign(args, successCallback, errorCallback);\n").replace(
            "if (isPoliciaOrigin && !isExactPoliciaCall)", "if (isExactPoliciaCall === false)")
        origin, constants, branch, _ = gran_canaria_extraction(legacy)
        expected_core = expected_core.replace(origin, '  // null: not applicable; false: reject; true: exact tuple.\n  let matchesGranCanariaSign = () => null;\n').replace(constants, "").replace(
            branch, '    const isExactGranCanariaCall = matchesGranCanariaSign(args, successCallback, errorCallback);\n').replace(
            'if (isGranCanariaOrigin && !isExactGranCanariaCall)', 'if (isExactGranCanariaCall === false)')
        origin, constants, branch, _ = fuerteventura_extraction(legacy)
        expected_core = expected_core.replace(constants, '  // null: not applicable; false: reject; true: exact tuple.\n  let matchesFuerteventuraSign = () => null;\n').replace(
            branch, '    const isExactFuerteventuraCall = matchesFuerteventuraSign(args, successCallback, errorCallback);\n').replace(
            'if (isFuerteventuraOrigin && !isExactFuerteventuraCall)', 'if (isExactFuerteventuraCall === false)')
        origin, constants, branch, _ = mineco_extraction(legacy)
        expected_core = expected_core.replace(constants, '  // null: not applicable; false: reject; true: exact tuple.\n  let matchesMinecoSign = () => null;\n').replace(
            branch, '    const isExactMinecoCall = matchesMinecoSign(args, successCallback, errorCallback);\n').replace(
            'if (isMinecoOrigin && !isExactMinecoCall)', 'if (isExactMinecoCall === false)')
        constants, helpers, branch, _ = canarias_extraction(legacy)
        expected_core = expected_core.replace(constants,
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesCanariasSign = () => null;\n").replace(
            helpers, "").replace(branch,
            "    const isExactCanariasCall = matchesCanariasSign(args, successCallback, errorCallback);\n").replace(
            "if (isCanariasOrigin && !isExactCanariasCall)", "if (isExactCanariasCall === false)")
        constants, helpers, selection, branch, _ = xunta_extraction(legacy)
        expected_core = expected_core.replace(constants, '  // null: not applicable; false: reject; true: exact tuple.\n  let matchesXuntaSign = () => null;\n  let matchesXuntaSelection = () => false;\n').replace(helpers, "").replace(
            selection, "matchesXuntaSelection(a)").replace(branch, '    const isExactXuntaCall = matchesXuntaSign(args, successCallback, errorCallback);\n').replace(
            "if (isXuntaOrigin && !isExactXuntaCall)", "if (isExactXuntaCall === false)")
        origin, constants, branch, _ = acceda_extraction(legacy)
        expected_core = expected_core.replace(constants, '  // null: not applicable; false: reject; true: exact tuple.\n  let matchesAccedaSign = () => null;\n').replace(
            branch, '    const isExactAccedaCall = matchesAccedaSign(args, successCallback, errorCallback);\n').replace(
            'if (isAccedaOrigin && !isExactAccedaCall)', 'if (isExactAccedaCall === false)')
        sevilla = sevilla_extraction(legacy)
        expected_core = expected_core.replace(sevilla["constants"], sevilla["stub"]).replace(
            sevilla["challenge"], "").replace(sevilla["sign"], sevilla["dispatch"]).replace(
            sevilla["group"], "  installSevillaAtseClickHook();\n\n").replace(
            sevilla["hooks"], "      installSevillaAtseMethodHooks(value);\n")
        ugr = ugr_extraction(legacy)
        expected_core = expected_core.replace(ugr["origin"], ugr["stub"]).replace(
            ugr["constants"], "").replace(ugr["setup"], "").replace(
            ugr["hooks"], "      installUgrMethodHooks(value, includeUgrSetup);\n").replace(
            ugr["branch"], "    const isExactUgrLiteralCall = matchesUgrSign(args);\n").replace(
            "isExactUgrLiteralCall ? ugrLiteralBase64 :", "isExactUgrLiteralCall ? encodeUgrLiteral() :")
        _, edits = badajoz_extraction(legacy)
        for old, new in edits:
            self.assertEqual(1, expected_core.count(old))
            expected_core = expected_core.replace(old, new, 1)
        expected_module, edits = melilla_batch_extraction(legacy)
        self.assertEqual(expected_module, (raw / "afirma_shim_melilla_batch.js").read_text())
        for old, new in edits:
            self.assertEqual(1, expected_core.count(old))
            expected_core = expected_core.replace(old, new, 1)
        expected_module, edits, _ = lugo_batch_extraction(legacy)
        self.assertEqual(expected_module, (raw / "afirma_shim_lugo_batch.js").read_text())
        for old, new in edits:
            self.assertEqual(1, expected_core.count(old))
            expected_core = expected_core.replace(old, new, 1)
        expected_module, edits, _ = caib_batch_extraction(legacy)
        self.assertEqual(expected_module, (raw / "afirma_shim_caib_batch.js").read_text())
        for old, new in edits:
            self.assertEqual(1, expected_core.count(old))
            expected_core = expected_core.replace(old, new, 1)
        expected_module, edits, expected_ids, _ = euskadi_post_extraction(legacy)
        self.assertEqual(expected_module, (raw / "afirma_shim_euskadi_post.js").read_text())
        self.assertEqual(expected_ids, (raw / "afirma_shim_request_ids.js").read_text())
        for old, new in edits:
            self.assertEqual(1, expected_core.count(old))
            expected_core = expected_core.replace(old, new, 1)
        self.assertEqual(expected_core, core)

    def test_jccm_is_packaged_separately_with_unchanged_exact_tuple(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_jccm.js"
        self.assertTrue(module.is_file(), "JCCM needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        constants = legacy[legacy.index("  const jccmOrigin ="):legacy.index("  const jccmRegistroOrigin =")]
        start = legacy.index("    const isJccmOrigin =")
        branch = legacy[start:legacy.index("    const dataB64 =", start)]
        expected = "\n  matchesJccmSign = args => {\n" + constants.replace("  const", "    const") + branch + "    return isExactJccmCall;\n  };\n"
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://ventanillaelectronica.jccm.es", core)
        self.assertNotIn("QUJDREU=", core)
        self.assertIn("    const isExactJccmCall = matchesJccmSign(args);\n", core)

    def test_jccm_registro_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_jccm_registro.js"
        self.assertTrue(module.is_file(), "JCCM Registro needs an independent optional body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        constants = legacy[legacy.index("  const jccmRegistroOrigin ="):legacy.index("  const sevillaAtseOrigin =")]
        start = legacy.index("    const isJccmRegistroOrigin =")
        end = legacy.index("    if (isJccmRegistroOrigin && !isExactJccmRegistroCall)", start)
        branch = legacy[start:end]
        expected = "\n  matchesJccmRegistroSign = (args, successCallback, errorCallback) => {\n" + constants.replace("  const", "    const").replace('\n    "', '\n      "') + branch + "    return isJccmRegistroOrigin ? isExactJccmRegistroCall : null;\n  };\n"
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://registrounicociudadanos.jccm.es", core)
        self.assertNotIn("format=XAdES Detached\\nmode=implicit", core)
        self.assertIn("    const isExactJccmRegistroCall = matchesJccmRegistroSign(args, successCallback, errorCallback);\n", core)
        rejection = '    if (isExactJccmRegistroCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n'
        self.assertIn(rejection, core)

    def test_airef_is_packaged_with_helpers_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_airef.js"
        self.assertTrue(module.is_file(), "AIReF needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = airef_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        for body in ("https://sede.airef.es", "/invesiteRE/action/solicitud/view",
                     "function isValidAirefPayload", "function isExactAirefSigningPage"):
            self.assertNotIn(body, core)
        self.assertIn("    const isExactAirefCall = matchesAirefSign(args, successCallback, errorCallback);\n", core)
        self.assertIn('    if (isExactAirefCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_cdti_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_cdti.js"
        self.assertTrue(module.is_file(), "CDTI needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, expected = cdti_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        for body in ("https://sede.cdti.gob.es", "const cdtiPage =", "const cdtiChallengePattern =",
                     "const cdtiExtraProperties ="):
            self.assertNotIn(body, core)
        self.assertIn("    const isExactCdtiCall = matchesCdtiSign(args, successCallback, errorCallback);\n", core)
        self.assertIn('    if (isExactCdtiCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)
        self.assertIn('isExactCdtiCall ? args[0] + "=" : args[0]', core)

    def test_policia_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_policia.js"
        self.assertTrue(module.is_file(), "Policia needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        origin = legacy[legacy.index("  const policiaOrigin ="):legacy.index("  const granCanariaOrigin =")]
        constants = legacy[legacy.index("  const policiaProcedurePage ="):legacy.index("  const staBatchOrigin =")]
        start = legacy.index("    const isPoliciaOrigin =")
        branch = legacy[start:legacy.index("    if (isPoliciaOrigin && !isExactPoliciaCall)", start)]
        expected = ("\n  matchesPoliciaSign = (args, successCallback, errorCallback) => {\n" +
                    (origin + constants).replace("  const", "    const").replace('\n    "', '\n      "') + branch +
                    "    return isPoliciaOrigin ? isExactPoliciaCall : null;\n  };\n")
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://sede.policia.gob.es", core)
        self.assertNotIn("const policiaExtraProperties =", core)
        dispatch = "    const isExactPoliciaCall = matchesPoliciaSign(args, successCallback, errorCallback);\n"
        self.assertIn(dispatch, core)
        self.assertLess(core.index("const isExactCdtiCall = matchesCdtiSign"), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index("const isExactJccmRegistroCall = matchesJccmRegistroSign"))
        self.assertIn('    if (isExactPoliciaCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_gran_canaria_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_gran_canaria.js"
        self.assertTrue(module.is_file(), "GranCanaria needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = gran_canaria_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn('https://sede.grancanaria.com', core)
        self.assertNotIn("const granCanariaExtraProperties =", core)
        dispatch = '    const isExactGranCanariaCall = matchesGranCanariaSign(args, successCallback, errorCallback);\n'
        self.assertIn(dispatch, core)
        self.assertLess(core.index('const isExactCanariasCall = matchesCanariasSign'), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index('const isExactFuerteventuraCall = matchesFuerteventuraSign'))
        self.assertIn('    if (isExactGranCanariaCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_fuerteventura_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_fuerteventura.js"
        self.assertTrue(module.is_file(), "Fuerteventura needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = fuerteventura_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn('https://sede.cabildofuer.es', core)
        self.assertNotIn("const fuerteventuraExtraProperties =", core)
        dispatch = '    const isExactFuerteventuraCall = matchesFuerteventuraSign(args, successCallback, errorCallback);\n'
        self.assertIn(dispatch, core)
        self.assertLess(core.index('const isExactGranCanariaCall = matchesGranCanariaSign'), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index('const isExactMinecoCall ='))
        self.assertIn('    if (isExactFuerteventuraCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_mineco_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_mineco.js"
        self.assertTrue(module.is_file(), "Mineco needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = mineco_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn('https://serviciosede.mineco.gob.es', core)
        self.assertNotIn("const minecoExtraProperties =", core)
        dispatch = '    const isExactMinecoCall = matchesMinecoSign(args, successCallback, errorCallback);\n'
        self.assertIn(dispatch, core)
        self.assertLess(core.index('const isExactFuerteventuraCall = matchesFuerteventuraSign'), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index('const isExactXuntaCall = matchesXuntaSign'))
        self.assertIn('    if (isExactMinecoCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_canarias_is_packaged_with_challenge_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_canarias.js"
        self.assertTrue(module.is_file(), "Canarias needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = canarias_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://sede.gobiernodecanarias.org", core)
        self.assertNotIn("function isExactCanariasChallenge", core)
        self.assertNotIn("const canariasExtraProperties =", core)
        dispatch = "    const isExactCanariasCall = matchesCanariasSign(args, successCallback, errorCallback);\n"
        self.assertIn(dispatch, core)
        self.assertLess(core.index("const isExactAirefCall = matchesAirefSign"), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index("const isExactGranCanariaCall = matchesGranCanariaSign"))
        self.assertIn('    if (isExactCanariasCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_xunta_is_packaged_with_sign_selection_and_exact_properties(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_xunta.js"
        self.assertTrue(module.is_file(), "Xunta needs one optional sign-and-selection body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, selection, _, expected = xunta_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        self.assertIn(selection, expected)
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://sede.xunta.gal", core)
        self.assertNotIn("function isExactXuntaProperties", core)
        self.assertNotIn("const xuntaFixedProperties", core)
        self.assertIn("else if (matchesXuntaSelection(a))", core)
        dispatch = "    const isExactXuntaCall = matchesXuntaSign(args, successCallback, errorCallback);\n"
        self.assertIn(dispatch, core)
        self.assertLess(core.index("const isExactMinecoCall = matchesMinecoSign"), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index("const isExactCdtiCall = matchesCdtiSign"))
        self.assertIn('    if (isExactXuntaCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_acceda_is_packaged_with_exact_tuple_and_rejection_dispatch(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_acceda.js"
        self.assertTrue(module.is_file(), "Acceda needs an optional packaged body")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        _, _, _, expected = acceda_extraction(legacy)
        self.assertEqual(expected, module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn('https://sede.administracionespublicas.gob.es', core)
        self.assertNotIn("const accedaExtraProperties =", core)
        dispatch = '    const isExactAccedaCall = matchesAccedaSign(args, successCallback, errorCallback);\n'
        self.assertIn(dispatch, core)
        self.assertLess(core.index('const isExactSevillaAtseCall ='), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index('const isExactAirefCall = matchesAirefSign'))
        self.assertIn('    if (isExactAccedaCall === false) {\n      rejectDirectCall(errorCallback, "INVALID_REQUEST");\n      return true;\n    }\n', core)

    def test_sevilla_state_setup_sign_and_hooks_share_one_optional_fragment(self):
        raw = ROOT / "app/src/main/res/raw"
        path = raw / "afirma_shim_sevilla_atse.js"
        self.assertTrue(path.is_file(), "Sevilla needs one optional stateful fragment")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        expected = sevilla_extraction(legacy)
        self.assertEqual(expected["module"], path.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://www.sevilla.org", core)
        self.assertNotIn("sevillaAtseSetupState", core)
        self.assertNotIn("function isValidSevillaAtseChallenge", core)
        self.assertNotIn("SEVILLA_SET_SERVLETS", core)
        self.assertIn(expected["dispatch"], core)
        self.assertLess(core.index(expected["dispatch"]), core.index("const isExactAccedaCall = matchesAccedaSign"))
        self.assertLess(core.index("  installSevillaAtseClickHook();"), core.index("  function receiveMelillaBatchResult"))
        self.assertLess(core.index("      installSevillaAtseMethodHooks(value);"), core.index("      installUgrMethodHooks(value, includeUgrSetup);"))
        self.assertIn("sevillaAtseSetupState === 3", path.read_text())
        self.assertIn("return isSevillaAtseOrigin ? isExactSevillaAtseCall : null;", path.read_text())

    def test_ugr_literal_encoding_setup_and_hooks_share_one_optional_fragment(self):
        raw = ROOT / "app/src/main/res/raw"
        path = raw / "afirma_shim_ugr.js"
        self.assertTrue(path.is_file(), "UGR needs one optional literal-and-setup fragment")
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        expected = ugr_extraction(legacy)
        self.assertEqual(expected["module"], path.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("https://sede.ugr.es", core)
        self.assertNotIn("Universidad de Granada", core)
        self.assertNotIn("VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ==", core)
        self.assertNotIn("UGR_SET_SERVLETS", core)
        dispatch = "    const isExactUgrLiteralCall = matchesUgrSign(args);\n"
        self.assertIn(dispatch, core)
        self.assertLess(core.index("const isExactBadajozCall ="), core.index(dispatch))
        self.assertLess(core.index(dispatch), core.index("const isExactCantabriaCall = matchesCantabriaSign"))
        self.assertIn("isExactUgrLiteralCall ? encodeUgrLiteral() :", core)
        self.assertIn("const hasValidUgrDataEncoding = base64Pattern.test(dataB64)", core)
        self.assertLess(core.index("      installSevillaAtseMethodHooks(value);"),
                        core.index("      installUgrMethodHooks(value, includeUgrSetup);"))
        self.assertIn("window.location.origin !== ugrOrigin", path.read_text())
        self.assertIn("return ugrCompatibilityEnabled &&", path.read_text())

    def test_badajoz_contract_state_and_diagnostics_share_one_optional_resource(self):
        raw = ROOT / "app/src/main/res/raw"
        legacy = (ROOT / "app/src/test/resources/dev/junta/firmamobile/browser/AfirmaJavascriptShimLegacy.js").read_text()
        expected, _ = badajoz_extraction(legacy)
        self.assertEqual(expected, (raw / "afirma_shim_badajoz.js").read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        for token in ("https://sede.dip-badajoz.es", "badajozSignHookReadyDiagnosticPosted",
                      "badajozDiagnosticWrappedFunctions", "badajozDiagnosticPostedStages"):
            self.assertNotIn(token, core)
        self.assertIn("if (isExactBadajozCall === false)", core)
        self.assertLess(core.index("const isExactJccmRegistroCall ="), core.index("const isExactBadajozCall ="))
        self.assertLess(core.index("const isExactBadajozCall ="), core.index("const isExactUgrLiteralCall ="))
        self.assertIn("window.setInterval(repairCurrentSigningLibraries, 500)", core)

    def test_sta_batch_family_has_its_own_resource(self):
        raw = ROOT / "app/src/main/res/raw"
        path = raw / "afirma_shim_melilla_batch.js"
        self.assertTrue(path.is_file(), "STA/Melilla needs its own optional resource")
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js",
            "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertNotIn("activeMelillaBatch", core)
        self.assertNotIn("function isMelillaBatchPage", core)
        self.assertNotIn("signBatchProcess", core)
        self.assertIn("pendingBatchCallbacks", core)
        self.assertIn("receiveMelillaBatchResult", core)
        self.assertIn("MINIAPPLET_BATCH_CANCEL", core)

    def test_lugo_batch_has_its_own_resource(self):
        raw = ROOT / "app/src/main/res/raw"
        self.assertTrue((raw / "afirma_shim_lugo_batch.js").is_file())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js",
            "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        for token in ("https://sede.deputacionlugo.org", "lugoClientBase", "lugoExtraProperties", "LUGO_CARGAR_APP_AFIRMA"):
            self.assertNotIn(token, core)
        self.assertIn("pending.lugoXml === true", core)
        self.assertIn("LUGO_XML_BATCH_CANCEL", core)
        self.assertLess(core.index('call === "BATCH_SIGN" && interceptMelillaBatchSign(args)'),
                        core.index('call === "LUGO_BATCH_SIGN" && interceptLugoBatchSign(args)'))
        self.assertLess(core.index('call === "LUGO_BATCH_SIGN" && interceptLugoBatchSign(args)'),
                        core.index('call === "CAIB_BATCH_SIGN" && interceptCaibBatchSign(args)'))

    def test_caib_batch_has_its_own_resource(self):
        raw = ROOT / "app/src/main/res/raw"
        self.assertTrue((raw / "afirma_shim_caib_batch.js").is_file())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js",
            "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        for token in ("https://intranet.caib.es", "caibClientBase", "caibSignerPathPattern",
                      "caibSignatureIdPattern", "caibBatchBase64Pattern",
                      "function decodeCaibUrlBase64", "CAIB_SET_FORCE_WS_MODE", "CAIB_SET_SERVLETS"):
            self.assertNotIn(token, core)
        self.assertIn("const isCaibBatchDocument = caibRequestToken() !== null;", core)
        self.assertIn("pending.caibXml === true", core)
        self.assertIn("CAIB_XML_BATCH_CANCEL", core)
        self.assertLess(core.index('call === "LUGO_BATCH_SIGN" && interceptLugoBatchSign(args)'),
                        core.index('call === "CAIB_BATCH_SIGN" && interceptCaibBatchSign(args)'))
        self.assertLess(core.index("installLugoBatchMethodHooks(value, includeLugoBatch);"),
                        core.index("installCaibBatchMethodHooks(value, includeCaibBatch);"))
        self.assertLess(core.index("installCaibBatchMethodHooks(value, includeCaibBatch);"),
                        core.index("installSevillaAtseMethodHooks(value);"))

    def test_euskadi_post_has_its_own_resource_without_owning_shared_ids(self):
        raw = ROOT / "app/src/main/res/raw"
        module = raw / "afirma_shim_euskadi_post.js"
        self.assertTrue(module.is_file())
        ids = (raw / "afirma_shim_request_ids.js").read_text()
        self.assertIn("function secureRequestId", ids)
        self.assertIn("crypto.randomUUID", ids)
        self.assertIn("crypto.getRandomValues", ids)
        self.assertNotIn("Math.random", ids)
        self.assertNotIn("EUSKADI_CLIENT_AUTH_POST", ids)
        self.assertNotIn("secureRequestId() {", module.read_text())
        core = "".join((raw / name).read_text() for name in (
            "afirma_shim_bootstrap.js", "afirma_shim_request_ids.js", "afirma_shim_handlers.js",
            "afirma_shim_batch_results.js", "afirma_shim_installation.js"))
        self.assertEqual(1, core.count("__JFM_EUSKADI_CLIENT_AUTH_POST_ENABLED__"))
        self.assertIn('type: "EUSKADI_CLIENT_AUTH_POST"', module.read_text())
        for token in ('type: "EUSKADI_CLIENT_AUTH_POST"', "euskadiClientAuthPostSent",
                      "eidas.izenpe.com", "eidas2.izenpe.com", "isSafeEuskadiOpaqueRequest",
                      "HTMLFormElement.prototype.submit", "HTMLFormElement.prototype.requestSubmit"):
            self.assertNotIn(token, core)
        self.assertIn("QA_PORTAL_DIAGNOSTIC", core)
        self.assertIn("MINIAPPLET_SIGN", core)
        self.assertEqual(1, core.count("function secureRequestId"))

    def test_repository_resource_is_generated_exactly(self):
        actual = compose(ROOT)
        self.assertIn(b"matchesCantabriaSign(args)", actual)
        self.assertFalse(synchronize(ROOT, write=False))

    def test_packaged_fragments_reconstruct_source_bytes(self):
        raw = ROOT / "app/src/main/res/raw"
        names = ["afirma_shim_bootstrap.js", "afirma_shim_cantabria.js", "afirma_shim_jccm.js",
                 "afirma_shim_jccm_registro.js", "afirma_shim_airef.js", "afirma_shim_cdti.js", "afirma_shim_policia.js", "afirma_shim_gran_canaria.js", "afirma_shim_fuerteventura.js", "afirma_shim_mineco.js", "afirma_shim_canarias.js", "afirma_shim_xunta.js", "afirma_shim_acceda.js", "afirma_shim_sevilla_atse.js", "afirma_shim_ugr.js", "afirma_shim_badajoz.js", "afirma_shim_request_ids.js", "afirma_shim_euskadi_post.js", "afirma_shim_handlers.js", "afirma_shim_melilla_batch.js", "afirma_shim_lugo_batch.js", "afirma_shim_caib_batch.js", "afirma_shim_batch_results.js", "afirma_shim_installation.js"]
        self.assertEqual(compose(ROOT), b"".join((raw / name).read_bytes() for name in names))
        self.assertEqual(set(names), {p.name for p in raw.glob("afirma_shim*.js")})

    def test_concatenation_is_ordered_without_extra_separators(self):
        self.assertEqual("".join(name + "\n" for name in self.names).encode(), compose(self.root))
        self.names.reverse()
        self.write_manifest()
        self.assertEqual("".join(name + "\n" for name in self.names).encode(), compose(self.root))

    def test_check_is_read_only_and_detects_stale_output(self):
        self.output.write_bytes(b"stale")
        with self.assertRaises(ValueError):
            synchronize(self.root, write=False)
        self.assertEqual(b"stale", self.output.read_bytes())

    def test_write_is_explicit_idempotent_and_preserves_mode(self):
        self.output.write_bytes(b"stale")
        self.output.chmod(0o640)
        self.assertTrue(synchronize(self.root, write=True))
        self.assertEqual(0o640, self.output.stat().st_mode & 0o777)
        self.assertFalse(synchronize(self.root, write=True))
        self.assertFalse(synchronize(self.root, write=False))
        self.assertEqual([], list(self.output.parent.glob(".afirma-shim-*")))

    def test_failed_replace_preserves_existing_resource_and_removes_temporary_file(self):
        self.output.write_bytes(b"original")
        with mock.patch("tools.generate_afirma_shim.os.replace", side_effect=OSError("synthetic failure")):
            with self.assertRaises(OSError):
                synchronize(self.root, write=True)
        self.assertEqual(b"original", self.output.read_bytes())
        self.assertEqual([], list(self.output.parent.glob(".afirma-shim-*")))

    def test_missing_and_unlisted_fragments_fail_closed(self):
        (self.parts / self.names[0]).unlink()
        with self.assertRaises(ValueError):
            compose(self.root)
        (self.parts / self.names[0]).write_text("bootstrap\n")
        (self.parts / "99-unused.js").write_text("extra\n")
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_missing_and_extra_packaged_fragments_fail_closed(self):
        synchronize(self.root, write=True)
        self.output.unlink()
        with self.assertRaises(ValueError):
            synchronize(self.root)
        synchronize(self.root, write=True)
        (self.output.parent / "afirma_shim_unused.js").write_text("extra\n")
        for write in (False, True):
            with self.subTest(write=write), self.assertRaises(ValueError):
                synchronize(self.root, write=write)

    def test_duplicate_and_unsafe_names_fail_closed(self):
        for names in [[self.names[0]] * 2, ["../escape.js"], ["/absolute.js"], ["subdir/10-file.js"], [], [1]]:
            with self.subTest(names=names):
                self.write_manifest(parts=names)
                with self.assertRaises(ValueError):
                    compose(self.root)

    def test_unknown_manifest_fields_and_versions_fail_closed(self):
        for changes in [{"format": 2}, {"format": True}, {"extra": "ignored"}, {"parts": "00-bootstrap.js"}]:
            with self.subTest(changes=changes):
                self.write_manifest(**changes)
                with self.assertRaises(ValueError):
                    compose(self.root)
        self.manifest.write_text('{"format":1,"format":1,"parts":[]}')
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_non_utf8_empty_and_crlf_parts_are_rejected(self):
        for data in [b"", b"\xff", b"line\r\n"]:
            with self.subTest(data=data):
                (self.parts / self.names[0]).write_bytes(data)
                with self.assertRaises(ValueError):
                    compose(self.root)

    def test_source_and_manifest_symlinks_are_rejected(self):
        p = self.parts / self.names[0]
        p.unlink()
        p.symlink_to(self.parts / self.names[1])
        with self.assertRaises(ValueError):
            compose(self.root)
        p.unlink()
        p.write_text("bootstrap\n")
        real = self.parts / "saved-manifest.json"
        self.manifest.rename(real)
        self.manifest.symlink_to(real)
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_source_directory_symlink_is_rejected(self):
        real = self.root / "real-parts"
        self.parts.rename(real)
        self.parts.symlink_to(real, target_is_directory=True)
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_output_symlink_is_rejected_without_touching_target(self):
        target = self.root / "target"
        target.write_bytes(b"untouched")
        self.output.symlink_to(target)
        with self.assertRaises(ValueError):
            synchronize(self.root, write=True)
        self.assertEqual(b"untouched", target.read_bytes())

    def test_runtime_bound_counts_utf16_units_not_unicode_codepoints(self):
        (self.parts / self.names[0]).write_text("😀" * (48 * 1024) + "\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_final_newline_and_manifest_byte_bound_are_enforced(self):
        (self.parts / self.names[0]).write_bytes(b"missing final newline")
        with self.assertRaises(ValueError):
            compose(self.root)
        self.manifest.write_bytes(b" " * 8193)
        with self.assertRaises(ValueError):
            compose(self.root)

    def test_script_character_and_byte_bounds_are_enforced(self):
        (self.parts / self.names[0]).write_bytes(b"x" * (96 * 1024))
        with self.assertRaises(ValueError):
            compose(self.root)
        (self.parts / self.names[0]).write_bytes(b"x" * (384 * 1024 + 1))
        with self.assertRaises(ValueError):
            compose(self.root)


if __name__ == "__main__":
    unittest.main()
