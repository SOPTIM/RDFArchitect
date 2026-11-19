# Agent Instructions

The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as described in RFC 2119 and RFC 8174.

Toolchain versions are listed under Prerequisites in `README.md`. Details on everything below are in `docs/content/developer-guide/` and `.github/CONTRIBUTING.md`.

## Architecture

The backend follows a hexagonal architecture (`docs/content/developer-guide/backend-architecture.md`). For new code:

- Each new action MUST get its own `*UseCase` interface with one method, implemented by a service in the matching package under `services/`.
- Controllers MUST depend only on use-case interfaces.
- Data access MUST go through `DatabasePort` only.
- New SPARQL queries MUST be separate files under `backend/src/main/resources/sparql-templates/`, loaded via `SparqlTemplateLoader`, not inline in Java.
- Existing code has deviations (use cases with several methods, packages by operation, inline queries). New code MUST NOT copy them.

## Generated files

These files MUST NOT be edited by hand. They MUST be regenerated with the given command when the trigger applies.

- `CHANGELOG.md`: generated from squash commit titles by the `Update Changelog` workflow at release time. Never touch it.
- `frontend/openapi.json`: after changing REST routes or DTOs, run `mvn exec:java@export-openapi` in `backend/` and commit the result; CI checks it.
- `frontend/src/lib/api/generated/`: after `frontend/openapi.json` changes, run `npm run api:generate` in `frontend/`.
- `docs/content/reference/changelog.md`: synced automatically by `npm start` and `npm run build` in `docs/`.
- `backend/LICENSES-THIRD-PARTY.md`: after changing dependencies in `backend/pom.xml`, run `mvn -B org.codehaus.mojo:license-maven-plugin:add-third-party` in `backend/`; CI checks it.
- `frontend/LICENSES-THIRD-PARTY.md`: after changing dependencies in `frontend/package.json`, run `npm run licenses:generate` in `frontend/`; lint checks it.

## Code quality

- Code and comments MUST be in English.
- Code MUST NOT be duplicated. Existing helpers SHOULD be reused, and new code SHOULD be written to be reusable.
- Readability and maintainability MUST take priority over brevity and cleverness.

## Comments

- Comments SHOULD only be added where the code is not self-explanatory. Use plain language and short sentences; bullet points are fine.
- A comment MAY explain a non-obvious reason, such as a workaround or an external constraint.
- Comments MUST NOT narrate the change, justify decisions, or refer to previous or other code ("previously …", "same as in X").
- Comments MUST NOT restate the code. If a comment would only repeat it, improve the name instead.
- Comments MUST NOT contain ticket, PR or author references.
- Commented-out code MUST NOT be committed. Delete unused code.
- Decorative comments (banners, separators, `} // end if`) SHOULD NOT be used. Split the file instead.
- When code changes, its comments and JavaDoc MUST be updated or removed in the same change.
- Comments SHOULD use the present tense and describe behavior ("Returns …", "Skips …"), without "we" or "I".
- Tests MAY use `// given`, `// when`, `// then` as structure markers.
- In the backend, every new or changed method of an interface MUST have JavaDoc: one summary sentence, `@param` for each parameter, `@return` unless `void`, and `@throws` for exceptions the caller has to handle.
- New `.sparql` files MUST start with a short `#` comment stating what the query does and which parameters it expects.

```java
// Good: The external service rejects more than 100 IDs per request, so they are sent in batches.
// Bad:  Loop over all classes and add them to the list.
// Bad:  Changed to a map because the list was too slow.
```

## Documentation

- Changes to behavior, API or setup MUST update the documentation in the same change:
  - user-visible behavior: `docs/content/user-guide/`
  - installation, configuration, operations: `docs/content/admin-guide/`
  - architecture, CI, code style: `docs/content/developer-guide/`
  - quickstart, prerequisites, configuration highlights, API client: `README.md`
- Documentation MUST be in English and describe the current state only, without history ("previously …", "since v1.7 …").
- Documentation SHOULD be descriptive: what exists and how to use it, not background or motivation.
- Documentation SHOULD use short sentences and prefer bullet points.
- New pages MUST be added to `docs/sidebars.js`.

## Tests and checks

- Backend tests: `mvn -B test`, a single class with `mvn -B test -Dtest=<ClassName>`.
- Frontend tests: `npm run test`, a single file with `npm run test -- <path>`.
- Before committing, run in `backend/`: `mvn -B spotless:apply`, then `mvn -B -Plint -DskipTests verify`.
- Before committing, run in `frontend/`: `npm run format`, then `npm run lint`.

## Commits

Commits follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) and `.github/CONTRIBUTING.md`.

- Commit messages and PR titles MUST use `<type>[optional scope][!]: <description> (#<pr>, RDFA-<id>, GH-<issue>)`. The PR title becomes the squash commit and the changelog entry.
- The type MUST be one of `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `style`.
- The description MUST be imperative, lowercase and without a trailing period. It SHOULD be short and specific.
- Commit bodies and PR descriptions SHOULD be limited to the relevant changes and written as bullet points, not prose.
- Breaking changes MUST be marked with `!` in the header or `BREAKING CHANGE:` in the body.
- Every commit MUST be signed off (`git commit -s`) for the DCO.
