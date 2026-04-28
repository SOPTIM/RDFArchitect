---
title: Contribution Scenarios
sidebar_position: 11
---

# Common Contribution Scenarios

## Fixing a UI bug

Almost always confined to `frontend/src/`. Reproduce against a local backend, write a Vitest unit test if the bug is in pure logic, fix, run `npm run lint && npm run test && npm run build`, walk the relevant section of the manual test checklist, open a PR.

## Adding a new field to a class / attribute / association

Touches every layer: backend DTO → frontend DTO type → reactive wrapper → mapper → UI control → tests on both sides. Use the [end-to-end walkthrough](./adding-a-feature) as a template. Keep all changes in one PR if scoped to a single field.

## Fixing or extending SHACL generation

Backend-only. The starting point is `services/shacl/SHACLGenerateService` and its test, `SHACLFromCIMGeneratorTest`. Add a failing test case first; it makes the fix easier to verify and prevents regressions.

## Adding migration support for a new schema-change pattern

Three steps:

1. Add a SPARQL template under `src/main/resources/sparql-templates/migration/`.
2. Extend `services/schemamigration/` to detect and emit the new pattern.
3. Extend the wizard's relevant step (`frontend/src/routes/migrate/steps/`) to confirm or override the proposed change.

## Improving documentation

Documentation lives in `docs/`. Markdown files there are referenced from the README and from each other. PRs that improve documentation are *especially* welcome — they are the lowest-effort way to start contributing and they help everyone.

## Where to ask questions

- **Architecture or design questions**: open a discussion in [GitHub Discussions](https://github.com/SOPTIM/RDFArchitect/discussions). Maintainers prefer this channel for anything that does not yet correspond to a concrete bug or feature.
- **Bugs**: open an issue with the bug report template. Include the version (visible on the homepage), browser, and a reproducer.
- **Features**: open an issue with the feature request template.
- **Security issues**: do not use public channels. See [`SECURITY.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/SECURITY.md).

## Appendix: useful commands

```bash
# Backend — common single commands
mvn -B spotless:apply                       # auto-format Java
mvn -B -Plint -DskipTests verify            # all linters (checkstyle, spotbugs, license)
mvn -B test                                 # unit tests
mvn -B verify                               # full verify (unit + integration + jacoco)
mvn -B test -Dtest=ClassName                # single test class
mvn -B test -Dtest=ClassName#methodName     # single test method

# Frontend — common single commands
npm run dev                                 # dev server on :1407
npm run build                               # production build
npm run test                                # vitest
npm run test -- --watch                     # vitest in watch mode
npm run lint                                # prettier + eslint + license check
npm run format                              # prettier --write + eslint --fix
npm run clean-install                       # nuke node_modules and reinstall

# Docker — full stack
cd docker
docker compose up --build                   # build + run
docker compose down                         # stop + remove
docker compose up backend                   # only the backend (frontend at :1407 expected)
```

Welcome to RDFArchitect — looking forward to your contribution.
