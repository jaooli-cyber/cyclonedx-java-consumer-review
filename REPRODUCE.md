# Reproducing the Java library check

## Read only verification

```sh
python3 -B verify_publication.py .
```

Requires Python 3.11 or newer and no external Python packages. It checks file
integrity and result-table consistency, not a fresh HTTP import or Maven build.

## Released library experiment

Use an isolated disposable Linux environment with JDK 25 and Maven. Acquire
the dependencies into separate version-specific directories, then disable
networking before compilation and execution. For each version, using absolute
output paths outside this repository:

```sh
mvn -f harness/harness-pom.xml -Dcore.version=13.2.0 \
  dependency:copy-dependencies -DoutputDirectory=/scratch/deps/13.2.0
mvn -f harness/harness-pom.xml -Dcore.version=13.3.0 \
  dependency:copy-dependencies -DoutputDirectory=/scratch/deps/13.3.0
# After restricting networking:
sh harness/run-library.sh 13.2.0 /scratch/deps/13.2.0 /scratch/check-old
sh harness/run-library.sh 13.3.0 /scratch/deps/13.3.0 /scratch/check-new
```

Check the main JAR hashes against provenance.json. Transitive dependency
resolution is not a bundled offline cache or a complete transitive lock;
preserve your resolved dependency tree when reproducing later. Expected: 24
fixtures each, 4 versus 20 parse successes, 8 versus 40 projection-preserving
round trips and zero expectation mismatches.

## Consumer reproduction

The included ConsumerBoundaryCheck uses the real ModelConverter at the pinned
Dependency-Track revision. Compile it against a separately built API and its
matching runtime dependencies, naming the core JAR core-13.2.0.jar or
core-13.3.0.jar as asserted by the harness. This source is a converter check;
it does not simulate database persistence.

For an HTTP reproduction, build the pinned base and the pinned head plus the
explicit validator override in builds/RESULTS.md. Start each against a fresh
PostgreSQL 17.6 database in a disposable, network-restricted test environment.
Using that instance's normal API authentication, create one project per fixture,
upload the fixture to /api/v1/bom, wait for its terminal workflow state, inspect
stored components and request /api/v1/bom/cyclonedx/project/PROJECT_UUID in both
JSON and XML with version=1.7. An upload acknowledgment is not import success.
Inspect cryptoProperties in both the stored component response and exports.

Selected case measurements are supplied for both clean-database repetitions.
Private infrastructure provisioning and raw HTTP traces are not distributed.
Recreating an API/database environment is an additional experiment, not an
action performed by the read-only publication verifier.
