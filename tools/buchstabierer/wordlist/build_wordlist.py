"""Builds the Buchstabierer word list from the Free German Dictionary (german.dic, public domain).

Usage: python3 build_wordlist.py <german.dic> <blocklist.txt> <out>

german.dic is cp1252 encoded, one word per line. The output is UTF-8, one noun per line, sorted.
See SOURCE.md for where the input comes from.
"""
import re
import sys
from typing import Iterable

LETTERS = re.compile(r"^[A-Za-zÄÖÜäöüß]+$")
MIN_LENGTH = 4
# Longer words are mostly compounds and tiring to spell.
MAX_LENGTH = 10
# Longest first only for readability, every suffix is tried.
SUFFIXES = ("ers", "ern", "ens", "es", "en", "er", "em", "ns", "e", "n", "s")


def is_blocked(word: str, exact: set[str], prefixes: tuple[str, ...]) -> bool:
    lower = word.lower()
    return lower in exact or lower.startswith(prefixes)


def filter_words(words: Iterable[str], blocklist: set[str]) -> list[str]:
    """Keeps nouns of 4 to 10 letters, drops blocklisted words and inflected forms of words that are kept.

    Nouns are words with a capital first letter and only lowercase letters after it. Words ending in "ens" are
    almost always genitives of nominalised verbs ("Abbiegens") and are dropped.

    Blocklist entries are lowercase. An entry ending in `*` also blocks every word starting with it.
    """
    exact = {entry for entry in blocklist if not entry.endswith("*")}
    prefixes = tuple(entry[:-1] for entry in blocklist if entry.endswith("*"))
    candidates = {
        word
        for word in (w.strip() for w in words)
        if LETTERS.match(word)
        and word[0].isupper()
        and word[1:].islower()
        and not word.endswith("ens")
        and MIN_LENGTH <= len(word) <= MAX_LENGTH
        and not is_blocked(word, exact, prefixes)
    }

    def inflected(word: str) -> bool:
        if any(word.endswith(suffix) and word[: -len(suffix)] in candidates for suffix in SUFFIXES):
            return True
        # Without the base form in the list, keep only the short genitive: "Abschnitts" instead of "Abschnittes".
        return word.endswith("es") and word[:-2] + "s" in candidates

    return sorted(word for word in candidates if not inflected(word))


def read_blocklist(path: str) -> set[str]:
    with open(path, encoding="utf-8") as file:
        return {line.strip().lower() for line in file if line.strip() and not line.startswith("#")}


def main() -> None:
    source, blocklist_path, out = sys.argv[1:4]
    with open(source, encoding="cp1252") as file:
        words = filter_words(file, read_blocklist(blocklist_path))
    with open(out, "w", encoding="utf-8", newline="\n") as file:
        file.write("\n".join(words) + "\n")
    print(f"{len(words)} words written to {out}")


if __name__ == "__main__":
    main()
