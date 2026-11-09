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


class Blocklist:
    """Entries are lowercase. `word` blocks exactly that word (and its inflected forms), `word*` every word starting
    with it, `*word` every word ending with it, `*word*` every word containing it. `!word` allows words ending with
    it again, for harmless words a suffix or infix rule would hit ("Barsch" against `*arsch`)."""

    def __init__(self, entries: set[str]):
        self.allowed = tuple(e[1:] for e in entries if e.startswith("!"))
        rules = [e for e in entries if not e.startswith("!")]
        self.contains = tuple(e[1:-1] for e in rules if e.startswith("*") and e.endswith("*") and len(e) > 2)
        self.suffixes = tuple(e[1:] for e in rules if e.startswith("*") and not e.endswith("*"))
        self.prefixes = tuple(e[:-1] for e in rules if e.endswith("*") and not e.startswith("*"))
        self.exact = {e for e in rules if "*" not in e}

    def blocks(self, word: str) -> bool:
        """True for a blocked word and for inflected forms of it ("Sexes", "Dorfhuren")."""
        lower = word.lower()
        if lower not in self.exact and lower.endswith(self.allowed):
            return False
        stems = [lower] + [lower[: -len(s)] for s in SUFFIXES if lower.endswith(s) and len(lower) > len(s)]
        return any(self._blocks_stem(stem) for stem in stems)

    def _blocks_stem(self, lower: str) -> bool:
        if lower in self.exact:
            return True
        if lower.endswith(self.allowed):
            return False
        return lower.startswith(self.prefixes) or lower.endswith(self.suffixes) or any(c in lower for c in self.contains)


def filter_words(words: Iterable[str], blocklist: set[str]) -> list[str]:
    """Keeps nouns of 4 to 10 letters, drops blocklisted words and inflected forms of words that are kept.

    Nouns are words with a capital first letter and only lowercase letters after it. Words ending in "ens" are
    almost always genitives of nominalised verbs ("Abbiegens") and are dropped. See [Blocklist] for its syntax.
    """
    blocked = Blocklist(blocklist)
    candidates = {
        word
        for word in (w.strip() for w in words)
        if LETTERS.match(word)
        and word[0].isupper()
        and word[1:].islower()
        and not word.endswith("ens")
        and MIN_LENGTH <= len(word) <= MAX_LENGTH
        and not blocked.blocks(word)
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
