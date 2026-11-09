import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_wordlist import filter_words  # noqa: E402


class FilterWordsTest(unittest.TestCase):
    def run_filter(self, words, blocklist=()):
        return filter_words(words, {w.lower() for w in blocklist})

    def test_inflected_forms_are_dropped(self):
        self.assertEqual(self.run_filter(["Aachen", "Aacheners", "Aachens"]), ["Aachen"])

    def test_plain_noun_is_kept(self):
        self.assertEqual(self.run_filter(["Haus"]), ["Haus"])

    def test_invalid_characters_are_dropped(self):
        self.assertEqual(self.run_filter(["Abc1", "Ab-c", "Ab'c", "Ab c"]), [])

    def test_all_caps_and_lowercase_are_dropped(self):
        self.assertEqual(self.run_filter(["USA", "AKKU", "laufen"]), [])

    def test_length_limits(self):
        self.assertEqual(self.run_filter(["Rad", "Rand", "Abteilung1"[:-1], "Feuerwehren", "Donaudampfschiff"]), ["Abteilung", "Rand"])

    def test_blocklist_removes_word_and_compounds(self):
        self.assertEqual(self.run_filter(["Arsch", "Arschloch", "Haus"], blocklist=["arsch*"]), ["Haus"])

    def test_plain_blocklist_entry_matches_exactly(self):
        self.assertEqual(self.run_filter(["Anal", "Analyse"], blocklist=["anal"]), ["Analyse"])

    def test_mixed_case_is_dropped(self):
        self.assertEqual(self.run_filter(["MyVideo", "Haus"]), ["Haus"])

    def test_genitive_of_nominalised_verbs_is_dropped(self):
        self.assertEqual(self.run_filter(["Abbiegens", "Haus"]), ["Haus"])

    def test_long_genitive_is_dropped_when_short_one_exists(self):
        self.assertEqual(self.run_filter(["Abschnittes", "Abschnitts"]), ["Abschnitts"])

    def test_infix_entry_blocks_compounds(self):
        self.assertEqual(self.run_filter(["Dreckfotze", "Fotzen", "Haus"], blocklist=["*fotze*"]), ["Haus"])

    def test_suffix_entry_with_exceptions(self):
        self.assertEqual(
            self.run_filter(["Riesenarsch", "Barsch", "Seebarsch", "Marsch"], blocklist=["*arsch", "!barsch", "!marsch"]),
            ["Barsch", "Marsch", "Seebarsch"],
        )

    def test_inflected_forms_of_blocked_words_are_blocked(self):
        self.assertEqual(self.run_filter(["Sexes", "Haus"], blocklist=["sex"]), ["Haus"])

    def test_inflected_forms_of_suffix_rule_words_are_blocked(self):
        self.assertEqual(self.run_filter(["Dorfhuren", "Haus"], blocklist=["*hure"]), ["Haus"])

    def test_exception_also_protects_the_inflection_check(self):
        self.assertEqual(self.run_filter(["Basispaste", "Vollspast"], blocklist=["*spast", "!paste"]), ["Basispaste"])

    def test_umlauts_and_eszett_are_letters(self):
        self.assertEqual(self.run_filter(["Ärger", "Straße"]), ["Straße", "Ärger"])

    def test_output_is_sorted_and_unique(self):
        self.assertEqual(self.run_filter(["Baum", "Apfel", "Baum"]), ["Apfel", "Baum"])


if __name__ == "__main__":
    unittest.main()
