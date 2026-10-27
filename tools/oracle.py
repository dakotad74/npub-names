#!/usr/bin/env python3
"""Independent oracle for npub-names algorithm v1.

Produces the frozen test vectors (spec-vectors.json) used by the Java and
TypeScript implementations and printed in SPEC.md. Deliberately implemented
from the spec text alone, with its own bech32 encoder, so implementations can
be verified against something that shares no code with them.

Algorithm v1 (frozen):
  tag     = ASCII "npub-names/v1"
  block_i = SHA-256(tag || x-only-pubkey(32 bytes) || byte(i)), i = 0, 1, 2, ...
  stream  = concat(block_0, block_1, ...) read as unsigned 64-bit big-endian words

  draw order:
    given  = GIVEN[draw() mod 1024]
    three surnames: repeat draw() mod 1024 until the word is not already chosen
    number = draw() mod 10000

  displayName = given + " " + s1 + " " + s2 + " " + s3
  withNumber  = displayName + " " + number zero-padded to 4 digits
"""

import hashlib
import json
import struct

TAG = b"npub-names/v1"
GIVEN_PATH = "src/main/resources/io/github/dakotad74/npubnames/given-names-1024.txt"
SURNAMES_PATH = "src/main/resources/io/github/dakotad74/npubnames/surnames-1024.txt"

CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"


def bech32_polymod(values):
    generator = [0x3B6A57B2, 0x26508E6D, 0x1EA119FA, 0x3D4233DD, 0x2A1462B3]
    chk = 1
    for value in values:
        top = chk >> 25
        chk = ((chk & 0x1FFFFFF) << 5) ^ value
        for i in range(5):
            if (top >> i) & 1:
                chk ^= generator[i]
    return chk


def bech32_encode(hrp, data_bytes):
    data = convertbits(data_bytes, 8, 5)
    values = [ord(c) >> 5 for c in hrp] + [0] + [ord(c) & 31 for c in hrp] + data
    polymod = bech32_polymod(values + [0, 0, 0, 0, 0, 0]) ^ 1
    checksum = [(polymod >> 5 * (5 - i)) & 31 for i in range(6)]
    return hrp + "1" + "".join(CHARSET[d] for d in data + checksum)


def convertbits(data, frombits, tobits, pad=True):
    acc = 0
    bits = 0
    ret = []
    maxv = (1 << tobits) - 1
    for value in data:
        acc = (acc << frombits) | value
        bits += frombits
        while bits >= tobits:
            bits -= tobits
            ret.append((acc >> bits) & maxv)
    if pad and bits:
        ret.append((acc << (tobits - bits)) & maxv)
    return ret


class WordStream:
    """Lazy stream of unsigned 64-bit big-endian words from counter-extended blocks."""

    def __init__(self, pubkey):
        self.pubkey = pubkey
        self.blocks = []

    def word(self, index):
        block_index = index // 4
        while block_index >= len(self.blocks):
            counter = len(self.blocks)
            self.blocks.append(hashlib.sha256(TAG + self.pubkey + bytes([counter])).digest())
        block = self.blocks[block_index]
        offset = (index % 4) * 8
        return struct.unpack(">Q", block[offset:offset + 8])[0]


def name_from_pubkey(pubkey, given, surnames):
    ws = WordStream(pubkey)
    index = 0
    given_word = given[ws.word(index) % 1024]
    index += 1
    chosen = []
    for _ in range(3):
        while True:
            word = surnames[ws.word(index) % 1024]
            index += 1
            if word not in chosen:
                chosen.append(word)
                break
    number = ws.word(index) % 10000
    display = " ".join([given_word] + chosen)
    return {
        "given": given_word,
        "surnames": chosen,
        "number": number,
        "numberPadded": f"{number:04d}",
        "displayName": display,
        "withNumber": f"{display} {number:04d}",
    }


def main():
    given = open(GIVEN_PATH, encoding="utf-8").read().split()
    surnames = open(SURNAMES_PATH, encoding="utf-8").read().split()
    assert len(given) == 1024 and len(surnames) == 1024

    # V1: a typical fixed pubkey (frozen, not random).
    fixed = bytes.fromhex("d02a1a5e1c5e8f0f2b6e1d4c9a7b8f3e5c6d7a8b9c0d1e2f3a4b5c6d7e8f9a0b")

    # Search: first counter-keyed pubkey whose number is below 1000
    # (zero-padding of the 4-digit number becomes observable).
    low_number = None
    for i in range(65536):
        candidate = i.to_bytes(2, "big") + b"\x00" * 30
        if name_from_pubkey(candidate, given, surnames)["number"] < 1000:
            low_number = candidate
            break

    # Search: first counter-keyed pubkey whose three raw surname draws contain
    # a repeat (exercises the deterministic redraw rule).
    redraw = None
    for i in range(65536):
        candidate = i.to_bytes(2, "big") + b"\x11" * 30
        ws = WordStream(candidate)
        raw = [surnames[ws.word(j) % 1024] for j in range(1, 4)]
        if len(set(raw)) < 3:
            redraw = candidate
            break

    subjects = [
        ("fixed", fixed),
        ("low-number", low_number),
        ("redraw", redraw),
        ("all-zero", bytes(32)),
        ("all-ff", bytes.fromhex("ff" * 32)),
    ]

    out = []
    for label, pubkey in subjects:
        r = name_from_pubkey(pubkey, given, surnames)
        out.append(
            {
                "label": label,
                "hexLower": pubkey.hex(),
                "hexUpper": pubkey.hex().upper(),
                "npub": bech32_encode("npub", pubkey),
                "nsecEncoding": bech32_encode("nsec", pubkey),  # wrong HRP: must be rejected
                "displayName": r["displayName"],
                "number": r["number"],
                "withNumber": r["withNumber"],
                "surnames": r["surnames"],
            }
        )

    # A valid bech32 npub whose payload is NOT 32 bytes (16 bytes here): reject.
    out.append({"label": "short-payload-npub", "npub": bech32_encode("npub", bytes(16))})

    with open("spec-vectors.json", "w", encoding="utf-8") as fh:
        json.dump(out, fh, indent=2)
    print(json.dumps(out, indent=2))


if __name__ == "__main__":
    main()
