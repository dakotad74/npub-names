package io.github.dakotad74.npubnames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Frozen acceptance vectors for npub-names algorithm v1.
 *
 * <p>Vectors were produced by tools/oracle.py, an independent implementation of SPEC.md
 * that shares no code with this library. If this test fails, the library is wrong —
 * the vectors are frozen, not regenerated to match the implementation.</p>
 */
class NpubNamesTest {

    private static final Vector FIXED =
        new Vector(
            "d02a1a5e1c5e8f0f2b6e1d4c9a7b8f3e5c6d7a8b9c0d1e2f3a4b5c6d7e8f9a0b",
            "npub16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sgt64q2",
            "Paula Daniels Stanton Little",
            629,
            List.of("Daniels", "Stanton", "Little"));

    private static final Vector LOW_NUMBER =
        new Vector(
            "000a000000000000000000000000000000000000000000000000000000000000",
            "npub1qq9qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqql2xumg",
            "Sheila Murillo Brooks Mata",
            405,
            List.of("Murillo", "Brooks", "Mata"));

    /** Raw surname draws repeat for this key; exercises the deterministic redraw rule. */
    private static final Vector REDRAW =
        new Vector(
            "0071111111111111111111111111111111111111111111111111111111111111",
            "npub1qpc3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygst5uhvv",
            "Jordan Vincent Moreno Esparza",
            1652,
            List.of("Vincent", "Moreno", "Esparza"));

    private static final Vector ALL_ZERO =
        new Vector(
            "0000000000000000000000000000000000000000000000000000000000000000",
            "npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqzqujme",
            "Brittany Miller Murillo Bradford",
            6833,
            List.of("Miller", "Murillo", "Bradford"));

    private static final Vector ALL_FF =
        new Vector(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "npub1lllllllllllllllllllllllllllllllllllllllllllllllllllsq7lrjw",
            "Sonja Sierra Roberts Curry",
            5002,
            List.of("Sierra", "Roberts", "Curry"));

    private static final List<Vector> VECTORS = List.of(FIXED, LOW_NUMBER, REDRAW, ALL_ZERO, ALL_FF);

    @Test
    void everySeedFormOfTheSameKeyProducesTheFrozenName() {
        for (Vector v : VECTORS) {
            String displayName = NpubNames.generate(v.hexLower).displayName();
            assertEquals(v.displayName, displayName, v.hexLower);

            assertEquals(v.displayName, NpubNames.generate(v.hexLower.toUpperCase()).displayName(), v.hexLower);
            assertEquals(v.displayName, NpubNames.generate(v.npub).displayName(), v.npub);
            assertEquals(v.displayName, NpubNames.generate(v.npub.toUpperCase()).displayName(), v.npub);
            assertEquals(v.displayName, NpubNames.generate(hexToBytes(v.hexLower)).displayName(), v.hexLower);

            Name name = NpubNames.generate(v.hexLower);
            assertEquals(v.number, name.number(), v.hexLower);
            assertEquals(v.surnames, name.surnames(), v.hexLower);
        }
    }

    @Test
    void displayNameIsGivenPlusThreeSurnamesAndWithNumberAppendsZeroPaddedNumber() {
        for (Vector v : VECTORS) {
            Name name = NpubNames.generate(v.hexLower);
            assertEquals(v.displayName, name.given() + " " + String.join(" ", name.surnames()), v.hexLower);
            assertEquals(
                v.displayName + " " + String.format("%04d", name.number()),
                name.withNumber(),
                v.hexLower);
        }
    }

    @Test
    void lowNumberVectorShowsTheFourDigitZeroPadding() {
        assertEquals("Sheila Murillo Brooks Mata 0405", NpubNames.generate(LOW_NUMBER.hexLower).withNumber());
    }

    @Test
    void redrawVectorStillProducesThreeDistinctSurnames() {
        List<String> surnames = NpubNames.generate(REDRAW.hexLower).surnames();
        assertEquals(3, surnames.size());
        assertEquals(3, surnames.stream().distinct().count());
    }

    @Test
    void repeatedGenerationIsDeterministicAndNamesAreComparable() {
        Name first = NpubNames.generate(FIXED.npub);
        Name second = NpubNames.generate(FIXED.hexLower);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(NpubNames.generate(ALL_FF.hexLower), first);
    }

    @Test
    void surnamesListIsImmutable() {
        Name name = NpubNames.generate(FIXED.hexLower);
        assertThrows(UnsupportedOperationException.class, () -> name.surnames().add("Extra"));
    }

    @Test
    void rejectsValidBech32WithWrongHumanReadablePart() {
        // Same 32-byte payload encoded with the nsec HRP: a valid bech32 string that
        // is not an npub. Must be rejected, never silently accepted.
        assertThrows(
            IllegalArgumentException.class,
            () -> NpubNames.generate("nsec16q4p5hsut68s72mwr4xf57u08ewx675tnsx3ute6fdwx6l50ng9sya35xl"));
    }

    @Test
    void rejectsNpubWhosePayloadIsNotThirtyTwoBytes() {
        // Valid bech32, right HRP, 16-byte payload.
        assertThrows(
            IllegalArgumentException.class, () -> NpubNames.generate("npub1qqqqqqqqqqqqqqqqqqqqqqqqqqk7h3rf"));
    }

    @Test
    void rejectsCorruptedBech32Checksum() {
        String corrupted = FIXED.npub.substring(0, FIXED.npub.length() - 1)
            + (FIXED.npub.charAt(FIXED.npub.length() - 1) == 'q' ? 'p' : 'q');
        assertThrows(IllegalArgumentException.class, () -> NpubNames.generate(corrupted));
    }

    @Test
    void rejectsMixedCaseBech32() {
        String mixed = "Npub1" + FIXED.npub.substring(5);
        assertThrows(IllegalArgumentException.class, () -> NpubNames.generate(mixed));
    }

    @Test
    void rejectsMalformedHexSeeds() {
        assertThrows(IllegalArgumentException.class, () -> NpubNames.generate("d02a1a5e"));
        assertThrows(
            IllegalArgumentException.class,
            () -> NpubNames.generate("d02a1a5e1c5e8f0f2b6e1d4c9a7b8f3e5c6d7a8b9c0d1e2f3a4b5c6d7e8f9a0bff"));
        assertThrows(
            IllegalArgumentException.class,
            () -> NpubNames.generate("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"));
        assertThrows(IllegalArgumentException.class, () -> NpubNames.generate(""));
        assertThrows(IllegalArgumentException.class, () -> NpubNames.generate(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> NpubNames.generate(new byte[33]));
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> NpubNames.generate((String) null));
        assertThrows(NullPointerException.class, () -> NpubNames.generate((byte[]) null));
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private record Vector(String hexLower, String npub, String displayName, int number, List<String> surnames) {}
}
