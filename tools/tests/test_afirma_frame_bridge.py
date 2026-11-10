"""Execute the document-start hook against a small DOM, without a live website."""
import pathlib
import shutil
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "app/src/main/res/raw/afirma_frame.js"

NODE_TEST = r"""
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync(process.argv[1], "utf8");
function environment(options = {}) {
    const document = {};
    const messages = [];
    class Frame {
        constructor(owner = document) {
            this.ownerDocument = owner; this.attributes = {}; this.attributeNodes = {};
        }
        setAttribute(name, value) { this.attributes[String(name).toLowerCase()] = value; }
        getAttributeNode(name) { return this.attributeNodes[name] || null; }
        setAttributeNode(attribute) {
            if (attribute.ownerElement && attribute.ownerElement !== this) throw new Error("InUseAttributeError");
            const previous = this.getAttributeNode(attribute.name);
            this.attributeNodes[attribute.name] = attribute;
            this.attributes[attribute.name] = attribute.value;
            attribute.ownerElement = this;
            return previous;
        }
        setAttributeNodeNS(attribute) { return this.setAttributeNode(attribute); }
    }
    Object.defineProperty(Frame.prototype, "src", {
        get() { return this.originalSrc; },
        set(value) { this.originalSrc = value; },
        configurable: true, enumerable: true
    });
    document.createElement = function (name) {
        assert.equal(name, "iframe");
        return new Frame();
    };
    document.createAttribute = function (name) {
        return { name, value: "", namespaceURI: null, ownerElement: null };
    };
    document.documentElement = { appendChild(frame) { return frame; } };
    const window = {
        HTMLIFrameElement: Frame,
        crypto: { getRandomValues(array) { array.fill(23); return array; } }
    };
    window.top = options.subframe ? {} : window;
    if (!options.noBridge) {
        window.FirmaMobileFrameAfirma = {
            postMessage(message) {
                if (options.throwOnMessage) throw new Error("unavailable");
                messages.push(message);
            }
        };
    }
    const context = vm.createContext({
        window, document, location: { protocol: options.insecure ? "http:" : "https:" },
        Uint8Array
    });
    vm.runInContext("(" + source + ")(" + (options.limit || 16777216) + ")", context);
    return { window, document, Frame, messages };
}
const request = "afirma://sign?dat=YWJj";
{
    const e = environment(), frame = new e.Frame();
    frame.src = request;
    assert.equal(frame.originalSrc, undefined);
    assert.equal(e.messages.length, 1);
    assert.equal(e.messages[0], e.window.FirmaMobileFrameDocumentId + "\n" + request);
    assert.match(e.window.FirmaMobileFrameDocumentId, /^[0-9a-f]{32}$/);
    assert.equal(Object.getOwnPropertyDescriptor(e.window, "FirmaMobileFrameDocumentId").writable, false);
    assert.equal(Object.getOwnPropertyDescriptor(e.window, "FirmaMobileFrameDocumentId").configurable, false);
}
{
    const e = environment(), frame = new e.Frame();
    frame.setAttribute("SRC", request);
    assert.equal(e.messages.length, 1);
    assert.equal(frame.attributes.src, undefined);
    frame.setAttribute("title", "ordinary title");
    assert.equal(frame.attributes.title, "ordinary title");
}
{
    const e = environment(), frame = new e.Frame();
    const url = "https://portal.example/?atributo=one&atributo=two";
    frame.src = url;
    frame.setAttribute("src", url);
    assert.equal(frame.originalSrc, url);
    assert.equal(frame.attributes.src, url);
    assert.deepEqual(e.messages, []);
}
{
    const e = environment(), foreignDocumentFrame = new e.Frame({});
    foreignDocumentFrame.src = request;
    assert.equal(foreignDocumentFrame.originalSrc, request);
    assert.deepEqual(e.messages, []);
}
for (const options of [{subframe:true}, {insecure:true}, {noBridge:true}]) {
    const e = environment(options), frame = new e.Frame();
    frame.src = request;
    assert.equal(frame.originalSrc, request);
    assert.deepEqual(e.messages, []);
    assert.equal(e.window.FirmaMobileFrameDocumentId, undefined);
}
{
    const e = environment({limit:16}), frame = new e.Frame();
    frame.src = request;
    assert.equal(frame.originalSrc, request);
    assert.deepEqual(e.messages, []);
}
{
    const e = environment({throwOnMessage:true}), frame = new e.Frame();
    frame.src = request;
    assert.equal(frame.originalSrc, request);
}
// Pinned official AutoScript creates a detached iframe and sets an Attr node.
{
    const e = environment();
    const iframeElem = e.document.createElement("iframe");
    const srcAttr = e.document.createAttribute("src");
    srcAttr.value = request;
    assert.equal(iframeElem.setAttributeNode(srcAttr), null);
    e.document.documentElement.appendChild(iframeElem);
    assert.equal(e.messages.length, 1);
    assert.equal(iframeElem.attributes.src, undefined);
}
{
    const e = environment(), frame = new e.Frame();
    const attribute = e.document.createAttribute("src");
    attribute.value = request;
    assert.equal(frame.setAttributeNodeNS(attribute), null);
    assert.equal(e.messages.length, 1);
    assert.equal(frame.attributes.src, undefined);
}
{
    const e = environment(), frame = new e.Frame({});
    const attribute = e.document.createAttribute("src");
    attribute.value = request;
    frame.setAttributeNode(attribute);
    assert.equal(frame.attributes.src, request);
    assert.deepEqual(e.messages, []);
}
{
    const e = environment(), frame = new e.Frame();
    const attribute = e.document.createAttribute("src");
    attribute.value = "https://portal.example/?atributo=one&atributo=two";
    assert.equal(frame.setAttributeNode(attribute), null);
    const replacement = e.document.createAttribute("src");
    replacement.value = "https://portal.example/next";
    assert.equal(frame.setAttributeNode(replacement), attribute);
    assert.equal(frame.attributes.src, replacement.value);
    assert.deepEqual(e.messages, []);
}
process.stdout.write("13 document/frame scenarios passed\n");
"""


class AfirmaFrameHookTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("node"), "Node.js is required to execute the JavaScript hook")
    def test_document_owned_frame_hook_preserves_browser_boundaries(self):
        result = subprocess.run(
            ["node", "-e", NODE_TEST, str(SCRIPT)],
            capture_output=True, text=True, timeout=30,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
