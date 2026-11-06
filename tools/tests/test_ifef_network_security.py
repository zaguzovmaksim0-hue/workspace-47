"""Fail-closed, host-scoped CA chain completion for portals that omit their issuer."""

import hashlib
import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[2]
CONFIG = ROOT / "app/src/main/res/xml/network_security_config.xml"
RAW = ROOT / "app/src/main/res/raw"

# Exact hosts -> the single missing issuer each one may use, and that issuer's pinned DER bytes.
ISSUER_SHA256 = {
    "ifef_sslcom_tls_issuing_rsa_ca_r1":
        "bfbc39e9fa2b84c9a92337e2344ee2381d8d3d3ae8ea75ef4e48e5807ed80c69",
    "fnmt_ac_componentes_informaticos":
        "f038421f07f20d63a20d3691e5a178ab8459ebe570c1647b7690554ef23876ab",
    "fnmt_ac_servidores_seguros_tipo1":
        "1edb6bd91274882db795bfc514f8aabe10ad955cbccfd3fd5a5b5febb2ce5b68",
    "globalsign_gcc_r3_ev_tls_ca_2025":
        "1b2928021a4b89cf0f572a2436e914e25412ad58edee221e6c7c3c5437179a26",
}
HOST_ISSUERS = {
    ("sedeelectronica.ifef.es",): "ifef_sslcom_tls_issuing_rsa_ca_r1",
    (
        "sede.airef.es",
        "sede.dip-caceres.es",
        "sede.ine.gob.es",
        "sede.isfas.gob.es",
        "sede.mites.gob.es",
    ): "fnmt_ac_componentes_informaticos",
    ("sede.consejodetransparencia.gob.es",): "fnmt_ac_servidores_seguros_tipo1",
    ("sede.imserso.gob.es",): "globalsign_gcc_r3_ev_tls_ca_2025",
}


class HostScopedIssuerNetworkSecurityConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(CONFIG).getroot()

    def test_default_trust_is_system_only_and_https_only(self):
        base, = self.root.findall("base-config")
        self.assertEqual(base.get("cleartextTrafficPermitted"), "false")
        self.assertEqual([x.get("src") for x in base.findall("trust-anchors/certificates")], ["system"])
        self.assertIsNone(base.find("domain"))

    def test_only_reviewed_exact_hosts_get_their_own_issuer(self):
        actual = {}
        for domain_config in self.root.findall("domain-config"):
            self.assertEqual(domain_config.get("cleartextTrafficPermitted"), "false")
            self.assertEqual([], domain_config.findall("domain-config"))
            domains = domain_config.findall("domain")
            self.assertTrue(all(d.get("includeSubdomains") == "false" for d in domains))
            sources = [x.get("src") for x in domain_config.findall("trust-anchors/certificates")]
            self.assertEqual(2, len(sources))
            self.assertEqual("system", sources[0])
            self.assertTrue(sources[1].startswith("@raw/"))
            actual[tuple(d.text for d in domains)] = sources[1].removeprefix("@raw/")
        self.assertEqual(HOST_ISSUERS, actual)

    def test_issuer_certificate_bytes_are_pinned(self):
        for name, digest in ISSUER_SHA256.items():
            with self.subTest(name=name):
                self.assertEqual(hashlib.sha256((RAW / f"{name}.cer").read_bytes()).hexdigest(), digest)
        self.assertEqual(set(ISSUER_SHA256), set(HOST_ISSUERS.values()))

    def test_no_debug_overrides_or_cleartext_exceptions(self):
        self.assertIsNone(self.root.find("debug-overrides"))
        self.assertTrue(all(x.get("cleartextTrafficPermitted") == "false" for x in self.root))


if __name__ == "__main__":
    unittest.main()
