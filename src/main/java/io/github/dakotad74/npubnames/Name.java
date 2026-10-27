package io.github.dakotad74.npubnames;

import java.util.List;
import java.util.Objects;

/**
 * A deterministic human name derived from a Nostr public key.
 *
 * <p>Immutable and comparable: the same key always yields an equal name.</p>
 */
public final class Name {

    private final String given;
    private final List<String> surnames;
    private final int number;

    Name(String given, List<String> surnames, int number) {
        this.given = Objects.requireNonNull(given, "given");
        this.surnames = List.copyOf(surnames);
        this.number = number;
    }

    /** The first name, e.g. {@code "Paula"}. */
    public String given() {
        return given;
    }

    /** Exactly three distinct surnames, in derivation order. Immutable. */
    public List<String> surnames() {
        return surnames;
    }

    /** The deterministic disambiguator, 0..9999. Derived always; show it only when needed. */
    public int number() {
        return number;
    }

    /** The clean display name, e.g. {@code "Paula Daniels Stanton Little"}. */
    public String displayName() {
        return given + " " + String.join(" ", surnames);
    }

    /** The display name plus the zero-padded four-digit number, e.g. {@code "Paula Daniels Stanton Little 0629"}. */
    public String withNumber() {
        return displayName() + " " + String.format("%04d", number);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Name name)) {
            return false;
        }
        return number == name.number && given.equals(name.given) && surnames.equals(name.surnames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(given, surnames, number);
    }

    @Override
    public String toString() {
        return "Name[" + displayName() + "]";
    }
}
