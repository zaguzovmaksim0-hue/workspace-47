"""Fail-closed, host-scoped CA chain completion for the IFEF portal."""

import hashlib
import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[2]
CONFIG = ROOT / "app/src/main/res/xml/network_security_config.xml"
ISSUER = ROOT / "app/src/main/res/raw/ifef_sslcom_tls_issuing_rsa_ca_r1.cer"
ISSUER_SHA256 = "bfbc39e9fa2b84c9a92337e2344ee2381d8d3d3ae8ea75ef4e48e5807ed80c69"


class IfefNetworkSecurityConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(CONFIG).getroot()

    def test_default_trust_is_system_only_and_https_only(self):
        base, = self.root.findall("base-config")
        self.assertEqual(base.get("cleartextTrafficPermitted"), "false")
        self.assertEqual([x.get("src") for x in base.findall("trust-anchors/certificates")], ["system"])
        self.assertIsNone(base.find("domain"))

    def test_ifef_is_only_exception_without_subdomain_wildcard(self):
        domain_config, = self.root.findall("domain-config")
        domain, = domain_config.findall("domain")
        self.assertEqual(domain.text, "sedeelectronica.ifef.es")
        self.assertNotEqual(domain.get("includeSubdomains"), "true")
        self.assertEqual(domain_config.get("cleartextTrafficPermitted"), "false")
        self.assertEqual([x.get("src") for x in domain_config.findall("trust-anchors/certificates")], [
            "system", "@raw/ifef_sslcom_tls_issuing_rsa_ca_r1",
        ])

    def test_issuer_certificate_bytes_are_pinned(self):
        self.assertEqual(hashlib.sha256(ISSUER.read_bytes()).hexdigest(), ISSUER_SHA256)

    def test_no_debug_overrides_or_cleartext_exceptions(self):
        self.assertIsNone(self.root.find("debug-overrides"))
        self.assertTrue(all(x.get("cleartextTrafficPermitted") == "false" for x in self.root))


if __name__ == "__main__":
    unittest.main()
