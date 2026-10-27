package io.github.dakotad74.npubnames;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The frozen v1 wordlists, loaded once and integrity-checked against the SHA-256 checksums
 * published in SPEC.md.
 *
 * <p>The loader fails fast: a wrong size, a malformed word, or a checksum mismatch means the
 * shipped resource differs from the frozen lists, and any name produced from it would be a
 * different (wrong) name. Failing closed here protects the determinism contract.</p>
 */
final class Wordlists {

    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");
    private static final int SIZE = 1024;

    static final String GIVEN_SHA256 =
        "53c0d58ead24ee0b632533f5bb4d52e891fce0e540cd9d0e9dc00fcdf174685a";
    static final String SURNAMES_SHA256 =
        "3db1448be28e63a2ead8dbb356423675f162a63f0dd6fd85f4fdc8e90d2289c2";

    private static final List<String> GIVEN = load("given-names-1024.txt", GIVEN_SHA256);
    private static final List<String> SURNAMES = load("surnames-1024.txt", SURNAMES_SHA256);

    private Wordlists() {}

    static List<String> givenNames() {
        return GIVEN;
    }

    static List<String> surnames() {
        return SURNAMES;
    }

    private static List<String> load(String resource, String expectedSha256) {
        String path = "/io/github/dakotad74/npubnames/" + resource;
        byte[] bytes;
        try (InputStream in = Wordlists.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing wordlist resource: " + path);
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read wordlist resource: " + path, e);
        }

        String actual = HexFormat.of().formatHex(sha256(bytes));
        if (!actual.equals(expectedSha256)) {
            throw new IllegalStateException(
                "wordlist integrity check failed for " + resource + ": expected sha256 "
                    + expectedSha256 + " but got " + actual);
        }

        String content = new String(bytes, StandardCharsets.UTF_8);
        if (!content.endsWith("\n")) {
            throw new IllegalStateException("wordlist must end with a newline: " + resource);
        }
        List<String> words = List.of(content.substring(0, content.length() - 1).split("\n", -1));
        if (words.size() != SIZE) {
            throw new IllegalStateException(
                "wordlist must hold exactly " + SIZE + " words, got " + words.size() + ": " + resource);
        }
        for (String word : words) {
            if (word.isEmpty() || !WORD.matcher(word).matches()) {
                throw new IllegalStateException("malformed word in " + resource + ": '" + word + "'");
            }
        }
        return words;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
