---
title: Testing
sidebar_position: 6
---

# Testing

## Backend

- **Unit tests** — `mvn -B test`. Surefire runs everything matching `*Test.java`.
- **Integration tests** — `mvn -B verify`. Failsafe runs everything matching `*IT.java`. Integration tests typically spin up Spring with `@SpringBootTest`.
- **Coverage** — JaCoCo runs as part of `verify`. The report is at `backend/target/site/jacoco/index.html`. There is no enforced threshold; aim for new code to be at least as well-covered as the area it sits in.
- **Test resources** — RDF fixtures live under `src/test/resources/`. Tests that need a triple store use `InMemoryDatabaseImpl`, which is faster and isolated.

The largest test suites today are around CIM-graph-to-DTO conversion (`cim/data/...`) and SHACL generation (`shacl/SHACLFromCIMGeneratorTest`); these are the canonical references for "what does a good RDFArchitect test look like".

## Frontend

- **Unit tests** — `npm run test` (Vitest, jsdom). Component-level and pure-function tests under `tests/`.
- **Linting** — `npm run lint` runs Prettier check, ESLint, and a third-party-license consistency check.
- **Build** — `npm run build` is run in CI; if it breaks, the lint did not catch the issue.

There is no end-to-end browser test suite at the time of writing. The `.github/pull_request_template.md` includes a substantial **manual test checklist** that contributors are expected to walk through before requesting review — treat it as the de-facto integration test.

## Running everything locally before a PR

```bash
# Backend
cd backend
mvn -B spotless:apply             # auto-format
mvn -B -Plint -DskipTests verify  # checkstyle, spotbugs, license headers
mvn -B test
mvn -B verify

# Frontend
cd ../frontend
npm run format                    # auto-format (prettier + eslint --fix)
npm run lint
npm run test
npm run build
```

CI runs all of the above on every PR. Running them locally first is much faster than waiting for the CI feedback loop.
