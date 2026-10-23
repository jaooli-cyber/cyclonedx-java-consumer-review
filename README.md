# CycloneDX Java and Dependency-Track review

A focused external interoperability check by José Ángel Olivares,
[BOM Fidelity / Global Trade Origin](https://bomfidelity.globaltradeorigin.com/).
The central distinction is **schema validity, library parsing, consumer import
and preservation of cryptographic detail are separate checks**.

The released core-java 13.3.0 fixes the tested CycloneDX 1.7 enum gap. The pinned
Dependency-Track upgrade has a separate Maven dependency conflict; a diagnostic
validator override enables import, but stored/exported cryptoProperties remains
absent, including for pre-existing controls. This is not a new retention regression.

| Per run | Core 13.2.0 and consumer base | Core 13.3.0 and diagnostic consumer |
| --- | ---: | ---: |
| Schema-valid inputs | 20 | 20 |
| Parsed inputs | 4 | 20 |
| Preserving library round trips | 8 | 40 |
| Completed HTTP imports | 4 | 20 |
| Exported components retaining cryptoProperties | 0 | 0 |

Read the [full report](REPORT.md), [pinned build result](builds/RESULTS.md) and
[reproduction instructions](REPRODUCE.md). The 24 synthetic/upstream JSON/XML
fixtures, case matrix, standalone Java harnesses and selected measurements are
included. There is no BOM Fidelity engine, portal or infrastructure code here.

```sh
python3 -B verify_publication.py .
```

The report concerns core-java #951 and Dependency-Track #7754 at the exact
revisions in provenance.json, not every release or input. No upstream test-suite
or UI certification is claimed. Cite v1.0.0 using [CITATION.cff](CITATION.cff).
Our material is Apache-2.0; upstream fixtures retain the accompanying licences.

Related source references: [Rust CBOM cases](https://github.com/jaooli-cyber/rust-cbom-reference-cases).
