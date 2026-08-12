package dev.junta.firmamobile.browser

import android.net.Uri
import android.webkit.WebView

/**
 * Narrow compatibility patch for the legacy Junta Oficina Virtual pages.
 *
 * The portal renders Bootstrap collapse markup without loading the Bootstrap collapse JavaScript,
 * and one mobile menu label is already mojibaked in the server-produced DOM. This patch only runs
 * on the exact HTTPS ws072 /ofvirtual/ origin and does not read forms, cookies or signing payloads.
 */
internal object OfvirtualPageCompatibility {
    fun apply(webView: WebView, rawUrl: String) {
        if (!appliesTo(rawUrl)) return
        webView.evaluateJavascript(SCRIPT, null)
    }

    internal fun appliesTo(rawUrl: String): Boolean = runCatching {
        val uri = Uri.parse(rawUrl)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            (uri.port == -1 || uri.port == HTTPS_PORT) &&
            uri.path.orEmpty().startsWith(PATH_PREFIX)
    }.getOrDefault(false)

    internal const val SCRIPT = """
        (() => {
          "use strict";

          const marker = "__jfmOfvirtualCompatibilityInstalled";
          const applyTextFix = () => {
            for (const label of document.querySelectorAll('.navbar-toggler-text')) {
              if ((label.textContent || '').trim() === 'MenÃº') {
                label.textContent = 'Menú';
              }
            }
          };
          const applyLegacyIconFix = () => {
            for (const icon of document.querySelectorAll('i.fas')) {
              const before = getComputedStyle(icon, '::before');
              if (before.content !== 'none' &&
                  !before.fontFamily.includes('FontAwesome') &&
                  !before.fontFamily.includes('Font Awesome')) {
                icon.classList.add('fa');
              }
            }
          };
          const applyFixes = () => {
            applyTextFix();
            applyLegacyIconFix();
            synchronizeCollapseControls();
          };

          const collapseButtonSelector = 'button[data-toggle="collapse"][data-target^="#"]';
          const nativeCollapseAvailable = () =>
            Boolean(window.jQuery && window.jQuery.fn &&
              typeof window.jQuery.fn.collapse === 'function') ||
            typeof window.bootstrap !== 'undefined';

          const closestCollapseButton = (node) => {
            for (let current = node; current && current !== document; current = current.parentElement) {
              if (current.matches && current.matches(collapseButtonSelector)) {
                return current;
              }
            }
            return null;
          };

          const resolveTarget = (button) => {
            const selector = button.getAttribute('data-target');
            if (!selector || !/^#[A-Za-z][A-Za-z0-9_.:-]*$/.test(selector)) {
              return null;
            }
            const target = document.getElementById(selector.slice(1));
            return target && target.classList.contains('collapse') ? target : null;
          };
          const updateCollapseControls = (selector, expanded) => {
            for (const control of document.querySelectorAll(collapseButtonSelector)) {
              if (control.getAttribute('data-target') !== selector) continue;
              control.classList.toggle('collapsed', !expanded);
              control.setAttribute('aria-expanded', String(expanded));
            }
          };
          const synchronizeCollapseControls = () => {
            const synchronizedTargets = new Set();
            for (const control of document.querySelectorAll(collapseButtonSelector)) {
              const selector = control.getAttribute('data-target');
              if (!selector || synchronizedTargets.has(selector)) continue;
              const target = resolveTarget(control);
              if (!target) continue;
              synchronizedTargets.add(selector);
              const expanded = target.classList.contains('show');
              updateCollapseControls(selector, expanded);
            }
          };
          const installObserver = () => {
            const root = document.documentElement;
            if (!root) return;
            new MutationObserver(applyFixes).observe(root, {
              childList: true,
              subtree: true
            });
          };

          if (window[marker] === true) {
            applyFixes();
            return;
          }
          Object.defineProperty(window, marker, {
            value: true,
            writable: false,
            configurable: false
          });

          document.addEventListener('click', (event) => {
            if (nativeCollapseAvailable()) return;
            const button = closestCollapseButton(event.target);
            if (!button) return;
            const selector = button.getAttribute('data-target');
            const target = resolveTarget(button);
            if (!target) return;

            event.preventDefault();
            const opening = !target.classList.contains('show');
            target.classList.toggle('show', opening);
            updateCollapseControls(selector, opening);
          }, false);

          const repairReadyDocument = () => {
            applyFixes();
            installObserver();
          };
          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', repairReadyDocument, { once: true });
          } else {
            repairReadyDocument();
          }
        })();
    """

    private const val HOST = "ws072.juntadeandalucia.es"
    private const val PATH_PREFIX = "/ofvirtual/"
    private const val HTTPS_PORT = 443
}
