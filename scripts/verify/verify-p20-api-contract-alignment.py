#!/usr/bin/env python3
"""P20 API contract and frontend type-alignment verification.

This verifier intentionally skips documentation / Markdown files. CI should
validate executable contracts, source code, and frontend types only; docs can be
renamed, omitted, or edited without failing release verification.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def read_required(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"Missing required file: {path}")
    return p.read_text(encoding="utf-8")


def require_contains(path: str, needles: list[str]) -> str:
    text = read_required(path)
    for needle in needles:
        if needle not in text:
            fail(f"{path} does not contain required text: {needle}")
    return text


def require_not_contains(path: str, needles: list[str]) -> str:
    text = read_required(path)
    for needle in needles:
        if needle in text:
            fail(f"{path} contains forbidden text: {needle}")
    return text


def main() -> int:
    envelope_ts = "ai-event-gateway-admin-ui/lib/api/envelope.ts"
    client_ts = "ai-event-gateway-admin-ui/lib/api/client.ts"
    proxy_ts = "ai-event-gateway-admin-ui/lib/server/backendProxy.ts"
    verify_release = "scripts/verify/verify-release.py"

    # Documentation and delivery summaries are intentionally not verified here.
    # The hard gate starts at the source contract below.

    envelope = require_contains(envelope_ts, [
        "export interface StandardApiEnvelope<T>",
        "code: string;",
        "message: string;",
        "data: T | null;",
        "timestamp: string;",
        "export interface LegacyApiEnvelope<T>",
        "export const STANDARD_SUCCESS_CODE = 'OK';",
        "export const UNAUTHORIZED_API_CODES",
        "export const NOT_FOUND_OR_UNSUPPORTED_CODES",
        "export function isStandardApiEnvelope",
        "export function isLegacyApiEnvelope",
        "export function standardEnvelopeCode",
        "export function isUnauthorizedApiCode",
        "export function isNotFoundOrUnsupportedCode",
        "export function makeStandardApiEnvelope",
    ])
    if "status:" in envelope or "httpStatus" in envelope or "success: boolean;" in envelope.split("export interface StandardApiEnvelope<T>", 1)[1].split("export interface LegacyApiEnvelope<T>", 1)[0]:
        fail(f"{envelope_ts} standard envelope must not expose transport status or legacy success flag")

    require_contains(client_ts, [
        "from '@/lib/api/envelope';",
        "STANDARD_SUCCESS_CODE",
        "isStandardApiEnvelope",
        "isLegacyApiEnvelope",
        "isUnauthorizedApiCode(bodyCode)",
        "isNotFoundOrUnsupportedCode(error.code)",
    ])
    require_not_contains(client_ts, [
        "interface StandardApiEnvelope<T>",
        "interface LegacyApiEnvelope<T>",
        "const STANDARD_SUCCESS_CODE = 'OK';",
    ])

    require_contains(proxy_ts, [
        "makeStandardApiEnvelope",
        "type StandardApiEnvelope",
        "function standardEnvelope<T>",
        "return makeStandardApiEnvelope(code, message, data);",
        "status: 200",
    ])

    require_contains(verify_release, [
        "standard API response contract",
        "API contract and frontend type alignment",
        "verify-p20-api-contract-alignment.py",
    ])

    print("P20 API contract and frontend type alignment verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
