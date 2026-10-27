# npub-names Specification — Algorithm v1

**Status: FROZEN.** Version `v1` is immutable. Any change to the tag, the wordlists, the draw
order, or the output formatting defines a new version and **must** change the tag string — a v1
implementation and a v2 implementation may coexist, but the same input must never silently map
to different names across versions.

**Promise.** A Nostr public key deterministically maps to one human-readable name. The same key
always produces the same name, in every implementation, forever. The npub (bech32) and hex forms
of the same key produce the same name. Collision with another key's name is possible in
principle (finite name space, infinite keys) but vanishingly rare at any realistic scale
(see [Collision analysis](#collision-analysis)).

Implementations: Java (this repository, `src/main/java`), an independent Python oracle
(`tools/oracle.py`, shares no code with the Java library), and a TypeScript mirror (`ts/`).
All are validated against the frozen vectors below.

---

## 1. Canonicalization

The canonical seed is the **32-byte x-only public key**. Every accepted input form must decode
to exactly these 32 bytes before any derivation happens.

| Input form | Rules |
|---|---|
| npub (bech32) | BIP-173 bech32 (checksum constant `1`, **not** bech32m), human-readable part exactly `npub`, payload exactly **32 bytes**. At most 90 characters. All-lowercase or all-uppercase accepted; **mixed case rejected**. Leftover padding bits must be zero. |
| hex | Exactly 64 characters from `[0-9a-fA-F]` (case-insensitive), decoded to 32 bytes. |
| Anything else | Rejected with an error. This includes valid bech32 strings with a different HRP (e.g. `nsec1…`), bech32 `npub` strings whose payload is not 32 bytes, strings of any other length, and non-hex characters. |

Null input is rejected by the caller-facing API. A valid bech32 `npub` whose payload is 16 bytes
is a real rejection case, not a corruption: it must fail the same way as garbage.

## 2. Derivation

```
tag      = ASCII "npub-names/v1"
block_i  = SHA-256( tag || pubkey_32 || byte(i) ),   i = 0x00, 0x01, 0x02, ...
stream   = block_0 || block_1 || block_2 || ...
```

`stream` is read as a sequence of **unsigned 64-bit big-endian words** (`w0`, `w1`, `w2`, …):
word `k` comes from byte offsets `8k … 8k+8` of the concatenated blocks.

Draw order (each draw consumes the next word in sequence):

1. **Given name** = `GIVEN[ w mod 1024 ]`
2. **Surname 1** = `SURNAMES[ w mod 1024 ]`
3. **Surname 2** = draw `w mod 1024`; **if the drawn word equals an already-chosen surname,
   discard it and draw the next word** (repeat until distinct)
4. **Surname 3** = same rule against surnames 1 and 2
5. **Number** = `w mod 10000`

Output:

```
displayName = given + " " + s1 + " " + s2 + " " + s3          // "Paula Daniels Stanton Little"
number      = the drawn number, 0..9999
withNumber  = displayName + " " + number zero-padded to 4 digits
              // "Paula Daniels Stanton Little 0629"
```

`withNumber` exists so a UI can disambiguate two identical `displayName`s in the same list.
Implementations expose both; choosing which to render is a UI decision, never part of the
algorithm.

Notes implementers must honor:

- **Modulo bias.** `2^64 mod 1024 = 0`, so name slots are exact. `2^64 mod 10000 = 1616`, a
  relative bias of ≈ 8.8 × 10⁻¹⁷ on the number slot — negligible and accepted by v1.
- **Counter bound.** The block counter is a single byte (0–255). The redraw rule makes needing
  even three blocks an event of probability ~10⁻⁹; reaching 256 blocks is beyond astronomically
  unlikely. Implementations must fail closed if the counter would wrap — never wrap silently.

## 3. Wordlists

Two frozen text files, one word per line, capitalized, **alphabetically ordered** (the order
defines the index mapping and is part of the freeze):

| File | Words | SHA-256 |
|---|---|---|
| `given-names-1024.txt` | 1024 given names | `53c0d58ead24ee0b632533f5bb4d52e891fce0e540cd9d0e9dc00fcdf174685a` |
| `surnames-1024.txt` | 1024 surnames | `3db1448be28e63a2ead8dbb356423675f162a63f0dd6fd85f4fdc8e90d2289c2` |

Sources (public domain, U.S. Government — U.S. Census Bureau):

- Given names: 1990 Census first names (`dist.male.first` + `dist.female.first`), male and
  female frequencies summed.
- Surnames: 2010 Census surnames (`Names_2010Census.csv`).

Selection rules used to build the freeze (reproducible via `tools/build-wordlists.py`):

1. Keep only words of ASCII letters `^[A-Za-z]{2,15}$` — no hyphens, spaces, or apostrophes.
2. Deduplicate case-insensitively.
3. Rank by frequency descending; ties broken by word ascending.
4. Take the top 1024, then freeze in alphabetical order.

**Implementations must verify both SHA-256 checksums at load time and fail closed on any
mismatch.** A silently corrupted list would produce plausible-looking but wrong names — exactly
the failure mode this library exists to prevent.

## 4. Frozen test vectors

Hardcode these in every implementation. They were produced by `tools/oracle.py`, an
independent implementation of this spec. If an implementation disagrees with a vector, **the
implementation is wrong**; never regenerate vectors to match code.

| Label | hex pubkey | npub | displayName | number | withNumber |
|---|---|---|---|---|---|
| fixed | `d02a1a5e1c5e8f0f2b6e1d4c9a7b8f3e5c6d7a8b9c0d1e2f3a4b5c6d7e8f9a0b` | `npub16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sgt64q2` | Paula Daniels Stanton Little | 629 | `Paula Daniels Stanton Little 0629` |
| low-number | `000a000000000000000000000000000000000000000000000000000000000000` | `npub1qq9qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqql2xumg` | Sheila Murillo Brooks Mata | 405 | `Sheila Murillo Brooks Mata 0405` |
| redraw | `0071111111111111111111111111111111111111111111111111111111111111` | `npub1qpc3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygst5uhvv` | Jordan Vincent Moreno Esparza | 1652 | `Jordan Vincent Moreno Esparza 1652` |
| all-zero | `0000000000000000000000000000000000000000000000000000000000000000` | `npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqzqujme` | Brittany Miller Murillo Bradford | 6833 | `Brittany Miller Murillo Bradford 6833` |
| all-ff | `ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff` | `npub1lllllllllllllllllllllllllllllllllllllllllllllllllllsq7lrjw` | Sonja Sierra Roberts Curry | 5002 | `Sonja Sierra Roberts Curry 5002` |

The `redraw` vector is special: the first three raw surname draws for that key contain a
repeat, so it exercises the redraw rule — its three surnames are distinct *because* of the
rule, not by luck.

Rejection vectors (must throw, never map to a name):

- `nsec16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sya35xl` — valid bech32, wrong HRP
- `npub1qqqqqqqqqqqqqqqqqqqqqqqqqqk7h3rf` — valid bech32 `npub`, 16-byte payload
- the `fixed` npub with its last character altered — checksum failure
- any mixed-case bech32, any 63- or 65-character hex string, any 64-character non-hex string,
  the empty string, and byte arrays whose length is not 32

Every hex vector above also proves case-insensitivity: the uppercase form must produce the
same name. The `all-zero` and `all-ff` pubkeys are stable edge vectors.

## 5. Collision analysis

The v1 name space is `1024 × 1024³ × 10000 ≈ 1.1 × 10¹⁶` distinct names
(≈ 1.1 × 10¹² without the number, ≈ 53.3 bits with it).

For `N` identities seen together, the birthday-bound probability that *some* pair shares a
name is `≈ 1 − e^(−N² / 2M)` with `M ≈ 1.1 × 10¹⁶`:

| N identities | P(any collision) |
|---|---|
| 1,000 | ≈ 4.5 × 10⁻¹¹ |
| 100,000 | ≈ 4.5 × 10⁻⁷ |
| 1,000,000 | ≈ 4.5 × 10⁻⁵ |
| 10,000,000 | ≈ 0.45 % |

For scale: robohash **set1** — the set Sparrow uses — contains exactly **1,000,000** distinct
robots (10 colors × 5 parts × 10 variants, counted from the robohash source); with 1,000
counterparties its any-collision probability is already ≈ 39 %. npub-names v1 reaches that
same probability only at ≈ 93 million identities. A collision is also cosmetic by nature: the
identity remains the public key, and `withNumber()` disambiguates visually when a UI needs it.

## 6. Versioning policy

- The tag string `"npub-names/v1"` is part of the algorithm. It domain-separates v1 digests
  from any future version and from other protocols.
- Changing the wordlists (adding, removing, reordering, recasing), the draw order, the redraw
  rule, the number formatting, or the canonicalization acceptance rules is a **new version**
  and must ship with a new tag (e.g. `"npub-names/v2"`).
- Implementations should expose the version they implement and refuse inputs only per the
  canonicalization table of *their* version.
