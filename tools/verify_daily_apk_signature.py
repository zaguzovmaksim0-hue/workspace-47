#!/usr/bin/env python3
"""Validate public apksigner evidence against the reviewed daily certificate pin."""
import argparse
from pathlib import Path
import re


def validate_report(report: str, expected: str) -> None:
    expected = expected.strip()
    if re.fullmatch(r"[0-9a-f]{64}", expected) is None:
        raise ValueError("Expected certificate must be one canonical SHA-256 digest")
    lines = report.splitlines()
    if [line for line in lines if line.startswith("Number of signers:")] != ["Number of signers: 1"]:
        raise ValueError("Daily APK must have exactly one signer")
    if not any(re.fullmatch(r"Verified using v2 scheme .*: true", line) for line in lines):
        raise ValueError("Daily APK must verify with APK Signature Scheme v2")
    certificate_lines = [line for line in lines if "certificate SHA-256 digest:" in line]
    allowed = {"Signer #1", "V1 Signer", "V2 Signer", "V3 Signer", "V3.0 Signer", "V3.1 Signer"}
    labels = set()
    if not certificate_lines:
        raise ValueError("Missing APK signer certificate digest")
    for line in certificate_lines:
        match = re.fullmatch(r"(.+?):? certificate SHA-256 digest: ([0-9a-f]{64})", line)
        if match is None:
            raise ValueError("Malformed APK signer certificate evidence")
        label, digest = match.groups()
        if label not in allowed or label in labels or digest != expected:
            raise ValueError("APK signer does not match the pinned daily certificate")
        labels.add(label)



def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--expected", type=Path, required=True)
    args = parser.parse_args()
    try:
        validate_report(args.report.read_text(), args.expected.read_text())
    except (OSError, ValueError) as error:
        parser.exit(1, f"Daily APK verification failed: {error}\n")
    print("Daily APK certificate matches the reviewed SHA-256 pin")


if __name__ == "__main__":
    main()
