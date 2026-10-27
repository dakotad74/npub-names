package io.github.dakotad74.npubnames;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic human names from Nostr public keys — algorithm v1 (frozen; see SPEC.md).
 *
 * <p>The canonical seed is the 32-byte x-only pubkey. The npub (bech32) and hex forms of the
 * same key always produce the same name: canonicalization happens here, once, so the
 * one-person-two-names class of bug Sparrow hit with robohash seeds cannot occur.</p>
 *
 * <p>Zero runtime dependencies.</p>
 */
public final class NpubNames {

    static final String TAG = "npub-names/v1";
    private static final int WORDLIST_SIZE = 1024;
    private static final int SURNAME_SLOTS = 3;
    private static final int NUMBER_MODULUS = 10_000;

    private NpubNames() {}

    /**
     * Generates the deterministic name for an npub (bech32, HRP {@code npub}, 32-byte payload)
     * or a 64-character hex x-only pubkey. Both forms are case-insensitive.
     *
     * @throws NullPointerException     if the input is null
     * @throws IllegalArgumentException if the input is neither of the two accepted forms
     */
    public static Name generate(String npubOrHex) {
        Objects.requireNonNull(npubOrHex, "npubOrHex");
        return generate(canonicalize(npubOrHex));
    }

    /**
     * Generates the deterministic name for a 32-byte x-only pubkey.
     *
     * @throws NullPointerException     if the input is null
     * @throws IllegalArgumentException if the input is not exactly 32 bytes
     */
    public static Name generate(byte[] xOnlyPubkey) {
        Objects.requireNonNull(xOnlyPubkey, "xOnlyPubkey");
        if (xOnlyPubkey.length != 32) {
            throw new IllegalArgumentException(
                "x-only pubkey must be exactly 32 bytes, got " + xOnlyPubkey.length);
        }

        WordStream words = new WordStream(xOnlyPubkey);
        String given = Wordlists.givenNames().get(slot(words, WORDLIST_SIZE));

        List<String> chosen = new ArrayList<>(SURNAME_SLOTS);
        for (int i = 0; i < SURNAME_SLOTS; i++) {
            String surname;
            do {
                surname = Wordlists.surnames().get(slot(words, WORDLIST_SIZE));
            } while (chosen.contains(surname)); // deterministic redraw on repeated words
            chosen.add(surname);
        }

        int number = (int) Long.remainderUnsigned(words.next(), NUMBER_MODULUS);
        return new Name(given, List.copyOf(chosen), number);
    }

    private static int slot(WordStream words, int modulus) {
        return (int) Long.remainderUnsigned(words.next(), modulus);
    }

    private static byte[] canonicalize(String input) {
        if (input.regionMatches(true, 0, "npub1", 0, 5)) {
            return npubBytes(input);
        }
        if (input.length() == 64 && isHex(input)) {
            return HexFormat.of().parseHex(input);
        }
        // Any other bech32-shaped string gets the precise reason it is rejected:
        // a valid bech32 with the wrong HRP (e.g. an accidentally pasted nsec) says so.
        if (input.matches("(?i)[a-z0-9]+1[a-z0-9]+")) {
            Bech32.Decoded decoded = Bech32.decode(input);
            throw new IllegalArgumentException("bech32 HRP must be npub, got: " + decoded.hrp());
        }
        throw new IllegalArgumentException(
            "unrecognized identifier: expected an npub (bech32) or a 64-character hex pubkey");
    }

    private static byte[] npubBytes(String input) {
        Bech32.Decoded decoded = Bech32.decode(input);
        if (!decoded.hrp().equals("npub")) {
            throw new IllegalArgumentException("bech32 HRP must be npub, got: " + decoded.hrp());
        }
        if (decoded.data().length != 32) {
            throw new IllegalArgumentException(
                "npub payload must be 32 bytes, got " + decoded.data().length);
        }
        return decoded.data();
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lazy stream of unsigned 64-bit big-endian words over counter-extended blocks:
     * {@code block_i = SHA-256(TAG || pubkey || byte(i))}, i = 0, 1, 2, ...
     */
    private static final class WordStream {

        private final byte[] pubkey;
        private final List<byte[]> blocks = new ArrayList<>();
        private long produced;

        WordStream(byte[] pubkey) {
            this.pubkey = pubkey;
        }

        long next() {
            int blockIndex = (int) (produced / 4);
            if (blockIndex > 0xFF) {
                throw new IllegalStateException("algorithm v1 requires more than 256 hash blocks");
            }
            while (blockIndex >= blocks.size()) {
                blocks.add(sha256(TAG.getBytes(StandardCharsets.US_ASCII), pubkey, (byte) blocks.size()));
            }
            byte[] block = blocks.get(blockIndex);
            int offset = (int) (produced % 4) * 8;
            produced++;
            long word = 0;
            for (int i = 0; i < 8; i++) {
                word = (word << 8) | (block[offset + i] & 0xFFL);
            }
            return word;
        }

        private static byte[] sha256(byte[] tag, byte[] pubkey, byte counter) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(tag);
                digest.update(pubkey);
                digest.update(counter);
                return digest.digest();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }
}
