# CycloneDX 1.7 crypto enums: library and consumer check

BOM Fidelity · José Ángel Olivares · 23 October 2026 · Review v1.0.0

## Conclusions

The core-java fix works for the targeted values. Released 13.3.0 reads the
schema-valid JSON and XML fixtures that 13.2.0 rejects and preserves their
cryptographic properties through JSON and XML re-serialization.

Two separate Dependency-Track findings remain:

1. At the pinned revision, PR #7754's dependency bump alone fails Maven's
   `RequireUpperBoundDeps` rule: the project manages
   `com.networknt:json-schema-validator` at **2.0.4**, whereas core-java 13.3.0
   requires **2.0.8**. The pinned base builds. A local diagnostic variant with
   only `-Dlib.json-schema-validator.version=2.0.8` also builds, without
   disabling the enforcer rule. This override is not part of the tested PR.
2. With that override, the real API imports the affected components successfully,
   but `cryptoProperties` is absent from the stored-component API representation
   and from CycloneDX **1.7** JSON/XML exports. The same boundary exists for
   already-supported controls on the base. This is not evidence of a new
   regression introduced by #7754, nor a claim that the original uploaded file
   is deleted or modified.

In short: **schema validity, library parsing, successful import and semantic
retention are four distinct checks**. This review verifies each of them for a
small, explicit matrix; it is not a general certification of either project.

## Pinned inputs

| Item | Identity |
| --- | --- |
| Core change | [cyclonedx-core-java #951](https://github.com/CycloneDX/cyclonedx-core-java/pull/951), merge `2becc7150d87b313951e4c4b7676d1da41fb8faf` |
| Released libraries | Maven Central `org.cyclonedx:cyclonedx-core-java:13.2.0` and `13.3.0` |
| Consumer change | [Dependency-Track #7754](https://github.com/DependencyTrack/dependency-track/pull/7754) |
| Consumer base | `b9dcc8959e5bacb84259e1b4f6a5623131951f27` |
| Consumer PR head | `a5341448910c2dcf3d461720fa3292697301733a` |
| Consumer source version | `5.3.0-SNAPSHOT`; not an installed stable-release instance |

At acquisition, #951 was merged and #7754 was open. The PR changes only the
root POM's core-java version from 13.2.0 to 13.3.0. The downloaded base POM and
head POM were compared byte-for-byte apart from that single substitution.
Artifact URLs and SHA-256 values are in `provenance.json`. Resolved test JARs
were checked against the separately acquired published JARs.

## Matrix and method

There are **24 input files**, each used with both library versions:

- Seven independently isolated values, each in JSON and XML: primitive
  `key-wrap`; protocol types `dtls`, `quic`, `eap-aka`, `eap-aka-prime`,
  `prins`, `5g-aka`.
- Two existing-value controls, each in JSON and XML: `block-cipher`, `tls`.
- Two deliberately invalid values, each in JSON and XML:
  `not-a-primitive`, `not-a-protocol`.
- The combined JSON and XML fixtures from #951, at its pinned merge revision.

Thus 20 inputs are schema-valid and four are invalid. The valid new-value and
combined inputs total 16; the existing-value controls total four. Expected
outcomes and component projections were fixed before execution in
`fixture-matrix.json`. The projection covers `bom-ref`, name, component type
and all `cryptoProperties` present in these fixtures. It does not assert
coverage of every possible CBOM property or parameter.

The library harness calls the real released parsers, schema validators and
generators. Every successfully parsed input is emitted as both JSON and XML,
validated, parsed again and compared with the predeclared projection. Both
cross-format directions are included.

Consumer checks use the actual pinned `ModelConverter`, not a reimplementation.
Separate HTTP checks start a fresh PostgreSQL database and the actual built API
for each run. Every input gets a new project. After upload, the client requires
an explicit terminal workflow state, queries stored components and project
import metadata, then requests both JSON and XML exports with `version=1.7`.
An HTTP 200 upload acknowledgment or `processing=false` alone does not count as
a successful import.

## Results per run

Each library and converter check was repeated twice with identical result JSON.
HTTP checks were repeated twice per variant against fresh databases; semantic
results agree after excluding generated project IDs, tokens and timestamps.
The selected per-case result tables retain the zero unexpected outcomes. Counts below are **per run**, not totals inflated by repeats.

| Check | Base: core 13.2.0 / validator 2.0.4 | Diagnostic: core 13.3.0 / validator 2.0.8 |
| --- | ---: | ---: |
| Input schema valid / invalid | 20 / 4 | 20 / 4 |
| Successfully parsed input files | 4 | 20 |
| New-value/combined inputs parsed | 0 / 16 | 16 / 16 |
| Invalid inputs rejected by parser | 4 / 4 | 4 / 4 |
| Valid, projection-preserving library round trips | 8 | 40 |
| Actual HTTP imports completed | 4 | 20 |
| Valid inputs failing after HTTP acceptance | 16 | 0 |
| Invalid inputs rejected at upload, HTTP 400 | 4 | 4 |
| Components stored in successful imports | 4 | 32 |
| Stored components with `cryptoProperties` in API response | 0 | 0 |
| JSON / XML 1.7 exports checked | 4 / 4 | 20 / 20 |
| Exported components with `cryptoProperties`, in either format | 0 | 0 |

All successful imports preserve the expected component names and
`CRYPTOGRAPHIC_ASSET` classifier and have project import metadata recorded.
The 32-component total includes the multi-component upstream fixtures; it is
not 32 independent algorithm definitions. Direct converter checks explain the
same measured boundary: four of four control components lose
`cryptoProperties` on the base, as do 32 of 32 converted components on the
diagnostic variant.

For example, `key-wrap.json` is valid under 1.7 with either library's validator.
The old parser rejects it. In the old API, upload returns 200 and a token, but
the workflow ends `FAILED` and no component is stored. In the aligned new API,
the workflow ends `COMPLETED`, one cryptographic-asset component is stored,
and the 1.7 export contains its name and type but not its input
`cryptoProperties.algorithmProperties.primitive = key-wrap`.

## Build finding

The three consumer build results are kept separate:

| Variant | Maven package result | Enforcer |
| --- | --- | --- |
| Pinned base, core 13.2.0 | Success | Enabled |
| Unmodified PR head, core 13.3.0 | Failure: networknt upper-bound conflict | Enabled |
| PR head plus validator 2.0.8 command-line override | Success | Enabled |

Build status and the dependency conflict are recorded in builds/RESULTS.md.
The command-line override is a narrow way to investigate the consumer after
the dependency conflict; it must not be mistaken for a passing build of the
unmodified PR. A maintainer can decide the appropriate permanent dependency
management change and run their full CI.

Builds used `package -pl apiserver -am`, offline resolved dependencies and
incremental reactor targets. Upstream tests, test compilation, formatting,
coverage and generated project CBOM tasks were skipped consistently. **This
review does not claim to have run the upstream test suite.** The independent
library, converter and HTTP tests described here were actually executed.

## Consumer boundary in source

The import activity parses the CBOM and invokes component conversion before
persistence. At the pinned source, `ModelConverter.convertComponent` does not
copy `cryptoProperties` into the internal `Component` model, and that model
has no `getCryptoProperties` accessor. Reverse conversion also does not emit
it. The measured import/export results are consistent with this source boundary:

- [ImportBomActivity](https://github.com/DependencyTrack/dependency-track/blob/a5341448910c2dcf3d461720fa3292697301733a/apiserver/src/main/java/org/dependencytrack/tasks/ImportBomActivity.java)
- [ModelConverter](https://github.com/DependencyTrack/dependency-track/blob/a5341448910c2dcf3d461720fa3292697301733a/apiserver/src/main/java/org/dependencytrack/parser/cyclonedx/util/ModelConverter.java)
- [Internal Component model](https://github.com/DependencyTrack/dependency-track/blob/a5341448910c2dcf3d461720fa3292697301733a/apiserver/src/main/java/org/dependencytrack/model/Component.java)

This separates the enum fix from broader CBOM representation support. It does
not imply that all fields are lost, that the fix is ineffective, or that the
original uploaded document cannot remain available separately.

## Environment, limits and next action

Runtime: OpenJDK Temurin 25.0.4.1+1, Maven 3.10.0 and PostgreSQL 17.6,
in an isolated remote Linux laboratory.

The review does not test the web UI, Dimitri's private original CBOM,
production deployment, other Dependency-Track releases, external vulnerability
feeds, every crypto field, or arbitrary input security. 

Recommended next steps are to align the managed validator dependency for the
upgrade and treat `cryptoProperties` model/persistence/export support as a
separate scoped issue if full cryptographic detail is intended in the consumer.
No upstream PR, public comment or service deployment was changed by this check.

Website: [BOM Fidelity](https://bomfidelity.globaltradeorigin.com/).
