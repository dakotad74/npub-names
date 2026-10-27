/**
 * Deterministic human names from Nostr public keys — algorithm v1.
 *
 * <p>Entry point: {@link NpubNames#generate(String)} and {@link NpubNames#generate(byte[])}.
 * The canonical seed is the 32-byte x-only pubkey, so the npub (bech32) and hex forms of one
 * key always yield the same {@link Name}. Zero runtime dependencies; the derivation is a pure
 * function of the key (no network, no state).</p>
 *
 * <p>The frozen algorithm, wordlist checksums, test vectors, collision analysis, and the
 * versioning policy live in the repository's {@code SPEC.md}. Changing any of them is a new
 * algorithm version and requires a new tag string — never a silent change of v1.</p>
 */
package io.github.dakotad74.npubnames;
