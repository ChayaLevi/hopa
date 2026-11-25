#!/usr/bin/env python3
"""Generate the signed-answer fixtures (ADR-0032).

Writes ``spec/fixtures/approval-signatures.json``:

- ``vectors``: fixed Ed25519 seeds, requests and challenges, with the exact
  action hash, signed bytes and signature every client must reproduce;
- ``checks``: how an asking device reads an answer (``AnswerCheck``), from a
  first signed allow that pins the key to a changed key, a replayed nonce or
  a changed amount, each denied with a stable code.

The signatures come from the ``cryptography`` package, independently of the
Kotlin code they test. ``--check`` fails when the committed file is stale.
"""

from __future__ import annotations

import base64
import hashlib
import json
import sys
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "spec" / "fixtures" / "approval-signatures.json"

DOMAIN = "hopa.approval.answer.v1"
ACTION_DOMAIN = "hopa.approval.action.v1"


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def fields(*values: str) -> bytes:
    out = b""
    for value in values:
        data = value.encode("utf-8")
        out += len(data).to_bytes(4, "big") + data
    return out


def action_hash(req: dict) -> str:
    return hashlib.sha256(
        fields(
            ACTION_DOMAIN,
            req["title"],
            req["body"],
            req["amount"],
            req["merchant"],
            req["consequence"],
            "true" if req["isPayment"] else "false",
            req["action"]["kind"],
        )
    ).hexdigest()


def message(challenge: dict, surface_id: str | None, req: dict, allow: bool) -> bytes:
    return fields(
        DOMAIN,
        challenge["nonce"],
        str(challenge["expiresAt"]),
        surface_id or "",
        action_hash(req),
        "allow" if allow else "deny",
    )


def key_of(seed: bytes) -> bytes:
    return Ed25519PrivateKey.from_private_bytes(seed).public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)


def sign(seed: bytes, challenge: dict, surface_id: str | None, req: dict, allow: bool) -> dict:
    sig = Ed25519PrivateKey.from_private_bytes(seed).sign(message(challenge, surface_id, req, allow))
    return {"v": 1, "alg": "Ed25519", "key": b64(key_of(seed)), "sig": b64(sig)}


def fingerprint(key: bytes) -> str:
    digest = hashlib.sha256(key).hexdigest()[:16]
    return " ".join(digest[i : i + 4] for i in range(0, 16, 4))


PHONE = bytes(range(32))
OTHER = bytes(range(32, 64))

pay_req = {
    "title": "Confirm payment",
    "body": "Money may leave your account.",
    "amount": "12.40",
    "merchant": "Example Shop",
    "consequence": "HOPA cannot undo a payment.",
    "isPayment": True,
    "action": {"kind": "payment.pay"},
}
plain_req = {
    "title": "Open Settings?",
    "body": "HOPA wants to open Wi-Fi settings on your phone.",
    "amount": "",
    "merchant": "",
    "consequence": "",
    "isPayment": False,
    "action": {"kind": "launch"},
}
unicode_req = dict(pay_req, title="Bezahlen?", merchant="Café Zürich", amount="€ 4,50")
CHALLENGE = {"v": 1, "nonce": b64(bytes(range(16))), "expiresAt": 1795000000000}
OTHER_CHALLENGE = {"v": 1, "nonce": b64(bytes(range(16, 32))), "expiresAt": 1795000000000}
SURFACE = "appr_ap_1_0a1b2c3d_1"
NOW = CHALLENGE["expiresAt"] - 10_000

vectors = []
for name, seed, req, challenge, surface, allow in (
    ("a payment allowed from its card", PHONE, pay_req, CHALLENGE, SURFACE, True),
    ("a plain request denied without a card", PHONE, plain_req, CHALLENGE, None, False),
    ("text outside ASCII is signed as UTF-8", OTHER, unicode_req, OTHER_CHALLENGE, SURFACE, True),
):
    vectors.append({
        "name": name,
        "seed": b64(seed),
        "publicKey": b64(key_of(seed)),
        "fingerprint": fingerprint(key_of(seed)),
        "request": req,
        "challenge": challenge,
        "surfaceId": surface,
        "allow": allow,
        "actionHash": action_hash(req),
        "message": message(challenge, surface, req, allow).hex(),
        "signature": sign(seed, challenge, surface, req, allow),
    })

checks = []


def check(name, allowed, code, signature, allow=True, sender="dev-phone", pinned=None, now=NOW, pins=False):
    checks.append({
        "name": name,
        "request": pay_req,
        "challenge": CHALLENGE,
        "surfaceId": SURFACE,
        "allow": allow,
        "from": sender,
        "pinned": b64(key_of(pinned)) if pinned else None,
        "now": now,
        "signature": signature,
        "allowed": allowed,
        "code": code,
        "pins": pins,
    })


good = sign(PHONE, CHALLENGE, SURFACE, pay_req, True)
check("the first signed allow pins the key", True, "pinned", good, pins=True)
check("a signed allow from the pinned key", True, "verified", good, pinned=PHONE)
check("a deny needs no proof", False, "denied", None, allow=False, pinned=PHONE)
check("an answer after the deadline", False, "expired", good, pinned=PHONE, now=CHALLENGE["expiresAt"] + 1)
check("an unsigned allow from a phone that never signed", True, "legacy", None)
check("an unsigned allow from a phone that signs", False, "unsigned", None, pinned=PHONE)
check("a signature of the wrong size", False, "malformed", dict(good, sig=b64(b"\x00" * 10)), pinned=PHONE)
check("a signature that is not Ed25519", False, "malformed", dict(good, alg="RSA"), pinned=PHONE)
check("a signed allow with no sender", False, "no_sender", good, sender="")
check("signed for another amount", False, "bad_signature",
      sign(PHONE, CHALLENGE, SURFACE, dict(pay_req, amount="1240.00"), True), pinned=PHONE)
check("a signed deny presented as allow", False, "bad_signature",
      sign(PHONE, CHALLENGE, SURFACE, pay_req, False), pinned=PHONE)
check("a replay of an answer to another question", False, "bad_signature",
      sign(PHONE, OTHER_CHALLENGE, SURFACE, pay_req, True), pinned=PHONE)
check("signed for another card", False, "bad_signature",
      sign(PHONE, CHALLENGE, "appr_ap_1_ffffffff_1", pay_req, True), pinned=PHONE)
check("a signature from another key", False, "key_changed",
      sign(OTHER, CHALLENGE, SURFACE, pay_req, True), pinned=PHONE)
check("a valid signature that claims another key", False, "bad_signature",
      dict(good, key=b64(key_of(OTHER))), pinned=PHONE)

document = {"version": 1, "vectors": vectors, "checks": checks}


def main() -> int:
    text = json.dumps(document, indent=2, ensure_ascii=False) + "\n"
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print(f"{OUT.relative_to(ROOT)} is stale; run tools/scripts/build-approval-signature-fixtures.py",
                  file=sys.stderr)
            return 1
        print(f"approval signature fixtures are current ({len(vectors)} vectors, {len(checks)} checks)")
        return 0
    OUT.write_text(text, encoding="utf-8", newline="\n")
    print(f"wrote {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
