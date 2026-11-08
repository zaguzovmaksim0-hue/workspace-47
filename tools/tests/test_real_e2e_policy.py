from __future__ import annotations

import json
import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

from tools.generate_afirma_shim import RESOURCE_RANGES

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/real-e2e.yml"
RUNNER = ROOT / "scripts/ci/run-real-e2e.sh"
REPORT_HELPER = ROOT / "scripts/ci/real_e2e_report.py"
INSTRUMENTATION = ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt"
CATALOG = ROOT / "app/src/main/res/raw/public_portal_catalog_v1.json"


class RealE2ePolicyTest(unittest.TestCase):
    def read(self, path: Path) -> str:
        self.assertTrue(path.is_file(), f"missing {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8")

    def helper(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(REPORT_HELPER), *args],
            cwd=ROOT,
            check=False,
            text=True,
            capture_output=True,
        )

    def test_wave_f_login_recipes_keep_the_reviewed_controls_and_eight_portal_boundary(self) -> None:
        source = self.read(INSTRUMENTATION)
        wave = source.split("BOE_PORTAL_ID ->", 1)[1].split("CSN_PORTAL_ID ->", 1)[0]
        for portal in ("CMT", "FONDOS", "IMSERSO", "INE", "SEPES", "DGOJ", "TEA"):
            self.assertIn(f"{portal}_PORTAL_ID ->", wave)
        for excluded in ("ITJ", "OEPM", "CYL", "FORMENTERA", "CNMC"):
            self.assertNotIn(f"{excluded}_PORTAL_ID ->", wave)
        self.assertEqual(5, wave.count("clickClaveAfirmaProvider(scenario)"))
        for pin in ("Consulta de sus trámites", "Acceso con Cl@ve", "VerCargando();",
                    "javascript:__doPostBack('ImageButtonClave2','')", 'expectedFormName = "servForm"',
                    'expectedFormAction = FONDOS_LOGIN_URL', 'INE_ENTRY_URL + "#accFig1254735165102"',
                    'expectedHref = "/tramite/estado_tramitacion/"', "Autenticarse en Cl@ve",
                    'expectedType = "submit"', 'expectedTag = "input"',
                    'expectedElementId = "ctl00_ContentFormulario_controlAutenticacion_Sign"',
                    'expectedFormName = "aspnetForm"', 'expectedFormAction = SEPES_LOGIN_URL',
                    "requireNoOnClick = true"):
            self.assertIn(pin, wave)
        ine = wave.split("INE_PORTAL_ID ->", 1)[1].split("SEPES_PORTAL_ID ->", 1)[0]
        self.assertNotIn("expectedFormAction", ine)
        for forbidden in ("loadUrl(", ".submit(", ".target =", "setAttribute('target'", "SAMLRequest"):
            self.assertNotIn(forbidden, wave)
        boe = source.split("internal fun boeCertificateAnchorScript()", 1)[1].split("internal fun", 1)[0]
        for pin in ("document.location.href", "document.readyState", "anchors.length !== 1",
                    "images.length !== 1", "certificado_txt.png", "Identificación con certificado electrónico",
                    "getAttribute('onclick') !== null", "getAttribute('target')", "anchor.click()"):
            self.assertIn(pin, boe)
        cmt = source.split("internal fun sedipualbaServerCertificateOptionScript(", 1)[1]
        for pin in ("document.location.href", "frames.length !== 1", "options.length !== 1",
                    "images.length !== 1", "titles.length !== 1", "doc.location.origin !== location.origin",
                    "/segex/identificacion_opciones.aspx", "option.click()"):
            self.assertIn(pin, cmt)
        fixture = self.read(INSTRUMENTATION.with_name("RealE2eWaveFRecipeInstrumentedTest.kt"))
        self.assertIn("boeCertificateAnchorRequiresUniquePinnedImage", fixture)
        self.assertIn("cmtOptionRequiresSameOriginFrame", fixture)
        self.assertIn("shouldInterceptRequest", fixture)

    def test_tea_recipe_clicks_only_the_observed_top_level_certificate_button(self) -> None:
        source = self.read(INSTRUMENTATION)
        recipe = source.split("TEA_PORTAL_ID ->", 1)[1].split("CSN_PORTAL_ID ->", 1)[0]
        self.assertEqual(1, recipe.count("clickExactButton("))
        for pin in ('expectedCurrentUrl = TEA_SELECTOR_URL', 'expectedElementId = null',
                    'expectedLabel = "Certificado o DNI electrónico"', 'expectedAriaLabel = null',
                    'expectedOnClick = null', 'waitForExpectedUrl = true', 'expectedType = "button"',
                    'expectedTag = "button"', 'requireNoOnClick = true'):
            self.assertIn(pin, recipe)
        for forbidden in ('loadUrl(', '.submit(', 'window.open', 'data-link', ':has-text',
                          'autoriza-', 'clickClaveAfirmaProvider'):
            self.assertNotIn(forbidden, recipe)
        self.assertIn('const val TEA_SELECTOR_URL =', source)
        self.assertIn('SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0&aut=CPE', source)
        fixture = self.read(INSTRUMENTATION.with_name("RealE2eWaveFRecipeInstrumentedTest.kt"))
        self.assertIn('teaCertificateButtonUsesTheExistingExactHelper', fixture)
        self.assertIn('autoriza-C', fixture)
        self.assertIn('data-link=', fixture)
        self.assertIn("addEventListener('click'", fixture)

    def test_dgoj_recipe_uses_two_pinned_login_submits_then_the_existing_afirma_helper(self) -> None:
        source = self.read(INSTRUMENTATION)
        recipe = source.split('DGOJ_PORTAL_ID ->', 1)[1].split('TEA_PORTAL_ID ->', 1)[0]
        self.assertEqual([
            'clickDgojLoginSubmit(scenario, secondStep = false)',
            'clickDgojLoginSubmit(scenario, secondStep = true)',
            'clickClaveAfirmaProvider(scenario)',
        ], [line.strip() for line in recipe.splitlines() if 'click' in line])
        helper = source.split('private fun clickDgojLoginSubmit(', 1)[1].split('private fun ', 1)[0]
        for pin in ('webView.url', 'dgojLoginSourceMatches', 'dgojLoginSubmitScript(secondStep)',
                    'if (clicked) return', 'UI_TIMEOUT_MILLIS', 'REAL_E2E_RECIPE_TARGET_MISMATCH'):
            self.assertIn(pin, helper)
        script = source.split('internal fun dgojLoginSubmitScript(', 1)[1].split('"""', 2)[1]
        for pin in ('window.top !== window.self', 'document.readyState', 'document.location.href',
                    'forms.length !== 1', 'controls.length !== 1', "input.form !== form",
                    '!form.contains(input)', 'getClientRects()', 'sedeCsrf', 'csrf.length !== 1',
                    "getAttribute('target')", 'base[target]', 'formaction', 'formmethod', 'formtarget',
                    "getAttribute('onclick') !== null", 'input.click()', 'form.action'):
            self.assertIn(pin, script)
        self.assertNotRegex(script, r'location\.href\s*=(?!=)')
        for forbidden in ('loadUrl(', '.submit(', 'window.open',
                          'setAttribute(', 'reload(', 'decodeURI', 'new URL(', 'console.', 'JSON.stringify'):
            self.assertNotIn(forbidden, helper + script)
        fixture = self.read(INSTRUMENTATION.with_name('RealE2eWaveFRecipeInstrumentedTest.kt'))
        self.assertIn('dgojLoginSubmitsRequireExactFormsAndReadyCsrf', fixture)
        self.assertIn('dgojPloginSessionComparisonIsBounded', fixture)

    def test_dgoj_login_scripts_fail_closed_and_wait_for_csrf_without_rewriting_the_document(self) -> None:
        source = self.read(INSTRUMENTATION)
        script = textwrap.dedent(source.split('internal fun dgojLoginSubmitScript(', 1)[1].split('"""', 2)[1])
        start = 'https://sede.ordenacionjuego.gob.es/tramite/login/inicio.jjsp?iA=no&limpiarBusqueda=S'
        plogin = 'https://sede.ordenacionjuego.gob.es/tramite/login/plogin.jjsp'
        action = 'https://sede.ordenacionjuego.gob.es/tramite/clave/lanzarPeticionClave.jjsp'
        for name, value in (('DGOJ_START_URL', start), ('DGOJ_PLOGIN_URL', plogin), ('DGOJ_CLAVE_ACTION', action)):
            script = script.replace('${JSONObject.quote(' + name + ')}', json.dumps(value))
        scripts = [script.replace('$secondStep', value) for value in ('false', 'true')]
        harness = r'''
            const assert = require('node:assert/strict');
            const vm = require('node:vm');
            function fixture(o = {}) {
              let clicks = 0, neighborClicks = 0;
              const second = o.second ?? false;
              const formName = second ? 'lanzarPeticionClave' : 'plogin';
              const inputId = second ? 'lanzarPeticionClave__' : 'plogin_login_texto_iniciar_sesion';
              const element = (tagName, attrs, value = '') => ({tagName, attrs, value,
                get id() { return attrs.id; }, get disabled() { return !!attrs.disabled; },
                getAttribute: name => attrs[name] ?? null,
                hasAttribute: name => Object.hasOwn(attrs, name),
                matches: selector => {assert.equal(selector, ':disabled'); return !!attrs.disabled;},
                getClientRects: () => o.hidden ? [] : [{}]});
              const form = element('FORM', {id: formName, name: formName, method: 'post', ...o.form});
              const rawAction = o.action ?? (second ? ACTION : PLOGIN + ';DGOJ_ID=synthetic-1');
              form.attrs.action = rawAction;
              form.action = o.resolvedAction ?? new URL(rawAction, o.url ?? (second ? PLOGIN + ';DGOJ_ID=synthetic-1' : START)).href;
              const input = element(o.tag ?? 'INPUT', {id: inputId, type: 'submit',
                name: second ? ' ' : 'login.texto.iniciar.sesion', ...o.input},
                o.label ?? (second ? '' : 'Iniciar sesión'));
              input.form = o.wrongOwner ? {} : form;
              input.click = () => clicks++;
              const csrf = element(o.csrfTag ?? 'INPUT', {type: 'hidden', name: 'sedeCsrf', ...o.csrf},
                o.csrfValue ?? 'synthetic-csrf');
              csrf.form = o.csrfOwner ? {} : form;
              form.elements = Array.from({length: o.csrfCount ?? 1}, () => csrf);
              form.contains = child => child === input && !o.outside;
              const neighbor = element('FORM', {id: 'iniciar_sesion_form', name: 'iniciar_sesion_form'});
              const neighborInput = element('INPUT', {id: 'other-login', type: 'submit'}, 'Iniciar sesión');
              neighborInput.click = () => neighborClicks++;
              const bases = o.base === undefined ? [] : [element('BASE', {target: o.base})];
              const document = {readyState: o.ready ?? 'complete',
                location: {href: o.url ?? (second ? PLOGIN + ';DGOJ_ID=synthetic-1' : START)},
                querySelectorAll(selector) {
                  if (selector === 'form') return [...Array.from({length: o.forms ?? 1}, () => form), neighbor];
                  if (selector === '[id]') return [...Array.from({length: o.controls ?? 1}, () => input), neighborInput];
                  if (selector === 'base[target]') return bases;
                  throw new Error('Unexpected selector: ' + selector);
                }};
              const window = {}; window.self = window; window.top = o.subframe ? {} : window;
              const context = vm.createContext({document, window,
                getComputedStyle: () => ({display: 'inline-block', visibility: 'visible', ...o.style})});
              const snapshot = () => JSON.stringify([form.attrs, form.action, input.attrs, csrf.attrs,
                csrf.value, document.location.href]);
              return {input, csrf, form, document, run() {
                const before = snapshot();
                const result = vm.runInContext(SCRIPTS[Number(second)], context);
                assert.equal(snapshot(), before);
                assert.equal(neighborClicks, 0);
                return [result, clicks];
              }};
            }
            const run = o => fixture(o).run();
            const rejected = o => {const [code, clicks] = run(o); assert.notEqual(code, 1); assert.equal(clicks, 0);};
            for (const second of [false, true]) {
              assert.deepEqual(run({second}), [1, 1]);
              assert.deepEqual(run({second, form: {target: ''}, base: ''}), [1, 1]);
              assert.deepEqual(run({second, label: second ? ' \t ' : '  Iniciar\n sesión  '}), [1, 1]);
              assert.deepEqual(run({second, action: second ? ACTION.slice(ORIGIN.length) :
                PLOGIN.slice(ORIGIN.length) + ';DGOJ_ID=a'}), [1, 1]);
              assert.deepEqual(run({second, action: second ? '../clave/lanzarPeticionClave.jjsp' :
                'plogin.jjsp;DGOJ_ID=a'}), [1, 1]);
              rejected({second, action: second ? '../clave/other.jjsp' : 'other.jjsp;DGOJ_ID=a'});
              rejected({second, action: second ? 'lanzarPeticionClave.jjsp' : '../plogin.jjsp;DGOJ_ID=a'});
              for (const options of [{forms: 0}, {forms: 2}, {form: {id: 'wrong'}}, {form: {name: 'wrong'}},
                {controls: 0}, {controls: 2}, {input: {id: 'wrong'}}, {wrongOwner: true}, {outside: true},
                {tag: 'BUTTON'}, {input: {type: 'button'}}, {input: {name: 'wrong'}}, {label: 'Cl@ve'},
                {hidden: true}, {style: {visibility: 'hidden'}}, {style: {display: 'none'}},
                {input: {disabled: true}}, {input: {onclick: ''}}, {input: {onclick: 'login();'}},
                {form: {method: 'get'}}, {action: 'https://foreign.example/login'}, {base: '_blank'},
                ...['_self', '_top', '_parent', '_blank', ' '].map(target => ({form: {target}})),
                ...['formaction', 'formmethod', 'formtarget'].map(attr => ({input: {[attr]: ''}})),
                {csrfCount: 0}, {csrfValue: ''}, {csrf: {disabled: true}}, {csrf: {type: 'text'}},
                {csrfTag: 'SELECT'}, {csrfCount: 2}, {csrfOwner: true}, {subframe: true}, {ready: 'loading'}])
                rejected({second, ...options});
              const f = fixture({second, csrfCount: 0});
              assert.deepEqual(f.run(), [0, 0]);
              f.form.elements.push(f.csrf); f.csrf.value = '';
              assert.deepEqual(f.run(), [0, 0]);
              f.csrf.value = 'synthetic-ready';
              assert.deepEqual(f.run(), [1, 1]);
            }
            for (const url of [START.replace('iA=no', 'iA=si'), START.replace('limpiarBusqueda=S', 'limpiarBusqueda=N'),
              START.replace('iA=no&', ''), START.replace('&limpiarBusqueda=S', ''),
              START + '&iA=no', START + '&limpiarBusqueda=S', START + '&extra=1', START + '#x',
              START + ';DGOJ_ID=a', START.replace('iA=no&limpiarBusqueda=S', 'limpiarBusqueda=S&iA=no')])
              rejected({url});
            const invalid = [PLOGIN, PLOGIN + ';DGOJ_ID=', PLOGIN + ';DGOJ_ID=' + 'a'.repeat(257),
              ...['http://', 'https://user@'].map(s => s + PLOGIN.slice('https://'.length)),
              ...[':443', ':444', '.evil.example', '.'].map(s => PLOGIN.replace(ORIGIN, ORIGIN + s)),
              PLOGIN.replace('plogin.jjsp', 'other.jjsp'), PLOGIN.replace('/login/', '/login/../login/'),
              PLOGIN + '/', PLOGIN + '/other', PLOGIN.replace('/login/', '//login/'),
              ...['a;DGOJ_ID=b', 'a;x=y', 'a?x=1', 'a#x', 'a/b', 'a\\b', 'a b', 'a\n', 'a\r',
                'a\t', 'a\u0000', 'é', '%41', 'a%2Fb', 'a%3Bb'].map(s => PLOGIN + ';DGOJ_ID=' + s),
              PLOGIN + ';jsessionid=a', PLOGIN + ';dgoj_id=a'];
            for (const url of invalid) {
              rejected({second: true, url}); rejected({action: url});
            }
            for (const value of ['a', 'a'.repeat(256), 'AZaz09._~-']) {
              assert.deepEqual(run({second: true, url: PLOGIN + ';DGOJ_ID=' + value}), [1, 1]);
              assert.deepEqual(run({action: PLOGIN + ';DGOJ_ID=' + value}), [1, 1]);
            }
            rejected({action: PLOGIN.replace(ORIGIN, ORIGIN + ':443') + ';DGOJ_ID=a',
              resolvedAction: PLOGIN + ';DGOJ_ID=a'});
            rejected({second: true, action: ACTION + ';DGOJ_ID=a'});
            rejected({second: true, url: ACTION + ';DGOJ_ID=a'});
            rejected({second: true, url: 'https://pasarela.clave.gob.es/Proxy2/ServiceRedirect;DGOJ_ID=a'});
        '''
        result = subprocess.run(
            ['node', '-e', f'const SCRIPTS={json.dumps(scripts)}, START={json.dumps(start)}, '
             f'PLOGIN={json.dumps(plogin)}, ACTION={json.dumps(action)}, '
             'ORIGIN="https://sede.ordenacionjuego.gob.es";\n' + harness],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_tea_exact_button_script_rejects_changed_controls_without_a_network_request(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source.split(' fun clickExactButton(', 1)[1]
        script = textwrap.dedent(helper.split('webView.evaluateJavascript(', 1)[1].split('"""', 2)[1])
        selector = (
            'https://sede.tea.hacienda.gob.es/static_files/common/html/selector_acceso/'
            'SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FTEAC-TRAM%2FSedeTRAM%3Ftram%3D0&aut=CPE'
        )
        values = {
            'matchUrlWithoutQuery': False, 'quotedExpectedUrl': selector,
            'quotedExpectedElementId': None, 'quotedExpectedLabel': 'Certificado o DNI electrónico',
            'quotedExpectedAriaLabel': None, 'quotedExpectedOnClick': None,
            'quotedWaitForBadajozSignHook': False, 'quotedExpectedTag': 'button',
            'quotedExpectedType': 'button', 'quotedExpectedSrc': None, 'requireNoOnClick': True,
            'quotedFormName': None, 'quotedFormAction': None,
            'matchCanariasLoginAction': False, 'quotedExpectedName': None, 'quotedHandlerName': None,
            'quotedParentAnchorClass': None, 'requireVisible': False, 'waitForAemetSignHook': False,
        }
        script = re.sub(r'\$(\w+)', lambda match: json.dumps(values[match[1]]), script)
        harness = r'''
            const assert = require('node:assert/strict');
            const vm = require('node:vm');
            function run(options = {}) {
              let clicks = 0;
              const element = {
                innerText: options.label ?? 'Certificado o DNI electrónico',
                getAttribute: name => ({type: 'button', ...options.attrs}[name] ?? null),
                click() { clicks++; }
              };
              const document = {querySelectorAll(tag) {
                assert.equal(tag, 'button');
                return Array.from({length: options.count ?? 1}, () => element);
              }};
              return [vm.runInNewContext(SCRIPT, {
                URL, document, location: {href: options.url || SOURCE}, window: {}
              }), clicks];
            }
            assert.deepEqual(run(), [1, 1]);
            assert.deepEqual(run({label: '  Certificado\n o DNI\t electrónico  '}), [1, 1]);
            assert.deepEqual(run({count: 2}), [2, 0]);
            for (const options of [{count: 0}, {attrs: {type: 'submit'}}, {attrs: {type: null}},
                {label: 'Cl@ve'}, {attrs: {onclick: ''}}, {attrs: {onclick: 'login();'}},
                {url: SOURCE.replace('rep=S', 'rep=N')}, {url: SOURCE.replace('aut=CPE', 'aut=PE')},
                {url: SOURCE.replace('tram%3D0', 'tram%3D2')}, {url: SOURCE + '&extra=1'}])
              assert.deepEqual(run(options), [0, 0]);
        '''
        result = subprocess.run(
            ['node', '-e', f'const SCRIPT={json.dumps(script)}, SOURCE={json.dumps(selector)};\n' + harness],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_wave_f_dom_scripts_reject_ambiguity_and_wait_for_the_reviewed_frame(self) -> None:
        source = self.read(INSTRUMENTATION)
        boe = textwrap.dedent(source.split('internal fun boeCertificateAnchorScript()', 1)[1]
                              .split('"""', 2)[1]).strip()
        cmt = textwrap.dedent(source.split('internal fun sedipualbaServerCertificateOptionScript(', 1)[1]
                              .split('"""', 2)[1]).strip()
        login = ('https://sede.cmt.gob.es/carpetaciudadana/login.aspx?returnUrl='
                 'https%3a%2f%2fsede.cmt.gob.es%2fcarpetaciudadana%2fcarpeta_ciudadana.aspx')
        label = 'Identificarse con certificado digital a través de nuestro servidor'
        cmt = cmt.replace('${JSONObject.quote(expectedLoginUrl)}', json.dumps(login))
        cmt = cmt.replace('${JSONObject.quote(optionLabel)}', json.dumps(label))
        harness = r'''
            const assert = require('node:assert/strict');
            const vm = require('node:vm');
            const copies = (n, value) => Array.from({length: n}, () => value);
            function boe(options = {}) {
              let clicks = 0;
              const attrs = {name: 'certificado', alt: 'Identificación con certificado electrónico',
                src: '/imagenes/certificado_txt.png', ...options.image};
              const image = {getAttribute: name => attrs[name] ?? null};
              const anchorAttrs = {
                href: 'https://extranet.boe.es/id/check-cert.php?referer=https://extranet.boe.es/anuncios/',
                ...options.anchor};
              const anchor = {textContent: options.text || '', getAttribute: name => anchorAttrs[name] ?? null,
                querySelectorAll(selector) { assert.equal(selector, 'img'); return copies(options.images ?? 1, image); },
                click() { clicks++; }};
              const document = {readyState: 'complete', location: {href: options.source || 'https://extranet.boe.es/anuncios/'},
                querySelectorAll(selector) { assert.equal(selector, 'a'); return copies(options.anchors ?? 1, anchor); }};
              return [vm.runInNewContext(BOE, {document}), clicks];
            }
            assert.deepEqual(boe(), [1, 1]);
            for (const options of [{anchors: 2}, {images: 2}, {image: {alt: 'wrong'}},
                {image: {src: '/wrong.png'}}, {image: {name: 'wrong'}}, {text: 'Cl@ve'},
                {anchor: {onclick: ''}}, {anchor: {target: '_blank'}}])
              assert.deepEqual(boe(options), [2, 0]);
            assert.deepEqual(boe({source: 'https://extranet.boe.es/other/'}), [0, 0]);
            function cmt(options = {}) {
              let clicks = 0;
              const image = {getAttribute: name => name === 'alt' ? (options.alt || LABEL) : null};
              const title = {textContent: options.title || LABEL};
              const option = {tagName: 'TBODY', getAttribute: name => name === 'target' ? options.target ?? null : null,
                querySelectorAll(selector) {
                  if (selector === 'img') return copies(options.images ?? 1, image);
                  assert.equal(selector, '.titulo-opcion span'); return copies(options.titles ?? 1, title);
                }, click() { clicks++; }};
              const doc = {readyState: 'complete',
                location: {origin: options.origin || 'https://sede.cmt.gob.es',
                  pathname: options.path || '/segex/identificacion_opciones.aspx'},
                querySelectorAll(selector) { assert.equal(selector, '[id="optSsl"]');
                  return copies(options.options ?? 1, option); }};
              const frame = {tagName: 'IFRAME', contentDocument: doc};
              const document = {readyState: 'complete', location: {href: options.source || LOGIN},
                querySelectorAll(selector) { assert.equal(selector, '[id="ventanaModal"]');
                  return copies(options.frames ?? 1, frame); }};
              const context = {document, location: {origin: 'https://sede.cmt.gob.es'}};
              const result = vm.runInNewContext(CMT, context);
              if (options.transition) {
                assert.deepEqual([result, clicks], [0, 0]);
                doc.location.pathname = '/segex/identificacion_opciones.aspx';
                return [vm.runInNewContext(CMT, context), clicks];
              }
              return [result, clicks];
            }
            assert.deepEqual(cmt(), [1, 1]);
            for (const options of [{frames: 2}, {options: 2}, {images: 2}, {titles: 2},
                {alt: 'wrong'}, {title: 'Firmar electrónicamente'}, {target: '_blank'}])
              assert.deepEqual(cmt(options), [2, 0]);
            for (const options of [{origin: 'https://foreign.example'}, {path: '/other.aspx'},
                {source: LOGIN.split('?')[0]}]) assert.deepEqual(cmt(options), [0, 0]);
            assert.deepEqual(cmt({path: '/carpetaciudadana/carpeta_ciudadana_cliente_autenticacion.aspx',
              transition: true}), [1, 1]);
        '''
        program = (f'const BOE={json.dumps(boe)}, CMT={json.dumps(cmt)}, '
                   f'LOGIN={json.dumps(login)}, LABEL={json.dumps(label)};\n' + harness)
        result = subprocess.run(['node', '-e', program], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_real_e2e_is_manual_main_only_and_read_only(self) -> None:
        source = self.read(WORKFLOW)
        self.assertIn("workflow_dispatch:", source)
        self.assertNotIn("pull_request:", source)
        self.assertNotIn("pull_request_target:", source)
        self.assertNotIn("\n  push:\n", source)
        self.assertIn("permissions:\n  contents: read", source)
        self.assertIn("if: github.ref == 'refs/heads/main'", source)
        self.assertIn("environment: real-e2e", source)
        self.assertIn("group: real-e2e-${{ contains(inputs.portal_id, ',') && 'batch' || inputs.portal_id || 'catalog' }}", source)
        self.assertIn("cancel-in-progress: false", source)
        self.assertIn("ref: ${{ github.sha }}", source)
        self.assertIn("persist-credentials: false", source)
        self.assertIn('test "$GITHUB_REF" = refs/heads/main', source)
        self.assertIn('test "$(git rev-parse HEAD)" = "$GITHUB_SHA"', source)

    def test_real_e2e_actions_are_sha_pinned(self) -> None:
        source = self.read(WORKFLOW)
        for line in source.splitlines():
            stripped = line.strip()
            if not stripped.startswith("uses:"):
                continue
            reference = stripped.split("uses:", 1)[1].strip().split()[0]
            self.assertRegex(reference, r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")
        self.assertIn(
            "ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
            source,
        )
        self.assertIn(
            "actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9",
            source,
        )

    def test_real_e2e_secrets_are_scoped_to_the_emulator_step(self) -> None:
        source = self.read(WORKFLOW)
        self.assertEqual(1, source.count("secrets.REAL_E2E_CERT_P12_B64"))
        self.assertEqual(1, source.count("secrets.REAL_E2E_CERT_PASSWORD"))
        build = source.index("Build QA test artifacts before credentials are exposed")
        credential = source.index("REAL_E2E_CERT_P12_B64:")
        self.assertLess(build, credential)
        self.assertIn("retention-days: 1", source)
        self.assertIn("build/reports/real-e2e/summary.json", source)
        self.assertIn("build/reports/real-e2e/summary.md", source)
        self.assertIn("build/reports/real-e2e/navigation/", source)
        self.assertNotIn("identity.p12", source)
        self.assertNotIn("password\n", source.lower())

    def test_real_e2e_webview_reset_starts_callback_apis_on_main_thread(self) -> None:
        source = self.read(ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt")
        start = source.index("private fun resetBrowserState()")
        end = source.index("private fun shell(", start)
        body = source[start:end]
        self.assertGreaterEqual(body.count("instrumentation.runOnMainSync"), 2)
        self.assertIn("CookieManager.getInstance().removeAllCookies", body)
        self.assertIn("WebView.clearClientCertPreferences", body)
        self.assertIn("cookies.await(5, TimeUnit.SECONDS)", body)
        self.assertIn("clientCert.await(5, TimeUnit.SECONDS)", body)

    def test_instrumented_probe_skips_without_opt_in_but_persists_enabled_failures(self) -> None:
        source = self.read(ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt")
        opt_in_skip = source.index('assumeTrue("REAL_E2E requires explicit opt-in", explicitlyEnabled)')
        portal_check = source.index('require(PORTAL_ID_PATTERN.matches(portalId)) { "REAL_E2E_INVALID_PORTAL_ID" }')
        result_init = source.index("val result = ProbeResult(")
        fixture_check = source.index('require(certificateFile.isFile) { "REAL_E2E_CERTIFICATE_MISSING" }')
        final_write = source.index("writeResult(result)")
        self.assertLess(opt_in_skip, portal_check)
        self.assertLess(portal_check, result_init)
        self.assertLess(result_init, fixture_check)
        self.assertLess(fixture_check, final_write)
        self.assertEqual(1, source.count("assumeTrue("))
        self.assertIn('"CERTIFICATE_MISSING"', source)
        self.assertIn('"PASSWORD_MISSING"', source)
        self.assertIn("safeInfrastructureCode(throwable, probeStage)", source)

    def test_runner_streams_credentials_without_remote_shell_redirection(self) -> None:
        runner = self.read(RUNNER)
        self.assertIn('adb_bounded shell run-as "$PACKAGE_NAME" mkdir -p "$FIXTURE_DIR"', runner)
        self.assertIn('adb_bounded shell -T run-as "$PACKAGE_NAME" dd of="$CERTIFICATE_PATH" bs=4096', runner)
        self.assertIn('adb_bounded shell -T run-as "$PACKAGE_NAME" dd of="$PASSWORD_PATH" bs=4096', runner)
        self.assertIn('chmod 600 "$CERTIFICATE_PATH" "$PASSWORD_PATH"', runner)
        self.assertIn('progress STAGE_CERT_WRITE_START', runner)
        self.assertIn('progress STAGE_PASSWORD_WRITE_START', runner)
        self.assertIn('progress STAGE_STAT_DONE', runner)
        self.assertNotIn('run-as "$PACKAGE_NAME" sh -c', runner)
        self.assertNotIn("cat > '$CERTIFICATE_PATH'", runner)
        self.assertNotIn("cat > '$PASSWORD_PATH'", runner)

    def test_runner_bounds_adb_operations_outside_instrumentation(self) -> None:
        runner = self.read(RUNNER)
        self.assertIn("readonly ADB_TIMEOUT_SECONDS=30", runner)
        self.assertIn("readonly ADB_INSTALL_TIMEOUT_SECONDS=120", runner)
        self.assertIn('timeout --signal=TERM --kill-after=5s "${ADB_TIMEOUT_SECONDS}s" adb "$@"', runner)
        self.assertIn('timeout --signal=TERM --kill-after=5s "${ADB_INSTALL_TIMEOUT_SECONDS}s" adb install --no-streaming -r "$1"', runner)
        self.assertIn('adb_bounded exec-out run-as "$PACKAGE_NAME" cat "$RESULT_PATH"', runner)
        self.assertIn('adb_bounded exec-out run-as "$PACKAGE_NAME" cat files/qa-navigation.log', runner)
        self.assertIn('adb_bounded shell am force-stop "$PACKAGE_NAME"', runner)
        self.assertIn('timeout --signal=TERM --kill-after=10s "${portal_timeout}s" \\', runner)
        self.assertIn('portal_timeout="$PORTAL_TIMEOUT_SECONDS"', runner)
        self.assertIn("readonly JCCM_PORTAL_TIMEOUT_SECONDS=330", runner)
        self.assertIn('      adb shell am instrument -w -r \\', runner)
        self.assertNotIn('\n  adb install -r ', runner)
        self.assertNotIn('\n    adb exec-out ', runner)
        self.assertNotIn('\n    adb shell run-as ', runner)

    def test_progress_and_partial_results_are_validated_before_upload(self) -> None:
        workflow = self.read(WORKFLOW)
        runner = self.read(RUNNER)
        helper = self.read(REPORT_HELPER)
        self.assertIn('PROGRESS_PATH="$REPORT_DIR/progress.tsv"', runner)
        self.assertIn('progress INSTALL_QA_START', runner)
        self.assertIn('progress INSTALL_QA_DONE', runner)
        self.assertIn('progress INSTALL_TEST_START', runner)
        self.assertIn('progress STAGE_FIXTURE_START', runner)
        self.assertIn('progress STAGE_MKDIR_DONE', runner)
        self.assertIn('progress STAGE_CERT_WRITE_START', runner)
        self.assertIn('progress STAGE_CERT_WRITE_DONE', runner)
        self.assertIn('progress STAGE_PASSWORD_WRITE_DONE', runner)
        self.assertIn('progress STAGE_STAT_DONE', runner)
        self.assertIn('::notice title=REAL_E2E progress::', runner)
        self.assertIn('progress PORTAL_START', runner)
        self.assertIn('progress INSTRUMENT_START', runner)
        self.assertIn('progress RESULT_READ_START', runner)
        self.assertIn('describe-result --result "$result_file" --portal "$portal_id"', runner)
        self.assertIn('progress NAV_READ_START', runner)
        self.assertIn('progress PORTAL_DONE', runner)
        self.assertIn('validate-progress', workflow)
        self.assertIn('validate-partial-results', workflow)
        self.assertIn('build/reports/real-e2e/progress.tsv', workflow)
        self.assertIn('build/reports/real-e2e/results/', workflow)
        self.assertIn("PROGRESS_STAGES", helper)
        self.assertIn("validate_progress", helper)
        self.assertIn("validate_partial_results", helper)

    def test_runner_uses_private_fixture_and_never_clears_app_data(self) -> None:
        source = self.read(RUNNER)
        self.assertTrue(source.startswith("#!/usr/bin/env bash\nset -euo pipefail\n"))
        self.assertIn('FIXTURE_DIR="files/real-e2e"', source)
        self.assertIn('run-as "$PACKAGE_NAME"', source)
        self.assertIn("base64 --decode", source)
        self.assertIn("unset REAL_E2E_CERT_P12_B64 REAL_E2E_CERT_PASSWORD", source)
        self.assertIn('rm -rf "$FIXTURE_DIR"', source)
        self.assertNotIn("pm clear", source)
        self.assertNotIn("set -x", source)
        self.assertNotIn("echo $REAL_E2E", source)
        self.assertNotIn("printenv", source)

    def test_runner_selects_catalog_and_supports_one_portal_retry(self) -> None:
        workflow = self.read(WORKFLOW)
        runner = self.read(RUNNER)
        self.assertIn("portal_id:", workflow)
        self.assertIn("deep_auth_signing:", workflow)
        self.assertIn('PORTAL_ID_FILTER: ${{ inputs.portal_id }}', workflow)
        self.assertIn('REAL_E2E_DEEP_AUTH_SIGNING: ${{ inputs.deep_auth_signing }}', workflow)
        self.assertIn('"$REPORT_HELPER" select', runner)
        self.assertIn('--portal "${PORTAL_ID_FILTER:-}"', runner)
        self.assertIn('--shard-index "$shard_index"', runner)
        self.assertIn('--shard-total "$shard_total"', runner)
        self.assertIn('for portal_id in "${portal_ids[@]}"', runner)
        self.assertIn("-e portalId", runner)
        self.assertIn("-e realE2e true", runner)
        self.assertIn("-e realE2eDeep", runner)

    def test_targeted_portal_uses_exactly_one_real_shard(self) -> None:
        workflow = self.read(WORKFLOW)
        self.assertIn("include: ${{ fromJSON(inputs.portal_id != ''", workflow)
        self.assertIn('{"shard_number":1,"shard_index":0,"shard_total":1}', workflow)
        self.assertIn('REAL_E2E_SHARD_TOTAL: ${{ matrix.shard_total }}', workflow)
        self.assertIn('shard ${{ matrix.shard_number }}/${{ matrix.shard_total }}', workflow)
        targeted = self.helper(
            "select",
            "--catalog",
            str(CATALOG),
            "--portal",
            "junta-andalucia-carne-joven",
            "--shard-index",
            "0",
            "--shard-total",
            "1",
        )
        self.assertEqual(0, targeted.returncode, targeted.stderr)
        self.assertEqual(["junta-andalucia-carne-joven"], targeted.stdout.splitlines())

    def test_workflow_runs_six_parallel_non_overlapping_shards_for_full_catalog(self) -> None:
        workflow = self.read(WORKFLOW)
        self.assertIn("strategy:\n      fail-fast: false\n      matrix:\n", workflow)
        self.assertEqual(6, workflow.count('"shard_total":6'))
        self.assertIn('REAL_E2E_SHARD_INDEX: ${{ matrix.shard_index }}', workflow)
        self.assertIn('REAL_E2E_SHARD_TOTAL: ${{ matrix.shard_total }}', workflow)
        self.assertIn('shard-${{ matrix.shard_number }}', workflow)

        shards = []
        for index in range(6):
            completed = self.helper(
                "select",
                "--catalog",
                str(CATALOG),
                "--shard-index",
                str(index),
                "--shard-total",
                "6",
            )
            self.assertEqual(0, completed.returncode, completed.stderr)
            shard = completed.stdout.splitlines()
            self.assertIn(len(shard), {30, 31})
            shards.extend(shard)
        self.assertEqual(183, len(shards))
        self.assertEqual(183, len(set(shards)))

    def test_carne_joven_recipe_is_exact_and_same_origin_only(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('CARNE_JOVEN_PORTAL_ID = "junta-andalucia-carne-joven"', source)
        self.assertIn(
            '"https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"',
            source,
        )
        self.assertIn(
            '"/carneJoven/servlet/CallAuthenticationServlet"',
            source,
        )
        self.assertIn('when (portalId)', source)
        self.assertIn('currentUrl != null && currentUrl != expectedCurrentUrl', source)
        self.assertIn('CARNE_JOVEN_AUTH_LINK_ID = "bot-obtener"', source)
        self.assertIn('const element = document.getElementById($quotedId)', source)
        self.assertIn("element.getAttribute('href') !== $quotedExpectedHref", source)
        self.assertIn('element.click()', source)
        self.assertIn('"1" -> clicked = true', source)
        self.assertIn('"2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"', source)
        self.assertIn('val quotedExpectedHref = JSONObject.quote(expectedHref)', source)
        self.assertIn('REAL_E2E_RECIPE_TARGET_MISMATCH', source)
        self.assertIn('SystemClock.sleep(RECIPE_POLL_MILLIS)', source)
        self.assertIn('REAL_E2E_RECIPE_TARGET_TIMEOUT', source)
        self.assertIn('RECIPE_TARGET_TIMEOUT', source)
        self.assertNotIn('window.location.assign(', source)
        self.assertNotIn('targetUrl = portalId', source)

    def test_ovorion_auth_recipe_clicks_only_the_exact_login_button(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('OVORION_PORTAL_ID = "junta-andalucia-ovorion"', source)
        self.assertIn(
            '"https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"',
            source,
        )
        self.assertIn('OVORION_AUTH_BUTTON_ID = "btnacceso"', source)
        self.assertIn('OVORION_AUTH_BUTTON_VALUE = "Acceder"', source)
        self.assertIn('OVORION_AUTH_BUTTON_ONCLICK = "autenticar();"', source)
        self.assertIn("element.getAttribute('type') !== 'button'", source)
        self.assertIn('element.value !== $quotedExpectedValue', source)
        self.assertIn("element.getAttribute('onclick') !== $quotedExpectedOnClick", source)
        self.assertIn('OVORION_PORTAL_ID -> clickExactAuthButton(', source)
        self.assertIn('element.click()', source)

    def test_ofvirtual_auth_recipe_clicks_only_the_exact_login_button(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('OFVIRTUAL_PORTAL_ID = "junta-andalucia-ofvirtual"', source)
        self.assertIn(
            '"https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"',
            source,
        )
        self.assertIn('OFVIRTUAL_AUTH_BUTTON_ID = "btnacceso"', source)
        self.assertIn('OFVIRTUAL_AUTH_BUTTON_VALUE = "Acceder"', source)
        self.assertIn('OFVIRTUAL_AUTH_BUTTON_ONCLICK = "autenticar();"', source)
        self.assertIn('OFVIRTUAL_PORTAL_ID -> clickExactAuthButton(', source)

    def test_badajoz_and_lleida_recipes_use_reviewed_certificate_controls(self) -> None:
        source = self.read(INSTRUMENTATION)
        for expected in (
            'BADAJOZ_PORTAL_ID = "diputacion-badajoz-portal"',
            'BADAJOZ_LOGIN_PAGE_URL =',
            'BADAJOZ_LOGIN_LINK_ID = "login"',
            'BADAJOZ_LOGIN_LINK_HREF = "javascript: abrirLogin(\'\');"',
            'BADAJOZ_CERT_BUTTON_ID = "firmar"',
            'BADAJOZ_CERT_BUTTON_LABEL = "Certificado digital"',
            'BADAJOZ_CERT_BUTTON_ONCLICK = "pulsarFirmarIdentificate();"',
            'LLEIDA_PORTAL_ID = "diputacion-lleida-sede"',
            'LLEIDA_LOGIN_PAGE_URL =',
            'LLEIDA_LOGIN_LINK_ID = "login"',
            'LLEIDA_LOGIN_LINK_HREF = "javascript: abrirLogin(\'\');"',
            'LLEIDA_CERT_BUTTON_ID = "btnValid"',
            'LLEIDA_CERT_BUTTON_ARIA_LABEL = "VALid"',
            'LLEIDA_CERT_BUTTON_ONCLICK = "javascript: pulsarLoginValid();"',
            'BADAJOZ_PORTAL_ID -> {',
            'waitForBadajozEntryPageReady(scenario)',
            'waitForBadajozSignHook = true',
            'LLEIDA_PORTAL_ID -> {',
            'waitForExpectedUrl = true',
            'LLEIDA_LOGIN_LINK_HREF',
            'internal fun clickExactButton(',
            'recipeUrlMatches(',
            "element.getAttribute('aria-label') !== expectedAriaLabel",
        ):
            self.assertIn(expected, source)

    def test_aeat_recipe_waits_for_the_reviewed_certificate_page_redirect(self) -> None:
        source = self.read(INSTRUMENTATION)
        recipe = source[source.index('AEAT_PORTAL_ID -> {'):]
        recipe = recipe[:recipe.index('LLEIDA_PORTAL_ID -> {')]
        self.assertIn('AEAT_CERTIFICATE_PAGE_URL', recipe)
        self.assertIn('AEAT_CERTIFICATE_BUTTON_LABEL', recipe)
        self.assertIn('expectedOnClick = null', recipe)
        self.assertIn('waitForExpectedUrl = true', recipe)

    def test_auth_recipe_surfaces_terminal_navigation_failure_before_timeout(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source[source.index('private fun clickExactAuthButton('):]
        helper = helper[:helper.index('private fun observePortal(')]
        self.assertIn('const val RECIPE_TERMINAL_GRACE_MILLIS = 5_000L', source)
        self.assertIn('hasObservedTerminalNavigationFailure()', helper)
        for event in (
            'event=NETWORK_ERROR',
            'event=BLANK_DOCUMENT',
            'event=SSL_ERROR_CANCELLED',
            'event=NAVIGATION_BLOCKED',
        ):
            self.assertIn(event, helper)
        self.assertIn('RECIPE_TERMINAL_GRACE_MILLIS', helper)

    def test_sevilla_recipe_clicks_only_the_exact_certificate_anchor(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('SEVILLA_PORTAL_ID = "sevilla-sede"', source)
        self.assertIn(
            '"https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"',
            source,
        )
        self.assertIn('SEVILLA_AUTH_CONTAINER_ID = "divBotonCertificado"', source)
        self.assertIn('SEVILLA_AUTH_LABEL = "Acceder"', source)
        self.assertIn('SEVILLA_AUTH_HREF = "#"', source)
        self.assertIn('SEVILLA_AUTH_ONCLICK = "doSign();"', source)
        self.assertIn('SEVILLA_PORTAL_ID -> clickExactContainedAnchor(', source)
        self.assertIn('element.innerText', source)
        self.assertIn('element.getAttribute(\'href\') !== $quotedExpectedHref', source)
        self.assertIn('element.getAttribute(\'onclick\') !== $quotedExpectedOnClick', source)
        self.assertIn('elements.length !== 1', source)
        self.assertIn("event => event.preventDefault()", source)
        self.assertIn("element.addEventListener('click', preventDefault, { once: true })", source)
        self.assertIn('element.click()', source)

    def test_diputacion_sevilla_recipe_reaches_the_reviewed_certificate_login(self) -> None:
        source = self.read(INSTRUMENTATION)
        block = source.split('DIPUTACION_SEVILLA_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertTrue(block.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
        for expected in (
            'DIPUTACION_SEVILLA_PORTAL_ID = "diputacion-sevilla-sede"',
            'DIPUTACION_SEVILLA_INDEX_URL =',
            'DIPUTACION_SEVILLA_AUTH_URL =',
            'DIPUTACION_SEVILLA_AUTH_LABEL = "Identificarse"',
            'DIPUTACION_SEVILLA_AUTH_HREF =',
            'DIPUTACION_SEVILLA_AUTH_BUTTON_LABEL = "ACCEDER"',
            'DIPUTACION_SEVILLA_AUTH_BUTTON_ONCLICK = "loginClave();"',
            'DIPUTACION_SEVILLA_PORTAL_ID -> {',
            'expectedLabel = DIPUTACION_SEVILLA_AUTH_LABEL',
            'expectedHref = DIPUTACION_SEVILLA_AUTH_HREF',
            'expectedOnClick = DIPUTACION_SEVILLA_AUTH_BUTTON_ONCLICK',
            'waitForExpectedUrl = true',
            'clickClaveAfirmaProvider(scenario)',
        ):
            self.assertIn(expected, source)

    def test_own_tls_recipes_only_click_reviewed_login_controls(self) -> None:
        source = self.read(INSTRUMENTATION)
        recipe = source.split('private fun applyPortalRecipe(', 1)[1].split(
            'private fun clickAragonIframeCertificateLogin(', 1
        )[0]
        for constant, portal in (
            ('SANIDAD', 'age-ministerio-de-sanidad'),
            ('ARAGON_TRAMITES', 'aragon-tramites-sede'),
            ('MADRID_CUENTA_DIGITAL', 'comunidad-madrid-cuenta-digital-carne-joven'),
            ('HUELVA', 'diputacion-huelva-portal'),
            ('JAEN', 'diputacion-jaen-sede'),
            ('VALLADOLID', 'diputacion-valladolid-sede'),
            ('GVA', 'gva-sede'),
            ('MALLORCA_INSTITUCIONAL', 'mallorca-portal-institucional'),
            ('SEGURIDAD_SOCIAL', 'seguridad-social-sede'),
            ('IMPORTASS', 'tgss-importass'),
        ):
            with self.subTest(portal=portal):
                self.assertTrue(f'{constant}_PORTAL_ID = "{portal}"' in source, portal)
                self.assertTrue(f'{constant}_PORTAL_ID' in recipe, portal)
        for forbidden in ('loadUrl(', '.submit(', 'evaluateJavascript('):
            self.assertTrue(forbidden not in recipe, forbidden)
        self.assertIn('expectedOnClick = "login();"', recipe)
        self.assertIn('expectedElementId = "IPCEIdP"', recipe)
        self.assertIn('expectedAriaLabel = "Acceder a DNIe o certificado"', recipe)
        self.assertIn('expectedHref = "javascript:;"', recipe)

    def test_aragon_iframe_login_rejects_ambiguous_or_foreign_controls(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source.split('private fun clickAragonIframeCertificateLogin(', 1)[1].split(
            'private fun clickMadridCuentaDigitalCertificateLogin(', 1
        )[0]
        script = textwrap.dedent(helper.split('"""', 2)[1]).strip()
        entry = 'https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion'
        frame = (
            'https://aplicaciones.aragon.es/mfe_core/rest/identification/TTO/'
            'aHR0cHM6Ly9hcGxpY2FjaW9uZXMuYXJhZ29uLmVzL3RyYW1pdGFyL3NvbGljaXR1ZC1nZW5lcmFs'
            'L2lkZW50aWZpY2FjaW9uL2lkZW50aWZpY2Fkbw=='
        )
        script = script.replace('$quotedEntry', json.dumps(entry)).replace('$quotedFrameUrl', json.dumps(frame))
        harness = r'''
            const assert = require('node:assert/strict');
            const vm = require('node:vm');
            function run(options = {}) {
              const clicks = [];
              const radio = {checked: false, disabled: false, click() {this.checked = true; clicks.push('radio');}};
              const button = {innerText: 'Acceder', disabled: false,
                getAttribute: name => name === 'onclick' ? 'identifyByMethod(applicationId, urlRedirect)' : null,
                click() {clicks.push('login');}};
              const origin = 'https://aplicaciones.aragon.es';
              const doc = {location: {href: options.frame || FRAME, origin},
                querySelectorAll(selector) {
                  if (selector === 'input#identificacion-2[name="identificacion"][type="radio"][value="certificado"]')
                    return [radio];
                  if (selector === 'label[for="identificacion-2"]')
                    return [{textContent: options.label || 'Con certificado electrónico'}];
                  if (selector === 'button') return options.duplicate ? [button, button] : [button];
                  throw Error('Unexpected selector');
                }};
              const document = {querySelectorAll(selector) {
                assert.equal(selector, 'iframe#mfe_iframe');
                return [{contentDocument: doc}];
              }};
              const result = vm.runInNewContext(SCRIPT, {document, location: {href: options.entry || ENTRY, origin}});
              return {result, clicks};
            }
            assert.deepEqual(run(), {result: 1, clicks: ['radio', 'login']});
            assert.deepEqual(run({duplicate: true}), {result: 2, clicks: []});
            assert.deepEqual(run({label: 'Enviar solicitud'}), {result: 2, clicks: []});
            assert.deepEqual(run({frame: 'https://foreign.example/'}), {result: 2, clicks: []});
            assert.deepEqual(run({entry: 'https://foreign.example/'}), {result: 0, clicks: []});
        '''
        harness = f'const SCRIPT = {json.dumps(script)}, ENTRY = {json.dumps(entry)}, FRAME = {json.dumps(frame)};\n' + harness
        result = subprocess.run(['node', '-e', harness], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_madrid_first_access_matches_stencil_properties_and_rejects_other_cards(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source.split('private fun clickMadridCuentaDigitalCertificateLogin(', 1)[1].split(
            'private fun waitForBadajozEntryPageReady(', 1
        )[0]
        self.assertIn('"ui-button" to "Acceder a Cuenta Digital"', helper)
        self.assertIn('"a.idps__list--link[role=\\"link\\"][tabindex=\\"0\\"]"', helper)
        script = textwrap.dedent(helper.split('"""', 2)[1]).strip()
        source_url = 'https://digital.comunidad.madrid/login/'
        for selector, label, kind in (
            ('ui-button', 'Acceder a Cuenta Digital', 'ui-button'),
            ('a.idps__list--link[role="link"][tabindex="0"]', 'Certificado Digital', 'a'),
        ):
            with self.subTest(control=kind):
                evaluated = script.replace('$quotedSource', json.dumps(source_url)).replace(
                    '$quotedSelector', json.dumps(selector)
                ).replace('$quotedLabel', json.dumps(label))
                harness = r'''
                    const assert = require('node:assert/strict');
                    const vm = require('node:vm');
                    function run({duplicate = false, text = LABEL, role = 'link', href = SOURCE} = {}) {
                      let clicks = 0;
                      const element = {localName: KIND, innerText: '', xRole: role, type: 'link', text,
                        querySelectorAll(selector) {assert.equal(selector, 'ui-text'); return [{text}];},
                        click() {clicks++;}};
                      const document = {querySelectorAll(selector) {
                        assert.equal(selector, SELECTOR);
                        return duplicate ? [element, element] : [element];
                      }};
                      return {result: vm.runInNewContext(SCRIPT, {document, location: {href}}), clicks};
                    }
                    assert.deepEqual(run(), {result: 1, clicks: 1});
                    assert.deepEqual(run({duplicate: true}), {result: 2, clicks: 0});
                    assert.deepEqual(run({text: 'Cl@ve Permanente'}), {result: 0, clicks: 0});
                    assert.deepEqual(run({href: 'https://foreign.example/'}), {result: 0, clicks: 0});
                    if (KIND === 'ui-button') assert.deepEqual(run({role: 'button'}), {result: 0, clicks: 0});
                '''
                constants = dict(SCRIPT=evaluated, SELECTOR=selector, LABEL=label, KIND=kind, SOURCE=source_url)
                program = '\n'.join(f'const {name} = {json.dumps(value)};' for name, value in constants.items())
                result = subprocess.run(['node', '-e', program + harness], capture_output=True, text=True, check=False)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_malaga_recipe_uses_only_the_reviewed_clave_afirma_client_auth_path(self) -> None:
        source = self.read(INSTRUMENTATION)
        for expected in (
            'MALAGA_PORTAL_ID = "diputacion-malaga-sede"',
            'MALAGA_ENTRY_URL =',
            'MALAGA_LOCAL_CLAVE_URL = "https://clave.malaga.es/clave.php"',
            'MALAGA_CLAVE_SUBMIT_VALUE = "Acceder con cl@ve"',
            'CLAVE_SERVICE_PROVIDER_URL =',
            'CLAVE_SERVICE_REDIRECT_URL =',
            'CLAVE_AFIRMA_ONCLICK =',
            'CLAVE_AFIRMA_IMAGE_SRC = "ImageRetrieve?id=IDP_AFIRMA"',
            'MALAGA_PORTAL_ID -> runMalagaClaveCertificateRecipe(scenario)',
            "document.querySelectorAll('form#claveFrm')",
            "input[type=\"hidden\"][name=\"tkn\"]",
            "input[type=\"submit\"]",
            "document.querySelectorAll('form[name=\"idpRedirect\"]')",
            "input[type=\"hidden\"][name=\"SAMLRequest\"]",
            "input[type=\"hidden\"][name=\"RelayState\"]",
            "input[type=\"hidden\"][name=\"SelectedIdP\"]",
            "typeof window.selectedIdP !== 'function'",
            "controls.length !== 1 || images.length !== 1",
            "const controlCard = controls[0].closest('article')",
            "const imageCard = images[0].closest('article')",
            "if (!controlCard || controlCard !== imageCard) return 2",
        ):
            self.assertIn(expected, source)
        recipe = source[source.index('private fun runMalagaClaveCertificateRecipe('):]
        recipe = recipe[:recipe.index('private fun clickStaCertificateLogin(')]
        self.assertNotIn('window.location=', recipe.replace(' ', ''))
        self.assertNotIn('loadUrl(', recipe)
        self.assertNotIn('controls[0].contains(images[0])', recipe)
        self.assertIn('controls[0].click()', recipe)

    def test_clave_afirma_step_waits_for_a_complete_service_provider_page(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source[source.index('private fun clickClaveAfirmaProvider('):]
        helper = helper[:helper.index('const forms = Array.from(')]
        self.assertIn("if (document.readyState !== 'complete') return 0;", helper)

    def test_clave_recipes_wait_while_the_document_lags_the_webview_url(self) -> None:
        source = self.read(INSTRUMENTATION)
        for helper_name, location_check in (
            ('clickClaveAfirmaProvider', "if (location.href !== $quotedServiceProvider) return 0;"),
            ('clickGestionaClaveLogin', "if (location.origin !== $quotedOrigin || location.pathname !== '/') return 0;"),
            ('clickRecClaveLogin', "if (location.href !== ${JSONObject.quote(REC_HOME_URL)}) return 0;"),
        ):
            helper = source[source.index(f'private fun {helper_name}('):]
            helper = helper[:helper.index('\n    private fun ', 1)]
            self.assertIn(location_check, helper)
            # The Kotlin side still rejects or times out on a real URL mismatch before any click.
            self.assertIn('val currentUrl = webView.url', helper)

    def test_mugeju_recipe_only_picks_the_clave_certificate_idp(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('MUGEJU_PORTAL_ID = "age-mutualidad-general-judicial-mugeju"', source)
        self.assertIn('MUGEJU_PORTAL_ID, AIREF_PORTAL_ID, CADIZ_PORTAL_ID -> clickClaveAfirmaProvider(scenario)', source)
        self.assertIn('CADIZ_PORTAL_ID = "diputacion-cadiz-portal"', source)

    def test_jccm_and_coruna_recipes_click_only_reviewed_controls_before_clave(self) -> None:
        source = self.read(INSTRUMENTATION)
        jccm = source.split('JCCM_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertIn('clickExactAuthButton(', jccm)
        self.assertTrue(jccm.rstrip().endswith('clickClaveAfirmaProvider(scenario, timeoutMillis = JCCM_ALTCHA_TIMEOUT_MILLIS)'))
        self.assertIn('JCCM_CLAVE_BUTTON_ID = "boton_presentar_solicitud_clave"', source)
        self.assertIn('JCCM_CLAVE_BUTTON_ONCLICK = "cargar_documentos_clave(); return false;"', source)
        coruna = source.split('CORUNA_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertIn('CORUNA_LINK_HOPS.forEach', coruna)
        self.assertIn('expectedType = null', coruna)
        self.assertTrue(coruna.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
        hops = source.split('val CORUNA_LINK_HOPS = listOf(', 1)[1].split('\n        )\n', 1)[0]
        self.assertEqual(hops.count('RecipeLinkHop('), 4)
        self.assertIn('CORUNA_TRAMITADOR_URL', hops)
        for forbidden in ('loadUrl(', '.submit(', 'SAMLRequest'):
            self.assertNotIn(forbidden, jccm + coruna)

    def test_soria_rioja_menorca_recipes_click_only_the_reviewed_certificate_control(self) -> None:
        source = self.read(INSTRUMENTATION)
        soria = source.split('SORIA_PORTAL_ID -> clickExactButton(', 1)[1].split('\n            )\n', 1)[0]
        self.assertIn('expectedCurrentUrl = SORIA_SOURCE_URL', soria)
        self.assertIn('SORIA_CERT_BUTTON_ONCLICK = "pulsarCertificado();"', source)
        rioja = source.split('LA_RIOJA_PORTAL_ID -> clickExactButton(', 1)[1].split('\n            )\n', 1)[0]
        self.assertIn('matchUrlWithoutQuery = true', rioja)
        self.assertIn('LA_RIOJA_CAS_LOGIN_URL = "https://ias1.larioja.org/casLR/login"', source)
        self.assertIn(
            'LA_RIOJA_CERT_BUTTON_ONCLICK = "loginClientCertSSL(\'https://ias1.larioja.org/clientcertSSL/login\')"',
            source,
        )
        menorca = source.split('MENORCA_PORTAL_INSTITUCIONAL_ID, MENORCA_SEDE_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertEqual(menorca.count('clickExactLabeledAnchor('), 1)
        self.assertIn('expectedTag = "input"', menorca)
        self.assertIn('expectedType = "submit"', menorca)
        self.assertIn('MENORCA_CERT_SUBMIT_ID = "ctl00_Content1_Button1"', source)
        self.assertIn('MENORCA_PERSONAL_DATA_HREF = "/cime/InfoPersonal.aspx"', source)
        self.assertNotIn('iniciartramit.aspx', source)
        for forbidden in ('loadUrl(', '.submit(', 'clickClaveAfirmaProvider'):
            self.assertNotIn(forbidden, soria + rioja + menorca)

    def test_safe_auth_sign_recipes_click_only_the_reviewed_login_control(self) -> None:
        source = self.read(INSTRUMENTATION)
        safe = source.split('val SAFE_AUTH_SIGN_PROFILES = setOf(', 1)[1].split(')', 1)[0]
        for profile in ('"dgt-verificacion-equipo"', '"aragon-siraw"', '"transportes-qys-cert-login"',
                        '"diputacion-lugo-sede"'):
            self.assertIn(profile, safe)
        blocks = []
        for head in ('DGT_PORTAL_ID -> clickExactButton(', 'SIRAW_PORTAL_ID -> clickExactAuthButton(',
                     'TRANSPORTES_PORTAL_ID -> clickExactButton(', 'LUGO_PORTAL_ID -> clickExactButton('):
            blocks.append(source.split(head, 1)[1].split('\n            )\n', 1)[0])
        self.assertIn('DGT_VERIFY_BUTTON_ONCLICK = "cargarAF()"', source)
        self.assertIn('LUGO_CERT_BUTTON_ONCLICK = "waiting(); chargeClient();"', source)
        self.assertIn('TRANSPORTES_SIGN_BUTTON_ONCLICK = "Sign_firmar();return false;"', source)
        for forbidden in ('loadUrl(', '.submit(', 'clickClaveAfirmaProvider'):
            self.assertNotIn(forbidden, ''.join(blocks))

    def test_caceres_recipe_follows_access_then_clave_logo(self) -> None:
        source = self.read(INSTRUMENTATION)
        block = source.split('CACERES_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertIn('expectedHref = CACERES_ACCESS_HREF', block)
        self.assertIn('expectedHref = CACERES_CLAVE_HREF', block)
        self.assertTrue(block.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
        self.assertIn('CACERES_CLAVE_HREF = "LoginClave2Front.do"', source)

    def test_csn_recipe_only_follows_the_reviewed_certificate_link(self) -> None:
        source = self.read(INSTRUMENTATION)
        block = source.split('CSN_PORTAL_ID -> clickExactLabeledAnchor(', 1)[1].split('\n            )\n', 1)[0]
        self.assertIn('expectedHref = CSN_CERT_HREF', block)
        self.assertIn('CSN_CERT_HREF = "https://sede.csn.gob.es:/Sede20/clave/peticioncert"', source)
        self.assertIn('CSN_PORTAL_ID = "age-consejo-de-seguridad-nuclear-csn"', source)

    def test_sede_gob_and_gsede_recipes_end_on_clave_without_scripted_navigation(self) -> None:
        source = self.read(INSTRUMENTATION)
        for head in ('in SEDE_GOB_CLAVE_HOSTS -> {', 'in GSEDE_CLAVE_PORTALS -> {'):
            block = source.split(head, 1)[1].split('\n            }\n', 1)[0]
            self.assertTrue(block.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
            for forbidden in ('loadUrl(', '.submit(', 'evaluateJavascript('):
                self.assertNotIn(forbidden, block)
        self.assertIn('"age-enaire-entidad-publica-empresarial" to "enaire.sede.gob.es"', source)
        self.assertIn('claveOnClick = "waiting(); loginClave();"', source)
        self.assertIn('claveOnClick = "loginClave();"', source)

    def test_wave_a_clave_recipes_stop_at_certificate_login(self) -> None:
        source = self.read(INSTRUMENTATION)
        consequential = source.split('val CONSEQ_RECIPE_PROFILES = setOf(', 1)[1].split('\n        )', 1)[0]
        profiles = {p["profileId"]: p for p in json.loads(
            self.read(ROOT / "config/site_profiles_v1.json")
        )["profiles"]}
        contracts = {
            "CNMV": "cnmv-sede-public-home",
            "CSD": "csd-sede-public",
            "POLICIA": "policia-solicitud-generica",
            "CATASTRO": "catastro-solicitudes-genericas",
            "MINECO": "ministerio-economia-instancia-generica",
            "TRANSPARENCIA": "age-portal-de-la-transparencia",
            "RED_ES": "red-es-sede-public-navigation",
            "ALMERIA": "diputacion-almeria-solicitud-general",
            "CORDOBA": "diputacion-cordoba-solicitud-generica",
            "PALENCIA": "diputacion-palencia-solicitud-general",
            "EDUCATION": "educacion-convocatoria",
            "EXTREMADURA_ANTERIOR": "extremadura-tramites",
            "FUERTEVENTURA": "fuerteventura-sede-electronica",
            "JUSTICIA": "justicia-sede-judicial-private-area",
            "MADRID_SEDE": "madrid-sede-tarjeta-azul",
            "MJUSTICIA": "mjusticia-fundaciones-idp75",
        }
        for name, profile_id in contracts.items():
            with self.subTest(profile=profile_id):
                profile = profiles[profile_id]
                self.assertEqual("QA_ONLY", profile["activation"])
                self.assertIn("CLIENT_TLS_AUTH", profile["capabilities"])
                if "SIGN" in profile["capabilities"]:
                    self.assertIn(f'"{profile_id}"', consequential)
                policy = profile["clientAuthPolicy"]
                self.assertEqual("IN_PLACE_FROM_SOURCE", policy["transitionMode"])
                self.assertEqual("POST", policy["requestMethod"])
                self.assertEqual(["https://pasarela-ident.clave.gob.es"], policy["requestOrigins"])
                self.assertEqual("/IdP2/AuthenticateCitizen", policy["requestPath"])
                self.assertEqual(15, policy["grantTtlSeconds"])
                self.assertEqual({}, policy["fixedQueryParameters"])
                self.assertEqual([], policy["requiredEphemeralQueryParameters"])
                terminal = "ServiceProvider" if name == "MADRID_SEDE" else "ServiceRedirect"
                self.assertEqual([f"https://pasarela.clave.gob.es/Proxy2/{terminal}"], policy["sourceUrls"])
                block = source.split(f"{name}_PORTAL_ID -> {{", 1)[1].split("\n            }\n", 1)[0]
                if name != "MADRID_SEDE":
                    self.assertTrue(block.rstrip().endswith("clickClaveAfirmaProvider(scenario)"))
                for forbidden in ("loadUrl(", ".submit(", "evaluateJavascript(", "sign("):
                    self.assertNotIn(forbidden, block)
        self.assertIn('FUERTEVENTURA_INSTITUCIONAL_PORTAL_ID = "fuerteventura-portal-institucional"', source)
        self.assertIn('expectedSrc = PALENCIA_CLAVE_IMAGE_SRC', source)
        self.assertIn('expectedType = "image"', source)
        self.assertIn('matchUrlWithoutQuery = true', source)
        self.assertIn('CORDOBA_CLAVE_HREF = ""', source)
        cordoba = source.split('CORDOBA_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertIn('expectedHref = CORDOBA_CLAVE_HREF', cordoba)
        self.assertIn('expectedFormName = "formClave"', cordoba)
        self.assertIn(
            'expectedFormAction = "https://apis.dipucordoba.es/apiclave/api/sessions"', cordoba,
        )

    def test_cordoba_login_click_matches_only_the_observed_anchor_and_form(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source.split('private fun clickExactLabeledAnchor(', 1)[1]
        script = helper.split('webView.evaluateJavascript(', 1)[1].split('"""', 2)[1]
        values = {
            "matchUrlWithoutQuery": False,
            "quotedExpectedUrl": (
                "https://sede.dipucordoba.es/diputacion/tramites/procedimiento/8876/solicitud-generica"
            ),
            "quotedExpectedLabel": "Ir al trámite con Cl@ve",
            "quotedExpectedOnClick": None,
            "quotedShadowHost": "",
            "quotedFormName": "formClave",
            "quotedFormAction": "https://apis.dipucordoba.es/apiclave/api/sessions",
            "quotedCssClass": None,
            "quotedExpectedElementId": None,
            "requireVisible": False,
            "requireFormContainment": False,
            "quotedAnchorType": None,
            "quotedAnchorAriaLabel": None,
            "quotedExpectedTitle": None,
        }
        scripts = {}
        for name, href in (("old", "www.dipucordoba.es"), ("reviewed", "")):
            substitutions = dict(values, quotedExpectedHref=href)
            scripts[name] = re.sub(r'\$(\w+)', lambda match: json.dumps(substitutions[match[1]]), script)
        harness = r"""
            const assert = require('node:assert/strict');
            const scripts = SCRIPTS;
            // Public attributes shared by probe 42466003392 snapshots 0 and 1.
            const snapshots = [0, 1].map(step => ({
              step,
              url: 'https://sede.dipucordoba.es/diputacion/tramites/procedimiento/8876/solicitud-generica',
              controls: [
                {tag: 'a', innerText: 'Ir al trámite con Cl@ve', href: null},
                {tag: 'a', innerText: 'Ir al trámite Sin identificación', href: null}
              ],
              form: {
                id: 'formClave', name: '', method: 'post', target: null,
                action: 'https://apis.dipucordoba.es/apiclave/api/sessions'
              }
            }));
            const run = (snapshot, change = {}, script = scripts.reviewed) => {
              let clicks = 0;
              const controls = snapshot.controls.map(control => ({...control}));
              if (change.publicOnly) controls.shift();
              else Object.assign(controls[0], change.anchor);
              if (change.duplicateAnchor) controls.push({...controls[0]});
              const elements = controls.map(control => ({
                ...control,
                getAttribute: name => control[name] ?? null,
                click: () => {
                  assert.equal(control.innerText, 'Ir al trámite con Cl@ve');
                  clicks++;
                }
              }));
              const form = {...snapshot.form, ...change.form};
              form.getAttribute = name => form[name] ?? null;
              const document = {
                readyState: 'complete',
                forms: change.missingForm ? [] : change.duplicateForm ? [form, form] : [form],
                querySelectorAll: tag => elements.filter(element => element.tag === tag)
              };
              const location = {href: change.url || snapshot.url};
              const code = eval(script);
              return [code, clicks];
            };
            for (const snapshot of snapshots) {
              assert.deepEqual(run(snapshot, {}, scripts.old), [0, 0]);
              assert.deepEqual(run(snapshot), [1, 1]);
              assert.deepEqual(run(snapshot, {anchor: {href: ''}}), [1, 1]);
              for (const change of [
                {publicOnly: true}, {anchor: {tag: 'div'}}, {anchor: {innerText: 'Acceso Cl@ve'}},
                ...['www.dipucordoba.es', '#', 'javascript:void(0)', 'https://foreign.example/login']
                  .map(href => ({anchor: {href}}))
              ]) assert.deepEqual(run(snapshot, change), [0, 0]);
              assert.deepEqual(run(snapshot, {duplicateAnchor: true}), [2, 0]);
              for (const url of [
                snapshot.url.replace('sede.dipucordoba.es', 'foreign.example'),
                snapshot.url + '/unreviewed', snapshot.url + '?unreviewed=1', snapshot.url + '#unreviewed'
              ]) assert.deepEqual(run(snapshot, {url}), [0, 0]);
              for (const change of [
                {missingForm: true}, {duplicateForm: true}, {form: {id: 'unreviewed'}},
                {form: {action: 'https://foreign.example/apiclave/api/sessions'}},
                {form: {action: 'https://apis.dipucordoba.es/apiclave/api/applications'}},
                {form: {method: 'get'}}, {form: {target: '_blank'}}
              ]) assert.deepEqual(run(snapshot, change), [2, 0]);
            }
        """.replace('SCRIPTS', json.dumps(scripts))
        result = subprocess.run(['node', '-e', harness], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_wave_d_clave_recipes_allow_only_reviewed_certificate_login(self) -> None:
        source = self.read(INSTRUMENTATION)
        profiles = {p["profileId"]: p for p in json.loads(
            self.read(ROOT / "config/site_profiles_v1.json")
        )["profiles"]}
        for name, profile_id in (
            ("AESA", "aesa-solicitud-general-public"),
            ("DGSFP", "dgsfp-sede-public-home"),
        ):
            with self.subTest(profile=profile_id):
                profile = profiles[profile_id]
                self.assertEqual(2, profile["profileVersion"])
                self.assertEqual("QA_ONLY", profile["activation"])
                self.assertEqual(["CLIENT_TLS_AUTH"], profile["capabilities"])
                self.assertEqual([], profile["operationPolicies"])
                policy = profile["clientAuthPolicy"]
                self.assertEqual("IN_PLACE_FROM_SOURCE", policy["transitionMode"])
                self.assertEqual("POST", policy["requestMethod"])
                self.assertEqual(["https://pasarela-ident.clave.gob.es"], policy["requestOrigins"])
                self.assertEqual(["https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"], policy["sourceUrls"])
                self.assertEqual("/IdP2/AuthenticateCitizen", policy["requestPath"])
                self.assertEqual({}, policy["fixedQueryParameters"])
                self.assertEqual([], policy["requiredEphemeralQueryParameters"])
                self.assertEqual(15, policy["grantTtlSeconds"])
                block = source.split(f"{name}_PORTAL_ID -> {{", 1)[1].split("\n            }\n", 1)[0]
                self.assertTrue(block.rstrip().endswith("clickClaveAfirmaProvider(scenario)"))
                for forbidden in ("loadUrl(", ".submit(", "evaluateJavascript(", "sign("):
                    self.assertNotIn(forbidden, block)

    def test_wave_d_autofirma_recipes_sign_only_login_challenges(self) -> None:
        source = self.read(INSTRUMENTATION)
        safe = source.split('val SAFE_AUTH_SIGN_PROFILES = setOf(', 1)[1].split(')', 1)[0]
        for name, profile in (("CANARIAS", "canarias-sede"), ("AEMET", "aemet-public-solicitud-navigation")):
            self.assertIn(f'"{profile}"', safe)
            block = source.split(f"{name}_PORTAL_ID -> {{", 1)[1].split("\n            }\n", 1)[0]
            self.assertEqual(2, block.count("clickExactLabeledAnchor("))
            self.assertEqual(1, block.count("clickExactButton("))
            self.assertIn("requireVisible = true", block)
            for forbidden in ("loadUrl(", ".submit(", "evaluateJavascript(", "sign(", '"Aceptar"'):
                self.assertNotIn(forbidden, block)
        self.assertIn('expectedOnClick = "firmar(formData);"', source)
        self.assertIn('expectedLabel = "Validarme"', source)
        self.assertIn('expectedHandlerName = "envia"', source)
        self.assertIn('waitForAemetSignHook = true', source)
        self.assertIn('expectedFormAction = AEMET_LOGIN_ACTION_URL', source)
        self.assertIn('matchCanariasLoginAction = true', source)
        self.assertIn('expectedName = "autenticarComprobacion"', source)
        stop = source.split('if (!deepEnabled && profileId == AEMET_PROFILE_ID) {', 1)[1].split('\n                }', 1)[0]
        self.assertIn('abandonAemetLoginAtBoundary(scenario)', stop)
        self.assertIn('classify(profileCapabilities, result)', stop)
        self.assertIn('return', stop)
        self.assertNotIn('performClick', stop)
        abandon = source.split('private fun abandonAemetLoginAtBoundary(', 1)[1].split('private fun ', 1)[0]
        self.assertIn('coordinator.cancel(SigningCancelReason.BACKGROUND, pending.requestId)', abandon)
        self.assertIn('check(abandoned)', abandon)
        soria = source.split('SORIA_PORTAL_ID -> clickExactButton(', 1)[1].split('\n            )\n', 1)[0]
        self.assertIn('expectedHandlerName = "pulsarCertificado"', soria)

    def test_exact_login_click_rejects_unreviewed_forms_and_stale_documents(self) -> None:
        source = self.read(INSTRUMENTATION)
        values = {
            "matchUrlWithoutQuery": False,
            "quotedExpectedUrl": "https://login.example/login/",
            "quotedExpectedElementId": "clave",
            "quotedExpectedLabel": "",
            "quotedExpectedAriaLabel": None,
            "quotedExpectedOnClick": None,
            "quotedWaitForBadajozSignHook": False,
            "quotedExpectedTag": "input",
            "quotedParentAnchorClass": None,
            "quotedHandlerName": None,
            "requireVisible": False,
            "waitForAemetSignHook": False,
            "quotedExpectedName": None,
            "matchCanariasLoginAction": False,
            "quotedAnchorType": None,
            "quotedAnchorAriaLabel": None,
            "quotedExpectedTitle": None,
            "quotedExpectedType": "image",
            "quotedExpectedSrc": "clave.png",
            "quotedFormName": "login",
            "quotedFormAction": "https://login.example/login/",
            "requireNoOnClick": True,
            "quotedShadowHost": "",
            "quotedExpectedHref": "#",
            "quotedCssClass": "imagenIdentificarse",
            "requireVisible": False,
            "requireFormContainment": True,
        }
        scripts = {}
        for kind, name in (("button", "clickExactButton"), ("anchor", "clickExactLabeledAnchor")):
            visibility = 'internal' if name == 'clickExactButton' else 'private'
            helper = source.split(f'{visibility} fun {name}(', 1)[1]
            script = helper.split('webView.evaluateJavascript(', 1)[1].split('"""', 2)[1]
            substitutions = dict(values, quotedExpectedOnClick="submit();" if kind == "anchor" else None)
            scripts[kind] = re.sub(r'\$(\w+)', lambda match: json.dumps(substitutions[match[1]]), script)
            if kind == "anchor":
                substitutions["requireFormContainment"] = False
                scripts["anchor_legacy"] = re.sub(
                    r'\$(\w+)', lambda match: json.dumps(substitutions[match[1]]), script,
                )
        harness = r"""
            const assert = require('node:assert/strict');
            for (const [variant, script] of Object.entries(SCRIPTS)) {
            const kind = variant.split('_')[0];
            const run = (change = {}) => {
              let clicks = 0;
              const form = {
                id: 'login', name: 'login', action: 'https://login.example/login/',
                contains: element => element.form === form,
                getAttribute: name => name === 'method' ? change.method || 'post' : change.target || null
              };
              if (change.action) form.action = change.action;
              const element = {
                id: change.id || 'clave', value: '', innerText: '', form: change.detached ? {} : form,
                classList: {contains: name => name === 'imagenIdentificarse' && !change.wrongClass},
                getAttribute: name => ({type: 'image', src: 'clave.png',
                  href: '#', onclick: change.onclick || (kind === 'anchor' ? 'submit();' : null),
                  formaction: change.formaction, formmethod: change.formmethod,
                  formtarget: change.formtarget}[name] ?? null),
                click: () => clicks++
              };
              const document = {
                forms: change.missing ? [] : change.duplicate ? [form, form] : [form],
                querySelectorAll: () => [element]
              };
              const location = {href: change.url || 'https://login.example/login'};
              const window = {};
              const code = eval(script);
              return [code, clicks];
            };
            assert.deepEqual(run(), [1, 1]);
            assert.deepEqual(run({url: 'https://login.example/login/'}), [1, 1]);
            assert.deepEqual(run({url: 'https://login.example/login/;jsessionid=SYNTHETIC'}), [1, 1]);
            assert.deepEqual(run({url: 'https://login.example/login;jsessionid=SYNTHETIC/'}), [1, 1]);
            assert.deepEqual(run({action: 'https://login.example/login/;jsessionid=SYNTHETIC'}), [1, 1]);
            assert.deepEqual(run({target: '_parent'}), [1, 1]);
            for (const change of [
              {action: 'https://login.example/file'}, {action: 'https://foreign.example/login/'},
              {action: 'https://login.example;jsessionid=.evil.test/login/'},
              {action: 'https://login.example/login/?session=;jsessionid=SYNTHETIC'},
              {action: 'https://login.example/login/#;jsessionid=SYNTHETIC'},
              {method: 'get'}, {missing: true}, {duplicate: true}, {target: '_blank'}
            ]) assert.deepEqual(run(change), [2, 0]);
            assert.deepEqual(run({detached: true}), variant === 'anchor_legacy' ? [1, 1] : [2, 0]);
            assert.deepEqual(run({id: 'other-login'}), [0, 0]);
            if (kind === 'button') for (const change of [
              {formaction: '/file'}, {formmethod: 'get'}, {formtarget: '_blank'}
            ]) assert.deepEqual(run(change), [2, 0]);
            if (kind === 'anchor') assert.deepEqual(run({wrongClass: true}), [0, 0]);
            assert.deepEqual(run({onclick: 'file();'}), [0, 0]);
            assert.deepEqual(run({url: 'https://login.example/stale'}), [0, 0]);
            assert.deepEqual(run({url: 'https://login.example/login//'}), [0, 0]);
            for (const url of ['https://login.example;jsessionid=.evil.test/login/',
              'https://login.example/login/?session=;jsessionid=SYNTHETIC',
              'https://login.example/login/#;jsessionid=SYNTHETIC']) {
              assert.deepEqual(run({url}), [0, 0], url);
            }
            }
        """.replace('SCRIPTS', json.dumps(scripts))
        result = subprocess.run(['node', '-e', harness], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_exact_anchor_can_require_one_visible_match_in_responsive_layout(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source.split('private fun clickExactLabeledAnchor(', 1)[1]
        script = helper.split('webView.evaluateJavascript(', 1)[1].split('"""', 2)[1]
        values = {
            "matchUrlWithoutQuery": False, "quotedExpectedUrl": "https://sede.depo.gal/start",
            "quotedShadowHost": "", "quotedExpectedLabel": "En liña", "quotedExpectedHref": "/login",
            "quotedExpectedOnClick": None, "quotedCssClass": "rightformbutton",
            "quotedFormName": None, "quotedFormAction": None, "requireVisible": True,
            "quotedExpectedElementId": None,
            "requireFormContainment": False,
            "quotedAnchorType": None, "quotedAnchorAriaLabel": None, "quotedExpectedTitle": None,
        }
        script = re.sub(r'\$(\w+)', lambda match: json.dumps(values[match[1]]), script)
        harness = r"""
            const assert = require('node:assert/strict');
            const run = (copies, change = {}) => {
              let clicks = 0;
              const anchors = copies.map(visible => ({
                innerText: change.label || '  En liña\n ',
                classList: {contains: name => name === 'rightformbutton' && !change.wrongClass},
                getAttribute: name => ({href: change.href || '/login', onclick: null}[name] ?? null),
                getClientRects: () => visible ? [{}] : [],
                click: () => clicks++
              }));
              const document = {querySelectorAll: () => anchors};
              const location = {href: change.url || 'https://sede.depo.gal/start'};
              return [eval(SCRIPT), clicks];
            };
            assert.deepEqual(run([false, true]), [1, 1]);
            assert.deepEqual(run([true, false]), [1, 1]);
            assert.deepEqual(run([true]), [1, 1]);
            assert.deepEqual(run([true, true]), [2, 0]);
            assert.deepEqual(run([false, false]), [0, 0]);
            assert.deepEqual(run([false]), [0, 0]);
            assert.deepEqual(run([]), [0, 0]);
            for (const change of [{label: 'Presencial'}, {href: '/file'}, {wrongClass: true},
              {url: 'https://foreign.example/start'}]) assert.deepEqual(run([false, true], change), [0, 0]);
        """.replace('SCRIPT', json.dumps(script))
        result = subprocess.run(['node', '-e', harness], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_gestiona_identificate_clicks_only_one_reviewed_ephemeral_anchor(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source.split('private fun clickGestionaIdentificate(', 1)[1].split('\n    private fun ', 1)[0]
        script = helper.split('webView.evaluateJavascript(', 1)[1].split('"""', 2)[1]
        values = {"quotedExpectedUrl": "https://tenant.example/info.0", "quotedLabel": "Identifícate"}
        script = re.sub(r'\$(\w+)', lambda match: json.dumps(values[match[1]]), script)
        harness = r"""
            const assert = require('node:assert/strict');
            const run = (change = {}) => {
              let clicks = 0;
              const element = {
                innerText: change.label ?? '  Identifícate\n ',
                getAttribute: name => name === 'href' ?
                  (Object.hasOwn(change, 'href') ? change.href : '?x=synthetic-token') : null,
                get href() {
                  return new URL(this.getAttribute('href'), change.base || 'https://tenant.example/info.0').href;
                },
                click: () => clicks++
              };
              const document = {
                querySelectorAll: selector => {
                  assert.equal(selector, 'a');
                  return change.missing ? [] : change.duplicate ? [element, element] : [element];
                }
              };
              const location = {href: change.url || 'https://tenant.example/info.0'};
              return [eval(SCRIPT), clicks];
            };
            assert.deepEqual(run(), [1, 1]);
            assert.deepEqual(run({duplicate: true}), [2, 0]);
            assert.deepEqual(run({missing: true}), [0, 0]);
            assert.deepEqual(run({label: 'Presentar solicitud'}), [0, 0]);
            assert.deepEqual(run({base: 'https://foreign.example/'}), [0, 0]);
            assert.deepEqual(run({base: 'https://tenant.example/'}), [1, 1]);
            for (const href of [null, '', '#', '?x=', 'https://foreign.example/?x=token',
              '//foreign.example/?x=token', '?x=//foreign.example', '?x=javascript:alert(1)',
              '?x=token&file=true', '?x=token#submit', '?x=token\\path', '?x=token space']) {
              assert.deepEqual(run({href}), [0, 0], href);
            }
            for (const url of ['https://foreign.example/info.0', 'https://tenant.example/info.2',
              'https://tenant.example/info.0?x=token', 'https://tenant.example/info.0/']) {
              assert.deepEqual(run({url}), [0, 0], url);
            }
        """.replace('SCRIPT', json.dumps(script))
        result = subprocess.run(['node', '-e', harness], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        for forbidden in ('loadUrl(', '.submit(', 'SAMLRequest'):
            self.assertNotIn(forbidden, helper)

    def test_wave_c_recipes_prove_only_the_reviewed_clave_certificate_login(self) -> None:
        source = self.read(INSTRUMENTATION)
        direct = source.split('val GESTIONA_CLAVE_ORIGINS = mapOf(', 1)[1].split('\n        )', 1)[0]
        identify = source.split('val GESTIONA_IDENTIFICATE_ORIGINS = mapOf(', 1)[1].split('\n        )', 1)[0]
        for portal, origin in {
            "diputacion-segovia-sede": "https://sede.dipsegovia.es",
            "el-hierro-portal-institucional": "https://elhierro.sedelectronica.es",
            "la-gomera-portal-institucional": "https://lagomera.sedelectronica.es",
        }.items():
            self.assertIn(f'"{portal}" to "{origin}"', direct)
        for portal, origin in {
            "diputacion-zamora-sede": "https://diputaciondezamora.sedelectronica.es",
            "diputacion-zaragoza-sede": "https://dpz.sedelectronica.es",
            "el-hierro-sede-electronica": "https://elhierro.sedelectronica.es",
            "la-gomera-sede-electronica": "https://lagomera.sedelectronica.es",
            "lanzarote-sede-electronica": "https://lanzaroteylagraciosa.sedelectronica.es",
        }.items():
            self.assertIn(f'"{portal}" to "{origin}"', identify)
        block = source.split('in GESTIONA_IDENTIFICATE_ORIGINS -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertIn('clickGestionaIdentificate(scenario, origin)', block)
        self.assertIn('clickGestionaClaveLogin(scenario, origin)', block)
        self.assertTrue(block.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
        isfas = source.split('ISFAS_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        for expected in ('clickExactButton(', 'expectedLabel = "Identificarse"',
                         'expectedOnClick = "identificarse();"', 'expectedFormName = "menuPrincipal"',
                         'clickExactLabeledAnchor(', 'expectedLabel = "Cl@ve"', 'expectedHref = "#"',
                         'expectedOnClick = ISFAS_CLAVE_ONCLICK', 'expectedFormName = "formWelcome"',
                         'expectedCssClass = "imagenIdentificarse"'):
            self.assertIn(expected, isfas)
        self.assertTrue(isfas.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
        self.assertIn('ISFAS_PORTAL_ID = "age-instituto-social-de-las-fuerzas-armadas-isfas"', source)
        self.assertIn("mojarra.jsfcljs(document.getElementById('formWelcome'),", source)
        self.assertIn("{'j_idt85':'j_idt85'},'');return false", source)
        for forbidden in ('loadUrl(', '.submit(', 'evaluateJavascript(', 'Tarjeta PKI'):
            self.assertNotIn(forbidden, block + isfas)
        self.assertNotIn('"la-palma-portal-institucional"', direct + identify)
        profiles = {p['profileId']: p for p in json.loads(self.read(ROOT / 'config/site_profiles_v1.json'))['profiles']}
        for profile_id in (
            'diputacion-segovia-registro', 'el-hierro-solicitud-general', 'la-gomera-instancia-general',
            'zamora-sede-public-navigation', 'zaragoza-sede-public-navigation', 'el-hierro-sede-public-navigation',
            'la-gomera-sede-public-navigation', 'lanzarote-sede-public-navigation', 'isfas-sede-public-navigation',
        ):
            with self.subTest(profile=profile_id):
                profile = profiles[profile_id]
                self.assertEqual(2, profile['profileVersion'])
                self.assertEqual('QA_ONLY', profile['activation'])
                self.assertEqual(['CLIENT_TLS_AUTH'], profile['capabilities'])
                self.assertEqual([], profile['operationPolicies'])
                self.assertTrue(profile['certificateRules']['requireDigitalSignatureKeyUsage'])
                policy = profile['clientAuthPolicy']
                self.assertEqual('IN_PLACE_FROM_SOURCE', policy['transitionMode'])
                self.assertEqual('POST', policy['requestMethod'])
                self.assertEqual(['https://pasarela.clave.gob.es/Proxy2/ServiceRedirect'], policy['sourceUrls'])
                self.assertEqual(['https://pasarela-ident.clave.gob.es'], policy['requestOrigins'])
                self.assertEqual('/IdP2/AuthenticateCitizen', policy['requestPath'])
                self.assertEqual(443, policy['requestPort'])
                self.assertEqual(15, policy['grantTtlSeconds'])
                self.assertEqual({}, policy['fixedQueryParameters'])
                self.assertEqual([], policy['requiredEphemeralQueryParameters'])

    def test_wave_c2_recipes_stop_at_the_reviewed_certificate_login(self) -> None:
        source = self.read(INSTRUMENTATION)
        pontevedra = source.split('PONTEVEDRA_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        extremadura = source.split('EXTREMADURA_PORTAL_ID -> {', 1)[1].split('\n            }\n', 1)[0]
        for expected in ('expectedCurrentUrl = PONTEVEDRA_ENTRY_URL', 'expectedLabel = "En liña"',
                         'expectedHref = PONTEVEDRA_LOGIN_HREF', 'expectedCssClass = "rightformbutton"',
                         'requireVisible = true', 'expectedCurrentUrl = PONTEVEDRA_LOGIN_URL',
                         'expectedLabel = "Certificado dixital ou DNI electrónico"', 'expectedHref = "#"',
                         'expectedOnClick = PONTEVEDRA_CLAVE_ONCLICK', 'expectedFormName = "formclave"',
                         'requireFormContainment = true',
                         'expectedFormAction = PONTEVEDRA_FORM_ACTION'):
            self.assertIn(expected, pontevedra)
        for expected in ('expectedCurrentUrl = EXTREMADURA_ANTERIOR_CLAVE_0_URL', 'expectedLabel = "Miespacio"',
                         'expectedHref = EXTREMADURA_LOGIN_URL', 'expectedCurrentUrl = EXTREMADURA_LOGIN_URL',
                         'expectedElementId = "link-clave"',
                         'expectedLabel = "Acceso mediante el sistema CLAVE"', 'expectedHref = EXTREMADURA_CLAVE_HREF'):
            self.assertIn(expected, extremadura)
        self.assertNotIn('requireVisible = true', extremadura)
        for block in (pontevedra, extremadura):
            self.assertEqual(2, block.count('clickExactLabeledAnchor('))
            self.assertTrue(block.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
            for forbidden in ('loadUrl(', '.submit(', 'evaluateJavascript(', 'sign(', 'Clave PIN', 'Presencial'):
                self.assertNotIn(forbidden, block)
        self.assertIn('PONTEVEDRA_PORTAL_ID = "diputacion-pontevedra-sede"', source)
        self.assertIn("{'formclave:j_idt231':'formclave:j_idt231'},'');return false", source)
        self.assertIn('EXTREMADURA_PORTAL_ID = "extremadura-tramites"', source)
        profiles = {p['profileId']: p for p in json.loads(self.read(ROOT / 'config/site_profiles_v1.json'))['profiles']}
        for profile_id in ('diputacion-pontevedra-instancia-xenerica', 'extremadura-tramites'):
            profile = profiles[profile_id]
            self.assertEqual(2, profile['profileVersion'])
            self.assertEqual('QA_ONLY', profile['activation'])
            policy = profile['clientAuthPolicy']
            self.assertEqual('IN_PLACE_FROM_SOURCE', policy['transitionMode'])
            self.assertEqual('POST', policy['requestMethod'])
            self.assertEqual(['https://pasarela.clave.gob.es/Proxy2/ServiceRedirect'], policy['sourceUrls'])
            self.assertEqual(['https://pasarela-ident.clave.gob.es'], policy['requestOrigins'])
            self.assertEqual('/IdP2/AuthenticateCitizen', policy['requestPath'])
            self.assertEqual(443, policy['requestPort'])
            self.assertEqual(15, policy['grantTtlSeconds'])
        self.assertEqual(['CLIENT_TLS_AUTH'], profiles['diputacion-pontevedra-instancia-xenerica']['capabilities'])
        self.assertEqual([], profiles['diputacion-pontevedra-instancia-xenerica']['operationPolicies'])
        entries = {e['portalId']: e for e in json.loads(self.read(CATALOG))['entries']}
        self.assertEqual(entries['extremadura-sede-anterior']['profileId'], entries['extremadura-tramites']['profileId'])

    def test_gestiona_recipe_submits_only_the_reviewed_clave_form(self) -> None:
        source = self.read(INSTRUMENTATION)
        block = source.split('in GESTIONA_CLAVE_ORIGINS -> {', 1)[1].split('\n            }\n', 1)[0]
        self.assertIn('clickGestionaClaveLogin(scenario, GESTIONA_CLAVE_ORIGINS.getValue(portalId))', block)
        self.assertTrue(block.rstrip().endswith('clickClaveAfirmaProvider(scenario)'))
        self.assertIn('GESTIONA_CLAVE_LABEL = "Acceso con sistema Cl@ve"', source)
        self.assertIn('"diputacion-avila-portal" to "https://diputacionavila.sedelectronica.es"', source)
        for portal, origin in (
            ("diputacion-alicante-portal", "https://diputacionalicante.sedelectronica.es"),
            ("diputacion-castellon-portal", "https://dipcas.sedelectronica.es"),
            ("lanzarote-portal-institucional", "https://lanzaroteylagraciosa.sedelectronica.es"),
            ("diputacion-teruel-sede", "https://dpteruel.sedelectronica.es"),
            ("diputacion-guadalajara-sede", "https://dguadalajara.sedelectronica.es"),
            ("age-consejo-de-transparencia-y-buen-gobierno-ctbg", "https://sede.consejodetransparencia.gob.es"),
        ):
            self.assertIn(f'"{portal}" to "{origin}"', source)
        helper = source[source.index('private fun clickGestionaClaveLogin('):]
        helper = helper[:helper.index('private fun clickExactLabeledAnchor(')]
        for expected in (
            'form[name="claveForm"]',
            'CLAVE_SERVICE_PROVIDER_URL',
            'input[type="hidden"][name="SAMLRequest"]',
            "onclick') === 'submit();'",
            'GESTIONA_CLAVE_LABEL',
        ):
            self.assertIn(expected, helper)
        self.assertNotIn('loadUrl(', helper)

    def test_rec_proves_only_the_clave_login_for_every_alias_and_never_files(self) -> None:
        source = self.read(INSTRUMENTATION)
        dispatch = source.split('if (profileId == REG_AGE_PROFILE_ID) {', 1)[1].split('\n        }\n', 1)[0]
        self.assertEqual(
            [line.strip() for line in dispatch.strip().splitlines()],
            ['clickRecClaveLogin(scenario)', 'clickClaveAfirmaProvider(scenario)', 'return'],
        )
        self.assertIn('applyPortalRecipe(scenario, portalId, profile.profileId.value)', source)
        self.assertIn('REC_HOME_URL = "https://reg.redsara.es/es/"', source)
        self.assertIn('REC_LOGIN_LABEL = "Iniciar sesión"', source)
        helper = source[source.index('private fun clickRecClaveLogin('):]
        helper = helper[:helper.index('private fun clickExactAuthButton(')]
        for expected in (
            "document.querySelectorAll('dnt-header-item')",
            "element.getAttribute('text') === $quotedLabel",
            'items.length !== 1',
            "items[0].getAttribute('icon') !== 'Login'",
            "items[0].closest('dnt-modal')",
            'items[0].click()',
            "document.readyState !== 'complete'",
            'hasObservedTerminalNavigationFailure()',
        ):
            self.assertIn(expected, helper)
        for forbidden in ('loadUrl(', '.submit(', 'claveForm', 'SAMLRequest'):
            self.assertNotIn(forbidden, helper)
        conseq = source.split('val CONSEQ_RECIPE_PROFILES = setOf(', 1)[1].split(')', 1)[0]
        self.assertIn('"reg-age-redsara"', conseq)

    def test_tenerife_and_caib_prove_only_the_clave_login_and_never_file(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('CAIB_SEU_PORTAL_ID, CAIB_REGISTRE_PORTAL_ID -> clickClaveAfirmaProvider(scenario)', source)
        self.assertIn('TENERIFE_SEDE_PORTAL_ID, TENERIFE_INSTITUCIONAL_PORTAL_ID -> {', source)
        self.assertIn(
            'TENERIFE_ACCESS_URL = "https://sede.tenerife.es/es/acceso?returnBack=personal%2Fvista-general"',
            source,
        )
        self.assertIn('expectedType = "submit"', source)
        self.assertIn("if (element.getAttribute('type') !== $quotedExpectedType) return false;", source)
        conseq = source.split('val CONSEQ_RECIPE_PROFILES = setOf(', 1)[1].split(')', 1)[0]
        for profile in (
            '"tenerife-sede-electronica"',
            '"caib-portafib"',
            '"diputacion-burgos-portal"',
            '"diputacion-huesca-portal"',
            '"airef-instancia-general"',
            '"jccm-registro-generico"',
        ):
            self.assertIn(profile, conseq)
            # Consequential signing must never be driven automatically for these profiles.
            safe = source.split('val SAFE_AUTH_SIGN_PROFILES = setOf(', 1)[1].split(')', 1)[0]
            self.assertNotIn(profile, safe)
        self.assertIn('(Capability.CLIENT_TLS_AUTH !in capabilities || result.clientCertProceeded)', source)

    def test_unizar_recipe_targets_the_certificate_button_inside_its_container(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('UNIZAR_PORTAL_ID = "unizar-tramitador"', source)
        self.assertIn(
            '"https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent"',
            source,
        )
        self.assertIn('UNIZAR_AUTH_CONTAINER_ID = "capaAccesoCertificado"', source)
        self.assertIn('UNIZAR_AUTH_ELEMENT_ID = "acEntrar"', source)
        self.assertIn(
            'const val UNIZAR_AUTH_LABEL =\n            "Pulse para ejecutar Autofirma e identificarse con certificado."',
            source,
        )
        self.assertIn('UNIZAR_AUTH_IMAGE_ALT = "certificado login"', source)
        self.assertIn('UNIZAR_AUTH_ONCLICK = "lanza();"', source)
        self.assertIn('UNIZAR_PORTAL_ID -> clickExactContainedButton(', source)
        self.assertIn("container.querySelectorAll('button')", source)
        self.assertIn("element.getAttribute('aria-label') ?? element.getAttribute('title')", source)
        self.assertIn("label !== $quotedExpectedLabel", source)
        self.assertIn("element.querySelector('img')?.getAttribute('alt') !== $quotedExpectedImageAlt", source)
        self.assertIn("elements.length !== 1", source)
        self.assertIn('element.click()', source)
        helper = source[source.index('private fun clickExactContainedButton('):]
        helper = helper[:helper.index('private fun clickExactAuthButton(')]
        self.assertIn('var targetMismatchObserved = false', helper)
        self.assertIn('"2" -> targetMismatchObserved = true', helper)
        self.assertIn('if (targetMismatchObserved)', helper)
        self.assertIn('"REAL_E2E_RECIPE_TARGET_MISMATCH"', helper)

    def test_auth_sign_waits_for_bounded_post_sign_observation(self) -> None:
        source = self.read(INSTRUMENTATION)
        report = self.read(REPORT_HELPER)
        self.assertIn('const val POST_SIGN_TIMEOUT_MILLIS = 30_000L', source)
        for field in (
            "postSignNavigationObserved",
            "postSignPageFinished",
            "postSignCallbackObserved",
            "postSignHostChanged",
            "postSignPathChanged",
            "authenticatedReturnObserved",
            "signingCallbackObserved",
            "postSignPortalAuthSuccess",
        ):
            self.assertIn(field, source)
            self.assertIn(field, report)
        observation = source[source.index("private fun isObservationComplete("):]
        observation = observation[:observation.index("private fun classify(")]
        self.assertIn("postSignObservationDeadline", observation)
        self.assertLess(
            observation.index("postSignObservationDeadline ?: return false"),
            observation.index("return result.pageFinished"),
        )
        self.assertIn("PASS_REAL_CRYPTO_SIGN", source)
        self.assertIn('"PASS_REAL_CRYPTO_SIGN"', report)
        self.assertIn("const val RESULT_SCHEMA_VERSION = 2", source)
        self.assertIn("RESULT_SCHEMA_VERSION = 2", report)
        self.assertIn("result.postSignNavigationObserved", source)
        self.assertIn("result.authenticatedReturnObserved", source)
        self.assertIn("result.signingCallbackObserved", source)
        self.assertIn("result.postSignPortalAuthSuccess", source)
        delta = source[source.index("private fun recordsAddedSince("):]
        delta = delta[:delta.index("private fun updateRecordObservations(")]
        self.assertIn("current.take(previous.size) == previous", delta)
        self.assertIn("current.drop(previous.size)", delta)
        signing_wait = source[source.index("private fun waitForSigningTerminalState("):]
        signing_wait = signing_wait[:signing_wait.index("private fun updateSigningEvidence(")]
        self.assertNotIn("updatePostSignObservations", signing_wait)
        self.assertIn("updateSigningEvidence", signing_wait)
        self.assertIn("return diagnosticRecords()", signing_wait)
        self.assertIn(
            "updateSigningEvidence(completedRecords, signingEvidenceTracker, result)",
            source,
        )
        sign_branch = source[source.index("if (deepEnabled && profileId in SAFE_AUTH_SIGN_PROFILES)") :]
        sign_branch = sign_branch[:sign_branch.index("} else {")]
        self.assertLess(
            sign_branch.index("val completedRecords = waitForSigningTerminalState("),
            sign_branch.index("val tracker = PostSignTracker("),
        )
        self.assertIn("latestMainFrameNavigation(completedRecords)", sign_branch)
        classify = source[source.index("private fun classify("):]
        classify = classify[:classify.index("private fun allowedClientAuthHosts(")]
        self.assertIn("result.postSignPortalAuthSuccess", classify)
        self.assertNotIn("(result.portalAuthSuccess || result.authenticatedReturnObserved)", classify)
        self.assertIn("result.signingCallbackObserved", classify)
        navigation_regex = source[source.index("val SANITIZED_NAVIGATION_EVENT"):]
        navigation_regex = navigation_regex[:navigation_regex.index("val SAFE_AUTH_SIGN_PROFILES")]
        self.assertNotIn("NETWORK_REQUEST", navigation_regex)

    def test_safe_auth_sign_avoids_compose_idle_for_webview_signing(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn("private fun signingCoordinatorForScenario(", source)
        self.assertIn("application().sanitizedLogger.snapshot()", source)
        self.assertIn("var signingCoordinator = if (safeAuthSigning)", source)
        self.assertIn("signingCoordinatorRefreshed", source)
        self.assertIn('it.contains("stage=PREPARE_READY")', source)
        self.assertIn("private fun clickSigningConfirmation(", source)
        self.assertIn("hasAnyDescendant(", source)
        self.assertIn("hasClickAction()", source)
        self.assertIn("hasAnyAncestor(isDialog())", source)
        self.assertIn("private fun performSigningConfirmationAction(", source)
        self.assertIn("AtomicBoolean(false)", source)
        self.assertIn("callbackHandled.set(onClick())", source)
        self.assertIn("callbackHandled.get()", source)
        self.assertIn("val exactTextButton", source)
        self.assertIn("hasText(\"Firmar\", substring = false, ignoreCase = false)", source)
        self.assertIn("useUnmergedTree = true", source)
        self.assertIn("useUnmergedTree = false", source)
        self.assertNotIn("val exactClickableButton", source)
        observation = source[source.index("private fun observePortal("):]
        observation = observation[:observation.index("private fun waitForSigningTerminalState(")]
        self.assertIn("signingCoordinatorForScenario(scenario)", observation)
        self.assertIn("signingCoordinator?.state?.value", observation)
        self.assertIn("clickSigningConfirmation()", observation)
        self.assertIn("if (!safeAuthSigning)", observation)
        signing_gate = observation[observation.index("val signingConfirmationVisible"):]
        signing_gate = signing_gate[:signing_gate.index("if (Capability.SIGN in profileCapabilities")]
        self.assertIn("if (safeAuthSigning)", signing_gate)
        self.assertIn("signingCoordinator?.state?.value", signing_gate)
        signing_wait = source[source.index("private fun waitForSigningTerminalState("):]
        signing_wait = signing_wait[:signing_wait.index("private fun signingCoordinatorForScenario(")]
        self.assertIn("signingCoordinator.state.value", signing_wait)

    def test_instrumentation_reflection_targets_exist_in_app_sources(self) -> None:
        source = self.read(INSTRUMENTATION)
        main_activity = self.read(ROOT / "app/src/main/java/dev/junta/firmamobile/MainActivity.kt")
        orchestrator = self.read(ROOT / "app/src/main/java/dev/junta/firmamobile/SigningOrchestrator.kt")
        reflected = set(re.findall(r'MainActivity::class\.java\.getDeclaredField\("([A-Za-z_]+)"\)', source))
        self.assertIn("currentWebView", reflected)
        self.assertIn("signing", reflected)
        for name in sorted(reflected):
            self.assertRegex(main_activity, rf"private (?:var|val) {name}\b", name)
        self.assertIn("private var signing: SigningOrchestrator?", main_activity)
        self.assertIn("private val signingCoordinator: SigningCoordinator,", orchestrator)
        helper = source[source.index("private fun signingCoordinatorForScenario("):]
        helper = helper[:helper.index("private fun clickSigningConfirmation(")]
        self.assertIn('getDeclaredField("signing")', helper)
        self.assertIn('orchestrator.javaClass.getDeclaredField("signingCoordinator")', helper)
        self.assertIn("SIGNING_OWNER_TIMEOUT_MILLIS", helper)
        self.assertIn('"REAL_E2E_SIGNING_OWNER_MISSING"', helper)
        self.assertIn('"REAL_E2E_SIGNING_OWNER_MISSING" -> "SIGNING_OWNER_MISSING"', source)

    def test_client_auth_prompt_is_detected_by_dialog_test_tags(self) -> None:
        source = self.read(INSTRUMENTATION)
        observation = source[source.index("private fun observePortal("):]
        observation = observation[:observation.index("private fun waitForSigningTerminalState(")]
        self.assertIn("taggedNodeVisible(CLIENT_AUTH_DIALOG_TAG)", observation)
        self.assertIn("taggedNodeVisible(INTERACTIVE_CLIENT_AUTH_DIALOG_TAG)", observation)
        self.assertIn('clientAuthDialogTextVisible("Dominio: $allowedHost")', observation)
        self.assertIn("clickTaggedNode(CLIENT_AUTH_CANCEL_TAG)", observation)
        self.assertIn("clickTaggedNode(INTERACTIVE_CLIENT_AUTH_CANCEL_TAG)", observation)
        self.assertIn("hasTestTag(CLIENT_AUTH_CONTINUE_TAG) and isEnabled()", observation)
        self.assertIn("clickTaggedNode(CLIENT_AUTH_CONTINUE_TAG)", observation)
        # The browser chrome trust label shares the dialog title, so visible text alone is ambiguous.
        self.assertNotIn('onAllNodesWithText("Acceso con certificado")', source)
        self.assertNotIn('onAllNodesWithText("Dominio: ', source)
        client_auth = observation[observation.index("if (Capability.CLIENT_TLS_AUTH in profileCapabilities &&"):]
        client_auth = client_auth[:client_auth.index("if (Capability.SELECT_CERTIFICATE in profileCapabilities")]
        self.assertNotIn('"Continuar"', client_auth)
        self.assertNotIn('"Cancelar"', client_auth)
        helper = source[source.index("private fun taggedNodeVisible("):]
        helper = helper[:helper.index("private fun clickSigningConfirmation(")]
        self.assertIn("hasAnyAncestor(hasTestTag(CLIENT_AUTH_DIALOG_TAG))", helper)
        self.assertNotIn("isDialog()", helper)
        self.assertIn("useUnmergedTree = true", helper)
        self.assertIn("useUnmergedTree = false", helper)
        dialogs = self.read(ROOT / "app/src/main/java/dev/junta/firmamobile/ui/BrowserConfirmationDialogs.kt")
        interactive = self.read(ROOT / "app/src/main/java/dev/junta/firmamobile/ui/InteractiveClientAuthDialog.kt")
        for tag in ("client-auth-confirmation", "client-auth-confirmation-continue", "client-auth-confirmation-cancel"):
            self.assertIn(f'Modifier.testTag("{tag}")', dialogs)
            self.assertIn(f'= "{tag}"', source)
        for tag in ("interactive-client-auth", "interactive-client-auth-cancel"):
            self.assertIn(f'Modifier.testTag("{tag}")', interactive)
            self.assertIn(f'= "{tag}"', source)
        strings = self.read(ROOT / "app/src/main/res/values/strings.xml")
        self.assertIn('<string name="certificate_selection_domain">Dominio: %1$s</string>', strings)

    def test_infrastructure_codes_unwrap_main_thread_wrappers(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn("safeInfrastructureCodeFor(unwrapMainThreadFailure(throwable), stage)", source)
        unwrap = source[source.index("private fun unwrapMainThreadFailure("):]
        unwrap = unwrap[:unwrap.index("private fun safeInfrastructureCodeFor(")]
        self.assertIn("current.javaClass == RuntimeException::class.java", unwrap)
        self.assertIn("MAX_FAILURE_UNWRAP_DEPTH", unwrap)
        self.assertIn("if (cause === current) break", unwrap)

    def test_unizar_recipe_waits_for_the_script_bound_certificate_handler(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source[source.index("private fun clickExactContainedButton("):]
        helper = helper[:helper.index("internal fun clickExactButton(")]
        self.assertIn("require(JS_IDENTIFIER.matches(expectedHandlerName))", helper)
        self.assertIn("(inlineOnClick !== null && inlineOnClick !== $quotedExpectedOnClick)", helper)
        self.assertIn("typeof $expectedHandlerName === 'function'", helper)
        self.assertIn("if (!handlerReady) return 0;", helper)
        self.assertIn("expectedHandlerName = UNIZAR_AUTH_HANDLER", source)
        self.assertIn('UNIZAR_AUTH_HANDLER = "lanza"', source)

    def test_runner_allows_the_post_sign_window_to_finish(self) -> None:
        runner = self.read(RUNNER)
        self.assertIn("readonly PORTAL_TIMEOUT_SECONDS=210", runner)
        self.assertIn(
            "75s portal observation + 90s signing + 30s post-sign observation + 15s margin",
            runner,
        )

    def test_real_e2e_waits_for_owned_webview_via_catalog_inspect(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('catalogSmoke(portalId, "INSPECT").contains("WEBVIEW_ACTIVE")', source)
        self.assertIn('updateCurrentHostFromRecords(records, result)', source)
        self.assertIn('SANITIZED_HOST', source)
        self.assertNotIn('activity.window.decorView', source)
        self.assertNotIn('findWebView(', source)
        self.assertNotIn('updateCurrentWebView(', source)

    def test_instrumentation_requires_explicit_opt_in_and_has_no_level_six(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('arguments.getString(REAL_E2E_ARGUMENT) == "true"', source)
        self.assertIn('arguments.getString(PORTAL_ID_ARGUMENT)', source)
        self.assertIn("ProbeClassification", source)
        self.assertNotIn("level = 6", source)
        self.assertNotIn("maxOf(result.level, 6)", source)
        self.assertIn("signingCancelledAtBoundary", source)

    def test_consequential_portals_are_classified_without_deep_automation(self) -> None:
        source = self.read(INSTRUMENTATION)
        report = self.read(REPORT_HELPER)
        self.assertIn('val CONSEQ_RECIPE_PROFILES = setOf(', source)
        self.assertIn('"reg-age-redsara"', source)
        self.assertIn('ProbeClassification.BLOCKED_CONSEQUENTIAL_ACTION', source)
        self.assertIn('result.profileId in CONSEQ_RECIPE_PROFILES', source)
        self.assertIn('"BLOCKED_CONSEQUENTIAL_ACTION"', report)

    def test_administrative_signing_profiles_are_not_deep_sign_allowlisted(self) -> None:
        source = self.read(INSTRUMENTATION)
        allowlist = source.split("val SAFE_AUTH_SIGN_PROFILES = setOf(", 1)[1].split(")", 1)[0]
        for forbidden in (
            "reg-age-redsara",
            "jccm-registro-generico",
            "airef-instancia-general",
            "ministerio-economia-instancia-generica",
            "xunta-galicia-solicitude-xenerica",
            "age-portal-de-la-transparencia",
            "caib-portafib",
        ):
            self.assertNotIn(f'"{forbidden}"', allowlist)
        for expected in (
            "junta-andalucia",
            "unizar-tramitador",
            "junta-ofvirtual",
            "aragon-siraw",
        ):
            self.assertIn(f'"{expected}"', allowlist)

    def test_report_helper_selects_exactly_the_current_catalog(self) -> None:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        expected = [entry["portalId"] for entry in catalog["entries"]]
        completed = self.helper("select", "--catalog", str(CATALOG))
        self.assertEqual(0, completed.returncode, completed.stderr)
        actual = completed.stdout.splitlines()
        self.assertEqual(expected, actual)
        self.assertEqual(183, len(actual))

    def test_report_helper_single_portal_filter_is_exact(self) -> None:
        selected = "junta-andalucia-carne-joven"
        completed = self.helper("select", "--catalog", str(CATALOG), "--portal", selected)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual([selected], completed.stdout.splitlines())
        rejected = self.helper("select", "--catalog", str(CATALOG), "--portal", "not-a-real-portal")
        self.assertNotEqual(0, rejected.returncode)

    def test_report_helper_batch_portal_filter_is_exact_and_ordered(self) -> None:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        selected = [
            catalog["entries"][5]["portalId"],
            catalog["entries"][1]["portalId"],
            catalog["entries"][9]["portalId"],
        ]
        completed = self.helper(
            "select", "--catalog", str(CATALOG), "--portal", ",".join(selected)
        )
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual(selected, completed.stdout.splitlines())

        for rejected_filter in (
            f"{selected[0]},{selected[0]}",
            f"{selected[0]},",
            f"{selected[0]},not-a-real-portal",
            f"{selected[0]}, {selected[1]}",
        ):
            rejected = self.helper(
                "select", "--catalog", str(CATALOG), "--portal", rejected_filter
            )
            self.assertNotEqual(0, rejected.returncode)

        exact_32 = [entry["portalId"] for entry in catalog["entries"][:32]]
        accepted_32 = self.helper(
            "select", "--catalog", str(CATALOG), "--portal", ",".join(exact_32)
        )
        self.assertEqual(0, accepted_32.returncode, accepted_32.stderr)
        self.assertEqual(exact_32, accepted_32.stdout.splitlines())

        rejected_33 = self.helper(
            "select",
            "--catalog",
            str(CATALOG),
            "--portal",
            ",".join(exact_32 + [catalog["entries"][32]["portalId"]]),
        )
        self.assertNotEqual(0, rejected_33.returncode)

        rejected_multi_shard = self.helper(
            "select",
            "--catalog",
            str(CATALOG),
            "--portal",
            ",".join(selected),
            "--shard-index",
            "0",
            "--shard-total",
            "6",
        )
        self.assertNotEqual(0, rejected_multi_shard.returncode)

    def test_progress_validator_accepts_only_sanitized_checkpoint_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "progress.tsv"
            path.write_text(
                "2026-08-31T22:00:00Z\t0\t1/31\tjunta-andalucia-carne-joven\tPORTAL_START\t42\n"
                "2026-08-31T22:00:01Z\t0\t1/31\tjunta-andalucia-carne-joven\tINSTRUMENT_DONE_0\t43\n",
                encoding="ascii",
            )
            accepted = self.helper("validate-progress", "--progress", str(path))
            self.assertEqual(0, accepted.returncode, accepted.stderr)
            path.write_text(
                "2026-08-31T22:00:00Z\t0\t1/31\tjunta-andalucia-carne-joven\tPASSWORD=my-secret\t42\n",
                encoding="ascii",
            )
            rejected = self.helper("validate-progress", "--progress", str(path))
            self.assertNotEqual(0, rejected.returncode)

    def test_progress_validator_accepts_emulator_recovery_checkpoints(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "progress.tsv"
            path.write_text(
                "2026-11-05T21:00:00Z\t0\t1/31\tjunta-andalucia-carne-joven\tINSTRUMENT_DONE_255\t90\n"
                "2026-11-05T21:00:01Z\t0\t1/31\tjunta-andalucia-carne-joven\tDEVICE_LOST\t91\n"
                "2026-11-05T21:00:02Z\t0\t1/31\tjunta-andalucia-carne-joven\tEMULATOR_RESTART_START\t92\n"
                "2026-11-05T21:01:30Z\t0\t1/31\tjunta-andalucia-carne-joven\tEMULATOR_RESTART_DONE\t180\n"
                "2026-11-05T21:09:30Z\t0\t9/31\tjunta-andalucia-sede\tEMULATOR_RESTART_FAILED\t660\n",
                encoding="ascii",
            )
            accepted = self.helper("validate-progress", "--progress", str(path))
            self.assertEqual(0, accepted.returncode, accepted.stderr)

    def test_synthetic_result_records_a_lost_emulator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            synthetic = self.helper(
                "synthetic", "--catalog", str(CATALOG),
                "--portal", "junta-andalucia-carne-joven",
                "--output", str(path), "--reason", "EMULATOR_LOST",
            )
            self.assertEqual(0, synthetic.returncode, synthetic.stderr)
            data = json.loads(path.read_text(encoding="ascii"))
            self.assertEqual("INFRASTRUCTURE_ERROR", data["classification"])
            self.assertEqual("EMULATOR_LOST", data["infrastructureError"])
            rejected = self.helper(
                "synthetic", "--catalog", str(CATALOG),
                "--portal", "junta-andalucia-carne-joven",
                "--output", str(path), "--reason", "ANYTHING_ELSE",
            )
            self.assertNotEqual(0, rejected.returncode)

    def test_runner_recovers_a_lost_emulator_without_restaging_credentials(self) -> None:
        workflow = self.read(WORKFLOW)
        runner = self.read(RUNNER)
        self.assertIn(
            "REAL_E2E_EMULATOR_OPTIONS: -no-window -gpu swangle -no-snapshot "
            "-noaudio -no-boot-anim -feature -Vulkan",
            workflow,
        )
        self.assertIn("emulator-options: ${{ env.REAL_E2E_EMULATOR_OPTIONS }}", workflow)
        self.assertIn("readonly EMULATOR_RESTART_LIMIT=4", runner)
        self.assertIn('if ((status != 0)) && ! device_online; then', runner)
        self.assertIn('report_device_loss "$portal_id"', runner)
        self.assertIn('write_synthetic_result "$portal_id" "$result_file" EMULATOR_LOST', runner)
        self.assertIn('if restart_emulator "${#portal_ids[@]}"; then', runner)
        self.assertIn('prepare_device "$total" || return 1', runner)
        self.assertIn("fixture_present", runner)
        restart = runner[runner.index("restart_emulator() {"):runner.index("write_synthetic_result() {")]
        self.assertNotIn("REAL_E2E_CERT", restart)
        self.assertNotIn("stage_fixture", restart)
        loss = runner[runner.index("report_device_loss() {"):runner.index("fixture_present() {")]
        self.assertNotIn("logcat", loss)
        self.assertNotIn("run-as", loss)

    def test_navigation_validator_accepts_sanitized_mini_applet_bridge_events(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "navigation.log"
            path.write_text(
                "timestamp=2026-09-01T19:38:25Z "
                "event=MINIAPPLET_BRIDGE origin=sede.dip-badajoz.es "
                "stage=SHIM_SCRIPT_INSTALLED algorithm=invalid format=invalid\n"
                "timestamp=2026-09-01T19:38:26Z "
                "event=MINIAPPLET_BRIDGE origin=sede.dip-badajoz.es "
                "stage=REJECTED algorithm=invalid format=invalid "
                "error=UNSUPPORTED_PROTOCOL\n",
                encoding="ascii",
            )
            accepted = self.helper("validate-log", "--log", str(path))
            self.assertEqual(0, accepted.returncode, accepted.stderr)

            path.write_text(
                "timestamp=2026-09-01T19:38:25Z event=UNSAFE_EVENT\n",
                encoding="ascii",
            )
            rejected = self.helper("validate-log", "--log", str(path))
            self.assertNotEqual(0, rejected.returncode)

    def test_sta_responsive_identify_uses_visible_contract_not_inner_text(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('private fun clickExactStaResponsiveIdentifyAnchor(', source)
        self.assertIn("querySelectorAll(':scope > span.a-text')", source)
        self.assertIn("(labels[0].textContent || '').trim().replace(/\\s+/g, ' ')", source)
        self.assertIn("element.classList.contains('responsive')", source)
        self.assertIn("element.classList.contains('ui-link')", source)
        self.assertIn("element.classList.contains('tamano-defecto')", source)
        self.assertIn("element.classList.contains('iconed')", source)
        self.assertIn("element.getAttribute(\'onclick\') === $quotedExpectedOnClick", source)
        self.assertIn('STA_IDENTIFY_ONCLICK =', source)
        helper = source[source.index('private fun clickExactStaResponsiveIdentifyAnchor'):]
        helper = helper[:helper.index('private fun clickExactAnchor')]
        self.assertNotIn('element.innerText', helper)
        self.assertNotIn('window.location.assign(', helper)
        self.assertNotIn('form.submit()', helper)

    def test_badajoz_hook_diagnostics_are_sanitized_and_bounded(self) -> None:
        source = "".join(self.read(ROOT / "app/src/main/res/raw" / name) for name, _, _ in RESOURCE_RANGES)
        self.assertIn('postShimDiagnostic("BADAJOZ_LATE_REWRAP_STARTED")', source)
        self.assertIn('postShimDiagnostic("BADAJOZ_SIGN_HOOK_READY")', source)
        self.assertIn('window.__jfmBadajozSignHookReady = true', source)
        self.assertIn('postBadajozDiagnosticOnce("BADAJOZ_CERT_BUTTON_CLICK")', source)
        for stage in (
            "BADAJOZ_PULSAR_SIGN_ENTRY",
            "BADAJOZ_FIRMAR_ENTRY",
            "BADAJOZ_GET_BASE64_ENTRY",
            "BADAJOZ_ECHO_ENTRY",
            "BADAJOZ_FORCE_WS_ENTRY",
        ):
            self.assertIn(f'"{stage}"', source)
        self.assertIn('window.setInterval(repairCurrentSigningLibraries, 500)', source)
        self.assertIn('window.clearInterval(hookGuardTimer)', source)
        self.assertIn('document.visibilityState === "hidden"', source)
        self.assertIn('window.addEventListener("pagehide", () => { documentActive = false; stopHookGuard(); })', source)
        self.assertIn('window.addEventListener("pageshow", () => { documentActive = true; startHookGuard(); })', source)
        badajoz = self.read(ROOT / "app/src/main/res/raw/afirma_shim_badajoz.js")
        self.assertNotIn('certificate', badajoz[badajoz.index('BADAJOZ_LATE_REWRAP_STARTED'):])

    def test_badajoz_recipe_waits_in_webview_for_the_ready_sign_hook(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('private fun waitForBadajozEntryPageReady(', source)
        self.assertIn('BADAJOZ_PAGE_READY_TIMEOUT_MILLIS = 60_000L', source)
        self.assertIn("document.readyState === 'complete'", source)
        self.assertIn('REAL_E2E_RECIPE_PAGE_READY_TIMEOUT', source)
        self.assertIn('window.__jfmBadajozSignHookReady !== true', source)
        self.assertIn('typeof window.MiniApplet?.sign !== \'function\'', source)
        self.assertIn('"3" -> waitingForBadajozSignHook = true', source)
        self.assertIn('performSemanticsAction(SemanticsActions.OnClick)', source)
        self.assertIn('hasText("Firmar", substring = false, ignoreCase = false)', source)
        self.assertIn('callbackHandled.set(onClick())', source)
        self.assertIn('callbackHandled.get()', source)

    def test_select_emits_no_blank_record_for_empty_filtered_shard(self) -> None:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        portal = catalog["entries"][0]["portalId"]
        populated = self.helper(
            "select", "--catalog", str(CATALOG), "--portal", portal,
            "--shard-index", "0", "--shard-total", "6",
        )
        self.assertEqual(0, populated.returncode, populated.stderr)
        self.assertEqual(portal + "\n", populated.stdout)
        empty = self.helper(
            "select", "--catalog", str(CATALOG), "--portal", portal,
            "--shard-index", "5", "--shard-total", "6",
        )
        self.assertEqual(0, empty.returncode, empty.stderr)
        self.assertEqual("", empty.stdout)

    def test_describe_result_emits_only_validated_safe_tokens(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            synthetic = self.helper(
                "synthetic", "--catalog", str(CATALOG),
                "--portal", "junta-andalucia-carne-joven",
                "--output", str(path), "--reason", "RESULT_MISSING",
            )
            self.assertEqual(0, synthetic.returncode, synthetic.stderr)
            data = json.loads(path.read_text(encoding="ascii"))
            data["infrastructureError"] = "AssertionError"
            path.write_text(json.dumps(data), encoding="ascii")
            described = self.helper(
                "describe-result", "--result", str(path),
                "--portal", "junta-andalucia-carne-joven",
            )
            self.assertEqual(0, described.returncode, described.stderr)
            self.assertEqual(
                "RESULT_DIAGNOSTIC portal=junta-andalucia-carne-joven "
                "classification=INFRASTRUCTURE_ERROR level=0 "
                "infrastructure=AssertionError signing=NONE",
                described.stdout.strip(),
            )

    def test_report_helper_rejects_secret_like_extra_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            synthetic = self.helper(
                "synthetic",
                "--catalog",
                str(CATALOG),
                "--portal",
                "junta-andalucia-carne-joven",
                "--output",
                str(path),
                "--reason",
                "RESULT_MISSING",
            )
            self.assertEqual(0, synthetic.returncode, synthetic.stderr)
            data = json.loads(path.read_text(encoding="ascii"))
            data["certificatePassword"] = "must-never-export"
            path.write_text(json.dumps(data), encoding="ascii")
            validated = self.helper(
                "validate-result",
                "--result",
                str(path),
                "--portal",
                "junta-andalucia-carne-joven",
            )
            self.assertNotEqual(0, validated.returncode)


    def test_runner_summarizes_instrumentation_and_guest_crashes_without_uploading_raw_text(self) -> None:
        workflow = self.read(WORKFLOW)
        runner = self.read(RUNNER)
        self.assertIn('readonly INSTRUMENT_OUTPUT="${RUNNER_TEMP:-/tmp}/real-e2e-instrument.out"', runner)
        self.assertIn('readonly GUEST_LOG="${RUNNER_TEMP:-/tmp}/real-e2e-guest.log"', runner)
        self.assertIn('        >"$INSTRUMENT_OUTPUT" 2>&1\n    status=$?', runner)
        self.assertIn('"$REPORT_HELPER" describe-instrumentation \\', runner)
        self.assertIn('rm -f "$INSTRUMENT_OUTPUT"\n', runner)
        self.assertIn('describe_guest_crashes "$portal_id" "$guest_since"', runner)
        self.assertIn('"$REPORT_HELPER" describe-guest-log --log "$GUEST_LOG" --portal "$portal_id" || true', runner)
        self.assertIn('rm -f "$GUEST_LOG"\n', runner)
        self.assertIn('rm -f "$INSTRUMENT_OUTPUT" "$GUEST_LOG" || true', runner)
        self.assertIn("REAL_E2E_NAVIGATION_LOG_DISCARDED", runner)
        self.assertNotIn("RUNNER_TEMP", workflow)
        self.assertNotIn("real-e2e-instrument.out", workflow)
        self.assertNotIn("real-e2e-guest.log", workflow)
        guest = runner[runner.index("describe_guest_crashes() {"):runner.index("main() {")]
        self.assertNotIn("run-as", guest)
        self.assertNotIn("REAL_E2E_CERT", guest)

    def test_runner_reports_host_emulator_crash_evidence(self) -> None:
        runner = self.read(RUNNER)
        loss = runner[runner.index("report_device_loss() {"):runner.index("fixture_present() {")]
        self.assertIn("REAL_E2E_HOST_MINIDUMPS count=", loss)
        self.assertIn('"$REPORT_HELPER" describe-minidump --dump "$newest_dump" || true', loss)
        self.assertIn("REAL_E2E_EMULATOR_EXIT code=", loss)
        self.assertIn('printf \'%s\\n\' "$?" >"$EMULATOR_EXIT_FILE"', runner)
        self.assertIn(") </dev/null >/dev/null 2>&1 &", runner)

    def test_instrumentation_summary_never_echoes_messages_or_stack_text(self) -> None:
        secret = "Z9999999Z"
        output = "\n".join([
            "INSTRUMENTATION_STATUS_CODE: 1",
            f"INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: holder {secret} https://example.invalid/a",
            "\tat org.junit.Assert.fail(Assert.java:89)",
            f"Caused by: java.lang.IllegalStateException: {secret}",
            "INSTRUMENTATION_STATUS_CODE: -2",
            "INSTRUMENTATION_RESULT: stream=",
            "FAILURES!!!",
            "Tests run: 1,  Failures: 1",
            f"INSTRUMENTATION_RESULT: shortMsg=holder: https://example.invalid/{secret}",
            "INSTRUMENTATION_CODE: -1",
        ])
        with tempfile.TemporaryDirectory() as directory:
            failed = Path(directory) / "failed.out"
            failed.write_text(output, encoding="utf-8")
            crashed = Path(directory) / "crashed.out"
            crashed.write_text(
                "INSTRUMENTATION_STATUS_CODE: 1\n"
                "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n"
                "INSTRUMENTATION_CODE: 0\n",
                encoding="utf-8",
            )
            summary = self.helper(
                "describe-instrumentation", "--output", str(failed),
                "--portal", "unizar-tramitador", "--status", "0",
            )
            crash = self.helper(
                "describe-instrumentation", "--output", str(crashed),
                "--portal", "unizar-tramitador", "--status", "0",
            )
            missing = self.helper(
                "describe-instrumentation", "--output", str(Path(directory) / "missing.out"),
                "--portal", "unizar-tramitador", "--status", "124",
            )
        for completed in (summary, crash, missing):
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertNotIn(secret, completed.stdout)
            self.assertNotIn("https", completed.stdout)
            self.assertNotIn("holder", completed.stdout)
        self.assertIn("exception=java.lang.AssertionError", summary.stdout)
        self.assertIn("caused_by=java.lang.IllegalStateException", summary.stdout)
        self.assertIn("result=REDACTED tests=1 failures=1 status_codes=1,-2", summary.stdout)
        self.assertIn("code=0 result=Process_crashed.", crash.stdout)
        self.assertIn("status=124 bytes=0", missing.stdout)

    def test_guest_crash_summary_emits_only_signal_name_and_class_tokens(self) -> None:
        secret = "Z9999999Z"
        log = "\n".join([
            "--------- beginning of crash",
            "F/libc    ( 3420): Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 "
            "in tid 3456 (RenderThread), pid 3420 (unta.firmamobile)",
            "F/DEBUG   ( 3500): *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
            "F/DEBUG   ( 3500): pid: 3420, tid: 3456, name: RenderThread  >>> dev.junta.firmamobile <<<",
            f"F/DEBUG   ( 3500): Abort message: '[FATAL:gl_utils.cc(42)] Check failed: {secret}'",
            "F/DEBUG   ( 3500):       #00 pc 000000000005a2c8  /apex/com.android.runtime/lib64/bionic/libc.so "
            "(abort+168) (BuildId: abc)",
            "E/AndroidRuntime( 3420): FATAL EXCEPTION: main",
            "E/AndroidRuntime( 3420): Process: dev.junta.firmamobile, PID: 3420",
            f"E/AndroidRuntime( 3420): java.lang.RuntimeException: holder {secret} https://example.invalid",
            "E/AndroidRuntime( 3420): \tat android.app.ActivityThread.main(ActivityThread.java:1)",
            f"I/am_crash(  590): [0,3420,dev.junta.firmamobile,1,java.lang.RuntimeException,{secret},A.java,1,0]",
            "I/am_kill (  590): [0,4212,com.google.android.gms.persistent,915,empty #17]",
            "I/am_proc_died(  590): [0,3420,dev.junta.firmamobile,0,2]",
            "E/cr_AwBrowserTerminator( 3420): Render process's crash wasn't handled by all associated "
            "webviews, triggering application crash.",
        ])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "guest.log"
            path.write_text(log, encoding="utf-8")
            described = self.helper("describe-guest-log", "--log", str(path), "--portal", "unizar-tramitador")
        self.assertEqual(0, described.returncode, described.stderr)
        self.assertNotIn(secret, described.stdout)
        self.assertNotIn("https", described.stdout)
        self.assertNotIn("holder", described.stdout)
        self.assertNotIn("com.google.android.gms", described.stdout)
        self.assertIn(
            "kind=native signal=SIGSEGV(11) code=SEGV_MAPERR thread=RenderThread process=unta.firmamobile",
            described.stdout,
        )
        self.assertIn("kind=abort token=FATAL:gl_utils.cc(42)", described.stdout)
        self.assertIn("kind=frame frame=00 module=libc.so symbol=abort+168", described.stdout)
        self.assertIn(
            "kind=java thread=main process=dev.junta.firmamobile exception=java.lang.RuntimeException",
            described.stdout,
        )
        self.assertIn("kind=am_crash process=dev.junta.firmamobile exception=java.lang.RuntimeException", described.stdout)
        self.assertIn("kind=proc_died process=dev.junta.firmamobile", described.stdout)
        self.assertIn("kind=webview_renderer_gone_unhandled", described.stdout)
        for line in described.stdout.splitlines():
            self.assertTrue(line.startswith(("REAL_E2E_GUEST portal=", "REAL_E2E_GUEST_SUMMARY portal=")), line)

    def test_anr_dropbox_trace_emits_only_main_thread_code_locations(self) -> None:
        secret = "Z9999999Z"
        log = "\n".join([
            "I/am_anr  (  590): [0,3420,dev.junta.firmamobile,0,Input dispatching timed out]",
            "Drop box contents: 3 entries",
            "========================================",
            "2026-11-06 15:00:01 data_app_anr (text, 9000 bytes)",
            "Process: com.example.unrelated",
            '"main" prio=5 tid=1 Native',
            "  at com.example.Unrelated.stale(Unrelated.java:1)",
            "",
            "========================================",
            "2026-11-06 15:30:01 data_app_anr (text, 9000 bytes)",
            "Process: dev.junta.firmamobile",
            '"main" prio=5 tid=1 Native',
            "  at dev.junta.firmamobile.Old.previousRun(Old.kt:1)",
            "",
            "========================================",
            "2026-11-06 16:02:01 data_app_anr (text, 9000 bytes)",
            "Process: dev.junta.firmamobile",
            f"Subject: Input dispatching timed out {secret}",
            '"main" prio=5 tid=1 Runnable',
            f"  | group=\"main\" sCount=0 {secret}",
            "  at dev.junta.firmamobile.profile.SiteProfileCatalogParser.parse(SiteProfileCatalogParser.kt:330)",
            f"  - locked <0x0abc> (a java.lang.Object) {secret}",
            "  at dev.junta.firmamobile.App.onCreate$lambda$0(App.kt:12)",
            "",
            '"RenderThread" prio=7 tid=12 Native',
            "  at com.example.Other.secretWork(Other.java:1)",
        ])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "guest.log"
            path.write_text(log, encoding="utf-8")
            described = self.helper("describe-guest-log", "--log", str(path), "--portal", "unizar-tramitador")
        self.assertEqual(0, described.returncode, described.stderr)
        self.assertNotIn(secret, described.stdout)
        self.assertNotIn("Input dispatching", described.stdout)
        self.assertNotIn("Other.secretWork", described.stdout)
        self.assertNotIn("Unrelated.stale", described.stdout)
        self.assertNotIn("Old.previousRun", described.stdout)
        self.assertIn("kind=anr process=dev.junta.firmamobile", described.stdout)
        self.assertIn(
            "kind=anr_frame frame=00 symbol=dev.junta.firmamobile.profile.SiteProfileCatalogParser.parse",
            described.stdout,
        )
        self.assertIn("kind=anr_frame frame=01 symbol=dev.junta.firmamobile.App.onCreate$lambda$0", described.stdout)

    def test_runner_reads_anr_dropbox_only_after_an_observed_anr(self) -> None:
        runner = (ROOT / "scripts/ci/run-real-e2e.sh").read_text(encoding="utf-8")
        guest = runner[runner.index("describe_guest_crashes() {"):runner.index("main() {")]
        self.assertIn("if grep -q 'am_anr' \"$GUEST_LOG\"; then", guest)
        self.assertIn("dumpsys dropbox --print data_app_anr >>\"$GUEST_LOG\"", guest)

    def test_host_minidump_summary_reports_signal_and_module_basenames_only(self) -> None:
        import struct

        modules = [
            (0x400000, 0x10000, "/opt/sdk/emulator/qemu/linux-x86_64/qemu-system-x86_64"),
            (0x7F0000000000, 0x200000, "/opt/sdk/emulator/lib64/libOpenglRender.so"),
        ]
        blob = bytearray(32 + 12 * 4)
        system_info = len(blob)
        blob += struct.pack("<H", 9) + bytes(54)
        names = []
        for _base, _size, name in modules:
            encoded = name.encode("utf-16-le")
            names.append(len(blob))
            blob += struct.pack("<I", len(encoded)) + encoded + b"\0\0"
        module_list = bytearray(struct.pack("<I", len(modules)))
        for (base, size, _name), name_rva in zip(modules, names):
            module_list += struct.pack("<QIIII", base, size, 0, 0, name_rva) + bytes(108 - 24)
        module_rva = len(blob)
        blob += module_list
        context = bytearray(1232)
        struct.pack_into("<Q", context, 248, modules[1][0] + 0x123)
        context_rva = len(blob)
        blob += context
        stack = struct.pack("<QQ", 0xDEADBEEF, modules[0][0] + 0x40)
        stack_rva = len(blob)
        blob += stack
        exception_rva = len(blob)
        blob += struct.pack("<IIIIQQII", 77, 0, 11, 1, 0, 0, 0, 0) + bytes(120)
        blob += struct.pack("<II", len(context), context_rva)
        thread_rva = len(blob)
        blob += struct.pack("<I", 1) + struct.pack(
            "<IIIIQQIIII", 77, 0, 0, 0, 0, 0x7FFD0000, len(stack), stack_rva, len(context), context_rva
        )
        entries = [(7, 56, system_info), (4, len(module_list), module_rva), (6, 168, exception_rva), (3, 52, thread_rva)]
        struct.pack_into("<4sIIIIIQ", blob, 0, b"MDMP", 0xA793, len(entries), 32, 0, 0, 0)
        for index, (stream_type, size, rva) in enumerate(entries):
            struct.pack_into("<III", blob, 32 + 12 * index, stream_type, size, rva)
        with tempfile.TemporaryDirectory() as directory:
            dump = Path(directory) / "crash.dmp"
            dump.write_bytes(bytes(blob))
            garbage = Path(directory) / "garbage.dmp"
            garbage.write_bytes(b"not a dump")
            described = self.helper("describe-minidump", "--dump", str(dump))
            unreadable = self.helper("describe-minidump", "--dump", str(garbage))
        self.assertEqual(0, described.returncode, described.stderr)
        self.assertIn(
            "signal=SIGSEGV(11) code=1 pc_module=libOpenglRender.so stack_modules=qemu-system-x86_64",
            described.stdout,
        )
        self.assertNotIn("/opt/sdk", described.stdout)
        self.assertEqual(0, unreadable.returncode, unreadable.stderr)
        self.assertTrue(unreadable.stdout.startswith("REAL_E2E_HOST_MINIDUMP unreadable="))


if __name__ == "__main__":
    unittest.main()
