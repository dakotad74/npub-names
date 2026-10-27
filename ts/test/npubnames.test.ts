import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { generate } from "../src/npubnames.ts";

/**
 * Frozen acceptance vectors for npub-names algorithm v1, identical to the Java suite
 * and to SPEC.md. Produced by tools/oracle.py. If this test fails, the mirror is wrong —
 * the vectors are frozen, never regenerated to match code.
 */

const VECTORS = [
  {
    label: "fixed",
    hexLower: "d02a1a5e1c5e8f0f2b6e1d4c9a7b8f3e5c6d7a8b9c0d1e2f3a4b5c6d7e8f9a0b",
    npub: "npub16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sgt64q2",
    displayName: "Paula Daniels Stanton Little",
    number: 629,
    surnames: ["Daniels", "Stanton", "Little"],
  },
  {
    label: "low-number",
    hexLower: "000a000000000000000000000000000000000000000000000000000000000000",
    npub: "npub1qq9qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqql2xumg",
    displayName: "Sheila Murillo Brooks Mata",
    number: 405,
    surnames: ["Murillo", "Brooks", "Mata"],
  },
  {
    label: "redraw",
    hexLower: "0071111111111111111111111111111111111111111111111111111111111111",
    npub: "npub1qpc3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygst5uhvv",
    displayName: "Jordan Vincent Moreno Esparza",
    number: 1652,
    surnames: ["Vincent", "Moreno", "Esparza"],
  },
  {
    label: "all-zero",
    hexLower: "0000000000000000000000000000000000000000000000000000000000000000",
    npub: "npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqzqujme",
    displayName: "Brittany Miller Murillo Bradford",
    number: 6833,
    surnames: ["Miller", "Murillo", "Bradford"],
  },
  {
    label: "all-ff",
    hexLower: "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
    npub: "npub1lllllllllllllllllllllllllllllllllllllllllllllllllllsq7lrjw",
    displayName: "Sonja Sierra Roberts Curry",
    number: 5002,
    surnames: ["Sierra", "Roberts", "Curry"],
  },
];

describe("npub-names v1 frozen vectors", () => {
  it("every seed form of the same key produces the frozen name", () => {
    for (const v of VECTORS) {
      const fromHex = generate(v.hexLower);
      assert.equal(fromHex.displayName(), v.displayName, v.label);
      assert.equal(generate(v.hexLower.toUpperCase()).displayName(), v.displayName, v.label);
      assert.equal(generate(v.npub).displayName(), v.displayName, v.label);
      assert.equal(generate(v.npub.toUpperCase()).displayName(), v.displayName, v.label);
      assert.equal(generate(hexToBytes(v.hexLower)).displayName(), v.displayName, v.label);

      const name = generate(v.hexLower);
      assert.equal(name.number, v.number, v.label);
      assert.deepEqual([...name.surnames], v.surnames, v.label);
    }
  });

  it("displayName is given plus three surnames; withNumber appends the zero-padded number", () => {
    for (const v of VECTORS) {
      const name = generate(v.hexLower);
      assert.equal(name.displayName(), `${v.displayName}`);
      assert.equal(name.withNumber(), `${v.displayName} ${String(name.number).padStart(4, "0")}`);
    }
  });

  it("the low-number vector shows the four-digit zero padding", () => {
    assert.equal(generate(VECTORS[1].hexLower).withNumber(), "Sheila Murillo Brooks Mata 0405");
  });

  it("the redraw vector still produces three distinct surnames", () => {
    const surnames = generate(VECTORS[2].hexLower).surnames;
    assert.equal(surnames.length, 3);
    assert.equal(new Set(surnames).size, 3);
  });

  it("repeated generation is deterministic and names are comparable", () => {
    const first = generate(VECTORS[0].npub);
    const second = generate(VECTORS[0].hexLower);
    assert.ok(first.equals(second));
    assert.ok(!first.equals(generate(VECTORS[4].hexLower)));
  });

  it("the surnames list is frozen", () => {
    const name = generate(VECTORS[0].hexLower);
    assert.throws(() => (name.surnames as string[]).push("Extra"), TypeError);
  });
});

describe("npub-names v1 rejections", () => {
  it("rejects valid bech32 with the wrong HRP", () => {
    assert.throws(
      () => generate("nsec16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sya35xl"),
      /HRP must be npub/,
    );
  });

  it("rejects an npub whose payload is not 32 bytes", () => {
    assert.throws(() => generate("npub1qqqqqqqqqqqqqqqqqqqqqqqqqqk7h3rf"), /32 bytes/);
  });

  it("rejects a corrupted bech32 checksum", () => {
    const npub = VECTORS[0].npub;
    const corrupted = npub.slice(0, -1) + (npub.at(-1) === "q" ? "p" : "q");
    assert.throws(() => generate(corrupted), /checksum/);
  });

  it("rejects mixed-case bech32", () => {
    assert.throws(() => generate("Npub1" + VECTORS[0].npub.slice(5)), /mixed-case/);
  });

  it("rejects malformed hex seeds", () => {
    assert.throws(() => generate("d02a1a5e"));
    assert.throws(() => generate("d02a1a5e1c5e8f0f2b6e1d4c9a7b8f3e5c6d7a8b9c0d1e2f3a4b5c6d7e8f9a0bff"));
    assert.throws(() => generate("z".repeat(64)));
    assert.throws(() => generate(""));
    assert.throws(() => generate(new Uint8Array(31)));
    assert.throws(() => generate(new Uint8Array(33)));
  });

  it("rejects null and undefined", () => {
    assert.throws(() => generate(null as unknown as string), TypeError);
    assert.throws(() => generate(undefined as unknown as string), TypeError);
  });
});

function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}
