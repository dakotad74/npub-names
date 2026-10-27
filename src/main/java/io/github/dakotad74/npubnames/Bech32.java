package io.github.dakotad74.npubnames;

import java.util.Arrays;

/**
 * Minimal BIP-173 bech32 decoder (checksum constant 1), as used by NIP-19 entities.
 *
 * <p>Deliberately self-contained: npub-names has zero runtime dependencies. Implements the
 * BIP-173 decoder rules verbatim: at most 90 characters, all-lowercase or all-uppercase
 * (mixed case rejected), strict charset, checksum verification, and the zero-padding
 * requirement when regrouping 5-bit groups into bytes.</p>
 */
final class Bech32 {

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int CHECKSUM_LENGTH = 6;
    private static final int MAX_LENGTH = 90;

    record Decoded(String hrp, byte[] data) {}

    private Bech32() {}

    static Decoded decode(String bech32) {
        if (bech32.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("bech32 string too long: " + bech32.length());
        }
        boolean hasLower = bech32.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = bech32.chars().anyMatch(Character::isUpperCase);
        if (hasLower && hasUpper) {
            throw new IllegalArgumentException("mixed-case bech32 strings are not allowed");
        }
        String s = hasUpper ? bech32.toLowerCase() : bech32;

        int separator = s.lastIndexOf('1');
        if (separator < 1 || separator + 1 + CHECKSUM_LENGTH > s.length()) {
            throw new IllegalArgumentException("invalid bech32 separator position");
        }
        String hrp = s.substring(0, separator);
        for (char c : hrp.toCharArray()) {
            if (c < 33 || c > 126) {
                throw new IllegalArgumentException("invalid character in bech32 human-readable part");
            }
        }

        int[] data = new int[s.length() - separator - 1];
        for (int i = 0; i < data.length; i++) {
            int value = CHARSET.indexOf(s.charAt(separator + 1 + i));
            if (value < 0) {
                throw new IllegalArgumentException("invalid bech32 character: " + s.charAt(separator + 1 + i));
            }
            data[i] = value;
        }
        if (!verifyChecksum(hrp, data)) {
            throw new IllegalArgumentException("bech32 checksum verification failed");
        }

        int[] payload = Arrays.copyOfRange(data, 0, data.length - CHECKSUM_LENGTH);
        return new Decoded(hrp, convertBits(payload, 5, 8, false));
    }

    private static boolean verifyChecksum(String hrp, int[] data) {
        int[] values = new int[hrp.length() * 2 + 1 + data.length];
        for (int i = 0; i < hrp.length(); i++) {
            values[i] = hrp.charAt(i) >> 5;
        }
        values[hrp.length()] = 0;
        for (int i = 0; i < hrp.length(); i++) {
            values[hrp.length() + 1 + i] = hrp.charAt(i) & 31;
        }
        System.arraycopy(data, 0, values, hrp.length() * 2 + 1, data.length);
        return polymod(values) == 1;
    }

    private static int polymod(int[] values) {
        int[] generator = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};
        int checksum = 1;
        for (int value : values) {
            int top = checksum >> 25;
            checksum = ((checksum & 0x1ffffff) << 5) ^ value;
            for (int i = 0; i < 5; i++) {
                if (((top >> i) & 1) != 0) {
                    checksum ^= generator[i];
                }
            }
        }
        return checksum;
    }

    /**
     * Regroups a 5-bit representation into {@code toBits}-bit groups.
     *
     * <p>With {@code pad = false} (the decoding direction), leftover bits must be zero and
     * shorter than {@code fromBits}, per BIP-173; anything else is a corrupted string.</p>
     */
    private static byte[] convertBits(int[] data, int fromBits, int toBits, boolean pad) {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        int maxAcc = (1 << (fromBits + toBits - 1)) - 1;
        byte[] out = new byte[data.length * fromBits / toBits];
        int written = 0;
        for (int value : data) {
            if (value < 0 || (value >> fromBits) != 0) {
                throw new IllegalArgumentException("invalid bech32 data value");
            }
            acc = ((acc << fromBits) | value) & maxAcc;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                out[written++] = (byte) ((acc >> bits) & maxv);
            }
        }
        if (pad) {
            if (bits != 0) {
                out[written++] = (byte) ((acc << (toBits - bits)) & maxv);
            }
        } else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
            throw new IllegalArgumentException("invalid bech32 padding bits");
        }
        return Arrays.copyOf(out, written);
    }
}
