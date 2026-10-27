# npub-names — deterministic human names from Nostr public keys

Turn any Nostr public key (`npub…` or hex) into a stable, human-readable name like
**"Paula Daniels Stanton Little"**. The same key always yields the same name — in every
implementation, forever. Built for clients like [Sparrow](https://sparrowwallet.com) as a
fallback when an identity has no published profile name, and published as a spec so any
client can compute the same name with no server, no lookup, and no network.

- **Zero dependencies** (Java and TypeScript alike) — works offline.
- **Frozen algorithm v1** — public spec, SHA-256-verified wordlists, frozen test vectors.
- **Cross-implementation** — Java reference library, TypeScript mirror, and an independent
  Python oracle, all validated against the same vectors.

## Quick path

**Java** (Gradle, Java 17+; vendor the sources or use JitPack once a release is tagged):

```java
import io.github.dakotad74.npubnames.NpubNames;

Name name = NpubNames.generate("npub16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sgt64q2");
name.displayName(); // "Paula Daniels Stanton Little"
name.number();      // 629
name.withNumber();  // "Paula Daniels Stanton Little 0629"

NpubNames.generate("d02a1a5e1c5e8f0f2b6e1d4c9a7b8f3e5c6d7a8b9c0d1e2f3a4b5c6d7e8f9a0b")
    .displayName(); // "Paula Daniels Stanton Little" — hex and npub agree
```

**TypeScript** (Node ≥ 22.18 or any TS bundler — see [`ts/README.md`](ts/README.md)):

```ts
import { generate } from "npub-names";

generate("npub16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sgt64q2").displayName();
// → "Paula Daniels Stanton Little"
```

Invalid input is rejected: wrong bech32 HRP (an accidental `nsec…` paste says so explicitly),
payload not 32 bytes, bad checksum, mixed-case bech32, malformed hex.

## What you get

| Field | Example | Notes |
|---|---|---|
| `given` | `Paula` | one given name |
| `surnames` | `Daniels, Stanton, Little` | always three, distinct (deterministic redraw on repeats) |
| `number` | `629` | deterministic disambiguator, 0–9999 — derive always, show only when needed |
| `displayName()` | `Paula Daniels Stanton Little` | the clean default for UI |
| `withNumber()` | `Paula Daniels Stanton Little 0629` | for disambiguating two identical names in one list |

## Guarantees

| Guarantee | How it holds |
|---|---|
| Same key → same name, always | canonical seed is the 32-byte x-only pubkey; derivation is pure SHA-256 |
| npub and hex agree | both decode to the same 32 bytes before hashing — the hex≠npub seed bug class is impossible |
| Same name across implementations | frozen vectors in [SPEC.md](SPEC.md), produced by an independent oracle; tests fail closed |
| Same name across time | wordlists frozen with SHA-256 checksums verified at load; algorithm changes require a new version tag |
| No network, no state | pure function of the key |

## Collisions

The v1 space is `1024 × 1024³ × 10000 ≈ 1.1 × 10¹⁶` names. Birthday-bound probability that
*some* pair collides among `N` identities:

| N identities | P(any collision) |
|---|---|
| 1,000 | ≈ 4.5 × 10⁻¹¹ |
| 100,000 | ≈ 4.5 × 10⁻⁷ |
| 10,000,000 | ≈ 0.45 % |

For scale: robohash **set1** — the set Sparrow uses for avatars — holds exactly 1,000,000
distinct robots (counted from the robohash source: 10 colors × 5 parts × 10 variants) and is
already at ≈ 39 % collision probability with 1,000 identities. npub-names reaches that only
at ≈ 93 million identities. A collision is also cosmetic: the identity remains the public
key, and `withNumber()` disambiguates visually.

## Using it as a fallback in your client

Resolution order that works well (what Sparrow does with kind-0 profiles):

1. Published profile name (kind-0 `name` / `display_name`) when present.
2. `displayName()` from npub-names.
3. Truncated npub as a last resort.

Integration notes:

- Canonicalize once: call `generate` with whatever identifier you hold (hex or npub) — the
  library normalizes to the same name. Do not pre-normalize per call site; that is how the
  one-person-two-seeds bug class is born.
- `Name` is immutable and pure: cache freely (per npub), precompute on background threads.
- Show `withNumber()` only where two identical `displayName`s appear together in one list.

## Documentation

| Doc | Contents |
|---|---|
| [`SPEC.md`](SPEC.md) | the frozen algorithm v1: canonicalization, derivation, wordlist rules, vectors, collision analysis, versioning policy |
| [`ts/README.md`](ts/README.md) | TypeScript mirror usage |
| [`tools/oracle.py`](tools/oracle.py) | independent Python implementation of the spec (vector generator) |

## Development

| Command | What |
|---|---|
| `./gradlew test` | Java suite (12 tests over the frozen vectors and rejection cases) |
| `cd ts && npm test` | TypeScript mirror suite (same vectors) |
| `python3 tools/oracle.py` | regenerate `spec-vectors.json` from the spec, independently |

Regenerating the wordlists (`tools/build-wordlists.py`) changes **every** existing name — per
[SPEC.md](SPEC.md) that is a new algorithm version with a new tag, never a silent v1 change.

## License & provenance

- Code: [MIT](LICENSE).
- Wordlists: U.S. Census Bureau public-domain data (1990 Census first names; 2010 Census
  surnames), filtered and frozen as documented in the spec.
- robohash comparison figures counted from the [e1ven/Robohash](https://github.com/e1ven/Robohash)
  source (set1 = 10 × 5 × 10 = 1,000,000 robots).
