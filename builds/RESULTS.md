# Pinned consumer build results

| Variant | Result | Maven enforcer |
| --- | --- | --- |
| Base b9dcc8959e5bacb84259e1b4f6a5623131951f27 | PASS | Enabled |
| PR head a5341448910c2dcf3d461720fa3292697301733a unchanged | FAIL | RequireUpperBoundDeps |
| Same head with -Dlib.json-schema-validator.version=2.0.8 | PASS | Enabled |

The project manages com.networknt:json-schema-validator 2.0.4 whereas core-java
13.3.0 requires 2.0.8. The diagnostic override is not part of PR 7754. Builds
targeted `package -pl apiserver -am`; upstream test and formatting tasks were
skipped. These results are not claims about upstream CI or later revisions.
