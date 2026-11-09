# Word list source

`src/main/res/raw/words.txt` is generated from the **Free German Dictionary** by Jan Schreiber.

| | |
|---|---|
| Project | https://sourceforge.net/projects/germandict/ |
| File | `german.dic`, SVN revision 2187 (last change 2021-10-01, "+ 1,600 word forms"), 2,152,638 lines, cp1252 |
| Fetched from | https://svn.code.sf.net/p/germandict/code/german.dic on 2026-11-09 |
| Licence | **Public Domain**, as stated in the licence field of the SourceForge project page. The `readme.txt` in the repository contains no licence text of its own. |

The project's "Quellen und Methoden.txt" says the word forms were extracted with Hunspell from a corpus (German
Wikipedia dump, OpenThesaurus with permission, own texts and colloquial/vulgar texts). That is why
`blocklist.txt` exists.

## Regenerate

```bash
curl -L -o /tmp/german.dic https://svn.code.sf.net/p/germandict/code/german.dic
python3 build_wordlist.py /tmp/german.dic blocklist.txt ../src/main/res/raw/words.txt
python3 -m unittest test_build_wordlist.py
```

## Filter (see `build_wordlist.py`)

1. Only letters `A-Z`, `a-z`, `ÄÖÜäöüß`.
2. Nouns only: capital first letter, lowercase afterwards (drops abbreviations, `MyVideo`, verbs, adjectives).
3. 4 to 10 letters.
4. Not ending in `ens` (genitives of nominalised verbs such as `Abbiegens`).
5. Not on `blocklist.txt`, including inflected forms (exact, prefix `word*`, suffix `*word`, infix `*word*` rules and `!word` exceptions, see the file header).
6. Inflected forms are dropped when their base form is kept (suffixes `ers ern ens es en er em ns e n s`),
   and `…es` is dropped when `…s` exists.

Result on revision 2187: 135,677 words, 1.36 MB.
