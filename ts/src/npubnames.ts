/**
 * npub-names — deterministic human names from Nostr public keys.
 *
 * TypeScript mirror of algorithm v1, validated against the same frozen vectors as the
 * Java reference implementation. See ../SPEC.md for the frozen algorithm; this file and
 * the Java implementation must always agree.
 *
 * Zero runtime dependencies. Runs on Node >= 22.18 (native type stripping) and in
 * bundlers that consume TypeScript directly.
 */
import { createHash } from "node:crypto";
import { GIVEN, SURNAMES } from "./wordlists.ts";

const TAG = "npub-names/v1";
const WORDLIST_SIZE = 1024;
const SURNAME_SLOTS = 3;
const NUMBER_MODULUS = 10_000n;
const CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

/** A deterministic human name derived from a Nostr public key. Immutable. */
export class Name {
  readonly given: string;
  readonly surnames: readonly string[];
  readonly number: number;

  constructor(given: string, surnames: readonly string[], number: number) {
    this.given = given;
    this.surnames = Object.freeze([...surnames]);
    this.number = number;
  }

  /** The clean display name, e.g. "Paula Daniels Stanton Little". */
  displayName(): string {
    return [this.given, ...this.surnames].join(" ");
  }

  /** Display name plus the zero-padded four-digit number, e.g. "Paula Daniels Stanton Little 0629". */
  withNumber(): string {
    return `${this.displayName()} ${String(this.number).padStart(4, "0")}`;
  }

  equals(other: unknown): boolean {
    return (
      other instanceof Name &&
      other.given === this.given &&
      other.surnames.length === this.surnames.length &&
      other.surnames.every((s, i) => s === this.surnames[i]) &&
      other.number === this.number
    );
  }
}

/**
 * Generates the deterministic name for an npub (bech32, HRP "npub", 32-byte payload) or a
 * 64-character hex x-only pubkey. Both forms are case-insensitive.
 *
 * Throws a TypeError for null/undefined input and an Error for anything that is neither of
 * the two accepted forms (including valid bech32 with the wrong HRP or a payload that is
 * not 32 bytes).
 */
export function generate(npubOrHex: string): Name;
export function generate(xOnlyPubkey: Uint8Array): Name;
export function generate(input: string | Uint8Array): Name {
  if (input === null || input === undefined) {
    throw new TypeError("npub-names: input must be an npub string or a 32-byte pubkey");
  }
  const pubkey = typeof input === "string" ? canonicalize(input) : require32(input);
  return derive(pubkey);
}

function require32(bytes: Uint8Array): Uint8Array {
  if (bytes.length !== 32) {
    throw new Error(`npub-names: x-only pubkey must be exactly 32 bytes, got ${bytes.length}`);
  }
  return bytes;
}

function canonicalize(input: string): Uint8Array {
  if (input.toLowerCase().startsWith("npub1")) {
    return npubBytes(input);
  }
  if (input.length === 64 && isHex(input)) {
    return hexToBytes(input);
  }
  // Any other bech32-shaped string gets the precise reason it is rejected:
  // a valid bech32 with the wrong HRP (e.g. an accidentally pasted nsec) says so.
  if (/^[a-z0-9]+1[a-z0-9]+$/i.test(input)) {
    const decoded = bech32Decode(input);
    throw new Error(`npub-names: bech32 HRP must be npub, got: ${decoded.hrp}`);
  }
  throw new Error(
    "npub-names: unrecognized identifier — expected an npub (bech32) or a 64-character hex pubkey",
  );
}

function npubBytes(input: string): Uint8Array {
  const decoded = bech32Decode(input);
  if (decoded.hrp !== "npub") {
    throw new Error(`npub-names: bech32 HRP must be npub, got: ${decoded.hrp}`);
  }
  if (decoded.data.length !== 32) {
    throw new Error(`npub-names: npub payload must be 32 bytes, got ${decoded.data.length}`);
  }
  return decoded.data;
}

function isHex(s: string): boolean {
  return /^[0-9a-fA-F]+$/.test(s);
}

function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

/** Lazy stream of unsigned 64-bit big-endian words over counter-extended blocks. */
class WordStream {
  #pubkey: Uint8Array;
  #blocks: Buffer[] = [];
  #produced = 0n;

  constructor(pubkey: Uint8Array) {
    this.#pubkey = pubkey;
  }

  next(): bigint {
    const blockIndex = Number(this.#produced >> 2n);
    if (blockIndex > 0xff) {
      throw new Error("npub-names: algorithm v1 requires more than 256 hash blocks");
    }
    while (blockIndex >= this.#blocks.length) {
      this.#blocks.push(
        createHash("sha256")
          .update(TAG, "ascii")
          .update(this.#pubkey)
          .update(Uint8Array.of(this.#blocks.length))
          .digest(),
      );
    }
    const word = this.#blocks[blockIndex].readBigUInt64BE(Number(this.#produced & 3n) * 8);
    this.#produced += 1n;
    return word;
  }
}

function derive(pubkey: Uint8Array): Name {
  const words = new WordStream(pubkey);
  const given = GIVEN[Number(words.next() % BigInt(WORDLIST_SIZE))];

  const chosen: string[] = [];
  for (let i = 0; i < SURNAME_SLOTS; i++) {
    let surname: string;
    do {
      surname = SURNAMES[Number(words.next() % BigInt(WORDLIST_SIZE))];
    } while (chosen.includes(surname)); // deterministic redraw on repeated words
    chosen.push(surname);
  }

  const number = Number(words.next() % NUMBER_MODULUS);
  return new Name(given, chosen, number);
}

interface Bech32Decoded {
  hrp: string;
  data: Uint8Array;
}

/** Minimal BIP-173 bech32 decoder (checksum constant 1). Mirrors the Java implementation. */
function bech32Decode(bech32: string): Bech32Decoded {
  if (bech32.length > 90) {
    throw new Error(`npub-names: bech32 string too long: ${bech32.length}`);
  }
  const hasLower = /[a-z]/.test(bech32);
  const hasUpper = /[A-Z]/.test(bech32);
  if (hasLower && hasUpper) {
    throw new Error("npub-names: mixed-case bech32 strings are not allowed");
  }
  const s = hasUpper ? bech32.toLowerCase() : bech32;

  const separator = s.lastIndexOf("1");
  if (separator < 1 || separator + 1 + 6 > s.length) {
    throw new Error("npub-names: invalid bech32 separator position");
  }
  const hrp = s.slice(0, separator);
  for (const c of hrp) {
    if (c.charCodeAt(0) < 33 || c.charCodeAt(0) > 126) {
      throw new Error("npub-names: invalid character in bech32 human-readable part");
    }
  }

  const data: number[] = [];
  for (const c of s.slice(separator + 1)) {
    const value = CHARSET.indexOf(c);
    if (value < 0) {
      throw new Error(`npub-names: invalid bech32 character: ${c}`);
    }
    data.push(value);
  }
  if (!bech32VerifyChecksum(hrp, data)) {
    throw new Error("npub-names: bech32 checksum verification failed");
  }

  return { hrp, data: convertBits(data.slice(0, data.length - 6), 5, 8, false) };
}

function bech32Polymod(values: number[]): number {
  const generator = [0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3];
  let chk = 1;
  for (const value of values) {
    const top = chk >> 25;
    chk = ((chk & 0x1ffffff) << 5) ^ value;
    for (let i = 0; i < 5; i++) {
      if ((top >> i) & 1) {
        chk ^= generator[i];
      }
    }
  }
  return chk;
}

function bech32VerifyChecksum(hrp: string, data: number[]): boolean {
  const values: number[] = [];
  for (const c of hrp) values.push(c.charCodeAt(0) >> 5);
  values.push(0);
  for (const c of hrp) values.push(c.charCodeAt(0) & 31);
  values.push(...data);
  return bech32Polymod(values) === 1;
}

function convertBits(data: number[], fromBits: number, toBits: number, pad: boolean): Uint8Array {
  let acc = 0;
  let bits = 0;
  const maxv = (1 << toBits) - 1;
  const maxAcc = (1 << (fromBits + toBits - 1)) - 1;
  const out: number[] = [];
  for (const value of data) {
    if (value < 0 || value >> fromBits !== 0) {
      throw new Error("npub-names: invalid bech32 data value");
    }
    acc = ((acc << fromBits) | value) & maxAcc;
    bits += fromBits;
    while (bits >= toBits) {
      bits -= toBits;
      out.push((acc >> bits) & maxv);
    }
  }
  if (pad) {
    if (bits !== 0) {
      out.push((acc << (toBits - bits)) & maxv);
    }
  } else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) !== 0) {
    throw new Error("npub-names: invalid bech32 padding bits");
  }
  return new Uint8Array(out);
}
