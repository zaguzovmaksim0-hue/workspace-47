from __future__ import annotations

import datetime
import hashlib
import re
import unittest
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.serialization import pkcs7
from cryptography.x509.oid import NameOID
from pyasn1.codec.ber import decoder
from pyasn1.codec.der import encoder
from pyasn1.type import univ
from pyasn1_modules import rfc5280, rfc5652


class IndependentPadesValidator:
    """Independent validator according to ISO 32000-1 (PDF 1.7) and PAdES (ETSI EN 319 142)."""

    @staticmethod
    def _parse_tlv_sequence(data: bytes) -> list[tuple[int, bytes, bytes]]:
        offset = 0
        items = []
        while offset < len(data):
            tag_byte = data[offset]
            offset += 1
            if tag_byte == 0:
                if offset < len(data) and data[offset] == 0:
                    offset += 1
                    continue
                else:
                    continue
            if offset >= len(data):
                break
            length_byte = data[offset]
            offset += 1
            if length_byte == 0x80:  # Indefinite length BER
                start = offset
                depth = 1
                pos = offset
                while pos < len(data) - 1 and depth > 0:
                    if data[pos] == 0 and data[pos + 1] == 0:
                        depth -= 1
                        if depth == 0:
                            break
                        pos += 2
                    elif (data[pos] & 0x20 != 0) and data[pos + 1] == 0x80:
                        depth += 1
                        pos += 2
                    else:
                        pos += 1
                val = data[start:pos]
                raw = data[start - 2 : pos + 2]
                items.append((tag_byte, val, raw))
                offset = pos + 2
            elif length_byte & 0x80:
                num_len_bytes = length_byte & 0x7F
                length = int.from_bytes(data[offset : offset + num_len_bytes], "big")
                offset += num_len_bytes
                val = data[offset : offset + length]
                raw = data[offset - num_len_bytes - 2 : offset + length]
                items.append((tag_byte, val, raw))
                offset += length
            else:
                length = length_byte
                val = data[offset : offset + length]
                raw = data[offset - 2 : offset + length]
                items.append((tag_byte, val, raw))
                offset += length
        return items

    @classmethod
    def validate(
        cls,
        pdf_bytes: bytes,
        expected_cert_fingerprint: bytes | None = None,
    ) -> list[dict]:
        if not pdf_bytes.startswith(b"%PDF-"):
            raise ValueError("Invalid PDF header")
        eof_idx = pdf_bytes.rfind(b"%%EOF")
        if eof_idx == -1:
            raise ValueError("Missing %%EOF marker in PDF")

        text = pdf_bytes.decode("latin1", errors="replace")

        # 1. Parse all objects and xref offsets
        obj_offsets: dict[int, int] = {}
        for m in re.finditer(r"(?:^|\n)(\d+)\s+(\d+)\s+obj\b", text):
            num = int(m.group(1))
            start = m.start() if text[m.start()] != "\n" else m.start() + 1
            obj_offsets[num] = start

        # 2. Parse latest trailer
        trailer_matches = list(re.finditer(r"trailer\s*<<([\s\S]*?)>>\s*(?:startxref|$)", text))
        if not trailer_matches:
            raise ValueError("No trailer found in PDF")
        last_trailer = trailer_matches[-1].group(1)

        root_m = re.search(r"/Root\s+(\d+)\s+\d+\s+R", last_trailer)
        if not root_m:
            raise ValueError("No /Root reference in trailer")
        root_num = int(root_m.group(1))

        # 3. Locate Document Catalog
        catalog_offset = obj_offsets.get(root_num)
        if catalog_offset is None:
            raise ValueError(f"Catalog object {root_num} not found")
        catalog_chunk = text[catalog_offset : text.find("endobj", catalog_offset)]

        # Check /AcroForm in Catalog
        acro_m = re.search(r"/AcroForm\s+(?:(\d+)\s+\d+\s+R|<<([\s\S]*?)>>)", catalog_chunk)
        if not acro_m:
            raise ValueError("No /AcroForm found in Document Catalog")

        if acro_m.group(1):
            acro_num = int(acro_m.group(1))
            acro_off = obj_offsets.get(acro_num)
            if acro_off is None:
                raise ValueError(f"AcroForm object {acro_num} not found")
            acro_chunk = text[acro_off : text.find("endobj", acro_off)]
        else:
            acro_chunk = acro_m.group(2)

        # 4. Check /Fields in AcroForm
        fields_m = re.search(r"/Fields\s*\[\s*([\s\S]*?)\s*\]", acro_chunk)
        if not fields_m:
            raise ValueError("No /Fields array in AcroForm")
        field_refs = [int(f.group(1)) for f in re.finditer(r"(\d+)\s+\d+\s+R", fields_m.group(1))]
        if not field_refs:
            raise ValueError("Empty /Fields array in AcroForm")

        # 5. Resolve first page and page /Annots
        pages_m = re.search(r"/Pages\s+(\d+)\s+\d+\s+R", catalog_chunk)
        if not pages_m:
            raise ValueError("No /Pages reference in Catalog")
        pages_num = int(pages_m.group(1))
        pages_off = obj_offsets.get(pages_num)
        if pages_off is None:
            raise ValueError(f"Pages object {pages_num} not found")
        pages_chunk = text[pages_off : text.find("endobj", pages_off)]

        # Find first page
        page_refs = [int(p.group(1)) for p in re.finditer(r"(\d+)\s+\d+\s+R", pages_chunk)]
        first_page_num = page_refs[0] if page_refs else pages_num
        page_off = obj_offsets.get(first_page_num)
        page_chunk = text[page_off : text.find("endobj", page_off)] if page_off else ""
        annots_m = re.search(r"/Annots\s*\[\s*([\s\S]*?)\s*\]", page_chunk)
        page_annot_refs = [int(a.group(1)) for a in re.finditer(r"(\d+)\s+\d+\s+R", annots_m.group(1))] if annots_m else []

        valid_signatures: list[dict] = []
        for f_num in field_refs:
            f_off = obj_offsets.get(f_num)
            if f_off is None:
                continue
            f_chunk = text[f_off : text.find("endobj", f_off)]
            if "/FT /Sig" not in f_chunk and "/FT/Sig" not in f_chunk:
                continue
            if "/Widget" not in f_chunk:
                continue
            if f_num not in page_annot_refs:
                raise ValueError(f"Signature field {f_num} is not referenced in page /Annots")

            # Check /V reference to Signature dictionary
            v_m = re.search(r"/V\s+(\d+)\s+\d+\s+R", f_chunk)
            if not v_m:
                continue
            sig_num = int(v_m.group(1))
            sig_off = obj_offsets.get(sig_num)
            if sig_off is None:
                continue
            sig_chunk = text[sig_off : text.find("endobj", sig_off)]
            if "/Type /Sig" not in sig_chunk and "/Type/Sig" not in sig_chunk:
                continue

            # Parse /ByteRange [ 0 off1 off2 len2 ]
            br_m = re.search(r"/ByteRange\s*\[\s*0\s+(\d+)\s+(\d+)\s+(\d+)\s*\]", sig_chunk)
            if not br_m:
                raise ValueError(f"Signature dictionary {sig_num} missing /ByteRange")
            off1 = int(br_m.group(1))
            off2 = int(br_m.group(2))
            len2 = int(br_m.group(3))

            if off1 <= 0 or off2 <= off1 + 2 or len2 <= 0 or (off2 + len2) != len(pdf_bytes):
                raise ValueError(f"Invalid /ByteRange boundaries: [ 0 {off1} {off2} {len2} ] for length {len(pdf_bytes)}")
            if pdf_bytes[off1] != ord("<") or pdf_bytes[off2 - 1] != ord(">"):
                raise ValueError("ByteRange does not match opening '<' and closing '>' of /Contents")

            hex_contents = pdf_bytes[off1 + 1 : off2 - 1].decode("ascii").strip()
            # Extract raw CMS bytes
            raw_hex_bytes = bytes.fromhex(hex_contents)

            # Reconstruct byteRangeData
            byte_range_data = pdf_bytes[0:off1] + pdf_bytes[off2 : off2 + len2]

            # Parse SignerInfos using TLV traversal
            ci_items = cls._parse_tlv_sequence(raw_hex_bytes)
            if not ci_items or ci_items[0][0] != 0x30:
                raise ValueError("Malformed CMS ContentInfo")
            ci_children = cls._parse_tlv_sequence(ci_items[0][1])
            sd_wrappers = [val for tag_b, val, raw_b in ci_children if tag_b == 0xA0]
            if not sd_wrappers:
                raise ValueError("Missing SignedData in ContentInfo")
            sd_items = cls._parse_tlv_sequence(sd_wrappers[0])
            if not sd_items or sd_items[0][0] != 0x30:
                raise ValueError("Malformed SignedData sequence")
            sd_children = cls._parse_tlv_sequence(sd_items[0][1])
            signer_infos_raw_list = [raw_b for tag_b, val_b, raw_b in sd_children if tag_b == 0x31]
            if not signer_infos_raw_list:
                raise ValueError("Missing signerInfos in SignedData")
            signer_infos_raw = signer_infos_raw_list[-1]

            signer_infos_obj, _ = decoder.decode(signer_infos_raw, asn1Spec=rfc5652.SignerInfos())
            if len(signer_infos_obj) != 1:
                raise ValueError("Expected exactly 1 SignerInfo in CMS")
            signer_info = signer_infos_obj[0]

            # Find digest algorithm
            digest_oid = str(signer_info["digestAlgorithm"]["algorithm"])
            if digest_oid == "1.3.14.3.2.26":
                hash_algo = hashes.SHA1()
                expected_digest = hashlib.sha1(byte_range_data).digest()
            elif digest_oid == "2.16.840.1.101.3.4.2.1":
                hash_algo = hashes.SHA256()
                expected_digest = hashlib.sha256(byte_range_data).digest()
            else:
                raise ValueError(f"Unsupported digest algorithm OID: {digest_oid}")

            # Verify messageDigest attribute in signedAttrs
            signed_attrs = signer_info["signedAttrs"]
            message_digest_found = None
            for attr in signed_attrs:
                attr_type_oid = str(attr["attrType"]) if "attrType" in attr else str(attr["type"])
                if attr_type_oid in ("1.2.840.113549.1.9.4", str(rfc5652.id_messageDigest)):
                    values_field = attr["attrValues"] if "attrValues" in attr else attr["values"]
                    val, _ = decoder.decode(values_field[0], asn1Spec=univ.OctetString())
                    message_digest_found = bytes(val)
                    break
            if message_digest_found is None:
                raise ValueError("Missing messageDigest in CMS signedAttributes")
            if message_digest_found != expected_digest:
                raise ValueError(f"CMS messageDigest mismatch: computed={expected_digest.hex()} vs signed={message_digest_found.hex()}")

            # Extract signing certificate from CMS
            certs = pkcs7.load_der_pkcs7_certificates(raw_hex_bytes)
            if not certs:
                raise ValueError("No certificates found in CMS SignedData")
            cert = certs[0]
            cert_der = cert.public_bytes(serialization.Encoding.DER)
            cert_fp = hashlib.sha256(cert_der).digest()

            if expected_cert_fingerprint and cert_fp != expected_cert_fingerprint:
                raise ValueError(f"Certificate fingerprint mismatch: expected {expected_cert_fingerprint.hex()} vs actual {cert_fp.hex()}")

            # Verify RSA signature over signedAttributes
            # In CMS, signature is computed over DER encoding of SET OF Attribute (tag 0x31)
            sig_input = univ.Set()
            for i, attr in enumerate(signed_attrs):
                sig_input.setComponentByPosition(i, attr)
            sig_input_der = encoder.encode(sig_input)

            sig_bytes = bytes(signer_info["signature"])
            public_key = cert.public_key()
            if not isinstance(public_key, rsa.RSAPublicKey):
                raise ValueError("Signer public key is not RSA")

            public_key.verify(
                sig_bytes,
                sig_input_der,
                padding.PKCS1v15(),
                hash_algo,
            )

            valid_signatures.append(
                {
                    "field_num": f_num,
                    "sig_num": sig_num,
                    "off1": off1,
                    "off2": off2,
                    "len2": len2,
                    "cert_subject": cert.subject.rfc4514_string(),
                    "cert_fp": cert_fp.hex(),
                }
            )

        if not valid_signatures:
            raise ValueError("No valid linked signature field found")

        return valid_signatures


class PadesPdfValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.priv_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = issuer = x509.Name(
            [
                x509.NameAttribute(NameOID.COUNTRY_NAME, "ES"),
                x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Firma Mobile QA"),
                x509.NameAttribute(NameOID.COMMON_NAME, "ACCEDA Test Signer"),
            ]
        )
        self.cert = (
            x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(issuer)
            .public_key(self.priv_key.public_key())
            .serial_number(12345)
            .not_valid_before(datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=1))
            .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=365))
            .sign(self.priv_key, hashes.SHA256())
        )
        self.cert_der = self.cert.public_bytes(serialization.Encoding.DER)
        self.cert_fp = hashlib.sha256(self.cert_der).digest()

    def create_valid_signed_pdf(self) -> bytes:
        sample_pdf = (
            b"%PDF-1.4\n"
            b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
            b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
            b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
            b"xref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n"
            b"trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n186\n%%EOF\n"
        )
        contents_hex_len = 4096

        root_obj = b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R >>\nendobj\n"
        page_obj = b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Annots [ 5 0 R ] >>\nendobj\n"
        acro_obj = b"4 0 obj\n<< /Fields [ 5 0 R ] /SigFlags 3 >>\nendobj\n"
        sig_field = b"5 0 obj\n<< /FT /Sig /Type /Annot /Subtype /Widget /Rect [ 0 0 0 0 ] /F 132 /T (Signature1) /V 6 0 R /P 3 0 R >>\nendobj\n"
        sig_header_len = len(b"6 0 obj\n<<\n/Type /Sig\n/Filter /Adobe.PPKLite\n/SubFilter /ETSI.CAdES.detached\n/ByteRange [ 0 0000000000 0000000000 0000000000 ]\n/Contents <")
        sig_footer = b">\n/M (D:20300102030405Z)\n/Reason (Firma electronica ACCEDA)\n>>\nendobj\n"

        curr_off = len(sample_pdf)
        off_root = curr_off
        curr_off += len(root_obj)
        off_page = curr_off
        curr_off += len(page_obj)
        off_acro = curr_off
        curr_off += len(acro_obj)
        off_field = curr_off
        curr_off += len(sig_field)
        off_sig_dict = curr_off
        off1 = curr_off + sig_header_len - 1
        off2 = off1 + 1 + contents_hex_len + 1
        curr_off += sig_header_len + contents_hex_len + len(sig_footer)

        xref_off = curr_off
        xref_chunk = (
            b"xref\n"
            b"1 1\n" + f"{off_root:010d} 00000 n \n".encode("ascii") +
            b"3 4\n" +
            f"{off_page:010d} 00000 n \n".encode("ascii") +
            f"{off_acro:010d} 00000 n \n".encode("ascii") +
            f"{off_field:010d} 00000 n \n".encode("ascii") +
            f"{off_sig_dict:010d} 00000 n \n".encode("ascii")
        )
        curr_off += len(xref_chunk)
        trailer_chunk = (
            b"trailer\n<<\n/Size 7\n/Root 1 0 R\n/Prev 186\n>>\nstartxref\n" +
            f"{xref_off}\n%%EOF\n".encode("ascii")
        )
        total_len = curr_off + len(trailer_chunk)
        len2 = total_len - off2

        sig_header = (
            b"6 0 obj\n<<\n/Type /Sig\n/Filter /Adobe.PPKLite\n/SubFilter /ETSI.CAdES.detached\n/ByteRange [ 0 " +
            f"{off1:010d} {off2:010d} {len2:010d}".encode("ascii") +
            b" ]\n/Contents <"
        )

        template = bytearray(
            sample_pdf + root_obj + page_obj + acro_obj + sig_field + sig_header +
            (b"0" * contents_hex_len) + sig_footer + xref_chunk + trailer_chunk
        )

        byte_range_data = bytes(template[0:off1] + template[off2 : off2 + len2])

        # Build detached PKCS#7 / CMS signature using cryptography
        builder = pkcs7.PKCS7SignatureBuilder().set_data(byte_range_data).add_signer(
            self.cert, self.priv_key, hashes.SHA256()
        )
        cms_der = builder.sign(
            serialization.Encoding.DER,
            [pkcs7.PKCS7Options.DetachedSignature, pkcs7.PKCS7Options.Binary],
        )
        cms_hex = cms_der.hex().upper().ljust(contents_hex_len, "0")
        template[off1 + 1 : off1 + 1 + contents_hex_len] = cms_hex.encode("ascii")
        return bytes(template)

    def test_valid_pades_signature_passes_independent_validation(self) -> None:
        pdf = self.create_valid_signed_pdf()
        signatures = IndependentPadesValidator.validate(pdf, self.cert_fp)
        self.assertEqual(1, len(signatures))
        self.assertEqual(5, signatures[0]["field_num"])
        self.assertEqual(6, signatures[0]["sig_num"])
        self.assertEqual(self.cert_fp.hex(), signatures[0]["cert_fp"])

    def test_orphan_signature_dictionary_without_acroform_is_rejected(self) -> None:
        # Broken commit c90c3d6 style: orphan /Type /Sig without AcroForm / Fields link
        sample_pdf = (
            b"%PDF-1.4\n"
            b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
            b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
            b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
            b"xref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n"
            b"trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n186\n%%EOF\n"
        )
        orphan_pdf = (
            sample_pdf +
            b"\n4 0 obj\n<<\n/Type /Sig\n/Filter /Adobe.PPKLite\n/SubFilter /ETSI.CAdES.detached\n"
            b"/ByteRange [ 0 0000000300 0000000400 0000000100 ]\n/Contents <0000>\n>>\nendobj\n"
            b"xref\n4 1\n0000000330 00000 n \ntrailer\n<< /Size 5 /Root 1 0 R /Prev 186 >>\nstartxref\n400\n%%EOF\n"
        )
        with self.assertRaises(ValueError) as ctx:
            IndependentPadesValidator.validate(orphan_pdf)
        self.assertIn("No /AcroForm found in Document Catalog", str(ctx.exception))

    def test_missing_ft_sig_in_field_is_rejected(self) -> None:
        pdf = self.create_valid_signed_pdf()
        corrupted = pdf.replace(b"/FT /Sig", b"/FT /Tx ")
        with self.assertRaises(ValueError) as ctx:
            IndependentPadesValidator.validate(corrupted)
        self.assertIn("No valid linked signature field found", str(ctx.exception))

    def test_signature_field_not_in_page_annots_is_rejected(self) -> None:
        pdf = self.create_valid_signed_pdf()
        corrupted = pdf.replace(b"/Annots [ 5 0 R ]", b"/Annots [ 9 0 R ]")
        with self.assertRaises(ValueError) as ctx:
            IndependentPadesValidator.validate(corrupted)
        self.assertIn("not referenced in page /Annots", str(ctx.exception))

    def test_tampered_document_byte_fails_cryptographic_verification(self) -> None:
        pdf = self.create_valid_signed_pdf()
        tampered = bytearray(pdf)
        tampered[140] = ord("8") if tampered[140] != ord("8") else ord("9")
        with self.assertRaises(ValueError) as ctx:
            IndependentPadesValidator.validate(bytes(tampered), self.cert_fp)
        self.assertIn("messageDigest mismatch", str(ctx.exception))

    def test_tampered_signature_bytes_fails_cryptographic_verification(self) -> None:
        pdf = self.create_valid_signed_pdf()
        match = re.search(rb"/Contents <([0-9A-F]+)", pdf)
        self.assertIsNotNone(match)
        assert match is not None
        start = match.start(1)
        tampered = bytearray(pdf)
        tampered[start] = ord("B") if tampered[start] == ord("A") else ord("A")
        with self.assertRaises(Exception):
            IndependentPadesValidator.validate(bytes(tampered), self.cert_fp)

    def test_malformed_acroform_missing_fields_is_rejected(self) -> None:
        pdf = self.create_valid_signed_pdf()
        corrupted = pdf.replace(b"/Fields [ 5 0 R ]", b"/Other [ 5 0 R ]")
        with self.assertRaises(ValueError) as ctx:
            IndependentPadesValidator.validate(corrupted)
        self.assertIn("No /Fields array in AcroForm", str(ctx.exception))

    def test_invalid_byte_range_boundaries_rejected(self) -> None:
        pdf = self.create_valid_signed_pdf()
        corrupted = pdf.replace(b"/ByteRange [ 0 0000000790", b"/ByteRange [ 0 0000000700")
        with self.assertRaises(ValueError) as ctx:
            IndependentPadesValidator.validate(corrupted)
        self.assertIn("ByteRange", str(ctx.exception))

    def test_missing_eof_is_rejected(self) -> None:
        pdf = self.create_valid_signed_pdf()
        corrupted = pdf.replace(b"%%EOF", b"%NOPE")
        with self.assertRaises(ValueError) as ctx:
            IndependentPadesValidator.validate(corrupted)
        self.assertIn("Missing %%EOF", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
