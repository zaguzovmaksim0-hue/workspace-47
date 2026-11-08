"""Execute the shipped login predicates and exact-click scripts without a portal or certificate."""
import json
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
RECIPE = ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt"


class CertificateLoginWaveDTest(unittest.TestCase):
    def node(self, script):
        result = subprocess.run(["node", "-e", script], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_aemet_auto_is_limited_to_the_exact_login_tuple(self):
        predicate = (ROOT / "config/afirma_shim/35-aemet.js").read_text()
        self.node(r"""
            const assert = require('node:assert/strict');
            let aemetCompatibilityEnabled = true, matchesAemetSign;
            const base64Pattern = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;
            const window = {location: {origin: 'https://sede.aemet.gob.es',
              href: 'https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso', search: '', hash: ''}};
            window.top = window;
            PREDICATE
            const args = text => [btoa(text), 'SHA1withRSA', 'AUTO', null, () => {}, () => {}];
            const match = a => matchesAemetSign(a, a[4], a[5]);
            for (const text of ['0', '1', '9999', '10000']) assert.equal(match(args(text)), true);
            for (const text of ['', '00', '01', '10001', '-1', '+1', '1.0', ' 1', '1\n',
                '%PDF-1.7', '<xml/>', 'https://example.org/', '123456']) {
              assert.equal(match(args(text)), false, text);
            }
            for (const [at, value] of [[0, 'MQ'], [0, 'MR=='], [1, 'SHA256withRSA'], [2, 'CAdES'],
                [2, 'auto'], [3, ''], [3, 'null'], [3, {}], [4, null], [5, null]]) {
              const a = args('1'); a[at] = value; assert.equal(match(a), false);
            }
            assert.equal(match([...args('1'), 'extra']), false);
            for (const suffix of ['/', '?x=1', '#x', '/other']) {
              window.location.href += suffix;
              assert.equal(match(args('1')), false);
              window.location.href = 'https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso';
            }
            window.top = {}; assert.equal(match(args('1')), false); window.top = window;
            window.location.origin = 'https://foreign.example'; assert.equal(match(args('1')), null);
            window.location.origin = 'https://sede.aemet.gob.es';
            aemetCompatibilityEnabled = false; assert.equal(match(args('1')), null);
        """.replace("PREDICATE", predicate))

    def test_canarias_accepts_only_the_two_reviewed_pages(self):
        predicate = (ROOT / "config/afirma_shim/34-canarias.js").read_text()
        profile = next(p for p in json.loads((ROOT / "config/site_profiles_v1.json").read_text())["profiles"]
                       if p["profileId"] == "canarias-sede")
        properties = "\n".join(f"{k}={v}" for k, v in profile["operationPolicies"][0]["fixedExtraProperties"].items())
        self.node(r"""
            const assert = require('node:assert/strict');
            let canariasCompatibilityEnabled = true, matchesCanariasSign;
            const base64Pattern = /^[A-Za-z0-9+/]+={0,2}$/;
            const window = {location: {origin: 'https://sede.gobiernodecanarias.org', search: '', hash: ''}};
            PREDICATE
            const a = [btoa('Sun, 08 Nov 2026 07:00:00 GMT'), 'SHA1withRSA', 'CAdES', PROPERTIES,
              () => {}, () => {}];
            for (const path of ['/sede/identificacion', '/sede/movil/identificacion']) {
              window.location.href = window.location.origin + path;
              assert.equal(matchesCanariasSign(a, a[4], a[5]), true);
              for (const suffix of ['/', '/other', '?x=1', '#x']) {
                window.location.href = window.location.origin + path + suffix;
                assert.equal(matchesCanariasSign(a, a[4], a[5]), false);
              }
            }
        """.replace("PREDICATE", predicate).replace("PROPERTIES", json.dumps(properties)))

    def test_canarias_certificate_link_requires_exact_title_label_and_href(self):
        source = RECIPE.read_text().split("private fun clickExactLabeledAnchor(", 1)[1]
        script = source.split("webView.evaluateJavascript(", 1)[1].split('"""', 2)[1]
        page = "https://sede.gobiernodecanarias.org/sede/movil/identificacionmenu"
        label = "Certificado Digital / DNI-E"
        values = dict(matchUrlWithoutQuery=False, quotedExpectedUrl=page, quotedShadowHost="",
                      quotedExpectedLabel=label, quotedExpectedHref="identificacion",
                      quotedExpectedTitle=label, quotedAnchorType=None, quotedAnchorAriaLabel=None,
                      quotedExpectedOnClick=None, quotedFormName=None, quotedFormAction=None,
                      quotedCssClass=None)
        rendered = re.sub(r"\$(\w+)", lambda m: json.dumps(values[m[1]]), script)
        self.node(r"""
            const assert = require('node:assert/strict');
            const run = (change = {}) => {
              let clicks = 0;
              const location = {href: PAGE};
              const link = {
                innerText: change.label ?? LABEL,
                getAttribute: name => ({href: change.href ?? 'identificacion',
                  title: Object.hasOwn(change, 'title') ? change.title : LABEL}[name] ?? null),
                click: () => clicks++
              };
              const document = {querySelectorAll: () => change.duplicate ? [link, link] : [link]};
              return [eval(SCRIPT), clicks];
            };
            for (const change of [{title: null}, {title: 'Other'}, {title: LABEL + ' '},
                {label: 'Other'}, {href: 'other'}]) assert.deepEqual(run(change), [0, 0]);
            assert.deepEqual(run({duplicate: true}), [2, 0]);
            assert.deepEqual(run(), [1, 1]);
        """.replace("SCRIPT", json.dumps(rendered)).replace("PAGE", json.dumps(page))
                  .replace("LABEL", json.dumps(label)))
        recipe = RECIPE.read_text().split("CANARIAS_PORTAL_ID -> {", 1)[1].split("\n            }", 1)[0]
        self.assertIn('expectedTitle = "Certificado Digital / DNI-E"', recipe)

    def test_canarias_page_migration_pins_profile_version_two_and_reviewed_evidence(self):
        profile = next(p for p in json.loads((ROOT / "config/site_profiles_v1.json").read_text())["profiles"]
                       if p["profileId"] == "canarias-sede")
        self.assertEqual(2, profile["profileVersion"])
        adapter = (ROOT / "app/src/main/java/dev/junta/firmamobile/signing/"
                   "CanariasCertificateLoginCadesAdapter.kt").read_text()
        parser = (ROOT / "app/src/main/java/dev/junta/firmamobile/profile/"
                  "SiteProfileCatalogParser.kt").read_text()
        self.assertRegex(adapter, r"const val PROFILE_VERSION = 2\b")
        self.assertRegex(parser, r"const val CANARIAS_PROFILE_VERSION = 2\b")
        evidence = {item["url"]: item["reviewedOn"] for item in profile["evidence"]}
        for path in ("/sede/identificacion", "/sede/movil/identificacion"):
            url = "https://sede.gobiernodecanarias.org" + path
            self.assertEqual("2026-11-08", evidence[url])
            self.assertIn(f'"{url}" to "2026-11-08"', parser)

    def test_soria_waits_for_late_handler_and_dgsfp_requires_its_parent_anchor(self):
        source = RECIPE.read_text().split("internal fun clickExactButton(", 1)[1]
        script = source.split("webView.evaluateJavascript(", 1)[1].split('"""', 2)[1]
        values = dict(matchUrlWithoutQuery=False, quotedExpectedUrl="https://login.example/",
                      quotedExpectedElementId="b_certificado", quotedExpectedLabel="ACCEDER",
                      quotedExpectedAriaLabel=None, quotedExpectedOnClick="pulsarCertificado();",
                      quotedExpectedType="button", quotedExpectedTag="button", quotedExpectedSrc=None,
                      requireNoOnClick=False, quotedFormName=None, quotedFormAction=None,
                      quotedWaitForBadajozSignHook=False, quotedHandlerName="pulsarCertificado",
                      quotedParentAnchorClass=None, requireVisible=False, waitForAemetSignHook=False,
                      quotedExpectedName=None, matchCanariasLoginAction=False,
                      waitForTransportesSignReady=False, waitForLugoSignReady=False,
                      matchPalenciaSessionPath=False)
        rendered = re.sub(r"\$(\w+)", lambda m: json.dumps(values[m[1]]), script)
        self.node(r"""
            const assert = require('node:assert/strict');
            let clicks = 0;
            const location = {href: 'https://login.example/'};
            const window = {};
            const element = {id: 'b_certificado', innerText: 'ACCEDER',
              getAttribute: name => ({type: 'button', onclick: 'pulsarCertificado();'}[name] ?? null),
              click: () => clicks++};
            const document = {readyState: 'loading', querySelectorAll: () => [element]};
            const script = SCRIPT;
            for (let i = 0; i < 5; i++) assert.deepEqual(eval(script),
              {readyState: 'loading', handlerReady: false, clicked: false});
            document.readyState = 'complete'; assert.deepEqual(eval(script),
              {readyState: 'complete', handlerReady: false, clicked: false});
            assert.equal(clicks, 0);
            window.pulsarCertificado = () => {};
            assert.equal(eval(script), 1); assert.equal(clicks, 1);
            location.href = 'https://foreign.example/'; assert.equal(eval(script), 0);
        """.replace("SCRIPT", json.dumps(rendered)))

        values.update(quotedExpectedElementId="imgClave", quotedExpectedLabel="", quotedExpectedOnClick=None,
                      quotedExpectedType=None, quotedExpectedTag="img", quotedExpectedSrc="clave.png",
                      quotedHandlerName=None, quotedParentAnchorClass="text-center")
        rendered = re.sub(r"\$(\w+)", lambda m: json.dumps(values[m[1]]), script)
        self.node(r"""
            const assert = require('node:assert/strict');
            let clicks = 0, target = '_self';
            const location = {href: 'https://login.example/'};
            const anchor = {tagName: 'A', className: 'text-center', getAttribute: () => target};
            const element = {id: 'imgClave', innerText: '', parentElement: anchor,
              getAttribute: name => name === 'src' ? 'clave.png' : null, click: () => clicks++};
            const document = {querySelectorAll: () => [element]};
            const script = SCRIPT;
            assert.equal(eval(script), 1); assert.equal(clicks, 1);
            for (const value of ['_blank', null]) { target = value; assert.equal(eval(script), 2); }
            target = '_self'; anchor.tagName = 'DIV'; assert.equal(eval(script), 2);
            anchor.tagName = 'A'; anchor.className = 'other'; assert.equal(eval(script), 2);
            assert.equal(clicks, 1);
        """.replace("SCRIPT", json.dumps(rendered)))

    def test_canarias_requires_the_live_form_to_have_the_reviewed_login_action(self):
        source = RECIPE.read_text().split("internal fun clickExactButton(", 1)[1]
        script = source.split("webView.evaluateJavascript(", 1)[1].split('"""', 2)[1]
        action = ("https://sede.gobiernodecanarias.org/-/action/"
                  "c002ea10-8036-11df-9987-52c39c0cc1c2/e8e67340-afe4-11e6-9a06-1eb47e039c2b/es_ES/"
                  "a15be610-b249-11e6-9a06-11991fa5bb48/accionProcesaFormularioLogin")
        page = "https://sede.gobiernodecanarias.org/sede/movil/identificacion"
        values = dict(matchUrlWithoutQuery=False, quotedExpectedUrl=page,
                      quotedExpectedElementId=None, quotedExpectedLabel="Validarme",
                      quotedExpectedAriaLabel=None, quotedExpectedOnClick="firmar(formData);",
                      quotedExpectedType="button", quotedExpectedTag="button", quotedExpectedSrc=None,
                      requireNoOnClick=False, quotedFormName="formData", quotedFormAction=action,
                      quotedWaitForBadajozSignHook=False, quotedHandlerName="firmar",
                      quotedParentAnchorClass=None, requireVisible=True, waitForAemetSignHook=False,
                      quotedExpectedName=None, matchCanariasLoginAction=True,
                      waitForTransportesSignReady=False, waitForLugoSignReady=False,
                      matchPalenciaSessionPath=False)
        rendered = re.sub(r"\$(\w+)", lambda m: json.dumps(values[m[1]]), script)
        self.node(r"""
            const assert = require('node:assert/strict');
            const run = (change = {}) => {
              let clicks = 0;
              const location = {href: PAGE};
              const params = new URLSearchParams({i3l: 'es_ES', i3pn: 'identificacion', i3sc: '', i3pt: 'S',
                i3d: 'e8e67340-afe4-11e6-9a06-1eb47e039c2b', i3csrf: 'synthetic'});
              if (change.params) change.params(params);
              const form = {id: 'formData', name: 'formData', action: (change.action || ACTION) + '?' + params,
                getAttribute: n => n === 'method' ? change.method || 'post' : change.target || null};
              const hookedSign = ready => Object.assign(() => {}, {__jfmSignHook: ready});
              const selected = change.selected || 'AutoScript';
              const window = {formData: change.global ? {} : form,
                AutoScript: {sign: hookedSign(selected !== 'AutoScript' || change.hook !== false)},
                MiniApplet: {sign: hookedSign(selected !== 'MiniApplet' || change.hook !== false)}};
              window.Script = change.noSelected ? null : window[selected];
              if (change.noSign) delete window.Script.sign;
              if (change.markerOnly) window.Script.sign = {__jfmSignHook: true};
              if (change.replacedGlobal) window[selected] = {sign: hookedSign(true)};
              if (change.noLibraries) window.Script = window.AutoScript = window.MiniApplet = undefined;
              if (!change.noHandler) window.firmar = () => {};
              const element = {innerText: change.accept ? 'Aceptar' : 'Validarme',
                form: change.detached ? {} : form, disabled: !!change.disabled,
                getClientRects: () => change.noRects ? [] : [{}],
                getAttribute: n => ({type: change.accept ? 'submit' : 'button',
                  onclick: change.accept ? 'return firmar();' : 'firmar(formData);'}[n] ?? null),
                click: () => clicks++};
              const getComputedStyle = () => ({visibility: change.hidden ? 'hidden' : 'visible'});
              const document = {readyState: 'complete', forms: change.duplicate ? [form, form] : [form],
                querySelectorAll: () => [element]};
              if (change.delayedHook) {
                window.Script.sign = hookedSign(false);
                for (let i = 0; i < 5; i++) assert.equal(eval(SCRIPT), 0);
                assert.equal(clicks, 0);
                window.Script.sign = hookedSign(true);
              }
              return [eval(SCRIPT), clicks];
            };
            assert.deepEqual(run(), [1, 1]);
            for (const selected of ['AutoScript', 'MiniApplet']) {
              assert.deepEqual(run({selected, hook: false}), [0, 0]);
              assert.deepEqual(run({selected, replacedGlobal: true}), [1, 1]);
              assert.deepEqual(run({selected, hook: false, replacedGlobal: true}), [0, 0]);
              assert.deepEqual(run({selected, delayedHook: true}), [1, 1]);
              assert.deepEqual(run({selected}), [1, 1]);
            }
            for (const change of [{noSelected: true}, {noLibraries: true}, {noSign: true},
                {markerOnly: true}]) assert.deepEqual(run(change), [0, 0]);
            for (const change of [{detached: true}, {global: true}, {method: 'get'}, {target: '_blank'},
                {duplicate: true}, {action: 'https://foreign.example/accionProcesaFormularioLogin'},
                {action: 'https://sede.gobiernodecanarias.org/file'},
                {params: p => p.set('i3pn', 'tramite')}, {params: p => p.delete('i3csrf')},
                {params: p => p.append('i3csrf', 'duplicate')}, {params: p => p.append('other', 'value')}]) {
              assert.deepEqual(run(change), [2, 0]);
            }
            assert.deepEqual(run({noHandler: true}),
              [{readyState: 'complete', handlerReady: false, clicked: false}, 0]);
            for (const change of [{accept: true}, {hidden: true}, {disabled: true}, {noRects: true}]) {
              assert.deepEqual(run(change), [0, 0]);
            }
        """.replace("SCRIPT", json.dumps(rendered)).replace("ACTION", json.dumps(action))
                  .replace("PAGE", json.dumps(page)))
