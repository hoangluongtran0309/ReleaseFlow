# Translation test data

Real DeepL output, used by `UntranslatedGuardTest` to hold the untranslated guard of
[ADR-0036](../../../../docs/adr/0036-translation-glossary.md) to what DeepL actually writes.
These files are refreshed from their sources below and never edited by hand.

| File | What it is | Source |
| --- | --- | --- |
| `vietnamese-translations.txt` | 60 Vietnamese translations DeepL made, without a glossary, of the texts of the release notes ReleaseFlow published for dbvault, its end-to-end test project | The dbvault regression fixture, `fixtures/releaseflow/v1/normalized/translations.jsonl`: the first 60 en→vi rows ordered by `sha256` |
| `vietnamese-translations-with-glossary.txt` | 35 Vietnamese translations of the same kind of texts, made with the glossary below | Variant A, run 1, of the glossary test recorded in ADR-0036 (35 dbvault texts, the approved glossary, and the Project context) |
| `dbvault-glossary.tsv` | The glossary approved for dbvault: 40 kept terms mapped to themselves and 6 fixed en→vi pairs, `source<TAB>target` | The dbvault regression overlay `overlays/glossary-14/glossary.tsv` |

In the `.txt` files each line is one answer, a line break inside an answer is written `\n`,
and lines starting with `#` are comments.
