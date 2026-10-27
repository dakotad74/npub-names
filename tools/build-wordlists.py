#!/usr/bin/env python3
"""Build the frozen wordlists for npub-names.

Sources (public domain, U.S. Government):
  - Surnames:   U.S. Census Bureau, 2010 Census surnames (Names_2010Census.csv)
                https://www2.census.gov/topics/genealogy/2010surnames/names.zip
  - Given names: U.S. Census Bureau, 1990 Census first names
                (dist.male.first + dist.female.first)
                https://www2.census.gov/topics/genealogy/1990surnames/dist.male.first
                https://www2.census.gov/topics/genealogy/1990surnames/dist.female.first

Rules (must stay in sync with SPEC.md):
  - Keep only words of ASCII letters (^[A-Za-z]{2,15}$): no hyphens, spaces or apostrophes.
  - Dedupe case-insensitively (given names sum male+female frequencies).
  - Rank by frequency descending, ties broken by word ascending.
  - Take the top 1024 of each list, then freeze in alphabetical order.
  - Capitalize (first letter upper, rest lower).
"""

import csv
import hashlib
import re
import sys
from collections import defaultdict

TOP_N = 1024
WORD_RE = re.compile(r"^[A-Za-z]{2,15}$")


def cap(word: str) -> str:
    return word[:1].upper() + word[1:].lower()


def take_top(counts: dict[str, float]) -> list[str]:
    ranked = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
    top = [cap(name) for name, _ in ranked[:TOP_N]]
    return sorted(top)


def build_given(dist_male: str, dist_female: str) -> list[str]:
    counts: dict[str, float] = defaultdict(float)
    for path in (dist_male, dist_female):
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                parts = line.split()
                if len(parts) < 2:
                    continue
                name, freq = parts[0], float(parts[1])
                if WORD_RE.match(name):
                    counts[name.upper()] += freq
    return take_top(counts)


def build_surnames(csv_path: str) -> list[str]:
    counts: dict[str, float] = {}
    with open(csv_path, encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            name = row["name"].strip()
            if WORD_RE.match(name):
                counts[name.upper()] = float(row["count"])
    return take_top(counts)


def sha256_file(path: str) -> str:
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def main() -> None:
    given = build_given(sys.argv[1], sys.argv[2])
    surnames = build_surnames(sys.argv[3])

    for name, words in (("given-names-1024.txt", given), ("surnames-1024.txt", surnames)):
        assert len(words) == TOP_N, f"{name}: {len(words)} != {TOP_N}"
        assert len({w.upper() for w in words}) == TOP_N, f"{name}: duplicates"
        with open(name, "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(words) + "\n")
        print(f"{name}: {len(words)} words, sha256={sha256_file(name)}")

    # Overlap report: words appearing in both lists are fine (different slots),
    # but worth knowing.
    overlap = {w for w in given} & {w for w in surnames}
    print(f"words present in both lists: {len(overlap)} {sorted(overlap)[:8]}{'...' if len(overlap) > 8 else ''}")


if __name__ == "__main__":
    main()
